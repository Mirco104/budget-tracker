package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.user.User;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ItemTermRepository extends JpaRepository<ItemTerm, Long> {

    List<ItemTerm> findByOwner(User owner);

    @Query("select t from ItemTerm t where t.owner = :owner and t.termDate between :from and :to")
    List<ItemTerm> findBetween(User owner, LocalDate from, LocalDate to);

    Optional<ItemTerm> findByOwnerAndItemKeyAndTermDate(User owner, UUID itemKey, LocalDate termDate);

    Optional<ItemTerm> findByTransaction(Transaction transaction);

    @Query("select t.transaction.id from ItemTerm t where t.transaction.id in :ids")
    List<Long> findLinkedTransactionIds(Collection<Long> ids);

    @Modifying
    @Query("delete from ItemTerm t where t.owner = :owner and t.itemKey = :itemKey")
    void deleteByItemKey(User owner, UUID itemKey);

    /** US34: links to the bookings of an import go with the import, so their terms are open again. */
    @Modifying
    @Query("delete from ItemTerm t where t.transaction.id in (select x.id from Transaction x where x.importBatch = :batch)")
    void deleteLinkedToBatch(io.github.mirco104.budget.importer.ImportBatch batch);

    @Modifying
    @Query("delete from ItemTerm t where t.itemKey in :keys")
    void deleteByItemKeys(Collection<UUID> keys);

    @Modifying
    @Query("delete from ItemTerm t where t.owner = :owner")
    void deleteByOwner(User owner);
}
