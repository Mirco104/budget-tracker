package io.github.mirco104.budget.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * US15, US25: the system admin is always the account with ADMIN_EMAIL (here application-test.properties for the
 * first start), checked on every start.
 */
@SpringBootTest
@Transactional
class AdminBootstrapTest {

    private static final String FIRST_ADMIN = "person-a@example.test";

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsTheSystemAdminWithDefaultCategories() {
        User admin = userRepository.findByEmail(FIRST_ADMIN).orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(admin.getDisplayName()).isEqualTo("Person A");
        assertThat(admin.getPasswordHash()).startsWith("$2").doesNotContain("test-only-password");
        assertThat(categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(admin)).hasSize(20);
    }

    @Test
    void changesNothingOnTheNextStartWithTheSameEmail() {
        long users = userRepository.count();

        adminBootstrap.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(userRepository.findByRole(Role.SYSTEM_ADMIN)).extracting(User::getEmail).containsExactly(FIRST_ADMIN);
    }

    @Test
    void anotherAdminEmailCreatesANewSystemAdminAndTheOldOneBecomesAdmin() {
        bootstrap(" Person-X@example.test ", "new-system-admin-password");

        User created = userRepository.findByEmail("person-x@example.test").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(created.getDisplayName()).isEqualTo("Person X");
        assertThat(categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(created)).hasSize(20);
        assertThat(userRepository.findByEmail(FIRST_ADMIN).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(userRepository.findByRole(Role.SYSTEM_ADMIN)).hasSize(1);
    }

    @Test
    void anExistingAccountWithTheAdminEmailBecomesSystemAdminAndKeepsItsPassword() {
        User existing = TestUsers.create(userService, "person-b@example.test");

        bootstrap("person-b@example.test", "ignored-admin-password");

        User promoted = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(promoted.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(passwordEncoder.matches(TestUsers.PASSWORD, promoted.getPasswordHash())).isTrue();
        assertThat(userRepository.findByEmail(FIRST_ADMIN).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(userRepository.findByRole(Role.SYSTEM_ADMIN)).hasSize(1);
    }

    @Test
    void withoutAdminEmailNothingChanges() {
        bootstrap("", "");

        assertThat(userRepository.findByRole(Role.SYSTEM_ADMIN)).extracting(User::getEmail).containsExactly(FIRST_ADMIN);
    }

    @Test
    void aNewAdminEmailWithoutPasswordKeepsTheCurrentSystemAdmin() {
        bootstrap("person-x@example.test", "");

        assertThat(userRepository.findByEmail("person-x@example.test")).isEmpty();
        assertThat(userRepository.findByRole(Role.SYSTEM_ADMIN)).extracting(User::getEmail).containsExactly(FIRST_ADMIN);
    }

    private void bootstrap(String email, String password) {
        new AdminBootstrap(userRepository, userService, email, password, "Person X")
                .run(new DefaultApplicationArguments());
    }
}
