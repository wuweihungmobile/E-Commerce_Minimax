# Sprint 284 Plan — 自選掃描角度安全讀碼複核（無新缺陷發現）

## 1. 範圍與起點

使用者貼回 Sprint 283 總結，指示「請繼續完成任務！」，無 `==>` 回覆。核對 `git log`／`gh run view --json status,conclusion,jobs,headSha`：兩次 commit（`d6b0ec4b`、`a4a21d5e`）皆已 push，headSha 與雲端 CI run（[39093682529](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39093682529)、[39094474116](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39094474116)）一致，conclusion 皆為 `success`，工作區乾淨且與 `origin/main` 同步，總結無造假。

**獨立盤點發現結構性轉折點**：逐一核對 `DEFERRED_ITEMS_TRACKER.md` 活躍清單中所有未標 ✅ 的項目（約 60 筆，涵蓋 DEF-025～DEF-388 區間），確認：
- 總結點名的候選 `DEF-348`（移除 MinIO/S3 預設憑證）在追蹤表上仍標 🟡，文字為「維持 yml 預設；移除預設值另排 Sprint」——讀起來是未定案的未來選項，非可直接動手的範圍。
- 其餘約 30 項 🟢 低優先級技術債逐一核對後，全部已是歷史上「已記錄、不排入排程」且有明確理由（零呼叫點死路徑／已知技術限制／效能未量測不宜盲改），或明確需使用者決策方向（DEF-294、297、306、330、334、337、346、355、356）。
- 過去幾輪點名的其他候選（DEF-241/285/317/331/332/342/344/364/365/370/371/373/374/375/376/378/381/382/383/385/388/366/367/368/369/372/377 等）經查證皆已於 Sprint 251～280 間結案，只是 grep 截斷未顯示 ✅ 標記。

**無「範圍明確、無需產品決策」新候選**。以 `AskUserQuestion` 呈現三個方向（開新掃描角度／執行 DEF-348／逐項裁決既有候選），使用者選擇**開新掃描角度**，沿用 Sprint 180／234 的自選審視模式。

## 2. 掃描角度與讀碼調查

以「誰能呼叫這個方法、資料來源是否被信任、是否有清楚的攻擊路徑」為準則，依次檢視：

