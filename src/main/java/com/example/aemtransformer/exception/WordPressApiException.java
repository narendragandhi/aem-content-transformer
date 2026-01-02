package com.example.aemtransformer.exception;

/**
 * Exception thrown when WordPress API operations fail.
 */
public class WordPressApiException extends RuntimeException {

    private final int statusCode;
    private final String url;

    public WordPressApiException(String message) {
        super(message);
        this.statusCode = 0;
        this.url = null;
    }

    public WordPressApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.url = null;
    }

    public WordPressApiException(String message, String url, int statusCode) {
        super(String.format("%s (URL: %s, Status: %d)", message, url, statusCode));
        this.statusCode = statusCode;
        this.url = url;
    }

    public WordPressApiException(String message, String url, int statusCode, Throwable cause) {
        super(String.format("%s (URL: %s, Status: %d)", message, url, statusCode), cause);
        this.statusCode = statusCode;
        this.url = url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getUrl() {
        return url;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }
}
