# Sprint 217 Plan — 整合測試改用生產權限表（DEF-304）

**Sprint**: Sprint 217
**日期**: 2026-09-29

## 1. 起點

Sprint 216 收尾後，使用者對該輪登記的四項逐一回覆：

- DEF-301（賣家改狀態取消訂單不做補償）：「請遵守 PRD」
- DEF-302（沒有未付款逾時取消）：「24 小時」
- DEF-303（付款／退款其餘不一致）：前端訂房沒有付款步驟、出貨前取消已付款訂單不回補庫存——「以下請處理，符合邏輯」
- DEF-304（整合測試手抄權限清單與生產不一致）：「以下請處理，符合邏輯」

四項會分成數個 Sprint 依序完成。本輪先做 DEF-304：後面三項都要靠整合測試驗證，測試用的權限若與生產不一致，後面每一輪的綠燈都不可信（DEF-298 就是這樣被蓋住的）。

## 2. 問題

`IntegrationTestConfiguration` 以 Mockito spy 覆寫 `RolePermissionMapping`，每個角色回傳一份手抄清單，並把 `hasPermission` 一律 stub 成 `true`。`JwtAuthenticationFilter` 用 `getAuthorities(role)` 決定請求帶哪些權限，所以所有經過真實 filter chain 的整合測試，拿到的都是手抄清單而不是生產權限。

Sprint 216 已對齊 BUYER 一列；其餘角色仍有差異：多給（測試放行、生產拒絕）如 SELLER／STORE_OWNER／ADMIN 的 `cart:*`、SELLER 的 `media:*`、HOST 的 `booking:create`；少給則更多（STORE_OWNER、ADMIN、SUPER_ADMIN 各少 30～44 個）。`hasPermission` 在生產程式碼沒有被呼叫，那兩個 stub 沒有實際作用。

## 3. 修法

不再覆寫。移除 spy、手抄清單與 `hasPermission` stub，整合測試直接使用生產的 `@Component RolePermissionMapping`——權限要改只改一處。

「測試失敗時是測試假設錯、還是生產缺陷」的判準：以 PRD §7.3 RBAC 矩陣與「RBAC 權限（M18 相關）」表為準，並看前端實際呼叫。

## 4. 結果：597 個整合測試，2 個類別失敗

先只跑整合測試（`mvn -o verify -DskipUnitTests=true`），使用生產權限表：597 個中 15 failures＋3 errors，只集中在兩個類別。

| 類別 | 失敗 | 原因 | 分類與處理 |
|------|------|------|------------|
| `M18MediaIntegrationTest` | 15/15 皆 403 | 以 SELLER 註冊，註解直寫「使用 SELLER 角色以取得 media:* 等權限」——依賴手抄清單多給的 `media:*` | **測試假設錯**。生產 SELLER 沒有 `media:*`；PRD「RBAC 權限（M18 相關）」的上傳媒體只列店主／店員／管理員；前端沒有任何頁面呼叫 `/v2/media`。比照 `M09NotificationTemplateIntegrationTest`，註冊後提升為測試租戶的店主再登入 |
| `M08ReviewStatsIntegrationTest` | 3 errors（context 載入失敗） | `@WebMvcTest` 切片不掃描 `@Component`，`JwtAuthenticationFilter` 需要的權限表原本由被移除的覆寫提供 | **測試設定**。把生產的 `RolePermissionMapping` 本身 `@Import` 進切片 |

沒有任何失敗顯示「生產拒絕了應該放行的操作」；也沒有測試斷言過「應該 403、生產卻放行」。其餘多給的權限（如 SELLER 用購物車、HOST 建立訂房）沒有任何整合測試走到，這也是清單能長期漂移的原因。

## 5. 守門測試

`IntegrationContextUsesProductionPermissionsIntegrationTest`（新，2 案例，走整合測試同一個 Spring context）：

1. 權限表 bean 不是 mock／spy，且每個角色的 `getAuthorities` 與 `new RolePermissionMapping()` 完全相同。
2. 經過真實 filter chain：`GET /v2/cart` 買家 200；SELLER、STORE_OWNER、ADMIN 都是 403 `E-1007`（PRD §7.3「M04 購物車」只有 Buyer 有權限）。

