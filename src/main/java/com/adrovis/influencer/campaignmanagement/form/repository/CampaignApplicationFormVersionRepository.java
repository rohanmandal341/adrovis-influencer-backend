package com.adrovis.influencer.campaignmanagement.form.repository;

import com.adrovis.influencer.campaignmanagement.form.entity.CampaignApplicationFormVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CampaignApplicationFormVersionRepository
        extends Repository<CampaignApplicationFormVersion, UUID> {

    CampaignApplicationFormVersion save(
            CampaignApplicationFormVersion version
    );

    Optional<CampaignApplicationFormVersion>
    findFirstByCampaign_IdOrderByVersionNumberDesc(UUID campaignId);

    Optional<CampaignApplicationFormVersion>
    findByCampaign_IdAndVersionNumber(
            UUID campaignId,
            int versionNumber
    );
    Optional<CampaignApplicationFormVersion> findByIdAndCampaign_Id(
            UUID id,
            UUID campaignId
    );

}