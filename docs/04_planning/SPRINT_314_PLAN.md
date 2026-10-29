# Sprint 314 Plan — DEF-434／DEF-431／DEF-430／DEF-432①②

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 313](SPRINT_313_PLAN.md) 的結案總結，加上「請繼續完成任務！」，**沒有任何 `==>` 回覆，也沒有 `AskUserQuestion` 的互動答案**。這是「無 `==>` 變體」的第六個實例（第五個是 [Sprint 310](SPRINT_310_PLAN.md)）。以下是我的**解讀（推論，不是使用者明說的）**：只做 Sprint 313 總結自己列為「下次直接說『請繼續』我可以做的」、且追蹤表標「不需產品決策」或「建議直接做」的項目——`DEF-431`、`DEF-430`、`DEF-432`①②、`DEF-434`。

不碰：`DEF-419`（權限模型決策）、舊規則的資料遷移（對正式資料的變更，未授權）、`DEF-432`③（『編輯店鋪』是否放寬到非 `ACTIVE`，追蹤表明寫需你確認）、Docker 建置快取與 `make down-clean` 流程（使用者自己做）。

push 判準：本輪含生產程式碼（功能開關的回應語意、前端表單與導覽），不在 2026-07-08 的明確授權範圍（金流／安全）內，所以 commit 後**不 push**，收尾用 `AskUserQuestion` 問。本機目前領先遠端 1 個純文件回填 commit `9301f056`（Sprint 313 的 RELEASE_TRACKER 回填），若使用者選 push 會一起帶出。

### 1.2 成功標準（開工時定義）

