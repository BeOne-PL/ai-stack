package org.alfresco.events.handler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.ai.AIClient;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.event.sdk.handling.handler.OnNodeDeletedEventHandler;
import org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler;
import org.alfresco.events.filter.AspectFilter;
import org.alfresco.repo.event.v1.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.alfresco.events.handler.ContentHandler.*;

/**
 * Handler responsible for managing folder-level events that affect AI document synchronization.
 * Responds to folder creation, updates, and deletions, and dynamically adjusts synchronization scope.
 */
@Slf4j
@Component
public class FolderHandler implements OnNodeCreatedEventHandler, OnNodeUpdatedEventHandler, OnNodeDeletedEventHandler {

    @Autowired
    ContentHandler contentHandler;

    @Autowired
    private AIClient aiClient;

    @Value("${alfresco.ai.sync.aspect}")
    private String syncAspect;

    /**
     * Invoked after bean construction to signal initialization.
     */
    @PostConstruct
    public void init() {
        log.info("FolderHandler bean has been initialized.");
    }

    /**
     * Handles incoming repository events affecting folders.
     * On creation/update, adds folder to sync list. On deletion, removes and purges associated documents.
     *
     * @param event the repository event representing a folder operation
     */
    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        log.debug("[FolderHandler] handleEvent");
        NodeResource nodeResource = (NodeResource) event.getData().getResource();
        String uuid = nodeResource.getId();
        switch (event.getType()) {
            case CREATED:
            case UPDATED:
                log.info("A new folder has been added for synchronization: {}", uuid);
                contentHandler.addFolder(uuid);
                break;
            case DELETED:
                log.info("A folder has been removed from synchronization: {}", uuid);
                contentHandler.removeFolder(uuid);
                try {
                    String response = aiClient.deleteDocumentsFromFolder(uuid);
                    log.info("Deletion completed for folder ID {}: {}", uuid, response);
                } catch (IOException e) {
                    log.error("Failed to remove documents for folder: {}", uuid);
                }
                break;
            default:
                log.warn("Unhandled event type: {} for node ID: {}", event.getType(), uuid);
        }
    }

    /**
     * Specifies which repository event types this handler will process.
     *
     * @return a set of event types including creation, update, and deletion
     */
    @Override
    public Set<EventType> getHandledEventTypes() {
        log.debug("[FolderHandler] start getHandledEventTypes");
        Set<EventType> handledEventTypes = Stream.of(
                        OnNodeCreatedEventHandler.super.getHandledEventTypes(),
                        OnNodeUpdatedEventHandler.super.getHandledEventTypes(),
                        OnNodeDeletedEventHandler.super.getHandledEventTypes()
                )
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        log.debug("[FolderHandler] end getHandledEventTypes");
        log.debug("Handling event types: {}", handledEventTypes);
        return handledEventTypes;
    }

    /**
     * Filters events to only those involving folders tagged with a specific aspect.
     *
     * @return the composite event filter
     */
    @Override
    public EventFilter getEventFilter() {
        log.debug("[FolderHandler] getEventFilter");
        EventFilter mainFilter = AspectFilter.of(syncAspect);
        return event -> {
            boolean test = mainFilter.test(event);
            log.info("[FolderHandler] Event filter testResult: {},  Event received: {}", test, event);
            return test;
        };
    }


}
