package io.github.mirco104.budget.category;

import io.github.mirco104.budget.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CategoryTargetRepository extends JpaRepository<CategoryTarget, Long> {

    // R15: a target belongs to the user through its category
    List<CategoryTarget> findByCategoryOwnerAndValidFromLessThanEqual(User owner, LocalDate date);

    Optional<CategoryTarget> findByCategoryIdAndValidFrom(Long categoryId, LocalDate validFrom);

    boolean existsByCategoryId(Long categoryId);

    /** US16: all targets of the user, for the export. */
    List<CategoryTarget> findByCategoryOwnerOrderByValidFromAsc(User owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CategoryTarget t where t.category in (select c from Category c where c.owner = :owner)")
    void deleteByOwner(User owner);
}
