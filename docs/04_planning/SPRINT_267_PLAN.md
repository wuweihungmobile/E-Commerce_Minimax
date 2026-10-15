# Sprint 267 Plan — FRD 補齊兩條既有功能的追溯 User Story（DEF-370／371）

**Sprint**: Sprint 267
**日期**: 2026-10-15

## 1. 範圍與起點

[SPRINT_266_PLAN.md](SPRINT_266_PLAN.md) 完成 DEF-368／369／377 後，剩餘 4 項缺口待排程。以 `AskUserQuestion` 徵詢後，使用者選擇「補齊 FRD 缺的兩條 User Story：DEF-370+371」——店鋪成員管理（Sprint 98 起分批實作）與個人資料編輯（Sprint 257）兩項功能皆已上線多時，FRD 卻從未收錄對應 User Story。

## 2. 方法

兩項功能的技術細節（端點、請求/回應、權限、錯誤碼）已在近期 Sprint 完整驗證過，不需重新讀碼：
- 店鋪成員管理：直接沿用 Sprint 266（[API_M17_Tenant.md](../02_architecture/api/API_M17_Tenant.md) §4.7~4.14）剛完成的完整規格，8 個端點皆已逐行讀碼核對過。
- 個人資料編輯：沿用 Sprint 257（[API_M03_Auth.md](../02_architecture/api/API_M03_Auth.md) §14）既有規格。

純粹把既有、已驗證的技術規格轉譯成 FRD 的 User Story／Acceptance Criteria 格式，不涉及新讀碼或程式碼變更。

## 3. 實作內容（純文件）

| 檔案 | 變更 |
|---|---|
| `docs/01_requirements/E-Commerce_FRD_v1.0.md` | 新增 §5 **US-M03-008 個人資料編輯**（Story 描述、4 條 AC、測試資料、人機協作確認點）；新增 §8 **US-M17-010 店鋪成員管理**（Story 描述、6 條 AC 涵蓋 8 個端點操作、測試資料、人機協作確認點）；§5.7／§8.6 API 規格概要表同步補上對應端點；修正 §5.7 既有失效連結（`API_M03_Auth.md` 連結漏了 `api/` 子目錄，自建立以來從未指向過存在的檔案）；修正 Sprint 261 修訂註記裡的既有 DEF 編號誤記（原寫「DEF-371／DEF-372」，應為「DEF-370／DEF-371」）；版本 v1.10→v1.11，新增修訂歷史列與頂部修訂註記 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-370／371 標記已完成 |

## 4. 撰寫過程中新發現的落差（已於本輪一併修正，非新登記缺陷）

- **§5.7 既有失效連結**：`[docs/02_architecture/API_M03_Auth.md](../02_architecture/API_M03_Auth.md)` 漏了 `api/` 子目錄，實際檔案在 `docs/02_architecture/api/API_M03_Auth.md`，此連結自文件建立以來從未真正可用。已修正並加註說明。
- **Sprint 261 修訂註記的 DEF 編號誤記**：檔頭「⚠️ 修訂註記（Sprint 261）」寫「登記為 DEF-371／DEF-372」，但實際追蹤表裡 DEF-370 才是店鋪成員管理、DEF-371 是個人資料編輯；且 DEF-372 是完全不相關的另一個項目（`ApiRouteDocDriftTest` 覆蓋範圍擴充）。已更正為「DEF-370／DEF-371」並加註說明原始錯誤。
- **本輪自身的追蹤表版本歷史操作失誤**：撰寫 `DEFERRED_ITEMS_TRACKER.md` 新版本時，第一次編輯誤將 Sprint 266 的「最後更新」內容直接覆寫為 Sprint 267 內容，未先降級為「歷史版本 v3.49」，導致版本歷史鏈短暫出現 v3.50→v3.48 跳號缺口。收尾複核時立即發現並補回遺失的 v3.49 條目，詳見 commit 歷史。

## 5. 測試

本輪為純 Markdown 文件變更（FRD／追蹤表），未修改任何 Java 原始碼或既有測試，故不需要執行 `mvn clean verify` 全量回歸（與 Sprint 264～266 的性質不同，那些輪次都修改了 `ApiRouteDocDriftTest.java` 或生產程式碼）。已確認本輪變更範圍不含 `backend/` 目錄下任何檔案。

## 6. 範圍外（延後）與待使用者決定

- M08 編號衝突（DEF-375）、測試排序脆弱性（DEF-376）：維持登記狀態，排程由使用者後續決定。

## 7. Velocity 紀錄

本輪為 FRD 補齊 2 條既有功能的追溯 User Story，無新功能開發、無程式碼變更，不納入傳統 Story Point 計算。

## 8. 下一步／Action Items

1. commit＋push，等待雲端 CI 確認全綠（純文件變更，預期本機輕量守門直接放行）。
2. 待使用者決定是否繼續處理剩餘 2 項缺口（M08 編號衝突 DEF-375、測試排序脆弱性 DEF-376），或轉向其他 Sprint。
