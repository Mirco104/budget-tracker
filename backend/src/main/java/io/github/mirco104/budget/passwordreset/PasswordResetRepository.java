package io.github.mirco104.budget.passwordreset;

import io.github.mirco104.budget.user.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    Optional<PasswordReset> findByTokenHash(String tokenHash);

    /** A new link for the account makes its older unused links unusable (US36). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordReset r set r.usedAt = :now where r.user = :user and r.usedAt is null")
    void retireOpenLinksOf(User user, Instant now);

    /** US16: deleting an account removes the links for it and the links its user created. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PasswordReset r where r.user = :user or r.createdBy = :user")
    void deleteByUserOrCreatedBy(User user);
}
