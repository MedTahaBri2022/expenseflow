package io.github.medtahabri.expenseflow.claim;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClaimRepository extends JpaRepository<ExpenseClaim, Long>, JpaSpecificationExecutor<ExpenseClaim> {

    /** The list loads each owner in the same query: no N+1. */
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<ExpenseClaim> findAll(Specification<ExpenseClaim> specification, Pageable pageable);
}
