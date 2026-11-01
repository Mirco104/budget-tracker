package io.github.mirco104.budget.invitation;

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

/** An invitation link an admin passes on (US15). Usable once until expiresAt. */
@Entity
@Table(name = "invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Random, at least 128 bit
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    protected Invitation() {
    }

    public Invitation(String token, User createdBy, Instant expiresAt) {
        this.token = token;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
