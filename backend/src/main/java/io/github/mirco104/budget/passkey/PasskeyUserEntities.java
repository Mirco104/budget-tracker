package io.github.mirco104.budget.passkey;

import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Component;

/**
 * The WebAuthn view of a user (US44): the random handle on the user row, the e-mail as name. Spring Security finds the
 * user by the name of the signed-in user, which is the current e-mail, so a changed e-mail keeps the passkeys.
 */
@Component
class PasskeyUserEntities implements PublicKeyCredentialUserEntityRepository {

    private final UserRepository userRepository;

    PasskeyUserEntities(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        return userRepository.findByPasskeyUserHandle(id.toBase64UrlString()).map(PasskeyUserEntities::entity)
                .orElse(null);
    }

    @Override
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        return userRepository.findByEmail(username)
                .filter(user -> user.getPasskeyUserHandle() != null)
                .map(PasskeyUserEntities::entity)
                .orElse(null);
    }

    /** Called once per user, before the first passkey: stores the new random handle. */
    @Override
    public void save(PublicKeyCredentialUserEntity entity) {
        userRepository.findByEmail(entity.getName()).orElseThrow()
                .changePasskeyUserHandle(entity.getId().toBase64UrlString());
    }

    @Override
    public void delete(Bytes id) {
        userRepository.findByPasskeyUserHandle(id.toBase64UrlString())
                .ifPresent(user -> user.changePasskeyUserHandle(null));
    }

    static PublicKeyCredentialUserEntity entity(User user) {
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.fromBase64(user.getPasskeyUserHandle()))
                .name(user.getEmail())
                .displayName(user.getDisplayName())
                .build();
    }
}
