# Sprint 182 Plan — 帳號/金流/內容/功能開關越權全掃（DEF-244~247）

**Sprint**: Sprint 182
**日期**: 2026-09-22

## 1. 起點

Sprint 181 修復 `DEF-242~243` 後，`DEFERRED_ITEMS_TRACKER.md` 僅剩 `DEF-235`/`DEF-236`/`DEF-241` 三項低優先級「不排入排程」項目。本輪選定全新角度：派出 2 個背景唯讀調查 agent，分別針對「認證/註冊/訂單狀態機」與「CMS/功能開關/通知/客服」兩大範圍做開放式越權掃描（不預設攻擊模式，讓 agent 自行找出「呼叫端能否讓系統相信一件沒有真的發生的事」這類缺陷）。

## 2. 掃描方法與結果

- **Agent 1（認證/訂單/金流）**：找到 🔴 CRITICAL `DEF-244`（註冊端點可自帶 `tenantId` 奪取任一店鋪的 `STORE_OWNER` 權限）、🟠 HIGH `DEF-245`（訂單狀態機可被賣家/店主直接偽造 `PAID`/`REFUNDED`）。
- **Agent 2（CMS/功能開關/通知/客服）**：找到 🔴 嚴重 `DEF-247`（功能開關自助申請可繞過平台審核直接生效）、🟡 MEDIUM `DEF-246`（CMS 頁面/橫幅更新端點可繞過 `cms:publish` 權限分層直接發布）、🟢 LOW（`ReviewService.createReview` 的 `orderId`/`bookingId` 未驗證，評估後不排入本輪）。

兩個 agent 找到的 4 個缺陷分屬不同模組，無重疊，皆經本人直接讀原始碼、追蹤呼叫鏈、grep 前端呼叫點後獨立驗證，才進入修復階段。

## 3. 修復內容

### 3.1 DEF-244（🔴 CRITICAL）：註冊端點可自帶 tenantId 奪取任一店鋪管理權

`POST /v2/auth/register`（完全公開、無需登入）先前接受呼叫端自帶的 `tenantId` 欄位：`AuthService.register()` 不僅把它寫入 `User.tenantId`，還會直接建立一筆 `TenantMember`（`storeRole=STORE_OWNER`）。任何人只要知道（或列舉出）任一租戶的 UUID，就能註冊一個帳號並立即取得該租戶的 `STORE_OWNER` 權限——完整的商品/訂單/定價/員工管理權，且完全不需要通過既有的 `TenantApplicationRequest` 審核流程或店主邀請流程。另外 `userType` 的 `@Pattern` 白名單先前也接受 `STORE_OWNER`/`STORE_STAFF`，等同雙重授予路徑。

**修法**：
- `RegisterRequest` 移除 `tenantId` 欄位；`userType` 的 `@Pattern` 收斂為 `^(BUYER|SELLER|HOST)$`。
- `AuthService.register()` 不再讀取/寫入任何租戶關聯欄位，`resolveUserRole()` 移除 `STORE_OWNER`/`STORE_STAFF` 兩個 case（僅保留 DTO 層 Bean Validation 失效時的防禦性後備）。
- 店主身分僅能透過既有的審核/邀請流程取得，公開註冊端點與租戶完全脫鉤。

**紅燈驗證的替代做法**：此修法是移除欄位（而非新增參數），不適用「暫時短路方法體」技巧（DEF-242 的做法）。改採獨立讀碼 + 交叉驗證：本人直接讀完 `AuthService.java`/`AuthController.java`/`RegisterRequest.java`，grep 全庫確認 `AuthService.register` 僅有一個呼叫入口（`AuthController.register`），grep 前端確認 `RegisterRequest`/`tenantId`/`userType` 的實際送出欄位，讀完 `AuthServiceTest`/`AuthServiceRegisterTest` 既有覆蓋範圍後才動手修復；修復後新增的測試（見下方）直接驗證「帶入任意租戶 UUID 不得建立任何 `tenant_members` 關聯」。

