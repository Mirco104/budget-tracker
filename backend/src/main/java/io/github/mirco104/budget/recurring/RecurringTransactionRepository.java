package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every query is scoped to the owner (R15). */
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    /** Series active in the period: start not after "to", end (if set) not before "from" */
    @EntityGraph(attributePaths = {"category", "account"})
    @Query("""
            select r from RecurringTransaction r
            where r.owner = :owner and r.startDate <= :to and (r.endDate is null or r.endDate >= :from)
            order by r.startDate, r.id
            """)
    List<RecurringTransaction> findActiveBetween(User owner, LocalDate from, LocalDate to);

    Optional<RecurringTransaction> findByIdAndOwner(Long id, User owner);

    /** Start date of the earliest series, null without series. */
    @Query("select min(r.startDate) from RecurringTransaction r where r.owner = :owner")
    LocalDate findMinStartDate(User owner);

    /** US34: undoing an import deletes the series it created (R31). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecurringTransaction r where r.importBatch = :batch")
    void deleteByImportBatch(io.github.mirco104.budget.importer.ImportBatch batch);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByAccountId(Long accountId);

    /** R24: the smallest amount per category, as rows [category id, min(amount)]. */
    @Query("select r.category.id, min(r.amount) from RecurringTransaction r where r.owner = :owner group by r.category.id")
    List<Object[]> findMinAmountPerCategory(User owner);

    /** US16: all series of the user, for the export. */
    @EntityGraph(attributePaths = {"category", "account"})
    List<RecurringTransaction> findByOwnerOrderByStartDateAscIdAsc(User owner);

    /** US16: deleting an account; the exceptions must be gone first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecurringTransaction r where r.owner = :owner")
    void deleteByOwner(User owner);
}
