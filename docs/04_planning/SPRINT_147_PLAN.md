# Sprint 147 Plan — 配額強制政策拍板 + MAX_PRODUCTS/MAX_ROOMS/MAX_POSTS 數量配額實作

**Sprint**: Sprint 147
**日期**: 2026-09-09

---

## 1. 缺口盤點結果

### 1.1 起點

回歸 AISDLC 流程盤點時，先完成 Sprint 146 遺留的流程債（[RELEASE_TRACKER.md](RELEASE_TRACKER.md) 回填 Sprint 146 push 狀態與雲端 CI 結果，見該文件 v2.32），再檢視 `SPRINT_146_PLAN.md` §9 Action Items 剩餘候選：

| # | 候選 | 性質 |
|---|------|------|
| 6 | 配額強制是否要實作 | 需產品決策（Sprint 145 §9 item 6 延續至今，已跨 2 個 Sprint 未決） |
| 7 | DEF-103/104/105 三筆輸入驗證 | 低優先級技術債，已排隊 |

使用者拍板本輪主題為「先決定配額強制政策」，並在決策過程中進一步選擇「新增為正式需求並實作強制」（而非維持現狀或僅記錄）。

### 1.2 決策過程中發現的事實

查證 PRD（`E-Commerce_PRD_v1.0_Final.md` §4.4）確認：Feature Toggle 矩陣表只列出 `MAX_PRODUCTS`/`MAX_ROOMS`/`MAX_POSTS`/`COMMISSION_RATE` 的預設值，緊接著的「具體實作約束」段落只規範了布林開關（`E-2020 FEATURE_DISABLED_FOR_TENANT`），全文找不到任何條文要求這四個數值配額要被強制執行，也沒有對應的錯誤碼定義——這與 Sprint 145 §5.1 對 `DEF-167` 的查證結論一致（「配額從來就沒有被執行過」）。

---

## 2. 使用者決策（AskUserQuestion 拍板紀錄）

| # | 問題 | 使用者裁定 |
|---|------|-----------|
| 1 | Sprint 147 主題 | 先決定配額強制政策 |
| 2 | MAX_PRODUCTS/MAX_ROOMS/MAX_POSTS/COMMISSION_RATE 最終方向 | **新增為正式需求並實作強制** |
| 3 | COMMISSION_RATE 是否納入本次範圍 | **排除在外**——它是比例值，且生產程式碼已透過 `Tenant.commissionRate` 欄位獨立運作抽佣，與這個 Feature Toggle 無關，混在一起會造成兩個抽佣來源同時存在的架構問題 |
| 4 | 三個數量上限的計數範圍 | **只計目前上架中/啟用中的項目**（Product/Room 計 `Listing.status = ACTIVE`；Post 計 `status = PUBLISHED`），下架後可再新增，不因歷史操作永久卡住店家 |

---

## 3. 實作內容

### 3.1 PRD 規格補齊（先於程式碼變更）

依 AISDLC 文件驅動原則，在動程式碼前先於 `E-Commerce_PRD_v1.0_Final.md` §4.4 補上「數量配額驗證」規格小節，比照既有布林開關驗證的寫法定義：計數範圍、驗證時機、配額來源（沿用既有全域預設值，本次不做逐租戶客製化覆寫）、新錯誤碼 `E-2009 QUOTA_EXCEEDED`、實作約束（比照既有規則，須在 Repository/Service 層執行，不得在 Controller 層）。

### 3.2 後端實作

**單一事實來源**：`AppConstants` 新增 `QUOTA_MAX_PRODUCTS=100`/`QUOTA_MAX_ROOMS=20`/`QUOTA_MAX_POSTS=50`；`TenantService` 既有的私有常數 `DEFAULT_MAX_PRODUCTS`/`DEFAULT_MAX_ROOMS`/`DEFAULT_MAX_POSTS`（先前只用來組 `FEATURE_DEFINITIONS` 給 `/features` 顯示頁，數值從未被其他地方引用）改為直接引用 `AppConstants` 常數，避免日後配額檢查與顯示頁的預設值各自維護一份數字造成漂移——這正是 Sprint 143 記錄過的「兩份清單需人工同步」既有教訓的同型風險。

