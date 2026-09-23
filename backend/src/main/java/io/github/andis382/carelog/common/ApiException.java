package io.github.andis382.carelog.common;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error the client is meant to see. The message is a key in messages*.properties
 * (resolved in the request language) or plain text when no key matches.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Object[] args;
    private final Map<String, List<String>> fieldErrors;
    private Map<String, Object> details = Map.of();

    public ApiException(HttpStatus status, String messageKey, Object... args) {
        this(status, messageKey, Map.of(), args);
    }

    private ApiException(HttpStatus status, String messageKey, Map<String, List<String>> fieldErrors, Object... args) {
        super(messageKey);
        this.status = status;
        this.args = args;
        this.fieldErrors = fieldErrors;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "error.not_found");
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "error.forbidden");
    }

    public static ApiException conflict(String messageKey, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, messageKey, args);
    }

    public static ApiException badRequest(String messageKey, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, messageKey, args);
    }

    /** A 422 tied to one field, rendered next to that field and in the form's error summary. */
    public static ApiException field(String field, String messageKey, Object... args) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, messageKey, Map.of(field, List.of(messageKey)), args);
    }

    /** Extra structured data for the client, e.g. who already gave a dose and when. */
    public ApiException withDetails(Map<String, Object> details) {
        this.details = details;
        return this;
    }

    public HttpStatus getStatus() { return status; }
    public Object[] getArgs() { return args; }
    public Map<String, List<String>> getFieldErrors() { return fieldErrors; }
    public Map<String, Object> getDetails() { return details; }
}
