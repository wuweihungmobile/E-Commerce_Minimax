package com.nextkey.ecommerce.api.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.ShippingTemplateDto;
import com.nextkey.ecommerce.core.logistics.ShippingTemplateService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 運費模板 REST API
 * US-006 M11: 商家設定固定運費 / 免運門檻模板
 */
@Slf4j
@RestController
@RequestMapping("/v2/shipping-templates")
@RequiredArgsConstructor
public class ShippingTemplateController {

    private final ShippingTemplateService shippingTemplateService;

    /**
     * 建立運費模板
     */
    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    public ResponseEntity<ApiResponse<ShippingTemplateDto.TemplateResponse>> createTemplate(
            @Valid @RequestBody ShippingTemplateDto.CreateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.info("Create shipping template: tenantId={}, name={}, feeType={}", tenantId, request.getName(), request.getFeeType());
        ShippingTemplateDto.TemplateResponse response = shippingTemplateService.createTemplate(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Shipping template created", response));
    }

    /**
     * 取得租戶的所有運費模板
     */
    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<List<ShippingTemplateDto.TemplateResponse>>> getTemplates() {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<ShippingTemplateDto.TemplateResponse> response = shippingTemplateService.getTemplates(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 更新運費模板
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ShippingTemplateDto.TemplateResponse>> updateTemplate(
            @PathVariable UUID id,
            @RequestBody ShippingTemplateDto.UpdateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.info("Update shipping template: id={}, tenantId={}", id, tenantId);
        ShippingTemplateDto.TemplateResponse response = shippingTemplateService.updateTemplate(id, tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Shipping template updated", response));
    }

    /**
     * 刪除運費模板
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.info("Delete shipping template: id={}, tenantId={}", id, tenantId);
        shippingTemplateService.deleteTemplate(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Shipping template deleted", null));
    }

    /**
     * 計算運費
     */
    @GetMapping("/{id}/calculate-fee")
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<ShippingTemplateDto.FeeCalculationResponse>> calculateFee(
            @PathVariable UUID id,
            @RequestParam BigDecimal orderAmount) {
        log.info("Calculate fee: templateId={}, orderAmount={}", id, orderAmount);
        ShippingTemplateDto.FeeCalculationResponse response = shippingTemplateService.calculateFee(id, orderAmount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