**共用檢查方法**：`FeatureToggleService`（既有 `checkFeatureEnabled` 的同一個服務）新增 `checkQuotaNotExceeded(int limit, long currentActiveCount)`，`currentActiveCount >= limit` 時拋 `BusinessException(ErrorCode.E_2009)`。呼叫端負責查詢目前數量並傳入正確上限，方法本身不耦合任何特定實體。

**Repository 新增計數方法**：
- `ListingRepository.countByTenantIdAndListingTypeAndStatus(tenantId, listingType, status)`——`MAX_PRODUCTS`/`MAX_ROOMS` 需依 `listingType` 分別計數，既有的 `countByTenantIdAndStatus` 不分 PRODUCT/ROOM。
- `PostRepository.countByTenantIdAndStatus(tenantId, status)`。

**檢查點（共 8 處，涵蓋建立與「重新上架」兩種會使配額生效的路徑）**：

| 服務 | 方法 | 檢查時機 |
|------|------|----------|
| `ProductService` | `createProduct` | RETAIL_ENABLED 檢查後、建立 Listing 前 |
| `ProductService` | `createProductFromDashboard` | 取得 tenantId 後、建立 Listing 前（`RETAIL_ENABLED` 已由呼叫端 `DashboardListingController` 檢查，見 §7 DEF-184） |
| `ProductService` | `updateProduct` | 僅當 `request.status` 會使 Listing **由非 ACTIVE 轉入 ACTIVE** 時 |
| `RoomService` | `createRoom` / `createRoomFromDashboard` / `updateRoom` | 與 Product 三個進入點對稱 |
| `PostService` | `createPost` | 僅當 `autoPublish=true` 時（此時新貼文仍是 DRAFT，尚未計入自己） |
| `PostService` | `publishPost` | 已有的「已發布則拒絕」防呆之後，`post.publish()` 之前 |

`updateProduct`/`updateRoom` 的檢查刻意限定「由非 ACTIVE 轉入 ACTIVE」才觸發（新增 `isActivatingListing` 私有方法判斷），理由有二：(1) 避免既有 ACTIVE 商品因改其他欄位（如品牌、標籤）被誤擋；(2) 堵住「先建滿額 → 下架一筆 → 用該名額建立新的 → 再把舊的重新上架」這個只在建立時檢查會漏掉的繞過路徑——若只查點 (2)：店鋪已有 100 個 ACTIVE 商品（達上限）→ 下架 1 個（99 ACTIVE）→ 建立新商品（99 < 100 通過，變 100 ACTIVE）→ 若無此檢查，重新上架剛才下架的那個會直接變成 101 ACTIVE，實質突破配額。

**前端**：不需改動。API 錯誤一律由既有的通用錯誤顯示機制處理（`GlobalExceptionHandler` 回傳 `{code, message}`，前端 axios 攔截器只特判 401，其餘一律以 `error.response.data.message` 顯示），`E-2009` 沿用相同機制即可顯示。

### 3.3 訊息設計的額外查證

追蹤 `BusinessException`/`GlobalExceptionHandler` 全鏈路以決定 `E-2009` 的訊息格式時，發現 `BusinessException.getUserMessage()` 固定回傳 `ErrorCode.getMessage()` 的原始模板字串，`GlobalExceptionHandler` 也只回傳這個值給前端——這代表全庫既有的三個帶 `%d`/`%s` 佔位符的錯誤訊息（`E_1088`/`E_1089`/`E_1090`，皆在 Review 模組）實際上從未把動態值代入就回給了使用者。此發現與本輪功能無直接關係，不在本輪修復，已登記為 `DEF-183`（見 §7）。因此 `E-2009` 刻意設計為**不帶佔位符的靜態訊息**：「已達店鋪配額上限，如需調整請聯繫平台管理員」，避免重蹈覆轍。

