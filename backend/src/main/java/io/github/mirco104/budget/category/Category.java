package io.github.mirco104.budget.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"name", "type"}))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryType type;

    // "group" is an SQL keyword
    @Enumerated(EnumType.STRING)
    @Column(name = "category_group", length = 20)
    private CategoryGroup group;

    @Column(nullable = false)
    private int sortOrder;

    @Column(precision = 12, scale = 2)
    private BigDecimal targetAmount;

    // Fraction of income, 0..1
    @Column(precision = 5, scale = 4)
    private BigDecimal targetPercent;

    // "system" is an SQL keyword
    @Column(name = "system_category", nullable = false)
    private boolean system;

    protected Category() {
    }

    public Category(String name, CategoryType type, CategoryGroup group, int sortOrder, boolean system) {
        this.name = name;
        this.type = type;
        this.group = group;
        this.sortOrder = sortOrder;
        this.system = system;
    }

    public void updateTarget(BigDecimal targetAmount, BigDecimal targetPercent) {
        this.targetAmount = targetAmount;
        this.targetPercent = targetPercent;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CategoryType getType() {
        return type;
    }

    public CategoryGroup getGroup() {
        return group;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public BigDecimal getTargetPercent() {
        return targetPercent;
    }

    public boolean isSystem() {
        return system;
    }
}
