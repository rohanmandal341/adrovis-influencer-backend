package com.adrovis.influencer.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.entity.NewsletterSubscriptionEntity;
import com.adrovis.influencer.repository.NewsletterSubscriptionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/newsletter")
@RequiredArgsConstructor
@Validated
public class NewsletterController {

    private final NewsletterSubscriptionRepository subscriptionRepository;

    @Data
    public static class SubscribeRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
    }

    /**
     * POST /api/v1/newsletter/subscribe
     * Idempotent email subscription
     */
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<String>> subscribe(@Valid @RequestBody SubscribeRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        if (!subscriptionRepository.existsByEmailIgnoreCase(cleanEmail)) {
            NewsletterSubscriptionEntity sub = NewsletterSubscriptionEntity.builder()
                    .email(cleanEmail)
                    .isActive(true)
                    .build();
            subscriptionRepository.save(sub);
        }

        return ResponseEntity.ok(ApiResponse.ok("Subscribed successfully! Welcome to Brand Collabz.", cleanEmail));
    }

    /**
     * GET /api/v1/newsletter/subscribers
     * Paginated list of subscribers with optional email search and active-status filtering
     */
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @GetMapping("/subscribers")
    public ResponseEntity<ApiResponse<Page<NewsletterSubscriptionEntity>>> getSubscribers(
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<NewsletterSubscriptionEntity> subscribers = subscriptionRepository.findFilteredSubscriptions(activeOnly, cleanSearch, pageable);
        return ResponseEntity.ok(ApiResponse.ok(subscribers));
    }
}
