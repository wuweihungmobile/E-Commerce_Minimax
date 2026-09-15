# Sprint 162 Plan — 延伸 Sprint 161 §4 範圍外揭露：強型別 enum 欄位 JSON 反序列化非法值 → 全域 400

**Sprint**: Sprint 162
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_161_PLAN.md](SPRINT_161_PLAN.md) §4「範圍外」明確揭露：`ChatService`/`PaymentService`/`CmsService`（Banner/Page）/`LogisticsService.createLogistics`/`PricingService` 等呼叫 `.valueOf(request.getX().name())` 的呼叫點，其 Request DTO 欄位本身已是強型別 enum（非原生 `String`）。這類欄位帶入非法字面值時，Jackson 在進入 Controller 方法前的反序列化階段就會拋出 `HttpMessageNotReadableException`，落入 `GlobalExceptionHandler` 的 catch-all 變成 500，而非正確的 400。S161 判斷此問題「是否要新增全域 `@ExceptionHandler(HttpMessageNotReadableException.class)`」屬於架構層級決策，留待未來評估、未修復。

本輪（使用者要求「繼續完成任務」）接續此角度，先向使用者確認修法範圍，使用者拍板選擇「全域 400（推薦）」：新增 `@ExceptionHandler(HttpMessageNotReadableException.class)`，統一回 400，涵蓋整個 JSON body 反序列化失敗的類別（不限 enum 欄位），而非只挑 enum 子集窄範圍處理。

---

## 2. 查證與決策依據

- 查證 `GlobalExceptionHandler.java`：確認先前完全沒有攔截 `HttpMessageNotReadableException`，全庫 `grep` 也確認無任何 handler 處理過此例外類型，落入 `handleGenericException` → `E_9900` 500。
- 查證全部測試：`grep` 全庫 `500`/`INTERNAL_SERVER_ERROR` 相關斷言，確認沒有任何測試依賴「malformed request body 回 500」這個現況（僅有 `BookingControllerE2ETest`/`M16ErpE2ETest` 兩處既有的 `anyOf(400, 500)`／`anyOf(400, 422, 500)` 寬鬆斷言，加入 400 分支後兩者仍會通過，不受影響）。
- 選擇「全域」而非「僅 enum 子集」範圍的理由：`HttpMessageNotReadableException` 是 Spring MVC 框架層級的例外，**只會**源自 client 送出的 request body 本身有問題（JSON 語法錯誤、型別不符、enum 非法字面值等），從無合法情境是伺服器端程式錯誤——這與 S161 §4 刻意不做「全域 `IllegalArgumentException` handler」的理由（`IllegalArgumentException` 可能源自非列舉解析的其他程式邏輯，全域兜底有掩蓋真正錯誤的風險）不同，`HttpMessageNotReadableException` 沒有這個風險，統一處理是 Spring Boot 標準實踐。

---

## 3. 修復內容

### 3.1 `DEF-213`：全域新增 `HttpMessageNotReadableException` → 400 handler

[GlobalExceptionHandler.java](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/GlobalExceptionHandler.java) 新增 `@ExceptionHandler(HttpMessageNotReadableException.class)`，統一回傳 `HttpStatus.BAD_REQUEST` + `ErrorCode.E_9000`（沿用既有「驗證錯誤」通用碼，與 `MethodArgumentNotValidException`/`BindException` handler 同組語意）。

紅燈先行：以 `LogisticsController.createLogistics`（S161 §4 明確點名的案例之一）為代表端點，`LogisticsDto.CreateRequest.logisticsProvider` 欄位型別為 `LogisticsDto.LogisticsProvider` enum，帶入 `"NOT_A_REAL_PROVIDER"` 字面值。修復前實際執行得到 `500`（`E-9900`，`Resolved Exception: HttpMessageNotReadableException`），修復後得到 `400`（`E-9000`）。新增測試案例 `M11LogisticsOrderIntegrationTest#createLogistics_invalidLogisticsProviderLiteral_returns400NotInternalServerError`。

此為框架層級的全域修復，非逐點防禦性程式碼，S161 §4 點名的其餘案例（`ChatService`/`PaymentService`/`CmsService` Banner・Page/`PricingService`）與全庫任何其他 `@RequestBody` 端點皆同步受此 handler 保護，不另外逐一新增重複測試（比照既有 `MethodArgumentNotValidException`/`BindException` handler 本身也無逐 DTO 測試的慣例）。

---

## 4. 驗證結果

- `M11LogisticsOrderIntegrationTest` 新增 1 個測試案例，紅燈先行證實修復前後差異（修復前 `mvn -o test` 實測得到 500／`E-9900`，`Resolved Exception: HttpMessageNotReadableException`；修復後 400／`E-9000`）。
- `checkstyle:check`（main + test）：0 違規。
- `mvn -o verify` **1351 個單元測試（與 Sprint 161 持平）+ 480 個整合測試（+1），0 failed**，PMD 無新增違規，`BUILD SUCCESS`（7m15s）。
- 本輪未執行 `make validate-e2e`（純後端全域例外處理器修正，未變更任何前端可觀察行為或既有 API 回應結構，僅將 malformed request body 的錯誤情境從 500 改為正確的 400，不影響任何 E2E 既定 happy path 斷言）。

---

## 5. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-213`（已修復，見 §3.1），移入已完成延後項目；原 S161 §4 該筆「範圍外」揭露狀態更新為已處理。

---

## 6. 誠實揭露總結

- 本輪只驗證了 `LogisticsController.createLogistics` 一個代表端點的行為改變（500→400），未逐一手動驗證 S161 §4 點名的其餘四個 Service（`ChatService`/`PaymentService`/`CmsService`/`PricingService`）的對應端點，而是依賴「這是框架層級的全域例外處理器，作用於所有 Controller」的機制性推論。此推論在架構上成立（Spring `@RestControllerAdvice` 對整個應用生效，不因呼叫點而異），但若日後要更嚴謹，可考慮在這些端點各自補一個對應的驗證測試。
- 未評估此變更對「client 端如何解讀先前 500 回應」的相容性影響（例如若有外部整合或前端曾經特別處理過 500 情境）——查證過程中僅確認**測試層級**沒有依賴，未查證是否有生產環境的外部呼叫方曾依賴此 500 行為，風險評估上判斷極低（因為原本 500 就是缺陷而非設計行為，且前端自身送出的 enum 值皆來自 TypeScript 強型別，本就不會觸發此路徑）。
