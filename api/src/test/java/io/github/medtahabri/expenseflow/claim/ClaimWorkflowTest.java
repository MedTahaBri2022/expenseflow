package io.github.medtahabri.expenseflow.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.github.medtahabri.expenseflow.user.Role;
import io.github.medtahabri.expenseflow.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The approval rules, without Spring or a database. */
class ClaimWorkflowTest {

    private final ClaimWorkflow workflow = new ClaimWorkflow(new BigDecimal("10000.00"));

    private final User finance = user(1, Role.FINANCE, null);
    private final User manager = user(2, Role.MANAGER, null);
    private final User employee = user(3, Role.EMPLOYEE, manager);
    private final User colleague = user(4, Role.EMPLOYEE, manager);
    private final User otherManager = user(5, Role.MANAGER, null);

    @Test
    void the_owner_submits_a_draft_and_nobody_else_can() {
        ExpenseClaim claim = claim(employee, "100.00", ClaimStatus.DRAFT);

        assertThat(workflow.allowedActions(claim, employee)).containsExactly(ClaimAction.SUBMIT);
        assertThat(workflow.allowedActions(claim, manager)).isEmpty();
        assertThat(workflow.allowedActions(claim, finance)).isEmpty();
        assertThat(workflow.canEdit(claim, employee)).isTrue();
        assertThat(workflow.canEdit(claim, manager)).isFalse();
    }

    @Test
    void the_manager_of_the_owner_decides_first() {
        ExpenseClaim claim = claim(employee, "100.00", ClaimStatus.SUBMITTED);

        assertThat(workflow.allowedActions(claim, manager)).containsExactlyInAnyOrder(ClaimAction.APPROVE, ClaimAction.REJECT);
        assertThat(workflow.allowedActions(claim, otherManager)).isEmpty();
        assertThat(workflow.allowedActions(claim, finance)).isEmpty();
        assertThat(workflow.allowedActions(claim, employee)).isEmpty();
        assertThat(workflow.canEdit(claim, employee)).isFalse();
    }

    @Test
    void a_claim_up_to_the_threshold_is_approved_in_one_step() {
        ExpenseClaim claim = claim(employee, "10000.00", ClaimStatus.SUBMITTED);

        assertThat(workflow.next(claim, ClaimAction.APPROVE, manager)).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void above_the_threshold_finance_must_approve_too() {
        ExpenseClaim claim = claim(employee, "10000.01", ClaimStatus.SUBMITTED);

        assertThat(workflow.next(claim, ClaimAction.APPROVE, manager)).isEqualTo(ClaimStatus.PENDING_FINANCE);

        ReflectionTestUtils.setField(claim, "status", ClaimStatus.PENDING_FINANCE);
        assertThat(workflow.allowedActions(claim, manager)).isEmpty();
        assertThat(workflow.allowedActions(claim, finance)).containsExactlyInAnyOrder(ClaimAction.APPROVE, ClaimAction.REJECT);
        assertThat(workflow.next(claim, ClaimAction.APPROVE, finance)).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void without_a_manager_finance_approves_and_counts_as_the_second_pair_of_eyes() {
        ExpenseClaim claim = claim(manager, "25000.00", ClaimStatus.SUBMITTED);

        assertThat(workflow.allowedActions(claim, finance)).contains(ClaimAction.APPROVE);
        assertThat(workflow.allowedActions(claim, manager)).isEmpty();
        assertThat(workflow.next(claim, ClaimAction.APPROVE, finance)).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void finance_never_approves_or_pays_its_own_claim() {
        User otherFinance = user(6, Role.FINANCE, null);
        ExpenseClaim submitted = claim(finance, "300.00", ClaimStatus.SUBMITTED);
        ExpenseClaim approved = claim(finance, "300.00", ClaimStatus.APPROVED);

        assertThat(workflow.allowedActions(submitted, finance)).isEmpty();
        assertThat(workflow.allowedActions(submitted, otherFinance)).contains(ClaimAction.APPROVE);
        assertThat(workflow.allowedActions(approved, finance)).isEmpty();
        assertThat(workflow.allowedActions(approved, otherFinance)).containsExactly(ClaimAction.PAY);
    }

    @Test
    void a_rejected_claim_can_only_be_reopened_by_its_owner() {
        ExpenseClaim claim = claim(employee, "100.00", ClaimStatus.REJECTED);

        assertThat(workflow.allowedActions(claim, employee)).containsExactly(ClaimAction.REOPEN);
        assertThat(workflow.allowedActions(claim, manager)).isEmpty();
        assertThat(workflow.next(claim, ClaimAction.REOPEN, employee)).isEqualTo(ClaimStatus.DRAFT);
    }

    @Test
    void a_paid_claim_is_final() {
        ExpenseClaim claim = claim(employee, "100.00", ClaimStatus.PAID);

        for (User user : List.of(employee, manager, finance)) {
            assertThat(workflow.allowedActions(claim, user)).isEmpty();
        }
    }

    @Test
    void visibility_is_owner_manager_and_finance() {
        ExpenseClaim claim = claim(employee, "100.00", ClaimStatus.SUBMITTED);

        assertThat(workflow.canView(claim, employee)).isTrue();
        assertThat(workflow.canView(claim, manager)).isTrue();
        assertThat(workflow.canView(claim, finance)).isTrue();
        assertThat(workflow.canView(claim, colleague)).isFalse();
        assertThat(workflow.canView(claim, otherManager)).isFalse();
    }

    @Test
    void a_draft_is_visible_to_its_owner_only() {
        ExpenseClaim claim = claim(employee, "100.00", ClaimStatus.DRAFT);

        assertThat(workflow.canView(claim, employee)).isTrue();
        assertThat(workflow.canView(claim, manager)).isFalse();
        assertThat(workflow.canView(claim, finance)).isFalse();
    }

    @Test
    void the_total_is_the_sum_of_the_items() {
        ExpenseClaim claim = new ExpenseClaim(employee, "Trip", Instant.now());
        claim.replaceItems(List.of(
                new ExpenseClaim.NewItem(LocalDate.now(), Category.MEALS, new BigDecimal("12.30"), "Lunch"),
                new ExpenseClaim.NewItem(LocalDate.now(), Category.TRAVEL, new BigDecimal("87.70"), "Train")));

        assertThat(claim.getTotal()).isEqualByComparingTo("100.00");
        claim.replaceItems(List.of());
        assertThat(claim.getTotal()).isEqualByComparingTo("0");
    }

    @Test
    void category_limits_are_inclusive() {
        assertThat(Category.MEALS.allows(new BigDecimal("500.00"))).isTrue();
        assertThat(Category.MEALS.allows(new BigDecimal("500.01"))).isFalse();
    }

    private static User user(long id, Role role, User manager) {
        User user = new User("user" + id + "@example.com", "User " + id, "hash", role, manager);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static ExpenseClaim claim(User owner, String amount, ClaimStatus status) {
        ExpenseClaim claim = new ExpenseClaim(owner, "Claim", Instant.now());
        claim.replaceItems(List.of(new ExpenseClaim.NewItem(LocalDate.now(), Category.TRAVEL,
                new BigDecimal(amount), "Item")));
        ReflectionTestUtils.setField(claim, "status", status);
        return claim;
    }
}
