# Sprint 292 Plan — 文件一致性檢查（第五輪）：修正 Sprint 261 之後累積的規格文件漂移

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 291 總結，回覆「請繼續完成任務，請依序多執行幾個項目，若不知道還有哪些，請回歸 AISDLC 流程，下一步該進行什麼？」。

對這句話的解讀（**推論，不是使用者說的**）：總結裡沒有任何 `==>` 逐項回覆，依既有慣例（[[inline-decision-replies-mean-proceed]] 的「無 `==>` 變體」）不替使用者決定 `DEF-397`／`DEF-398`；追蹤表活躍區也已確認沒有「範圍明確、無需決策」的新候選（逐列核對，S284 之後新增的只有這兩筆，皆等裁決）。這種情況下 AISDLC 的下一步固定是 **document-consistency-check**（[consistency-check.md](../../AISDLC_v0.09/workflow/core/consistency-check.md)）＋ **Sprint 驗收**（[sprint-execution.md](../../AISDLC_v0.09/workflow/core/sprint-execution.md) 步驟 5）。「多執行幾個項目」的解讀：檢查過程中發現的、AI 能獨立處理且不涉產品決策的缺漏，一併修掉。

開工先核對上輪收尾，不採信貼回的總結：`git status` 乾淨；`git fetch` 後 `main`＝`origin/main`＝`1f4c5b38`；`93ae0151` 與 `1f4c5b38` 的雲端 run（39634878830／39637021836）三個 job 皆 success、`headSha` 相符。

### 1.2 「上次檢查」的真正起點（先核實，不採信記憶）

記憶裡寫的上次檢查是 Sprint 261（第四輪，commit `96d1a7f7`，2026-10-13）。核實：Sprint 262～273 是依該輪發現補文件，Sprint 274～277 補 M10～M16 的 API 文件（與 DEF-286 主線同一系列），之後都不是整體一致性檢查；`git log 96d1a7f7..HEAD -- backend/src/main frontend/src` 顯示其後有 **15 個 commit 動過生產程式碼**（後端 57 檔、前端 28 檔）。檢查範圍以這 15 個 commit 為準，而不是整個文件樹重掃。

### 1.3 成功標準（開工時定義）

1. 每個 Sprint 261 之後的行為變更，都能對上現行規格文件，或明確列為已知缺漏。
2. 能機械驗證的項目（錯誤碼、權限、環境變數、Docker 映像、migration、路由、連結、錨點、Markdown 圍欄）用腳本或既有守門驗證，不憑印象。
3. 修正後重跑同一批檢查，數字下降到預期；既有守門（`ApiRouteDocDriftTest`、`validate-schema-doc.sh`）仍通過。
4. 檢查工具本身的缺陷要在結論前發現並更正（見 §6）。

## 2. 檢查方法與結果

### 2.1 機械檢查：無漂移（不需修正）

| 項目 | 方法 | 結果 |
|---|---|---|
| 新增錯誤碼 | 取 `ErrorCode.java` 自 `96d1a7f7` 以來新增的 12 個（`E-7505`、`E-8012`～`E-8019`、`E-8100`、`E-1098`、`E-1099`），逐一 grep 現行文件 | 12 個都在 `API_Error_Codes.md` 與另一份規格文件 |
| 新增權限 | `Permission.java`／`RolePermissionMapping.java` 的差異（僅 `SHIPPING_TEMPLATE_READ`） | `API_M11_Logistics.md` v1.1 已涵蓋 |
| 環境變數 | `application*.yml` 的 `${ENV}` 共 41 個 vs `ENVIRONMENT_VARIABLES.md` | 全部有記載；文件列出的 `E2E_GATE_*` 7 個在 `scripts/validate-e2e.sh`，不是過期 |
| Docker 映像 | `docker-compose*.yml` 4 個 image vs `DOCKER_POLICY.md` 核准清單 | 全部在核准清單，tag 一致 |
| Flyway migration | `git diff --name-status 96d1a7f7..HEAD` | 無新增 migration；`validate-schema-doc.sh` 通過 |
| 後端路由 | `ApiRouteDocDriftTest` | 2／2 通過（修改 API_M03／M14／M16／M17 之後重跑） |
| 通知管道誠實化（Sprint 271） | 該 commit 當時沒動規格文件，但 Sprint 272 的 `API_M09_Notification.md` 與 FRD §6C 已涵蓋 | 一致 |
| `@Cacheable` | 全庫只有 2 處（`dashboardStats`、`ratingStats`） | 兩者的 Redis 序列化設定都已就位（`dashboardStats` 原本就有，`ratingStats` 於 Sprint 285／DEF-390 補上）；這個掃描角度已窮盡，不需要再掃 |

