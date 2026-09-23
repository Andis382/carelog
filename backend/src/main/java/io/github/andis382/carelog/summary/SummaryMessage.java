package io.github.andis382.carelog.summary;

import io.github.andis382.carelog.common.Texts;
import io.github.andis382.carelog.summary.WeeklyReport.Duty;
import io.github.andis382.carelog.summary.WeeklyReport.Notable;
import io.github.andis382.carelog.summary.WeeklyReport.Problem;
import io.github.andis382.carelog.summary.WeeklyReport.VitalStat;
import io.github.andis382.carelog.vitals.VitalKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Words for the weekly WhatsApp summary, in the reader's language. Empty sections stay empty. */
@Component
public class SummaryMessage {

    private static final int MAX_PROBLEMS = 4;
    private static final int MAX_NOTES = 2;
    private static final int NOTE_LENGTH = 90;

    private final Texts texts;

    public SummaryMessage(Texts texts) {
        this.texts = texts;
    }

    public Map<String, String> params(WeeklyReport r, String locale, String elder, ZoneId zone) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("elder", elder);
        p.put("week", week(r.weekStart(), r.weekEnd(), locale));
        p.put("doses", doses(r, locale));
        p.put("problems", problems(r.problems(), locale));
        p.put("vitals", vitals(r.vitals(), locale));
        p.put("meals", meals(r.meals(), locale));
        p.put("notes", notes(r.notes(), locale, zone));
        p.put("supplies", r.suppliesLow().isEmpty() ? "" : texts.in(locale, "summary.supplies", String.join(", ", r.suppliesLow())));
        p.put("hours", hours(r.duty(), locale));
        return p;
    }

    static String week(LocalDate start, LocalDate end, String locale) {
        Locale loc = Locale.forLanguageTag(locale);
        DateTimeFormatter dayMonth = DateTimeFormatter.ofPattern("d MMMM", loc);
        if (start.getMonth() == end.getMonth()) {
            return start.getDayOfMonth() + "–" + dayMonth.format(end);
        }
        return dayMonth.format(start) + " – " + dayMonth.format(end);
    }

    private String doses(WeeklyReport r, String locale) {
        if (r.doses().due() == 0) {
            return texts.in(locale, "summary.doses_none");
        }
        return texts.in(locale, "summary.doses", String.valueOf(r.doses().adherencePct()), String.valueOf(r.doses().given()),
            String.valueOf(r.doses().due()));
    }

    private String problems(List<Problem> problems, String locale) {
        if (problems.isEmpty()) {
            return "";
        }
        DateTimeFormatter day = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag(locale));
        String listed = problems.stream().limit(MAX_PROBLEMS)
            .map(p -> texts.in(locale, "summary.problem", day.format(p.date()), p.time(), p.medicine(),
                texts.in(locale, "summary.state." + p.state())))
            .collect(Collectors.joining("; "));
        if (problems.size() > MAX_PROBLEMS) {
            listed += "; " + texts.in(locale, "summary.more", String.valueOf(problems.size() - MAX_PROBLEMS));
        }
        return texts.in(locale, "summary.problems", listed);
    }

    /** Blood pressure, sugar and weight always; temperature, pulse and oxygen only when something was unusual. */
    private String vitals(List<VitalStat> stats, String locale) {
        return stats.stream()
            .filter(s -> VitalKind.valueOf(s.kind()).alwaysSummarised() || s.outsideRange() > 0)
            .map(s -> vitalLine(s, VitalKind.valueOf(s.kind()).decimals(), locale))
            .collect(Collectors.joining("\n"));
    }

    private String vitalLine(VitalStat s, int decimals, String locale) {
        String name = texts.in(locale, "summary.vital_name." + s.kind());
        String unit = texts.in(locale, "summary.unit." + s.kind());
        if (s.count() == 1) {
            return texts.in(locale, "summary.vital_single", name, value(s.avg(), s.avg2(), decimals, locale), unit);
        }
        return texts.in(locale, "summary.vital", name, value(s.avg(), s.avg2(), decimals, locale), unit,
            value(s.min(), s.min2(), decimals, locale), value(s.max(), s.max2(), decimals, locale), String.valueOf(s.count()));
    }

    private String meals(WeeklyReport.Meals meals, String locale) {
        if (meals.mainMeals() == 0) {
            return texts.in(locale, "summary.meals_none");
        }
        if (meals.glassesPerDay() == null || meals.glassesPerDay().signum() == 0) {
            return texts.in(locale, "summary.meals_no_water", String.valueOf(meals.ateWell()), String.valueOf(meals.mainMeals()));
        }
        return texts.in(locale, "summary.meals", String.valueOf(meals.ateWell()), String.valueOf(meals.mainMeals()),
            number(meals.glassesPerDay(), locale));
    }

    private String notes(List<Notable> notes, String locale, ZoneId zone) {
        if (notes.isEmpty()) {
            return "";
        }
        DateTimeFormatter day = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag(locale));
        String listed = notes.stream().limit(MAX_NOTES)
            .map(n -> texts.in(locale, "summary.note", day.format(n.at().atZone(zone)), n.by() == null ? "" : firstName(n.by()),
                noteText(n, locale)))
            .collect(Collectors.joining(" "));
        return texts.in(locale, "summary.notes", listed);
    }

    private String noteText(Notable n, String locale) {
        if (n.text() != null && !n.text().isBlank()) {
            String text = n.text().strip();
            return text.length() > NOTE_LENGTH ? text.substring(0, NOTE_LENGTH).strip() + "…" : text;
        }
        return texts.in(locale, "summary.note_kind." + n.kind(), n.score() == null ? "" : String.valueOf(n.score()));
    }

    private String hours(List<Duty> duty, String locale) {
        String listed = duty.stream()
            .filter(d -> d.name() != null && Math.max(d.checkedInMinutes(), d.scheduledMinutes()) > 0)
            .map(d -> texts.in(locale, "summary.hour", firstName(d.name()),
                String.valueOf(Math.round((d.checkedInMinutes() > 0 ? d.checkedInMinutes() : d.scheduledMinutes()) / 60.0))))
            .collect(Collectors.joining(", "));
        return listed.isEmpty() ? "" : texts.in(locale, "summary.hours", listed);
    }

    private static String value(BigDecimal first, BigDecimal second, int decimals, String locale) {
        String shown = number(first.setScale(decimals, RoundingMode.HALF_UP), locale);
        return second == null ? shown : shown + "/" + number(second.setScale(0, RoundingMode.HALF_UP), locale);
    }

    private static String number(BigDecimal value, String locale) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag(locale));
        format.setMaximumFractionDigits(1);
        format.setGroupingUsed(false);
        return format.format(value);
    }

    private static String firstName(String name) {
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }
}
