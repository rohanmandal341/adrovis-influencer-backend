package com.adrovis.influencer.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.entity.InquiryEntity;
import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.repository.InquiryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
@Validated
public class InquiryController {

    private final InquiryRepository inquiryRepository;

    @Data
    public static class CreateInquiryRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        // OPTIONAL: Phone number
        private String phone;

        // OPTIONAL: Default is CREATOR if not sent
        private String role; // "BRAND", "CREATOR", "AGENCY"

        // STRICTLY OPTIONAL: Creators do not have companies
        private String companyName;

        // OPTIONAL: Only brands/agencies typically fill this
        private String budget;

        @NotBlank(message = "Message or requirement details required")
        private String message;
    }

    @Data
    public static class UpdateStatusRequest {
        @NotBlank(message = "Status is required")
        private String status; // "NEW", "IN_REVIEW", "CONTACTED", "RESOLVED"
    }

    /**
     * POST /api/v1/inquiries
     * Public submission endpoint for Creators, Brands, and Agencies
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitInquiry(@Valid @RequestBody CreateInquiryRequest request) {
        String cleanRole = request.getRole() != null ? request.getRole().toUpperCase().trim() : "CREATOR";
        if (!cleanRole.matches("BRAND|CREATOR|AGENCY")) {
            cleanRole = "CREATOR";
        }

        InquiryEntity inquiry = InquiryEntity.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null)
                .role(cleanRole)
                .companyName(request.getCompanyName() != null && !request.getCompanyName().isBlank() ? request.getCompanyName().trim() : null)
                .budget(request.getBudget() != null && !request.getBudget().isBlank() ? request.getBudget().trim() : null)
                .message(request.getMessage().trim())
                .status("NEW")
                .build();

        InquiryEntity saved = inquiryRepository.save(inquiry);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Your inquiry has been received. Our team will contact you shortly.", saved.getId().toString()));
    }

    /**
     * GET /api/v1/inquiries
     * Paginated list with filtering by role, status, and search query
     */
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InquiryEntity>>> listInquiries(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));

        String cleanRole = (role != null && !role.isBlank()) ? role.toUpperCase().trim() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.toUpperCase().trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<InquiryEntity> results = inquiryRepository.findFilteredInquiries(cleanRole, cleanStatus, cleanSearch, pageable);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * GET /api/v1/inquiries/{id}
     * Get specific inquiry details
     */
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InquiryEntity>> getInquiryDetails(@PathVariable UUID id) {
        InquiryEntity inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found with ID: " + id));
        return ResponseEntity.ok(ApiResponse.ok(inquiry));
    }

    /**
     * PATCH /api/v1/inquiries/{id}/status
     * Update status (NEW -> IN_REVIEW -> CONTACTED -> RESOLVED)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "adrovisBearer")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<InquiryEntity>> updateInquiryStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        InquiryEntity inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found with ID: " + id));

        inquiry.setStatus(request.getStatus().toUpperCase().trim());
        InquiryEntity updated = inquiryRepository.save(inquiry);

        return ResponseEntity.ok(ApiResponse.ok("Status updated successfully", updated));
    }
}
