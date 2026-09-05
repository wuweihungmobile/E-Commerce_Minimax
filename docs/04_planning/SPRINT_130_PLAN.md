# Sprint 130 Plan — DEF-093 裸實體序列化洩漏 passwordHash + DEF-092 權限碼孤兒

**Sprint**: Sprint 130
**日期**: 2026-09-06

---

## 1. 本輪範圍

Sprint 129 收尾時留下的候選清單為 DEF-080~093（詳見 `DEFERRED_ITEMS_TRACKER.md`），本輪開工時使用者從中點名兩項：

- **DEF-093**：`ListingController.getListings()`/`getListing()` 直接序列化裸 `Listing` 實體，`owner`（LAZY → `User`）懶載入會把整個 `User`（含 `passwordHash`，無 `@JsonIgnore`）序列化進 API 回應。Sprint 129 標記為「建議下一輪優先評估」的資安缺陷。
- **DEF-092**：5 個權限碼（`cms:read/create/update/publish`、`notification:create`）僅 `SUPER_ADMIN` 到得了。Sprint 129 已判定為「忘記授權」並拍板產品定性，登記為「下一輪可直接實作」。

**AskUserQuestion 拍板紀錄**：

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | Sprint 130 涵蓋範圍 | 僅此 2 項（DEF-093、DEF-092），推薦組合 |
| 2 | DEF-092 角色分派方案 | 初次詢問時只呈現「比照 PostController」vs「沿用 DEF-073/075 統一慣例」兩個籠統選項，使用者選了後者 |
| 3 | DEF-092 角色分派方案（更正） | 動手實作並測試通過後，發現 `DEFERRED_ITEMS_TRACKER.md` 原記錄的 DEF-092 列**早已記載 Sprint 129 拍板過的細緻方案**（比初次問題的兩個選項都更具體），與剛完成的實作不同。誠實揭露此落差後重新詢問，使用者改判**採用本表原記錄的細緻方案**（見 §3） |

---

## 2. DEF-093：`ListingController` 裸實體序列化外洩 `passwordHash`

**缺陷**：`getListings()` 回傳 `ApiResponse<Page<Listing>>`、`getListing()` 回傳 `ApiResponse<Listing>`，皆未經 DTO 轉換直接回傳 JPA 實體。`Listing.owner` 為 `@ManyToOne(fetch=LAZY)` 指向 `User`；專案未設定 Jackson Hibernate 模組（`Hibernate5Module`/`Hibernate6Module`）亦未關閉 `spring.jpa.open-in-view`（預設 `true`），序列化裸實體時懶載入代理會在回應序列化階段被觸發，把整個 `User` 實體遞迴序列化進 JSON——`User.passwordHash` 沒有 `@JsonIgnore`，密碼雜湊值以明文形式外洩進 API 回應。

**紅燈實測確認漏洞屬實**：暫時還原修復前的程式碼，呼叫 `GET /v2/listings/:id` 與 `GET /v2/listings`，回應 JSON 明文包含 `"owner":{...,"passwordHash":"$2a$10$SHOULD.NEVER.LEAK.INTO.RESPONSE",...}`。

**修復**：新增 `ListingResponse` DTO（`api/dto/ListingResponse.java`），比照 Sprint 129 `ListingOptionDto`（DEF-076）的既有慣例——一律用 `Listing.getTenantId()`（影子欄位，`insertable=false, updatable=false`）取得 `tenantId`，完全不觸碰 LAZY 的 `owner`/`tenant` 關聯本身。欄位涵蓋前端 TS `Listing` 介面宣告的全部欄位（`id`/`tenantId`/`listingType`/`title`/`description`/`coverImageUrl`/`status`/`basePrice`/`currency`/`tags`/`createdAt`/`updatedAt`），不含 `ownerId`/`metadata`（前端未使用，避免不必要的欄位暴露面）。`ListingController.getListings()`/`getListing()` 改用 `ListingResponse::fromEntity` 映射後回傳，回傳型別改為 `ApiResponse<Page<ListingResponse>>`/`ApiResponse<ListingResponse>`。

**前端相容性**：確認全庫無其他程式碼直接依賴這兩個端點的舊回傳型別；前端 `services/listing.ts` 的 `Listing` 介面欄位全數保留，無破壞性變更。

**測試**：新增 `ListingControllerE2ETest`（`integration/`，`@MockBean ListingRepository` + `@WithMockUser`，比照 `M12EffectivePriceIntegrationTest` 的輕量整合測試寫法，不需真實建立租戶/JWT）：
- IT-DEF093-01：`GET /v2/listings/:id` 回應不含 `owner`/`passwordHash`，且保留前端依賴欄位
- IT-DEF093-02：`GET /v2/listings`（分頁清單）回應不含 `owner`/`passwordHash`

兩案例修復前皆紅燈（`$.data.owner` 存在且含明文 `passwordHash`），修復後綠燈。

---

## 3. DEF-092：`cms:*`/`notification:create` 權限碼孤兒

**缺陷**：`CmsController`（頁面/橫幅 CRUD+發布）、`NotificationController`（`POST /send`）的 `@PreAuthorize` 引用 `cms:read`/`cms:create`/`cms:update`/`cms:publish`/`notification:create`，但這 5 個碼從未定義於 `Permission` 枚舉。生產唯一授權來源 `RolePermissionMapping.getAuthorities()` 只發枚舉內已定義的碼，即使 `@PreAuthorize` 帶 `hasRole('SUPER_ADMIN') or hasAuthority(...)` fallback，**除 SUPER_ADMIN 外沒有任何角色到得了這些端點**。

