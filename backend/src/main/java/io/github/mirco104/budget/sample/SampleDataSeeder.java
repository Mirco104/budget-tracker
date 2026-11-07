package io.github.mirco104.budget.sample;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.category.UpdateTargetRequest;
import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.recurring.RecurringTransactionRequest;
import io.github.mirco104.budget.recurring.RecurringTransactionService;
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
 * to October 2026. Runs only on an empty database and before {@link io.github.mirco104.budget.user.AdminBootstrap},
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
    private final RecurringTransactionService recurringTransactionService;
    private final String password;

    public SampleDataSeeder(
            UserRepository userRepository, UserService userService, CategoryService categoryService,
            TransactionService transactionService, RecurringTransactionService recurringTransactionService,
            @Value("${app.sample.password}") String password) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.recurringTransactionService = recurringTransactionService;
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

    /** Many bookings in every month, targets with a change in 2026, a salary raise and series for the reserve. */
    private void personA(Data d) {
        d.series("2850", "Gehalt", "Gehalt/Lohn (netto)", RecurringInterval.MONTHLY, "2025-01-01", "2025-12-31");
        d.series("3000", "Gehalt", "Gehalt/Lohn (netto)", RecurringInterval.MONTHLY, "2026-01-01", null);
        d.series("-950", "Miete", "Wohnen", RecurringInterval.MONTHLY, "2025-01-01", null);
        d.series("-120", "Strom und Internet", "Nebenkosten", RecurringInterval.MONTHLY, "2025-01-05", null);
        d.series("-12.99", "Streaming", "Freizeit", RecurringInterval.MONTHLY, "2025-01-10", null);
        d.series("-200", "ETF-Sparplan", "Investitionen", RecurringInterval.MONTHLY, "2025-01-15", null);
        d.series("-85", "Haftpflicht", "Versicherungen", RecurringInterval.YEARLY, "2025-03-15", null);
        d.series("-240", "Kfz-Versicherung", "Versicherungen", RecurringInterval.HALF_YEARLY, "2025-04-01", null);

        d.target("Wohnen", FIRST_MONTH, "950");
        d.target("Nebenkosten", FIRST_MONTH, "130");
        d.target("Versicherungen", FIRST_MONTH, "60");
        d.target("Investitionen", FIRST_MONTH, "200");
        d.target("Gesundheit", FIRST_MONTH, "20");
        d.target("Lebensmittel", FIRST_MONTH, "400");
        d.target("Mobilität", FIRST_MONTH, "150");
        d.target("Freizeit", FIRST_MONTH, "150");
        d.target("Urlaub", FIRST_MONTH, "100");
        d.target("Persönlicher Bedarf", FIRST_MONTH, "60");
        d.target("Lebensmittel", YearMonth.of(2026, 1), "450");

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
        d.series("1800", "Gehalt (Teilzeit)", "Gehalt/Lohn (netto)", RecurringInterval.MONTHLY, "2025-01-01", null);
        d.series("-700", "Miete", "Wohnen", RecurringInterval.MONTHLY, "2025-01-01", null);
        d.series("-45", "Handy und Internet", "Nebenkosten", RecurringInterval.MONTHLY, "2025-01-03", null);
        d.series("-120", "Software-Abo", "Selbstständigkeit", RecurringInterval.YEARLY, "2025-02-01", null);

        d.target("Wohnen", FIRST_MONTH, "700");
        d.target("Nebenkosten", FIRST_MONTH, "45");
        d.target("Lebensmittel", FIRST_MONTH, "250");
        d.target("Freizeit", FIRST_MONTH, "80");

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
     * Starts in July 2025 (average over fewer than 12 months, R23) and has a loan that ends. Lebensmittel and Freizeit
     * were 12 % and 4 % of the salary before decision 36; now the same amounts in euro.
     */
    private void personC(Data d) {
        YearMonth start = YearMonth.of(2025, 7);
        d.series("2400", "Gehalt", "Gehalt/Lohn (netto)", RecurringInterval.MONTHLY, "2025-07-01", null);
        d.series("-620", "Miete", "Wohnen", RecurringInterval.MONTHLY, "2025-07-01", null);
        d.series("-180", "Autokredit", "Kredite", RecurringInterval.MONTHLY, "2025-07-01", "2026-06-30");
        d.series("-35", "Patenschaft", "Spenden", RecurringInterval.MONTHLY, "2025-07-01", null);

        d.target("Wohnen", start, "620");
        d.target("Kredite", start, "180");
        d.target("Lebensmittel", start, "288");
        d.target("Freizeit", start, "96");
        d.target("Mobilität", start, "60");

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
                    new TransactionRequest(BigDecimal.valueOf(cents, 2), description, date, id(category), null, null, null));
        }

        void series(String amount, String description, String category, RecurringInterval interval,
                String startDate, String endDate) {
            recurringTransactionService.create(new RecurringTransactionRequest(
                    new BigDecimal(amount), description, id(category), interval, LocalDate.parse(startDate),
                    endDate == null ? null : LocalDate.parse(endDate), null));
        }

        void target(String category, YearMonth from, String amount) {
            categoryService.updateTarget(id(category), from, new UpdateTargetRequest(new BigDecimal(amount)));
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