**既有「鎖住漏洞」的測試**：`AuthServiceTest.register_withTenantId_createsTenantMemberAssociation` 先前明確斷言 `verify(tenantMemberRepository).save(any())`——即把漏洞行為當作正確功能驗證。已替換為 `register_doesNotCreateAnyTenantAssociation`，斷言 `verify(tenantMemberRepository, never()).save(any())`。

**連帶修復的測試設施**：`M11PromoCheckoutIntegrationTest`/`M11CartPromoIntegrationTest` 的測試 fixture 依賴移除前的機制，讓賣家與買家共享同一租戶（本專案的促銷碼租戶範圍判定依賴呼叫端自己的 `TenantContext`，買家也須是目標租戶的真實成員）。改用本專案既有的安全替代模式：註冊後直接透過 `TenantMemberRepository` 建立成員關聯（比照 `AuthControllerE2ETest` 既有的 `deleteMyAccount_nonBuyerRole_returns403` 測試手法），不再依賴已移除的不安全機制。`M16ErpE2ETest`/`M09NotificationTemplateIntegrationTest` 同樣改用註冊後直接以 repository 賦予角色/租戶的既有模式。

### 3.2 DEF-245（🟠 HIGH）：訂單狀態機可被直接偽造 PAID/REFUNDED

`OrderService.updateOrderStatus`（`PATCH /v2/orders/{orderId}/status`，`order:update` 由 `SELLER`/`STORE_OWNER`/`ADMIN`/`SUPER_ADMIN` 持有）是純粹的狀態機驗證，完全不區分「這個狀態轉換是不是應該由某個特定子系統觸發」。`OrderStateMachine` 允許 `CREATED→PAID`、`REFUNDING→REFUNDED`，且此方法只檢查目標狀態是否為合法的下一步、以及呼叫者是否有權限操作此訂單（租戶/擁有者），從未檢查目標狀態本身是否該由呼叫端直接指定。任一賣家/店主可對自己租戶下尚未真正收到付款的訂單直接呼叫 `PATCH .../status` 帶 `targetStatus=PAID`，訂單立即被標記已付款（無真實 `Payment` 記錄），可用於灌水業績/觸發後續結算流程；同理可偽造 `REFUNDED`，向買家謊報退款已完成而未真的退款。

**修法**（經 `AskUserQuestion` 徵詢使用者：完全封鎖，含 `ADMIN`/`SUPER_ADMIN` 皆無例外，推薦選項）：新增私有常數集合 `PAYMENT_SYSTEM_ONLY_STATUSES = {"PAID", "REFUNDED"}`；`updateOrderStatus` 拆為兩個重載——原 3 參數簽章對外開放（`OrderController` 呼叫），新增 4 參數版本多一個 `systemTriggered` 旗標，僅 `PaymentService.processOrderPayment`（收到真實付款後）以 `true` 呼叫繞過限制。非 `systemTriggered` 呼叫若 `targetStatus` 落在 `PAYMENT_SYSTEM_ONLY_STATUSES`，一律拋 `E_5001` 拒絕，需改由付款/退款子系統觸發。

**Self-invocation 踩坑（`mvn verify` 全量回歸才抓到）**：初版把 `@CacheEvict`/`@Transactional` 只標在 4 參數版本、3 參數版本為純委派的無註解薄封裝。`RedisCacheConfigTest`（既有的反射式註解覆蓋率測試）立即抓到 3 參數版本缺少 `@CacheEvict`——這不只是測試期望不符，而是**真的功能回歸**：Spring AOP 代理只攔截「從物件外部進來」的呼叫，3 參數方法內部以 `this.` 呼叫 4 參數方法屬於同一物件內部呼叫（self-invocation），會繞過代理，讓 4 參數方法自身的 `@CacheEvict`/`@Transactional` 對這條路徑完全失效——`OrderController` 呼叫的路徑因此會靜默失去快取清除與交易邊界保障。修法：兩個重載都各自完整標註 `@CacheEvict`/`@Transactional`，確保兩條真實外部呼叫入口（`OrderController`→3 參數、`PaymentService`→4 參數）都各自被代理正確攔截。

