# Sprint 150 Plan — DEF-186/187：Address/Analytics/Logistics 前後端契約掃描 + 運費模板設定頁

**Sprint**: Sprint 150
**日期**: 2026-09-10

---

## 1. 起點

Sprint 149 完成後，`DEFERRED_ITEMS_TRACKER.md` 待排程清單四項皆非「已拍板、待執行」的具體任務（DEF-103/104/105 已拍板不排、其餘三項皆註記「需先確認是否要做」）。使用者拍板本輪方向：**延續自選掃描慣例**，由 Agent 自選一個尚未掃過的角度做全面稽核。

比對歷史已涵蓋角度（訂單/付款/退貨/評價、CMS/Blog/媒體/通知、客服工單/知識庫/FAQ、租戶/訂房/購物車/儀表板、ERP 採購/供應商、Sprint 149 Auth/OAuth），選定 **`Address`／`Analytics`／`Logistics`／`ShippingTemplate`** 四個從未被掃過的模組，比對 `frontend/src/services/{address,analytics,logistics}.ts` 與後端 `AddressController`／`AnalyticsController`／`LogisticsController`／`ShippingTemplateController` 的呼叫契約。

---

## 2. 缺口盤點

### 2.1 Address — 無缺口

`services/address.ts` 的 5 個方法（list/create/update/remove/setDefault）與 `AddressController` 5 個端點一一對應，欄位（`recipientName`/`phone`/`postalCode`/`city`/`district`/`addressLine`/`isDefault`）與 `AddressDto` 完全一致。`grep` 確認 `checkout/mixed`、`checkout/product`、`(auth)/addresses` 三處實際呼叫。**判定：完整，無需處理。**

### 2.2 Analytics — 文件字串過時，行為正確

`AnalyticsController`/`AnalyticsService` 的 Javadoc 皆寫「(Mock Implementation)」，但實地讀取 `AnalyticsService` 原始碼確認 5 個方法（`getDashboardStats`/`getRevenueStats`/`getOrderStats`/`getListingStats`/`getRecentActivity`）皆為真實 DB 查詢（`OrderRepository`/`PaymentRepository`/`ListingRepository` 等），非假資料。前端 `services/analytics.ts` 呼叫其中 3 個（`stats`/`orders`/`revenue`），`listings`/`activity` 兩個零呼叫點——**排除為偽陽性**：這兩個端點回傳的資料（`ListingStats`/`RecentActivity`）目前沒有對應的儀表板 UI 區塊需要它們，屬於「已完成後端、尚無 UI 消費」而非契約漂移。Javadoc「Mock」字樣過時具誤導性但非本輪範圍（文件字串修正不影響行為，且與本輪「契約漂移」性質不同，不擴大範圍修改）。

### 2.3 API_ENDPOINTS 死碼 → `DEF-186`

`frontend/src/lib/api.ts` 存在兩組死碼：
- `API_ENDPOINTS.dashboard.{analytics,listings,orders,bookings}` 四個 key——與下方 `API_ENDPOINTS.analytics.*`（`services/analytics.ts` 實際使用）路徑重複，`grep` 全庫確認這四個 key **零呼叫點**。
- `API_ENDPOINTS.dashboardTenants.{features,updateFeature}` 整組——與同檔案 `API_ENDPOINTS.dashboard.tenants.{features,updateFeature}`（`dashboard/tenants/[id]/features/page.tsx` 實際使用的那組）完全重複，**零呼叫點**。

與 Sprint 146 `DEF-178`（`getFeatureMap` 同型死碼）同一類型：舊版本重構後遺留的死碼配置，同輪一併清理。

### 2.4 Logistics — 建立/出貨相關 4 方法零呼叫點，但屬於更大的訂單履約缺口的一部分（見 §4）

`services/logistics.ts` 只實作 `getByOrder`/`getTrackingDetail`（買家查看物流狀態），`LogisticsController` 其餘 5 個端點（`createLogistics`/`getLogistics`/`trackLogistics`/`updateLogisticsStatus`/`cancelLogistics`）全數零前端呼叫點。追查 `createLogistics` 在後端也**只被 Controller 呼叫一次**（無任何 Service 內部自動觸發路徑）。繼續往上追查發現：**這不是 Logistics 模組自己的缺口，而是整個賣家訂單履約流程從未有前端入口**（詳見 §4），故不在本輪單獨修復，登記為 `DEF-188`（見 §6）。

### 2.5 ShippingTemplate — 完全零前端入口，導致全站訂單一律免運費 → `DEF-187`

