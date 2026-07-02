# Sprint 40 Retrospective / Sprint 40 回顧會議

> **Sprint 編號**: Sprint 40
> **期間**: 2027-04-25 ~ 2027-05-08
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 9 SP（US-001~003）|
| 完成 SP | 9 SP（全數）|
| 主題 | ROOM 可用性 UX 完成（含小幅後端）|

---

## 2. 做得好的（What went well）

- **實作前 grep 定位 API 陷阱**：動手前查出 availability 端點是 GET+@RequestBody（且測試註解早已自標「應改 @RequestParam」）,並查出 3 處以 body 呼叫的既有測試 → 一次連動修好,無事後驚喜。
- **read-only 後端變動 + 完整守門驗證**：首次後端變動控制在 controller 參數綁定（無 DB/schema）,並以完整 `make validate-release`（act 330 tests 0 fail）驗證,而非只靠編譯。
- **測試斷言防順序陷阱**：API-M06-009 用遠期日期（now+365）避開其他 @Order 測試已訂的近期日期,避免共享 testRoomListingId 的日期衝突假失敗。
- **前端 UX 收斂**：availability 統一取代 S38 /price-only,並以 roomAddBlocked 禁用未確認可用性的加購,行為一致。
- **環境衝突快速定位**：validate-release 首次失敗是 port 6379 被 test DB 佔用（非測試失敗）,依 CI 修復鐵律看實際錯誤 → test-db-down 後即通過,未盲改。

---

## 3. 待改善的（What to improve）

- **test DB 與 act 的 port 衝突需制度化**：pre-commit 核心測試需 test DB（5432/6379）,但 validate-release 的 act 也要 6379 → 兩者互斥。應在文件/Makefile 明確「commit 用 test-db-up、validate-release 前 test-db-down」,或讓 test DB 用非標準 port。
- **後端 pre-commit 需 DB 才能過**：核心測試含 @SpringBootTest（@ActiveProfiles integration-test）,無 DB 則 context 載入失敗。開發者需記得先 `make test-db-up`;可考慮把純需 DB 的 @SpringBootTest 移出「quick test」範圍。
- **availability 仍非整月日曆**：目前為區間查詢,買家無法一眼看整月可訂狀況;整月日曆 UI（+ 後端日曆端點 AI-2202 對齊）待評估。
- **push 債達 42+ commit（S32~S40）**：已通過完整 validate-release、技術面就緒,強烈建議此檢查點清償,勿再滾大。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1906 | 檢查點徵詢後 push S32~S40 累積批次 | 已通過完整 validate-release;檢查點徵詢後 push;嚴禁 --no-verify | Dev David | **P1（強烈建議清償）** | 檢查點 |
| AI-2301 | test DB / act port 衝突制度化 | 文件化「commit 啟 test DB、validate-release 前 test-db-down」;或 test DB 改非標準 port | SD Marcus | P3 | 後續 |
| AI-1903 | 買家閉環 live 走查 | 順延（需 live 環境）;含 S37~S40 全成果 | QA Quincy | P1 | S41 |
| AI-2202 | 端點契約清理 + 整月日曆評估 | api.ts 未實作端點（/listings/{id}/calendar）對齊 + 整月日曆 UI 評估 | SD Marcus | P3 | 後續 |
| AI-2101b | 登入 helper 完全統一 | at-m10 / at-m17 收斂 | QA Quincy | P3 | 後續 |
| — | DEF-021（CJK 字體）| 續延後（P3）| — | P3 | 後續 |

---

## 5. Sprint 39 → Sprint 40 Action Items 追蹤結果

| Action Item | 內容 | Sprint 40 達成狀態 |
|------------|------|---------------------|
| AI-2201 | availability 端點修復 + 詳情頁即時可用性 | ✅ 完成（US-001 後端 @RequestParam + US-002 前端即時可用性）|
| AI-1906 | 檢查點 push | ⏳ 待檢查點（已通過 validate-release，技術就緒）|
| AI-1903 | 買家 live 走查 | ⏸️ 順延 S41 |
| AI-2202 | 端點契約清理 | ⏸️ 續延（併整月日曆評估）|
| AI-2101b | 登入 helper 完全統一 | ⏸️ 續延 |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S35 | 18（純前端異常高）|
| S36 | 10 |
| S37 | 10 |
| S38 | 8 |
| S39 | 10 |
| **S40** | **9** |

> **觀察**：S40 = 9 SP，健康區間。含後端變動故保守;實作前 grep 定位陷阱 + 完整 act 驗證,品質紮實。環境 port 衝突耗少量除錯但依鐵律快速化解。

---

## 7. 下一步

> **Sprint 41（規劃中）**: 買家閉環 live 走查（AI-1903，驗證 S37~S40 全成果）+ 整月日曆/端點契約（AI-2202）。**強烈建議先於檢查點清償 push 債**（S32~S40 已通過完整 validate-release，技術就緒）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
