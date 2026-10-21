package dev.paydeck.api.payments;

/** Why a card payment was turned down, in words the cardholder can act on. */
public enum DeclineReason {
    CARD_FROZEN("The card is frozen."),
    CARD_CANCELLED("The card has been cancelled."),
    OVER_LIMIT("The payment is over the card's spending limit."),
    INSUFFICIENT_FUNDS("There is not enough money in the wallet.");

    private final String message;

    DeclineReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
