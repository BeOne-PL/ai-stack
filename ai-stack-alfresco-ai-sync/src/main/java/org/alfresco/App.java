package org.alfresco;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.core.model.NodeChildAssociation;
import org.alfresco.events.handler.TagContentHandler;
import org.alfresco.model.NodeEventTask;
import org.alfresco.service.AlfrescoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main application class for initializing synchronization and processing events.
 * It performs an initial synchronization for folders and processes queued events using a thread pool.
 */
@Slf4j
@SpringBootApplication
public class App implements CommandLineRunner {

    private static final long EXECUTOR_SHUTDOWN_TIMEOUT = 5L;

    @Value("${alfresco.ai.sync.parallel.threads}")
    private int parallelThreads;

    @Value("${alfresco.ai.customizations.pipeline.aspect:cm:generalclassifiable}")
    private String pipelineAspect;

    @Autowired
    private AtomicBoolean isInitialSyncComplete;

    @Autowired
    private BlockingQueue<NodeEventTask> eventQueue;

    @Autowired
    private AlfrescoClient alfrescoClient;

    public static void main(String... args) {
        SpringApplication.run(App.class, args);
    }

    /**
     * Runs the application logic upon startup, performing an initial synchronization
     * and then processing any queued events.
     *
     * @param args Command-line arguments
     */
    @Override
    public void run(String... args) {
        log.info("Ensure directory structure is initialized.");
        initializeDirectoryStructure();
        log.info("Starting initial sync process.");
        performInitialSync();
        isInitialSyncComplete.set(true);
        log.info("Finished initial sync process.");
        processQueuedEvents();
    }

    /**
     * Initializes the directory structure required for the Knowledge Base.
     */
    private void initializeDirectoryStructure() {
        alfrescoClient.setupInitialKnowledgeFolders();
    }

    /**
     * Performs the initial synchronization pass over designated folders.
     * Each document is processed and synchronization metadata is updated accordingly.
     * Also ensures that retryable documents are moved back into the pipeline for processing.
     */
    private void performInitialSync() {
        alfrescoClient.getFoldersToSync().forEach(folder -> {
            log.info("Starting initial synchronization for folder: {}", folder);
            var processedCount = new AtomicInteger(0);
            alfrescoClient.synchronizeDocuments(processedCount, folder);
            log.info("Initial synchronization for folder {} complete. Processed {} documents", folder, processedCount.get());
            alfrescoClient.updateTime(folder.id(), true);
        });

        alfrescoClient.getSyncFolders(pipelineAspect).forEach(file ->{
            log.info("Processing start folder documents for initialization.");
            List<NodeChildAssociation> documents = alfrescoClient.listFolderDocuments(file);
            for (NodeChildAssociation doc : documents) {
                try {
                    String documentId = doc.getId();
                    String documentName = doc.getName();
                    String timestampedDocumentName = alfrescoClient.appendMarkTimestamp(documentName);
                    // TODO: Consider moving to start folder again in case when doc is already in "retry" folder
                    alfrescoClient.moveDocument(
                            documentId,
                            alfrescoClient.getNodeId(
                                alfrescoClient.initialKnowledgePipelineRetryFolder
                            ).getNodeId(),
                            timestampedDocumentName);
                    alfrescoClient.updateNodeTitle(documentId, timestampedDocumentName);
                    log.info("Moved document ({}) to retry folder for tagging pipeline.", doc.getId());
                } catch (Exception e) {
                    log.error("Failed to move document ({}) for tag retrying.", doc.getId(), e);
                }
            }
        });
    }

    /**
     * Processes any events that were queued during the initial synchronization.
     * Events are processed using a fixed thread pool of the specified size.
     */
    private void processQueuedEvents() {
        if (eventQueue.isEmpty()) {
            log.info("No events to process in the queue.");
            return;
        }

        log.info("[App] Processing {} queued events with {} threads", eventQueue.size(), parallelThreads);
        var executor = Executors.newFixedThreadPool(parallelThreads);

        try {
            processEventQueue(executor);
        } finally {
            shutdownExecutor(executor);
        }
    }

    /**
     * Processes each event in the queue using the provided executor service.
     *
     * @param executor ExecutorService responsible for parallel event processing
     */
    private void processEventQueue(ExecutorService executor) {
        NodeEventTask event;
        while ((event = eventQueue.poll()) != null) {
            NodeEventTask finalEvent = event;
            executor.submit(() -> handleEvent(finalEvent));
        }
    }

    /**
     * Handles a single event using the ContentHandler. Logs errors if event processing fails.
     *
     * @param event RepoEvent to be processed
     */
    private void handleEvent(NodeEventTask event) {
        try {
            if (event.getHandler().getEventFilter().test(event.getEvent())) {
                event.getHandler().handleEvent(event.getEvent());
            }
        } catch (Exception e) {
            log.error("Failed to process event: {}", event, e);
        }
    }

    /**
     * Shuts down the executor service gracefully. If termination times out,
     * forces a shutdown and interrupts any remaining tasks.
     *
     * @param executor ExecutorService to shut down
     */
    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
