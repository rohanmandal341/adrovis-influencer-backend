package com.adrovis.influencer.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.adrovis.influencer.dto.AgencyResponse;
import com.adrovis.influencer.dto.ApiResponse;
import com.adrovis.influencer.service.AgencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "adrovisBearer")
@RequestMapping("/api/v1/agencies")
@RequiredArgsConstructor
public class AgencyController {

    private final AgencyService agencyService;
    /** Reads the existing agency list behind the current Admin-only policy. */

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgencyResponse>>> listAgencies() {
        return ResponseEntity.ok(ApiResponse.ok(agencyService.getAllAgencies()));
    }
}