`grep` 全庫確認 `frontend/src` 對 `/v2/shipping-templates`（`ShippingTemplateController` 的 CRUD 4 端點：建立/查詢/修改/刪除運費模板）**零命中**——沒有 service 檔案、沒有任何頁面。

追查 `ShippingTemplateService.calculateFeeForTenant`（`OrderService`/`CombinedCheckoutService`/`RedisCartService` 內部呼叫，已正確接入結帳計算）：
```java
// 依 tenantId 取得第一個運費模板計算運費；無模板時回傳 0（免運）。
if (templates.isEmpty()) { return BigDecimal.ZERO; }
```
由於沒有任何前端入口能建立模板，**所有租戶目前都處於零模板狀態**，等同全站商品訂單一律免運費——不是測試環境的偶發現象，是生產環境的常態，且直接影響賣家實際收取的運費（金流語意層面的功能性缺口，非單純顯示問題）。

`ShippingTemplateDto.UpdateRequest` 不含 `feeType`（後端設計為建立後計費方式不可變更），前端表單需對齊此限制。

判定為真實缺陷，編號 `DEF-187`，本輪修復。

---

## 3. 使用者決策

本輪掃描角度已由 §1 的 `AskUserQuestion` 拍板。盤點過程中發現 §2.5（`DEF-187`）與 §2.4/§4（`DEF-188`）兩項規模遠超單純契約漂移的重大缺口，再次以 `AskUserQuestion` 徵詢處理方式：使用者拍板「兩項都做，依序 A（運費模板設定頁）→ B（訂單出貨管理），B 若本輪做不完則移到下一個 Sprint」。

本輪（Sprint 150）完成 A（`DEF-187`）；B（`DEF-188`）經 §4 的追加調查後確認規模需要獨立 Sprint（不只是前端頁面，後端本身也缺對應能力，見下），依使用者授權移至 Sprint 151，本輪僅完成精確的範圍盤點與登記。

---

## 4. DEF-188 追加調查：訂單出貨管理不只是缺前端頁面，後端也缺對應能力

原以為 `DEF-188` 與 `DEF-187` 同型（後端已完整，純前端補入口），追加調查後發現並非如此：

1. **`frontend/src/app` 找不到任何賣家訂單管理頁面**——`dashboard/` 下有 products/rooms/posts/pricing/returns/support/erp 等子目錄，唯獨沒有 orders。`dashboard/page.tsx` 顯示「待出貨：N」統計數字（`AnalyticsService.getOrderStats` 的 `pendingShipment`，即狀態為 `PAID` 的訂單數），但沒有任何頁面能將訂單標記為出貨。

2. **後端 `GET /v2/orders` 是買家專用端點**：`OrderService.getUserOrders` 固定 `findByUserIdOrderByCreatedAtDesc(當前使用者ID, ...)`，回傳的是呼叫者自己的訂單，不是「當前租戶收到的訂單」。**沒有任何端點能列出一個租戶所有待處理的訂單**——`AnalyticsService.getRecentActivity` 雖內部用了 `orderRepository.findByTenantIdOrderByCreatedAtDesc`，但只回傳 `ActivityItem`（type/description/timestamp 三個顯示用欄位），不是可操作的訂單列表。

3. **後端 `GET /v2/orders/{orderId}` 的擁有權檢查未涵蓋賣家**：`OrderService.getOrder` 只允許「訂單擁有者本人 or ADMIN/SUPER_ADMIN」讀取，未比照 `updateOrderStatus` 已有的 `checkOrderStatusUpdateAuthorization`（owner OR **same-tenant** OR admin）三選一模式。也就是說，即使賣家已經知道某筆訂單的 ID，目前也無法用 `GET /v2/orders/{orderId}` 查看其詳情——但賣家卻可以對同一筆訂單呼叫 `PATCH /v2/orders/{orderId}/status`（因為那支已有 tenant 分支）。這是一個**讀寫授權不對稱**的既有疏漏：能寫入自己讀不到的資料。

4. `POST /v2/logistics`（建立物流單/出貨，含追蹤號）功能完整且經多輪 IDOR 加固（DEF-019/024/036），但同樣因為沒有訂單列表/詳情入口，賣家實務上永遠無法觸發它。

**結論**：`DEF-188` 的修復範圍是「新增賣家訂單管理」這個完整功能切片，至少包含：
- 後端：新增租戶範圍的訂單列表端點（可重用 `orderRepository.findByTenantIdOrderByCreatedAtDesc`，需補分頁/依狀態篩選）；擴充 `OrderService.getOrder` 的授權比照 `checkOrderStatusUpdateAuthorization` 加入 same-tenant 分支（讀寫授權對稱化）。
- 前端：新增 `/dashboard/orders`（列表）＋ `/dashboard/orders/[id]`（詳情，含狀態轉換操作與建立物流單表單）。

