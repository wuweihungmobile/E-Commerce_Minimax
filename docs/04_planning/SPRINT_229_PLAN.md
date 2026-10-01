# Sprint 229 Plan — 買家通知：訂房取消後的退款狀態（PRD US-005）、付款逾時（US-014）、退款完成

**Sprint**: Sprint 229
**日期**: 2026-10-01

## 1. 起點

使用者貼回 Sprint 225～228 的總結並說「請繼續完成任務！」（沒有針對新項目逐一回覆；「繼續」的解讀為**推論**，沿用 Sprint 216／223 的做法：不動需要使用者決定的項目，做總結自己列為「還沒做」且 AI 能獨立處理的事）。

- 最後一個 docs-only commit（`6b9a8c4`）的雲端 CI 上一輪沒等 → 已確認成功（run 36769159893，7 分 25 秒）。
- 總結「還沒做」的兩項：**逾時與退款完成都不通知買家**，以及 `DEF-315`。本輪做第一項；`DEF-315` 動到認證 token 格式，追蹤表建議「單獨一輪處理」，排 Sprint 230。
- `DEF-316` 標的是「等你決定」，使用者沒有回覆，所以不替他決定（新功能加權限模型）。

這不是新功能，而是 Sprint 227「請對齊PRD」時**漏掉的 PRD 驗收標準**：

- PRD US-005（取消與退款）：「取消前 ≥ 24h 全額退款；取消前 < 24h 不退款（Q14）；**取消後即時收到退款狀態通知**」。
- PRD US-014（支付失敗通知提醒）：「若支付超時或金流阻斷，系統發送 Email/Push 通知提示訂單未完成，**並提供重試連結**」。

Sprint 219／225／226／227 都把「通知買家」列為未做，理由是「通知內容是產品決策」。但 PRD 已經寫明要通知買家**什麼事**；內容只是把已決定的結果（Q14 的退不退、退多少）說清楚，不需要新的產品決策。

## 2. 查證（現況）

1. **全庫沒有任何商業事件會產生通知**：`NotificationService.sendNotification` 的呼叫者只有管理員 API（`NotificationController`）。`ORDER_*`／`BOOKING_*`／`PAYMENT_*` 等類型都存在，但沒有人送（見 §6，新登記 `DEF-318`）。
2. **`REFUND_COMPLETED` 只存在一半**：通知「範本」枚舉（`NotificationTemplate.NotificationType`）與前端選項有它，但真正寫入 `notifications` 的 `Notification.NotificationType`、請求用的 `NotificationDto.NotificationType`、資料庫 CHECK 約束（V52）**都沒有**——直接送會先在 `valueOf` 丟例外，過了這關 INSERT 也會被約束擋下。（Sprint 226 總結寫「類型已存在」只對了一半。）
3. **通知管線只有站內通知是真的**：消費者只把預建列標成 `isSent=true` 並寫歷史，EMAIL／SMS／PUSH 沒有任何送出實作（全庫只有 `AccountSecurityService` 呼叫 `EmailSender`）。見 §6 `DEF-317`。
4. **交易邊界有兩個陷阱**：預建列與入佇在同一個 `@Transactional` 方法裡，呼叫端的交易還沒提交就送出，消費者可能先看到訊息卻查不到資料列（只能重試），呼叫端回滾還會留下孤兒訊息；而在 `afterCommit` 回呼裡直接呼叫（`REQUIRED`）會**加入已提交的交易，寫入永遠不會被提交**（Spring 文件的已知行為；§5 突變 M01b 實測：整合測試 16 個有 6 個變紅）。
5. 前端收件匣（`/notifications`）已存在，列表不過濾 `isSent`（預建列一建立就看得到，即使消費者排程被關閉）。但類型標籤沒有 `REFUND_COMPLETED`（會顯示原始字串）、連結只認 `data.orderId`。

## 3. 設計

三種買家通知，全部是站內通知（`IN_APP`）、盡力而為：

