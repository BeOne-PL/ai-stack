package org.alfresco.model;

/**
 * Custom runtime exception used to signal AI processing or synchronization failures
 * within the Alfresco integration stack.
 */
public class AIStackException extends RuntimeException {

    /**
     * Constructs a new AIStackException with the specified detail message.
     *
     * @param message the error message to be logged or propagated
     */
    public AIStackException(String message) {
        super(message);
    }

    /**
     * Constructs a new AIStackException with the specified detail message and cause.
     *
     * @param message the error message to be logged or propagated
     * @param e the underlying exception that triggered this failure
     */
    public AIStackException(String message, Exception e) {
        super(message, e);
    }
}