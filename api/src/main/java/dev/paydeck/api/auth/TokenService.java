package dev.paydeck.api.auth;

import java.time.Duration;
import java.time.Instant;

import dev.paydeck.api.common.InvalidCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out the two tokens a signed-in app holds:
 * <ul>
 *   <li>an <b>access token</b>, a JWT the app sends with every request. It
 *   expires after 15 minutes, so a stolen one is not useful for long.</li>
 *   <li>a <b>refresh token</b>, a random string the app trades for a new pair
 *   when the access token expires. Each one works once.</li>
 * </ul>
 */
@Service
public class TokenService {

    public static final String ISSUER = "paydeck";
    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);

    public record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Tokens issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_LIFETIME))
                .claim("role", user.getRole().name())
                .build();
        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        String refreshToken = Secrets.randomToken();
        refreshTokens.save(new RefreshToken(user.getId(), Secrets.sha256Hex(refreshToken),
                now.plus(REFRESH_TOKEN_LIFETIME)));
        return new Tokens(accessToken, refreshToken, ACCESS_TOKEN_LIFETIME.toSeconds());
    }

    /**
     * Uses up a refresh token and returns the user it belongs to. If a token
     * that was already used turns up again, someone may have copied it, so
     * every session of that user is signed out.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = InvalidCredentialsException.class)
    public java.util.UUID redeem(String refreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(Secrets.sha256Hex(refreshToken))
                .orElseThrow(TokenService::expired);
        if (stored.isRevoked()) {
            refreshTokens.revokeAllFor(stored.getUserId());
            throw expired();
        }
        if (stored.isExpired()) {
            throw expired();
        }
        stored.revoke();
        return stored.getUserId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(String refreshToken) {
        refreshTokens.findByTokenHash(Secrets.sha256Hex(refreshToken)).ifPresent(RefreshToken::revoke);
    }

    private static InvalidCredentialsException expired() {
        return new InvalidCredentialsException("Your session has ended. Please sign in again.");
    }
}