| # | 角度 | 檔案 | 結論 |
|---|------|------|------|
| 1 | Stripe webhook 簽章驗證 | `StripeWebhookController`、`StripeSignatureVerifierService` | 乾淨。HMAC-SHA256＋timestamp 300 秒容差＋`MessageDigest.isEqual` 常數時間比較＋事件去重（`ProcessedStripeEvent`）＋prod 環境缺 `STRIPE_WEBHOOK_SECRET` 時建構子直接 fail-fast（`DEF-262`，與 `DEF-320` JWT_SECRET 同款模式） |
| 2 | 檔案儲存物件 key／路徑穿越 | `StorageService.sanitizeFileName`／`belongsToTenant` | 乾淨。路徑分隔符與穿越序列已剝離（`DEF-221`），跨租戶讀取已有 `belongsToTenant` 把關（`DEF-101`） |
| 3 | CORS／SecurityConfig | `SecurityConfig.java` | 乾淨。CORS 來源可配置、webhook 例外僅開放必要路徑並附風險註解、bcrypt cost 12（`DEF-323`）、每一條規則皆有對應 DEF 註解可查 |
| 4 | 付款／退款金額是否信任前端輸入 | `PaymentService`、`PaymentStateService` | 乾淨。付款金額一律取自伺服器端 `order.getTotalAmount()`／`booking.getTotalAmount()`；退款金額雖可由請求指定，但有 `refundAmount.compareTo(payment.getAmount()) > 0` 上限檢查且僅店家/管理員可呼叫 |
| 5 | 密碼重設／Email 驗證 token | `AccountSecurityService`、`AccountTokenService` | 乾淨。32 位元組 `SecureRandom`＋SHA-256 雜湊儲存（不存原文）＋`GETDEL` 原子消耗＋60 秒寄信 cooldown＋時序側通道防護（`DEF-290`，背景執行緒寄信） |
| 6 | FAQ 搜尋高亮是否為儲存型/反射型 XSS | `FaqService.highlightKeyword`、`frontend/.../faq/page.tsx` | 乾淨。已於 `DEF-102` 修復：先 `HtmlUtils.htmlEscape(text)` escape 原始內容，才在 escape 後的字串上插入硬編碼 `<mark>`／`</mark>`，使用者輸入的 `keyword` 只用於比對、不會被當作新 HTML 插入。全庫唯一一處高亮實作，無其他模組重複同型風險 |
| 7 | 購物車促銷碼清除邏輯的邊緣案例（Sprint 282/283 新程式碼） | `RedisCartService.removeItem`／`resolveStoreIdsInCart` | 排除。原懷疑「listing 已被硬刪除時 `listingRepository.findById` 返回 empty，導致促銷碼清除邏輯被跳過」——查證全庫 `ListingService` 從無任何硬刪除呼叫點（商品/房源只有狀態變更，從不真正 `delete`），此前提不可能發生，`ifPresent` 分支恆會執行 |
| 8 | 從未被追蹤表提及過的 Service（可能是真正未被審視的角落） | 全庫 72 個 `*Service.java` 比對 `DEFERRED_ITEMS_TRACKER.md`，篩出 3 個：`SettlementService`、`SupportMessageService`、`NotificationHistoryService` | `SettlementService` 僅為 facade（真正邏輯在 `SettlementCalculator`／`SettlementGenerator`／`SettlementReviewer`，三者分別已被提及 5／20／7 次，非真空白）。`SupportMessageService`：owner／租戶檢查完整（`findByIdAndCustomerId`、`requireStoreTenantUnlessSuperAdmin` 已處理系統租戶佔位值陷阱，為 Sprint 234 `DEF-329` 系列修復後版本）。`NotificationHistoryService`：`markOneAsRead` 正確比對 `history.getUserId().equals(userId)`，無跨使用者讀取風險 |

## 3. 結論

**本輪未發現可確認的新缺陷**。八個角度（含一個全庫交叉比對找出的「真正未被提及」清單）逐一讀碼／追查呼叫鏈後皆為乾淨或已有既有防護，兩個初步懷疑（LINE Pay webhook 路由因未加入 `permitAll` 清單而實際不可達、購物車促銷碼邊緣案例）追查後確認皆無實際影響或前提不可能發生，誠實記錄為「查證後排除」而非包裝成發現。

這是延續 `e-commerce-multi-sprint-test-loop` 記憶中 Sprint 77／78「罕見的無新漏洞 Sprint」先例的第三次——283 轮迭代后，这个代码库在常见攻击面（webhook、文件路径、CORS、金额计算、token 生成、XSS、新增程式碼边缘案例、未被审视的角落）上的安全态势已相当成熟。继续地毯式猜测攻击面的边际收益正在快速递减。

## 4. 本輪異動

**無程式碼變更**。僅新增本計畫書與 `RELEASE_TRACKER.md` 一列記錄，供未來查詢「Sprint 284 做過什麼、查過哪些角度、結論是什麼」，避免下一輪誤以為這些角度從未被檢查過而重複调查。

## 5. 下一步／Action Items

- 繼續「開新掃描角度」：候選包括 DEF-330(c) 提到的「掃描所有 `getCurrentTenant()` 呼叫處的守門測試」（難度與誤報量未評估，是基礎設施投資而非 bug 修復）、或挑選更深入的業務流程角度（例如跨角色真實全棧實測，比對 `aisdlc-next-step-request-playbook` 記憶中「agent 只讀碼抓不到測試固件繞過同一段邏輯」的教訓）。
- `DEF-348` 後續（移除 MinIO/S3 預設憑證）：待使用者確認是否為定案方向。
- 追蹤表其餘低優先級技術債與需決策項目：待使用者裁決方向。
