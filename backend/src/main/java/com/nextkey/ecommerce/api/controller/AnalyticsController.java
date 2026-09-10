package com.nextkey.ecommerce.api.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.AnalyticsDto;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.core.analytics.AnalyticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分析 REST API
 *
 * <p>Sprint 153（Sprint 150 §7 範圍外項目）：修正過時的「Mock Implementation」Javadoc，
 * 見 {@link AnalyticsService} 類別註解說明，純文件修正，無行為變更。
 */
@Slf4j
@RestController
@RequestMapping("/v2/dashboard")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * 取得儀表板統計
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ResponseEntity<ApiResponse<AnalyticsDto.DashboardStats>> getDashboardStats() {
        AnalyticsDto.DashboardStats stats = analyticsService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 取得營收統計
     */
    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ResponseEntity<ApiResponse<AnalyticsDto.RevenueStats>> getRevenueStats(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "DAY") String granularity) {

        AnalyticsDto.AnalyticsRequest request = AnalyticsDto.AnalyticsRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .granularity(granularity)
                .build();

        AnalyticsDto.RevenueStats stats = analyticsService.getRevenueStats(request);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 取得訂單統計
     */
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ResponseEntity<ApiResponse<AnalyticsDto.OrderStats>> getOrderStats() {
        AnalyticsDto.OrderStats stats = analyticsService.getOrderStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 取得 Listing 統計
     */
    @GetMapping("/listings")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ResponseEntity<ApiResponse<AnalyticsDto.ListingStats>> getListingStats() {
        AnalyticsDto.ListingStats stats = analyticsService.getListingStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 取得最近活動
     */
    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ResponseEntity<ApiResponse<AnalyticsDto.RecentActivity>> getRecentActivity(
            @RequestParam(defaultValue = "20") int limit) {
        AnalyticsDto.RecentActivity activity = analyticsService.getRecentActivity(limit);
        return ResponseEntity.ok(ApiResponse.success(activity));
    }
}
