# Release Notes - v2028.04.08-01 (Sprint 64)

**發布日期**: 2028-04-08（規劃）／實作完成 2026-07-05
**發布類型**: Minor（Admin 列表真分頁化+篩選；schema-free；後端+前端）
**Sprint**: Sprint 64
**狀態**: ⏳ 待 push（本 Sprint commit；收尾後即 push，嚴禁 `--no-verify`）

> Sprint 64 主題：**Admin 租戶/使用者列表真分頁化 + 伺服器端篩選**。修正一個實際的效能隱患——分頁機制本身失效。

---

## 新功能 / 改進 🚀

- **`AdminService.getTenants` 真分頁化**：改用 `JpaSpecificationExecutor` + `Specification` 動態組合 `status`/`keyword`（name/slug）篩選，真資料庫分頁（修正前 `page`/`size` 參數完全未被使用）。
- **`AdminService.getUsers` 真分頁化**：同上模式，新增 `status`/`keyword`（email/fullName）篩選，既有 `tenantId`/`role` 篩選改走 Specification。
- **前端 `admin/tenants/page.tsx`**：移除 client-side filter，改為伺服器端篩選+分頁；分頁籤數量改用各狀態獨立查詢取得。

## 測試 / 驗證 ✅

- **後端單元**：`mvn test` **481 tests，0 fail**（含更新 2 個 + 新增 4 個）。
- **後端完整整合**：`mvn verify -Pintegration-test`（含 failsafe）**全量 342 tests，0 fail**（`AdminControllerE2ETest` 18/18 無迴歸）。
- **schema 漂移守門**：無漂移（無新 migration）。
- **前端**：lint/tsc/build 皆 0 error。

## 技術決策 / 已知限制 ⚠️

- **問題比原候選描述更嚴重**：真正問題是分頁機制本身失效（`findAll()` 取回全部資料），非僅缺篩選功能。
- **分頁籤數量查詢待優化**：前端目前用 4 次 `size=1` 查詢取得各狀態數量，功能正確但非最有效率，未來可考慮後端提供聚合計數端點。

## 資料庫遷移 🗄️

- 無（schema-free；欄位皆已存在）。

## 內含 Commit（Sprint 64）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 64 Plan | 55480d4 | Admin 列表真分頁化+篩選計劃（2 US / 8 SP）|
| US-001+US-002 | 9a3440d | getTenants/getUsers 真分頁化 + 篩選 + 前端修正 |
| Sprint 64 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