**紅燈驗證**：新增 3 個測試涵蓋（一般呼叫端指定 `PAID`、指定 `REFUNDED`、`ADMIN` 嘗試指定 `PAID`），因新增了方法重載（非破壞既有簽章），比照 `DEF-242` 手法暫時把限制判斷短路為 `if (false && ...)`，確認 3 案例全數轉紅，證實漏洞存在；還原後轉綠。

**既有「鎖住漏洞」的測試修正**：`OrderServiceTest.updateOrderStatus_validTransition`（買家透過 3 參數呼叫直接轉 `PAID`）、`updateOrderStatus_admin_bypassesTenant`（`ADMIN` 透過 3 參數呼叫直接轉 `PAID`）皆把偽造行為當作正確功能斷言。前者改為 `updateOrderStatus_systemTriggeredPaidTransition_succeedsAndLogsState`（改用 4 參數 `systemTriggered=true`，比照 `PaymentService` 真實呼叫方式）；後者拆成兩個測試——`updateOrderStatus_admin_bypassesTenant` 改用非付款狀態（`SHIPPING`）驗證租戶檢查本身仍正常放行，另新增 `updateOrderStatus_admin_cannotForgePaidStatus` 明確斷言即使 `ADMIN` 也不得偽造 `PAID`。`updateOrderStatus_concurrentClaim_throwsE5001`（驗證併發搶佔）也改用 `systemTriggered=true`——否則修復後這個案例會被新的外部呼叫限制先擋下（剛好同為 `E_5001`），造成測試看似通過但實際上已測不到原本要驗證的併發搶佔邏輯。

### 3.3 DEF-246（🟡 MEDIUM）：CMS 更新端點可繞過 cms:publish 權限分層

`CmsService.updatePage`/`updateBanner`（`cms:update`，`SELLER`/`HOST`/`STORE_OWNER`/`STORE_STAFF` 皆持有）的請求 DTO 帶有 `status` 欄位，`updatePageStatusFields`/`updateBannerStatusFields` 對此欄位無條件寫入，等同任一持有 `cms:update` 的角色可直接把 `status` 設為 `PUBLISHED`，完全繞過刻意分層、僅授予 `OWNER+ADMIN` 的 `cms:publish` 權限（本應透過專屬的 `POST /v2/cms/pages/{id}/publish`/`.../banners/{id}/publish` 端點）。查證本專案自己已有的正確參照範例——`PostService.updatePost`（`M15Dto.UpdatePostRequest`）完全沒有 `status` 欄位，狀態變更僅能經 `publishPost`/`unpublishPost` 兩個獨立方法，`CmsService` 在建立時偏離了這個既有的正確模式。

**修法**：`updatePageStatusFields`/`updateBannerStatusFields` 在寫入前檢查：若目標值為 `PUBLISHED`，一律拋 `E_1007`（403，「Publishing requires cms:publish permission, use the publish endpoint instead」），需改呼叫對應的 `publish` 端點；`DRAFT`/`ARCHIVED` 等非 publish-tier 狀態不受影響，維持原行為（因為持有 `cms:update` 者本就能完全編輯該筆內容的其他所有欄位，降級/封存不構成新的越權）。

**前端影響評估**：grep 全庫確認 `frontend/src` 對 `/v2/cms/pages`、`/v2/cms/banners` 零呼叫點——此模組目前完全沒有前端 UI 串接，修法不影響任何現有使用者操作路徑。

**紅燈驗證**：新增 2 個測試（`updatePage_statusPublished_mustBeRejected`/`updateBanner_statusPublished_mustBeRejected`），暫時把兩處新增的 `if (newStatus == PUBLISHED)` 判斷短路為 `if (false && ...)`，確認轉紅（舊邏輯繼續往下跑到 `save()`，因測試未 stub 該路徑而拋 `NullPointerException`，證實無早期攔截）；還原後轉綠。

