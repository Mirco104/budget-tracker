package io.github.mirco104.budget.common;

/** 410, e.g. an invitation that expired or was already used (US15). */
public class GoneException extends RuntimeException {

    public GoneException(String message) {
        super(message);
    }
}
