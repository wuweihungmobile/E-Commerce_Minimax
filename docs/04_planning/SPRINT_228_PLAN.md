# Sprint 228 Plan — 前端相依審計改為每週排程（DEF-314 後續）

**Sprint**: Sprint 228
**日期**: 2026-10-01

## 1. 起點

Sprint 224 修掉前端 `next`／`axios` 的 critical／high 漏洞（`DEF-314`）時留下一個政策問題：`ci.yml` 的 `npm audit` 是 `continue-on-error: true` 且不是 push 觸發，漏洞才能一路存活而沒有任何關卡擋下。使用者 2026-10-01 對「依賴審計要不要進 CI 關卡」回覆「請依照最佳化進行！」（排程見 [SPRINT_225_PLAN.md](SPRINT_225_PLAN.md) §1）。

## 2. 方案比較（為什麼選排程）

| 方案 | 評估 |
|------|------|
| A. 放進 push 關卡（阻擋性） | ✗ 新的安全公告隨時會出現，與當次 push 改了什麼無關：不相干的 push 會突然變紅，還要在沒有人改相依的情況下被迫升級。本專案的關卡是「本機守門＋雲端 CI」，把外部資料庫的變動放進去會讓關卡不可重現 |
| B. 維持現狀（`ci.yml` 手動觸發、`continue-on-error`） | ✗ 等於沒有審計，正是 `DEF-314` 的成因 |
| **C. 每週排程、失敗只通知、不擋 push** | ✓ **採用**。漏洞出現後最多一週內會被看見；不影響任何 push；成本極低 |
| D. Dependabot alerts／security updates | 建議使用者另外啟用（GitHub 倉庫設定，免費、不耗 Actions 額度、**涵蓋後端 Maven**）；這是倉庫設定，無法由程式碼完成，本輪不動 |

## 3. 實作

- `.github/workflows/dependency-audit.yml`：每週一 01:00 UTC（台灣 09:00）與手動觸發（`workflow_dispatch`）；`npm audit --omit=dev --audit-level=high`（只看正式相依、high 以上），有漏洞就讓 workflow 失敗（GitHub 會通知最後修改它的人），結果寫進 job summary。`npm audit` 只讀 `package-lock.json`，不需要 `npm ci` 或建置，一次約 20～40 秒；`permissions: contents: read`、`timeout-minutes: 5`、`concurrency` 避免重疊。
- `Makefile` 新增 `make audit-deps`（同一條指令），本機隨時可跑；不接入 `validate-*` 任何守門。

## 4. 驗證

- workflow YAML 可解析（排程與手動觸發、三個步驟如預期）。
- `make audit-deps`：`found 0 vulnerabilities`（Sprint 224 升級後）。
- **反向驗證（指令確實會失敗）**：在暫存目錄以已知有漏洞的 `axios@1.15.0` 建 lockfile，`npm audit --omit=dev --audit-level=high` 結束碼為 **1**（含 `| tee` 與 `set -o pipefail` 之後），輸出「1 high severity vulnerability」——workflow 的步驟會因此失敗。
- **雲端首次實跑**：push 後以 `gh workflow run "依賴安全審計 / Dependency Audit" --ref main` 手動觸發一次，✅ **成功，17 秒**（run 36767879139；`npm audit` 在雲端同樣回報 0 個高風險漏洞）。排程本身（每週一 01:00 UTC）要等到週一才會第一次自動執行，未能在本輪看到。推送觸發的 `Local CI` 雲端 CI 全綠（run 36767866005）。

## 5. 決策與已知限制

- **只涵蓋前端 npm 相依**。後端 Maven 相依沒有任何排程掃描：`ci.yml` 的 `dependency-review-action` 只在 PR 上跑，而本專案只用 `main` 分支、不開 PR；OWASP Maven 外掛早已被它取代。建議啟用 Dependabot alerts（方案 D）。
- **排程會耗 Actions 額度**：每週一次、每次約 1 分鐘（計費上取整）。本專案曾為省額度停用排程（`technical-debt-review.yml`），這是目前唯一啟用的排程；不需要時把 `schedule` 區塊註解掉，`make audit-deps` 與手動觸發仍可用。若 Actions 帳單問題（2026-06-01／06-29／09-22 三度發生）再次出現，這個 workflow 也會立刻失敗並通知——那是帳單問題，不是漏洞。
- **「通知」依賴 GitHub 的通知設定**：排程 workflow 失敗時通知的是最後修改它的人；沒有另外接 Slack／Email。
- 本機 `make audit-deps` 需要網路（查詢 npm 的公告資料庫）。

## 6. 後續

- 使用者可考慮：啟用 GitHub Dependabot alerts（涵蓋後端）。
- 等使用者決定：`DEF-316`（商家端訂房管理，含權限模型）、`DEF-306`。
- 已登記未修：`DEF-315`（同一秒簽發的 Refresh Token 位元組相同）。