**角色分派**（採用 `DEFERRED_ITEMS_TRACKER.md` 於 Sprint 129 已拍板的細緻方案，非 DEF-073/075 的「OWNER 全權/STAFF 唯讀/ADMIN 全權」統一樣板）：

- `cms:read`/`cms:create`/`cms:update`：比照 `PostController` 既有 CRUD 開放範圍，授予 `STORE_OWNER`/`STORE_STAFF`/`SELLER`/`HOST`（無唯讀限制——`STORE_STAFF` 在此模組與 `PostController` 一致擁有完整寫入權，不同於 `dashboard`/`faq`/`knowledge`/`media` 的員工唯讀慣例）。
- `cms:publish`：限制較高層級，僅 `STORE_OWNER`+`ADMIN`（`SUPER_ADMIN` 經 `EnumSet.allOf` 自動取得）。
- `notification:create`：同樣僅 `STORE_OWNER`+`ADMIN`——`NotificationService.sendNotification()` 對任意 `userId` 生效、無租戶邊界檢查，保守授權避免任意店主對他租戶用戶發送通知的風險面擴大（此為既有事實，非本輪修復範圍，僅影響角色分派判斷）。

**修復**：`Permission.java` 新增 5 常數（`CMS_READ`/`CMS_CREATE`/`CMS_UPDATE`/`CMS_PUBLISH`/`NOTIFICATION_CREATE`）；`RolePermissionMapping.java` 依上述分派——`STORE_OWNER`/`ADMIN` 全授 5 碼；`STORE_STAFF`/`SELLER`/`HOST` 僅授 `CMS_READ`/`CMS_CREATE`/`CMS_UPDATE`（不含 `CMS_PUBLISH`/`NOTIFICATION_CREATE`）。`PreAuthorizePermissionCoverageTest.SUPER_ADMIN_FALLBACK_ONLY` 清空（原列這 5 碼，現已全數修復，依既有規則「清冊不得包含已修好的碼」同步移除）。

**測試**：`RolePermissionMappingTest` 新增 5 案例：
- `storeOwner_hasFullAccessToDef092Modules`：`STORE_OWNER` 5 碼全授
- `storeStaff_hasCmsCrudButNotPublishOrNotification`：`STORE_STAFF` 有 CRUD 三碼、無 publish/notification
- `sellerAndHost_haveCmsCrudButNotPublishOrNotification`：`SELLER`/`HOST` 同上
- `admin_hasFullAccessToDef092Modules`：`ADMIN` 5 碼全授（跨租戶）
- `superAdmin_hasAllDef092Permissions`：`SUPER_ADMIN` 經 `allOf` 自動取得

`PreAuthorizePermissionCoverageTest` 新增 `def092CodesAreDefined()` 案例斷言 5 碼已在枚舉中；既有 `everyReferencedAuthorityCodeIsDefined`/`superAdminFallbackOnlyCodesAreUnchanged` 兩支測試在清冊清空後自然轉綠，作為獨立確認。

**誠實揭露（過程瑕疵）**：本輪調查階段派出的 Explore agent 雖然完整讀出了 `PostController`、`RolePermissionMapping`、`IntegrationTestConfiguration` 等程式碼細節，但沒有查到 `DEFERRED_ITEMS_TRACKER.md` 裡 DEF-092 那一列**本身就記載著 Sprint 129 已拍板的細緻方案**——只看到 `SPRINT_129_PLAN.md` 裡「比照 PostController 開放」的籠統定性描述。因此第一次 `AskUserQuestion` 給使用者的選項不完整（只有「嚴格比照 PostController」與「沿用 DEF-073/075 統一慣例」兩個籠統選項），使用者選了後者並已完整實作＋測試通過。**在更新 `DEFERRED_ITEMS_TRACKER.md` 時才發現這個落差**，隨即停下向使用者誠實揭露，經確認後改採本表原記錄的細緻方案，重寫 `RolePermissionMapping` 角色分派與 `RolePermissionMappingTest` 斷言。教訓：涉及「已有拍板記錄」的缺陷，應先完整讀過 `DEFERRED_ITEMS_TRACKER.md` 對應列的完整內容（而非僅依賴調查 agent 的摘要），再向使用者提出決策問題。

---

## 4. 本輪未修、已登記待排程的項目

DEF-080~091（契約漂移類），詳見 `DEFERRED_ITEMS_TRACKER.md`。

---

## 5. 驗證

- 新增/修改測試：`ListingControllerE2ETest`（新檔，+2：IT-DEF093-01/02）、`RolePermissionMappingTest`（+5）、`PreAuthorizePermissionCoverageTest`（+1，另清空 `SUPER_ADMIN_FALLBACK_ONLY`）
- DEF-093、DEF-092 修復前皆完成紅燈驗證（暫時還原程式碼確認測試失敗），修復後綠燈
- 後端全量回歸 `mvn -o verify`：**BUILD SUCCESS**，單元 **1096**（相對 Sprint 129 的 1090，+6，恰等於本輪新增測試數：`RolePermissionMappingTest` +5、`PreAuthorizePermissionCoverageTest` +1）、整合 **465**（相對 463，+2，恰等於 `ListingControllerE2ETest` 新增的 2 案例），0 Failures / 0 Errors；checkstyle（main+test）**0 violations**；PMD 通過
- 未新增/修改 Flyway migration（本輪未變更任何 `@Entity`），`make validate-schema`/`make validate-schema-doc` 不適用（留待 push 前輕量守門一併確認無漂移）
