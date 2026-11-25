package io.github.mirco104.budget.passkey;

import io.github.mirco104.budget.auth.AuthService;
import io.github.mirco104.budget.auth.InvalidCredentialsException;
import io.github.mirco104.budget.auth.LoginAttempts;
import io.github.mirco104.budget.auth.LoginLockedException;
import io.github.mirco104.budget.common.BadRequestException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.HttpSessionPublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.HttpSessionPublicKeyCredentialCreationOptionsRepository;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US44: add, rename and delete passkeys, and sign in with one. Both ways have two steps: the options with a random
 * challenge (kept in the session until the second step, usable once), then the answer of the browser, which Spring
 * Security checks against the challenge, the domain and the stored public key.
 */
@Service
public class PasskeyService {

    static final String DEFAULT_LABEL = "Passkey";
    static final int MAX_LABEL_LENGTH = 50;
    static final String NOT_RECOGNISED = "Der Passkey wurde nicht erkannt. Bitte versuche es noch einmal.";

    // The JSON of WebAuthn (base64url bytes, the names of the browser API); only for these calls
    private static final JsonMapper JSON = JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();
    private static final JavaType ASSERTION = JSON.getTypeFactory()
            .constructParametricType(PublicKeyCredential.class, AuthenticatorAssertionResponse.class);
    private static final JavaType ATTESTATION = JSON.getTypeFactory()
            .constructParametricType(PublicKeyCredential.class, AuthenticatorAttestationResponse.class);
    private static final String NOT_SAVED = "Der Passkey ließ sich nicht speichern. Bitte versuche es noch einmal.";

    private final WebAuthnRelyingPartyOperations operations;
    private final PasskeyRepository passkeyRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttempts loginAttempts;
    private final AuthService authService;
    private final PublicKeyCredentialCreationOptionsRepository creationOptions =
            new HttpSessionPublicKeyCredentialCreationOptionsRepository();
    private final PublicKeyCredentialRequestOptionsRepository requestOptions =
            new HttpSessionPublicKeyCredentialRequestOptionsRepository();

    public PasskeyService(
            WebAuthnRelyingPartyOperations operations, PasskeyRepository passkeyRepository,
            UserRepository userRepository, CurrentUser currentUser, PasswordEncoder passwordEncoder,
            LoginAttempts loginAttempts, AuthService authService) {
        this.operations = operations;
        this.passkeyRepository = passkeyRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<PasskeyResponse> list() {
        return passkeyRepository.findByOwnerOrderByCreatedAtAscIdAsc(currentUser.get()).stream()
                .map(PasskeyResponse::from).toList();
    }

    /**
     * Step 1 of adding a passkey. Needs the password (decision 96): a passkey opens the account for good, so a session
     * left open on another device must not be enough to add one. 400 on password if it is wrong.
     */
    @Transactional
    public String registrationOptions(String password, HttpServletRequest request, HttpServletResponse response) {
        User user = signedInUser();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidFieldException("password", "Das Passwort ist falsch.");
        }
        // The current e-mail, not the one of the session, which stays the old one after a change (US16)
        PublicKeyCredentialCreationOptions options = operations.createPublicKeyCredentialCreationOptions(
                new ImmutablePublicKeyCredentialCreationOptionsRequest(
                        UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), null, List.of())));
        creationOptions.save(request, response, options);
        return JSON.writeValueAsString(options);
    }

    /** Step 2: the new passkey from the browser, body {@code {credential, label}}; 400 if it cannot be checked. */
    @Transactional
    public PasskeyResponse register(String body, HttpServletRequest request, HttpServletResponse response) {
        User user = signedInUser();
        PublicKeyCredentialCreationOptions options = creationOptions.load(request);
        creationOptions.save(request, response, null);
        PublicKeyCredential<AuthenticatorAttestationResponse> credential;
        JsonNode label;
        try {
            JsonNode tree = JSON.readTree(body);
            label = tree.get("label");
            credential = JSON.treeToValue(tree.get("credential"), ATTESTATION);
        } catch (RuntimeException e) {
            throw new BadRequestException(NOT_SAVED);
        }
        String name = label(label != null && label.isString() ? label.asString() : null);
        if (options == null || !options.getUser().getId().toBase64UrlString().equals(user.getPasskeyUserHandle())) {
            throw new BadRequestException("Das Hinzufügen ist abgelaufen. Bitte starte es noch einmal.");
        }
        CredentialRecord record;
        try {
            record = operations.registerCredential(new ImmutableRelyingPartyRegistrationRequest(
                    options, new RelyingPartyPublicKey(credential, name)));
        } catch (RuntimeException e) {
            throw new BadRequestException(NOT_SAVED);
        }
        return PasskeyResponse.from(
                passkeyRepository.findByCredentialId(record.getCredentialId().toBase64UrlString()).orElseThrow());
    }

    @Transactional
    public PasskeyResponse rename(Long id, String label) {
        Passkey passkey = own(id);
        passkey.rename(label(label));
        return PasskeyResponse.from(passkey);
    }

    @Transactional
    public void delete(Long id) {
        passkeyRepository.delete(own(id));
    }

    /** Step 1 of the sign-in: options without a user, so the browser offers every passkey of this domain. */
    public String loginOptions(HttpServletRequest request, HttpServletResponse response) {
        PublicKeyCredentialRequestOptions options =
                operations.createCredentialRequestOptions(new ImmutablePublicKeyCredentialRequestOptionsRequest(null));
        requestOptions.save(request, response, options);
        return JSON.writeValueAsString(options);
    }

    /**
     * Step 2: signs in like the password (new session, last sign-in, US14). 401 with one text for every failure; each
     * counts against the lock of the client IP (pentest F6), and a locked IP gets 429. The lock of an e-mail after wrong
     * passwords does not apply: a passkey cannot be guessed.
     */
    @Transactional
    public void login(String body, HttpServletRequest request, HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        if (loginAttempts.isLockedByIp(ip)) {
            throw new LoginLockedException();
        }
        PublicKeyCredentialRequestOptions options = requestOptions.load(request);
        requestOptions.save(request, response, null);
        User user;
        try {
            if (options == null) {
                throw new IllegalStateException("No request options in the session");
            }
            PublicKeyCredential<AuthenticatorAssertionResponse> credential = JSON.readValue(body, ASSERTION);
            PublicKeyCredentialUserEntity entity =
                    operations.authenticate(new RelyingPartyAuthenticationRequest(options, credential));
            user = userRepository.findByPasskeyUserHandle(entity.getId().toBase64UrlString()).orElseThrow();
        } catch (RuntimeException e) {
            loginAttempts.recordIpFailure(ip);
            throw new InvalidCredentialsException(NOT_RECOGNISED);
        }
        loginAttempts.resetIp(ip);
        authService.signIn(user, request, response);
    }

    private Passkey own(Long id) {
        return passkeyRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Passkey " + id + " nicht gefunden."));
    }

    private User signedInUser() {
        return userRepository.findById(currentUser.details().id()).orElseThrow();
    }

    private static String label(String label) {
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.length() > MAX_LABEL_LENGTH) {
            throw new InvalidFieldException("label", "Der Name darf höchstens " + MAX_LABEL_LENGTH + " Zeichen haben.");
        }
        return trimmed.isEmpty() ? DEFAULT_LABEL : trimmed;
    }
}
