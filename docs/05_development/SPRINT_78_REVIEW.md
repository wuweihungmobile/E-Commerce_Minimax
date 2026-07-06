# Sprint 78 Review / Sprint 78 評審會議

> **Sprint 編號**: Sprint 78
> **期間**: 2026-07-06
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: `OAuthService`/`PromoService` 單元測試補齊（先前皆為零覆蓋）+ 擁有權/租戶檢查與 OAuth 特有安全考量主動審視

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `OAuthService`/`PromoService` 範圍探查 | 1 | ✅ 完成 |
| US-002 | `OAuthService` 單元測試補齊 | 2 | ✅ 完成 |
| US-003 | `PromoService` 單元測試補齊 | 2 | ✅ 完成 |

**5 SP 全數完成**。本 Sprint 延續多 Sprint 測試強化計劃，聚焦 `OAuthService`。探查後發現該服務的核心邏輯（`exchangeCodeForUserInfo`）目前是**未完成的 stub**，無條件拋出 `UnsupportedOperationException`——此現況為 pre-existing 已知技術債（`docs/06_quality/TECHNICAL_DEBT_TODO_SCAN.md`「模擬實現」🟡中、`docs/04_planning/PRODUCT_BACKLOG.md` P3「OAuth2/KYC 實名」），非本 Sprint 新發現。因此涉及擁有權判斷的 `findOrCreateOAuthUser` 邏輯目前是無法從 public API 觸及的死碼，**未發現需要修復的擁有權/租戶檢查缺口**。由於範圍遠小於預期，時間充裕，依指示一併涵蓋 `PromoService`，同樣**未發現缺口**。

---

## 2. 交付內容

### 探查結論（US-001）

- **`OAuthService`（211 行）2 個 public 方法**：`handleOAuthLogin`/`linkOAuthAccount` 皆在第一步呼叫 private 方法 `exchangeCodeForUserInfo`，該方法無條件拋出 `UnsupportedOperationException`。`linkOAuthAccount` 的 `userId` 參數由 `OAuthController` 從已認證的 `@AuthenticationPrincipal UserPrincipal` 取得而非 request body，Controller 層設計正確，不存在代他人連結 OAuth 帳號的 IDOR 風險。全庫搜尋確認沒有其他 OAuth Provider 串接實作可替代此 stub，`OAuthProvider` 枚舉僅 `GOOGLE`/`GITHUB` 兩個值，無 provider 專屬實作類別。
- **`PromoService`（111 行）3 個 public 方法**：`validatePromoCode` 透過 `PromoCodeRepository.findByCodeIgnoreCaseAndTenantId` 在查詢層級做租戶隔離；`computeDiscount`/`incrementUsageCount` 接受呼叫端已查得的 `PromoCode`，檢視唯一呼叫端 `RedisCartService` 後確認信任邊界合理。

### OAuth 特有安全考量評估結果

- **state/CSRF 防護、redirect_uri 白名單驗證、token 交換後的租戶/使用者綁定**：因 OAuth 整合尚未實作，目前**無法評估**（沒有實際的 HTTP 呼叫發生）。觀察到 `OAuthDto.AuthRequest`/`LinkRequest` 目前完全沒有 `state` 欄位，記錄供未來實際串接 OAuth Provider API 時一併補上，非本 Sprint 可修復的缺口（修復對象是尚不存在的程式碼）。

### 文件

- **`SPRINT_78_PLAN.md`**（新檔）：本 Sprint 計劃，含前置範圍探查、既有測試覆蓋現況、US-001/002/003 完整 AC。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ `OAuthServiceTest.java` 撰寫 → 編譯 → 執行（4 tests 0 fail）→ `PromoServiceTest.java` 撰寫 → 編譯 → 執行（17 tests 0 fail），未累積 |
| `OAuthServiceTest`（新檔） | ✅ 4 tests，0 fail |
| `PromoServiceTest`（新檔） | ✅ 17 tests，0 fail |
| 後端全量單元回歸（`mvn test`，無 test DB） | ⚠️ 808 tests，3 Errors（`SellerDashboardServiceCacheTest` 3 個方法 `Failed to load ApplicationContext`），經確認與 `OAuthService`/`PromoService` 完全無關，是已知環境陷阱：該測試需要真實 PostgreSQL |
| `make test-db-up` 後重跑 `mvn test` | ✅ **0 fail**（含本 Sprint 新增 21 個） |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更），EXIT_CODE=0，backend 啟動成功、entity 與 Flyway schema 對齊 |
| 驗證方式選擇 | 依全量回歸頻率政策：本 Sprint **未修改任何生產程式碼**（純新增測試），故僅需 `mvn test`，不需 `mvn verify -Pintegration-test` |

---

## 4. 誠實揭露（Rule 12）

1. **本 Sprint 未發現新漏洞，連續第 2 個 Sprint 維持此結論**：Sprint 77 是連續 7 個發現漏洞的 Sprint 後首次無新發現，本 Sprint（78）延續此結果——逐一審視 `OAuthService`/`PromoService` 共 5 個 public 方法後，確認沒有需要修復的擁有權/租戶檢查缺口，不因先前連續多個 Sprint 有發現而勉強找理由標記缺口。
2. **`OAuthService` 的測試價值受限於現況，如實說明而非誇大覆蓋率**：由於核心邏輯是無條件拋出例外的 stub，新增的 4 個測試實質上只驗證「fail-closed、無副作用」這個單一行為模式，`findOrCreateOAuthUser` 內真正涉及擁有權判斷的邏輯（既有帳號查找、email 自動連結、新用戶建立）在目前狀態下完全無法被單元測試觸及，這不是本 Sprint 遺漏，而是功能本身尚未實作，已在測試 Javadoc 與本文件中明確記錄，避免後續開發者誤讀「已有測試」為「邏輯已驗證安全」。
3. **`mvn test`（未啟動 test DB）出現 3 個 Error 曾一度需要澄清**：初次執行全量回歸時 `SellerDashboardServiceCacheTest` 3 個方法因 `Failed to load ApplicationContext` 失敗，經確認為需要真實 PostgreSQL 的已知環境陷阱（非本 Sprint 迴歸），`make test-db-up` 後重跑確認 0 fail，如實記錄此排查過程而非略過不提。
4. **OAuth 特有安全考量無法評估，不代表已確認安全**：state/CSRF、redirect_uri 驗證、token 綁定等考量因功能未實作而無法測試或審查，明確記錄為「待未來實際串接時重新評估」，而非誤導性地宣稱「已審查通過」。

---

## 5. Demo 重點

- **誠實面對「規模遠小於預期」的探查結果**：`OAuthService` 原先被視為與其他 Service 同等規模的測試強化對象，探查後發現其核心邏輯是死碼，及時調整測試策略（聚焦 fail-closed 行為而非勉強測試不存在的邏輯），並利用節省下的時間一併完成 `PromoService`。
- **死碼識別方法可複用於未來類似情境**：先確認「public 方法的哪些程式碼路徑實際可達」再設計測試案例，避免對無法觸及的邏輯撰寫看似完整實則無意義的測試。
- **環境陷阱與程式碼迴歸的區分**：`mvn test` 初次出現的 3 個 Error 透過分析錯誤訊息（`Failed to load ApplicationContext` + 已知需要真實 DB 的整合測試）快速確認非本 Sprint 造成，避免誤判為程式碼缺陷而白費排查時間。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
