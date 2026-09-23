package io.github.andis382.carelog.circle;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.andis382.carelog.support.CircleTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Health data stays inside its circle: another circle cannot read, record against or even see it. */
class TenantIsolationApiTest extends CircleTest {

    @Test
    void anotherCircleCannotSeeOrTouchAMedicineOrItsDoses() throws Exception {
        Cookie kola = coordinator("elira@example.com");
        Cookie hoxha = coordinator("besa@example.com");
        long metformin = dailyMedicine(kola, "Metformin", "08:00");

        getJson(hoxha, "/api/medications").andExpect(jsonPath("$.length()").value(0));
        getJson(hoxha, "/api/medications/" + metformin).andExpect(status().isNotFound());
        putJson(hoxha, "/api/medications/" + metformin, """
            {"name":"Changed","frequency":"DAILY","times":["09:00"],"startDate":"%s"}
            """.formatted(today())).andExpect(status().isNotFound());
        postJson(hoxha, "/api/doses", """
            {"medicationId":%d,"date":"%s","time":"08:00","status":"GIVEN"}
            """.formatted(metformin, today())).andExpect(status().isUnprocessableContent());
        getJson(hoxha, "/api/today").andExpect(jsonPath("$.doses.length()").value(0));

        getJson(kola, "/api/medications/" + metformin).andExpect(jsonPath("$.medication.name").value("Metformin"));
    }

    @Test
    void readingsNotesAndPhotosStayInTheirCircle() throws Exception {
        Cookie kola = coordinator("elira@example.com");
        Cookie hoxha = coordinator("besa@example.com");
        postJson(kola, "/api/vitals", "{\"kind\":\"SUGAR\",\"value1\":128}").andExpect(status().isCreated());
        postJson(kola, "/api/journal", "{\"kind\":\"NOTE\",\"text\":\"Slept well\"}").andExpect(status().isCreated());
        MockMultipartFile photo = new MockMultipartFile("file", "box.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2});
        String fileId = read(mvc.perform(multipart("/api/uploads").file(photo).cookie(kola).with(csrf()))
            .andExpect(status().isCreated())).get("id").asString();

        getJson(hoxha, "/api/vitals?kind=SUGAR&days=7").andExpect(jsonPath("$.readings.length()").value(0));
        getJson(hoxha, "/api/log").andExpect(jsonPath("$.entries.length()").value(0));
        getJson(hoxha, "/api/files/" + fileId).andExpect(status().isNotFound());
        postJson(hoxha, "/api/journal", "{\"kind\":\"NOTE\",\"text\":\"x\",\"photoFileId\":\"" + fileId + "\"}")
            .andExpect(status().isUnprocessableContent());

        getJson(kola, "/api/log").andExpect(jsonPath("$.entries.length()").value(2));
    }
}
