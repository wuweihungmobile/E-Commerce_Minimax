package com.nextkey.ecommerce.api.config;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.filter.JwtAuthenticationFilter;
import com.nextkey.ecommerce.api.filter.LoginRateLimitFilter;
import com.nextkey.ecommerce.api.filter.RateLimitFilter;
import com.nextkey.ecommerce.api.filter.TenantContextFilter;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.trace.RequestId;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TenantContextFilter tenantContextFilter;
    private final RateLimitFilter rateLimitFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;

    // CORS configuration
    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    /**
     * 逗號分隔的 CORS 允許來源，來源為 application.yml 的 app.cors.allowed-origins
     * （環境變數 APP_CORS_ALLOWED_ORIGINS 可覆寫）。前端 NEXT_PUBLIC_API_URL 於 build 時可指向正式後端網域，
     * 瀏覽器跨源直連，故正式環境必須能設定自己的前端來源。Sprint 191，DEF-265。
     */
    @Value("${app.cors.allowed-origins}")
    private String corsAllowedOriginsRaw;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                         TenantContextFilter tenantContextFilter,
                         RateLimitFilter rateLimitFilter,
                         LoginRateLimitFilter loginRateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.tenantContextFilter = tenantContextFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.loginRateLimitFilter = loginRateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            // 安全標頭（PRD 16.4.1：錯誤回應需帶 nosniff、X-Frame-Options: DENY；SRD：CSP）。
            // nosniff／X-Frame-Options: DENY／Cache-Control: no-store 是 Spring Security 預設；此處補預設沒有的
            // CSP 與 Referrer-Policy，並讓 HSTS 在 TLS 由代理終止時也送（見 isHttpsRequest）。
            // 本服務只回 JSON（無 Swagger 或 HTML 頁），default-src 'none' 不會擋到功能。
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                // 不含 includeSubDomains：子網域是否都能走 https 無從驗證，而該指示送出後一年內無法收回
                .httpStrictTransportSecurity(hsts -> hsts
                    .requestMatcher(SecurityConfig::isHttpsRequest)
                    .includeSubDomains(false)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(mapper.writeValueAsString(ApiResponse.error(
                        ErrorCode.E_1000.getCode(),
                        "Authentication required"
                    )));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(mapper.writeValueAsString(ApiResponse.error(
                        ErrorCode.E_1007.getCode(),
                        "Insufficient permissions"
                    )));
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/v2/auth/register").permitAll()
                .requestMatchers("/v2/auth/login").permitAll()
                .requestMatchers("/v2/auth/refresh").permitAll()
                // Protected auth endpoints (require authentication)
                .requestMatchers("/v2/auth/logout").authenticated()
                .requestMatchers("/v2/auth/me").authenticated()
                // Public tenant endpoints (Guest access for store application and viewing)
                .requestMatchers("/v2/tenants/apply").permitAll()
                .requestMatchers("/v2/tenants/{id}").permitAll()
                .requestMatchers("/v2/public/**").permitAll()
                // M15 CMS public endpoints (前台公開 API)
                .requestMatchers("/v2/posts").permitAll()
                .requestMatchers("/v2/posts/**").permitAll()
                .requestMatchers("/v2/listings/*/card").permitAll()
                // CMS 頁面/橫幅公開瀏覽端點（Sprint 82 DEF-034 修復，訪客行銷內容，比照 /v2/posts 模式）
                // 🔴 務必限定 HTTP method：/v2/cms/pages/* 若不限 GET 會誤放行 PUT /v2/cms/pages/{pageId}（Admin 更新）
                .requestMatchers(HttpMethod.GET, "/v2/cms/pages/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/v2/cms/banners/active").permitAll()
                .requestMatchers(HttpMethod.POST, "/v2/cms/banners/*/click").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // WebSocket/SockJS handshake（M10 IM）：放行 HTTP 握手；
                // 實際身份驗證於 STOMP CONNECT frame 由 StompAuthChannelInterceptor 處理
                .requestMatchers("/ws/**").permitAll()
                // Stripe webhook（Sprint 160 DEF-202 修復）：Stripe 伺服器回呼永遠不會帶 JWT，
                // 先前缺此規則導致 .anyRequest().authenticated() 一律回 401，webhook 事實上完全
                // 不可達（實測驗證，見 StripeWebhookReachabilityTest）。真正的身份驗證由
                // StripeWebhookController 內的 StripeSignatureVerifierService（HMAC-SHA256 signature）
                // 把關，非 JWT，故此處 permitAll 正確。只放行 /stripe，/linepay 為未串接的 stub
                // （零 signature 驗證機制），刻意維持需要驗證，避免開放無防護的公開端點。
                .requestMatchers(HttpMethod.POST, "/v2/payments/webhook/stripe").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, TenantContextFilter.class)
            // DEF-220：/v2/auth/login 專屬 per-IP 限流，補上 RateLimitFilter 刻意排除 /v2/auth/**
            // 留下的缺口（登入前無租戶身分可綁定，故獨立於租戶限流之外）
            .addFilterAfter(loginRateLimitFilter, RateLimitFilter.class);

        return http.build();
    }

    /**
     * HSTS 只能在 HTTPS 回應上送（瀏覽器對 http 回應中的 HSTS 一律忽略）。TLS 通常在應用前方的代理／負載平衡器
     * 終止，後端收到的是 http，Spring 預設的 {@code request.isSecure()} 因此恆為 false、HSTS 永遠不送；
     * 這裡同時採信 {@code X-Forwarded-Proto}（多層代理時取第一段）。此標頭可被呼叫端偽造，但偽造只會讓 http
     * 回應多帶一個瀏覽器本來就會忽略的標頭，沒有安全影響。
     */
    static boolean isHttpsRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && "https".equalsIgnoreCase(forwardedProto.split(",")[0].trim());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(corsAllowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Idempotency-Key：訂房結帳（POST /v2/bookings）與合併結帳（POST /v2/checkout/mixed）皆帶此
        // 自訂標頭。未列入白名單時，preflight 回應的 Access-Control-Allow-Headers 會缺少它，瀏覽器
        // 據此封鎖真正的 POST（伺服器端不會報錯，故長期未被察覺）。Sprint 128，DEF-072。
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Tenant-ID",
                "Idempotency-Key"));
        // X-Request-ID：跨源時瀏覽器預設讀不到自訂回應標頭，需列入才能讓前端取得（DEF-280）
        configuration.setExposedHeaders(List.of("Authorization",
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "Retry-After",
                RequestId.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(CORS_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
