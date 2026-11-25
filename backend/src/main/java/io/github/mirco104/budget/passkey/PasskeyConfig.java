package io.github.mirco104.budget.passkey;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

/**
 * WebAuthn of Spring Security for passkeys (US44, decision 96). The relying party is the domain of the app
 * (PASSKEY_RP_ID, in Docker from DOMAIN); the browser accepts a passkey only on that domain over HTTPS, or on
 * localhost. The passkey must check the person (fingerprint, face or device PIN), since it replaces the password.
 */
@Configuration
class PasskeyConfig {

    @Bean
    WebAuthnRelyingPartyOperations relyingPartyOperations(
            PasskeyUserEntities userEntities, PasskeyCredentials credentials,
            @Value("${app.passkey.rp-id}") String rpId, @Value("${app.passkey.origins}") Set<String> origins) {
        Webauthn4JRelyingPartyOperations operations = new Webauthn4JRelyingPartyOperations(
                userEntities, credentials,
                PublicKeyCredentialRpEntity.builder().id(rpId).name("Budget-Tracker").build(), origins);
        operations.setCustomizeCreationOptions(options -> options.authenticatorSelection(
                AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build()));
        operations.setCustomizeRequestOptions(options -> options.userVerification(UserVerificationRequirement.REQUIRED));
        return operations;
    }
}
