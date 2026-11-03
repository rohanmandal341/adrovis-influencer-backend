package com.adrovis.influencer.collector.service;

import com.adrovis.influencer.collector.dto.CreatorFilterRequest;
import com.adrovis.influencer.collector.dto.CreatorTableResponse;
import com.adrovis.influencer.collector.dto.RecentCreatorDto;
import com.adrovis.influencer.collector.entity.CollectorCreator;
import com.adrovis.influencer.collector.entity.CollectorTeamMember;
import com.adrovis.influencer.collector.repository.CollectorCreatorRepository;
import com.adrovis.influencer.collector.repository.CollectorTeamMemberRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectorCreatorService {

    private final CollectorCreatorRepository creatorRepo;
    private final CollectorTeamMemberRepository memberRepo;

    private static final DateTimeFormatter UI_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");

    @Transactional(readOnly = true, transactionManager = "collectorTransactionManager")
    public CreatorTableResponse getCreatorsTable(CreatorFilterRequest req) {
        Sort.Direction direction = "asc".equalsIgnoreCase(req.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = req.getSortBy() != null ? req.getSortBy() : "createdAt";
        Pageable pageable = PageRequest.of(Math.max(0, req.getPage()), Math.max(1, req.getSize()), Sort.by(direction, sortProperty));

        Specification<CollectorCreator> spec = buildSpecification(req);
        Page<CollectorCreator> page = creatorRepo.findAll(spec, pageable);

        Map<UUID, String> memberNameMap = memberRepo.findAll().stream()
                .collect(Collectors.toMap(CollectorTeamMember::getId, CollectorTeamMember::getDisplayName, (a, b) -> a));

        List<RecentCreatorDto> items = page.getContent().stream().map(c -> {
            String addedByName = c.getFirstAddedBy() != null ? memberNameMap.getOrDefault(c.getFirstAddedBy(), "Admin") : "Admin";
            return RecentCreatorDto.builder()
                    .id(c.getId())
                    .username(c.getInstagramUsername() != null ? "@" + c.getInstagramUsername() : "—")
                    .name(c.getFullName() != null ? c.getFullName() : "UNKNOWN")
                    .email(c.getEmail())
                    .followers(c.getFollowers())
                    .posts(c.getPostCount())
                    .niche(c.getNiche() != null ? c.getNiche().split(",")[0].trim() : "—")
                    .location(c.getLocation())
                    .addedBy(addedByName)
                    .addedByInitials(getInitials(addedByName))
                    .addedAt(c.getCreatedAt() != null ? c.getCreatedAt().format(UI_DATE_FMT) : "—")
                    .build();
        }).collect(Collectors.toList());

        long totalCreators = creatorRepo.countTotalCreators();
        long withEmail = creatorRepo.countUniqueEmails();
        long withUser = creatorRepo.countWithUsername();
        long withLoc = creatorRepo.countWithLocation();

        CreatorTableResponse.CreatorStatsBar statsBar = CreatorTableResponse.CreatorStatsBar.builder()
                .totalCreators(totalCreators)
                .totalGrowth("+12.8%")
                .withEmailCount(withEmail)
                .withEmailPercentage(totalCreators > 0 ? Math.round((withEmail * 100.0 / totalCreators) * 10.0) / 10.0 : 0.0)
                .withUsernameCount(withUser)
                .withUsernamePercentage(totalCreators > 0 ? Math.round((withUser * 100.0 / totalCreators) * 10.0) / 10.0 : 0.0)
                .withLocationCount(withLoc)
                .withLocationPercentage(totalCreators > 0 ? Math.round((withLoc * 100.0 / totalCreators) * 10.0) / 10.0 : 0.0)
                .totalNiches(7)
                .build();

        return CreatorTableResponse.builder()
                .stats(statsBar)
                .items(items)
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "collectorTransactionManager")
    public String exportCreatorsCsv(CreatorFilterRequest req) {
        Specification<CollectorCreator> spec = buildSpecification(req);
        List<CollectorCreator> creators = creatorRepo.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println("ID,Instagram Username,Full Name,Email,Followers,Following,Posts,Location,Niche,Added At");

        for (CollectorCreator c : creators) {
            pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s,\"%s\",\"%s\",\"%s\"%n",
                    c.getId(),
                    safe(c.getInstagramUsername()),
                    safe(c.getFullName()),
                    safe(c.getEmail()),
                    c.getFollowers() != null ? c.getFollowers() : "",
                    c.getFollowing() != null ? c.getFollowing() : "",
                    c.getPostCount() != null ? c.getPostCount() : "",
                    safe(c.getLocation()),
                    safe(c.getNiche()),
                    c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""
            );
        }
        pw.flush();
        return sw.toString();
    }

    private Specification<CollectorCreator> buildSpecification(CreatorFilterRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (req.getSearch() != null && !req.getSearch().isBlank()) {
                String term = "%" + req.getSearch().trim().toLowerCase() + "%";
                Predicate searchEmail = cb.like(cb.lower(root.get("email")), term);
                Predicate searchUsername = cb.like(cb.lower(root.get("instagramUsername")), term);
                Predicate searchName = cb.like(cb.lower(root.get("fullName")), term);
                predicates.add(cb.or(searchEmail, searchUsername, searchName));
            }

            if (req.getNiche() != null && !req.getNiche().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("niche")), "%" + req.getNiche().trim().toLowerCase() + "%"));
            }

            if (req.getLocation() != null && !req.getLocation().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")), "%" + req.getLocation().trim().toLowerCase() + "%"));
            }

            if (req.getAddedBy() != null) {
                predicates.add(cb.equal(root.get("firstAddedBy"), req.getAddedBy()));
            }

            if (req.getFollowerRange() != null && !req.getFollowerRange().isBlank()) {
                switch (req.getFollowerRange()) {
                    case "0-10k" -> predicates.add(cb.lessThanOrEqualTo(root.get("followers"), 10000L));
                    case "10k-50k" -> predicates.add(cb.between(root.get("followers"), 10000L, 50000L));
                    case "50k-100k" -> predicates.add(cb.between(root.get("followers"), 50000L, 100000L));
                    case "100k+" -> predicates.add(cb.greaterThan(root.get("followers"), 100000L));
                }
            }

            if (req.getTimeRange() != null && !req.getTimeRange().isBlank()) {
                OffsetDateTime now = OffsetDateTime.now();
                if ("TODAY".equalsIgnoreCase(req.getTimeRange())) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.toLocalDate().atStartOfDay().atOffset(now.getOffset())));
                } else if ("LAST_7_DAYS".equalsIgnoreCase(req.getTimeRange())) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.minusDays(7)));
                } else if ("THIS_MONTH".equalsIgnoreCase(req.getTimeRange())) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(now.getOffset())));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String safe(String str) {
        return str != null ? str.replace("\"", "\"\"") : "";
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "TM";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }
}