package io.github.mirco104.budget.category;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query is scoped to the owner (R15). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Ties on sortOrder (left by overlapping moves before they were renumbered) are broken by id. */
    List<Category> findAllByOwnerOrderBySortOrderAscIdAsc(User owner);

    Optional<Category> findByIdAndOwner(Long id, User owner);

    Optional<Category> findTopByOwnerOrderBySortOrderDesc(User owner);

    boolean existsByOwnerAndNameIgnoreCase(User owner, String name);

    boolean existsByOwnerAndNameIgnoreCaseAndIdNot(User owner, String name, Long id);
}