1. `DEF-434`：`PUT /v2/dashboard/tenants/features/{feature}` 的 `enabled=false` 一律回 `DISABLED`；啟用分支行為不變；紅燈先行（單元＋真實 PostgreSQL／Redis／HTTP 鏈）；每個新增的判斷都經突變驗證。
2. `DEF-431`：先量測真實鎖下的並行行為，再決定不變量；IT-M06-104 不再是零斷言；真實棧有同等的並行訂房測試；突變驗證證明測試有牙齒。
3. `DEF-430`：儀表板首頁有『店鋪管理』入口，店鋪詳情頁有『功能開關』入口，可見性與目標頁的放行條件一致；E2E 走完整的點擊路徑。
4. `DEF-432`①②：開店申請與店鋪編輯表單都有選填的『店鋪描述』（≤ 1000 字）；編輯頁的死碼分支處理掉；E2E 讀回後端確認。
5. 全量後端 `mvn verify`、完整 E2E 套件通過；`tsc`／ESLint 0 警告；文件同步。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法（出處） | 核對結果 |
|---|---|
| `DEF-434`：『畫面不會觸發它…只有直接打 API 才遇得到』（追蹤表，Sprint 311 讀碼） | **不精確**。`Switch` 送出的是 `!checked`，所以開關顯示為關時送 `enabled=true`；但畫面停留在過期狀態（顯示為開、實際已被別處關掉）時點擊會送 `enabled=false`。前端用回應的 `newState` 更新畫面（`features/page.tsx` 第 119～137 行），修復前後畫面行為相同。`API_M17` 已更正。 |
| `DEF-434`：『改之前要先確認沒有既有測試或文件把舊行為當成契約』 | 已 grep。後端測試裡斷言 `PENDING_APPROVAL` 的功能開關案例（`TenantServiceTest` 2、`FeatureRequestLifecycleIntegrationTest` 1、`TenantControllerE2ETest` 1）都是送 `enabled=true`；前端 `at-m17-003` 的 `PENDING_APPROVAL` 斷言也是啟用申請的回應；`API_M17` §4.6、FRD AC-M17-006-1 只描述啟用申請。**沒有任何東西把舊行為當成契約。** |
| `DEF-431`：『若真實棧已有同等的並行訂房測試…則刪除』 | **沒有真實 Redis 鎖下的**。後端使用並行機制的測試檔共 21 個；以 `lockDateRange`、`concurrent`＋`booking`、`doubleBook` 等字樣 grep，併發建立訂房的只有 `BookingControllerE2ETest` 裡兩個案例，而且都在永遠取得的 mock 鎖下：`API-M06-002`（兩個錯開 50 毫秒的請求，條件式斷言——兩個都成功才失敗）與 IT-M06-104（零斷言）。`RoomCalendarLockRealRedisIntegrationTest` 只測逐晚鎖的取得與釋放（循序）。其餘檔案我只依這個 grep 判斷與併發建立訂房無關，沒有逐一讀。所以不能刪，要寫真實鎖下的並行測試。 |
| `DEF-431`：『六次執行都印出 201／400，但那只是 n=6』 | 本輪量測 **240 輪**（真實 Redis 鎖 120 輪、鎖 mock 120 輪；3～4 個執行緒同時送出，每輪用還沒被訂過的新日期，第二次探針每 20 輪換一間新房源），**每一輪**都是恰好一個 `201`、其餘 `400 E-4001`、資料庫恰好 1 筆訂房、2 晚日曆為 `BOOKED`。零偏離（沒有 500、沒有雙重預訂）。 |
| `DEF-431`：IT-M06-104 的『鎖失敗』是否會發生 | 不會。`IntegrationTestConfiguration` 的 `RedisLockService` 是永遠取得的 mock。但這正好可以利用：鎖永遠取得＝Redis 鎖完全沒有互斥效果，並行請求會一路走到資料庫那幾層。 |
| 各防護層實際攔下輸家的比例（探針，每種配置 20 輪 x 3 個執行緒＝40 個輸家，從日誌的警告訊息統計） | 真實 Redis 鎖：**39 個**被 Redis 鎖擋下、1 個被可用性檢查擋下。鎖 mock：**31 個**死在日曆的唯一約束、6 個被日曆列的『已被別筆訂走』擋下、3 個被可用性檢查擋下。兩段的合計各恰好是 40。 |
| `DEF-432`②：『`user.role === 'StoreOwner'` 分支永遠不會成立；實際值是 `STORE_OWNER`』 | 成立。`features/page.tsx` 以 `STORE_OWNER` 比對；突變 DC1 把分支的比對值改成 `STORE_OWNER` 後店主被擋在編輯表單外，同時證明店主的實際角色值就是 `STORE_OWNER`；店員的角色值 `STORE_STAFF` 由 `NAV-02` 的前提斷言（讀 `localStorage.user.role`）驗證。所以把死碼『修正』成 `STORE_OWNER` 會把店主鎖在外面。 |
| `DEF-432`①：『申請表單也沒有』 | 成立。FRD AC-M17-001-1 的測試資料含 `storeDescription`；PRD §9.10.1 標為選填（❌）。 |
| 我自己先寫進文件的『`GET` 回 `""`』 | **寫的當下沒量過**（E2E 的斷言用 `?? ''`，兩種表示都放行）。先用占位符標記，再用探針量測：`PUT {storeDescription: ''}` → `200`，回應與之後的 `GET` 都是 `""`（字串，不是 `null`）。 |
| `API_M17` §4.4 對一般成員（`STORE_STAFF`）寫 `E-4031` | **不精確**。探針量到：店員按儲存得 `403`／`E-1007`（方法安全層先擋下）。已更正 §4.4 這一行；§4.6、§4.8、§4.9、§4.13、§4.14 有同樣的句子，沒有逐一量測，登記 `DEF-438`。 |
| 後端對開店申請送出的空字串 | 實測會**原樣存成空字串**（突變 A2 的失敗訊息：`Received: ""`）。所以申請表單『留空就不送』有實質意義，不是多此一舉。 |

## 2. 做法

### 2.1 `DEF-434`（後端，一個方法）

`TenantService.determineToggleStatus`：`enabled=false` 一律回 `DISABLED`／`Feature disabled`，不再看請求前是否已啟用；啟用分支原樣（需審核且原本未啟用 → `PENDING_APPROVAL`，其餘 `ENABLED`）。沒有新增狀態值（前端只拿 `PENDING_APPROVAL` 判斷，新值沒有人會處理）。

### 2.2 `DEF-431`（只動測試）

量測後決定的不變量：**同一房源同一段日期同時進來的請求，每一輪恰好一個成立（`201`），其餘 `400 E-4001`；資料庫只有一筆訂房；那幾晚的日曆都是 `BOOKED` 且指向勝出的那一筆。**

