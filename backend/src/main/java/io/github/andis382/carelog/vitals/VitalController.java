package io.github.andis382.carelog.vitals;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.vitals.VitalService.History;
import io.github.andis382.carelog.vitals.VitalService.ReadingCommand;
import io.github.andis382.carelog.vitals.VitalService.ReadingView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vitals")
public class VitalController {

    private final VitalService vitals;
    private final CircleService circle;
    private final CurrentUser currentUser;

    public VitalController(VitalService vitals, CircleService circle, CurrentUser currentUser) {
        this.vitals = vitals;
        this.circle = circle;
        this.currentUser = currentUser;
    }

    public record ReadingRequest(
        @NotNull VitalKind kind,
        @NotNull BigDecimal value1,
        BigDecimal value2,
        @Size(max = 500) String note,
        @Size(max = 64) String clientId,
        Instant at) {}

    @GetMapping
    public History history(@RequestParam VitalKind kind, @RequestParam(defaultValue = "30") int days) {
        return vitals.history(currentUser.organizationId(), kind, Math.min(days, 366));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingView record(@Valid @RequestBody ReadingRequest req) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        Vital saved = vitals.record(orgId, currentUser.id(),
            new ReadingCommand(req.kind(), req.value1(), req.value2(), req.note(), req.clientId(), req.at()));
        return VitalService.view(saved, circle.ranges(orgId).get(saved.getKind()), circle.names(orgId));
    }
}
