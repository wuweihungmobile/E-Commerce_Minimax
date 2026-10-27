# Sprint 308 Plan — DEF-423 整條鏈實測、登記 DEF-424／DEF-425、逐項關閉 Sprint 307 的限制（使用者貼回 Sprint 307 總結並說「請繼續完成任務」，沒有 `==>`）

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 307](SPRINT_307_PLAN.md) 的完成總結，結尾只有一句「請繼續完成任務！」，**沒有任何 `==>` 回覆**。

我的解讀（推論，寫下來讓使用者能糾正）：

1. **這是「無 `==>`」變體的第四個實例**：不是對任何「等你決定」項目的授權。Sprint 307 總結沒有等使用者決定的項目；使用者端待辦是 Sprint 306 留下的 `make down-clean` → `make up` → `make dev-admin-password`（只有使用者能做，本輪沒有碰他的開發資料庫與具名卷），以及 `DEF-419`（運費模板的同型缺口，追蹤表標明屬權限模型決策）。
2. **做的是總結「限制（誠實揭露）」裡 AI 能獨立實測或核對的項目**：`DEF-423` 的影響只實測到 `effective-price` 端點、購物車的開關語意「沒有追」、`users.role` 沒有實測、E2E 4 個跳過沒有核對、全量驗證變慢沒查原因。
3. **不碰 `DEF-419`；不改任何生產程式碼**。發現「需要產品決策」的只登記，不替使用者決定。
4. 開工前先自己核對上一輪的「雲端 CI 全綠」，不直接採信總結（§1.3 第一列）。

### 1.2 成功標準（開工時定義）

1. `DEF-423`：把 Sprint 307 只量到 `effective-price` 的影響，量到**消費者實際被收的錢**（購物車 → 訂單明細 → 訂單總額），而且在**真實 Redis** 上（後端整合測試的 Redis 是 mock）。修復前的數字必須是**量到的**，不是推論。
2. 購物車的動態定價開關語意：用實測弄清楚「看誰的開關」，不憑猜測。
3. Sprint 307 §6、§8 其餘能核對的限制逐項核對；不能核對的寫明原因。
4. 新增的測試必須通過突變驗證（會咬人）；完整 E2E 套件在乾淨原始碼上通過。
5. 文件與追蹤表同步，每個數字都能對回工具輸出。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 「雲端 CI 全綠」（Sprint 307 總結） | `gh run list`：run 40517176358（Local CI (act-compatible)，push，12m34s）對 `79302fcf` 為 `completed／success`。本機 `HEAD` 為 `835d71e0`，領先 `origin/main` 1 個 commit（Sprint 307 的純文件回填），工作樹乾淨；Docker daemon 在跑（29.9.1）、沒有殘留容器、8080／3000 空閒 |
| 「購物車的調整後單價就是收款依據」（Sprint 307 為讀碼推論） | 讀碼：`OrderService.buildProductOrder` 的 `unitPrice(cartItem.getUnitPrice())`、`subtotal(cartItem.getSubtotal())`，總額 `itemsTotal + shippingFee − discountAmount`，沒有再算一次價格；**實測**（§4）：規則價 800、原價 1000 的商品，購物車與訂單明細單價、訂單總額都是 800 |
| 「購物車的動態價格開關看的是呼叫者所在租戶的開關」 | 屬實：`FeatureToggleService.isFeatureEnabled` 讀 `TenantContext.getCurrentTenant()`；`RedisCartService.tryProductAdjustment` 呼叫它 |
| 「呼叫者所在租戶」實際是誰 | `RolePermissionMapping` 第 41～43 行：`CART_READ`／`CART_UPDATE`／`CART_DELETE` **只出現在 `BUYER`**（用 `awk` 逐角色列出驗證），`SUPER_ADMIN` 是 `EnumSet.allOf`；`STORE_OWNER`（第 95～157 行）、`STORE_STAFF`、`ADMIN` 都沒有。所以購物車使用者的租戶都是系統租戶佔位值，它的 `DYNAMIC_PRICING_ENABLED` 在 Flyway 種子是開啟（`V1__Initial_Schema.sql` 第 521 行、`V6__Tenant_Feature_Toggles_Fix.sql` 第 58 行） |
| 「`users.role` 有沒有 CHECK」（Sprint 307 只讀了 V1） | 全部遷移 `grep`：只有 V1 第 58 行的索引 `idx_users_role`，沒有任何 CHECK。`UserRole` 列舉 9 個值（`GUEST`、`BUYER`、`SELLER`、`HOST`、`STORE_OWNER`、`STORE_STAFF`、`ADMIN`、`SUPER_ADMIN`、`CFO`）；遷移裡 `INSERT INTO users` 寫入的 role 字面值只有 `STORE_OWNER`、`SUPER_ADMIN`、`BUYER`；唯一對 `users.role` 賦值的 `UPDATE` 是 V87（把沒有有效成員資格的 `STORE_STAFF` 改成 `BUYER`）。全在列舉內 |
| 「E2E 4 個跳過是不是 `at-m17-003`／`004`」（Sprint 307 沒核對） | **是**：完整套件以 list 報表跑，4 個 `-` 行逐一列出（§2.4） |
| 「全量 verify 28:16 比 19:28 慢，原因沒查」 | failsafe 報告還在；至少能回答『是不是新測試造成的』：不是（§2.5）。原因本身因缺 Sprint 306 的基準而查不出 |

