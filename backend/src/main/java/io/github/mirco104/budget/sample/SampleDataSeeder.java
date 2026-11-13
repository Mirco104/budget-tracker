package io.github.mirco104.budget.sample;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.bankaccount.BankAccountService;
import io.github.mirco104.budget.plan.EntryMode;
import io.github.mirco104.budget.plan.PlanService;
import io.github.mirco104.budget.plan.PlannedItem;
import io.github.mirco104.budget.plan.PlannedItemRequest;
import io.github.mirco104.budget.plan.PlannedItemRepository;
import io.github.mirco104.budget.plan.RhythmUnit;
import io.github.mirco104.budget.plan.TermService;
import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.transaction.TransactionRequest;
import io.github.mirco104.budget.transaction.TransactionService;
import io.github.mirco104.budget.user.AppUserDetails;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import io.github.mirco104.budget.user.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fictional sample data for the local profile "sample" (decision 35): Person A to D with bookings from January 2025
 * to October 2026 and a plan of planned items (section 19). The terms of automatic items up to October 2026 are
 * bookings marked "automatisch", as after the conversion of V14; later ones the app enters itself (P6). Runs only on an empty database and before {@link io.github.mirco104.budget.user.AdminBootstrap},
 * which then adds the system admin. Everything goes through the services, so all rules and validations apply.
 * Amounts vary with the month index instead of random numbers, so every reset gives the same data.
 */
