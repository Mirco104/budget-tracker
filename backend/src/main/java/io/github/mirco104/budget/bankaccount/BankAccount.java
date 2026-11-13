package io.github.mirco104.budget.bankaccount;

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

/** A bank account of a user (R26). Every transaction and series is booked on one. */
@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    /** Name of the account every user starts with (decision 39). */
    public static final String DEFAULT_NAME = "Girokonto";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // R15: every account belongs to one user
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 50)
    private String name;

    // Normalized by Iban.normalize, null without IBAN
    @Column(length = 34)
    private String iban;

    @Column(name = "default_account", nullable = false)
    private boolean defaultAccount;

    // Concept 3.4: only Alltag counts in income and expenses (P1)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 8)
    private AccountRole role = AccountRole.EVERYDAY;

    @Column(nullable = false)
    private boolean archived;

    protected BankAccount() {
    }

    public BankAccount(User owner, String name, String iban, boolean defaultAccount) {
        this.owner = owner;
        this.name = name;
        this.iban = iban;
        this.defaultAccount = defaultAccount;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getIban() {
        return iban;
    }

    public boolean isDefaultAccount() {
        return defaultAccount;
    }

    public boolean isArchived() {
        return archived;
    }

    public void update(String name, String iban, boolean archived) {
        this.name = name;
        this.iban = iban;
        this.archived = archived;
    }

    public void setDefaultAccount(boolean defaultAccount) {
        this.defaultAccount = defaultAccount;
    }

    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }
}
