package com.adrovis.influencer.collector.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.collector.dto.CreatorFilterRequest;
import com.adrovis.influencer.collector.dto.CreatorTableResponse;
import com.adrovis.influencer.collector.entity.CollectorTeamMember;
import com.adrovis.influencer.collector.repository.CollectorTeamMemberRepository;
import com.adrovis.influencer.collector.service.CollectorCreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/collector/creators")
@RequiredArgsConstructor
public class CollectorCreatorController {

    private final CollectorCreatorService creatorService;
    private final CollectorTeamMemberRepository memberRepo;

    @GetMapping
    public ResponseEntity<CreatorTableResponse> getCreators(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String niche,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID addedBy,
            @RequestParam(required = false) String followerRange,
            @RequestParam(required = false) String timeRange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        CreatorFilterRequest req = CreatorFilterRequest.builder()
                .search(search)
                .niche(niche)
                .location(location)
                .addedBy(addedBy)
                .followerRange(followerRange)
                .timeRange(timeRange)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .build();

        return ResponseEntity.ok(creatorService.getCreatorsTable(req));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String niche,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID addedBy,
            @RequestParam(required = false) String followerRange,
            @RequestParam(required = false) String timeRange) {

        CreatorFilterRequest req = CreatorFilterRequest.builder()
                .search(search)
                .niche(niche)
                .location(location)
                .addedBy(addedBy)
                .followerRange(followerRange)
                .timeRange(timeRange)
                .build();

        String csvData = creatorService.exportCreatorsCsv(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=creators_export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    @GetMapping("/members")
    public ResponseEntity<List<CollectorTeamMember>> getMembers() {
        return ResponseEntity.ok(memberRepo.findByActiveTrue());
    }
}