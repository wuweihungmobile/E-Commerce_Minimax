# Sprint 74 計劃 / Sprint 74 Plan

> **Sprint 編號**: Sprint 74
> **期間**: 2026-07-06
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_73_PLAN.md` §7「後續 Sprint 待處理清單」——`CmsService`（11 方法，測試目錄完全不存在）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 73 收尾狀態 | ✅ 已收尾並 push | `ReviewService` 測試強化 + `DEF-028`/`DEF-029`/`DEF-030` 跨租戶修復完成 |
| `CmsService` 範圍探查 | ✅ 完成 | 1 個 Service（11 public 方法、418 行），與 Sprint 66/67/69/71（皆 8 SP 量級）相當，**單一 Sprint 涵蓋、不拆分** |
| 探查中發現的擁有權/租戶隔離問題 | ✅ 已標記並記錄：**2 項併入本 Sprint 修復**（`DEF-032`/`DEF-033`），1 項因涉及 API 簽名/`SecurityConfig` 變更等業務判斷，記錄擱置（`DEF-034`） | 詳見 US-002（`DEF-032`）、US-003（`DEF-033`）、US-004（`DEF-034` 擱置記錄） |
| 探查誠實揭露 | `CmsService` 11 個方法**全數完全零測試覆蓋**（無單元測試、無整合/E2E 測試），為多 Sprint 測試強化計劃中首個「整個 Service 零覆蓋」的案例 | 依 Rule 12「大聲失敗」誠實揭露範圍界線 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

| 方法 | 既有測試涵蓋 | 擁有權/租戶檢查現況 |
|---|---|---|
| `createPage` | ❌ 無 | 建立時以 `TenantContext.getCurrentTenant()` 設定自身 tenantId，無需額外檢查 |
| `updatePage` | ❌ 無 | ❌ **缺口（`DEF-032`）**：`findById` 後直接修改，完全無租戶檢查 |
| `publishPage` | ❌ 無 | ❌ **缺口（`DEF-032`）**：同上 |
| `getPageBySlug`（公開） | ❌ 無 | ⚠️ **待決策（`DEF-034`）**：完全不做租戶過濾，且 `/v2/cms/**` 實際未列入 `SecurityConfig` `permitAll()` |
| `getPages`（Admin 列表） | ❌ 無 | ❌ **缺口（`DEF-033`）**：呼叫無租戶過濾的查詢，Repository 已有 `findByTenantIdAndStatusOrderBySortOrderAsc` 卻未使用 |
| `createBanner` | ❌ 無 | 建立時以 `TenantContext.getCurrentTenant()` 設定自身 tenantId，無需額外檢查 |
| `updateBanner` | ❌ 無 | ❌ **缺口（`DEF-032`）**：同 `updatePage` |
| `publishBanner` | ❌ 無 | ❌ **缺口（`DEF-032`）**：同 `publishPage` |
| `getBanners`（Admin 列表） | ❌ 無 | ❌ **缺口（`DEF-033`）**：同 `getPages`，`BannerRepository.findByTenantIdAndStatus` 已存在卻未使用 |
| `getActiveBanners`（公開） | ❌ 無 | ⚠️ **待決策（`DEF-034`）**：同 `getPageBySlug` |
| `recordBannerClick`（公開） | ❌ 無 | 純計數遞增，無資料外洩風險，無需擁有權檢查 |

REST 層由 `CmsController.java`（`/v2/cms/**`）暴露。

---

## 2. Sprint 74 目標

> **主題**: `CmsService` 11 個零覆蓋方法單元測試從零建立 + 2 項探查發現的跨租戶問題修復（已確認）+ 1 項待決策事項記錄

為 `CmsService` 先前完全零覆蓋的 11 個方法建立單元測試，並修復探查階段已確認的 2 項跨租戶檢查缺口（比照 `DEF-019/024/026/028/029` 既有前例），同時將 1 項涉及業務/架構判斷的公開端點租戶範圍問題記錄待決策。

---

## 3. User Story

### US-001：`CmsService` 11 個方法單元測試從零建立

> **SP**: 8 | **優先級**: P1 | **狀態**: 待執行

**AC-001-1**：新增 `CmsServiceTest.java`，涵蓋全部 11 個方法的正常路徑與既有錯誤路徑（`E_8004`/`E_8005`/`E_9005`）。

**AC-001-2**：測試風格比照既有 `ReviewServiceTest`/`OrderServiceTest`：`@ExtendWith(MockitoExtension.class)` + `@Mock` repository + `@InjectMocks` service，`TenantContext` 於各測試內設定、`@AfterEach` 清理（含 `SecurityContextHolder.clearContext()`），AssertJ 風格斷言。

**AC-001-3**：過程中若發現生產程式碼問題則停止修改、記錄待決策，不自行假設修復（US-002/003 已確認的 2 項、US-004 待決策記錄除外）。

---

### US-002：修復 `updatePage`/`publishPage`/`updateBanner`/`publishBanner` 跨租戶寫入（`DEF-032`，已確認）

> **SP**: 2 | **優先級**: P0（安全） | **狀態**: 待執行

**背景**：四個寫入方法皆呼叫 `findById` 後直接修改/發布，完全沒有擁有權/租戶檢查。Controller 端僅要求 `cms:update`/`cms:publish` 權限，此權限可能分散於各租戶的管理者角色，任一租戶管理者可竄改/發布其他租戶的頁面或橫幅，屬跨租戶寫入 IDOR，與 `DEF-019`（`LogisticsService.createLogistics`）/`DEF-024`/`DEF-028` 同一 tenant-based 模式。

**AC-002-1**：先在 `CmsServiceTest.java` 新增**修復前會失敗（紅燈）**測試：以租戶 B 的 `TenantContext` 呼叫四個方法（資源屬租戶 A），驗證修復前會錯誤地成功寫入。

**AC-002-2**：新增 `checkCmsTenantOwnership(UUID resourceTenantId)` + `isCurrentUserAdmin()` 兩個 helper（比照 `ReviewService.checkReviewManagementAuthorization`/`isCurrentUserAdmin`），四處呼叫前置檢查，越權拋 `E_1007`。

**AC-002-3**：AC-002-1 測試轉綠；新增本租戶放行、admin 跨租戶放行的對照測試，避免修復矯枉過正。

---

### US-003：修復 `getPages`/`getBanners` 跨租戶讀取（`DEF-033`，已確認）

> **SP**: 2 | **優先級**: P0（安全） | **狀態**: 待執行

**背景**：`getPages`/`getBanners`（Admin 列表）呼叫 `findByStatusOrderBySortOrderAsc` 完全無租戶過濾，即使 `ContentPageRepository`/`BannerRepository` 皆已有 `findByTenantIdAndStatus(OrderBySortOrderAsc)` 方法卻從未被使用（與 Sprint 65/66 的「查詢方法寫好卻沒接上」死碼模式相同）。任一持有 `cms:read` 權限的租戶管理者皆可取得系統中所有租戶的頁面/橫幅列表。

**AC-003-1**：先在 `CmsServiceTest.java` 新增**修復前會失敗（紅燈）**測試：以租戶 A 的 `TenantContext` 呼叫 `getPages`/`getBanners`，驗證修復前會取用錯誤的（未租戶過濾的）查詢路徑。

**AC-003-2**：`getPages`/`getBanners` 改為：`isCurrentUserAdmin()` 沿用舊查詢（跨租戶總覽），非 admin 一律改用既有的租戶過濾查詢方法（無需新增 Repository 方法，比照 `DEF-026`/`DEF-029` 保留舊方法 + 分支使用模式）。

**AC-003-3**：AC-003-1 測試轉綠；新增租戶 A 僅看到自己租戶資料、admin 看到全部的對照測試。

---

### US-004：記錄公開端點租戶範圍待決策事項（`DEF-034`，擱置不修）

> **SP**: 0（僅文件記錄） | **優先級**: 中 | **狀態**: 待執行

**背景**：探查階段發現 `getPageBySlug`/`getActiveBanners`（公開瀏覽端點）完全不做租戶過濾；`TenantContextFilter` 對匿名請求恆設為 `SYSTEM_TENANT_ID`，無法沿用 `TenantContext` 解析訪客瀏覽的租戶。經比對 `PostController`/`PostService.getPublishedPostBySlug`（同屬 CMS 領域，`/v2/posts` 已 `permitAll()`）發現既有慣例是「呼叫端明確傳入 `tenantId` query 參數」；另發現 `/v2/cms/**` 實際未列入 `SecurityConfig` 的 `permitAll()` 清單，與程式碼註解「(公開)」不符。修復方案涉及 API 簽名變更與 `SecurityConfig` 調整，需業務/架構判斷，經使用者確認本 Sprint 擱置僅記錄。

**AC-004-1**：於 `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-034`，記錄為 🟡 中優先級待決策項目，不排入排程。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|----|
| US-001 | `CmsService` 11 個方法單元測試從零建立 | 8 | P1 |
| US-002 | 修復 `updatePage`/`publishPage`/`updateBanner`/`publishBanner` 跨租戶寫入（`DEF-032`） | 2 | P0（安全） |
| US-003 | 修復 `getPages`/`getBanners` 跨租戶讀取（`DEF-033`） | 2 | P0（安全） |
| US-004 | 記錄公開端點租戶範圍待決策事項（`DEF-034`） | 0 | 中 |
| **合計** | | **12** | |

> **Velocity 參考**：略低於 Sprint 73（14 SP，3 項安全修復），因本 Sprint 僅 2 項安全修復（1 項因涉及業務判斷擱置僅記錄）；與 Sprint 66/67/69/71 一般測試強化 Sprint（8 SP）+ 少量安全修復量級相當。

---

## 5. Definition of Done

- [x] US-001：新增 `CmsServiceTest.java`（新檔，涵蓋全部 11 個方法，共 27 個測試）
- [x] 開發-編譯-測試循環：每完成一批測試立即編譯 + 執行驗證，未累積
- [x] US-002：新增修復前紅燈測試（確認失敗）→ 新增 `checkCmsTenantOwnership`/`isCurrentUserAdmin` → 修復 `updatePage`/`publishPage`/`updateBanner`/`publishBanner` → 測試轉綠
- [x] US-003：新增修復前紅燈測試（確認失敗）→ 修復 `getPages`/`getBanners` → 測試轉綠
- [x] 因本 Sprint 修改生產程式碼（US-002+003），完成後執行全量回歸 `mvn verify -Pintegration-test`，**1085 tests 0 fail**（單元 743 + 整合 342）
- [x] `make validate-schema` 無漂移，EXIT_CODE=0
- [x] US-004：`DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-034`（🟡 中優先級待決策，使用者已確認擱置）
- [x] `DEFERRED_ITEMS_TRACKER.md` 更新（`DEF-032`/`DEF-033` 記入「已完成延後項目」）
- [x] Sprint 74 Review / Retro / Release Notes + trackers
- [x] `CmsServiceTest.java` 過程中發現未使用的 `import org.springframework.data.domain.Page;`（checkstyle-test 攔截，比照 Sprint 66 `AuthServiceTest` 同類問題），已移除並重新驗證全量回歸通過
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試 | `CmsServiceTest.java`（新檔） |
| 生產程式碼修復 | `CmsService.java`（US-002/003，新增 `checkCmsTenantOwnership`/`isCurrentUserAdmin` helper） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 `DEF-032`/`DEF-033`/`DEF-034`） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 7. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 75+ 規劃參考：

1. `ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`
2. 待決策事項：`DEF-034`（`CmsService` 公開端點租戶範圍設計）、`ADMIN` 角色權限邊界（租戶內 vs 全域）盤點（延續自 Sprint 73 Retro）

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
