package com.adrovis.influencer.exception;

import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.payment.security.PaymentOperationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Preserves the existing not-found envelope without exposing storage details. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(404).body(ApiResponse.fail(exception.getMessage()));
    }

    /** Method-level authorization must produce 403, not be swallowed by the generic 500 handler. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException exception) {
        return failure(403, "FORBIDDEN", "You do not have permission to perform this action.");
    }

    /** Distinguishes missing identity from an unavailable trusted identity check. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> authentication(AuthenticationException exception) {
        return exception instanceof AuthenticationServiceException
                ? failure(503, "AUTHENTICATION_UNAVAILABLE", "Account validation is temporarily unavailable.")
                : failure(401, "UNAUTHORIZED", "A valid account access token is required.");
    }

    /** Payment services provide deliberately sanitized domain errors, never provider exception text. */
    @ExceptionHandler(PaymentOperationException.class)
    public ResponseEntity<Map<String, Object>> payment(PaymentOperationException exception) {
        return failure(exception.status(), exception.code(), exception.getMessage());
    }

    /** Invalid JSON, DTO fields and parameter types are client errors; rejected values are not echoed. */
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception) {
        return failure(400, "INVALID_REQUEST", "The request contains missing or invalid fields.");
    }

    /** Preserves existing explicit controller statuses such as an unknown analytics session. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> explicitStatus(ResponseStatusException exception) {
        return failure(exception.getStatusCode().value(), "REQUEST_REJECTED", "The request could not be completed.");
    }

    /** SQL statements, parameters and connection details never appear in public API responses. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> database(DataAccessException exception) {
        return failure(503, "DATA_UNAVAILABLE", "Data is temporarily unavailable. Please retry later.");
    }

    /** Replaces the old exception-message echo because it could expose SQL or provider details. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception exception) {
        String reference = java.util.UUID.randomUUID().toString();
        LOG.error("Unexpected request failure: reference={}, type={}", reference, exception.getClass().getSimpleName());
        return failure(500, "INTERNAL_ERROR", "An unexpected error occurred. Reference: " + reference);
    }

    /** Provides a consistent minimal error envelope and prevents caching security failures. */
    private ResponseEntity<Map<String, Object>> failure(int status, String code, String message) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(Map.of("success", false, "errorCode", code, "message", message));
    }
}
