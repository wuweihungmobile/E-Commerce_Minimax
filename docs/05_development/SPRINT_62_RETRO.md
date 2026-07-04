# Sprint 62 Retrospective / Sprint 62 回顧會議

> **Sprint 編號**: Sprint 62
> **期間**: 2028-02-27 ~ 2028-03-11
> **回顧日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001 5 + US-002 3）|
| 完成 SP | 8 SP（全數）|
| 主題 | M18 知識管理/FAQ 測試防護網補強 |

---

## 2. 做得好的（What went well）

- **規劃前重新盤點而非照單全收 backlog 描述**：`PRODUCT_BACKLOG.md` 候選 #9 的描述已過時（維護紀錄 v1.3 早已註明「非從 0 建立」），規劃時沒有直接照描述排 2 SP 的小任務，而是重新盤點三個目標服務的實際方法覆蓋率，發現真正最大的風險缺口（`KnowledgeBaseService` 完全零覆蓋、含版本控制邏輯）並據此重新估點（5+3=8 SP），避免規劃失準。
- **識別出測試被繞過的假覆蓋**：發現 `M18KnowledgePhase2IntegrationTest` 雖然存在，但對 `KnowledgeBaseService` 使用 `@MockBean` 完全繞過，實質業務邏輯測試覆蓋是 0——這種「有測試檔案但沒測到真邏輯」的情況比「完全沒測試檔案」更隱蔽，仔細盤點才抓出來。
- **踩雷後正確診斷根因**：pre-commit 期間出現大量 `ClassNotFoundException`，沒有誤判為程式碼錯誤或盲目重試，而是正確識別為「背景 commit 與前景手動 mvn 指令共用 target 目錄互相破壞」的環境問題，以 `mvn clean test-compile` 驗證程式碼本身無誤後，改變操作順序（序列化、不並發）解決。

---

## 3. 待改善的（What to improve）

- **開發流程的並發意識不足**：本 Sprint 首次踩到「背景 commit 的 pre-commit 測試」與「前景手動開發下一個 US 時跑 mvn」共用 `target/` 目錄互相破壞的問題。已記錄為明確教訓（見 Review 第 4 節），未來執行多 US 並行開發時，應等待前一個 commit 的 pre-commit 完全結束後才在同目錄執行下一個 mvn 指令。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |
| （未立案）| FAQ 前端頁面 | `/v2/faqs` API 已備妥，補齊列表/詳情/搜尋頁面 | Dev David | P2 | 待排入（Sprint 63 候選）|
| （未立案）| Admin 租戶/使用者列表伺服器端篩選修正 | `getTenants`/`getUsers` 補 status/keyword 參數 | Dev David | P2 | 待排入 |
| （未立案）| `/dashboard/revenue` 營收報表頁補齊 | PRD 已規劃路由但未建頁 | Dev David | P2 | 待排入 |

---

## 5. Sprint 61 → Sprint 62 Action Items 追蹤結果

| Action Item | 內容 | Sprint 62 達成狀態 |
|------------|------|---------------------|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|
| Admin 租戶/使用者列表伺服器端篩選修正 | Sprint 61 提出 | 未排入（續留候選）|
| `/dashboard/revenue` 營收報表頁補齊 | Sprint 61 提出 | 未排入（續留候選）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S58 | 2 |
| S59 | 3 |
| S60 | 8 |
| S61 | 8 |
| **S62** | **8** |

> **觀察**：S62 = 8 SP，連續三個 Sprint 維持穩定產能。品質：後端單元 459（+38）+ 完整整合（含 failsafe）342 tests 0 fail、`make validate-schema` 無漂移。**`PRODUCT_BACKLOG.md` 候選 #9 正式清償，且發現並修復了比原描述更嚴重的測試缺口（KnowledgeBaseService 版本控制邏輯零覆蓋）**。

---

## 7. 下一步

> **檢查點**：Sprint 62 已完成。依現行節奏，本 Sprint 收尾後立即 push。`DEFERRED_ITEMS_TRACKER.md` 可自主執行的延後項目已再次清空，僅剩 AI-2416（需 Phase D-1 正式上線）與 AI-1903（需 live 環境）兩項非 Claude Code 可自主推進的項目。本次累積三項未立案候選（FAQ 前端頁面、Admin 篩選修正、營收報表頁）供 Sprint 63 規劃時評估優先級，其中 FAQ 前端頁面因「API 已備妥、純前端串接、無外部依賴」規模明確，是目前最可能的下一步候選。Loop 將依此繼續推進。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
