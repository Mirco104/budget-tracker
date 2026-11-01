package io.github.mirco104.budget.category;

import io.github.mirco104.budget.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryTargetRepository extends JpaRepository<CategoryTarget, Long> {

    // R15: a target belongs to the user through its category
    List<CategoryTarget> findByCategoryOwnerAndValidFromLessThanEqual(User owner, LocalDate date);

    Optional<CategoryTarget> findByCategoryIdAndValidFrom(Long categoryId, LocalDate validFrom);

    boolean existsByCategoryId(Long categoryId);
}
