package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.nextkey.ecommerce.domain.model.chat.Conversation;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

/**
 * StompAuthChannelInterceptor SUBSCRIBE 授權驗證（DEF-035）。
 *
 * <p>Sprint 75 探查發現：{@code StompAuthChannelInterceptor} 僅在 CONNECT 時驗證 JWT，
 * 對後續 SUBSCRIBE frame 完全未檢查目的地是否為呼叫者本人有權存取的對話，任何連線者（含
 * 匿名／他人帳號）皆可訂閱任意 {@code /queue/conversations/{id}/messages} 竊聽他人對話即時訊息，
 * 與 REST 層 {@code ChatService.getMessages} 的 participant-scoping 保護不一致。
 *
 * <p>TC-STOMP-SUB-001~006 證明修復前後行為差異：非參與者/匿名者訂閱應被攔截（回傳 null 阻斷），
 * 參與者（initiator/recipient）訂閱應正常放行，且與對話無關的目的地不受影響。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DEF-035: StompAuthChannelInterceptor SUBSCRIBE 授權")
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private ConversationRepository conversationRepository;

    private StompAuthChannelInterceptor interceptor;

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID INITIATOR_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();
    private static final UUID OUTSIDER_ID = UUID.randomUUID();

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtTokenService, conversationRepository);

        conversation = Conversation.builder()
                .initiatorId(INITIATOR_ID)
                .recipientId(RECIPIENT_ID)
                .isActive(true)
                .build();
        conversation.setId(CONVERSATION_ID);
    }

    @Test
    @DisplayName("TC-STOMP-SUB-001: initiator 訂閱自己的對話 → 放行")
    void subscribe_initiatorOwnConversation_isAllowed() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        Message<?> result = interceptor.preSend(subscribeMessage(CONVERSATION_ID, authenticatedAs(INITIATOR_ID)),
                mock(org.springframework.messaging.MessageChannel.class));

        assertThat(result).as("對話參與者（initiator）訂閱應放行").isNotNull();
    }

    @Test
    @DisplayName("TC-STOMP-SUB-002: recipient 訂閱自己的對話 → 放行")
    void subscribe_recipientOwnConversation_isAllowed() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        Message<?> result = interceptor.preSend(subscribeMessage(CONVERSATION_ID, authenticatedAs(RECIPIENT_ID)),
                mock(org.springframework.messaging.MessageChannel.class));

        assertThat(result).as("對話參與者（recipient）訂閱應放行").isNotNull();
    }

    @Test
    @DisplayName("TC-STOMP-SUB-003（DEF-035 紅燈證明）: 非參與者訂閱他人對話 → 必須攔截")
    void subscribe_nonParticipant_mustBeRejected() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        Message<?> result = interceptor.preSend(subscribeMessage(CONVERSATION_ID, authenticatedAs(OUTSIDER_ID)),
                mock(org.springframework.messaging.MessageChannel.class));

        assertThat(result)
                .as("非參與者不應能訂閱他人對話的即時訊息 queue（否則可竊聽他人對話，DEF-035）")
                .isNull();
    }

    @Test
    @DisplayName("TC-STOMP-SUB-004（DEF-035 紅燈證明）: 未認證（匿名）連線訂閱任意對話 → 必須攔截")
    void subscribe_unauthenticated_mustBeRejected() {
        // 未認證直接於取得 userId 階段短路攔截，不會查詢 conversationRepository（無需 stub）。
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/queue/conversations/" + CONVERSATION_ID + "/messages");
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        Message<?> subscribeMsg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(subscribeMsg, mock(org.springframework.messaging.MessageChannel.class));

        assertThat(result).as("匿名連線不應能訂閱任何對話的即時訊息 queue（DEF-035）").isNull();
    }

    @Test
    @DisplayName("TC-STOMP-SUB-005: 訂閱不存在的對話 → 攔截")
    void subscribe_conversationNotFound_isRejected() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        Message<?> result = interceptor.preSend(subscribeMessage(CONVERSATION_ID, authenticatedAs(INITIATOR_ID)),
                mock(org.springframework.messaging.MessageChannel.class));

        assertThat(result).as("對話不存在時應攔截，避免資訊洩漏").isNull();
    }

    @Test
    @DisplayName("TC-STOMP-SUB-006: 與對話 queue 無關的目的地不受影響（不誤傷其他訂閱）")
    void subscribe_unrelatedDestination_isUnaffected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/system-announcements");
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        Message<?> subscribeMsg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(subscribeMsg, mock(org.springframework.messaging.MessageChannel.class));

        assertThat(result).as("非對話 queue 的訂閱不應被此授權檢查攔截").isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────

    private Message<?> subscribeMessage(final UUID conversationId, final UsernamePasswordAuthenticationToken auth) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/queue/conversations/" + conversationId + "/messages");
        accessor.setSessionId("session-1");
        accessor.setUser(auth);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(final UUID userId) {
        UserPrincipal principal = new UserPrincipal(userId, "user@example.com", "ROLE_BUYER", UUID.randomUUID().toString());
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
    }
}
