# Sprint 89 Retrospective / Sprint 89 回顧會議

> **Sprint 編號**: Sprint 89
> **期間**: 2026-07-09
> **回顧日期**: 2026-07-09
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（M16 採購審批門檻前端串接） |
| 完成 SP | 5 SP |
| 主軸 | Sprint 88 retro 排定「三者最佳化順序」第二項的前半：M16 採購審批金額上限機制前端補齊（後端已於 Sprint 85 完成）。M07 結算逆轉表單改列獨立 Sprint 90，避免單 Sprint 範圍過大 |

---

## 2. 做得好的（What went well）

- **動手前探查發現後端回應缺口，主動補齊而非讓前端帶著已知限制上線**：探查確認 `PUT /v2/tenants/{id}` 早已支援寫入 `purchaseOrderApprovalThreshold`，但 `GET /v2/tenants/{id}`（`TenantDetailsResponse`）從未回傳此欄位——若不補上，StoreOwner 進編輯表單時完全看不到目前已設定的門檻值，只能盲寫覆蓋。判斷此為功能完整性所需、非新設計決策（只是少組一個既有欄位），直接補上並新增對應單元測試，而非把已知限制留給未來 Sprint。
- **發現並修正前端型別與後端 enum 長期不同步的缺陷**：`POStatus`（前端）沿用 `PARTIAL_RECEIVED` 拼字，但後端實際 enum 是 `PARTIALLY_RECEIVED`，導致該狀態徽章在 3 個檔案中都會 fallback 顯示原始英文字串而非中文標籤。因為這正是本次要新增審批狀態徽章的同一段程式碼，判斷屬本次改動範圍內的直接缺陷，一併修正。
- **識別無對應後端「單筆詳情」端點，據此收斂 UI 設計而非另建假頁面**：`AdminController` 的採購審批僅有列表（含核准/駁回所需全部欄位）與核准/駁回兩個動作端點，無單筆查詢端點。因此 SuperAdmin 審批頁採「列表卡片內建核准/駁回」單頁設計，而非仿 `admin/tenants` 拆「列表頁＋詳情頁」兩頁（後者會呼叫不存在的 API）。
- **探查 `/admin/*` 既有頁面慣例後，撤回原先計畫中的 client-side 角色守衛**：原計畫沿用 `/dashboard/*` 頁面常見的 `AuthService` 角色檢查 + 導頁模式，但實際檢視 `admin/tenants`、`admin/tenants/[id]/review`、`admin/audit-logs` 三個既有頁面後發現，`/admin/*` 目錄下的既有慣例是完全不做 client-side 檢查、純粹依賴後端 `@PreAuthorize` 403（`admin/tenants/[id]/review/page.tsx` 甚至留有一個從未呼叫的 `AuthService` unused import，佐證此慣例）。改依此慣例移除自建的角色守衛，保持與同目錄其他檔案一致。

## 3. 待改善的（What to improve）

- **`purchaseOrderApprovalThreshold` 目前無法透過 UI 清除（unset）**：後端語意是「`null` = 不變更此設定」，沒有「明確清除」的 sentinel 值，因此表單留空時前端選擇不送出該欄位（維持現狀），但這代表 StoreOwner 一旦設定門檻後，找不到任何方式透過現有 API 移除門檻限制（只能改設一個很大的數字變相停用）。這是後端 Sprint 85 的既有設計限制，非本 Sprint 引入，但值得記錄，待有實際需求再評估是否需要新增明確的清除機制（例如改用 `Optional`/獨立 DELETE 端點的設計決策，需要業務判斷）。
- **無法在 SuperAdmin 審批頁看到採購單品項明細**：因後端 `PurchaseOrderSummaryResponse` 不含品項，審批僅能依單號/金額/租戶 ID 判斷。目前 PRD 描述的審批情境本身聚焦金額門檻本身，暫判斷可接受；若未來需要品項層級審核，需另外評估是否新增後端詳情端點。

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （前端候選，延續自 Sprint 88） | M07 結算逆轉發起/確認表單 | 後端 API 已完成（Sprint 86），前端尚未實作 | Dev David | 🟡 中 | Sprint 90 |
| （PRD Phase 2-B，延續） | M18 客服工單子系統 | 連續多 Sprint 列為候選 | PM Victoria | 🟡 中 | 待排入 |
| DEF-043 | ROOM 訂房結帳流程建立預訂成功後清空整個購物車 | 延續自 Sprint 88 | Dev David | 🟡 中 | 待排入 |
| DEF-044 | 已付款訂單退款/取消時無庫存自動回補 | 延續自 Sprint 88 | PM Victoria | 🟡 中 | 待排入 |
| （技術債，延續） | `purchaseOrderApprovalThreshold` 無清除機制 | 待業務決策是否需要明確清除 API | PM Victoria | 🟢 低 | 待排入 |
| （技術債，延續自 Sprint 71-87） | `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |

---

## 5. Sprint 88 → Sprint 89 Action Items 追蹤結果

| Action Item | 內容 | Sprint 89 達成狀態 |
|------------|------|---------------------|
| M16 採購審批門檻設定表單 + SuperAdmin 審批清單頁面 | Sprint 88 retro 排定「三者最佳化順序」第二項前半 | ✅ **已完成** |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S86 | 13 |
| S87 | 5 |
| S88 | 8 |
| **S89** | **5**（M16 門檻表單 + SuperAdmin 審批清單頁 + 前端型別缺陷修正） |

---

## 7. 下一步

> **檢查點**：Sprint 89 已完成，M16 採購審批門檻機制前後端全部串接完畢。下一輪依序進行：
> 1. Sprint 90：M07 結算逆轉發起/確認表單（後端 API 已完成）。
> 2. M18 客服工單子系統（PRD Phase 2-B）。
> 3. 技術債：`RELEASE_TRACKER.md` 補齊 Sprint 74+ 列；DEF-043/DEF-044 排程評估。

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