這個範圍遠大於「補一個設定頁」，需要獨立 Sprint 的完整開發-測試循環（含新測試、`mvn verify`、`validate-e2e`），故依 §3 使用者授權移至 Sprint 151，本輪不倉促動手。

---

## 5. DEF-187 實作內容（本輪完成）

修法純前端，**後端零程式碼變動**（`ShippingTemplateController`/`Service` 功能已完整且已有 `ShippingTemplateControllerE2ETest` 覆蓋，只是前端從未使用）：

1. `frontend/src/lib/api.ts`：新增 `API_ENDPOINTS.shippingTemplates`（list/create/update/delete 對齊 `/v2/shipping-templates` 4 端點）；同時清理 `DEF-186` 兩組死碼 key。
2. `frontend/src/services/shippingTemplate.ts`（新檔）：`ShippingTemplateService` 4 個方法，型別對齊 `ShippingTemplateDto`（`UpdateRequest` 刻意不含 `feeType`，比照後端「計費方式建立後不可變更」的設計）。
3. `frontend/src/components/shipping/ShippingTemplateList.tsx`（新檔）：CRUD 列表 + 表單 Modal，比照既有 `PricingRuleList.tsx` 的元件慣例（同一套 shadcn UI 元件、錯誤處理、`confirm()`/`alert()` 模式）。明確標示清單第一筆為「生效中」（對齊 `calculateFeeForTenant` 取第一筆的實際行為），不新增後端沒有的限制（例如不阻擋建立第二筆模板）。
4. `frontend/src/app/dashboard/shipping/page.tsx`（新檔）：頁面外殼，比照 `dashboard/pricing/rules/page.tsx` 慣例（含 `await AuthService.logout()`）。
5. `frontend/src/app/dashboard/page.tsx`：`QUICK_LINKS` 新增「運費模板設定」連結（`data-testid="dashboard-shipping-link"`，比照既有 `dashboard-pricing-link`/`dashboard-returns-link` 慣例）。

不改動：`ShippingTemplateService.calculateFee`（DEF-037 已拍板刻意不做租戶過濾，本輪不動）；`calculateFeeForTenant`「取第一筆」的既有行為（改變此語意屬於另一個決策，非本輪範圍）。

---

## 6. 驗證結果

- **`npx tsc --noEmit`**：通過，0 error。
- **`npx eslint`**（對 5 個變更/新增檔案）：0 error，1 個既有慣例警告（`services/shippingTemplate.ts` 的 `import/no-anonymous-default-export`，與 `services/address.ts`/`logistics.ts`/`auth.ts` 等既有檔案同型，非新增問題）。
- **手動功能驗證**：`make up` 啟動真實 Docker Compose 全棧（postgres+redis+backend+frontend），以 `psql` 直接建立一筆 `ACTIVE` 狀態租戶並將測試使用者 `role`/`tenant_id` 直接指向該租戶（比照 `ShippingTemplateControllerE2ETest` 建立測試租戶的方式，繞開與本輪無關的既有問題——`/tenant/apply` 走的 `TenantApplication` 審核流程在目前程式碼中不會讓申請自動變成可用租戶，見 [[m17-tenant-application-dual-flow]]），重新登入取得帶正確 `role`/`tenantId` 的 JWT 後，實際呼叫本輪新增的 4 個前端 service 方法對應的 HTTP 請求（list/create/update/delete），確認建立/查詢/修改/刪除運費模板皆正確運作，回應欄位與 TypeScript 型別完全一致（含 `FIXED`/`FREE_THRESHOLD` 兩種計費方式）。**過程中一次性插曲**：`make up` 沿用的 dev Postgres named volume 因累積自更早期版本、缺少後續 migration 新增的 `tenants.connect_charges_enabled` 等欄位（`ddl-auto=update` 未能補上此欄位，與本輪程式碼無關，登入端點本身即會 500），以 `make down-clean` 清空該 volume 重建後解決，純環境操作、無程式碼變動。
- **未新增 Playwright E2E 規格**：比照結構相同的既有頁面（`dashboard/pricing/rules`、`dashboard/returns`）現況——兩者同樣是賣家限定的設定頁，`frontend/e2e/` 目前也完全沒有任何規格造訪它們，原因相同：目前的 E2E `registerAndLogin` helper 只能取得一般買家帳號，沒有可重用的「取得一個具備 `product:create` 權限、真正 ACTIVE 的租戶測試帳號」路徑（`/tenant/apply` 走向見上），這是先於本輪已存在、範圍更大的既有基礎設施缺口，非本輪順手能解決，如實記錄而非略過不提。
- **`make validate-e2e`（既有回歸基準）**：**57 passed / 4 skipped / 0 failed**，與 Sprint 145/146/148/149 既有基準完全一致，backend 以 `ddl-auto=validate` + Flyway 成功啟動（腳本明確輸出「entity 與 Flyway schema 對齊，無漂移」），確認本輪變更無回歸。
- 後端：零檔案變動，未執行 `mvn verify`（`ShippingTemplateController`/`Service` 完全未修改，既有 `ShippingTemplateControllerE2ETest`/`ShippingTemplateServiceTest` 已覆蓋其行為）。
- schema：本輪未動任何 entity 欄位或 migration，未執行 `make validate-schema`。

