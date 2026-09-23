package io.github.andis382.carelog.meds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class DoseApiTest extends CircleTest {

    private Cookie elira;
    private Cookie mira;
    private long metformin;

    @BeforeEach
    void circle() throws Exception {
        elira = coordinator("elira@example.com");
        mira = member(elira, "CARER", "Mira Hasani", "mira@example.com");
        metformin = dailyMedicine(elira, "Metformin", "08:00", "20:00");
    }

    private String dose(String time, String status, String extra) {
        return """
            {"medicationId":%d,"date":"%s","time":"%s","status":"%s"%s}
            """.formatted(metformin, today(), time, status, extra);
    }

    @Test
    void aSecondGivenForTheSameDoseIsRefusedWithWhoAndWhen() throws Exception {
        postJson(mira, "/api/doses", dose("08:00", "GIVEN", ""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recordedBy").value("Mira Hasani"));

        JsonNode conflict = read(mvc.perform(post("/api/doses").cookie(elira).with(csrf()).header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON).content(dose("08:00", "GIVEN", "")))
            .andExpect(status().isConflict()));

        assertThat(conflict.get("message").asString()).matches("Already given by Mira at \\d\\d:\\d\\d\\.");
        assertThat(conflict.get("details").get("recordedBy").asString()).isEqualTo("Mira Hasani");
        assertThat(conflict.get("details").get("status").asString()).isEqualTo("GIVEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dose_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void refusedAfterGivenIsAlsoRefused() throws Exception {
        postJson(mira, "/api/doses", dose("20:00", "GIVEN", "")).andExpect(status().isCreated());
        postJson(elira, "/api/doses", dose("20:00", "REFUSED", ",\"note\":\"Would not take it\""))
            .andExpect(status().isConflict());
    }

    @Test
    void aRetriedOfflineUploadIsRecognisedInsteadOfDoubled() throws Exception {
        String body = dose("08:00", "GIVEN", ",\"clientId\":\"phone-1-abc\",\"at\":\"" + Instant.now().minusSeconds(600) + "\"");
        long first = read(postJson(mira, "/api/doses", body).andExpect(status().isCreated())).get("id").asLong();
        long again = read(postJson(mira, "/api/doses", body).andExpect(status().isCreated())).get("id").asLong();

        assertThat(again).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dose_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void refusedAndSkippedNeedAReason() throws Exception {
        postJson(mira, "/api/doses", dose("08:00", "REFUSED", ""))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.errors.note[0]").exists());
        postJson(mira, "/api/doses", dose("08:00", "SKIPPED", ",\"note\":\"Sugar 68, doctor said to skip\""))
            .andExpect(status().isCreated());
    }

    @Test
    void aTimeThatIsNotOnTheScheduleIsRejected() throws Exception {
        postJson(mira, "/api/doses", dose("09:00", "GIVEN", ""))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.errors.time[0]").exists());
    }

    @Test
    void onlyTheRecorderCanUndoAMisTap() throws Exception {
        long id = read(postJson(elira, "/api/doses", dose("08:00", "GIVEN", "")).andExpect(status().isCreated())).get("id").asLong();

        deleteJson(mira, "/api/doses/" + id).andExpect(status().isForbidden());
        deleteJson(elira, "/api/doses/" + id).andExpect(status().isNoContent());
        postJson(mira, "/api/doses", dose("08:00", "GIVEN", "")).andExpect(status().isCreated());
    }

    @Test
    void theDailyCardShowsTheDoseAndWhoGaveIt() throws Exception {
        postJson(mira, "/api/doses", dose("08:00", "GIVEN", "")).andExpect(status().isCreated());

        getJson(elira, "/api/today")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.doses.length()").value(2))
            .andExpect(jsonPath("$.doses[0].time").value("08:00"))
            .andExpect(jsonPath("$.doses[0].state").value("GIVEN"))
            .andExpect(jsonPath("$.doses[0].event.by").value("Mira Hasani"))
            .andExpect(jsonPath("$.doses[1].time").value("20:00"));
    }
}
