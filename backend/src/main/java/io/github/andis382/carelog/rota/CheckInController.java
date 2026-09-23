package io.github.andis382.carelog.rota;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.rota.CheckIn.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkins")
public class CheckInController {

    private final CheckInService checkIns;
    private final CurrentUser currentUser;

    public CheckInController(CheckInService checkIns, CurrentUser currentUser) {
        this.checkIns = checkIns;
        this.currentUser = currentUser;
    }

    /** GPS is optional: a carer may refuse location, and the check-in still counts. */
    public record CheckInRequest(
        Instant at,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
        Integer accuracy,
        @Size(max = 64) String clientId) {

        Position position() {
            if (lat == null || lng == null) {
                return null;
            }
            return new Position(lat.setScale(6, RoundingMode.HALF_UP), lng.setScale(6, RoundingMode.HALF_UP),
                accuracy == null ? null : Math.max(0, accuracy));
        }
    }

    public record CheckInView(Long id, Instant checkedInAt, Instant checkedOutAt, boolean located) {
        static CheckInView of(CheckIn c) {
            return new CheckInView(c.getId(), c.getCheckedInAt(), c.getCheckedOutAt(), c.getInLat() != null);
        }
    }

    @PostMapping("/in")
    public CheckInView checkIn(@Valid @RequestBody CheckInRequest req) {
        currentUser.requireRecorder();
        return CheckInView.of(checkIns.checkIn(currentUser.organizationId(), currentUser.id(), req.at(), req.position(),
            req.clientId()));
    }

    @PostMapping("/out")
    public CheckInView checkOut(@Valid @RequestBody CheckInRequest req) {
        currentUser.requireRecorder();
        return CheckInView.of(checkIns.checkOut(currentUser.organizationId(), currentUser.id(), req.at(), req.position(),
            req.clientId()));
    }
}
