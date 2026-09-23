package io.github.andis382.carelog.rota;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.auth.UserRepository;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.rota.RotaService.ShiftCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RotaController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MINE_DAYS = 28;

    private final RotaService rota;
    private final ShiftRepository shifts;
    private final ShiftSwapRepository swaps;
    private final UserRepository users;
    private final CircleService circle;
    private final CircleTime time;
    private final CurrentUser currentUser;

    public RotaController(RotaService rota, ShiftRepository shifts, ShiftSwapRepository swaps, UserRepository users,
                          CircleService circle, CircleTime time, CurrentUser currentUser) {
        this.rota = rota;
        this.shifts = shifts;
        this.swaps = swaps;
        this.users = users;
        this.circle = circle;
        this.time = time;
        this.currentUser = currentUser;
    }

    public record ShiftRequest(
        @NotNull Long userId,
        @NotNull LocalDate date,
        @NotNull LocalTime start,
        @NotNull LocalTime end,
        @NotNull ShiftKind kind,
        @Size(max = 300) String note,
        @Min(0) @Max(8) Integer repeatWeeks) {}

    public record SwapRequest(@NotNull Long toUserId, @Size(max = 300) String message) {}

    public record SwapView(Long id, Long shiftId, Long fromUserId, String fromName, Long toUserId, String toName, String status,
                           String message, Instant createdAt, Instant respondedAt) {}

    public record ShiftView(Long id, Long userId, String userName, LocalDate date, String start, String end, String kind,
                            String note, long minutes, boolean now, SwapView pendingSwap) {}

    public record Person(Long id, String name, String role) {}

    public record RotaWeek(LocalDate weekStart, LocalDate today, List<ShiftView> shifts, List<Person> people) {}

    public record MyRota(List<ShiftView> shifts, List<SwapWithShift> incoming, List<SwapWithShift> outgoing, List<Person> people) {}

    public record SwapWithShift(SwapView swap, ShiftView shift) {}

    @GetMapping("/api/rota")
    public RotaWeek week(@RequestParam(required = false) LocalDate week) {
        Long orgId = currentUser.organizationId();
        LocalDate today = time.today(orgId);
        LocalDate start = (week == null ? today : week).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Shift> list = rota.between(orgId, start, start.plusDays(6));
        return new RotaWeek(start, today, views(orgId, list), people(orgId));
    }

    @GetMapping("/api/rota/mine")
    public MyRota mine() {
        Long orgId = currentUser.organizationId();
        Long me = currentUser.id();
        LocalDate today = time.today(orgId);
        List<Shift> upcoming = shifts.findByOrganizationIdAndUserIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(orgId, me,
            today, today.plusDays(MINE_DAYS));
        Map<Long, String> names = circle.names(orgId);
        List<ShiftSwap> pending = swaps.findByOrganizationIdAndStatus(orgId, ShiftSwap.Status.PENDING);
        Map<Long, Shift> swapShifts = shifts.findAllById(pending.stream().map(ShiftSwap::getShiftId).toList()).stream()
            .collect(Collectors.toMap(Shift::getId, Function.identity()));
        LocalDateTime now = time.localNow(orgId);
        List<SwapWithShift> incoming = pending.stream().filter(s -> s.getToUserId().equals(me))
            .map(s -> new SwapWithShift(swapView(s, names), shiftView(swapShifts.get(s.getShiftId()), names, null, now)))
            .toList();
        List<SwapWithShift> outgoing = pending.stream().filter(s -> s.getFromUserId().equals(me))
            .map(s -> new SwapWithShift(swapView(s, names), shiftView(swapShifts.get(s.getShiftId()), names, null, now)))
            .toList();
        return new MyRota(views(orgId, upcoming), incoming, outgoing, people(orgId));
    }

    @PostMapping("/api/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ShiftView> create(@Valid @RequestBody ShiftRequest req) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        List<Shift> created = rota.create(orgId, currentUser.id(), currentUser.role(), new ShiftCommand(req.userId(), req.date(),
            req.start(), req.end(), req.kind(), req.note(), req.repeatWeeks() == null ? 0 : req.repeatWeeks()));
        return views(orgId, created);
    }

    @DeleteMapping("/api/shifts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        currentUser.requirePlanner();
        rota.delete(currentUser.organizationId(), id);
    }

    @PostMapping("/api/shifts/{id}/swap")
    @ResponseStatus(HttpStatus.CREATED)
    public SwapView requestSwap(@PathVariable Long id, @Valid @RequestBody SwapRequest req) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        return swapView(rota.requestSwap(orgId, currentUser.id(), id, req.toUserId(), req.message()), circle.names(orgId));
    }

    @PostMapping("/api/swaps/{id}/accept")
    public SwapView accept(@PathVariable Long id) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        return swapView(rota.accept(orgId, currentUser.id(), id), circle.names(orgId));
    }

    @PostMapping("/api/swaps/{id}/decline")
    public SwapView decline(@PathVariable Long id) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        return swapView(rota.decline(orgId, currentUser.id(), id), circle.names(orgId));
    }

    @PostMapping("/api/swaps/{id}/cancel")
    public SwapView cancel(@PathVariable Long id) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        return swapView(rota.cancel(orgId, currentUser.id(), id), circle.names(orgId));
    }

    private List<ShiftView> views(Long orgId, List<Shift> list) {
        Map<Long, String> names = circle.names(orgId);
        Map<Long, ShiftSwap> pending = list.isEmpty() ? Map.of()
            : swaps.findByShiftIdIn(list.stream().map(Shift::getId).toList()).stream()
                .filter(s -> s.getStatus() == ShiftSwap.Status.PENDING)
                .collect(Collectors.toMap(ShiftSwap::getShiftId, Function.identity(), (a, b) -> a));
        LocalDateTime now = time.localNow(orgId);
        return list.stream().map(s -> shiftView(s, names, pending.get(s.getId()), now)).toList();
    }

    private ShiftView shiftView(Shift s, Map<Long, String> names, ShiftSwap pending, LocalDateTime now) {
        return new ShiftView(s.getId(), s.getUserId(), names.get(s.getUserId()), s.getShiftDate(), HH_MM.format(s.getStartTime()),
            HH_MM.format(s.getEndTime()), s.getKind().name(), s.getNote(), s.minutes(), s.covers(now),
            pending == null ? null : swapView(pending, names));
    }

    private static SwapView swapView(ShiftSwap s, Map<Long, String> names) {
        return new SwapView(s.getId(), s.getShiftId(), s.getFromUserId(), names.get(s.getFromUserId()), s.getToUserId(),
            names.get(s.getToUserId()), s.getStatus().name(), s.getMessage(), s.getCreatedAt(), s.getRespondedAt());
    }

    private List<Person> people(Long orgId) {
        return users.findByOrganizationIdAndRemovedAtIsNullOrderByNameAsc(orgId).stream()
            .filter(u -> u.getRole().canRecord())
            .sorted(Comparator.comparing(User::getName))
            .map(u -> new Person(u.getId(), u.getName(), u.getRole().name()))
            .toList();
    }
}
