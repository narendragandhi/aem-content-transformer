package com.example.aemtransformer.exception;

/**
 * Exception thrown when content transformation fails.
 */
public class TransformationException extends RuntimeException {

    private final String phase;

    public TransformationException(String message) {
        super(message);
        this.phase = null;
    }

    public TransformationException(String message, Throwable cause) {
        super(message, cause);
        this.phase = null;
    }

    public TransformationException(String message, String phase) {
        super(String.format("%s (Phase: %s)", message, phase));
        this.phase = phase;
    }

    public TransformationException(String message, String phase, Throwable cause) {
        super(String.format("%s (Phase: %s)", message, phase), cause);
        this.phase = phase;
    }

    public String getPhase() {
        return phase;
    }
}
