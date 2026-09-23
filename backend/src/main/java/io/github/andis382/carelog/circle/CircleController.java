package io.github.andis382.carelog.circle;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.auth.InvitationRepository;
import io.github.andis382.carelog.auth.Organization;
import io.github.andis382.carelog.auth.Role;
import io.github.andis382.carelog.auth.UserRepository;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.files.UploadController;
import io.github.andis382.carelog.vitals.VitalKind;
import io.github.andis382.carelog.vitals.VitalRange;
import io.github.andis382.carelog.vitals.VitalService.RangeView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The circle as a whole: the elder, the people, the plan and the settings. */
@RestController
@RequestMapping("/api/circle")
public class CircleController {

    private final CircleService circle;
    private final CircleTime time;
    private final ElderRepository elders;
    private final UserRepository users;
    private final InvitationRepository invitations;
    private final CurrentUser currentUser;

    public CircleController(CircleService circle, CircleTime time, ElderRepository elders, UserRepository users,
                            InvitationRepository invitations, CurrentUser currentUser) {
        this.circle = circle;
        this.time = time;
        this.elders = elders;
        this.users = users;
        this.invitations = invitations;
        this.currentUser = currentUser;
    }

    public record ContactView(String name, String relation, String phone) {}

    public record ElderView(String fullName, String firstName, Integer birthYear, Integer age, String photoFileId, String photoUrl,
                            String conditions, String allergies, String gpName, String gpPhone, String address, String town,
                            List<ContactView> contacts) {
        public static ElderView of(Elder e, int year) {
            return new ElderView(e.getFullName(), e.firstName(), e.getBirthYear(), e.ageIn(year), e.getPhotoFileId(),
                UploadController.fileUrl(e.getPhotoFileId()), e.getConditions(), e.getAllergies(), e.getGpName(), e.getGpPhone(),
                e.getAddress(), e.getTown(),
                e.getContacts().stream().map(c -> new ContactView(c.getName(), c.getRelation(), c.getPhone())).toList());
        }
    }

    public record MemberView(Long id, String name, String email, String phone, String role, Instant lastLoginAt, boolean you,
                             boolean payer) {}

    /** {@code maxMembers} is null when the plan has no limit. */
    public record CircleView(String name, String timezone, String locale, String plan, Integer maxMembers, int historyDays,
                             LocalDate visibleFrom, Long payerUserId, int graceMinutes, boolean doseAlerts, boolean weeklySummary,
                             List<RangeView> ranges, ElderView elder, List<MemberView> members, long openInvitations) {}

    public record RangeRequest(@NotNull VitalKind kind, BigDecimal low, BigDecimal high, BigDecimal low2, BigDecimal high2) {}

    public record SettingsRequest(@Min(15) @Max(90) int graceMinutes, boolean doseAlerts, boolean weeklySummary,
                                  List<@Valid RangeRequest> ranges) {}

    public record PlanRequest(@NotNull Plan plan, @NotNull Long payerUserId) {}

    @GetMapping
    public CircleView get() {
        Long orgId = currentUser.organizationId();
        Organization org = currentUser.organization();
        CircleSettings settings = circle.settings(orgId);
        Long me = currentUser.id();
        List<MemberView> members = users.findByOrganizationIdAndRemovedAtIsNullOrderByNameAsc(orgId).stream()
            .map(u -> new MemberView(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole().name(), u.getLastLoginAt(),
                u.getId().equals(me), u.getId().equals(settings.getPayerUserId())))
            .toList();
        Instant now = time.now();
        long open = invitations.findByOrganizationIdAndAcceptedAtIsNullOrderByCreatedAtDesc(orgId).stream()
            .filter(i -> i.isUsable(now)).count();
        Plan plan = settings.getPlan();
        return new CircleView(org.getName(), org.getTimezone(), org.getLocale(), plan.name(),
            plan.maxMembers() == Integer.MAX_VALUE ? null : plan.maxMembers(), plan.historyDays(), circle.visibleFrom(orgId),
            settings.getPayerUserId(), settings.getGraceMinutes(), settings.isDoseAlerts(), settings.isWeeklySummary(),
            circle.ranges(orgId).values().stream().map(RangeView::of).toList(),
            elders.findByOrganizationId(orgId).map(e -> ElderView.of(e, time.today(orgId).getYear())).orElse(null),
            members, open);
    }

    @PutMapping("/settings")
    @Transactional
    public CircleView settings(@Valid @RequestBody SettingsRequest req) {
        currentUser.requireRole(Role.OWNER);
        Long orgId = currentUser.organizationId();
        CircleSettings settings = circle.settings(orgId);
        settings.setGraceMinutes(req.graceMinutes());
        settings.setDoseAlerts(req.doseAlerts());
        settings.setWeeklySummary(req.weeklySummary());
        settings.setUpdatedAt(time.now());
        if (req.ranges() != null) {
            Map<VitalKind, VitalRange> ranges = circle.ranges(orgId);
            for (RangeRequest r : req.ranges()) {
                requireOrdered(r.low(), r.high(), "ranges");
                requireOrdered(r.low2(), r.high2(), "ranges");
                VitalRange range = ranges.get(r.kind());
                range.setLow(r.low());
                range.setHigh(r.high());
                range.setLow2(r.kind().paired() ? r.low2() : null);
                range.setHigh2(r.kind().paired() ? r.high2() : null);
            }
        }
        return get();
    }

    /** No real payment: the owner switches plans here, and the README explains how billing would attach. */
    @PutMapping("/plan")
    @Transactional
    public CircleView plan(@Valid @RequestBody PlanRequest req) {
        currentUser.requireRole(Role.OWNER);
        Long orgId = currentUser.organizationId();
        users.findByIdAndOrganizationIdAndRemovedAtIsNull(req.payerUserId(), orgId)
            .orElseThrow(() -> ApiException.field("payerUserId", "error.not_found"));
        if (req.plan() == Plan.FREE && users.countByOrganizationIdAndRemovedAtIsNull(orgId) > Plan.FREE.maxMembers()) {
            throw ApiException.conflict("plan.too_many_for_free");
        }
        CircleSettings settings = circle.settings(orgId);
        settings.setPlan(req.plan());
        settings.setPayerUserId(req.payerUserId());
        settings.setUpdatedAt(time.now());
        return get();
    }

    private static void requireOrdered(BigDecimal low, BigDecimal high, String field) {
        if (low != null && high != null && low.compareTo(high) > 0) {
            throw ApiException.field(field, "circle.range_order");
        }
    }
}
