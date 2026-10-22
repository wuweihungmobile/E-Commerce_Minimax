# Sprint 295 Plan — DEF-399：文件結構性漂移的機械守門（編號標題不被程式碼圍欄吞掉、技術棧版本對齊 package.json）

## 1. 起點與範圍

### 1.1 起點（使用者的明確決定，不是推論）

[Sprint 294](SPRINT_294_PLAN.md) 收尾時，我用 `AskUserQuestion` 就 `DEF-397`／`DEF-398`／`DEF-399` 各問了一題（每題都附我的建議與取捨）。使用者的回答（2026-10-22）：

| 項目 | 使用者選擇 | 與我的建議 |
|---|---|---|
| `DEF-397`（PRD §16.4.1 的 `Content-Type` charset） | **全域強制 charset** | **不同**（我建議修訂 PRD 措辭） |
| `DEF-398`（前端 99 筆 ESLint warning） | **99 筆全部處理** | **不同**（我建議先做行為不變的 58 筆） |
| `DEF-399`（文件結構性漂移守門） | **做 (a)＋(b)** | 相同 |

三項都照使用者的決定做，不套用我自己的建議。我排的順序：先 `DEF-399`（最小、純測試、不需 E2E）→ `DEF-397`（全域行為變更，需全量 `mvn verify`＋`make validate-e2e`）→ `DEF-398`（最大，55 個前端檔）。本 Sprint 是第一項。

### 1.2 範圍（`DEF-399` 登記的 (a)＋(b)，不含 (c)）

- **(a)** 檢查「編號標題不得落在程式碼圍欄內」。
- **(b)** 比對 `frontend/package.json` 的 `next`／`react` 主版本與文件技術棧表。
- **(c)** 前端路由 vs PRD §10 路由表——登記時就標「最脆弱（PRD 用『（及 `new`／`[id]`）』合併寫法），不建議」，使用者也沒選，**不做**。

### 1.3 成功標準（開工時定義）

1. 兩個守門測試在現況通過（零誤報）。
2. **每個守門都證明得了自己能失敗**：把 Sprint 292 發現的缺陷放回文件，測試必須變紅，且失敗訊息要直接指到檔案與行號。
3. 規則不能誤報已知的合法內容（例如 `SETTLEMENT_JOB_RUNBOOK.md` 的 PromQL `#` 註解）。
4. 掃描規則本身要有自己的單元測試（正例、反例、邊界）。
5. Checkstyle（測試）、PMD 通過；生產程式碼零變更。

## 2. 設計

兩個測試類別，放在新套件 `com.nextkey.ecommerce.docs`，定位 `docs/` 的方式比照既有 `ApiRouteDocDriftTest`／`ErrorCodeDocDriftTest`（`Path.of(System.getProperty("basedir", "."), "..", …)`）：

### 2.1 `DocMarkdownFenceTest`（(a)）

- **規則是由 Sprint 292 缺陷的成因推得的，不是憑空訂的**：孤兒圍欄造成的「吞掉區間」一定從一個**沒有語言標記**的圍欄開始（它原本是某個區塊的結尾，而結尾不能帶 info string），所以只檢查開頭沒有 info string 的圍欄。有語言標記的（` ```bash `、` ```markdown `）是刻意的範例區塊，裡面出現編號的 `# 1. 步驟` 註解是合法的。
- 標題只認**編號標題**（`## 7. …`、`### 6.7 …`）：shell／PromQL 區塊裡的 `# 註解` 沒有編號（`SETTLEMENT_JOB_RUNBOOK.md` 就有，Sprint 292 用 markdown-it 掃描時它是唯一的誤報來源）。
- 圍欄開關遵循 CommonMark：開頭是 0～3 個空白加 3 個以上同字元（反引號或波浪號），反引號圍欄的 info string 不得含反引號；結尾必須同字元、長度不短於開頭、後面只能有空白（**不能帶 info string**）；縮排 4 格以上不是圍欄。
- 掃描範圍：`docs/**/*.md` 與 `README.md`。
- **已知歷史快照名單**：`E-Commerce_PRD_v0.9_R02_Final.md` 有同一個孤兒圍欄，是被取代的歷史版本，Sprint 292 決定不修，所以放進 `KNOWN_HISTORICAL_SNAPSHOTS`。另有一個測試 `knownSnapshotsStillViolate` 保證名單不會過期：一旦該檔被修好，測試會要求把它從名單移除。

### 2.2 `TechStackDocDriftTest`（(b)）

