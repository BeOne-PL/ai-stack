package org.alfresco.events.handler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.ai.AIClient;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.filter.IsFileFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.event.sdk.handling.handler.OnNodeDeletedEventHandler;
import org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler;
import org.alfresco.events.filter.ParentFolderFilter;
import org.alfresco.model.NodeEventTask;
import org.alfresco.repo.event.v1.model.*;
import org.alfresco.service.AlfrescoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Handler for content-related repository events that manages synchronization with an AI service.
 * Handles node creation, update, and deletion events within a specified folder.
 */
@Slf4j
@Component
public class ContentHandler implements OnNodeCreatedEventHandler, OnNodeUpdatedEventHandler, OnNodeDeletedEventHandler {

    public static final String CREATED = "org.alfresco.event.node.Created";
    public static final String UPDATED = "org.alfresco.event.node.Updated";
    public static final String DELETED = "org.alfresco.event.node.Deleted";
    public static final String CM_TITLE = "cm:title";

    private static final String INITIALIZATION_ERROR = "Failed to initialize ContentHandler: {}";

    private List<String> folderIdsList;

    @Autowired
    private AIClient aiClient;

    @Autowired
    private AlfrescoClient alfrescoClient;

    @Autowired
    private BlockingQueue<NodeEventTask> eventQueue;

    @Autowired
    private AtomicBoolean isInitialSyncComplete;

