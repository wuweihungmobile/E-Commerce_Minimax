# Sprint 86 Retrospective / Sprint 86 回顧會議

> **Sprint 編號**: Sprint 86
> **期間**: 2026-07-09
> **回顧日期**: 2026-07-09
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（M07 跨週期退款調整單機制，PRD §6.2.1） |
| 完成 SP | 13 SP（含探查中發現並經使用者同意併入的兩個既有缺陷修復） |
| 主軸 | 補齊連續三個 Sprint（83/84/85）retro 列為候選但未排入的 PRD 規格缺口：`adjustment_statement` + `CREDIT_NOTE` 逆轉雙重授權 |

---

## 2. 做得好的（What went well）

- **範圍大、決策點多的需求，動手前用 AskUserQuestion 分兩輪釐清，而非自行假設**：這次任務有兩層需要業務決策的模糊點——(1) 該做到「僅 adjustment_statement 本體」還是「完整三層含 CREDIT_NOTE + 雙重授權」；(2)「SuperAdmin + 財務長雙重授權」在系統完全沒有「財務長」角色概念的情況下該如何落地。兩者都先探查程式碼現況、列出具體選項與各自的取捨（含「系統目前沒有這個角色」這種會實質影響工作量的事實），再用 AskUserQuestion 讓使用者拍板，而非依賴自己對「雙重授權」的字面理解直接動手設計。
- **動手前的探查揭穿了 PRD 自身「退款會被扣除」的機制根本沒有真正運作**：沒有直接相信 PRD 公式「結算金額 = GMV - 抽成 - 退款」已經實作，而是實際讀 `SettlementCalculator.calculateTotalRefunds` 原始碼，發現它對「已經濾除 REFUNDED 訂單」的清單再篩選 REFUNDED，邏輯上永遠回傳 0——這個死碼註解本身就承認了這件事（「保留此方法以供未來流程調整」）。進一步推理出這只在「全額退款」情境下無害（訂單本已被 GMV 排除），但「部分退款」情境（`Order.status` 不變）完全沒被扣除，是真正的既有缺口，修正時精準只補這個缺口而不重新設計已經正確的排除機制。
- **發現一個與當前任務無關、但影響現有生產程式碼可用性的權限缺陷，主動停下報告而非默默修或默默忽略**：撰寫規劃前重新確認既有 `SettlementController`/`SettlementReviewer` 程式碼時，發現 `@PreAuthorize("hasAuthority('admin:read'/'admin:write')")` 檢查的權限字串在 `RolePermissionMapping`/`Permission` enum 中從未被定義、從未被授予任何角色（含 SUPER_ADMIN），代表 Sprint 80/81 建立的整條結算審核流程在真實環境對任何人都是 403 不可達；既有測試之所以全數通過，是因為測試手動塞入字面值字串繞過了真實授權邏輯。沒有在同一個回合默默修掉（可能超出使用者原本授權範圍），也沒有忽略不提（會讓下一次要動這個檔案的人重複踩坑），而是完整說明影響範圍後用 AskUserQuestion 讓使用者決定是否併入本 Sprint。
- **新增的 `CreditNote` entity/repository 動手前先搜尋是否已存在，避免重複造輪子並發現既有的 Sprint-84-同類型 shadow-column 陷阱**：探查時發現 `CreditNote.java`/`CreditNoteRepository.java` 早已存在（對應 V36 migration 建表)但完全沒有任何 service/controller 引用（孤兒程式碼），直接沿用而非重寫。沿用時也注意到其 `tenantId`/`originalStatementId` 都只是唯讀 shadow 欄位（真正的可寫關聯是 `tenant`/`originalStatement` 物件），與 Sprint 84 修復的 `Listing.tenantId` 是同一種陷阱模式——這次在寫 `SettlementReversalService` 時第一次就直接用 `.tenant(...)`/`.originalStatement(...)` builder 呼叫，未重蹈覆轍。

## 3. 待改善的（What to improve）

- **既有 `AdminService.approveTenant`/`rejectTenant` 的 `approvedBy`/`rejectedBy` 欄位長期硬編碼字串 `"SYSTEM_ADMIN"`，本次新增的結算逆轉改用真實 `TenantContext.getCurrentUser()`，兩種精確度不一致的慣例目前同時存在於 `AdminController`/`SettlementController`**：這是 Sprint 85 retro 就記錄過的既有技術債，本次未回頭修正（範圍外），待下次觸碰該程式碼時處理。
- **`PRODUCT_BACKLOG.md` 的 M07 章節內容明顯落後於 Sprint 49-86 期間的真實金流/結算/分潤/逆轉開發進度（仍描述「M07 後端目前是 Mock」）**：這次僅更新了 `DEFERRED_ITEMS_TRACKER.md`（本 Sprint 完成項目的權威記錄），未同步修正 `PRODUCT_BACKLOG.md` 的過時描述，因為該文件涉及的落差範圍已遠超本 Sprint 主題，屬於獨立的文件維護債務。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （技術債，延續自 Sprint 71-86）| `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| （技術債，延續自 Sprint 85）| `AdminService.approveTenant`/`rejectTenant` 的 `approvedBy`/`rejectedBy` 硬編碼字串改為真實 `TenantContext.getCurrentUser()` | 與本次新增的結算逆轉精確度不一致 | Dev David | 🟢 低 | 待排入 |
| （文件維護，新增）| `PRODUCT_BACKLOG.md` M07 章節內容更新（不再描述為 Mock，補上 Sprint 49-86 真實金流/結算/分潤/逆轉進度） | 內容明顯落後於實際開發進度 | PM Victoria | 🟢 低 | 待排入 |
| （前端候選，延續自 Sprint 85）| M16 採購審批門檻設定表單 + SuperAdmin 審批清單頁面；M07 結算逆轉發起/確認表單 | 後端 API 已完成，前端尚未實作 | Dev David | 🟡 中 | 待排入 |

---

## 5. Sprint 85 → Sprint 86 Action Items 追蹤結果

| Action Item | 內容 | Sprint 86 達成狀態 |
|------------|------|---------------------|
| PRD 規格要求：M07 結算系統跨週期退款調整單（`adjustment_statement`） | Sprint 85 retro「下一步」建議項目 1（連續三個 Sprint 列為候選） | ✅ **已完成**：`adjustment_statement` + `CREDIT_NOTE` + CFO 雙重授權逆轉全數落地，並額外修復兩個探查中發現的既有缺陷（退款扣除死碼、admin 權限缺口） |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S82 | 3 |
| S83 | 3 |
| S84 | 5 |
| S85 | 5 |
| **S86** | **13**（M07 跨週期退款調整單 + CREDIT_NOTE 雙重授權 + 兩項既有缺陷修復，原估 8 SP） |

---

## 7. 下一步

> **檢查點**：Sprint 86 已完成，PRD §6.2.1 M07 跨週期退款處理機制全數落地（含 CFO 雙重授權逆轉），`admin:read`/`admin:write` 權限缺口已修復，目前無高優先級（🔴）延後項目。**下一輪規劃建議**：
> 1. PRD Phase 2-B 項目：收貨地址簿、M18 客服工單子系統，視容量排入。
> 2. 前端候選：M16 採購審批門檻設定表單/審批清單頁面、M07 結算逆轉發起/確認表單。
> 3. 技術債：`RELEASE_TRACKER.md` 補齊 Sprint 74+ 列；`PRODUCT_BACKLOG.md` M07 章節更新；`AdminService` 審核人欄位精確度統一。

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
