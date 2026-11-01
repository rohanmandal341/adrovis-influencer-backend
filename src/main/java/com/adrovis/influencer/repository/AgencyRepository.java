package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.AgencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgencyRepository extends JpaRepository<AgencyEntity, UUID> {
    Optional<AgencyEntity> findByContactPhone(String contactPhone);
}
