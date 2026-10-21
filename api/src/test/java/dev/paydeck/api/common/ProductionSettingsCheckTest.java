package dev.paydeck.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ProductionSettingsCheckTest {

    private static final Map<String, String> GOOD = Map.of(
            "JWT_SECRET", "q8Zt3nY1kVb0pLr7sXw2mCf9hJd4gTa6eUo5iRy",
            "DATABASE_URL", "jdbc:postgresql://db.internal:5432/paydeck",
            "PAYDECK_PUBLIC_BASE_URL", "https://api.paydeck.example");

    @Test
    void acceptsCompleteSettings() {
        assertThat(ProductionSettingsCheck.problems(GOOD::get)).isEmpty();
    }

    @Test
    void listsEverythingThatIsMissing() {
        assertThat(ProductionSettingsCheck.problems(name -> null)).hasSize(3);
    }

    @Test
    void refusesAShortSecretAndAPlainHttpAddress() {
        Map<String, String> settings = Map.of(
                "JWT_SECRET", "too-short",
                "DATABASE_URL", GOOD.get("DATABASE_URL"),
                "PAYDECK_PUBLIC_BASE_URL", "http://api.paydeck.example");
        assertThat(ProductionSettingsCheck.problems(settings::get))
                .hasSize(2)
                .anySatisfy(problem -> assertThat(problem).contains("JWT_SECRET"))
                .anySatisfy(problem -> assertThat(problem).contains("https://"));
    }

    @Test
    void wantsAJdbcAddressNotTheHostsPostgresLink() {
        Map<String, String> settings = new java.util.HashMap<>(GOOD);
        settings.put("DATABASE_URL", "postgres://user:pass@db.internal:5432/paydeck");
        assertThat(ProductionSettingsCheck.problems(settings::get)).singleElement()
                .asString().contains("jdbc:postgresql://");
    }
}
