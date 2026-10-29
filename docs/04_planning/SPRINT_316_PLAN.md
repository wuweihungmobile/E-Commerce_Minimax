# Sprint 316 Plan — DEF-438（店員看得到『編輯店鋪』）與 DEF-437（IT-M02-007／008 名實不符）

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 315](SPRINT_315_PLAN.md) 的總結，加一句『請繼續完成任務！』，**沒有任何 `==>` 回覆，也沒有互動選項的答案**。我的解讀（推論，不是使用者說的）：只做總結自己列為『下次無 `==>` 的「請繼續」我可以直接做的』兩項——`DEF-438`、`DEF-437`；追蹤表對這兩項都標了『不需產品決策』。不碰 `DEF-419`（權限模型決策）、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取等待使用者處理的項目。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，不再詢問。Sprint 315 的純文件回填 commit `1a99d105`（領先遠端 1 個）是本輪 commit 的祖先，會隨這次 push 一起帶出；使用者看完總結後沒有要求把它單獨提前 push。

### 1.2 成功標準（開工時定義）

1. 店員（`STORE_STAFF`）在核准中與非核准中的店鋪詳情頁都看不到『編輯店鋪』與『功能開關』；店主的對照組照常看得到（『功能開關』仍只給核准中的店鋪）。
2. 紅燈先行：新增／加強的 E2E 對修復前的建置紅燈，而且紅在『編輯店鋪』這個入口，不是別的原因。
3. 以真實的店員重新量測 `API_M17` §2.1 的九個端點，文件依實測校正；量測機械化成整合測試，並用兩種獨立的方法量測、結果必須一致。
4. `DEF-437`：先實測兩個測試是否真的『不可能失敗』再處置；清掉孤兒；真實守住的不變量不能因此少一個。
5. 每個新增的判斷都經突變驗證；全量後端 `mvn verify` 與完整 E2E 通過；文件守門測試在文件定稿後通過；push 後雲端 CI 全綠並核對整合測試數。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法（出處） | 核對結果 |
|---|---|
| 追蹤表 `DEF-438`：『編輯店鋪』沒有角色條件，只看 `isOwner` | **屬實**。讀碼 `TenantDetail.tsx`；E2E 對修復前建置紅燈：核准中店鋪的店員，『編輯店鋪』連結預期 0 個、實際 1 個（`NAV-02` 第 121 行；前面的『目前店鋪』對照與『功能開關』斷言都過）。 |
| 追蹤表 `DEF-438` Sprint 315 補記：非 `ACTIVE` 店鋪的店員也看得到 | **屬實**。`NAV-04` 的三個前提都過（已停權、店員拿到完整資料〔聯絡信箱有值〕、『目前店鋪』標籤在）之後，『編輯店鋪』預期 0 個、實際 1 個（第 158 行）。 |
| 追蹤表／`API_M17`：§4.6、§4.8、§4.9、§4.13、§4.14 對一般成員寫 `E-4031` | **錯誤碼不對**。真實的店員打九個端點**一律 `403 E-1007`**（方法安全層擋下，到不了 Service 層）；`E-4031` 要先通過方法層才遇得到（例如不是該店鋪成員的 `ADMIN`）。狀態碼 403 是對的。 |
| `API_M17` §2.1 的『範圍』欄：『同店 `STORE_STAFF`』沒有量測 | 本輪補上，兩種方法結果完全一致（見 §3）。 |
| 追蹤表 `DEF-437`：IT-M02-007 把服務整個 mock 掉（『讀碼，**沒有執行**』） | **升級為實測**：把 `BookingService.createBooking` 整個換成 `throw`，`BookingIntegrationTest` 的 4 個案例**一個都沒紅**，`BookingApiRealStackIntegrationTest` 18 個案例中 17 個轉紅（基準 22 案例全綠）。 |
| （新發現）同檔案的 `IT-M02-008` 是同款 | DisplayName『預訂衝突-跨租戶隔離』、方法名 `..._throwsNotFoundException`，斷言卻是 `201`，結尾註解自己承認『mock 繞過了租戶檢查，實際會返回成功』；`bookingService.createBooking` 沒有 stub、回 `null`，實際釘住的是『服務回 null 也回 201』。追蹤表只登記了 007。 |
| （新發現）`API_M17` §5 權限矩陣對 `SUPER_ADMIN` 標 ✅ | 與同一份文件 §2.1 的實測矛盾（`SUPER_ADMIN` 對 §4.2、§4.4～§4.14 過不了方法層，403 `E-1007`）。本輪拆欄時一併更正。 |
| （新發現）`TC_M02_Room.md` 與 Sprint 06 報告仍列 IT-M02-007／008 | 該目錄檔頭有 Sprint 244 的『現況聲明』：保留原樣未重寫、現行測試以程式碼為準。**不碰**。 |

