package io.github.mirco104.budget.category;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every query is scoped to the owner (R15). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Ties on sortOrder (left by overlapping moves before they were renumbered) are broken by id. */
    List<Category> findAllByOwnerOrderBySortOrderAscIdAsc(User owner);

    Optional<Category> findByIdAndOwner(Long id, User owner);

    Optional<Category> findTopByOwnerOrderBySortOrderDesc(User owner);

    boolean existsByOwnerAndNameIgnoreCase(User owner, String name);

    boolean existsByOwnerAndNameIgnoreCaseAndIdNot(User owner, String name, Long id);

    /** US16: deleting an account; targets, transactions and series must be gone first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Category c where c.owner = :owner")
    void deleteByOwner(User owner);
}
