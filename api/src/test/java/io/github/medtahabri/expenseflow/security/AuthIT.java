package io.github.medtahabri.expenseflow.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.medtahabri.expenseflow.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIT extends IntegrationTest {

    private String login(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void login_returns_a_token_and_the_profile() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("Employee@Example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.user.managerName").value("Karim Manager"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void unknown_email_and_wrong_password_get_the_same_answer() throws Exception {
        String wrongPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("employee@example.com", "not the password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("nobody@example.com", "not the password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void invalid_login_payload_is_a_400_with_field_errors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("not-an-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void the_api_needs_a_valid_token() throws Exception {
        mvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    @Test
    void me_reflects_the_database_not_the_token() throws Exception {
        call(manager, get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("manager@example.com"))
                .andExpect(jsonPath("$.managerName").value(nullValue()));
    }

    @Test
    void a_token_of_a_deleted_user_stops_working() throws Exception {
        String token = tokenOf(otherManager);
        users.delete(otherManager);

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reports_are_for_finance_only() throws Exception {
        call(employee, get("/api/reports/summary?from=2026-01-01&to=2026-12-31")).andExpect(status().isForbidden());
        call(manager, get("/api/reports/summary?from=2026-01-01&to=2026-12-31")).andExpect(status().isForbidden());
        call(finance, get("/api/reports/summary?from=2026-01-01&to=2026-12-31")).andExpect(status().isOk());
    }

    @Test
    void health_is_public() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
