package com.nextkey.ecommerce.core.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.nextkey.ecommerce.api.dto.ChatDto;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.MessageRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * DEF-382：{@code ChatService} 的「對話找不到或非本人」四處一律改用 M10 專屬的 {@code E_8100}，
 * 不再誤用通用驗證碼 {@code E_9005}（官方定義「缺少必填欄位」，與「資源不存在」語意無關）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DEF-382: ChatService 對話不存在改用 E_8100")
class ChatServiceNotFoundErrorCodeTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatService chatService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @Test
    @DisplayName("sendMessage：對話不存在或非本人 → E_8100")
    void sendMessage_conversationNotFound_throwsE8100() {
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.empty());
        ChatDto.SendMessageRequest request = ChatDto.SendMessageRequest.builder()
                .conversationId(CONVERSATION_ID).content("hi").build();

        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8100);
    }

    @Test
    @DisplayName("getMessages：對話不存在或非本人 → E_8100")
    void getMessages_conversationNotFound_throwsE8100() {
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(USER_ID, CONVERSATION_ID, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8100);
    }

    @Test
    @DisplayName("markAsRead：對話不存在或非本人 → E_8100")
    void markAsRead_conversationNotFound_throwsE8100() {
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.markAsRead(USER_ID, CONVERSATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8100);
    }

    @Test
    @DisplayName("deleteConversation：對話不存在或非本人 → E_8100")
    void deleteConversation_conversationNotFound_throwsE8100() {
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.deleteConversation(USER_ID, CONVERSATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_8100);
    }
}