### 3.4 DEF-247（🔴 嚴重）：功能開關自助申請繞過平台審核直接生效

`TenantService.updateFeatureToggle`（店主自助申請開關，`US-M17-006`）的 `FeatureDefinition` 對部分功能標記 `requiresApproval=true`（`BOOKING_ENABLED`/`DYNAMIC_PRICING_ENABLED`/`PROMO_ENABLED`），意圖是這類功能需要平台審核才能真正啟用。但無論 `requiresApproval` 為何，`toggle.setIsEnabled(enabled)`（既有 toggle）與 `buildNewFeatureToggle(..., enabled)`（新 toggle）皆無條件直接寫入呼叫端要求的值——回應的 `status`/`statusDescription` 會誠實顯示 `"PENDING_APPROVAL"`/`"Feature request submitted, pending platform approval"`，但資料庫裡的 `isEnabled` 其實已經是 `true`，任何讀取 `TenantFeatureToggle.isEnabled` 的下游程式碼（`FeatureToggleService.isFeatureEnabled`/`checkFeatureEnabled`）會認定該功能已真正啟用。查證全庫確認**目前完全沒有任何管理員審核/核准端點存在**——這不是「審核尚未串接完成」的暫時狀態，而是店主自助永久性地繞過了一個從未真正建立防線的審核機制。

**修法**：僅在「該功能需審核 且 此次為新啟用要求（`enabled=true` 且 `previousState` 為 `false`）」時，實際寫入 DB 的值改為 `false`（真正保持未啟用），讓回應文字與 DB 狀態一致；停用請求（`enabled=false`）與不需審核的功能不受影響，沿用原行為；已經真正啟用中的需審核功能重複確認 `enabled=true` 也不受影響（不會被本次修法誤打回未啟用）。`resolveFeatureStatus`（既有的查詢端狀態推導邏輯，`toggle != null && requiresApproval` → `PENDING`）本就已預期「toggle 存在但未啟用」代表待審核中，修法後的實際寫入行為與這個既有推導邏輯完全吻合。

**既有「假綠燈」測試修正**：`TenantControllerE2ETest.updateFeatureToggle_AsStoreOwner_ReturnsPendingApproval` 先前只斷言回應 `data.status == "PENDING_APPROVAL"`，從未實際查驗 DB 裡 `isEnabled` 的真實值——修復前這個測試在漏洞存在時一樣是綠燈。補上 `data.newState == false` 的回應斷言，並新增直查 `TenantFeatureToggleRepository` 驗證 `isEnabled` 確實為 `false` 的斷言。

**紅燈驗證**：新增 `TenantServiceTest.updateFeatureToggle_requiresApprovalFeature_newRequest_staysDisabled`（新 toggle 申請需審核功能）與 `..._alreadyEnabled_staysEnabled`（既有已啟用不受影響的回歸防護）。暫時把 `effectiveEnabled` 計算改回無條件等於 `enabled`，確認前者轉紅（`expected: <false> but was: <true>`）；還原後轉綠。

## 4. 測試總覽

- **紅燈先行**：`DEF-245`/`DEF-246`/`DEF-247` 皆因新增重載或新增判斷分支（未破壞既有簽章），採用「暫時短路新增的檢查邏輯」手法驗證；`DEF-244` 因是欄位移除（無法用短路技巧模擬「欄位存在」），改採獨立讀碼＋交叉驗證的替代嚴謹度，已於 §3.1 誠實揭露。
- **新增測試**：`AuthControllerE2ETest` +2（DEF-244 端到端）、`OrderServiceTest` +3（DEF-245）、`CmsServiceTest` +2（DEF-246）、`TenantServiceTest` +2（DEF-247）。單元測試合計 +7（1608→1615），整合測試合計 +2（484→486），與逐一清點的異動筆數完全吻合。
- **既有測試修正（鎖住漏洞行為 → 改為驗證正確行為）**：`AuthServiceTest`（1 個）、`OrderServiceTest`（2 個：`validTransition` 改走系統觸發路徑、`admin_bypassesTenant` 改用非付款狀態驗證）、`TenantControllerE2ETest`（1 個，補上 DB 直查斷言）。
- **測試設施修正（因移除不安全機制而需改用既有安全替代模式，非行為缺陷）**：`M11PromoCheckoutIntegrationTest`/`M11CartPromoIntegrationTest`/`M16ErpE2ETest`/`M09NotificationTemplateIntegrationTest`。
- **架構修正（`mvn verify` 全量回歸中意外發現，非計劃內項目）**：`OrderService.updateOrderStatus` 的 self-invocation 快取/交易註解遺失，見 §3.2。

