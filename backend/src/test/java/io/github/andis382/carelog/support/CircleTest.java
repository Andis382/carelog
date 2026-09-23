package io.github.andis382.carelog.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Care-circle helpers: a coordinator, members joining by link, the Family plan, JSON calls. */
public abstract class CircleTest extends IntegrationTest {

    protected static final ZoneId TIRANE = ZoneId.of("Europe/Tirane");

    @Autowired
    protected ObjectMapper json;

    protected static LocalDate today() {
        return LocalDate.now(TIRANE);
    }

    protected Cookie coordinator(String email) throws Exception {
        return registerOwner(email, "Nëna Drita");
    }

    /** Invites someone with a role and joins with the link, as a new person would. */
    protected Cookie member(Cookie owner, String role, String name, String email) throws Exception {
        String invite = postJson(owner, "/api/team/invitations", "{\"role\":\"" + role + "\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(invite).get("url").asString().replaceAll(".*/join/", "");
        MvcResult joined = mvc.perform(post("/api/auth/join").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"%s","name":"%s","email":"%s","password":"secret123","locale":"en"}
                    """.formatted(token, name, email)))
            .andExpect(status().isCreated())
            .andReturn();
        return sessionCookie(joined);
    }

    protected long userId(Cookie session) throws Exception {
        return me(session).get("user").get("id").asLong();
    }

    protected JsonNode me(Cookie session) throws Exception {
        return read(mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk()));
    }

    protected void setPhone(Cookie session, String name, String phone) throws Exception {
        putJson(session, "/api/auth/me", "{\"name\":\"" + name + "\",\"phone\":\"" + phone + "\",\"locale\":\"en\"}")
            .andExpect(status().isOk());
    }

    protected void upgrade(Cookie owner) throws Exception {
        putJson(owner, "/api/circle/plan", "{\"plan\":\"FAMILY\",\"payerUserId\":" + userId(owner) + "}")
            .andExpect(status().isOk());
    }

    /** A daily medicine started a while ago, returning its id. */
    protected long dailyMedicine(Cookie planner, String name, String... times) throws Exception {
        String list = String.join("\",\"", times);
        String body = """
            {"name":"%s","strength":"500 mg","doseText":"1 tablet","frequency":"DAILY","times":["%s"],"startDate":"%s"}
            """.formatted(name, list, today().minusDays(20));
        return read(postJson(planner, "/api/medications", body).andExpect(status().isCreated())).get("id").asLong();
    }

    protected ResultActions postJson(Cookie session, String url, String body) throws Exception {
        return mvc.perform(post(url).cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions putJson(Cookie session, String url, String body) throws Exception {
        return mvc.perform(put(url).cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions getJson(Cookie session, String url) throws Exception {
        return mvc.perform(get(url).cookie(session).header("Accept-Language", "en"));
    }

    protected ResultActions deleteJson(Cookie session, String url) throws Exception {
        return mvc.perform(delete(url).cookie(session).with(csrf()));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected long organizationId(Cookie session) throws Exception {
        return me(session).get("organization").get("id").asLong();
    }
}
