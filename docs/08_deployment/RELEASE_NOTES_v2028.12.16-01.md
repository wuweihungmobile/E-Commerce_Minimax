# Release Notes - v2028.12.16-01 (Sprint 82)

**發布日期**: 2028-12-16（規劃）／實作完成 2026-07-08
**發布類型**: 🔒 安全/設計修復（CMS 公開端點租戶範圍）；後端聚焦，無前端變動（前端目前無呼叫端）
**Sprint**: Sprint 82（DEF-034，承接 Sprint 81 retro 記錄的候選項目）

> Sprint 82 主題：修復 `CmsService` 的 `getPageBySlug`/`getActiveBanners`/`recordBannerClick` 三個公開瀏覽端點完全不做租戶過濾的設計缺口。使用者拍板業務決策：這些 CMS 頁面/橫幅定位為訪客可瀏覽的公開行銷內容，比照既有 `PostController` 前例修復。

---

## 🔒 安全/設計修復：CMS 公開端點租戶範圍（DEF-034）

- **問題**：`getPageBySlug`/`getActiveBanners`/`recordBannerClick` 完全未依 `tenantId` 過濾，任何租戶已發布的頁面/橫幅會混雜出現在瀏覽結果中。重新探查發現與原始記錄有出入：這三個端點實際上**沒有** `permitAll()`，需要登入才能呼叫（登入後任何租戶使用者皆看到混在一起的內容）；且前端**完全沒有任何地方**呼叫這三個端點，修改零風險。
- **業務決策**：定位為公開行銷內容（如首頁橫幅、關於我們頁），比照 `PostController`/`PostService.getPublishedPostBySlug` 既有模式——`permitAll()` + 呼叫端明確傳入 `tenantId` query 參數。
- **修復**：`ContentPageRepository`/`BannerRepository` 新增租戶範圍化查詢方法；`CmsService` 三方法新增 `tenantId` 參數 + `E_1002` 空值檢查；`CmsController` 三端點新增必填 `tenantId` 參數；`SecurityConfig` 新增 3 條 `permitAll()`。
- **修復過程中自我糾正的潛在風險**：原計畫對 `/v2/cms/pages/*` 整段 `permitAll()`，發現會與 `PUT /v2/cms/pages/{pageId}`（Admin 更新）path pattern 衝突，若不限定 HTTP method 會誤放行 Admin 寫入端點；改用 `requestMatchers(HttpMethod.GET, ...)` 限定方法後才套用。

## 測試 / 驗證 ✅

- **`CmsServiceTest`**：32 tests（含 tenantId 必填驗證、跨租戶查無結果不外洩案例），0 fail。
- **後端全量整合回歸**（`mvn verify -Pintegration-test`，`make test-db-up` 後，因涉及 `SecurityConfig` 全站授權規則變更）：詳見 commit 訊息最終數字。
- **schema 漂移守門**：`make validate-schema` 無漂移（純查詢邏輯與簽名變更，無 migration）。

## 內含 Commit（Sprint 82）

| US / 項目 | 說明 |
|----------|------|
| Sprint 82 Plan | DEF-034 修復規劃（含業務決策記錄）|
| DEF-034 | `ContentPageRepository`/`BannerRepository` 租戶範圍化查詢 + `CmsService`/`CmsController` tenantId 參數 + `SecurityConfig` permitAll |
| Sprint 82 收尾 | Retro / Release Notes + trackers（DEF-034 移至已完成）|

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
**基於**: AISDLC v0.09 Release Management Workflow
