package io.github.mirco104.budget.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.category.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** US15: the first admin comes from the environment (here application-test.properties) on the first start. */
@SpringBootTest
@Transactional
class AdminBootstrapTest {

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void createsTheSystemAdminWithDefaultCategories() {
        User admin = userRepository.findByEmail("person-a@example.test").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(admin.getDisplayName()).isEqualTo("Person A");
        assertThat(admin.getPasswordHash()).startsWith("$2").doesNotContain("test-only-password");
        assertThat(categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(admin)).hasSize(20);
    }

    @Test
    void createsNoSecondAdminOnTheNextStart() {
        long users = userRepository.count();

        adminBootstrap.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(users);
    }
}