兩個測試各守不同的層（上表的比例就是依據）：

| 測試 | 鎖 | 主要守的層 |
|---|---|---|
| `BookingApiRealStackIntegrationTest.create_concurrentRequestsForSameDates_exactlyOneWins`（新增） | 真實 Redis | Redis 逐晚鎖取不到時的錯誤對應（`E-4001`），以及真實鎖與資料庫的組合 |
| `BookingControllerE2ETest` IT-M06-104（改寫，原本零斷言） | 永遠取得的 mock＝鎖失效的最壞情況 | 資料庫那幾層：唯一約束衝突的錯誤對應、可用性檢查、日曆列的『已被別筆訂走』 |

兩者都是 6 輪 x 3 個執行緒，起跑柵欄讓請求盡量同時進入。IT-M06-104 每一輪並行之後另外補一個**確定性的晚到請求**（勝出者已提交），也必須 `400 E-4001` 且訂房數不變——原因見第 4 節（沒有它時，拿掉晚到者兩道防線的突變不會讓測試變紅）。

### 2.3 `DEF-430`（前端）

- 儀表板首頁的快速入口新增『店鋪管理』（`/dashboard/tenants`，`data-testid=dashboard-tenants-link`）。
- 店鋪詳情頁新增『功能開關』按鈕（`/dashboard/tenants/{id}/features`）。顯示條件＝`isOwner && ACTIVE && 角色為 STORE_OWNER／ADMIN`。比追蹤表原建議（`isOwner && ACTIVE`）多一個角色條件：功能開關頁自己只放行這兩種角色，其他成員點了會被導回店鋪列表，新入口不該替他們新增一條死路。

### 2.4 `DEF-432`①②（前端）

- ① 開店申請與店鋪編輯表單都新增選填的『店鋪描述』`textarea`（`maxLength=1000`）。沿用 `ReviewForm.tsx` 已有的原生 `textarea` 寫法，不新增 UI 元件。
  - 編輯表單預填目前的描述，**改過才送出**（後端 `null`＝不變更；沒改就不送，原本沒有描述的店鋪不會因為儲存其他欄位被存成空字串），清空時送空字串。
  - 申請表單留空就不送。
- ② 刪除 `edit/page.tsx` 的 `StoreOwner` 分支。**不是改成 `STORE_OWNER`**：分支的內容與註解（『StoreOwner 不能編輯』）和 FRD／API『店主可編輯』相反；授權本來就由後端 `PUT` 把關，前端不需要這個分支。

## 3. 測試

後端（紅燈先行，對修復前版本實測）：

| 檔案 | 變更 |
|---|---|
| `TenantServiceTest` | `UpdateFeatureToggleTests` +6：3 個修復前紅燈（`expected: <DISABLED> but was: <PENDING_APPROVAL>`）＋3 個守住啟用分支的對照案例 |
| `FeatureRequestLifecycleIntegrationTest` | +3（真實 PostgreSQL／Redis／HTTP 鏈；修復前這 3 個紅、原本 4 個綠）：沒申請過的需審核功能送 `false`、撤回待審核的申請後可再申請、免審核功能連送兩次 `false` |
| `BookingApiRealStackIntegrationTest` | +1：真實 Redis 鎖下的並行搶訂 |
| `BookingControllerE2ETest` | IT-M06-104 改寫（名稱與編號不變）；新增小方法 `bookingIdsCheckingInOn` |

前端 E2E（真實後端，完全不 mock）：

| 規格 | 變更 |
|---|---|
| `at-dashboard-tenant-nav-real.spec.ts`（新增） | 3 案例：店主從儀表板首頁一路點連結到功能開關頁；`STORE_STAFF` 看得到詳情頁但沒有入口；店鋪停權後沒有入口 |
| `at-m17-004.spec.ts` | 2 → 4 案例：01／02 改為檢查『描述被預填、沒改就不送、沒被洗掉』（`saveAndExpectDetail` 回傳 PUT 實際送出的內容）；新增 03（改描述）、04（清空描述） |
| `at-m17-001.spec.ts` | +2 案例：填描述／不填描述，從管理員待審核列表讀回申請紀錄 |
| `at-m17-003.spec.ts` | 只更正檔頭『沒有任何導覽連結通到』的說法 |

