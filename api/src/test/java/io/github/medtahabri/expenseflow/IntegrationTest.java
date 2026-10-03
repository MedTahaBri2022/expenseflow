package io.github.medtahabri.expenseflow;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
import io.github.medtahabri.expenseflow.user.Role;
import io.github.medtahabri.expenseflow.user.User;
import io.github.medtahabri.expenseflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base of the integration tests: the whole application against a real
 * PostgreSQL (Testcontainers), migrations included, called over HTTP with
 * MockMvc and real tokens.
 */
@SpringBootTest
public abstract class IntegrationTest {

    /**
     * One container for the whole test run, shared by every test class: the
     * Spring context is cached between classes, so the database it points to
     * must outlive each class.
     */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String PASSWORD = "correct horse battery";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    protected UserRepository users;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    protected JdbcTemplate jdbc;

    protected MockMvc mvc;
    protected User finance;
    protected User manager;
    protected User employee;
    protected User colleague;
    protected User otherManager;

    @BeforeEach
    void resetDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("TRUNCATE claim_event, expense_item, expense_claim, app_user RESTART IDENTITY CASCADE");
        String hash = passwords.encode(PASSWORD);
        finance = users.save(new User("finance@example.com", "Fatima Finance", hash, Role.FINANCE, null));
        manager = users.save(new User("manager@example.com", "Karim Manager", hash, Role.MANAGER, null));
        employee = users.save(new User("employee@example.com", "Amina Employee", hash, Role.EMPLOYEE, manager));
        colleague = users.save(new User("colleague@example.com", "Youssef Colleague", hash, Role.EMPLOYEE, manager));
        otherManager = users.save(new User("other.manager@example.com", "Other Manager", hash, Role.MANAGER, null));
    }

    protected String tokenOf(User user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected ResultActions call(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + tokenOf(user)));
    }

    protected ResultActions callJson(User user, MockHttpServletRequestBuilder request, String json) throws Exception {
        return call(user, request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected static String item(String category, String amount) {
        return item(category, amount, LocalDate.now().minusDays(2));
    }

    protected static String item(String category, String amount, LocalDate day) {
        return "{\"spentOn\":\"" + day + "\",\"category\":\"" + category + "\",\"amount\":" + amount
                + ",\"description\":\"" + category.toLowerCase() + " expense\"}";
    }

    protected static String claimJson(String title, String... items) {
        return "{\"title\":\"" + title + "\",\"items\":[" + String.join(",", items) + "]}";
    }

    protected static String action(long version, String comment) {
        return comment == null ? "{\"version\":" + version + "}"
                : "{\"version\":" + version + ",\"comment\":\"" + comment + "\"}";
    }

    /** Creates a claim as {@code owner} and returns its id. */
    protected long createClaim(User owner, String json) throws Exception {
        String body = callJson(owner, post("/api/claims"), json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected long versionOf(User user, long claimId) throws Exception {
        String body = call(user, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/claims/" + claimId)).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.version")).longValue();
    }

    /** Runs an action with the current version; returns the response. */
    protected ResultActions act(User user, long claimId, String actionName, String comment) throws Exception {
        // Read in the database: the acting user may not be allowed to see the claim.
        long version = jdbc.queryForObject("SELECT version FROM expense_claim WHERE id = ?", Long.class, claimId);
        return callJson(user, post("/api/claims/" + claimId + "/" + actionName), action(version, comment));
    }
}
