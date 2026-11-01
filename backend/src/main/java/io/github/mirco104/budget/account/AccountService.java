package io.github.mirco104.budget.account;

import io.github.mirco104.budget.account.AccountExport.Account;
import io.github.mirco104.budget.account.AccountExport.CategoryExport;
import io.github.mirco104.budget.account.AccountExport.RecurringExport;
import io.github.mirco104.budget.account.AccountExport.TargetExport;
import io.github.mirco104.budget.account.AccountExport.TransactionExport;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.category.CategoryTarget;
import io.github.mirco104.budget.category.CategoryTargetRepository;
import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.invitation.InvitationRepository;
import io.github.mirco104.budget.recurring.RecurringException;
import io.github.mirco104.budget.recurring.RecurringExceptionRepository;
import io.github.mirco104.budget.recurring.RecurringTransaction;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import io.github.mirco104.budget.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US16: change the password, export all own data, delete the account with all data. */
@Service
public class AccountService {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;
    private final CategoryTargetRepository categoryTargetRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringRepository;
    private final RecurringExceptionRepository exceptionRepository;
    private final InvitationRepository invitationRepository;
    private final Clock clock;

    public AccountService(
            CurrentUser currentUser, UserRepository userRepository, PasswordEncoder passwordEncoder,
            CategoryRepository categoryRepository, CategoryTargetRepository categoryTargetRepository,
            TransactionRepository transactionRepository, RecurringTransactionRepository recurringRepository,
            RecurringExceptionRepository exceptionRepository, InvitationRepository invitationRepository,
            Clock clock) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryRepository = categoryRepository;
        this.categoryTargetRepository = categoryTargetRepository;
        this.transactionRepository = transactionRepository;
        this.recurringRepository = recurringRepository;
        this.exceptionRepository = exceptionRepository;
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

    @Transactional(readOnly = true)
    public AccountExport export() {
        User user = signedInUser();
        Map<Long, List<TargetExport>> targets = categoryTargetRepository.findByCategoryOwnerOrderByValidFromAsc(user)
                .stream()
                .map(CategoryTarget::toEntry)
                .collect(Collectors.groupingBy(TargetEntry::categoryId, Collectors.mapping(
                        entry -> new TargetExport(entry.validFrom(), entry.targetAmount(), entry.targetPercent()),
                        Collectors.toList())));
        List<CategoryExport> categories = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .map(category -> new CategoryExport(category.getName(), category.getSortOrder(),
                        category.isArchived(), targets.getOrDefault(category.getId(), List.of())))
                .toList();
        List<TransactionExport> transactions = transactionRepository.findByOwnerOrderByDateAscIdAsc(user).stream()
                .map(t -> new TransactionExport(t.getDate(), t.getAmount(), t.getDescription(),
                        t.getCategory().getName()))
                .toList();
        List<RecurringTransaction> series = recurringRepository.findByOwnerOrderByStartDateAscIdAsc(user);
        Map<Long, List<LocalDate>> exceptions = series.isEmpty() ? Map.of()
                : exceptionRepository.findByRecurringIn(series).stream()
                        .collect(Collectors.groupingBy(e -> e.getRecurring().getId(), Collectors.mapping(
                                RecurringException::getOccurrenceDate, Collectors.toList())));
        List<RecurringExport> recurring = series.stream()
                .map(r -> new RecurringExport(r.getSeriesId(), r.getAmount(), r.getDescription(),
                        r.getCategory().getName(), r.getInterval(), r.getStartDate(), r.getEndDate(),
                        exceptions.getOrDefault(r.getId(), List.of()).stream().sorted().toList()))
                .toList();
        return new AccountExport(
                Instant.now(clock),
                new Account(user.getEmail(), user.getDisplayName(), user.getRole(), user.getCreatedAt()),
                categories, transactions, recurring);
    }

    /** Afterwards no data of the user exists any more (US16). The caller ends the session. */
    @Transactional
    public void delete(DeleteAccountRequest request) {
        User user = signedInUser();
        checkPassword(user, request.password(), "password");
        exceptionRepository.deleteByOwner(user);
        recurringRepository.deleteByOwner(user);
        transactionRepository.deleteByOwner(user);
        categoryTargetRepository.deleteByOwner(user);
        categoryRepository.deleteByOwner(user);
        invitationRepository.deleteByCreatedBy(user);
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
