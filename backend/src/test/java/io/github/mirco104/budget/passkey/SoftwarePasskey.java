package io.github.mirco104.budget.passkey;

import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.AuthenticatorDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * A passkey in software for the tests (US44): answers the options of the backend like a browser with a platform
 * authenticator would, with an ES256 key, no attestation and the person checked (UP and UV).
 */
final class SoftwarePasskey {

    static final String RP_ID = "localhost";
    static final String ORIGIN = "http://localhost:8080";

    private static final ObjectConverter CONVERTER = new ObjectConverter();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final KeyPair keyPair;
    private final byte[] credentialId = new byte[16];
    private byte[] userHandle;
    private long signCount;

    SoftwarePasskey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        new SecureRandom().nextBytes(credentialId);
    }

    /** The body of POST /api/account/passkeys for the challenge and user handle of the creation options. */
    String register(String challenge, String userHandle, String label, String origin) throws Exception {
        this.userHandle = Base64.getUrlDecoder().decode(userHandle);
        AttestedCredentialData credentialData = new AttestedCredentialData(
                AAGUID.ZERO, credentialId, EC2COSEKey.create((ECPublicKey) keyPair.getPublic(),
                        COSEAlgorithmIdentifier.ES256));
        AuthenticatorData<RegistrationExtensionAuthenticatorOutput> authenticatorData = new AuthenticatorData<>(
                sha256(RP_ID.getBytes(StandardCharsets.UTF_8)),
                (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV | AuthenticatorData.BIT_AT),
                signCount, credentialData);
        byte[] attestationObject = new AttestationObjectConverter(CONVERTER)
                .convertToBytes(new AttestationObject(authenticatorData, new NoneAttestationStatement()));
        byte[] clientData = clientData("webauthn.create", challenge, origin);
        return """
                {"label": %s, "credential": {"id": "%s", "rawId": "%s", "type": "public-key",
                  "response": {"attestationObject": "%s", "clientDataJSON": "%s", "transports": ["internal", "hybrid"]},
                  "clientExtensionResults": {}, "authenticatorAttachment": "platform"}}
                """.formatted(label == null ? "null" : "\"" + label + "\"", b64(credentialId), b64(credentialId),
                b64(attestationObject), b64(clientData));
    }

    /** The body of POST /api/auth/passkey for the challenge of the request options, signed with this passkey. */
    String signIn(String challenge) throws Exception {
        return signIn(challenge, ORIGIN, keyPair);
    }

    String signIn(String challenge, String origin) throws Exception {
        return signIn(challenge, origin, keyPair);
    }

    String signIn(String challenge, String origin, KeyPair key) throws Exception {
        signCount++;
        byte[] authenticatorData = new AuthenticatorDataConverter(CONVERTER).convert(
                new AuthenticatorData<AuthenticationExtensionAuthenticatorOutput>(
                        sha256(RP_ID.getBytes(StandardCharsets.UTF_8)),
                        (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV), signCount));
        byte[] clientData = clientData("webauthn.get", challenge, origin);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key.getPrivate());
        signature.update(ByteBuffer.allocate(authenticatorData.length + 32)
                .put(authenticatorData).put(sha256(clientData)).array());
        return """
                {"id": "%s", "rawId": "%s", "type": "public-key",
                  "response": {"authenticatorData": "%s", "clientDataJSON": "%s", "signature": "%s", "userHandle": "%s"},
                  "clientExtensionResults": {}, "authenticatorAttachment": "platform"}
                """.formatted(b64(credentialId), b64(credentialId), b64(authenticatorData), b64(clientData),
                b64(signature.sign()), b64(userHandle));
    }

    static KeyPair otherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static byte[] clientData(String type, String challenge, String origin) {
        return """
                {"type":"%s","challenge":"%s","origin":"%s","crossOrigin":false}"""
                .formatted(type, challenge, origin).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }
}