---

## 7. 範圍外（延後）

- **`AnalyticsService`/`AnalyticsController` 的 Javadoc「(Mock Implementation)」字樣過時**（實際是真實 DB 查詢）：純文件字串修正，與本輪「契約漂移」性質不同，登記但不在本輪修改。
- **`AnalyticsService.getListingStats`/`getRecentActivity` 零前端呼叫點**：已完成後端、尚無 UI 消費，非缺陷（§2.2）。

---

## 8. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) v2.0 §3.2，本節為必要章節。

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | DEF-186 死碼清理（API_ENDPOINTS 重複 key） | Sprint 150 自選掃描發現 | ✅ 完成 | 移入已完成延後項目 |
| 2 | DEF-187 運費模板設定頁 | Sprint 150 自選掃描發現 | ✅ 完成 | 移入已完成延後項目 |
| 3 | 執行 `make validate-e2e` 並回填本節結果 | 本輪交付前 | ✅ 完成 | 57 passed / 4 skipped / 0 failed，與既有基準一致 |
| 4 | DEF-188 賣家訂單管理（列表+詳情+出貨，含後端租戶範圍查詢/讀取授權對稱化） | Sprint 150 §4 追加調查 | ⬜ 待排程 | **使用者已拍板排入 Sprint 151**，範圍見 §4 |
| 5 | `RELEASE_TRACKER` 回填本輪 push 狀態與雲端 CI 結果 | 本輪交付後 | ⬜ 待執行 | push 後同日回填 |
| 6 | DEF-103/104/105 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已拍板不排入排程 |
| 7 | `/dashboard/tenants/[id]/features` 顯示頁補上配額用量 | S147 §6 範圍外 | ⬜ 待排程 | 需先確認是否要做（新功能，非技術債） |
| 8 | 會員資料匯出／自助刪除帳戶補前端入口 | S149 §7 範圍外 | ⬜ 待排程 | 後端已完整，需先確認 UI 位置與是否要做 |
| 9 | OAuth 登入/連結串接 | 既有 stub（S78 記錄） | ⬜ 待排程 | 需先確認是否要做（範圍較大） |
| 10 | `AnalyticsService`/`Controller` Javadoc「Mock」字樣過時 | Sprint 150 §7 範圍外 | ⬜ 待排程 | 純文件修正，低優先級 |

---

## 9. 誠實揭露總結

- 本輪掃描角度（Address/Analytics/Logistics/ShippingTemplate）由 Agent 自選，依「尚未被涵蓋」排除法選定，非隨機挑選。
- 掃描過程中發現的兩項重大缺口（`DEF-187`/`DEF-188`）規模遠超單純的「欄位對不上」型契約漂移，**先徵詢使用者方向再動手**，未因為「反正後端都在」就自行決定蓋新頁面。
- `DEF-188` 一開始被誤判為與 `DEF-187` 同型（純前端補入口），追加調查（§4）後發現後端本身也有讀寫授權不對稱的既有疏漏，**如實更正範圍認知**而非依原始（不準確的）判斷倉促動手——這正是 Rule 1「遇到混淆時停下來」與過去 `DEF-047`「原記錄的修復目標查證後被推翻」教訓的直接應用。
- `DEF-187` 未新增 Playwright E2E 規格，如實記錄原因（既有 E2E helper 無法取得 ACTIVE 賣家租戶測試帳號，這是先於本輪存在的基礎設施缺口）而非略過不提；改以真實 Docker 環境手動功能驗證替代，過程記錄於 §6。
- 本輪零後端程式碼變動，`mvn verify`/`make validate-schema` 皆未執行——如實記錄為「本輪範圍不需要」。
