# Sprint 66 Review / Sprint 66 評審會議

> **Sprint 編號**: Sprint 66
> **期間**: 2028-04-23 ~ 2028-05-06
> **評審日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 多 Sprint 測試強化計劃（第一階段）——ProductService 搜尋死碼修復 + AuthService 測試從 0 建立

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 修正 ProductService.getProducts 的關鍵字搜尋死碼 | 3 | ✅ 完成 |
| US-002 | AuthService 單元測試從 0 建立 | 5 | ✅ 完成 |

**8 SP 全數完成**。全面盤點超過 15 個核心 Service 零測試覆蓋後，優先處理風險最高（認證核心）與影響最直接（搜尋功能失效的真實 bug）的兩項。

---

## 2. 交付內容

- **`ProductRepository.searchByTenantIdAndKeyword`**：依 `Listing.title`/`Listing.description`（大小寫不敏感）比對關鍵字，取代原本被誤用、與無篩選分支完全相同的方法。
- **`ProductService.getProducts`**：`keyword` 分支改用新查詢方法，關鍵字搜尋真正生效。
- **`ProductServiceTest`**：新增 3 個測試，涵蓋關鍵字命中/不命中/無篩選三種情境，證明修正前後行為差異。
- **`AuthServiceTest.java`**：全新建立，16 個 Mockito 單元測試，涵蓋 `register`（成功/email 重複/含 tenantId）、`login`（成功/帳號不存在/密碼錯誤）、`refreshToken`（成功/失效/過期/已撤銷/帳號非啟用）、`logout`（單一 token/全部撤銷）、`getCurrentUser`（含租戶/無租戶/不存在）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`mvn test`）| ✅ **494 tests，0 fail**（含新增 AuthServiceTest 16 + ProductServiceTest 3）|
| 後端完整整合（`mvn verify -Pintegration-test`，含 failsafe）| ✅ **494 + 342 = 836 tests，0 fail** |
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（無新 migration）|
| 前端 | 本 Sprint 無前端變動，未執行 |

---

## 4. 誠實揭露（Rule 12）

1. **`mvn verify` 抓到 pre-commit 未涵蓋的 checkstyle-test 違規**：`AuthServiceTest.java` 初版含未使用的 import（`org.mockito.ArgumentMatchers.eq`）。pre-commit hook 執行的 `mvn checkstyle:check` 直接呼叫預設 execution（僅檢查 `src/main`），未觸發綁定於 `verify` phase、專門檢查測試原始碼 `UnusedImports` 的 `checkstyle-test` execution，因此第一次 commit 後才在全量 `mvn verify -Pintegration-test` 時被攔截（`BUILD FAILURE`）。以獨立 commit（`cce796c`）移除該 import 修正，修正後重跑全量整合測試確認 0 fail。此為既有 pre-commit/verify 檢查範疇落差，非本 Sprint 引入的新問題，但值得記錄供未來參考：**pre-commit 的快速檢查無法完全取代 `mvn verify` 的完整檢查**。
2. **US-002 僅涵蓋 Mockito 單元測試，未含真實 DB 整合測試**：比照既有 `AdminServiceTest`/`FaqServiceTest` 風格，`AuthServiceTest` 全數以 mock repository/JWT 服務驗證邏輯分支，未新增 `AuthControllerTrueIntegrationTest` 以外的真實 DB 整合案例（該既有整合測試已涵蓋部分 happy path，本 Sprint 未擴充）。

---

## 5. Demo 重點

- **關鍵字搜尋真正生效**：`GET /v2/products?keyword=xxx` 現在會依商品標題/描述真正過濾，而非過去固定回傳全部上架商品。
- **AuthService 五大方法皆有自動化回歸保護**：`register`/`login`/`refreshToken`/`logout`/`getCurrentUser` 的成功路徑與所有錯誤碼分支（E_1001~E_1006）皆有測試覆蓋，未來修改認證邏輯時可立即發現回歸。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
