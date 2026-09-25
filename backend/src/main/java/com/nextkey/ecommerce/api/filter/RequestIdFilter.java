package com.nextkey.ecommerce.api.filter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nextkey.ecommerce.shared.trace.RequestId;

/**
 * PRD §16.4.1：每個回應帶 {@code X-Request-ID}，並寫入日誌 MDC，讓使用者回報的 ID 能直接對到後端日誌。
 *
 * <p>沿用呼叫端（前端／上游代理）帶入的值以利跨系統串接，但只接受安全字元與長度上限，否則重新產生——
 * 這個值會被印進日誌，不驗證的話呼叫端可帶入換行字元偽造日誌行。
 *
 * <p>排在最前面（早於 Spring Security 過濾鏈），使 401／403／429 這類在鏈內就被擋下的回應也帶得到。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern ACCEPTABLE_INBOUND_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(RequestId.HEADER);
        if (requestId == null || !ACCEPTABLE_INBOUND_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(RequestId.MDC_KEY, requestId);
        response.setHeader(RequestId.HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
    }
}
