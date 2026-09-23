package io.github.andis382.carelog.rota;

import io.github.andis382.carelog.alerts.CircleNotifier;
import io.github.andis382.carelog.auth.Role;
import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.auth.UserRepository;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The duty rota and shift swaps. */
@Service
public class RotaService {

    private static final int MAX_REPEAT_WEEKS = 8;

    private final ShiftRepository shifts;
    private final ShiftSwapRepository swaps;
    private final UserRepository users;
    private final CircleNotifier notifier;
    private final CircleTime time;

    public RotaService(ShiftRepository shifts, ShiftSwapRepository swaps, UserRepository users, CircleNotifier notifier,
                       CircleTime time) {
        this.shifts = shifts;
        this.swaps = swaps;
        this.users = users;
        this.notifier = notifier;
        this.time = time;
    }

    public record ShiftCommand(Long userId, LocalDate date, LocalTime start, LocalTime end, ShiftKind kind, String note,
                               int repeatWeeks) {}

    public List<Shift> between(Long orgId, LocalDate from, LocalDate to) {
        return shifts.findByOrganizationIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(orgId, from, to);
    }

    /** Who is on duty at this moment, night shifts from yesterday included. */
    public List<Shift> onDuty(Long orgId, LocalDateTime now) {
        return between(orgId, now.toLocalDate().minusDays(1), now.toLocalDate()).stream().filter(s -> s.covers(now)).toList();
    }

    /**
     * Owner and family plan anyone's shifts; a carer may add her own. Weekly repeats make
     * "Mira, Monday to Saturday" a one-time job.
     */
    @Transactional
    public List<Shift> create(Long orgId, Long creatorId, Role creatorRole, ShiftCommand cmd) {
        if (!creatorRole.canPlan() && !cmd.userId().equals(creatorId)) {
            throw ApiException.forbidden();
        }
        User person = users.findByIdAndOrganizationIdAndRemovedAtIsNull(cmd.userId(), orgId)
            .orElseThrow(() -> ApiException.field("userId", "error.not_found"));
        if (!person.getRole().canRecord()) {
            throw ApiException.field("userId", "shift.viewer");
        }
        if (cmd.start().equals(cmd.end())) {
            throw ApiException.field("end", "shift.zero_length");
        }
        int repeat = Math.max(0, Math.min(MAX_REPEAT_WEEKS, cmd.repeatWeeks()));
        String note = cmd.note() == null || cmd.note().isBlank() ? null : cmd.note().trim();
        List<Shift> created = new ArrayList<>();
        for (int week = 0; week <= repeat; week++) {
            Shift shift = new Shift(orgId, person.getId(), cmd.date().plusWeeks(week), cmd.start(), cmd.end(), cmd.kind(), note,
                creatorId);
            requireNoOverlap(orgId, person.getId(), shift, null);
            created.add(shifts.save(shift));
        }
        return created;
    }

    @Transactional
    public void delete(Long orgId, Long id) {
        shifts.delete(shifts.findByIdAndOrganizationId(id, orgId).orElseThrow(ApiException::notFound));
    }

    @Transactional
    public ShiftSwap requestSwap(Long orgId, Long fromUserId, Long shiftId, Long toUserId, String message) {
        Shift shift = shifts.findByIdAndOrganizationId(shiftId, orgId).orElseThrow(ApiException::notFound);
        if (!shift.getUserId().equals(fromUserId)) {
            throw ApiException.forbidden();
        }
        if (!shift.startsAt().isAfter(time.localNow(orgId))) {
            throw ApiException.conflict("swap.shift_started");
        }
        if (toUserId.equals(fromUserId)) {
            throw ApiException.field("toUserId", "swap.self");
        }
        User to = users.findByIdAndOrganizationIdAndRemovedAtIsNull(toUserId, orgId)
            .orElseThrow(() -> ApiException.field("toUserId", "error.not_found"));
        if (!to.getRole().canRecord()) {
            throw ApiException.field("toUserId", "shift.viewer");
        }
        if (swaps.existsByShiftIdAndStatus(shiftId, ShiftSwap.Status.PENDING)) {
            throw ApiException.conflict("swap.already_pending");
        }
        String note = message == null || message.isBlank() ? null : message.trim();
        ShiftSwap swap = swaps.save(new ShiftSwap(orgId, shiftId, fromUserId, toUserId, note, time.now()));
        User from = users.findById(fromUserId).orElseThrow();
        notifier.swapRequested(orgId, swap.getId(), from, to, shift.getShiftDate(), shift.getStartTime(), shift.getEndTime(), note);
        return swap;
    }

    /** Only the person asked can accept; the shift then becomes theirs. */
    @Transactional
    public ShiftSwap accept(Long orgId, Long userId, Long swapId) {
        ShiftSwap swap = pendingFor(orgId, swapId);
        if (!swap.getToUserId().equals(userId)) {
            throw ApiException.forbidden();
        }
        Shift shift = shifts.findByIdAndOrganizationId(swap.getShiftId(), orgId).orElseThrow(ApiException::notFound);
        if (!shift.startsAt().isAfter(time.localNow(orgId))) {
            throw ApiException.conflict("swap.shift_started");
        }
        requireNoOverlap(orgId, userId, shift, shift.getId());
        shift.setUserId(userId);
        swap.answer(ShiftSwap.Status.ACCEPTED, time.now());
        return swap;
    }

    @Transactional
    public ShiftSwap decline(Long orgId, Long userId, Long swapId) {
        ShiftSwap swap = pendingFor(orgId, swapId);
        if (!swap.getToUserId().equals(userId)) {
            throw ApiException.forbidden();
        }
        swap.answer(ShiftSwap.Status.DECLINED, time.now());
        return swap;
    }

    @Transactional
    public ShiftSwap cancel(Long orgId, Long userId, Long swapId) {
        ShiftSwap swap = pendingFor(orgId, swapId);
        if (!swap.getFromUserId().equals(userId)) {
            throw ApiException.forbidden();
        }
        swap.answer(ShiftSwap.Status.CANCELLED, time.now());
        return swap;
    }

    private ShiftSwap pendingFor(Long orgId, Long swapId) {
        ShiftSwap swap = swaps.findByIdAndOrganizationId(swapId, orgId).orElseThrow(ApiException::notFound);
        if (swap.getStatus() != ShiftSwap.Status.PENDING) {
            throw ApiException.conflict("swap.not_pending");
        }
        return swap;
    }

    private void requireNoOverlap(Long orgId, Long userId, Shift candidate, Long ignoreId) {
        LocalDate day = candidate.getShiftDate();
        boolean clash = shifts.findByOrganizationIdAndUserIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(orgId, userId,
                day.minusDays(1), day.plusDays(1)).stream()
            .filter(s -> !s.getId().equals(ignoreId))
            .anyMatch(s -> s.overlaps(candidate));
        if (clash) {
            throw ApiException.conflict("shift.overlap");
        }
    }
}
