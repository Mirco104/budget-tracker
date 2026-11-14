package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.bankaccount.BankAccount;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.importer.ImportBatch;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One version of a planned item (section 19.3). A change "from month" ends this version in the previous month and
 * creates a new one with the same item key; terms hang on the item key.
 */
@Entity
@Table(name = "planned_items")
public class PlannedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private UUID itemKey;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean saving;

    // Null only for a saving item
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;

    // Null for "ohne festen Termin", then rhythmCount and anchorDate are null as well
    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private RhythmUnit rhythmUnit;

    private Integer rhythmCount;

    private LocalDate anchorDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private EntryMode entryMode;

    @Column(nullable = false)
    private boolean workdaysOnly;

    // First day of the month from which this version applies
    @Column(nullable = false)
    private LocalDate validFrom;

    // Last day of the last month, null = open-ended
    private LocalDate validTo;

    // P6: terms from this day on are entered automatically; a new version keeps the day of the item
    @Column(nullable = false)
    private LocalDate autoFrom;

    // P8: the savings account of the reserve pot of a rare item; null for a calculated pot
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserve_account_id")
    private BankAccount reserveAccount;

    // Concept 3.6: the saving goal of a saving item
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saving_goal_id")
    private io.github.mirco104.budget.goal.SavingGoal savingGoal;

    // R31: the import that created the item, null for one created by hand
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    protected PlannedItem() {
    }

    public PlannedItem(User owner, UUID itemKey, LocalDate autoFrom) {
        this.owner = owner;
        this.itemKey = itemKey;
        this.autoFrom = autoFrom;
    }

    public void update(
            String name, BigDecimal amount, boolean saving, Category category, BankAccount account,
            RhythmUnit rhythmUnit, Integer rhythmCount, LocalDate anchorDate, EntryMode entryMode,
            boolean workdaysOnly, YearMonth validFrom) {
        this.name = name;
        this.amount = amount;
        this.saving = saving;
        this.category = category;
        this.account = account;
        this.rhythmUnit = rhythmUnit;
        this.rhythmCount = rhythmUnit == null ? null : rhythmCount;
        this.anchorDate = rhythmUnit == null ? null : anchorDate;
        this.entryMode = entryMode;
        this.workdaysOnly = workdaysOnly;
        this.validFrom = validFrom.atDay(1);
    }

    /** Ends this version with the given month; null makes it open-ended again. */
    public void endWith(YearMonth month) {
        this.validTo = month == null ? null : month.atEndOfMonth();
    }

    /** R31: the item was created with an import; undoing the import deletes it (US34). */
    public void markImported(ImportBatch batch) {
        this.importBatch = batch;
    }

    /** The data for the calculations; a subcategory counts as its parent (R32). */
    public PlanCalculator.ItemData data() {
        return new PlanCalculator.ItemData(
                id, category == null ? null : category.getCalculationCategory().getId(), amount, saving, rhythmUnit,
                rhythmCount,
                getValidFrom(), getValidTo());
    }

    /** P5: the terms of this version; null for an item without a fixed date. */
    public TermSchedule schedule() {
        return rhythmUnit == null ? null
                : new TermSchedule(rhythmUnit, rhythmCount, anchorDate, getValidFrom(), getValidTo());
    }

    public Long getId() {
        return id;
    }

    public io.github.mirco104.budget.goal.SavingGoal getSavingGoal() {
        return savingGoal;
    }

    public void setSavingGoal(io.github.mirco104.budget.goal.SavingGoal savingGoal) {
        this.savingGoal = savingGoal;
    }

    public LocalDate getAutoFrom() {
        return autoFrom;
    }

    public User getOwner() {
        return owner;
    }

    public UUID getItemKey() {
        return itemKey;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isSaving() {
        return saving;
    }

    public Category getCategory() {
        return category;
    }

    public BankAccount getAccount() {
        return account;
    }

    public RhythmUnit getRhythmUnit() {
        return rhythmUnit;
    }

    public Integer getRhythmCount() {
        return rhythmCount;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public EntryMode getEntryMode() {
        return entryMode;
    }

    public boolean isWorkdaysOnly() {
        return workdaysOnly;
    }

    public YearMonth getValidFrom() {
        return YearMonth.from(validFrom);
    }

    public YearMonth getValidTo() {
        return validTo == null ? null : YearMonth.from(validTo);
    }

    public BankAccount getReserveAccount() {
        return reserveAccount;
    }

    public void setReserveAccount(BankAccount reserveAccount) {
        this.reserveAccount = reserveAccount;
    }
}
