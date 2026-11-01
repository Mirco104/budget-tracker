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

    @EntityGraph(attributePaths = "category")
    List<Transaction> findByOwnerAndDateBetweenOrderByDateDescIdDesc(User owner, LocalDate from, LocalDate to);

    Optional<Transaction> findByIdAndOwner(Long id, User owner);

    /** Date of the earliest transaction, null without transactions. */
    @Query("select min(t.date) from Transaction t where t.owner = :owner")
    LocalDate findMinDate(User owner);

    boolean existsByCategoryId(Long categoryId);

    /** US16: all transactions of the user, for the export. */
    @EntityGraph(attributePaths = "category")
    List<Transaction> findByOwnerOrderByDateAscIdAsc(User owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Transaction t where t.owner = :owner")
    void deleteByOwner(User owner);
}
