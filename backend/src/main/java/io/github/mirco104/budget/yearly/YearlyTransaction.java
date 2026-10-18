package io.github.mirco104.budget.yearly;

import io.github.mirco104.budget.category.Category;
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
import java.time.YearMonth;

/**
 * Yearly transaction, counted as amountPerYear / 12 in every month from validFrom to validTo (R1).
 * validFrom and validTo are always the first day of a month; validTo is inclusive and null means open-ended.
 */
@Entity
@Table(name = "yearly_transactions")
public class YearlyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPerYear;

    @Column(nullable = false, length = 100)
    private String description;

    private LocalDate dueDate;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    protected YearlyTransaction() {
    }

    public YearlyTransaction(BigDecimal amountPerYear, String description, LocalDate dueDate,
            YearMonth validFrom, YearMonth validTo, Category category) {
        update(amountPerYear, description, dueDate, validFrom, validTo, category);
    }

    public void update(BigDecimal amountPerYear, String description, LocalDate dueDate,
            YearMonth validFrom, YearMonth validTo, Category category) {
        this.amountPerYear = amountPerYear;
        this.description = description;
        this.dueDate = dueDate;
        this.validFrom = validFrom.atDay(1);
        this.validTo = validTo == null ? null : validTo.atDay(1);
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmountPerYear() {
        return amountPerYear;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public YearMonth getValidFrom() {
        return YearMonth.from(validFrom);
    }

    public YearMonth getValidTo() {
        return validTo == null ? null : YearMonth.from(validTo);
    }

    public Category getCategory() {
        return category;
    }
}
