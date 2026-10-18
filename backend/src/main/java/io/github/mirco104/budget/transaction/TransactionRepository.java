package io.github.mirco104.budget.transaction;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = "category")
    List<Transaction> findByDateBetweenOrderByDateDescIdDesc(LocalDate from, LocalDate to);
}
