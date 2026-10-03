package io.github.medtahabri.expenseflow.claim;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "expense_item")
public class ExpenseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private ExpenseClaim claim;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    protected ExpenseItem() {
    }

    ExpenseItem(ExpenseClaim claim, LocalDate spentOn, Category category, BigDecimal amount, String description) {
        this.claim = claim;
        this.spentOn = spentOn;
        this.category = category;
        this.amount = amount;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getSpentOn() {
        return spentOn;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }
}
