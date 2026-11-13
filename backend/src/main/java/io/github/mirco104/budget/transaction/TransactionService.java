package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.bankaccount.BankAccountService;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.plan.ItemTermRepository;
import io.github.mirco104.budget.plan.TermService;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final BankAccountService bankAccountService;
    private final ItemTermRepository termRepository;
    private final TermService termService;
    private final io.github.mirco104.budget.plan.PlannedItemRepository itemRepository;
    private final io.github.mirco104.budget.goal.SavingGoalRepository goalRepository;
    private final CurrentUser currentUser;

    public TransactionService(
            TransactionRepository transactionRepository, CategoryService categoryService,
            BankAccountService bankAccountService, ItemTermRepository termRepository, TermService termService,
            CurrentUser currentUser, io.github.mirco104.budget.plan.PlannedItemRepository itemRepository,
            io.github.mirco104.budget.goal.SavingGoalRepository goalRepository) {
        this.itemRepository = itemRepository;
        this.goalRepository = goalRepository;
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.bankAccountService = bankAccountService;
        this.termRepository = termRepository;
        this.termService = termService;
        this.currentUser = currentUser;
    }

    /** The longest period of the list (US26): to must lie before from + 10 years. */
    static final int MAX_PERIOD_YEARS = 10;

    // Bookings with their date in the period, both days included, newest first. Planned items are no bookings (P1).
    /** 27f4: from the first to the last booking, both null without one. */
    public record Range(LocalDate from, LocalDate to) {
    }

    /**
     * 27f4: the whole period of the user's bookings for the quick choice "Gesamt" (US26); at most the last 10 years
     * of it, like every period of the list.
     */
    @Transactional(readOnly = true)
    public Range range() {
        LocalDate first = transactionRepository.findFirstBookingDate(currentUser.get());
        LocalDate last = transactionRepository.findLastBookingDate(currentUser.get());
        if (first == null || last == null) {
            return new Range(null, null);
        }
        LocalDate earliest = last.minusYears(MAX_PERIOD_YEARS).plusDays(1);
        return new Range(first.isBefore(earliest) ? earliest : first, last);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidFieldException("to", "„Bis“ darf nicht vor „Von“ liegen.");
        }
        if (!to.isBefore(from.plusYears(MAX_PERIOD_YEARS))) {
            throw new InvalidFieldException("to", "Der Zeitraum darf höchstens 10 Jahre lang sein.");
        }
        List<Transaction> transactions = transactionRepository
                .findByOwnerAndDateBetweenOrderByDateDescIdDesc(currentUser.get(), from, to);
        // P5: an expense that fulfils a term is fix
        Set<Long> fix = termService.fixIds(from, to);
        Map<Long, java.util.UUID> reserves = termService.reserveTerms(from, to);
        return transactions.stream()
                .map(transaction -> TransactionResponse.from(transaction, fix.contains(transaction.getId()),
                        reserves.get(transaction.getId())))
                .toList();
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        checkAmount(request.amount());
        if (request.replaces() != null && Boolean.TRUE.equals(request.transfer())) {
            throw new InvalidFieldException("replaces", "Eine Umbuchung erfüllt keinen Termin.");
        }
        Transaction transaction = new Transaction(
                currentUser.get(),
                request.amount().setScale(2, RoundingMode.HALF_UP),
                request.description().trim(),
                request.date(),
                categoryOf(request),
                bankAccountService.findBookable(request.accountId()));
        transaction.markSaving(Boolean.TRUE.equals(request.saving()));
        payFrom(transaction, request);
        Transaction saved = transactionRepository.save(transaction);
        if (request.targetAccountId() != null) {
            pair(saved, request);
        }
        if (request.replaces() != null) {
            termService.link(request.replaces().itemId(), request.replaces().date(), saved, "replaces");
        }
        return TransactionResponse.from(saved, request.replaces() != null && saved.getAmount().signum() < 0);
    }

    @Transactional
    public TransactionResponse update(long id, TransactionRequest request) {
        Transaction transaction = findTransaction(id);
        checkAmount(request.amount());
        transaction.update(
                request.amount().setScale(2, RoundingMode.HALF_UP),
                request.description().trim(),
                request.date(),
                categoryOf(request),
                bankAccountService.findBookable(request.accountId()));
        transaction.markSaving(Boolean.TRUE.equals(request.saving()));
        payFrom(transaction, request);
        // 27f3: amount and description apply to the other side too, with the opposite sign; the date stays per side
        Transaction peer = transaction.getTransferPeer();
        if (peer != null) {
            peer.update(transaction.getAmount().negate(), transaction.getDescription(), peer.getDate(), null,
                    peer.getAccount());
        }
        // R27: a transfer fulfils no term
        if (transaction.isTransfer()) {
            termRepository.findByTransaction(transaction).ifPresent(termRepository::delete);
        }
        return TransactionResponse.from(transaction, termService.fixIds(transaction.getDate(), transaction.getDate()).contains(id));
    }

    /**
     * Deletes the booking. A term it was linked to keeps its row without a booking, so a deleted automatic entry is
     * not entered again (P6, section 19.6).
     */
    @Transactional
    public void delete(long id) {
        Transaction transaction = findTransaction(id);
        termRepository.findByTransaction(transaction).ifPresent(term -> term.link(null));
        // 27f3: both sides of a transfer pair go together
        Transaction peer = transaction.getTransferPeer();
        if (peer != null) {
            transaction.setTransferPeer(null);
            peer.setTransferPeer(null);
            transactionRepository.flush();
            transactionRepository.delete(peer);
        }
        transactionRepository.delete(transaction);
    }

    // P8: an expense may be paid from the reserve pot of a rare item or from a saving goal, not from both
    private void payFrom(Transaction transaction, TransactionRequest request) {
        if (request.reserveItemKey() == null && request.savingGoalId() == null) {
            transaction.payFrom(null, null);
            transaction.setNoReserve(Boolean.TRUE.equals(request.noReserve()));
            return;
        }
        transaction.setNoReserve(false);
        if (transaction.isTransfer() || transaction.getAmount().signum() > 0) {
            throw new InvalidFieldException("reserveItemKey", "Nur eine Ausgabe kann aus einer Rücklage bezahlt werden.");
        }
        if (request.reserveItemKey() != null && request.savingGoalId() != null) {
            throw new InvalidFieldException("reserveItemKey", "Bitte nur eine Rücklage oder ein Sparziel wählen.");
        }
        if (request.reserveItemKey() != null) {
            boolean rare = itemRepository.findByItemKeyAndOwner(request.reserveItemKey(), currentUser.get()).stream()
                    .anyMatch(item -> item.getRhythmUnit() == io.github.mirco104.budget.plan.RhythmUnit.MONTH
                            && item.getRhythmCount() > 1 && !item.isSaving());
            if (!rare) {
                throw new InvalidFieldException("reserveItemKey", "Diese Rücklage gibt es nicht.");
            }
        }
        transaction.payFrom(request.reserveItemKey(), request.savingGoalId() == null ? null
                : goalRepository.findByIdAndOwner(request.savingGoalId(), currentUser.get())
                        .orElseThrow(() -> new InvalidFieldException("savingGoalId", "Das Sparziel gibt es nicht.")));
    }