## 2. 發現

### 2.1 `DEF-423` 的完整影響（量到的）

在 `PricingService.createRule` 拿掉 `listingId` 的擁有權檢查（突變 M1，對「兩個 id 並存」的請求等同修復前），以新的 Playwright 規格在打包 JAR ＋ PostgreSQL ＋ Redis 上走一遍。店鋪 A 帶**自己的房源 id**、夾帶**店鋪 B 的商品 id**（原價 1000）建立 `MANUAL_OVERRIDE`、`price=1` 的規則；之後一位**全新註冊的消費者**把店鋪 B 的商品放進購物車並下單：

| 步驟 | 修復後（現行程式） | 拿掉檢查（≈ 修復前） |
|---|---|---|
| 夾帶請求 | 403 `E-1007` | **201**（規則建在店鋪 A 名下、掛到店鋪 B 的商品上；回應沒有 `code`） |
| 消費者購物車的商品單價 | 1000 | **1**（`appliedRuleName` 是夾帶的那條規則） |
| 購物車總額 | 1000 | **1** |
| 訂單明細單價 | 1000 | **1** |
| 訂單總額（店鋪 B 沒有運費模板，運費 0） | 1000 | **1** |

訂單歸屬店鋪 B（規格的 `order.tenantId` 斷言在突變下仍通過）。也就是說修復前，**任何開了動態定價的店鋪，能讓別家店的商品對所有消費者變成任意價**（`MANUAL_OVERRIDE` 的 `price` 只要大於 0），而且那家店不需要申請過動態定價——規格在 `E2E-PCART-02` 之前從不替店鋪 B 開啟開關，它是開店核准後的預設值（PRD 預設為關閉；規格沒有另外斷言這個前提）。原因是消費者購物車查的是系統租戶的開關（§2.2），不是被改價那家店的。

邊界（誠實揭露）：①M1 是「整行拿掉」。對「兩個 id 並存」的請求等同修復前；對「只帶 `listingId`」的請求則比修復前更寬鬆（修復前會驗證 `listingId`），本輪的證據只用前者。②訂單只量到**建立為止**；付款金額取自訂單總額是讀碼推論，沒有跑到付款。

### 2.2 動態定價開關的語意（`DEF-424`，待使用者裁決）

> **後續（Sprint 309）**：本輪收尾我用互動選項問了這題，使用者選 (B)『關閉後立即失效』，已於 [Sprint 309](SPRINT_309_PLAN.md) 實作與驗證。下面保留 Sprint 308 當時的分析。

- 店鋪層的 `DYNAMIC_PRICING_ENABLED` 只管**建立規則**（`createRule` 的第一行）。套用到購物車時查的是**呼叫者租戶**的開關；購物車使用者都是系統租戶，其開關在種子是開啟，所以它實質上是全平台的「套用」總開關。
- **實測**（`E2E-PCART-03`）：管理員關閉店鋪 B 的開關後，B 建立新規則回 403 `E-2004`（開關對「建立」有效），但 B 既有的規則仍套用在全新消費者的購物車（單價仍是規則價 800，不是原價 1000）。
- **這不違反規格**：PRD 權限矩陣（`E-Commerce_PRD_v1.0_Final.md` 第 1283 行）寫的是「Service 層在建規則前查詢」；`API_M12` §1.2 早已把「只擋新建規則」寫成現況。PRD 沒有定義「撤銷開關後既有規則是否繼續套用」。**我一度把它當缺陷，讀到這兩處之後改成「待裁決的產品語意」**（§7 第 3 點）。
- **我原本的另一個假設不成立**：以為「店主當買家，價格會因為自己店鋪的開關而不同」。讀 `RolePermissionMapping` 後發現店主根本沒有購物車權限，這個面向不存在，已從規格移除（§7 第 2 點）。
- 選項與建議見追蹤表 `DEF-424`：(A) 維持現狀並明文化；(B) **建議**——套用端改看商品所屬店鋪的開關（`isFeatureEnabledForTenant(listing.getTenantId(), …)`，該方法已存在）；(C) 撤銷時順便停用既有規則。我用突變 M4 實驗了 (B) 的購物車側（不提交）：`E2E-PCART-03` 轉紅（預期 800、實際 1000），`E2E-PCART-01`／`02` 維持綠燈。但訂房側 `BookingService` 兩處呼叫也要一併改才一致，而且「平台管理員替店鋪商品建立的規則（規則的租戶是系統租戶）要不要受店鋪開關約束」需要一併裁決，所以**沒有擅自改**——它會改變消費者實際被收的價格。

