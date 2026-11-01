package io.github.mirco104.budget.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Ties on sortOrder (left by overlapping moves before they were renumbered) are broken by id. */
    List<Category> findAllByOrderBySortOrderAscIdAsc();

    Optional<Category> findTopByOrderBySortOrderDesc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
