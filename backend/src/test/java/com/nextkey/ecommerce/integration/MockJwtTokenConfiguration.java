package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 測試配置 - Mock JwtTokenService
 * 讓測試中的 mock JWT token (test-token-*) 可以通過驗證
 */
@TestConfiguration
public class MockJwtTokenConfiguration {

    @Bean
    @Primary
    public JwtTokenService jwtTokenService() {
        JwtTokenService mockService = Mockito.mock(JwtTokenService.class);

        // Mock validateToken: test-token-* 都視為有效
        when(mockService.validateToken(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return token != null && token.startsWith("test-token-");
        });

        // Mock getUserId: 從 test-token-{userId}-{role} 提取 userId
        when(mockService.getUserId(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token != null && token.startsWith("test-token-")) {
                // 格式: test-token-{userId}-{role}
                String[] parts = token.split("-");
                if (parts.length >= 3) {
                    return java.util.UUID.fromString(parts[2]);
                }
            }
            return java.util.UUID.randomUUID();
        });

        // Mock getEmail
        when(mockService.getEmail(anyString())).thenReturn("test@example.com");

        // Mock getRole: 從 test-token-{userId}-{role} 提取 role
        when(mockService.getRole(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token != null && token.startsWith("test-token-")) {
                String[] parts = token.split("-");
                if (parts.length >= 4) {
                    return parts[3]; // role
                }
            }
            return "STORE_OWNER";
        });

        // Mock getTenantId
        when(mockService.getTenantId(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token != null && token.startsWith("test-token-")) {
                String[] parts = token.split("-");
                if (parts.length >= 5) {
                    return parts[4]; // tenantId
                }
            }
            return java.util.UUID.randomUUID().toString();
        });

        return mockService;
    }
}