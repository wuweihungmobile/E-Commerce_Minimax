# Sprint 74 Review / Sprint 74 評審會議

> **Sprint 編號**: Sprint 74
> **期間**: 2026-07-06
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: `CmsService` 11 個零覆蓋方法單元測試從零建立 + 2 項探查發現的跨租戶問題修復（已確認）+ 1 項待決策事項記錄

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `CmsService` 11 個方法單元測試從零建立 | 8 | ✅ 完成 |
| US-002 | 修復 `updatePage`/`publishPage`/`updateBanner`/`publishBanner` 跨租戶寫入（`DEF-032`） | 2 | ✅ 完成 |
| US-003 | 修復 `getPages`/`getBanners` 跨租戶讀取（`DEF-033`） | 2 | ✅ 完成 |
| US-004 | 記錄公開端點租戶範圍待決策事項（`DEF-034`） | 0 | ✅ 完成 |

**12 SP 全數完成**。本 Sprint 開始前先完整探查 `CmsService` 範圍，確認 11 個方法全數零覆蓋（多 Sprint 測試強化計劃中首個「整個 Service 零覆蓋」案例），並依使用者要求主動以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者/當前租戶」的角度逐一審視，發現 2 項跨租戶安全缺口與 1 項涉及 API 簽名/`SecurityConfig` 變更的待決策事項。

---

## 2. 交付內容

### 新增測試（US-001~003 合併於單一檔案）

- **`CmsServiceTest.java`**（新檔，**27 個測試**）：涵蓋全部 11 個方法：
  - `createPage`（2）：建立成功（tenantId/authorId 取自 context）、slug 重複 `E_9005`。
  - `updatePage`（4）：本租戶成功、不存在 `E_8004`、跨租戶必須拒絕（`DEF-032` 紅燈驗證）、admin 跨租戶放行。
  - `publishPage`（3）：本租戶成功、跨租戶必須拒絕（`DEF-032` 紅燈驗證）、admin 跨租戶放行。
  - `getPageBySlug`（3）：已發布回傳成功、不存在 `E_8004`、未發布 `E_8004`（現況行為，`DEF-034` 待決策不變更）。
  - `getPages`（2）：非 admin 僅見本租戶（`DEF-033` 紅燈驗證）、admin 見跨租戶總覽。
  - `createBanner`（1）：建立成功。
  - `updateBanner`（4）：本租戶成功、不存在 `E_8005`、跨租戶必須拒絕（`DEF-032` 紅燈驗證）、admin 跨租戶放行。
  - `publishBanner`（3）：本租戶成功、跨租戶必須拒絕（`DEF-032` 紅燈驗證）、admin 跨租戶放行。
  - `getBanners`（2）：非 admin 僅見本租戶（`DEF-033` 紅燈驗證）、admin 見跨租戶總覽。
  - `getActiveBanners`（2）：指定 position、未指定查全部（現況行為，`DEF-034` 待決策不變更）。
  - `recordBannerClick`（1）：正確呼叫 repository 遞增。

### 生產程式碼修復（US-002：`DEF-032`）

- **問題**：`updatePage`/`publishPage`/`updateBanner`/`publishBanner`（`core/cms/CmsService.java`）皆呼叫 `findById` 後直接修改/發布，完全沒有擁有權/租戶檢查。Controller 端僅要求 `cms:update`/`cms:publish` 權限，此權限可能分散於各租戶的管理者角色，任一租戶管理者可竄改/發布其他租戶的頁面或橫幅，屬跨租戶寫入 IDOR，與 `DEF-019`（`LogisticsService.createLogistics`）/`DEF-024`/`DEF-028` 同一 tenant-based 模式。
- **紅燈證明**：新增測試 `updatePage_crossTenant_mustBeRejected`/`publishPage_crossTenant_mustBeRejected`/`updateBanner_crossTenant_mustBeRejected`/`publishBanner_crossTenant_mustBeRejected`，斷言「他租戶資源不得放行」。**修復前執行確認失敗**（4 個 Failure，皆因程式碼直接往下執行到 `toPageResponse`/`toBannerResponse` 對未 stub 的 `save()` 回傳值解參考而 NullPointerException），實測證實修復前完全沒有攔截跨租戶寫入。
- **修復**：新增 `checkCmsTenantOwnership(UUID resourceTenantId)` + `isCurrentUserAdmin()` 兩個 helper（比照 `ReviewService.checkReviewManagementAuthorization`/`isCurrentUserAdmin` 既有前例），採「本租戶 or admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）」放行，越權拋 `E_1007`。
- **轉綠**：修復後重跑，`CmsServiceTest` 全數通過（含正向對照：本租戶管理者放行、admin 跨租戶放行，避免修復矯枉過正）。

