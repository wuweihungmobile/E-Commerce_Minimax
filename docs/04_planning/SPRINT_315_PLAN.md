# Sprint 315 Plan — DEF-432③（『編輯店鋪』放寬到所有非 ACTIVE 狀態）

## 1. 起點與範圍

### 1.1 起點

[Sprint 314](SPRINT_314_PLAN.md) 收尾，我用 `AskUserQuestion` 問了兩題。以下是工具回傳的**真實互動答案**（不是我對「請繼續」的推論）：

| 題 | 使用者的選擇 | 授權範圍 |
|---|---|---|
| 要現在 push Sprint 314 嗎 | 現在 push（建議） | `cace4b8a`（連同上輪留在本機的純文件回填 `9301f056`）→ 已於 `da5e366f..cace4b8a` push，pre-push 輕量守門通過；雲端 CI run [40879538243](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40879538243) 三個 job 皆 success（13 分 41 秒），下載整合 job 的完整雲端日誌核對整合測試 **971／0 失敗／0 錯誤／0 跳過** |
| `DEF-432`③：『編輯店鋪』要不要放寬到非 `ACTIVE` 的店鋪 | **放寬到所有非 ACTIVE 狀態（照 FRD）**——不是我建議的『維持現狀』 | 選項說明列出的改動範圍：①後端 `TenantService.getTenantDetails`（非 `ACTIVE` 的精簡回應）對該店鋪的有效成員回完整資料，非成員仍回精簡版；②前端 `TenantDetail.tsx`『編輯店鋪』拿掉 `ACTIVE` 條件（『功能開關』仍只給 `ACTIVE`）；③後端 `PUT` 不用改；④測試：單元（成員／非成員 x 各狀態）、真實棧、E2E（停權店鋪的店主能改名稱）；⑤文件：`API_M17` §4.3 |

使用者在作答後緊接著補了一句**長期授權**：「請依照慣例，做完一輪後，直接Commit + Push to main，請記住！」——所以本輪 commit 後直接 push，不再詢問（已寫入記憶）。

範圍：`DEF-432`③。不碰 `DEF-438`（店員看得到『編輯店鋪』）、`DEF-437`、`DEF-419`、舊規則資料遷移。

### 1.2 成功標準（開工時定義）

1. 該店鋪的有效成員（店主、店員）對任何狀態的店鋪都拿到完整資料；非成員（匿名、陌生的已登入者、已移除、僅受邀）在非 `ACTIVE` 時仍是精簡版；`ACTIVE` 的行為不變。
2. 店主在每一種非 `ACTIVE` 狀態都能改店鋪資料；非店主仍然不行（放寬的是讀取，不是寫入）；改資料不會改變店鋪狀態。
3. 前端『編輯店鋪』不看店鋪狀態；『功能開關』仍只給 `ACTIVE`。
4. 紅燈先行；每個新增的判斷都經突變驗證；全量後端 `mvn verify` 與完整 E2E 通過；文件同步。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法（出處） | 核對結果 |
|---|---|
| 選項說明：『`TenantService.updateTenant` 本來就不檢查店鋪狀態，限制只在前端按鈕』 | **屬實**。讀碼無任何狀態檢查；結果表實測（修復前就綠）：店主在 4 個非 `ACTIVE` 狀態的 `PUT` 都是 `200` 且名稱已改。 |
| 選項說明：『`GET` 對非 `ACTIVE` 只回精簡資料（沒有聯絡信箱、電話、描述）』 | **屬實**。修復前實測：停權店鋪的店主 `GET` 的 `contactEmail` 是 `null`（E2E 前提斷言的失敗訊息）。 |
| 選項說明：『單純放開按鈕會讓編輯表單載入後送出時出錯（讀碼推論，沒實測）』 | **仍是推論，本輪沒有量到**：兩層紅燈裡沒有『只修前端、後端沒修』的組合，所以沒量到表單的實際行為。結論不影響本輪（兩邊都修）。 |
| 停權店鋪的店主登入後還拿得到店鋪租戶（`isOwner` 才會成立） | 讀碼：`AuthService.resolveTenantForUser` 不看店鋪狀態。**E2E 實測**：停權後店主登入、`GET` 完整資料、『編輯店鋪』出現、儲存成功（`E2E-M17-004-05`）。 |
| 『放寬編輯』會不會讓停權店鋪的店主自己把店鋪改回 `ACTIVE`（`TenantUpdateRequest` 有選填的 `status` 欄位） | 讀碼：`updateTenant`／`applyTenantUpdates` 完全不讀 `status`（死欄位）。**實測**：整合測試的 `PUT` 結果表每次請求都帶 `status: ACTIVE`（4 種非 `ACTIVE` 狀態 x 6 種觀看者），資料庫的店鋪狀態一格都沒變；突變 X8（讓 `PUT` 套用請求的 `status`）被這張表擊殺。所以放寬編輯不會讓店主自行復權。 |
| 『有效成員』是否包含店員 | 選項說明寫的是『有效成員』；我解讀為 `tenant_members.status = ACTIVE` 的所有成員（店主、店員）——與 `ACTIVE` 店鋪現有的門檻可見度規則（`DEF-264`）同一個判斷。這是我的解讀，見第 8 節。 |

