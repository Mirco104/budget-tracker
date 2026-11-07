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

/** A source of a bank file ("C24:Ausgabenkonto") the user put on an account; the next import preselects it (R30). */
@Entity
@Table(name = "bank_account_sources")
public class BankAccountSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;

    @Column(name = "source_key", nullable = false, length = 120)
    private String sourceKey;

    protected BankAccountSource() {
    }

    public BankAccountSource(User owner, BankAccount account, String sourceKey) {
        this.owner = owner;
        this.account = account;
        this.sourceKey = sourceKey;
    }

    public BankAccount getAccount() {
        return account;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void moveTo(BankAccount account) {
        this.account = account;
    }
}
