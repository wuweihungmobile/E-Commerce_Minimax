# Sprint 236 Plan — 訂房歸屬「房源所屬的店鋪」（DEF-319 訂房側）

**Sprint**: Sprint 236
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（順序為我的安排，屬推論）

使用者在 Sprint 232 結尾以互動選擇拍板 `DEF-319` 的修法為「同店結帳」（訂房蓋房源所屬店鋪；訂單蓋商品所屬店鋪，購物車含多家店商品時要求分開結帳；Flyway 回填歷史資料；一併決定促銷碼與運費模板以哪個租戶解析）；我把它視為授權依序完成（推論）。前置的 `DEF-325`／`DEF-327`／`DEF-328`／`DEF-329` 已在 Sprint 234～235 處理完（`DEF-329` 必須先於本項：本項做完後真實店鋪租戶會擁有真實消費者的資料，被移除的店員不能再帶著店鋪租戶，見 [SPRINT_235_PLAN.md](SPRINT_235_PLAN.md)）。

本輪只做**訂房側**，訂單側與合併結帳留給 Sprint 237。理由（推論）：訂房是單一房源、沒有購物車與拆單問題，修法最小、可以先把「蓋哪個租戶」與回填資料的做法在小範圍驗證；訂單側要處理購物車以店鋪為單位、運費模板、前端分店結帳與 `orders.tenant_id` 回填，影響面大得多。**合併結帳（`CombinedCheckoutService`）同時產生訂單與訂房、共用一個促銷碼，「以哪個租戶解析」必須與訂單側的同店規則一起設計，所以它的訂房那一半本輪不動**（仍蓋買家租戶，Sprint 237 處理）。

PRD 對「同店」有直接依據：US-008（§17.3.2）「單筆訂單可包含來自同一商家的多個品項；Phase 1 不支援跨商家訂單（PC-005）」；PC-005（§15.1.3）「Phase 1 不支援跨商家／跨品項優惠」。促銷活動是店鋪層的設定（§4.4：店主／賣家建立促銷活動要查 `PROMO_ENABLED`）。

### 1.2 修復前的行為與本輪改動

- **修復前**：`BookingService.createBooking` 取 `TenantContext.getCurrentTenant()`（下單者的租戶）當訂房的租戶，**同一個值也拿去解析促銷碼**。沒有店鋪的一般消費者的租戶是系統租戶佔位值，所以他們的訂房全部蓋成系統租戶；商家端訂房管理（Sprint 231）對這些訂房永遠是 0 筆，店主讀不到也處理不了（Sprint 232 已用真實 JAR＋PostgreSQL＋Redis＋兩個真實角色實測，見追蹤表 `DEF-319`）。促銷碼則是反過來：店鋪建立的券，沒有店鋪的消費者永遠用不到（突變 M1 還原舊行為時實測：`E-5007 無效的優惠碼`）；而 A 店成員卻能拿 A 店的券去訂 B 店的房（依程式碼推論，未實測）。
- **本輪**：訂房的租戶改為**房源所屬的店鋪**（`listing.getTenantId()`），促銷碼用同一個租戶解析。取消訂房時退還額度是依用券紀錄（`promo_code_usages.booking_id`）找券，與租戶無關；改日期重算折扣是依 `booking.tenantId` 找券，所以「蓋章的租戶」與「解析促銷碼的租戶」必須是同一個——本輪兩處同步改。
- **歷史資料**：`V88` 把訂房的租戶與房源租戶不一致者回填成房源的租戶（§3）。

### 1.3 測試固件發現的另一個問題（新登記 `DEF-332`）

把 `createBooking` 改成讀 `listing.getTenantId()` 後第一次跑 `BookingControllerE2ETest`：**25 個案例 11 個回 500**（`IllegalArgumentException: The given id must not be null`）。原因不在正式程式：該測試的固件用 `Listing.builder().tenantId(…).ownerId(…)` 建立房源——`tenantId`／`ownerId` 是 `insertable = false` 的**唯讀影子欄位**，寫入資料庫的是 `tenant`／`owner` 關聯；只設影子欄位，`listings.tenant_id` 就存成 NULL。正式資料庫（Flyway）這個欄位是 `NOT NULL`，**測試資料庫卻是可為 NULL**（`information_schema` 實測：`tenant_id`、`owner_id` 皆 `is_nullable = YES`；測試庫由 Hibernate 依實體產生，我推測是 `@JoinColumn(nullable = false)` 與同一欄位的影子欄位 `@Column`（預設可為 NULL）併成一個欄位時取了後者，**未驗證**）。所以這類固件會默默存成 NULL，直到有程式碼讀它才爆。