## 4. 突變驗證

做法沿用 Sprint 242 起的突變驗證驅動（驅動腳本放在工作階段的暫存目錄，不進 repo）：備份 → 套用（`old` 必須恰好出現一次，先乾跑檢查）→ 跑指定測試 → 還原 → 逐位元組核對；失敗集合從 surefire／failsafe 的 XML 取。每個突變都事先寫下預期的失敗集合（`expect`／`must`），**紅燈要驗『因為對的原因』**，所以前端另外讀每個失敗案例的斷言訊息。

### 4.1 `DEF-434`（後端，基準 Z0：81 案例、0 失敗）

6 個突變全數擊殺，**每一個的失敗集合都與事先預測完全一致**（不多不少）：

| # | 突變 | 擊殺者 |
|---|---|---|
| M1 | 還原舊行為（`enabled=false` 且原本未啟用 → `PENDING_APPROVAL`） | 單元 3＋真實棧 3（就是修復前紅燈的那 6 個新案例） |
| M2 | 停用回 `ENABLED` | 單元 4（含對照案例『停用已啟用的功能』）＋真實棧 4（含既有的『核准後店主關閉』案例） |
| M3 | 停用的說明文字多一個句點 | 單元 2 |
| M4 | 啟用分支忽略 `requiresApproval`（免審核功能啟用也變待審核） | 單元 1（對照案例） |
| M5 | 啟用分支忽略原狀態（需審核功能已啟用中再確認也變待審核） | 單元 1（對照案例） |
| M7 | 審核檢查排在停用檢查之前 | 單元 2＋真實棧 2 |

M4、M5 只有單元層的對照案例抓得到（真實棧案例沒有走到『啟用免審核功能』與『已啟用中再確認』這兩條路）。

### 4.2 `DEF-431`（後端；測試庫 `ddl-auto: update`，見限制 2）

IT-M06-104 的第一版（沒有晚到請求）與最終版分兩輪跑。

| # | 突變 | 第一版 | 最終版（含晚到請求） | 解讀 |
|---|---|---|---|---|
| Y1 | 取鎖失敗的錯誤碼 `E-4001` → `E-4000` | 擊殺：**只有**真實鎖測試 | 擊殺：**只有**真實鎖測試 | 真實鎖下 39／40 個輸家走這條；鎖 mock 測試不受影響 |
| Y2 | Redis 逐晚鎖取不到也當作成功（鎖層關掉） | 存活（兩個測試都綠） | 存活（兩個測試都綠） | 防禦縱深：資料庫那幾層仍擋住，測試守的是不變量，不是『每一層都存在』 |
| Y3 | 唯一約束衝突的錯誤碼 `E-4001` → `E-4000` | 擊殺：**只有**鎖 mock 測試 | 擊殺 | 競態路徑的錯誤對應 |
| Y4 | 唯一約束衝突的 `catch` 不匹配（例外原樣往外丟） | 擊殺：鎖 mock 測試 | 擊殺 | 同上 |
| Y5 | 日曆列『已被別筆訂走』不丟例外 | 存活 | 存活 | 可用性檢查仍擋住晚到者（防禦縱深） |
| Y7 | 可用性檢查永遠通過 | （未跑） | 存活 | 日曆列『已被別筆訂走』仍擋住（防禦縱深） |
| Y6 | Y5＋Y7（晚到者的兩道防線都拿掉） | **存活** | **擊殺** | 第一版對這兩層不敏感（同時進來的請求主要死在唯一約束），補上確定性的晚到請求後才抓得到 |
| Y9 | 每一層都拿掉（Y2＋Y5＋Y7＋Y4） | 擊殺：兩個測試都紅 | 擊殺：兩個測試都紅 | 完全沒有防護時兩個測試都偵測得到 |

第一輪（第一版 IT-M06-104）與第二輪（最終版）的 Z0 基準都是兩個測試全綠；第三輪補跑 Y1、Y2、Y9 是為了讓每一列的『最終版』欄都對著最終版的測試。三輪的還原都以逐位元組核對（`restored=True`），並確認 `backend/src/main` 只剩 `TenantService.java` 一個被修改的檔案。

