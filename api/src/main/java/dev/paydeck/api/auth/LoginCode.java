package dev.paydeck.api.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A one-time code the API gives the app after "Sign in with Google". It is
 * only worth anything together with the PKCE code verifier the app kept.
 */
@Entity
@Table(name = "login_codes")
public class LoginCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private String codeChallenge;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected LoginCode() {
        // For JPA
    }

    public LoginCode(UUID userId, String codeHash, String codeChallenge, Instant expiresAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.codeChallenge = codeChallenge;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isUsable() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void markUsed() {
        usedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeChallenge() {
        return codeChallenge;
    }
}
