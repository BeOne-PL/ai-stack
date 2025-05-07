package org.alfresco.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents the result of a folder lookup in the Alfresco repository.
 * <p>
 * Contains a flag indicating whether the folder exists,
 * and the corresponding nodeId if it was found.
 */
@Data
@AllArgsConstructor
public class NodeSearchResult {
    /**
     * Indicates whether the folder exists at the specified path.
     */
    private final boolean exists;

    /**
     * The nodeId of the folder if it exists; empty otherwise.
     */
    private final String nodeId;

    /**
     * Constructs a result representing a non-existent folder.
     */
    public NodeSearchResult() {
        this.exists = false;
        this.nodeId = "";
    }
}
