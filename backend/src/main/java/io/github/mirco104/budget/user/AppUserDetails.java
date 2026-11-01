package io.github.mirco104.budget.user;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The signed-in user in the security context. Services get the user through {@link CurrentUser}. */
public record AppUserDetails(Long id, String email, String passwordHash, Role role) implements UserDetails {

    public static AppUserDetails from(User user) {
        return new AppUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    // Keeps the e-mail and the hash out of logs
    @Override
    public String toString() {
        return "AppUserDetails[id=" + id + ", role=" + role + "]";
    }
}
