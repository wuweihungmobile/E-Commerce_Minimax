package com.nextkey.ecommerce.core.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.domain.model.notification.NotificationHistory;
import com.nextkey.ecommerce.domain.repository.NotificationHistoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;

@DisplayName("NotificationHistoryService Tests")
@ExtendWith(MockitoExtension.class)
class NotificationHistoryServiceTest {

    @Mock
    private NotificationHistoryRepository historyRepository;

    @InjectMocks
    private NotificationHistoryService historyService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID HISTORY_ID = UUID.randomUUID();

    // ========== createHistory ==========

    @Test
    @DisplayName("TC-H001: createHistory — 正常建立通知歷史記錄")
    void createHistory_validInput_savesAndReturns() {
        NotificationHistory saved = NotificationHistory.builder()
                .id(HISTORY_ID)
                .userId(USER_ID)
                .tenantId(TENANT_ID)
                .notificationType("ORDER_CONFIRMED")
                .channel("IN_APP")
                .title("訂單已確認")
                .body("您的訂單 #1234 已確認")
                .isRead(false)
                .build();

        when(historyRepository.save(any(NotificationHistory.class))).thenReturn(saved);

        NotificationHistory result = historyService.createHistory(
                USER_ID, TENANT_ID, "ORDER_CONFIRMED", "IN_APP", "訂單已確認", "您的訂單 #1234 已確認");

        assertNotNull(result);
        assertEquals(USER_ID, result.getUserId());
        assertEquals("ORDER_CONFIRMED", result.getNotificationType());
        assertFalse(result.getIsRead());

        ArgumentCaptor<NotificationHistory> captor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals("IN_APP", captor.getValue().getChannel());
    }

    @Test
    @DisplayName("TC-H002: createHistory — channel 為 null 時預設 IN_APP")
    void createHistory_nullChannel_defaultsToInApp() {
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        historyService.createHistory(USER_ID, TENANT_ID, "NEW_MESSAGE", null, "新訊息", "您有一條新訊息");

        ArgumentCaptor<NotificationHistory> captor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals("IN_APP", captor.getValue().getChannel());
    }

    // ========== getHistory ==========

    @Test
    @DisplayName("TC-H003: getHistory — 回傳分頁通知歷史與未讀計數")
    void getHistory_validUserId_returnsPagedHistory() {
        NotificationHistory h1 = NotificationHistory.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .notificationType("ORDER_CONFIRMED")
                .channel("IN_APP")
                .title("訂單確認")
                .body("內容")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(historyRepository.findByUserIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(h1)));
        when(historyRepository.countUnreadByUserId(USER_ID)).thenReturn(1);

        NotificationDto.HistoryListResponse result = historyService.getHistory(USER_ID, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getUnreadCount());
        assertEquals("訂單確認", result.getItems().get(0).getTitle());
        assertFalse(result.getItems().get(0).getIsRead());
    }

    @Test
    @DisplayName("TC-H004: getHistory — size 超過上限 50 時應截斷")
    void getHistory_sizeExceedsMax_cappedAt50() {
        when(historyRepository.findByUserIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(historyRepository.countUnreadByUserId(any(UUID.class))).thenReturn(0);

        historyService.getHistory(USER_ID, 0, 100);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByUserIdOrderByCreatedAtDesc(any(UUID.class), captor.capture());
        assertEquals(50, captor.getValue().getPageSize());
    }

    // ========== markOneAsRead ==========

    @Test
    @DisplayName("TC-H005: markOneAsRead — 未讀記錄標記為已讀")
    void markOneAsRead_unreadHistory_setsIsReadTrue() {
        NotificationHistory history = NotificationHistory.builder()
                .id(HISTORY_ID)
                .userId(USER_ID)
                .notificationType("NEW_MESSAGE")
                .channel("IN_APP")
                .title("新訊息")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.of(history));
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto.HistoryResponse result = historyService.markOneAsRead(USER_ID, HISTORY_ID);

        assertTrue(result.getIsRead());
        assertNotNull(result.getReadAt());
        verify(historyRepository).save(history);
    }

    @Test
    @DisplayName("TC-H006: markOneAsRead — 已讀記錄不重複更新")
    void markOneAsRead_alreadyRead_doesNotSaveAgain() {
        NotificationHistory history = NotificationHistory.builder()
                .id(HISTORY_ID)
                .userId(USER_ID)
                .notificationType("NEW_MESSAGE")
                .channel("IN_APP")
                .title("新訊息")
                .isRead(true)
                .readAt(Instant.now().minusSeconds(60))
                .createdAt(Instant.now().minusSeconds(120))
                .build();

        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.of(history));

        NotificationDto.HistoryResponse result = historyService.markOneAsRead(USER_ID, HISTORY_ID);

        assertTrue(result.getIsRead());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-H007: markOneAsRead — 他人記錄應拋出 BusinessException")
    void markOneAsRead_differentUser_throwsBusinessException() {
        NotificationHistory history = NotificationHistory.builder()
                .id(HISTORY_ID)
                .userId(UUID.randomUUID()) // 不同 userId
                .notificationType("NEW_MESSAGE")
                .channel("IN_APP")
                .title("新訊息")
                .isRead(false)
                .build();

        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.of(history));

        assertThrows(BusinessException.class, () -> historyService.markOneAsRead(USER_ID, HISTORY_ID));
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-H008: markOneAsRead — 記錄不存在應拋出 BusinessException")
    void markOneAsRead_notFound_throwsBusinessException() {
        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> historyService.markOneAsRead(USER_ID, HISTORY_ID));
    }

    // ========== getUnreadCount ==========

    @Test
    @DisplayName("TC-H009: getUnreadCount — 正確回傳未讀計數")
    void getUnreadCount_returnsCorrectCount() {
        when(historyRepository.countUnreadByUserId(USER_ID)).thenReturn(5);

        NotificationDto.HistoryUnreadCountResponse result = historyService.getUnreadCount(USER_ID);

        assertEquals(USER_ID, result.getUserId());
        assertEquals(5, result.getUnreadCount());
    }
}
