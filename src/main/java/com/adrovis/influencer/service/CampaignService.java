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

    @Value("${app.backend-base-url:http://localhost:8080}")
    private String backendBaseUrl;

    @Transactional(readOnly = true)
    public Page<PublicCampaignResponse> getFilteredCampaigns(CampaignFilterRequest filter) {
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize());
        Page<CampaignEntity> entities = campaignRepository.findFilteredCampaigns(
                filter.getCampaignType(),
                filter.getMaxFollowers(),
                filter.getBrand(),
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
        log.info("Tracked application click for campaign ID: {} (Brand: {})", id, campaign.getBrandName());
        return campaign.getApplicationFormUrl();
    }

    @SuppressWarnings("unchecked")
    private PublicCampaignResponse mapToPublicResponse(CampaignEntity entity) {
        Map<String, Object> data = entity.getCanonicalData();
        if (data == null) {
            data = Collections.emptyMap();
        }

        List<String> imageUrls = entity.getImages().stream()
                .map(CampaignImageEntity::getPublicUrl)
                .collect(Collectors.toList());

        if (imageUrls.isEmpty() && data.containsKey("images")) {
            Object imgObj = data.get("images");
            if (imgObj instanceof List<?>) {
                imageUrls = ((List<?>) imgObj).stream().map(Object::toString).collect(Collectors.toList());
            }
        }

        Map<String, Object> comp = (Map<String, Object>) data.getOrDefault("compensation", Collections.emptyMap());
        List<String> deliverables = (List<String>) data.getOrDefault("deliverables", Collections.emptyList());
        List<String> categories = (List<String>) data.getOrDefault("categories", Collections.emptyList());
        Map<String, Object> eligibility = (Map<String, Object>) data.getOrDefault("eligibility", Collections.emptyMap());
        Map<String, Object> schedule = (Map<String, Object>) data.getOrDefault("schedule", Collections.emptyMap());
        List<String> reqs = (List<String>) data.getOrDefault("important_requirements", Collections.emptyList());
        List<String> refs = (List<String>) data.getOrDefault("reference_links", Collections.emptyList());

        String proxyUrl = backendBaseUrl + "/api/v1/campaigns/" + entity.getId() + "/apply";

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
                .adRights((String) data.get("ad_rights"))
                .categories(categories)
                .productName((String) data.get("product_name"))
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
