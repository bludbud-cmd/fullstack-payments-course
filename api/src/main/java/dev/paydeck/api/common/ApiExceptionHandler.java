package dev.paydeck.api.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Turns exceptions into error responses in the standard "problem details"
 * format (RFC 9457), so the app can always show a helpful message.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail ruleBroken(BusinessRuleException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "Request not allowed", e.getMessage());
        problem.setProperty("code", e.getCode());
        return problem;
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "Not allowed", e.getMessage());
        problem.setProperty("code", "FORBIDDEN");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        ProblemDetail problem = problem(HttpStatus.UNAUTHORIZED, "Cannot sign in", e.getMessage());
        problem.setProperty("code", "INVALID_CREDENTIALS");
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
        problem.setProperty("code", e.getCode());
        return problem;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        return problem(HttpStatus.CONFLICT, "Idempotency key reused", e.getMessage());
    }

    /** Two requests raced to do the same thing, for example both using one Idempotency-Key. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail raced(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "Another request changed the same data at the same time. Please try again.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException e) {
        return problem(HttpStatus.BAD_REQUEST, "Missing header",
                "The " + e.getHeaderName() + " header is required");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Some fields are not valid");
        problem.setProperty("fields", fields);
        return problem;
    }

    /** Same as above, for controllers that also validate headers or path variables. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalidParameters(HandlerMethodValidationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors()
                        .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
            } else {
                result.getResolvableErrors().forEach(error -> fields.putIfAbsent(
                        result.getMethodParameter().getParameterName(), error.getDefaultMessage()));
            }
        });
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Some fields are not valid");
        problem.setProperty("fields", fields);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
