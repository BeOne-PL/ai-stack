package org.alfresco.events.handler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.ai.AIClient;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler;
import org.alfresco.events.filter.AspectFilter;
import org.alfresco.repo.event.v1.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.alfresco.events.handler.ContentHandler.*;

/**
 * Handler responsible for managing folder-level events that affect AI document synchronization.
 * Responds to folder creation and updates by modifying the set of synchronized tagging folders.
 */
@Slf4j
@Component
public class TagFolderHandler implements OnNodeCreatedEventHandler, OnNodeUpdatedEventHandler {

    @Autowired
    TagContentHandler tagContentHandler;

    @Autowired
    private AIClient aiClient;

    @Value("${alfresco.ai.customizations.pipeline.aspect:cm:generalclassifiable}")
    private String pipelineAspect;

    /**
     * Invoked after bean construction to signal initialization.
     */
    @PostConstruct
    public void init() {
        log.info("TagFolderHandler bean has been initialized.");
    }

    /**
     * Handles incoming repository events for folders involved in tagging operations.
     * On creation/update, adds the folder to sync list. On deletion, removes documents from sync list.
     *
     * @param event the repository event representing a folder operation
     */
    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        log.debug("[TagFolderHandler] handleEvent");
        NodeResource nodeResource = (NodeResource) event.getData().getResource();
        String uuid = nodeResource.getId();
        switch (event.getType()) {
            case CREATED:
            case UPDATED:
                log.info("A new folder has been added for tagging: {}", uuid);
                tagContentHandler.addFolder(uuid);
                break;
            case DELETED:
                log.info("A folder has been removed from tagging: {}", uuid);
                tagContentHandler.removeFolder(uuid);
                break;
            default:
                log.warn("Unhandled event type: {} for node ID: {}", event.getType(), uuid);
        }
    }

    /**
     * Specifies which repository event types this handler will process.
     *
     * @return a set of event types including creation and update
     */
    @Override
    public Set<EventType> getHandledEventTypes() {
        log.debug("[TagFolderHandler] start getHandledEventType");
        Set<EventType> handledEventTypes = Stream.of(
                        OnNodeCreatedEventHandler.super.getHandledEventTypes(),
                        OnNodeUpdatedEventHandler.super.getHandledEventTypes()
                )
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        log.debug("[TagFolderHandler] end getHandledEventType");

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
        log.debug("[TagFolderHandler] getEventFilter");
        EventFilter mainFilter = AspectFilter.of(pipelineAspect);
        return event -> {
            boolean test = mainFilter.test(event);
            log.info("[TagFolderHandler] Event filter testResult: {},  Event received: {}", test, event);
            return test;
        };
    }


}
