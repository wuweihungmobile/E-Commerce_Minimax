# Release Notes - v2028.10.21-01 (Sprint 78)

**發布日期**: 2028-10-21（規劃）／實作完成 2026-07-06
**發布類型**: 🔍 探查 + 🧪 測試補強（schema-free；後端聚焦，無前端變動；**未修改任何生產程式碼**）
**Sprint**: Sprint 78（多 Sprint 測試強化計劃）
**狀態**: ✅ 已 push

> Sprint 78 主題：探查 `OAuthService`（2 個 public 方法），發現核心邏輯（`exchangeCodeForUserInfo`）目前是**未完成的 stub**，無條件拋出 `UnsupportedOperationException`——此現況為 pre-existing 已知技術債（`docs/06_quality/TECHNICAL_DEBT_TODO_SCAN.md`「模擬實現」、`docs/04_planning/PRODUCT_BACKLOG.md` P3「OAuth2/KYC 實名」），非本 Sprint 新發現，涉及擁有權判斷的 `findOrCreateOAuthUser` 邏輯目前是無法觸及的死碼，**未發現需要修復的擁有權/租戶檢查缺口**。因範圍遠小於預期，時間充裕，一併涵蓋 `PromoService`（3 個 public 方法），同樣**未發現缺口**（租戶隔離已在 Repository 查詢層級正確實作）。連續第 2 個 Sprint（77/78）「探查後確認無新缺口」。

---

## 🔍 範圍探查（US-001）

- **`OAuthService`（211 行）2 個 public 方法**：`handleOAuthLogin`/`linkOAuthAccount` 皆在第一步呼叫無條件拋出 `UnsupportedOperationException` 的 stub，涉及擁有權判斷的 `findOrCreateOAuthUser` 邏輯目前無法從 public API 觸及；`linkOAuthAccount` 的 `userId` 由 Controller 從已認證的 `UserPrincipal` 取得，不存在代他人連結帳號的 IDOR 風險。OAuth 特有安全考量（state/CSRF、redirect_uri 驗證、token 綁定）因整合未實作而無法評估。
- **`PromoService`（111 行）3 個 public 方法**：`validatePromoCode` 於查詢層級以 `tenantId` 做租戶隔離；`computeDiscount`/`incrementUsageCount` 信任呼叫端已查得的 `PromoCode`，唯一呼叫端 `RedisCartService` 使用方式合理。

## 🧪 測試補強（US-002 + US-003）

- **`OAuthServiceTest.java`（新檔，4 個測試）**：驗證 `handleOAuthLogin`（GOOGLE/GITHUB 兩種 provider）與 `linkOAuthAccount`（不同 userId）在目前 stub 狀態下皆 fail-closed（拋 `UnsupportedOperationException`）且不對任何 Repository 產生副作用。
- **`PromoServiceTest.java`（新檔，17 個測試）**：涵蓋 `validatePromoCode`（空白代碼、查無資料、停用、尚未開始、已過期、達使用上限、合法有效，含 `ArgumentCaptor` 驗證租戶隔離查詢條件）、`computeDiscount`（null 防呆、低於消費門檻、PERCENTAGE/FIXED_AMOUNT/FREE_SHIPPING 三種折扣類型、`maxDiscountAmount` 上限）、`incrementUsageCount`。

## 測試 / 驗證 ✅

- **`OAuthServiceTest`**：4 tests，0 fail。
- **`PromoServiceTest`**：17 tests，0 fail。
- **後端全量單元回歸**（`mvn test`，`make test-db-up` 後）：**0 fail**（含本 Sprint 新增 21 個）。初次未啟動 test DB 時出現 3 個 `SellerDashboardServiceCacheTest` Error（`Failed to load ApplicationContext`），確認為已知環境陷阱（需真實 PostgreSQL）與本 Sprint 無關。
- **驗證方式選擇**：依全量回歸頻率政策，本 Sprint 未修改任何生產程式碼（純新增測試），僅需 `mvn test`，不需 `mvn verify -Pintegration-test`。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更），EXIT_CODE=0。

## 技術決策 / 已知限制 ⚠️

- **本 Sprint 未修改任何生產程式碼**：探查確認 `OAuthService`/`PromoService` 皆無需修復的擁有權/租戶檢查缺口，本次發布純為測試覆蓋率補強。
- **`OAuthService` 的測試覆蓋範圍受限於現況**：由於核心邏輯是未完成的 stub，新增測試僅能驗證「fail-closed、無副作用」，`findOrCreateOAuthUser` 內真正的擁有權判斷邏輯待未來實際實作 OAuth 整合後需重新測試與審查。
- **OAuth 特有安全考量（state/CSRF、redirect_uri 驗證、token 綁定）尚無法評估**：功能未實作，待未來實際串接時一併補上（含目前 DTO 缺少的 `state` 欄位）。
- **無前端變動**：本 Sprint 純後端測試補強。

## 資料庫遷移 🗄️

- 無（schema-free；本 Sprint 未新增/修改任何 Entity 或 Repository 方法）。

## 內含 Commit（Sprint 78）

| US / 項目 | 說明 |
|----------|------|
| Sprint 78 Plan | `OAuthService`/`PromoService` 範圍探查 + 單元測試補齊計劃（3 US / 5 SP）|
| US-001 | `OAuthService`/`PromoService` 範圍探查（無程式碼變更，未發現新缺口）|
| US-002 | `OAuthServiceTest.java` 新增 4 個測試 |
| US-003 | `PromoServiceTest.java` 新增 17 個測試 |
| Sprint 78 收尾 | Review / Retro / Release Notes |

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
