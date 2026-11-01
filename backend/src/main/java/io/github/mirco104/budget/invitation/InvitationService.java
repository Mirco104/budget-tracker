package io.github.mirco104.budget.invitation;

import io.github.mirco104.budget.common.GoneException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US15: an admin creates a link; whoever opens it once within 7 days becomes a user. */
@Service
public class InvitationService {

    static final Duration VALIDITY = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;

    private final InvitationRepository invitationRepository;
    private final UserService userService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public InvitationService(
            InvitationRepository invitationRepository, UserService userService, CurrentUser currentUser,
            Clock clock) {
        this.invitationRepository = invitationRepository;
        this.userService = userService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public Invitation create() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        User admin = currentUser.requireAdmin();
        return invitationRepository.save(new Invitation(token, admin, Instant.now(clock).plus(VALIDITY)));
    }

    /** 404 for an unknown link, 410 for an expired or used one; 409 and 400 come from {@link UserService}. */
    @Transactional
    public User accept(String token, AcceptInvitationRequest request) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Diese Einladung gibt es nicht."));
        Instant now = Instant.now(clock);
        if (!invitation.isUsableAt(now)) {
            throw new GoneException("Die Einladung ist abgelaufen oder wurde schon benutzt.");
        }
        User user = userService.create(request.email(), request.displayName(), request.password(), Role.USER);
        invitation.markUsed(now);
        return user;
    }
}
