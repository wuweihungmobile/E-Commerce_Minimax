# Release Notes - v2028.08.12-01 (Sprint 73)

**發布日期**: 2028-08-12（規劃）／實作完成 2026-07-06
**發布類型**: 🧪 測試強化 + 🔴 安全修復（P0；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 73（多 Sprint 測試強化計劃）
**狀態**: ✅ 已 push

> Sprint 73 主題：**`ReviewService` 7 個零覆蓋方法單元測試從零建立**，並處理範圍探查階段主動審視發現的 3 項擁有權/租戶檢查缺口。依 `SPRINT_71_PLAN.md`/`SPRINT_72_PLAN.md` 排程建議執行，動手前先完整探查範圍，並依使用者要求主動以「誰可以呼叫、有無檢查資源歸屬」的角度逐一審視 14 個方法，確認 3 項缺口後才動手實作。

---

## 🧪 測試強化（US-001）

- **`ReviewService` 7 個零覆蓋方法單元測試從零建立**：`updateReview`/`deleteReview`/`markHelpful`/`markAsHandled`/`markAsUnhandled`/`getReviewsByHandlingStatus`/`getUserReviews`，先前完全沒有任何單元測試或整合/E2E 測試涵蓋。
- 新增 `ReviewServiceTest.java`（新檔），合計 **18 個單元測試**，涵蓋正常路徑、`E_1087`（評價不存在）、`E_1007`（權限不足）等錯誤路徑。
- 其餘 7 個方法（`createReview`/`getReviewsByListingId`/`getRatingStats`/`searchReviews`/`addImage`/`removeImage`/`reorderImages`）已由既有整合/搜尋/快取測試涵蓋，不重複造測試。

## 🔴 安全修復 P0：`DEF-028`

- **`markAsHandled`/`markAsUnhandled` 跨租戶寫入 IDOR**：
  - **修復前**：完全沒有擁有權/租戶檢查，任一租戶的賣家（`SELLER`/`HOST`/`STORE_OWNER`/`ADMIN`）皆可竄改其他租戶商品評價的處理狀態。
  - **紅燈證明**：新增測試斷言「他租戶評價不得放行」，修復前執行**確認失敗**，實測證實漏洞存在。
  - **修復後**：新增 `checkReviewManagementAuthorization`/`isCurrentUserAdmin` helper，採「本租戶 or admin」放行，越權拋 `E_1007`。

## 🔴 安全修復 P0：`DEF-029`

- **`getReviewsByHandlingStatus` 跨租戶讀取洩漏**：
  - **修復前**：呼叫 `findByIsHandled` 完全無租戶過濾，任一租戶賣家皆可取得系統中所有租戶的評價列表。
  - **紅燈證明**：新增測試斷言結果不得包含他租戶評價，修復前執行**確認失敗**，實測證實漏洞存在。
  - **修復後**：新增 `ReviewRepository.findByIsHandledAndTenantId`，非 admin 改用租戶過濾查詢，admin 沿用舊查詢維持跨租戶總覽能力。

## 🔴 安全修復 P0：`DEF-030`

- **`getUserReviews` 匿名保護繞過（IDOR/隱私）**：
  - **修復前**：未檢查 `userId` 是否為呼叫者本人，任一持有 `order:read` 權限的使用者（含一般買家）皆可代入他人 `userId` 取得完整評價內容，繞過 `isAnonymous` 匿名保護。
  - **紅燈證明**：新增測試斷言非本人查詢應拋例外，修復前執行**確認失敗**，實測證實漏洞存在。
  - **修復後**：比照 `DEF-018` 買家自助模式，改為 owner-or-admin，非 admin 且非本人拋 `E_1007`。

## 測試 / 驗證 ✅

- **`ReviewServiceTest` 紅綠燈流程**：修復前 18 tests 4 Failures（`DEF-028`×2/`DEF-029`×1/`DEF-030`×1）→ 修復後 18 tests 0 fail。
- **後端單元回歸**（`mvn test`）：**716 tests，0 fail**。
- **後端全量回歸**（`mvn verify -Pintegration-test`）：**BUILD SUCCESS**，單元 716 + 整合（failsafe）342 = **1058 tests，0 fail**。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 僅新增 repository 查詢方法，無 entity/migration 變更）。
- **開發-編譯-測試循環**：紅燈測試撰寫過程中發現 2 類 fixture 缺陷（`reviewType` 遺漏導致的 NPE、stub 設計不當導致的巧合性失敗）並即時修正，確認乾淨紅燈才進行修復。

## 技術決策 / 已知限制 ⚠️

- **`isCurrentUserAdmin()` 沿用既有前例慣例**：`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 皆視為可跨租戶放行，與 `RolePermissionMapping.java` 註解「`ADMIN` 租戶內管理」存在潛在語意落差，此為既有慣例本身（`DEF-018/019/023/024`）延續而來，非本 Sprint 引入，已記錄待未來一次性盤點。
- **`DEF-031`（技術債，記錄不修）**：`markHelpful` 同一使用者重複投票會無限累加；`createReview`/`BookingReviewService.createBookingReview` 未驗證 `orderId`/`bookingId` 歸屬。皆非跨租戶 IDOR，使用者已決策擱置。
- **無前端變動**：本 Sprint 純後端 Service 層測試與修復。

## 資料庫遷移 🗄️

- 無（schema-free；純 Java service 層邏輯 + 新增 repository 查詢方法）。

## 內含 Commit（Sprint 73）

| US / 項目 | 說明 |
|----------|------|
| Sprint 73 Plan | `ReviewService` 測試強化 + 3 項安全問題處理計劃（5 US / 14 SP）|
| US-001 | `ReviewService` 7 個零覆蓋方法單元測試從零建立（18 個測試）|
| US-002 | `DEF-028` 修復：`markAsHandled`/`markAsUnhandled` 跨租戶寫入 |
| US-003 | `DEF-029` 修復：`getReviewsByHandlingStatus` 跨租戶讀取 |
| US-004 | `DEF-030` 修復：`getUserReviews` 匿名保護繞過 |
| US-005 | `DEF-031` 技術債記錄 |
| Sprint 73 收尾 | Review / Retro / Release Notes + trackers |

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
