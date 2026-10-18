package io.github.mirco104.budget.category;

import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Target of a category from validFrom onward (R3). validFrom is always the first day of a month.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "valid_from"}))
public class CategoryTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(precision = 12, scale = 2)
    private BigDecimal targetAmount;

    // Fraction of income, 0..1
    @Column(precision = 5, scale = 4)
    private BigDecimal targetPercent;

    protected CategoryTarget() {
    }

    public CategoryTarget(Category category, YearMonth validFrom) {
        this.category = category;
        this.validFrom = validFrom.atDay(1);
    }

    public void update(BigDecimal targetAmount, BigDecimal targetPercent) {
        this.targetAmount = targetAmount;
        this.targetPercent = targetPercent;
    }

    public TargetEntry toEntry() {
        return new TargetEntry(category.getId(), YearMonth.from(validFrom), targetAmount, targetPercent);
    }
}
