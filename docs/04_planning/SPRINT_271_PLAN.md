# Sprint 271 Plan — 通知管道誠實化：EMAIL 真實送出、SMS/PUSH 明確回報未送達（DEF-317）

**Sprint**: Sprint 271
**日期**: 2026-10-15

## 1. 範圍與起點

[SPRINT_270_PLAN.md](SPRINT_270_PLAN.md) 完成後，依使用者拍板順序進入第三項：DEF-317。

DEF-317 原記錄：`NotificationConsumerService` 處理通知訊息時，不論 channel 是 IN_APP／EMAIL／SMS／PUSH，一律把資料列標成 `isSent=true`、寫通知歷史，但實際上只有 IN_APP 是真的「送達」（資料列本身可見即完成）；EMAIL／SMS／PUSH 從未真正寄信、發簡訊、推播。管理員 API（`POST /v2/notifications/send`，僅 SUPER_ADMIN）可指定 `channel=EMAIL`，收件人什麼都收不到，卻顯示已送出。

## 2. 讀碼調查（唯讀 agent，兩輪）

### 2.1 現況確認

`NotificationConsumerService.buildSentNotification`／`markPrecreatedNotificationSent` 兩處皆無條件 `.isSent(true)`，全檔案（含 `NotificationProducerService`／`NotificationService`）沒有任何呼叫 `EmailSender` 或任何簡訊/推播閘道的程式碼。`EmailSender` 介面（Sprint 204，DEF-252/253）已有兩個實作：`SmtpEmailSender`（`@Primary`，條件式啟用於 `spring.mail.username` 非空）、`LoggingEmailSender`（其餘情況，`canDeliver()` 在非 prod 回 true、prod 回 false）。全庫唯一真實呼叫 `EmailSender` 的既有程式碼是 `AccountSecurityService`（忘記密碼、Email 驗證），與通知管線無關。SMS／PUSH 連 stub 都不存在（`grep` 全庫零命中任何簡訊/推播閘道類別）。

### 2.2 影響範圍確認（第二輪調查）

全庫唯一能建立 EMAIL channel 通知的路徑是管理員限定 API（`POST /v2/notifications/send`／`/broadcast`，`@PreAuthorize("hasRole('SUPER_ADMIN')")`）；所有自動化業務邏輯（`BuyerNotificationService` 的 17 個公開方法：訂單/訂房取消、退款、付款成功/失敗等）固定寫死 `channel=IN_APP`，不受本次修改影響。既有測試（`NotificationConsumerServiceTest` 6 案例、`NotificationPipelineRedisIntegrationTest`、`NotificationProduceConsumeTest`）全部只用 IN_APP channel，沒有任何「channel=EMAIL 且斷言 isSent=true」的組合會被打破。

## 3. 做法

`NotificationConsumerService` 新增 `dispatch(NotificationMessage)` 私有方法，依管道決定真實送出結果（`DispatchOutcome(sent, errorMessage)` 私有 record）：

- **IN_APP**：視為必定成功（資料列本身可見即是送達）。
- **EMAIL**：收件地址非空 且 `emailSender.canDeliver()` 為真時才呼叫 `emailSender.send(...)`；`canDeliver()=false`（如正式環境忘記設定 `SMTP_USERNAME`）或 `send()` 拋 `EmailDeliveryException` 時，標記未送達並記錄原因，不呼叫 `send()` 或吞掉例外假裝成功。
- **SMS／PUSH**：目前無任何閘道實作，直接標記未送達並說明原因（不嘗試、不拋例外——拋例外會誤觸訊息層級的重試/DLQ 機制，而這類失敗重試再多次都不會成功）。

`buildSentNotification`／`markPrecreatedNotificationSent` 改用 `dispatch(...)` 的結果決定 `isSent`／`sentAt`／`errorMessage`，不再無條件標記成功。

## 4. 實作內容

