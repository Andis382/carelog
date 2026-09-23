package io.github.andis382.carelog.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MissedDoseWatchTest extends CircleTest {

    @Autowired
    private MissedDoseWatch watch;

    @Test
    void aDoseMoreThanTwoHoursLateAlertsTheCoordinatorOnce() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        setPhone(elira, "Elira Kola", "069 204 1187");
        putJson(elira, "/api/elder", "{\"fullName\":\"Drita Kola\"}").andExpect(status().isOk());
        String twoAndAHalfHoursAgo = LocalDateTime.now(TIRANE).minusMinutes(150).format(DateTimeFormatter.ofPattern("HH:mm"));
        dailyMedicine(elira, "Atorvastatin", twoAndAHalfHoursAgo);
        long orgId = organizationId(elira);

        assertThat(watch.check(orgId)).isEqualTo(1);
        assertThat(watch.check(orgId)).isZero();

        String body = jdbc.queryForObject("SELECT body FROM outbound_messages WHERE template_key = 'dose_missed'", String.class);
        assertThat(body).contains("Drita").contains(twoAndAHalfHoursAgo).contains("Atorvastatin 500 mg");
    }

    @Test
    void aRecordedDoseRaisesNoAlert() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        setPhone(elira, "Elira Kola", "069 204 1187");
        LocalDateTime slot = LocalDateTime.now(TIRANE).minusMinutes(150);
        String time = slot.format(DateTimeFormatter.ofPattern("HH:mm"));
        long statin = dailyMedicine(elira, "Atorvastatin", time);
        postJson(elira, "/api/doses", """
            {"medicationId":%d,"date":"%s","time":"%s","status":"GIVEN"}
            """.formatted(statin, slot.toLocalDate(), time)).andExpect(status().isCreated());

        assertThat(watch.check(organizationId(elira))).isZero();
    }
}
