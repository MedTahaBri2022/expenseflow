package io.github.medtahabri.expenseflow.claim;

import java.time.Instant;

import io.github.medtahabri.expenseflow.user.User;
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
import org.hibernate.annotations.Immutable;

/** One change of status of a claim: who, when, from what to what, and why. */
@Entity
@Immutable
@Table(name = "claim_event")
public class ClaimEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private ExpenseClaim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ClaimStatus toStatus;

    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ClaimEvent() {
    }

    public ClaimEvent(ExpenseClaim claim, User actor, ClaimStatus fromStatus, ClaimStatus toStatus,
                      String comment, Instant occurredAt) {
        this.claim = claim;
        this.actor = actor;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public User getActor() {
        return actor;
    }

    public ClaimStatus getFromStatus() {
        return fromStatus;
    }

    public ClaimStatus getToStatus() {
        return toStatus;
    }

    public String getComment() {
        return comment;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