| 檔案 | 變更 |
|---|---|
| `backend/.../infrastructure/mq/NotificationConsumerService.java` | 新增 `EmailSender` 依賴、`dispatch()`／`dispatchEmail()`／`DispatchOutcome` record；`buildSentNotification`／`markPrecreatedNotificationSent` 改用真實送出結果 |
| `backend/.../infrastructure/mq/NotificationConsumerServiceTest.java` | 新增 `@Mock EmailSender`；新增 TC-C007~012（EMAIL 成功/canDeliver=false/送出拋例外、SMS/PUSH 未實作、預建列＋EMAIL 失敗四類情境） |
| `backend/.../infrastructure/mq/NotificationProduceConsumeTest.java` | 建構子新增 `EmailSender` mock 參數（既有測試僅用 IN_APP，不受行為影響） |
| `backend/.../infrastructure/mq/NotificationPipelineRedisIntegrationTest.java` | 同上 |
| `backend/.../core/notification/BuyerNotificationService.java` | 更新已過時的類別註解（原文稱 EMAIL 無送出實作，現已不符；同時記錄「是否讓本類別額外送 EMAIL 以落實 PRD US-014」為範圍外的產品決策，見第 6 節） |

## 5. 測試

依 CLAUDE.md「開發-編譯-測試循環」規則：

1. `mvn -o clean test-compile`：首次因新增建構子參數使兩個既有測試檔案編譯失敗（`NotificationProduceConsumeTest`／`NotificationPipelineRedisIntegrationTest` 直接 `new NotificationConsumerService(...)` 少了新參數），修正後 `BUILD SUCCESS`。
2. `mvn -o test -Dtest=NotificationConsumerServiceTest,NotificationProduceConsumeTest`：**Tests run: 14, Failures: 0, Errors: 0**（含新增 6 案例）。
3. 全量 `mvn -o clean verify`（本次變更屬生產程式碼，依 [[sprint-full-regression-policy]] 需跑全量；與 Sprint 270 的 19 個 entity 檔案變更一併驗證，詳見 SPRINT_270_PLAN.md §5 的結果）。

**過程中的環境插曲（非程式碼問題，誠實記錄）**：驗證期間背景執行的 `mvn clean verify` 與同時段 Sprint 269 `git push` 內建的 `validate-schema` 腳本（host 上另一個獨立的 `mvn package` 流程）共用 `backend/target/`，第一次全量驗證因此失敗（`ClassFormatError: Invalid method Code length 0` in `OrderStateLog.class`——該檔案與本次變更完全無關，是兩個 Maven 行程同時寫入造成的檔案損毀）。確認無其他 mvn/push 行程後重跑即恢復正常。

## 6. 範圍外與待使用者決定

- **`BuyerNotificationService` 是否該額外送 EMAIL**：PRD US-014 要求「付款逾時後通知買家並提供重試連結」含 Email/Push，Sprint 229 當時因基礎設施未就位只送站內通知。本次修復讓 EMAIL 基礎設施變得可用，但是否要現在就讓退款/付款逾時等 17 個事件額外寄 Email，涉及信件樣板設計、寄送頻率節制等產品決策，非本次基礎設施修復範圍，留待後續 Sprint 評估。
- **SMS／PUSH 真正接上閘道**：需選定第三方供應商（Twilio/FCM 等）、申請帳號、新增裝置 token 欄位（`Notification.recipient` 目前只存 email/phone 字串，無裝置 token 概念），屬於新功能開發，非本次範圍。
- **DEF-347（SMTP 時序側通道）現況與追蹤表描述有落差**：讀碼確認忘記密碼路徑（`AccountSecurityService.sendPasswordResetMail`）已於 Sprint 211（DEF-290）改為背景虛擬執行緒非同步送出，不構成回應時間側通道；追蹤表／SRD 現有文字仍描述為「同步寄送」已過時。僅剩 Email 驗證重寄（`dispatchEmailVerification`）維持同步，但該端點需登入、不構成未登入狀態下的帳號列舉攻擊面。是否據此更新或降級 DEF-347，留待使用者確認（非本輪變更範圍，僅發現並記錄）。

## 7. Velocity 紀錄

本輪為 1 項缺口修復（通知管道誠實化），非新功能開發，不納入傳統 Story Point 計算。

## 8. 下一步／Action Items

1. 確認全量 `mvn -o clean verify` 結果無回歸（Sprint 270＋271 合併驗證）。
2. 分別整理 Sprint 270、271 各自的 commit（避免混在一起），依序 commit＋push。
3. 回填 push 狀態與雲端 CI 結果。
4. 三個使用者拍板的候選缺口（DEF-361／332／317）至此全數完成；回到 AISDLC 流程盤點是否有新的下一步。
