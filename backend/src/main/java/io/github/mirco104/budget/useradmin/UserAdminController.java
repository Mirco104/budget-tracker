package io.github.mirco104.budget.useradmin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only for admins; the service checks the role (US25). */
@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public List<UserSummary> findAll() {
        return userAdminService.findAll();
    }

    @PutMapping("/{id}/role")
    public UserSummary changeRole(@PathVariable long id, @Valid @RequestBody ChangeRoleRequest request) {
        return userAdminService.changeRole(id, request.role());
    }
}
