package io.github.andis382.carelog.circle;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RolePermissionsApiTest extends CircleTest {

    private static final String READING = "{\"kind\":\"BP\",\"value1\":132,\"value2\":84}";

    private Cookie elira;
    private Cookie gent;
    private Cookie mira;
    private Cookie arta;
    private long metformin;

    @BeforeEach
    void circle() throws Exception {
        elira = coordinator("elira@example.com");
        upgrade(elira);
        gent = member(elira, "FAMILY", "Gent Kola", "gent@example.com");
        mira = member(elira, "CARER", "Mira Hasani", "mira@example.com");
        arta = member(elira, "VIEWER", "Arta Marku", "arta@example.com");
        metformin = dailyMedicine(elira, "Metformin", "08:00");
    }

    @Test
    void aViewerCanReadTheLogButNotWriteToIt() throws Exception {
        getJson(arta, "/api/today").andExpect(status().isOk());
        getJson(arta, "/api/log").andExpect(status().isOk());

        postJson(arta, "/api/vitals", READING).andExpect(status().isForbidden());
        postJson(arta, "/api/doses", """
            {"medicationId":%d,"date":"%s","time":"08:00","status":"GIVEN"}
            """.formatted(metformin, today())).andExpect(status().isForbidden());
        postJson(arta, "/api/journal", "{\"kind\":\"NOTE\",\"text\":\"Hello\"}").andExpect(status().isForbidden());
        postJson(arta, "/api/checkins/in", "{}").andExpect(status().isForbidden());
    }

    @Test
    void aCarerRecordsCareButCannotManageThePeopleOrThePlan() throws Exception {
        postJson(mira, "/api/vitals", READING).andExpect(status().isCreated());
        postJson(mira, "/api/meals", "{\"slot\":\"LUNCH\",\"amount\":\"HALF\"}").andExpect(status().isCreated());

        postJson(mira, "/api/team/invitations", "{\"role\":\"CARER\"}").andExpect(status().isForbidden());
        deleteJson(mira, "/api/team/members/" + userId(arta)).andExpect(status().isForbidden());
        putJson(mira, "/api/circle/plan", "{\"plan\":\"FREE\",\"payerUserId\":" + userId(mira) + "}").andExpect(status().isForbidden());
        postJson(mira, "/api/medications", """
            {"name":"Aspirin","frequency":"DAILY","times":["09:00"],"startDate":"%s"}
            """.formatted(today())).andExpect(status().isForbidden());
    }

    @Test
    void familyPlansCareButOnlyTheCoordinatorRunsTheCircle() throws Exception {
        postJson(gent, "/api/medications", """
            {"name":"Aspirin","strength":"100 mg","frequency":"DAILY","times":["09:00"],"startDate":"%s"}
            """.formatted(today())).andExpect(status().isCreated());
        putJson(gent, "/api/elder", "{\"fullName\":\"Drita Kola\",\"birthYear\":1945}").andExpect(status().isOk());

        putJson(gent, "/api/circle/settings", "{\"graceMinutes\":45,\"doseAlerts\":true,\"weeklySummary\":true}")
            .andExpect(status().isForbidden());
        postJson(gent, "/api/team/invitations", "{\"role\":\"VIEWER\"}").andExpect(status().isForbidden());
    }

    @Test
    void theCoordinatorCannotHandOutHerOwnRoleByLink() throws Exception {
        postJson(elira, "/api/team/invitations", "{\"role\":\"OWNER\"}").andExpect(status().isUnprocessableContent());
    }

    @Test
    void aRemovedMemberIsSignedOutButKeepsHerNameOnWhatSheRecorded() throws Exception {
        postJson(mira, "/api/vitals", READING).andExpect(status().isCreated());

        deleteJson(elira, "/api/team/members/" + userId(mira)).andExpect(status().isNoContent());

        getJson(mira, "/api/today").andExpect(status().isUnauthorized());
        getJson(elira, "/api/vitals?kind=BP&days=7")
            .andExpect(jsonPath("$.readings[0].by").value("Mira Hasani"));
        getJson(elira, "/api/circle")
            .andExpect(jsonPath("$.members.length()").value(3));
    }
}
