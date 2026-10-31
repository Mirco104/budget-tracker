package io.github.mirco104.budget.category;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryTargetRepository extends JpaRepository<CategoryTarget, Long> {

    List<CategoryTarget> findByValidFromLessThanEqual(LocalDate date);

    Optional<CategoryTarget> findByCategoryIdAndValidFrom(Long categoryId, LocalDate validFrom);

    boolean existsByCategoryId(Long categoryId);
}
