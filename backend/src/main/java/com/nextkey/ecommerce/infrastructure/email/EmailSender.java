package com.nextkey.ecommerce.infrastructure.email;

/**
 * 寄信介面（Sprint 204，DEF-252／253；PRD §7.4.2）。
 *
 * <p>忘記密碼與 Email 驗證都要寄出含一次性連結的信。寄信通道刻意抽成介面：Phase 1 只有日誌型 Mock
 * （{@link LoggingEmailSender}），日後接 SMTP／SendGrid／SES 只需新增一個實作並標 {@code @Primary}，
 * 呼叫端不必動。
 */
public interface EmailSender {

    /**
     * 寄出一封純文字信。
     *
     * @throws EmailDeliveryException 寄送失敗；呼叫端依情境決定是否吞掉（註冊時寄驗證信失敗不可讓註冊失敗）
     */
    void send(String to, String subject, String body);

    /**
     * 這個實作是否真的能讓收件人拿到信。
     *
     * <p>用途：「開店申請需已驗證 Email」這個前置條件只在此為 {@code true} 時才生效——沒有信可寄，
     * 申請者就無從驗證，強制檢查只會把所有人鎖在門外（PRD §7.4.2）。
     */
    boolean canDeliver();
}
