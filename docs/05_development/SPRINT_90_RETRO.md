# Sprint 90 Retrospective / Sprint 90 回顧會議

> **Sprint 編號**: Sprint 90
> **期間**: 2026-07-09
> **回顧日期**: 2026-07-09
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 6 SP（M07 結算逆轉發起/確認表單） |
| 完成 SP | 6 SP |
| 主軸 | Sprint 88 retro 排定「三者最佳化順序」第二項的後半：M07 跨週期退款調整單機制的結算逆轉發起/確認表單 |

---

## 2. 做得好的（What went well）

- **動手前探查發現「後端 API 已完成」的認知落差，先補齊發現缺口而非硬套現有頁面設計**：Sprint 88 retro 記錄「M07 後端 API 已完成」，但深入探查發現 `initiate`/`confirm` 端點雖已就位，卻沒有任何列表端點能讓 SUPER_ADMIN/CFO 查到「哪些結算單是 PAID/REVERSAL_PENDING 狀態」——若不補上，表單形同無法使用。判斷此為功能完整性所需（不做就沒有可操作的資料來源），直接新增一個唯讀列表端點，而非動手做一個註定打不通 API 的假頁面。
- **正確識別逆轉機制與既有審核機制的授權模型差異，未盲目複製既有分支邏輯**：`getPendingReviewStatements`（結算審核用）依 `isSuperAdmin` 分「跨租戶／限自己租戶」兩種行為，但探查 `initiateReversal`/`confirmReversal` 原始碼後確認：這兩個方法對 SUPER_ADMIN 與 CFO 一律不做租戶篩選（`findById` 無租戶條件），因為 `@PreAuthorize` 本身已把入口鎖死給這兩種角色。因此新列表端點刻意不复用「限自己租戶」回退分支，改為兩種角色一視同仁跨租戶查詢，正確對齊既有動作端點的實際授權模型，而非表面複製相似程式碼。
- **明確劃定範圍界線並公開記錄，而非默默擴大或默默略過**：探查中發現商家自助 `/dashboard/settlements`（結算單列表/提交審核 UI）也完全零前端資產，屬 Sprint 80/81 遺留、從未被排入任何 Sprint 的獨立缺口。判斷 Sprint 88 retro 明確排定的候選項目文字聚焦「結算逆轉」本身，不擴大範圍動手做商家頁面，同時在 Plan 文件「範圍外」明確記錄此缺口，避免日後被誤認為「已處理」。

## 3. 待改善的（What to improve）

- **`GET /v2/admin/settlements/reversal-candidates` 目前無獨立 E2E/Controller 層測試**：僅在 `SettlementReversalServiceTest` 新增 Service 層測試（mock repository），未新增 Controller 層測試驗證 `@PreAuthorize("hasAuthority('settlement:reverse')")` 確實生效（非 SUPER_ADMIN/CFO 呼叫應 403）。比照 Sprint 85 M16 Plan 曾提及「此點需明確驗證」的原則，本次因時間考量未補上，記錄為技術債。
- **前端「確認逆轉」按鈕的角色互斥檢查依賴 `localStorage` 內快取的使用者角色**，若使用者在多分頁/多裝置間切換角色身份，可能出現前端誤判可操作但後端仍會 403 拒絕的情況——僅體驗問題，非安全問題（後端 `E_1007` 才是真正邊界），但值得未來優化為即時查詢。

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （技術債） | `reversal-candidates` 端點補 Controller/E2E 層 403 驗證測試 | 目前僅有 Service 層 mock 測試 | QA Quincy | 🟡 中 | 待排入 |
| （PRD Phase 2-B，延續） | M18 客服工單子系統 | 連續多 Sprint 列為候選 | PM Victoria | 🟡 中 | Sprint 91 |
| （獨立缺口，本 Sprint 探查發現） | 商家自助 `/dashboard/settlements`（結算單列表/提交審核 UI）零前端資產 | Sprint 80/81 遺留，未曾排入排程 | PM Victoria | 🟢 低 | 待排入 |
| DEF-043 | ROOM 訂房結帳流程建立預訂成功後清空整個購物車 | 延續自 Sprint 88 | Dev David | 🟡 中 | 待排入 |
| DEF-044 | 已付款訂單退款/取消時無庫存自動回補 | 延續自 Sprint 88 | PM Victoria | 🟡 中 | 待排入 |
| （技術債，延續自 Sprint 89） | `purchaseOrderApprovalThreshold` 無清除機制 | 待業務決策是否需要明確清除 API | PM Victoria | 🟢 低 | 待排入 |
| （技術債，延續自 Sprint 71-87） | `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |

---

## 5. Sprint 89 → Sprint 90 Action Items 追蹤結果

| Action Item | 內容 | Sprint 90 達成狀態 |
|------------|------|---------------------|
| M07 結算逆轉發起/確認表單 | Sprint 88 retro 排定「三者最佳化順序」第二項後半 | ✅ **已完成**（含補上探查發現的後端列表端點缺口） |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S87 | 5 |
| S88 | 8 |
| S89 | 5 |
| **S90** | **6**（M07 逆轉候選列表端點 + 發起/確認頁面） |

---

## 7. 下一步

> **檢查點**：Sprint 90 已完成，M07 結算逆轉機制前後端全部串接完畢。「三者最佳化順序」（M18/PRODUCT結帳/M16-M07表單）三項全部完成。下一輪依序進行：
> 1. M18 客服工單子系統（PRD Phase 2-B，多次列為候選，Sprint 91 起）。
> 2. 技術債：`RELEASE_TRACKER.md` 補齊 Sprint 74+ 列；DEF-043/DEF-044 排程評估；`reversal-candidates` Controller 層測試補強。
> 3. 全部完成後，回歸 `E-Commerce_PRD_v1.0_Final.md` 找下一個未開發的 PRD 項目繼續。

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
