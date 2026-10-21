package dev.paydeck.api.auth;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-up and sign-in. These are the only money-free endpoints anyone can call without a token. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128, message = "must be at least 10 characters") String password,
            @NotBlank @Size(max = 100) String displayName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Step two of "Sign in with Google": the one-time code plus the PKCE verifier only the app knows. */
    public record CodeExchangeRequest(@NotBlank String code, @NotBlank @Size(min = 43, max = 128) String codeVerifier) {
    }

    public record UserResponse(UUID id, String email, String displayName, Role role) {

        public static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
        }
    }

    public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                               UserResponse user) {

        static AuthResponse of(AuthService.SignedIn signedIn) {
            return new AuthResponse(signedIn.tokens().accessToken(), signedIn.tokens().refreshToken(), "Bearer",
                    signedIn.tokens().expiresInSeconds(), UserResponse.of(signedIn.user()));
        }
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.SignedIn signedIn = auth.register(request.email(), request.password(),
                request.displayName().strip());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.of(signedIn));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.of(auth.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthResponse.of(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/token")
    public AuthResponse exchangeCode(@Valid @RequestBody CodeExchangeRequest request) {
        return AuthResponse.of(auth.redeemLoginCode(request.code(), request.codeVerifier()));
    }
}
