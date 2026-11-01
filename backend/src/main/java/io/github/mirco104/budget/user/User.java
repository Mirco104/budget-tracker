package io.github.mirco104.budget.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A person who signs in (US14). Owns their categories, transactions and series (R15). */
// "user" is reserved in PostgreSQL
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stored in lower case, so the sign-in ignores case
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    // BCrypt, never the password
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private Instant createdAt;

    // Last successful sign-in, for the user overview (US25)
    private Instant lastLoginAt;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName, Role role, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = createdAt;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Expects the e-mail in lower case, like {@link UserService}. */
    public void changeEmail(String email) {
        this.email = email;
    }

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
