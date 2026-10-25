package io.github.mirco104.budget.recurring;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringExceptionRepository extends JpaRepository<RecurringException, Long> {

    List<RecurringException> findByRecurringIn(Collection<RecurringTransaction> recurring);

    // Derived delete: removes through the EntityManager, so loaded exceptions do not go stale
    void deleteByRecurringAndOccurrenceDateGreaterThanEqual(RecurringTransaction recurring, LocalDate from);
}
