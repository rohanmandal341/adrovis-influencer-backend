package com.adrovis.influencer.campaignmanagement.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.adrovis.influencer.campaignmanagement.application.exception.ApplicationAnswersInvalidException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        basePackages = "com.adrovis.influencer.campaignmanagement"
)
public class CampaignManagementExceptionHandler {

    @ExceptionHandler(CampaignManagementException.class)
    public ResponseEntity<Map<String, Object>> domain(
            CampaignManagementException exception
    ) {
        return failure(
                exception.getStatus().value(),
                exception.getErrorCode(),
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fields = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "Invalid value."
                                : error.getDefaultMessage()
                )
        );

        return failure(
                400,
                "INVALID_REQUEST",
                "Please correct the request fields.",
                fields
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> invalidParameters(
            ConstraintViolationException exception
    ) {
        return failure(
                400,
                "INVALID_REQUEST",
                "The request contains missing or invalid fields.",
                Map.of()
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> staleWrite(
            OptimisticLockingFailureException exception
    ) {
        return failure(
                412,
                "VERSION_CONFLICT",
                "Campaign changed. Reload it before saving.",
                Map.of()
        );
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> busy(
            PessimisticLockingFailureException exception
    ) {
        return failure(
                409,
                "CAMPAIGN_BUSY",
                "Campaign is being modified. Reload and try again.",
                Map.of()
        );
    }

    private ResponseEntity<Map<String, Object>> failure(
            int status,
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("errorCode", code);
        body.put("message", message);

        if (!fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }

        body.put("timestamp", Instant.now());

        return ResponseEntity.status(status)
                .header("Cache-Control", "no-store")
                .body(body);
    }
    @ExceptionHandler(ApplicationAnswersInvalidException.class)
    public ResponseEntity<Map<String, Object>> invalidAnswers(
            ApplicationAnswersInvalidException exception
    ) {
        return failure(
                422,
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getFieldErrors()
        );
    }
}