## 2. 做法

### 2.1 後端：`TenantService.getTenantDetails`

把 `DEF-264` 的成員判斷（`existsByTenantIdAndUserIdAndStatus(…, ACTIVE)`，匿名不查）提到精簡回應之前；精簡回應的條件從 `!isApproved` 改成 `!isApproved && !isTenantMember`。`ACTIVE` 店鋪的路徑與回應完全不變，採購審批門檻仍只對有效成員可見。`PUT` 不改。

### 2.2 前端：`TenantDetail.tsx`

『編輯店鋪』的顯示條件從 `isOwner && ACTIVE` 改成 `isOwner`；『功能開關』按鈕維持 `isOwner && ACTIVE && 角色為 STORE_OWNER／ADMIN`。

## 3. 測試

後端（紅燈先行，對修復前版本實測）：

| 檔案 | 變更 |
|---|---|
| `TenantServiceTest` | +12（3 個參數化案例 x 4 個非 `ACTIVE` 狀態）：有效成員→完整資訊（含門檻與店主資訊）；已登入非成員→精簡版且成員檢查要求 `ACTIVE`；匿名→精簡版且不做成員查詢。修復前 8 個紅：4 個成員案例（`expected: <Full description here> but was: <null>`）＋4 個『已登入非成員』（它們斷言成員檢查帶 `ACTIVE`，修復前精簡回應之前根本沒查）；匿名的 4 個是修復前就綠的對照 |
| `TenantDetailsNonActiveIntegrationTest`（新增，真實 PostgreSQL／Redis／HTTP 鏈） | 3 案例：①狀態 x 觀看者結果表（5 種狀態 x 6 種觀看者＝30 格；觀看者含匿名、陌生人、已移除成員、僅受邀成員、店員、店主），修復前恰好 8 格不符（4 個非 `ACTIVE` 狀態的店員與店主：預期完整版與門檻、實際精簡版）；②停權店鋪的店主看到表單要預填的欄位；③PUT 結果表：店主在 4 個非 `ACTIVE` 狀態都能改名稱，其餘觀看者 4xx 且資料不變（修復前就綠，是『放寬的是讀取』的對照） |

前端 E2E（真實後端）：

| 規格 | 變更 |
|---|---|
| `at-m17-004.spec.ts` | 新增 `05`：店鋪被停權後店主走完整個編輯流程（前提斷言後端回完整資料 → 表單預填 → 改名稱 → 儲存 → 詳情頁顯示新名稱且仍是停權 → 後端讀回）；`openEditForm` 點擊前加可見性斷言（缺入口時幾秒內就紅，不等 90 秒逾時） |
| `at-dashboard-tenant-nav-real.spec.ts` | `NAV-03` 加『編輯店鋪』的正對照：停權店鋪沒有『功能開關』是它自己的 `ACTIVE` 條件，不是整排按鈕沒渲染 |

**兩層紅燈（E2E）**：先對 Sprint 314 的建置（後端、前端都未修）跑 → `E2E-M17-004-05` 死在後端前提（`contactEmail` 是 `null`）、`NAV-03` 找不到『編輯店鋪』；只重建修好的後端 JAR 再跑 → `004-05` 通過前提、改死在前端點擊（`NAV-03` 仍紅）；再修前端 → 兩者皆綠，該兩個規格 8／8。

