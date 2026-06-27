package com.nextkey.ecommerce.core.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.ShippingTemplateDto;
import com.nextkey.ecommerce.domain.model.logistics.ShippingTemplate;
import com.nextkey.ecommerce.domain.repository.ShippingTemplateRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * ShippingTemplateService 單元測試（US-006）
 *
 * 測試範圍：
 * - TC-ST001: calculateFee() FIXED 類型 → 直接回傳 fixedAmount
 * - TC-ST002: calculateFee() FREE_THRESHOLD，訂單金額 >= 門檻 → 免運費（0）
 * - TC-ST003: calculateFee() FREE_THRESHOLD，訂單金額 < 門檻 → 回傳 fixedAmount
 * - TC-ST004: calculateFee() 模板不存在 → 拋出 BusinessException E_7504
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingTemplateService 單元測試（US-006）")
class ShippingTemplateServiceTest {

    private static final BigDecimal FIXED_AMOUNT = BigDecimal.valueOf(80);
    private static final BigDecimal FREE_THRESHOLD = BigDecimal.valueOf(500);

    @Mock
    private ShippingTemplateRepository shippingTemplateRepository;

    @Mock
    private TenantRepository tenantRepository;

    private ShippingTemplateService shippingTemplateService;

    @BeforeEach
    void setUp() {
        shippingTemplateService = new ShippingTemplateService(shippingTemplateRepository, tenantRepository);
    }

    @Test
    @DisplayName("TC-ST001: calculateFee() FIXED 類型 → 直接回傳 fixedAmount")
    void calculateFee_fixedType_returnsFixedAmount() {
        UUID templateId = UUID.randomUUID();
        ShippingTemplate template = ShippingTemplate.builder()
                .feeType(ShippingTemplate.FeeType.FIXED)
                .fixedAmount(FIXED_AMOUNT)
                .build();
        when(shippingTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        ShippingTemplateDto.FeeCalculationResponse result =
                shippingTemplateService.calculateFee(templateId, BigDecimal.valueOf(200));

        assertThat(result.getShippingFee()).isEqualByComparingTo(FIXED_AMOUNT);
        assertThat(result.getFeeType()).isEqualTo("FIXED");
    }

    @Test
    @DisplayName("TC-ST002: calculateFee() FREE_THRESHOLD，訂單金額 >= 門檻 → 免運費（0）")
    void calculateFee_freeThreshold_orderMeetsThreshold_returnsZero() {
        UUID templateId = UUID.randomUUID();
        ShippingTemplate template = ShippingTemplate.builder()
                .feeType(ShippingTemplate.FeeType.FREE_THRESHOLD)
                .fixedAmount(FIXED_AMOUNT)
                .freeThreshold(FREE_THRESHOLD)
                .build();
        when(shippingTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        ShippingTemplateDto.FeeCalculationResponse result =
                shippingTemplateService.calculateFee(templateId, FREE_THRESHOLD);

        assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("TC-ST003: calculateFee() FREE_THRESHOLD，訂單金額 < 門檻 → 回傳 fixedAmount")
    void calculateFee_freeThreshold_orderBelowThreshold_returnsFixedAmount() {
        UUID templateId = UUID.randomUUID();
        ShippingTemplate template = ShippingTemplate.builder()
                .feeType(ShippingTemplate.FeeType.FREE_THRESHOLD)
                .fixedAmount(FIXED_AMOUNT)
                .freeThreshold(FREE_THRESHOLD)
                .build();
        when(shippingTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        ShippingTemplateDto.FeeCalculationResponse result =
                shippingTemplateService.calculateFee(templateId, BigDecimal.valueOf(499));

        assertThat(result.getShippingFee()).isEqualByComparingTo(FIXED_AMOUNT);
    }

    @Test
    @DisplayName("TC-ST004: calculateFee() 模板不存在 → 拋出 BusinessException E_7504")
    void calculateFee_templateNotFound_throwsBusinessException() {
        UUID templateId = UUID.randomUUID();
        when(shippingTemplateRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingTemplateService.calculateFee(templateId, BigDecimal.valueOf(200)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.E_7504);
                });
    }
}