### 2.2 逐 commit 對照：行為變更有沒有同輪更新規格

對 15 個 commit 逐一列「動到的生產程式」與「同輪動到的規格文件」。多數同輪已更新；沒有同輪更新的是 Sprint 269（前端水合，無行為變更）、Sprint 270（測試固件欄位，無 API 行為變更）、Sprint 271（通知管道，後續 Sprint 272 已補文件）、Sprint 281（`TenantService` 店主驗證）、Sprint 286（權限，Sprint 287 已補）、Sprint 288（編碼，待 `DEF-397`）。**唯一真正漏掉的是 Sprint 281**（見 §3 #5）。

### 2.3 腳本檢查（工具置於 session 暫存目錄，未進 repo）

- **斷裂連結**：掃 `docs/` 下 668 個 `.md` 的 1876 條相對連結（略過程式碼圍欄與行內程式碼）。
- **失效錨點**：72 條含 `#章節` 的連結，以 GitHub 標題 slug 規則比對目標標題。
- **Markdown 圍欄**：用 markdown-it 14.1.0（CommonMark 解析器，裝在暫存目錄、版本固定）解析全部 668 個文件，找「原文看起來是標題、卻被解析成程式碼區塊內容」的行。
- **前端路由 vs PRD §10**、**技術棧版本 vs `package.json`**。

## 3. 發現與處置

分類沿用 consistency-check 的 §2.3：🔴 遺漏（照文件做會出錯／讀到錯的）、🟡 待補充、🟢 有意省略、🟠 時序不匹配。

