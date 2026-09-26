package com.nextkey.ecommerce.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Sprint 204：日誌型 Mock 寄信實作。
 *
 * <p>為什麼要守：一次性連結等同密碼重設憑證，任何能讀日誌的人拿到就能接管帳號。
 * 非正式環境要把連結寫進日誌（開發與自動化測試靠它取連結），正式環境則絕不能寫。
 */
@DisplayName("Sprint 204: LoggingEmailSender")
class LoggingEmailSenderTest {

    private static final String LINK = "http://localhost:3000/reset-password?token=SECRET-TOKEN-VALUE";
    private static final String TO = "someone@example.com";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private List<String> loggedMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("非 prod：信件全文（含連結）寫進日誌，且視為可取得連結（canDeliver = true）")
    void nonProd_logsFullContent() {
        LoggingEmailSender sender = new LoggingEmailSender(new MockEnvironment().withProperty("x", "y"));

        sender.send(TO, "重設密碼", "請點 " + LINK);

        assertThat(sender.canDeliver()).isTrue();
        assertThat(loggedMessages()).anyMatch(m -> m.contains(LINK) && m.contains(TO));
    }

    @Test
    @DisplayName("prod：日誌不得出現連結、token、收件人任何一項；canDeliver = false；啟動與寄信各記一筆 WARN")
    void prod_neverLogsLinkTokenOrRecipient() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        LoggingEmailSender sender = new LoggingEmailSender(prod);
        sender.send(TO, "重設密碼", "請點 " + LINK);

        assertThat(sender.canDeliver()).isFalse();
        assertThat(loggedMessages())
                .noneMatch(m -> m.contains("SECRET-TOKEN-VALUE") || m.contains(LINK) || m.contains(TO));
        assertThat(appender.list).hasSize(2).allMatch(event -> event.getLevel().toString().equals("WARN"));
    }
}
