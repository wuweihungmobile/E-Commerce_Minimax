# Sprint 237 Plan — 訂單歸屬「商品所屬的店鋪」並限同店結帳（DEF-319 訂單側＋合併結帳，DEF-330 (b)、DEF-332）

**Sprint**: Sprint 237
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（順序為我的安排，屬推論）

使用者在 Sprint 232 結尾以互動選擇拍板 `DEF-319` 的修法為「同店結帳」（訂房蓋房源所屬店鋪；訂單蓋商品所屬店鋪，購物車含多家店商品時要求分開結帳；Flyway 回填歷史資料；一併決定促銷碼與運費模板以哪個租戶解析）；我把它視為授權依序完成（推論）。[SPRINT_236_PLAN.md](SPRINT_236_PLAN.md) 做完訂房側，本輪做**訂單側與合併結帳**，一併收斂 `DEF-330` (b)（三份 `SYSTEM_TENANT_UUID` 複本）並處理 `DEF-332`（測試庫的房源租戶欄位可為 NULL）。

**前端的分店鋪購物車與結帳畫面留給 Sprint 238**（我的安排）：本輪後端自成一體——`storeId` 都是選填，購物車只有一家店鋪的商品時（既有使用方式）前端完全不必改；含多家店鋪時，後端回明確的 `E-5020`，要等 Sprint 238 的畫面才能分店鋪結帳。

PRD 對「同店」有直接依據：US-008（§17.3.2）「單筆訂單可包含來自同一商家的多個品項；Phase 1 不支援跨商家訂單（PC-005）」；PC-005（§15.1.3）「Phase 1 不支援跨商家／跨品項優惠」。促銷活動是店鋪層的設定（§4.4：店主／賣家建立促銷活動要查 `PROMO_ENABLED`）。

### 1.2 修復前的行為（Sprint 232 實測＋本輪讀碼）

- **訂單**：`OrderService.createOrderFromCart`／`CombinedCheckoutService` 取 `TenantContext.getCurrentTenant()`（下單者的租戶）當訂單的租戶。沒有店鋪的消費者的租戶是系統租戶佔位值（不是 null、全體共用），所以訂單全部蓋成系統租戶，店主的賣家訂單列表永遠 0 筆、讀不到（403）也處理不了（Sprint 232 已用真實 JAR＋PostgreSQL＋Redis＋兩個真實角色實測）。
- **本輪讀碼另外發現同一個根因的擴散面**（都是依訂單或買家租戶解析，所以也都連帶錯誤；**皆為讀碼推論，未逐項實測**，運費與促銷碼兩項另有突變驗證還原舊行為時的紅燈佐證）：
  1. **運費模板**：`ShippingTemplateService.calculateFeeForTenant(買家租戶, …)`——系統租戶沒有運費模板，消費者的運費永遠是 0，店鋪設定的運費從未生效；購物車的運費預估（Sprint 101）也一樣。
  2. **促銷碼**：購物車套用、驗證、結帳都以買家租戶找券——店鋪建立的券，沒有店鋪的消費者永遠用不到。
  3. **庫存流水帳**：`ProductInventoryService` 寫的 `stock_movements.tenant_id` 取 `order.getTenant().getId()`，訂單預扣／出貨的流水帳因此蓋成系統租戶，店鋪自己的庫存異動查詢看不到消費者訂單造成的異動。
  4. **訂單衍生資料**：客服工單、退貨單（取 `order.getTenantId()`）、以訂單為對象的對話，租戶都是從訂單複製的，跟著錯。
  5. **週結算**：`SettlementGenerator` 依 `orders.tenant_id` 為每個 ACTIVE 租戶（含系統租戶）彙總，所以消費者的訂單不會進店鋪的結算單（與追蹤表 `DEF-319` 的推論相同，未實測）。
- **購物車本來就可以放任何店鋪的商品**：`RedisCartService.addItem` 不驗證商品所屬租戶，購物車以（使用者, 買家租戶）為鍵，所以「購物車含多家店鋪的商品」在修復前就是會發生的情況。