### 2.3 `users.role`（關閉 Sprint 307 §8 第 2 點）

結論：**沒有缺陷，不處理**。沒有 CHECK 是事實（§1.3），所以資料庫會接受任何 50 字內的字串；但遷移寫入的值（`INSERT` 與唯一的 `UPDATE`，見 §1.3）全在列舉內，目前沒有任何來源能寫進列舉外的值。它和 `DEF-422` 是同一類潛伏風險（列舉外的值會讓載入該列的查詢丟 `IllegalArgumentException`），要出現得靠手動改庫或日後移除列舉值。沒有做「直接 `INSERT` 一列壞值」的實測：沒有 CHECK 已經回答了『資料庫會不會擋』，剩下的後果與 `DEF-422`（`tenants.status`）已實測過的機制相同。

### 2.4 E2E 4 個跳過（`DEF-425`）

完整套件（285 案例）：**281 通過／4 跳過／0 失敗**。4 個跳過逐一列出，正好是兩個檔案的**全部**案例：

| # | 案例 |
|---|---|
| 1 | `at-m17-003.spec.ts` › AT-M17-003: Feature Toggle 更新 › Feature Toggle 開關切換 |
| 2 | `at-m17-003.spec.ts` › AT-M17-003: Feature Toggle 更新 › Feature Toggle 數值更新 |
| 3 | `at-m17-004.spec.ts` › AT-M17-004: 店鋪 Profile 更新 › 店鋪 Profile 完整更新 |
| 4 | `at-m17-004.spec.ts` › AT-M17-004: 店鋪 Profile 更新 › 店鋪 Profile 部分更新 |

- **事實**：兩個規格用 `registerAndLogin` 新註冊的帳號（沒有店鋪）進 `/dashboard/tenants`；`TenantList` 對沒有店鋪的人顯示「尚無店鋪資料」（`TenantList.tsx` 第 173 行），規格是「看得到就做、否則 `test.skip()`」，所以每次都走到跳過。
- **推論（沒有驗證）**：換成有店鋪的帳號，選擇器可能也過期（列表頁卡片連到詳情頁、檔案裡沒有「編輯」字樣；功能開關頁的檔案沒有 checkbox／「編輯」字樣，元件可能拆在別處，我沒有追進去）。
- **涵蓋範圍**：API 層有（`TenantControllerE2ETest`）；UI 層沒有，`at-dashboard-tenant-features-real` 只鎖住 `DEF-359` 的徽章。
- **歷史**：Sprint 146 計畫書寫「at-m17-004 通過」並把當時的 4 個跳過歸因為購物車案例；Sprint 197、198、307 寫「4 個跳過是這兩個檔案的條件式 `test.skip()`，沒有核對」。我**沒有重建**「何時開始永遠跳過」的時間線。
- 處理：登記 `DEF-425`（測試衛生，不需產品決策），建議改寫成 `-real` 規格並拿掉條件式跳過。**本輪不重寫**——要對真實 UI 重做選擇器，值得獨立一輪，且「數值更新」該測什麼可能牽涉判斷。

### 2.5 全量驗證變慢（關閉 Sprint 307 §6 的「沒查」）

