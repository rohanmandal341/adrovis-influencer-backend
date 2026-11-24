package com.adrovis.influencer.service;

import com.adrovis.influencer.dto.AdminCampaignResponse;
import com.adrovis.influencer.dto.UpdateAdminCampaignRequest;
import com.adrovis.influencer.entity.CampaignEntity;
import com.adrovis.influencer.entity.CampaignImageEntity;
import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminCampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;

    @Transactional(value = "primaryTransactionManager", readOnly = true)
    public Page<AdminCampaignResponse> listCampaigns(String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<CampaignEntity> entities = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? campaignRepository.findAllByOrderByCreatedAtDesc(pageable)
                : campaignRepository.findByApprovalStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT), pageable);

        return entities.map(this::toAdminResponse);
    }

    @Transactional(value = "primaryTransactionManager", readOnly = true)
    public AdminCampaignResponse getCampaign(UUID id) {
        CampaignEntity entity = campaignRepository.findByIdWithImages(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + id));
        return toAdminResponse(entity);
    }

    @Transactional(value = "primaryTransactionManager", readOnly = true)
    public Map<String, Long> getQueueCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PENDING", campaignRepository.countByApprovalStatus("PENDING"));
        counts.put("APPROVED", campaignRepository.countByApprovalStatus("APPROVED"));
        counts.put("REJECTED", campaignRepository.countByApprovalStatus("REJECTED"));
        return counts;
    }

    @Transactional("primaryTransactionManager")
    @SuppressWarnings("unchecked")
    public AdminCampaignResponse updateCampaign(UUID id, UpdateAdminCampaignRequest req) {
        CampaignEntity entity = campaignRepository.findByIdWithImages(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + id));

        // 1. Update indexed top-level columns
        entity.setBrandName(req.getBrandName().trim());
        entity.setCampaignTitle(req.getCampaignTitle().trim());
        entity.setCampaignType(req.getCampaignType().trim().toUpperCase(Locale.ROOT));
        entity.setBrandInstagramUrl(blankToNull(req.getBrandInstagramUrl()));
        entity.setApplicationFormUrl(req.getApplicationFormUrl().trim());
        if (req.getExpiresAt() != null) {
            entity.setExpiresAt(req.getExpiresAt());
        }

        // 2. Replace ordered rows in public.campaign_images (primary source for frontend Gallery)
        List<String> cleanImages = cleanList(req.getImages(), 6);
        entity.getImages().clear();
        campaignRepository.saveAndFlush(entity);

        for (int i = 0; i < cleanImages.size(); i++) {
            entity.getImages().add(CampaignImageEntity.builder()
                    .campaign(entity)
                    .storagePath("admin-curated")
                    .publicUrl(cleanImages.get(i))
                    .imageOrder(i + 1)
                    .build());
        }

        // 3. Synchronize canonical_data JSONB so native SQL filters & fallbacks stay 100% consistent
        Map<String, Object> data = entity.getCanonicalData() != null
                ? new LinkedHashMap<>(entity.getCanonicalData())
                : new LinkedHashMap<>();

        data.put("brand_name", entity.getBrandName());
        data.put("campaign_title", entity.getCampaignTitle());
        data.put("campaign_type", entity.getCampaignType());
        data.put("description", blankToNull(req.getDescription()));
        data.put("brand_instagram_url", entity.getBrandInstagramUrl());
        data.put("application_form_url", entity.getApplicationFormUrl());
        data.put("images", cleanImages);

        if (req.getReferenceLinks() != null) {
            data.put("reference_links", cleanList(req.getReferenceLinks(), 10));
        }

        // Compensation
        Map<String, Object> comp = new LinkedHashMap<>((Map<String, Object>) data.getOrDefault("compensation", Map.of()));
        comp.put("currency", "INR");
        comp.put("creator_payout", blankToNull(req.getCreatorPayout()));
        comp.put("payout_amount_inr", req.getPayoutAmountInr());
        comp.put("product_provided", blankToNull(req.getProductProvided()));
        comp.put("product_value", blankToNull(req.getProductValue()));
        comp.put("product_value_inr", req.getProductValueInr());
        comp.put("compensation_details", blankToNull(req.getCompensationDetails()));
        if (req.getPayoutTiers() != null) {
            comp.put("payout_tiers", req.getPayoutTiers());
        }
        data.put("compensation", comp);

        // Eligibility
        Map<String, Object> elig = new LinkedHashMap<>((Map<String, Object>) data.getOrDefault("eligibility", Map.of()));
        if (req.getMinimumFollowers() != null) elig.put("minimum_followers", req.getMinimumFollowers());
        if (req.getMinimumFollowersDisplay() != null) elig.put("minimum_followers_display", req.getMinimumFollowersDisplay().trim());
        if (req.getGender() != null) elig.put("gender", req.getGender().trim().toUpperCase(Locale.ROOT));
        if (req.getLocations() != null) elig.put("locations", cleanList(req.getLocations(), 30));
        if (req.getOtherRequirements() != null) elig.put("other_requirements", cleanList(req.getOtherRequirements(), 30));
        data.put("eligibility", elig);

        // Campaign details (syncs both top-level and nested campaign_details keys)
        Map<String, Object> details = new LinkedHashMap<>((Map<String, Object>) data.getOrDefault("campaign_details", Map.of()));
        details.put("description", blankToNull(req.getDescription()));
        if (req.getDeliverables() != null) {
            List<String> dels = cleanList(req.getDeliverables(), 30);
            data.put("deliverables", dels);
            details.put("deliverables", dels);
        }
        if (req.getCategories() != null) {
            List<String> cats = cleanList(req.getCategories(), 15);
            data.put("categories", cats);
            details.put("creator_categories", cats);
        }
        data.put("ad_rights", blankToNull(req.getAdRights()));
        details.put("ad_rights", blankToNull(req.getAdRights()));
        data.put("product_name", blankToNull(req.getProductName()));
        details.put("product_details", blankToNull(req.getProductName()));
        data.put("campaign_details", details);

        // Schedule
        Map<String, Object> schedule = new LinkedHashMap<>((Map<String, Object>) data.getOrDefault("schedule", Map.of()));
        if (req.getApplicationDeadline() != null) schedule.put("application_deadline", req.getApplicationDeadline().trim());
        if (req.getContentSubmissionDeadline() != null) schedule.put("content_submission_deadline", req.getContentSubmissionDeadline().trim());
        if (req.getGoLiveDate() != null) schedule.put("go_live_date", req.getGoLiveDate().trim());
        data.put("schedule", schedule);

        if (req.getImportantRequirements() != null) {
            data.put("important_requirements", cleanList(req.getImportantRequirements(), 30));
        }

        entity.setCanonicalData(data);

        // 4. Optional instant approval upon save
        if (Boolean.TRUE.equals(req.getApproveNow())) {
            entity.setApprovalStatus("APPROVED");
            entity.setIsActive(true);
        }

        CampaignEntity saved = campaignRepository.saveAndFlush(entity);
        log.info("Admin updated campaign {} (status={}, isActive={})", id, saved.getApprovalStatus(), saved.getIsActive());
        return toAdminResponse(saved);
    }

    @Transactional("primaryTransactionManager")
    public AdminCampaignResponse setApprovalStatus(UUID id, String targetStatus) {
        CampaignEntity entity = campaignRepository.findByIdWithImages(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + id));

        String normalized = targetStatus.trim().toUpperCase(Locale.ROOT);
        entity.setApprovalStatus(normalized);
        entity.setIsActive("APPROVED".equals(normalized));

        CampaignEntity saved = campaignRepository.saveAndFlush(entity);
        log.info("Campaign {} transitioned to {} (isActive={})", id, normalized, saved.getIsActive());
        return toAdminResponse(saved);
    }

    @Transactional("primaryTransactionManager")
    public void deleteCampaign(UUID id) {
        if (!campaignRepository.existsById(id)) {
            throw new ResourceNotFoundException("Campaign not found with ID: " + id);
        }
        campaignRepository.deleteById(id);
    }

    private AdminCampaignResponse toAdminResponse(CampaignEntity entity) {
        return AdminCampaignResponse.builder()
                .approvalStatus(entity.getApprovalStatus())
                .isActive(entity.getIsActive())
                .applicationFormUrl(entity.getApplicationFormUrl())
                .campaign(campaignService.mapToPublicResponse(entity))
                .build();
    }

    private List<String> cleanList(List<String> raw, int limit) {
        if (raw == null) return List.of();
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }

    private String blankToNull(String val) {
        return (val == null || val.isBlank()) ? null : val.trim();
    }
}