### 1.3 設計（本輪採用的做法與理由）

**購物車仍是一份**（以買家租戶為鍵，不搬動已存在的 Redis 資料），**店鋪是從商品所屬的房源動態算出來的**：

| 面向 | 做法 |
|------|------|
| 購物車回應 | 每個項目帶 `storeId`／`storeName`；回應多一個 `stores[]`：每家店鋪各自的小計、**該店鋪運費模板的運費**、已套用的促銷碼、折扣、應付金額。頂層的 `shippingFee`／`discountAmount`／`finalAmount` 是各店鋪合計（單一店鋪時與該店鋪摘要相同，既有前端不受影響） |
| 結帳（`POST /v2/orders`、`POST /v2/checkout/mixed`） | 新增選填 `storeId`。購物車只有一家店鋪的商品時可省略；含多家店鋪時**必填**，否則 `E-5020`；只結該店鋪的項目，其餘留在購物車。決定規則集中在 `CartStoreSelector`（有單元測試） |
| 促銷碼 | 套用／驗證／移除都以店鋪為單位（`apply-promo` 的 body 與 `DELETE /promo`、`GET /validate-promo` 的 query 都有選填 `storeId`，省略規則同上）；Redis 的鍵多一段店鋪（`promo:{user}:{買家租戶}:{店鋪}`） |
| 縱深防線 | `OrderService.buildProductOrder` 對照資料庫裡每個商品實際所屬的店鋪，與訂單的店鋪不同就拋 `E-5020`——任何呼叫路徑都不可能建出跨店鋪的訂單 |
| 合併結帳 | 商品與房間必須同一家店鋪；訂單、訂房、促銷碼、運費都以該店鋪為準；指定的店鋪缺商品或房間 → `E-5004` |
| 舊的 ROOM 訂單路徑（`createRoomOrder`，前端零呼叫點） | 一併改成房源所屬店鋪，避免日後有人接上又重演 |

沒有採用「購物車以（使用者, 店鋪）為鍵」：那會讓既有 Redis 購物車失效、需要列舉某使用者的所有店鋪購物車（`SCAN`），且 `GET /v2/cart` 的回應形狀要大改。現在的做法讓既有單一店鋪使用方式零改動。

### 1.4 `DEF-332`（Sprint 236 發現）的處理——做法 (a) 實測

Sprint 236 發現測試庫的 `listings.tenant_id`／`owner_id` 可為 NULL，只設影子欄位的固件會默默存成 NULL。本輪**實作建議的做法 (a)**：`Listing` 兩個影子欄位的 `@Column` 加 `nullable = false`（只影響 Hibernate 產生的 DDL；測試庫與雲端 CI 都是每次重建的乾淨資料庫，`information_schema` 實測兩欄已變 `NO`）。結果當場揭露 **7 個測試類**的固件有問題（`OrderControllerE2ETest`、`BuyerOrderJourneyE2ETest`、`MockPaymentUnderStripeIntegrationTest`、`M16ErpE2ETest`、`M16ErpIntegrationTest`、`SkuManagementIntegrationTest`，加上 Sprint 236 已修的 `BookingControllerE2ETest`），其中 M16／SKU 三個用的是「先存成 NULL、再用 JDBC UPDATE 補寫」的舊做法，且還有第二層問題：固定 ID 的使用者其實不存在（`User.id` 是 `@GeneratedValue`，builder 的 `.id()` 被忽略、存成隨機 id），改設關聯後 `owner_id` 外鍵才暴露——都已修成真的關聯。其餘 10 處只設影子欄位的固件（`M12EffectivePriceIntegrationTest`、`M12PricingIntegrationTest`、`M12PricingProductIntegrationTest`、`M01ProductIntegrationTest`、`M17MaintenanceWorkflowIntegrationTest`、`M02RoomIntegrationTest`、`ListingControllerE2ETest`、`M11ShippingFeeIntegrationTest`、`BookingIntegrationTest` 等）在 NOT NULL 的資料庫上照常通過，代表它們沒有真的把房源寫進資料庫（純 mock 或只在記憶體內）。

