package org.alfresco.model;

/**
 * Represents a taggable folder within the Alfresco repository.
 *
 * @param id the unique identifier of the folder
 * @param name the human-readable name of the folder
 */
public record FolderTag(String id, String name) {}