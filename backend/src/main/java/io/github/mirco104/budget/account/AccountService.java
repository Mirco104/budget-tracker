package io.github.mirco104.budget.account;

import io.github.mirco104.budget.account.AccountExport.Account;
import io.github.mirco104.budget.account.AccountExport.BankAccountExport;
import io.github.mirco104.budget.account.AccountExport.CategoryExport;
import io.github.mirco104.budget.account.AccountExport.TransactionExport;
import io.github.mirco104.budget.bankaccount.BankAccountRepository;
import io.github.mirco104.budget.bankaccount.BankAccountSourceRepository;
import io.github.mirco104.budget.importer.ImportBatchRepository;
import io.github.mirco104.budget.category.CategoryOrder;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.account.AccountExport.PlannedItemExport;
import io.github.mirco104.budget.account.AccountExport.TermExport;
import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.plan.ItemTerm;
import io.github.mirco104.budget.plan.ItemTermRepository;
import io.github.mirco104.budget.plan.PlannedItemRepository;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.invitation.InvitationRepository;
import io.github.mirco104.budget.passwordreset.PasswordResetRepository;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import io.github.mirco104.budget.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US16: change display name, e-mail and password, export all own data, delete the account with all data. */
@Service
public class AccountService {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final PlannedItemRepository itemRepository;
    private final ItemTermRepository termRepository;
    private final io.github.mirco104.budget.goal.SavingGoalRepository goalRepository;
    private final io.github.mirco104.budget.bankaccount.BankStatementRepository statementRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountSourceRepository sourceRepository;
    private final ImportBatchRepository importBatchRepository;
    private final Clock clock;

    public AccountService(
            CurrentUser currentUser, UserRepository userRepository, PasswordEncoder passwordEncoder,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository, PlannedItemRepository itemRepository,
            ItemTermRepository termRepository, io.github.mirco104.budget.goal.SavingGoalRepository goalRepository,
            io.github.mirco104.budget.bankaccount.BankStatementRepository statementRepository,
            InvitationRepository invitationRepository,
            BankAccountRepository bankAccountRepository, BankAccountSourceRepository sourceRepository,
            ImportBatchRepository importBatchRepository, PasswordResetRepository passwordResetRepository,
            Clock clock) {
        this.passwordResetRepository = passwordResetRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.sourceRepository = sourceRepository;
        this.importBatchRepository = importBatchRepository;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.termRepository = termRepository;
        this.goalRepository = goalRepository;
        this.statementRepository = statementRepository;
        this.invitationRepository = invitationRepository;
        this.clock = clock;
    }

    /** 400 on currentPassword if it is wrong, on newPassword if it is shorter than 12 characters. */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = signedInUser();
        checkPassword(user, request.currentPassword(), "currentPassword");
        if (request.newPassword().length() < UserService.MIN_PASSWORD_LENGTH) {
            throw new InvalidFieldException("newPassword",
                    "Das Passwort muss mindestens " + UserService.MIN_PASSWORD_LENGTH + " Zeichen haben.");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public User changeDisplayName(ChangeDisplayNameRequest request) {
        User user = signedInUser();
        user.changeDisplayName(request.displayName().trim());
        return user;
    }

    /**
     * 400 on password if it is wrong, 409 if another account has the e-mail (ignoring case) and for the system admin,
     * whose e-mail only ADMIN_EMAIL sets (US25).
     */
    @Transactional
    public User changeEmail(ChangeEmailRequest request) {
        User user = signedInUser();
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new ConflictException("Die E-Mail des Systemadmins wird über ADMIN_EMAIL festgelegt.");
        }
        checkPassword(user, request.password(), "password");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(email)
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("Diese E-Mail-Adresse ist schon registriert.");
                });
        user.changeEmail(email);
        return user;
    }

    @Transactional(readOnly = true)
    public AccountExport export() {
        User user = signedInUser();
        List<BankAccountExport> bankAccounts = bankAccountRepository.findAllOrdered(user).stream()
                .map(a -> new BankAccountExport(a.getName(), a.getIban(), a.isDefaultAccount(), a.isArchived()))
                .toList();
        List<CategoryExport> categories = categoryRepository.findAllByOwner(user).stream()
                .sorted(CategoryOrder.TREE)
                .map(category -> new CategoryExport(category.getName(),
                        category.getParent() == null ? null : category.getParent().getName(), category.isArchived()))
                .toList();
        List<TransactionExport> transactions = transactionRepository.findByOwnerOrderByDateAscIdAsc(user).stream()
                .map(t -> new TransactionExport(t.getDate(), t.getAmount(), t.getDescription(),
                        t.getCategory() == null ? null : t.getCategory().getPath(), t.getAccount().getName(),
                        t.isTransfer(), t.isAuto(), t.getCounterparty(), t.getBankCategory()))
                .toList();
        Map<java.util.UUID, List<TermExport>> terms = termRepository.findByOwner(user).stream()
                .sorted(java.util.Comparator.comparing(ItemTerm::getTermDate))
                .collect(Collectors.groupingBy(ItemTerm::getItemKey, Collectors.mapping(
                        t -> new TermExport(t.getTermDate(), t.getTransaction() != null, t.isSkipped()),
                        Collectors.toList())));
        List<PlannedItemExport> items = itemRepository.findAllOf(user).stream()
                .map(i -> new PlannedItemExport(i.getItemKey(), i.getName(), i.getAmount(), i.isSaving(),
                        i.getCategory() == null ? null : i.getCategory().getPath(), i.getAccount().getName(),
                        i.getRhythmUnit(), i.getRhythmCount(), i.getAnchorDate(), i.getEntryMode(), i.isWorkdaysOnly(),
                        i.getValidFrom(), i.getValidTo(), terms.getOrDefault(i.getItemKey(), List.of())))
                .toList();
        return new AccountExport(
                Instant.now(clock),
                new Account(user.getEmail(), user.getDisplayName(), user.getRole(), user.getCreatedAt()),
                bankAccounts,
                categories, transactions, items);
    }

    /** Afterwards no data of the user exists any more (US16). The caller ends the session. */
    @Transactional
    public void delete(DeleteAccountRequest request) {
        User user = signedInUser();
        checkPassword(user, request.password(), "password");
        // There is always one system admin (US25)
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new ConflictException("Der Systemadmin kann sein Konto nicht löschen.");
        }
        termRepository.deleteByOwner(user);
        itemRepository.deleteByOwner(user);
        transactionRepository.deleteByOwner(user);
        goalRepository.deleteByOwner(user);
        statementRepository.deleteByOwner(user);
        transactionRepository.deleteByOwner(user);
        importBatchRepository.deleteByOwner(user);
        categoryRepository.deleteByOwner(user);
        sourceRepository.deleteByOwner(user);
        bankAccountRepository.deleteByOwner(user);
        invitationRepository.deleteByCreatedBy(user);
        passwordResetRepository.deleteByUserOrCreatedBy(user);
        // The bulk deletes cleared the persistence context, so the user is removed by id
        userRepository.deleteById(user.getId());
    }

    private User signedInUser() {
        return userRepository.findById(currentUser.details().id()).orElseThrow();
    }

    private void checkPassword(User user, String password, String field) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidFieldException(field, "Das Passwort ist falsch.");
        }
    }
}
