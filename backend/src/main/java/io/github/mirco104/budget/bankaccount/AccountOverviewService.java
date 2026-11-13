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

    /** One account with its app balance today and its last balance from the bank (null without one). */
    public record AccountLine(
            long id, String name, String iban, AccountRole role, boolean defaultAccount, boolean archived,
            BigDecimal balance, LocalDate statementDate, BigDecimal statementAmount) {
    }

    /** The accounts of one role with the sum of their balances. */
    public record RoleGroup(AccountRole role, BigDecimal total, List<AccountLine> accounts) {
    }

    /** P11: the balance from the bank, the app balance on its day and the difference (not booked). */
    public record ReconcileResult(BigDecimal appBalance, BigDecimal bankBalance, BigDecimal difference) {
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
                        return new AccountLine(account.getId(), account.getName(), account.getIban(), role,
                                account.isDefaultAccount(), account.isArchived(),
                                AccountBalance.balance(last == null ? null : last.statement(),
                                        entries.getOrDefault(account.getId(), List.of()), today),
                                last == null ? null : last.getDate(), last == null ? null : last.getAmount());
                    })
                    .toList();
            BigDecimal total = lines.stream().map(AccountLine::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            groups.add(new RoleGroup(role, total.setScale(2, RoundingMode.HALF_UP), lines));
        }
        return groups;
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
