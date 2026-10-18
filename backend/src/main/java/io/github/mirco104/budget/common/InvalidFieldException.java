package io.github.mirco104.budget.common;

/**
 * A request field is syntactically valid but refers to something that does not exist.
 * Mapped to 400 with the same field error shape as Bean Validation.
 */
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
