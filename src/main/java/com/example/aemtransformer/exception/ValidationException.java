package com.example.aemtransformer.exception;

/**
 * Exception thrown when input validation fails.
 */
public class ValidationException extends RuntimeException {

    private final String field;
    private final Object invalidValue;

    public ValidationException(String message) {
        super(message);
        this.field = null;
        this.invalidValue = null;
    }

    public ValidationException(String message, String field) {
        super(String.format("%s (Field: %s)", message, field));
        this.field = field;
        this.invalidValue = null;
    }

    public ValidationException(String message, String field, Object invalidValue) {
        super(String.format("%s (Field: %s, Value: %s)", message, field, invalidValue));
        this.field = field;
        this.invalidValue = invalidValue;
    }

    public String getField() {
        return field;
    }

    public Object getInvalidValue() {
        return invalidValue;
    }
}