failsafe 報告（129 個類別、948 案例）：類別時間加總 21.5 分，報告檔的時間跨度 21:05:19～21:25:58＝**至少 20.6 分**——整合階段單獨就比 Sprint 306 整個 verify（19:28）長。Sprint 307 新增的兩個類別：`PlatformStatsCorruptRowFlywayIntegrationTest` 15.8 秒（第 25／129 名）、`PricingRuleScopeIsolationE2ETest` 4.2 秒（第 83／129 名），合計約 20 秒，**約佔整合階段的 1.6%**。類別時間中位數 8.8 秒，≥ 30 秒的有 6 個。結論：**不是新測試造成的**。真正原因查不出：Sprint 306 的逐類別報告已被覆蓋，沒有基準可比；單元階段的報告也被文件守門補跑覆蓋（現在只剩 4 個類別），沒有單元階段的耗時。可能是環境（開工時 Docker daemon 剛啟動、機器負載），但那只是猜測。

## 3. 刻意沒做的事

| 項目 | 為什麼沒做 |
|---|---|
| 修 `DEF-424` | 產品語意待裁決，且修法改變消費者實際被收的價格（§2.2） |
| 重寫 `at-m17-003`／`004`（`DEF-425`） | 要對真實 UI 重做選擇器，值得獨立一輪（§2.4） |
| `DEF-419` | 追蹤表標明屬權限模型決策，要使用者決定；本輪是無 `==>` 變體 |
| 50 條上限的錯誤訊息與常數名 `MAX_ACTIVE_RULES_PER_ROOM` | 維持 Sprint 307 決定 7：訊息是使用者可見文字，使用者沒給措辭 |
| 修改 PRD | 本輪沒有新的需求決定 |
| 雲端 e2e job 實跑 | 只能手動觸發，本地優先 CI 策略下以本機守門為準 |
| 後端程式與測試 | 沒有改動（`git status` 只有文件與新的 E2E 規格），所以沒有重跑全量 `mvn verify`；文件守門測試另行補跑（§6） |

## 4. 測試

新增 [`frontend/e2e/at-pricing-cart-checkout-real.spec.ts`](../../frontend/e2e/at-pricing-cart-checkout-real.spec.ts)（真實全棧：打包 JAR ＋ PostgreSQL ＋ Redis ＋ 兩家真實店鋪〔真實開店流程〕＋消費者；全程用 API 登入，不碰瀏覽器登入狀態）：

| 案例 | 內容 |
|---|---|
| `E2E-PCART-01` | 店鋪 A（HYBRID）帶自己房源 id、夾帶店鋪 B（開關維持開店核准後的預設值，PRD 預設關閉）的商品 id 建立規則 → 403 **`E-1007`**（不是功能開關的 `E-2004`）；全新消費者的購物車單價／總額、訂單明細單價／總額仍是原價 1000。狀態碼與所有金額斷言用 `expect.soft`：守門失效時仍會一路走到訂單，把整條鏈每一步的實際金額印出來（§2.1 的數字就是這樣量到的） |
| `E2E-PCART-02` | **對照組**：開啟店鋪 B 的開關，B 為自己的商品建立規則（價 800）→ 購物車單價 800、`originalUnitPrice` 1000、`discountAmount` 200、`appliedRuleName`、`priceAdjustmentType = DISCOUNT`、總額 800；下單後訂單明細單價、小計與總額都是 800。沒有它，01 的「原價」在規則根本不生效時也會是原價（空洞的斷言） |
| `E2E-PCART-03` | **現況記錄（`DEF-424` 待裁決）**：管理員關閉店鋪 B 的開關 → 新規則 403 `E-2004`，但既有規則仍套用在全新消費者的購物車（單價 800）。標題與斷言訊息都標明「現況」，裁決後要跟著改 |

為什麼 03 不用 `test.fail()`：這個專案用 `test.fail()` 描述「修好之後應有的行為」（`DEF-319` 時），前提是應有的行為有 PRD 依據；`DEF-424` 的應有行為還沒有人拍板，所以只記錄現況，不預設答案。

靜態檢查：`eslint --max-warnings 0` 退出碼 0、`tsc --noEmit` 退出碼 0（皆直接取真實退出碼，沒有接管線）。

## 5. 突變驗證

驅動見記憶 `mutation-validation-driver-pattern` 的精神（備份 → 套用 → 建置 → 跑 → 還原 → 雜湊比對），這次是 **JAR 層**：每個突變重新 `mvn package`，再起乾淨 PostgreSQL ＋ Redis ＋ 後端 ＋ 前端，只跑新規格。每個替換字串事先檢查在原始碼中**剛好出現一次**。

