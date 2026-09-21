package io.miragon.blueprint.adapter.inbound.rest;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cross-cutting web error handling for the REST adapter: turns domain and input errors into RFC 9457
 * {@code application/problem+json} responses (Spring serialises {@link ProblemDetail} as such
 * automatically).
 *
 * <p>This is web configuration, hence the {@code Configuration} suffix whitelisted for
 * {@code adapter.inbound.rest} and its home here rather than in a new top-level {@code config} package
 * (which the architecture tests would reject).
 */
@RestControllerAdvice
public class GlobalExceptionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionConfiguration.class);

    /** Invalid input — a bad UUID, a blank value object, an unknown status filter. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        log.debug("Bad request: {}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    /** Unknown aggregate — the services signal a missing application with {@link IllegalStateException}. */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException exception) {
        log.debug("Unprocessable: {}", exception.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    /**
     * Errors bubbling up from the remote engine over its REST API (via the generated client). A 4xx —
     * typically a mismatching message correlation when the process token is no longer at the expected
     * wait state (e.g. a withdraw arriving too late) — becomes a 409; anything else the engine rejects
     * is surfaced as a 502, since it is an upstream failure rather than a client mistake.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail handleEngineError(RestClientResponseException exception) {
        HttpStatus status = exception.getStatusCode().is4xxClientError()
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_GATEWAY;
        log.debug("Engine rejected the call ({}): {}", exception.getStatusCode(), exception.getMessage());
        String title = status == HttpStatus.CONFLICT ? "Action not available in the current state" : "Engine error";
        return problem(status, title, exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create("https://miravelo.example/problems/" + status.value()));
        return problemDetail;
    }
}
