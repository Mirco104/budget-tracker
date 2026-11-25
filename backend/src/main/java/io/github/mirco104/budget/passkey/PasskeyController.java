package io.github.mirco104.budget.passkey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * US44: passkeys of the signed-in user and the sign-in with a passkey. The options and the answer of the browser are
 * the JSON of WebAuthn, so they pass through as text and the service reads them with the WebAuthn mapper.
 */
@RestController
public class PasskeyController {

    private final PasskeyService passkeyService;

    public PasskeyController(PasskeyService passkeyService) {
        this.passkeyService = passkeyService;
    }

    @GetMapping("/api/account/passkeys")
    public List<PasskeyResponse> list() {
        return passkeyService.list();
    }

    @PostMapping(path = "/api/account/passkeys/options", produces = MediaType.APPLICATION_JSON_VALUE)
    public String registrationOptions(
            @Valid @RequestBody PasskeyOptionsRequest options, HttpServletRequest request,
            HttpServletResponse response) {
        return passkeyService.registrationOptions(options.password(), request, response);
    }

    @PostMapping("/api/account/passkeys")
    @ResponseStatus(HttpStatus.CREATED)
    public PasskeyResponse register(@RequestBody String body, HttpServletRequest request, HttpServletResponse response) {
        return passkeyService.register(body, request, response);
    }

    @PutMapping("/api/account/passkeys/{id}")
    public PasskeyResponse rename(@PathVariable Long id, @Valid @RequestBody RenamePasskeyRequest rename) {
        return passkeyService.rename(id, rename.label());
    }

    @DeleteMapping("/api/account/passkeys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        passkeyService.delete(id);
    }

    @PostMapping(path = "/api/auth/passkey/options", produces = MediaType.APPLICATION_JSON_VALUE)
    public String loginOptions(HttpServletRequest request, HttpServletResponse response) {
        return passkeyService.loginOptions(request, response);
    }

    @PostMapping("/api/auth/passkey")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void login(@RequestBody String body, HttpServletRequest request, HttpServletResponse response) {
        passkeyService.login(body, request, response);
    }
}