固件補上 `.tenant(…)`／`.owner(…)` 後 25/25 通過。我另外掃描了全部測試：`Listing.builder()` 只設 `.tenantId(` 而沒設 `.tenant(` 的有 **40 處，其中 19 處在會連真實資料庫的測試**（掃描法見 §6.1 第 4 點）；商品／訂單相關的一批在 Sprint 237 修訂單側時會直接踩到。登記為 `DEF-332`，建議隨 Sprint 237 處理。

## 2. 使用者決策與需要使用者知悉的行為變更

本輪沒有新的使用者決策（`DEF-319` 的修法已在 Sprint 232 結尾拍板）。有三個**行為變更**（我的判斷，請使用者否決或調整）：

1. **`V88` 資料遷移會改既有資料庫的訂房**：訂房的租戶 ≠ 房源所屬租戶者，一律改成房源的租戶；不動其他欄位，可重複執行。正式環境部署前可先用同一個條件看會影響多少筆：`SELECT b.id, b.tenant_id AS booking_tenant, l.tenant_id AS listing_tenant FROM bookings b JOIN listings l ON l.id = b.room_listing_id WHERE b.tenant_id <> l.tenant_id`。
2. **店主與店員從此看得到真實消費者在自己店鋪的訂房**（店主另可取消、更新；店員只有讀取權限），包含訂房人姓名、電話、Email、特殊需求等個資——這是修復的目的（Sprint 231 的商家端訂房管理），但也是第一次發生，請確認店鋪端的個資處理告知是否到位。修復前這些訂房歸在系統租戶，沒有任何店鋪看得到。
3. **促銷碼改以房源所屬店鋪解析**：店鋪建立的券，沒有店鋪的消費者第一次真的能用在這家店的房源；反過來，A 店成員不能再拿 A 店的券去訂 B 店的房（原本可以，依程式碼推論）。

## 3. 實作內容

- `BookingService.createBooking`：`tenantId` 由 `getCurrentTenant()` 改為 `listing.getTenantId()`；`PromoService.resolveValidPromoForCheckout` 與 `buildBookingCore`（訂房的租戶、`BOOKING_CREATED` 稽核紀錄的租戶）都用這個值。`buildBookingCore` 的簽章不變（`CombinedCheckoutService` 仍傳買家租戶，Sprint 237 處理）。
- `V88__Backfill_Booking_Tenant_To_Listing_Store.sql`：`UPDATE bookings b SET tenant_id = l.tenant_id FROM listings l WHERE b.room_listing_id = l.id AND b.tenant_id <> l.tenant_id`。不變量：`bookings.tenant_id` 等於其房源的 `tenant_id`。付款、評價、用券紀錄沒有自己的 `tenant_id`（經由訂房或房源取得），稽核紀錄是當時的歷史事實，所以只需要改這一欄。
- `frontend/e2e/at-seller-dashboard-real.spec.ts`：E2E-SDASH-03／04 移除 `test.fail()` 轉為正常斷言（訂單側的 SDASH-07／08 仍標記，Sprint 237 移除）。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元（+2） | `BookingServiceCreateBookingTest`：沒有店鋪的買家（系統租戶）訂房→訂房與稽核都蓋成**房源所屬店鋪**；`BookingPromoCodeTest`：同一情境下促銷碼以**房源所屬店鋪**解析（用買家租戶解析會找不到券）。既有兩個測試類的固件補上房源租戶（機械式更新） |
| HTTP＋真實 PostgreSQL（+2） | `BookingControllerE2ETest` API-M06-020：沒有店鋪的真實消費者訂房→訂房歸屬房源店鋪（店主端列表與詳情看得到、訂房人自己仍讀得到、另一位沒有店鋪的消費者看不到且詳情 403）；API-M06-021：消費者用店鋪的促銷碼訂房→折扣照常套用，**店主**改日期重算後折扣仍在（改日期要 `booking:update`，一般買家沒有，是店主端動作）。固件修正見 §1.3 |
| 遷移邏輯（+2） | `BackfillBookingTenantMigrationIntegrationTest`：讀 `V88` 的實際 SQL 對種好的資料執行（交易內、結束回滾）：消費者的訂房（租戶≠房源租戶）與 A 店成員訂 B 店的房都被搬到房源所屬店鋪；本來就一致者、別家店房源的訂房一律不動；只改租戶，狀態／金額／訂房人不變；可重複執行。Flyway 本身能否套用由 `make validate-schema-doc` 驗證 |
| 真實後端 Playwright | `at-seller-dashboard-real.spec.ts` SDASH-03（店主的訂房列表看得到消費者訂的這筆）、SDASH-04（店主開啟並取消，全額退款、取消方為商家）：**修復前以 `test.fail()` 標記為應失敗**（Sprint 232 實測），本輪移除標記後 `make validate-e2e` 驗證 |