- 讀 `frontend/package.json` 的 `next`、`react`（`dependencies` 找不到再找 `devDependencies`），取主版本（精確、`^`、`~`、`>=` 寫法都能解析）。
- 只比對「現況」的技術棧行（表格列與架構圖共 7 行，分布在 README、PRD、FRD、SRD），**不掃全文**：修訂註記與版本紀錄會合理地提到舊版本（例如「`Next.js 15` 校正為 `Next.js 16`」），全文比對會誤報。
- 代價是樣式綁定這幾行目前的寫法。文件改寫了版本行，測試會報「找不到」並指示更新 `CLAIMS`——刻意如此，比靜默失去保護好。

## 3. 驗證

### 3.1 現況

`mvn -o test -Dtest='DocMarkdownFenceTest,TechStackDocDriftTest'`：**11 個全過**（`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2），Maven 實際重新編譯了 304 個測試檔（不是被 IDE 預編蓋住）；surefire XML 逐案確認。全庫掃描（`docs/` 加 `README.md`）零違規。

### 3.2 突變驗證（11 個，全部擊殺）

把缺陷放回文件 → 跑兩個測試 → 比對失敗集合 → 還原（位元組比對＋`git diff` 雙重確認；v0.9 快照是 CRLF 換行，驅動腳本保留換行格式）。腳本在 session 暫存目錄，未進 repo。

| # | 把什麼放回去 | 預期紅燈的測試 | 結果 | 失敗訊息指到哪裡 |
|---|---|---|---|---|
| D1 | PRD 在 §6.6 末尾放回孤兒 ` ``` ` | `noNumberedHeadingSwallowedByBareFence` | **KILLED**，失敗集合恰為該測試 | `…PRD_v1.0_Final.md 第 958 行：### 6.7 …` 起列出被吞的標題 |
| D2 | PRD 技術棧列 `Next.js 16`→`15` | `techStackDocsMatchPackageJson` | KILLED | `…PRD… 第 69 行寫 next 15，但 frontend/package.json 是 next 16.x` |
| D3 | README 同上 | 同上 | KILLED | `README.md 第 19 行…` |
| D4 | FRD 同上 | 同上 | KILLED | `…FRD… 第 118 行…` |
| D5 | SRD 架構圖 `Frontend (Next.js 16)`→`15` | 同上 | KILLED | `…SRD… 第 46 行…` |
| D6 | SRD 架構圖 `React 19`→`18` | 同上 | KILLED | `…SRD… 第 47 行寫 react 18…` |
| D7 | SRD 技術選型表的 Next 版本欄 `16.x`→`15.x`（只改第二個群組） | 同上 | KILLED | `…SRD… 第 139 行…` |
| D8 | SRD 技術選型表 React `19.x`→`18.x` | 同上 | KILLED | `…SRD… 第 140 行…` |
| D9 | README 技術棧列改寫成別的說法（找不到樣式） | 同上 | KILLED | `README.md 找不到符合 /…/ 的技術棧行（文件改寫了版本行？請同步更新 TechStackDocDriftTest.CLAIMS）` |
| D10 | 把 v0.9 快照的孤兒圍欄修掉（名單該過期了） | `knownSnapshotsStillViolate` | KILLED | `…v0.9… 已不再有被吞掉的編號標題，請從 KNOWN_HISTORICAL_SNAPSHOTS 移除` |
| D11 | 在 README 結尾附加孤兒圍欄＋編號標題（證明 README 有被掃） | `noNumberedHeadingSwallowedByBareFence` | KILLED | `README.md 第 212 行：## 9. 被吞的編號標題` |

每個突變的失敗集合都**恰好等於預期**（沒有連帶紅燈），還原後 `git diff` 為空、位元組一致。

### 3.3 規則自己的單元測試（`DocMarkdownFenceTest` 的 7 個純函式案例）

