# Sprint 41 Retrospective / Sprint 41 回顧會議

> **Sprint 編號**: Sprint 41
> **期間**: 2027-05-09 ~ 2027-05-22
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 12 SP（US-001~006）|
| 完成 SP | 12 SP（全數）|
| 主題 | S41 技術債徹底清償 + 整月日曆 |

---

## 2. 做得好的（What went well）

- **實作前 5-Agent 並行探勘定位全貌**：動手前以 5 個 Explore Agent 平行摸清各項目現況（日曆/契約、登入 helper、port、字體、走查），一次釐清每項可行範圍與風險，避免逐項踩坑。
- **技術債制度化而非一次性修**：AI-2301 選「validate-release 自動 test-db-down」單一自動化點，同時涵蓋直接執行 + pre-push 兩路徑，根治而非再靠人工記憶。
- **契約清理先 grep 使用點再動**：US-003 移除死碼前 grep 全 `frontend/src` 確認 0 使用（listings.update/delete）、有用則對齊（pricing base path），無事後破壞。
- **旗艦 US 後端 read-only + 完整驗證**：整月日曆後端純新增端點（用既有 repository），無 schema 變動；以直接 mvn test（18 tests 0 fail）+ 完整 validate-e2e（47 passed、schema 對齊）雙重把關。
- **誠實界線清楚**：AI-1903 與 DEF-021 明確區分「可自動化交付」與「需真人/需拍板殘留」，未灌水宣稱完成（Rule 12）。
- **逐 US commit + 開發-編譯-測試循環**：每 US 完成即編譯/測試/lint/commit，隨時停在乾淨檢查點。

---

## 3. 待改善的（What to improve）

- **後端 commit 的 pre-commit 核心測試耗時**：@SpringBootTest 逐一啟動 Spring context，核心測試套件超過 2 分鐘，首次 commit 被工具 timeout 中斷。→ 後端 commit 應預留足夠 timeout；或評估把純需 DB 的 @SpringBootTest 移出 pre-commit「quick test」範圍（AI-2302 候選）。
- **背景長任務啟動方式**：`nohup ... &` 放進 run_in_background 命令會讓外層立即回 exit 0（誤判完成）；應直接讓工具背景執行單一命令、或用等待器輪詢 pid。已在本 Sprint 修正做法。
- **整月日曆為區間查詢版**：後端回傳「已有記錄」之日期，無記錄視為可訂；未涵蓋「房源尚未開放日曆」的顯式標記。整月日曆的「未開放 vs 可訂」語意可再細化（AI-2202 延伸）。
- **AI-1903 真人走查仍未執行**：自動證據 + checklist 已備，但真 DB 跨角色資料流走查需 live 環境，續延。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-1903 | 買家閉環 live 走查（真人）| 依 BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST 於 live 環境走查真 DB 跨角色資料流 | QA Quincy | P1 | 需 live 環境 |
| AI-2302 | 後端 pre-commit 核心測試提速 | 評估把純需 DB 的 @SpringBootTest 移出 quick test，或平行化 | SD Marcus | P3 | 後續 |
| AI-2202c | 整月日曆語意細化 | 「未開放 vs 可訂」顯式標記 + 整月價格顯示 | SD Marcus | P3 | 後續 |
| DEF-021 | CJK 字體 | 已決策 accepted fallback；如需品牌一致性再開正式 task（選項 B）| — | P3（待拍板）| 後續 |

---

## 5. Sprint 40 → Sprint 41 Action Items 追蹤結果

| Action Item | 內容 | Sprint 41 達成狀態 |
|------------|------|---------------------|
| AI-1906 | 檢查點 push S32~S40 | ✅ 已完成（上一 session push，origin/main=2e33c6d）|
| AI-2301 | test DB / act port 制度化 | ✅ 完成（US-001）|
| AI-2101b | 登入 helper 完全統一 | ✅ 完成（US-002）|
| AI-2202 | 端點契約清理 + 整月日曆 | ✅ 完成（US-003 契約 + US-004 整月日曆）|
| AI-1903 | 買家 live 走查 | 🟡 部分（自動證據 + checklist 交付；真人 live 走查續留）|
| DEF-021 | CJK 字體 | ✅ 決策完成（US-006，accepted fallback）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S36 | 10 |
| S37 | 10 |
| S38 | 8 |
| S39 | 10 |
| S40 | 9 |
| **S41** | **12** |

> **觀察**：S41 = 12 SP，略高於近期均值——因徹底清償全候選（技術債 4 項小型 + 整月日曆旗艦 + 2 評估/走查型）。品質未因量而降：完整 validate-e2e 47 passed、schema 對齊、後端 18 tests 0 fail。技術債（port/helper/契約）一次性清乾淨，後續 Sprint 可回歸功能開發。

---

## 7. 下一步

> **檢查點**：Sprint 41 已完成（8 commit，本地各層驗證通過含 validate-e2e）。**建議於檢查點徵詢後執行完整 `make validate-release` 並 push**（AI-1908；嚴禁 --no-verify）。Sprint 42 候選：AI-1903 真人 live 走查（需環境）、AI-2202c 整月日曆語意細化、AI-2302 後端 pre-commit 提速，或回歸新功能開發。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
