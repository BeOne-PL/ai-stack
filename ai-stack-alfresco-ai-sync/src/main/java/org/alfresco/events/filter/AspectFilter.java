package org.alfresco.events.filter;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.event.sdk.handling.filter.AbstractEventFilter;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;

import java.util.Objects;

/**
 * Event filter that accepts events in which a specified aspect has just been added to a node.
 * Ignores updates where the aspect was already present before the event occurred.
 */
@Slf4j
public class AspectFilter extends AbstractEventFilter {

    private final String acceptedAspect;

    /**
     * Constructs an AspectFilter for a specific aspect name.
     *
     * @param acceptedAspect the aspect to filter for, must not be null
     */
    private AspectFilter(final String acceptedAspect) {
        this.acceptedAspect = Objects.requireNonNull(acceptedAspect);
    }

    /**
     * Factory method for creating an {@link AspectFilter} instance.
     *
     * @param acceptedAspect the aspect name to monitor
     * @return a configured AspectFilter instance
     */
    public static AspectFilter of(final String acceptedAspect) {
        return new AspectFilter(acceptedAspect);
    }

    /**
     * Evaluates whether the specified event should be accepted.
     * An event is accepted if the target node has gained the specified aspect,
     * in other words it did not exist before the event but is present afterward.
     *
     * @param event the repository event to evaluate
     * @return true if the aspect was newly added and false otherwise
     */
    @Override
    public boolean test(RepoEvent<DataAttributes<Resource>> event) {
        log.debug("Checking filter for aspect {} and event {}", acceptedAspect, event);
        final NodeResource nodeResourceBefore = (NodeResource) event.getData().getResourceBefore();
        final NodeResource nodeResource = (NodeResource) event.getData().getResource();
        final boolean aspectExistedBefore = (nodeResourceBefore != null && nodeResourceBefore.getAspectNames().contains(acceptedAspect));
        final boolean aspectExists = (nodeResource != null && nodeResource.getAspectNames().contains(acceptedAspect));
        log.debug("The aspect {} has been added? {}", acceptedAspect, aspectExists && !aspectExistedBefore);
        return aspectExists && !aspectExistedBefore;
    }

}