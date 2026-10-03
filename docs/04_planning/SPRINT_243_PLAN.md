# Sprint 243 Plan — 文件對齊（2/3）：M05 訂單與 M06 訂房的 API 規格依實作改寫、路由漂移守門，並修復四個以真實服務實測發現的缺陷（DEF-338／339／340／343）

**Sprint**: Sprint 243
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（順序為我的安排，屬推論）

[SPRINT_242_PLAN.md](SPRINT_242_PLAN.md) §8 排定下一步是文件對齊的第二輪（使用者在 Sprint 232 選了「比照 Sprint 203」：PRD 內文不動只加修訂註記、API／SRD 依實作改寫、FRD 補 M06／M07／M09 章、TC 加現況聲明；見 `DEF-322`、`DEF-323` (b)）：**M05 訂單、M06 訂房的 API 規格**，並把它們納入路由守門。Sprint 241 做 M03／M04，Sprint 244 做 SRD／FRD／PRD／TC／環境變數。

### 1.2 方法（沿用 Sprint 241，並加強）

- **逐端點讀 Controller、DTO、Service**，文件裡的每個宣稱（欄位、限制、狀態碼、錯誤碼、權限）都對照程式碼；權限矩陣直接讀 `RolePermissionMapping`。
- **形狀與行為的宣稱，用真實服務實測，不憑讀碼**：寫文件之前先寫一支**探針**（暫時的，不提交），對真實 PostgreSQL＋Redis＋完整 HTTP／JWT／權限鏈逐端點打（約 90 個請求），把真實回應印出來；文件裡的 JSON 範例與狀態碼都取自這份輸出。探針的結果再轉成永久的契約測試：`OrderApiRealStackIntegrationTest`（11）、`BookingApiRealStackIntegrationTest`（11）。
- **路由有沒有記載，用機械比對**：`ApiRouteDocDriftTest` 新登記 M05（`OrderController`、`CheckoutController`、`OrderPaymentController`）與 M06（`BookingController`、`BookingPaymentController`、`DashboardBookingController`）。

### 1.3 查出的差異（文件 vs 實作）

**M05 訂單**（v1.0 是 2026-04-09 的 6 個端點）

| 差異 | 說明 |
|------|------|
| 建立訂單的請求 | v1.0 寫 `items[]`、`paymentMethod`、`unitPrice`，**都不存在**：訂單從購物車結帳，請求只帶收件資訊與選填的 `storeId`／`addressId` |
| 端點 | 6 個→實際 **15 個**（含合併結帳 1、付款 7）。v1.0 的 `GET /dashboard/orders`、`PUT /dashboard/orders/:id/status` 不存在（實際是 `GET /v2/orders/tenant`、`PATCH /v2/orders/{id}/status`），漏了日誌、合併結帳與整組付款端點 |
| 冪等標頭 | v1.0 `X-Idempotency-Key`；實際 `Idempotency-Key`（UUID v4） |
| 錯誤碼 | v1.0 的 `E-4021`／`E-4022`／`E-4023`／`E-4024`／`E-4041` **全部不存在於後端** |
| 多店鋪／店鋪狀態 | 完全沒有：Sprint 237 的 `storeId`／`E-5020`、Sprint 239／242 的 `E-2010`（下單與付款）、`storeOpen` |
| 狀態機與權限 | 沒有記載：`PAID`／`REFUNDED`／`REFUNDING` 不能用 `PATCH` 指定（`DEF-245`／`DEF-301`）、取消的補償（庫存、優惠券、`REFUNDING`）、24 小時逾時自動取消、退款端點實際上只有管理員能成功 |

**M06 訂房**（v1.0 是 2026-04-28 的 6 個端點）

| 差異 | 說明 |
|------|------|
| 端點 | 6 個→實際 **11 個**：漏了 `GET /calendar`、整組付款（3）與商家端 `GET /v2/dashboard/bookings` |
| 取消與退款 | v1.0 沒有 `canceledBy`／`refundStatus`／`refundAmount`；PRD Q14 的 24 小時門檻、商家／管理員代為取消全額退款、等待自動退款（Sprint 225～227）都沒記載 |
| 多租戶 | v1.0 寫「買家只能看到自己 tenant 下的預訂」；實際訂房歸屬房源所屬的店鋪（Sprint 236），商家可讀、改、取消自己店鋪的訂房（Sprint 231） |
| 更新 | 實測：`PAID` 的訂房**不能更新**（`E-5010`），`CONFIRMED` 可以；買家沒有 `booking:update`（403），所以更新實務上是店家代改 |