    @Value("${alfresco.ai.sync.aspect}")
    private String syncAspect;
    @Value("${alfresco.ai.sync.timeBeforeRestart:600000}")
    private int timeBeforeRestart;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "content-handler-restart-thread");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Adds a folder ID to the list of monitored folders.
     *
     * @param folder the folder ID to register
     */
    protected void addFolder(String folder) {
        folderIdsList.add(folder);
    }

    /**
     * Removes a folder ID from the list of monitored folders.
     *
     * @param folder the folder ID to deregister
     */
    protected void removeFolder(String folder) {
        folderIdsList.remove(folder);
    }

    /**
     * Initializes the handler by resolving the folder ID from the configured folder path.
     * Executed after dependency injection is complete.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing ContentHandler");
        try {
            folderIdsList = alfrescoClient.getSyncFolders(syncAspect);
            if (folderIdsList != null && !folderIdsList.isEmpty()) {
                log.info("[ContentHandler] Successfully initialized with folder IDs: {}", folderIdsList);
            }else{
                log.error("[ContentHandler] Initialization error, initialized with folder IDs: {}", folderIdsList);
                executorService.submit(this::retryFolderInitialization);
            }

        } catch (Exception e) {
            log.error(INITIALIZATION_ERROR, e.getMessage(), e);
            throw new IllegalStateException("Failed to resolve folder ID", e);
        }
    }

    /**
     * Periodically attempts to reinitialize the folder ID list if the initial attempt fails.
     * Restarts the application if initialization exceeds the configured time threshold.
     */
    private void retryFolderInitialization() {
        long startTime = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                folderIdsList = alfrescoClient.getSyncFolders(syncAspect);
                if (folderIdsList != null && !folderIdsList.isEmpty()) {
                    log.info("[ContentHandler] Successfully reinitialized with folder IDs: {}", folderIdsList);
                    break;
                }
                long elapsedTime = System.currentTimeMillis() - startTime;
                log.error("[ContentHandler] Reinitialization error after {}, initialized with folder IDs: {}", elapsedTime, folderIdsList);
                if (elapsedTime >= timeBeforeRestart) {
                    log.error("[ContentHandler] Initialization timed out after {} minutes", timeBeforeRestart/60000);
                    restartApp();
                    break;
                }
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[ContentHandler] Initialization interrupted. Exiting...");
                break;
            } catch (Exception e) {
                log.error(INITIALIZATION_ERROR, e.getMessage(), e);
                throw new IllegalStateException("Failed to resolve folder ID", e);
            }
        }
    }

    /**
     * Forces a system exit to allow a container-managed restart.
     */
    private void restartApp() {
        log.warn("[ContentHandler] Restarting application...");
        try {
            System.exit(1);
        }
        catch (Exception e) {
            log.error("[ContentHandler] Failed to restart application: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles repository events based on their type and current sync status.
     *
     * @param event The repository event to handle
     */
    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        log.debug("[ContentHandler] handleEvent");
        NodeResource nodeResource = extractNodeResource(event);
        String uuid = nodeResource.getId();

        log.info("[ContentHandler] Processing {} event for node ID: {}", event.getType(), uuid);

        try {
            if (isInitialSyncComplete.get()) {
                processEvent(event, nodeResource, uuid);

                int index = IntStream.range(0, nodeResource.getPrimaryHierarchy().size())
                        .filter(i -> folderIdsList.contains(nodeResource.getPrimaryHierarchy().get(i)))
                        .findFirst()
                        .orElse(-1);
                alfrescoClient.updateTime(folderIdsList.get(index), false);
            } else {
                log.warn("Initial sync pending. Queueing event for node ID: {}", uuid);
                eventQueue.add(new NodeEventTask(this, event));
            }
        } catch (Exception e) {
            log.error("Failed to process {} event for node ID {}: {}",
                    event.getType(), uuid, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Specifies the repository event types this handler is interested in.
     * Combines creation, update and delate event types.
     *
     * @return a set of {@link EventType} enums the handler can process
     */
    @Override
    public Set<EventType> getHandledEventTypes() {
        log.debug("[ContentHandler] start getHandledEventTypes");
        Set<EventType> handledEventTypes = Stream.of(
                        OnNodeCreatedEventHandler.super.getHandledEventTypes(),
                        OnNodeUpdatedEventHandler.super.getHandledEventTypes(),
                        OnNodeDeletedEventHandler.super.getHandledEventTypes()
                )
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        log.debug("[ContentHandler] end getHandledEventTypes");
        log.debug("Handling event types: {}", handledEventTypes);
        return handledEventTypes;
    }

    /**
     * Provides a composite filter that ensures only file-based events
     * within monitored folders are processed.
     *
     * @return a filter predicate applied to each incoming event
     */
    @Override
    public EventFilter getEventFilter() {
        log.debug("[ContentHandler] getEventFilter");
        EventFilter mainFilter = IsFileFilter.get() // Make sure it's a file and not inner folder without content
                .and(
                    Optional.ofNullable(folderIdsList)
                        .filter(ids -> !ids.isEmpty())
                        .map(ParentFolderFilter::of)
                        .orElseThrow(() ->
                                new IllegalStateException("Folder IDs not initialized or list is empty"))
                );
        return event -> {
            boolean test = mainFilter.test(event);
            log.debug("[ContentHandler] Event filter testResult: {},  Event received: {}", test, event);
          return test;
        };
    }

    /**
     * Processes a repository event based on its type.
     *
     * @param event the event to process
     * @param nodeResource the node resource extracted from the event
     * @param uuid the node’s unique identifier
     * @throws IOException if the AI service interaction fails
     */
    private void processEvent(RepoEvent<DataAttributes<Resource>> event, NodeResource nodeResource, String uuid)
            throws IOException {
        switch (event.getType()) {
            case CREATED:
                alfrescoClient.processDocument(uuid, getSyncFolderId(nodeResource), nodeResource.getName());
                break;
            case UPDATED:
                handleUpdateEvent(event, uuid, nodeResource);
                break;
            case DELETED:
                handleDeleteEvent(uuid);
                break;
            default:
                log.warn("Unhandled event type: {} for node ID: {}", event.getType(), uuid);
        }
    }

    /**
     * Resolves the ID of the first known sync folder in the node’s hierarchy.
     *
     * @param nodeResource the node whose folder ancestry is examined
     * @return the matching folder ID or an empty string
     */
    public String getSyncFolderId(NodeResource nodeResource) {
        return  nodeResource.getPrimaryHierarchy().stream()
                .filter(folderIdsList::contains)
                .findFirst().orElse("");
    }

    /**
     * Handles update events by determining whether any relevant attributes (name, content, or title) have changed.
     * If so, triggers reprocessing of the document.
     *
     * @param event the event to process
     * @param uuid the node’s unique identifier
     * @param nodeResource the node resource extracted from the event
     * @throws IOException if processing via AlfrescoClient fails
     */
    private void handleUpdateEvent(RepoEvent<DataAttributes<Resource>> event, String uuid, NodeResource nodeResource) throws IOException {
        NodeResource before = (NodeResource) event.getData().getResourceBefore();
        NodeResource after  = (NodeResource) event.getData().getResource();
        boolean nameChanged = (before != null) && !Objects.equals(before.getContent(), after.getContent());
        boolean contentChanged = (before != null) && !Objects.equals(before.getContent(), after.getContent());
        boolean titleChanged = (before != null) &&
                !Objects.equals(before.getProperties().get(CM_TITLE), after.getProperties().get(CM_TITLE));
        log.debug("[Node update event] nameChange = {}, contentChange = {}, titleChange = {}",
                nameChanged, contentChanged, titleChanged);
        if (nameChanged || contentChanged || titleChanged) {
            alfrescoClient.processDocument(uuid, getSyncFolderId(nodeResource), nodeResource.getName());
        } else {
            log.info("Skipping update for node ID {} ({}): content unchanged",
                    uuid, nodeResource.getName());
        }
    }

    /**
     * Handles a deletion event by forwarding the deletion request to the AI service.
     *
     * @param uuid the unique identifier of the deleted node
     * @throws IOException if the deletion request fails
     */
    private void handleDeleteEvent(String uuid) throws IOException {
        log.info("Processing deletion for node ID: {}", uuid);
        String response = aiClient.deleteDocument(uuid);
        log.info("Deletion completed for node ID {}: {}", uuid, response);
    }

    /**
     * Extracts and validates the NodeResource from an event.
     *
     * @param event the repository event
     * @return the associated {@link NodeResource}
     */
    private NodeResource extractNodeResource(RepoEvent<DataAttributes<Resource>> event) {
        return Optional.ofNullable(event)
                .map(RepoEvent::getData)
                .map(DataAttributes::getResource)
                .filter(resource -> resource instanceof NodeResource)
                .map(resource -> (NodeResource) resource)
                .orElseThrow(() -> new IllegalArgumentException("Invalid event resource type"));
    }
}