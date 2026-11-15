package io.github.mirco104.budget.closing;

import io.github.mirco104.budget.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosedMonthRepository extends JpaRepository<ClosedMonth, Long> {

    @EntityGraph(attributePaths = "savingGoal")
    Optional<ClosedMonth> findByOwnerAndMonth(User owner, LocalDate month);

    @EntityGraph(attributePaths = "savingGoal")
    List<ClosedMonth> findByOwner(User owner);

    boolean existsBySavingGoalId(long savingGoalId);

    void deleteByOwner(User owner);
}