### 突變驗證（Rule 9：測試必須在守門被拿掉時失敗）

| # | 突變 | 結果 |
|---|------|------|
| M1 | `createBooking` 的訂房租戶改回 `getCurrentTenant()`（＝修復前的行為） | **4 個測試轉紅**：單元 `createBooking_tenantlessBuyer_isStampedWithTheRoomsStoreTenant`、`tenantlessBuyer_promoIsResolvedInTheRoomsStoreTenant`；E2E API-M06-020、API-M06-021（促銷碼 `E-5007 無效的優惠碼`） |
| M2 | 只把促銷碼解析改回 `getCurrentTenant()`（訂房租戶維持修復後） | **2 個轉紅**：`tenantlessBuyer_promoIsResolvedInTheRoomsStoreTenant`、API-M06-021 |
| V1 | `V88` 拿掉 `AND b.tenant_id <> l.tenant_id` | `isIdempotent` 轉紅（第二次仍更新所有列） |
| V2 | `V88` 的 `SET tenant_id = l.tenant_id` 改成 `= b.tenant_id`（等於沒改） | 兩個測試皆轉紅 |

每次突變都先備份源碼、突變後等 class 比源碼新才跑、跑完還原並以 `cmp` 確認逐位元組一致（VS Code Java 語言服務會在約 1 秒內自己編好，Maven 印 `Nothing to compile` 不代表突變沒生效）。

## 5. 驗證結果

- **後端**：全量 `mvn -o clean verify` **BUILD SUCCESS（16 分 22 秒）**：單元 **2020**（+2）／整合 **711**（+4）／**0 失敗**／0 略過；checkstyle（main＋test）0 違規；PMD 通過。（Sprint 235：2018／707，15 分 10 秒。）驗證用的 JAR 是在最後一次修改 `createBooking` 的**註解**（更正我自己寫錯的兩處：PRD 章節編號應是 §4.4 而不是 §9；以及「取消時查券」應是「改日期重算折扣時查券」）之前建置的——註解不影響行為，改完後另以 `mvn -o test` 重跑 `BookingServiceCreateBookingTest`、`BookingPromoCodeTest`、`BookingControllerE2ETest`、`BackfillBookingTenantMigrationIntegrationTest` 共 54 個測試，全數通過。
- **Schema 文件**：`make validate-schema-doc` 對乾淨 PostgreSQL（postgres:18-alpine）套用 **88 個** Flyway 遷移（含 `V88`）通過，SRD／PRD 的 DDL 與實際 schema 一致（`V88` 是純資料遷移，不改 schema）。
- **真實後端 E2E**：`make validate-e2e`（沿用 `mvn verify` 剛建的 JAR；前端 `src` 本輪沒有變動，沿用既有建置）**129 個測試：125 通過／4 略過／0 失敗（3.4 分鐘）**。`at-seller-dashboard-real.spec.ts` 的 SDASH-03、04 移除 `test.fail()` 後**以正常斷言通過**（店主的訂房列表看得到消費者訂的這筆、店主開啟並取消→全額退款且取消方為商家；測試結果目錄裡這兩個案例沒有失敗證物），SDASH-07、08（訂單側）仍是「預期失敗」（`error-context.md` 仍在）。那 4 個略過是既有基準（Sprint 231～235 皆為 4 個）。
- **前端**：`tsc --noEmit`、`eslint`（`at-seller-dashboard-real.spec.ts`）皆通過；前端 `src` 本輪沒有變動。
- **push 與雲端 CI**：（push 後於回填 commit 補上）
- **耗時觀察（承 Sprint 233～235，未歸因）**：本機全量 `mvn verify` 本輪 16 分 22 秒（Sprint 235：15 分 10 秒；Sprint 234：17 分 19 秒；Sprint 233：15 分 21 秒）。雲端整合 job 近七次為 5m01s／6m02s／6m00s／4m15s／7m47s／7m46s／8m03s（Sprint 235），本輪 push 後的數字是第四個資料點。

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **合併結帳的訂房那一半仍蓋買家租戶**（`CombinedCheckoutService`）：要與訂單側的同店規則一起設計（購物車是否含商品與房源、跨店怎麼辦、單一促銷碼由哪個租戶解析），Sprint 237 處理。在那之前，經由合併結帳建立的訂房仍會落在系統租戶。
2. **`V88` 的邊角**：修復前「A 店成員拿 A 店的促銷碼訂 B 店的房」留下的訂房，回填後屬於 B 店；之後若改日期重算折扣，依 `booking.tenantId` 找不到 A 店的券，折扣會歸零（與一般「券已不存在」的處理相同）。這種資料是修復前的錯誤使用方式留下的，我選擇不為它保留跨店歸屬；實際筆數未知（部署前可用 §2 的 `SELECT` 加上 `AND b.promo_code IS NOT NULL` 查）。
3. **`BookingService` 自己那份 `SYSTEM_TENANT_UUID` 常數仍在**（`checkBookingOwnership` 用它排除系統租戶）：收斂為 `TenantContext.isStoreTenant` 是 `DEF-330` (b)，與 `OrderService`、`LogisticsService` 的兩份一起在 Sprint 237 做。
4. **固件掃描法（`DEF-332`）**：以腳本找出 `Listing.builder()…build()` 區塊內含 `.tenantId(` 但不含 `.tenant(` 者，再依該檔是否含 `@SpringBootTest` 或 `IntegrationTestConfiguration` 分成「會連真實資料庫」與「純 mock」。這是文字比對，沒有逐一判斷每處的 Listing 最後有沒有存進資料庫，所以 19 是**上限**，不是確定會踩到的數量。
5. **店主端「改日期」的權限**：`PUT /v2/bookings/{id}` 要 `booking:update`，一般買家沒有，所以消費者無法自己改日期（只有店主端能改）。這是既有行為，本輪沒有動，也沒有確認 PRD 是否要求買家能改日期。

