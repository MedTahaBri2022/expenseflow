package io.github.medtahabri.expenseflow.user;

public enum Role {
    /** Creates and submits their own claims. */
    EMPLOYEE,
    /** Also approves the claims of the people they manage. */
    MANAGER,
    /** Sees every claim, gives the second approval above the threshold, pays. */
    FINANCE
}
