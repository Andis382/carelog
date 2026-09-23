package io.github.andis382.carelog.circle;

import io.github.andis382.carelog.auth.InvitationRepository;
import io.github.andis382.carelog.auth.Role;
import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.auth.UserRepository;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.vitals.VitalKind;
import io.github.andis382.carelog.vitals.VitalRange;
import io.github.andis382.carelog.vitals.VitalRangeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The circle's plan, its limits and its settings. */
@Service
public class CircleService {

    private final CircleSettingsRepository settings;
    private final VitalRangeRepository ranges;
    private final UserRepository users;
    private final InvitationRepository invitations;
    private final CircleTime time;

    public CircleService(CircleSettingsRepository settings, VitalRangeRepository ranges, UserRepository users,
                         InvitationRepository invitations, CircleTime time) {
        this.settings = settings;
        this.ranges = ranges;
        this.users = users;
        this.invitations = invitations;
        this.time = time;
    }

    /** Called once when someone starts a circle: they coordinate it and, for now, pay for it. */
    @Transactional
    public CircleSettings start(Long organizationId, User coordinator) {
        CircleSettings created = settings.save(new CircleSettings(organizationId, coordinator.getId()));
        ranges(organizationId);
        return created;
    }

    @Transactional
    public CircleSettings settings(Long organizationId) {
        return settings.findById(organizationId).orElseGet(() -> {
            Long payer = users.findByOrganizationIdAndRoleAndRemovedAtIsNull(organizationId, Role.OWNER).stream()
                .findFirst().map(User::getId).orElse(null);
            return settings.save(new CircleSettings(organizationId, payer));
        });
    }

    public Plan plan(Long organizationId) {
        return settings(organizationId).getPlan();
    }

    /** The first day a member may see, or null when the plan shows all history. */
    public LocalDate visibleFrom(Long organizationId) {
        int days = plan(organizationId).historyDays();
        return days == 0 ? null : time.today(organizationId).minusDays(days);
    }

    /** Moves a requested start date forward to what the plan lets the circle see. */
    public LocalDate clampFrom(Long organizationId, LocalDate from) {
        LocalDate visible = visibleFrom(organizationId);
        return visible != null && (from == null || from.isBefore(visible)) ? visible : from;
    }

    /** A new join link counts as a seat: members plus open invitations must fit the plan. */
    public void requireSeatForInvite(Long organizationId) {
        Instant now = time.now();
        long open = invitations.findByOrganizationIdAndAcceptedAtIsNullOrderByCreatedAtDesc(organizationId).stream()
            .filter(i -> i.isUsable(now))
            .count();
        if (users.countByOrganizationIdAndRemovedAtIsNull(organizationId) + open >= plan(organizationId).maxMembers()) {
            throw ApiException.conflict("plan.member_limit");
        }
    }

    /** Checked again on joining, in case links were made before a switch back to Free. */
    public void requireSeatToJoin(Long organizationId) {
        if (users.countByOrganizationIdAndRemovedAtIsNull(organizationId) >= plan(organizationId).maxMembers()) {
            throw ApiException.conflict("plan.member_limit");
        }
    }

    @Transactional
    public Map<VitalKind, VitalRange> ranges(Long organizationId) {
        Map<VitalKind, VitalRange> byKind = new EnumMap<>(VitalKind.class);
        ranges.findByOrganizationId(organizationId).forEach(r -> byKind.put(r.getKind(), r));
        for (VitalKind kind : VitalKind.values()) {
            byKind.computeIfAbsent(kind, k -> ranges.save(new VitalRange(organizationId, k)));
        }
        return byKind;
    }

    /** Author names for entries, removed members included. One query for a whole screen. */
    public Map<Long, String> names(Long organizationId) {
        return users.findByOrganizationIdOrderByNameAsc(organizationId).stream()
            .collect(Collectors.toMap(User::getId, User::getName));
    }

    public List<User> coordinators(Long organizationId) {
        return users.findByOrganizationIdAndRoleAndRemovedAtIsNull(organizationId, Role.OWNER);
    }
}
