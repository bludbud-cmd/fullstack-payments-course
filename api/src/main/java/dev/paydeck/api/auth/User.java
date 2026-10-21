package dev.paydeck.api.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Someone who can sign in to PayDeck, with a password, with Google, or both. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Always stored in lower case, so "Asha@Example.com" and "asha@example.com" are one person. */
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String displayName;

    /** A BCrypt hash of the password, or {@code null} for Google-only users. Never the password itself. */
    private String passwordHash;

    /** Google's permanent ID for this person, once they have used "Sign in with Google". */
    private String googleSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private Instant createdAt;

    protected User() {
        // For JPA
    }

    public static User withPassword(String email, String displayName, String passwordHash, Role role) {
        User user = new User();
        user.email = normalizeEmail(email);
        user.displayName = displayName;
        user.passwordHash = passwordHash;
        user.role = role;
        user.createdAt = Instant.now();
        return user;
    }

    public static User withGoogle(String email, String displayName, String googleSubject, Role role) {
        User user = new User();
        user.email = normalizeEmail(email);
        user.displayName = displayName;
        user.googleSubject = googleSubject;
        user.role = role;
        user.createdAt = Instant.now();
        return user;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    public void linkGoogle(String googleSubject) {
        this.googleSubject = googleSubject;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
