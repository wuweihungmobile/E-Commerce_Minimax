# Sprint 261 Plan — 文件一致性檢查（第四輪）：修正 7 處過期敘述，新登記 9 筆文件缺口

**Sprint**: Sprint 261
**日期**: 2026-10-13

## 1. 缺口盤點結果

### 1.1 起點與排程（推論）

延續上一輪 Sprint 的收尾總結：`DEFERRED_ITEMS_TRACKER.md` 已完成兩輪漂移搬移（134 筆移至已完成區），活躍區塊剩 52 筆真正開放的技術債，皆為低優先級或等待使用者決定，没有現成的高優先級開發項目可直接排 Sprint。依既有 playbook（S145／S203／S232 三次同類情境皆得出此結論），**待辦池清空到只剩「等你決定」與低優先級時，AISDLC 下一步固定是 document-consistency-check**，故本輪執行此流程，而非另挑一筆技術債開發。這是本輪對「繼續完成任務」的**推論**解讀，未經使用者在本輪逐項確認。

### 1.2 起點修正：上次對齊其實是 S244，不是 S232

原先假設「上次文件對齊」是 Sprint 232（28 輪前），但動工前先 `grep` 核實 S233～260 每輪標題後發現：**Sprint 241／243／244 本身就是三輪「文件對齊」**（M03～M07／M09），只比 S232 晚 9～12 輪；S241 還順手加了自動化測試 `ApiRouteDocDriftTest`，常態守門 `API_M03_Auth.md`／`API_M04_Cart.md`／`API_M05_Order.md`／`API_M06_Booking.md` 四份文件與對應 Controller 的路由一致性。真正的起點應為 **S244（17 輪前）**，範圍也排除這四個已有自動防護的模組，聚焦在 S244 當時沒排進去的模組（M07/M08/M12/M17）與 Sprint 245～260 新增的功能。

### 1.3 方法：三個唯讀 agent 平行稽核

| Agent | 範圍 | 耗時／token |
|---|---|---|
| A | API／SRD 技術文件 ↔ 未受 `ApiRouteDocDriftTest` 守護的模組（M01/M02/M07/M08/M12/M17）+ S245～253 新端點 | 14.5 分鐘／27.8 萬 token |
| B | SRD 技術章節／TC 測試文件／Docker 治理文件（CLAUDE.md 新規則） | 14.0 分鐘／22.9 萬 token |
| C | PRD／FRD ↔ S245～260 新功能追蹤鏈 + `DEFERRED_ITEMS_TRACKER.md` 交叉核對 | 15.5 分鐘／26.2 萬 token |

三個 prompt 皆明文禁止寫入檔案與執行 git 指令（CLAUDE.md 子 agent 唯讀範圍規則），回報後以 `git status`／`git diff` 核對確認三者皆未寫入任何檔案（乾淨）。

### 1.4 查出的差異（彙整，依四分類法）

**🔴 遺漏，本輪直接修正（7 處過期文字）**：

| 文件 | 問題 | 本輪處理 |
|---|---|---|
| PRD §9.7 修訂註記 | 「`state-log` 未實作（DEF-345）」已過期，Sprint 246（DEF-350）已實作 | 改為已實作，註明路徑差異 |
| PRD §10（5 列） | DEF-358(a)(b)(c)(d) 四項全數標示「⚠️ 缺口，待產品決策」，但 Sprint 256～259 已全部補齊，四份 Sprint 計畫書的「實作內容」清單都沒有回頭更新 PRD | 改為「已補齊」+ Sprint 編號 |
| FRD §6A.2／§6A.7 | state-log／店家後台按鈕同型過期敘述（❌ 未實作／「待排」） | 改為 ✅ 已實作 |
| FRD §8.6 | 連結指向不存在的 `API_M17_Tenant_Management.md`（實際檔名 `API_M17_Tenant.md`，此連結從未指向過存在的檔案） | 修正連結 |
| FRD 情境使用指引 | Phase 1/2 表仍寫 M06(完整)/M07/M09 為 Phase 2，但三者自 Sprint 244 起已各有專章並持續被更新為 Phase 1 等級內容，經 Sprint 244/245/247/249/250/252 六次 FRD 修訂都沒被同步 | 移至 Phase 1，M08 保留 Phase 2 並註明原因 |
| FRD 修訂紀錄表 | 檔頭版號跳到 v1.7，修訂紀錄表停在 v1.6，漏了對應列 | 補登 v1.7 列 |
| SRD §6.3.4 | 訂房狀態轉換表漏列 no-show 自動取消路徑（Sprint 246，DEF-352） | 新增一列 |

