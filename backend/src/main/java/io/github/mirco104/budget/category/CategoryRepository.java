package io.github.mirco104.budget.category;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every query is scoped to the owner (R15). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** In no particular order; {@link CategoryOrder} sorts them (decision 38). */
    List<Category> findAllByOwner(User owner);

    Optional<Category> findByIdAndOwner(Long id, User owner);

    Optional<Category> findTopByOwnerOrderBySortOrderDesc(User owner);

    /** R32: top-level names are unique per user, ignoring case. */
    boolean existsByOwnerAndParentIsNullAndNameIgnoreCaseAndIdNot(User owner, String name, Long id);

    /** R32: subcategory names are unique per parent, ignoring case. */
    boolean existsByParentAndNameIgnoreCaseAndIdNot(Category parent, String name, Long id);

    boolean existsByParent(Category parent);

    Optional<Category> findFirstByOwnerAndParentIsNullAndNameIgnoreCase(User owner, String name);

    Optional<Category> findFirstByParentAndNameIgnoreCase(Category parent, String name);

    List<Category> findAllByParent(Category parent);

    /** US16: deleting an account; targets, transactions and series must be gone first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Category c where c.owner = :owner and c.parent is not null")
    void deleteSubcategoriesByOwner(User owner);

    /** US16: subcategories first, because they refer to their parent (R32). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Category c where c.owner = :owner")
    void deleteTopLevelByOwner(User owner);

    default void deleteByOwner(User owner) {
        deleteSubcategoriesByOwner(owner);
        deleteTopLevelByOwner(owner);
    }
}
