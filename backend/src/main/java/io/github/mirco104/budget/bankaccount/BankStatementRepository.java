package io.github.mirco104.budget.bankaccount;

import io.github.mirco104.budget.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    List<BankStatement> findByOwnerOrderByDateAsc(User owner);

    Optional<BankStatement> findByAccountAndDate(BankAccount account, LocalDate date);

    boolean existsByAccountId(Long accountId);

    @Modifying
    @Query("delete from BankStatement s where s.owner = :owner")
    void deleteByOwner(User owner);
}