## 4. 突變驗證

做法沿用 [Sprint 314](SPRINT_314_PLAN.md) 第 4 節（驅動腳本放在工作階段的暫存目錄，不進 repo；`old` 必須恰好出現一次，先乾跑；逐位元組還原並核對；失敗集合從 surefire／failsafe 的 XML 取）。因為參數化案例在 XML 裡的案例名不好預測，這輪新增「這些案例名子字串都必須出現在失敗集合」的比對（`must_sub`），並把每個突變實際擊殺的案例全部列出。

### 4.1 後端（基準 Z0：89 案例〔單元 86＋整合 3〕、0 失敗；全部擊殺）

| # | 突變 | 擊殺的案例 |
|---|---|---|
| X1 | 成員被當成非成員（精簡回應的條件改回 `!isApproved`，等於修復前） | 單元 4（各狀態的成員案例）＋整合 2（結果表、停權店鋪的店主預填欄位） |
| X2 | 所有人都拿完整版（精簡回應條件加 `&& false`） | 單元 9（匿名 4、已登入非成員 4、既有的 `PENDING_REVIEW` 精簡版案例 1）＋整合 1（結果表） |
| X3 | 僅受邀（`INVITED`）被當成有效成員（成員檢查的狀態參數改成 `INVITED`） | 單元 9（成員 4、已登入非成員 4，另有既有的 `ACTIVE` 成員案例 1）＋整合 2（結果表、預填欄位） |
| X4 | 已移除（`REMOVED`）被當成有效成員 | 與 X3 相同的 11 個案例 |
| X5 | 採購審批門檻外洩給非成員 | 整合 1（結果表：`ACTIVE` 店鋪的陌生人看到 `full+threshold`）＋既有的 `DEF-264` 單元案例 1 |
| X7 | 寫入對所有人開放（拿掉 `updateTenant` 的店主檢查，並把 `@PreAuthorize` 改成 `"true"`） | 整合 1（`PUT` 結果表）＋既有單元案例 `updateTenant_staffRole_shouldThrowException` |
| X8 | `PUT` 套用請求的 `status`（店主自行復權） | 整合 1（`PUT` 結果表；這個斷言是在第一輪突變之後補的，所以單獨再跑一輪：基準 3／3 綠、突變擊殺） |

X3、X4 兩個突變證明單元案例對『成員檢查要帶 `ACTIVE`』是有牙齒的（成員案例的 stub 與『已登入非成員』案例的 `verify` 都綁 `ACTIVE`）。整合結果表的『已移除』『僅受邀』兩種觀看者，邏輯上分別會被 X4、X3 變成完整版而抓到；**我沒有逐格檢視這兩次的不符清單**，這句是推論。

### 4.2 前端（真實全棧，1 批 1 個突變；基準：該兩個規格 8／8）

| 批 | 突變 | 擊殺案例與失敗訊息 |
|---|---|---|
| G1 | 『編輯店鋪』加回 `ACTIVE` 條件 | `NAV-03`：`getByRole('link', { name: '編輯店鋪' })` 預期可見、5 秒內找不到；`M17-004-05`：同一個入口在 `openEditForm` 的可見性斷言上 5 秒內紅（Sprint 314 補的快速失敗斷言在這裡派上用場，原本會等 90 秒逾時） |

