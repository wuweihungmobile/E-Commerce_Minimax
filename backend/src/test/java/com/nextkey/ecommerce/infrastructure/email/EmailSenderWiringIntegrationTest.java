package com.nextkey.ecommerce.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.mail.MailHealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sprint 209（DEF-252／253 接上真實寄信服務）：真實 Spring context 驗證兩件事，兩者都是本輪
 * 實測才發現、單元測試看不到的問題。
 *
 * <p><b>問題一</b>：一開始用 {@code @ConditionalOnProperty(prefix = "spring.mail", name = "username")}
 * 做為 {@link SmtpEmailSender} 的啟用條件，在真實 context 下發現完全沒設定 SMTP 的環境仍然注入
 * {@link SmtpEmailSender}——因為 {@code application.yml} 給了 {@code ${SMTP_USERNAME:}} 空字串
 * 預設值，該屬性鍵永遠「存在」，{@code @ConditionalOnProperty} 只看鍵是否存在、不看值是否為空。
 * 改用 {@code @ConditionalOnExpression} 判斷解析後的字串是否為空才修復（見 {@link SmtpEmailSender}）。
 *
 * <p><b>問題二</b>：{@code spring-boot-starter-mail} 在 classpath 上即自動註冊
 * {@link MailHealthIndicator}，會對外連 SMTP 伺服器做即時連線測試；{@code spring.mail.host} 給了
 * 非空的真實預設值（{@code smtp.gmail.com}），使 {@code JavaMailSender} bean 永遠存在，該健康檢查
 * 因此永遠會被觸發。在沒有出站網路連到 Gmail 的環境（本輪在 act 容器內實測到），這個健康檢查會
 * 卡住或失敗，拖累整體 {@code /api/actuator/health} 逾時，導致 {@code validate-schema.sh} 的
 * 150 秒健康等待失敗——push 因此被擋（真實發生過，不是假設）。已用
 * {@code management.health.mail.enabled: false} 停用。
 *
 * <p>本檔測「沒設定 SMTP」的預設情境（本機開發／CI／既有測試皆是如此）；「設定了 SMTP」的情境見
 * {@link SmtpEmailSenderWiringIntegrationTest}。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("Sprint 209: EmailSender 真實 context 注入（未設定 SMTP）")
class EmailSenderWiringIntegrationTest {

    @Autowired
    private EmailSender emailSender;

    @Autowired(required = false)
    private MailHealthIndicator mailHealthIndicator;

    @Test
    @DisplayName("UT-EMAIL-WIRING-001: 未設定 spring.mail.username → 注入 LoggingEmailSender（不是 SmtpEmailSender）")
    void withoutSmtpConfig_injectsLoggingEmailSender() {
        assertThat(emailSender).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    @DisplayName("UT-EMAIL-WIRING-002: MailHealthIndicator 已停用（management.health.mail.enabled=false）")
    void mailHealthIndicator_isDisabled() {
        assertThat(mailHealthIndicator).isNull();
    }
}
