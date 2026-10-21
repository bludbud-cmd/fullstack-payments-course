package dev.paydeck.api.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes down security-relevant actions. Never put passwords, tokens or card
 * numbers in {@code detail}: the log is read by people.
 */
@Service
public class AuditLog {

    private final AuditEventRepository events;

    public AuditLog(AuditEventRepository events) {
        this.events = events;
    }

    /** Records an event as part of the current change: if the change is rolled back, so is the event. */
    @Transactional
    public void record(UUID actorUserId, String action, String targetType, UUID targetId, String detail) {
        events.save(new AuditEvent(actorUserId, action, targetType, targetId, detail));
    }

    /**
     * Records an event in its own transaction, so it is kept even if the
     * request fails (a failed login) or only reads data (staff looking at an
     * account).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordNow(UUID actorUserId, String action, String targetType, UUID targetId, String detail) {
        events.save(new AuditEvent(actorUserId, action, targetType, targetId, detail));
    }
}