沒有再做『拿掉 `isOwner` 條件』的突變：它與 Sprint 314 的 B7 是同一個條件，同一個限制（E2E 情境裡沒有『角色合格但登入租戶不是這間店鋪』的使用者），結論不變，見第 7 節。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| Sprint 314 雲端 CI（`cace4b8a`） | run [40879538243](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40879538243) 三個 job 皆 success（Backend Unit Tests 2 分 53 秒／Frontend Lint & Build 53 秒／Backend Integration Tests & Package 10 分 42 秒，整個 run 13 分 41 秒）；`gh run view --json status,conclusion,headSha` 交叉確認 headSha 為 `cace4b8a`；下載整合 job 的完整雲端日誌（約 57 MB）確認整合測試 **971／0 失敗／0 錯誤／0 跳過**、`BUILD SUCCESS`、`<<< FAILURE`／`<<< ERROR` 標記 0 個 |
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**22 分 53 秒**；單元 **2433／0 失敗**（2421＋本輪 12：`TenantServiceTest` 3 個參數化案例 x 4 個非 `ACTIVE` 狀態）；整合 **974／0 失敗**（971＋本輪 3：`TenantDetailsNonActiveIntegrationTest`）；Checkstyle main／test 都是 0 違規；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個。受影響的既有類別全過：`TenantControllerE2ETest` 14、`FeatureRequestLifecycleIntegrationTest` 7、`BookingApiRealStackIntegrationTest` 18 |
| 完整 E2E 套件（`scripts/validate-e2e.sh`，重用已建置的 JAR 與前端） | **294 通過／0 跳過／0 失敗**（293＋本輪 1：`E2E-M17-004-05`），7.3 分鐘；後端以 `ddl-auto=validate` 對 Flyway 從零重建的資料庫啟動成功（entity 與 schema 無漂移）；認證端點被限流 23 次（E2E 輔助函式依 `Retry-After` 重試，全數自癒）；日誌中跳過、flaky、did not run 的標記皆 0 個 |
| 這次動到的 2 個 E2E 規格（修復後） | 8／8 通過（31.8 秒）；完整套件內同樣通過 |
| `tsc --noEmit`（全專案，含 `e2e/`）、`npm run lint`（`eslint --max-warnings 0`，全專案） | 退出碼都是 0（不經管線取得） |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**，`BUILD SUCCESS` |
| IDE Problems | 53 個檔案條目、診斷 **0**；本輪改過的 `TenantService`、`TenantServiceTest`、`TenantDetailsNonActiveIntegrationTest` 都在條目內且 0 診斷（`.tsx` 由 `tsc`／ESLint 把關） |
| 突變驗證 | 後端 7 個全擊殺（`mutate.py` 兩輪：X1～X5、X7，另補 X8）；前端 1 個擊殺；所有突變都以逐位元組核對還原，並確認 `backend/src/main` 與 `frontend/src` 只剩本輪預期的變更 |
| 沒有執行的項目 | 本輪沒有改 schema、遷移，所以 `make validate-schema-doc` 不在範圍（沒有執行） |

## 6. 過程中的失誤與修正

1. **預測錯誤**：我預測『已登入非成員』的單元案例在修復前就是綠的（當對照組），實際 4 個也紅了——它們斷言『成員檢查必須帶 `ACTIVE`』，而修復前在回精簡版之前根本沒查成員（`verify` 失敗，stub 也沒被用到）。這是對的原因，保留；真正「修復前就綠」的對照是匿名的 4 個案例和整合測試的 `PUT` 結果表。
2. **追蹤表編輯半途不一致**：`DEF-432` 的三個編輯，前兩個套用了、第三個因為原文是『放寬到非 ACTIVE』（沒有反引號）沒命中，一度處於『抬頭說已完成、尾巴還說待裁決』的矛盾狀態；讀出該行確切尾端後補上，並檢查殘留字樣（『仍待你裁決』『部分完成』『待你裁決』『⚠️』各 0 次）。
3. **IDE 暫時性診斷**：改 `getTenantDetails` 時我先刪後加兩個編輯，兩者之間 IDE 回報 `isTenantMember` 找不到變數——是中間狀態，兩個編輯完成後以實際編譯與測試為準（86／86 通過）。
4. **`status: ACTIVE` 斷言是第一輪突變之後才補的**：寫文件時想到 `TenantUpdateRequest` 有選填的 `status` 欄位，而 `PUT` 又不檢查店鋪狀態——先讀碼確認後端不讀它，再補成實測（整合測試的 `PUT` 結果表每次都帶 `status: ACTIVE`），所以突變 X8 單獨再跑一輪。
5. **前端突變驅動用子 shell 的 `&` 啟動**，不像其他背景工作會有完成通知；我自己讀日誌確認進度，沒有漏看結果。

## 7. 限制（誠實揭露）

