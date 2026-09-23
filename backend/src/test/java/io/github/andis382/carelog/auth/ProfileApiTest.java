package io.github.andis382.carelog.auth;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

class ProfileApiTest extends CircleTest {

    @Test
    void thePhoneIsStoredInternationalAndKeptWhenAnUpdateLeavesItOut() throws Exception {
        Cookie elira = coordinator("elira@example.com");

        putJson(elira, "/api/auth/me", "{\"name\":\"Elira Kola\",\"phone\":\"069 204 1187\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.phone").value("355692041187"));
        putJson(elira, "/api/auth/me", "{\"name\":\"Elira Kola\",\"locale\":\"sq\"}")
            .andExpect(jsonPath("$.user.phone").value("355692041187"))
            .andExpect(jsonPath("$.user.locale").value("sq"));
        putJson(elira, "/api/auth/me", "{\"name\":\"Elira Kola\",\"phone\":\"\"}")
            .andExpect(jsonPath("$.user.phone").value(nullValue()));
    }
}
