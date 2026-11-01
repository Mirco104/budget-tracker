package io.github.mirco104.budget.account;

import io.github.mirco104.budget.auth.AuthService;
import io.github.mirco104.budget.auth.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** US16: the signed-in user's own account. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;

    public AccountController(AccountService accountService, AuthService authService) {
        this.accountService = accountService;
        this.authService = authService;
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(request);
    }

    @PutMapping("/display-name")
    public MeResponse changeDisplayName(@Valid @RequestBody ChangeDisplayNameRequest request) {
        return MeResponse.from(accountService.changeDisplayName(request));
    }

    @PutMapping("/email")
    public MeResponse changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        return MeResponse.from(accountService.changeEmail(request));
    }

    @GetMapping("/export")
    public ResponseEntity<AccountExport> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"budget-export.json\"")
                .body(accountService.export());
    }

    /** Deletes the account with all data and ends the session. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody DeleteAccountRequest delete, HttpServletRequest request) {
        accountService.delete(delete);
        authService.logout(request);
    }
}