1. **『有效成員』包含店員**：非 `ACTIVE` 店鋪的店員也拿到完整資料，包含內部的採購審批門檻——與 `ACTIVE` 店鋪既有的規則（`DEF-264`）一致，但這是我對選項說明『有效成員』的解讀（第 8 節）。
2. **`DEF-438` 的影響面被這次放寬擴大**：『編輯店鋪』沒有角色條件（只看 `isOwner`＝登入租戶是這間店鋪），所以店員在**非 `ACTIVE`** 店鋪的詳情頁也會看到它，表單載得進來（成員拿得到完整資料），按儲存才得 `403 E-1007`。修復前店員在非 `ACTIVE` 店鋪看不到這個按鈕（被 `ACTIVE` 條件擋住）。沒有修（`DEF-438` 另案，不在本輪範圍），已補記在追蹤表。
3. **已終止（`TERMINATED`）的店鋪也放寬了**：這是使用者『所有非 ACTIVE 狀態』的字面結果。若要排除已終止，是前後端各一行條件。
4. **E2E 只驗證 `SUSPENDED`**：管理員 API 能製造的狀態；`PENDING_REVIEW`、`REJECTED` 在正式環境不可達（舊流程），`TERMINATED` 的終止條件沒有在 E2E 裡製造。這四種狀態在單元層（參數化）與整合層（直接改資料庫狀態）都驗證了。
5. **沒有量到『只修前端、後端沒修』的組合**（選項說明裡『編輯表單載入後送出時出錯』的推論）：兩層紅燈的順序是『都沒修 → 只修後端 → 都修』。
6. **前端 `isOwner` 條件仍沒有案例覆蓋**（同 Sprint 314 的限制 4）。

## 8. 我替使用者做的決定（可一句話推翻）

1. 選項說明的『有效成員』＝ `tenant_members.status = ACTIVE` 的所有成員（店主與店員），不是只有店主。
2. 『編輯店鋪』不加角色條件（維持既有的 `isOwner`），所以店員也看得到——`DEF-438` 另案處理，不在本輪順手改。
3. 『功能開關』仍只給 `ACTIVE`（選項說明明寫）。
4. 已終止（`TERMINATED`）也放寬（字面照 FRD『非 ACTIVE』）。
5. 在整合測試補上『請求帶 `status: ACTIVE` 不能自行復權』的斷言——超出選項說明，但它是『放寬編輯』最直接的風險，而且實測不需要改任何正式碼。

## 9. 新登記的缺陷

本輪沒有新登記的缺陷。`DEF-438` 的症狀因為本輪放寬而延伸到非 `ACTIVE` 的店鋪（見限制 2），已補記在追蹤表的 `DEF-438`。

## 10. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 315 列。本輪的 commit `12604d70` 已 push（`cace4b8a..12604d70`），依使用者 2026-10-29 的長期授權（『做完一輪後，直接Commit + Push to main』）直接 push，沒有再詢問；pre-push 輕量守門通過，雲端 CI run [40893210008](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40893210008) 三個 job 皆 success（Backend Unit Tests 2 分 54 秒／Frontend Lint & Build 56 秒／Backend Integration Tests & Package 10 分 57 秒，整個 run 13 分 56 秒）；`gh run view --json status,conclusion,headSha` 交叉確認 headSha 為 `12604d70`；下載整合 job 的完整雲端日誌（約 58 MB）確認整合測試 **974／0 失敗／0 錯誤／0 跳過**、`BUILD SUCCESS`，`TenantDetailsNonActiveIntegrationTest` 3、`TenantControllerE2ETest` 14、`FeatureRequestLifecycleIntegrationTest` 7、`BookingApiRealStackIntegrationTest` 18、`BookingControllerE2ETest` 25 與本機一致。

本輪的 commit 範圍：生產程式 `TenantService`（後端）與 `TenantDetail.tsx`（前端）；測試 `TenantServiceTest`、`TenantDetailsNonActiveIntegrationTest`（新增）、E2E 規格 `at-m17-004`、`at-dashboard-tenant-nav-real`；文件 `API_M17_Tenant.md`（v2.7）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-432` 全部完成、`DEF-438` 補記）、`RELEASE_TRACKER.md`（含 Sprint 314 的 push 與雲端 CI 回填）、`SPRINT_314_PLAN.md`（回填）與本計畫書。沒有 Flyway 遷移。

**待使用者處理**：`DEF-419`、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-438`（`STORE_STAFF` 看得到『編輯店鋪』——本輪放寬後影響面更大，建議優先——並以 `STORE_STAFF` 重新量測 `API_M17` 其他端點的錯誤碼）、`DEF-437`（IT-M02-007 改名或刪除）。
