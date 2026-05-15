package com.nextkey.ecommerce.core.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.notification.NotificationTemplateDto;
import com.nextkey.ecommerce.domain.model.notification.NotificationTemplate;
import com.nextkey.ecommerce.domain.model.notification.NotificationTemplate.NotificationChannel;
import com.nextkey.ecommerce.domain.model.notification.NotificationTemplate.NotificationType;
import com.nextkey.ecommerce.domain.repository.NotificationTemplateRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * 取得模板列表
     */
    @Transactional(readOnly = true)
    public NotificationTemplateDto.ListResponse getTemplates(
            UUID tenantId, NotificationTemplateDto.SearchRequest request) {

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        NotificationType notificationType = null;
        NotificationChannel channel = null;

        if (request.getNotificationType() != null && !request.getNotificationType().isEmpty()) {
            notificationType = NotificationType.valueOf(request.getNotificationType());
        }
        if (request.getChannel() != null && !request.getChannel().isEmpty()) {
            channel = NotificationChannel.valueOf(request.getChannel());
        }

        Page<NotificationTemplate> page = templateRepository.searchTemplates(
                tenantId, notificationType, channel, request.getIsActive(), pageable);

        List<NotificationTemplateDto.Response> responses = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return NotificationTemplateDto.ListResponse.builder()
                .templates(responses)
                .page(request.getPage())
                .size(request.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    /**
     * 取得模板詳情
     */
    @Transactional(readOnly = true)
    public NotificationTemplateDto.Response getTemplate(UUID tenantId, UUID templateId) {
        NotificationTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Template not found"));

        if (template.getTenantId() != null && !template.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "Access denied");
        }

        return toResponse(template);
    }

    /**
     * 建立模板
     */
    @Transactional
    public NotificationTemplateDto.Response createTemplate(UUID tenantId, UUID userId,
            NotificationTemplateDto.CreateRequest request) {

        if (templateRepository.existsByTemplateCodeAndTenantId(request.getTemplateCode(), tenantId)) {
            throw new BusinessException(ErrorCode.E_6001, "Template code already exists");
        }

        List<String> variables = extractVariables(request.getContentTemplate());
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            variables = request.getVariables();
        }

        NotificationTemplate template = NotificationTemplate.builder()
                .tenantId(tenantId)
                .templateCode(request.getTemplateCode())
                .notificationType(NotificationType.valueOf(request.getNotificationType()))
                .channel(NotificationChannel.valueOf(request.getChannel()))
                .name(request.getName())
                .subject(request.getSubject())
                .contentTemplate(request.getContentTemplate())
                .variables(variables)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        template = templateRepository.save(template);

        log.info("NotificationTemplate created: id={}, templateCode={}, tenantId={}",
                template.getId(), template.getTemplateCode(), tenantId);

        return toResponse(template);
    }

    /**
     * 更新模板
     */
    @Transactional
    public NotificationTemplateDto.Response updateTemplate(UUID tenantId, UUID userId,
            UUID templateId, NotificationTemplateDto.UpdateRequest request) {

        NotificationTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Template not found"));

        if (template.getTenantId() != null && !template.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "Access denied");
        }

        if (request.getTemplateCode() != null && !request.getTemplateCode().equals(template.getTemplateCode())) {
            if (templateRepository.existsByTemplateCodeAndTenantId(request.getTemplateCode(), tenantId)) {
                throw new BusinessException(ErrorCode.E_6001, "Template code already exists");
            }
            template.setTemplateCode(request.getTemplateCode());
        }

        if (request.getNotificationType() != null) {
            template.setNotificationType(NotificationType.valueOf(request.getNotificationType()));
        }
        if (request.getChannel() != null) {
            template.setChannel(NotificationChannel.valueOf(request.getChannel()));
        }
        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getSubject() != null) {
            template.setSubject(request.getSubject());
        }
        if (request.getContentTemplate() != null) {
            template.setContentTemplate(request.getContentTemplate());
            template.setVariables(extractVariables(request.getContentTemplate()));
        }
        if (request.getVariables() != null) {
            template.setVariables(request.getVariables());
        }
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }
        if (request.getPriority() != null) {
            template.setPriority(request.getPriority());
        }

        template.setUpdatedBy(userId);
        template = templateRepository.save(template);

        log.info("NotificationTemplate updated: id={}, templateCode={}", template.getId(), template.getTemplateCode());

        return toResponse(template);
    }

    /**
     * 刪除模板 (軟刪除)
     */
    @Transactional
    public void deleteTemplate(UUID tenantId, UUID templateId) {
        NotificationTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Template not found"));

        if (template.getTenantId() != null && !template.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "Access denied");
        }

        template.setDeletedAt(Instant.now());
        template.setIsActive(false);
        templateRepository.save(template);

        log.info("NotificationTemplate deleted: id={}", templateId);
    }

    /**
     * 渲染模板內容
     */
    @Transactional(readOnly = true)
    public NotificationTemplateDto.RenderResponse renderTemplate(
            UUID tenantId, String templateCode, Map<String, String> variables) {

        NotificationTemplate template = templateRepository
                .findByTemplateCodeAndTenantId(templateCode, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Template not found"));

        if (!template.getIsActive()) {
            throw new BusinessException(ErrorCode.E_8001, "Template is not active");
        }

        String content = render(template.getContentTemplate(), variables);
        String subject = template.getSubject() != null
                ? render(template.getSubject(), variables)
                : null;

        return NotificationTemplateDto.RenderResponse.builder()
                .subject(subject)
                .content(content)
                .variables(variables)
                .build();
    }

    // ========== Helper Methods ==========

    private NotificationTemplateDto.Response toResponse(NotificationTemplate template) {
        return NotificationTemplateDto.Response.builder()
                .id(template.getId())
                .tenantId(template.getTenantId())
                .templateCode(template.getTemplateCode())
                .notificationType(template.getNotificationType().name())
                .channel(template.getChannel().name())
                .name(template.getName())
                .subject(template.getSubject())
                .contentTemplate(template.getContentTemplate())
                .variables(template.getVariables())
                .isActive(template.getIsActive())
                .priority(template.getPriority())
                .createdBy(template.getCreatedBy())
                .updatedBy(template.getUpdatedBy())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private List<String> extractVariables(String template) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        return matcher.results()
                .map(mr -> mr.group(1))
                .distinct()
                .collect(Collectors.toList());
    }

    private String render(String template, Map<String, String> variables) {
        if (template == null || variables == null) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}