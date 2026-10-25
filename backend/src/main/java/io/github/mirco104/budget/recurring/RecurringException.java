package io.github.mirco104.budget.recurring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/** A suspended occurrence of a series, created by "Nur dieser Eintrag" (R13). */
@Entity
@Table(name = "recurring_exceptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"recurring_id", "occurrence_date"}))
public class RecurringException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_id", nullable = false)
    private RecurringTransaction recurring;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    protected RecurringException() {
    }

    public RecurringException(RecurringTransaction recurring, LocalDate occurrenceDate) {
        this.recurring = recurring;
        this.occurrenceDate = occurrenceDate;
    }

    public RecurringTransaction getRecurring() {
        return recurring;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }
}
