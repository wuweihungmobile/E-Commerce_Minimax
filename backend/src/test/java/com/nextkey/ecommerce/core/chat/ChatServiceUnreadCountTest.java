package com.nextkey.ecommerce.core.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.nextkey.ecommerce.api.dto.ChatDto;
import com.nextkey.ecommerce.domain.model.chat.Conversation;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.MessageRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * DEF-383：{@code ChatDto.ConversationResponse.unreadCount} 依瀏覽者角色正確填值。
 *
 * <p>修復前 {@code ChatService.toConversationResponse} 一律回傳 {@code unreadCount(null)}，
 * builder 註解寫「Will be filled by caller」但 {@code createConversation}／
 * {@code getUserConversations} 兩個呼叫點都沒有填值。本測試驗證修復後依瀏覽者是
 * conversation 的 initiator 還是 recipient，回傳對應角色的未讀計數欄位，而非恆為 null。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DEF-383: ChatService 未讀計數依角色填值")
class ChatServiceUnreadCountTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatService chatService;

    private static final UUID INITIATOR_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();

    @Test
    @DisplayName("createConversation 回傳既有對話，瀏覽者為 initiator → unreadCount 取 initiatorUnreadCount（非 null）")
    void createConversation_existingConversation_fillsInitiatorUnreadCount() {
        Conversation existing = Conversation.builder()
                .conversationType(Conversation.ConversationType.DIRECT)
                .initiatorId(INITIATOR_ID)
                .recipientId(RECIPIENT_ID)
                .initiatorUnreadCount(3)
                .recipientUnreadCount(7)
                .isActive(true)
                .build();
        existing.setId(UUID.randomUUID());
        when(conversationRepository.findByInitiatorIdAndRecipientIdAndIsActiveTrue(INITIATOR_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));

        ChatDto.CreateConversationRequest request = ChatDto.CreateConversationRequest.builder()
                .recipientId(RECIPIENT_ID)
                .build();

        ChatDto.ConversationResponse response = chatService.createConversation(request, INITIATOR_ID);

        assertThat(response.getUnreadCount())
                .as("呼叫者是 initiator，應回傳 initiatorUnreadCount 而非恆為 null 或誤取 recipientUnreadCount")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("getUserConversations：同一次列表中，身為 initiator 與身為 recipient 的對話各自回傳正確角色未讀數")
    void getUserConversations_mixedRoles_fillsPerConversationRoleAwareUnreadCount() {
        UUID viewerId = INITIATOR_ID;
        UUID otherUserId = UUID.randomUUID();

        // 對話一：viewer 是 initiator，應取 initiatorUnreadCount=2（非 recipientUnreadCount=9）
        Conversation asInitiator = Conversation.builder()
                .conversationType(Conversation.ConversationType.DIRECT)
                .initiatorId(viewerId)
                .recipientId(otherUserId)
                .initiatorUnreadCount(2)
                .recipientUnreadCount(9)
                .isActive(true)
                .build();
        asInitiator.setId(UUID.randomUUID());

        // 對話二：viewer 是 recipient，應取 recipientUnreadCount=5（非 initiatorUnreadCount=1）
        Conversation asRecipient = Conversation.builder()
                .conversationType(Conversation.ConversationType.DIRECT)
                .initiatorId(otherUserId)
                .recipientId(viewerId)
                .initiatorUnreadCount(1)
                .recipientUnreadCount(5)
                .isActive(true)
                .build();
        asRecipient.setId(UUID.randomUUID());

        Page<Conversation> page = new PageImpl<>(List.of(asInitiator, asRecipient));
        when(conversationRepository.findByUserIdOrderByLastMessageAtDesc(org.mockito.ArgumentMatchers.eq(viewerId),
                org.mockito.ArgumentMatchers.any())).thenReturn(page);
        when(conversationRepository.countUnreadConversations(viewerId)).thenReturn(2);

        ChatDto.ConversationListResponse result = chatService.getUserConversations(viewerId, 0, 20);

        assertThat(result.getConversations()).hasSize(2);
        assertThat(result.getConversations().get(0).getUnreadCount())
                .as("第一筆 viewer 是 initiator，應取 initiatorUnreadCount")
                .isEqualTo(2);
        assertThat(result.getConversations().get(1).getUnreadCount())
                .as("第二筆 viewer 是 recipient，應取 recipientUnreadCount")
                .isEqualTo(5);
    }
}