| 突變 | 做法 | 01 | 02 | 03 | 擊殺者與原因 |
|---|---|---|---|---|---|
| M1 | `createRule` 拿掉 `listingId` 的擁有權檢查（≈ `DEF-423` 修復前） | 失敗 | 未執行 | 未執行 | 01：夾帶回 201；購物車單價、購物車總額、訂單明細單價、訂單總額全是 1（§2.1） |
| M2 | 購物車完全不套用動態定價 | **通過** | 失敗 | 未執行 | 02：購物車是 1000，預期 800。01 仍綠＝證明 02 對照組的必要 |
| M3 | 訂單明細單價、小計與總額改用商品原價 | 通過 | 失敗 | 未執行 | 02：購物車斷言通過，訂單明細單價 1000，預期 800 |
| M4 | 購物車改看商品所屬店鋪的開關（`DEF-424` 的 (B)，**不提交**） | 通過 | 通過 | 失敗 | 03：1000，預期 800 |

**4 個突變全數擊殺，沒有存活。**「未執行」是 serial 模式下前一案例失敗所致，不是漏測；03 的鑑別力由 M4 單獨證明。每個突變 88～142 秒；四個來源檔（`PricingService.java`、`RedisCartService.java`、`OrderService.java`）還原後驅動比對 SHA-256 皆一致，我另用 `git status`／`git diff` 獨立核對，`backend/src` 沒有任何差異。`unresolved_compilation_hits` 全為 0（排除 IDE 語言服務搶寫出假的 class）。

## 6. 驗證總表

| 項目 | 結果 |
|---|---|
| 新規格單獨跑（第一次，完整建置） | 3 通過（18.3 秒），認證端點被限流 0 次 |
| 完整 E2E 套件（乾淨原始碼、完整建置：`mvn package`＋`npm ci`＋`next build`、2 個 worker） | **285 案例：281 通過／4 跳過／0 失敗**，8.6 分鐘；schema 對齊（backend 以 `ddl-auto=validate` 啟動成功）；「遷移後沒有測試資料，`admin@nextkey.local` 無法以已知密碼登入」✅；認證端點被限流 0 次；新規格三案例在並行下 1.9／2.4／1.8 秒通過。**4 個跳過見 §2.4（`DEF-425`），所以這不是「乾淨的全綠」** |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 它們屬單元階段，本輪沒有重跑全量 verify，所以在**所有文件定稿後**（含本計畫書、追蹤表、`RELEASE_TRACKER`、`API_M04`、`API_M12`）以 `mvn -o test -Dtest=…` 另行補跑：4 個類別共 **14 個案例，0 失敗／0 錯誤／0 跳過**，`BUILD SUCCESS` |
| 突變驗證 | 4 個全數擊殺（§5） |

**這次完整套件不是 `make validate-e2e` 本身，而是它的副本**：`scripts/validate-e2e.sh` 沒有「只跑某個規格」的開關，所以我在 scratchpad 複製一份，只改兩行（`REPO_ROOT` 寫死為本專案路徑；最後的 `npx playwright test` 加 `--reporter=list`，才能列出被跳過案例的名稱）。我用 `diff` 核對過差異只有這兩行；其餘流程（起乾淨 PostgreSQL／Redis、`mvn package`、`npm ci`、V94 檢查、E2E admin 設定、Playwright）與原腳本逐字相同。

## 7. 過程中的失誤與修正

1. **第一批突變白跑，而且差點被誤讀成「全部擊殺」**：我用 Python 驅動去呼叫閘門腳本。Python 3.9 偵測到 C 語系會把 `LC_CTYPE` 設成 `C.UTF-8` 傳給子行程（PEP 538），macOS 內建 bash 3.2 於是把閘門腳本第 87 行 `$PG_PORT、` 的全形頓號吃進變數名，`set -u` 報 `PG_PORT�: unbound variable`。四個突變各只花 14～19 秒，`gate_exit` 都是 1——彙總裡看起來就像「四個測試都轉紅」。我是因為耗時短得不合理才去讀日誌。這個坑記憶 `driver-script-pitfalls-pipe-eof-locale-guard` 第 2 點早已記載，並要求「新驅動首跑要早看輸出」，我沒做。修法：傳給子行程的環境拿掉 `LC_CTYPE`、補上逾時，並先以 M1 在前景跑通管線才批次執行。
2. **「店主當買家價格會不同」是我的錯誤假設**：寫 `DEF-424` 的測試時，原本打算加第二個案例（自己店鋪開關關閉的店主去買別家商品）。讀 `RolePermissionMapping` 才發現店主沒有購物車權限，這個面向不存在，已移除，沒有留下半成品。
3. **起初把 `DEF-424` 當缺陷**：讀 `API_M12` §1.2 與 PRD 矩陣之後改為「規格沒定義的產品語意」，追蹤表與規格註解都照這個定性寫，並明寫「這不違反 PRD」。
4. **M1 第一次前景跑沒有量到訂單**：01 的購物車斷言用的是一般 `expect`，鏈在購物車就停了（購物車單價 1 vs 1000），沒有走到下單。改成 `expect.soft` 後重跑，才量到訂單明細與總額（§2.1）。
5. **解析工具的小失誤**：awk 的 `exp` 是內建函式名稱，拿來當變數語法錯誤；日誌裡的 ✓／✘ 是多位元組字元，`substr` 切壞。改用 Python 解析，所以逐案例狀態表（§5）來自 Python 的結果，不是 awk。
6. **zsh 老坑又犯一次**：`grep --include=*.java` 沒加引號報 `no matches found`（記憶 `zsh-false-zero-results-traps`），補上引號重跑。

