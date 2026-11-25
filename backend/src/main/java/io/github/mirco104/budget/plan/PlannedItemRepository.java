package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PlannedItemRepository extends JpaRepository<PlannedItem, Long> {

    // Fetches category and account, the plan and the term list need their names
    @Query("""
            select i from PlannedItem i left join fetch i.category join fetch i.account
            where i.owner = :owner order by i.name, i.validFrom
            """)
    List<PlannedItem> findAllOf(User owner);

    Optional<PlannedItem> findByIdAndOwner(Long id, User owner);

    List<PlannedItem> findByItemKeyAndOwner(UUID itemKey, User owner);

    boolean existsByCategoryId(Long categoryId);

    /** R24: the smallest amount per category, as rows [category id, min(amount)]. */
    @Query("""
            select i.category.id, min(i.amount) from PlannedItem i
            where i.owner = :owner and i.category is not null group by i.category.id
            """)
    List<Object[]> findMinAmountPerCategory(User owner);

    boolean existsByAccountId(Long accountId);

    @Query("select min(i.validFrom) from PlannedItem i where i.owner = :owner")
    java.time.LocalDate findMinValidFrom(User owner);

    @Modifying
    @Query("delete from PlannedItem i where i.importBatch = :batch")
    void deleteByImportBatch(io.github.mirco104.budget.importer.ImportBatch batch);

    @Query("select i.itemKey from PlannedItem i where i.importBatch = :batch")
    List<UUID> findKeysByImportBatch(io.github.mirco104.budget.importer.ImportBatch batch);

    @Modifying
    @Query("delete from PlannedItem i where i.owner = :owner")
    void deleteByOwner(User owner);

    // Ticket #139: the items of an account and the reserve pots linked to it move to another account
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PlannedItem i set i.account = :target where i.account = :source")
    void moveAccount(
            io.github.mirco104.budget.bankaccount.BankAccount source,
            io.github.mirco104.budget.bankaccount.BankAccount target);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PlannedItem i set i.reserveAccount = :target where i.reserveAccount = :source")
    void moveReserveAccount(
            io.github.mirco104.budget.bankaccount.BankAccount source,
            io.github.mirco104.budget.bankaccount.BankAccount target);

    /** Deleting an account leaves the reserves linked to it without account (P8). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PlannedItem i set i.reserveAccount = null where i.reserveAccount = :account")
    void clearReserveAccount(io.github.mirco104.budget.bankaccount.BankAccount account);
}