**同型的實體（`Order`、`Booking`、`ShippingTemplate`…的 `tenantId`／`userId` 影子欄位）本輪沒有動**，登記在 `DEF-332` 的狀態欄，要不要比照處理是後續決定。

## 2. 使用者決策與需要使用者知悉的行為變更

本輪沒有新的使用者決策（修法已在 Sprint 232 結尾拍板）。有幾個**行為變更**（我的判斷，請使用者否決或調整）：

1. **`V89` 資料遷移會改既有資料庫的訂單租戶，而且影響金流**（細節在遷移檔頭註解）：
   - 只搬「單一店鋪、尚未被任何結算單認領」的訂單。已被結算單認領（可能在系統租戶的結算單裡）、項目來自多家店鋪、沒有項目的訂單一律不動，筆數會以 `RAISE NOTICE` 印在 Flyway 日誌；**被認領的那批需要財務人工決定怎麼處理**。
   - 被搬到店鋪、已完成且尚未結算的訂單，會在該店鋪**下一次**週結算進入結算單（結算本來就納入所有尚未結算的已完成訂單，不論哪週下單）。結算單仍要經過審核才會撥款，但金額可能不小。
   - 衍生資料（客服工單、退貨單、對話）與庫存異動（改成 SKU 所屬房源的店鋪）會跟著改。
   - 部署前可先用遷移檔頭註解裡的預覽 `SELECT` 看影響筆數。
2. **店主與店員從此看得到真實消費者的訂單**，包含收件人姓名、電話、地址——這是修復的目的，但也是第一次發生。（承 [SPRINT_236_PLAN.md](SPRINT_236_PLAN.md) §2 第 2 點對訂房的同一件事。）
3. **店鋪設定的運費模板與優惠券，第一次對沒有店鋪的消費者生效**；反過來，A 店成員不能再拿 A 店的券買 B 店的商品。
4. **購物車含多家店鋪的商品時，結帳會回 `E-5020`，直到 Sprint 238 的前端畫面完成**（單一店鋪的購物車完全不受影響）。
5. **部署後購物車上已套用的促銷碼會消失**（Redis 鍵多了店鋪一段，舊鍵不再被讀；購物車商品本身不受影響，使用者要重新套用）。

## 3. 實作內容