### 1.4 寫文件時以真實服務實測發現並修復的缺陷（先實測，再決定修或登記）

**`DEF-338` 🟡：建立訂單／訂房／合併結帳的回應（與冪等重放存下的回應），`tenantId`／`userId`／`items[].listingId`／`roomListingId` 是 `null`。** 探針的 `POST /v2/orders` 得到 `"tenantId":null,"userId":null,"items":[{"listingId":null…}]`，`GET` 詳情卻是對的。根因與 Sprint 115 的第四陷阱相同：這些是 `insertable = false` 的**唯讀影子欄位**，只有從資料庫載入才有值，同一個持久化脈絡剛建立的實體是 `null`，回應轉換直接讀它。**修法在實體**：`Order`（`tenantId`／`userId`）、`OrderItem`（`listingId`／`skuId`）、`Booking`（`tenantId`／`userId`／`roomListingId`）的 getter 在影子欄位沒有值時退回關聯的 id；載入自資料庫的實體行為不變（影子欄位有值就用它）。一處修好，回應轉換、稽核、日後任何讀這些欄位的程式都受益，不必逐一處理。

**`DEF-339` 🟡：列表的 `sortBy`／`sortDir` 亂填回 `500 E-9900`。** `GET /v2/orders`、`/v2/orders/tenant`、`/v2/bookings`、`/v2/dashboard/bookings` 用 `Sort.by(Direction.fromString(sortDir), sortBy)` 直接吃使用者輸入：`sortDir` 不是 asc／desc 時丟未攔截的 `IllegalArgumentException`，`sortBy` 不是實體屬性時 Spring Data 在執行查詢時丟 `PropertyReferenceException`，都落入 catch-all。與 Sprint 241 的 `DEF-335` 同型。**修法**：`PageableUtils.sortOf(sortBy, sortDir, 允許清單)`，不合法一律 `400 E-9000`；訂單允許 `createdAt`／`updatedAt`／`totalAmount`／`status`，訂房另加 `checkInDate`／`checkOutDate`——**個資欄位（收件電話、住客 Email…）不在清單內**，所以也不能拿來排序。前端只用 `createdAt`／`DESC`，不受影響。其餘三處（`/v2/listings`、`/v2/products`、`/v2/rooms`）同型，**實測 6 個探針全部 500**，見 `DEF-344`（登記，未修）。

**`DEF-340` 🟡：`POST /v2/bookings` 入住日＝退房日回 `201`，建出 0 晚、0 元的訂房。** `BookingService.resolveBookableRoom` 與 `handleDateChange` 用 `checkOut.isBefore(checkIn)`，同一天通過；DTO 的 `@FutureOrPresent`／`@Future` 也擋不住「兩者同為未來的同一天」。探針得 `201`、`nightsCount 0`、`totalAmount 0.00`（購物車對同一情形本來就回 `E-4004`、舊的 ROOM 訂單路徑用 `nights <= 0` 也擋）。任何有 `booking:create` 的人都能製造無限多筆 0 元訂房污染店家列表，也能「付款」。**修法**：建立與更新都改成 `!checkOut.isAfter(checkIn)` → `E-4003`。

