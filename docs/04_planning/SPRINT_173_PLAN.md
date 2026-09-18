# Sprint 173 Plan — PricingService.updateRule 未驗證日期範圍，可寫入 validFrom > validTo 的永不生效規則（DEF-225）

**Sprint**: Sprint 173
**日期**: 2026-09-18

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 無任何待排程項目（`DEF-224` 已於 Sprint 172 結案）。延伸 Sprint 172 §8 誠實揭露的觀察方向——`ErrorCode.E_8001`（「無效的定價規則設定」）本業在 `src/main` 中零呼叫點，可能代表定價規則模組的驗證邏輯尚未實作對應的錯誤路徑——本輪深入查證 `PricingService`（`backend/src/main/java/com/nextkey/ecommerce/core/pricing/PricingService.java`）的驗證覆蓋範圍。

## 2. 缺口說明

`PricingService.createRule`（`PricingService.java:60`）呼叫 `validateRuleRequest`（`PricingService.java:363`），對 `validTo` 早於 `validFrom` 的情況拋出 `ErrorCode.E_4003`。但 `updateRule`（`PricingService.java:91`）完全沒有呼叫這個驗證——`request.getValidFrom()`/`request.getValidTo()` 若非 null 各自獨立 set 到既有實體上，兩者之間、或與既有值之間是否仍滿足 `validFrom <= validTo`，從未被檢查。

具體攻擊面：呼叫端只需 PATCH 單一欄位就能造出無效狀態，例如既有規則 `validFrom=2026-06-01, validTo=2026-08-31`，只送 `validTo=2026-01-01`（不動 `validFrom`）即可成功寫入 `validFrom > validTo` 的規則，回應 200 無任何錯誤。

後果查證：`isRuleApplicable`（`PricingService.java:406`）判斷規則適用日期的條件是 `!rule.getValidFrom().isAfter(date) && !rule.getValidTo().isBefore(date)`——若 `validFrom > validTo`，不存在任何 `date` 能同時滿足兩者，此規則對所有日期永遠不適用。房東以為更新了促銷規則的有效期間，實際上規則從此對任何日期都不生效，且系統不會回報任何錯誤——與 Sprint 171/172 的「錯誤碼選錯」不同，這是**驗證邏輯缺口**（規則永久失效但無感知，非文字訊息問題）。

紅燈實測驗證此缺口確實存在（見 §5）：修復前呼叫 `updateRule` 送入不一致日期，`pricingRuleRepository.save()`（未 mock 回傳值時預設回 null）之後 `toRuleResponse(null)` 拋 `NullPointerException`，證實請求在到達 `save()` 前完全沒有被攔截；生產環境中（`save()` 有真實回傳值）則會是「200 成功但規則永不生效」的靜默失效，非崩潰。

`PricingDto.UpdateRuleRequest`（`PricingDto.java:61`）本身也沒有任何 Bean Validation 標註可攔截此情況（本就無法用單欄位標註表達跨欄位規則）。

## 3. 修法決策

比照 `createRule` 既有的驗證語意與訊息文字，在 `updateRule` 合併請求欄位到既有實體之後（覆蓋所有「只改一邊」的部分更新情境），檢查合併後的最終 `validFrom`/`validTo` 是否仍滿足順序，違反則拋出**與 `createRule` 相同的 `ErrorCode.E_4003`**（訊息："Valid to date must be after valid from date"）——這是同一條業務規則在 create/update 兩個入口的一致實作，不新增錯誤碼（`E_8001` 本業的「無效的定價規則設定」語意更抽象，此處直接複用 `E_4003` 更精確且與既有慣例一致，比照 Rule 11 配合程式碼庫慣例）。

## 4. 修復內容

- `PricingService.java:104-112`（`updateRule`）：在 `validFrom`/`validTo` 兩個 partial-update 區塊之後，新增：
  ```java
  if (rule.getValidTo().isBefore(rule.getValidFrom())) {
      throw new BusinessException(ErrorCode.E_4003, "Valid to date must be after valid from date");
  }
  ```
  檢查對象是合併後的實體欄位（`rule.getValidFrom()`/`rule.getValidTo()`），而非請求 DTO 的原始欄位——確保「只改一邊」的部分更新也能被攔截到最終狀態的不一致。

## 5. 測試

