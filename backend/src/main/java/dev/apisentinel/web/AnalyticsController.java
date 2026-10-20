package dev.apisentinel.web;

import dev.apisentinel.dto.AnalyticsDtos;
import dev.apisentinel.security.ApiUserPrincipal;
import dev.apisentinel.service.AnalyticsService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;
    public AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/overview")
    public AnalyticsDtos.OverviewResponse overview(@AuthenticationPrincipal ApiUserPrincipal user,
        @RequestParam(defaultValue = "24h") String range, @RequestParam(required = false) UUID endpointId) {
        return analytics.overview(user.id(), range, endpointId);
    }
}