### 4.3 `DEF-430`／`DEF-432`（前端，真實全棧，7 批 16 個突變；基準：未突變的建置 14 個案例全綠，最終狀態再跑一次 14／14）

每批內各突變的首個失敗案例互不重疊（`serial` 模式下第一個失敗會讓後面的案例不執行）。**15 個擊殺、1 個存活**，每個都以預期的案例、預期的原因變紅（讀每個失敗案例的斷言訊息核對）：

| 批 | 突變 | 擊殺案例與失敗訊息 |
|---|---|---|
| B1 | N1 拿掉儀表板快速入口 | `NAV-01`：`getByTestId('dashboard-tenants-link')` 找不到 |
| B1 | D1 編輯表單不預填描述 | `M17-004-01`：`#storeDescription` 預期有值、實際 `""` |
| B1 | A1 申請表單不送描述 | `M17-001` 填描述案例：申請紀錄預期有描述、實際 `null` |
| B2 | N2 快速入口網址指到 `/dashboard` | `NAV-01`：網址停在 `/dashboard` |
| B2 | D2 編輯表單一律送出描述 | `M17-004-01`：『沒改店鋪描述就不送出』被違反 |
| B2 | A2 申請表單把空字串也送出 | `M17-001` 不填描述案例：預期 `null`、實際 `""`（順帶證實後端會原樣存空字串） |
| B3 | N3 功能開關連結指到詳情頁 | `NAV-01`：網址停在詳情頁、預期 `/features` |
| B3 | D3 編輯表單從不送描述 | `M17-004-03`：送出內容的 `storeDescription` 預期新值、實際 `undefined` |
| B3 | A3 申請表單少了 `maxLength` | `M17-001` 填描述案例：`maxlength` 預期 `1000`、實際沒有 |
| B4 | N4 拿掉角色條件 | `NAV-02`：店員看得到『功能開關』（計數 1、預期 0） |
| B4 | D4 編輯表單少了 `maxLength` | `M17-004-03`：`maxlength` 預期 `1000`、實際沒有 |
| B5 | N5 拿掉 `ACTIVE` 條件 | `NAV-03`：停權店鋪仍有『功能開關』（計數 1、預期 0） |
| B5 | D5 清空描述時改成不送（空字串被轉成 `undefined`） | `M17-004-04`：清空要明確送出空字串、實際 `undefined` |
| B6 | N6 按鈕永不顯示 | `NAV-01`：點擊逾時（入口不存在；之後已在點擊前加可見性斷言，缺入口時幾秒內就紅） |
| B6 | DC1 把死碼分支『修正』成 `STORE_OWNER` | `M17-004-01`：店主被擋在編輯表單外（`#storeName` 找不到） |
| B7 | N7 拿掉 `isOwner` 條件 | **存活**（11 個案例全綠，預期的等價突變，見限制 4） |

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**19 分 32 秒**；單元 **2421／0 失敗**（2415＋本輪 6：`TenantServiceTest.UpdateFeatureToggleTests`）；整合 **971／0 失敗**（967＋本輪 4：`FeatureRequestLifecycleIntegrationTest` 3、`BookingApiRealStackIntegrationTest` 1；IT-M06-104 是改寫，不增）；Checkstyle main／test 都是 0 違規；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個。四個新增或改寫的類別都有跑到：`TenantServiceTest$UpdateFeatureToggleTests` 17、`FeatureRequestLifecycleIntegrationTest` 7、`BookingApiRealStackIntegrationTest` 18、`BookingControllerE2ETest` 25，全過 |
| 完整 E2E 套件（`scripts/validate-e2e.sh`，重用已建置的 JAR 與前端） | **293 通過／0 跳過／0 失敗**（286＋本輪 7），7.3 分鐘；後端以 `ddl-auto=validate` 對 Flyway 從零重建的資料庫啟動成功（entity 與 schema 無漂移）；認證端點被限流 14 次（E2E 輔助函式依 `Retry-After` 重試，全數自癒）；日誌中跳過、flaky、did not run 的標記皆 0 個 |
| 這次動到的 5 個 E2E 檔案（突變驗證全部結束、還原並重建後） | 14／14 通過（48.8 秒）；完整套件內同樣通過 |
| `tsc --noEmit`（全專案，含 `e2e/`）、`npm run lint`（`eslint --max-warnings 0`，全專案） | 退出碼都是 0（不經管線取得） |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**，`BUILD SUCCESS`（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1） |
| IDE Problems | 42 個檔案條目、診斷 **0**；本輪改過的 `TenantService`、`TenantServiceTest`、`FeatureRequestLifecycleIntegrationTest`、`BookingApiRealStackIntegrationTest`、`BookingControllerE2ETest` 都在條目內且 0 診斷 |
| 突變驗證 | `DEF-434` 6／6 擊殺；`DEF-431` 8 個（擊殺 5、存活 3 為防禦縱深）；前端 16 個（擊殺 15、存活 1）；所有突變都以逐位元組核對還原，並確認沒有突變標記殘留 |
| 沒有執行的項目 | 本輪沒有改 schema、遷移，所以 `make validate-schema-doc` 不在範圍（沒有執行） |

