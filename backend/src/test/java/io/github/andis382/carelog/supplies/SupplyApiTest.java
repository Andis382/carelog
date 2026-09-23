package io.github.andis382.carelog.supplies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

class SupplyApiTest extends CircleTest {

    @Test
    void runningLowTellsTheCoordinatorOnceAndRestockingIsQuiet() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        setPhone(elira, "Elira Kola", "069 204 1187");
        Cookie mira = member(elira, "CARER", "Mira Hasani", "mira@example.com");
        long diapers = read(postJson(mira, "/api/supplies", "{\"name\":\"Diapers (L)\"}").andExpect(status().isCreated()))
            .get("id").asLong();

        putJson(mira, "/api/supplies/" + diapers + "/status", "{\"status\":\"RUNNING_LOW\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.supply.status").value("RUNNING_LOW"))
            .andExpect(jsonPath("$.supply.updatedBy").value("Mira Hasani"))
            .andExpect(jsonPath("$.notified[0]").value("Elira Kola"));
        putJson(mira, "/api/supplies/" + diapers + "/status", "{\"status\":\"RUNNING_LOW\"}")
            .andExpect(jsonPath("$.notified.length()").value(0));
        putJson(elira, "/api/supplies/" + diapers + "/status", "{\"status\":\"OK\"}")
            .andExpect(jsonPath("$.notified.length()").value(0));

        String body = jdbc.queryForObject("SELECT body FROM outbound_messages WHERE template_key = 'supplies_low'", String.class);
        assertThat(body).contains("Elira").contains("Mira").contains("Diapers (L)").contains("running low");
        getJson(elira, "/api/log?types=SUPPLIES").andExpect(jsonPath("$.entries.length()").value(3));
    }

    @Test
    void theSameItemCannotBeListedTwice() throws Exception {
        Cookie elira = coordinator("elira@example.com");
        postJson(elira, "/api/supplies", "{\"name\":\"Gloves\"}").andExpect(status().isCreated());
        postJson(elira, "/api/supplies", "{\"name\":\"gloves\"}").andExpect(status().isUnprocessableContent());
    }
}