- **紅燈先行**：`git stash push -- PricingService.java` 只暫存生產程式碼修復（測試檔案不暫存），新增的兩個測試對修復前程式碼執行：
  - `updateRule_validToBeforeExistingValidFrom_throwsE4003`：既有規則 `validFrom=2026-06-01/validTo=2026-08-31`，只送 `validTo=2026-01-01`。
  - `updateRule_validFromAfterExistingValidTo_throwsE4003`：只送 `validFrom=2026-12-01`。
  - 兩案例修復前皆拋出 `NullPointerException`（而非預期的 `BusinessException`），證實修復前後行為確有差異（驗證完全沒被觸發，請求直接落到 `save()`）。
- `git stash pop` 還原修復後重跑：兩案例轉綠，斷言 `errorCode == ErrorCode.E_4003` 且 `verify(pricingRuleRepository, never()).save(any())`。
- 新增測試置於 `PricingServiceTest` 新的 `@Nested class RuleUpdateValidationTests`（`updateRule` 先前完全零測試覆蓋，含成功案例）。

## 6. 驗證結果

- 紅燈階段：見 §5，兩案例修復前拋 `NullPointerException`，修復後轉綠。
- `mvn -o test -Dtest=PricingServiceTest`：26 個測試（+2），0 failed。
- `mvn -o test -Dtest=M12PricingIntegrationTest,M12PricingProductIntegrationTest`（`make test-db-up` 真實 postgres/redis）：15 個測試，0 failed，確認既有 `updateRule_success_returns200`（只送 `ruleName`+`priority`，未觸及日期欄位）不受影響。
- `mvn -o verify`（完整回歸）：**1552 個單元測試（+2）+ 482 個整合測試（持平），0 failed**，`BUILD SUCCESS`；checkstyle（main+test）0 違規；PMD 無新增問題。
- **前端呼叫點查證**：`grep -rn "updateRule\|PUT.*pricing.*rules" frontend/src` 確認 `frontend/src/lib/api` 與 `dashboard` 定價規則管理頁確有呼叫 `PUT /v2/pricing/rules/{ruleId}`，其表單提交邏輯每次都會一併送出完整的 `validFrom`/`validTo`（非單欄位 PATCH），故此缺口在現有前端 UI 路徑下不會被觸發；风险來自於直接呼叫 API 的其他消費端（如未來的行動端、批次匯入工具或手動 API 呼叫）或未來新增只改單一日期欄位的 UI（如「延長促銷一週」這類只改 `validTo` 的操作）。修復本身對現有前端行為無影響（現有表單本就會送出一致的日期組合，不會觸發新增的驗證錯誤）。
- 未執行 `make validate-e2e`：本輪修改僅新增後端服務層的一項輸入驗證，不改變既有成功路徑的回應格式；`make validate-release` 執行前會涵蓋完整 E2E 驗證。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-225`（🟢 低優先級表）：狀態直接記為「✅ 已修復（Sprint 173）」，發現與修復同輪完成，比照 `DEF-223`/`DEF-224` 同 Sprint 內發現並處理的先例。

---

## 8. 誠實揭露總結

- **本輪未擴大檢查其他 Service 是否有類似的「create 有驗證、update 沒有」不對稱缺口**：這是本次查證 `PricingService` 過程中偶然發現的具體模式，未做全庫掃描確認是否有其他 Service 存在相同不對稱（例如 `ListingService`/`RoomService` 等是否也有對應的 update 方法缺日期或範圍驗證），若未來時間允許可另開一輪全庫掃描「同一業務物件的 create 驗證規則是否也套用到 update」。
- **`E_8001` 本業（「無效的定價規則設定」）仍然零呼叫點**：本輪修復刻意選擇複用 `E_4003` 而非啟用 `E_8001`，因為 `E_4003` 已是 `createRule` 對完全相同語意（`validTo < validFrom`）的既有用法，複用比另立新語意更符合 Rule 11（配合既有慣例）。`E_8001` 本業究竟該對應哪個實際驗證情境（若有）仍是未解之謎，維持在 Sprint 172 §8 記錄的「未來若需要可另開查證」狀態，不在本輪範圍內強行找一個情境套上去。
- **前端呼叫點查證止於現有頁面的表單提交模式（一次送出完整日期組合），未逐行核對所有呼叫 `updateRule` 的程式路徑**：足以確認「現有 UI 不會觸發新增的驗證錯誤」，但不是對前端程式碼的窮盡審查。
