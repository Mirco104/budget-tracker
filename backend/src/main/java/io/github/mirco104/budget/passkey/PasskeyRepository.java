package io.github.mirco104.budget.passkey;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasskeyRepository extends JpaRepository<Passkey, Long> {

    Optional<Passkey> findByCredentialId(String credentialId);

    /** R15: only the signed-in user's own passkey; another user's id is not found (404). */
    Optional<Passkey> findByIdAndOwner(Long id, User owner);

    List<Passkey> findByOwnerOrderByCreatedAtAscIdAsc(User owner);

    /** US16: deleting an account removes its passkeys. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Passkey p where p.owner = :owner")
    void deleteByOwner(User owner);
}
