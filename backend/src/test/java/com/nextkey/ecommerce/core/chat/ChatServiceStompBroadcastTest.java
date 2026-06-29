package com.nextkey.ecommerce.core.chat;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
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
}
