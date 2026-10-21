package dev.paydeck.api.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.paydeck.api.account.AccountController.AccountResponse;
import dev.paydeck.api.account.AccountRepository;
import dev.paydeck.api.audit.AuditEvent;
import dev.paydeck.api.audit.AuditEventRepository;
import dev.paydeck.api.audit.AuditLog;
import dev.paydeck.api.auth.AuthController.UserResponse;
import dev.paydeck.api.auth.CurrentUser;
import dev.paydeck.api.auth.Role;
import dev.paydeck.api.auth.User;
import dev.paydeck.api.auth.UserRepository;
import dev.paydeck.api.common.BusinessRuleException;
import dev.paydeck.api.common.NotFoundException;
import dev.paydeck.api.ledger.LedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tools for PayDeck staff. SecurityConfig already keeps customers out of
 * /api/admin; {@code @PreAuthorize} narrows some endpoints to admins only.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record UserDetails(UserResponse user, Instant createdAt, List<AccountResponse> accounts) {
    }

    public record ChangeRoleRequest(@NotNull Role role) {
    }

    public record AuditEventResponse(UUID id, UUID actorUserId, String action, String targetType, UUID targetId,
                                     String detail, Instant createdAt) {

        static AuditEventResponse of(AuditEvent event) {
            return new AuditEventResponse(event.getId(), event.getActorUserId(), event.getAction(),
                    event.getTargetType(), event.getTargetId(), event.getDetail(), event.getCreatedAt());
        }
    }

    private final UserRepository users;
    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final AuditEventRepository auditEvents;
    private final AuditLog audit;

    public AdminController(UserRepository users, AccountRepository accounts, LedgerService ledger,
                           AuditEventRepository auditEvents, AuditLog audit) {
        this.users = users;
        this.accounts = accounts;
        this.ledger = ledger;
        this.auditEvents = auditEvents;
        this.audit = audit;
    }

    /** Find a customer by email, or list the newest 50 sign-ups. */
    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserResponse> findUsers(@RequestParam(required = false) String email) {
        List<User> found = email == null || email.isBlank()
                ? users.findTop50ByOrderByCreatedAtDesc()
                : users.findByEmail(User.normalizeEmail(email)).stream().toList();
        return found.stream().map(UserResponse::of).toList();
    }

    @GetMapping("/users/{id}")
    @Transactional(readOnly = true)
    public UserDetails user(CurrentUser staff, @PathVariable UUID id) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        audit.recordNow(staff.id(), "USER_VIEWED", "USER", id, "by " + staff.role());
        List<AccountResponse> owned = accounts.findByOwnerUserIdOrderByCreatedAt(id).stream()
                .map(account -> AccountResponse.of(account, ledger.balanceOf(account.getId())))
                .toList();
        return new UserDetails(UserResponse.of(user), user.getCreatedAt(), owned);
    }

    /** Takes effect the next time that person's access token is refreshed (within 15 minutes). */
    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse changeRole(CurrentUser admin, @PathVariable UUID id,
                                   @Valid @RequestBody ChangeRoleRequest request) {
        if (admin.id().equals(id)) {
            throw new BusinessRuleException("CANNOT_CHANGE_OWN_ROLE",
                    "Ask another admin to change your role, so PayDeck is never left without one.");
        }
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        Role before = user.getRole();
        user.changeRole(request.role());
        audit.record(admin.id(), "ROLE_CHANGED", "USER", id, before + " to " + request.role());
        return UserResponse.of(user);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AuditEventResponse> auditLog() {
        return auditEvents.findTop100ByOrderByCreatedAtDesc().stream().map(AuditEventResponse::of).toList();
    }
}
