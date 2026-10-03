package io.github.medtahabri.expenseflow.claim;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.medtahabri.expenseflow.claim.ClaimDtos.ActionRequest;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ClaimDetail;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ClaimSummary;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.CreateClaimRequest;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.EventView;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ItemRequest;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ItemView;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.PersonView;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.UpdateClaimRequest;
import io.github.medtahabri.expenseflow.common.ApiException;
import io.github.medtahabri.expenseflow.common.PageResponse;
import io.github.medtahabri.expenseflow.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimService {

    public static final String CURRENCY = "MAD";

    private final ClaimRepository claims;
    private final ClaimEventRepository events;
    private final ClaimWorkflow workflow;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimEventRepository events, ClaimWorkflow workflow, Clock clock) {
        this.claims = claims;
        this.events = events;
        this.workflow = workflow;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummary> list(User user, ClaimStatus status, String search, boolean mine, Pageable page) {
        Specification<ExpenseClaim> filter = ClaimSpecifications.visibleTo(user)
                .and(ClaimSpecifications.mineOnly(user, mine))
                .and(ClaimSpecifications.hasStatus(status))
                .and(ClaimSpecifications.titleContains(search));
        return PageResponse.of(claims.findAll(filter, page), ClaimService::summary);
    }

    @Transactional
    public ClaimDetail create(User owner, CreateClaimRequest request) {
        Instant now = clock.instant();
        ExpenseClaim claim = new ExpenseClaim(owner, request.title().trim(), now);
        claim.replaceItems(validItems(request.items()));
        claims.save(claim);
        events.save(new ClaimEvent(claim, owner, null, ClaimStatus.DRAFT, null, now));
        return detail(claim, owner);
    }

    @Transactional(readOnly = true)
    public ClaimDetail get(User user, Long id) {
        return detail(visibleClaim(user, id), user);
    }

    @Transactional
    public ClaimDetail update(User user, Long id, UpdateClaimRequest request) {
        ExpenseClaim claim = visibleClaim(user, id);
        if (!workflow.canEdit(claim, user)) {
            throw ApiException.forbidden("Only the owner can change a claim, and only while it is a draft");
        }
        checkVersion(claim, request.version());
        claim.rename(request.title().trim());
        claim.replaceItems(validItems(request.items()));
        claims.flush(); // version conflicts surface here, inside the request
        return detail(claim, user);
    }

    @Transactional
    public ClaimDetail act(User user, Long id, ClaimAction action, ActionRequest request) {
        ExpenseClaim claim = visibleClaim(user, id);
        if (!workflow.allowedActions(claim, user).contains(action)) {
            throw ApiException.conflict("Action " + action + " is not possible on a " + claim.getStatus()
                    + " claim for you");
        }
        checkVersion(claim, request.version());
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        if (action == ClaimAction.SUBMIT && claim.getItems().isEmpty()) {
            throw ApiException.unprocessable("A claim needs at least one item to be submitted",
                    Map.of("items", "add at least one item"));
        }
        if (action == ClaimAction.REJECT && comment == null) {
            throw ApiException.unprocessable("A rejection needs a reason",
                    Map.of("comment", "explain why the claim is rejected"));
        }

        ClaimStatus from = claim.getStatus();
        ClaimStatus to = workflow.next(claim, action, user);
        Instant now = clock.instant();
        claim.moveTo(to, now);
        events.save(new ClaimEvent(claim, user, from, to, comment, now));
        claims.flush();
        return detail(claim, user);
    }

    @Transactional(readOnly = true)
    public List<EventView> history(User user, Long id) {
        visibleClaim(user, id);
        return events.findByClaimIdOrderByOccurredAtAscIdAsc(id).stream()
                .map(e -> new EventView(e.getId(), person(e.getActor()), e.getFromStatus(), e.getToStatus(),
                        e.getComment(), e.getOccurredAt()))
                .toList();
    }

    /** A claim the user may not see is reported as missing, not as forbidden: no existence leak. */
    private ExpenseClaim visibleClaim(User user, Long id) {
        ExpenseClaim claim = claims.findById(id).orElseThrow(() -> ApiException.notFound("Claim " + id));
        if (!workflow.canView(claim, user)) {
            throw ApiException.notFound("Claim " + id);
        }
        return claim;
    }

    private static void checkVersion(ExpenseClaim claim, Long expected) {
        if (expected != claim.getVersion()) {
            throw ApiException.conflict("The claim has changed since you loaded it (version "
                    + claim.getVersion() + ", yours " + expected + "); reload it and try again");
        }
    }

    /** Company rules that Bean Validation cannot express: the limit per item of each category. */
    private static List<ExpenseClaim.NewItem> validItems(List<ItemRequest> items) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            ItemRequest item = items.get(i);
            if (!item.category().allows(item.amount())) {
                errors.put("items[" + i + "].amount", "the limit for " + item.category() + " is "
                        + item.category().limitPerItem() + " " + CURRENCY + " per item");
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.unprocessable("Some items exceed the company limits", errors);
        }
        return items.stream()
                .map(i -> new ExpenseClaim.NewItem(i.spentOn(), i.category(), i.amount(), i.description().trim()))
                .toList();
    }

    private ClaimDetail detail(ExpenseClaim claim, User user) {
        Set<ClaimAction> actions = workflow.allowedActions(claim, user);
        List<ItemView> items = claim.getItems().stream()
                .map(i -> new ItemView(i.getId(), i.getSpentOn(), i.getCategory(), i.getAmount(), i.getDescription()))
                .toList();
        return new ClaimDetail(claim.getId(), claim.getTitle(), person(claim.getOwner()), claim.getStatus(),
                claim.getTotal(), CURRENCY, claim.getCreatedAt(), claim.getSubmittedAt(), claim.getVersion(),
                items, actions, workflow.canEdit(claim, user));
    }

    private static ClaimSummary summary(ExpenseClaim claim) {
        return new ClaimSummary(claim.getId(), claim.getTitle(), person(claim.getOwner()), claim.getStatus(),
                claim.getTotal(), claim.getCreatedAt(), claim.getSubmittedAt());
    }

    static PersonView person(User user) {
        return new PersonView(user.getId(), user.getFullName(), user.getEmail());
    }
}
