package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.user.User;
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

/** R33, decision 63: a bank category of one bank, mapped by the user to an own category. */
@Entity
@Table(name = "bank_category_mappings")
public class BankCategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankProfile profile;

    @Column(name = "bank_category", nullable = false, length = 100)
    private String bankCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    protected BankCategoryMapping() {
    }

    public BankCategoryMapping(User owner, BankProfile profile, String bankCategory, Category category) {
        this.owner = owner;
        this.profile = profile;
        this.bankCategory = bankCategory;
        this.category = category;
    }

    public String getBankCategory() {
        return bankCategory;
    }

    public Category getCategory() {
        return category;
    }

    public void mapTo(Category category) {
        this.category = category;
    }
}
