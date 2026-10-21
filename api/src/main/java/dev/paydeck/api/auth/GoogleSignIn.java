package dev.paydeck.api.auth;

import java.io.IOException;
import java.net.URI;

import dev.paydeck.api.common.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * "Sign in with Google" for an app, in four steps:
 * <ol>
 *   <li>The app makes a secret code verifier, and opens the browser at
 *   {@code /api/auth/google} with its hash (the code challenge).</li>
 *   <li>The API sends the browser to Google. Spring Security handles the
 *   OAuth 2.0 back-and-forth with Google.</li>
 *   <li>Google sends the browser back to the API, which finds or creates the
 *   user and sends the browser to the app's link ({@code paydeck://auth})
 *   with a one-time code.</li>
 *   <li>The app posts the code and its verifier to {@code /api/auth/token}
 *   and gets PayDeck tokens.</li>
 * </ol>
 * The app never sees Google's secrets, and Google's client secret never
 * leaves the server.
 */
@RestController
public class GoogleSignIn {

    private static final String REDIRECT_URI = "paydeck.google.redirectUri";
    private static final String CODE_CHALLENGE = "paydeck.google.codeChallenge";

    private final ObjectProvider<ClientRegistrationRepository> googleClient;
    private final AuthService auth;
    private final AuthProperties properties;

    public GoogleSignIn(ObjectProvider<ClientRegistrationRepository> googleClient, AuthService auth,
                        AuthProperties properties) {
        this.googleClient = googleClient;
        this.auth = auth;
        this.properties = properties;
    }

    @GetMapping("/api/auth/google")
    public ResponseEntity<?> start(@RequestParam("redirect_uri") String redirectUri,
                                   @RequestParam("code_challenge") String codeChallenge,
                                   @RequestParam(name = "code_challenge_method", defaultValue = "S256") String method,
                                   HttpServletRequest request) {
        if (googleClient.getIfAvailable() == null) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Sign in with Google is not set up on this server.");
        }
        // Only send codes to links we know belong to the PayDeck app. Otherwise
        // a phishing page could start a sign-in and collect the code itself.
        if (!properties.appRedirectUris().contains(redirectUri)) {
            return problem(HttpStatus.BAD_REQUEST, "BAD_REDIRECT_URI", "This redirect_uri is not allowed.");
        }
        if (!"S256".equals(method) || codeChallenge.length() < 43 || codeChallenge.length() > 128) {
            return problem(HttpStatus.BAD_REQUEST, "BAD_CODE_CHALLENGE", "Send a SHA-256 (S256) code challenge.");
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(REDIRECT_URI, redirectUri);
        session.setAttribute(CODE_CHALLENGE, codeChallenge);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/oauth2/authorization/google")).build();
    }

    /** Google said yes. Send the browser back to the app with a one-time code. */
    void onSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        HttpSession session = request.getSession(false);
        String redirectUri = session == null ? null : (String) session.getAttribute(REDIRECT_URI);
        String challenge = session == null ? null : (String) session.getAttribute(CODE_CHALLENGE);
        // PayDeck uses its own tokens from here on, so forget the Google session.
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        if (redirectUri == null || challenge == null) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Start signing in from the PayDeck app.");
            return;
        }

        OidcUser google = (OidcUser) authentication.getPrincipal();
        try {
            User user = auth.signInWithGoogle(google.getSubject(), google.getEmail(),
                    Boolean.TRUE.equals(google.getEmailVerified()), google.getFullName());
            String code = auth.createLoginCode(user.getId(), challenge);
            response.sendRedirect(withParam(redirectUri, "code", code));
        } catch (InvalidCredentialsException e) {
            response.sendRedirect(withParam(redirectUri, "error", "email_not_verified"));
        }
    }

    /** Google said no, or the person pressed Cancel. */
    void onFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        HttpSession session = request.getSession(false);
        String redirectUri = session == null ? null : (String) session.getAttribute(REDIRECT_URI);
        if (redirectUri == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Google sign-in failed.");
            return;
        }
        session.invalidate();
        response.sendRedirect(withParam(redirectUri, "error", "access_denied"));
    }

    private static String withParam(String uri, String name, String value) {
        return UriComponentsBuilder.fromUriString(uri).queryParam(name, value).build().toUriString();
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("Cannot sign in with Google");
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
