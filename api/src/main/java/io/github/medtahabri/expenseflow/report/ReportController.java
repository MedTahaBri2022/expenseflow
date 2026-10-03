package io.github.medtahabri.expenseflow.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Figures for the finance team (role FINANCE, enforced in SecurityConfig).
 * Aggregations run in PostgreSQL, not in Java.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final JdbcClient jdbc;

    public ReportController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Claims submitted between {@code from} and {@code to} (inclusive). */
    @GetMapping("/summary")
    public Summary summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from) || from.plusYears(2).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The period must be between 1 day and 2 years");
        }
        var start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        var end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<StatusTotal> byStatus = jdbc.sql("""
                        SELECT status, count(*) AS claims, coalesce(sum(total), 0) AS amount
                          FROM expense_claim
                         WHERE submitted_at >= :start AND submitted_at < :end
                         GROUP BY status ORDER BY status""")
                .param("start", start).param("end", end)
                .query((rs, n) -> new StatusTotal(rs.getString("status"), rs.getLong("claims"),
                        rs.getBigDecimal("amount")))
                .list();

        // Money actually committed: approved and paid claims only.
        List<CategoryTotal> byCategory = jdbc.sql("""
                        SELECT i.category, count(*) AS items, sum(i.amount) AS amount
                          FROM expense_item i
                          JOIN expense_claim c ON c.id = i.claim_id
                         WHERE c.status IN ('APPROVED', 'PAID')
                           AND c.submitted_at >= :start AND c.submitted_at < :end
                         GROUP BY i.category ORDER BY amount DESC""")
                .param("start", start).param("end", end)
                .query((rs, n) -> new CategoryTotal(rs.getString("category"), rs.getLong("items"),
                        rs.getBigDecimal("amount")))
                .list();

        List<MonthTotal> byMonth = jdbc.sql("""
                        SELECT to_char(submitted_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month,
                               count(*) AS claims, sum(total) AS amount
                          FROM expense_claim
                         WHERE status IN ('APPROVED', 'PAID')
                           AND submitted_at >= :start AND submitted_at < :end
                         GROUP BY month ORDER BY month""")
                .param("start", start).param("end", end)
                .query((rs, n) -> new MonthTotal(rs.getString("month"), rs.getLong("claims"),
                        rs.getBigDecimal("amount")))
                .list();

        return new Summary(from, to, "MAD", byStatus, byCategory, byMonth);
    }

    public record StatusTotal(String status, long claims, BigDecimal amount) {
    }

    public record CategoryTotal(String category, long items, BigDecimal amount) {
    }

    public record MonthTotal(String month, long claims, BigDecimal amount) {
    }

    public record Summary(LocalDate from, LocalDate to, String currency, List<StatusTotal> byStatus,
                          List<CategoryTotal> byCategory, List<MonthTotal> approvedByMonth) {
    }
}
