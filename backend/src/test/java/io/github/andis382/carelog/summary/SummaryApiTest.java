package io.github.andis382.carelog.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SummaryApiTest extends CircleTest {

    private Cookie elira;
    private LocalDate lastMonday;

    @BeforeEach
    void lastWeek() throws Exception {
        elira = coordinator("elira@example.com");
        long metformin = dailyMedicine(elira, "Metformin", "08:00");
        putJson(elira, "/api/elder", "{\"fullName\":\"Drita Kola\",\"birthYear\":1945}").andExpect(status().isOk());
        lastMonday = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        long orgId = organizationId(elira);
        long by = userId(elira);
        for (int day = 0; day < 7; day++) {
            LocalDate date = lastMonday.plusDays(day);
            String status = day == 2 ? "REFUSED" : "GIVEN";
            jdbc.update("""
                INSERT INTO dose_events (organization_id, medication_id, dose_date, scheduled_time, status, note, recorded_by, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                orgId, metformin, Date.valueOf(date), Time.valueOf(LocalTime.of(8, 0)), status,
                day == 2 ? "Felt sick" : null, by, Timestamp.from(date.atTime(8, 5).atZone(TIRANE).toInstant()));
        }
    }

    @Test
    void thePageShowsLastWeekAndTheMessageThePayerWouldGet() throws Exception {
        getJson(elira, "/api/summary")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.report.weekStart").value(lastMonday.toString()))
            .andExpect(jsonPath("$.report.doses.due").value(7))
            .andExpect(jsonPath("$.report.doses.given").value(6))
            .andExpect(jsonPath("$.report.doses.adherencePct").value(86))
            .andExpect(jsonPath("$.report.problems[0].state").value("REFUSED"))
            .andExpect(jsonPath("$.preview.payerHasPhone").value(false));
    }

    @Test
    void sendNowWritesToThePayerOnWhatsApp() throws Exception {
        setPhone(elira, "Elira Kola", "+355 69 204 1187");

        postJson(elira, "/api/summary/send", "{\"week\":\"" + lastMonday + "\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.adherencePct").value(86))
            .andExpect(jsonPath("$.sentTo").value("Elira Kola"));

        String body = jdbc.queryForObject(
            "SELECT body FROM outbound_messages WHERE template_key = 'weekly_summary' AND recipient = '355692041187'", String.class);
        assertThat(body)
            .contains("Drita")
            .contains("86% of medicines given (6 of 7 doses)")
            .contains("refused")
            .contains("does not replace being there");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM weekly_summaries", Integer.class)).isEqualTo(1);
    }

    @Test
    void withoutAPhoneNumberThereIsNobodyToSendTo() throws Exception {
        postJson(elira, "/api/summary/send", "{\"week\":\"" + lastMonday + "\"}").andExpect(status().isConflict());
    }
}