@Component
@Profile("sample & !postgres")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private static final YearMonth FIRST_MONTH = YearMonth.of(2025, 1);
    private static final YearMonth LAST_MONTH = YearMonth.of(2026, 10);

    private final UserRepository userRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final PlanService planService;
    private final PlannedItemRepository itemRepository;
    private final TermService termService;
    private final TransactionRepository transactionRepository;
    private final BankAccountService bankAccountService;
    private final io.github.mirco104.budget.goal.SavingGoalService goalService;
    private final io.github.mirco104.budget.bankaccount.BankStatementRepository statementRepository;
    private final String password;

    public SampleDataSeeder(
            UserRepository userRepository, UserService userService, CategoryService categoryService,
            TransactionService transactionService, PlanService planService, PlannedItemRepository itemRepository,
            TermService termService, TransactionRepository transactionRepository, BankAccountService bankAccountService,
            io.github.mirco104.budget.goal.SavingGoalService goalService,
            io.github.mirco104.budget.bankaccount.BankStatementRepository statementRepository,
            @Value("${app.sample.password}") String password) {
        this.goalService = goalService;
        this.statementRepository = statementRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.planService = planService;
        this.itemRepository = itemRepository;
        this.termService = termService;
        this.transactionRepository = transactionRepository;
        this.bankAccountService = bankAccountService;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        seed("person-a@example.test", "Person A", Role.ADMIN, this::personA);
        seed("person-b@example.test", "Person B", Role.USER, this::personB);
        seed("person-c@example.test", "Person C", Role.USER, this::personC);
        seed("person-d@example.test", "Person D", Role.USER, this::personD);
        log.info("Created the sample data for Person A to D.");
    }

    /** Many bookings in every month, budgets with a change in 2026, a salary raise, rare items and a saving item. */
    private void personA(Data d) {
        d.fixed("2850", "Gehalt", "Gehalt/Lohn (netto)", 1, "2025-01-01", "2025-12-31");
        d.fixed("3000", "Gehalt", "Gehalt/Lohn (netto)", 1, "2026-01-01", null);
        d.fixed("-950", "Miete", "Wohnen", 1, "2025-01-01", null);
        d.fixed("-120", "Strom und Internet", "Nebenkosten", 1, "2025-01-05", null);
        d.fixed("-12.99", "Streaming", "Freizeit", 1, "2025-01-10", null);
        d.fixed("-200", "ETF-Sparplan", "Investitionen", 1, "2025-01-15", null);
        d.fixed("-85", "Haftpflicht", "Versicherungen", 12, "2025-03-15", null);
        d.fixed("-240", "Kfz-Versicherung", "Versicherungen", 6, "2025-04-01", null);
        // Concept 3.4, P8: a savings account that holds the reserve of the car insurance
        d.savingsAccountFor("Tagesgeld", "Kfz-Versicherung", "500");

        d.budget("20", "Gesundheit", FIRST_MONTH);
        d.budget("400", "Lebensmittel", FIRST_MONTH);
        d.budget("150", "Mobilität", FIRST_MONTH);
        d.budget("150", "Freizeit", FIRST_MONTH);
        d.budget("100", "Urlaub", FIRST_MONTH);
        d.budget("60", "Persönlicher Bedarf", FIRST_MONTH);
        d.budget("450", "Lebensmittel", YearMonth.of(2026, 1));
        d.saving("150", "Notgroschen", "2026-01-28");

        int i = 0;
        for (YearMonth month = FIRST_MONTH; !month.isAfter(LAST_MONTH); month = month.plusMonths(1), i++) {
            int[] days = {3, 10, 17, 24};
            for (int k = 0; k < days.length; k++) {
                d.book(-(6840 + ((i * 7 + k * 5) % 20) * 150), "Wocheneinkauf", month.atDay(days[k]), "Lebensmittel");
            }
            d.book(-(5500 + (i * 3 % 15) * 100), "Tanken", month.atDay(8), "Mobilität");
            d.book(-(5200 + (i * 5 % 12) * 100), "Tanken", month.atDay(22), "Mobilität");
            d.book(-(3500 + (i * 11 % 40) * 100), "Restaurant", month.atDay(14), "Freizeit");
            d.book(-(1800 + (i % 7) * 200), "Drogerie", month.atDay(6), "Persönlicher Bedarf");
            if (i % 3 == 2) {
                d.book(-1495, "Apotheke", month.atDay(12), "Gesundheit");
            }
            if (month.getMonthValue() == 8) {
                d.book(month.getYear() == 2025 ? -120000 : -135000, "Sommerurlaub", month.atDay(9), "Urlaub");
            }
        }
        d.book(1250, "Pfand", LocalDate.of(2025, 3, 21), "Lebensmittel");
        d.book(100000, "Weihnachtsgeld", LocalDate.of(2025, 11, 25), "Sonstige Einnahmen");
        d.book(-25000, "Weihnachtsgeschenke", LocalDate.of(2025, 12, 15), "Geschenke");
    }

    /** Fewer bookings and an irregular income from self-employment. */
    private void personB(Data d) {
        d.fixed("1800", "Gehalt (Teilzeit)", "Gehalt/Lohn (netto)", 1, "2025-01-01", null);
        d.fixed("-700", "Miete", "Wohnen", 1, "2025-01-01", null);
        d.fixed("-45", "Handy und Internet", "Nebenkosten", 1, "2025-01-03", null);
        d.fixed("-120", "Software-Abo", "Selbstständigkeit", 12, "2025-02-01", null);

        d.budget("250", "Lebensmittel", FIRST_MONTH);
        d.budget("80", "Freizeit", FIRST_MONTH);

        int i = 0;
        for (YearMonth month = FIRST_MONTH; !month.isAfter(LAST_MONTH); month = month.plusMonths(1), i++) {
            d.book(-(8500 + (i * 9 % 30) * 100), "Einkauf", month.atDay(5), "Lebensmittel");
            d.book(-(7900 + (i * 4 % 25) * 100), "Einkauf", month.atDay(19), "Lebensmittel");
            if (i % 3 == 1) {
                d.book(40000 + i * 2500, "Auftrag Grafikdesign", month.atDay(20), "Einnahmen Selbstständigkeit");
            }
            if (i % 2 == 0) {
                d.book(-2400, "Kino", month.atDay(11), "Freizeit");
            }
        }
    }

    /**
     * Starts in July 2025 (average over fewer than 12 months, R23) and has a loan that ends; a weekly item (P2).
     */
    private void personC(Data d) {
        YearMonth start = YearMonth.of(2025, 7);
        d.fixed("2400", "Gehalt", "Gehalt/Lohn (netto)", 1, "2025-07-01", null);
        d.fixed("-620", "Miete", "Wohnen", 1, "2025-07-01", null);
        d.fixed("-180", "Autokredit", "Kredite", 1, "2025-07-01", "2026-06-30");
        d.fixed("-35", "Patenschaft", "Spenden", 1, "2025-07-01", null);

        d.budget("288", "Lebensmittel", start);
        d.budget("96", "Freizeit", start);
        d.budget("60", "Mobilität", start);
        d.weekly("-15", "Wochenmarkt", "Lebensmittel", "2025-07-04");

        int i = 0;
        for (YearMonth month = start; !month.isAfter(LAST_MONTH); month = month.plusMonths(1), i++) {
            int[] days = {4, 13, 26};
            for (int k = 0; k < days.length; k++) {
                d.book(-(6000 + ((i * 13 + k * 7) % 25) * 100), "Supermarkt", month.atDay(days[k]), "Lebensmittel");
            }
            d.book(-5800, "Bahnticket", month.atDay(1), "Mobilität");
            if (i % 4 == 0) {
                d.book(35000, "Nachhilfe", month.atDay(28), "Nebenjob");
            }
        }
    }

    /** Almost nothing, to see the empty states with a user who has data. */
    private void personD(Data d) {
        d.book(-4250, "Wocheneinkauf", LocalDate.of(2026, 9, 12), "Lebensmittel");
        d.book(150000, "Gehalt", LocalDate.of(2026, 10, 1), "Gehalt/Lohn (netto)");
        d.book(-1999, "Konzertkarte", LocalDate.of(2026, 10, 5), "Freizeit");
    }

    /** Creates the user and adds the data while signed in as that user, because the services use CurrentUser. */
    private void seed(String email, String displayName, Role role, Consumer<Data> data) {
        User user = userService.create(email, displayName, password, role);
        AppUserDetails details = AppUserDetails.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
        try {
            Map<String, Long> categories = categoryService.findAllOrdered().stream()
                    .collect(Collectors.toMap(Category::getName, Category::getId));
            data.accept(new Data(categories));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Shortcuts for one user; amounts of single bookings are in cents. */
    private final class Data {

        private final Map<String, Long> categories;

        private Data(Map<String, Long> categories) {
            this.categories = categories;
        }

        void book(long cents, String description, LocalDate date, String category) {
            transactionService.create(
                    new TransactionRequest(BigDecimal.valueOf(cents, 2), description, date, id(category), null, null, null,
                            null, null, null));
        }

        /** An automatic item with a month rhythm (decision 51); its terms up to October 2026 become bookings. */
        void fixed(String amount, String name, String category, int months, String anchor, String end) {
            LocalDate anchorDate = LocalDate.parse(anchor);
            PlannedItem item = create(new PlannedItemRequest(name, new BigDecimal(amount), false, id(category), null,
                    RhythmUnit.MONTH, months, anchorDate, EntryMode.AUTO, false, YearMonth.from(anchorDate), null));
            if (end != null) {
                planService.delete(item.getId(), YearMonth.from(LocalDate.parse(end)).plusMonths(1));
                item = itemRepository.findById(item.getId()).orElseThrow();
            }
            enterPastTerms(item);
        }

        /**
         * A savings account (role Sparen) with a fictional balance from the bank on the last sample day, linked to the
         * reserve pot of the rare item with the given name (P8, P11).
         */
        void savingsAccountFor(String accountName, String itemName, String balance) {
            io.github.mirco104.budget.bankaccount.BankAccount account = bankAccountService.create(accountName, null);
            account.setRole(io.github.mirco104.budget.bankaccount.AccountRole.SAVINGS);
            statementRepository.save(statement(account, LAST_MONTH.atEndOfMonth(), new BigDecimal(balance)));
            itemRepository.findAllOf(currentOwner()).stream()
                    .filter(item -> item.getName().equals(itemName))
                    .findFirst()
                    .ifPresent(item -> planService.linkReserve(item.getId(), account.getId()));
        }

        private io.github.mirco104.budget.bankaccount.BankStatement statement(
                io.github.mirco104.budget.bankaccount.BankAccount account, LocalDate date, BigDecimal amount) {
            io.github.mirco104.budget.bankaccount.BankStatement statement =
                    new io.github.mirco104.budget.bankaccount.BankStatement(currentOwner(), account, date);
            statement.setAmount(amount);
            return statement;
        }

        /** An automatic weekly item (decision 50). */
        void weekly(String amount, String name, String category, String anchor) {
            LocalDate anchorDate = LocalDate.parse(anchor);
            enterPastTerms(create(new PlannedItemRequest(name, new BigDecimal(amount), false, id(category), null,
                    RhythmUnit.WEEK, 1, anchorDate, EntryMode.AUTO, false, YearMonth.from(anchorDate), null)));
        }

        /** A budget for variable costs, without a fixed date (P4). A later month starts a new version. */
        void budget(String amount, String category, YearMonth from) {
            String name = categoryName(category);
            itemRepository.findAllOf(currentOwner()).stream()
                    .filter(i -> i.getRhythmUnit() == null && i.getName().equals(name) && i.getValidTo() == null)
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> planService.update(existing.getId(), new PlannedItemRequest(name,
                                    new BigDecimal(amount).negate(), false, id(category), null, null, null, null,
                                    EntryMode.MANUAL, false, from, null)),
                            () -> planService.create(new PlannedItemRequest(name, new BigDecimal(amount).negate(),
                                    false, id(category), null, null, null, null, EntryMode.MANUAL, false, from, null)));
        }

        /**
         * A saving item for a saving goal of the same name, ticked off by the user (decision 51, concept 3.6); no
         * bookings, they are ticked off in the app.
         */
        void saving(String amount, String name, String anchor) {
            LocalDate anchorDate = LocalDate.parse(anchor);
            long goal = goalService.create(new io.github.mirco104.budget.goal.SavingGoalService.GoalRequest(
                    name, new BigDecimal("3000"), null)).id();
            planService.create(new PlannedItemRequest(name, new BigDecimal(amount), true, null, null,
                    RhythmUnit.MONTH, 1, anchorDate, EntryMode.TICK, false, YearMonth.from(anchorDate), goal));
        }

        private PlannedItem create(PlannedItemRequest request) {
            return itemRepository.findById(planService.create(request).id()).orElseThrow();
        }

        // Like V14: every term up to the last sample month is a booking marked "automatisch"
        private void enterPastTerms(PlannedItem item) {
            for (LocalDate date : item.schedule().termsBetween(item.getValidFrom().atDay(1), LAST_MONTH.atEndOfMonth())) {
                Transaction booking = new Transaction(currentOwner(), item.getAmount(), item.getName(), date,
                        item.getCategory(), bankAccountService.findBookable(null));
                booking.markAuto();
                termService.link(item.getItemKey(), date, transactionRepository.save(booking));
            }
        }

        private User currentOwner() {
            return userRepository.findByEmail(
                    ((AppUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername())
                    .orElseThrow();
        }

        private String categoryName(String category) {
            id(category);
            return category;
        }

        private long id(String category) {
            Long id = categories.get(category);
            if (id == null) {
                throw new IllegalStateException("Unknown default category: " + category);
            }
            return id;
        }
    }
}