## 8. 限制（誠實揭露）

1. 整條鏈量到**訂單建立為止**；付款金額取自訂單總額是讀碼推論。
2. 訂房側（`BookingService` 兩處呼叫同一個開關方法）只讀碼，沒有實測；訂房端點的權限與呼叫者租戶也沒追。
3. M1 對「只帶 `listingId`」的請求比修復前更寬鬆；§2.1 的證據只用「兩個 id 並存」的請求。
4. `E2E-PCART-03` 是現況記錄，不是規格；裁決後必須跟著改。
5. `DEF-425` 的「選擇器可能過期」是推論；「何時開始永遠跳過」沒有重建。
6. 完整套件是 `validate-e2e.sh` 的副本（§6），不是 `make validate-e2e` 本身。
7. 全量驗證變慢的原因查不出（§2.5）。
8. 雲端 e2e job 仍未實跑；`DEF-419` 未動。
9. 沒有重跑全量後端 `mvn verify`（後端沒有改動）。

## 9. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 想推翻時 |
|---|---|---|---|
| 1 | `DEF-424` 只登記為待裁決的產品語意，不修 | 與 PRD、`API_M12` §1.2 的文字一致；修法會改變消費者被收的價格，並牽動訂房側與 `SUPER_ADMIN` 規則的語意 | 告訴我選 (A)／(B)／(C)；選 (B) 我會一併改訂房側與相關測試，並把 `E2E-PCART-03` 改成斷言原價 |
| 2 | `E2E-PCART-03` 寫成「現況記錄」，不用 `test.fail()` | 應有的行為沒有 PRD 依據（§4） | 告訴我改用 `test.fail()` |
| 3 | `DEF-425` 只登記，本輪不重寫 `at-m17-003`／`004` | 要重做選擇器，值得獨立一輪；「數值更新」該測什麼可能牽涉判斷 | 告訴我現在就做 |
| 4 | 維持 Sprint 307 決定 7：不改 50 條上限的訊息與常數名 | 使用者沒給措辭 | 給我措辭 |
| 5 | 價格類斷言用 `expect.soft` | 守門失效時能量到整條鏈每一步的金額（§4） | 告訴我改回一般 `expect` |
| 6 | 用 `validate-e2e.sh` 的副本（寫死路徑、list 報表）跑單規格與完整套件 | 不動 repo 內的腳本；原腳本沒有只跑某規格的開關 | 告訴我改成直接 `make validate-e2e` |
| 7 | 沒有改任何生產程式碼；**沒有 push** | 本輪只有測試與文件；依慣例，純文件／測試的 commit 留在本機，隨下次有功能變更的 push 一起出去（見 §10） | 告訴我 push |

## 10. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 308 列。本輪新增的 commit 尚未 push；本機另有 Sprint 307 的純文件回填 commit `835d71e0` 同樣未 push。

> **後續（Sprint 309）**：上面是 Sprint 308 當時的狀態。這些 commit 已隨 Sprint 309 的 push（`79302fcf..d26e8a00`）一併帶出，雲端 CI 結果（先紅後綠）見 [SPRINT_309_PLAN.md](SPRINT_309_PLAN.md) §9。

本輪的 commit 範圍：新增 `frontend/e2e/at-pricing-cart-checkout-real.spec.ts`；修改 `API_M04_Cart.md`（v2.3）、`API_M12_Dynamic_Pricing.md`（v2.6）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-423` 更新、新增 `DEF-424`／`DEF-425`）、`RELEASE_TRACKER.md`（v4.38）；新增本計畫書。`backend/src` 沒有任何差異。
