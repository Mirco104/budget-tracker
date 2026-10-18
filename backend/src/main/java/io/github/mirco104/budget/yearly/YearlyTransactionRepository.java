package io.github.mirco104.budget.yearly;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YearlyTransactionRepository extends JpaRepository<YearlyTransaction, Long> {

    // monthStart is the first day of the month, like validFrom and validTo
    @EntityGraph(attributePaths = "category")
    @Query("""
            select y from YearlyTransaction y
            where y.validFrom <= :monthStart and (y.validTo is null or y.validTo >= :monthStart)
            order by y.validFrom desc, y.id desc
            """)
    List<YearlyTransaction> findValidIn(@Param("monthStart") LocalDate monthStart);

    @EntityGraph(attributePaths = "category")
    List<YearlyTransaction> findAllByOrderByIdAsc();
}
