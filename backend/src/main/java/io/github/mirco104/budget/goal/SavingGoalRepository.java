package io.github.mirco104.budget.goal;

import io.github.mirco104.budget.bankaccount.BankAccount;
import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

    List<SavingGoal> findByOwnerOrderByNameAsc(User owner);

    Optional<SavingGoal> findByIdAndOwner(Long id, User owner);

    @Modifying
    @Query("delete from SavingGoal g where g.owner = :owner")
    void deleteByOwner(User owner);

    /** Ticket #145: merging an account (R26) moves its goals to the target. */
    @Modifying
    @Query("update SavingGoal g set g.account = :target where g.account = :source")
    void moveAccount(BankAccount source, BankAccount target);

    /** Ticket #145: deleting an account leaves its goals without account. */
    @Modifying
    @Query("update SavingGoal g set g.account = null where g.account = :account")
    void clearAccount(BankAccount account);
}
