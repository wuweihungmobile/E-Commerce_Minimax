# Sprint 46 Retrospective / Sprint 46 回顧會議

> **Sprint 編號**: Sprint 46
> **期間**: 2027-07-18 ~ 2027-07-31
> **回顧日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001~002）|
| 完成 SP | 8 SP（全數）|
| 主題 | 定價機制真正統一——漲價型規則計入 ROOM booking（M12 進階定價收官）|

---

## 2. 做得好的（What went well）

- **決策 → 實作的乾淨銜接**：S45 產出 PRICING_MECHANISM_UNIFICATION.md 把「漲價是否計入 booking」界定為 PO 決策並另立 AI-2406b；S46 使用者拍板選項 B 後直接落地，無需重新探勘。**決策型 sprint 的產出（ADR 候選）真的驅動了下一個實作 sprint**，驗證 S45「決策密集項另立」的策略正確。
- **計算核心不動、只改取用端**：探勘早已確認 `calculatePrice` 的 adjustedTotal 本就含漲價乘數，本 Sprint 只放寬 BookingService 三處「折扣閘門」+ 補降級 + 前端顯示，**未動計算核心**——大幅縮小爆炸半徑，讓行為變更可控且可測。
- **中性語意設計避免欄位謊言**：漲價下「discount/省」命名會失真。採「有號 `discountAmount` + 新增 `priceAdjustmentType` 方向欄位」，保留欄位名向後相容、擴語意而非改名，前端據方向雙向顯示。**一個小設計決策同時解決了後端語意與前端顯示兩端**。
- **環境故障後的嚴謹交接與復原**：上一對話遇 Bash 重複輸出故障、且一度誤信污染輸出。透過詳盡交接文件 + 本 session 開工先 probe 驗環境、再以 `.git` / grep 交叉驗證 commit 落盤與幽靈變更清理，**未在髒狀態上疊加動作**，US-001 得以確認乾淨後才續 US-002。
- **schema-free 連續五 Sprint**：S42~S46 零 migration，計價行為變更全靠既有 jsonb config + DTO 欄位擴充達成，降低 schema 漂移與 push 風險。

---

## 3. 待改善的（What to improve）

- **E2E 編號與計劃脫節**：計劃寫「新增 ROOM-06/07 漲價變體」，但該編號早已是折扣案例，實際順延為 ROOM-08/09。→ 教訓：規劃 E2E 案例時應先查既有 spec 的最大編號，避免計劃與實作編號對不上（雖不影響行為，增加對照成本）。
- **背景並發 git commit 導致環境故障**：上一對話同時啟動多個背景 `git commit`（pre-commit 慢）並發操作同一 repo index，疑為 Bash 重複輸出故障誘因。→ 行動：commit 一次一個、前景執行設長 timeout，不並發多個背景 git commit（已寫入記憶）。
- **既有 priority / range 查詢語意落差仍待評估**：`bestRule` 最高 priority 勝出（非最低價）、`findActiveRulesForDateRange` 要求覆蓋整段區間（部分晚數 SEASONAL 漏套），漲價生效後這兩項的能見度提高。→ 本 Sprint 記錄不修，但需列入 backlog 追蹤，避免長期擱置成隱性技術債。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S46 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2406c | PRODUCT/cart 漲價評估 | `RedisCartService.tryProductDiscount` 目前只折扣，評估 PRODUCT 是否需計漲價（對齊 ROOM）| PM Victoria + SD Marcus | P3 | 待評估 |
| AI-2407 | 定價規則選取語意評估 | `bestRule` priority 治理 + `findActiveRulesForDateRange` 逐日精準查詢（部分晚數落差）| SD Marcus | P3 | 待評估 |
| AI-2202e | 開放窗語意實作 | PO 拍板 schema 後實作（migration + 四處 booking 邏輯 + 前端 + E2E + 既有房源 backfill）| SD Marcus | P3 | 待 PO 決策 |
| AI-2406 | room_calendar.price DROP COLUMN | `V58__Drop_Room_Calendar_Price.sql`（無資料無讀寫者，風險極低）| Dev David | P4 | 後續低風險 |
| AI-1903 | 買家閉環 live 走查（真人）| 於 live 環境走查真 DB 跨角色資料流 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 45 → Sprint 46 Action Items 追蹤結果

| Action Item | 內容 | Sprint 46 達成狀態 |
|------------|------|---------------------|
| AI-2406b | 漲價型規則是否計入 booking 總價 | ✅ 完成（PO 拍板選項 B → US-001 後端 + US-002 前端落地，漲價計入 ROOM booking）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S46 累積 6 Sprint，待徵詢後完整守門 push）|
| AI-2202e | 開放窗語意實作 | 🟡 續留（待 PO 拍板 schema）|
| AI-2406 | room_calendar.price DROP COLUMN | 🟡 續留（後續低風險）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S41 | 12 |
| S42 | 7 |
| S43 | 10 |
| S44 | 8 |
| S45 | 5 |
| **S46** | **8** |

> **觀察**：S46 = 8 SP，實作型 sprint（計價行為變更），較 S45（5 SP 決策型）回到正常負載，落在健康區間。品質：後端單元 18 + 真 DB 整合 34 全過、validate-e2e **50 passed/0 fail**（+2 漲價 E2E）、schema 無漂移、**無 schema 變動**（連續 S42~S46 零 migration）、catch(Exception)/@Deprecated=0。**M12 進階定價真正收官**：折扣 + 漲價皆「顯示與收費一致」。

---

## 7. 下一步

> **檢查點**：Sprint 46 已完成（US-001 `20a399b` + US-002 `151ce2d` + 收尾，本地各層驗證通過含 validate-e2e 50/6/0）。**push 債已累積 S41~S46（6 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。Sprint 47 候選：**AI-2202e 開放窗實作（需 PO 拍板 schema，將打破零-migration 慣例）**、AI-2406c PRODUCT/cart 漲價評估、AI-2407 定價規則選取語意評估、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能開發。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
