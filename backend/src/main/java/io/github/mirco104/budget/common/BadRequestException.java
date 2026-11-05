package io.github.mirco104.budget.common;

/** A request is invalid as a whole, e.g. parameters that exclude each other. Mapped to 400 with the message. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
