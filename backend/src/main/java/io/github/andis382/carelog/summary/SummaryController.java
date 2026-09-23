package io.github.andis382.carelog.summary;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.summary.SummaryService.Preview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final SummaryService summaries;
    private final CircleService circle;
    private final CircleTime time;
    private final CurrentUser currentUser;

    public SummaryController(SummaryService summaries, CircleService circle, CircleTime time, CurrentUser currentUser) {
        this.summaries = summaries;
        this.circle = circle;
        this.time = time;
        this.currentUser = currentUser;
    }

    public record SendRequest(@NotNull LocalDate week) {}

    public record SentView(Long id, LocalDate weekStart, Instant generatedAt, String source, String sentTo, Integer adherencePct,
                           String content) {
        static SentView of(WeeklySummary s) {
            return new SentView(s.getId(), s.getWeekStart(), s.getGeneratedAt(), s.getSource().name(), s.getSentToName(),
                s.getAdherencePct(), s.getContent());
        }
    }

    public record SummaryPage(WeeklyReport report, Preview preview, List<SentView> sent, LocalDate thisWeek,
                              LocalDate visibleFrom) {}

    /** Defaults to last week, the one a Sunday summary was about. */
    @GetMapping
    public SummaryPage page(@RequestParam(required = false) LocalDate week) {
        Long orgId = currentUser.organizationId();
        LocalDate thisWeek = SummaryService.mondayOf(time.today(orgId));
        LocalDate start = week == null ? thisWeek.minusWeeks(1) : SummaryService.mondayOf(week);
        if (start.isAfter(thisWeek)) {
            throw ApiException.badRequest("error.bad_request");
        }
        LocalDate visibleFrom = circle.visibleFrom(orgId);
        if (visibleFrom != null && start.plusDays(6).isBefore(visibleFrom)) {
            throw ApiException.conflict("plan.history_limit");
        }
        WeeklyReport report = summaries.report(orgId, start);
        List<SentView> sent = summaries.history(orgId).stream().map(SentView::of).toList();
        return new SummaryPage(report, summaries.preview(orgId, report), sent, thisWeek, visibleFrom);
    }

    @PostMapping("/send")
    public SentView send(@Valid @RequestBody SendRequest req) {
        currentUser.requirePlanner();
        Long orgId = currentUser.organizationId();
        LocalDate start = SummaryService.mondayOf(req.week());
        if (start.isAfter(SummaryService.mondayOf(time.today(orgId)))) {
            throw ApiException.badRequest("error.bad_request");
        }
        return SentView.of(summaries.send(orgId, start, WeeklySummary.Source.MANUAL));
    }
}
