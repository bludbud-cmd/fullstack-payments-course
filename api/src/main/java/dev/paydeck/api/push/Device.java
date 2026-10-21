package dev.paydeck.api.push;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A phone that has asked for push notifications, and the token Firebase gave it. */
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String token;

    @Column(nullable = false)
    private Instant createdAt;

    protected Device() {
        // For JPA
    }

    public Device(UUID userId, String platform, String token) {
        this.userId = userId;
        this.platform = platform;
        this.token = token;
        this.createdAt = Instant.now();
    }

    /** The same phone signed in as someone else: notifications now go to them. */
    public void moveTo(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }
}
