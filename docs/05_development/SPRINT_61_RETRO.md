# Sprint 61 Retrospective / Sprint 61 回顧會議

> **Sprint 編號**: Sprint 61
> **期間**: 2028-02-13 ~ 2028-02-26
> **回顧日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001 5 + US-002 3）|
| 完成 SP | 8 SP（全數）|
| 主題 | M13/M14 後台管理深化（Admin Audit Log 查詢 + M13 測試防護網）|

---

## 2. 做得好的（What went well）

- **規劃階段即時停下確認卡關，而非硬闖**：原本 Sprint 61 主題規劃鎖定 M01 ElasticSearch 全文檢索，但盤點階段發現該項目需要新增 `DOCKER_POLICY.md` 未核准的 Docker image（CLAUDE.md 明文規定需人工核准），且 RICE 優先級實際上是所有候選中最低。及時停下向使用者確認並改打 M13/M14 後台深化，避免在受阻項目上投入後才卡關。
- **開發-編譯-測試循環確實揪出真 bug**：US-001 原規劃以靜態 JPQL 動態篩選寫法實作查詢，但依循「每完成一支程式立即編譯+測試」的紀律，在 E2E 測試階段（而非等到 Sprint 收尾才跑全量回歸）就發現 PostgreSQL 對純 null 參數的型別推斷限制，改用 Specification 動態組合解決，問題發現與修復成本都很低。
- **環境層級卡關（Docker Desktop 當機）正確升級而非默默重試到底**：驗證 pre-push CI 改造時遇到 Docker Desktop daemon 因先前 OOM 崩潰導致重啟卡住 15 分鐘，主動停下向使用者說明狀況與已嘗試的修復，取得授權後才進行更激進的強制重啟，而非無限重試或跳過驗證直接提交。
- **先驗證再提交的紀律延續**：4 個 CI/hook 改造檔案在提交前先實際跑過 `make validate-push` 確認可用，US-001/US-002 皆先跑過對應測試才 commit，符合「開發-編譯-測試循環」規則。

---

## 3. 待改善的（What to improve）

- **pre-commit 核心測試耗時偏長**：本 Sprint 兩次 commit（US-002、US-001）的 pre-commit 核心測試各花費約 5-10 分鐘（`mvn test -Dtest="core.**"` 會啟動多個 `@SpringBootTest`），造成開發循環中的等待時間偏長。目前無明確待改善行動（此為既有機制的已知取捨：commit 快檢換取 push 前的完整驗證），僅記錄供未來評估是否有選擇性跳過或平行化的空間。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |
| （未立案）| Admin 租戶/使用者列表伺服器端篩選修正 | `getTenants`/`getUsers` 補 status/keyword 參數；修正前端 client-side filter 與後端分頁不同步問題 | Dev David | P2 | 待排入 |
| （未立案）| `/dashboard/revenue` 營收報表頁補齊 | PRD 已規劃路由但未建頁，串接既有 `/v2/dashboard/revenue` | Dev David | P2 | 待排入 |

---

## 5. Sprint 60 → Sprint 61 Action Items 追蹤結果

| Action Item | 內容 | Sprint 61 達成狀態 |
|------------|------|---------------------|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|
| （未立案）| 結構化錯誤欄位方案 | 未啟動（待未來需求驅動）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S57 | 2 |
| S58 | 2 |
| S59 | 3 |
| S60 | 8 |
| **S61** | **8** |

> **觀察**：S61 = 8 SP，與 S60 持平。拆為 2 個獨立 US（5+3）降低單一項目風險，US-002 不依賴 US-001，可並行驗證。品質：後端單元 439 + 完整整合（含 failsafe）342 tests 0 fail、`make validate-schema` 無漂移。**DEF-016 遺留缺口正式清償**。

---

## 7. 下一步

> **檢查點**：Sprint 61 已完成。依現行節奏，本 Sprint 收尾後立即 push。`DEFERRED_ITEMS_TRACKER.md` 可自主執行的延後項目已再次清空，僅剩 AI-2416（需 Phase D-1 正式上線）與 AI-1903（需 live 環境）兩項非 Claude Code 可自主推進的項目；本次新增兩項未立案候選（Admin 篩選修正、營收報表頁）供下一 Sprint 規劃時評估優先級。Loop 將繼續依 `PRODUCT_BACKLOG.md` RICE 排序評估 Sprint 62 主題並自動推進。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