| 事件 | 時機 | 類型／標題 | 內容 |
|------|------|-----------|------|
| 訂房取消（買家本人、商家、管理員代為取消） | `cancelBooking` 交易**提交之後** | `ORDER_CANCELLED`／訂房已取消 | 依 Q14 結果分五種：尚未付款不需退款／全額退款處理中（含金額）／入住前不足 24 小時依政策不退款／商家取消（點名商家，含金額）／商家取消卻找不到可退款付款（交客服確認） |
| 未付款逾時取消（訂單、訂房） | 逾時服務取消成功（回傳 `true`）之後 | `ORDER_CANCELLED`／訂單（訂房）因逾期未付款已取消 | 說明超過付款期限已自動取消；訂房附 `listingId`（前端「重新預訂」連結），訂單附 `orderId` |
| 自動退款完成（訂單、訂房） | `RefundProcessingService` 退款成功之後 | `REFUND_COMPLETED`／退款已完成 | 金額與「已退回原付款方式，實際入帳時間依付款機構而定」；訂單金額取付款**實際已退的累計額**，訂房取取消時依 Q14 決定的應退金額 |

**`BuyerNotificationService`**（`core/notification`）：

- **盡力而為、永不拋例外**：通知是取消與退款的附帶效果，Redis 或資料庫暫時失敗不可讓已完成的取消失敗，更不可讓已退的款被排程當成失敗而重試。所有失敗只留警告日誌；代價是失敗的那則通知不會補送。
- **自己開新交易（`REQUIRES_NEW`）**，用 `TransactionTemplate` 而不是 `@Transactional`：在 `@Transactional` 方法裡 catch 內層 `sendNotification` 丟的例外，會留下 rollback-only 標記，離開時仍丟 `UnexpectedRollbackException`（Spring 的既知行為，**本輪沒有另外重現**）；`TransactionTemplate` 配合外層 try/catch 才能真的「永不拋」，單元測試以「送出失敗、讀取失敗都不拋」驗證這個契約。
- 文案是純函式（`bookingCancelledContent` 等），單元測試直接驗證。識別方式與前端一致（`訂單 #{編號前 8 碼}`、有房源標題用「標題」否則 `訂房 #{編號前 8 碼}`、新台幣 `NT$1,234`）。

**掛載點**：

- **退款完成**：`RefundProcessingService.refundOne` 在退款成功之後呼叫，**放在判斷失敗的 try 之外**——通知的問題不會被當成退款失敗而進入退避／重試。`Target` 多一個 `notifyRefunded` 元件。
- **逾時**：兩個 `*TimeoutService.cancelOne` 在 `cancelExpired…` 回傳 `true` 之後呼叫（那時取消的交易已提交；`false`、例外都不通知）。
- **取消訂房**：`BookingService.cancelBooking` 以 `TransactionSynchronization.afterCommit` 註冊（沿用 `AuthService` 寄 Email 驗證信的寫法；沒有交易同步時直接呼叫）。取消前是否已付款（`wasPaid`）由 `cancelBooking` 傳入——取消後的 `refund_status = NONE` 分不出「沒付過款」與「依 Q14 不退」。

**`V86`**：重建 `notifications_notification_type_check`，加入 `REFUND_COMPLETED`；`Notification.NotificationType` 與 `NotificationDto.NotificationType` 補上同一個值（通知偏好設定會自動多出這一項）。

**前端**：收件匣類型標籤加「退款完成」；通知 `data` 帶 `bookingId` 顯示「查看訂房 →」、帶 `listingId` 顯示「重新預訂 →」（PRD US-014 的重試連結；訂單沒有對應的「重新下單」頁，只連到訂單）。

## 4. 實作

- `V86__Notifications_Refund_Completed_Type.sql`；`Notification`／`NotificationDto` 的 `NotificationType` 加 `REFUND_COMPLETED`。
- `BuyerNotificationService`（新）：`notifyBookingCancelled`／`notifyBookingPaymentTimeout`／`notifyOrderPaymentTimeout`／`notifyOrderRefunded`／`notifyBookingRefunded`。
- `RefundProcessingService`（`Target.notifyRefunded`，`refundOne` 在 try 之外呼叫）；`OrderTimeoutService`／`BookingTimeoutService`（`cancelOne`）；`BookingService.cancelBooking`（`notifyAfterCommit`）。
- 前端：`notificationInbox.ts`（類型與標籤）、`(auth)/notifications/page.tsx`（`dataIdOf`、「查看訂房」「重新預訂」連結）；新增 mock 規格 `at-notification-inbox-links.spec.ts`（逾時通知的「重新預訂」要等 24 小時才造得出來，真實後端 E2E 驗證不到）。
- 文件：`API_M06_Booking.md`（取消與逾時段落的通知說明）。

## 5. 驗證

**測試**