**🟡／🟢 遺漏，登記但本輪不修復（9 筆新 DEF，見 §1.5）**

**其他，一併修正**：
- `RELEASE_TRACKER.md` 既有 Sprint 246 列的連結長期指向不存在的 `SPRINT_246_PLAN.md`（兩個 agent 各自獨立確認）——重建該文件（見 §3）。
- SRD 檔頭「版本」欄、文檔元數據「最後更新」、檔尾「最後更新」三處各自獨立停在不同舊日期，與修訂歷史表最新列不一致——三處一併同步。

**確認無落差**（agent 逐項核對後判定正確，不需改動）：Sprint 245（核心入住退房）、247（結算）、248（PRD 路由）、249/250（通知，含確認 FRD §6C 已正確跟上，SRD 刻意不重複屬已記錄的決定）、251（評價授權修復本身不需文件變更）、252（PRD/FRD 路由層級皆已同步）、253（PRD §6.6.3 嵌入卡片）、259（FRD 訪客權限本就寫對，是程式碼追上文件）、260（確認為零呼叫點的預防性修復，不需文件變更）。TC 測試文件沒跟上 M06/M08/M09 新功能，核實是 **Sprint 244 使用者已拍板的政策**（改以程式碼測試為準），非意外遺漏。Docker 治理文件（image 版本、healthcheck、resources、profiles、local-llm 殘留）逐項核對**完全一致**，無違規。

### 1.5 新登記的缺口（9 筆，詳細內容見 `DEFERRED_ITEMS_TRACKER.md`）

| 編號 | 優先級 | 摘要 |
|---|---|---|
| DEF-364 | 🟡 | M07 結算模組從未建立 API 規格文件＋SRD 章節；結算沖正／撥款重試等高風險金流端點完全無文件、無自動防護 |
| DEF-365 | 🟡 | M08 評價模組從需求到 API 規格全線無文件，連基本 CRUD 都沒被記載過 |
| DEF-370 | 🟡 | FRD 缺「店鋪成員管理」（Sprint 248）User Story，7+1 個端點未入文件 |
| DEF-371 | 🟡 | FRD 缺「個人資料編輯」（Sprint 257）User Story |
| DEF-366 | 🟢 | `ReturnRequestController`（9 端點，Sprint 118 起存在）完全無文件、無自動防護 |
| DEF-367 | 🟢 | `SupportTicketController`（12 端點）同上，未完全驗證 |
| DEF-368 | 🟢 | M12 三個新端點未入文件＋一個文件宣稱存在但程式沒有的端點 |
| DEF-369 | 🟢 | M17 文件自身兩處內部矛盾＋6 個 `AdminController` 端點未入文件 |
| DEF-372 | 🟢 | `ApiRouteDocDriftTest` 自動防護範圍只覆蓋 4 個模組，另 6 個模組＋2 個完整 Controller 都沒有機械防漂移 |

另把 S261 新發現的 M01/M02 具體缺口（`category` 必填矛盾、3 個文件宣稱存在但不存在的端點、6 個程式有但文件沒提的端點）併入既有 **DEF-286**（原本只是概括性「索引不完整」，本輪補上具體清單），不開新編號。

### 1.6 更正與偽陽性

無——三個 agent 的發現經 `git status`／直接讀碼複核後皆屬實，唯一需要校正的是我自己原先對「上次對齊是哪一輪」的記憶（見 §1.2）。