- `ErrorCode.E_5020`（HTTP 400）：「訂單的商品必須屬於同一家店鋪，請指定要結帳的店鋪」。**新增錯誤碼要同步四處**：`ErrorCode`、`GlobalExceptionHandler` 的狀態對應（`switch` 是窮舉的，漏了編譯不過）、`docs/02_architecture/API_Error_Codes.md`、`GlobalExceptionHandlerTest` 的期望表（漏了該測試失敗）。
- `CartStoreSelector`（新）：決定「這次要結哪一家店鋪」的單一規則（§1.3），訂單、合併結帳、購物車的促銷碼操作共用。
- `RedisCartService`：`getCart` 帶出項目的店鋪；`getCartWithPromo` 依店鋪分組、每家算運費與折扣，回傳 `stores[]`；促銷碼的套用／驗證／移除／讀取都以店鋪為單位（簽章多一個 `storeId`），新增 `resolveStoreId`（套用、移除用，沒有項目就是錯誤）與 `resolveStoreIdForValidation`（驗證用：端點設計上永遠回 200，所以空購物車維持原本的行為、以買家租戶驗證）。
- `CartController`：`apply-promo`（body）、`DELETE /promo`、`GET /validate-promo`（query）都有選填 `storeId`。
- `OrderService`：`createOrderFromCart` 取購物車（買家租戶為鍵）→ 選店鋪 → 只取該店鋪的商品項目 → 訂單蓋店鋪、運費與促銷碼以店鋪解析 → 只移除已結的項目；`buildProductOrder` 加縱深防線；`createRoomOrder` 以房源所屬店鋪。
- `CombinedCheckoutService`：商品與房間必須同一家店鋪（§1.3）。
- `OrderDto.CreateRequest.storeId`、`CheckoutDto.MixedCheckoutRequest.storeId`、`CartDto`（`storeId`／`storeName`／`StoreCartSummary`／`stores`／`ApplyPromoRequest.storeId`／`ApplyPromoResponse.storeId`）。
- **`DEF-330` (b)**：`OrderService`、`BookingService`、`LogisticsService` 三份私有的 `SYSTEM_TENANT_UUID` 常數移除，改用 `TenantContext.isStoreTenant`（Sprint 234 新增的單一述詞）；同時改寫了三處已經過時的 Javadoc（原本寫「訂單／訂房會蓋成系統租戶」）。
- **`DEF-332`**：`Listing` 影子欄位 `nullable = false` ＋ 7 個測試類的固件（§1.4）。
- `V89__Backfill_Order_Tenant_To_Store.sql`：回填（§2 第 1 點）。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元 | `CartStoreSelectorTest`（6）：只有一家店鋪不必指定／多家店鋪沒指定→`E-5020`／指定的店鋪沒項目→`E-5004`／房源已刪除的項目不參與選擇。`OrderStoreCheckoutTest`（5）：沒有店鋪的消費者（購物車租戶是系統租戶、店鋪是另一個租戶，才分得出用了哪一個）→ 訂單蓋店鋪、運費用店鋪模板、不查買家租戶；促銷碼在店鋪解析；兩家店鋪沒指定→`E-5020` 且不動購物車；指定一家→只結那一家、另一家留在購物車；指定了沒有項目的店鋪→`E-5004`；**縱深防線**：項目宣稱的店鋪與商品實際所屬的店鋪不同→`E-5020`。`CartControllerStoreParamTest`（4）：促銷碼三個端點（套用、驗證、移除）的 `storeId` 確實傳到服務層（控制器不猜店鋪、不拿買家租戶驗證）。`RedisCartServiceTest`（+10，`StoreScoped`）：項目帶店鋪；兩家店鋪各用各的運費模板且絕不用買家租戶查；促銷碼在店鋪驗證與查券、以（使用者, 店鋪）為鍵；多家店鋪沒指定→`E-5020` 且不寫入；只套用在指定店鋪、不影響另一家；指定沒有項目的店鋪→`E-5004`；移除只動該店鋪的鍵；驗證促銷碼的店鋪決定（明確指定就用指定的、單一店鋪用那一家、**空購物車維持原本的行為以買家租戶驗證，端點永遠回 200**、多家店鋪才要求指定——這一條是完整 `mvn verify` 的 `M11CartPromoIntegrationTest` 抓到的行為變更，已修）。既有的 `OrderPromoCodeTest`／`OrderServiceTest`／`RedisCartServiceTest` 固件改成「買家租戶≠店鋪」（`OrderPromoCodeTest` 的購物車租戶改為系統租戶佔位值） |
| 真實 PostgreSQL＋Redis 整合（+6） | `StoreScopedCheckoutIntegrationTest`：兩家店鋪＋一位沒有店鋪的真實消費者＋兩位店主。單一店鋪購物車→訂單歸店鋪、運費用店鋪模板、**店主的賣家訂單列表看得到、讀得到；另一家店鋪的店主看不到（`E-1007`）；消費者自己仍讀得到**；兩家店鋪沒指定→`E-5020`、沒有訂單、購物車原封不動；分開結帳→各歸各的店鋪、各用各的運費；促銷碼屬於店鋪（店鋪 A 的券套用在店鋪 B 失敗、兩家店鋪的購物車必須指定店鋪、折扣落在 A 的訂單、B 的結帳不受影響）；合併結帳限同店（沒指定→`E-5020`；指定 A→Order 與 Booking 都歸 A、運費與促銷碼用 A 的、B 的商品留在購物車）；指定的店鋪缺商品或房間→`E-5004` |
| 遷移邏輯（+3） | `BackfillOrderTenantMigrationIntegrationTest`：讀 `V89` 的實際 SQL 對種好的資料執行（交易內、結束回滾）：單一店鋪未結算→搬；多家店鋪／已被結算單認領／沒有項目／本來就對→不動，且只改租戶；客服工單、退貨單、以訂單為對象的對話跟著訂單、庫存異動改成 SKU 所屬房源的店鋪、沒有訂單的平台工單不動；可重複執行。Flyway 本身能否套用（含 `DO` 區塊與欄位名稱）由 `make validate-schema-doc` 對乾淨資料庫套用所有遷移驗證 |
| 真實後端 Playwright（+4，改 4） | 新增 `at-store-checkout-real.spec.ts`（SCHK-01～04）：兩家真實店鋪（真實開店流程）＋一位沒有店鋪的真實消費者——購物車依店鋪分組、沒指定店鋪→`E-5020`、指定店鋪 A→訂單歸 A（A 的店主看得到、B 的看不到）、B 的商品留在購物車再自己結帳。`at-seller-dashboard-real.spec.ts` 的 SDASH-07／08 移除 `test.fail()`（原本描述「修好後應有的行為」） |

