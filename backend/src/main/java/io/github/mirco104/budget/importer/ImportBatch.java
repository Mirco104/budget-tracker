package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** One import of a bank file (US33); undoing it deletes its transactions (US34). */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // R15: every import belongs to one user
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankProfile profile;

    @Column(nullable = false)
    private Instant createdAt;

    // Period of the transactions it created
    @Column(nullable = false)
    private LocalDate firstDate;

    @Column(nullable = false)
    private LocalDate lastDate;

    @Column(name = "row_count", nullable = false)
    private int count;

    protected ImportBatch() {
    }

    public ImportBatch(User owner, BankProfile profile, Instant createdAt, LocalDate firstDate, LocalDate lastDate,
            int count) {
        this.owner = owner;
        this.profile = profile;
        this.createdAt = createdAt;
        this.firstDate = firstDate;
        this.lastDate = lastDate;
        this.count = count;
    }

    public Long getId() {
        return id;
    }

    public BankProfile getProfile() {
        return profile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDate getFirstDate() {
        return firstDate;
    }

    public LocalDate getLastDate() {
        return lastDate;
    }

    public int getCount() {
        return count;
    }
}