## 5. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1615 個單元測試（+7）+ 486 個整合測試（+2），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。

過程中第一次跑完整 `mvn verify` 揪出 2 個問題，皆已修復並重跑確認：
1. `M09NotificationTemplateIntegrationTest` 5 個案例失敗（依賴 DEF-244 移除前的註冊機制建立測試租戶關聯）。
2. `RedisCacheConfigTest.updateOrderStatus_shouldHaveCacheEvictAnnotation` 失敗（DEF-245 拆分重載時的 self-invocation 註解遺失，見 §3.2）——這是本輪唯一一個「修復安全缺陷的同時意外引入功能回歸」的案例，靠既有的反射式註解覆蓋率測試攔下，重申了 `mvn verify` 全量回歸（而非只跑新增/直接相關測試）在此類重構中的必要性。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-244`/`DEF-245`/`DEF-246`/`DEF-247`：狀態皆記為「✅ 已修復（Sprint 182）」，發現與修復同輪完成。
- `ReviewService.createReview` 的 `orderId`/`bookingId` 未驗證（Agent 2 🟢 LOW 發現）：查證確認此二欄位僅用於評論與訂單/訂房的關聯展示，不影響任何金流/庫存/權限判斷，且評論本身仍需通過既有的 `checkReviewEligibility`（買家確實完成該筆交易）把關，現階段可利用性有限，記錄但不排入本輪。

## 7. 誠實揭露總結

- `DEF-244` 是本輪最高風險項目：完全公開、無需任何前置條件即可觸發，攻擊成本僅為「知道一個租戶 UUID」（租戶 UUID 在多處回應中並非高度保密，如公開的商品/房源詳情頁）。其存在時間橫跨多個先前 Sprint 皆未被發現，凸顯「認證/註冊」這類基礎設施層級端點即使功能穩定運作已久，仍需要定期以「這個公開端點能否被用來取得非預期權限」的角度重新審視，而非只在新增功能時才檢查。
- `DEF-245` 的 self-invocation 踩坑是本輪最重要的方法論收穫：修復安全缺陷本身沒有問題，但拆分方法重載時若未注意 Spring AOP 代理的呼叫來源限制，會在「看似正確」的重構中靜默引入功能回歸。這類回歸不會被新增的安全測試發現（安全測試只驗證授權/拒絕邏輯是否正確，不驗證快取/交易是否生效），必須依賴既有的、目的不同的測試（本例為 `RedisCacheConfigTest`）才能攔下，再次印證「`mvn verify` 全量回歸」而非「只跑新增/直接相關測試」的必要性。
- `DEF-247` 與 `DEF-238`（Sprint 180，通知系統無租戶檢查）、`DEF-060`（M17 開店審核台接在死流程上）同屬「系統中存在一個聲稱有把關機制、但把關本身從未真正被建立或已失效」的模式家族，建議未來持續以此角度掃描其餘標記 `requiresApproval`/`需審核`/`待確認` 字樣但缺乏對應審核端點的功能。
- `DEFERRED_ITEMS_TRACKER.md` 目前僅剩 `DEF-235`/`DEF-236`/`DEF-241` 三項低優先級「不排入排程」項目，另新增本輪 `ReviewService` 的 🟢 LOW 項目（不排入排程），Sprint 183 開工時仍需自選新掃描角度。