## 2. 做法

### 2.1 前端：`TenantDetail.tsx`

新增 `canManageStore = isOwner && (currentRole === 'STORE_OWNER' || currentRole === 'ADMIN')`，『編輯店鋪』與『功能開關』共用這一條角色條件；『功能開關』仍多一個 `ACTIVE` 條件。角色集合直接沿用 Sprint 314 為『功能開關』定的（`features/page.tsx` 的放行條件）與追蹤表的建議（『比照功能開關，只對 `STORE_OWNER`／`ADMIN` 顯示』）；後端 `PUT /v2/tenants/{id}` 的 `@PreAuthorize` 放行的也是 `ROLE_STORE_OWNER`／`ROLE_ADMIN`。後端與編輯頁（`edit/page.tsx`）不動。

抽成共用常數而不是在『編輯店鋪』再抄一份同樣的條件，是因為 `DEF-438` 本身就是兩個兄弟條件各寫一份後漂移的結果；這個常數動到『功能開關』那一行（行為不變，由 `NAV-01`～`NAV-03` 與突變 F3 覆蓋）。

### 2.2 後端：只有測試

`TenantStaffAccessIntegrationTest`（新增，`integration/`，真實 PostgreSQL／Redis＋完整 JWT／權限鏈，MockMvc，風格同 Sprint 315 的 `TenantDetailsNonActiveIntegrationTest`）：2 個案例成對——

1. 店員（本店的有效 `STORE_STAFF`）依序呼叫 `API_M17` §2.1 的九個端點，結果表一次列出所有不符，預期一律 `403 E-1007`。
2. 對照組：店主發**同樣**的九個請求，預期 8 個 `200`、邀請 `201`。這個案例不可省：方法安全層在參數綁定之後才執行，請求本文不合法會先得 `400`、永遠看不到 `403`，沒有對照組就分不清店員『因為角色被擋』與『因為請求寫錯被擋』。

沒有加『被擋之後資料沒變』的副作用斷言：被方法安全層擋下時 Service 根本沒被呼叫，副作用斷言無法失敗（Rule 2、Rule 9）。

### 2.3 `DEF-437`：刪除而不是改名

追蹤表給的是『改名或刪除』二選一。選刪除，理由：007 唯一真實的內容（控制器對成功回應包 `201`）已有 `BookingApiRealStackIntegrationTest` 的『建立訂房 201』在**真實服務**上驗證；008 則什麼都沒驗證。改名會留下兩個以 mock 服務回傳值當斷言的案例，卻占用目錄裡『預訂衝突』的編號。同時清掉只有它們在用的孤兒（`buildMockBookingResponse`、`TEST_TENANT_ID`、`TEST_USER_ID`、`ObjectMapper` 與 `ListingRepository` 欄位及 import），類別 Javadoc 記下刪除原因與真實覆蓋的位置。

### 2.4 文件

`API_M17_Tenant.md` v2.8：§2.1 新增 `STORE_STAFF` 欄與說明、§4.5～§4.9／§4.13／§4.14 的錯誤碼表（店員 `E-1007`，`E-4031` 改成『通過方法層但不是該租戶店主／有效成員者』）、§5 權限矩陣拆欄並更正 `SUPER_ADMIN`、§6 新增 `E-1007` 列、新增修訂註記 10、§8 契約測試加上新測試。

## 3. 量測

### 3.1 兩種獨立的方法，結果完全一致

| 方法 | 做法 | 結果 |
|---|---|---|
| 打包 JAR 的真實 HTTP（一次性探針，**未入庫**） | 起 PostgreSQL＋Redis＋打包 JAR＋前端；資料全由真實流程建立：店主開店（含 Email 驗證、管理員核准）→ 另三個使用者註冊 → 店主邀請兩人為 `STORE_STAFF` → 兩人接受 → 店員重新登入；JWT 解碼確認 `role=STORE_STAFF`、`tenantId` 為該店鋪；店員與店主各發同樣的九個請求 | 店員：九個端點一律 `403 E-1007`；店主：`200`、`200`、`200`、`200`、`200`、`201`、`200`、`200`、`200` |
| `TenantStaffAccessIntegrationTest`（入庫） | MockMvc＋真實 PostgreSQL／Redis＋真實 JWT 過濾器與方法安全層 | 同上，2 案例通過 |

