package com.nextkey.ecommerce.core.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.domain.model.notification.NotificationHistory;
import com.nextkey.ecommerce.domain.repository.NotificationHistoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知歷史服務（Sprint 20 US-005）
 * 面向用戶端的已讀/未讀歷史查詢，與發送追蹤（NotificationService）分離
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private final NotificationHistoryRepository historyRepository;

    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 寫入通知歷史（由 NotificationService.sendNotification 呼叫）
     */
    @Transactional
    public NotificationHistory createHistory(UUID userId, UUID tenantId,
            String notificationType, String channel, String title, String body) {
        NotificationHistory history = NotificationHistory.builder()
                .userId(userId)
                .tenantId(tenantId)
                .notificationType(notificationType)
                .channel(channel != null ? channel : "IN_APP")
                .title(title)
                .body(body)
                .isRead(false)
                .build();
        return historyRepository.save(history);
    }

    /**
     * 取得通知歷史列表（分頁）
     */
    @Transactional(readOnly = true)
    public NotificationDto.HistoryListResponse getHistory(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        Page<NotificationHistory> historyPage =
                historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);

        int unreadCount = historyRepository.countUnreadByUserId(userId);

        List<NotificationDto.HistoryResponse> items = historyPage.getContent().stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());

        return NotificationDto.HistoryListResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * 標記單筆通知為已讀
     */
    @Transactional
    public NotificationDto.HistoryResponse markOneAsRead(UUID userId, UUID historyId) {
        NotificationHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8002, "Notification history not found"));

        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1007, "Cannot access notification of other user");
        }

        if (!Boolean.TRUE.equals(history.getIsRead())) {
            history.setIsRead(true);
            history.setReadAt(Instant.now());
            history = historyRepository.save(history);
            log.info("Notification history marked as read: id={}, userId={}", historyId, userId);
        }

        return toHistoryResponse(history);
    }

    /**
     * 取得未讀計數
     */
    @Transactional(readOnly = true)
    public NotificationDto.HistoryUnreadCountResponse getUnreadCount(UUID userId) {
        int count = historyRepository.countUnreadByUserId(userId);
        return NotificationDto.HistoryUnreadCountResponse.builder()
                .userId(userId)
                .unreadCount(count)
                .build();
    }

    private NotificationDto.HistoryResponse toHistoryResponse(NotificationHistory h) {
        return NotificationDto.HistoryResponse.builder()
                .notificationId(h.getId())
                .type(h.getNotificationType())
                .channel(h.getChannel())
                .title(h.getTitle())
                .body(h.getBody())
                .isRead(h.getIsRead())
                .readAt(h.getReadAt())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
