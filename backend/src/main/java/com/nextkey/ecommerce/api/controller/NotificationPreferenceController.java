package com.nextkey.ecommerce.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.notification.NotificationPreferenceDto;
import com.nextkey.ecommerce.core.notification.NotificationPreferenceService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知用戶偏好設定 API
 * Sprint 19 US-005: M09 通知偏好設定
 */
@Slf4j
@RestController
@RequestMapping("/v2/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    /**
     * 取得登入用戶的所有通知偏好。
     * 無明確設定的 type × channel 組合預設回傳 enabled = true。
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationPreferenceDto.PreferenceItem>>> getPreferences() {
        UUID userId = TenantContext.getCurrentUser();
        log.info("Get notification preferences: userId={}", userId);
        List<NotificationPreferenceDto.PreferenceItem> prefs = preferenceService.getPreferences(userId);
        return ResponseEntity.ok(ApiResponse.success("Notification preferences retrieved", prefs));
    }

    /**
     * Upsert 單一通知偏好。
     * 不存在則建立；存在則更新。
     */
    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceDto.PreferenceItem>> upsertPreference(
            @Valid @RequestBody NotificationPreferenceDto.UpsertRequest request) {
        UUID userId = TenantContext.getCurrentUser();
        log.info("Upsert notification preference: userId={} type={} channel={} enabled={}",
                userId, request.getNotificationType(), request.getChannel(), request.getEnabled());
        NotificationPreferenceDto.PreferenceItem result = preferenceService.upsertPreference(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Notification preference updated", result));
    }
}
