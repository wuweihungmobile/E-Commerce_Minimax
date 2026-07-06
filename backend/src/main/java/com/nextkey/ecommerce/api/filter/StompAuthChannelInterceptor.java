package com.nextkey.ecommerce.api.filter;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP CONNECT 時從 Authorization header 驗證 JWT，
 * 並將 UserPrincipal 注入 StompHeaderAccessor，
 * 使後續 @MessageMapping 方法可透過 Principal 取得使用者身份。
 *
 * <p>DEF-035：SUBSCRIBE 時額外檢查 {@code /queue/conversations/{id}/messages} 目的地，
 * 僅放行對話參與者（initiator/recipient），避免任何連線者竊聽他人對話即時訊息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_QUEUE_PATTERN =
            Pattern.compile("^/queue/conversations/([0-9a-fA-F-]{36})/messages$");

    private final JwtTokenService jwtTokenService;
    private final ConversationRepository conversationRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return handleSubscribe(message, accessor);
        }

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[STOMP] CONNECT without valid Authorization header");
            return message;
        }

        String token = authHeader.substring(7);
        if (!jwtTokenService.validateToken(token)) {
            log.warn("[STOMP] CONNECT with invalid JWT token");
            return message;
        }

        try {
            UUID userId = jwtTokenService.getUserId(token);
            String email = jwtTokenService.getEmail(token);
            String role = jwtTokenService.getRole(token);
            String tenantId = jwtTokenService.getTenantId(token);

            UserPrincipal principal = new UserPrincipal(userId, email, role, tenantId);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(role)));
            accessor.setUser(auth);

            log.debug("[STOMP] CONNECT authenticated: userId={}, tenantId={}", userId, tenantId);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[STOMP] CONNECT JWT parse error: {}", e.getMessage());
        }

        return message;
    }

    /**
     * DEF-035：SUBSCRIBE 目的地為 {@code /queue/conversations/{id}/messages} 時，
     * 僅放行對話參與者（initiator/recipient）；其餘目的地不受影響。
     */
    private Message<?> handleSubscribe(final Message<?> message, final StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = CONVERSATION_QUEUE_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        UUID conversationId;
        try {
            conversationId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException e) {
            log.warn("[STOMP] SUBSCRIBE rejected: invalid conversationId in destination={}", destination);
            return null;
        }

        UUID subscriberId = extractUserId(accessor);
        if (subscriberId == null) {
            log.warn("[STOMP] SUBSCRIBE rejected: unauthenticated principal, destination={}", destination);
            return null;
        }

        boolean isParticipant = conversationRepository.findById(conversationId)
                .map(c -> subscriberId.equals(c.getInitiatorId()) || subscriberId.equals(c.getRecipientId()))
                .orElse(false);

        if (!isParticipant) {
            log.warn("[STOMP] SUBSCRIBE rejected: userId={} is not a participant of conversationId={}",
                    subscriberId, conversationId);
            return null;
        }

        return message;
    }

    private UUID extractUserId(final StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (!(user instanceof UsernamePasswordAuthenticationToken auth)) {
            return null;
        }
        if (!(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal.getUserId();
    }
}
