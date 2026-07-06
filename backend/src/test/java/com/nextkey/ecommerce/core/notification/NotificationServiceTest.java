package com.nextkey.ecommerce.core.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.NotificationRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.mq.NotificationProducerService;
import com.nextkey.ecommerce.shared.exception.BusinessException;

/**
 * NotificationService 單元測試（Sprint 77 US-001）
 *
 * 涵蓋範圍：
 * - sendNotification：使用者不存在、偏好停用跳過、正常發送、recipient 推導、MQ 失敗重試計數
 * - broadcastNotification：依租戶篩選、單一使用者入佇失敗不中斷整體
 * - getUserNotifications：unreadOnly 分支、分頁上限
 * - markAsRead：指定 ID（含他人通知不受影響的擁有權驗證）、全部標記
 * - deleteNotification：擁有權檢查（本人可刪、他人 403）、不存在 404
 * - getUnreadCount
 */
@DisplayName("NotificationService Tests")
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationProducerService notificationProducerService;
    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    @InjectMocks
    private NotificationService notificationService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();

    private User buildUser() {
        return User.builder()
                .id(USER_ID)
                .email("buyer@example.com")
                .phone("0912345678")
                .role(User.UserRole.BUYER)
                .build();
    }

    private NotificationDto.SendRequest buildSendRequest() {
        return NotificationDto.SendRequest.builder()
                .userId(USER_ID)
                .notificationType(NotificationDto.NotificationType.ORDER_CONFIRMED)
                .title("訂單已確認")
                .content("您的訂單已確認")
                .build();
    }

    // ========== sendNotification ==========

    @Test
    @DisplayName("TC-S001: sendNotification — 使用者不存在應拋出 BusinessException(E_1006)")
    void sendNotification_userNotFound_throwsBusinessException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        NotificationDto.SendRequest request = buildSendRequest();

        assertThrows(BusinessException.class, () -> notificationService.sendNotification(request));
        verify(notificationProducerService, never()).sendToQueue(any());
    }

    @Test
    @DisplayName("TC-S002: sendNotification — 使用者偏好停用該 type+channel 時跳過發送")
    void sendNotification_preferenceDisabled_skipsSending() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(buildUser()));
        when(notificationPreferenceService.isEnabled(
                eq(USER_ID), eq(Notification.NotificationType.ORDER_CONFIRMED), eq(Notification.NotificationChannel.IN_APP)))
                .thenReturn(false);

        NotificationDto.NotificationResponse response = notificationService.sendNotification(buildSendRequest());

        assertFalse(response.getIsSent());
        assertEquals("Skipped by user preference", response.getErrorMessage());
        verify(notificationProducerService, never()).sendToQueue(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-S003: sendNotification — 正常流程建立記錄並送入 MQ 佇列")
    void sendNotification_validRequest_savesAndQueues() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(buildUser()));
        when(notificationPreferenceService.isEnabled(any(), any(), any())).thenReturn(true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(NOTIFICATION_ID);
            return n;
        });

        NotificationDto.NotificationResponse response = notificationService.sendNotification(buildSendRequest());

        assertNotNull(response);
        assertEquals(USER_ID, response.getUserId());
        assertEquals("IN_APP", response.getChannel());
        verify(notificationProducerService, times(1)).sendToQueue(any(NotificationDto.SendRequest.class));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertFalse(captor.getValue().getIsSent());
        assertFalse(captor.getValue().getIsRead());
    }

    @Test
    @DisplayName("TC-S004: sendNotification — channel 為 EMAIL 且未指定 recipient 時，以使用者 email 作為收件人")
    void sendNotification_emailChannelNoRecipient_derivesFromUserEmail() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(buildUser()));
        when(notificationPreferenceService.isEnabled(any(), any(), eq(Notification.NotificationChannel.EMAIL)))
                .thenReturn(true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto.SendRequest request = NotificationDto.SendRequest.builder()
                .userId(USER_ID)
                .notificationType(NotificationDto.NotificationType.PAYMENT_SUCCESS)
                .title("付款成功")
                .content("您的付款已成功")
                .channel(NotificationDto.Channel.EMAIL)
                .build();

        NotificationDto.NotificationResponse response = notificationService.sendNotification(request);

        assertEquals("buyer@example.com", response.getRecipient());
    }

    @Test
    @DisplayName("TC-S005: sendNotification — MQ 入佇失敗時記錄錯誤訊息、遞增重試次數並拋出例外")
    void sendNotification_queueFails_recordsErrorAndRethrows() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(buildUser()));
        when(notificationPreferenceService.isEnabled(any(), any(), any())).thenReturn(true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        RuntimeException queueError = new RuntimeException("Redis unavailable");
        org.mockito.Mockito.doThrow(queueError).when(notificationProducerService).sendToQueue(any());

        NotificationDto.SendRequest request = buildSendRequest();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> notificationService.sendNotification(request));
        assertEquals("Redis unavailable", thrown.getMessage());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        Notification finalSaved = captor.getValue();
        assertEquals("Redis unavailable", finalSaved.getErrorMessage());
        assertEquals(1, finalSaved.getRetryCount());
    }

    // ========== broadcastNotification ==========

    @Test
    @DisplayName("TC-B001: broadcastNotification — 指定 tenantId 時僅廣播給該租戶使用者")
    void broadcastNotification_withTenantId_onlyTargetsTenantUsers() {
        UUID tenantId = UUID.randomUUID();
        User tenantUser = User.builder().id(UUID.randomUUID()).tenantId(tenantId).build();
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(tenantUser));

        NotificationDto.BroadcastRequest request = NotificationDto.BroadcastRequest.builder()
                .notificationType(NotificationDto.NotificationType.SYSTEM_ANNOUNCEMENT)
                .title("公告")
                .content("系統維護通知")
                .tenantId(tenantId)
                .build();

        int count = notificationService.broadcastNotification(request);

        assertEquals(1, count);
        verify(userRepository, never()).findAll();
        verify(notificationProducerService, times(1)).sendToQueue(any());
    }

    @Test
    @DisplayName("TC-B002: broadcastNotification — tenantId 為 null 時廣播給全平台使用者")
    void broadcastNotification_nullTenantId_targetsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().id(UUID.randomUUID()).build(),
                User.builder().id(UUID.randomUUID()).build()));

        NotificationDto.BroadcastRequest request = NotificationDto.BroadcastRequest.builder()
                .notificationType(NotificationDto.NotificationType.SYSTEM_ANNOUNCEMENT)
                .title("公告")
                .content("全平台維護通知")
                .build();

        int count = notificationService.broadcastNotification(request);

        assertEquals(2, count);
        verify(userRepository, never()).findByTenantId(any());
    }

    @Test
    @DisplayName("TC-B003: broadcastNotification — 單一使用者入佇失敗不中斷整體廣播，其餘使用者仍計入成功數")
    void broadcastNotification_oneUserQueueFails_othersStillCounted() {
        User user1 = User.builder().id(UUID.randomUUID()).build();
        User user2 = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));
        org.mockito.Mockito.doThrow(new RuntimeException("queue full"))
                .doNothing()
                .when(notificationProducerService).sendToQueue(any());

        NotificationDto.BroadcastRequest request = NotificationDto.BroadcastRequest.builder()
                .notificationType(NotificationDto.NotificationType.SYSTEM_ANNOUNCEMENT)
                .title("公告")
                .content("內容")
                .build();

        int count = notificationService.broadcastNotification(request);

        assertEquals(1, count);
        verify(notificationProducerService, times(2)).sendToQueue(any());
    }

    // ========== getUserNotifications ==========

    @Test
    @DisplayName("TC-G001: getUserNotifications — unreadOnly=true 時僅查詢未讀通知")
    void getUserNotifications_unreadOnlyTrue_queriesUnreadOnly() {
        when(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(0);

        notificationService.getUserNotifications(USER_ID, 0, 20, true);

        verify(notificationRepository).findByUserIdAndIsReadFalseOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class));
        verify(notificationRepository, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("TC-G002: getUserNotifications — unreadOnly=false 時查詢全部通知")
    void getUserNotifications_unreadOnlyFalse_queriesAll() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(0);

        notificationService.getUserNotifications(USER_ID, 0, 20, false);

        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class));
        verify(notificationRepository, never()).findByUserIdAndIsReadFalseOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("TC-G003: getUserNotifications — size 超過上限 50 時應截斷")
    void getUserNotifications_sizeExceedsMax_cappedAt50() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(0);

        notificationService.getUserNotifications(USER_ID, 0, 999, false);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(USER_ID), captor.capture());
        assertEquals(50, captor.getValue().getPageSize());
    }

    // ========== markAsRead ==========

    @Test
    @DisplayName("TC-M001: markAsRead — 指定通知 ID 且屬於本人時標記為已讀")
    void markAsRead_ownNotification_marksAsRead() {
        Notification own = Notification.builder().id(NOTIFICATION_ID).userId(USER_ID).isRead(false).build();
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(own));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(0);

        NotificationDto.MarkReadRequest request = NotificationDto.MarkReadRequest.builder()
                .notificationIds(List.of(NOTIFICATION_ID))
                .build();

        notificationService.markAsRead(USER_ID, request);

        assertTrue(own.getIsRead());
        assertNotNull(own.getReadAt());
        verify(notificationRepository, times(1)).save(own);
    }

    @Test
    @DisplayName("TC-M002: markAsRead — 擁有權驗證：指定 ID 屬於他人時不應被標記已讀（IDOR 防護）")
    void markAsRead_otherUsersNotification_isNotModified() {
        Notification othersNotification = Notification.builder()
                .id(NOTIFICATION_ID).userId(OTHER_USER_ID).isRead(false).build();
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(othersNotification));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(0);

        NotificationDto.MarkReadRequest request = NotificationDto.MarkReadRequest.builder()
                .notificationIds(List.of(NOTIFICATION_ID))
                .build();

        notificationService.markAsRead(USER_ID, request);

        assertFalse(othersNotification.getIsRead(), "他人的通知不應被目前使用者標記為已讀");
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("TC-M003: markAsRead — 未指定 notificationIds 時標記全部未讀通知為已讀")
    void markAsRead_noIdsSpecified_marksAllUnread() {
        Notification n1 = Notification.builder().id(UUID.randomUUID()).userId(USER_ID).isRead(false).build();
        Notification n2 = Notification.builder().id(UUID.randomUUID()).userId(USER_ID).isRead(false).build();
        when(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n1, n2)));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(0);

        NotificationDto.MarkReadRequest request = new NotificationDto.MarkReadRequest();

        notificationService.markAsRead(USER_ID, request);

        assertTrue(n1.getIsRead());
        assertTrue(n2.getIsRead());
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    // ========== deleteNotification ==========

    @Test
    @DisplayName("TC-D001: deleteNotification — 本人通知可正常刪除")
    void deleteNotification_ownNotification_deletesSuccessfully() {
        Notification own = Notification.builder().id(NOTIFICATION_ID).userId(USER_ID).build();
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(own));

        notificationService.deleteNotification(USER_ID, NOTIFICATION_ID);

        verify(notificationRepository, times(1)).delete(own);
    }

    @Test
    @DisplayName("TC-D002: deleteNotification — 擁有權驗證：刪除他人通知應拋出 BusinessException(E_1007)")
    void deleteNotification_otherUsersNotification_throwsBusinessException() {
        Notification othersNotification = Notification.builder().id(NOTIFICATION_ID).userId(OTHER_USER_ID).build();
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(othersNotification));

        assertThrows(BusinessException.class,
                () -> notificationService.deleteNotification(USER_ID, NOTIFICATION_ID));
        verify(notificationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("TC-D003: deleteNotification — 通知不存在時拋出 BusinessException(E_8002)")
    void deleteNotification_notFound_throwsBusinessException() {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> notificationService.deleteNotification(USER_ID, NOTIFICATION_ID));
    }

    // ========== getUnreadCount ==========

    @Test
    @DisplayName("TC-U001: getUnreadCount — 正確回傳未讀計數")
    void getUnreadCount_returnsCorrectCount() {
        when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(7);

        NotificationDto.UnreadCountResponse response = notificationService.getUnreadCount(USER_ID);

        assertEquals(USER_ID, response.getUserId());
        assertEquals(7, response.getUnreadCount());
    }
}