**`DEF-343` 🟡：預訂建立後 60 秒內，同房源同日期無法再被訂。** 寫取消契約測試時發現：取消後立刻讓別的買家重訂同一段日期回 `E-4001`，但日曆列已是 `AVAILABLE`、`availability` 也回 `available: true`。後端日誌指出 `lockDateRangeNoWait: Failed to acquire lock … Date range is being modified by another user`。**根因**：`RedisLockService.forceReleaseLock(resourceId)` 自己會加 `lock:` 前綴，而 `RoomCalendarService.unlockDateRange` 傳進去的鍵已經帶了 `lock:`，刪掉的是不存在的 `lock:lock:room:…`——**日期鎖從來沒有被釋放**，要等 60 秒 TTL。取鎖失敗的回滾（`acquiredLocks`）有同一個錯誤。**後果**：買家訂了又取消、立刻重訂（或別人訂那一段）會得到令人困惑的「日期衝突」；改期到與原日期重疊的日期同樣被擋 60 秒。**為什麼 200+ 個 Sprint 沒被發現**：`RoomCalendarServiceTest` 以 mock 的 `RedisLockService` 驗證，而且**期望的就是錯的鍵**（`"lock:" + 資源 ID`，把缺陷固定成預期行為）；整合測試的 `IntegrationTestConfiguration` 又把 `RedisLockService` 換成 mock，只有真實 Redis 看得到鍵有沒有真的被刪。**修法**：傳資源 ID（不含前綴）；單元測試改成正確的鍵；新增 `RoomCalendarLockRealRedisIntegrationTest`（真實 Redis：鎖定後每晚都有鍵、解鎖後真的消失、部分取鎖失敗時已取得的鎖被回滾）。`lockDateRangeWithWait` 有同樣的前綴問題，我一併改了，但它**沒有任何呼叫者**（死碼），所以沒有測試也沒有被驗證。

### 1.5 另外登記（寫文件時發現，未修）

| 編號 | 內容 |
|------|------|
| `DEF-341` 🟢 | 兩個預設訊息誤導：日曆參數無效（區間 > 92 天、結束早於開始）回 `E-3001`「無效的刊登類型」；Stripe 未啟用回 `E-6002`「付款已取消」。錯誤碼可用、文字與實際原因不符 |
| `DEF-342` 🟢 | 訂單／訂房列表的 `sortBy`／`sortDir` **實際上沒有作用**：repository 方法名寫死 `OrderByCreatedAtDesc`，Pageable 的排序只是次要排序（我寫的契約測試第一版期望 `totalAmount asc` 先出現最便宜的，被它打臉）。測試刻意**不斷言順序**，免得把缺陷固定成正常行為 |
| `DEF-344` 🟢 | `/v2/listings`、`/v2/products`、`/v2/rooms` 的 `sortBy`／`sortDir` 亂填同樣 500（M01／M02；三處的欄位對應各不相同，留待 M01／M02 文件對齊時一併處理，`PageableUtils.sortOf` 可重用） |

### 1.6 更正與偽陽性（我自己的錯）

- 我在探針之前**假設**店主可以退款（`order:update` 看起來夠用）。實測店主與買家都是 `403 E-1007`：退款要求 `order:update` **且**「訂單本人或管理員」——買家是本人卻沒有 `order:update`，店主有 `order:update` 卻不是本人，**實際上只有管理員能退**。文件如實記載（§6），沒有改行為（是否該讓店主退款是產品決定）。
- 我在 M06 文件第一版寫日期格式錯誤回 `E-9007`，實測是 `E-9000`（`MethodArgumentTypeMismatchException` 的處理器）；已更正並補契約斷言。
- 兩個取消案例第一次失敗，我先懷疑測試時序，查日誌才確認是 `DEF-343`——**「剛取消的日期不能重訂」差點被我當成測試不穩定放過**。
- 突變驗證的第一輪有 1 個存活（`MS3`：把個資欄位加進允許排序清單沒有任何測試察覺）；補契約斷言「實體上真的存在、但不在允許清單的欄位（收件電話、住客 Email…）不能排序」後 2 個相關突變都被抓到。

## 2. 使用者決策與需要使用者知悉的行為變更

本輪沒有新的使用者決策（依 Sprint 232 Q4「比照 Sprint 203」）。行為變更：

1. **訂房 `check-in == check-out` 從 `201` 變 `400 E-4003`**（`DEF-340`）。
2. **列表 `sortBy`／`sortDir` 不合法從 `500` 變 `400 E-9000`，且不在允許清單的欄位（含個資欄位）不能排序**（`DEF-339`）。前端只用 `createdAt`／`DESC`，不受影響；直接打 API 並依其他欄位排序的呼叫者會得到 `400`——但那些請求原本就**不會**真的排序（`DEF-342`）。
3. **建立訂單／訂房／合併結帳的回應現在帶 `tenantId`／`userId`／`listingId`／`roomListingId`**（原本是 `null`，`DEF-338`）。
4. **預訂成立後同日期不再被鎖 60 秒**（`DEF-343`）：取消後立刻重訂、改期到重疊日期現在可行。
5. M05、M06 規格文件改寫（文件，不動資料）；新增守門測試。

