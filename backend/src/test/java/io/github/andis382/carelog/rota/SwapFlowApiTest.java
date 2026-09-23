package io.github.andis382.carelog.rota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SwapFlowApiTest extends CircleTest {

    private Cookie elira;
    private Cookie gent;
    private Cookie mira;
    private long miraSaturday;
    private LocalDate day;

    @BeforeEach
    void rota() throws Exception {
        elira = coordinator("elira@example.com");
        upgrade(elira);
        gent = member(elira, "FAMILY", "Gent Kola", "gent@example.com");
        mira = member(elira, "CARER", "Mira Hasani", "mira@example.com");
        setPhone(gent, "Gent Kola", "068 223 4518");
        day = today().plusDays(4);
        miraSaturday = read(postJson(elira, "/api/shifts", """
            {"userId":%d,"date":"%s","start":"08:00","end":"16:00","kind":"DAY"}
            """.formatted(userId(mira), day)).andExpect(status().isCreated())).get(0).get("id").asLong();
    }

    private long askGent() throws Exception {
        return read(postJson(mira, "/api/shifts/" + miraSaturday + "/swap",
                "{\"toUserId\":" + userId(gent) + ",\"message\":\"Family wedding on Saturday\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING")))
            .get("id").asLong();
    }

    @Test
    void acceptingASwapHandsTheShiftOver() throws Exception {
        long swap = askGent();

        getJson(gent, "/api/rota/mine")
            .andExpect(jsonPath("$.incoming[0].swap.fromName").value("Mira Hasani"))
            .andExpect(jsonPath("$.incoming[0].shift.date").value(day.toString()));
        postJson(gent, "/api/swaps/" + swap + "/accept", "{}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        getJson(elira, "/api/rota?week=" + day)
            .andExpect(jsonPath("$.shifts[0].userName").value("Gent Kola"))
            .andExpect(jsonPath("$.shifts[0].pendingSwap").doesNotExist());
    }

    @Test
    void theAskedMemberHearsAboutItOnWhatsApp() throws Exception {
        askGent();

        String body = jdbc.queryForObject(
            "SELECT body FROM outbound_messages WHERE template_key = 'shift_swap_request' AND recipient = '355682234518'",
            String.class);
        assertThat(body).contains("Mira").contains("08:00").contains("Family wedding");
    }

    @Test
    void decliningKeepsTheShiftWhereItWas() throws Exception {
        long swap = askGent();

        postJson(gent, "/api/swaps/" + swap + "/decline", "{}").andExpect(jsonPath("$.status").value("DECLINED"));

        getJson(elira, "/api/rota?week=" + day).andExpect(jsonPath("$.shifts[0].userName").value("Mira Hasani"));
        postJson(gent, "/api/swaps/" + swap + "/accept", "{}").andExpect(status().isConflict());
    }

    @Test
    void onlyThePersonAskedCanAnswerAndOnlyOneRequestAtATime() throws Exception {
        long swap = askGent();

        postJson(elira, "/api/swaps/" + swap + "/accept", "{}").andExpect(status().isForbidden());
        postJson(mira, "/api/shifts/" + miraSaturday + "/swap", "{\"toUserId\":" + userId(elira) + "}")
            .andExpect(status().isConflict());
        postJson(gent, "/api/shifts/" + miraSaturday + "/swap", "{\"toUserId\":" + userId(elira) + "}")
            .andExpect(status().isForbidden());
    }

    @Test
    void theRequesterCanWithdrawIt() throws Exception {
        long swap = askGent();

        postJson(mira, "/api/swaps/" + swap + "/cancel", "{}").andExpect(jsonPath("$.status").value("CANCELLED"));
        askGent();
    }

    @Test
    void aCarerMayAddHerOwnShiftButNotSomeoneElses() throws Exception {
        postJson(mira, "/api/shifts", """
            {"userId":%d,"date":"%s","start":"18:00","end":"21:00","kind":"VISIT"}
            """.formatted(userId(mira), day)).andExpect(status().isCreated());
        postJson(mira, "/api/shifts", """
            {"userId":%d,"date":"%s","start":"18:00","end":"21:00","kind":"VISIT"}
            """.formatted(userId(gent), day)).andExpect(status().isForbidden());
        postJson(elira, "/api/shifts", """
            {"userId":%d,"date":"%s","start":"12:00","end":"14:00","kind":"VISIT"}
            """.formatted(userId(mira), day)).andExpect(status().isConflict());
    }
}
