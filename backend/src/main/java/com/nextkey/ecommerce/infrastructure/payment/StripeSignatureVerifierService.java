package com.nextkey.ecommerce.infrastructure.payment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Stripe Webhook Signature 驗證服務
 *
 * 使用 HMAC-SHA256 驗證 Stripe 發送的 webhook 請求，與 Stripe SDK
 * Webhook.constructEvent() 的底層演算法相同。
 *
 * Stripe signature header 格式：t=timestamp,v1=sig1,v1=sig2,...
 *
 * Phase 3: 可改用 Stripe SDK Webhook.constructEvent() 並解析事件型別
 */
@Slf4j
@Service
public class StripeSignatureVerifierService {

    static final long TOLERANCE_SECONDS = 300L;
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 驗證 Stripe webhook signature。
     * 若 webhookSecret 為空，跳過驗證（測試模式）。
     *
     * @param payload      raw request body (必須是原始字串，不可 parse 後重新序列化)
     * @param sigHeader    Stripe-Signature header 值
     * @param webhookSecret stripe.webhook-secret 設定值
     * @throws BusinessException E_5015 驗證失敗
     */
    public void verify(String payload, String sigHeader, String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.debug("Stripe webhook secret not configured — skipping signature verification (test mode)");
            return;
        }

        if (sigHeader == null || sigHeader.isEmpty()) {
            log.error("[E_5015] Missing Stripe-Signature header");
            throw new BusinessException(ErrorCode.E_5015, "Missing Stripe-Signature header");
        }

        long timestamp = parseTimestamp(sigHeader);
        List<String> v1Signatures = parseV1Signatures(sigHeader);

        if (v1Signatures.isEmpty()) {
            log.error("[E_5015] No v1 signature found in Stripe-Signature header");
            throw new BusinessException(ErrorCode.E_5015, "No v1 signature in header");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > TOLERANCE_SECONDS) {
            log.error("[E_5015] Stripe webhook timestamp outside tolerance: diff={}s", Math.abs(now - timestamp));
            throw new BusinessException(ErrorCode.E_5015, "Webhook timestamp outside tolerance window");
        }

        String signedPayload = timestamp + "." + payload;
        String expected = hmacSha256Hex(webhookSecret, signedPayload);

        boolean matched = v1Signatures.stream()
                .anyMatch(v1 -> MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        v1.getBytes(StandardCharsets.UTF_8)));

        if (!matched) {
            log.error("[E_5015] Stripe webhook signature mismatch");
            throw new BusinessException(ErrorCode.E_5015, "Webhook signature mismatch");
        }

        log.debug("Stripe webhook signature verified successfully");
    }

    private long parseTimestamp(String sigHeader) {
        for (String part : sigHeader.split(",")) {
            if (part.startsWith("t=")) {
                try {
                    return Long.parseLong(part.substring(2).trim());
                } catch (NumberFormatException e) {
                    log.error("[E_5015] Invalid timestamp in Stripe-Signature header: {}", part);
                    throw new BusinessException(ErrorCode.E_5015, "Invalid timestamp in Stripe-Signature header");
                }
            }
        }
        log.error("[E_5015] Missing timestamp in Stripe-Signature header");
        throw new BusinessException(ErrorCode.E_5015, "Missing timestamp in Stripe-Signature header");
    }

    private List<String> parseV1Signatures(String sigHeader) {
        List<String> sigs = new ArrayList<>();
        for (String part : sigHeader.split(",")) {
            if (part.startsWith("v1=")) {
                sigs.add(part.substring(3).trim());
            }
        }
        return sigs;
    }

    private String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new BusinessException(ErrorCode.E_9900, "HMAC-SHA256 computation failed: " + e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
