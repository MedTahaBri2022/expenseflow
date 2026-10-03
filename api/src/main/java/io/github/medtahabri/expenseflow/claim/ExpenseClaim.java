package io.github.medtahabri.expenseflow.claim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.medtahabri.expenseflow.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "expense_claim")
public class ExpenseClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(2);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("spentOn ASC, id ASC")
    private List<ExpenseItem> items = new ArrayList<>();

    protected ExpenseClaim() {
    }

    public ExpenseClaim(User owner, String title, Instant createdAt) {
        this.owner = owner;
        this.title = title;
        this.createdAt = createdAt;
    }

    public void rename(String title) {
        this.title = title;
    }

    /** Replaces every item; the total always matches the items. */
    public void replaceItems(List<NewItem> newItems) {
        items.clear();
        for (NewItem item : newItems) {
            items.add(new ExpenseItem(this, item.spentOn(), item.category(),
                    item.amount().setScale(2, RoundingMode.UNNECESSARY), item.description()));
        }
        total = items.stream().map(ExpenseItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    void moveTo(ClaimStatus next, Instant at) {
        if (next == ClaimStatus.SUBMITTED) {
            submittedAt = at;
        }
        status = next;
    }

    public record NewItem(LocalDate spentOn, Category category, BigDecimal amount, String description) {
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getTitle() {
        return title;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<ExpenseItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
