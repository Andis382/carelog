package io.github.andis382.carelog.summary;

import io.github.andis382.carelog.alerts.CircleNotifier;
import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.auth.UserRepository;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleSettings;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.config.AppProperties;
import io.github.andis382.carelog.daily.JournalRepository;
import io.github.andis382.carelog.daily.MealRepository;
import io.github.andis382.carelog.meds.DoseEventRepository;
import io.github.andis382.carelog.meds.MedicationRepository;
import io.github.andis382.carelog.messaging.Messenger;
import io.github.andis382.carelog.messaging.Messenger.Outgoing;
import io.github.andis382.carelog.messaging.OutboundMessage;
import io.github.andis382.carelog.messaging.TemplateRenderer;
import io.github.andis382.carelog.rota.CheckInRepository;
import io.github.andis382.carelog.rota.ShiftRepository;
import io.github.andis382.carelog.supplies.SupplyRepository;
import io.github.andis382.carelog.vitals.VitalRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The weekly summary: computed live for the page, and sent to the payer on WhatsApp on
 * Sunday evening (or when someone presses "Send now").
 */
@Service
public class SummaryService {

    private static final String TEMPLATE = "weekly_summary";

    private final MedicationRepository medications;
    private final DoseEventRepository doses;
    private final VitalRepository vitals;
    private final MealRepository meals;
    private final JournalRepository journal;
    private final SupplyRepository supplies;
    private final ShiftRepository shifts;
    private final CheckInRepository checkIns;
    private final WeeklySummaryRepository summaries;
    private final UserRepository users;
    private final CircleService circle;
    private final CircleTime time;
    private final CircleNotifier notifier;
    private final SummaryMessage words;
    private final TemplateRenderer renderer;
    private final Messenger messenger;
    private final AppProperties props;

    public SummaryService(MedicationRepository medications, DoseEventRepository doses, VitalRepository vitals,
                          MealRepository meals, JournalRepository journal, SupplyRepository supplies, ShiftRepository shifts,
                          CheckInRepository checkIns, WeeklySummaryRepository summaries, UserRepository users,
                          CircleService circle, CircleTime time, CircleNotifier notifier, SummaryMessage words,
                          TemplateRenderer renderer, Messenger messenger, AppProperties props) {
        this.medications = medications;
        this.doses = doses;
        this.vitals = vitals;
        this.meals = meals;
        this.journal = journal;
        this.supplies = supplies;
        this.shifts = shifts;
        this.checkIns = checkIns;
        this.summaries = summaries;
        this.users = users;
        this.circle = circle;
        this.time = time;
        this.notifier = notifier;
        this.words = words;
        this.renderer = renderer;
        this.messenger = messenger;
        this.props = props;
    }

    /** The message exactly as the payer would receive it, in their language. */
    public record Preview(Long payerId, String payerName, boolean payerHasPhone, String locale, String text) {}

    public static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public WeeklyReport report(Long orgId, LocalDate weekStart) {
        ZoneId zone = time.zone(orgId);
        LocalDate weekEnd = weekStart.plusDays(6);
        Instant from = weekStart.atStartOfDay(zone).toInstant();
        Instant to = weekStart.plusDays(7).atStartOfDay(zone).toInstant();
        CircleSettings settings = circle.settings(orgId);
        return WeeklyReportBuilder.build(new WeeklyReportBuilder.Inputs(weekStart, time.localNow(orgId), zone, settings.grace(),
            medications.findByOrganizationIdOrderByNameAsc(orgId),
            doses.findByOrganizationIdAndDoseDateBetween(orgId, weekStart, weekEnd),
            vitals.findByOrganizationIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(orgId, from, to),
            circle.ranges(orgId),
            meals.findByOrganizationIdAndMealDateBetweenOrderByRecordedAtAsc(orgId, weekStart, weekEnd),
            journal.findByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtAsc(orgId, from, to),
            supplies.findByOrganizationIdOrderByNameAsc(orgId),
            shifts.findByOrganizationIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(orgId, weekStart, weekEnd),
            checkIns.findByOrganizationIdAndCheckedInAtBetweenOrderByCheckedInAtAsc(orgId, from, to),
            circle.names(orgId)));
    }

    public Preview preview(Long orgId, WeeklyReport report) {
        Optional<User> payer = payer(orgId);
        String locale = payer.map(User::getLocale).orElse(props.getDefaultLocale());
        String text = renderer.render(TEMPLATE, locale, params(orgId, report, locale));
        return new Preview(payer.map(User::getId).orElse(null), payer.map(User::getName).orElse(null),
            payer.map(u -> u.getPhone() != null).orElse(false), locale, text);
    }

    @Transactional
    public WeeklySummary send(Long orgId, LocalDate weekStart, WeeklySummary.Source source) {
        User payer = payer(orgId).filter(u -> u.getPhone() != null)
            .orElseThrow(() -> ApiException.conflict("summary.no_payer_phone"));
        WeeklyReport report = report(orgId, weekStart);
        OutboundMessage message = messenger.send(new Outgoing(orgId, payer.getPhone(), payer.getName(), TEMPLATE,
            payer.getLocale(), params(orgId, report, payer.getLocale()), null, "summary", null));
        return summaries.save(new WeeklySummary(orgId, weekStart, time.now(), source, payer.getId(), payer.getName(),
            report.doses().adherencePct(), message.getBody(), message.getId()));
    }

    public List<WeeklySummary> history(Long orgId) {
        return summaries.findTop12ByOrganizationIdOrderByGeneratedAtDesc(orgId);
    }

    /** Sunday from 19:00 circle time, once per week, for circles that want it. */
    @Transactional
    public boolean sendIfDue(Long orgId) {
        LocalDateTime now = time.localNow(orgId);
        if (now.getDayOfWeek() != DayOfWeek.SUNDAY || now.getHour() < 19) {
            return false;
        }
        LocalDate weekStart = mondayOf(now.toLocalDate());
        if (summaries.existsByOrganizationIdAndWeekStartAndSource(orgId, weekStart, WeeklySummary.Source.SCHEDULED)
            || payer(orgId).map(User::getPhone).isEmpty()) {
            return false;
        }
        send(orgId, weekStart, WeeklySummary.Source.SCHEDULED);
        return true;
    }

    private Map<String, String> params(Long orgId, WeeklyReport report, String locale) {
        Map<String, String> params = words.params(report, locale, notifier.elderName(orgId), time.zone(orgId));
        params.put("link", props.link("/summary?week=" + report.weekStart()));
        return params;
    }

    private Optional<User> payer(Long orgId) {
        Long payerId = circle.settings(orgId).getPayerUserId();
        return payerId == null ? Optional.empty() : users.findByIdAndOrganizationIdAndRemovedAtIsNull(payerId, orgId);
    }
}
