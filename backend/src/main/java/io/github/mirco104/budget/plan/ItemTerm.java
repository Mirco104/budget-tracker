package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A handled term of a planned item (section 19.3): linked to a booking, skipped by the user, or (no booking, not
 * skipped) an automatic entry the user deleted. Terms without a row are calculated (P5).
 */
@Entity
@Table(name = "item_terms")
public class ItemTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private UUID itemKey;

    // The planned date, before the working-day shift
    @Column(nullable = false)
    private LocalDate termDate;

    // The database sets it to null when the booking is deleted
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(nullable = false)
    private boolean skipped;

    protected ItemTerm() {
    }

    public ItemTerm(User owner, UUID itemKey, LocalDate termDate) {
        this.owner = owner;
        this.itemKey = itemKey;
        this.termDate = termDate;
    }

    public void link(Transaction transaction) {
        this.transaction = transaction;
        this.skipped = false;
    }

    public void skip() {
        this.transaction = null;
        this.skipped = true;
    }

    public Long getId() {
        return id;
    }

    public UUID getItemKey() {
        return itemKey;
    }

    public LocalDate getTermDate() {
        return termDate;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public boolean isSkipped() {
        return skipped;
    }
}