/**
     * 27f3: the other side of a transfer: the amount leaves the account of the booking and arrives on the target
     * account. The booking itself becomes the outflow.
     */
    private void pair(Transaction saved, TransactionRequest request) {
        if (!saved.isTransfer()) {
            throw new InvalidFieldException("targetAccountId", "Nur eine Umbuchung hat ein Zielkonto.");
        }
        io.github.mirco104.budget.bankaccount.BankAccount target = bankAccountService.findBookable(request.targetAccountId());
        if (target.getId().equals(saved.getAccount().getId())) {
            throw new InvalidFieldException("targetAccountId", "Quell- und Zielkonto müssen verschieden sein.");
        }
        BigDecimal outflow = saved.getAmount().abs().negate();
        saved.update(outflow, saved.getDescription(), saved.getDate(), null, saved.getAccount());
        Transaction inflow = transactionRepository.save(new Transaction(
                currentUser.get(), outflow.negate(), saved.getDescription(), saved.getDate(), null, target));
        saved.setTransferPeer(inflow);
        inflow.setTransferPeer(saved);
    }

    // R27: a transfer has no category (400 if one is sent), every other booking needs one
    private Category categoryOf(TransactionRequest request) {
        if (Boolean.TRUE.equals(request.transfer()) || Boolean.TRUE.equals(request.saving())) {
            if (request.categoryId() != null) {
                throw new InvalidFieldException("categoryId", "Eine Umbuchung hat keine Kategorie.");
            }
            return null;
        }
        if (request.categoryId() == null) {
            throw new InvalidFieldException("categoryId", "Die Kategorie ist Pflicht.");
        }
        return categoryService.findBookable(request.categoryId());
    }

    // R11: the sign decides, so 0 is neither income nor expense
    private static void checkAmount(BigDecimal amount) {
        if (amount.signum() == 0) {
            throw new InvalidFieldException("amount", "Der Betrag darf nicht 0 sein.");
        }
    }

    // Another user's transaction is 404 like an unknown one (R15)
    private Transaction findTransaction(long id) {
        return transactionRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Transaktion " + id + " wurde nicht gefunden."));
    }
}
