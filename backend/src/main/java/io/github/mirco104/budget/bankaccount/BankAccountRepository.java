package io.github.mirco104.budget.bankaccount;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every query is scoped to the owner (R15). */
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    /** The default account first, then by name. */
    @Query("select a from BankAccount a where a.owner = :owner order by a.defaultAccount desc, lower(a.name), a.id")
    List<BankAccount> findAllOrdered(User owner);

    Optional<BankAccount> findByIdAndOwner(Long id, User owner);

    Optional<BankAccount> findByOwnerAndDefaultAccountTrue(User owner);

    boolean existsByOwnerAndNameIgnoreCaseAndIdNot(User owner, String name, Long id);

    boolean existsByOwnerAndIbanAndIdNot(User owner, String iban, Long id);

    /** US16: deleting a user; transactions and series must be gone first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BankAccount a where a.owner = :owner")
    void deleteByOwner(User owner);
}
