package io.github.medtahabri.expenseflow.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings ({@code expenseflow.*} in application.yml). Validated at
 * startup: a missing or too short JWT secret stops the application instead of
 * producing weak tokens.
 */
@Validated
@ConfigurationProperties("expenseflow")
public record ExpenseFlowProperties(
        @NotNull @Size(min = 32, message = "must be at least 32 characters") String jwtSecret,
        @NotNull Duration tokenTtl,
        @NotNull @Positive BigDecimal financeApprovalThreshold,
        boolean demoData,
        List<String> corsOrigins) {
}
