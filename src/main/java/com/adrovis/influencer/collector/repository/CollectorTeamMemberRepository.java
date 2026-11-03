package com.adrovis.influencer.collector.repository;

import com.adrovis.influencer.collector.entity.CollectorTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectorTeamMemberRepository extends JpaRepository<CollectorTeamMember, UUID> {
    List<CollectorTeamMember> findByActiveTrue();
    Optional<CollectorTeamMember> findByTelegramUserId(Long telegramUserId);
    long countByActiveTrue();
}