### 6.2 待使用者決定／確認（累積）

| 項目 | 內容 | 我的建議 |
|------|------|----------|
| `DEF-326` | SELLER／HOST 自助註冊後落在系統租戶，持有商品／房源／定價／運費／CMS／貼文寫入權限，彼此與平台自營資料互通（[SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §6.1） | 未歸屬店鋪者不得寫入 |
| Sprint 234 §6.4 | 沒有店鋪的使用者的限流單位（已登入→使用者、匿名→來源 IP，容量 100 次／分）是工程決策，PRD 未定義 | 同意（有疑慮可調整容量或加全域上限） |
| Sprint 235 §2 | `V87` 會改既有資料庫的使用者角色；Stripe Connect 端點由 `SELLER` 改為 `STORE_OWNER` | 同意（部署前先跑 §2 的 `SELECT` 看影響人數） |
| 本輪 §2 | `V88` 會改既有資料庫的訂房租戶；店主／店員第一次看得到消費者訂房的個資；促銷碼改以店鋪租戶解析 | 同意（部署前先跑 §2 的 `SELECT` 看影響筆數） |

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果。
2. **Sprint 237：DEF-319 訂單側**：`OrderService.createOrderFromCart`／`buildProductOrder` 蓋商品所屬店鋪、購物車含多家店商品時要求分開結帳（PRD US-008／PC-005 支持）、促銷碼與運費模板以店鋪租戶解析、`orders.tenant_id` 的 Flyway 回填；合併結帳（`CombinedCheckoutService`）一併處理；前端結帳流程依店鋪拆開；SDASH-07／08 移除 `test.fail()`；收斂三份 `SYSTEM_TENANT_UUID`（`DEF-330` (b)）；處理 `DEF-332`（固件補關聯，並考慮讓測試庫也是 `NOT NULL`）。
3. 之後：Sprint 238～239 文件對齊（`DEF-322`、`DEF-323` (b)），Sprint 240 店鋪成員管理前端，Sprint 241 CMS 卡片連結（`DEF-321` (c)），Sprint 242 通知事件（`DEF-318`），Sprint 243～244 店鋪前台 `/stores`。
4. **待使用者**：§6.2 的四項；Sprint 237 結束時我會再用互動選擇請使用者一併確認。
5. 選配：整合測試的 bcrypt 強度（先量測，見 [SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §5、[SPRINT_235_PLAN.md](SPRINT_235_PLAN.md) §5）。
