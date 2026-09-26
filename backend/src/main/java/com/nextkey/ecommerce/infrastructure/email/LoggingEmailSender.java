package com.nextkey.ecommerce.infrastructure.email;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 日誌型 Mock 寄信實作（Sprint 204，PRD §7.4.2）：不真的寄信，只寫日誌。
 *
 * <ul>
 *   <li><b>非 {@code prod} profile</b>：把信件全文（含一次性連結）寫進日誌，供開發與自動化測試取用連結。
 *       {@link #canDeliver()} 為 {@code true}——連結取得得到，前置條件照常生效。</li>
 *   <li><b>{@code prod} profile</b>：<b>不記錄內容</b>。連結等同密碼重設憑證，任何能讀日誌的人都能接管帳號，
 *       不可進日誌。{@link #canDeliver()} 為 {@code false}，並在啟動時記一筆 WARN。</li>
 * </ul>
 *
 * <p>接上真實寄信服務時：新增 {@link EmailSender} 實作並標 {@code @Primary}，本類別即不再被注入。
 */
@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    private final boolean logContent;

    public LoggingEmailSender(final Environment environment) {
        this.logContent = !environment.acceptsProfiles(Profiles.of("prod"));
        if (!logContent) {
            log.warn("[EMAIL] 正式環境尚未接上寄信服務：忘記密碼與 Email 驗證不會寄出任何信件，"
                    + "開店申請的 Email 驗證前置條件不生效（PRD §7.4.2）。上線前須新增真實的 EmailSender 實作");
        }
    }

    @Override
    public void send(final String to, final String subject, final String body) {
        if (logContent) {
            log.info("[MOCK-EMAIL] to={} subject={} body={}", to, subject, body);
        } else {
            log.warn("[MOCK-EMAIL] 有一封信未寄出（無真實寄信服務，內容依規定不記錄）：subject={}", subject);
        }
    }

    @Override
    public boolean canDeliver() {
        return logContent;
    }
}
