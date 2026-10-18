package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CategorySeederTest {

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void seedsCategoriesOnStartup() {
        assertThat(categoryRepository.count()).isEqualTo(21);
        assertThat(categoryRepository.findAll())
                .filteredOn(Category::isSystem)
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.getGroup()).isEqualTo(CategoryGroup.REMAINDER);
                    assertThat(c.getType()).isEqualTo(CategoryType.EXPENSE);
                });
    }

    @Test
    void doesNotSeedTwice() {
        categorySeeder.run(new DefaultApplicationArguments());

        assertThat(categoryRepository.count()).isEqualTo(21);
    }
}
