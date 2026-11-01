package io.github.mirco104.budget.category;

import jakarta.persistence.Column;
import io.github.mirco104.budget.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "name"}))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // R15: every category belongs to one user
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private int sortOrder;

    // Archived categories are missing in forms and planning but still count in past months (US10)
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean archived;

    protected Category() {
    }

    public Category(User owner, String name, int sortOrder) {
        this.owner = owner;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isArchived() {
        return archived;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}
