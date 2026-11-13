package com.adrovis.influencer.campaignmanagement.publisher.repository;

import com.adrovis.influencer.campaignmanagement.publisher.entity.PublisherProfile;
import com.adrovis.influencer.campaignmanagement.publisher.enums.PublisherType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublisherProfileRepository
        extends JpaRepository<PublisherProfile, UUID> {

    // Checks whether this account already has the selected publisher type.
    boolean existsByOwnerAccountIdAndPublisherType(
            UUID ownerAccountId,
            PublisherType publisherType
    );

    // Lists only active profiles belonging to the authenticated account.
    List<PublisherProfile> findAllByOwnerAccountIdAndArchivedAtIsNull(
            UUID ownerAccountId
    );

    // Combines resource identity and ownership in the database query.
    Optional<PublisherProfile> findByIdAndOwnerAccountIdAndArchivedAtIsNull(
            UUID id,
            UUID ownerAccountId
    );

    // Admin creation can select any active publisher profile.
    Optional<PublisherProfile> findByIdAndArchivedAtIsNull(UUID id);
}