---

## 4. 測試

**後端單元測試**：新增 14 個，全數**紅燈先行且實際執行**（非僅口頭宣稱）：

- `ProductServiceTest`（+4）：`createProduct_quotaExceeded_doesNotCreateListing`、`createProduct_underQuota_createsSuccessfullyAndChecksQuota`、`updateProduct_reactivateOverQuota_throwsAndDoesNotSave`、`updateProduct_nonStatusChangeWhileActive_doesNotCheckQuota`（守衛：確認非啟用轉換不誤觸發配額查詢）。
- `RoomServiceTest`（+4）：對稱的四個案例。
- `PostServiceTest`（+3）：`createPost_autoPublishOverQuota_throwsException`、`publishPost_quotaExceeded_throwsAndDoesNotSave`、`publishPost_underQuota_checksQuotaAndPublishes`。
- `FeatureToggleServiceTest`（+3）：`checkQuotaNotExceeded` 邊界測試（低於上限不拋、**剛好等於上限**拋出、超過上限拋出）——驗證用的是 `>=` 而非 `>`，即上限是「最多可有 N 個」而非「第 N+1 個才擋」。

**紅燈驗證方式**：對 `ProductService.java`/`RoomService.java`/`PostService.java` 分別執行 `git stash push -- <檔案>` 還原至 Sprint 147 之前的版本，重跑對應測試類別，確認且僅確認新增的配額相關測試案例失敗（既有案例維持通過，證明未破壞既有行為）；`git stash pop` 還原後重跑轉為全數通過。三次 stash/pop 循環的失敗數分別為 3、3、3（`ProductServiceTest`/`RoomServiceTest`/`PostServiceTest`），與新增測試數一致。

**既有測試相容性**：`PostServiceTest` 原本未 mock `FeatureToggleService`，新增建構子依賴後若不補上會使 `createPost_withAutoPublish_success`/`publishPost_success` 兩個既有測試因 `featureToggleService` 為 `null` 而 NPE——已補上 `@Mock` 欄位，確認兩者仍通過。

**編譯陷阱複查**：每次 `mvn compile`/`mvn test` 前皆 `rm -rf target/maven-status`（部分情況需 `mvn clean compile`，見執行記錄）強制重編並確認出現 `Compiling N source files` 該行，不採信單獨的 `BUILD SUCCESS`（[[mvn-phantom-build-success-after-failure]] 既有教訓）。

**checkstyle**：`mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test`（兩個 execution 皆驗）**0 violations**。

**後端全量回歸**：`make test-db-up` 後 `mvn -o verify`（含 failsafe 整合測試），結果見 §5。

**前端**：本輪零前端檔案變動，未執行 `tsc`/`eslint`/`build`（無變更範圍可驗）。

---

## 5. 驗證結果

`make test-db-up` 後 `mvn -o verify`（含 checkstyle main+test、PMD、failsafe 整合測試）**BUILD SUCCESS**，耗時 8:39 min：

- **單元測試**：**1210**（相對 Sprint 146 的 1196，+14，與本輪新增測試數一致）、0 failures/errors
- **整合測試**：**477**（與 Sprint 146 持平，本輪未新增整合測試方法），0 failures/errors/skipped
- **checkstyle**（main+test 兩個 execution）：**0 violations**
- **PMD**：通過，無 violation
- **`make validate-schema`**：**通過**（entity 與 Flyway schema 對齊，無漂移——本輪僅新增 Repository 衍生查詢方法與常數，未動任何 entity 欄位或 migration）
- **前端**：本輪零檔案變動，未執行（無變更範圍可驗）
- **E2E**：本輪未執行 `make validate-e2e`——純後端業務邏輯新增，既有 Playwright 案例不涉及配額情境（無測試資料會自然觸發 100/20/50 筆上限），留待未來若有 UI 呈現配額用量時再一併補 E2E 覆蓋

