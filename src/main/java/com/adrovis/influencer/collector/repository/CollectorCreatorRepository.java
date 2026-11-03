package com.adrovis.influencer.collector.repository;

import com.adrovis.influencer.collector.entity.CollectorCreator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface CollectorCreatorRepository extends JpaRepository<CollectorCreator, UUID>, JpaSpecificationExecutor<CollectorCreator> {

    @Query("SELECT COUNT(c) FROM CollectorCreator c")
    long countTotalCreators();

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.email IS NOT NULL AND c.email != ''")
    long countUniqueEmails();

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.instagramUsername IS NOT NULL AND c.instagramUsername != ''")
    long countWithUsername();

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.location IS NOT NULL AND c.location != ''")
    long countWithLocation();

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.createdAt >= :startDate")
    long countCreatedSince(@Param("startDate") OffsetDateTime startDate);

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.createdAt >= :startDate AND c.createdAt < :endDate")
    long countCreatedBetween(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.email IS NOT NULL AND c.createdAt >= :startDate")
    long countEmailsCreatedSince(@Param("startDate") OffsetDateTime startDate);

    @Query("SELECT COUNT(c) FROM CollectorCreator c WHERE c.email IS NOT NULL AND c.createdAt >= :startDate AND c.createdAt < :endDate")
    long countEmailsCreatedBetween(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    // Distribution of Niches (PostgreSQL unnest)
    @Query(value = """
        SELECT trim(niche_item) AS nicheName, COUNT(*) AS cnt
        FROM creators c, unnest(string_to_array(c.niche, ',')) AS niche_item
        WHERE c.niche IS NOT NULL AND trim(niche_item) <> ''
        GROUP BY trim(niche_item)
        ORDER BY cnt DESC
        LIMIT 10
    """, nativeQuery = true)
    List<Map<String, Object>> findNicheDistribution();

    // Location Insights (Top Cities)
    @Query(value = """
        SELECT location AS locName, COUNT(*) AS cnt
        FROM creators
        WHERE location IS NOT NULL AND location <> ''
        GROUP BY location
        ORDER BY cnt DESC
        LIMIT 6
    """, nativeQuery = true)
    List<Map<String, Object>> findLocationDistribution();

    // Collection trend over time
    @Query(value = """
        SELECT to_char(created_at, 'DD Mon') AS dayLabel,
               date(created_at) AS rawDate,
               COUNT(*) AS totalCreators,
               COUNT(email) AS uniqueEmails
        FROM creators
        WHERE created_at >= :startDate
        GROUP BY dayLabel, rawDate
        ORDER BY rawDate ASC
    """, nativeQuery = true)
    List<Map<String, Object>> findDailyCollectionTrend(@Param("startDate") OffsetDateTime startDate);

    // Recent creators
    List<CollectorCreator> findTop5ByOrderByCreatedAtDesc();
    List<CollectorCreator> findTop8ByOrderByCreatedAtDesc();
}