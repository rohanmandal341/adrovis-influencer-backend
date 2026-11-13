package com.adrovis.influencer.campaignmanagement.publisher.controller;

import com.adrovis.influencer.campaignmanagement.publisher.dto.CreatePublisherProfileRequest;
import com.adrovis.influencer.campaignmanagement.publisher.dto.PublisherProfileResponse;
import com.adrovis.influencer.campaignmanagement.publisher.service.PublisherProfileService;
import com.adrovis.influencer.dto.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portal/publisher-profiles")
@RequiredArgsConstructor
public class PublisherProfileController {

    private final PublisherProfileService profiles;

    /**
     * Creates a profile for the authenticated account.
     * Role and ownership checks are enforced by the service.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PublisherProfileResponse>> create(
            @Valid @RequestBody CreatePublisherProfileRequest request
    ) {
        PublisherProfileResponse profile = profiles.create(request);

        URI location = URI.create(
                "/api/v1/portal/publisher-profiles/me"
        );

        return ResponseEntity.created(location)
                .body(ApiResponse.ok(
                        "Publisher profile created successfully.",
                        profile
                ));
    }

    /**
     * Lists only the authenticated account's active publisher profiles.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<PublisherProfileResponse>>> mine() {
        return ResponseEntity.ok(
                ApiResponse.ok(profiles.getOwnProfiles())
        );
    }
}