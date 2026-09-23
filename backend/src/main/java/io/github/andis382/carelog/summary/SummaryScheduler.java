package io.github.andis382.carelog.summary;

import io.github.andis382.carelog.circle.CircleSettings;
import io.github.andis382.carelog.circle.CircleSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks every ten minutes whether it is Sunday evening somewhere. Each circle keeps its own
 * time zone, so "Sunday 19:00" is the elder's Sunday, not the server's.
 */
@Component
@Profile("!test")
public class SummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SummaryScheduler.class);

    private final CircleSettingsRepository settings;
    private final SummaryService summaries;

    public SummaryScheduler(CircleSettingsRepository settings, SummaryService summaries) {
        this.settings = settings;
        this.summaries = summaries;
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void sendDueSummaries() {
        for (CircleSettings circle : settings.findByWeeklySummaryTrue()) {
            try {
                if (summaries.sendIfDue(circle.getOrganizationId())) {
                    log.info("Weekly summary sent for circle {}", circle.getOrganizationId());
                }
            } catch (RuntimeException e) {
                log.warn("Weekly summary failed for circle {}", circle.getOrganizationId(), e);
            }
        }
    }
}
