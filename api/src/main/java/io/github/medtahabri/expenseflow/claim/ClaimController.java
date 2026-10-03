package io.github.medtahabri.expenseflow.claim;

import java.net.URI;
import java.util.List;

import io.github.medtahabri.expenseflow.claim.ClaimDtos.ActionRequest;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ClaimDetail;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ClaimSummary;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.CreateClaimRequest;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.EventView;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.UpdateClaimRequest;
import io.github.medtahabri.expenseflow.common.PageResponse;
import io.github.medtahabri.expenseflow.security.CurrentUser;
import io.github.medtahabri.expenseflow.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService service;
    private final CurrentUser currentUser;

    public ClaimController(ClaimService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<ClaimSummary> list(@RequestParam(required = false) ClaimStatus status,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "false") boolean mine,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return service.list(currentUser.get(), status, q, mine, pageable);
    }

    @PostMapping
    public ResponseEntity<ClaimDetail> create(@Valid @RequestBody CreateClaimRequest request) {
        ClaimDetail created = service.create(currentUser.get(), request);
        return ResponseEntity.created(URI.create("/api/claims/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ClaimDetail get(@PathVariable Long id) {
        return service.get(currentUser.get(), id);
    }

    @PutMapping("/{id}")
    public ClaimDetail update(@PathVariable Long id, @Valid @RequestBody UpdateClaimRequest request) {
        return service.update(currentUser.get(), id, request);
    }

    /** One endpoint per transition: submit, approve, reject, reopen, pay. */
    @PostMapping("/{id}/{action:submit|approve|reject|reopen|pay}")
    public ClaimDetail act(@PathVariable Long id, @PathVariable String action,
                           @Valid @RequestBody ActionRequest request) {
        User user = currentUser.get();
        return service.act(user, id, ClaimAction.valueOf(action.toUpperCase()), request);
    }

    @GetMapping("/{id}/events")
    public List<EventView> history(@PathVariable Long id) {
        return service.history(currentUser.get(), id);
    }
}
