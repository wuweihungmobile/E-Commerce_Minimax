package com.nextkey.ecommerce.core.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.nextkey.ecommerce.api.dto.notification.NotificationTemplateDto;
import com.nextkey.ecommerce.domain.model.notification.NotificationTemplate;
import com.nextkey.ecommerce.domain.repository.NotificationTemplateRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;

/**
 * NotificationTemplateService 單元測試（Sprint 77 US-001）
 *
 * 涵蓋範圍：
 * - getTemplates：委派 repository 查詢，正確帶入 tenantId
 * - getTemplate：擁有權檢查（本租戶可讀、他租戶拒絕、全域模板 tenantId=null 任何租戶皆可讀）
 * - createTemplate：模板代碼重複拒絕、變量自動提取、明確指定變量覆蓋提取結果
 * - updateTemplate：擁有權檢查（他租戶拒絕）、部分欄位更新、變更內容時重新提取變量
 * - deleteTemplate：軟刪除、擁有權檢查（他租戶拒絕）
 * - renderTemplate：模板不存在、未啟用、正常渲染變量替換
 */
@DisplayName("NotificationTemplateService Tests")
@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @InjectMocks
    private NotificationTemplateService templateService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    private NotificationTemplate buildTemplate(UUID tenantId) {
        return NotificationTemplate.builder()
                .id(TEMPLATE_ID)
                .tenantId(tenantId)
                .templateCode("ORDER_CONFIRMED_EMAIL")
                .notificationType(NotificationTemplate.NotificationType.ORDER_CONFIRMED)
                .channel(NotificationTemplate.NotificationChannel.EMAIL)
                .name("訂單確認信")
                .subject("您的訂單已確認")
                .contentTemplate("Hello {{user_name}}, order {{order_id}} confirmed.")
                .variables(List.of("user_name", "order_id"))
                .isActive(true)
                .priority(0)
                .build();
    }

    // ========== getTemplates ==========

    @Test
    @DisplayName("TC-T001: getTemplates — 以呼叫端傳入的 tenantId 查詢，不外洩其他租戶")
    void getTemplates_delegatesWithTenantId() {
        when(templateRepository.searchTemplates(eq(TENANT_ID), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(buildTemplate(TENANT_ID))));

        NotificationTemplateDto.SearchRequest request = NotificationTemplateDto.SearchRequest.builder()
                .page(0).size(20).build();

        NotificationTemplateDto.ListResponse response = templateService.getTemplates(TENANT_ID, request);

        assertEquals(1, response.getTemplates().size());
        verify(templateRepository).searchTemplates(eq(TENANT_ID), any(), any(), any(), any());
    }

    // ========== getTemplate ==========

    @Test
    @DisplayName("TC-T002: getTemplate — 本租戶模板可正常取得")
    void getTemplate_ownTenant_returnsTemplate() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(buildTemplate(TENANT_ID)));

        NotificationTemplateDto.Response response = templateService.getTemplate(TENANT_ID, TEMPLATE_ID);

        assertEquals(TEMPLATE_ID, response.getId());
    }

    @Test
    @DisplayName("TC-T003: getTemplate — 擁有權驗證：讀取他租戶模板應拋出 BusinessException(E_1007)")
    void getTemplate_otherTenant_throwsBusinessException() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(buildTemplate(OTHER_TENANT_ID)));

        assertThrows(BusinessException.class, () -> templateService.getTemplate(TENANT_ID, TEMPLATE_ID));
    }

    @Test
    @DisplayName("TC-T004: getTemplate — 模板不存在應拋出 BusinessException(E_8003)")
    void getTemplate_notFound_throwsBusinessException() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> templateService.getTemplate(TENANT_ID, TEMPLATE_ID));
    }

    @Test
    @DisplayName("TC-T005: getTemplate — 全域模板（tenantId=null）任何租戶皆可讀取（設計慣例，非缺口）")
    void getTemplate_globalTemplate_accessibleByAnyTenant() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(buildTemplate(null)));

        NotificationTemplateDto.Response response = templateService.getTemplate(TENANT_ID, TEMPLATE_ID);

        assertNotNull(response);
    }

    // ========== createTemplate ==========

    @Test
    @DisplayName("TC-T006: createTemplate — 模板代碼重複時拋出 BusinessException(E_6001)")
    void createTemplate_duplicateCode_throwsBusinessException() {
        when(templateRepository.existsByTemplateCodeAndTenantId("ORDER_CONFIRMED_EMAIL", TENANT_ID))
                .thenReturn(true);

        NotificationTemplateDto.CreateRequest request = NotificationTemplateDto.CreateRequest.builder()
                .templateCode("ORDER_CONFIRMED_EMAIL")
                .notificationType("ORDER_CONFIRMED")
                .channel("EMAIL")
                .name("訂單確認信")
                .contentTemplate("Hello {{user_name}}")
                .build();

        assertThrows(BusinessException.class, () -> templateService.createTemplate(TENANT_ID, USER_ID, request));
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-T007: createTemplate — 未指定 variables 時由 contentTemplate 自動提取")
    void createTemplate_noExplicitVariables_extractsFromContent() {
        when(templateRepository.existsByTemplateCodeAndTenantId(any(), any())).thenReturn(false);
        when(templateRepository.save(any(NotificationTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationTemplateDto.CreateRequest request = NotificationTemplateDto.CreateRequest.builder()
                .templateCode("NEW_CODE")
                .notificationType("ORDER_CONFIRMED")
                .channel("EMAIL")
                .name("Test")
                .contentTemplate("Hi {{user_name}}, your order {{order_id}} is ready.")
                .build();

        NotificationTemplateDto.Response response = templateService.createTemplate(TENANT_ID, USER_ID, request);

        assertEquals(List.of("user_name", "order_id"), response.getVariables());
    }

    @Test
    @DisplayName("TC-T008: createTemplate — 明確指定 variables 時覆蓋自動提取結果")
    void createTemplate_explicitVariables_overridesExtraction() {
        when(templateRepository.existsByTemplateCodeAndTenantId(any(), any())).thenReturn(false);
        when(templateRepository.save(any(NotificationTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationTemplateDto.CreateRequest request = NotificationTemplateDto.CreateRequest.builder()
                .templateCode("NEW_CODE")
                .notificationType("ORDER_CONFIRMED")
                .channel("EMAIL")
                .name("Test")
                .contentTemplate("Hi {{user_name}}")
                .variables(List.of("custom_var"))
                .build();

        NotificationTemplateDto.Response response = templateService.createTemplate(TENANT_ID, USER_ID, request);

        assertEquals(List.of("custom_var"), response.getVariables());
    }

    @Test
    @DisplayName("TC-T009: createTemplate — 建立的模板綁定呼叫端傳入的 tenantId（非跨租戶建立）")
    void createTemplate_bindsToCallerTenant() {
        when(templateRepository.existsByTemplateCodeAndTenantId(any(), any())).thenReturn(false);
        when(templateRepository.save(any(NotificationTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationTemplateDto.CreateRequest request = NotificationTemplateDto.CreateRequest.builder()
                .templateCode("NEW_CODE")
                .notificationType("ORDER_CONFIRMED")
                .channel("EMAIL")
                .name("Test")
                .contentTemplate("Hi")
                .build();

        ArgumentCaptor<NotificationTemplate> captor = ArgumentCaptor.forClass(NotificationTemplate.class);
        templateService.createTemplate(TENANT_ID, USER_ID, request);

        verify(templateRepository).save(captor.capture());
        assertEquals(TENANT_ID, captor.getValue().getTenantId());
        assertEquals(USER_ID, captor.getValue().getCreatedBy());
    }

    // ========== updateTemplate ==========

    @Test
    @DisplayName("TC-T010: updateTemplate — 擁有權驗證：更新他租戶模板應拋出 BusinessException(E_1007)")
    void updateTemplate_otherTenant_throwsBusinessException() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(buildTemplate(OTHER_TENANT_ID)));

        NotificationTemplateDto.UpdateRequest request = NotificationTemplateDto.UpdateRequest.builder()
                .name("竄改名稱").build();

        assertThrows(BusinessException.class,
                () -> templateService.updateTemplate(TENANT_ID, USER_ID, TEMPLATE_ID, request));
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-T011: updateTemplate — 模板不存在應拋出 BusinessException(E_8003)")
    void updateTemplate_notFound_throwsBusinessException() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        NotificationTemplateDto.UpdateRequest request = NotificationTemplateDto.UpdateRequest.builder()
                .name("新名稱").build();

        assertThrows(BusinessException.class,
                () -> templateService.updateTemplate(TENANT_ID, USER_ID, TEMPLATE_ID, request));
    }

    @Test
    @DisplayName("TC-T012: updateTemplate — 僅更新有提供的欄位，未提供欄位維持原值")
    void updateTemplate_partialUpdate_onlyChangesProvidedFields() {
        NotificationTemplate existing = buildTemplate(TENANT_ID);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(NotificationTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationTemplateDto.UpdateRequest request = NotificationTemplateDto.UpdateRequest.builder()
                .name("更新後名稱")
                .build();

        NotificationTemplateDto.Response response =
                templateService.updateTemplate(TENANT_ID, USER_ID, TEMPLATE_ID, request);

        assertEquals("更新後名稱", response.getName());
        assertEquals("您的訂單已確認", response.getSubject(), "未提供的 subject 應維持原值");
        assertEquals("ORDER_CONFIRMED_EMAIL", response.getTemplateCode(), "未提供的 templateCode 應維持原值");
    }

    @Test
    @DisplayName("TC-T013: updateTemplate — 變更 contentTemplate 時重新提取變量")
    void updateTemplate_contentTemplateChanged_reextractsVariables() {
        NotificationTemplate existing = buildTemplate(TENANT_ID);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(NotificationTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationTemplateDto.UpdateRequest request = NotificationTemplateDto.UpdateRequest.builder()
                .contentTemplate("New content with {{new_var}} only")
                .build();

        NotificationTemplateDto.Response response =
                templateService.updateTemplate(TENANT_ID, USER_ID, TEMPLATE_ID, request);

        assertEquals(List.of("new_var"), response.getVariables());
    }

    @Test
    @DisplayName("TC-T014: updateTemplate — 模板代碼變更為他人已使用的代碼時拋出 BusinessException(E_6001)")
    void updateTemplate_duplicateNewCode_throwsBusinessException() {
        NotificationTemplate existing = buildTemplate(TENANT_ID);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
        when(templateRepository.existsByTemplateCodeAndTenantId("ALREADY_TAKEN", TENANT_ID)).thenReturn(true);

        NotificationTemplateDto.UpdateRequest request = NotificationTemplateDto.UpdateRequest.builder()
                .templateCode("ALREADY_TAKEN")
                .build();

        assertThrows(BusinessException.class,
                () -> templateService.updateTemplate(TENANT_ID, USER_ID, TEMPLATE_ID, request));
        verify(templateRepository, never()).save(any());
    }

    // ========== deleteTemplate ==========

    @Test
    @DisplayName("TC-T015: deleteTemplate — 本租戶模板軟刪除成功（設定 deletedAt 且 isActive=false）")
    void deleteTemplate_ownTenant_softDeletes() {
        NotificationTemplate existing = buildTemplate(TENANT_ID);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(NotificationTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        templateService.deleteTemplate(TENANT_ID, TEMPLATE_ID);

        assertNotNull(existing.getDeletedAt());
        assertFalse(existing.getIsActive());
        verify(templateRepository).save(existing);
    }

    @Test
    @DisplayName("TC-T016: deleteTemplate — 擁有權驗證：刪除他租戶模板應拋出 BusinessException(E_1007)")
    void deleteTemplate_otherTenant_throwsBusinessException() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(buildTemplate(OTHER_TENANT_ID)));

        assertThrows(BusinessException.class, () -> templateService.deleteTemplate(TENANT_ID, TEMPLATE_ID));
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-T017: deleteTemplate — 模板不存在應拋出 BusinessException(E_8003)")
    void deleteTemplate_notFound_throwsBusinessException() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> templateService.deleteTemplate(TENANT_ID, TEMPLATE_ID));
    }

    // ========== renderTemplate ==========

    @Test
    @DisplayName("TC-T018: renderTemplate — 模板不存在應拋出 BusinessException(E_8003)")
    void renderTemplate_notFound_throwsBusinessException() {
        when(templateRepository.findByTemplateCodeAndTenantId("UNKNOWN", TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> templateService.renderTemplate(TENANT_ID, "UNKNOWN", Map.of()));
    }

    @Test
    @DisplayName("TC-T019: renderTemplate — 模板未啟用應拋出 BusinessException(E_8001)")
    void renderTemplate_inactive_throwsBusinessException() {
        NotificationTemplate inactive = buildTemplate(TENANT_ID);
        inactive.setIsActive(false);
        when(templateRepository.findByTemplateCodeAndTenantId("ORDER_CONFIRMED_EMAIL", TENANT_ID))
                .thenReturn(Optional.of(inactive));

        assertThrows(BusinessException.class,
                () -> templateService.renderTemplate(TENANT_ID, "ORDER_CONFIRMED_EMAIL", Map.of()));
    }

    @Test
    @DisplayName("TC-T020: renderTemplate — 正常渲染時將變量替換進 subject 與 content")
    void renderTemplate_active_rendersVariables() {
        when(templateRepository.findByTemplateCodeAndTenantId("ORDER_CONFIRMED_EMAIL", TENANT_ID))
                .thenReturn(Optional.of(buildTemplate(TENANT_ID)));

        NotificationTemplateDto.RenderResponse response = templateService.renderTemplate(
                TENANT_ID, "ORDER_CONFIRMED_EMAIL", Map.of("user_name", "小明", "order_id", "ORD-001"));

        assertTrue(response.getContent().contains("小明"));
        assertTrue(response.getContent().contains("ORD-001"));
    }
}
