package io.github.andis382.carelog.alerts;

import io.github.andis382.carelog.circle.CircleSettings;
import io.github.andis382.carelog.circle.CircleSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class MissedDoseScheduler {

    private static final Logger log = LoggerFactory.getLogger(MissedDoseScheduler.class);

    private final CircleSettingsRepository settings;
    private final MissedDoseWatch watch;

    public MissedDoseScheduler(CircleSettingsRepository settings, MissedDoseWatch watch) {
        this.settings = settings;
        this.watch = watch;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void checkLateDoses() {
        for (CircleSettings circle : settings.findByDoseAlertsTrue()) {
            try {
                watch.check(circle.getOrganizationId());
            } catch (RuntimeException e) {
                log.warn("Missed-dose check failed for circle {}", circle.getOrganizationId(), e);
            }
        }
    }
}