| # | 發現 | 證據 | 處置 | 類別 |
|---|---|---|---|---|
| 1 | **PRD 孤兒圍欄吞掉 10 個標題**：`E-Commerce_PRD_v1.0_Final.md` 第 955 行有一個沒有對應開頭的 ` ``` `（前面是 §6.6 的純敘述規格，沒有程式碼）。它讓其後所有圍欄的開／關角色對調，§6.7～§6.10 與 §7、§7.1～§7.5（含 **§7.3 RBAC 權限矩陣**）共 10 個標題在 GitHub／VS Code 預覽中被當成程式碼區塊內容 | markdown-it 解析：修復前 10 個標題被吞，修復後 0 個；`git blame` 指向 2026-09-03 的 commit `e9d599dd5`（Sprint 117），存在至少 7 週；連 `v0.9_R02_Final` 快照也有同一個 | 移除該行；PRD 升 v1.0.7 並加修訂註記 | 🔴 |
| 2 | **技術棧版本過期**：README、PRD §1.2、FRD §1.3、SRD §1.1 架構圖（2 行）與 §1.3 技術選型表（2 行：`Next.js 15` 一行、React 版本欄 `18.x` 一行）共 7 行寫 `Next.js 15`／`React 18`（關鍵字搜尋只抓到 6 行，React 版本欄那行是改表格時才發現的） | `frontend/package.json`：`next` 16.4.2、`react` 19.2.4；`git log -S'"next": "16'` 指向 2026-05-10，落後 5 個月。`Spring Boot 3.2`／`Java 21`／`PostgreSQL 18` 三項核對一致 | 改為 Next.js 16／React 19（PRD v1.0.7、FRD v1.17、SRD v1.8） | 🟡 |
| 3 | **PRD §10.1.2 路由表漏列**：`/dashboard/reviews`（Sprint 287，DEF-392）不在表內，而這張表在 Sprint 253 才依程式碼整表校對過；`/dashboard/erp`（ERP 總覽頁）與 `/cms`、`/cms/media`、`/cms/posts/new`、`/cms/posts/[id]/edit`（從 2026-05 就存在）也不在表內或補列清單 | 腳本比對 102 個前端頁面，扣掉「合併寫法」的誤報後，真缺漏是這 6 條；`/cms/*` 在 `DEF-074`／`DEF-097` 都被當成真實頁面修過，但專案內其他頁面沒有連結到它 | `/dashboard/reviews` 加進表格；其餘補進 S253 開的「實際已存在」清單並註明性質（見決定 #3）。**不判斷這些頁面該不該存在** | 🟡 |
| 4 | **6 條斷裂連結（現行規格文件）**：FRD 2 條（`API_M05_Order.md`、`API_M12_Dynamic_Pricing.md` 少了 `api/` 子目錄）、`API_M14` 1 條、`API_M16` 3 條（檔案在同一個 `api/` 目錄內，卻寫成 `./api/...`） | 腳本；目標檔都存在於 `docs/02_architecture/api/` | 改正路徑 | 🔴 |
| 5 | **`API_M17` §4.6 的 403 條件過期**：寫「非該租戶成員」，但 Sprint 281（DEF-331(f)）把 `TenantService.updateFeatureToggle` 改為只認店主（`STORE_OWNER`），`STORE_STAFF` 也是 403；該 commit 沒有同輪更新文件 | 讀碼 `TenantService.java`；單元測試 `updateFeatureToggle_notOwner_rejectedWithoutAudit` | 改為如實描述（`API_M17` v2.1）。本文件另外 7 處同款「非該租戶成員」列**本輪未逐一核對** | 🔴 |
| 6 | **21 個失效錨點**：`SRD_System_Architecture.md` §6.5 資料表索引 16 個（`SRD_Database_Schema.md` 的標題已改為 `2.1.1 \`tenants\` - …` 的分組編號，索引仍用舊的 `#21-tenants---…`）；`API_M03_Auth.md` 5 個（FRD 錨點寫 `#us-m03-001`，實際標題 slug 是 `us-m03-001-會員註冊`；`BR-M03-001` 是依賴項條列、不是標題，改指向引用它的 US 章節） | 腳本；16 個新錨點由程式依現行標題計算後再人工比對，不是手打 | 逐一改正 | 🟡 |
| 7 | **FRD 檔頭「最後更新」自建立起未維護**（停在 2026-04-09）——Sprint 289 登記的長期漂移 | 讀檔 | 改為 2026-10-22 並註明版本紀錄在檔尾（FRD v1.17） | 🟢 |
| 8 | **RELEASE_TRACKER 缺 Sprint 274～277 四個 row**：版本歷史 v3.98～v4.01 各寫「新增 Sprint 27X row」、統計區也逐次加了數，但總覽表從未有這四列 | 四個功能 commit 對本檔的 diff 為空；`git log -S'\| Sprint 274 \|'`（275／276／277 同）整個歷史零命中，對照組 Sprint 273 有命中。與「連續性已中斷」欄記載的 20 個已知缺列不同，這四列是**宣稱已新增、實際未寫入** | 依一手來源補列（版本歷史、當時統計列、commit、`gh run view` 重新核對三個雲端 run）；補列後總覽表缺列恰為已知的 20 個 | 🔴 |
| 9 | **文件結構性漂移沒有機械守門**（#1、#3、#2 三類都是靠一次性腳本才發現） | — | 登記 `DEF-399`（不排入排程，等使用者決定） | 🟡 |

## 4. 驗證

| 檢查 | 修復前 | 修復後 |
|---|---|---|
| PRD 被吞進程式碼區塊的標題（markdown-it） | 10 | **0**（掃 668 個文件，剩 `PRD_v0.9_R02_Final.md` 快照 9 個＝歷史版本；`SETTLEMENT_JOB_RUNBOOK.md` 2 個＝PromQL 的 `#` 註解行，誤報） |
| 失效錨點 | 23（修正工具後的數字，見 §6） | **1**（`PRD_v0.9_R02_Final.md` 快照內，同一個孤兒圍欄造成） |
| 斷裂連結（`docs/` 全部） | 98 | **92**（本計畫書建立後重跑；FRD／SRD 版本紀錄指向本計畫書的 2 條連結在建立前曾暫時算作斷裂，94→92） |
| 現行規格區（`01_requirements`、`02_architecture`，排除 `archive/`）斷裂連結 | 6 條 | **0**（`02_architecture` 剩 5 條全在 `archive/SRD_Database_Schema_designdraft.md`） |
| `ApiRouteDocDriftTest` | — | 2／2 通過（`mvn -o test -Dtest=ApiRouteDocDriftTest`） |
| `./scripts/validate-schema-doc.sh` | — | 通過（SRD 的 DDL 與 PRD §8.2 欄位清單皆與 Flyway 實際 schema 一致） |
| RELEASE_TRACKER 總覽表缺列 | 24（20 已知＋274～277） | **20**（恰為文件記載的已知缺口） |

## 5. Sprint 驗收（sprint-execution.md 步驟 5）

驗收範圍：Sprint 285～291（Sprint 284 之後的全部功能與修復 Sprint）。**驗收證據是雲端 CI 結果與各 Sprint 計畫書當輪已完成的成功標準驗證；本輪重新核對 CI 結果，沒有重跑測試。**

| Sprint | 功能 commit／雲端 run | 文件回填 commit／雲端 run | 結果 |
|---|---|---|---|
| 285（DEF-390／391／393） | `944f0dbf`／39150169431 | `4ca68a7d`／39151333985 | success／success |
| 286（DEF-330a／330c／395） | `3cc17094`／39204330846 | `b8b4f3eb`／39205593941 | success／success |
| 287（DEF-330a／392／394＋依賴審計） | `3be2eab6`／39438935574 | `a5abf913`／39441553186 | success／success |
| 288（DEF-396） | `4da22d39`／39457350591 | `42c35421`／39459486329 | success／success |
| 289（E2E 限流偵測＋DEF-373 文件） | `0f62feed`／39468533224 | `afa2edbc`／39470335748 | success／success |
| 290（IDE Problems 241→0） | `0d9ff326`／39523474421 | `544be7a4`／39525034180 | success／success |
| 291（DEF-394 收尾） | `93ae0151`／39634878830 | `1f4c5b38`／39637021836 | success／success |

這個區間內有一個非 success：**2026-10-19 的「依賴安全審計」排程 run 39206786930（`b8b4f3eb`）failure**——那就是 Sprint 287 升級 `next` 的起因；Sprint 287 之後 2026-10-20 的審計 run 39439129009 為 success。**但本輪開工時重跑 `npm audit` 又出現新的 high 等級漏洞**（`follow-redirects` 1.16.0，由 `axios` 帶入，兩則新公告 GHSA-q3jh-wrc6-jgq8、GHSA-8r9p-f939-6h3c，修補版 1.16.1）——這是 10-20 之後才公布的公告。它是依賴問題，不是文件問題，不混進本輪 commit，另開 Sprint 293 處理。

## 6. 未處理與限制（誠實揭露）

1. **92 條歷史斷裂連結沒有動**：Sprint 7～18 的計畫書／回顧、舊版 Release Notes（v12.0.0～v14.0.0）、`archive/` 目錄、`FLYWAY_EVALUATION.md` 等，是當時的歷史紀錄。成因（路徑寫錯或檔案後來移動）沒有逐一查證；修路徑會讓歷史紀錄「看起來」一直正確，我認為不值得。
2. **`PRD_v0.9_R02_Final.md` 快照同樣有孤兒圍欄，沒有修**：它是被 v1.0 取代的歷史版本。
3. **`docs/03_testing/` 的 TC 文件沒有檢查**：`TC_Index.md` 檔頭已有 Sprint 244 的現況聲明（停在 2026 年上半年、測試權威來源是程式碼），不是新發現。
4. **`API_M17` 其餘 7 處「非該租戶成員」列沒有逐一核對**對應 service 的檢查是店主還是成員（#5 只核對了有證據的 §4.6）。
5. **Sprint 275 的 Release Tag `v2030.04.132-01` 是依序列推定**，歷史上沒有任何 commit 出現過這個字串。
6. **「變更涵蓋了所有行為」沒有保證**：逐 commit 對照只看 commit 內有沒有動規格文件，再對沒動的 commit 讀其行為變更；對「同輪有動文件、但文件寫得不完整」的 commit（例如 Sprint 278 的 10 個新錯誤碼逐一對應到各模組）只做了抽查（錯誤碼 12 個都在 `API_Error_Codes.md`），沒有逐頁重讀。
7. **我自己的工具缺陷與兩次寫錯**（都在 commit 之前更正）：
   - 第一版錨點檢查用非貪婪正則配對 ` ``` ` 圍欄，遇到 PRD 的孤兒圍欄就把後面的標題當成程式碼，回報 43 個失效錨點（兩份 PRD 各 11 個，多數是工具誤判）；改為逐行追蹤圍欄後降為 23（兩份 PRD 各剩 1 個）。剩下的那 1 個（`#7-使用者角色人物誌與-rbac-權限矩陣`）標題文字與連結完全一致卻找不到，才去用真正的 CommonMark 解析器確認，發現是 §7 標題真的被吞（#1）——工具缺陷與真缺陷疊在一起，先修工具才看得到真缺陷。
   - 我先在 PRD 路由表補列說明裡寫「`/cms/media` 與 `/dashboard/media` 呼叫同一組 `services/cms` 端點」，查證後只有 posts 這一對成立；media 是兩套不同的 service（`DEF-387` 已決策維持現狀的兩套媒體庫），已在 commit 前改正。
   - FRD 與 SRD 的修訂紀錄我先寫錯了技術棧表的章節編號（FRD 是 §1.3 不是 §1.2；SRD 是 §1.1 與 §1.3 不是 §3／§4），commit 前改正。
8. **S289 留下的三個小項處置**：(a) 註冊遇限流先固定等 6 秒——S289 已明寫「不合併」（Rule 3），只是 E2E 耗時，**不做**；(b) `createReply` 拋 `E-1007` 的英文訊息 `Only the listing owner can reply`——讀碼確認這句只在「非管理員且非同租戶」時才拋（即另一租戶的外人），對外人說這句話不誤導，同租戶共同擁有人看不到，**不做**；(c) FRD 檔頭「最後更新」——**已做**（#7）。

## 7. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | PRD 孤兒圍欄用「移除該行」修，而不是補一個開頭 | §6.6 末段全是敘述性規格，沒有任何程式碼；移除孤兒結尾是最小改動，且 markdown-it 實測後所有後續圍欄恢復成對 | 還原 PRD 第 955 行（`git revert` 該段），後續圍欄會再次反轉 |
| 2 | 技術棧表改成 `Next.js 16`／`React 19`（對齊實際），而不是把 `package.json` 降回 15 | 5 個月來實際一直是 16，事實以程式碼為準 | 想鎖定「規格基準仍是 15」：還原這 6 處即可 |
| 3 | `/cms` 系列補進「實際已存在」清單並註明「沒有其他頁面連結過去」，**不**建議移除 | 不是缺陷；是否保留早期 CMS 後台屬產品決定 | 想移除這四頁：刪 `frontend/src/app/cms/` 並從 PRD 清單拿掉（`/cms` 與 `/dashboard/posts` 共用前端 `services/cms`；`/cms/media` 用 `services/cms`、`/dashboard/media` 用 `services/media`；刪除前先確認 E2E 沒有依賴） |
| 4 | 不修 92 條歷史斷裂連結、不修 v0.9 快照 | 歷史紀錄；見 §6.1／§6.2 | — |
| 5 | RELEASE_TRACKER 補列用「事後補登」明示，Sprint 275 的 Tag 標明為推定；**不**為 Sprint 274～277 建立計畫書 | 補列的每個事實都有一手來源可查；計畫書若重建只能抄 commit 訊息，沒有新資訊（Sprint 246 的前例有一手來源 commit message 可重建，這四個的核心資訊已在版本歷史） | 想要計畫書：以 Sprint 246 的重建方式補四份 |
| 6 | 登記 `DEF-399` 而不是直接做守門測試 | 新增測試是新程式碼，且規則設計（只守編號標題、不守路由表）需要使用者同意取捨 | 回「依照建議」即做 (a)＋(b) |

## 8. 需要使用者決定

1. **`DEF-397`**（PRD §16.4.1 `Content-Type` charset）：建議不變——選項 (3) 修訂 PRD 措辭（`application/json` 沒有定義 charset 參數，RFC 8259 §8.1／§11 已查證原文），不改全域編碼設定；可選：`SecurityConfig` 的 401／403 兩處補 `setCharacterEncoding` 並加單元測試，防止將來訊息改成中文重蹈 `DEF-396`。回「依照建議」即做。
2. **`DEF-398`**（前端 99 筆 ESLint warning／55 檔）：建議不變——先做行為不變的 58 筆（匿名 default export 22、未使用變數 36），其餘 41 筆逐案判讀；驗收為 `npm run lint` 0 warning、`type-check`、`build`、`make validate-e2e` 全綠。
3. **`DEF-399`**（新）：文件結構性漂移守門，建議先做 (a)「編號標題不得落在程式碼圍欄內」與 (b)「`package.json` 的 next／react 主版本 vs 文件技術棧表」，兩者合計約 60 行；不建議做 (c) 路由表比對。

## 9. 文件異動清單

| 文件 | 異動 |
|---|---|
| `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md` | v1.0.6→**v1.0.7**：移除孤兒圍欄；§1.2 `Next.js 16`；§10.1.2 補 `/dashboard/reviews`、`/dashboard/erp`、`/cms` 系列；修訂註記 |
| `docs/01_requirements/E-Commerce_FRD_v1.0.md` | v1.16→**v1.17**：技術棧 §1.3；2 條失效連結；檔頭「最後更新」 |
| `docs/02_architecture/SRD_System_Architecture.md` | v1.7→**v1.8**：Next.js 16／React 19（§1.1、§1.3）；§6.5 的 16 個錨點；兩處「最後更新」 |
| `docs/02_architecture/api/API_M03_Auth.md` | 5 個 FRD 錨點 |
| `docs/02_architecture/api/API_M14_Admin_Console.md` | 1 條連結 |
| `docs/02_architecture/api/API_M16_ERP.md` | 3 條連結 |
| `docs/02_architecture/api/API_M17_Tenant.md` | v2.0→**v2.1**：§4.6 的 403 條件＋修訂註記 7 |
| `README.md` | Next.js 16 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | v3.66→**v3.67**：新登記 `DEF-399` |
| `docs/04_planning/RELEASE_TRACKER.md` | v4.09→**v4.10**：補列 Sprint 274～277、新增 Sprint 292、統計區 |
| `docs/04_planning/SPRINT_292_PLAN.md` | 新增（本文件） |

純文件，無生產程式碼變更。
