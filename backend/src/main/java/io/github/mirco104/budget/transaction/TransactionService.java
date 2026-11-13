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
    private final CurrentUser currentUser;

    public TransactionService(
            TransactionRepository transactionRepository, CategoryService categoryService,
            BankAccountService bankAccountService, ItemTermRepository termRepository, TermService termService,
            CurrentUser currentUser) {
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
        return transactions.stream()
                .map(transaction -> TransactionResponse.from(transaction, fix.contains(transaction.getId())))
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
        Transaction saved = transactionRepository.save(transaction);
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
        transactionRepository.delete(transaction);
    }

    // R27: a transfer has no category (400 if one is sent), every other booking needs one
    private Category categoryOf(TransactionRequest request) {
        if (Boolean.TRUE.equals(request.transfer())) {
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