## 6. 過程中的失誤與修正

1. **並行呼叫共用工作目錄**：同一回合同時發出『後端打包』與『前端建置』兩個呼叫，第二個的 `cd` 沒有生效，`npm run build` 跑在 `backend/`，失敗（`ENOENT … backend/package.json`）。後端 JAR 已成功；前端單獨重跑。
2. **前端突變驅動第一次 7 批全部 `INVALID(no tests ran)`**：驅動從 Python 子行程啟動 E2E 腳本，Python 的語系強制轉換讓子行程帶著 `LC_CTYPE=C.UTF-8`，macOS `/bin/bash` 3.2 把腳本第 87 行 `$PG_PORT、`（變數後接全形頓號）併成變數名，`unbound variable`，整個堆疊沒起來。驅動的防呆（通過與失敗都是 0 就判 `INVALID`）擋住了，沒有被誤當成『突變存活』；用最小例子重現（錯誤訊息逐字相同），驗證修法（子行程改 `LC_ALL=C`）後重跑。**第一次驗證修法的檢查本身是無效的**（未加引號的 heredoc 被外層 shell 先把變數展開成空字串），改用檔案方式重做才算數。
3. **`DEF-431` 第一輪突變 Y5、Y6 存活，我預測 Y6 會紅**。原因：同時進來的請求主要死在日曆的唯一約束（探針 31／40），晚到者的兩道防線（可用性檢查、日曆列『已被別筆訂走』）很少被走到——不是測試寫壞，是它對這兩層不敏感。處置：IT-M06-104 每一輪並行之後補一個確定性的晚到請求，重跑突變（第 4 節）。
4. **兩處我『先寫成事實、後來才發現沒量過』**：①文件的『畫面不會觸發這種請求』沿用追蹤表的讀碼，讀 `Switch` 後更正；②『`GET` 回 `""`』我先用占位符 `PENDING-MEASURE` 標記，探針量到才寫入（提交前 grep 確認占位符已清掉）。
5. **回報中途把探針輪數說成 280**，重算是 20＋20＋100＋100＝**240**（真實鎖 120、鎖 mock 120）。
6. **暫放檔案**：丟棄式探針 `ZzConcurrencyProbeIntegrationTest.java`（含編譯產物）與 `zz-probe-staff-edit.spec.ts` 用完即刪，`git status` 確認沒有殘留。

## 7. 限制（誠實揭露）

