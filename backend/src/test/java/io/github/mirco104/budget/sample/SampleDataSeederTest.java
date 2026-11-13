package io.github.mirco104.budget.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.transaction.TransactionService;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import io.github.mirco104.budget.user.UserService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Decision 35: the sample data for Person A to D on an empty database. Own in-memory database and no ADMIN_EMAIL,
 * so no user exists before the seeder runs. The seeder is built by hand, because its profile is not active in tests.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sample-data-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH",
        "app.admin.email="
})
class SampleDataSeederTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2025, 1, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 10, 31);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private io.github.mirco104.budget.plan.PlanService planService;

    @Autowired
    private io.github.mirco104.budget.plan.PlannedItemRepository itemRepository;

    @Autowired
    private io.github.mirco104.budget.plan.TermService termService;

    @Autowired
    private io.github.mirco104.budget.bankaccount.BankAccountService bankAccountService;

    @Autowired
    private io.github.mirco104.budget.goal.SavingGoalService goalService;

    @Autowired
    private io.github.mirco104.budget.bankaccount.BankStatementRepository statementRepository;

    @Autowired
    private WebApplicationContext context;

    private SampleDataSeeder seeder;

    @BeforeEach
    void seed() {
        seeder = new SampleDataSeeder(userRepository, userService, categoryService, transactionService, planService,
                itemRepository, termService, transactionRepository, bankAccountService, goalService, statementRepository,
                "sample-only-password");
        seeder.run(new DefaultApplicationArguments());
    }

    @Test
    void createsPersonAToDWithTheirRoles() {
        assertThat(userRepository.findAll())
                .extracting(User::getEmail, User::getDisplayName, User::getRole)
                .containsExactlyInAnyOrder(
                        tuple("person-a@example.test", "Person A", Role.ADMIN),
                        tuple("person-b@example.test", "Person B", Role.USER),
                        tuple("person-c@example.test", "Person C", Role.USER),
                        tuple("person-d@example.test", "Person D", Role.USER));
    }

    @Test
    void changesNothingOnTheNextStart() {
        long transactions = transactionRepository.count();

        seeder.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(4);
        assertThat(transactionRepository.count()).isEqualTo(transactions);
    }

    @Test
    void bookingsOfPersonAFillJanuary2025ToOctober2026() {
        User a = user("person-a@example.test");

        assertThat(bookings(a, FIRST_DAY, FIRST_DAY.plusMonths(1).minusDays(1))).isNotEmpty();
        assertThat(bookings(a, LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31))).isNotEmpty();
        assertThat(bookings(a, LocalDate.of(2026, 10, 1), LAST_DAY)).isNotEmpty();
        assertThat(bookings(a, LAST_DAY.plusDays(1), LocalDate.of(2030, 12, 31))).isEmpty();
        assertThat(bookings(a, LocalDate.of(2020, 1, 1), FIRST_DAY.minusDays(1))).isEmpty();
    }

    @Test
    void personCStartsInJuly2025AndPersonDHasAlmostNothing() {
        assertThat(bookings(user("person-c@example.test"), FIRST_DAY, LocalDate.of(2025, 6, 30))).isEmpty();
        assertThat(bookings(user("person-c@example.test"), LocalDate.of(2025, 7, 1), LAST_DAY)).isNotEmpty();
        assertThat(bookings(user("person-d@example.test"), FIRST_DAY, LAST_DAY)).hasSizeBetween(1, 5);
    }

    @Test
    void dashboardOfPersonAShowsTheSampleValues() throws Exception {
        MockMvc mockMvc = TestUsers.mockMvcFor(context, user("person-a@example.test"));

        // Salary 2,850 and the Christmas bonus 1,000
        mockMvc.perform(get("/api/dashboard").param("month", "2025-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeTotal").value(3850.00));
        // Rent 950, utilities 120, streaming 12.99 and the savings plan 200 are automatic entries linked to their
        // terms, so they are fix (P5); the yearly and half-yearly items start later
        mockMvc.perform(get("/api/dashboard").param("month", "2025-01"))
                .andExpect(jsonPath("$.expenses.recurring").value(1282.99));
        // The plan of a category is the sum of its items (P3): 400 for Lebensmittel in 2025, 450 from 2026
        mockMvc.perform(get("/api/dashboard").param("month", "2026-03"))
                .andExpect(jsonPath("$.categories[?(@.name == 'Lebensmittel')].target").value(450.0));
        // The new salary series from 2026
        mockMvc.perform(get("/api/dashboard").param("month", "2026-03"))
                .andExpect(jsonPath("$.incomeTotal").value(3000.00));
    }

    private User user(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private List<Transaction> bookings(User owner, LocalDate from, LocalDate to) {
        return transactionRepository.findByOwnerAndDateBetweenOrderByDateDescIdDesc(owner, from, to);
    }
}
