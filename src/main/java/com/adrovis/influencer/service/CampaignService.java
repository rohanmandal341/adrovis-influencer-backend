package com.adrovis.influencer.service;

import com.adrovis.influencer.dto.CampaignFilterRequest;
import com.adrovis.influencer.dto.PublicCampaignResponse;
import com.adrovis.influencer.entity.CampaignEntity;
import com.adrovis.influencer.entity.CampaignImageEntity;
import com.adrovis.influencer.exception.ResourceNotFoundException;
import com.adrovis.influencer.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;

    @Value("${app.backend-base-url:https://adrovis-influencer-backend.onrender.com}")
    private String backendBaseUrl;

    @Transactional(readOnly = true)
    public Page<PublicCampaignResponse> getFilteredCampaigns(CampaignFilterRequest filter) {
        int page = Math.max(0, filter.getPage() != null ? filter.getPage() : 0);
        int size = Math.max(1, Math.min(100, filter.getSize() != null ? filter.getSize() : 12));
        Pageable pageable = PageRequest.of(page, size);

        Page<CampaignEntity> entities = campaignRepository.findFilteredCampaigns(
                filter.getCampaignType(),
                filter.getMaxFollowers(),
                filter.getBrand(),
                filter.getCategory(),
                filter.getLocation(),
                filter.getMinPayout(),
                pageable
        );
        return entities.map(this::mapToPublicResponse);
    }

    @Transactional
    public PublicCampaignResponse getCampaignById(UUID id) {
        CampaignEntity campaign = campaignRepository.findActiveByIdWithImages(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + id));

        campaignRepository.incrementViewsCount(id);
        return mapToPublicResponse(campaign);
    }

    @Transactional
    public String trackAndGetApplicationUrl(UUID id) {
        CampaignEntity campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with ID: " + id));

        campaignRepository.incrementClicksCount(id);
        log.info("Application click recorded for campaign ID: {} (Brand: {})", id, campaign.getBrandName());
        return campaign.getApplicationFormUrl();
    }

    @SuppressWarnings("unchecked")
    private PublicCampaignResponse mapToPublicResponse(CampaignEntity entity) {
        Map<String, Object> data = entity.getCanonicalData();
        if (data == null) {
            data = Collections.emptyMap();
        }

        // 1. Resolve Images: strictly deduplicated, max 4 images
        List<String> imageUrls = entity.getImages().stream()
                .map(CampaignImageEntity::getPublicUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .limit(4)
                .collect(Collectors.toList());

        if (imageUrls.isEmpty() && data.containsKey("images")) {
            Object imgObj = data.get("images");
            if (imgObj instanceof List<?>) {
                imageUrls = ((List<?>) imgObj).stream()
                        .map(Object::toString)
                        .filter(url -> url != null && !url.isBlank())
                        .distinct()
                        .limit(4)
                        .collect(Collectors.toList());
            }
        }

        // 2. Resolve nested campaign_details (fixes deliverables, categories, adRights, productName)
        Map<String, Object> campaignDetails = (Map<String, Object>) data.getOrDefault("campaign_details", Collections.emptyMap());

        List<String> deliverables = (List<String>) data.get("deliverables");
        if (deliverables == null || deliverables.isEmpty()) {
            deliverables = (List<String>) campaignDetails.getOrDefault("deliverables", Collections.emptyList());
        }

        List<String> categories = (List<String>) data.get("categories");
        if (categories == null || categories.isEmpty()) {
            categories = (List<String>) campaignDetails.getOrDefault("creator_categories", Collections.emptyList());
        }

        String adRights = (String) data.get("ad_rights");
        if (adRights == null || adRights.isBlank()) {
            adRights = (String) campaignDetails.get("ad_rights");
        }

        String productName = (String) data.get("product_name");
        if (productName == null || productName.isBlank()) {
            productName = (String) campaignDetails.get("product_details");
        }

        // 3. Resolve Compensation & Eligibility
        Map<String, Object> comp = (Map<String, Object>) data.getOrDefault("compensation", Collections.emptyMap());
        Map<String, Object> eligibility = (Map<String, Object>) data.getOrDefault("eligibility", Collections.emptyMap());
        Map<String, Object> schedule = (Map<String, Object>) data.getOrDefault("schedule", Collections.emptyMap());
        List<String> reqs = (List<String>) data.getOrDefault("important_requirements", Collections.emptyList());
        List<String> refs = (List<String>) data.getOrDefault("reference_links", Collections.emptyList());

        // 4. Dynamic Proxy URL Resolution (Stripping local host and trailing slashes)
        String baseUrl = backendBaseUrl != null ? backendBaseUrl.replaceAll("/+$", "") : "https://adrovis-influencer-backend.onrender.com";
        String proxyUrl = baseUrl + "/api/v1/campaigns/" + entity.getId() + "/apply";

        return PublicCampaignResponse.builder()
                .id(entity.getId())
                .brandName(entity.getBrandName())
                .brandInstagramUrl(entity.getBrandInstagramUrl())
                .campaignTitle(entity.getCampaignTitle())
                .campaignType(entity.getCampaignType())
                .applyProxyUrl(proxyUrl)
                .images(imageUrls)
                .compensation(comp)
                .deliverables(deliverables)
                .adRights(adRights)
                .categories(categories)
                .productName(productName)
                .eligibility(eligibility)
                .schedule(schedule)
                .importantRequirements(reqs)
                .referenceLinks(refs)
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .viewsCount(entity.getViewsCount())
                .clicksCount(entity.getClicksCount())
                .build();
    }
}