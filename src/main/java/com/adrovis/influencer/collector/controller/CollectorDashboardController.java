package com.adrovis.influencer.collector.controller;

import com.adrovis.influencer.collector.dto.DashboardOverviewDto;
import com.adrovis.influencer.collector.service.CollectorDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/collector/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CollectorDashboardController {

    private final CollectorDashboardService dashboardService;

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewDto> getOverview(
            @RequestParam(name = "timeRange", defaultValue = "LAST_7_DAYS") String timeRange) {
        return ResponseEntity.ok(dashboardService.getOverview(timeRange));
    }
}