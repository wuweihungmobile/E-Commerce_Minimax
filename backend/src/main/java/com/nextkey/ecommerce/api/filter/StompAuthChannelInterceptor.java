package com.nextkey.ecommerce.api.filter;

import java.util.List;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP CONNECT 時從 Authorization header 驗證 JWT，
 * 並將 UserPrincipal 注入 StompHeaderAccessor，
 * 使後續 @MessageMapping 方法可透過 Principal 取得使用者身份。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenService jwtTokenService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
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
}
