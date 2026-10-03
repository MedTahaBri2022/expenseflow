package io.github.medtahabri.expenseflow.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.medtahabri.expenseflow.IntegrationTest;
import org.junit.jupiter.api.Test;

/** The approval workflow over HTTP, with the audit trail it leaves. */
class ClaimWorkflowIT extends IntegrationTest {

    @Test
    void a_small_claim_goes_from_draft_to_paid() throws Exception {
        long id = createClaim(employee, claimJson("Client visit", item("TRAVEL", "320.00"), item("MEALS", "180.50")));

        call(employee, get("/api/claims/" + id))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(500.50))
                .andExpect(jsonPath("$.currency").value("MAD"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.allowedActions", containsInAnyOrder("SUBMIT")));

        act(employee, id, "submit", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        act(manager, id, "approve", "ok").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        act(finance, id, "pay", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.allowedActions", hasSize(0)));

        call(employee, get("/api/claims/" + id + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].toStatus").value(org.hamcrest.Matchers.contains(
                        "DRAFT", "SUBMITTED", "APPROVED", "PAID")))
                .andExpect(jsonPath("$[2].actor.fullName").value("Karim Manager"))
                .andExpect(jsonPath("$[2].comment").value("ok"));
    }

    @Test
    void above_the_threshold_finance_approves_after_the_manager() throws Exception {
        long id = createClaim(employee, claimJson("Conference",
                item("TRAVEL", "4800.00"), item("TRAVEL", "4200.00"), item("LODGING", "1950.00")));

        act(employee, id, "submit", null).andExpect(status().isOk());
        act(manager, id, "approve", null).andExpect(jsonPath("$.status").value("PENDING_FINANCE"));
        act(manager, id, "approve", null).andExpect(status().isConflict());
        act(finance, id, "approve", null).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void nobody_approves_their_own_claim() throws Exception {
        long id = createClaim(manager, claimJson("Manager trip", item("TRAVEL", "900.00")));
        act(manager, id, "submit", null).andExpect(status().isOk());

        // The manager has no manager: finance gives the approval, never the owner.
        act(manager, id, "approve", null).andExpect(status().isConflict());
        act(finance, id, "approve", null).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void a_rejection_needs_a_reason_and_the_owner_can_reopen() throws Exception {
        long id = createClaim(employee, claimJson("Dinner", item("MEALS", "480.00")));
        act(employee, id, "submit", null);

        act(manager, id, "reject", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.comment").exists());
        act(manager, id, "reject", "Paid by the HR budget").andExpect(jsonPath("$.status").value("REJECTED"));
        act(employee, id, "reopen", null).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void an_empty_claim_cannot_be_submitted() throws Exception {
        long id = createClaim(employee, claimJson("Nothing yet"));

        act(employee, id, "submit", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    void a_submitted_claim_can_no_longer_be_edited() throws Exception {
        long id = createClaim(employee, claimJson("Taxi", item("TRAVEL", "120.00")));
        act(employee, id, "submit", null);
        long version = versionOf(employee, id);

        callJson(employee, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/claims/" + id),
                "{\"version\":" + version + ",\"title\":\"Taxi\",\"items\":[" + item("TRAVEL", "4000.00") + "]}")
                .andExpect(status().isForbidden());
    }

    @Test
    void a_stale_version_is_refused() throws Exception {
        long id = createClaim(employee, claimJson("Taxi", item("TRAVEL", "120.00")));
        long readVersion = versionOf(employee, id);
        callJson(employee, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/claims/" + id),
                "{\"version\":" + readVersion + ",\"title\":\"Taxi and train\",\"items\":["
                        + item("TRAVEL", "120.00") + "," + item("TRAVEL", "90.00") + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(210.00));

        // Submitting with the version read before the edit: the owner has not seen the change.
        callJson(employee, post("/api/claims/" + id + "/submit"), action(readVersion, null))
                .andExpect(status().isConflict());
    }

    @Test
    void two_simultaneous_decisions_cannot_both_win() throws Exception {
        long id = createClaim(employee, claimJson("Hotel", item("LODGING", "800.00")));
        act(employee, id, "submit", null);
        long version = versionOf(employee, id);
        String managerToken = tokenOf(manager);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> decisions = List.of(
                () -> decide(start, managerToken, id, "approve", version, null),
                () -> decide(start, managerToken, id, "reject", version, "duplicate"));
        List<Future<Integer>> results = new ArrayList<>();
        for (Callable<Integer> decision : decisions) {
            results.add(pool.submit(decision));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        Integer decisionsRecorded = jdbc.queryForObject(
                "SELECT count(*) FROM claim_event WHERE claim_id = ? AND from_status = 'SUBMITTED'", Integer.class, id);
        assertThat(decisionsRecorded).isEqualTo(1);
    }

    private int decide(CountDownLatch start, String token, long id, String actionName, long version, String comment)
            throws Exception {
        start.await();
        return mvc.perform(post("/api/claims/" + id + "/" + actionName)
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(action(version, comment)))
                .andReturn().getResponse().getStatus();
    }
}
