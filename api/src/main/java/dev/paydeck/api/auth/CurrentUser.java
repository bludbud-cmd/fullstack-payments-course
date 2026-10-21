package dev.paydeck.api.auth;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is making this request, read from their access token. Controllers ask
 * for it as a parameter; see {@link CurrentUserArgumentResolver}.
 */
public record CurrentUser(UUID id, Role role) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")));
    }

    public boolean isStaff() {
        return role == Role.SUPPORT || role == Role.ADMIN;
    }
}
