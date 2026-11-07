package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Every query is scoped to the owner (R15). */
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    List<ImportBatch> findByOwnerOrderByCreatedAtDescIdDesc(User owner);

    Optional<ImportBatch> findByIdAndOwner(Long id, User owner);

    /** US16: deleting a user; the transactions must be gone first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ImportBatch b where b.owner = :owner")
    void deleteByOwner(User owner);
}
