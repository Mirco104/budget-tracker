package io.github.mirco104.budget.user;

/**
 * ADMIN may also create invitations (US15) and change roles (US25). SYSTEM_ADMIN is the one admin created on the
 * first start; nobody can demote it.
 */
public enum Role {
    USER,
    ADMIN,
    SYSTEM_ADMIN;

    public boolean isAdmin() {
        return this != USER;
    }
}
