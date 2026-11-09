package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.importer.ImportBatch;
import io.github.mirco104.budget.transaction.Transaction;
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

    /** "Alle Einträge" deletes a part of the series with all its exceptions (R13); a replacing booking stays (R28). */
    void deleteByRecurring(RecurringTransaction recurring);

    /** R28: the exception that points to the transaction, removed so the occurrence counts again. */
    void deleteByTransaction(Transaction transaction);

    @Query("select e from RecurringException e join fetch e.recurring where e.transaction.id in :ids")
    List<RecurringException> findByTransactionIdIn(Collection<Long> ids);

    @Query("select e.transaction.id from RecurringException e where e.transaction.owner = :owner")
    List<Long> findReplacingTransactionIds(User owner);

    List<RecurringException> findByRecurringAndOccurrenceDateGreaterThanEqualAndTransactionIsNotNull(
            RecurringTransaction recurring, LocalDate from);

    /** US34: undoing an import; before its transactions. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RecurringException e
            where e.transaction in (select t from Transaction t where t.importBatch = :batch)
            """)
    void deleteByImportBatch(ImportBatch batch);

    /** US34: undoing an import; the exceptions of the series it created (R31), before those series. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RecurringException e
            where e.recurring in (select r from RecurringTransaction r where r.importBatch = :batch)
            """)
    void deleteOfSeriesCreatedBy(ImportBatch batch);

    /** US16: deleting an account. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RecurringException e
            where e.recurring in (select r from RecurringTransaction r where r.owner = :owner)
            """)
    void deleteByOwner(User owner);
}
