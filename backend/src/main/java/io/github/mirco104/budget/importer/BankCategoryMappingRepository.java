package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query is scoped to the owner (R15). */
public interface BankCategoryMappingRepository extends JpaRepository<BankCategoryMapping, Long> {

    List<BankCategoryMapping> findByOwnerAndProfile(User owner, BankProfile profile);

    Optional<BankCategoryMapping> findByOwnerAndProfileAndBankCategory(User owner, BankProfile profile, String bankCategory);
}