### 3.2 突變實測帶出的事實（寫進文件前才知道）

把九個端點的 `@PreAuthorize` 全部放寬到 `ROLE_STORE_STAFF`（突變 PALL）時：§4.2、§4.5、§4.7 店員得 `200`（Service 層只要求『有效成員』，店員正是；§4.2 只回呼叫者自己的成員資格）；其餘六個（§4.4、§4.6、§4.8、§4.9、§4.13、§4.14，含雖是 `GET` 的 §4.9）仍被 Service 層的店主檢查擋下（`E-4031`）。所以**方法層是 §4.2、§4.5、§4.7 在店員面前唯一的一道牆**——這正是這個整合測試值得留下的原因。

我在突變結果出來前，原本在文件裡寫『讀取類端點會直接對店員開放』；突變顯示 §4.9 雖是 `GET` 卻不開放，已改寫成上面實測的集合。

## 4. 測試與突變驗證

### 4.1 紅燈先行

| 層 | 做法 | 結果 |
|---|---|---|
| E2E（真實全棧） | 先改規格：`NAV-02` 加『目前店鋪』對照與『編輯店鋪』斷言、新增 `NAV-04`（停權店鋪的店員）；對**尚未修復**的建置跑 | `NAV-02` 紅在第 121 行、`NAV-04` 紅在第 158 行（『編輯店鋪』預期 0、實際 1）；`NAV-01` 綠；`NAV-03`（店主對照）綠 |
| E2E 修復後 | 重建前端、重啟 | 導覽與 `at-m17-004` 兩個規格 **9／9 通過** |
| 後端矩陣測試 | 特徵化測試（量測現況，不改生產碼）：沒有『修復前的紅燈』 | 第一次執行即綠；證明它咬得到靠下面的突變 |

### 4.2 突變驗證

做法沿用 [Sprint 314](SPRINT_314_PLAN.md) 第 4 節與 [Sprint 315](SPRINT_315_PLAN.md) 第 4 節（驅動腳本放在工作階段的暫存目錄，不進 repo；`old` 必須恰好出現一次，先乾跑；逐位元組還原並核對；失敗集合取自 surefire／failsafe 的 XML）。這輪新增：驅動收集**失敗訊息**，支援『這些字串必須出現／不可出現』的比對——結果表測試只有一個方法，光看方法紅燈無法歸因到單一端點。

**前端**（真實全棧，每個突變：改碼 → `npm run build` → 重啟前端 → 跑導覽規格；因 serial 模式第一個失敗會讓後面 `did not run`，每個突變再單獨跑一次 `NAV-03|NAV-04`）：

| # | 突變 | 結果 |
|---|---|---|
| F1 | `canManageStore` 的角色條件拿掉（`... \|\| Date.now() > 0`，永遠為真） | `NAV-02` 紅在第 119 行（『功能開關』預期 0、實際 1）；`NAV-04` 紅在第 158 行（『編輯店鋪』）；`NAV-01`、`NAV-03` 綠 |
| F3 | 只有『功能開關』按鈕改回 `isOwner && ACTIVE`（沒有角色） | `NAV-02` 紅在第 119 行；`NAV-04` 綠（停權店鋪本來就沒有『功能開關』，是它自己的 `ACTIVE` 條件擋下） |
| F4 | 只有『編輯店鋪』按鈕改回 `isOwner`（＝修復前的狀態） | `NAV-02` 紅在第 121 行（前面第 119 行的『功能開關』斷言是過的，所以確實抓到『編輯店鋪』）；`NAV-04` 紅在第 158 行 |

三個突變都還原並核對 SHA-256 一致，最後用還原後的程式碼重建並重啟前端。

**後端**（基準 Z0：2 案例、0 失敗；`TenantController` 的 `@PreAuthorize` 突變）：

