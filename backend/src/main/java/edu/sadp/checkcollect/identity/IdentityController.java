package edu.sadp.checkcollect.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class IdentityController {
    private final IdentityService identity;
    private final SessionLogin login;

    public IdentityController(IdentityService identity, SessionLogin login) {
        this.identity = identity;
        this.login = login;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record PasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {}
    public record CreateRequest(String username, String displayName, String initialPassword, Set<Role> roles) {}
    public record UpdateRequest(String displayName, boolean enabled, Set<Role> roles) {}

    @GetMapping("/auth/csrf")
    public java.util.Map<String, String> csrf(CsrfToken token) {
        return java.util.Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/auth/login")
    public UserSummary passwordLogin(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserAccount user = identity.verifyPassword(body.username(), body.password());
        login.establish(user.id(), request, response);
        return user.summary();
    }

    @PostMapping("/auth/logout")
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }

    @GetMapping("/me")
    public UserSummary me() { return identity.currentUser().summary(); }

    @PostMapping("/me/password")
    public UserSummary changePassword(@Valid @RequestBody PasswordRequest body) {
        identity.changePassword(body.oldPassword(), body.newPassword());
        return identity.currentUser().summary();
    }

    @GetMapping("/admin/users")
    public List<UserSummary> users() { return identity.allUsers(); }

    @PostMapping("/admin/users")
    public UserSummary create(@RequestBody CreateRequest body) {
        try { return identity.createUser(body.username(), body.displayName(), body.initialPassword(), body.roles()); }
        catch (DataIntegrityViolationException error) { throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在", error); }
    }

    @PatchMapping("/admin/users/{id}")
    public UserSummary update(@PathVariable UUID id, @RequestBody UpdateRequest body) {
        return identity.updateUser(id, body.displayName(), body.enabled(), body.roles());
    }
}
