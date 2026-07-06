# Sprint 78 計劃 / Sprint 78 Plan

> **Sprint 編號**: Sprint 78
> **期間**: 2026-07-06
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_76_PLAN.md`/`SPRINT_77_PLAN.md` §7「後續 Sprint 待處理清單」——`OAuthService`（`PromoService`/`IdempotencyService`/`FeatureToggleService` 視時間視情況一併涵蓋）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 77 收尾狀態 | ✅ 已收尾並 push | 4 個通知相關 Service 探查 + 測試補齊完成，`DEF-038` 記錄待決策 |
| `OAuthService` 範圍探查 | ✅ 完成，範圍遠小於預期 | `core/oauth/OAuthService.java`（211 行，2 個 public 方法）：`exchangeCodeForUserInfo`（兩個 public 方法共用的第一步）目前是**未完成的 stub**，無條件拋出 `UnsupportedOperationException`（程式碼註解「模擬實現」）。此現況已是 pre-existing 已知技術債，非本 Sprint 新發現，詳見「1.1 探查結論」 |
| 時間充裕，一併涵蓋 `PromoService` | ✅ 完成 | `core/promo/PromoService.java`（111 行，3 個 public 方法），設計正確，詳見「1.2 探查結論」 |
| 探查中發現的擁有權/租戶隔離問題 | ✅ 已逐一審視，**未發現新的擁有權/租戶檢查缺口** | 與 Sprint 77 相同，屬「探查後確認無新缺口」的 Sprint（累計為第 2 次，前一次為 Sprint 77） |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

### 1.1 `OAuthService`（`core/oauth/OAuthService.java`，211 行，2 個 public 方法）

| 方法 | 既有測試涵蓋（修復前） | 擁有權/租戶檢查現況 |
|---|---|---|
| `handleOAuthLogin(OAuthDto.AuthRequest)` | 無 | 第一步即呼叫 private 方法 `exchangeCodeForUserInfo`，該方法**無條件拋出 `UnsupportedOperationException`**（`OAuthService.java:115`，程式碼註解「模擬實現」）。因此後續的 `findOrCreateOAuthUser`（既有 OAuth 帳戶查找、email 既有帳號自動連結、新用戶建立與 tenant 指派——這段才是真正涉及擁有權判斷的邏輯）在目前正式接線下是**無法從 public API 觸及的死碼**，沒有可利用的路徑 |
| `linkOAuthAccount(UUID userId, OAuthDto.LinkRequest)` | 無 | 同樣第一步呼叫 `exchangeCodeForUserInfo` 立即拋出，`existsByProviderAndProviderUserId` 衝突檢查與 `oAuthAccountRepository.save` 寫入同樣是死碼。`userId` 參數本身由 `OAuthController.linkOAuthAccount` 從已認證的 `@AuthenticationPrincipal UserPrincipal` 取得（非 request body 欄位），**Controller 層設計正確，不存在代他人連結 OAuth 帳號的 IDOR 風險** |

**探查結論**：
- 此現況（OAuth 整合尚未實作，僅為框架）**並非本 Sprint 新發現**，已記錄於 `docs/06_quality/TECHNICAL_DEBT_TODO_SCAN.md`（「模擬實現」🟡中）與 `docs/04_planning/PRODUCT_BACKLOG.md`（P3「OAuth2/KYC 實名」，待商業需求觸發），全庫搜尋確認沒有其他 OAuth Provider 串接實作可替代此 stub。
- 因兩個 public 方法皆會在觸及任何擁有權判斷邏輯前就 fail-closed，**沒有發現需要在本 Sprint 修復的擁有權/租戶檢查缺口**。
- OAuth 特有的安全考量（`state` 參數 CSRF 防護、`redirect_uri` 白名單驗證、token 交換後的租戶/使用者綁定）目前**因整合尚未實作而無法評估**——待未來實際串接 OAuth Provider API 時須重新審查；另觀察到 `OAuthDto.AuthRequest`/`LinkRequest` 目前完全沒有 `state` 欄位，屆時需一併補上。

### 1.2 `PromoService`（`core/promo/PromoService.java`，111 行，3 個 public 方法）

| 方法 | 既有測試涵蓋（修復前） | 擁有權/租戶檢查現況 |
|---|---|---|
| `validatePromoCode(String, UUID tenantId)` | 無單元測試（僅 `M11CartPromoIntegrationTest` 間接涵蓋） | ✅ 正常，透過 `PromoCodeRepository.findByCodeIgnoreCaseAndTenantId` 在**查詢層級**做租戶隔離（非事後過濾），設計正確 |
| `computeDiscount(PromoCode, BigDecimal)` | 同上 | 接受呼叫端已查得的 `PromoCode` 物件，本身不重複做租戶檢查。檢視唯一呼叫端 `RedisCartService` 後確認：拿到的都是先前 `validatePromoCode` 已用正確 `tenantId` 查出的 promo，屬合理的信任邊界（同一次呼叫鏈內、非跨模組暴露的方法） |
| `incrementUsageCount(PromoCode)` | 同上 | 同上 |

**探查結論**：`PromoService` 設計正確，租戶隔離在 Repository 查詢條件層級完成，未發現需要修復的擁有權/租戶檢查缺口。

---

## 2. Sprint 78 目標

> **主題**: `OAuthService`/`PromoService` 單元測試補齊（先前皆為零單元測試覆蓋）+ 擁有權/租戶檢查與 OAuth 特有安全考量主動審視（結論：無新缺口；`OAuthService` 因整合未實作，測試聚焦於 fail-closed 行為驗證）

---

## 3. User Story

### US-001：`OAuthService`/`PromoService` 範圍探查

> **SP**: 1 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-001-1**：找到 `OAuthService`，列出檔案路徑、行數、全部 public 方法、既有測試狀況、依賴的 Repository/Service，確認每個方法是否有明顯的擁有權/租戶檢查邏輯，以及 OAuth 特有的安全考量（state/CSRF、redirect_uri 驗證、token 綁定）。

**AC-001-2**：探查中發現 `exchangeCodeForUserInfo` 無條件拋出 `UnsupportedOperationException`，確認此為 pre-existing 已知技術債（非新發現），並確認後續擁有權判斷邏輯（`findOrCreateOAuthUser`）目前是無法觸及的死碼。

**AC-001-3**：時間充裕，一併探查 `PromoService`（3 個 public 方法），確認租戶隔離設計正確。

---

### US-002：`OAuthService` 單元測試補齊

> **SP**: 2 | **優先級**: P1（測試防護網） | **狀態**: ✅ 完成

**背景**：`OAuthService` 2 個方法先前完全零單元測試，屬多 Sprint 測試強化計劃的既定排程模組。

**AC-002-1**：新增 `OAuthServiceTest.java`，驗證 `handleOAuthLogin`/`linkOAuthAccount` 在目前 stub 狀態下的 fail-closed 行為——不論 provider（GOOGLE/GITHUB）、userId 為何，皆立即拋出 `UnsupportedOperationException`，且不對 `UserRepository`/`OAuthAccountRepository`/`TenantRepository`/`JwtTokenService` 產生任何副作用（確認不會有帳號被意外建立/誤連結）。

**AC-002-2**：測試需以 Javadoc 明確記錄現況——為何目前只能測到 fail-closed 行為、`findOrCreateOAuthUser` 為何是死碼、未來真正串接 OAuth Provider API 時需要重新審查哪些安全考量，避免後續開發者誤以為此測試已涵蓋帳號連結的擁有權邏輯。

**AC-002-3**：`mvn test` 全數通過，因未修改生產程式碼，依全量回歸頻率政策不需執行 `mvn verify -Pintegration-test`。

---

### US-003：`PromoService` 單元測試補齊

> **SP**: 2 | **優先級**: P1（測試防護網） | **狀態**: ✅ 完成

**背景**：`PromoService` 3 個 public 方法先前無單元測試，僅有整合測試間接涵蓋。

**AC-003-1**：新增 `PromoServiceTest.java`，涵蓋 `validatePromoCode`（空白代碼、查無資料、停用、尚未開始、已過期、達使用上限、合法有效，並以 `ArgumentCaptor` 驗證傳入 Repository 的 tenantId 與代碼正規化正確）、`computeDiscount`（null 防呆、低於門檻、PERCENTAGE/FIXED_AMOUNT/FREE_SHIPPING 三種折扣類型計算與四捨五入、`maxDiscountAmount` 上限）、`incrementUsageCount`（次數遞增與儲存）。

**AC-003-2**：`mvn test` 全數通過。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|----|
| US-001 | `OAuthService`/`PromoService` 範圍探查 | 1 | P2 |
| US-002 | `OAuthService` 單元測試補齊 | 2 | P1 |
| US-003 | `PromoService` 單元測試補齊 | 2 | P1 |
| **合計** | | **5** | |

> **Velocity 參考**：本 Sprint 未發現需修復的漏洞，且 `OAuthService` 實際可測範圍遠小於預期（核心邏輯為死碼），屬本輪多 Sprint 測試強化計劃中規模最小的 Sprint 之一，量級低於 Sprint 77（7 SP）。

---

## 5. Definition of Done

- [x] US-001：完整探查 `OAuthService`（2 個 public 方法）與 `PromoService`（3 個 public 方法），確認既有測試覆蓋與擁有權檢查現況；確認 `OAuthService` 的 stub 現況為 pre-existing 已知技術債
- [x] US-002：新增 `OAuthServiceTest.java`（4 個測試），涵蓋 `handleOAuthLogin`/`linkOAuthAccount` 的 fail-closed 行為與無副作用驗證，`mvn test` 0 fail
- [x] US-003：新增 `PromoServiceTest.java`（17 個測試），涵蓋全部 3 個方法，`mvn test` 0 fail
- [x] 開發-編譯-測試循環：每完成一個測試檔案立即編譯 + 執行驗證，未累積
- [x] 本 Sprint**未修改任何生產程式碼**，依全量回歸頻率政策僅需 `mvn test`（全量 0 fail，含 `make test-db-up` 後重跑排除環境雜訊），不需 `mvn verify -Pintegration-test`
- [x] `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）
- [x] `DEFERRED_ITEMS_TRACKER.md` 本 Sprint 未新增任何 DEF（未發現需記錄的新缺口，無需更新此文件）
- [x] Sprint 78 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試（新檔） | `OAuthServiceTest.java`（4 個測試） |
| 後端測試（新檔） | `PromoServiceTest.java`（17 個測試） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 7. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 79+ 規劃參考：

1. `IdempotencyService`、`FeatureToggleService`（延續自 Sprint 66 排程，尚未探查，多 Sprint 測試強化計劃剩餘最後 2 個模組）
2. 待決策事項：`DEF-034`（`CmsService` 公開端點租戶範圍設計）、`DEF-037`（`ShippingTemplateService.calculateFee` 跨租戶查詢，已決策擱置）、`DEF-038`（`TenantContextFilter` ADMIN 跨租戶架構疑慮，🔴 高優先級，待業務/架構決策，可能影響過去 9 個既有 DEF 修復的前提，建議儘早排入決策議程）、`ADMIN` 角色權限邊界（租戶內 vs 全域）盤點（與 `DEF-038` 高度相關，建議合併評估）
3. 新觀察（本 Sprint）：`OAuthService` 是本輪多 Sprint 測試強化計劃中首個「核心邏輯為未完成 stub」的模組，探查方法（先確認可達性再設計測試）同樣適用於未來遇到類似「框架已搭好但功能未實作」的模組；累計連續 2 個 Sprint（77/78）探查後確認無新缺口，顯示先前發現的漏洞確實是特定模組的實際缺口，而非審視方法系統性製造假陽性

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
