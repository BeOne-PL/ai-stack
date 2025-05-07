package org.alfresco.model;

import org.alfresco.event.sdk.handling.handler.NodeEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;

/**
 * Encapsulates a repository event together with its associated event handler.
 * Used to defer execution of events until initial synchronization is complete.
 *
 * @param handler the {@link NodeEventHandler} responsible for processing the event
 * @param event the actual repository {@link RepoEvent} to be handled
 */
public record NodeEventTask(NodeEventHandler handler,
                            RepoEvent<DataAttributes<Resource>> event) {
    /**
     * Returns the handler associated with this task.
     *
     * @return the event handler
     */
    public NodeEventHandler getHandler() {
        return handler;
    }

    /**
     * Returns the repository event associated with this task.
     *
     * @return the queued repository event
     */
    public RepoEvent<DataAttributes<Resource>> getEvent() {
        return event;
    }
}