---

## 6. 範圍外（延後）

- **COMMISSION_RATE 強制執行**：使用者拍板排除在外，維持現狀（見 §2 決策 3）。
- **逐租戶客製化配額覆寫**（例如平台管理員為特定店鋪調高 `MAX_PRODUCTS`）：PRD 新規格明確排除，屬獨立需求，另行規劃。
- **`/dashboard/tenants/[id]/features` 顯示頁補上實際數值**：目前該頁仍依 Sprint 145 (DEF-167/168) 的既有修復，完全不顯示數值配額（`isBoolean` 為 false 即跳過）；本輪只做後端強制執行，不擴大範圍去改顯示邏輯讓店主能在畫面上看到自己的配額用量，避免範圍蔓延。
- **`DEF-183`**（`BusinessException.getUserMessage()` 固定回傳未代入模板，`E_1088`/`1089`/`1090` 三筆佔位符從未生效）：本輪查證發現但與配額功能無直接關係，登記不修復。
- **`DEF-184`**（`DashboardListingController.createListing` 既有布林開關檢查放在 Controller 層，牴觸 PRD 明文規定）：行為正確，純屬分層規範落差，登記不修復，避免範圍蔓延。

---

## 7. 延後項目登記

| DEF | 摘要 | 不修復的理由 |
|-----|------|-------------|
| DEF-183 | `BusinessException.getUserMessage()` 固定回傳原始模板，`E_1088`/`1089`/`1090` 佔位符從未代入 | 既有缺陷非本輪引入，範圍局限在 Review 模組 3 筆訊息 |
| DEF-184 | `DashboardListingController.createListing` 既有布林開關檢查放在 Controller 層 | 行為正確，純架構規範落差，非本輪配額功能範圍 |

---

## 8. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) v2.0 §3.2，本節為必要章節。

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | 配額強制政策決策 + `MAX_PRODUCTS`/`MAX_ROOMS`/`MAX_POSTS` 實作 | Sprint 145 §9 item 6 | ✅ 完成 | — |
| 2 | `RELEASE_TRACKER` 回填 Sprint 146 push 狀態與雲端 CI 結果 | 本輪開工盤點 | ✅ 完成 | — |
| 3 | `RELEASE_TRACKER` 回填本輪 push 狀態與雲端 CI 結果 | 本輪交付後 | ⬜ 待下一輪開工時回填 | 依既有「狀態欄維護規則」 |
| 4 | DEF-103/104/105 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已在追蹤器 |
| 5 | DEF-183（`getUserMessage()` 佔位符缺陷） | 本輪查證發現 | ⬜ 待排程 | 低優先級，範圍局限 Review 模組 |
| 6 | DEF-184（Controller 層布林開關檢查） | 本輪查證發現 | ⬜ 待排程 | 低優先級，純架構規範落差 |
| 7 | `/dashboard/tenants/[id]/features` 顯示頁補上配額用量 | 本輪 §6 範圍外 | ⬜ 待排程 | 需先確認是否要做（新功能，非技術債） |

---

## 9. 誠實揭露總結

- PRD 查證結論（「配額從來沒有被強制執行過」）與 Sprint 145 §5.1 的結論一致，非本輪新發現，本輪是把該已知落差正式轉為「已補規格、已實作」的狀態。
- `updateProduct`/`updateRoom` 的重新上架繞過路徑是實作過程中主動想到、並非使用者或掃描報告指出，已用專門的紅燈測試（`*_reactivateOverQuota_*`）驗證修法確實堵住。
- `DEF-183`（訊息佔位符從未生效）是設計 `E-2009` 訊息格式時的副產品發現，與本輪配額功能本身無關，如實登記但不擴大處理範圍。
- 本輪只做後端強制執行，`/features` 顯示頁仍看不到配額用量——店主目前只能在真正超額被拒絕時才知道上限存在，這是刻意的範圍收斂（§6），非遺漏。