1. **並行測試是不變量檢查，不是窮舉交錯**：每個測試 6 輪 x 3 個執行緒，探針 240 輪零偏離，但那不是證明。測試設計成任何偏離（500、雙重預訂）都紅燈，所以罕見的交錯若存在，會以偶發紅燈出現——目前沒有觀察到。
2. **沒測『拿掉資料庫唯一約束本身』**：測試庫的 `ddl-auto: update` 會保留舊約束，拿掉實體上的 `@UniqueConstraint` 並不會真的拿掉約束（突變會因為錯誤的原因存活），所以改用程式碼層的突變（唯一約束衝突的錯誤對應被改掉、`catch` 不匹配）。
3. **Redis 鎖層單獨移除是防禦縱深**：突變 Y2（取鎖失敗也當作成功）兩個新測試都不會紅——它們守的是『不變量』，不是『每一層都存在』。
4. **前端 `isOwner` 條件沒有案例覆蓋**（突變 B7 存活）：E2E 情境裡沒有『角色合格但登入租戶不是這間店鋪』的使用者。
5. **E2E 沒有覆蓋 `ADMIN` 角色**（只有 `STORE_OWNER`、`STORE_STAFF`）；『功能開關頁會把非店主導回列表』是讀碼（`features/page.tsx` 第 66～69 行），沒有 E2E 直接驗證，NAV-02 驗證的是『入口不顯示』。
6. **`maxlength` 只驗屬性存在**，沒有驗瀏覽器真的截斷輸入；也沒有前端字數驗證（以 `maxLength` 為唯一限制，後端 `@Size(max=1000)` 兜底）。
7. **店鋪詳情頁不顯示描述**（不在這四個項目內）。
8. **前端沒有單元測試**，所有前端驗證都是 Playwright 對真實後端（打包 JAR＋PostgreSQL＋Redis）的 E2E。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『功能開關』入口多加了角色條件（`STORE_OWNER`／`ADMIN`），比追蹤表原建議多一個，避免替店員新增死路。
2. 店鋪描述『改過才送出』；申請表單『留空不送』。
3. **刪除**編輯頁的死碼分支，而不是改成 `STORE_OWNER`（突變證明『修正』會把店主擋在表單外）。
4. 申請表單也加了描述（追蹤表括號提到；FRD AC-M17-001-1）。
5. `enabled=false` 一律 `DISABLED`，不新增狀態值。
6. 兩個並行測試都保留（各守不同層）；IT-M06-104 改寫而不是刪除，並加上確定性的晚到請求。
7. 描述欄位不加字數計數器與前端長度驗證。
8. 新發現的缺口只登記、不順手修：`DEF-437`（IT-M02-007 名稱與斷言不一致）、`DEF-438`（店員看得到『編輯店鋪』）。
9. `API_M17` §4.4 的一行（店員回 `E-1007`）因為有直接量測所以更正；其他端點的同類句子沒量，不動。

## 9. 新登記的缺陷

- **`DEF-437`**：`BookingIntegrationTest` 的 IT-M02-007（DisplayName『預訂衝突-Redis鎖防範雙重預訂』）把服務整個 mock 掉並斷言 `201`，名稱承諾的是衝突，斷言的卻是成功（讀碼，沒有執行）。測試衛生，不需產品決策。
- **`DEF-438`**：`STORE_STAFF` 在店鋪詳情頁看得到『編輯店鋪』，按儲存得 `403 E-1007`（探針實測）；`API_M17` §4.6／§4.8／§4.9／§4.13／§4.14 對一般成員寫 `E-4031` 的句子沒有量測。修法不需產品決策。

## 10. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 314 列。本輪已 commit，**尚未 push**：含生產程式碼（功能開關的回應語意、前端表單與導覽），不在 2026-07-08 的明確 push 授權範圍；收尾以互動選項詢問使用者要不要 push，以及 `DEF-432`③ 怎麼處理。本機同時領先遠端的還有 Sprint 313 的純文件回填 commit `9301f056`，若使用者選 push 會一起帶出。

本輪的 commit 範圍：生產程式 `TenantService`（後端）與 `TenantApplyForm`、`TenantEditForm`、`TenantDetail`、儀表板首頁、`edit/page.tsx`（前端）；測試 `TenantServiceTest`、`FeatureRequestLifecycleIntegrationTest`、`BookingApiRealStackIntegrationTest`、`BookingControllerE2ETest`，以及 E2E 規格 `at-dashboard-tenant-nav-real`（新增）、`at-m17-001`、`at-m17-003`、`at-m17-004`；文件 `API_M17_Tenant.md`（v2.6）、`DEFERRED_ITEMS_TRACKER.md`、`RELEASE_TRACKER.md` 與本計畫書。沒有 Flyway 遷移。

**待使用者處理**：`DEF-419`、`DEF-432`③（『編輯店鋪』是否放寬到非 `ACTIVE`）、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-438`（`STORE_STAFF` 看得到『編輯店鋪』，並以 `STORE_STAFF` 重新量測 `API_M17` 其他端點的錯誤碼）、`DEF-437`（IT-M02-007 改名或刪除）。
