package dev.paydeck.api.live;

import java.time.Instant;
import java.util.UUID;

import dev.paydeck.api.live.AccountEvent.Kind;

/** What subscribers to /topic/accounts/{id} and /topic/users/{id} receive, as JSON. */
public record LiveMessage(UUID accountId, Kind kind, String title, String body, UUID cardId, long balanceCents,
                          Instant at) {
}
