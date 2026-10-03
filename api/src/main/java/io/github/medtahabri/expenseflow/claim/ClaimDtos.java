package io.github.medtahabri.expenseflow.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the claim endpoints. */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record ItemRequest(
            @NotNull @PastOrPresent LocalDate spentOn,
            @NotNull Category category,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 200) String description) {
    }

    public record CreateClaimRequest(
            @NotBlank @Size(max = 120) String title,
            @NotNull @Size(max = 50) List<@Valid @NotNull ItemRequest> items) {
    }

    /** Replaces title and items of a draft. {@code version} is the one the client last read. */
    public record UpdateClaimRequest(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 120) String title,
            @NotNull @Size(max = 50) List<@Valid @NotNull ItemRequest> items) {
    }

    public record ActionRequest(
            @NotNull @PositiveOrZero Long version,
            @Size(max = 500) String comment) {
    }

    public record PersonView(Long id, String fullName, String email) {
    }

    public record ItemView(Long id, LocalDate spentOn, Category category, BigDecimal amount, String description) {
    }

    public record ClaimSummary(Long id, String title, PersonView owner, ClaimStatus status, BigDecimal total,
                               Instant createdAt, Instant submittedAt) {
    }

    public record ClaimDetail(Long id, String title, PersonView owner, ClaimStatus status, BigDecimal total,
                              String currency, Instant createdAt, Instant submittedAt, long version,
                              List<ItemView> items, Set<ClaimAction> allowedActions, boolean editable) {
    }

    public record EventView(Long id, PersonView actor, ClaimStatus fromStatus, ClaimStatus toStatus,
                            String comment, Instant occurredAt) {
    }
}
