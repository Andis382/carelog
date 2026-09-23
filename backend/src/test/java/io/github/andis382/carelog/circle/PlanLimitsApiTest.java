package io.github.andis382.carelog.circle;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PlanLimitsApiTest extends CircleTest {

    @Test
    void theFreePlanRefusesAThirdMember() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        member(elira, "FAMILY", "Gent Kola", "gent@example.com");

        postJson(elira, "/api/team/invitations", "{\"role\":\"CARER\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("two members")));
    }

    @Test
    void anOpenJoinLinkTakesASeatOnTheFreePlan() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        postJson(elira, "/api/team/invitations", "{\"role\":\"CARER\"}").andExpect(status().isCreated());

        postJson(elira, "/api/team/invitations", "{\"role\":\"VIEWER\"}").andExpect(status().isConflict());
    }

    @Test
    void theFamilyPlanHasRoomForTheWholeCircle() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        upgrade(elira);
        member(elira, "FAMILY", "Gent Kola", "gent@example.com");
        member(elira, "CARER", "Mira Hasani", "mira@example.com");
        member(elira, "VIEWER", "Arta Marku", "arta@example.com");

        getJson(elira, "/api/circle")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan").value("FAMILY"))
            .andExpect(jsonPath("$.members.length()").value(4));
        putJson(elira, "/api/circle/plan", "{\"plan\":\"FREE\",\"payerUserId\":" + userId(elira) + "}")
            .andExpect(status().isConflict());
    }

    @Test
    void joiningIsRefusedWhenLinksOutliveADowngrade() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        upgrade(elira);
        String first = inviteToken(elira, "FAMILY");
        String second = inviteToken(elira, "CARER");
        putJson(elira, "/api/circle/plan", "{\"plan\":\"FREE\",\"payerUserId\":" + userId(elira) + "}").andExpect(status().isOk());

        join(first, "gent@example.com").andExpect(status().isCreated());
        join(second, "mira@example.com").andExpect(status().isConflict());
    }

    @Test
    void theFreePlanHidesHistoryOlderThanThirtyDaysUntilTheCircleUpgrades() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        long orgId = organizationId(elira);
        LocalDate old = today().minusDays(40);
        jdbc.update("INSERT INTO vitals (organization_id, kind, value1, value2, measured_at, recorded_by) VALUES (?, 'BP', 138, 84, ?, ?)",
            orgId, Timestamp.from(old.atTime(8, 30).atZone(TIRANE).toInstant()), userId(elira));
        jdbc.update("INSERT INTO vitals (organization_id, kind, value1, value2, measured_at, recorded_by) VALUES (?, 'BP', 128, 79, ?, ?)",
            orgId, Timestamp.from(today().minusDays(2).atTime(8, 30).atZone(TIRANE).toInstant()), userId(elira));

        getJson(elira, "/api/vitals?kind=BP&days=60")
            .andExpect(jsonPath("$.readings.length()").value(1))
            .andExpect(jsonPath("$.visibleFrom").value(today().minusDays(30).toString()));
        getJson(elira, "/api/log?to=" + old.plusDays(3) + "&days=7")
            .andExpect(jsonPath("$.limitedByPlan").value(true))
            .andExpect(jsonPath("$.entries.length()").value(0));

        upgrade(elira);

        getJson(elira, "/api/vitals?kind=BP&days=60").andExpect(jsonPath("$.readings.length()").value(2));
        getJson(elira, "/api/log?to=" + old.plusDays(3) + "&days=7")
            .andExpect(jsonPath("$.limitedByPlan").value(false))
            .andExpect(jsonPath("$.entries[0].type").value("VITALS"));
    }

    private String inviteToken(Cookie owner, String role) throws Exception {
        String url = read(postJson(owner, "/api/team/invitations", "{\"role\":\"" + role + "\"}").andExpect(status().isCreated()))
            .get("url").asString();
        return url.replaceAll(".*/join/", "");
    }

    private ResultActions join(String token, String email) throws Exception {
        return mvc.perform(post("/api/auth/join").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"token":"%s","name":"Someone","email":"%s","password":"secret123"}
            """.formatted(token, email)));
    }
}
