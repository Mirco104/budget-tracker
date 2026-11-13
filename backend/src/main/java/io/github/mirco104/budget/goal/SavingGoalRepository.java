package io.github.mirco104.budget.goal;

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
}
