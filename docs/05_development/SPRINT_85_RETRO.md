# Sprint 85 Retrospective / Sprint 85 回顧會議

> **Sprint 編號**: Sprint 85
> **期間**: 2026-07-09
> **回顧日期**: 2026-07-09
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（PRD §6.7.2 M16 採購審批金額上限機制） |
| 完成 SP | 5 SP |
| 主軸 | 補齊 Sprint 10 M16 ERP 正式交付時遺漏、且截至 Sprint 84 從未被任何追蹤文件記錄的 P0 需求缺口（AI-2419） |

---

## 2. 做得好的（What went well）

- **探查先行，先確認需求描述本身的完整性再動手**：PRD §6.7.2 對此功能僅有一句話描述，動手前先確認 §8.2.8 Schema 與 §9.15 API 規格是否有同步展開——結果發現兩者都沒有，需求描述與規格本身存在落差。沒有直接依賴這句話腦補設計，而是先探查現有 `PurchaseOrder` entity，發現 `POStatus` enum 已定義 `APPROVED` 值但完全未接線（懸空多年），這個發現直接決定了狀態機設計要「啟用既有欄位」而非「發明新欄位」。
- **明確識別並記錄「刻意不比照」的既有慣例，而非盲目套用最近的前例**：Sprint 80/81 `SettlementReviewer` 建立了「非 SUPER_ADMIN 限自己租戶、SUPER_ADMIN 跨租戶」的雙層審批慣例，且是最近、測試最完整的前例。但 PRD 原文明確寫「需 **SuperAdmin** 核准」，性質是平台方對租戶自身金流之上的獨立把關——若比照雙層模式，等於讓租戶自己的 ADMIN 也能核准，違背此機制存在的目的。因此改為完全比照 `AdminController`/`AdminService` 既有的「僅限 SUPER_ADMIN」端點模式（`approveTenant`/`rejectTenant` 先例），並在 Sprint Plan 中明確記錄「為何不用最近的前例」，避免日後被誤判為遺漏套用新慣例。
- **新增跨租戶查詢方法時，主動在程式碼與文件中自我註記 IDOR 檢查的推理過程**：這個 codebase 反覆出現「新增功能忘記加租戶擁有權檢查」的 IDOR 模式（近 13 個 Sprint 的 DEF 修復皆屬此類）。`AdminService.getPendingApprovalPurchaseOrders`/`approvePurchaseOrder`/`rejectPurchaseOrder` 三個新方法刻意不做任何 `tenantId` 篩選，這次沒有等到日後被抓出來當作新漏洞，而是在方法 Javadoc 與 Sprint Plan 都明確記錄「為何不篩選是正確的」（呼叫路徑已被 Controller 層 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 鎖死，跨租戶查看/核准正是本功能存在的目的），把「經過檢查、確認不需要」的推理過程留下痕跡，而非留下一段沒有註解、容易被誤判的程式碼。
- **money-related 欄位 null 語意的向下相容設計**：`Tenant.purchaseOrderApprovalThreshold` 採 nullable、無預設值（null = 不啟用門檻），確保既有租戶的既有行為完全不受影響，migration 也不需要為既有資料回填假設值。

## 3. 待改善的（What to improve）

- **`AdminDto`/`AdminService` 的既有租戶審核回應（`approvedBy`/`rejectedBy`）長期硬編碼字串 `"SYSTEM_ADMIN"`，未真正記錄操作者身分**：本次新增的採購單審批改用真實的 `TenantContext.getCurrentUser()` 寫入 `reviewedBy`（UUID），比舊有的 `approveTenant`/`rejectTenant` 更精確，但這代表同一個 `AdminController` 內存在兩種不同精確度的稽核慣例並存。這次依範圍未回頭修正舊有的 `TenantResponse`/`TenantRejectResponse` 硬編碼字串，留給下次觸碰該程式碼時處理。
- **前端 UI 完全未納入本 Sprint**：StoreOwner 設定門檻的表單、SuperAdmin 審批待審清單頁面皆未實作，僅完成後端 API。這是規劃時就明確排除的範圍外項目（記錄於 Sprint Plan），但代表這個 P0 功能目前只能透過 API 直接呼叫使用，尚無法讓真實使用者操作。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （技術債，延續自 Sprint 71-85）| `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| （技術債，新增）| `AdminService.approveTenant`/`rejectTenant` 的 `approvedBy`/`rejectedBy` 硬編碼字串改為真實 `TenantContext.getCurrentUser()` | 本次新採購單審批已採用真實使用者 ID，與既有租戶審核方法的精確度不一致 | Dev David | 🟢 低 | 待排入 |
| （前端候選，新增）| M16 採購審批門檻設定表單 + SuperAdmin 審批待審清單頁面 | 後端 API 已完成（`PUT /v2/tenants/{id}`、`/v2/admin/purchase-orders/**`），前端尚未實作 | Dev David | 🟡 中 | 待排入 |

---

## 5. Sprint 84 → Sprint 85 Action Items 追蹤結果

| Action Item | 內容 | Sprint 85 達成狀態 |
|------------|------|---------------------|
| PRD P0：M16 ERP 採購審批金額上限機制 | Sprint 84 retro「下一步」建議項目 1 | ✅ **已完成**：後端 API 全數落地（門檻設定 + 審批/駁回/待審清單），前端列入候選延後 |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S81 | 6 |
| S82 | 3 |
| S83 | 3 |
| S84 | 5 |
| **S85** | **5**（M16 採購審批金額上限機制，後端 API 全量） |

---

## 7. 下一步

> **檢查點**：Sprint 85 已完成，PRD §6.7.2 M16 採購審批金額上限機制後端 API 全數落地，目前無高優先級（🔴）延後項目。**下一輪規劃建議（依 Sprint 84 retro 優先順序，依使用者指示「兩者依序進行」，接續處理第 2 項）**：
> 1. PRD 規格要求：M07 結算系統跨週期退款調整單（`adjustment_statement`）。
> 2. PRD Phase 2-B 項目：收貨地址簿、M18 客服工單子系統，視容量排入。
> 3. 技術債：`RELEASE_TRACKER.md` 補齊 Sprint 74+ 列；M16 採購審批前端 UI。

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
