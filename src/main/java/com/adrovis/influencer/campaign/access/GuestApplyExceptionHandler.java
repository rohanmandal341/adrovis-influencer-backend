package com.adrovis.influencer.campaign.access;

import com.adrovis.influencer.controller.CampaignController;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CampaignController.class)
public class GuestApplyExceptionHandler {

    @ExceptionHandler(SignupRequiredException.class)
    public ResponseEntity<Map<String, Object>> signupRequired(SignupRequiredException exception) {
        return ResponseEntity.status(403).header("Cache-Control", "no-store").body(Map.of(
                "success", false,
                "errorCode", "SIGNUP_REQUIRED",
                "message", exception.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> unavailable(DataAccessException exception) {
        return ResponseEntity.status(503).header("Cache-Control", "no-store").body(Map.of(
                "success", false, "errorCode", "APPLICATION_ACCESS_UNAVAILABLE",
                "message", "Application access is temporarily unavailable. Please try again."));
    }
}