### 生產程式碼修復（US-003：`DEF-033`）

- **問題**：`getPages`/`getBanners`（Admin 列表）呼叫 `findByStatusOrderBySortOrderAsc` 完全無租戶過濾，即使 `ContentPageRepository`/`BannerRepository` 皆已有 `findByTenantIdAndStatus(OrderBySortOrderAsc)` 方法卻從未被使用（與 Sprint 65 `getRevenueStats` granularity 死碼、Sprint 66 `ProductService` 關鍵字搜尋死碼同一類「查詢方法寫好卻沒接上」模式）。任一持有 `cms:read` 權限的租戶管理者皆可取得系統中所有租戶的頁面/橫幅列表。
- **紅燈證明**：新增測試 `getPages_nonAdmin_onlyOwnTenant`/`getBanners_nonAdmin_onlyOwnTenant`，斷言結果僅含本租戶資料。**修復前執行確認失敗**（2 個 Error，因程式碼固定呼叫未 stub 到的 `findByStatusOrderBySortOrderAsc` 導致 NullPointerException），實測證實修復前程式碼完全未依租戶區分呼叫路徑。
- **修復**：`getPages`/`getBanners` 改為 `isCurrentUserAdmin()` 沿用舊查詢（跨租戶總覽），非 admin 一律改用既有的 `findByTenantIdAndStatusOrderBySortOrderAsc`/`findByTenantIdAndStatus`（無需新增 Repository 方法，比照 `DEF-026`/`DEF-029` 保留舊方法 + 分支使用模式）。
- **轉綠**：修復後重跑全數通過（含 admin 可見所有租戶的對照測試）。

### 待決策事項記錄（US-004：`DEF-034`）

- 探查確認 `getPageBySlug`/`getActiveBanners`（公開瀏覽端點）完全不做租戶過濾；`TenantContextFilter` 對匿名請求恆設為 `SYSTEM_TENANT_ID`，無法沿用 `TenantContext` 解析訪客瀏覽的租戶。經比對 `PostController`/`PostService.getPublishedPostBySlug`（同屬 CMS 領域，`/v2/posts` 已 `permitAll()`）發現既有慣例是「呼叫端明確傳入 `tenantId` query 參數」；另發現 `/v2/cms/**` 實際未列入 `SecurityConfig` 的 `permitAll()` 清單，與程式碼註解「(公開)」不符。此為公開端點租戶範圍設計缺口（非跨租戶 IDOR，無寫入/竄改風險），修復涉及 API 簽名變更與 `SecurityConfig` 調整，需業務/架構判斷。**使用者已確認**本 Sprint 擱置僅記錄，不排入排程。

### 文件

