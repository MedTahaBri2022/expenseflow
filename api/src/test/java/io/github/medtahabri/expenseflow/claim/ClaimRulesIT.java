package io.github.medtahabri.expenseflow.claim;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import io.github.medtahabri.expenseflow.IntegrationTest;
import org.junit.jupiter.api.Test;

/** Validation, company limits, visibility and the list filters. */
class ClaimRulesIT extends IntegrationTest {

    @Test
    void items_are_validated() throws Exception {
        callJson(employee, post("/api/claims"), claimJson("",
                        item("MEALS", "-5"), item("MEALS", "12.345"), item("MEALS", "20", LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors['items[0].amount']").exists())
                .andExpect(jsonPath("$.errors['items[1].amount']").exists())
                .andExpect(jsonPath("$.errors['items[2].spentOn']").exists());
    }

    @Test
    void company_limits_per_category_are_enforced() throws Exception {
        callJson(employee, post("/api/claims"), claimJson("Dinner", item("TRAVEL", "300.00"), item("MEALS", "650.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors['items[1].amount']").value("the limit for MEALS is 500.00 MAD per item"));
    }

    @Test
    void an_unknown_category_is_a_400() throws Exception {
        callJson(employee, post("/api/claims"), claimJson("Gift", item("GIFTS", "10.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_draft_is_private_and_a_submitted_claim_is_seen_by_manager_and_finance() throws Exception {
        long id = createClaim(employee, claimJson("Taxi", item("TRAVEL", "120.00")));

        call(manager, get("/api/claims/" + id)).andExpect(status().isNotFound());
        call(finance, get("/api/claims/" + id)).andExpect(status().isNotFound());

        act(employee, id, "submit", null).andExpect(status().isOk());

        call(manager, get("/api/claims/" + id)).andExpect(status().isOk());
        call(finance, get("/api/claims/" + id)).andExpect(status().isOk());
        call(colleague, get("/api/claims/" + id)).andExpect(status().isNotFound());
        call(otherManager, get("/api/claims/" + id)).andExpect(status().isNotFound());
        call(colleague, get("/api/claims/" + id + "/events")).andExpect(status().isNotFound());
        act(otherManager, id, "approve", null).andExpect(status().isNotFound());
    }

    @Test
    void the_list_shows_what_each_role_may_see() throws Exception {
        long employeeClaim = createClaim(employee, claimJson("Employee claim", item("TRAVEL", "100.00")));
        long colleagueClaim = createClaim(colleague, claimJson("Colleague claim", item("TRAVEL", "100.00")));
        long otherTeamClaim = createClaim(otherManager, claimJson("Other team claim", item("TRAVEL", "100.00")));
        createClaim(employee, claimJson("Employee draft", item("TRAVEL", "100.00")));
        for (long id : new long[] {employeeClaim, colleagueClaim}) {
            act(id == employeeClaim ? employee : colleague, id, "submit", null);
        }
        act(otherManager, otherTeamClaim, "submit", null);

        call(employee, get("/api/claims"))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Employee claim", "Employee draft")));
        call(manager, get("/api/claims"))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Employee claim", "Colleague claim")));
        call(manager, get("/api/claims?mine=true")).andExpect(jsonPath("$.totalElements").value(0));
        call(finance, get("/api/claims"))
                .andExpect(jsonPath("$.content[*].title",
                        containsInAnyOrder("Employee claim", "Colleague claim", "Other team claim")));
    }

    @Test
    void the_list_filters_by_status_and_searches_titles_literally() throws Exception {
        long submitted = createClaim(employee, claimJson("100% client visit", item("TRAVEL", "100.00")));
        createClaim(employee, claimJson("Client_visit draft", item("TRAVEL", "100.00")));
        createClaim(employee, claimJson("Office chair", item("SUPPLIES", "900.00")));
        act(employee, submitted, "submit", null);

        call(employee, get("/api/claims?status=SUBMITTED"))
                .andExpect(jsonPath("$.content[*].title", contains("100% client visit")));
        call(employee, get("/api/claims?q=CLIENT"))
                .andExpect(jsonPath("$.totalElements").value(2));
        // % and _ are searched as characters, not as wildcards.
        call(employee, get("/api/claims").param("q", "%")).andExpect(jsonPath("$.content[*].title", contains("100% client visit")));
        call(employee, get("/api/claims").param("q", "_")).andExpect(jsonPath("$.content[*].title", contains("Client_visit draft")));
    }

    @Test
    void the_list_is_paginated_newest_first() throws Exception {
        for (int i = 1; i <= 3; i++) {
            createClaim(employee, claimJson("Claim " + i, item("TRAVEL", "10.00")));
        }

        call(employee, get("/api/claims?size=2&page=0"))
                .andExpect(jsonPath("$.content[*].title", contains("Claim 3", "Claim 2")))
                .andExpect(jsonPath("$.totalPages").value(2));
        call(employee, get("/api/claims?size=101")).andExpect(status().isBadRequest());
    }

    @Test
    void the_finance_summary_counts_committed_money() throws Exception {
        long paid = createClaim(employee, claimJson("Taxi", item("TRAVEL", "250.00")));
        act(employee, paid, "submit", null);
        act(manager, paid, "approve", null);
        act(finance, paid, "pay", null);
        long waiting = createClaim(colleague, claimJson("Supplies", item("SUPPLIES", "640.00")));
        act(colleague, waiting, "submit", null);

        String today = LocalDate.now().toString();
        call(finance, get("/api/reports/summary?from=" + today + "&to=" + today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus[*].status", containsInAnyOrder("PAID", "SUBMITTED")))
                .andExpect(jsonPath("$.byCategory[*].category", contains("TRAVEL")))
                .andExpect(jsonPath("$.byCategory[0].amount").value(250.00));
    }
}
