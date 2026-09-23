package io.github.andis382.carelog.rota;

import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.rota.CheckIn.Position;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Arriving and leaving. Both may arrive late from an offline phone; the client id keeps them single. */
@Service
public class CheckInService {

    private final CheckInRepository checkIns;
    private final CircleTime time;

    public CheckInService(CheckInRepository checkIns, CircleTime time) {
        this.checkIns = checkIns;
        this.time = time;
    }

    @Transactional
    public CheckIn checkIn(Long orgId, Long userId, Instant at, Position position, String clientId) {
        Optional<CheckIn> replay = blank(clientId) ? Optional.empty() : checkIns.findByOrganizationIdAndClientId(orgId, clientId);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (checkIns.findByUserIdAndCheckedOutAtIsNull(userId).isPresent()) {
            throw ApiException.conflict("checkin.already_open");
        }
        return checkIns.save(new CheckIn(orgId, userId, time.resolveAt(at), position, clientId));
    }

    @Transactional
    public CheckIn checkOut(Long orgId, Long userId, Instant at, Position position, String clientId) {
        Optional<CheckIn> replay = blank(clientId) ? Optional.empty() : checkIns.findByOrganizationIdAndOutClientId(orgId, clientId);
        if (replay.isPresent()) {
            return replay.get();
        }
        CheckIn open = checkIns.findByUserIdAndCheckedOutAtIsNull(userId)
            .orElseThrow(() -> ApiException.conflict("checkin.not_open"));
        Instant when = time.resolveAt(at);
        if (when.isBefore(open.getCheckedInAt())) {
            throw ApiException.field("at", "checkin.before_start");
        }
        open.checkOut(when, position, clientId);
        return open;
    }

    public Optional<CheckIn> open(Long userId) {
        return checkIns.findByUserIdAndCheckedOutAtIsNull(userId);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
