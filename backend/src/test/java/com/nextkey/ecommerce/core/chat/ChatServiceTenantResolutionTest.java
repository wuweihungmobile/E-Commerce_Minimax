package com.nextkey.ecommerce.core.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.nextkey.ecommerce.api.dto.ChatDto;
import com.nextkey.ecommerce.domain.model.chat.Conversation;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.MessageRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * ChatService 對話租戶推導行為驗證（Sprint 25 US-003 / AI-802）。
 *
 * <p>驗證 {@code createConversation} 寫入的 {@code Conversation.tenantId} 是否依
 * {@code resolveTenantId} 規則推導，並與 V56 migration 回填規則一致：
 * <ol>
 *   <li>有 listing 且查得租戶 → 取 listing 租戶（優先於 order）</li>
 *   <li>無 listing（或 listing 查無）但 order 查得租戶 → 取 order 租戶</li>
 *   <li>純 DIRECT（無關聯）或關聯實體皆查無 → 退回 System Tenant</li>
 * </ol>
 *
 * <p>依使用者指示：Listing/Order 僅以 Mockito mock 提供 {@code getTenantId()}，
 * 不建構完整實體；斷言以 AssertJ 為主。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("US-003: ChatService 對話租戶推導（resolveTenantId）")
class ChatServiceTenantResolutionTest {

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
    private static final UUID RECIPIENT_ID = UUID.randomUUID();
    private static final UUID LISTING_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID LISTING_TENANT = UUID.randomUUID();
    private static final UUID ORDER_TENANT = UUID.randomUUID();
    /** System Tenant ID（V9 / V56 回填退路），須與 ChatService.SYSTEM_TENANT_ID 一致。 */
    private static final UUID SYSTEM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        // 無既有對話 → 走新建流程（lenient：DEF-118 併發搶占測試會覆寫為多次呼叫的序列回傳值）
        org.mockito.Mockito.lenient()
                .when(conversationRepository.findByInitiatorIdAndRecipientIdAndIsActiveTrue(USER_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());
        // saveAndFlush 回傳傳入的 conversation，供後續 toConversationResponse 使用
        // （lenient：DEF-118 併發搶占測試會覆寫為拋出 DataIntegrityViolationException）
        org.mockito.Mockito.lenient()
                .when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("有 listing 且查得租戶 → 取 listing 租戶，且不查 order（listing 優先）")
    void createConversation_withListingHavingTenant_usesListingTenantAndSkipsOrder() {
        Listing listing = mock(Listing.class);
        when(listing.getTenantId()).thenReturn(LISTING_TENANT);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        // 同時帶 orderId，用以證明 listing 優先於 order
        chatService.createConversation(request(LISTING_ID, ORDER_ID), USER_ID);

        assertThat(savedConversation().getTenantId()).isEqualTo(LISTING_TENANT);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("無 listing 但 order 查得租戶 → 取 order 租戶")
    void createConversation_withoutListingButOrderHasTenant_usesOrderTenant() {
        Order order = mock(Order.class);
        when(order.getTenantId()).thenReturn(ORDER_TENANT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        chatService.createConversation(request(null, ORDER_ID), USER_ID);

        assertThat(savedConversation().getTenantId()).isEqualTo(ORDER_TENANT);
    }

    @Test
    @DisplayName("listing 查無但 order 查得租戶 → 退回 order 租戶")
    void createConversation_withListingNotFound_fallsBackToOrderTenant() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.empty());
        Order order = mock(Order.class);
        when(order.getTenantId()).thenReturn(ORDER_TENANT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        chatService.createConversation(request(LISTING_ID, ORDER_ID), USER_ID);

        assertThat(savedConversation().getTenantId()).isEqualTo(ORDER_TENANT);
    }

    @Test
    @DisplayName("純 DIRECT（無 listing/order）→ 退回 System Tenant")
    void createConversation_withoutListingOrOrder_usesSystemTenant() {
        chatService.createConversation(request(null, null), USER_ID);

        assertThat(savedConversation().getTenantId()).isEqualTo(SYSTEM_TENANT);
    }

    @Test
    @DisplayName("listing 與 order 皆查無 → 退回 System Tenant")
    void createConversation_withListingAndOrderNotFound_usesSystemTenant() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.empty());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        chatService.createConversation(request(LISTING_ID, ORDER_ID), USER_ID);

        assertThat(savedConversation().getTenantId()).isEqualTo(SYSTEM_TENANT);
    }

    @Test
    @DisplayName("DEF-118：併發搶占失敗（另一請求已建立同一對 active 對話）→ 回傳搶贏的既有對話，而非拋出原始例外")
    void createConversation_concurrentClaimLost_returnsRaceWinner() {
        Conversation raceWinner = Conversation.builder()
                .id(UUID.randomUUID()).initiatorId(USER_ID).recipientId(RECIPIENT_ID)
                .tenantId(SYSTEM_TENANT).isActive(true).initiatorUnreadCount(0).recipientUnreadCount(0)
                .conversationType(Conversation.ConversationType.DIRECT)
                .build();
        when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));
        when(conversationRepository.findByInitiatorIdAndRecipientIdAndIsActiveTrue(USER_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty(), Optional.of(raceWinner));

        ChatDto.ConversationResponse response = chatService.createConversation(request(null, null), USER_ID);

        assertThat(response.getConversationId()).isEqualTo(raceWinner.getId());
        verify(messageRepository, never()).save(any());
    }

    private ChatDto.CreateConversationRequest request(final UUID listingId, final UUID orderId) {
        return ChatDto.CreateConversationRequest.builder()
                .recipientId(RECIPIENT_ID)
                .listingId(listingId)
                .orderId(orderId)
                .build();
    }

    /** 擷取寫入 DB 的 Conversation（建立流程在無初始訊息時恰呼叫一次 saveAndFlush）。 */
    private Conversation savedConversation() {
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
