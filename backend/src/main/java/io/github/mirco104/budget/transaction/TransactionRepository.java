package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every query is scoped to the owner (R15). */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = {"category", "account"})
    List<Transaction> findByOwnerAndDateBetweenOrderByDateDescIdDesc(User owner, LocalDate from, LocalDate to);

    /** R27: the transactions of the period without transfers, for every calculation. */
    @EntityGraph(attributePaths = {"category", "account"})
    @Query("""
            select t from Transaction t
            where t.owner = :owner and t.transfer = false and t.date between :from and :to
            order by t.date desc, t.id desc
            """)
    List<Transaction> findCountedBetween(User owner, LocalDate from, LocalDate to);

    /** R29: every transaction of the user with a category, for the category suggestion. */
    @EntityGraph(attributePaths = "category")
    List<Transaction> findByOwnerAndTransferFalse(User owner);

    Optional<Transaction> findByIdAndOwner(Long id, User owner);

    /** R30: which of the fingerprints the user has imported already. */
    @Query("select t.fingerprint from Transaction t where t.owner = :owner and t.fingerprint in :fingerprints")
    List<String> findImportedFingerprints(User owner, java.util.Collection<String> fingerprints);

    /** US34: the stored transactions the import made counterparts of a transfer (R27). */
    List<Transaction> findByTransferBatch(io.github.mirco104.budget.importer.ImportBatch batch);

    boolean existsByCategoryBeforeTransferId(Long categoryId);

    /** US34: undoing an import. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Transaction t where t.importBatch = :batch")
    void deleteByImportBatch(io.github.mirco104.budget.importer.ImportBatch batch);

    /** Date of the earliest transaction that is no transfer (R27), null without one. */
    @Query("select min(t.date) from Transaction t where t.owner = :owner and t.transfer = false")
    LocalDate findMinDate(User owner);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByAccountId(Long accountId);

    /** R24: the smallest amount per category, as rows [category id, min(amount)]. */
    @Query("""
            select t.category.id, min(t.amount) from Transaction t
            where t.owner = :owner and t.transfer = false group by t.category.id
            """)
    List<Object[]> findMinAmountPerCategory(User owner);

    /** US16: all transactions of the user, for the export. */
    @EntityGraph(attributePaths = {"category", "account"})
    List<Transaction> findByOwnerOrderByDateAscIdAsc(User owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Transaction t where t.owner = :owner")
    void deleteByOwner(User owner);
}
