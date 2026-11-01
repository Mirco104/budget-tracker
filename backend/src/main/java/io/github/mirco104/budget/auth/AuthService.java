package io.github.mirco104.budget.auth;

import io.github.mirco104.budget.user.AppUserDetails;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in and sign-out with a session cookie (US14). */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final LoginAttempts loginAttempts;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthService(
            AuthenticationManager authenticationManager, LoginAttempts loginAttempts, CurrentUser currentUser,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.loginAttempts = loginAttempts;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
    }

    /** 401 for wrong e-mail or password (same text), 429 while the e-mail is locked, even with the right password. */
    public void login(LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
        String email = login.email().trim().toLowerCase(Locale.ROOT);
        if (loginAttempts.isLocked(email)) {
            throw new LoginLockedException();
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, login.password()));
        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(email);
            throw new InvalidCredentialsException();
        }
        loginAttempts.reset(email);
        signIn(authentication, request, response);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        return MeResponse.from(userRepository.findById(currentUser.details().id()).orElseThrow());
    }

    /** Signs in a user who just set their password, e.g. after accepting an invitation (US15). */
    public void signIn(User user, HttpServletRequest request, HttpServletResponse response) {
        AppUserDetails details = AppUserDetails.from(user);
        signIn(UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()),
                request, response);
    }

    // A new session id on every sign-in against session fixation
    private void signIn(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