## 3. 實作內容

**程式碼**
- `Order`／`OrderItem`／`Booking`：影子欄位 getter 退回關聯 id（`DEF-338`）。
- `PageableUtils.sortOf`（新）；`OrderService`（2 處）、`BookingService`（2 處）改用，各自帶允許清單（`DEF-339`）。
- `BookingService.resolveBookableRoom`／`handleDateChange`：`isBefore` → `!isAfter`（`DEF-340`）。
- `RoomCalendarService`：`unlockDateRange`、`lockDateRangeNoWait`／`lockDateRangeWithWait` 的回滾，傳資源 ID 不含 `lock:` 前綴（`DEF-343`）。
- **收斂 Sprint 242 的 `PaymentStoreGuard.storeIdOf`**：它是為了同一個影子欄位陷阱加的第二套機制（關聯優先、退回影子欄位）；實體 getter 修好後它重複了，且它的單元測試斷言的前提（「影子欄位是 `null`」）已不成立（全量驗證第一輪就是它失敗）。移除 `storeIdOf`，付款守門改回讀 `order.getTenantId()`／`booking.getTenantId()`；Sprint 242 的接線測試（只有關聯的訂單，守門用的是關聯的店鋪）與 `M07PaymentMockIntegrationTest` 繼續守住，並由 MD1～MD5 的實體 getter 突變涵蓋。
- `PaymentStoreGuard`／`PaymentService` 的註解不再引用外部記憶檔。

**文件**
- [API_M05_Order.md](../02_architecture/api/API_M05_Order.md) → **v2.0**（全文改寫，15 個端點）。
- [API_M06_Booking.md](../02_architecture/API_M06_Booking.md) → **v2.0**（全文改寫，11 個端點）。
- [API_Index.md](../02_architecture/API_Index.md) → v1.3（M05 改寫、新增 M06 區塊、完整性揭露）。

**測試**
- `ApiRouteDocDriftTest`（登記 M05、M06 共 6 個 Controller）。
- `OrderApiRealStackIntegrationTest`（11）、`BookingApiRealStackIntegrationTest`（11）、`ContractFixture`（共用的真實資料）、`RoomCalendarLockRealRedisIntegrationTest`（2）。
- 單元：`ShadowFieldGetterTest`（4）、`PageableUtilsTest`（+3）、`BookingServiceCreateBookingTest`／`BookingServiceUpdateDateChangeTest`（各 +1）、`RoomCalendarServiceTest`（兩個期望改為正確的鍵）。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元 | 見 §3。同日入退房建立／更新都 `E-4003` 且不鎖日曆、不建立訂房；影子欄位 getter（只有關聯→取關聯 id、影子欄位有值→用它、兩者皆無→null）；`sortOf`（合法值、不在清單的欄位含 `null`／空白／`createdAt,desc`／`user.passwordHash`、方向不是 asc／desc） |
| 真實 PostgreSQL＋Redis＋完整 HTTP 鏈（+24） | `OrderApiRealStackIntegrationTest`：建立（回應帶店鋪／買家／商品 id，與 GET 一致；購物車被清）、冪等（重送 200 同一張、`E-9004`）、建立錯誤（`E-5004`／`E-3001`／`E-9000`／401）、列表（Page 形狀、size 上限 100、`sortBy`／`sortDir`／個資欄位 → `E-9000`）、詳情權限（本人／店主／管理員 200、別人 403、404、401）、店家列表（店主看得到、買家空頁、`status` 篩選、`E-5001`）、`PATCH status`（`CONFIRMED`／`PAID`／`REFUNDING`／亂填 → `E-5001`、缺欄位 `E-9000`、買家 403、付款後推進到 `SHIPPING`）、日誌、取消（店主／別人 403、已取消／已出貨 `E-5002`、404）、付款狀態與 Mock 付款（形狀、重複 `E-5011`、別人 403、失敗只記錄、Stripe 未啟用 `E-6002`）、退款（買家與店主 403、管理員部分／全額、超額與超過兩位小數 `E-6009`）、合併結帳。`BookingApiRealStackIntegrationTest`：可用性（可訂／已訂走／無效區間 200＋原因碼／非房源／不存在／缺參數／日期格式）、日曆、建立（回應帶 id、付款期限約 24 小時、15:00／11:00）、建立錯誤（衝突 `E-4001`、人數 `E-4005`、**同一天 `E-4003`**、反向、過去日期、缺欄位、非房源、不存在）、冪等、列表／排序／個資欄位、詳情權限、更新（買家 403、店主改住客與人數、超額、改成被訂走的日期、**改成 0 晚**、付款後 `E-5010`）、**取消後日期立刻可再訂**（`DEF-343`）、取消與退款政策（≥24h 全額 `PENDING`、不足 24h `NONE`、店主代為取消 `MERCHANT` 全額）、付款。`RoomCalendarLockRealRedisIntegrationTest`：鎖定後每晚都有鍵、解鎖後真的消失且立刻可再鎖、部分取鎖失敗時已取得的鎖被回滾而別人的鎖不被動到 |