**紅燈先行**：以 `git stash` 暫時還原舊的覆寫，兩個案例都失敗（`Expecting value to be false but was true`；賣家 `Status expected:<403> but was:<200>`）；還原後通過，並以 grep 確認修正確實還原。

## 6. 觀察到、不在本輪範圍的差異（登記 DEF-306）


逐列對照 PRD §7.3 矩陣時，看到生產權限表本身與 PRD 有出入（這是「生產 vs. PRD」，不是本輪處理的「測試 vs. 生產」）。舉例：STORE_STAFF 的商品／房源管理（PRD RW*，生產只有讀）與上傳媒體（PRD M18 表 RW，生產只有讀）、HOST 沒有 `booking:cancel`（PRD M06「預訂管理」Host RX*，且 PRD 規定商家主動取消預訂須全額退款）、STORE_OWNER 與 ADMIN 有 `order:create`（PRD「M05 訂單（買家）」兩者為 —）。多數是 Sprint 129 等輪次依既有慣例做的取捨，是否調整牽涉產品定性，只登記、不改。HOST 取消預訂會在後續處理訂房付款（DEF-303 (1)）時一併評估。

## 6.1 研究 DEF-302 時發現：全專案從未啟用排程（登記 DEF-305）

規劃 DEF-302（未付款 24 小時自動取消，需要排程）時發現：全專案沒有任何 `@EnableScheduling`，`git log -S "EnableScheduling"` 也查無紀錄——**從第一個 commit 起就沒有啟用過**。Spring Boot 不會自行處理 `@Scheduled`，所以現有三個排程從未自動執行：

- `SettlementGenerator.generateWeeklyStatements()`（每週一產生結算單）——`SETTLEMENT_JOB_RUNBOOK.md` §4.4 甚至列了「檢查應用是否啟用 `@EnableScheduling`」，但沒有人真的檢查過。管理員手動觸發端點（Sprint 208，DEF-287）是目前唯一會產生結算單的途徑。
- `NotificationConsumerService.consumeNotifications()`／`processRetryQueue()`——推進 Redis `notification:stream` 的訊息沒有人消費，佇列只增不減。單筆 `NotificationService.sendNotification` 會先直接寫一筆 `isSent=false` 的通知再推入佇列，所以站內看得到、但永遠是「未送出」；廣播 `broadcastNotification` 只推佇列、不直接寫庫，**廣播通知從未出現在任何人的通知列表**。另外，消費者處理訊息時會再寫一筆 `isSent=true` 的通知——一旦啟用排程，每則單筆通知會變成兩筆。這兩個通知端點目前前端沒有呼叫點（DEF-238 起僅 SUPER_ADMIN 可呼叫）。

本輪只登記。啟用排程會讓這些既有排程在正式環境開始執行（第一次會處理累積的通知佇列、週一會產生結算單），且通知的重複寫入要先處理，將在 DEF-302 那一輪一併處理並說明影響。

## 7. 驗證

- 修正後：三個相關類別 20/20 通過（`M18MediaIntegrationTest` 15、`M08ReviewStatsIntegrationTest` 3、守門 2）。
- 全量 `mvn -o clean verify`：結果見 §8。

## 8. 全量驗證結果

`mvn -o clean verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1813**（持平）／整合 **597**（+2：守門測試）／0 failures／0 errors／0 skipped；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（12 分 31 秒）。

修正兩個類別時，Maven 顯示「Nothing to compile」——測試類別是被 IDE 自動編譯進 `target/test-classes` 的；上面的 `clean verify` 已清空並以 javac 全部重編，數字以它為準。

## 9. 決策與已知限制

- **分類判準（我做的判斷）**：以 PRD 的 RBAC 表為準判定 `M18MediaIntegrationTest` 是測試假設錯，而不是把 `media:*` 加給 SELLER。
- 以 `@WebMvcTest` + `@WithMockUser(authorities=...)` 直接指定權限的切片測試（如 `M08ReviewIntegrationTest`）不經過角色→權限對照，不在本輪範圍；它們測的是「給定權限時控制器的授權運算式」，沒有宣稱某角色擁有這些權限。
- 本輪只改測試程式碼，生產行為沒有變更。
