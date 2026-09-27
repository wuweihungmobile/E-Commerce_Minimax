# Sprint 211 Plan — 忘記密碼端點的時序側通道（DEF-290）

**Sprint**: Sprint 211
**日期**: 2026-09-28

## 1. 起點

Sprint 210 收尾後，`DEFERRED_ITEMS_TRACKER.md` 再次無 AI 可獨立處理的活躍待辦（`myTodoList.md` 三項仍需使用者操作實際環境）。延續 Sprint 182 起「自選掃描角度」的既有模式，本輪自選角度前先重新檢視 Sprint 209（接上 Google Workspace SMTP 真實寄信服務）留下的程式碼註解與已知限制清單，而非另尋全新角度——因為 Sprint 209 本身就在 `AccountSecurityService` 類別 Javadoc 明確記下一條「已知限制」尚未處理。

## 2. 本輪掃描角度：Sprint 209 自己記下的已知限制是否已隨真實 SMTP 上線而變成真實風險

`AccountSecurityService`（Sprint 204 建立）原本的類別註解寫著：

> 已知限制（時序）：申請重設連結時，「帳號存在」的路徑比「不存在」多做 Redis 與寄信，回應時間會有差異。目前的 Mock 寄信是即時的，差異可忽略；接上真實寄信服務（會阻塞）之後，應改為非同步寄送，否則會成為 Email 是否已註冊的時序側通道。

Sprint 209 已把 `LoggingEmailSender`（即時 mock）換成 `SmtpEmailSender`（真實連線 `smtp.gmail.com` 的阻塞式 `JavaMailSender.send()`），但沒有一併處理這條注釋預告的風險，`sendPasswordResetMail` 仍在 `AuthController.forgotPassword` 的請求執行緒中同步呼叫 `emailSender.send(...)` 才回應。

### 2.1 查證：這是否為真實、目前存在的風險

- `POST /api/auth/password/forgot` → `AccountSecurityService.requestPasswordReset` → 帳號存在且有密碼、未在冷卻中時 → `sendPasswordResetMail` 同步呼叫 `emailSender.send(...)`。
- 一旦部署環境設定了 `SMTP_USERNAME`（`myTodoList.md` §1 待辦），`SmtpEmailSender` 會取代 `LoggingEmailSender` 成為 `@Primary` bean（`@ConditionalOnExpression`），`send()` 內部是同步、阻塞的 `mailSender.send(message)`（真實 TLS SMTP 握手＋遞送，非本機/CI 環境常見耗時為數百毫秒至數秒）。
- 對照組（Email 不存在／OAuth-only 帳號／60 秒冷卻中）完全不會呼叫 `emailSender.send`，`requestPasswordReset` 幾乎立即回應。
- 三種路徑中兩種（不存在、冷卻中）回應時間相同且極快，只有「帳號存在且不在冷卻中」這條路徑會被真實 SMTP 延遲拖慢——足以構成可觀測的帳號列舉時序側通道，且完全命中 Sprint 204 原註解預告的情境。這不是理論推測，而是 Sprint 209 已完成的變更把一個原本被註解記錄「目前差異可忽略」的已知限制，轉為真實存在的風險。

**結論**：判定為真實缺口（DEF-290），且修法在 Sprint 204 的註解裡已經寫明方向（「應改為非同步寄送」），非新的業務判斷，屬於可直接修復範圍。

## 3. 修復

`AccountSecurityService` 新增一個僅供此用途、不需要 Spring 生命週期管理的 `Executor passwordResetMailExecutor = Executors.newVirtualThreadPerTaskExecutor()`（JDK 21 虛擬執行緒；`final` 欄位有就地初始化，Lombok `@RequiredArgsConstructor` 不會將其視為建構子參數，不影響現有依賴注入與既有測試的 `@InjectMocks`）。

`sendPasswordResetMail` 內，冷卻檢查與 `tokenService.issue`（皆為快速的 Redis 操作，同步執行、維持既有「已簽發即立刻生效」的語意）之後，把實際寄信這一步（含既有的 `EmailDeliveryException` 吞例外與記錄）整段丟進 `passwordResetMailExecutor.execute(...)`；`requestPasswordReset` 因此不再等待任何網路 I/O 就回應，三條路徑（存在、不存在、冷卻中）的回應時間不再有可觀測差異。

**刻意不擴大範圍**：`sendEmailVerification`（會員主動要求重寄驗證信）維持同步呼叫，因為它是已登入會員的顯式動作、失敗時要如實回報 `E_9905` 讓使用者知道沒寄出（`AC-M03-007-3` 既有契約），若改成非同步會讓呼叫端拿不到寄信是否成功的訊號，這是需要保留的既有行為而非本次缺口範圍；且該端點需要登入，呼叫者已經知道自己的帳號存在，不構成列舉攻擊面。`sendEmailVerificationQuietly`（註冊後背景寄信）本來就已吞掉所有例外、不影響回應內容，其耗時只影響註冊 API 的延遲而非資訊洩漏，同樣不在本次缺口範圍內。

## 4. 測試

**紅燈先行**：`AccountSecurityServiceTest` 新增 `RequestPasswordReset.doesNotBlockOnMailDelivery`——用 `CountDownLatch` 讓 mock 的 `emailSender.send(...)` 阻塞最多 2 秒（模擬真實 SMTP 延遲），斷言 `requestPasswordReset(EMAIL)` 必須在 500ms 內回傳（遠短於模擬的 SMTP 延遲），並確認背景執行緒確實有開始寄信。針對修復前的程式碼（`emailSender.send` 直接同步呼叫）實際執行本測試，量測到 `elapsed=2.03s`，斷言 `elapsed < 500ms` 失敗，證實缺口存在。

**修復後**：同一測試轉綠（回應時間不受 mock 延遲影響）。既有 `sendsLinkToActivePasswordAccount`／`deliveryFailure_isSwallowed` 兩案例因寄信改為非同步，原本呼叫後立即 `verify(emailSender)` 的斷言有時間視窗風險，改用 `verify(emailSender, timeout(2000))`（Mockito 輪詢直到符合或逾時），行為斷言本身不變。

**突變驗證**：暫時把 `passwordResetMailExecutor.execute(...)` 包裹拿掉、改回直接同步呼叫 `emailSender.send(...)`，重新編譯後執行整個 `AccountSecurityServiceTest`：新測試正確變回紅燈（`elapsed=2.03s` 斷言失敗），其餘 18 案例不受影響全部通過；還原修復後重新編譯確認全數 19 案例轉綠。

## 5. 驗證結果

- `AccountSecurityServiceTest`：19 tests，0 fail（含本輪新增 1 案例、調整 2 案例的驗證方式）
- checkstyle（main+test）：0 violations
- 全量驗證 `mvn -o verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1791**（+1）／整合 **568**（持平，本輪未新增整合測試）／0 failures／0 errors／0 skipped；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（9:01 min）
- 未變更 entity/migration，未跑 `make validate-schema`
- 未跑 E2E／`make validate-release`

## 6. 後續

- 本項不需要使用者操作，已完整修復並驗證。
- `myTodoList.md` 記載的三項人工待辦（真實寄信服務憑證、維運三件事、Stripe 測試模式走查）狀態不變，仍在使用者手上；一旦使用者完成 SMTP 憑證設定，這項修復會立刻生效，不需額外程式變更。