### 突變驗證（Rule 9：測試必須在被守的東西被拿掉或改錯時失敗）

**程式碼（19 個，跑上述單元＋契約測試共 88 個）——全部被抓到**

| # | 突變 | 結果 |
|---|------|------|
| MK1 | `unlockDateRange` 改回傳 `lock:room:…`（`DEF-343` 原缺陷） | 4 個轉紅 |
| MK2 | 取鎖失敗的回滾改回 `"lock:" + 鍵` | 2 |
| MB1 | `resolveBookableRoom` 改回 `isBefore`（同一天通過，`DEF-340`） | 2 |
| MB2 | `handleDateChange` 改回 `isBefore` | 2 |
| MS1 | `sortOf` 方向改回 `Direction.fromString`（`DEF-339`） | 3 |
| MS2 | `sortOf` 不檢查允許清單 | 3 |
| **MS3** | 訂房允許排序清單加入 `guestEmail`（個資） | **第一輪存活**（沒有測試察覺）→ 補契約斷言後 1 個轉紅 |
| MS4 | 訂單允許排序清單加入 `shippingPhone`（個資） | 1 |
| MD1 | `Order.getTenantId` 改回只讀影子欄位（`DEF-338`） | 4 |
| MD2 | `Order.getUserId` 同 | 2 |
| MD3 | `OrderItem.getListingId` 同 | 3 |
| MD4 | `Booking.getRoomListingId` 同 | 3 |
| MD5 | `Booking.getTenantId` 同 | 4 |
| MC1 | 拿掉 `PATCH status` 對 `PAID`／`REFUNDED` 的封鎖（契約測試的牙齒） | 1 |
| MC2 | 拿掉退款的「本人或管理員」檢查（店主就能退款） | 1 |
| MC3 | 退款政策：買家 < 24 小時也全額退款 | 1 |
| MC4 | 取消方一律記成買家 | 1 |
| MC5 | 更新放行 `PAID` 的訂房 | 1 |
| MC6 | 拿掉建立時的人數上限檢查 | 2 |

**文件（10 個，對 `ApiRouteDocDriftTest`；改文件→跑守門→還原）——全部被抓到**：DM6 拿掉退款端點的章節標題、DM7 多宣告不存在的 `GET /v2/orders/export`、DM8 路徑變數改名 `{id}`、DM9 拿掉商家端訂房列表、DM10 拿掉日曆、DM11 多宣告不存在的 `DELETE /v2/bookings/{bookingId}`、DM12 拿掉屬 M06 但路由在訂單付款控制器的訂房付款狀態、DM13 前綴碰撞（拿掉 `GET /v2/orders` 標題但 `/v2/orders/tenant` 還在）、DM14 拿掉 Stripe 回跳、DM15 拿掉合併結帳。

每次突變都先備份源碼、等 class 比源碼新才跑、跑完還原並以 `cmp`／`sha256` 確認逐位元組一致。

## 5. 驗證結果

