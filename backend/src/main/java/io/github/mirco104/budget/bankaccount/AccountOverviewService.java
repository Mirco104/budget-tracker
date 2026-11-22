package io.github.mirco104.budget.bankaccount;

import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The page Konten (concept 4.4): accounts by role with their app balance (P11) and the reconciliation. */
@Service
public class AccountOverviewService {

    private final BankAccountRepository accountRepository;
    private final BankStatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AccountOverviewService(
            BankAccountRepository accountRepository, BankStatementRepository statementRepository,
            TransactionRepository transactionRepository, CurrentUser currentUser, Clock clock) {
        this.accountRepository = accountRepository;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * One account with its app balance today and its last balance from the bank (null without one). lowerLimit is the
     * amount of the lower limit (null without one) and belowLimit tells whether the balance lies below it (P12).
     */
    public record AccountLine(
            long id, String name, String iban, AccountRole role, boolean defaultAccount, boolean archived,
            BigDecimal balance, LocalDate statementDate, BigDecimal statementAmount, LowerLimitMode lowerLimitMode,
            BigDecimal lowerLimit, boolean belowLimit) {
    }

    /** The accounts of one role with the sum of their balances. */
    public record RoleGroup(AccountRole role, BigDecimal total, List<AccountLine> accounts) {
    }

    /** P11: the balance from the bank, the app balance on its day and the difference (not booked). */
    public record ReconcileResult(BigDecimal appBalance, BigDecimal bankBalance, BigDecimal difference) {
    }

    /** Ticket #135: one balance from the bank of an account with the app balance on its day and the difference. */
    public record BalanceLine(long id, LocalDate date, BigDecimal amount, BigDecimal appBalance, BigDecimal difference) {
    }

    @Transactional(readOnly = true)
    public List<RoleGroup> groups() {
        LocalDate today = LocalDate.now(clock);
        Map<Long, List<AccountBalance.Entry>> entries = entriesByAccount();
        Map<Long, List<BankStatement>> statements = statementRepository.findByOwnerOrderByDateAsc(currentUser.get())
                .stream().collect(Collectors.groupingBy(s -> s.getAccount().getId()));
        List<RoleGroup> groups = new ArrayList<>();
        for (AccountRole role : AccountRole.values()) {
            List<AccountLine> lines = accountRepository.findAllOrdered(currentUser.get()).stream()
                    .filter(account -> account.getRole() == role)
                    .map(account -> {
                        BankStatement last = lastOnOrBefore(statements.get(account.getId()), today);
                        BigDecimal balance = AccountBalance.balance(last == null ? null : last.statement(),
                                entries.getOrDefault(account.getId(), List.of()), today);
                        BigDecimal limit = account.limit();
                        return new AccountLine(account.getId(), account.getName(), account.getIban(), role,
                                account.isDefaultAccount(), account.isArchived(), balance,
                                last == null ? null : last.getDate(), last == null ? null : last.getAmount(),
                                account.getLowerLimitMode(), limit, AccountBalance.below(balance, limit));
                    })
                    .toList();
            BigDecimal total = lines.stream().map(AccountLine::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            groups.add(new RoleGroup(role, total.setScale(2, RoundingMode.HALF_UP), lines));
        }
        return groups;
    }

    /** P12: sets the lower limit of the account; CUSTOM needs an amount (400 on the field). */
    @Transactional
    public void setLowerLimit(long accountId, LowerLimitMode mode, BigDecimal amount) {
        BankAccount account = accountRepository.findByIdAndOwner(accountId, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Konto " + accountId + " wurde nicht gefunden."));
        if (mode == LowerLimitMode.CUSTOM && amount == null) {
            throw new io.github.mirco104.budget.common.InvalidFieldException("amount", "Bitte einen Betrag eingeben.");
        }
        account.setLowerLimit(mode, amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** P11: the app balance of the account at the end of the day. */
    @Transactional(readOnly = true)
    public BigDecimal balance(BankAccount account, LocalDate day) {
        List<BankStatement> statements = statementRepository.findByOwnerOrderByDateAsc(currentUser.get()).stream()
                .filter(s -> s.getAccount().getId().equals(account.getId()))
                .toList();
        BankStatement last = lastOnOrBefore(statements, day);
        return AccountBalance.balance(last == null ? null : last.statement(),
                entriesByAccount().getOrDefault(account.getId(), List.of()), day);
    }

    /** R36: the balance of every everyday account on the last day of the month gives the variable spending. */
    public record VariableResponse(BigDecimal amount, boolean moreThanExpected, LocalDate date, int accounts) {
    }

    /** R36 (stage 2): variable spending of the month from the balances on its last day; amount null without one. */
    @Transactional(readOnly = true)
    public VariableResponse variable(java.time.YearMonth month) {
        LocalDate last = month.atEndOfMonth();
        List<BankStatement> statements = statementRepository.findByOwnerOrderByDateAsc(currentUser.get());
        Map<Long, List<AccountBalance.Entry>> entries = entriesByAccount();
        List<BigDecimal> differences = new java.util.ArrayList<>();
        for (BankStatement statement : statements) {
            BankAccount account = statement.getAccount();
            if (!statement.getDate().equals(last) || account.getRole() != AccountRole.EVERYDAY || account.isArchived()) {
                continue;
            }
            BankStatement previous = statements.stream()
                    .filter(s -> s.getAccount().getId().equals(account.getId()) && s.getDate().isBefore(last))
                    .reduce((a, b) -> b)
                    .orElse(null);
            differences.add(AccountBalance.difference(previous == null ? null : previous.statement(),
                    entries.getOrDefault(account.getId(), List.of()), statement.statement()));
        }
        AccountBalance.Variable variable = AccountBalance.variable(differences);
        return variable == null
                ? new VariableResponse(null, false, last, 0)
                : new VariableResponse(variable.amount(), variable.moreThanExpected(), last, differences.size());
    }

    /** P11: stores the balance from the bank for its day (replacing one of the same day) and shows the difference. */
    @Transactional
    public ReconcileResult reconcile(long accountId, LocalDate date, BigDecimal amount) {
        BankAccount account = accountRepository.findByIdAndOwner(accountId, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Konto " + accountId + " wurde nicht gefunden."));
        if (date.isAfter(LocalDate.now(clock))) {
            throw new InvalidFieldException("date", "Der Stichtag darf nicht in der Zukunft liegen.");
        }
        List<BankStatement> before = statementRepository.findByOwnerOrderByDateAsc(currentUser.get()).stream()
                .filter(s -> s.getAccount().getId().equals(accountId) && s.getDate().isBefore(date))
                .toList();
        BankStatement previous = before.isEmpty() ? null : before.getLast();
        List<AccountBalance.Entry> entries = entriesByAccount().getOrDefault(accountId, List.of());
        AccountBalance.Statement bank = new AccountBalance.Statement(date, amount.setScale(2, RoundingMode.HALF_UP));
        BigDecimal app = AccountBalance.balance(previous == null ? null : previous.statement(), entries, date);
        BankStatement statement = statementRepository.findByAccountAndDate(account, date)
                .orElseGet(() -> new BankStatement(currentUser.get(), account, date));
        statement.setAmount(bank.amount());
        statementRepository.save(statement);
        return new ReconcileResult(app, bank.amount(),
                AccountBalance.difference(previous == null ? null : previous.statement(), entries, bank));
    }

    /** Ticket #135: the balances from the bank of the account, newest first (P11). */
    @Transactional(readOnly = true)
    public List<BalanceLine> history(long accountId) {
        BankAccount account = ownAccount(accountId);
        Map<LocalDate, BankStatement> byDate = statementsOf(account).stream()
                .collect(Collectors.toMap(BankStatement::getDate, s -> s));
        List<AccountBalance.Statement> statements = byDate.values().stream().map(BankStatement::statement).toList();
        return AccountBalance.history(statements, entriesByAccount().getOrDefault(accountId, List.of())).stream()
                .map(r -> new BalanceLine(byDate.get(r.statement().date()).getId(), r.statement().date(),
                        r.statement().amount(), r.appBalance(), r.difference()))
                .toList();
    }

    /** Ticket #135: deletes a wrong balance from the bank; the app balance then counts from the one before (P11). */
    @Transactional
    public void deleteBalance(long accountId, long balanceId) {
        BankAccount account = ownAccount(accountId);
        BankStatement statement = statementsOf(account).stream()
                .filter(s -> s.getId() == balanceId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Kontostand " + balanceId + " wurde nicht gefunden."));
        statementRepository.delete(statement);
    }

    private BankAccount ownAccount(long accountId) {
        return accountRepository.findByIdAndOwner(accountId, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Konto " + accountId + " wurde nicht gefunden."));
    }

    private List<BankStatement> statementsOf(BankAccount account) {
        return statementRepository.findByOwnerOrderByDateAsc(currentUser.get()).stream()
                .filter(s -> s.getAccount().getId().equals(account.getId()))
                .toList();
    }

    private Map<Long, List<AccountBalance.Entry>> entriesByAccount() {
        return transactionRepository.findAllWithAccount(currentUser.get()).stream()
                .collect(Collectors.groupingBy(t -> t.getAccount().getId(),
                        Collectors.mapping(AccountOverviewService::entry, Collectors.toList())));
    }

    private static AccountBalance.Entry entry(Transaction t) {
        return new AccountBalance.Entry(t.getDate(), t.getAmount());
    }

    private static BankStatement lastOnOrBefore(List<BankStatement> statements, LocalDate day) {
        if (statements == null) {
            return null;
        }
        return statements.stream()
                .filter(s -> !s.getDate().isAfter(day))
                .max(Comparator.comparing(BankStatement::getDate))
                .orElse(null);
    }
}
