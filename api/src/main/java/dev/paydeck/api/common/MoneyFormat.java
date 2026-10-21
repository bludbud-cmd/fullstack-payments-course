package dev.paydeck.api.common;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

import dev.paydeck.api.money.SupportedCurrency;

/** Writes amounts for people to read, such as "$12.50", in notifications. */
public final class MoneyFormat {

    private MoneyFormat() {
    }

    public static String format(long cents, SupportedCurrency currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        format.setCurrency(Currency.getInstance(currency.name()));
        return format.format(BigDecimal.valueOf(cents, 2));
    }
}
