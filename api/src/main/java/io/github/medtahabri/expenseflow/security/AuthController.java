package io.github.medtahabri.expenseflow.security;

import java.time.Instant;

import io.github.medtahabri.expenseflow.user.Role;
import io.github.medtahabri.expenseflow.user.User;
import io.github.medtahabri.expenseflow.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final CurrentUser currentUser;
    /** Compared against when the e-mail is unknown, so both failures take the same time. */
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder passwords, TokenService tokens,
                          CurrentUser currentUser) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.dummyHash = passwords.encode("no-such-user");
    }

    @PostMapping("/auth/login")
    @Transactional(readOnly = true)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmail(request.email().trim().toLowerCase()).orElse(null);
        boolean valid = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !valid) {
            // Same answer for an unknown e-mail and a wrong password.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong e-mail or password");
        }
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), me(user));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public Me me() {
        return me(currentUser.get());
    }

    private static Me me(User user) {
        User manager = user.getManager();
        return new Me(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                manager == null ? null : manager.getFullName());
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 128) String password) {
    }

    public record Me(Long id, String fullName, String email, Role role, String managerName) {
    }

    public record LoginResponse(String token, Instant expiresAt, Me user) {
    }
}
