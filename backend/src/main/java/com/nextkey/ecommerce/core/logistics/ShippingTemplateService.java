package com.nextkey.ecommerce.core.logistics;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.ShippingTemplateDto;
import com.nextkey.ecommerce.domain.model.logistics.ShippingTemplate;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.ShippingTemplateRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 運費模板服務
 * 支援 CRUD 操作及運費計算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingTemplateService {

    private final ShippingTemplateRepository shippingTemplateRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public ShippingTemplateDto.TemplateResponse createTemplate(UUID tenantId, ShippingTemplateDto.CreateRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        ShippingTemplate template = ShippingTemplate.builder()
                .tenant(tenant)
                .name(request.getName())
                .feeType(request.getFeeType())
                .fixedAmount(request.getFixedAmount())
                .freeThreshold(request.getFreeThreshold())
                .build();

        template = shippingTemplateRepository.save(template);
        log.info("Shipping template created: id={}, tenantId={}", template.getId(), tenantId);
        return toResponse(template);
    }

    @Transactional(readOnly = true)
    public List<ShippingTemplateDto.TemplateResponse> getTemplates(UUID tenantId) {
        return shippingTemplateRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShippingTemplateDto.TemplateResponse updateTemplate(UUID id, UUID tenantId, ShippingTemplateDto.UpdateRequest request) {
        ShippingTemplate template = shippingTemplateRepository.findById(id)
                .filter(t -> tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7504, "Shipping template not found"));

        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getFixedAmount() != null) {
            template.setFixedAmount(request.getFixedAmount());
        }
        if (request.getFreeThreshold() != null) {
            template.setFreeThreshold(request.getFreeThreshold());
        }

        template = shippingTemplateRepository.save(template);
        log.info("Shipping template updated: id={}", id);
        return toResponse(template);
    }

    @Transactional
    public void deleteTemplate(UUID id, UUID tenantId) {
        ShippingTemplate template = shippingTemplateRepository.findById(id)
                .filter(t -> tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7504, "Shipping template not found"));

        shippingTemplateRepository.delete(template);
        log.info("Shipping template deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public ShippingTemplateDto.FeeCalculationResponse calculateFee(UUID templateId, BigDecimal orderAmount) {
        ShippingTemplate template = shippingTemplateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7504, "Shipping template not found"));

        BigDecimal shippingFee = computeShippingFee(template, orderAmount);

        return ShippingTemplateDto.FeeCalculationResponse.builder()
                .templateId(templateId)
                .feeType(template.getFeeType().name())
                .orderAmount(orderAmount)
                .shippingFee(shippingFee)
                .build();
    }

    private BigDecimal computeShippingFee(ShippingTemplate template, BigDecimal orderAmount) {
        BigDecimal fixedAmount = template.getFixedAmount() != null ? template.getFixedAmount() : BigDecimal.ZERO;
        if (template.getFeeType() == ShippingTemplate.FeeType.FIXED) {
            return fixedAmount;
        }
        // FREE_THRESHOLD: 訂單金額 >= 免運門檻時免運費
        BigDecimal threshold = template.getFreeThreshold() != null ? template.getFreeThreshold() : BigDecimal.ZERO;
        return orderAmount.compareTo(threshold) >= 0 ? BigDecimal.ZERO : fixedAmount;
    }

    private ShippingTemplateDto.TemplateResponse toResponse(ShippingTemplate template) {
        return ShippingTemplateDto.TemplateResponse.builder()
                .id(template.getId())
                .tenantId(template.getTenantId())
                .name(template.getName())
                .feeType(template.getFeeType())
                .fixedAmount(template.getFixedAmount())
                .freeThreshold(template.getFreeThreshold())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