### 突變驗證（Rule 9：測試必須在守門被拿掉時失敗）

| # | 突變（把守門拿掉／改回舊行為） | 結果 |
|---|------|------|
| MO1 | 訂單的店鋪改回買家租戶 | 18 個測試轉紅 |
| MO2 | 訂單運費改回用買家租戶查模板 | 13 個轉紅 |
| MO3 | 促銷碼改回在買家租戶解析 | 15 個轉紅 |
| MO4 | 店鋪選擇改成「取第一個項目的店鋪」（不檢查多家店鋪） | 5 個轉紅（含整合測試） |
| MO5 | 拿掉 `buildProductOrder` 的縱深防線 | `buildProductOrder_rejectsAListingOfAnotherStore` 轉紅 |
| MO6 | 拿掉「只取該店鋪項目」的篩選 | `multipleStores_withStore_ordersOnlyThatStoresItems` 轉紅（縱深防線攔下） |
| MR1 | 購物車的運費預估改回用系統租戶查模板 | 4 個轉紅 |
| MR2 | 促銷碼的 Redis 鍵拿掉店鋪一段 | 3 個轉紅 |
| MR3 | 購物車套用促銷碼改回在買家租戶驗證 | 2 個轉紅 |
| MR4 | 驗證促銷碼的店鋪決定：空購物車不再回退買家租戶（回 null） | `resolveStoreForValidation_emptyCart_fallsBackToTheBuyersTenant` 轉紅 |
| MK1 | `CartController.applyPromo` 不傳 request 的 `storeId` | `applyPromo_passesTheRequestedStore` 轉紅 |
| MK2 | `CartController.removePromo` 移除時用未決定的 `storeId`（而非服務層決定的店鋪） | `removePromo_removesTheResolvedStoresPromo` 轉紅 |
| MK3 | `CartController.validatePromo` 改回在買家租戶驗證 | `validatePromo_validatesInTheResolvedStore` 轉紅 |
| MC1 | 合併結帳的訂房租戶改回買家租戶 | 整合測試轉紅（訂房必須歸店鋪 A） |
| MC2 | 合併結帳的運費改回買家租戶 | 整合測試轉紅 |
| MC3 | 合併結帳的促銷碼改回在買家租戶解析 | 整合測試轉紅（`E-5007`） |
| W1 | `V89` 拿掉「尚未結算」條件 | 轉紅（已被認領的訂單被搬走） |
| W2 | `V89` 把「單一店鋪」條件改成 `>= 1` | 轉紅（多家店鋪的訂單被搬走） |
| W3／W4／W6／W7 | `V89` 的客服工單、庫存異動、退貨單、對話更新各自改成沒作用 | 各自轉紅 |
| W5 | `V89` 的訂單更新改成沒作用 | 三個測試都轉紅 |

