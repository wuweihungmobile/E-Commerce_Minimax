# Sprint 95 Plan — M12 動態定價 pricing_rules 數量與衝突防護機制

**Sprint**: Sprint 95
**日期**: 2026-07-19
**主題**: PRD §5.5.1/§9.13/§18.9.1 明列的 M12 動態定價規則約束（50 條上限、同類型唯一、時間重疊衝突檢核、預覽過去日期拒絕），重新全面比對 PRD v1.0 Final 全文後發現的落地缺口，94 個 Sprint 以來從未被實作。

---

## 1. 缺口盤點結果（背景）

比照 Sprint 93/94 的方法論（不信任可能過時的 `PRODUCT_BACKLOG.md`/`DEFERRED_ITEMS_TRACKER.md`，重新逐段核對 PRD 全文並與程式碼交叉比對），本輪找到：

- **PRD §5.5.1**：`pricing_rules` 數量約束 —— 每 `room_listing_id` 最多 50 條 `is_active=true` 規則、每種 `rule_type` 最多 1 條 active 規則、覆蓋行為定義（v0.9_R02_Loop_01：軟刪除舊規則、新規則寫入、保留歷史、不 hard delete）。
- **PRD §9.13**：`POST /api/v2/dashboard/pricing-rules` 後置條件明文要求 50 條上限檢查，回傳 `E-4001 RULE_LIMIT_EXCEEDED`。
- **PRD §18.9.1**：QA Loop 2 正式採納的 4 條 Test Case（TC-LO2-M12-001~004），涵蓋上限、時間重疊拒絕、覆蓋後 gap 回退 base_price、預覽過去日期拒絕。

程式碼庫證據：`ErrorCode.E_4008`("定價規則衝突") 已預留但全庫從未被拋出，是典型「規格已定義、實作被遺漏」的訊號；`PricingService.validateRuleRequest` 僅檢查 `validTo > validFrom`，完全沒有數量/唯一性/重疊檢查。

## 2. 規格落差與工程決策

1. **錯誤碼選用**：PRD 原文四條 Test Case（TC-LO2-M12-001/002/004）與 §9.13 後置條件皆明文寫 `E-4001`（非預留的 `E_4008`）。`E_4001` 在本專案是通用 400 驗證錯誤碼（`RoomCalendarService`/`BookingService`/多處 API 皆以自訂 message 重複使用同一碼），故沿用 PRD 字面規格與既有慣例，直接用 `ErrorCode.E_4001` + 自訂 message，不使用孤兒碼 `E_4008`。
2. **「確認覆蓋」機制的 API 設計**（PRD 未明訂欄位名稱，屬工程範圍決策）：`CreateRuleRequest` 新增 `Boolean confirmOverride` 欄位。同類型 active 規則與新規則時間範圍**重疊**時：
   - `confirmOverride` 非 `true` → 拒絕，回傳 `E-4001`「新規則與現有同類型規則時間範圍重疊，請先編輯現有規則的有效期間」（對應 TC-PR-003/TC-LO2-M12-002）。
   - `confirmOverride=true` → 僅軟刪除**重疊**的同類型舊規則（`isActive=false`），新規則寫入為 active（對應 TC-PR-001/002）。
3. **同類型「不重疊」規則予以保留，不強制唯一**：PRD §5.5.1 字面寫「同類型唯一」，但 TC-PR-004（覆蓋後 gap 期間回退 base_price）顯示系統設計實際容許同類型規則分屬不同時間段。判斷「唯一」約束的真正意圖是避免同時間窗口的規則衝突，而非物理上禁止同類型出現兩筆記錄。故本 Sprint 僅在**時間範圍重疊**時才觸發衝突/覆蓋機制；不重疊的同類型規則（如不同年度各自的旺季規則）可直接共存，不需 `confirmOverride`。此為 PRD 未見顯式反例、工程判斷合理的範圍決策。
4. **定價預覽過去日期校驗落點**：PRD 字面寫的端點是 `POST /api/v2/dashboard/rooms/:id/pricing-preview`，但程式碼庫實際的「預覽未來定價日曆」功能是 Sprint 83 已上線的 `GET /v2/dashboard/pricing/calendar`（`PricingController.getCalendarPreview`）。依 Rule 2/3（簡潔優先、精準改動，不重複建置功能相同但路徑不同的端點）與既有慣例（本專案 API 路徑本就已統一整併至 `/v2/dashboard/pricing/*`，未逐條照抄 v0.8 舊路徑），將過去日期校驗加在既有端點，不新增重複端點。
5. **`updateRule`/`deleteRule` 不納入本次檢查**：PRD 的 50 條上限與衝突檢核文字皆針對「建立」情境（TC 案例也都是 `POST`），故只在 `createRule` 加上驗證，維持最小改動範圍。

## 3. 實作內容

1. **`PricingService.enforceRuleLimitAndConflict`**（新增私有方法，於 `createRule` 內 `validateRuleRequest` 之後呼叫）：
   - 查詢該 `roomListingId` 現有 active 規則數，`>= 50` 即拒絕（`E-4001 RULE_LIMIT_EXCEEDED`），不寫入。
   - 過濾出同 `ruleType` 且與新規則時間範圍重疊（`validFrom <= newValidTo && validTo >= newValidFrom`）的既有 active 規則；有重疊且未 `confirmOverride` → 拒絕；有重疊且已確認 → 軟刪除該些規則後放行建立。
2. **`PricingDto.CreateRuleRequest`**：新增 `Boolean confirmOverride` 欄位。
3. **`PricingController.getCalendarPreview`**：新增 `startDate` 早於今日即拒絕（`E-4001`「定價預覽不支援過去日期」）。
4. **測試**：
   - `PricingServiceTest`（單元，新增 `RuleCreationLimitAndConflictTests` 4 項）：50 條上限拒絕、重疊未確認拒絕（舊規則不受影響）、確認覆蓋後軟刪除+新規則生效、不重疊同類型共存不需確認。
   - `M12PricingIntegrationTest`（整合，新增 4 項 IT-M12-009~012）：以上 3 項透過真實 HTTP 呼叫驗證，另加過去日期預覽拒絕。

## 4. 驗證結果

- 單元測試：`PricingServiceTest` 23 tests 0 fail（含新增 4 項）
- 整合測試：`M12PricingIntegrationTest` 12 tests 0 fail（含新增 4 項）、`M12PricingProductIntegrationTest` 回歸通過（PRODUCT 型別建立規則不受影響，`roomListingId` 為 null 時上限/衝突檢查自然略過）
- Checkstyle 0 violations、PMD 0 violations
- 全量回歸 `mvn verify -Pintegration-test`：詳見 commit 訊息
- `make validate-schema`：無 migration，schema-free

## 5. 範圍外（延後）

- 每日凌晨 03:00 `room_calendar.price` 全量重算排程（PRD §5.5.2）——與本次「建立時驗證」缺口性質不同，屬排程機制，未在本輪 PRD 掃描中列為高信心缺口，需另案評估現有排程基礎設施（`@Scheduled`）後排入。
- `MANUAL_OVERRIDE` 類型是否也應納入 50 條/唯一性檢查——目前 `overridePrice`/`setCalendarPrice` 走獨立方法未經過 `enforceRuleLimitAndConflict`，PRD 未明確要求，維持現狀。
