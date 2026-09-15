# Sprint 163 Plan — 延伸 Sprint 161 §7 誠實揭露：`@PathVariable`/`@RequestParam` 型別轉換失敗 → 全域 400

**Sprint**: Sprint 163
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_161_PLAN.md](SPRINT_161_PLAN.md) §7 誠實揭露列出「未對 `AdminController`/`TenantController` 以外的其餘 Controller 做全面『query param 型別轉換錯誤』的系統性掃描（例如 `@RequestParam UUID`/`@RequestParam LocalDate` 等 Spring 內建型別轉換失敗的情境），本輪僅鎖定『enum 字串』這一種型別」。本輪（使用者要求「繼續完成任務」）接續此角度查證。

---

## 2. 查證方法與範圍

- `grep` 全庫 Controller 統計含 `@PathVariable UUID`／`@RequestParam ... LocalDate` 等 Spring 內建型別轉換的端點，命中 **35 個 Controller 檔案**（幾乎每個帶資源 ID 的端點都屬此類），確認這是廣泛存在的模式，非孤例。
- 查證 `GlobalExceptionHandler.java`：確認先前完全沒有攔截 `MethodArgumentTypeMismatchException`（`org.springframework.web.method.annotation`），全庫 `grep` 也確認無任何 handler 處理過此例外類型，落入 `handleGenericException` → `E_9900` 500。
- 紅燈先行：以 `OrderController.getOrder`（`GET /v2/orders/{orderId}`，`@PathVariable UUID orderId`）為代表端點，帶入非 UUID 字串 `"not-a-real-uuid"`。修復前實際執行得到 500（`E-9900`，`Resolved Exception: MethodArgumentTypeMismatchException`），修復後得到 400（`E-9000`）。
- 查證全部測試：`grep` 確認沒有任何既有測試以非法路徑變數/查詢參數字串依賴「回 500」的現況。

---

## 3. 決策依據：延伸而非重新徵詢

`MethodArgumentTypeMismatchException` 與 Sprint 162 已修復的 `HttpMessageNotReadableException` 是同一性質的例外：**只會**源自 client 送出的路徑變數/查詢參數本身無法解析成宣告的 Java 型別（UUID、`LocalDate`、`Integer` 等），從無合法情境是伺服器端程式錯誤。Sprint 162 使用者已就「Spring 框架層級、只會源自 client 輸入錯誤的反序列化/綁定例外」這一類問題拍板「全域 400」方案；本輪判斷屬於同一決策的自然延伸（而非獨立的新架構決策），故直接沿用相同修法與 `ErrorCode`（`E_9000`），未再另外發起 `AskUserQuestion`。誠實揭露：這是本輪的主動判斷，若使用者認為仍應每次個別確認，請告知以修正後續流程。

---

## 4. 修復內容

### 4.1 `DEF-214`：全域新增 `MethodArgumentTypeMismatchException` → 400 handler

[GlobalExceptionHandler.java](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/GlobalExceptionHandler.java) 新增 `@ExceptionHandler(MethodArgumentTypeMismatchException.class)`，統一回傳 `HttpStatus.BAD_REQUEST` + `ErrorCode.E_9000`（沿用 Sprint 162 同一組通用碼）。

框架層級的全域修復，涵蓋全庫 35 個 Controller 檔案中所有 `@PathVariable`/`@RequestParam` 型別轉換情境（`UUID`/`LocalDate`/`Integer` 等），不逐一新增重複測試，比照 Sprint 162 的處理方式，只以一個代表端點（`OrderController.getOrder`）鎖住此機制。新增測試 `RequestParamTypeMismatchValidationTest`（獨立輕量測試類別，比照 `PostControllerFilterValidationTest`/Sprint 162 模式）。

---

## 5. 驗證結果

- `RequestParamTypeMismatchValidationTest` 新增 1 個測試案例，紅燈先行證實修復前後差異（500 → 400）。
- `checkstyle:check`（main + test）：0 違規。
- `mvn -o verify` **1352 個單元測試（+1）+ 480 個整合測試（持平），0 failed**，PMD 無新增違規，`BUILD SUCCESS`（6m44s）。
- 本輪未執行 `make validate-e2e`（純後端全域例外處理器修正，未變更任何前端可觀察行為或既有 API 回應結構，僅將 malformed path/query 參數的錯誤情境從 500 改為正確的 400，不影響任何 E2E 既定 happy path 斷言）。

---

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-214`（已修復，見 §4.1）。
- Sprint 161 §7 該筆「未系統性掃描 query param 型別轉換錯誤」的誠實揭露，狀態更新為已處理。

---

## 7. 誠實揭露總結

- §3 的「延伸而非重新徵詢」是本輪主動的判斷呼叫，未透過 `AskUserQuestion` 重新確認範圍——理由是這與 Sprint 162 剛拍板的決策屬同一性質例外（框架層級、只會源自 client 輸入錯誤），但這仍是一次自主判斷，記錄於此供使用者覆核。
- 只驗證了 `OrderController.getOrder` 一個代表端點的行為改變（500→400），未逐一手動驗證其餘 34 個命中檔案的每個端點，而是依賴「這是框架層級的全域例外處理器，作用於所有 Controller」的機制性推論（與 Sprint 162 同一立場）。
- ~~未評估 `@RequestParam LocalDate`（日期格式錯誤）是否會拋出完全相同的 `MethodArgumentTypeMismatchException`~~ **同日已補驗證關閉**：新增 `RoomCalendarController.unmarkMaintenance`（`@RequestParam @DateTimeFormat LocalDate startDate`）案例，實測確認 `LocalDate` 型別轉換失敗同樣拋出 `MethodArgumentTypeMismatchException`（非其他子類型），受同一 handler 保護。`RequestParamTypeMismatchValidationTest` 現有 2 個案例（UUID 路徑變數 + LocalDate 查詢參數），皆為綠燈（修復已在 main 分支生效，此為鎖定既有正確行為的回歸測試，非紅燈先行）。
