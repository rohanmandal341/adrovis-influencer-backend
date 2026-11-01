package com.adrovis.influencer.controller;

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
@RequestMapping("/api/v1/agencies")
@RequiredArgsConstructor
public class AgencyController {

    private final AgencyService agencyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgencyResponse>>> listAgencies() {
        return ResponseEntity.ok(ApiResponse.ok(agencyService.getAllAgencies()));
    }
}
