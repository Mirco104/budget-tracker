package io.github.mirco104.budget.common;

/** 409, e.g. a name that is already taken or a resource that is still in use. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
