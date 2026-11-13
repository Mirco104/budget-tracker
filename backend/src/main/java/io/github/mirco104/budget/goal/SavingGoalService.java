package io.github.mirco104.budget.goal;

import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.plan.ItemTerm;
import io.github.mirco104.budget.plan.ItemTermRepository;
import io.github.mirco104.budget.plan.PlanCalculator;
import io.github.mirco104.budget.plan.PlannedItem;
import io.github.mirco104.budget.plan.PlannedItemRepository;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saving goals of the signed-in user (concept 3.6, AP 27e). */
@Service
public class SavingGoalService {

    private final SavingGoalRepository goalRepository;
    private final PlannedItemRepository itemRepository;
    private final ItemTermRepository termRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public SavingGoalService(
            SavingGoalRepository goalRepository, PlannedItemRepository itemRepository,
            ItemTermRepository termRepository, TransactionRepository transactionRepository, CurrentUser currentUser,
            Clock clock) {
        this.goalRepository = goalRepository;
        this.itemRepository = itemRepository;
        this.termRepository = termRepository;
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record GoalRequest(
            @NotBlank(message = "Der Name ist Pflicht.")
            @Size(max = 100, message = "Der Name darf höchstens 100 Zeichen lang sein.")
            String name,
            @PositiveOrZero(message = "Der Betrag darf nicht negativ sein.")
            @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
            BigDecimal targetAmount,
            LocalDate targetDate) {
    }

    /**
     * saved: the ticked-off or entered saving bookings of its items, plus the amounts assigned at a month closing
     * (P10), minus what was paid from it; monthly: the monthly share of its saving items in the current month.
     */
    public record GoalResponse(
            long id, String name, BigDecimal targetAmount, LocalDate targetDate, BigDecimal saved, BigDecimal monthly) {
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> findAll() {
        List<PlannedItem> items = itemRepository.findAllOf(currentUser.get());
        List<ItemTerm> terms = termRepository.findByOwner(currentUser.get());
        return goalRepository.findByOwnerOrderByNameAsc(currentUser.get()).stream()
                .map(goal -> response(goal, items, terms))
                .toList();
    }

    @Transactional
    public GoalResponse create(GoalRequest request) {
        SavingGoal goal = new SavingGoal(currentUser.get());
        goal.update(request.name().trim(), scale(request.targetAmount()), request.targetDate());
        return response(goalRepository.save(goal), List.of(), List.of());
    }

    @Transactional
    public GoalResponse update(long id, GoalRequest request) {
        SavingGoal goal = find(id);
        goal.update(request.name().trim(), scale(request.targetAmount()), request.targetDate());
        return response(goal, itemRepository.findAllOf(currentUser.get()), termRepository.findByOwner(currentUser.get()));
    }

    /** A goal with saving items or bookings cannot be deleted (409). */
    @Transactional
    public void delete(long id) {
        SavingGoal goal = find(id);
        boolean used = itemRepository.findAllOf(currentUser.get()).stream()
                .anyMatch(item -> item.getSavingGoal() != null && item.getSavingGoal().getId().equals(id))
                || transactionRepository.existsBySavingGoalId(id);
        if (used) {
            throw new ConflictException("Das Sparziel hat Sparposten oder Buchungen und kann nicht gelöscht werden.");
        }
        goalRepository.delete(goal);
    }

    /** The goal for a booking or an item; null without an id, 400 on the field for another user's (R15). */
    @Transactional(readOnly = true)
    public SavingGoal findUsable(Long id, String field) {
        if (id == null) {
            return null;
        }
        return goalRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new InvalidFieldException(field, "Das Sparziel gibt es nicht."));
    }

    /** Extra amounts per goal id, e.g. the month closings (P10); empty until AP 27h. */
    protected java.util.Map<Long, BigDecimal> extraSaved() {
        return java.util.Map.of();
    }

    private GoalResponse response(SavingGoal goal, List<PlannedItem> items, List<ItemTerm> terms) {
        List<PlannedItem> ofGoal = items.stream()
                .filter(item -> item.getSavingGoal() != null && item.getSavingGoal().getId().equals(goal.getId()))
                .toList();
        Set<UUID> keys = ofGoal.stream().map(PlannedItem::getItemKey).collect(Collectors.toSet());
        BigDecimal paidIn = terms.stream()
                .filter(term -> keys.contains(term.getItemKey()) && term.getTransaction() != null)
                .map(term -> term.getTransaction().getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidOut = transactionRepository.sumPaidFromGoal(goal.getId()).abs();
        BigDecimal extra = extraSaved().getOrDefault(goal.getId(), BigDecimal.ZERO);
        YearMonth month = YearMonth.now(clock);
        BigDecimal monthly = ofGoal.stream()
                .filter(item -> PlanCalculator.validIn(item.data(), month))
                .map(item -> PlanCalculator.monthlyShare(item.getAmount(), item.getRhythmUnit(), item.getRhythmCount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GoalResponse(goal.getId(), goal.getName(), goal.getTargetAmount(), goal.getTargetDate(),
                paidIn.add(extra).subtract(paidOut).setScale(2, RoundingMode.HALF_UP),
                monthly.setScale(2, RoundingMode.HALF_UP));
    }

    private SavingGoal find(long id) {
        return goalRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Sparziel " + id + " wurde nicht gefunden."));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
