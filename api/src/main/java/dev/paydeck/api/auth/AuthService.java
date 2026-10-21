package dev.paydeck.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.paydeck.api.audit.AuditLog;
import dev.paydeck.api.common.ConflictException;
import dev.paydeck.api.common.InvalidCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Signing up, signing in and signing out. */
@Service
public class AuthService {

    static final Duration LOGIN_CODE_LIFETIME = Duration.ofMinutes(2);

    private final UserRepository users;
    private final LoginCodeRepository loginCodes;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AuditLog audit;
    private final AuthProperties properties;
    /**
     * Checked against when the email is unknown, so a wrong email takes as
     * long to answer as a wrong password and the timing gives nothing away.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, LoginCodeRepository loginCodes, PasswordEncoder passwordEncoder,
                       TokenService tokens, AuditLog audit, AuthProperties properties) {
        this.users = users;
        this.loginCodes = loginCodes;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.audit = audit;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public record SignedIn(User user, TokenService.Tokens tokens) {
    }

    @Transactional
    public SignedIn register(String email, String password, String displayName) {
        String normalized = User.normalizeEmail(email);
        if (users.findByEmail(normalized).isPresent()) {
            throw emailTaken();
        }
        User user;
        try {
            user = users.saveAndFlush(User.withPassword(normalized, displayName,
                    passwordEncoder.encode(password), roleFor(normalized)));
        } catch (DataIntegrityViolationException raceWithAnotherSignUp) {
            throw emailTaken();
        }
        audit.record(user.getId(), "USER_REGISTERED", "USER", user.getId(), null);
        return new SignedIn(user, tokens.issue(user));
    }

    @Transactional
    public SignedIn login(String email, String password) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        String hash = found.map(User::getPasswordHash).orElse(null);
        boolean matches = passwordEncoder.matches(password, hash != null ? hash : dummyHash);
        if (found.isEmpty() || hash == null || !matches) {
            audit.recordNow(found.map(User::getId).orElse(null), "LOGIN_FAILED", "USER",
                    found.map(User::getId).orElse(null), null);
            throw new InvalidCredentialsException("That email and password do not match.");
        }
        User user = found.get();
        audit.record(user.getId(), "LOGIN", "USER", user.getId(), "password");
        return new SignedIn(user, tokens.issue(user));
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public SignedIn refresh(String refreshToken) {
        UUID userId = tokens.redeem(refreshToken);
        User user = users.findById(userId).orElseThrow();
        return new SignedIn(user, tokens.issue(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        tokens.revoke(refreshToken);
    }

    /**
     * Called after Google confirms who someone is. Finds them by their Google
     * ID, or by email if Google has verified that the email is theirs (this
     * links Google to an existing password account), or signs them up.
     */
    @Transactional
    public User signInWithGoogle(String googleSubject, String email, boolean emailVerified, String name) {
        Optional<User> bySubject = users.findByGoogleSubject(googleSubject);
        if (bySubject.isPresent()) {
            return bySubject.get();
        }
        if (!emailVerified) {
            throw new InvalidCredentialsException("Google has not verified this email address.");
        }
        String normalized = User.normalizeEmail(email);
        User user = users.findByEmail(normalized)
                .map(existing -> {
                    existing.linkGoogle(googleSubject);
                    audit.record(existing.getId(), "GOOGLE_LINKED", "USER", existing.getId(), null);
                    return existing;
                })
                .orElseGet(() -> {
                    User created = users.save(User.withGoogle(normalized,
                            name == null || name.isBlank() ? normalized : name, googleSubject, roleFor(normalized)));
                    audit.record(created.getId(), "USER_REGISTERED", "USER", created.getId(), "google");
                    return created;
                });
        audit.record(user.getId(), "LOGIN", "USER", user.getId(), "google");
        return user;
    }

    /** A short-lived, single-use code the app trades for tokens with {@link #redeemLoginCode}. */
    @Transactional
    public String createLoginCode(UUID userId, String codeChallenge) {
        String code = Secrets.randomToken();
        loginCodes.save(new LoginCode(userId, Secrets.sha256Hex(code), codeChallenge,
                Instant.now().plus(LOGIN_CODE_LIFETIME)));
        return code;
    }

    /**
     * Only the app that started the sign-in knows the code verifier, so a code
     * stolen on its way back to the app is useless to anyone else.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public SignedIn redeemLoginCode(String code, String codeVerifier) {
        LoginCode stored = loginCodes.findByCodeHash(Secrets.sha256Hex(code))
                .filter(LoginCode::isUsable)
                .orElseThrow(() -> new InvalidCredentialsException("This sign-in link has expired. Please try again."));
        stored.markUsed();
        if (!Secrets.constantTimeEquals(Secrets.pkceChallenge(codeVerifier), stored.getCodeChallenge())) {
            throw new InvalidCredentialsException("This sign-in link has expired. Please try again.");
        }
        User user = users.findById(stored.getUserId()).orElseThrow();
        return new SignedIn(user, tokens.issue(user));
    }

    private Role roleFor(String normalizedEmail) {
        return properties.adminEmails().contains(normalizedEmail) ? Role.ADMIN : Role.CUSTOMER;
    }

    private static ConflictException emailTaken() {
        return new ConflictException("EMAIL_TAKEN", "An account with this email already exists. Try signing in.");
    }
}
