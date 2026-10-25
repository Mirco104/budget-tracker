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

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // "group" is an SQL keyword; null means "ohne Gruppe"
    @Enumerated(EnumType.STRING)
    @Column(name = "category_group", length = 20)
    private CategoryGroup group;

    @Column(nullable = false)
    private int sortOrder;

    protected Category() {
    }

    public Category(String name, CategoryGroup group, int sortOrder) {
        this.name = name;
        this.group = group;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CategoryGroup getGroup() {
        return group;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
