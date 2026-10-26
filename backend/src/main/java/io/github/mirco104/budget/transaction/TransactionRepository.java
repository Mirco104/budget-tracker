package io.github.mirco104.budget.transaction;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = "category")
    List<Transaction> findByDateBetweenOrderByDateDescIdDesc(LocalDate from, LocalDate to);

    /** Date of the earliest transaction, null without transactions. */
    @Query("select min(t.date) from Transaction t")
    LocalDate findMinDate();
}
