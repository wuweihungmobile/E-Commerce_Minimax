# Sprint 209 Plan — 接上真實寄信服務（Google Workspace SMTP）

**Sprint**: Sprint 209
**日期**: 2026-09-27

## 1. 起點

Sprint 203～208 收尾後，使用者以 `AskUserQuestion` 選擇「真實寄信服務要接哪一個」，選項為 SendGrid（推薦）／Amazon SES／一般 SMTP／先不接。使用者選「Other」並回覆「我使用google workspace，請改使用這個」——不在選項內，但意圖明確：用 Google Workspace 帳號寄信，屬於 SMTP 整合的一種。

## 2. 為什麼是新增一個實作，不是改介面

`EmailSender`（Sprint 204）與 `LoggingEmailSender` 的既有 Javadoc 已經預告了這一天：「接上真實寄信服務時：新增 `EmailSender` 實作並標 `@Primary`，本類別即不再被注入。」呼叫端（`AccountSecurityService`）完全不用改。

## 3. Google Workspace SMTP 的實作細節

- **協定**：走 `spring-boot-starter-mail`（`JavaMailSender`），對 `smtp.gmail.com:587`（STARTTLS）。沒有用 Gmail API／服務帳號——那需要在 Google Cloud 建專案＋服務帳號＋Workspace 網域授權，設定成本高很多，一般 SMTP 就能滿足忘記密碼／Email 驗證這種低量交易信的需求。
- **兩個 Google 特有限制，已寫進 class 註解**：
  1. **應用程式密碼**：`spring.mail.password` 必須是 App Password（16 碼），不是登入密碼；該帳號要先開兩步驟驗證才能產生。**若組織的 Workspace 管理員已停用應用程式密碼，本實作無法使用**，要改走 OAuth2（XOAUTH2），本輪未做。
  2. **寄件位址（From）**：Gmail 只接受寄件位址是驗證帳號本身，或該帳號已設定的「寄件別名」，否則拒絕或直接改回驗證帳號。`app.mail.from` 預設沿用 `spring.mail.username`，要用別的位址得先在 Google 帳戶設定加別名。

## 4. 兩個只有跑真實 Spring context 才發現的問題

寫完程式碼、單元測試全綠之後，我自己先用一個臨時的 `@SpringBootTest` 驗證「沒設定 SMTP 時注入的是哪個 bean」，結果發現：

### 4.1 `@ConditionalOnProperty` 判斷不出「空字串」

一開始用 `@ConditionalOnProperty(prefix = "spring.mail", name = "username")` 作為 `SmtpEmailSender` 的啟用條件。但 `application.yml` 給 `spring.mail.username` 的預設值是 `${SMTP_USERNAME:}`（空字串，不是完全沒有這個鍵）——`@ConditionalOnProperty` 只看鍵存不存在，不看值是否為空字串，所以在**完全沒設定 SMTP 的環境（本機開發／CI／既有測試）也會誤判為已設定**，把 `SmtpEmailSender` 建出來取代 `LoggingEmailSender`。改用 `@ConditionalOnExpression("!'${spring.mail.username:}'.isEmpty()")`，判斷的是**解析後的字串值**，才正確。

### 4.2 `spring-boot-starter-mail` 自動掛的健康檢查會連外網

`spring-boot-starter-mail` 一上 classpath，Actuator 就自動註冊 `MailHealthIndicator`；而 `spring.mail.host` 給了非空的真實預設值 `smtp.gmail.com`（沒設定 `SMTP_HOST` 時也一樣），使 `JavaMailSender` bean **永遠存在**，這個健康檢查因此永遠會被觸發，對外連 `smtp.gmail.com:587` 做即時連線測試。

**這個問題不是理論上的**：commit 完後跑 `git push`，pre-push 的 `make validate-push` 在 act 容器裡啟動後端做 schema 漂移檢查，容器連不到（或連很慢）Google 的 SMTP，導致整體 `/api/actuator/health` 逾時，`validate-schema.sh` 的 150 秒健康等待失敗，**push 被擋下**。用 `management.health.mail.enabled: false` 停用——寄信只是輔助功能，不該讓整個應用的健康狀態（k8s/docker healthcheck、部署守門用）被一個外部 SMTP 連線測試綁架。

