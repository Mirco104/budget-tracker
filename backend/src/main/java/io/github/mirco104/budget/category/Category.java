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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "parent_id", "name"}))
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

    // A key from CategoryIcons (US28)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'tag'")
    private String icon;

    // R32: one level; a parent has no parent itself
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    protected Category() {
    }

    public Category(User owner, String name, int sortOrder, String icon) {
        this.owner = owner;
        this.name = name;
        this.sortOrder = sortOrder;
        this.icon = icon;
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

    public String getIcon() {
        return icon;
    }

    public void changeIcon(String icon) {
        this.icon = icon;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    /** R32: the parent category, null for a top-level category. */
    public Category getParent() {
        return parent;
    }

    public void moveTo(Category parent) {
        this.parent = parent;
    }

    /** R32: every calculation treats a subcategory like its parent (for now, decision 61). */
    public Category getCalculationCategory() {
        return parent == null ? this : parent;
    }

    /** R32: "Parent › Child", for a top-level category its name. */
    public String getPath() {
        return parent == null ? name : parent.getName() + " › " + name;
    }
}
