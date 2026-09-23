package io.github.andis382.carelog.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full application against a real PostgreSQL database (see application-test.yml).
 * Every test starts from empty tables. Sessions are Spring Session (JDBC), so a signed-in
 * client is represented by its SESSION cookie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        List<String> tables = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
            String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }

    /** Registers a new owner + organisation and returns the session cookie. */
    protected Cookie registerOwner(String email, String organizationName) throws Exception {
        String body = """
            {"name":"Owner","email":"%s","password":"secret123","organizationName":"%s","locale":"en"}
            """.formatted(email, organizationName);
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("register failed: " + result.getResponse().getContentAsString());
        }
        return sessionCookie(result);
    }

    protected static Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) {
            throw new IllegalStateException("No SESSION cookie in response");
        }
        return cookie;
    }
}
