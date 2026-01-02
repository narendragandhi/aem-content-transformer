package com.example.aemtransformer.exception;

/**
 * Exception thrown when content parsing fails.
 */
public class ContentParsingException extends RuntimeException {

    private final String contentType;

    public ContentParsingException(String message) {
        super(message);
        this.contentType = null;
    }

    public ContentParsingException(String message, Throwable cause) {
        super(message, cause);
        this.contentType = null;
    }

    public ContentParsingException(String message, String contentType) {
        super(String.format("%s (Content type: %s)", message, contentType));
        this.contentType = contentType;
    }

    public ContentParsingException(String message, String contentType, Throwable cause) {
        super(String.format("%s (Content type: %s)", message, contentType), cause);
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
