package io.github.mirco104.budget.passkey;

import io.github.mirco104.budget.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Component;

/** The passkeys as credential records of Spring Security (US44), stored in {@link PasskeyRepository}. */
@Component
class PasskeyCredentials implements UserCredentialRepository {

    private final PasskeyRepository passkeyRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    PasskeyCredentials(PasskeyRepository passkeyRepository, UserRepository userRepository, Clock clock) {
        this.passkeyRepository = passkeyRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** A new passkey after the registration, or the new counter of a known one after a sign-in. */
    @Override
    public void save(CredentialRecord record) {
        String credentialId = record.getCredentialId().toBase64UrlString();
        passkeyRepository.findByCredentialId(credentialId).ifPresentOrElse(
                passkey -> passkey.recordUse(record.getSignatureCount(), record.isBackupState(), Instant.now(clock)),
                () -> passkeyRepository.save(new Passkey(
                        userRepository.findByPasskeyUserHandle(record.getUserEntityUserId().toBase64UrlString())
                                .orElseThrow(),
                        credentialId,
                        new Bytes(record.getPublicKey().getBytes()).toBase64UrlString(),
                        record.getAttestationObject().toBase64UrlString(),
                        record.getSignatureCount(),
                        record.isUvInitialized(),
                        record.isBackupEligible(),
                        record.isBackupState(),
                        record.getTransports().stream().map(AuthenticatorTransport::getValue).sorted()
                                .collect(Collectors.joining(",")),
                        record.getLabel(),
                        Instant.now(clock))));
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return passkeyRepository.findByCredentialId(credentialId.toBase64UrlString()).map(PasskeyCredentials::record)
                .orElse(null);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return userRepository.findByPasskeyUserHandle(userId.toBase64UrlString())
                .map(user -> passkeyRepository.findByOwnerOrderByCreatedAtAscIdAsc(user).stream()
                        .map(PasskeyCredentials::record).toList())
                .orElse(List.of());
    }

    @Override
    public void delete(Bytes credentialId) {
        passkeyRepository.findByCredentialId(credentialId.toBase64UrlString()).ifPresent(passkeyRepository::delete);
    }

    static CredentialRecord record(Passkey passkey) {
        Set<AuthenticatorTransport> transports = Arrays.stream(passkey.getTransports().split(","))
                .filter(value -> !value.isEmpty())
                .map(AuthenticatorTransport::valueOf)
                .collect(Collectors.toSet());
        ImmutableCredentialRecord.ImmutableCredentialRecordBuilder builder = ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(Bytes.fromBase64(passkey.getCredentialId()))
                .userEntityUserId(Bytes.fromBase64(passkey.getOwner().getPasskeyUserHandle()))
                .publicKey(new ImmutablePublicKeyCose(Bytes.fromBase64(passkey.getPublicKey()).getBytes()))
                .signatureCount(passkey.getSignatureCount())
                .uvInitialized(passkey.isUvInitialized())
                .transports(transports)
                .backupEligible(passkey.isBackupEligible())
                .backupState(passkey.isBackupState())
                .attestationObject(Bytes.fromBase64(passkey.getAttestationObject()))
                .label(passkey.getLabel())
                .created(passkey.getCreatedAt());
        if (passkey.getLastUsedAt() != null) {
            builder.lastUsed(passkey.getLastUsedAt());
        }
        return builder.build();
    }
}