- **後端全量**：`mvn -o clean verify` **BUILD SUCCESS（19 分 18 秒）**——單元 **2110**（+8：`ShadowFieldGetterTest` 4、`PageableUtilsTest` +3、訂房建立／更新各 +1，再減去移除 `storeIdOf` 的 1 個）／整合 **792**（+24：`OrderApiRealStackIntegrationTest` 11、`BookingApiRealStackIntegrationTest` 11、`RoomCalendarLockRealRedisIntegrationTest` 2）／**0 失敗**／0 略過。**第一輪全量有 1 個失敗**：Sprint 242 的 `PaymentStoreGuardTest.storeIdOf_…` 斷言的前提（影子欄位是 `null`）被本輪的實體 getter 修復推翻——不是新缺陷，而是兩套機制並存；收斂到實體 getter、移除 `storeIdOf` 後重跑全綠（見 §3）。
- **`make validate-schema-doc`**：本輪沒有新遷移、沒有 schema 變動，未重跑（Sprint 242 的 90 個遷移通過仍成立）。
- **前端**：沒有改動（沿用 Sprint 242 還原後重建的版本）。
- **真實後端 E2E**：`E2E_GATE_SKIP_BUILD=1 make validate-e2e`（JAR 是上面 verify 剛建好的版本）**142 個測試：138 通過／4 略過／0 失敗（4.0 分鐘）**，後端以 `ddl-auto=validate` 啟動確認 entity 與 Flyway schema 對齊。那 4 個略過是既有基準。
- **突變驗證**：見 §4（程式碼 19＋文件 10，全部被抓到；`MS3` 第一輪存活、補契約斷言後抓到）。
- **push 與雲端 CI**：（待回填）

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **路由守門只守「端點有沒有記載」**：欄位形狀、狀態碼、錯誤碼的細節由契約測試與人工維護。M05／M06 的細節都有真實服務的契約測試；**Stripe 路徑**（發起、回跳、webhook、退款）的行為以 Mock／WireMock 驗證，**從未對真實 Stripe 驗證**（沿用 Sprint 207／226／227 的揭露）。
2. **`lockDateRangeWithWait` 是沒有呼叫者的死碼**：前綴問題我一併改了，但沒有測試也沒有被驗證。
3. **M05 §7.7 訂房付款狀態的路由在訂單付款控制器**，守門以 M05 為準宣告；M06 文件只連結過去。
4. **範圍外的 M06 相鄰端點**：房東維護日曆（`RoomCalendarController`）、訂房評價（`BookingReviewController`）、舊版 `POST /v2/payments` 與 Stripe／LINE Pay webhook（`/v2/payments/webhook/*`，M07）都沒有納入本輪守門與文件。
5. **`API_Index.md` 仍不是完整端點清單**（`DEF-286`）；M03／M04／M05／M06 已完整且有守門。

### 6.2 待使用者決定／確認（累積）

| 項目 | 內容 | 我的建議 |
|------|------|----------|
| 本輪新增 `DEF-342` | `sortBy`／`sortDir` 對訂單／訂房列表實際上沒有作用 | 讓 repository 改用不寫死排序的查詢（`findByUserId(…, Pageable)`），讓參數真的生效；或乾脆從 API 移除這兩個參數。前端只用預設，沒有急迫性 |
| 退款入口（§1.6） | 店主沒有退款端點，只有管理員能退（取消已付款訂單後由排程自動退款） | 維持現狀；若店主需要「部分退款」才需要設計權限 |
| Sprint 242 `DEF-337` | 停權→恢復營業不會自動重新上架 | 見 [SPRINT_242_PLAN.md](SPRINT_242_PLAN.md) §6.2 |
| Sprint 242 `DEF-333`／V90 | **部署前請先跑 V90 檔頭的三個預覽 `SELECT` 看筆數** | — |

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果。
2. **Sprint 244**：SRD（訂單狀態圖、token 效期與 Cookie、寄信 Mock 的過時說法）、FRD 補 M06／M07／M09 章、PRD 修訂註記、TC 文件現況聲明、環境變數文件（只改文件；compose 傳遞環境變數屬 Docker 設定，依 CLAUDE.md 須使用者明確指示）。
3. 之後：店鋪成員管理前端（`DEF-321` (a)）→ CMS 卡片連結（`DEF-321` (c)）→ 通知事件（`DEF-318`）→ 店鋪前台 `/stores`（`DEF-321` (b)）；M01／M02 的 API 文件對齊（含 `DEF-344`）。
