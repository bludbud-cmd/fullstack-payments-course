package dev.paydeck.api.auth;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. Every request needs a valid access token except
 * signing in, the API docs and the health check. Finer rules (is this your
 * card?) live in {@link dev.paydeck.api.access.AccessPolicy}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<ClientRegistrationRepository> googleClient,
                                            GoogleSignIn googleSignIn) throws Exception {
        http
                // CSRF attacks ride on cookies the browser sends by itself. The API is
                // signed into with an Authorization header instead, which a
                // malicious web page cannot add, so CSRF protection is not needed.
                .csrf(csrf -> csrf.disable())
                // No login session is kept between requests: each one carries its own token.
                // (Google sign-in briefly needs a session to remember where it started.)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/**", "/oauth2/**", "/login/oauth2/**").permitAll()
                        // Stripe calls the webhook, and checks its own signature (see StripeWebhookController).
                        // The WebSocket checks the token itself, in StompAuthInterceptor.
                        .requestMatchers("/api/stripe/webhook", "/payments/return", "/ws").permitAll()
                        .requestMatchers("/actuator/health", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**",
                                "/error").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("SUPPORT", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server
                        .authenticationEntryPoint(ProblemResponses.notSignedIn())
                        .accessDeniedHandler(ProblemResponses.notAllowed())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromToken())))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(ProblemResponses.notSignedIn())
                        .accessDeniedHandler(ProblemResponses.notAllowed()));

        // "Sign in with Google" is switched on only when a Google client ID is configured.
        if (googleClient.getIfAvailable() != null) {
            http.oauth2Login(login -> login
                    .loginPage("/api/auth/google")
                    .successHandler(googleSignIn::onSuccess)
                    .failureHandler(googleSignIn::onFailure));
        }
        return http.build();
    }

    /** BCrypt, stored with a {bcrypt} prefix so the algorithm can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecretKey jwtSigningKey(AuthProperties properties) {
        byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("paydeck.auth.jwt-secret (JWT_SECRET) must be at least 32 characters");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /** Accepts only tokens that PayDeck signed, that have not expired. */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.ISSUER));
        return decoder;
    }

    /** Turns the token's "role" claim into ROLE_CUSTOMER, ROLE_SUPPORT or ROLE_ADMIN. */
    private static JwtAuthenticationConverter rolesFromToken() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
