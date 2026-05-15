package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.notification.NotificationTemplateDto;
import com.nextkey.ecommerce.core.notification.NotificationTemplateService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知模板 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class NotificationTemplateController {

    private final NotificationTemplateService templateService;

    /**
     * 取得模板列表
     */
    @GetMapping("/notification-templates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationTemplateDto.ListResponse>> getTemplates(
            @RequestParam(required = false) String notificationType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = TenantContext.getCurrentTenant();

        NotificationTemplateDto.SearchRequest request = NotificationTemplateDto.SearchRequest.builder()
                .notificationType(notificationType)
                .channel(channel)
                .isActive(isActive)
                .page(page)
                .size(size)
                .build();

        NotificationTemplateDto.ListResponse response = templateService.getTemplates(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得模板詳情
     */
    @GetMapping("/notification-templates/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationTemplateDto.Response>> getTemplate(
            @PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        NotificationTemplateDto.Response response = templateService.getTemplate(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 建立模板 (Dashboard)
     */
    @PostMapping("/dashboard/notification-templates")
    @PreAuthorize("hasAuthority('notification_template:create')")
    public ResponseEntity<ApiResponse<NotificationTemplateDto.Response>> createTemplate(
            @Valid @RequestBody NotificationTemplateDto.CreateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID userId = TenantContext.getCurrentUser();

        log.info("Create notification template: templateCode={}, tenantId={}",
                request.getTemplateCode(), tenantId);

        NotificationTemplateDto.Response response = templateService.createTemplate(tenantId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Template created", response));
    }

    /**
     * 更新模板 (Dashboard)
     */
    @PutMapping("/dashboard/notification-templates/{id}")
    @PreAuthorize("hasAuthority('notification_template:update')")
    public ResponseEntity<ApiResponse<NotificationTemplateDto.Response>> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody NotificationTemplateDto.UpdateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID userId = TenantContext.getCurrentUser();

        log.info("Update notification template: id={}, tenantId={}", id, tenantId);

        NotificationTemplateDto.Response response = templateService.updateTemplate(tenantId, userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Template updated", response));
    }

    /**
     * 刪除模板 (Dashboard)
     */
    @DeleteMapping("/dashboard/notification-templates/{id}")
    @PreAuthorize("hasAuthority('notification_template:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();

        log.info("Delete notification template: id={}, tenantId={}", id, tenantId);

        templateService.deleteTemplate(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Template deleted", null));
    }

    /**
     * 渲染模板預覽
     */
    @PostMapping("/notification-templates/render")
    @PreAuthorize("hasAuthority('notification_template:read')")
    public ResponseEntity<ApiResponse<NotificationTemplateDto.RenderResponse>> renderTemplate(
            @RequestBody NotificationTemplateDto.RenderRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        log.info("Render notification template: templateCode={}, tenantId={}",
                request.getTemplateCode(), tenantId);

        NotificationTemplateDto.RenderResponse response = templateService.renderTemplate(
                tenantId, request.getTemplateCode(), request.getVariables());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}