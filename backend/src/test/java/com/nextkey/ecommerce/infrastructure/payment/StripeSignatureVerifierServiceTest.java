package com.nextkey.ecommerce.infrastructure.payment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * StripeSignatureVerifierService 單元測試
 * AC-004: 有效 / 無效 / 缺少 signature 三大情境
 */
@DisplayName("StripeSignatureVerifierService Tests")
class StripeSignatureVerifierServiceTest {

    private StripeSignatureVerifierService verifier;

    private static final String TEST_SECRET = "whsec_test_secret_for_unit_testing_only";
    private static final String TEST_PAYLOAD = "{\"type\":\"payment_intent.succeeded\",\"id\":\"evt_test\"}";

    @BeforeEach
    void setUp() {
        verifier = new StripeSignatureVerifierService();
    }

    @Test
    @DisplayName("TC-001: 測試模式（secret 未配置）— 任何 signature 皆通過")
    void testMode_emptySecret_skipVerification() {
        assertDoesNotThrow(() -> verifier.verify(TEST_PAYLOAD, null, ""));
        assertDoesNotThrow(() -> verifier.verify(TEST_PAYLOAD, null, null));
        assertDoesNotThrow(() -> verifier.verify(TEST_PAYLOAD, "t=0,v1=invalid", ""));
    }

    @Test
    @DisplayName("TC-002: 有效 signature — 驗證通過")
    void validSignature_passes() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + TEST_PAYLOAD;
        String sig = computeHmacSha256Hex(TEST_SECRET, signedPayload);
        String sigHeader = "t=" + timestamp + ",v1=" + sig;

        assertDoesNotThrow(() -> verifier.verify(TEST_PAYLOAD, sigHeader, TEST_SECRET));
    }

    @Test
    @DisplayName("TC-003: 無效 signature — 拋出 E_5015")
    void invalidSignature_throwsE5015() {
        long timestamp = Instant.now().getEpochSecond();
        String sigHeader = "t=" + timestamp + ",v1=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifier.verify(TEST_PAYLOAD, sigHeader, TEST_SECRET));
        assertEquals(ErrorCode.E_5015, ex.getErrorCode());
    }

    @Test
    @DisplayName("TC-004: 缺少 Stripe-Signature header — 拋出 E_5015")
    void missingSignatureHeader_throwsE5015() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifier.verify(TEST_PAYLOAD, null, TEST_SECRET));
        assertEquals(ErrorCode.E_5015, ex.getErrorCode());
    }

    @Test
    @DisplayName("TC-005: Signature header 為空字串 — 拋出 E_5015")
    void emptySignatureHeader_throwsE5015() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifier.verify(TEST_PAYLOAD, "", TEST_SECRET));
        assertEquals(ErrorCode.E_5015, ex.getErrorCode());
    }

    @Test
    @DisplayName("TC-006: 過期 timestamp（超過 300 秒容忍視窗）— 拋出 E_5015")
    void expiredTimestamp_throwsE5015() throws Exception {
        long expiredTimestamp = Instant.now().getEpochSecond() - (StripeSignatureVerifierService.TOLERANCE_SECONDS + 10);
        String signedPayload = expiredTimestamp + "." + TEST_PAYLOAD;
        String sig = computeHmacSha256Hex(TEST_SECRET, signedPayload);
        String sigHeader = "t=" + expiredTimestamp + ",v1=" + sig;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifier.verify(TEST_PAYLOAD, sigHeader, TEST_SECRET));
        assertEquals(ErrorCode.E_5015, ex.getErrorCode());
    }

    private String computeHmacSha256Hex(String secret, String data)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
