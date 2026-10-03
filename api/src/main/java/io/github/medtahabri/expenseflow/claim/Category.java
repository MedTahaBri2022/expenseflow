package io.github.medtahabri.expenseflow.claim;

import java.math.BigDecimal;

/** Expense categories with the company's limit per item, in MAD. */
public enum Category {
    MEALS("500.00"),
    TRAVEL("5000.00"),
    LODGING("2000.00"),
    SUPPLIES("1500.00"),
    OTHER("1000.00");

    private final BigDecimal limitPerItem;

    Category(String limitPerItem) {
        this.limitPerItem = new BigDecimal(limitPerItem);
    }

    public BigDecimal limitPerItem() {
        return limitPerItem;
    }

    public boolean allows(BigDecimal amount) {
        return amount.compareTo(limitPerItem) <= 0;
    }
}
