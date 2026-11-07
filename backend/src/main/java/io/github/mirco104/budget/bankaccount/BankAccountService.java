package io.github.mirco104.budget.bankaccount;

import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bank accounts of the signed-in user (R15, R26, US30). */
@Service
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringRepository;
    private final BankAccountSourceRepository sourceRepository;
    private final CurrentUser currentUser;

    public BankAccountService(
            BankAccountRepository bankAccountRepository, TransactionRepository transactionRepository,
            RecurringTransactionRepository recurringRepository, BankAccountSourceRepository sourceRepository,
            CurrentUser currentUser) {
        this.sourceRepository = sourceRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.transactionRepository = transactionRepository;
        this.recurringRepository = recurringRepository;
        this.currentUser = currentUser;
    }

    /** The default account first, then by name. */
    @Transactional(readOnly = true)
    public List<BankAccountResponse> findAll() {
        return findAllOrdered().stream()
                .map(account -> BankAccountResponse.from(account, isUsed(account)))
                .toList();
    }

    /** The user's accounts as entities, default first. */
    @Transactional(readOnly = true)
    public List<BankAccount> findAllOrdered() {
        return bankAccountRepository.findAllOrdered(currentUser.get());
    }

    @Transactional
    public BankAccountResponse create(CreateBankAccountRequest request) {
        return BankAccountResponse.from(create(request.name(), request.iban()), false);
    }

    /** A new account that is not the default one; 409 for a name or IBAN the user already has (R26). */
    @Transactional
    public BankAccount create(String rawName, String rawIban) {
        String name = rawName.trim();
        String iban = checkedIban(rawIban, null);
        checkName(name, null);
        return bankAccountRepository.save(new BankAccount(currentUser.get(), name, iban, false));
    }

    /**
     * Renames, changes the IBAN, archives and makes an account the default one (R26). The default account cannot be
     * archived, and it stops being the default only when another account becomes it (409).
     */
    @Transactional
    public BankAccountResponse update(long id, UpdateBankAccountRequest request) {
        BankAccount account = find(id);
        String name = request.name().trim();
        String iban = checkedIban(request.iban(), id);
        checkName(name, id);
        boolean makeDefault = request.defaultAccount();
        if (account.isDefaultAccount() && !makeDefault) {
            throw new ConflictException("Lege ein anderes Konto als Standardkonto fest.");
        }
        if (makeDefault && request.archived()) {
            throw new ConflictException("Das Standardkonto kann nicht archiviert werden.");
        }
        if (makeDefault && !account.isDefaultAccount()) {
            bankAccountRepository.findByOwnerAndDefaultAccountTrue(currentUser.get())
                    .ifPresent(previous -> previous.setDefaultAccount(false));
            account.setDefaultAccount(true);
        }
        account.update(name, iban, request.archived());
        return BankAccountResponse.from(account, isUsed(account));
    }

    /** R26: only an account without transactions and series can be deleted, never the default account. */
    @Transactional
    public void delete(long id) {
        BankAccount account = find(id);
        if (account.isDefaultAccount()) {
            throw new ConflictException("Das Standardkonto kann nicht gelöscht werden.");
        }
        if (isUsed(account)) {
            throw new ConflictException(
                    "Auf diesem Konto gibt es Buchungen. Das Konto kann nur archiviert werden.");
        }
        sourceRepository.deleteByAccount(account);
        bankAccountRepository.deleteById(id);
    }

    /**
     * The account for a booking: the default account without an id, else the user's account with it; 400 on field
     * accountId if it is unknown or another user's (R15). Archived accounts stay bookable, so existing entries can be
     * edited (like categories, US10).
     */
    @Transactional(readOnly = true)
    public BankAccount findBookable(Long accountId) {
        User owner = currentUser.get();
        if (accountId == null) {
            return bankAccountRepository.findByOwnerAndDefaultAccountTrue(owner).orElseThrow();
        }
        return bankAccountRepository.findByIdAndOwner(accountId, owner)
                .orElseThrow(() -> new InvalidFieldException("accountId", "Das Konto existiert nicht."));
    }

    // Another user's account is 404 like an unknown one (R15)
    private BankAccount find(long id) {
        return bankAccountRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Konto " + id + " wurde nicht gefunden."));
    }

    private boolean isUsed(BankAccount account) {
        return transactionRepository.existsByAccountId(account.getId())
                || recurringRepository.existsByAccountId(account.getId());
    }

    private void checkName(String name, Long id) {
        if (bankAccountRepository.existsByOwnerAndNameIgnoreCaseAndIdNot(currentUser.get(), name, id == null ? -1 : id)) {
            throw new ConflictException("Ein Konto mit dem Namen \"" + name + "\" gibt es schon.");
        }
    }

    // R26: 400 on field iban if it is invalid, 409 if another account of the user has it
    private String checkedIban(String value, Long id) {
        String iban = Iban.normalize(value);
        if (iban == null) {
            return null;
        }
        if (!Iban.isValid(iban)) {
            throw new InvalidFieldException("iban", "Die IBAN ist ungültig.");
        }
        if (bankAccountRepository.existsByOwnerAndIbanAndIdNot(currentUser.get(), iban, id == null ? -1 : id)) {
            throw new ConflictException("Ein Konto mit dieser IBAN gibt es schon.");
        }
        return iban;
    }
}
