package com.adrovis.influencer.repository;

import com.adrovis.influencer.entity.InquiryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InquiryRepository extends JpaRepository<InquiryEntity, UUID> {

    @Query(value = """
        SELECT * FROM inquiries i
        WHERE (:role IS NULL OR i.role = :role)
          AND (:status IS NULL OR i.status = :status)
          AND (:search IS NULL OR (
                LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(i.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(i.company_name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              ))
        ORDER BY i.created_at DESC
        """,
            countQuery = """
        SELECT count(*) FROM inquiries i
        WHERE (:role IS NULL OR i.role = :role)
          AND (:status IS NULL OR i.status = :status)
          AND (:search IS NULL OR (
                LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(i.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(i.company_name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              ))
        """,
            nativeQuery = true)
    Page<InquiryEntity> findFilteredInquiries(
            @Param("role") String role,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );
}