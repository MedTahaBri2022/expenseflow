package io.github.medtahabri.expenseflow.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.github.medtahabri.expenseflow.claim.Category;
import io.github.medtahabri.expenseflow.claim.ClaimAction;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ActionRequest;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ClaimDetail;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.CreateClaimRequest;
import io.github.medtahabri.expenseflow.claim.ClaimDtos.ItemRequest;
import io.github.medtahabri.expenseflow.claim.ClaimService;
import io.github.medtahabri.expenseflow.user.Role;
import io.github.medtahabri.expenseflow.user.User;
import io.github.medtahabri.expenseflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Demo users and claims for a local run (DEMO_DATA=true). Runs only on an
 * empty database and goes through the service, so the demo claims follow the
 * same rules and have the same audit trail as real ones.
 */
@Component
@ConditionalOnBooleanProperty("expenseflow.demo-data")
public class DemoData {

    /** Local demo only: every demo account uses this password. */
    public static final String PASSWORD = "password";

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final ClaimService claims;

    public DemoData(UserRepository users, PasswordEncoder passwords, ClaimService claims) {
        this.users = users;
        this.passwords = passwords;
        this.claims = claims;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        if (users.count() > 0) {
            return;
        }
        String hash = passwords.encode(PASSWORD);
        User finance = users.save(new User("fatima.finance@example.com", "Fatima Zahra El Idrissi", hash, Role.FINANCE, null));
        User manager = users.save(new User("karim.manager@example.com", "Karim Benali", hash, Role.MANAGER, null));
        User amina = users.save(new User("amina.employee@example.com", "Amina Tazi", hash, Role.EMPLOYEE, manager));
        User youssef = users.save(new User("youssef.employee@example.com", "Youssef Alaoui", hash, Role.EMPLOYEE, manager));

        LocalDate today = LocalDate.now();
        ClaimDetail draft = claims.create(amina, new CreateClaimRequest("Client visit in Rabat", List.of(
                item(today.minusDays(3), Category.TRAVEL, "320.00", "Train Casablanca - Rabat, return"),
                item(today.minusDays(3), Category.MEALS, "180.00", "Lunch with the client"))));

        ClaimDetail submitted = claims.create(youssef, new CreateClaimRequest("Office supplies", List.of(
                item(today.minusDays(10), Category.SUPPLIES, "640.00", "Keyboard and mouse"),
                item(today.minusDays(9), Category.SUPPLIES, "95.50", "Notebooks"))));
        act(youssef, submitted, ClaimAction.SUBMIT, null);

        ClaimDetail conference = claims.create(amina, new CreateClaimRequest("Tech conference in Marrakech", List.of(
                item(today.minusDays(20), Category.TRAVEL, "4800.00", "Flights"),
                item(today.minusDays(19), Category.LODGING, "1950.00", "Hotel, night 1"),
                item(today.minusDays(18), Category.LODGING, "1950.00", "Hotel, night 2"),
                item(today.minusDays(18), Category.OTHER, "990.00", "Conference ticket"),
                item(today.minusDays(18), Category.MEALS, "450.00", "Dinner"))));
        conference = act(amina, conference, ClaimAction.SUBMIT, null);
        act(manager, conference, ClaimAction.APPROVE, "Agreed with the team lead"); // above 10,000: finance next

        ClaimDetail paid = claims.create(youssef, new CreateClaimRequest("Taxi to the airport", List.of(
                item(today.minusDays(30), Category.TRAVEL, "250.00", "Taxi"))));
        paid = act(youssef, paid, ClaimAction.SUBMIT, null);
        paid = act(manager, paid, ClaimAction.APPROVE, null);
        act(finance, paid, ClaimAction.PAY, "Paid with the monthly payroll");

        ClaimDetail rejected = claims.create(amina, new CreateClaimRequest("Team dinner", List.of(
                item(today.minusDays(12), Category.MEALS, "480.00", "Dinner for 6"))));
        rejected = act(amina, rejected, ClaimAction.SUBMIT, null);
        act(manager, rejected, ClaimAction.REJECT, "Team events are paid by the HR budget");

        log.info("Demo data created: 4 users (password '{}'), 5 claims; draft #{}", PASSWORD, draft.id());
    }

    private ClaimDetail act(User user, ClaimDetail claim, ClaimAction action, String comment) {
        return claims.act(user, claim.id(), action, new ActionRequest(claim.version(), comment));
    }

    private static ItemRequest item(LocalDate day, Category category, String amount, String description) {
        return new ItemRequest(day, category, new BigDecimal(amount), description);
    }
}