孤兒結尾圍欄吞掉標題（Sprint 292 缺陷的重現）、圍欄成對不違規、有語言標記的範例區塊合法、無編號 `#` 註解不誤報（RUNBOOK 的情境）、結尾圍欄不能帶 info string、波浪號與結尾長度、info string 含反引號的行與縮排 4 格都不是圍欄。我寫完之後自己審查發現一個案例（行內程式碼那個）第一行以 `>` 開頭，根本不會被圍欄樣式匹配，沒有真的在測「info string 含反引號就不是圍欄」；已改成以 ` ``` ` 開頭並帶反引號的行。

### 3.4 其他

| 檢查 | 結果 |
|---|---|
| `mvn -o checkstyle:check@checkstyle-test` | commit 時由 pre-commit 執行（結果見 §6） |
| 生產程式碼 | `git diff -- backend/src/main` 為空 |

## 4. 限制（誠實揭露）

1. **這個守門在「純文件 commit」時，本機不會攔**：backend 的 pre-commit 快速測試只跑 `com.nextkey.ecommerce.core.**`，不含新的 `docs` 套件；pre-push 對「無 backend／frontend 程式碼變動」的推送直接放行。會跑它的時機是雲端 CI 的 `Backend Unit Tests`（`mvn test`，每次 push 都跑）、本機 pre-push（有程式碼變動時）、以及手動 `mvn test`。也就是說，純文件造成的漂移會在 push **之後**由雲端 CI 變紅發現，而不是在 commit 時。要在 commit 時攔，需要在 pre-commit 加一段「有 `docs/**/*.md` 或 `README.md` 被 staged 就跑這兩個測試」（比照 SRD 文件漂移守門那一段，約增加 15～30 秒）——這是 hook 變更，**本輪沒有做**，留給使用者決定。
2. **規則只抓「編號標題被吞」**：沒有編號的標題被吞掉不會被發現。取捨理由見 §2.1（避免誤報 shell／PromQL 註解）。
3. **巢狀在清單項目或引用區塊裡的圍欄不在範圍內**（縮排 4 格以上、`>` 開頭的行都不被當成圍欄）。本專案的 PRD 缺陷是頂層圍欄，掃描 668 個文件時沒有發現需要這個的情況。
4. **技術棧樣式綁定現行寫法**（§2.2）。
5. **(c) 路由表比對沒做**（§1.2）。
6. 只比對 `next`／`react` 兩個套件；Spring Boot、Java、PostgreSQL 版本沒有納入（`DEF-399` 登記的範圍是 `next`／`react`）。

## 5. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | 只檢查無語言標記圍欄內的編號標題 | 由缺陷成因推得，並且正好避開已知誤報（RUNBOOK） | 想擴大：改 `numberedHeadingsInsideBareFences` 的 `bare` 條件／標題樣式 |
| 2 | v0.9 快照放進名單而不是順手修掉 | Sprint 292 已決定歷史版本不改；名單加過期檢查，不會悄悄爛掉 | 想修快照：移除孤兒圍欄後，測試 `knownSnapshotsStillViolate` 會要求你清名單 |
| 3 | 掃描範圍 `docs/**/*.md`＋`README.md` | `README.md` 是技術棧表的來源之一 | — |
| 4 | **不改 pre-commit hook** | hook 變更有自己的風險（`CLAUDE.md` 對 hook 的歷史教訓），也超出 `DEF-399` 登記的範圍 | 想在 commit 時攔：在 pre-commit 增加一段（§4.1） |

## 6. 結果

| 項目 | 結果 |
|---|---|
| pre-commit（Checkstyle＋Maven 編譯＋核心層快速測試＋secret 掃描） | 一次通過（commit `b119a2d4`；因含 backend 檔案輸出達數 MB，以結尾的「快速檢查通過」與 `git log` 確認 HEAD 前進） |
| pre-push 輕量守門 | 通過（本機 act 的 Backend Unit Tests——其中已包含新的 `docs` 套件，所以新守門在本機 push 時實際跑過；schema 漂移守門 backend 啟動成功） |
| 雲端 CI | run [39726549258](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39726549258)：`completed／success`，三個 job（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build）皆 success，以 `gh run view --json` 確認 `headSha` 與本機 commit 一致 |

## 7. 後續

使用者已決定 `DEF-397`（全域強制 charset）與 `DEF-398`（99 筆全部處理），分別是 Sprint 296、297。

## 8. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/test/java/com/nextkey/ecommerce/docs/DocMarkdownFenceTest.java` | 新增（9 個案例） |
| `backend/src/test/java/com/nextkey/ecommerce/docs/TechStackDocDriftTest.java` | 新增（2 個案例） |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | v3.67→**v3.68**：`DEF-399` 結案；`DEF-397`／`DEF-398` 記錄使用者的決定 |
| `docs/04_planning/RELEASE_TRACKER.md` | v4.13→**v4.14**：新增 Sprint 295 row；版本歷史記錄上輪回填 commit 的雲端結果 |
| `docs/04_planning/SPRINT_295_PLAN.md` | 新增（本文件） |

生產程式碼（`backend/src/main`）零變更。
