# Sprint 240 Plan — 沒有店鋪的 SELLER／HOST 不得寫入店家層資料（使用者拍板；DEF-326）

**Sprint**: Sprint 240
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（順序為我的安排，屬推論）

使用者在 Sprint 238 結尾以互動選擇拍板三件事，全選建議項：(1) `V89` 照現狀回填；(2) 結帳擋掉非 ACTIVE 的店鋪（Sprint 239 已完成）；(3) **`DEF-326` 選項 (a)：未歸屬任何店鋪的 SELLER／HOST 不得寫入店家層資料**（與 PRD「需先申請開店」一致；依租戶判斷，不是依角色標籤）。本輪實作 (3)。選項與取捨見 [SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §6.1。

### 1.2 修復前先實測（本輪才第一次實測；原本只有 Sprint 234 稽核 agent 讀碼）

`DEF-326` 在 Sprint 234 只是讀碼（稽核端點 A13、A15～A22）。本輪先寫測試、在**未修復的程式**上跑，確認問題真實存在：

- **HTTP 整合層**（真實 PostgreSQL、走真實的註冊→登入→JWT→生產權限對照表，`StoreLessSellerHostE2ETest`）：12 個案例在未修復時 **11 個變紅**（唯一綠的是「有店鋪的 SELLER 角色照舊」這個對照，修復前後都該成立）。沒有店鋪的自助註冊 SELLER：`POST /v2/products` → **201**（在系統租戶的買家目錄上架商品）、`POST /v2/shipping-templates` → **200**（系統租戶的運費模板會套用在所有一般買家的結帳）、`POST /v2/cms/pages` → **200**、`POST /v2/dashboard/posts` → **200**、ERP 讀取（供應商、庫存）→ **200**、`GET /v2/seller/dashboard` → **200**（讀到系統租戶＝所有一般消費者訂單的統計）；沒有店鋪的 HOST：`PUT /v2/rooms/{id}` 通過權限檢查（得到 404 而不是 403）；登入回應、換發、`GET /v2/auth/me` 的角色都是 `SELLER`。
- **真實全棧**（打包 JAR＋真實 Redis＋Playwright，`at-store-less-seller-real.spec.ts`）：未修復的 JAR 上，SLS-01 在第一個斷言（登入回應的角色是 `SELLER`，不是 `BUYER`）失敗；serial 模式下 SLS-02／03 因此沒有執行，所以**真實全棧的紅燈證據只涵蓋「角色」這一項，探針的紅燈證據來自上面的 HTTP 整合層**。（之後把角色斷言改成 `expect.soft`，失敗時探針結果會一起列出。）
- 那次真實全棧守門另有 **2 個既有規格失敗**（`at-account-data-rights` 的刪除帳戶與匯出，是整輪第一、第二個案例；頁面快照顯示當下沒登入）。上一輪（Sprint 239）同一份 JAR 與前端是全綠的，那次跑的又是修復前的 JAR，**與本輪改動無關**；**原因未查明**（守門的後端日誌是暫存檔、跑完即刪），見 §5。

### 1.3 設計：判斷點是「簽發」（讀碼）

- 權限完全由 token 的 `role` 宣告決定：`JwtAuthenticationFilter` 讀 `role` → `RolePermissionMapping.getAuthorities` → 授權。
- access token **只在一處簽發**：`AuthService.generateAuthResponse`（唯一呼叫 `JwtTokenService.generateAccessToken` 的主程式碼；登入、換發、OAuth 都經過它，見 Sprint 215 的 `completeLogin`）。

所以不必逐一修各店家層端點的擁有權檢查（商品、房源、定價、運費模板、CMS、貼文、ERP……十幾處，且日後新增的又會漏），而是在簽發時推導「有效角色」：

| 面向 | 做法 |
|------|------|
| 有效角色 | `AuthService.effectiveRole(user, tenant)`：角色是 `SELLER` 或 `HOST`，且**租戶不是真實店鋪**（系統租戶佔位值、或租戶解析不到）→ `BUYER`；其餘一律不變。判斷用 `TenantContext.isStoreTenant`（Sprint 234 的單一述詞） |
| 判斷依據 | **租戶，不是角色標籤**：有真實店鋪租戶的 SELLER／HOST（舊資料、店鋪成員）照常運作；平台管理員（`SUPER_ADMIN`、本來就在系統租戶）等其他角色不受影響（單元測試對所有其他角色逐一斷言） |
| 資料庫 | `users.role` **不動**。開店核准時 `AdminService` 把角色改成 `STORE_OWNER`；有效角色每次簽發時重新推導，所以自我修復（店鋪成員被移除、租戶被刪除，下次簽發自動變回買家），不需要資料遷移 |
| 一致性 | 登入回應的 `user.role`、token 的 `role` 宣告、`GET /v2/auth/me` 的 `userType`／`tenants[].role` 都用有效角色，不會出現「回應說是 SELLER、實際權限是買家」。只有 SELLER／HOST 才查租戶，其他角色（含大宗的 BUYER）不多花查詢。**註冊回應的 `userType` 不變**——那是「註冊時存下的角色」，與 PRD AC-M03-001-1 一致 |
| 賣家儀表板 | `GET /v2/seller/dashboard` 原本是 `hasRole('SELLER')`：真正的店主（`STORE_OWNER`）呼叫不了、只有沒有店鋪的 SELLER 能呼叫並讀到系統租戶的統計。改為 `hasAnyRole('STORE_OWNER','SELLER')`，並在方法內擋系統租戶的呼叫者（`E-1007`，**先判斷、不查詢**）——換發前簽發、仍帶 `SELLER` 角色的舊 token 在有效期內也讀不到 |
| 前端 | **沒有改動**：前端沒有任何畫面會註冊 `SELLER`／`HOST`（註冊頁不送 `userType`，grep 確認），也不呼叫 `GET /v2/auth/me`（只用同路徑的 `DELETE` 與 `/me/data-export`）；沒有店鋪的使用者本來就由既有的 `/tenant/apply`（申請開店）流程取得店鋪 |

### 1.4 修復後角色的副作用（讀碼）

- `SELLER` 與 `HOST` 的權限集合原本**沒有**購物車、建立訂單、訂房、客服工單、退貨——自助註冊的賣家連一般購物都做不到。簽發為 `BUYER` 後，他們就是一個正常的消費者（這符合「還沒開店」的實況）。
- `UserPrivacyService.deleteMyAccount` 檢查的是**資料庫**角色（只有 `BUYER` 能自助刪除帳戶），不是 token 角色，本輪沒動（見 §6.1）。

## 2. 使用者決策與需要使用者知悉的行為變更

本輪實作的就是使用者在 Sprint 238 結尾拍板的第 (3) 項，沒有新的產品決策。行為變更：

1. **沒有店鋪的 SELLER／HOST 變成買家**：不能再建立／修改／刪除商品、房源、定價、運費模板、CMS、貼文，讀不到 ERP 與賣家儀表板；但可以像一般消費者購物。**已簽發的 access token 在有效期內（15 分鐘）不受影響**，與其他租戶或角色變更相同。
2. **賣家儀表板**：真正的店主（`STORE_OWNER`）從此能呼叫（原本只有沒有店鋪的 SELLER 呼叫得到，且讀到的是系統租戶的統計）。
3. **既有資料（新登記的待決定，見 §6.2）**：系統租戶下由沒有店鋪的 SELLER／HOST 建立的商品、房源、貼文，會留在買家目錄與公開頁面，**擁有者不能再編輯、下架或刪除**。我沒有查正式環境；部署前請先跑 §6.2 的預覽 `SELECT` 看筆數。

## 3. 實作內容

- `AuthService`：`effectiveRole`／`isSellerOrHost`；`generateAuthResponse`（token 與回應）與 `getCurrentUser`（`userType`、`tenants[].role`）改用有效角色。
- `SellerDashboardController.getDashboard`：`hasAnyRole('STORE_OWNER','SELLER')` ＋ 系統租戶守門。
- **既有測試的改寫**（Rule 9：測試固定的是缺陷本身，要改成固定意圖）：
  - `M13SellerDashboardIntegrationTest`：原本 IT-DASH-001 用**系統租戶**讀儀表板（正是 `DEF-326`，卻被當成正常行為固定下來）。改以真正的 `UserPrincipal`（`TenantContextFilter` 只認它，`@WithMockUser` 的主體會被當成匿名而落到系統租戶）、店鋪租戶、`STORE_OWNER`／`SELLER` 兩個角色；新增 IT-DASH-004（系統租戶的呼叫者→403 `E-1007`，且 `verifyNoInteractions` 完全不查詢）。
  - `ProductControllerE2ETest`、`CartControllerE2ETest`：原本「註冊 SELLER→登入→**事後**才把租戶寫進資料庫」，token 帶的是系統租戶，正是 DEF-326 的情境。改成先歸到店鋪租戶再登入（與真實流程一致：開店核准後重新登入，JWT 才帶店鋪租戶）。我先挑出所有引用 `SELLER`／`HOST` 的測試類別（24 個、342 個測試）跑一次：**只有這兩個類別失敗**（Cart 8、Product 4，共 12 個）；全量 `verify` 另行確認（見 §5）。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元 | `AuthServiceTest.EffectiveRole`（16 次執行）：沒有店鋪的 SELLER／HOST 簽發為 BUYER（token 與回應都是，資料庫角色不動）、租戶解析不到也是、有店鋪租戶（`User.tenantId`）的 SELLER／HOST 角色照舊、店鋪成員（`tenant_members` 有效成員）身分的 SELLER 照舊、**其他 7 個角色一律不變**（含 `SUPER_ADMIN`）、換發（refresh）走同一套、`getCurrentUser` 一致（沒有店鋪回 BUYER、有店鋪回 SELLER 含 `tenants[].role`） |
| HTTP 整合（真實 PostgreSQL，+12） | `StoreLessSellerHostE2ETest`：簽發（登入回應與 token 的 role 是 BUYER、`users.role` 不動）、有店鋪者照舊、換發、`/auth/me`；商品／運費模板／CMS／貼文（SELLER 與 HOST）／ERP／儀表板被擋（403 `E-1007`，**且斷言系統租戶沒有出現任何探針建立的資料**），每個案例含「有店鋪的 SELLER 與店主照常」的對照；儀表板縱深防禦（舊 token）；HOST 房源（有店鋪的 HOST 通過權限檢查得到 404、沒有店鋪的得到 403）。探針的請求內容都是合法的——Spring MVC 先綁定並驗證請求內容、才進方法安全檢查，內容不合法會先得到 400，把「沒被擋」藏起來 |
| HTTP 整合（改寫） | `M13SellerDashboardIntegrationTest` 3→6 個（見 §3） |
| 真實後端 Playwright（+3） | `at-store-less-seller-real.spec.ts`：**SLS-01** 自助註冊的 SELLER 以買家身分登入，商品／運費模板／CMS／ERP／儀表板全被擋；**SLS-02** 自助註冊的 HOST，房源與 CMS 被擋；**SLS-03** 對照：走真實開店流程的店主通過同樣的權限檢查（404），並能讀賣家儀表板。探針用「不存在的隨機 ID」：權限檢查先於資源查詢，被擋的得 403 `E-1007`、通過的得 404，而且**不會在共用的資料庫留下任何資料**（例如在系統租戶新增一個對所有買家結帳生效的運費模板） |

### 突變驗證（Rule 9：測試必須在修復被拿掉時失敗）

跑 `AuthServiceTest`＋`StoreLessSellerHostE2ETest`（`AuthService` 突變，54 個測試）與 `M13SellerDashboardIntegrationTest`＋`StoreLessSellerHostE2ETest`（儀表板突變，18 個測試）；表中是轉紅的測試數。

| # | 突變 | 結果 |
|---|------|------|
| MA1 | `effectiveRole` 一律回資料庫角色（拿掉降級） | 14 個轉紅 |
| MA2 | 所有 SELLER／HOST 都降級，不看有沒有店鋪 | 12 個轉紅（「有店鋪的 SELLER／HOST 角色照舊」對照守住了「依租戶、不是依標籤」） |
| MA3 | 只降級 SELLER，漏掉 HOST | 4 個轉紅 |
| MA4 | 降級範圍擴大到所有非 BUYER 的角色（含 `SUPER_ADMIN`） | 6 個轉紅（「其他角色一律不變」守住了平台管理員不被降級） |
| MA5 | `GET /v2/auth/me` 不用有效角色 | 2 個轉紅（單元＋HTTP） |
| MA6 | 登入回應的 `user.role` 不用有效角色 | 6 個轉紅 |
| MA7 | token 的 `role` 宣告不用有效角色（回應仍是 BUYER） | 11 個轉紅 |
| MD1 | 儀表板拿掉系統租戶守門 | 3 個轉紅（含 `verifyNoInteractions`） |
| MD2 | 儀表板還原成只有 `hasRole('SELLER')` | 2 個轉紅（店主讀不到） |

每次突變都先備份源碼、等 class 比源碼新才跑（IDE 的 Java 語言服務有在跑，會搶先編譯）、跑完還原並以 `cmp` 確認逐位元組一致，結束時再以 `sha256` 核對兩個被突變的檔案與突變前完全相同。

## 5. 驗證結果

- **後端全量**：`mvn -o clean verify` **BUILD SUCCESS（20 分 32 秒）**——單元 **2074**（+16：`AuthServiceTest.EffectiveRole`）／整合 **738**（+15：`StoreLessSellerHostE2ETest` 12 個、`M13SellerDashboardIntegrationTest` 3→6）／**0 失敗**／0 略過。全量結果也證實先前的盤點：引用 `SELLER`／`HOST` 的測試，只有 `ProductControllerE2ETest`、`CartControllerE2ETest` 兩個類別需要改（它們的 12 個失敗是修復後第一次跑那 24 個類別時看到的）。
- **`make validate-schema-doc`**：對乾淨 PostgreSQL 套用 **89** 個 Flyway 遷移（本輪沒有新遷移）通過。
- **前端**：沒有改動（`frontend/src` 沒有比上次建置新的檔案），沿用 Sprint 239 的建置；只新增 Playwright 規格 `at-store-less-seller-real.spec.ts`。
- **真實後端 E2E**：`E2E_GATE_SKIP_BUILD=1 make validate-e2e`（JAR 是上面 verify 剛建好的修復版）**139 個測試：135 通過／4 略過／0 失敗（4.4 分鐘）**，後端以 `ddl-auto=validate` 啟動確認 entity 與 Flyway 對齊；新增的 SLS-01／02／03 全綠；沒有 flaky、沒有 did not run。那 4 個略過是既有基準。
- **修復前那次守門的 2 個既有規格失敗沒有再發生**：`at-account-data-rights` 的刪除帳戶與匯出（整輪第一、第二個案例）這次通過。**原因仍未查明**：失敗當時頁面快照顯示未登入，一個可能的機制是冷啟動時 `registerAndLogin` 的註冊等待上限（6 秒）不夠、helper 默默略過登入；而我剛好在守門啟動前後改完檔，IDE 的 Java 語言服務可能正在重編、搶走 CPU。**這只是假說，沒有證據**（守門的後端日誌是暫存檔，跑完即刪）。這次重跑時我沒有改任何檔案。若日後再出現，先留下後端日誌（`E2E_BACKEND_LOG`）再判斷。
- **突變驗證**：見 §4（9 個全部被抓到，每次還原後以 `cmp` 確認逐位元組一致，結束時再以 `sha256` 核對）。
- **push 與雲端 CI**：（push 後於回填 commit 補上）

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **已簽發的 access token 在有效期內（15 分鐘）仍帶 `SELLER`／`HOST`**：部署後的 15 分鐘內，既有的 token 仍有舊權限。只有儀表板端點自己多擋了一次系統租戶。是否要在 `JwtAuthenticationFilter` 也依 token 的租戶宣告降級（立即生效，但規則會散在兩處）是取捨；我沒做，因為與 Sprint 235（`DEF-329`）接受的是同一個 15 分鐘窗口。
2. **帳戶刪除的前端入口與後端不一致**：帳戶頁用登入回應的 `role` 決定是否顯示「刪除帳戶」，沒有店鋪的 SELLER 現在是 `BUYER`，會看到入口；但 `deleteMyAccount` 檢查資料庫角色（只有 `BUYER`），會回 `E-1009`。影響面極小（前端不會註冊 SELLER／HOST，只有 API 直接註冊的人會遇到），本輪不動隱私功能。
3. **`users.role` 仍可能是 `SELLER`／`HOST` 而沒有店鋪**：資料庫的角色是「註冊時存下的」，有效角色才是權限依據。管理後台的使用者列表、稽核紀錄看到的仍是資料庫的值。
4. **`DEF-330` (a)**：`BUYER` 可達的低風險設定讀取（定價規則列表、運費模板列表、通知模板）仍回系統租戶的設定資料，與公開日曆、結帳運費同源；沒有店鋪的 SELLER／HOST 現在落在這個既有範圍，沒有新增暴露。
5. **前端沒有元件層測試**（承 [SPRINT_238_PLAN.md](SPRINT_238_PLAN.md) §4）；本輪沒有前端改動。

### 6.2 待使用者決定／確認（累積）

| 項目 | 內容 | 我的建議 |
|------|------|----------|
| **本輪新增：既有資料** | 修復後，系統租戶下由沒有店鋪的 SELLER／HOST 建立的商品、房源、貼文會留在買家目錄與公開頁面，擁有者不能再編輯、下架或刪除（只有平台管理員能）。我沒有查正式環境。部署前先跑預覽 `SELECT`（見下）看筆數 | 先看筆數；有資料的話，建議由管理員下架（不刪除），日後擁有者開店後再由他自己重新上架 |
| Sprint 239 §6.1 第 1 點 | 店鋪停權前已成立、尚未付款的訂單，是否也擋掉付款（回 `E-2010`，提示消費者取消） | 擋掉：與「避免消費者付款給停權店鋪」同一個理由；代價是多一個擋點與一則提示 |
| Sprint 234 §6.4 | 沒有店鋪的使用者的限流單位（已登入→使用者、匿名→來源 IP，容量 100 次／分）是工程決策，PRD 未定義 | 同意（有疑慮可調整容量或加全域上限） |
| Sprint 235～237 §2 | `V87`／`V88` 會改既有資料庫的使用者角色與訂房租戶；店主／店員第一次看得到消費者訂房與訂單的個資（`V89` 使用者已拍板照現狀） | `V87`／`V88` 同意（部署前先跑預覽 `SELECT`） |
| `DEF-326` | **使用者已拍板（Sprint 238 結尾）選項 (a)，本輪完成** | — |

**預覽 `SELECT`（唯讀，已在測試資料庫驗證語法）**：

```sql
-- 系統租戶下、由 SELLER／HOST 建立的商品與房源
SELECT l.listing_type, l.status, count(*) AS n
FROM listings l JOIN users u ON u.id = l.owner_id
WHERE l.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND u.role IN ('SELLER', 'HOST')
GROUP BY l.listing_type, l.status ORDER BY 1, 2;

-- 系統租戶下、由 SELLER／HOST 發的貼文
SELECT p.status, count(*) AS n
FROM posts p JOIN users u ON u.id = p.author_id
WHERE p.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND u.role IN ('SELLER', 'HOST')
GROUP BY p.status ORDER BY 1;
```

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果。
2. 之後（我的安排）：文件對齊（`DEF-322`、`DEF-323` (b)；API／SRD／FRD 補上 Sprint 235～240 的契約變更——登入回應的角色語意、`E-2010`、`E-5020`、分店鋪購物車與結帳、訂房／訂單歸屬店鋪；PRD 內文不動只加修訂註記，比照 Sprint 203）→ 店鋪成員管理前端（`DEF-321` (a)）→ CMS 卡片連結（`DEF-321` (c)）→ 通知事件（`DEF-318`）→ 店鋪前台 `/stores`（`DEF-321` (b)）。
3. 選配：整合測試的 bcrypt 強度——**除非有人真的量測，否則不再追這個假說**（見 [SPRINT_239_PLAN.md](SPRINT_239_PLAN.md) §5：雲端整合 job 近十一次 4m15s～8m05s，同一份後端程式碼差近 2 分鐘）。
