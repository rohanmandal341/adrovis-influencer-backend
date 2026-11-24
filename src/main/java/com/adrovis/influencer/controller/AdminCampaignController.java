package com.adrovis.influencer.controller;

import com.adrovis.influencer.dto.AdminCampaignResponse;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.dto.UpdateAdminCampaignRequest;
import com.adrovis.influencer.service.AdminCampaignService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/campaigns")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "adrovisBearer")
public class AdminCampaignController {

    private final AdminCampaignService adminCampaignService;

    public AdminCampaignController(AdminCampaignService adminCampaignService) {
        this.adminCampaignService = adminCampaignService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminCampaignResponse>>> listByStatus(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminCampaignService.listCampaigns(status, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCampaignResponse>> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(adminCampaignService.getCampaign(id)));
    }

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<Object>> getCounts() {
        return ResponseEntity.ok(ApiResponse.ok(adminCampaignService.getQueueCounts()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCampaignResponse>> updateCampaign(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminCampaignRequest request) {
        AdminCampaignResponse updated = adminCampaignService.updateCampaign(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Campaign updated successfully", updated));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminCampaignResponse>> approveCampaign(@PathVariable UUID id) {
        AdminCampaignResponse approved = adminCampaignService.setApprovalStatus(id, "APPROVED");
        return ResponseEntity.ok(ApiResponse.ok("Campaign approved and published live", approved));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AdminCampaignResponse>> rejectCampaign(@PathVariable UUID id) {
        AdminCampaignResponse rejected = adminCampaignService.setApprovalStatus(id, "REJECTED");
        return ResponseEntity.ok(ApiResponse.ok("Campaign rejected and hidden from public feed", rejected));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCampaign(@PathVariable UUID id) {
        adminCampaignService.deleteCampaign(id);
        return ResponseEntity.ok(ApiResponse.ok("Campaign deleted", null));
    }
}