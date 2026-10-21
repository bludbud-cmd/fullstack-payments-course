package dev.paydeck.api.auth;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The {@code paydeck.auth.*} settings from application.yml. */
@ConfigurationProperties("paydeck.auth")
public record AuthProperties(String jwtSecret, List<String> adminEmails, List<String> appRedirectUris) {

    public AuthProperties {
        adminEmails = adminEmails == null ? List.of()
                : adminEmails.stream().filter(e -> !e.isBlank()).map(User::normalizeEmail).toList();
        appRedirectUris = appRedirectUris == null ? List.of() : List.copyOf(appRedirectUris);
    }
}
