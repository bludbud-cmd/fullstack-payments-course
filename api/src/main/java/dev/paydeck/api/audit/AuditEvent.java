package dev.paydeck.api.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One line in the audit log. Events are only ever added, never changed. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Who did it, or {@code null} when nobody was signed in (for example, a failed login). */
    private UUID actorUserId;

    @Column(nullable = false)
    private String action;

    private String targetType;

    private UUID targetId;

    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
        // For JPA
    }

    AuditEvent(UUID actorUserId, String action, String targetType, UUID targetId, String detail) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
