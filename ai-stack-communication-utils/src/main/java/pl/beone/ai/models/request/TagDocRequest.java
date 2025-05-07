package pl.beone.ai.models.request;

import java.util.List;

/**
 * Encapsulates the input required for tagging a document via the AI pipeline.
 *
 * @param fileName the name of the file to be analyzed
 * @param fileContent the full textual content of the file
 * @param candidateTags a predefined set of possible tags the model should consider
 */
public record TagDocRequest(String fileName, String fileContent, List<String> candidateTags) {}