## 2. 使用者決策（本輪結束時以 `AskUserQuestion` 徵詢）

本輪新發現的 9 筆缺口裡，M07／M08／ReturnRequest／SupportTicket／M17 成員管理／M03 個人資料編輯共 6 項需要**撰寫全新文件**（API 規格、SRD 章節、FRD User Story），工作量明顯大於單純修正過期文字，且優先順序屬產品／排程決策，本輪只登記、不擅自撰寫。徵詢內容見對話紀錄（是否另排 Sprint 專門補寫、只做金流風險最高的 M07 優先、還是先維持登記狀態）。

## 3. 實作內容（文件清單）

- `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md`：5 處修正
- `docs/01_requirements/E-Commerce_FRD_v1.0.md`：版本號 v1.7→v1.8、頂部新增修訂註記、6 處內文修正、新增 v1.8 修訂紀錄列（含補登遺漏的 v1.7 列）
- `docs/02_architecture/SRD_System_Architecture.md`：版本號 v1.6→v1.7、新增 §6.3.4 no-show 狀態列、新增 v1.7 修訂歷史列、三處版本/日期中繼資料同步
- `docs/04_planning/DEFERRED_ITEMS_TRACKER.md`：文件版本 v3.43→v3.44、新增歷史版本列、修正 DEF-286、新增 DEF-364～DEF-372 共 9 筆
- `docs/04_planning/SPRINT_246_PLAN.md`：**新建**（事後重建，見檔內說明）
- `docs/04_planning/SPRINT_261_PLAN.md`：本檔案
- `docs/04_planning/RELEASE_TRACKER.md`：新增 Sprint 261 列

## 4. 測試

不適用——本輪**沒有修改任何程式碼**，純文件修正與登記，不涉及編譯、單元測試或整合測試。

## 5. 驗證結果

- 三個 agent 回報後，立即 `git status`／核對確認工作目錄在 agent 執行期間維持乾淨（無意外寫入），符合 CLAUDE.md 子 agent 唯讀範圍規則的要求。
- `DEFERRED_ITEMS_TRACKER.md` 最大既有編號 `grep` 確認為 DEF-363，新編號從 DEF-364 起無衝突。
- Agent C 另發現活躍區塊 🟡+🟢 實際逐行計數為 22 筆而非上一輪總結所稱的 23 筆；本輪未深入查明這 1 筆差異的成因（表格內部分項目的 emoji 標記位置不統一，精確腳本化計數留待下一輪），不影響本輪任何修正的正確性，僅誠實記錄此計數落差存在。

## 6. 範圍外（延後）與待使用者決定

- **6 項新文件撰寫**（M07/M08 API 規格+SRD、ReturnRequest/SupportTicket 文件、M17 成員管理與 M03 個人資料編輯兩條 FRD User Story）：工作量大，列為 §2 的決策徵詢對象。
- **`ApiRouteDocDriftTest.GUARDED` 擴充**（DEF-372）：待上述文件補齊後再擴充機械防護，本輪不動測試程式碼。
- **追蹤表 🟡🟢 計數的 1 筆落差**：留待下一輪若需要精確對帳時腳本化處理。

## 7. Velocity 紀錄

本輪性質為稽核＋文件修正，非傳統 Story Point 開發工作，不納入 Velocity 計算。

## 8. 下一步／Action Items

1. 依使用者對 §2 的回覆，決定 M07／M08 等 6 項新文件的撰寫排程。
2. 若使用者選擇立即撰寫，建議比照 S241/243/244 的做法分輪進行（避免單輪範圍過大），優先順序建議：M07（金流風險）> M08（已有實際授權 bug 修復史）> M17 成員管理／M03 個人資料編輯（FRD User Story，相對獨立）> ReturnRequest／SupportTicket（純補文件，風險最低）。
3. commit 本輪所有文件變更；由於未改動任何程式碼，是否仍需跑完整 `make validate-release` 由 pre-commit/pre-push hook 既有規則決定（純 `.md` 變更預期可快速通過核心檢查）。
