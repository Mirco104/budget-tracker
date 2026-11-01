package io.github.mirco104.budget.invitation;

import io.github.mirco104.budget.auth.AuthService;
import io.github.mirco104.budget.auth.MeResponse;
import io.github.mirco104.budget.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;
    private final AuthService authService;

    public InvitationController(InvitationService invitationService, AuthService authService) {
        this.invitationService = invitationService;
        this.authService = authService;
    }

    /** Only for ADMIN (SecurityConfig). The link opens the app with the token as a parameter. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create() {
        Invitation invitation = invitationService.create();
        String link = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/")
                .queryParam("einladung", invitation.getToken())
                .toUriString();
        return new InvitationResponse(link, invitation.getExpiresAt());
    }

    /** Creates the user with the default categories and signs them in. */
    @PostMapping("/{token}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse accept(
            @PathVariable String token, @Valid @RequestBody AcceptInvitationRequest accept,
            HttpServletRequest request, HttpServletResponse response) {
        User user = invitationService.accept(token, accept);
        authService.signIn(user, request, response);
        return MeResponse.from(user);
    }
}
