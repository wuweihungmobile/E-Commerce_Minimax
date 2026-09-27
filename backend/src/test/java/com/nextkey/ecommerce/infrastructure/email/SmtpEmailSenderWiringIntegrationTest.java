package com.nextkey.ecommerce.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Sprint 209：{@code spring.mail.username} 有設定時，真實 context 注入的是 {@link SmtpEmailSender}
 * 而非 {@link LoggingEmailSender}（與 {@link EmailSenderWiringIntegrationTest} 相反的情境，
 * 獨立成另一個 context 以免 Spring Test 快取把兩種屬性組合混在一起）。
 *
 * <p>不驗證真的能連上 Google 的 SMTP 伺服器——那需要真實憑證，是人工手動驗證的範圍。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("Sprint 209: EmailSender 真實 context 注入（已設定 SMTP）")
class SmtpEmailSenderWiringIntegrationTest {

    @DynamicPropertySource
    static void smtpProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.mail.username", () -> "noreply@example-workspace.com");
        registry.add("spring.mail.password", () -> "app-password-placeholder");
    }

    @Autowired
    private EmailSender emailSender;

    @Test
    @DisplayName("UT-EMAIL-WIRING-003: 設定 spring.mail.username → 注入 SmtpEmailSender（不是 LoggingEmailSender）")
    void withSmtpConfig_injectsSmtpEmailSender() {
        assertThat(emailSender).isInstanceOf(SmtpEmailSender.class);
    }
}
