package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.bankaccount.BankAccount;
import io.github.mirco104.budget.bankaccount.BankAccountService;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.dashboard.IncomeAverage;
import io.github.mirco104.budget.goal.SavingGoalRepository;
import io.github.mirco104.budget.importer.ImportBatch;
import io.github.mirco104.budget.plan.PlanCalculator.Group;
import io.github.mirco104.budget.plan.PlanCalculator.ItemData;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Planned items of the signed-in user (R15) and the plan of a month (P2–P4, P9, section 19). */
@Service
public class PlanService {

    private final PlannedItemRepository itemRepository;
    private final ItemTermRepository termRepository;
    private final CategoryService categoryService;
    private final BankAccountService bankAccountService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final SavingGoalRepository goalRepository;

    public PlanService(
            PlannedItemRepository itemRepository, ItemTermRepository termRepository, CategoryService categoryService,
            BankAccountService bankAccountService, CurrentUser currentUser, Clock clock,
            SavingGoalRepository goalRepository) {
        this.clock = clock;
        this.goalRepository = goalRepository;
        this.itemRepository = itemRepository;
        this.termRepository = termRepository;
        this.categoryService = categoryService;
        this.bankAccountService = bankAccountService;
        this.currentUser = currentUser;
    }

    /** All versions of all items of the user. */
    @Transactional(readOnly = true)
    public List<PlannedItem> findAll() {
        return itemRepository.findAllOf(currentUser.get());
    }

    /** P3, P4, P9 for the month, without the average (for the dashboard). */
    @Transactional(readOnly = true)
    public PlanCalculator.Result calculate(YearMonth month) {
        return PlanCalculator.of(findAll().stream().map(PlannedItem::data).toList(), month);
    }

    /** The plan page of a month; average is Ē(M) and Emin(M) for orientation (R23). */
    @Transactional(readOnly = true)
    public PlanResponse plan(YearMonth month, IncomeAverage.Result average) {
        List<PlannedItem> items = findAll();
        List<ItemData> data = items.stream().map(PlannedItem::data).toList();
        PlanCalculator.Result result = PlanCalculator.of(data, month);

        List<PlanResponse.CategoryTotal> categories = new ArrayList<>();
        Map<Long, Category> categoryById = new java.util.HashMap<>();
        items.stream().filter(i -> i.getCategory() != null)
                .forEach(i -> categoryById.put(i.getCategory().getId(), i.getCategory()));
        for (Group group : List.of(Group.INCOME, Group.FIXED, Group.BUDGET)) {
            PlanCalculator.categoryTotals(data, month, group).forEach((id, total) -> categories.add(
                    new PlanResponse.CategoryTotal(group, id, categoryById.get(id).getName(), total)));
        }
        categories.sort(Comparator.comparing(PlanResponse.CategoryTotal::group)
                .thenComparing(PlanResponse.CategoryTotal::categoryName, String.CASE_INSENSITIVE_ORDER));

        List<PlannedItemResponse> valid = items.stream()
                .filter(item -> PlanCalculator.validIn(item.data(), month))
                .map(PlannedItemResponse::from)
                .toList();
        return new PlanResponse(
                month.toString(),
                result.income(),
                result.fixed(),
                result.budgets(),
                result.saving(),
                result.unplanned(),
                result.savingRate(),
                result.planTotal(),
                money(average.average()),
                money(average.minimum()),
                average.months(),
                average.from() == null ? null : average.from().toString(),
                average.to() == null ? null : average.to().toString(),
                average.minimum() != null && result.income().compareTo(average.minimum()) > 0,
                categories,
                valid);
    }

    /** 27f2: past terms an automatic item with these values enters at once; 0 for other entry modes. */
    public int pastTerms(PlannedItemRequest request) {
        if (request.entryMode() != null && request.entryMode() != EntryMode.AUTO) {
            return 0;
        }
        return TermService.pastTerms(request.rhythmUnit(), request.rhythmCount(), request.anchorDate(),
                request.validFrom(), Boolean.TRUE.equals(request.workdaysOnly()), LocalDate.now(clock));
    }

    @Transactional
    public PlannedItemResponse create(PlannedItemRequest request) {
        PlannedItem item = new PlannedItem(currentUser.get(), UUID.randomUUID(), LocalDate.now(clock));
        apply(item, request, request.validFrom());
        return PlannedItemResponse.from(itemRepository.save(item));
    }

    /** R31: a monthly item from a series found in an import, entered automatically from its first occurrence. */
    @Transactional
    public PlannedItem createMonthlyForImport(
            String name, BigDecimal amount, long categoryId, BankAccount account, LocalDate anchor,
            ImportBatch batch) {
        PlannedItem item = new PlannedItem(currentUser.get(), UUID.randomUUID(), LocalDate.now(clock));
        item.update(name.trim(), amount.setScale(2, RoundingMode.HALF_UP), false, categoryService.findBookable(categoryId),
                account, RhythmUnit.MONTH, 1, anchor, EntryMode.AUTO, false, YearMonth.from(anchor));
        item.markImported(batch);
        return itemRepository.save(item);
    }

