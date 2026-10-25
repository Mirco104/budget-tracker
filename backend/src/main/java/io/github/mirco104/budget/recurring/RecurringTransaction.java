package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.category.Category;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** One series of a recurring transaction (R12). Its occurrences are calculated, not stored. */
@Entity
@Table(name = "recurring_transactions")
public class RecurringTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Shared by all parts of a series after "Dieser und alle folgenden" (R13)
    @Column(nullable = false)
    private UUID seriesId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // "interval" is an SQL keyword
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_interval", nullable = false, length = 20)
    private RecurringInterval interval;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    protected RecurringTransaction() {
    }

    public RecurringTransaction(
            UUID seriesId, BigDecimal amount, String description, Category category, RecurringInterval interval,
            LocalDate startDate, LocalDate endDate) {
        this.seriesId = seriesId;
        update(amount, description, category, interval, startDate, endDate);
    }

    public void update(
            BigDecimal amount, String description, Category category, RecurringInterval interval,
            LocalDate startDate, LocalDate endDate) {
        this.amount = amount;
        this.description = description;
        this.category = category;
        this.interval = interval;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void endOn(LocalDate endDate) {
        this.endDate = endDate;
    }

    public RecurrenceSchedule schedule(Set<LocalDate> exceptions) {
        return new RecurrenceSchedule(startDate, endDate, interval, exceptions);
    }

    public Long getId() {
        return id;
    }

    public UUID getSeriesId() {
        return seriesId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public RecurringInterval getInterval() {
        return interval;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
