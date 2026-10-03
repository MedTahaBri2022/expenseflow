package io.github.medtahabri.expenseflow.claim;

import io.github.medtahabri.expenseflow.user.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the claim list. Visibility is part of the query, not a post-filter. */
final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    /**
     * Own claims, in any status; plus, once submitted, the team's for a manager
     * and everyone's for finance. Same rule as {@link ClaimWorkflow#canView}.
     */
    static Specification<ExpenseClaim> visibleTo(User user) {
        return (root, query, cb) -> {
            var owner = root.get("owner");
            Predicate own = cb.equal(owner.get("id"), user.getId());
            Predicate submitted = cb.notEqual(root.get("status"), ClaimStatus.DRAFT);
            Predicate others = user.isFinance()
                    ? submitted
                    : cb.and(submitted, cb.equal(owner.get("manager").get("id"), user.getId()));
            return cb.or(own, others);
        };
    }

    static Specification<ExpenseClaim> hasStatus(ClaimStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    /** Case-insensitive search in the title; % and _ typed by a user are literal. */
    static Specification<ExpenseClaim> titleContains(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            String escaped = text.trim().toLowerCase()
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            return cb.like(cb.lower(root.get("title")), "%" + escaped + "%", '!');
        };
    }

    static Specification<ExpenseClaim> mineOnly(User user, boolean mine) {
        return (root, query, cb) -> mine ? cb.equal(root.get("owner").get("id"), user.getId()) : cb.conjunction();
    }
}
