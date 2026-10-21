package dev.paydeck.api.auth;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Security turns away requests before they reach a controller, so it
 * needs its own way to answer in the same problem-details format.
 */
final class ProblemResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ProblemResponses() {
    }

    /** No token, or a token that is expired or forged: 401, and the app should sign in again. */
    static AuthenticationEntryPoint notSignedIn() {
        return (request, response, e) -> write(response, HttpStatus.UNAUTHORIZED, "Not signed in",
                "Sign in to continue.", "NOT_SIGNED_IN");
    }

    /** Signed in, but this role may not use this endpoint: 403. */
    static AccessDeniedHandler notAllowed() {
        return (request, response, e) -> write(response, HttpStatus.FORBIDDEN, "Not allowed",
                "Your account is not allowed to do this.", "FORBIDDEN");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String title, String detail,
                              String code) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        JSON.writeValue(response.getOutputStream(), problem);
    }
}
