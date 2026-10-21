package dev.paydeck.api.live;

import java.util.UUID;

/**
 * Something happened to an account that people should hear about straight
 * away. Services publish these inside their database transaction;
 * {@link LiveUpdates} sends them out only once the transaction has committed,
 * so nobody is told about money that then failed to move.
 */
public record AccountEvent(UUID accountId, Kind kind, String title, String body, UUID cardId) {

    public enum Kind {
        MONEY_IN,
        MONEY_OUT,
        CARD_PAYMENT,
        CARD_DECLINED,
        REFUND,
        CARD_UPDATED;

        /** Declines get their own on/off switch in notification settings. */
        public boolean isDecline() {
            return this == CARD_DECLINED;
        }
    }

    public static AccountEvent of(UUID accountId, Kind kind, String title, String body) {
        return new AccountEvent(accountId, kind, title, body, null);
    }
}
