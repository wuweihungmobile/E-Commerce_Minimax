package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.core.cms.media.MediaService;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.model.user.Permission;
import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.infrastructure.redis.RedisLockService;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisCommands;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 整合測試配置
 * 使用 Mock Redis 和 Mock Service 來避免需要真實的外部服務連接
 */
@Slf4j
@TestConfiguration
public class IntegrationTestConfiguration {

    // 使用 AtomicReference 來共享 mock connection（因為 @Bean 方法呼叫順序不確定）
    private final AtomicReference<RedisConnection> connectionRef = new AtomicReference<>();
    private final AtomicReference<RedisCommands> commandsRef = new AtomicReference<>();

    /**
     * 初始化 Redis connection mocks
     */
    private synchronized void initializeRedisMocks() {
        if (connectionRef.get() == null) {
            RedisConnection connection = Mockito.mock(RedisConnection.class);
            RedisCommands commands = Mockito.mock(RedisCommands.class);

            when(connection.isPipelined()).thenReturn(false);
            when(connection.commands()).thenReturn(commands);
            // Mock key commands - 使用正確的返回類型
            when(commands.del(any(byte[][].class))).thenReturn(1L);
            when(commands.expire(any(byte[].class), anyLong())).thenReturn(true);
            when(commands.ttl(any(byte[].class))).thenReturn(-1L);
            when(commands.pExpire(any(byte[].class), anyLong())).thenReturn(true);
            when(commands.pTtl(any(byte[].class))).thenReturn(-1L);
            when(commands.exists(any(byte[].class))).thenReturn(false);
            // commands.type() 返回 DataType
            when(commands.type(any(byte[].class))).thenReturn(org.springframework.data.redis.connection.DataType.NONE);
            // commands.keys() 返回 Set<byte[]>
            when(commands.keys(any(byte[].class))).thenReturn(java.util.Collections.emptySet());

            connectionRef.set(connection);
            commandsRef.set(commands);
        }
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        initializeRedisMocks();
        RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connectionRef.get());
        return factory;
    }

    @SuppressWarnings("unchecked")
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {
        initializeRedisMocks();
        RedisTemplate<String, Object> mockTemplate = Mockito.mock(RedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        ValueOperations<String, Object> valueOps = Mockito.mock(ValueOperations.class);

        // Mock opsForHash() and opsForValue()
        when(mockTemplate.opsForHash()).thenReturn(hashOps);
        when(mockTemplate.opsForValue()).thenReturn(valueOps);

        // entries() 返回空 map（用於空購物車）
        when(hashOps.entries(anyString())).thenReturn(Collections.emptyMap());

        // get() 返回 null
        when(hashOps.get(anyString(), any())).thenReturn(null);

        // size() 返回 0
        when(hashOps.size(anyString())).thenReturn(0L);

        // put() 不做任何事
        doNothing().when(hashOps).put(anyString(), anyString(), any());

        // putAll() 不做任何事
        doNothing().when(hashOps).putAll(anyString(), any());

        // delete() 返回 0（表示刪除了 0 個 key）- 因為是 void 方法才能用 doNothing
        // 但 HashOperations.delete 返回 Long，所以用 thenReturn
        when(hashOps.delete(anyString(), any(Object[].class))).thenReturn(0L);

        // hasKey() 返回 false
        when(hashOps.hasKey(anyString(), any())).thenReturn(false);

        // values() 返回空 list
        when(hashOps.values(anyString())).thenReturn(Collections.emptyList());

        // keys() 返回空 set
        when(hashOps.keys(anyString())).thenReturn(Collections.emptySet());

        // get() 返回 null
        when(valueOps.get(anyString())).thenReturn(null);

        // increment() 返回 1
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(valueOps.increment(anyString(), anyLong())).thenReturn(1L);

        // Mock delete() - 返回 true 表示刪除成功
        when(mockTemplate.delete(anyString())).thenReturn(true);
        when(mockTemplate.delete(anyCollection())).thenReturn(1L);

        // Mock expire() - 返回 true 表示設置過期時間成功
        when(mockTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(mockTemplate.expire(anyString(), anyLong(), any(java.util.concurrent.TimeUnit.class))).thenReturn(true);

        // Mock hasKey() - 返回 false（key 不存在）
        when(mockTemplate.hasKey(anyString())).thenReturn(false);

        // Mock getExpire() - 返回 -1（不存在的 key）
        when(mockTemplate.getExpire(anyString())).thenReturn(-1L);
        when(mockTemplate.getExpire(anyString(), any(java.util.concurrent.TimeUnit.class))).thenReturn(-1L);

        // Mock type() - 返回 NONE
        when(mockTemplate.type(anyString())).thenReturn(org.springframework.data.redis.connection.DataType.NONE);

        // Mock operations - 返回 mock 的 hashOps 和 valueOps
        when(mockTemplate.opsForHash()).thenReturn(hashOps);
        when(mockTemplate.opsForValue()).thenReturn(valueOps);
        when(mockTemplate.opsForList()).thenReturn(Mockito.mock(org.springframework.data.redis.core.ListOperations.class));
        when(mockTemplate.opsForSet()).thenReturn(Mockito.mock(org.springframework.data.redis.core.SetOperations.class));
        when(mockTemplate.opsForZSet()).thenReturn(Mockito.mock(org.springframework.data.redis.core.ZSetOperations.class));

        return mockTemplate;
    }

    @Bean
    @Primary
    public RefreshTokenService refreshTokenService() {
        RefreshTokenService mockService = Mockito.mock(RefreshTokenService.class);

        // Mock 所有的方法，讓它們不回報錯誤
        doNothing().when(mockService).storeRefreshToken(any(), anyString());
        when(mockService.isRefreshTokenValid(any(), anyString())).thenReturn(true);
        doNothing().when(mockService).blacklistRefreshToken(any(), anyString());
        doNothing().when(mockService).blacklistAllRefreshTokens(any());

        return mockService;
    }

    @Bean
    @Primary
    public JwtTokenService jwtTokenService() {
        JwtTokenService mockService = Mockito.mock(JwtTokenService.class);

        // 測試用的 secret key（必須與 application-integration-test.yml 中的一致）
        String testSecret = "testSecretKeyForJwtTokenGenerationThatIsAtLeast256BitsLongForTesting";
        SecretKeySpec secretKeySpec = new SecretKeySpec(testSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        // Mock validateToken - 支援所有測試 token
        when(mockService.validateToken(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token == null || token.isBlank() || token.equals("invalid.jwt.token")) {
                return false;
            }
            // 明確拒絕過期的測試 token
            if (token.equals("expired.invalid.token") || token.startsWith("expired-")) {
                return false;
            }
            // 接受所有測試用的 token 格式
            if (token.startsWith("test-token-") || token.startsWith("refresh-token-")) {
                return true;
            }
            // 對於任何包含 "." 的字串（看起來像真實 JWT），嘗試解析
            if (token.contains(".")) {
                try {
                    Jwts.parser()
                            .verifyWith(secretKeySpec)
                            .build()
                            .parseSignedClaims(token);
                    return true;
                } catch (JwtException e) {
                    log.debug("JWT parsing failed for token: {}", token.substring(0, Math.min(20, token.length())));
                    // 解析失敗應該返回 false（拒絕無效 token）
                    return false;
                }
            }
            // 其他測試用的特殊字串 token，直接接受
            return true;
        });

        when(mockService.isTokenExpired(anyString())).thenReturn(false);

        // Mock generateAccessToken - 生成真實的 JWT token（包含所有 claims）
        when(mockService.generateAccessToken(any(UUID.class), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    String email = invocation.getArgument(1);
                    String role = invocation.getArgument(2);
                    String tenantId = invocation.getArgument(3);
                    if (tenantId == null) {
                        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001").toString();
                    }
                    // 構造真實的 JWT，包含所有必要的 claims
                    return Jwts.builder()
                            .subject(userId.toString())
                            .claim("email", email)
                            .claim("role", role)
                            .claim("tenantId", tenantId)
                            .issuedAt(new java.util.Date())
                            .expiration(new java.util.Date(System.currentTimeMillis() + 1800000))
                            .signWith(secretKeySpec, Jwts.SIG.HS256)
                            .compact();
                });

        // Mock generateRefreshToken
        when(mockService.generateRefreshToken(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    return "refresh-token-" + userId.toString();
                });

        // Mock getUserId - 從測試 token 或 JWT token 解析 userId
        when(mockService.getUserId(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token == null || token.isBlank() || token.equals("invalid.jwt.token")) {
                throw new IllegalArgumentException("Invalid token for getUserId");
            }
            // 解析測試 token 格式：test-token-{uuid}-{role} 或 refresh-token-{uuid}
            if (token.startsWith("test-token-") || token.startsWith("refresh-token-")) {
                try {
                    // 提取 token 中間的 UUID 部分
                    // refresh-token-dd3555b8-923f-4f51-b979-0ff5b8158e99 split 後:
                    // ["refresh", "token", "dd3555b8", "923f", "4f51", "b979", "0ff5b8158e99"]
                    // UUID 部分是 parts[2] 到 parts[6]
                    String[] parts = token.split("-");
                    if (parts.length >= 7) {
                        String uuidStr = parts[2] + "-" + parts[3] + "-" + parts[4] + "-" + parts[5] + "-" + parts[6];
                        return UUID.fromString(uuidStr);
                    }
                } catch (Exception e) {
                    // 解析失敗，拋出異常
                    throw new IllegalArgumentException("Cannot parse userId from token", e);
                }
            }
            // 嘗試解析真實 JWT token
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKeySpec)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                return UUID.fromString(claims.getSubject());
            } catch (JwtException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot parse userId from JWT", e);
            }
        });

        // Mock getEmail - 嘗試從 token 解析，如果失敗拋出異常
        when(mockService.getEmail(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token == null || token.isBlank() || token.equals("invalid.jwt.token")) {
                throw new IllegalArgumentException("Invalid token for getEmail");
            }
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKeySpec)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                return claims.get("email", String.class);
            } catch (JwtException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot parse email from JWT", e);
            }
        });

        // Mock getRole - 從測試 token 或 JWT token 解析 role
        when(mockService.getRole(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token == null || token.isBlank() || token.equals("invalid.jwt.token")) {
                throw new IllegalArgumentException("Invalid token for getRole");
            }
            // 解析測試 token 格式：test-token-{uuid}-{ROLE}
            if (token.startsWith("test-token-") || token.startsWith("refresh-token-")) {
                // Token 格式：test-token-{uuid}-SELLER 或 test-token-{uuid}-BUYER
                String[] parts = token.split("-");
                if (parts.length >= 4) {
                    String role = parts[parts.length - 1];
                    // 確保角色名稱是大寫（符合 User.UserRole enum）
                    return role.toUpperCase();
                }
                throw new IllegalArgumentException("Cannot parse role from token");
            }
            // 嘗試解析真實 JWT token
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKeySpec)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                String role = claims.get("role", String.class);
                if (role == null) {
                    throw new IllegalArgumentException("Role claim is null");
                }
                return role.toUpperCase();
            } catch (JwtException | IllegalArgumentException e) {
                // 如果解析失敗，拋出異常而不是返回 BUYER
                // 這樣 validateToken() 返回 false，請求不被認證，返回 401
                throw new IllegalArgumentException("Cannot parse role from JWT: " + e.getMessage(), e);
            }
        });

        // Mock getTenantId - 嘗試從 token 解析，如果失敗拋出異常
        when(mockService.getTenantId(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token == null || token.isBlank() || token.equals("invalid.jwt.token")) {
                throw new IllegalArgumentException("Invalid token for getTenantId");
            }
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKeySpec)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                return claims.get("tenantId", String.class);
            } catch (JwtException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot parse tenantId from JWT", e);
            }
        });

        return mockService;
    }

    /**
     * 🔴 Mock JwtDecoder - 停用 OAuth2 Resource Server JWK 驗證
     * 這樣測試時不會嘗試連線到外部 issuer
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> {
            // 嘗試解析 JWT，如果失敗返回一個 mock JWT
            try {
                String testSecret = "testSecretKeyForJwtTokenGenerationThatIsAtLeast256BitsLongForTesting";
                SecretKeySpec secretKeySpec = new SecretKeySpec(testSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                Claims claims = Jwts.parser()
                        .verifyWith(secretKeySpec)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                return Jwt.withTokenValue(token)
                        .headers(h -> h.putAll(claims))
                        .claims(c -> c.putAll(claims))
                        .issuedAt(claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.now())
                        .expiresAt(claims.getExpiration() != null ? claims.getExpiration().toInstant() : Instant.now().plusSeconds(3600))
                        .subject(claims.getSubject())
                        .build();
            } catch (JwtException e) {
                // 如果解析失敗，返回一個 mock JWT（用於測試）
                return Jwt.withTokenValue(token)
                        .headers(h -> h.put("alg", "HS256"))
                        .claims(c -> {
                            c.put("sub", "test-user-id");
                            c.put("email", "test@example.com");
                            c.put("role", "BUYER");
                            c.put("tenantId", "00000000-0000-0000-0000-000000000001");
                        })
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
            }
        };
    }

    /**
     * Mock RolePermissionMapping - 根據不同角色返回正確的權限
     * 這樣 SELLER/BUYER 角色測試才能正確區分權限
     *
     * 🔴 修復：使用 spy 並明確 stub 每個角色，避免 any() matcher 在 Docker 環境中可能的問題
     */
    @Bean
    @Primary
    public RolePermissionMapping rolePermissionMapping() {
        // 先建立真實實例
        RolePermissionMapping realMapping = new RolePermissionMapping();

        // 使用 spy 委託給真實實例，確保在任何環境都能正確工作
        RolePermissionMapping spyMapping = Mockito.spy(realMapping);

        // 🔴 明確 stub 每個角色，避免依賴 any() matcher
        Mockito.doReturn(getAuthoritiesForRole(User.UserRole.SELLER)).when(spyMapping).getAuthorities(User.UserRole.SELLER);
        Mockito.doReturn(getAuthoritiesForRole(User.UserRole.BUYER)).when(spyMapping).getAuthorities(User.UserRole.BUYER);
        Mockito.doReturn(getAuthoritiesForRole(User.UserRole.STORE_OWNER)).when(spyMapping).getAuthorities(User.UserRole.STORE_OWNER);
        Mockito.doReturn(getAuthoritiesForRole(User.UserRole.STORE_STAFF)).when(spyMapping).getAuthorities(User.UserRole.STORE_STAFF);
        Mockito.doReturn(getAuthoritiesForRole(User.UserRole.HOST)).when(spyMapping).getAuthorities(User.UserRole.HOST);
        Mockito.doReturn(getAuthoritiesForRole(User.UserRole.ADMIN)).when(spyMapping).getAuthorities(User.UserRole.ADMIN);
        Mockito.doReturn(getAuthoritiesForRole(User.UserRole.SUPER_ADMIN)).when(spyMapping).getAuthorities(User.UserRole.SUPER_ADMIN);
        Mockito.doReturn(getAuthoritiesForRole(User.UserRole.GUEST)).when(spyMapping).getAuthorities(User.UserRole.GUEST);

        // 也 stub hasPermission 方法，回傳 true 表示允許所有權限（測試環境）
        Mockito.doReturn(true).when(spyMapping).hasPermission(any(User.UserRole.class), anyString());
        Mockito.doReturn(true).when(spyMapping).hasPermission(any(User.UserRole.class), any(Permission.class));

        return spyMapping;
    }

    /**
     * 取得特定角色的完整權限列表
     * 與真實 RolePermissionMapping 的實作保持一致
     */
    private List<String> getAuthoritiesForRole(User.UserRole role) {
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + role.name());
        authorities.add(role.name());

        switch (role) {
            case SELLER:
            case STORE_OWNER:
                authorities.addAll(Arrays.asList(
                        "cart:read", "cart:update", "cart:delete",
                        "product:read", "product:create", "product:update", "product:delete",
                        "order:read", "order:create", "order:update",
                        "user:read", "user:update",
                        "media:read", "media:create", "media:update", "media:delete",
                        // Sprint 128（DEF-073）：對齊生產 RolePermissionMapping——通知模板由店主管理，
                        // 原本這四個碼被錯誤地掛在 BUYER 底下，使 M09 整合測試以買家身分通過，
                        // 掩蓋了「生產端 Permission 枚舉根本沒有這些碼」的缺陷。
                        "notification_template:read", "notification_template:create",
                        "notification_template:update", "notification_template:delete"
                ));
                break;
            case BUYER:
                authorities.addAll(Arrays.asList(
                        "cart:read", "cart:update", "cart:delete",
                        "product:read",
                        "order:read", "order:create", "order:update",
                        "user:read", "user:update",
                        "booking:read", "booking:create", "booking:cancel"
                ));
                break;
            case HOST:
                authorities.addAll(Arrays.asList(
                        "room:read", "room:create", "room:update", "room:delete",
                        "booking:read", "booking:create", "booking:update", "booking:cancel",
                        "user:read", "user:update"
                ));
                break;
            case SUPER_ADMIN:
                authorities.addAll(Arrays.asList(
                        "cart:read", "cart:update", "cart:delete",
                        "product:read", "product:create", "product:update", "product:delete",
                        "room:read", "room:create", "room:update", "room:delete",
                        "order:read", "order:create", "order:update", "order:delete",
                        "booking:read", "booking:create", "booking:update", "booking:cancel",
                        "user:read", "user:update", "user:create", "user:delete",
                        "tenant:read", "tenant:update", "tenant:create"
                ));
                break;
            case ADMIN:
                authorities.addAll(Arrays.asList(
                        "cart:read", "cart:update", "cart:delete",
                        "product:read", "product:create", "product:update", "product:delete",
                        "room:read", "room:create", "room:update", "room:delete",
                        "order:read", "order:create", "order:update", "order:delete",
                        "booking:read", "booking:create", "booking:update", "booking:cancel",
                        "user:read", "user:update", "user:create", "user:delete"
                ));
                break;
            case GUEST:
                authorities.addAll(Arrays.asList("product:read", "room:read"));
                break;
            default:
                // STORE_STAFF 等其他角色返回基本權限
                authorities.addAll(Arrays.asList("product:read", "room:read", "order:read"));
                break;
        }
        return authorities;
    }

    /**
     * Mock StorageService - 避免 MinIO 連接問題
     * 讓所有操作都不實際訪問 S3/MinIO
     */
    @Bean
    @Primary
    public StorageService storageService() {
        StorageService mockService = Mockito.mock(StorageService.class);

        // Mock uploadFile - 返回假的路徑
        when(mockService.uploadFile(any(UUID.class), anyString(), any(), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    UUID tenantId = invocation.getArgument(0);
                    String fileName = invocation.getArgument(1);
                    return tenantId.toString() + "/" + UUID.randomUUID() + "-" + fileName;
                });

        // Mock objectExists - 總是返回 true
        when(mockService.objectExists(any(UUID.class), anyString())).thenReturn(true);

        // Mock getObject - 返回空的輸入流
        when(mockService.getObject(any(UUID.class), anyString()))
                .thenReturn(new ByteArrayInputStream("mock-content".getBytes()));

        // Mock deleteObject - 不拋出異常
        doNothing().when(mockService).deleteObject(anyString());

        return mockService;
    }

    /**
     * Mock MediaService - 避免實際上傳到 MinIO
     * 這個 mock 會覆蓋 @Primary 的 StorageService mock
     */
    @Bean
    @Primary
    public MediaService mediaService() {
        MediaService mockService = Mockito.mock(MediaService.class);

        // Mock uploadMedia (MultipartFile version) - 返回假的回應
        when(mockService.uploadMedia(any(UUID.class), any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    Object multipartFile = invocation.getArgument(2);
                    String fileName = "test-multipart.jpg";
                    try {
                        // 嘗試取得原始檔案名稱
                        java.lang.reflect.Method getOriginalFilename = multipartFile.getClass().getMethod("getOriginalFilename");
                        Object result = getOriginalFilename.invoke(multipartFile);
                        if (result != null) {
                            fileName = result.toString();
                        }
                    } catch (Exception e) {
                        // 忽略，使用預設值
                    }
                    return M15Dto.MediaUploadResponse.builder()
                            .fileName(fileName)
                            .fileSize(1024L)
                            .mimeType("image/jpeg")
                            .filePath("/test-tenant/media/" + fileName)
                            .build();
                });

        // Mock uploadMedia (path version) - 返回假的回應
        when(mockService.uploadMedia(any(UUID.class), any(UUID.class), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String fileName = invocation.getArgument(2);
                    return M15Dto.MediaUploadResponse.builder()
                            .fileName(fileName)
                            .fileSize(invocation.getArgument(4))
                            .mimeType(invocation.getArgument(5))
                            .filePath(invocation.getArgument(6))
                            .build();
                });

        // Mock getMediaList - 返回空的媒體列表
        when(mockService.getMediaList(any(UUID.class), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    return M15Dto.MediaListResponse.builder()
                            .items(List.of())
                            .totalCount(0)
                            .page(0)
                            .size(20)
                            .totalPages(0)
                            .build();
                });

        // Mock deleteMedia - 不拋出異常
        doNothing().when(mockService).deleteMedia(any(UUID.class), any(UUID.class));

        return mockService;
    }

    /**
     * Mock MediaService (core.media) - 避免實際上傳到 MinIO
     * 這個 mock 是為了 MediaCategoryController 等需要 MediaService 的控制器
     */
    @Bean
    @Primary
    public com.nextkey.ecommerce.core.media.MediaService coreMediaService() {
        com.nextkey.ecommerce.core.media.MediaService mockService = Mockito.mock(com.nextkey.ecommerce.core.media.MediaService.class);

        // Mock 所有可能的方法，避免回傳 null 導致的 NPE
        // 使用 Answer 來處理任意參數並返回合理的預設值
        when(mockService.getCategories()).thenReturn(List.of());
        when(mockService.getCategory(any(UUID.class))).thenReturn(null);
        when(mockService.createCategory(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest.class);
            if (req == null) return null;
            return com.nextkey.ecommerce.api.dto.media.MediaCategoryDto.builder()
                    .id(UUID.randomUUID())
                    .name(req.getName() != null ? req.getName() : "default")
                    .description(req.getDescription())
                    .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                    .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                    .build();
        });
        when(mockService.updateCategory(any(UUID.class), any())).thenReturn(null);
        doNothing().when(mockService).deleteCategory(any(UUID.class));

        when(mockService.getAssets(anyInt(), anyInt(), any(), any(), any())).thenReturn(
                org.springframework.data.domain.Page.empty()
        );
        when(mockService.getAsset(any(UUID.class))).thenReturn(null);
        when(mockService.uploadAsset(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, com.nextkey.ecommerce.api.dto.media.UploadMediaRequest.class);
            if (req == null) return null;
            return com.nextkey.ecommerce.api.dto.media.MediaAssetDto.builder()
                    .id(UUID.randomUUID())
                    .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                    .fileName(req.getFileName() != null ? req.getFileName() : "default.jpg")
                    .filePath(req.getFilePath() != null ? req.getFilePath() : "/default/path")
                    .fileSize(req.getFileSize() != null ? req.getFileSize() : 0L)
                    .mimeType(req.getMimeType() != null ? req.getMimeType() : "image/jpeg")
                    .tags(req.getTags() != null ? req.getTags() : List.of())
                    .build();
        });
        when(mockService.updateAsset(any(UUID.class), any())).thenReturn(null);
        doNothing().when(mockService).deleteAsset(any(UUID.class));
        when(mockService.existsMediaById(anyString())).thenReturn(true);

        return mockService;
    }

    /**
     * Mock RedisLockService - 避免 Redis 鎖操作失敗
     * 這個 mock 覆蓋 @Service RedisLockService
     */
    @Bean
    @Primary
    public RedisLockService redisLockService() {
        RedisLockService mockLockService = Mockito.mock(RedisLockService.class);

        // Mock 所有可能的方法返回合理值
        when(mockLockService.tryAcquireLockNoWait(anyString())).thenReturn(UUID.randomUUID().toString());
        when(mockLockService.tryAcquireLock(anyString())).thenReturn(UUID.randomUUID().toString());
        when(mockLockService.tryAcquireLock(anyString(), anyLong())).thenReturn(UUID.randomUUID().toString());
        when(mockLockService.tryAcquireLockWithWait(anyString(), anyLong())).thenReturn(UUID.randomUUID().toString());
        when(mockLockService.tryAcquireLockWithWaitlong(anyString(), anyLong())).thenReturn(UUID.randomUUID().toString());
        when(mockLockService.releaseLock(anyString(), anyString())).thenReturn(true);
        when(mockLockService.isLocked(anyString())).thenReturn(false);
        when(mockLockService.extendLock(anyString(), anyString(), anyLong())).thenReturn(true);
        doNothing().when(mockLockService).forceReleaseLock(anyString());

        return mockLockService;
    }

    /**
     * Mock IdempotencyService - 避免 Redis idempotency 操作問題
     * 這個 mock 覆蓋 @Service IdempotencyService
     *
     * 使用 in-memory Map 來追蹤 idempotency key 狀態，支援 E2E 測試
     */
    @Bean
    @Primary
    public com.nextkey.ecommerce.core.idempotency.IdempotencyService idempotencyService() {
        com.nextkey.ecommerce.core.idempotency.IdempotencyService mockService =
                Mockito.mock(com.nextkey.ecommerce.core.idempotency.IdempotencyService.class);

        // 使用 in-memory Map 追蹤 idempotency key 狀態
        // key: idempotencyKey, value: Object (可能是 "PROCESSING" 或 BookingDto.BookingResponse)
        Map<String, Object> idempotencyStorage = new ConcurrentHashMap<>();

        // Mock checkAndMark - 如果 key 不存在則標記並返回 true，否則返回 false
        when(mockService.checkAndMark(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            synchronized (idempotencyStorage) {
                if (idempotencyStorage.containsKey(key)) {
                    return false; // 重複請求
                }
                idempotencyStorage.put(key, "PROCESSING");
                return true; // 新請求
            }
        });

        // Mock markCompleted - 儲存響應（這是真正區分「完成」與「處理中」的關鍵）
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object response = invocation.getArgument(1);
            synchronized (idempotencyStorage) {
                idempotencyStorage.put(key, response);
            }
            return null;
        }).when(mockService).markCompleted(anyString(), any());

        // Mock getStoredResponse - 返回已儲存的響應
        // 重要：如果值是 "PROCESSING" 表示仍在處理中，返回 null
        // 如果是其他物件（BookingDto.BookingResponse），表示已完成，返回該物件
        when(mockService.getStoredResponse(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = idempotencyStorage.get(key);
            if (value == null || "PROCESSING".equals(value)) {
                return null; // 不存在或仍在處理中
            }
            return value; // 返回已儲存的回應（可能是 BookingDto.BookingResponse）
        });

        // Mock isStillProcessing - 檢查是否仍在處理中
        when(mockService.isStillProcessing(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = idempotencyStorage.get(key);
            return "PROCESSING".equals(value);
        });

        // Mock remove - 刪除 key
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            synchronized (idempotencyStorage) {
                idempotencyStorage.remove(key);
            }
            return null;
        }).when(mockService).remove(anyString());

        // Mock isValidUuidV4 - 嚴格驗證 UUID v4 格式
        // UUID v4 格式：xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
        // 其中 x 是任意十六進位，4 是版本號，y 是 8, 9, a, 或 b
        when(mockService.isValidUuidV4(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (key == null) {
                return false;
            }
            // UUID v4 格式：8-4-4-4-12 的結構
            // 正則表達式驗證嚴格的 UUID v4 格式
            String uuidV4Pattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
            if (!key.matches(uuidV4Pattern)) {
                return false;
            }
            // 進一步驗證可以解析為 UUID
            try {
                UUID uuid = UUID.fromString(key);
                return uuid.version() == 4;
            } catch (IllegalArgumentException e) {
                return false;
            }
        });

        return mockService;
    }

    /**
     * Mock RedisCartService - 避免 Redis 操作問題
     * 這個 mock 覆蓋 @Service RedisCartService
     *
     * 使用 in-memory Map 來追蹤購物車狀態，支援 E2E 測試
     */
    @Bean
    @Primary
    public RedisCartService redisCartService() {
        RedisCartService mockService = Mockito.mock(RedisCartService.class);

        // 使用 in-memory Map 追蹤購物車狀態
        // key: userId:tenantId, value: Map<itemKey, CartItemData>
        Map<String, Map<String, RedisCartService.CartItemData>> cartStorage = new ConcurrentHashMap<>();

        // Mock addItem - 返回成功的回應，並實際存儲 item
        // 修正：當商品已存在時要累加數量（模擬真實 RedisCartService 行為）
        when(mockService.addItem(any(UUID.class), any(UUID.class), any(CartDto.AddItemRequest.class)))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    UUID tenantId = invocation.getArgument(1);
                    CartDto.AddItemRequest request = invocation.getArgument(2);

                    String cartKey = userId.toString() + ":" + tenantId.toString();
                    String itemKey = request.getListingId().toString(); // SKU 為 null 時只有 listingId

                    // 檢查是否已有相同商品在購物車（真實邏輯）
                    RedisCartService.CartItemData existingItem = cartStorage
                            .computeIfAbsent(cartKey, k -> new ConcurrentHashMap<>())
                            .get(itemKey);

                    int newQuantity = request.getQuantity();
                    if (existingItem != null) {
                        // 累加現有數量
                        newQuantity += existingItem.getQuantity();
                    }

                    // 創建 cart item data
                    RedisCartService.CartItemData itemData = RedisCartService.CartItemData.builder()
                            .listingId(request.getListingId())
                            .skuId(request.getSkuId())
                            .quantity(newQuantity)
                            .unitPrice(java.math.BigDecimal.valueOf(100))
                            .subtotal(java.math.BigDecimal.valueOf(100 * newQuantity))
                            .listingType("PRODUCT")
                            .addedAt(Instant.now())
                            .build();

                    // 實際存儲到 in-memory map
                    cartStorage.computeIfAbsent(cartKey, k -> new ConcurrentHashMap<>()).put(itemKey, itemData);

                    // 計算實際的總數量
                    int totalItemsInCart = cartStorage.get(cartKey).values().stream()
                            .mapToInt(RedisCartService.CartItemData::getQuantity)
                            .sum();

                    return CartDto.AddItemResponse.builder()
                            .success(true)
                            .message("Item added to cart")
                            .item(CartDto.CartItemResponse.builder()
                                    .cartItemKey(itemKey)
                                    .listingId(request.getListingId())
                                    .listingName("Test Product")
                                    .quantity(newQuantity)
                                    .unitPrice(java.math.BigDecimal.valueOf(100))
                                    .subtotal(java.math.BigDecimal.valueOf(100 * newQuantity))
                                    .build())
                            .totalItemsInCart(totalItemsInCart)
                            .build();
                });

        // Mock updateItem(String cartItemKey, int quantity) - 返回成功的回應
        when(mockService.updateItem(any(UUID.class), any(UUID.class), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    UUID tenantId = invocation.getArgument(1);
                    String cartItemKey = invocation.getArgument(2);
                    int quantity = invocation.getArgument(3);

                    String cartKey = userId.toString() + ":" + tenantId.toString();
                    Map<String, RedisCartService.CartItemData> cart = cartStorage.get(cartKey);

                    if (cart == null || !cart.containsKey(cartItemKey)) {
                        throw new com.nextkey.ecommerce.shared.exception.CartItemNotFoundException("Cart item not found: " + cartItemKey);
                    }

                    RedisCartService.CartItemData itemData = cart.get(cartItemKey);
                    itemData.setQuantity(quantity);
                    itemData.setSubtotal(java.math.BigDecimal.valueOf(100 * quantity));

                    String[] parts = cartItemKey.split(":");
                    UUID listingId = UUID.fromString(parts[0]);

                    return CartDto.CartItemResponse.builder()
                            .cartItemKey(cartItemKey)
                            .listingId(listingId)
                            .quantity(quantity)
                            .unitPrice(java.math.BigDecimal.valueOf(100))
                            .subtotal(java.math.BigDecimal.valueOf(100 * quantity))
                            .build();
                });

        // Mock updateItem(UUID, UUID, UUID, UUID, int) - 當 item 不存在時拋異常
        when(mockService.updateItem(any(UUID.class), any(UUID.class), any(UUID.class), any(), anyInt()))
                .thenThrow(new com.nextkey.ecommerce.shared.exception.CartItemNotFoundException("Cart item not found"));

        // Mock removeItem(String cartItemKey) - 當 item 不存在時拋異常
        doAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            UUID tenantId = invocation.getArgument(1);
            String cartItemKey = invocation.getArgument(2);

            String cartKey = userId.toString() + ":" + tenantId.toString();
            Map<String, RedisCartService.CartItemData> cart = cartStorage.get(cartKey);

            if (cart == null || !cart.containsKey(cartItemKey)) {
                throw new com.nextkey.ecommerce.shared.exception.CartItemNotFoundException("Cart item not found: " + cartItemKey);
            }

            cart.remove(cartItemKey);
            return null;
        }).when(mockService).removeItem(any(UUID.class), any(UUID.class), anyString());

        // 對於不存在的 UUID 版本拋異常
        doThrow(new com.nextkey.ecommerce.shared.exception.CartItemNotFoundException("Cart item not found"))
                .when(mockService).removeItem(any(UUID.class), any(UUID.class), any(UUID.class), any());

        // Mock clearCart - 清空購物車
        doAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            UUID tenantId = invocation.getArgument(1);
            String cartKey = userId.toString() + ":" + tenantId.toString();
            cartStorage.remove(cartKey);
            return null;
        }).when(mockService).clearCart(any(UUID.class), any(UUID.class));

        // Mock getCart - 返回購物車內容
        when(mockService.getCart(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    UUID tenantId = invocation.getArgument(1);
                    String cartKey = userId.toString() + ":" + tenantId.toString();

                    Map<String, RedisCartService.CartItemData> cart = cartStorage.get(cartKey);
                    List<CartDto.CartItemResponse> items = new ArrayList<>();
                    int totalQuantity = 0;

                    if (cart != null) {
                        for (Map.Entry<String, RedisCartService.CartItemData> entry : cart.entrySet()) {
                            RedisCartService.CartItemData itemData = entry.getValue();
                            items.add(CartDto.CartItemResponse.builder()
                                    .cartItemKey(entry.getKey())
                                    .listingId(itemData.getListingId())
                                    .listingName("Test Product")
                                    .quantity(itemData.getQuantity())
                                    .unitPrice(itemData.getUnitPrice())
                                    .subtotal(itemData.getSubtotal())
                                    .build());
                            totalQuantity += itemData.getQuantity();
                        }
                    }

                    final int finalTotalQuantity = totalQuantity;
                    return CartDto.CartResponse.builder()
                            .userId(userId)
                            .cartId("cart-" + userId)
                            .items(items)
                            .itemCount(finalTotalQuantity)  // totalItems = 數量總和 (1+2+3=6)
                            .totalAmount(java.math.BigDecimal.valueOf(finalTotalQuantity * 100))
                            .currency("TWD")
                            .build();
                });

        // Mock getCartItemCount - 返回正確的數量
        when(mockService.getCartItemCount(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    UUID tenantId = invocation.getArgument(1);
                    String cartKey = userId.toString() + ":" + tenantId.toString();

                    Map<String, RedisCartService.CartItemData> cart = cartStorage.get(cartKey);
                    if (cart == null) {
                        return 0;
                    }
                    return cart.values().stream()
                            .mapToInt(RedisCartService.CartItemData::getQuantity)
                            .sum();
                });

        // Mock applyPromoCode - 返回錯誤（購物車為空）
        when(mockService.applyPromoCode(any(UUID.class), any(UUID.class), anyString()))
                .thenThrow(new com.nextkey.ecommerce.shared.exception.CartEmptyException("Cannot apply promo to empty cart"));

        // Mock removePromoCode - 不拋異常
        doNothing().when(mockService).removePromoCode(any(UUID.class), any(UUID.class));

        // Mock validatePromoCode
        when(mockService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(CartDto.PromoValidationResult.builder()
                        .valid(false)
                        .invalidReason("INVALID")
                        .build());

        // Mock getCartWithPromo - 返回購物車內容
        when(mockService.getCartWithPromo(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    UUID tenantId = invocation.getArgument(1);
                    String cartKey = userId.toString() + ":" + tenantId.toString();

                    Map<String, RedisCartService.CartItemData> cart = cartStorage.get(cartKey);
                    List<CartDto.CartItemResponse> items = new ArrayList<>();
                    int totalQuantity = 0;

                    if (cart != null) {
                        for (Map.Entry<String, RedisCartService.CartItemData> entry : cart.entrySet()) {
                            RedisCartService.CartItemData itemData = entry.getValue();
                            items.add(CartDto.CartItemResponse.builder()
                                    .cartItemKey(entry.getKey())
                                    .listingId(itemData.getListingId())
                                    .listingName("Test Product")
                                    .quantity(itemData.getQuantity())
                                    .unitPrice(itemData.getUnitPrice())
                                    .subtotal(itemData.getSubtotal())
                                    .build());
                            totalQuantity += itemData.getQuantity();
                        }
                    }

                    final int finalTotalQuantity = totalQuantity;
                    return CartDto.CartResponse.builder()
                            .userId(userId)
                            .cartId("cart-" + userId)
                            .items(items)
                            .itemCount(finalTotalQuantity)  // totalItems = 數量總和
                            .totalAmount(java.math.BigDecimal.valueOf(finalTotalQuantity * 100))
                            .currency("TWD")
                            .build();
                });

        return mockService;
    }
}
