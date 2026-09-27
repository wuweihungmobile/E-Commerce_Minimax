package com.nextkey.ecommerce.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 真實寄信實作：Google Workspace SMTP（Sprint 209，接上 DEF-252／253）。
 *
 * <p>只在 {@code spring.mail.username} 有設定時才會被建立並取代 {@link LoggingEmailSender}
 * （見 {@code @ConditionalOnExpression}）。留空時（本機開發／CI／既有測試皆是如此）這個 bean
 * 完全不會被建立，行為與接上真實服務之前一模一樣——包含 E2E 從 {@code LoggingEmailSender}
 * 日誌取一次性連結的既有機制，不受影響。
 *
 * <p><b>刻意不用 {@code @ConditionalOnProperty}</b>：{@code application.yml} 用
 * {@code ${SMTP_USERNAME:}} 給了空字串預設值，該屬性鍵永遠「存在」（只是值可能是空字串），
 * {@code @ConditionalOnProperty} 只判斷鍵是否存在、不判斷是否為空字串，會在完全沒設定
 * SMTP 的環境誤判為已設定（已用真實 Spring context 的整合測試驗證過這個誤判會真的發生）。
 * {@code @ConditionalOnExpression} 對已解析的字串值判斷是否為空，才是正確的閘門。
 *
 * <p><b>Google Workspace 的兩個限制，接上前務必知道</b>：
 * <ul>
 *   <li><b>應用程式密碼</b>：{@code spring.mail.password} 必須是應用程式密碼（App Password），
 *       不是該帳號的登入密碼；帳號需先在 Google 帳戶設定開啟兩步驟驗證才能產生一組。若組織的
 *       Workspace 管理員已停用應用程式密碼，本實作無法使用，須改走 OAuth2（XOAUTH2），
 *       本輪未實作。</li>
 *   <li><b>寄件位址（From）只能是驗證帳號本身，或該帳號已設定的寄件別名</b>：Gmail 對其他位址
 *       會拒絕寄送或直接改回驗證帳號。{@code app.mail.from} 因此預設沿用
 *       {@code spring.mail.username}；要改成別的位址，須先在該 Google 帳戶「設定 → 帳戶與
 *       匯入 → 代收郵件」新增並完成驗證。</li>
 * </ul>
 *
 * <p>寄信內容（含一次性連結，等同帳號憑證）不寫入日誌，比照 {@link LoggingEmailSender} 在
 * {@code prod} 的做法；連收件人 email 也不記（比照 {@code AccountSecurityService} 全程只以
 * {@code userId} 記錄，不記 email，見既有慣例）。
 */
@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${spring.mail.username:}'.isEmpty()")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(final JavaMailSender mailSender,
            @Value("${spring.mail.username}") final String username,
            @Value("${app.mail.from:}") final String configuredFrom) {
        this.mailSender = mailSender;
        this.fromAddress = configuredFrom.isBlank() ? username : configuredFrom;
        log.info("[EMAIL] 真實寄信服務已啟用（Google Workspace SMTP）");
    }

    @Override
    public void send(final String to, final String subject, final String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("[EMAIL] 寄送失敗：subject={}", subject, e);
            throw new EmailDeliveryException("Failed to send email via SMTP", e);
        }
    }

    @Override
    public boolean canDeliver() {
        return true;
    }
}
