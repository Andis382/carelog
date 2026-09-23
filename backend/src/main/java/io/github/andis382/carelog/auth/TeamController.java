package io.github.andis382.carelog.auth;

import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleSettings;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.common.Tokens;
import io.github.andis382.carelog.config.AppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** People in the circle and the join links waiting to be used. Only the coordinator manages them. */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    private static final Duration INVITATION_TTL = Duration.ofDays(14);
    /** The coordinator role is not handed out by link; everyone else is. */
    private static final Set<Role> INVITABLE = Set.of(Role.FAMILY, Role.CARER, Role.VIEWER);

    private final UserRepository users;
    private final InvitationRepository invitations;
    private final CircleService circle;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final CurrentUser currentUser;
    private final AppProperties props;
    private final Clock clock;

    public TeamController(UserRepository users, InvitationRepository invitations, CircleService circle,
                          FindByIndexNameSessionRepository<? extends Session> sessions, CurrentUser currentUser,
                          AppProperties props, Clock clock) {
        this.users = users;
        this.invitations = invitations;
        this.circle = circle;
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.props = props;
        this.clock = clock;
    }

    public record Member(Long id, String name, String email, String phone, String role, Instant lastLoginAt, boolean you) {}

    public record PendingInvitation(Long id, String name, String role, String url, Instant expiresAt) {}

    public record TeamView(List<Member> members, List<PendingInvitation> invitations) {}

    public record InviteRequest(@NotNull Role role, @Size(max = 120) String name) {}

    @GetMapping
    public TeamView team() {
        Long orgId = currentUser.organizationId();
        Long me = currentUser.id();
        Instant now = clock.instant();
        List<Member> members = users.findByOrganizationIdAndRemovedAtIsNullOrderByNameAsc(orgId).stream()
            .map(u -> new Member(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole().name(), u.getLastLoginAt(),
                u.getId().equals(me)))
            .toList();
        List<PendingInvitation> pending = currentUser.hasRole(Role.OWNER)
            ? invitations.findByOrganizationIdAndAcceptedAtIsNullOrderByCreatedAtDesc(orgId).stream()
                .filter(i -> i.isUsable(now))
                .map(this::view)
                .toList()
            : List.of();
        return new TeamView(members, pending);
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PendingInvitation invite(@Valid @RequestBody InviteRequest req) {
        currentUser.requireRole(Role.OWNER);
        if (!INVITABLE.contains(req.role())) {
            throw ApiException.field("role", "team.role_not_invitable");
        }
        Long orgId = currentUser.organizationId();
        circle.requireSeatForInvite(orgId);
        Invitation inv = new Invitation(orgId, Tokens.urlToken(), req.role(),
            req.name() == null || req.name().isBlank() ? null : req.name().trim(), currentUser.id(),
            clock.instant().plus(INVITATION_TTL));
        invitations.save(inv);
        return view(inv);
    }

    @DeleteMapping("/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void revoke(@PathVariable Long id) {
        currentUser.requireRole(Role.OWNER);
        Invitation inv = invitations.findByIdAndOrganizationId(id, currentUser.organizationId()).orElseThrow(ApiException::notFound);
        invitations.delete(inv);
    }

    /**
     * Removes someone from the circle: they are signed out everywhere and cannot sign in again,
     * but everything they recorded keeps their name.
     */
    @DeleteMapping("/members/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remove(@PathVariable Long id) {
        currentUser.requireRole(Role.OWNER);
        if (id.equals(currentUser.id())) {
            throw ApiException.conflict("team.cannot_remove_self");
        }
        Long orgId = currentUser.organizationId();
        User user = users.findByIdAndOrganizationIdAndRemovedAtIsNull(id, orgId).orElseThrow(ApiException::notFound);
        user.setRemovedAt(clock.instant());
        CircleSettings settings = circle.settings(orgId);
        if (id.equals(settings.getPayerUserId())) {
            settings.setPayerUserId(currentUser.id());
        }
        sessions.findByPrincipalName(user.getEmail()).keySet().forEach(sessions::deleteById);
    }

    private PendingInvitation view(Invitation i) {
        return new PendingInvitation(i.getId(), i.getName(), i.getRole().name(), props.link("/join/" + i.getToken()), i.getExpiresAt());
    }
}
