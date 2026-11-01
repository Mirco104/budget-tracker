package io.github.mirco104.budget.useradmin;

import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US25: admins see all accounts and make users admins or admins users. The system admin stays system admin. */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserAdminService(UserRepository userRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> findAll() {
        currentUser.requireAdmin();
        return userRepository.findAll(Sort.by("createdAt", "id")).stream()
                .map(UserSummary::from)
                .toList();
    }

    /** 400 for SYSTEM_ADMIN as the new role, 404 for an unknown account, 409 for the system admin and oneself. */
    @Transactional
    public UserSummary changeRole(long id, Role role) {
        User admin = currentUser.requireAdmin();
        if (role == Role.SYSTEM_ADMIN) {
            throw new InvalidFieldException("role", "Die Rolle Systemadmin lässt sich nicht vergeben.");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Konto " + id + " wurde nicht gefunden."));
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new ConflictException("Der Systemadmin bleibt immer Systemadmin.");
        }
        if (user.getId().equals(admin.getId())) {
            throw new ConflictException("Die eigene Rolle kannst du nicht ändern.");
        }
        user.changeRole(role);
        return UserSummary.from(user);
    }
}
