package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.user.User;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecurringExceptionRepository extends JpaRepository<RecurringException, Long> {

    List<RecurringException> findByRecurringIn(Collection<RecurringTransaction> recurring);

    // Derived delete: removes through the EntityManager, so loaded exceptions do not go stale
    void deleteByRecurringAndOccurrenceDateGreaterThanEqual(RecurringTransaction recurring, LocalDate from);

    /** US16: deleting an account. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RecurringException e
            where e.recurring in (select r from RecurringTransaction r where r.owner = :owner)
            """)
    void deleteByOwner(User owner);
}