每次突變都先備份源碼、突變後等 class 比源碼新才跑、跑完還原並以 `cmp` 確認逐位元組一致。**沒有做突變驗證的部分**：`V89` 的 `RAISE NOTICE` 計數（只是日誌，沒有測試斷言它）、前端（Sprint 238）。

## 5. 驗證結果

- **後端**：全量 `mvn -o clean verify` **BUILD SUCCESS（16 分 30 秒）**：單元 **2047**（+27）／整合 **720**（+9）／**0 失敗**／0 略過；checkstyle（main＋test）0 違規；PMD 通過。（Sprint 236：2020／711，16 分 22 秒。）**第一次完整 verify 失敗過**：單元 2040 全過、整合 720 中有 2 個失敗——`M11CartPromoIntegrationTest` 的 `GET /v2/cart/validate-promo` 兩個案例（真實 Redis＋真實服務層，空購物車）。原因是我把驗證端點也改成用會丟 `E-5004` 的店鋪決定，破壞了「該端點永遠回 200、valid／invalid 寫在回應本文」的既有設計。已修（`resolveStoreIdForValidation`，見 §3），並補單元測試與控制器層測試後重跑完整 verify 通過。**這件事只有完整 verify 抓得到**：我自己挑的測試子集沒有包含它。
- **突變驗證**：見 §4（23 個突變全部被抓到）。
- **Schema 文件**：`make validate-schema-doc` 對乾淨 PostgreSQL（postgres:18-alpine）套用 **89 個** Flyway 遷移（含 `V89` 的 `DO` 區塊）通過，SRD／PRD 的 DDL 與實際 schema 一致（`V89` 是純資料遷移，不改 schema）。
- **真實後端 E2E**：`make validate-e2e`（沿用 `mvn verify` 剛建的 JAR；前端 `src` 本輪沒有變動，沿用既有建置）**133 個測試：129 通過／4 略過／0 失敗（3.7 分鐘）**。新增的 `at-store-checkout-real.spec.ts`（SCHK-01～04）全綠；`at-seller-dashboard-real.spec.ts` 的 SDASH-07、08 移除 `test.fail()` 後**以正常斷言通過**（測試結果目錄裡沒有這兩個案例的失敗證物）。那 4 個略過是既有基準（Sprint 231～236 皆為 4 個）。
- **前端**：`tsc --noEmit`、`eslint`（兩個 e2e 規格）皆通過；前端 `src` 本輪沒有變動。
- **push 與雲端 CI**：（push 後於回填 commit 補上）
- **耗時觀察（承 Sprint 233～236，未歸因）**：本機全量 `mvn verify` 本輪 16 分 30 秒（Sprint 236：16 分 22 秒；Sprint 235：15 分 10 秒）。雲端整合 job 近八次為 5m01s／6m02s／6m00s／4m15s／7m47s／7m46s／8m03s／8m05s，本輪 push 後的數字是第五個資料點。

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **前端尚未支援分店鋪結帳**：購物車頁沒有依店鋪分組、沒有每家店鋪各自的結帳按鈕與促銷碼輸入，結帳頁也不會帶 `storeId`；多家店鋪的購物車目前只會看到 `E-5020`（Sprint 238）。前端錯誤碼對照表也還沒有 `E-5020`。
2. **沒有檢查店鋪本身的狀態**：訂單歸屬的店鋪若已停權或關閉，結帳仍會成功（既有行為：只檢查商品的狀態，原本以買家租戶（系統租戶，一定是 ACTIVE）下單，沒有這個問題；現在歸屬店鋪才有）。是否要在結帳時擋掉非 ACTIVE 的店鋪，是新的產品決定，本輪沒有擅自加。
3. **`V89` 對已被結算單認領的訂單不動**（§2 第 1 點）：修復前若週結算已經替系統租戶產生過包含消費者訂單的結算單，那批訂單仍留在系統租戶，店鋪不會因此拿到錢；實際筆數未知（部署前可用遷移檔頭註解的預覽查詢加上 `settled_statement_id IS NOT NULL` 的條件查）。
4. **歷史上項目來自多家店鋪的訂單無法判定歸屬**，不動（修復前購物車沒有限制）。
5. **`CartController.getTenantId` 還有一份系統租戶 UUID 的字面值**（沒有登入租戶時的回退）：它是字串字面值而不是 `SYSTEM_TENANT_UUID` 常數，不在 `DEF-330` (b) 原本列的三份之內，本輪沒有動（Rule 3）。
6. **舊的購物車促銷碼鍵殘留**：`promo:{user}:{買家租戶}` 不再被讀，留在 Redis 直到 30 天 TTL 過期，無害。
7. **`DEF-332` 只處理了 `Listing`**：`Order`、`Booking`、`ShippingTemplate` 等實體也有同型的影子欄位（`tenantId`／`userId`），測試庫同樣可能存成 NULL；本輪沒有逐一處理（見追蹤表）。
8. **購物車 `addItem` 仍不驗證商品所屬租戶與狀態**（既有行為，驗證留在結帳時）。

