package io.github.mirco104.budget.bankaccount;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every query is scoped to the owner (R15). */
public interface BankAccountSourceRepository extends JpaRepository<BankAccountSource, Long> {

    @EntityGraph(attributePaths = "account")
    List<BankAccountSource> findByOwner(User owner);

    Optional<BankAccountSource> findByOwnerAndSourceKey(User owner, String sourceKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BankAccountSource s where s.account = :account")
    void deleteByAccount(BankAccount account);

    /** US16: deleting a user; before the accounts. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BankAccountSource s where s.owner = :owner")
    void deleteByOwner(User owner);
}
