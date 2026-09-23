package io.github.andis382.carelog.circle;

import io.github.andis382.carelog.auth.OrganizationRepository;
import io.github.andis382.carelog.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Time as the circle lives it. Dose times, days and weeks are in the circle's time zone
 * (the elder's home), whatever the time zone of the child reading from abroad.
 */
@Component
public class CircleTime {

    /** A phone's clock may run a little fast; anything further ahead is a mistake. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
    /** Offline entries are accepted for a week, long enough for any dead spot. */
    private static final Duration OFFLINE_LIMIT = Duration.ofDays(7);

    private final OrganizationRepository organizations;
    private final Clock clock;

    public CircleTime(OrganizationRepository organizations, Clock clock) {
        this.organizations = organizations;
        this.clock = clock;
    }

    public ZoneId zone(Long organizationId) {
        return organizations.findById(organizationId)
            .map(o -> ZoneId.of(o.getTimezone()))
            .orElseThrow(ApiException::notFound);
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today(Long organizationId) {
        return LocalDate.now(clock.withZone(zone(organizationId)));
    }

    public LocalDateTime localNow(Long organizationId) {
        return LocalDateTime.now(clock.withZone(zone(organizationId)));
    }

    /**
     * When an entry happened: now, or the moment a phone recorded it while offline.
     * The log must show when the pill was given, not when the phone found a signal.
     */
    public Instant resolveAt(Instant at) {
        Instant now = clock.instant();
        if (at == null) {
            return now;
        }
        if (at.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw ApiException.field("at", "entry.at_future");
        }
        if (at.isBefore(now.minus(OFFLINE_LIMIT))) {
            throw ApiException.field("at", "entry.at_too_old");
        }
        return at;
    }
}
