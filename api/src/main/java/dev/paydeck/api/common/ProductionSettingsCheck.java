package dev.paydeck.api.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuses to start a deployed API that still has the settings meant for your
 * own Mac. Runs only with {@code SPRING_PROFILES_ACTIVE=production}: a clear
 * message at start-up beats a guessable signing key later.
 *
 * <p>It listens for the moment the settings are read, before anything else
 * starts (even the database connection), so a missing setting is reported
 * first. {@link dev.paydeck.api.PayDeckApplication#main} registers it.
 */
public class ProductionSettingsCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (!environment.matchesProfiles("production")) {
            return;
        }
        List<String> problems = problems(environment::getProperty);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("PayDeck is not ready for production:\n  - "
                    + String.join("\n  - ", problems));
        }
    }

    /** Each problem as a sentence. {@code settings} reads an environment variable. */
    public static List<String> problems(Function<String, String> settings) {
        List<String> problems = new ArrayList<>();
        String secret = settings.apply("JWT_SECRET");
        if (secret == null || secret.length() < 32) {
            problems.add("Set JWT_SECRET to a long random value, for example from: openssl rand -base64 48");
        }
        String database = settings.apply("DATABASE_URL");
        if (database == null || !database.startsWith("jdbc:postgresql://")) {
            problems.add("Set DATABASE_URL to jdbc:postgresql://HOST:5432/DATABASE (and DATABASE_USERNAME, DATABASE_PASSWORD)");
        }
        String publicUrl = settings.apply("PAYDECK_PUBLIC_BASE_URL");
        if (publicUrl == null || !publicUrl.startsWith("https://")) {
            problems.add("Set PAYDECK_PUBLIC_BASE_URL to the API's own https:// address");
        }
        return problems;
    }
}
