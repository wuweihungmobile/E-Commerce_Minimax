# Sprint 36 Retrospective / Sprint 36 回顧會議

> **Sprint 編號**: Sprint 36
> **期間**: 2027-02-28 ~ 2027-03-13
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP（P1+P2）| 10 SP（US-001~003）|
| 完成 SP | 10 SP（全數）|
| Buffer | US-004 買家 live 走查（延，需 live 環境）|
| 主題 | 安全收尾（DEF-019）+ 版型架構債償還（DEF-020）|
| 清償 DEF | DEF-019（安全）、DEF-020（架構債）|
| 活躍安全 DEF | **歸零**（DEF-019 收尾）|

---

## 2. 做得好的（What went well）

- **安全項終於清零**：DEF-019 歷經 S32（盤點）→ S33（付款側）→ S36（物流/賣家側）三個 Sprint 落地，活躍安全 DEF 歸零。
- **實測驅動揪出隱藏債**：US-001 以本機乾淨 DB 實跑整合測試，揪出 **S33 遺留、未被 CI（integration job 可能 continue-on-error）擋下的 M07 5 個失敗**（`@Transactional` 一級快取 + Order 影子欄位），一併修好——真正讓 M07/M11 綠燈而非靠 continue-on-error 掩蓋。
- **架構重構以全棧 E2E 驗證**：US-002（route-group layout + client 邊界下推 + URL 搜尋）為重大架構異動，未只靠靜態綠燈，而以 `make validate-e2e` 全棧驗證 at-homepage 等價不退步（含最高風險的搜尋改走 URL）。
- **遵官方指引**：US-002 動手前讀 Next 16 docs，採「server page searchParams → props」官方建議做法，免 useSearchParams+Suspense；連帶償還 F-06（nonce）。
- **補齊驗收缺口**：US-003 把 T1 的 home-error/重試「功能已上、驗收缺席」補為 E2E（QA 四方審議提列）。

---

## 3. 待改善的（What to improve）

- **m15 既有 flaky 成為 release 阻礙**：`at-m15-e2e 媒體庫篩選功能` 在多次全棧 E2E 持續失敗（S35、S36 US-002/003），已非「偶發」而近乎穩定失敗。它與本 Sprint 無關，但會擋 `make validate-release`。**push 前必須實際處理（重跑或修復），不能長期靠「重跑」掩蓋。**
- **整合測試 job continue-on-error 掩蓋失敗**：S33 打掛 M07 卻未被守門擋下，暴露 CI integration 階段可能對失敗過於寬容。後續應檢視 act-compat integration job 是否應轉嚴格（惟需先清 flaky）。
- **@Transactional 測試一級快取陷阱**：影子欄位（insertable=false）在同交易快取下讀為 null，是重複踩到的坑（DEF-017、DEF-019）。應沉澱為測試撰寫指引/helper。
- **累積 push 債持續擴大**：main 已領先 origin/main 20 個 commit（S32~S36），push 守門一次要驗證的量大、風險高。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-2001 | m15 flaky push 前處理 | `at-m15-e2e 媒體庫篩選功能` push 守門前重跑；若仍穩定失敗則實際修復（media dialog/session teardown），不可長期靠重跑 | QA Quincy + Dev David | **P1（阻擋 release）** | 檢查點/S37 |
| AI-1906 | 檢查點徵詢後 push S32~36 累積批次 | 完整守門（make validate-release）綠燈後 push；含 AI-2001 前置 | Dev David | P1 | 檢查點 |
| AI-1901 | (auth) 買家頁套共用 Shell | S37 買家頁套 StorefrontShell（Tools 隱藏）；DEF-020 已鋪好 route-group 前置，直接沿用 layout | Dev David | P1 | Sprint 37 |
| AI-1903 | 買家閉環 live 走查 | 順延（需 live 環境）| QA Quincy | P1 | Sprint 37 |
| AI-1905 | 首頁商品「有資料」E2E + 分頁翻頁 | 需 seed 商品，補自動化（目前手動 checklist）| QA Quincy | P3 | 後續 |
| AI-2002 | @Transactional 測試影子欄位指引 | 沉澱「一級快取致 insertable=false 影子欄位為 null」之測試撰寫範式/helper（DEF-017/019 重複踩坑）| SD Marcus | P3 | 後續 |
| — | DEF-021（CJK 字體）/ DEF-022（E2E 硬等待）| 續延後，見 DEFERRED_ITEMS_TRACKER | — | P3 | 後續 |

---

## 5. Sprint 35 → Sprint 36 Action Items 追蹤結果

| Action Item | 內容 | Sprint 36 達成狀態 |
|------------|------|---------------------|
| AI-1902 | DEF-019 物流/賣家側 IDOR | ✅ 完成（US-001，活躍安全 DEF 歸零）|
| DEF-020 | 版型 Shell 架構重構 | ✅ 完成（US-002）|
| AI-1907 | home-error/重試 E2E 補測 | ✅ 完成（US-003）|
| AI-1901 | (auth) 買家頁套 Shell | ⏸️ 順延 S37（DEF-020 前置已就緒）|
| AI-1903 | 買家 live 走查 | ⏸️ 順延 S37（需 live 環境）|
| AI-1905 | 首頁有資料 E2E + 分頁 | ⏸️ 續延（需 seed）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S30 | 8 |
| S31 | 8 |
| S32 | 5 |
| S33 | ~2 |
| S34 | 3 |
| S35 | 18（純前端異常高）|
| **S36** | **10** |

> **觀察**：S36 = 10 SP，回歸健康區間（8~11），符合 S35 Retro「S36 涉既有頁面改造、SP 回歸保守」的預期。後端安全 + 前端架構重構混合 Sprint，實測驗證成本高但品質紮實。

---

## 7. 下一步

> **Sprint 37（規劃中）**: (auth) 買家頁全頁套版（AI-1901，沿用 DEF-020 route-group layout）+ 買家 live 走查（AI-1903）。並在檢查點徵詢後，把 S32~S36 累積批次經完整守門（含 AI-2001 m15 處理）一次 push。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
