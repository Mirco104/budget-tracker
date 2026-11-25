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

    // R35: how closely the user follows the actuals, 1 (plan only) to 4 (bookings with categories); a new user starts
    // with stage 2, the one the setup wizard recommends
    @Column(nullable = false)
    private int istStage = 2;

    // US40: the setup wizard was finished or put off; until then the app opens it
    @Column(nullable = false)
    private boolean setupDone;

    // Decision 90: automatic entries (P6) start on this day; set when leaving stage 1, null = from "Gilt ab"
    private java.time.LocalDate autoEntriesFrom;

    // US44: random WebAuthn user handle (base64url), set with the first passkey; never the e-mail or the id
    @Column(unique = true, length = 100)
    private String passkeyUserHandle;

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

    /** R35: expects 1 to 4, checked by the request. */
    public void changeIstStage(int istStage) {
        this.istStage = istStage;
    }

    public void changeSetupDone(boolean setupDone) {
        this.setupDone = setupDone;
    }

    public int getIstStage() {
        return istStage;
    }

    public boolean isSetupDone() {
        return setupDone;
    }

    public java.time.LocalDate getAutoEntriesFrom() {
        return autoEntriesFrom;
    }

    public void changeAutoEntriesFrom(java.time.LocalDate autoEntriesFrom) {
        this.autoEntriesFrom = autoEntriesFrom;
    }

    public String getPasskeyUserHandle() {
        return passkeyUserHandle;
    }

    public void changePasskeyUserHandle(String passkeyUserHandle) {
        this.passkeyUserHandle = passkeyUserHandle;
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