- 單元：`BuyerNotificationServiceTest` 19 案例（文案：Q14 五種結果＋識別與金額格式；送出：收件人、類型、站內通道、`data` 帶得出連結、金額取實際已退累計額；失敗隔離：**交易必須是 `REQUIRES_NEW`**、資料列不存在、送出失敗與讀取失敗都不拋）；`RefundProcessingServiceTest` +4（成功才通知、搶輸不通知、訂房、通知發生在退款之後且例外不被當成退款失敗）；`OrderTimeoutServiceTest`／`BookingTimeoutServiceTest` 各 +1（只通知真的取消的、取消之後才通知）；`BookingServiceCancelRefundTest` +6（已付款／未付款／管理員代為取消都通知、取消被拒不通知、**在交易裡等提交之後才通知、回滾永遠不通知**）；`BookingServiceOwnershipTest`、`BookingPromoCodeTest` 補上新依賴的 mock。
- 整合（真實 PostgreSQL；Redis 為 mock，所以這些測試驗證的是資料庫寫入與交易時序，不是佇列消費）：`BookingCancellationRefundIntegrationTest` +4 案例並在 5 個既有案例補斷言（取消即時一則通知、排程退回後再一則「退款已完成」、不足 24 小時說明不退且**不會**有退款完成、未付款、管理員代為取消的收件人是買家；4 個執行緒同時取消買家只收到 1 則、4 個處理者同時退款只通知 1 次、Stripe 失敗不通知而重試成功通知 1 次、被拒的重複取消不再通知）；`OrderAutoRefundIntegrationTest`（退款完成通知、重複執行只一次、一張失敗不影響另一張、併發處理者、遲到付款 `DEF-308` 也通知）；`OrderTimeoutIntegrationTest`／`BookingTimeoutIntegrationTest`（內容與 `data`、沒取消或已付款不通知、重複執行只通知一次）。四個類別共 45 案例。
- 前端：`tsc --noEmit` 乾淨、eslint 0 錯誤。真實後端 E2E `E2E-BPAYR-07／08／09` 擴充：取消請求一回來收件匣就有通知（即時）、退款完成後有 `REFUND_COMPLETED`——**這是 `V86` 在真實 Flyway 資料庫生效的唯一驗證**（整合測試的資料庫由 Hibernate 建立，約束是枚舉自動產生的）；收件匣畫面顯示「退款已完成」「退款完成」標籤與「查看訂單／查看訂房」連結。

**突變驗證**（逐一植入錯誤後跑上述單元與整合測試，**19 個全被抓到**）：

| 突變 | 失敗數 |
|------|--------|
| M01 通知交易 `REQUIRES_NEW` → `REQUIRED`（`afterCommit` 內的寫入不會被提交） | 單元 1；**真實 DB 整合 16 個有 6 個** |
| M02 通知失敗不吞例外（往外拋） | 2 |
| M03 買家取消／商家取消的主詞對調 | 2 |
| M04 拿掉「尚未付款不需退款」分支 | 1 |
| M05 商家取消、已付款卻沒有可退金額，也說「依政策不予退款」 | 1 |
| M06 訂單退款完成通知的金額改用訂單總額（而非實際已退累計額） | 1 |
| M07 退款成功後不通知 | 3 |
| M08 通知放進退款失敗判斷的 try（通知例外被當成退款失敗） | 1 |
| M09 訂房退款完成接成訂單的通知方法 | 1 |
| M10 訂單退款完成接成訂房的通知方法 | 2 |
| M11 訂單逾時：沒取消成功也通知 | 1 |
| M12 訂單逾時：取消後不通知 | 1 |
| M13 訂房逾時：沒取消成功也通知 | 1 |
| M14 訂房逾時：取消後不通知 | 1 |
| M15 取消訂房：不等提交就通知（直接呼叫） | 2 |
| M16 取消訂房：`wasPaid` 永遠 false | 3 |
| M17 取消訂房：商家／管理員取消不通知 | 1 |
| M18 持久化枚舉缺 `REFUND_COMPLETED` | **真實 DB 整合 16 個有 3 個** |

做法備註：突變腳本改完源碼後要等對應 class 的修改時間晚於源碼才開跑。第一版腳本以「Maven 輸出有沒有 `Compiling`」判斷突變是否生效，結果把已經被抓到的 M01 判成無效——實際是 VS Code 的 Java 語言服務在改檔後約 1 秒內就把 class 編好，Maven 於是印 `Nothing to compile`，測試跑的確實是突變後的版本。**未做突變驗證**：前端收件匣（沒有單元測試框架，靠 E2E）與 `V86` 本身（只有 `validate-e2e` 驗得到；整合測試資料庫的約束是 Hibernate 由枚舉產生的）。

