package com.nextkey.ecommerce.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Sprint 209（DEF-252／253 接上真實寄信服務）：SmtpEmailSender（Google Workspace SMTP）。
 *
 * <p>本類別不驗證真的能連上 Google 的 SMTP 伺服器（那是人工在 Stripe 測試模式走查之外，另一項
 * AI 無法代替的手動驗證），只驗證：組出的 {@link SimpleMailMessage} 內容正確、失敗時包裝成
 * {@link EmailDeliveryException}、寄件位址的預設/覆寫邏輯，以及最重要的——內容絕不進日誌
 * （比照 {@code LoggingEmailSenderTest} 的隱私守門慣例）。
 */
@DisplayName("Sprint 209: SmtpEmailSender（Google Workspace SMTP）")
class SmtpEmailSenderTest {

    private static final String LINK = "http://localhost:3000/reset-password?token=SECRET-TOKEN-VALUE";
    private static final String TO = "someone@example.com";
    private static final String USERNAME = "noreply@example-workspace.com";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(SmtpEmailSender.class);
        appender.start();
        logger.addAppender(appender);
        mailSender = mock(JavaMailSender.class);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private List<String> loggedMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("UT-SMTP-001: from 未設定 → 沿用 spring.mail.username；成功寄送不進日誌任何內容")
    void send_success_usesUsernameAsFromByDefault_neverLogsContent() {
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, USERNAME, "");

        sender.send(TO, "重設密碼", "請點 " + LINK);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo(USERNAME);
        assertThat(sent.getTo()).containsExactly(TO);
        assertThat(sent.getSubject()).isEqualTo("重設密碼");
        assertThat(sent.getText()).contains(LINK);

        assertThat(sender.canDeliver()).isTrue();
        assertThat(loggedMessages())
                .noneMatch(m -> m.contains("SECRET-TOKEN-VALUE") || m.contains(LINK) || m.contains(TO));
    }

    @Test
    @DisplayName("UT-SMTP-002: 已設定 app.mail.from → 覆寫 username 作為寄件位址")
    void send_configuredFrom_overridesUsername() {
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, USERNAME, "alias@example-workspace.com");

        sender.send(TO, "subject", "body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("alias@example-workspace.com");
    }

    @Test
    @DisplayName("UT-SMTP-003: JavaMailSender 拋 MailException → 包裝成 EmailDeliveryException，不記內容只記 subject")
    void send_mailExceptionWrappedAsEmailDeliveryException() {
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, USERNAME, "");
        doThrow(new MailSendException("smtp boom")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.send(TO, "重設密碼", "請點 " + LINK))
                .isInstanceOf(EmailDeliveryException.class)
                .hasCauseInstanceOf(MailSendException.class);

        assertThat(loggedMessages())
                .noneMatch(m -> m.contains("SECRET-TOKEN-VALUE") || m.contains(LINK) || m.contains(TO));
    }
}
