package dev.paydeck.api.money;

/**
 * The currencies PayDeck accounts can hold. Amounts are always stored as a
 * whole number of the currency's smallest unit (cents, pence, paise), never
 * as a decimal, so there are no rounding errors.
 */
public enum SupportedCurrency {
    USD, EUR, GBP, INR
}
