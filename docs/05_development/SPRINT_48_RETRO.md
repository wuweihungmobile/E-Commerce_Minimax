# Sprint 48 Retrospective / Sprint 48 回顧會議

> **Sprint 編號**: Sprint 48
> **期間**: 2027-08-15 ~ 2027-08-28
> **回顧日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001~002）|
| 完成 SP | 8 SP（全數）|
| 主題 | PRODUCT/cart 漲價——M12 進階定價 PRODUCT 側收官 |

---

## 2. 做得好的（What went well）

- **探勘揪出「兩道閘門」，避免只改一半**：規劃時憑 ROOM 經驗以為只需放寬 cart 閘門，探勘發現 PRODUCT 的 `applyProductRule` 結構上是 discount-only（數學上算不出漲價）——若只改閘門 1 會做出「看似完成但漲價仍不生效」的半成品。**探勘先行（Rule 1/8）在動手前就揪出真正的核心修改點**，並據此誠實把 SP 從 3 修正為 8。
- **先解結構性核心再放閘門**：嚴格照執行順序——先擴充 `applyProductRule` 並用單元測試（ProductEffectivePriceTests）證實能算出 effectivePrice>basePrice，再放寬 cart 閘門。避免「閘門放了但算不出漲價」的假綠。
- **向後相容 fallback 保住既有折扣**：既有 PRODUCT 折扣用「任意 ruleType + discountPercent」表達（S44），新設計讓漲價型 key 優先、未命中走 discountPercent fallback——SEASONAL+discountPercent 舊折扣零退步（整合測試佐證）。**擴充而非改寫，保護既有行為**。
- **下單自動繼承省一層改動**：因 `OrderService.createOrderFromCart` 信任 cart 的 subtotal，getCart 含漲價後訂單自動繼承，OrderService 無需改——探勘確認後省下不必要的改動（Rule 3）。
- **誠實界定 checkout 不改**：確認 checkout 為 ROOM 訂房頁、不逐項顯示 PRODUCT 定價後，不硬加無意義的顯示（Rule 2/3），並在 Review 揭露。

---

## 3. 待改善的（What to improve）

- **規劃期 SP 估算過度樂觀**：初估 3 SP 憑「比照 ROOM」，未先探勘 PRODUCT 計價路徑就報數字，實際 8 SP。→ 教訓：跨模組「比照既有」的估算，動手前應先快速探勘目標模組的實際結構（PRODUCT 與 ROOM 的計價器本就分歧），而非假設對稱。本次靠探勘及時修正並經 PO 核准。
- **PRODUCT/ROOM 兩套計價器分歧未根除**：本 Sprint 讓 PRODUCT 的 applyProductRule 支援漲價、對齊 config key，但仍是與 ROOM calculateAdjustment 並存的兩套計算器。→ 分歧是本次「閘門 2」的根因；長期宜評估抽共用「單日規則套用」核心（AI-2409），惟合併需考量 ROOM 的 stay-based 規則（EARLY_BIRD/LONG_STAY/LAST_MINUTE），風險中等。
- **cart 前端定價顯示債拖了 4 個 Sprint**：PRODUCT 折扣後端自 S44 已回欄位，前端到 S48 才顯示。→ 教訓：後端回傳的顯示欄位應在同 Sprint 或緊接 Sprint 接前端，避免「後端做了、前端沒接」的隱性未完成。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S48 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2409 | 定價計算器統一評估 | 評估抽 PRODUCT/ROOM 共用「單日規則套用」核心（消兩套計算器分歧）| SD Marcus | P4 | 待評估 |
| AI-2407 | 定價規則選取語意評估 | bestRule priority + findActiveRulesForDateRange 逐日精準查詢 | SD Marcus | P3 | 待評估 |
| AI-2202f | 開放窗清除機制 | 部分更新無法清 open_until_date/booking_window_days 回 NULL | SD Marcus | P4 | 待評估 |
| AI-2408 | availability reason i18n | availability unavailableReason 錯誤碼化 + 前端訊息映射 | SA Amanda | P4 | 待評估 |
| AI-1903 | 買家閉環 live 走查（真人）| 於 live 環境走查真 DB 跨角色資料流 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 47 → Sprint 48 Action Items 追蹤結果

| Action Item | 內容 | Sprint 48 達成狀態 |
|------------|------|---------------------|
| AI-2406c | PRODUCT/cart 漲價 | ✅ 完成（US-001 後端兩道閘門 + US-002 前端雙向顯示，M12 全面收官）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S48 累積 8 Sprint，待徵詢後完整守門 push）|
| AI-2407 | 定價規則語意評估 | 🟡 續留（P3 待評估）|
| AI-2202f / AI-2408 | 開放窗清除 / reason i18n | 🟡 續留（P4 待評估）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S43 | 10 |
| S44 | 8 |
| S45 | 5 |
| S46 | 8 |
| S47 | 7 |
| **S48** | **8** |

> **觀察**：S48 = 8 SP，實作型 sprint（含跨模組計價擴充），落在健康區間。品質：後端單元 22 + 真 DB 整合 54 全過、validate-e2e **53 passed/0 fail**（+1 E2E-M11-012）、schema 無漂移、catch(Exception)/@Deprecated=0、**schema-free**（V58）。**M12 進階定價全面收官**：ROOM+PRODUCT 折扣+漲價皆「顯示與收費一致」。

---

## 7. 下一步

> **檢查點**：Sprint 48 已完成（US-001 `b92447a` + US-002 `b3d0a8c` + 收尾，本地各層驗證通過含 validate-e2e 53/6/0 + schema 無漂移）。**push 債已累積 S41~S48（8 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。**M12 進階定價已全面收官**，Sprint 49 候選：AI-2407 定價規則選取語意、AI-2409 定價計算器統一、AI-2202f 開放窗清除、AI-2408 reason i18n、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能開發。**建議：M12 大功告成後，可考慮清償 8 Sprint push 債，或啟動新 EPIC。**

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
