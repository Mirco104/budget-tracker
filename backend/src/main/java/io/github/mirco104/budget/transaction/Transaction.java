package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.bankaccount.BankAccount;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.importer.ImportBatch;
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

// "transaction" is an SQL keyword
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    private String description;

    // "date" is an SQL keyword
    @Column(name = "booking_date", nullable = false)
    private LocalDate date;

    // R27: null exactly for a transfer between own accounts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private boolean transfer;

    // R30: set for a transaction from an import; the fingerprint keeps a row from being imported twice
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @Column(length = 64)
    private String fingerprint;

    // R29: the counterparty and the bank's category from the import, for the category suggestion
    @Column(length = 100)
    private String counterparty;

    @Column(name = "bank_category", length = 100)
    private String bankCategory;

    // US34: the import that made this stored transaction the counterpart of a transfer (R27), and its category before
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_batch_id")
    private ImportBatch transferBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_before_transfer_id")
    private Category categoryBeforeTransfer;

    // R26: every transaction is booked on one bank account of its owner
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;

    // R15: every transaction belongs to one user
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    protected Transaction() {
    }

    public Transaction(
            User owner, BigDecimal amount, String description, LocalDate date, Category category, BankAccount account) {
        this.owner = owner;
        update(amount, description, date, category, account);
    }

    public void update(BigDecimal amount, String description, LocalDate date, Category category, BankAccount account) {
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.category = category;
        this.transfer = category == null;
        this.account = account;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDate() {
        return date;
    }

    public Category getCategory() {
        return category;
    }

    /** R30: remembers the import, the fingerprint of the row, the counterparty and the bank's category. */
    public void markImported(ImportBatch importBatch, String fingerprint, String counterparty, String bankCategory) {
        this.importBatch = importBatch;
        this.fingerprint = fingerprint;
        this.counterparty = counterparty;
        this.bankCategory = bankCategory;
    }

    /**
     * R27: the counterpart of an imported transfer becomes a transfer as well; its category goes, the import remembers
     * it for the undo (US34).
     */
    public void makeTransfer(ImportBatch batch) {
        this.transferBatch = batch;
        this.categoryBeforeTransfer = this.category;
        this.category = null;
        this.transfer = true;
    }

    /** US34: the import that made it a transfer is undone; it gets its category back unless it was changed since. */
    public void undoTransfer() {
        if (transfer && categoryBeforeTransfer != null) {
            this.category = categoryBeforeTransfer;
            this.transfer = false;
        }
        this.transferBatch = null;
        this.categoryBeforeTransfer = null;
    }

    public ImportBatch getImportBatch() {
        return importBatch;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public String getBankCategory() {
        return bankCategory;
    }

    /** R27: a transfer between own accounts; it has no category and counts in no calculation. */
    public boolean isTransfer() {
        return transfer;
    }

    public BankAccount getAccount() {
        return account;
    }
}
