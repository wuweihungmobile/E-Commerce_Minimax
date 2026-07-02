# Sprint 38 Retrospective / Sprint 38 回顧會議

> **Sprint 編號**: Sprint 38
> **期間**: 2027-03-28 ~ 2027-04-10
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP（P1+P2）| 8 SP（US-001~002）|
| 完成 SP | 8 SP（全數）|
| Buffer | US-003 買家 live 走查（延，需 live 環境）|
| 主題 | 買家體驗補完 —— 商品詳情頁 |

---

## 2. 做得好的（What went well）

- **前置驗證徹底,避免臆測**：動手前以 Explore + grep 確認後端端點（`/v2/listings/{id}`、`/{id}/price`）、DTO 欄位（CalculatePriceResponse / AddItemRequest）與加購參數,未對「探勘報告的推測」照單全收（探勘曾誤報 /price 不存在,實際存在——親自 grep 更正）。
- **借鑒 S37 教訓,避開重蹈覆轍**：詳情頁的加購鈕、日期表單全用專屬 testid（listing-add-cart / listing-checkin 等）,未再讓 `button[type="submit"]` 泛用選擇器碰撞;本 Sprint E2E 一次到位、無連鎖失敗。
- **mock 取代 seed,務實解鎖積欠 E2E**：AI-1905「有資料網格 + 分頁」積欠多 Sprint（卡在 seed 風險）,本 Sprint 改用 page.route mock（既有 at-homepage 模式）低風險落地,不再空等 seed 基建。
- **React 19 / route-group 慣例延續**：cartEvents 事件通知（比照 authStore）解決 layout 不 re-render 的購物車數更新,無 effect-setState 違規。
- **零後端變動達成新功能**：詳情頁完全復用既有後端端點,無 DB/migration 風險。

---

## 3. 待改善的（What to improve）

- **探勘報告需交叉驗證**：Explore 報告對 `/price` 端點的描述前後不一（先說存在、grep 又說沒有）,靠人工 grep 才釐清。日後對「關鍵 API 是否存在」應以實際 grep/讀檔為準,勿信單一來源。
- **ROOM 詳情僅半套**：目前 ROOM 只到「計價 + 加購物車」,完整訂房（日曆可用性、booking 建立）未做;買家 live 走查（S39）可能暴露缺口。
- **加購真實授權路徑未自動化**：E2E 加購走 mock 成功,未驗證「未登入加購 → 401 → 引導登入」的真實後端路徑（僅前端 addError 處理）。可於 live 走查或補一條登入態真實加購 E2E。
- **push 債持續（31 commit）**：S32~S38 累積 31 commit 未 push。技術阻礙（m15）已於 S37 清除,應盡快在檢查點完成 push。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1906 | 檢查點徵詢後 push S32~S38 累積批次 | `make validate-release` 綠燈後 push;嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-1903 | 買家閉環 live 走查 | 順延（需 live 環境）;含 S37 套版 + S38 詳情頁 | QA Quincy | P1 | S39 |
| AI-2101 | E2E 共用登入 helper 抽取 | 9 份重複 registerAndLogin 抽單一 helper（testid submit + waitForURL,含 DEF-022）| QA Quincy | P2 | S39 |
| AI-2103b | ROOM 完整訂房流程 | 日曆可用性 + booking 建立（詳情頁「預訂」直達訂房,非僅加購）| Dev David | P2 | S39 規劃 |
| AI-2104 | 登入態真實加購 E2E | 補一條登入 → 詳情 → 真實 POST /v2/cart/items → 購物車出現該品的整合 E2E | QA Quincy | P3 | 後續 |
| AI-2102 | secret 掃描器正/負案例測試 | 為 pre-commit secret 掃描加測試 | Dev David | P3 | 後續 |
| — | DEF-021（CJK 字體）| 續延後（P3）| — | P3 | 後續 |

---

## 5. Sprint 37 → Sprint 38 Action Items 追蹤結果

| Action Item | 內容 | Sprint 38 達成狀態 |
|------------|------|---------------------|
| AI-2103 | 商品詳情頁 | ✅ 完成（US-001）|
| AI-1905 | 首頁有資料 E2E + 分頁 | ✅ 完成（US-002，mock-based）|
| AI-1903 | 買家 live 走查 | ⏸️ 順延 S39（需 live 環境）|
| AI-1906 | 檢查點 push S32~S37（現 S32~S38）| ⏳ 待檢查點（m15 阻礙 S37 已清）|
| AI-2101 | E2E 共用 helper 抽取（含 DEF-022）| ⏸️ 順延 S39 |
| AI-2102 | secret 掃描器測試 | ⏸️ 續延 |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S33 | ~2 |
| S34 | 3 |
| S35 | 18（純前端異常高）|
| S36 | 10 |
| S37 | 10 |
| **S38** | **8** |

> **觀察**：S38 = 8 SP，健康區間下緣。新功能頁（詳情頁 PRODUCT/ROOM 條件渲染 + 計價/加購整合）保守估合理,實際一次到位、無返工——前置驗證 + 借鑒 S37 testid 教訓奏效。

---

## 7. 下一步

> **Sprint 39（規劃中）**: 買家 live 走查（AI-1903，驗證 S37 套版 + S38 詳情頁真實閉環）+ ROOM 完整訂房（AI-2103b）+ E2E 共用 helper 抽取（AI-2101）。並在檢查點徵詢後,把 S32~S38 累積批次經完整守門一次 push（AI-1906）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
