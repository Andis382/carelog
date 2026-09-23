package io.github.andis382.carelog.vitals;

import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.common.Numbers;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Readings and the circle's usual ranges. Ranges are shown next to values; nothing acts on them. */
@Service
public class VitalService {

    private final VitalRepository vitals;
    private final CircleService circle;
    private final CircleTime time;

    public VitalService(VitalRepository vitals, CircleService circle, CircleTime time) {
        this.vitals = vitals;
        this.circle = circle;
        this.time = time;
    }

    public record ReadingCommand(VitalKind kind, BigDecimal value1, BigDecimal value2, String note, String clientId, Instant at) {}

    public record RangeView(String kind, BigDecimal low, BigDecimal high, BigDecimal low2, BigDecimal high2) {
        public static RangeView of(VitalRange r) {
            return new RangeView(r.getKind().name(), r.getLow(), r.getHigh(), r.getLow2(), r.getHigh2());
        }
    }

    public record ReadingView(Long id, String kind, BigDecimal value1, BigDecimal value2, Instant measuredAt, String by,
                              String note, String position) {}

    public record Stats(int count, BigDecimal min, BigDecimal avg, BigDecimal max, BigDecimal min2, BigDecimal avg2,
                        BigDecimal max2) {}

    public record History(String kind, RangeView range, LocalDate from, LocalDate visibleFrom, List<ReadingView> readings,
                          Stats stats) {}

    @Transactional
    public Vital record(Long orgId, Long userId, ReadingCommand cmd) {
        if (cmd.clientId() != null && !cmd.clientId().isBlank()) {
            Optional<Vital> replay = vitals.findByOrganizationIdAndClientId(orgId, cmd.clientId());
            if (replay.isPresent()) {
                return replay.get();
            }
        }
        VitalKind kind = cmd.kind();
        if (!kind.plausible(cmd.value1().doubleValue())) {
            throw ApiException.field("value1", "vital.implausible");
        }
        BigDecimal second = null;
        if (kind.paired()) {
            if (cmd.value2() == null) {
                throw ApiException.field("value2", "vital.second_required");
            }
            if (!kind.plausibleSecond(cmd.value2().doubleValue()) || cmd.value2().compareTo(cmd.value1()) >= 0) {
                throw ApiException.field("value2", "vital.implausible");
            }
            second = cmd.value2().setScale(0, RoundingMode.HALF_UP);
        }
        BigDecimal first = cmd.value1().setScale(kind.decimals(), RoundingMode.HALF_UP);
        String note = cmd.note() == null || cmd.note().isBlank() ? null : cmd.note().trim();
        return vitals.save(new Vital(orgId, kind, first, second, time.resolveAt(cmd.at()), note, userId, cmd.clientId()));
    }

    public History history(Long orgId, VitalKind kind, int days) {
        LocalDate today = time.today(orgId);
        LocalDate requested = today.minusDays(Math.max(1, days) - 1L);
        LocalDate from = circle.clampFrom(orgId, requested);
        Instant fromInstant = from.atStartOfDay(time.zone(orgId)).toInstant();
        VitalRange range = circle.ranges(orgId).get(kind);
        Map<Long, String> names = circle.names(orgId);
        List<Vital> readings = vitals.findByOrganizationIdAndKindAndMeasuredAtGreaterThanEqualOrderByMeasuredAtAsc(orgId, kind, fromInstant);
        List<ReadingView> views = readings.stream().map(v -> view(v, range, names)).toList();
        return new History(kind.name(), RangeView.of(range), from, circle.visibleFrom(orgId), views, stats(readings));
    }

    /** The last reading of every kind, for the Today card. */
    public Map<VitalKind, Vital> latest(Long orgId) {
        Map<VitalKind, Vital> latest = new EnumMap<>(VitalKind.class);
        for (VitalKind kind : VitalKind.values()) {
            vitals.findTopByOrganizationIdAndKindOrderByMeasuredAtDesc(orgId, kind).ifPresent(v -> latest.put(kind, v));
        }
        return latest;
    }

    public static ReadingView view(Vital v, VitalRange range, Map<Long, String> names) {
        return new ReadingView(v.getId(), v.getKind().name(), v.getValue1(), v.getValue2(), v.getMeasuredAt(),
            names.get(v.getRecordedBy()), v.getNote(), position(v, range));
    }

    /** "above", "below" or null when inside the usual range (or no range is set). */
    public static String position(Vital v, VitalRange range) {
        if (range == null) {
            return null;
        }
        if (above(v.getValue1(), range.getHigh()) || above(v.getValue2(), range.getHigh2())) {
            return "above";
        }
        if (below(v.getValue1(), range.getLow()) || below(v.getValue2(), range.getLow2())) {
            return "below";
        }
        return null;
    }

    static Stats stats(List<Vital> readings) {
        if (readings.isEmpty()) {
            return new Stats(0, null, null, null, null, null, null);
        }
        List<BigDecimal> firsts = readings.stream().map(Vital::getValue1).toList();
        List<BigDecimal> seconds = readings.stream().map(Vital::getValue2).filter(Objects::nonNull).toList();
        return new Stats(readings.size(), Numbers.min(firsts), Numbers.avg(firsts), Numbers.max(firsts),
            Numbers.min(seconds), Numbers.avg(seconds), Numbers.max(seconds));
    }

    private static boolean above(BigDecimal value, BigDecimal limit) {
        return value != null && limit != null && value.compareTo(limit) > 0;
    }

    private static boolean below(BigDecimal value, BigDecimal limit) {
        return value != null && limit != null && value.compareTo(limit) < 0;
    }
}