    /**
     * "Gilt ab" (section 19.3): a change from the month of the version changes it; a later month ends the version in
     * the previous month and starts a new version with the same item key. A month before the version is only allowed
     * when no earlier version of the item exists.
     */
    @Transactional
    public PlannedItemResponse update(long id, PlannedItemRequest request) {
        PlannedItem item = find(id);
        YearMonth from = request.validFrom();
        if (from.equals(item.getValidFrom())) {
            apply(item, request, from);
            return PlannedItemResponse.from(item);
        }
        if (from.isBefore(item.getValidFrom())) {
            boolean earlierVersion = itemRepository.findByItemKeyAndOwner(item.getItemKey(), currentUser.get())
                    .stream().anyMatch(other -> other.getValidFrom().isBefore(item.getValidFrom()));
            if (earlierVersion) {
                throw new InvalidFieldException("validFrom",
                        "„Gilt ab“ darf nicht vor dem Beginn dieser Fassung liegen.");
            }
            apply(item, request, from);
            return PlannedItemResponse.from(item);
        }
        if (item.getValidTo() != null && from.isAfter(item.getValidTo())) {
            throw new InvalidFieldException("validFrom", "Der Posten endet vor diesem Monat.");
        }
        YearMonth end = item.getValidTo();
        item.endWith(from.minusMonths(1));
        PlannedItem next = new PlannedItem(currentUser.get(), item.getItemKey(), item.getAutoFrom());
        apply(next, request, from);
        next.endWith(end);
        return PlannedItemResponse.from(itemRepository.save(next));
    }

    /**
     * Ends the item from a month on: the version in that month ends in the previous month, later versions are
     * deleted. Without a month the item is deleted with all versions and handled terms; its bookings stay.
     */
    @Transactional
    public void delete(long id, YearMonth from) {
        PlannedItem item = find(id);
        List<PlannedItem> versions = itemRepository.findByItemKeyAndOwner(item.getItemKey(), currentUser.get());
        if (from == null) {
            termRepository.deleteByItemKey(currentUser.get(), item.getItemKey());
            itemRepository.deleteAll(versions);
            return;
        }
        for (PlannedItem version : versions) {
            if (!version.getValidFrom().isBefore(from)) {
                itemRepository.delete(version);
            } else if (version.getValidTo() == null || !version.getValidTo().isBefore(from)) {
                version.endWith(from.minusMonths(1));
            }
        }
    }

    private void apply(PlannedItem item, PlannedItemRequest request, YearMonth validFrom) {
        if (request.amount().signum() == 0) {
            throw new InvalidFieldException("amount", "Der Betrag darf nicht 0 sein.");
        }
        boolean saving = Boolean.TRUE.equals(request.saving());
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (saving) {
            amount = amount.abs().negate();
        }
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryService.findBookable(request.categoryId());
        } else if (!saving) {
            throw new InvalidFieldException("categoryId", "Die Kategorie ist Pflicht.");
        }
        RhythmUnit unit = request.rhythmUnit();
        if (unit != null) {
            if (request.rhythmCount() == null) {
                throw new InvalidFieldException("rhythmCount", "Wie oft ist Pflicht.");
            }
            if (request.anchorDate() == null) {
                throw new InvalidFieldException("anchorDate", "Das Datum ist Pflicht.");
            }
        }
        EntryMode mode = request.entryMode() != null ? request.entryMode() : defaultMode(saving, amount, unit);
        if (unit == null && mode == EntryMode.AUTO) {
            // Without a date there is no term to enter automatically
            mode = EntryMode.MANUAL;
        }
        item.update(request.name().trim(), amount, saving, category,
                bankAccountService.findBookable(request.accountId()), unit, request.rhythmCount(),
                request.anchorDate(), mode, Boolean.TRUE.equals(request.workdaysOnly()), validFrom);
        if (request.savingGoalId() != null && !saving) {
            throw new InvalidFieldException("savingGoalId", "Nur ein Sparposten gehört zu einem Sparziel.");
        }
        item.setSavingGoal(request.savingGoalId() == null ? null
                : goalRepository.findByIdAndOwner(request.savingGoalId(), currentUser.get())
                        .orElseThrow(() -> new InvalidFieldException("savingGoalId", "Das Sparziel gibt es nicht.")));
    }

    /** P8: links the pot of a rare item (all versions) to an account (27f4: of any role), or unlinks it. */
    @Transactional
    public PlannedItemResponse linkReserve(long id, Long accountId) {
        PlannedItem item = find(id);
        if (item.getRhythmUnit() != RhythmUnit.MONTH || item.getRhythmCount() < 2 || item.isSaving()) {
            throw new InvalidFieldException("accountId", "Nur ein seltener Posten hat eine Rücklage.");
        }
        BankAccount account = null;
        if (accountId != null) {
            // 27f4 (decision 60): any account, also an everyday one, may hold a reserve
            account = bankAccountService.findBookable(accountId);
        }
        for (PlannedItem version : itemRepository.findByItemKeyAndOwner(item.getItemKey(), currentUser.get())) {
            version.setReserveAccount(account);
        }
        return PlannedItemResponse.from(item);
    }

    // Decision 51: income and fixed costs automatically, saving when ticked off, budgets by the user
    static EntryMode defaultMode(boolean saving, BigDecimal amount, RhythmUnit unit) {
        return switch (PlanCalculator.groupOf(saving, amount, unit)) {
            case INCOME, FIXED -> unit == null ? EntryMode.MANUAL : EntryMode.AUTO;
            case SAVING -> EntryMode.TICK;
            case BUDGET -> EntryMode.MANUAL;
        };
    }

    // Another user's item is 404 like an unknown one (R15)
    private PlannedItem find(long id) {
        return itemRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Posten " + id + " wurde nicht gefunden."));
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
