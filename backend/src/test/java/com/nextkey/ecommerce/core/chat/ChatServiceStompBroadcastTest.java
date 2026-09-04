package com.nextkey.ecommerce.core.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.nextkey.ecommerce.api.dto.ChatDto;
import com.nextkey.ecommerce.domain.model.chat.Conversation;
import com.nextkey.ecommerce.domain.model.chat.Message;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.MessageRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * ChatService STOMP 廣播行為驗證（TC-STOMP-C001~C003）
 *
 * <p>TC-STOMP-C001: 發送訊息後呼叫 convertAndSend 到正確 destination
 * <p>TC-STOMP-C002: destination 格式為 /queue/conversations/{id}/messages
 * <p>TC-STOMP-C003: payload 為 MessageResponse（非 null）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TC-STOMP: ChatService STOMP 廣播行為")
class ChatServiceStompBroadcastTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatService chatService;

    @Captor
    private ArgumentCaptor<ChatDto.MessageResponse> payloadCaptor;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = Conversation.builder()
                .initiatorId(USER_ID)
                .recipientId(UUID.randomUUID())
                .initiatorUnreadCount(0)
                .recipientUnreadCount(0)
                .isActive(true)
                .build();
        conversation.setId(CONVERSATION_ID);

        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID))
                .thenReturn(Optional.of(conversation));

        Message saved = Message.builder()
                .conversation(conversation)
                .senderId(USER_ID)
                .messageType(Message.MessageType.TEXT)
                .content("Hello STOMP")
                .isRead(false)
                .build();
        saved.setId(MESSAGE_ID);

        when(messageRepository.save(any(Message.class))).thenReturn(saved);
        // Sprint 125（DEF-056）：對話狀態更新改為 conversationRepository.recordNewMessage
        // 原子 UPDATE，sendMessage 不再呼叫 save(Conversation)，故不再 stub 它。
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("TC-STOMP-C001/C002/C003: 發送訊息後廣播到正確 destination，payload 為 MessageResponse")
    void sendMessage_shouldBroadcastViaStompToCorrectDestination() {
        ChatDto.SendMessageRequest request = ChatDto.SendMessageRequest.builder()
                .conversationId(CONVERSATION_ID)
                .content("Hello STOMP")
                .build();

        chatService.sendMessage(USER_ID, request);

        String expectedDest = "/queue/conversations/" + CONVERSATION_ID + "/messages";
        verify(messagingTemplate).convertAndSend(eq(expectedDest), any(ChatDto.MessageResponse.class));
    }

    @Test
    @DisplayName("TC-STOMP-C004 (DEF-012): 廣播 payload 的 conversationId 不為 null（由關聯取得，非唯讀鏡像欄位）")
    void sendMessage_broadcastPayload_shouldCarryNonNullConversationId() {
        ChatDto.SendMessageRequest request = ChatDto.SendMessageRequest.builder()
                .conversationId(CONVERSATION_ID)
                .content("Hello STOMP")
                .build();

        chatService.sendMessage(USER_ID, request);

        verify(messagingTemplate).convertAndSend(any(String.class), payloadCaptor.capture());
        ChatDto.MessageResponse payload = payloadCaptor.getValue();
        // DEF-012: 新建 + save 的 Message in-memory 實例唯讀 conversationId 欄位為 null，
        // 必須由 conversation 關聯取得，否則 mobile 等以 payload 解析的 client 會拿到 null。
        assertThat(payload.getConversationId())
                .as("廣播 payload conversationId 不應為 null")
                .isEqualTo(CONVERSATION_ID);
    }

    // ========== Sprint 125（DEF-056）：未讀計數改為原子 UPDATE，驗證派送的遞增方向正確 ==========

    @Test
    @DisplayName("DEF-056: initiator 發訊息 → recordNewMessage 遞增 recipient 未讀（initiatorDelta=0, recipientDelta=1）")
    void sendMessage_senderIsInitiator_incrementsRecipientUnreadOnly() {
        ChatDto.SendMessageRequest request = ChatDto.SendMessageRequest.builder()
                .conversationId(CONVERSATION_ID)
                .content("Hello STOMP")
                .build();

        // conversation 於 setUp() 以 USER_ID 作為 initiatorId 建立，本測試以 USER_ID 發訊息
        chatService.sendMessage(USER_ID, request);

        verify(conversationRepository).recordNewMessage(
                eq(CONVERSATION_ID), eq(MESSAGE_ID), eq("Hello STOMP"), any(), eq(0), eq(1));
    }

    @Test
    @DisplayName("DEF-056: recipient 發訊息 → recordNewMessage 遞增 initiator 未讀（initiatorDelta=1, recipientDelta=0）")
    void sendMessage_senderIsRecipient_incrementsInitiatorUnreadOnly() {
        UUID recipientUserId = conversation.getRecipientId();
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, recipientUserId))
                .thenReturn(Optional.of(conversation));
        Message savedByRecipient = Message.builder()
                .conversation(conversation)
                .senderId(recipientUserId)
                .messageType(Message.MessageType.TEXT)
                .content("Reply from recipient")
                .isRead(false)
                .build();
        savedByRecipient.setId(UUID.randomUUID());
        when(messageRepository.save(any(Message.class))).thenReturn(savedByRecipient);

        ChatDto.SendMessageRequest request = ChatDto.SendMessageRequest.builder()
                .conversationId(CONVERSATION_ID)
                .content("Reply from recipient")
                .build();

        chatService.sendMessage(recipientUserId, request);

        verify(conversationRepository).recordNewMessage(
                eq(CONVERSATION_ID), eq(savedByRecipient.getId()), eq("Reply from recipient"), any(), eq(1), eq(0));
    }
}
