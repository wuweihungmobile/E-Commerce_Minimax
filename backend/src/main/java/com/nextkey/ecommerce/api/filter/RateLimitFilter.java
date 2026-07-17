package com.nextkey.ecommerce.api.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 每租戶 API 限流（Token Bucket，PRD §3.3/§13.4/§16.4.2：每租戶 100 req/min，超過回傳 429）。
 *
 * <p>限流維度僅「每租戶」——PRD 全文唯一明確要求的維度，未提及 per-IP/per-user 疊加，故不額外實作。
 * 未解析出租戶身分的請求（{@code /v2/auth/**} 登入前、{@code /actuator/**} 健康檢查）不納入限流，
 * 因這些路徑無業務租戶可綁定，排除範圍比照 {@link TenantContextFilter}。
 *
 * <p>Token Bucket 狀態存於 Redis（容量 100、每 60 秒完全補滿），以單支 Lua script 原子讀取-補充-扣除，
 * 避免高併發下的 race condition。Redis 故障時 fail-open（放行請求，僅記錄錯誤日誌），避免快取層
 * 故障波及全站可用性——PRD 未定義此情境，此為工程決策。
 *
 * <p>錯誤碼複用既有 {@link ErrorCode#E_9904}（已預留、已映射 429，非新增），因 PRD §16.4.2 原文寫的
 * {@code E-6001} 與既有 {@code ErrorCode.E_6001}（付款失敗）撞碼，故不採用。
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "ratelimit:tenant:";
    private static final double CAPACITY = 100.0;
    private static final double WINDOW_MS = 60_000.0;
    private static final double REFILL_PER_MS = CAPACITY / WINDOW_MS;
    private static final String TTL_SECONDS = String.valueOf((long) (WINDOW_MS / 1000 * 2));
    private static final int HTTP_TOO_MANY_REQUESTS = 429; // HttpServletResponse 無 SC_TOO_MANY_REQUESTS 常數（RFC 6585）
    private static final long MS_PER_SECOND = 1000L;

    private static final String SCRIPT =
            "local key = KEYS[1]\n"
            + "local capacity = tonumber(ARGV[1])\n"
            + "local refill_per_ms = tonumber(ARGV[2])\n"
            + "local now = tonumber(ARGV[3])\n"
            + "local ttl_seconds = tonumber(ARGV[4])\n"
            + "local data = redis.call('HMGET', key, 'tokens', 'ts')\n"
            + "local tokens = tonumber(data[1])\n"
            + "local last_ts = tonumber(data[2])\n"
            + "if tokens == nil then\n"
            + "  tokens = capacity\n"
            + "  last_ts = now\n"
            + "end\n"
            + "local elapsed = now - last_ts\n"
            + "if elapsed > 0 then\n"
            + "  tokens = math.min(capacity, tokens + elapsed * refill_per_ms)\n"
            + "  last_ts = now\n"
            + "end\n"
            + "local allowed = 0\n"
            + "if tokens >= 1 then\n"
            + "  tokens = tokens - 1\n"
            + "  allowed = 1\n"
            + "end\n"
            + "redis.call('HMSET', key, 'tokens', tokens, 'ts', last_ts)\n"
            + "redis.call('EXPIRE', key, ttl_seconds)\n"
            + "local remaining = math.floor(tokens)\n"
            + "local deficit = 1 - tokens\n"
            + "local wait_ms = 0\n"
            + "if deficit > 0 then\n"
            + "  wait_ms = math.ceil(deficit / refill_per_ms)\n"
            + "end\n"
            + "return string.format('%d:%d:%d', allowed, remaining, wait_ms)";

    private static final DefaultRedisScript<String> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(SCRIPT, String.class);

    // 可為 null：用 ObjectProvider 取 getIfAvailable() 是為了相容 @WebMvcTest 這類窄切片測試
    // （如 M08ReviewIntegrationTest/M18KnowledgePhase2IntegrationTest），該類測試只載入 Web 層，
    // 不會自動配置 RedisAutoConfiguration，若強制要求此 bean 會讓整個測試 context 啟動失敗。
    // 單一建構子上的 @Autowired(required=false) 對此無效（Spring 仍會嘗試完整解析該建構子，
    // 只有存在多個建構子可供退回選擇時 required=false 才有意義），改用 ObjectProvider 才是
    // Spring 官方文件記載、可靠的「可選依賴」寫法。正式環境一律有
    // spring-boot-starter-data-redis 自動配置的 StringRedisTemplate，不受影響。
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(final ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                     final HttpServletResponse response,
                                     final FilterChain filterChain) throws ServletException, IOException {
        if (redisTemplate == null) {
            filterChain.doFilter(request, response);
            return;
        }

        var tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String result;
        try {
            result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(KEY_PREFIX + tenantId),
                    String.valueOf(CAPACITY),
                    String.valueOf(REFILL_PER_MS),
                    String.valueOf(System.currentTimeMillis()),
                    TTL_SECONDS);
        } catch (DataAccessException ex) {
            log.error("[RateLimitFilter] Redis unavailable, failing open for tenant {}", tenantId, ex);
            filterChain.doFilter(request, response);
            return;
        }

        if (result == null) {
            // 防禦性處理：某些測試環境會將 RedisConnectionFactory 整個 mock 掉（未真正拋出
            // DataAccessException，而是靜默回傳 null），語意上等同 Redis 不可用，比照 fail-open。
            log.error("[RateLimitFilter] Redis script returned null, failing open for tenant {}", tenantId);
            filterChain.doFilter(request, response);
            return;
        }

        String[] parts = result.split(":");
        boolean allowed = "1".equals(parts[0]);
        long remaining = Long.parseLong(parts[1]);
        long waitMs = Long.parseLong(parts[2]);
        long resetEpochSeconds = Instant.now().plusMillis(waitMs).getEpochSecond();

        response.setHeader("X-RateLimit-Limit", String.valueOf((long) CAPACITY));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpochSeconds));

        if (!allowed) {
            long retryAfterSeconds = Math.max(1, (waitMs + MS_PER_SECOND - 1) / MS_PER_SECOND);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(HTTP_TOO_MANY_REQUESTS);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error(ErrorCode.E_9904.getCode(), ErrorCode.E_9904.getMessage())));
            log.warn("[RateLimitFilter] Tenant {} exceeded rate limit, retry after {}s", tenantId, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/v2/auth/") || path.startsWith("/actuator/");
    }
}
