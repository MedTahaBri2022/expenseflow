package io.github.medtahabri.expenseflow.claim;

public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    /** Approved by the manager, waiting for the finance team (total above the threshold). */
    PENDING_FINANCE,
    APPROVED,
    REJECTED,
    PAID
}
