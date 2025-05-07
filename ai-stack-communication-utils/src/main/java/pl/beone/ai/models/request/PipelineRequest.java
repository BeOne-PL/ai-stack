package pl.beone.ai.models.request;

import java.util.List;

/**
 * Represents a request payload sent to the AI pipeline for processing.
 *
 * @param stream whether the response should be streamed incrementally
 * @param model the identifier of the AI model to be invoked
 * @param messages a sequence of input messages forming the conversation or context
 */
public record PipelineRequest(boolean stream, String model, List<PipelineMessage> messages) {}
