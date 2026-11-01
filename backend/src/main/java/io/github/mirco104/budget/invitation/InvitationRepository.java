package io.github.mirco104.budget.invitation;

import io.github.mirco104.budget.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);

    /** US16: deleting an account removes the invitations its user created. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Invitation i where i.createdBy = :user")
    void deleteByCreatedBy(User user);
}
