package pl.beone.ai.models.response;

import java.util.List;

/**
 * Represents the outcome of an AI-based document tagging operation.
 *
 * @param tags the list of all tags assigned to the document, including the main tag if applicable
 * @param mainTag the primary classification label selected by the model
 * @param publiclyAllowed indicates whether the content is suitable for public disclosure based on AI analysis
 * @param errorMsg an optional message describing any error that occurred during tagging
 */
public record TagAnalysisResponse(List<String> tags, String mainTag, boolean publiclyAllowed, String errorMsg) {}