- **`SPRINT_74_PLAN.md`**（新檔）：本 Sprint 計劃，含前置範圍探查、既有測試覆蓋現況、US-001~004 完整 AC。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-032`/`DEF-033`（皆已完成，直接記入「已完成延後項目」）、`DEF-034`（🟡 中優先級待決策）。
- **`RELEASE_NOTES_v2028.08.26-01.md`**（新檔）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ 每完成一批測試立即編譯 + 執行驗證，US-002/003 額外執行「紅燈確認 → 修復 → 轉綠確認」雙重驗證節奏（以 `git stash` 暫時還原生產程式碼到修復前狀態執行紅燈、`git stash pop` 還原修復後再確認轉綠） |
| `CmsServiceTest` 修復前（US-002/003 紅燈階段） | 🔴 27 tests，**4 Failures + 2 Errors**（`updatePage_crossTenant_mustBeRejected`/`publishPage_crossTenant_mustBeRejected`/`updateBanner_crossTenant_mustBeRejected`/`publishBanner_crossTenant_mustBeRejected` 為 AssertionError；`getPages_nonAdmin_onlyOwnTenant`/`getBanners_nonAdmin_onlyOwnTenant` 為 NullPointerException，皆正確反映「修復前完全未攔截跨租戶操作」的漏洞本質），其餘 21 tests 正常通過 |
| `CmsServiceTest` 修復後 | ✅ 27 tests，0 fail |
| checkstyle-test | ⚠️ 首次全量回歸攔截 1 個未使用 import（`org.springframework.data.domain.Page`），移除後重新驗證通過（比照 Sprint 66 `AuthServiceTest` 同類問題） |
| 後端單元回歸（`mvn test` 等效統計） | ✅ **743 tests，0 fail**（較 Sprint 73 的 716 增加本 Sprint 新增的 27 個） |
| 全量回歸（`mvn verify -Pintegration-test`） | ✅ **BUILD SUCCESS**：單元 743 + 整合（failsafe）342 = **1085 tests，0 fail** |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更），EXIT_CODE=0 |
| 驗證方式選擇 | 依全量回歸頻率政策：本 Sprint 修改生產程式碼（`CmsService.java`），故執行全量 `mvn verify -Pintegration-test`（執行 2 次：第一次因 checkstyle 未使用 import 失敗，修正後第二次 BUILD SUCCESS），非僅 `mvn test` |

---

## 4. 誠實揭露（Rule 12）

1. **首次全量回歸被 checkstyle-test 攔截**：`CmsServiceTest.java` 初版含未使用的 `import org.springframework.data.domain.Page;`（僅使用 `PageImpl`/`PageRequest`，未直接使用 `Page` 型別），與 Sprint 66 `AuthServiceTest` 發生過的同類問題重演。移除後重新編譯、重新執行單元測試與全量回歸，確認乾淨通過，未掩蓋此次疏漏。
2. **`CmsService` 是本多 Sprint 測試強化計劃中首個「整個 Service 全數方法零覆蓋」的案例**：不同於先前 Sprint（`ReviewService` 14 方法中 7 個已有既有整合測試涵蓋），`CmsService` 的 `createPage`/`getPageBySlug`/`getActiveBanners`/`recordBannerClick` 等看似低風險的方法也完全沒有任何既有測試（單元/整合/E2E）涵蓋，本 Sprint 依範圍全數補齊而非僅補「有問題」的方法。
3. **`DEF-034` 探查過程中額外發現 `SecurityConfig` 不一致**：`CmsController` 的 `/v2/cms/**` 端點註解「(公開)」但實際未列入 `permitAll()` 清單，落入 `.anyRequest().authenticated()`；此為探查過程中的額外發現，非本 Sprint 原定範圍，已一併記錄於 `DEF-034` 供後續決策時一併考量，本 Sprint 未變更 `SecurityConfig`。
4. **`isCurrentUserAdmin()` 判斷邏輯延續既有慣例**：與 `DEF-018/019/023/024/028/029/030` 相同，`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 皆視為可跨租戶放行；`ADMIN` 角色權限邊界（租戶內 vs 全域）盤點仍是 Sprint 73 Retro 記錄但尚未排入的待決策事項，本 Sprint 未一併處理。

---

## 5. Demo 重點

- **主動審視擁有權/租戶檢查延續有效**：延續 Sprint 73 建立的「探查階段主動審視」紀律，本 Sprint 對 11 個方法逐一分析「誰可以呼叫、有沒有檢查資源歸屬」，一次揭露 2 項安全缺口 + 1 項待決策事項，且在動手寫測試前就先完成分析。
- **兩種不同語意的跨租戶檢查在同一 Service 內並存**：`updatePage`/`publishPage`/`updateBanner`/`publishBanner` 是「本租戶或 admin」（tenant-based，無本人語意，比照 `DEF-019`/`DEF-028`）；`getPages`/`getBanners` 是「非 admin 才過濾」（分支邏輯，比照 `DEF-026`/`DEF-029`）。
- **紅燈測試的失敗型態確實反映漏洞本質**：4 個寫入方法的紅燈是 AssertionError（`expecting throwable but none thrown` 演變為 NPE，因程式碼會繼續往下跑到序列化階段），2 個列表方法的紅燈是 NullPointerException（因程式碼固定呼叫另一個未被測試 stub 的方法），兩種失敗型態皆正確對應「修復前完全沒有攔截」的事實，而非測試撰寫錯誤（本 Sprint 未重演 Sprint 73 的 fixture 缺陷問題）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
