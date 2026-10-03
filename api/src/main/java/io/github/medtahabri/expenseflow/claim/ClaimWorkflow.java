package io.github.medtahabri.expenseflow.claim;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import io.github.medtahabri.expenseflow.user.User;

/**
 * The approval rules, in one place and without any framework: who may do what
 * to a claim, and where each action leads.
 *
 * <pre>
 * DRAFT --submit--> SUBMITTED --approve--> APPROVED --pay--> PAID
 *                       |   \--approve, total above threshold--> PENDING_FINANCE --approve--> APPROVED
 *                       \--reject--> REJECTED <--reject-- PENDING_FINANCE
 * REJECTED --reopen--> DRAFT
 * </pre>
 *
 * Nobody acts on their own claim except to submit or reopen it. The first
 * approval belongs to the owner's manager, or to the finance team when the
 * owner has no manager.
 */
public class ClaimWorkflow {

    private final BigDecimal financeApprovalThreshold;

    public ClaimWorkflow(BigDecimal financeApprovalThreshold) {
        this.financeApprovalThreshold = financeApprovalThreshold;
    }

    /** A draft is private to its owner; once submitted, the manager and finance see it too. */
    public boolean canView(ExpenseClaim claim, User user) {
        if (isOwner(claim, user)) {
            return true;
        }
        return claim.getStatus() != ClaimStatus.DRAFT && (user.manages(claim.getOwner()) || user.isFinance());
    }

    public boolean canEdit(ExpenseClaim claim, User user) {
        return isOwner(claim, user) && claim.getStatus() == ClaimStatus.DRAFT;
    }

    public Set<ClaimAction> allowedActions(ExpenseClaim claim, User user) {
        Set<ClaimAction> actions = EnumSet.noneOf(ClaimAction.class);
        boolean owner = isOwner(claim, user);
        switch (claim.getStatus()) {
            case DRAFT -> {
                if (owner) {
                    actions.add(ClaimAction.SUBMIT);
                }
            }
            case SUBMITTED -> {
                if (!owner && isFirstApprover(claim, user)) {
                    actions.add(ClaimAction.APPROVE);
                    actions.add(ClaimAction.REJECT);
                }
            }
            case PENDING_FINANCE -> {
                if (!owner && user.isFinance()) {
                    actions.add(ClaimAction.APPROVE);
                    actions.add(ClaimAction.REJECT);
                }
            }
            case REJECTED -> {
                if (owner) {
                    actions.add(ClaimAction.REOPEN);
                }
            }
            case APPROVED -> {
                if (!owner && user.isFinance()) {
                    actions.add(ClaimAction.PAY);
                }
            }
            case PAID -> {
                // final state
            }
        }
        return actions;
    }

    /** The status an allowed action leads to. */
    public ClaimStatus next(ExpenseClaim claim, ClaimAction action, User actor) {
        return switch (action) {
            case SUBMIT -> ClaimStatus.SUBMITTED;
            case REJECT -> ClaimStatus.REJECTED;
            case REOPEN -> ClaimStatus.DRAFT;
            case PAY -> ClaimStatus.PAID;
            case APPROVE -> needsFinanceApproval(claim, actor) ? ClaimStatus.PENDING_FINANCE : ClaimStatus.APPROVED;
        };
    }

    private boolean needsFinanceApproval(ExpenseClaim claim, User actor) {
        // A finance approver at the first step already is the second pair of eyes.
        return claim.getStatus() == ClaimStatus.SUBMITTED
                && !actor.isFinance()
                && claim.getTotal().compareTo(financeApprovalThreshold) > 0;
    }

    private static boolean isFirstApprover(ExpenseClaim claim, User user) {
        User owner = claim.getOwner();
        return owner.getManager() == null ? user.isFinance() : user.manages(owner);
    }

    private static boolean isOwner(ExpenseClaim claim, User user) {
        return claim.getOwner().getId().equals(user.getId());
    }
}