## 5. 實作內容

- `pom.xml`：新增 `spring-boot-starter-mail`。
- `application.yml`：
  - `spring.mail.*`（host/port 給 Google 的真實預設值，username/password 留空表示未設定）。
  - `app.mail.from`（留空表示沿用 username）。
  - `management.health.mail.enabled: false`。
- `SmtpEmailSender`（新）：`@Primary @ConditionalOnExpression`，`send()` 失敗包裝成 `EmailDeliveryException`，內容與收件人皆不進日誌（比照 `LoggingEmailSender` 在 prod 的做法、`AccountSecurityService` 只記 `userId` 不記 email 的既有慣例）。

## 6. 測試

| 測試 | 守什麼 |
|------|--------|
| `SmtpEmailSenderTest`（3，純 mock） | from 預設沿用 username／可被 `app.mail.from` 覆寫；`MailException` 包裝成 `EmailDeliveryException`；失敗時日誌不含收件人／連結／token |
| `EmailSenderWiringIntegrationTest`（2，真實 context） | 未設定 SMTP → 注入 `LoggingEmailSender`（不是 `SmtpEmailSender`）；`MailHealthIndicator` bean 不存在 |
| `SmtpEmailSenderWiringIntegrationTest`（1，真實 context，`@DynamicPropertySource` 設定 username） | 設定了 SMTP → 注入 `SmtpEmailSender`（不是 `LoggingEmailSender`） |

**紅燈先行、突變驗證**（4 種，全被抓到）：
- `SmtpEmailSender` 內：from 永遠用 username 忽略覆寫、失敗吞掉不包裝、錯誤訊息把收件人/內容記進日誌——3 種皆被 `SmtpEmailSenderTest` 抓到。
- 把 `@ConditionalOnExpression` 換回 `@ConditionalOnProperty`（回到 §4.1 的原始 bug）→ `EmailSenderWiringIntegrationTest` 變紅（UT-EMAIL-WIRING-001）。
- 把 `management.health.mail.enabled` 改回 `true`（回到 §4.2 的原始問題）→ `EmailSenderWiringIntegrationTest` 變紅（UT-EMAIL-WIRING-002）。

## 7. 誠實揭露

- **沒有真實憑證，未對真實 Google Workspace SMTP 伺服器驗證過**——沒有真的寄出過一封信。所有測試都是對 mock 或本機 context 驗證，沒有網路層驗證。
- **這是人工才能做的部分**：你要提供 `SMTP_USERNAME`（Workspace 帳號）、`SMTP_PASSWORD`（該帳號的應用程式密碼，先確認開了兩步驟驗證）；若要用不同的寄件位址，先在該帳號設好寄件別名。設好環境變數後，建議先用忘記密碼流程手動測一次，確認信真的能收到——這是驗證本輪修復是否真的有效的唯一方式，我沒辦法代替你做。
- 若貴組織 Workspace 管理員停用了應用程式密碼，本實作會無法使用（SMTP 認證會被拒絕），需改走 OAuth2，屆時再處理。

## 8. 驗證結果

**過程中的環境插曲**：Sprint 208 的 push 因本輪 §4.2 發現的問題被 pre-push 擋下一次（`schema-gate` 150 秒健康等待逾時），修好後 test DB 需重新 `make test-db-up`（push 的 `test-db-down` 會停用它）才能繼續跑本機驗證。

**單獨執行、test DB 就位、過程中未與任何其他 `mvn` 呼叫並行**：`mvn -o clean verify`

- 單元 **1788**（+3：`SmtpEmailSenderTest`）
- 整合 **568**（+3：`EmailSenderWiringIntegrationTest` 2、`SmtpEmailSenderWiringIntegrationTest` 1）
- 0 failures／0 errors／0 skipped
- checkstyle（main＋test）0 violations
- `BUILD SUCCESS`，9:43 min

**未執行**：對真實 Google Workspace SMTP 的連線驗證（沒有憑證，見 §7）；E2E 與 `make validate-release`；本輪沒有 Flyway 遷移。

## 9. Push

**尚未 push。** 新功能，且上一輪 push（Sprint 208）已因本輪發現的 §4.2 問題被 pre-push 擋下一次；本輪修好後需重新走完整 push 流程。
