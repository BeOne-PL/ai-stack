package pl.beone.ai.models.request;

/**
 * Represents a single message sent to the pipeline model during inference.
 *
 * @param role the sender role
 * @param content the message content to be processed by the pipeline
 */
public record PipelineMessage(String role, String content) {}
