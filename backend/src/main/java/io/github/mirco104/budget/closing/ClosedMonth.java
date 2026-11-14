package io.github.mirco104.budget.closing;

import io.github.mirco104.budget.goal.SavingGoal;
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
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * P10: a closed month with what was left of it; with a saving goal the amount counts to the goal's balance, without
 * one the month is closed without a goal ("Nicht zuordnen"). It is no booking.
 */
@Entity
@Table(name = "month_closings")
public class ClosedMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // The first day of the month
    @Column(name = "closing_month", nullable = false)
    private LocalDate month;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saving_goal_id")
    private SavingGoal savingGoal;

    protected ClosedMonth() {
    }

    public ClosedMonth(User owner, LocalDate month, BigDecimal amount, SavingGoal savingGoal) {
        this.owner = owner;
        this.month = month;
        this.amount = amount;
        this.savingGoal = savingGoal;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getMonth() {
        return month;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public SavingGoal getSavingGoal() {
        return savingGoal;
    }
}