| # | 突變 | 結果 |
|---|---|---|
| PALL | 九個端點的註解全部放寬到 `ROLE_STORE_STAFF` | 店員案例紅，失敗訊息九格全在：§4.2、§4.5、§4.7 `實際 200`；§4.4、§4.6、§4.8、§4.9、§4.13、§4.14 `實際 403 E-4031`；店主案例綠 |
| P4.4 | 只放寬 §4.4 | 店員案例紅，訊息**只有** §4.4（`實際 403 E-4031`），其餘八格沒出現 |
| P4.5 | 只放寬 §4.5 | 只有 §4.5（`實際 200`） |
| P4.14 | 只放寬 §4.14 | 只有 §4.14（`實際 403 E-4031`） |
| O4.8 | 邀請端點的註解拿掉 `ROLE_STORE_OWNER` | **店主**案例紅，訊息 §4.8 `預期 201，實際 403 E-1007`；店員案例綠 |
| O4.14 | 移除成員端點的註解拿掉 `ROLE_STORE_OWNER` | 店主案例紅，訊息 §4.14 `預期 200，實際 403 E-1007`；店員案例綠 |

PALL 證明九個格子各自獨立有牙齒（每格的標籤都出現在訊息裡）；P4.x 證明歸因不串；O4.x 證明店主對照組不是擺設。**沒有對全部九格逐一單跑**（只挑了 §4.4〔寫入類〕、§4.5〔讀取類〕、§4.14〔最後一個〕三個代表），逐格獨立性由 PALL 的訊息與結果表程式碼（逐格累積、不提前結束）支撐。

**`DEF-437` 的無效性實測**（對刪除前的原始測試）：

