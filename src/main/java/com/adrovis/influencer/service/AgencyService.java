package com.adrovis.influencer.service;

import com.adrovis.influencer.dto.AgencyResponse;
import com.adrovis.influencer.entity.AgencyEntity;
import com.adrovis.influencer.repository.AgencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgencyService {

    private final AgencyRepository agencyRepository;

    @Transactional(readOnly = true)
    public List<AgencyResponse> getAllAgencies() {
        return agencyRepository.findAll().stream()
                .map(agency -> AgencyResponse.builder()
                        .id(agency.getId())
                        .agencyName(agency.getAgencyName())
                        .contactPhone(agency.getContactPhone())
                        .contactEmail(agency.getContactEmail())
                        .instagramHandle(agency.getInstagramHandle())
                        .notes(agency.getNotes())
                        .activeCampaignsCount(agency.getCampaigns() != null ? agency.getCampaigns().size() : 0)
                        .createdAt(agency.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
