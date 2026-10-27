package io.github.mirco104.budget.recurring;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    /** Series active in the period: start not after "to", end (if set) not before "from" */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select r from RecurringTransaction r
            where r.startDate <= :to and (r.endDate is null or r.endDate >= :from)
            order by r.startDate, r.id
            """)
    List<RecurringTransaction> findActiveBetween(LocalDate from, LocalDate to);

    /** Start date of the earliest series, null without series. */
    @Query("select min(r.startDate) from RecurringTransaction r")
    LocalDate findMinStartDate();
}