| # | 突變 | 結果 |
|---|---|---|
| Z0 | 基準 | `BookingIntegrationTest` 4＋`BookingApiRealStackIntegrationTest` 18＝22 案例、0 失敗 |
| V1 | `BookingService.createBooking` 第一行換成 `throw`（型別安全的 `if (Math.abs(1) > 0) throw …`，避免 unreachable code 編譯錯誤） | **17 案例紅，全在 `BookingApiRealStackIntegrationTest`**：`availability`、`calendar`、`cancel_paid_refundPolicy`、`cancel_unpaid`、`checkInAndCheckOut_byPlatformAdmin`、`checkInAndCheckOut_byStoreOwner`、`create_concurrentRequestsForSameDates_exactlyOneWins`、`create_errors`、`create_idempotencyKey`、`create_returnsTheStoreBuyerAndRoomIds`、`detail_authorization`、`listsAndDashboard`、`manualRefund_forSimulatedNoShowCancellation`、`manualRefund_rejectsNonSystemCancellation`、`payment`、`stateLog_afterCheckInAndCheckOut`、`update`；**`BookingIntegrationTest` 的 4 案例（含 IT-M02-007、008）0 紅** |

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| Sprint 315 雲端 CI（`12604d70`） | 已於 Sprint 315 回填：run [40893210008](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40893210008) 三個 job 皆 success、整合 974／0。其純文件回填 commit `1a99d105` 隨本輪 push 一起帶出。 |
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**21 分 26 秒**；單元 **2433／0 失敗／0 錯誤／0 跳過**（與 Sprint 315 相同，本輪沒有改單元測試）；整合 **974／0 失敗／0 錯誤／0 跳過**（974＋新類別 2－刪除的 2＝974）；Checkstyle main／test 都是 0 違規；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個。相關類別：`TenantStaffAccessIntegrationTest` 2、`BookingIntegrationTest` 2（原 4）、`TenantDetailsNonActiveIntegrationTest` 3（不變）。 |
| 完整 E2E 套件（`scripts/validate-e2e.sh`，重用已建置的 JAR 與前端） | **295 通過／0 跳過／0 失敗／0 flaky**（10.3 分鐘；294＋本輪 `NAV-04`）；後端以 `ddl-auto=validate` 對 Flyway 從零重建的資料庫啟動成功（entity 與 schema 無漂移）；『遷移後無測試帳號與測試租戶』的檢查通過；認證端點被限流 0 次；`NAV-01`～`04`、`M17-004-01`～`05` 都在其中。 |
| 這次動到的 E2E 規格（修復後，單獨跑） | 導覽＋`at-m17-004` **9／9 通過**（1.1 分鐘）；修復前 `NAV-02`、`NAV-04` 紅（見 §4.1）。 |
| `tsc --noEmit`（全專案，含 `e2e/`）、`npm run lint`（`eslint --max-warnings 0`，全專案） | 退出碼都是 0，`tsc` 沒有任何輸出（不經管線取得；修復後與定稿後各跑一次）。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`。 |
| IDE Problems | 全部條目診斷 **0**；本輪改過的 `BookingIntegrationTest`、新增的 `TenantStaffAccessIntegrationTest`（因為它沒有以真實路徑出現在總清單，另外單獨查詢過）、被突變後還原的 `TenantController` 與 `BookingService` 都是 0（`.ts`／`.tsx` 由 `tsc`／ESLint 把關）。 |
| 突變驗證 | 前端 3 個全擊殺；後端 6 個全擊殺（失敗訊息的端點標籤吻合）；`DEF-437` 無效性實測 V1（17 案例紅，全在真實棧）；所有突變都以逐位元組／SHA-256 核對還原，最後 `git status` 只剩本輪預期的 8 個檔案。 |
| 一次性真實 HTTP 探針 | 規格已刪除（未入庫），`git status` 確認 `frontend/e2e` 只剩預期的變更；輸出檔在被 git 忽略的 `frontend/test-results/`。 |
| 沒有執行的項目 | 本輪沒有改 schema、遷移，所以 `make validate-schema-doc` 不在範圍（沒有執行）；沒有量測『另一間店鋪的店主』對九個端點的結果；`DEF-439` 的風險形狀沒有實測。 |

## 6. 過程中的失誤與修正

1. **文件措辭先於證據**：寫 `API_M17` §2.1 時，我在突變結果出來前就寫了『讀取類端點會直接對店員開放』；突變 PALL 的失敗訊息顯示 §4.9 雖是 `GET` 卻得 `E-4031`，開放的只有 §4.2、§4.5、§4.7，改寫成實測的集合。
2. **工作目錄漂移**：早期幾個指令的 `cd frontend` 讓後續指令的 cwd 殘留在子目錄；之後每個指令都以專案根目錄的絕對路徑 `cd` 開頭。
3. **zsh 未加引號的萬用字元**：第一次搜尋 `--include=*.md` 被 zsh 擋成 `no matches found`，加引號重跑（已知陷阱）。
4. **macOS 的 `ls` 不支援 `--time-style`**：檢查後端 JAR 是否比主程式新時指令靜默失敗（沒有輸出），改用 `find -newer` 才得到可信的答案。
5. **追蹤表只登記了 007，同檔案的 008 是同款**：讀測試本體時才發現，納入本項處理並在 §8 列為可推翻的決定。
6. **下載雲端日誌第一次失敗**：較新的 `gh` 預設拒絕輸出含終端轉義序列（ANSI 顏色碼）的回應，`gh api …/logs` 退出碼 1、檔案是空的（背景任務的完成通知只說「failed」，原因要讀我自己導出的錯誤檔才知道）。加上它要求的 `--allow-escape-sequences` 重跑（輸出只進檔案、不進終端，再以 `python3 -I` 解析）才取得完整日誌。

## 7. 限制（誠實揭露）

1. **角色條件的 `ADMIN` 分支沒有案例覆蓋**：E2E 沒有『登入租戶就是這間店鋪的 `ADMIN`』的使用者；`ADMIN` 通過方法層但在 Service 層仍不是店主（`E-4031`），所以即使顯示了入口也只是按儲存得 403，影響很小，沿用『功能開關』既有的角色集合。
2. **編輯頁本身沒有進場角色守門**：店員直接輸入 `/dashboard/tenants/{id}/edit` 仍進得了表單（成員拿得到完整資料），按儲存才得 403；既有的 403 處理會顯示『您沒有權限編輯此店鋪』並導回列表。沒有改（追蹤表的建議只涉及按鈕，Rule 3）。
3. **前端 `isOwner` 條件仍沒有案例覆蓋**（同 Sprint 314、315 的限制）：E2E 情境裡沒有『角色合格但登入租戶不是這間店鋪』的使用者。
4. **『另一間店鋪的店主』對這九個端點的結果仍沒有量測**（`API_M17` §2.1 的範圍欄已如實保留這一項）。
5. **真實 HTTP 探針是一次性的，沒有入庫**；入庫的只有 MockMvc 整合測試。兩者本輪結果一致，但之後只有後者會持續守門。
6. **後端突變沒有對九格逐一單跑**（見 §4.2）。
7. **`BookingIntegrationTest` 剩下的 005、006 同樣是以 mock 服務的回傳值驗證控制器包裝**，名稱與斷言不矛盾，所以不在 `DEF-437` 的範圍；真實的可用性行為由 `BookingApiRealStackIntegrationTest` 守住。
8. **`TC_M02_Room.md`（含 Sprint 06 的 QA／測試報告）仍列 IT-M02-007／008**：依該目錄檔頭的現況聲明不維護，沒有碰。
9. **`DEF-439` 的『風險形狀』是推論**：沒有實測『可用性檢查或鎖鍵不含房源 id』會讓兩間店鋪的房源互相擋下。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『請繼續完成任務！』（無 `==>`）＝只做總結自己列的 `DEF-438`、`DEF-437`，不碰待裁決或待使用者處理的項目。
2. 『編輯店鋪』的角色集合沿用 Sprint 314 為『功能開關』定的 `STORE_OWNER`／`ADMIN`（追蹤表建議），不另造規則。
3. 抽共用常數 `canManageStore`，因此動到『功能開關』那一行（行為不變）。
4. 不替編輯頁加進場角色守門（見限制 2）。
5. `DEF-437` **刪除**而不是改名；並把同檔案同款的 **`IT-M02-008` 一併刪除**（追蹤表字面只寫 007）。
6. 不碰 `TC_M02_Room.md` 等歷史文件。
7. 順手更正 `API_M17` §5 對 `SUPER_ADMIN` 的 ✅（與同一份文件 §2.1 的實測矛盾，不更正的話我新增的欄位會和它並排矛盾）。
8. 被刪除的 008 暴露的覆蓋缺口只**登記**成 `DEF-439`，不順手補測試。
9. 把店員矩陣做成**永久的整合測試**，而不只是一次性探針（它守住的是 §4.2、§4.5、§4.7 唯一的一道牆）。

## 9. 新登記的缺陷

- **`DEF-439`**：不同店鋪的房源在同一段日期互不衝突，沒有任何真實測試守住（測試缺口，不需產品決策）。詳見追蹤表。

## 10. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 316 列。本輪的 commit `60d9beeb` 已 push（`12604d70..60d9beeb`，連同 Sprint 315 的純文件回填 commit `1a99d105` 一起帶出），依使用者 2026-10-29 的長期授權（『做完一輪後，直接Commit + Push to main』）直接 push，沒有再詢問；pre-push 輕量守門通過（後端單元與前端 Lint／建置兩個 act job 皆成功、schema 漂移無漂移）。雲端 CI run [40923546803](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40923546803) 三個 job 皆 success（Backend Unit Tests 2 分 55 秒／Frontend Lint & Build 1 分 04 秒／Backend Integration Tests & Package 10 分 30 秒，整個 run 13 分 31 秒，2026-10-29 17:40:19Z～17:53:50Z）；以 `gh run view --json status,conclusion,headSha,jobs` 交叉確認 headSha 為 `60d9beeb`，並下載整合 job 的完整雲端日誌（約 56 MB）確認整合測試 **974／0 失敗／0 錯誤／0 跳過**、`BUILD SUCCESS`、`<<< FAILURE`／`<<< ERROR` 標記 0 個，`TenantStaffAccessIntegrationTest` 2、`BookingIntegrationTest` 2、`TenantDetailsNonActiveIntegrationTest` 3、`BookingApiRealStackIntegrationTest` 18、`TenantControllerE2ETest` 14 與本機一致。回填 commit 只改文件，照 Sprint 306 以來的慣例留在本機，隨下一個有功能變更的 Sprint 一起帶出。

本輪的 commit 範圍：生產程式 `TenantDetail.tsx`（前端，唯一的生產碼變更）；測試 `TenantStaffAccessIntegrationTest`（新增）、`BookingIntegrationTest`（刪除 IT-M02-007／008 與孤兒）、E2E 規格 `at-dashboard-tenant-nav-real`；文件 `API_M17_Tenant.md`（v2.8）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-437`／`DEF-438` 完成、新登記 `DEF-439`）、`RELEASE_TRACKER.md` 與本計畫書。沒有 Flyway 遷移，後端沒有任何生產碼變更。

**待使用者處理**：`DEF-419`、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-439`（在 `BookingApiRealStackIntegrationTest` 補『兩間不同店鋪的房源同一段日期各訂一次都成功』，並做突變驗證）。另有兩個**可選的量測**（不是缺陷）：『另一間店鋪的店主』對 `API_M17` §2.1 九個端點的結果、編輯頁是否值得加進場角色守門（需要你決定要不要，所以沒有排進『可直接做』）。
