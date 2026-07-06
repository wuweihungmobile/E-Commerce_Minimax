# Release Notes - v2028.08.26-01 (Sprint 74)

**發布日期**: 2028-08-26（規劃）／實作完成 2026-07-06
**發布類型**: 🧪 測試強化 + 🔴 安全修復（P0；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 74（多 Sprint 測試強化計劃）
**狀態**: ✅ 已 push

> Sprint 74 主題：**`CmsService` 11 個零覆蓋方法單元測試從零建立**，並處理範圍探查階段主動審視發現的 2 項跨租戶檢查缺口。依 `SPRINT_73_PLAN.md` 排程建議執行，動手前先完整探查範圍，並依既定要求主動以「誰可以呼叫、有無檢查資源歸屬」的角度逐一審視 11 個方法，確認 2 項缺口後才動手實作；第 3 項發現因涉及 API 簽名/`SecurityConfig` 變更，記錄待決策不強行修復。

---

## 🧪 測試強化（US-001）

- **`CmsService` 11 個方法單元測試從零建立**：`createPage`/`updatePage`/`publishPage`/`getPageBySlug`/`getPages`/`createBanner`/`updateBanner`/`publishBanner`/`getBanners`/`getActiveBanners`/`recordBannerClick`，先前完全沒有任何單元測試或整合/E2E 測試涵蓋——多 Sprint 測試強化計劃中首個「整個 Service 全數方法零覆蓋」的案例。
- 新增 `CmsServiceTest.java`（新檔），合計 **27 個單元測試**，涵蓋正常路徑、`E_8004`/`E_8005`（找不到）、`E_9005`（slug 重複）、`E_1007`（權限不足）等錯誤路徑。

## 🔴 安全修復 P0：`DEF-032`

- **`updatePage`/`publishPage`/`updateBanner`/`publishBanner` 跨租戶寫入 IDOR**：
  - **修復前**：完全沒有擁有權/租戶檢查，任一租戶的管理者（持有 `cms:update`/`cms:publish` 權限）皆可竄改/發布其他租戶的頁面或橫幅。
  - **紅燈證明**：新增測試斷言「他租戶資源不得放行」，修復前執行**確認失敗**，實測證實漏洞存在。
  - **修復後**：新增 `checkCmsTenantOwnership`/`isCurrentUserAdmin` helper，採「本租戶 or admin」放行，越權拋 `E_1007`。

## 🔴 安全修復 P0：`DEF-033`

- **`getPages`/`getBanners`（Admin 列表）跨租戶讀取洩漏**：
  - **修復前**：呼叫 `findByStatusOrderBySortOrderAsc` 完全無租戶過濾，即使 Repository 已有現成的租戶過濾查詢方法卻從未接上，任一租戶管理者皆可取得系統中所有租戶的頁面/橫幅列表。
  - **紅燈證明**：新增測試斷言結果僅含本租戶資料，修復前執行**確認失敗**，實測證實漏洞存在。
  - **修復後**：非 admin 改用既有的租戶過濾查詢，admin 沿用舊查詢維持跨租戶總覽能力。

## ⚠️ 待決策記錄：`DEF-034`（本 Sprint 不修復）

- **`getPageBySlug`/`getActiveBanners`（公開端點）完全不做租戶過濾**：探查確認訪客瀏覽會混雜所有租戶已發布的頁面/橫幅內容。既有 `PostController`/`PostService` 慣例是要求呼叫端明確傳入 `tenantId`，但修復涉及 API 簽名變更；另意外發現 `/v2/cms/**` 實際未列入 `SecurityConfig` 的 `permitAll()` 清單，與程式碼註解「(公開)」不符。使用者已確認本 Sprint 擱置僅記錄，待業務/架構決策後排入未來 Sprint。

## 測試 / 驗證 ✅

- **`CmsServiceTest` 紅綠燈流程**：修復前 27 tests 4 Failures + 2 Errors（`DEF-032`×4 / `DEF-033`×2）→ 修復後 27 tests 0 fail。
- **後端單元回歸**：**743 tests，0 fail**（較 Sprint 73 增加本 Sprint 新增的 27 個）。
- **後端全量回歸**（`mvn verify -Pintegration-test`）：**BUILD SUCCESS**，單元 743 + 整合（failsafe）342 = **1085 tests，0 fail**。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。
- **checkstyle**：首次全量回歸攔截 `CmsServiceTest.java` 未使用的 `import org.springframework.data.domain.Page;`（比照 Sprint 66 `AuthServiceTest` 同類問題），移除後重新驗證通過。

## 技術決策 / 已知限制 ⚠️

- **`isCurrentUserAdmin()` 沿用既有前例慣例**：`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 皆視為可跨租戶放行，與 `DEF-018/019/023/024/028/029/030` 延續相同慣例，`ADMIN` 角色權限邊界一致性盤點仍待未來排入。
- **`DEF-034`（待決策，記錄不修）**：`getPageBySlug`/`getActiveBanners` 公開端點租戶範圍設計 + `/v2/cms/**` 未列入 `permitAll()`。
- **無前端變動**：本 Sprint 純後端 Service 層測試與修復。

## 資料庫遷移 🗄️

- 無（schema-free；純 Java service 層邏輯，未新增 Repository 方法——`DEF-033` 修復沿用既有已存在但先前未使用的查詢方法）。

## 內含 Commit（Sprint 74）

| US / 項目 | 說明 |
|----------|------|
| Sprint 74 Plan | `CmsService` 測試強化 + 2 項安全問題處理計劃（4 US / 12 SP）|
| US-001 | `CmsService` 11 個方法單元測試從零建立（27 個測試）|
| US-002 | `DEF-032` 修復：`updatePage`/`publishPage`/`updateBanner`/`publishBanner` 跨租戶寫入 |
| US-003 | `DEF-033` 修復：`getPages`/`getBanners` 跨租戶讀取 |
| US-004 | `DEF-034` 待決策事項記錄 |
| Sprint 74 收尾 | Review / Retro / Release Notes + trackers |

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