**全量**（`mvn -o clean verify`，23 分 19 秒）：單元 1961（+31）／整合 678（+4）／0 失敗；checkstyle 0 違規（主程式碼與測試）、PMD 通過。前端 `next build` 成功、`tsc --noEmit` 乾淨、eslint 0 錯誤（95 個警告，與 Sprint 224 之後的基準相同）。`make validate-schema-doc`：86 個 Flyway 遷移在乾淨 PostgreSQL 上依序套用成功、文件與 schema 一致。`make validate-e2e`（乾淨 PostgreSQL＋Flyway 套用到 V86、`ddl-auto=validate`、打包 JAR＋`npm start`，復用剛驗證過的 JAR）：**111 個測試，107 通過／4 略過／0 失敗**（4.6 分；比 Sprint 227 多的 3 個是新的 `E2E-NINBOX-01～03`；`E2E-BPAYR-07／08／09` 的通知斷言全過，證明 `V86` 在 Flyway 建出的真實資料庫生效）。

## 6. 決策與已知限制

- **只有站內通知**。PRD US-014 寫的是 Email／Push，但通知管線的 EMAIL／SMS／PUSH 管道**不會真的送出**（查證 3）——消費者只把資料列標成「已送出」。本輪不假裝有，只送站內通知；這個既有缺口登記為 `DEF-317`。
- **盡力而為，失敗不補送**：Redis 或資料庫暫時失敗時，那則通知就遺失了（取消與退款本身不受影響）。要補送需要交易外發信箱（outbox）之類的機制，超出本輪；需要時請決定。
- **取消訂房的請求會暫時多占一條資料庫連線**（依 Spring 的交易完成順序推論：`afterCommit` 回呼先於連線釋放；**未量測**）：`REQUIRES_NEW` 的通知要另外借一條。連線池上限 20（`application.yml`），取消是低頻操作；只有「同時進行中的取消數 ≥ 連線池大小」才會等待，逾時後這則通知失敗（被吞掉、不影響取消）。逾時與退款兩條路徑沒有外層交易，不受影響。
- **使用者可關閉**：`ORDER_CANCELLED`／`REFUND_COMPLETED` 的站內通知受通知偏好設定控制（預設全開）；使用者關掉就收不到。PRD 沒有定義「必要通知」不可關閉。
- **訂單的取消不通知買家**：本輪只接 PRD 明文要求的三件事。買家取消訂單、賣家改狀態取消、`ORDER_CONFIRMED`／`ORDER_SHIPPED` 等其他商業事件，全庫仍沒有人送通知；Stripe 付款失敗（`payment_intent.payment_failed`，PRD US-014 的「金流阻斷」）也沒接。登記為 `DEF-318`。
- **重試連結**：訂房逾時附「重新預訂」（連到房源頁）；訂單沒有「重新下單」頁，只連到訂單詳情。
- **文案是依 PRD 與 Q14 的結果撰寫的繁體中文**，沒有經過產品確認；想改措辭只需改 `BuyerNotificationService` 的文案函式。
- **消費者排程關閉時**（`APP_SCHEDULING_ENABLED=false`）：通知仍會出現在收件匣（預建列即可見），只是不會被標成已送出、也不寫通知歷史；Redis 佇列會累積。
- **前端沒有單元測試框架**（`package.json` 的 `test` 是 `echo`）。收件匣的標籤與連結由真實後端 E2E（`E2E-BPAYR-07／08`：「查看訂單／查看訂房」與標籤）與 mock E2E（`at-notification-inbox-links.spec.ts`：「重新預訂」連結與標籤）在真實瀏覽器驗證，未做突變驗證。
- 逾時通知對舊單：首次部署後第一輪逾時排程取消的舊訂單也會各通知一次（數量取決於當時的積壓；歷史訂房 `payment_due_at` 為 NULL，不會逾時）。消費者每秒處理約 1 則（`DEF-307`），預建列不受影響。
- 通知文字裡的金額格式：新台幣 `NT$1,234`，非新台幣的訂單顯示幣別代碼。

## 7. 後續

- Sprint 230：`DEF-315`（同一秒簽發的 Refresh Token 位元組相同，加 `jti`）。
- 等使用者決定：`DEF-316`（商家端訂房管理，含權限模型）、`DEF-306`。
- 新登記未修：`DEF-317`（通知的 EMAIL／SMS／PUSH 管道不會真的送出）、`DEF-318`（除這三種外，所有商業事件都沒有通知）。
