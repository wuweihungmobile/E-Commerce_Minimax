# Release Notes - v2028.05.06-01 (Sprint 66)

**發布日期**: 2028-05-06（規劃）／實作完成 2026-07-05
**發布類型**: Patch（測試強化 + 死碼修復；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 66
**狀態**: ⏳ 待 push（本 Sprint commit；收尾後即 push，嚴禁 `--no-verify`）

> Sprint 66 主題：**多 Sprint 測試強化計劃（第一階段）——ProductService 搜尋死碼修復 + AuthService 測試從 0 建立**。

---

## 新功能 / 改進 🚀

- **`ProductService.getProducts` 關鍵字搜尋死碼修復**：修正前不論是否傳入 `keyword`，皆固定回傳全部上架商品（`keyword` 分支誤用與無篩選分支完全相同的查詢方法）。新增 `ProductRepository.searchByTenantIdAndKeyword`，現在真正依商品標題/描述（大小寫不敏感）過濾。
- **`AuthService` 單元測試從 0 建立**：新增 `AuthServiceTest.java`，16 個 Mockito 單元測試涵蓋 `register`/`login`/`refreshToken`/`logout`/`getCurrentUser` 共 5 個 public 方法的成功路徑與所有錯誤碼分支。

## 測試 / 驗證 ✅

- **後端單元**：`mvn test` **494 tests，0 fail**（含新增 `AuthServiceTest` 16 個 + `ProductServiceTest` 3 個）。
- **後端完整整合**：`mvn verify -Pintegration-test`（含 failsafe）**494 + 342 = 836 tests，0 fail**。
- **schema 漂移守門**：無漂移（無新 migration）。
- **前端**：本 Sprint 無前端變動，未執行。

## 技術決策 / 已知限制 ⚠️

- **`AuthServiceTest` 僅涵蓋 Mockito 單元測試**：比照既有 `AdminServiceTest`/`FaqServiceTest` 風格，不含真實 DB 整合測試（既有 `AuthControllerTrueIntegrationTest` 已涵蓋部分 happy path，本 Sprint 未擴充）。
- **pre-commit 與 `mvn verify` checkstyle 檢查範疇落差**：`AuthServiceTest.java` 初版含未使用 import，pre-commit（僅檢查 `src/main`）未攔截，於全量 `mvn verify -Pintegration-test`（`checkstyle-test` execution 檢查測試原始碼 `UnusedImports`）才被抓到，已以獨立 commit 修正並重新驗證通過。

## 資料庫遷移 🗄️

- 無（schema-free）。

## 內含 Commit（Sprint 66）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 66 Plan | 668009a | 多 Sprint 測試強化計劃第一階段（2 US / 8 SP）|
| US-001 | c1705b8 | 修正 ProductService 關鍵字搜尋死碼 |
| US-002 | 18319f2 | 新增 AuthServiceTest（16 個測試）|
| US-002 修正 | cce796c | 移除未使用 import，修正 checkstyle-test 違規 |
| Sprint 66 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