### 6.2 待使用者決定／確認（累積）

| 項目 | 內容 | 我的建議 |
|------|------|----------|
| `DEF-326` | SELLER／HOST 自助註冊後落在系統租戶，持有商品／房源／定價／運費／CMS／貼文寫入權限，彼此與平台自營資料互通（[SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §6.1） | 未歸屬店鋪者不得寫入 |
| Sprint 234 §6.4 | 沒有店鋪的使用者的限流單位（已登入→使用者、匿名→來源 IP，容量 100 次／分）是工程決策，PRD 未定義 | 同意（有疑慮可調整容量或加全域上限） |
| Sprint 235 §2 | `V87` 會改既有資料庫的使用者角色；Stripe Connect 端點由 `SELLER` 改為 `STORE_OWNER` | 同意（部署前先跑 §2 的 `SELECT` 看影響人數） |
| Sprint 236 §2 | `V88` 會改既有資料庫的訂房租戶；店主／店員第一次看得到消費者訂房的個資；促銷碼改以店鋪租戶解析 | 同意（部署前先跑 §2 的 `SELECT` 看影響筆數） |
| 本輪 §2 | `V89` 會改既有資料庫的訂單租戶，**影響結算金流**；店主／店員第一次看得到消費者訂單的個資；運費與促銷碼改以店鋪解析；多家店鋪的購物車在 Sprint 238 前無法結帳 | **請特別確認第 1 點的結算影響**；其餘同意 |
| 本輪 §6.1 第 2 點 | 結帳時是否擋掉非 ACTIVE 的店鋪 | 擋掉（新產品決定，需要你確認） |

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果。
2. **Sprint 238：前端分店鋪購物車與結帳**：購物車頁依店鋪分組（`stores[]`）、每家店鋪各自的促銷碼輸入與結帳按鈕；商品結帳頁與合併結帳頁帶 `storeId`；錯誤碼對照表補 `E-5020`；Playwright 驗收（購物車兩家店鋪→分別結帳）。
3. 之後：Sprint 239～240 文件對齊（`DEF-322`、`DEF-323` (b)；API／SRD 補上本輪與 Sprint 236 的契約變更），Sprint 241 店鋪成員管理前端，Sprint 242 CMS 卡片連結（`DEF-321` (c)），Sprint 243 通知事件（`DEF-318`），Sprint 244～245 店鋪前台 `/stores`。
4. **待使用者**：§6.2 的六項；本輪是一個自然的停點（`DEF-319` 後端全部完成），結束時我會再用互動選擇請使用者一併確認。
5. 選配：整合測試的 bcrypt 強度（先量測，見 [SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §5、[SPRINT_235_PLAN.md](SPRINT_235_PLAN.md) §5、[SPRINT_236_PLAN.md](SPRINT_236_PLAN.md) §5）。
