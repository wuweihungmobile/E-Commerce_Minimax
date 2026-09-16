package com.nextkey.ecommerce.api.filter;

import java.io.IOException;
import java.util.Collections;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 登入端點每來源 IP 限流（Sprint 168，DEF-220）。
 *
 * <p>背景：{@link RateLimitFilter} 的每租戶限流刻意排除整個 {@code /v2/auth/**}（登入前無租戶
 * 身分可綁定），導致 {@code POST /v2/auth/login} 完全沒有任何節流機制——這是 Sprint 93 當時的
 * 工程範圍決策，未評估其安全後果。此 Filter 專門補上這個缺口，只套用在登入端點本身，以來源 IP
 * 為維度（與 {@link com.nextkey.ecommerce.infrastructure.security.LoginAttemptService} 的
 * per-account 鎖定互為雙層防護：IP 節流先擋掉高速自動化攻擊，帳號鎖定則防止攻擊者跨多個 IP
 * 輪流對同一帳號慢速嘗試）。
 *
 * <p>本專案沒有反向代理／CDN 在前端終止連線（見 {@code docker-compose.yml} 無 nginx/traefik
 * service），故直接信任 {@link HttpServletRequest#getRemoteAddr()} 作為真實來源 IP；刻意不採信
 * {@code X-Forwarded-For} 之類的 client 可自訂 header，否則攻擊者只要每次帶不同的偽造標頭值
 * 就能讓伺服器誤判為不同來源，直接繞過限流。若未來加入受信任的反向代理，須改用該代理層寫入的
 * 標頭並限定只信任該代理節點，此檔屆時需要一併調整。
 *
 * <p>Token Bucket 演算法與 Lua script 比照 {@link RateLimitFilter}（同樣以單支 Lua script 原子
 * 讀取-補充-扣除，避免高併發下的 race condition；Redis 故障時 fail-open，理由相同：避免快取層
 * 故障波及登入功能可用性）。容量低於租戶限流的 100/分鐘，但刻意不設得過緊：同一來源 IP（如公司
 * NAT、校園/公用 Wi-Fi、行動網路 CGNAT）在短時間內可能有多位不同使用者同時登入，容量過低會誤傷
 * 這類合法情境（{@code make validate-e2e} 實測：Playwright 4 workers 平行對同一 host 發出約 50 次
 * `registerAndLogin`/`loginOnly` 呼叫時，原訂的 10/分鐘一度造成 8 個既有 spec 的登入被誤擋）。
 * 30/分鐘仍遠低於真正的自動化 brute force（每秒數十次的量級），同時足以涵蓋前述合法併發情境；
 * 真正防止「鎖定單一帳號」的是 per-account 的
 * {@link com.nextkey.ecommerce.infrastructure.security.LoginAttemptService}，此 IP 節流只負責
 * 擋掉高速自動化流量，兩者互為雙層防護，缺一不可（IP 節流無法單獨防止跨 IP 輪流嘗試同一帳號）。
 */
@Slf4j
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "ratelimit:login_ip:";
    private static final String LOGIN_PATH = "/v2/auth/login";
    private static final double CAPACITY = 30.0;
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

    // 比照 RateLimitFilter：ObjectProvider 讓 @WebMvcTest 這類未載入 RedisAutoConfiguration 的
    // 窄切片測試也能建構此 Filter 而不失敗
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(final ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
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

        String clientIp = request.getRemoteAddr();

        String result;
        try {
            result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(KEY_PREFIX + clientIp),
                    String.valueOf(CAPACITY),
                    String.valueOf(REFILL_PER_MS),
                    String.valueOf(System.currentTimeMillis()),
                    TTL_SECONDS);
        } catch (DataAccessException ex) {
            log.error("[LoginRateLimitFilter] Redis unavailable, failing open for IP {}", clientIp, ex);
            filterChain.doFilter(request, response);
            return;
        }

        if (result == null) {
            log.error("[LoginRateLimitFilter] Redis script returned null, failing open for IP {}", clientIp);
            filterChain.doFilter(request, response);
            return;
        }

        String[] parts = result.split(":");
        boolean allowed = "1".equals(parts[0]);
        long waitMs = Long.parseLong(parts[2]);

        if (!allowed) {
            long retryAfterSeconds = Math.max(1, (waitMs + MS_PER_SECOND - 1) / MS_PER_SECOND);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(HTTP_TOO_MANY_REQUESTS);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error(ErrorCode.E_9904.getCode(), ErrorCode.E_9904.getMessage())));
            log.warn("[LoginRateLimitFilter] IP {} exceeded login rate limit, retry after {}s",
                    clientIp, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(request.getServletPath()));
    }
}
