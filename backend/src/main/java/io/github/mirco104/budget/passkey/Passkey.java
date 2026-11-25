package io.github.mirco104.budget.passkey;

import io.github.mirco104.budget.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A passkey of a user (US44): the public key and the attestation object of the registration, base64url. The private
 * key never leaves the device, so a copy of the database opens no account.
 */
@Entity
@Table(name = "passkeys")
public class Passkey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(nullable = false, unique = true, length = 1400)
    private String credentialId;

    // COSE key
    @Column(nullable = false, length = 2000)
    private String publicKey;

    // Spring Security reads the key details from it on every sign-in
    @Column(nullable = false, length = 4000)
    private String attestationObject;

    @Column(nullable = false)
    private long signatureCount;

    @Column(nullable = false)
    private boolean uvInitialized;

    @Column(nullable = false)
    private boolean backupEligible;

    @Column(nullable = false)
    private boolean backupState;

    // The transports the browser named, comma separated (e.g. "hybrid,internal")
    @Column(nullable = false, length = 200)
    private String transports;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastUsedAt;

    protected Passkey() {
    }

    public Passkey(
            User owner, String credentialId, String publicKey, String attestationObject, long signatureCount,
            boolean uvInitialized, boolean backupEligible, boolean backupState, String transports, String label,
            Instant createdAt) {
        this.owner = owner;
        this.credentialId = credentialId;
        this.publicKey = publicKey;
        this.attestationObject = attestationObject;
        this.signatureCount = signatureCount;
        this.uvInitialized = uvInitialized;
        this.backupEligible = backupEligible;
        this.backupState = backupState;
        this.transports = transports;
        this.label = label;
        this.createdAt = createdAt;
    }

    /** After a sign-in: the new signature counter and when it was used. */
    public void recordUse(long signatureCount, boolean backupState, Instant at) {
        this.signatureCount = signatureCount;
        this.backupState = backupState;
        this.lastUsedAt = at;
    }

    public void rename(String label) {
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getAttestationObject() {
        return attestationObject;
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public boolean isUvInitialized() {
        return uvInitialized;
    }

    public boolean isBackupEligible() {
        return backupEligible;
    }

    public boolean isBackupState() {
        return backupState;
    }

    public String getTransports() {
        return transports;
    }

    public String getLabel() {
        return label;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
