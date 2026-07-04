# Deferred Items Tracker / 延後項目追蹤器

> **用途**: 追蹤所有被延後到未來 Sprint 的工作項目
> **更新原則**: 每個 Sprint Review 後必須更新此文件
> **審查時機**: Sprint Planning 前必須先閱讀此文件

---

## 活躍延後項目 / Active Deferred Items

### 🔴 高優先級 - 下一 Sprint 應優先處理

*目前無高優先級延後項目。*

---

### 🟡 中優先級 - 未來 Sprint 處理

| ID | 標題 | 原始 Sprint | 延後原因 | 前置需求 | 預估 SP | 狀態 |
|----|------|-------------|---------|---------|---------|------|
| DEF-021 | CJK 字體品牌一致性（技術債） | Sprint 35（Turbopack 限制發現） | S35 因 Turbopack 無法 self-host next/font CJK（Noto Sans TC 大量 unicode-range 子集無法解析），改用系統 CJK 字體堆疊；系統堆疊跨平台字重/字距不一，品牌字體一致性下降。後續評估 `next/font/local` + 預先子集化 Noto Sans TC woff2 以恢復品牌字體一致性 | 需 woff2 子集化工具鏈 + 驗證 Turbopack 相容性 | 2 | ✅ 已決策（S41 US-006）：維持系統字體堆疊為 **accepted fallback**；選項 B（`@font-face` 自 host 子集 woff2，技術可行、無 CSP 阻擋）記錄為選配未來任務，待品牌一致性需求由使用者拍板。詳見 [CJK_FONT_ASSESSMENT.md](../06_quality/CJK_FONT_ASSESSMENT.md) |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | Sprint 53（Phase D-1 拆分） | Phase D-1（本 Sprint）只做 Connect Express 帳戶 onboarding；付款成功後平台代收款項如何 transfer 給賣家（`stripe.transfer_data`/`Transfer.create`）+ 對帳/提現介面 | 需 Phase D-1 帳戶已上線（`connectOnboardingStatus=COMPLETE`）| 5+ | ⚠️ 待評估（P3）|
| AI-2408 | availability reason 錯誤碼化 + i18n | Sprint 47（未開放原因為英文字串） | availability `unavailableReason` 目前為後端英文字串（"is booked"/"is not open..."），前端沿用既有路徑原樣顯示；評估碼化 + 前端訊息映射 | 需定義 reason code 枚舉 + 前端 i18n map | 2 | ⚠️ 待評估（P4） |

---

## 已完成延後項目 / Completed Deferred Items

| ID | 標題 | 移出 Sprint | 完成 Sprint | 備註 |
|----|------|-------------|-------------|------|
| DEF-001 | Booking E2E 完整預訂流程測試 | Sprint 5 | Sprint 6 | 已實作統一端點 + Feature Toggle + 測試資料，E2E 測試通過 |
| DEF-004 | listings.tags 欄位類型修復 | Sprint 7 | Sprint 10 | V8__Fix_Listings_Tags_Column_Type.sql migration 已建立，測試通過 (6/6) |
| DEF-005 | M10 IM SA 需求分析 | Sprint 21 Buffer-B | Sprint 22 | M10_IM_REQUIREMENTS.md 建立，PM/PO Victoria APPROVED |
| DEF-006 | M11 Provider Stub 強化 | Sprint 21 Buffer-C | Sprint 22 | HCT/TCAT 追蹤號改為 {Provider}-{yyyyMMdd}-{HEX8} 格式 |
| DEF-007 | M11 物流與訂單履約流程整合 | Sprint 21 | Sprint 23 | createLogistics 前置驗證 + 訂單狀態同步 SHIPPING/DELIVERED，4 個整合測試通過 |
| DEF-008 | ShippingTemplate 接入訂單結帳流程 | Sprint 21 | Sprint 23 | V47 Migration + shippingFee 欄位 + 免運門檻邏輯，3 個整合測試通過 |
| DEF-009 | Logistics.logisticsData jsonb 映射慣例統一 | Sprint 25 | Sprint 26 | US-006：統一為 Map + @JdbcTypeCode(SqlTypes.JSON)，validate-schema 通過 |
| DEF-010 | OrderStateMachine 一致性清理（SHIPPING→CANCELLED） | Sprint 25 | Sprint 26 | US-004：移除 SHIPPING→CANCELLED + 轉換表↔canCancel 一致性不變量測試 |
| DEF-011 | cancelLogistics 錯誤碼修正（→ E_7500 系列） | Sprint 25 | Sprint 26 | US-004：改 E_7500（not found）/E_7502（delivered）+ 3 單元測試 |
| DEF-012 | ChatService.toMessageResponse 廣播 conversationId 補正 | Sprint 25 | Sprint 26 | US-003：改由 conversation 關聯取 id + 廣播 payload 測試 |
| DEF-014 | e2e 乾淨 DB 註冊回 401 致 10 spec 失敗 | Sprint 26 | Sprint 26 | 真因為 validate-e2e.sh 誤設 NEXT_PUBLIC_API_URL（雙 /v2），非產品 bug；修正後 27 passed/5 skip/0 fail，e2e 改 strict 預設 |
| DEF-013 | M09 MQ 通知缺端到端驗證 | Sprint 26 | Sprint 27 | US-003：補 produce→佇列→consume 端到端測試（NotificationProduceConsumeTest，真實 ObjectMapper + 共用佇列）。**揪出並修復真 bug**：producer 用 Stream(XADD)、consumer 用 List(RPOP) 同 key 型別不相容 → 通知永不被消費；改為兩端一致 List（leftPush/rightPop） |
| DEF-015 | 前端 next/font/google 建置期外部抓取 | Sprint 26 | Sprint 27 | US-002：layout.tsx 的 Geist 變數從未被 CSS/Tailwind 消費（死碼），移除 next/font/google import → 離線 build 不再抓 Google，零視覺影響 |
| DEF-016 | Admin Audit Log 持久化 | Sprint 28 | Sprint 31 | US-003：建 AuditLog entity + AuditLogRepository + V57 migration；AdminService 6 個關鍵操作寫入 audit_log（與 log.info 並存，catch 不中斷主流程）；make validate-schema 無漂移，AdminServiceIntegrationTest 稽核測試通過 |
| DEF-018 | 訂單 getOrder 無擁有權檢查（IDOR，安全） | Sprint 31 | Sprint 32 | US-001（AI-1601）：getOrder 加 owner/admin 擁有權檢查（比照同類 cancelOrder/getOrderStateLogs 的 inline pattern），越權回 403/E_1007；findOrderById 不動保留 404 not-found 語意，最小爆炸半徑不影響 payment/賣家/admin 內部流程；補 BuyerOrderJourneyE2ETest.otherBuyerCannotGetOrder（買家 C 讀 A 訂單→403）；本地訂單 E2E 20 tests 0 fail |
| DEF-020 | 版型 Shell 架構債（route-group layout + client 邊界下推 + 全站狀態走 URL） | Sprint 35 | Sprint 36 | US-002：Architect 審查建議三項全數償還——(a) 建 App Router route-group `app/(storefront)/layout.tsx` 承載 TOP（Header）/Bottom（Footer），首頁移入 group（換頁不重建版型）；(b) `"use client"` 邊界下推——StorefrontShell 改 grid-only server component、Footer/Tools 維持 server，僅 Header（含 SearchBar/ThemeSwitcher）client；(c) 搜尋/分類/排序/分頁改走 URL query（server page 讀 searchParams → props 傳 client `HomeContent`，官方建議免 useSearchParams+Suspense；Header 搜尋 router.push、購物車數量自取），移除 page-scoped callback 與 nonce 補丁（連帶償還 F-06）。以 key-remount 於篩選變更顯示 skeleton（避免 effect 內同步 setState，符 React 19 嚴格 hooks）。build/type-check/lint 0 error；**at-homepage E2E 4 tests 全棧全綠**（含搜尋改走 URL 的 E2E-HOME-03），既有 E2E 不退步（唯 m15 既有 flaky） |
| DEF-019 | 訂單付款/物流讀寫無擁有權檢查（IDOR 姊妹，安全） | Sprint 32 | Sprint 36 | **歷時 S32→33→36**。S33 修訂單付款側（checkOrderOwnership，買家限本人）。**S36（AI-1902）補完物流/賣家側**：LogisticsService.createLogistics 加 tenant-based 擁有權檢查（`order.tenantId==當前租戶`、admin 放行、越權 403/E_1007，賣家側租戶語意）；PaymentService.processOrderPayment（`/v2/payments` 對外入口）加 user-based 擁有權檢查（比照 checkOrderOwnership）。**測試**：新增 LogisticsServiceOwnershipTest + PaymentServiceOwnershipTest（各 3 tests，越權→E_1007、本人/本租戶通過、admin 放行）；對齊 M07 整合測試（`@Transactional` 一級快取致影子 `userId` 為 null → builder 明確設 `.userId` + 訂單擁有者=呼叫者，**順帶修好 S33 遺留的 5 個 M07 失敗**）。乾淨 DB：M07 8 + M11 4 整合測試 0 fail、單元 353 tests 0 fail。**活躍安全 DEF 歸零**。備註：processBookingPayment（預訂付款側）非 DEF-019 範圍，如需擁有權檢查另立項評估 |
| DEF-017 | ERP 手動庫存異動租戶隔離（安全） | Sprint 28 | Sprint 34 | **歷時 Sprint 28→34（三度誠實回退後落地）**。US-001（AI-1701）：StockMovementService.createManualMovement 加 null 安全租戶檢查（inject ListingRepository → `!tenantId.equals(listing.getTenantId())`，越權 403/E_1007，移除 getTenantListings no-op）。**三層根因**：Listing.tenantId insertable=false 影子欄位（需 tenant 關聯）+ Tenant.id @GeneratedValue 使 @WithErpSecurity 硬編 FIXED_TENANT_ID 無 tenants 列 + listings FK。**修法**：M16 以 raw SQL 種 FIXED_TENANT_ID 租戶列（比照 TestDatabaseInitializer）+ JDBC UPDATE listing tenant_id + 修 @AfterAll cleanup 先刪 product_skus + IT-M16-307 跨租戶測試。乾淨 DB 43 tests 0 fail |
| AI-2403 | 進階定價接入 PRODUCT/Cart 計價鏈 | Sprint 43 | Sprint 44 | US-001（AI-2403）：`RedisCartService.getCart` 讀取時對 PRODUCT 項以 `getEffectivePrice`（今日基準）套折扣（toggle+向後相容），unitPrice/subtotal 折扣後 + CartItemResponse transient 折扣欄位；`OrderService` 未改（訂單繼承 getCart 折扣後 subtotal，顯示與下單一致）；getEffectivePrice 補 appliedRuleName。schema-free。單元 3 + 真 DB 整合 45 tests 0 fail |
| AI-2405b | 買家整月日曆每日折扣顯示 | Sprint 43 | Sprint 44 | US-002（AI-2405b）：`getCalendar` 以 `calculatePrice` 逐日 breakdown merge 折扣（checkOut exclusive→endDate+1、只可訂日、只折扣型）；CalendarResponse + CalendarDay 補 originalPrice/appliedRuleName；MonthCalendar 原價刪除線（保留 calendar-price testid + 新增 calendar-original-price）+ E2E-ROOM-07。無 schema |
| AI-2303 | Inter 字體建置期 Google Fonts 依賴 | Sprint 42 | Sprint 44 | US-003（AI-2303）：`layout.tsx` next/font/google → next/font/local，committed Inter latin variable woff2（48KB OFL，一次性自 Google Fonts 取得）；消 build 期網路依賴（同源 DEF-015）；CJK 系統堆疊不動。build 0 error、無 next/font/google 引用 |
| AI-2406 | 定價機制統一（room_calendar 手動日價 vs 規則） | Sprint 43 | Sprint 45 | US-001（AI-2406）：探勘揭穿「雙定價機制」實為死碼假象——`room_calendar.price` 寫入路徑 setDatePrice/setDatePriceBulk **零呼叫者**、欄位恆 NULL。移除死碼兩方法；BookingService 三處讀取（checkAvailability/getCalendar/calendarBaseTotal）移除死欄位 fallback 改直取 basePrice（**行為等價**，順帶修正 calendarBaseTotal NULL→ZERO 潛在低估）；RoomCalendar.price 註解標記停用（未加 @Deprecated 以維持 @Deprecated=0 慣例）；確立 MANUAL_OVERRIDE 為唯一手動日價路徑。決策文件 PRICING_MECHANISM_UNIFICATION.md。單元 6 + 真 DB 整合 57 tests 0 fail、schema-free。**漲價計入 booking 行為變更另立 AI-2406b（PO 決策）；DROP COLUMN 另立後續低風險** |
| AI-2202d | 整月日曆「未開放 vs 可訂」語意 | Sprint 42 | Sprint 45 | US-002（AI-2202d，spike）：產決策文件 CALENDAR_OPEN_WINDOW_ASSESSMENT.md——記錄「無記錄=可訂」為 availability/booking/calendar 三層硬語意、room_calendar 稀疏 lazy 建立；三選項比較（A Room 層級 open_until_date【推薦，需 migration】/ B CLOSED 狀態【高風險】/ C 純前端【不建議單用】）+ 既有房源 NULL 安全過渡。**不改 production code**。**實作另立 AI-2202e（需 PO 拍板 schema）** |
| AI-2406b | 漲價型規則計入 ROOM booking 總價（計價行為變更） | Sprint 45 | Sprint 46 | **PO 拍板選項 B**。US-001（後端）：BookingService 放寬三處折扣閘門（tryDynamicPricing `<baseTotal`→`≠0`、getCalendar 逐日 `<0`→`≠0`、calculateTotalAmount toggle 開即採 adjustedTotal）使 availability/月曆/建單 totalAmount 一律含漲價乘數；保留 toggle 關短路+失敗降級（向後相容）；計算核心不動；PricingService 抽 `resolveListingForPricing` 優雅降級（無 Room fallback basePrice、null 回 4xx 非 NPE→500）；DTO 中性調整語意（discountAmount 改有號差額，新增 priceAdjustmentType DISCOUNT/MARKUP/NONE）。US-002（前端）：ListingDetail/MonthCalendar 折扣維持刪除線+綠 badge、漲價改不刪除線+橙 badge「加價 X」；E2E-ROOM-08/09 漲價變體。後端單元 18 + 真 DB 整合 34、validate-e2e **50 passed/0 fail**、schema-free（V57）。**M12 進階定價收官**（折扣+漲價皆顯示=收費）。只做 ROOM（PRODUCT 另立 AI-2406c）；priority/range 查詢落差另立 AI-2407 |
| AI-2202e | 開放窗語意實作（未開放 vs 可訂） | Sprint 45 | Sprint 47 | **PO 拍板選項 A + 追加滾動視窗 + host UI**。US-001（後端）：V58 migration rooms 加 open_until_date DATE + booking_window_days INT（皆 nullable、既有列 NULL=無限制、backfill 免異動、ADD COLUMN IF NOT EXISTS 冪等）；抽 Room.resolveOpenUntil（取最早生效 min）三層一律呼叫；getCalendar 超窗無記錄日補 NOT_OPEN（計算產物非持久化，抽 appendNotOpenDays 控 NPath）、checkAvailability 超窗 available=false+原因、createBooking+reschedule 超窗擋訂 E-3002（422）；RoomCalendarService 未改（擋在 caller 層）；兩欄 NULL 維持現狀。US-002（前端）：MonthCalendar NOT_OPEN 灰底禁選不刪除線+data-not-open+圖例；ListingDetail 沿用既有不可訂路徑；booking.ts type；room.ts+RoomForm 雙欄位；E2E-ROOM-10/11。後端單元 9 + 真 DB 整合 38（含 API-M06-016）、validate-schema **無漂移**、validate-e2e **52 passed/0 fail**。⚠️ V58 結束 S42~S46 連續零-migration。只做 ROOM；清窗機制另立 AI-2202f、reason i18n 另立 AI-2408 |
| AI-2406c | PRODUCT/cart 漲價（M12 進階定價 PRODUCT 側收官） | Sprint 46 | Sprint 48 | **兩道閘門**（比 ROOM 難）。US-001（後端）：閘門 2（結構性）applyProductRule 由 discount-only 擴充支援漲價型 MANUAL_OVERRIDE price/SEASONAL multiplier/WEEKDAY_WEEKEND weekendMultiplier（對齊 ROOM config key，保留 discountPercent 向後相容）；閘門 1 RedisCartService 折扣閘門 `<現價`→`≠現價`；CartItemResponse 加 priceAdjustmentType + 有號 discountAmount；下單自動繼承（OrderService 未改）。US-002（前端）：cart/page 首次顯示 item 定價（折扣刪除線+綠標/漲價不刪除線+橙標），checkout 不 itemize 未改；E2E-M11-012。後端單元 22 + 真 DB 整合 54（含 IT-EP-004）、validate-e2e **53 passed/0 fail**、schema-free（V58）。**M12 進階定價全面收官**。誠實：SP 初估 3→探勘修正 8（兩道閘門）；PRODUCT/ROOM 兩套計算器對齊 key 未合併（另立 AI-2409）|
| AI-2410 | 真實金流 Phase A：卡片付款 MVP（Stripe Checkout hosted，平台代收）| Sprint 49 | Sprint 50 | 承 S49 評估 + PO 拍板（Checkout hosted + 先平台代收）。V59 payments 加 Stripe 欄位 + STRIPE method + PROCESSING；接回孤兒 gateway——StripePaymentGateway 補 createCheckoutSession（Session.create 平台代收）+ retrieveCheckoutSession；STRIPE_PAYMENT_ENABLED toggle（預設關=mock 不變）；PaymentStateService initiateStripeCheckout（建 PROCESSING+Session）+ confirmStripeCheckout（回跳 retrieve，paid→SUCCESS+Order PAID，冪等）；端點 /pay/checkout + /return；前端 orders/[id] 重導 + success/cancel + E2E-M11-013。後端單元 9（WireMock TC-S004/005）+ 真 DB 整合 25（mock 不退步）、validate-schema 無漂移、validate-e2e **54 passed/0 fail**。誠實：Phase A 僅回跳 retrieve、webhook 權威狀態留 Phase B（AI-2411）；平台代收分帳留 Phase D；⚠️ V59 打破 schema-free |
| AI-2411 | 真實金流 Phase B：webhook 事件驅動權威狀態 | Sprint 49 | Sprint 51 | 補 Phase A「買家未回跳」缺口。PaymentWebhookService 解析 Stripe 事件（checkout.session.completed[paid]→SUCCESS+Order PAID 權威、payment_intent.payment_failed→FAILED、未知→記錄不 dispatch）；StripeWebhookController 驗簽後委派一律回 2xx；PaymentStateService 抽 markStripePaymentSucceeded/Failed 共用核心（回跳 Phase A 與 webhook 雙路徑一致）；V60 processed_stripe_events 去重 + event id 去重；雙層冪等。後端單元 9（UT-WH-001~005）+ 真 DB 整合 21（mock/Phase A 不退步）、validate-schema V60 無漂移、validate-e2e **54 passed/0 fail**。誠實：只做成功/失敗（退款留 Phase C）；失敗路徑 best-effort（pi 惰性）；測試模式跳驗簽，生產須配 secret（上線 checklist AI-2414）|
| AI-2412 | 真實金流 Phase C：退款真串接 | Sprint 49 | Sprint 52 | StripePaymentGateway.processRefund 由 stub 改真 Refund.create（以 payment_intent 全額退款）；PaymentStateService.mockRefund 重構 refundOrderPayment toggle-aware（stripe 真退款+存 stripe_refund_id / mock 保留）；PaymentWebhookService 加 charge.refunded 權威 REFUNDED（冪等+V60 去重）；createCheckoutSession 補 pi metadata order_id（補 Phase B best-effort）；V61 payments 加 stripe_refund_id。後端單元 19（TC-S006 + 退款 005~007 + UT-WH-006）+ 真 DB 整合 21（mock/Phase A/B 不退步）、validate-schema V61 無漂移、validate-e2e **54 passed/0 fail**。誠實：只做全額退款（partial 另立 AI-2415）；測試不打真 Stripe（上線 checklist AI-2414）|
| AI-2414 | 真金流上線 checklist | Sprint 51 | Sprint 53 | 產出 `docs/08_deployment/STRIPE_PRODUCTION_CHECKLIST.md`：金鑰/環境變數、webhook 端點註冊事件清單、Connect Platform Profile（Phase D-1 起）、測試模式端到端人工驗證項目（付款成功/失敗/退款/Connect onboarding）、正式金鑰切換順序、已知限制揭露。**文件本身為完成交付**；文件內列出的人工驗證步驟仍需使用者於測試模式親自執行（誠實揭露，非本項範圍） |
| AI-2413 | 真實金流 Phase D-1：Stripe Connect Express 帳戶 onboarding | Sprint 49 | Sprint 53 | **PO 決策 Stripe Connect（非手動撥款）+ Express（非 Standard）**。US-001：V62 migration（tenants 加 stripe_connect_account_id/connect_onboarding_status/connect_charges_enabled/connect_payouts_enabled）；StripePaymentGateway 新增 createConnectAccount（Account.create type=express）/createAccountLink（AccountLink.create type=account_onboarding）/getConnectAccountStatus（Account.retrieve）；TenantStripeConnectService（onboarding 發起/複用既有 accountId/狀態查詢，`STRIPE_CONNECT_ENABLED` toggle 保護）；SellerDashboardController 新增 onboarding/status 端點。US-002：PaymentWebhookService 擴充 `account.updated` dispatch（反查 tenant 回填狀態，沿用 V60 去重）。後端單元 20（WireMock TC-S007~010 + UT-CONNECT-001~006 + UT-WH-007~008）+ 真 DB 整合 4（IT-CONNECT）+ 全量回歸 536 tests 0 fail、validate-schema V62 無漂移。誠實：**只做帳戶 onboarding，不做代收後 transfer 分潤**（另立 AI-2416，Phase D-2）；不含前端（賣家後台按鈕）；只做 Express（非 Standard/Custom）|
| DEF-022 | E2E 硬等待（waitForTimeout 固定 sleep） | Sprint 35 | Sprint 42 | **歷時 S35→39→42**。S39（AI-2101）已收斂登入 helper 部分；S42（US-003）完成餘下清除：5 檔（at-m11-cart-checkout、at-m15-e2e、at-m17-001/002）冗餘 `waitForTimeout` 刪除、可替換者改顯式等待（`waitForURL`/`waitForResponse`/`expect().toBeVisible()`/`toHaveClass()`）並順帶補斷言（Rule 9）；**保留** at-m10-chat STOMP SUBSCRIBE settle 例外（無 client 可觀察訊號）。**連帶根治**移除 sleep 後浮現的既有 flaky：auth helper 與 S37 共用 Header「註冊」連結碰撞（`.first()` 恆選 header 連結 + re-render 不穩定 → 改 `goto('/register')`，Playwright 快照佐證）+ 原生 alert teardown（at-m15 檔案級 dialog beforeEach + at-m17-002 dialog 處理器）。validate-e2e 46 passed/0 fail |
| AI-2407 | 定價規則選取語意修正——overlap 查詢 + 同優先級 tie-break | Sprint 46（記錄不修） | Sprint 54 | **PO 決策同優先級「後建立者優先」**。US-001：`PricingRuleRepository.findActiveRulesForDateRange` JPQL 由 containment（`validFrom<=startDate AND validTo>=endDate`）改 overlap（`validFrom<=endDate AND validTo>=startDate`），修正部分晚數 SEASONAL 等規則被整批排除的漏套問題；`isRuleApplicable` 逐日精準判斷不變，overlap 僅放寬候選前置篩選。US-002：`calculatePrice`/`getEffectivePrice` 排序 Comparator 補 `.thenComparing(createdAt, reverseOrder())` 落實 tie-break（免 migration，`PricingRule.createdAt` 既有欄位）；修正既有空斷言測試 UT-M12-009（標題聲稱驗證「後建立覆蓋」但斷言僅 `isNotNull()`）為真斷言；新增 `getEffectivePrice` tie-break 單元測試（UT-M12-019）+ 真 DB 整合測試（API-M06-017，驗證部分晚數 SEASONAL 規則於 overlap 修正後正確生效）。後端單元 508 + 真 DB 整合 415 全量回歸 0 fail、`make validate-schema` 無漂移（schema-free）。誠實：只修正查詢語意與排序次鍵，未重寫計價核心邏輯（`isRuleApplicable`/`calculateAdjustment` 均不動）|
| AI-2409 | 定價計算器統一評估 | Sprint 48（兩道閘門根因） | Sprint 55 | **Spike，不改 production code**。產出決策文件 `PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md`：確認 `MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND` 三型別自 S48 起已語意等價（僅程式碼重複）；`EARLY_BIRD`/`LONG_STAY`/`LAST_MINUTE` 對 PRODUCT 本無自然語意（無「目標日期」概念），非未對齊缺陷；發現 `applyProductRule` 對此三型別會靜默落入通用 `discountPercent` fallback、完全略過原 gating（`minDaysAhead`/`minNights`/`maxDaysAhead`），但確認後端 `validateRuleRequest` 無搭配驗證、前端 `PricingRuleList.tsx` 又僅支援 `roomListingId`（無 PRODUCT 定價規則管理 UI），故此落差目前僅能經直接 API 觸發，實際曝險低。比較 3 個合併選項（維持現狀 / 抽共用 helper 統一三對齊型別【建議，2-3 SP】/ 全面合併含 stay-based【不建議】），結論**非急迫，可視未來容量排入，不需 PO 決策**。誠實：純評估無程式碼變動，無需回歸測試 |
| AI-2415 | 部分退款（任意金額，運費不退）| Sprint 52（Phase C 只做全額退款） | Sprint 56 | **PO 決策**：(1) 部分退款粒度＝任意金額（非選品項）；(2) 運費不退。US-001：V63 migration `payments` 加 `refunded_amount`；`PaymentStatus` 加 `PARTIALLY_REFUNDED`；`PaymentStateService.refundOrderPayment` 擴充 `amount` 參數（null=剩餘全額向後相容，指定需正數且不超剩餘可退額度，逾越丟 `E_6009`）；累計 `refundedAmount`，達全額轉 `REFUNDED`+`Order.REFUNDED`，未達轉 `PARTIALLY_REFUNDED`（Order 狀態不變）；`OrderPaymentController`/`OrderPaymentStateDto` 同步擴充；抽 `resolveRefundAmount`/`executeStripeRefund` 兩輔助方法消 NPathComplexity。後端單元 512（新增 UT-PAY-STRIPE-008~011 + 更新 005/006）+ 真 DB 整合 419（`M07PaymentMockIntegrationTest` 不退步）全量回歸 0 fail、`make validate-schema` 無漂移（V63）。誠實：只修真 Stripe 退款路徑（`refundOrderPayment`），**未動平行的純 Mock 路徑**（`PaymentService.processRefund`，技術債現況記錄不修）；`stripeRefundId` 僅存最後一次退款 id，多次部分退款完整歷史需獨立子表（本次不做）；webhook（`charge.refunded`）路徑仍假設全額，未解析部分退款金額 |
| AI-2202f | 開放窗清除機制 | Sprint 47（部分更新慣例限制） | Sprint 57 | **工程設計決策（非業務）**：新增專屬清除端點，不改全域 DTO 慣例。US-001：`RoomService.clearOpenWindow` 一次性將 `openUntilDate`/`bookingWindowDays` 清回 null；`RoomController` 新增 `DELETE /v2/rooms/{listingId}/open-window`（`room:update` 權限，比照既有 `CartController.clearCart` 模式）；前端 `RoomForm.tsx` 新增「清除開放窗設定」按鈕呼叫新端點（修正清空輸入框送出不會清除的誤導性行為）。後端單元新增 `RoomServiceTest`（3 tests：清除成功/不影響其他欄位/找不到房源）+ 全量單元 515 + 真 DB 整合 422 全量回歸 0 fail、`make validate-schema` 無漂移（schema-free）、前端 build 0 error。誠實：未新增 RoomController 層級 E2E 測試（專案內 Room CRUD 端點原本就無此類測試，與既有慣例一致，非本次降低覆蓋率）|

---

## Sprint 歷史紀錄

### Sprint 57 (2026-07-04)

**主題**: 開放窗清除機制（2 SP，US-001 完成）

**完成**:
- **AI-2202f → ✅ 完成（US-001）**：詳見「已完成延後項目」表格。新增 `DELETE /v2/rooms/{listingId}/open-window` 端點 + `RoomForm.tsx` 清除按鈕，讓賣家可將開放窗欄位清回無限制。

**驗證**:
- 後端單元 515 tests 0 fail（新增 `RoomServiceTest` 3 tests）+ 真 DB 整合 422 tests 0 fail；`make validate-schema` 無漂移（schema-free）；前端 `npm run build` 0 error。catch(Exception)/@Deprecated=0。
- 誠實：機制選擇（專屬端點 vs DTO wrapper 型別）為純工程設計決策，依既有「非 null 才更新」全域慣例判斷新增端點風險最低，未徵詢 PO（非業務語意事項）；未新增 RoomController 層級 E2E 測試，因專案內 Room CRUD 端點本就無此類既有測試（非本次降低覆蓋率）。

**下一 Sprint 候選**:
- AI-2408 availability reason 錯誤碼化 + i18n（P4）、AI-2416 真實金流 Phase D-2 分潤（P3，需 Phase D-1 上線，目前不可執行）、（未立案）純 Mock 退款路徑與真 Stripe 路徑整合評估（P4）、AI-1903 真人 live 走查（需環境，目前不可執行）。

**里程碑**：**開放窗語意缺口收尾**（AI-2202e 建立語意 + AI-2202f 補上清除機制，M06 開放窗功能完整）。活躍延後：AI-2416（P3）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 56 (2026-07-04)

**主題**: 部分退款——任意金額，運費不退（5 SP，US-001 完成）

**完成**:
- **AI-2415 → ✅ 完成（US-001）**：詳見「已完成延後項目」表格。PO 決策（任意金額粒度 + 運費不退）後，`PaymentStateService.refundOrderPayment` 支援部分退款，V63 migration + `PARTIALLY_REFUNDED` 狀態。

**驗證**:
- 後端單元 512 tests 0 fail（含新增 UT-PAY-STRIPE-008~011 + 更新 005/006）+ 真 DB 整合 419 tests 0 fail（`M07PaymentMockIntegrationTest` 8 tests 不退步）；`make validate-schema` 無漂移（V63）。catch(Exception)/@Deprecated=0。
- 誠實：只修真 Stripe 退款路徑，未動平行的純 Mock 退款路徑（技術債，記錄不修）；`stripeRefundId` 僅存最後一次退款 id；webhook 路徑仍假設全額退款。

**下一 Sprint 候選**:
- AI-2202f 開放窗清除機制（P4）、AI-2408 availability reason 錯誤碼化 + i18n（P4）、AI-2416 真實金流 Phase D-2 分潤（P3，需 Phase D-1 上線，目前不可執行）、AI-1903 真人 live 走查（需環境，目前不可執行）、（技術債，未立案）純 Mock 退款路徑與真 Stripe 路徑整合評估。

**里程碑**：**退款機制擴充支援部分金額**——與全額退款（Phase C）並存，PO 決策的兩項業務語意（任意金額粒度、運費不退）已落地。活躍延後：AI-2416（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 55 (2026-07-04)

**主題**: 定價計算器統一評估——PRODUCT/ROOM 兩套計算器分歧探勘（3 SP，US-001 完成，Spike）

**完成**:
- **AI-2409 → ✅ 完成（US-001，Spike）**：詳見「已完成延後項目」表格。產出 `docs/06_quality/PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md`，結論：三個已對齊型別（MANUAL_OVERRIDE/SEASONAL/WEEKDAY_WEEKEND）僅程式碼重複、無語意問題；三個 stay-based 型別（EARLY_BIRD/LONG_STAY/LAST_MINUTE）對 PRODUCT 無自然語意；發現的靜默 gating 略過落差因前端無 PRODUCT 定價規則管理 UI 而實際曝險低。**不改 production code**。

**驗證**:
- 純文件產出，不動 code；既有測試狀態沿用 S54（後端單元 508 + 整合 415，0 fail）；catch(Exception)/@Deprecated=0；schema-free。
- 誠實：spike 型（決策文件非可運行功能，SP 偏輕）；建議選項（抽共用 helper）非急迫，不強制排入後續 Sprint。

**下一 Sprint 候選**:
- AI-2415 部分退款評估（P4）、AI-2202f 開放窗清除機制（P4）、AI-2408 availability reason 錯誤碼化 + i18n（P4）、AI-2416 真實金流 Phase D-2 分潤（P3，需 Phase D-1 上線）、AI-1903 真人 live 走查（需環境）。

**里程碑**：**定價計算器分歧已釐清**——確認現況為合理分歧非缺陷，無急迫技術債。活躍延後：AI-2415（P4）/ AI-2416（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 54 (2026-07-04)

**主題**: 定價規則選取語意修正——Range 查詢 overlap 化 + 同優先級 tie-break 落實（6 SP，US-001~002 全數完成 + US-003 文件更新）

**完成**:
- **AI-2407 → ✅ 完成（US-001+US-002）**：詳見「已完成延後項目」表格。PO 決策同優先級 tie-break 業務語意為「後建立者優先」；range 查詢 containment→overlap 修正部分晚數規則漏套問題；排序 Comparator 補 createdAt 次鍵；修正既有空斷言測試 UT-M12-009。
- **US-003 → ✅ 完成**：本文件 AI-2407 狀態更新（活躍延後移至已完成）。

**驗證**:
- 後端單元 508 tests 0 fail（含 UT-M12-009 修正 + UT-M12-019 新增）+ 真 DB 整合 415 tests 0 fail（含 API-M06-017 新增，`BookingControllerE2ETest` 21 tests 0 fail）；`make validate-schema` 無漂移（schema-free，無 migration）。catch(Exception)/@Deprecated=0。
- 誠實：只修正查詢語意（候選規則前置篩選）與排序次鍵（tie-break），`isRuleApplicable`/`calculateAdjustment` 逐日精準計算核心不動；本 Sprint 無 migration、無前端變動。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S54（本 Sprint 決策先規劃後 push，收尾時徵詢）、AI-2416 真實金流 Phase D-2 分潤（需 Phase D-1 上線）、AI-2409 定價計算器統一評估、AI-2202f 開放窗清除、AI-2408 reason i18n、AI-1903 真人 live 走查（需環境）。

**里程碑**：**定價規則選取語意缺口收斂**——range 查詢語意正確化 + 同優先級行為明確化（不再依賴資料庫未定義回傳順序）。活躍延後：AI-2409（P4）/ AI-2415（P4）/ AI-2416（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 53 (2026-07-04)

**主題**: 真實金流 Phase D-1——Stripe Connect Express 帳戶 onboarding（8 SP，US-001~002 全數完成 + US-003/US-004 不計點文件產出）

**完成**:
- **AI-2413 → ✅ Phase D-1 完成（US-001+US-002，後端）**：Connect Express 帳戶 onboarding。V62 migration（tenants 加 4 個 Connect 欄位）；StripePaymentGateway 新增 createConnectAccount/createAccountLink/getConnectAccountStatus；TenantStripeConnectService（onboarding 發起/複用/狀態查詢，toggle 保護）；SellerDashboardController onboarding/status 端點；PaymentWebhookService 擴充 account.updated dispatch。
- **AI-2414 → ✅ 完成（US-003）**：`STRIPE_PRODUCTION_CHECKLIST.md` 產出（金鑰/webhook/Connect/測試模式端到端驗證項目/正式金鑰切換順序）。
- **AI-1903 → 🟡 部分（US-004，同 Sprint 41 模式）**：更新既有 `BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`（新增真 Stripe toggle 走查項 + 連結上線 checklist）；真人 live 走查本身仍續留待使用者執行。

**驗證**:
- 後端單元 20（StripePaymentGateway +4 含 TC-S007~010、TenantStripeConnectService 6 含 UT-CONNECT-001~006、PaymentWebhookService +2 含 UT-WH-007~008）+ 真 DB 整合 4（M13SellerStripeConnectIntegrationTest，IT-CONNECT-001~004）+ **全量回歸 536 tests 0 fail**（含 M16 ERP 37，因 TestDatabaseInitializer/M16ErpIntegrationTest 兩處 raw SQL tenant 種子需同步補新增 NOT NULL 欄位，屬既有「erp-tenant-test-seeding-gotcha」模式再現，已修正）；`make validate-schema` 無漂移（V62）。catch(Exception)/@Deprecated=0。
- 誠實：只做帳戶 onboarding，**不做代收後 transfer 分潤**（另立 AI-2416，Phase D-2，Sprint 55）；不含前端（賣家後台按鈕串接視後續容量）；只做 Express（非 Standard/Custom）；正式上線需人工確認 Stripe Connect Platform Profile 已完成平台資料送審（非本 Sprint 可代查）。
- **範圍決策**（PO 授權 Claude Code 逕行決策，2026-07-04）：原規劃 US-001~004 共 10 SP 高於歷史區間，下修為正式承諾 8 SP（US-001+US-002），US-003/US-004 降為不計點附屬產出；AI-2407 定價規則語意評估延後至 Sprint 54（前置調查發現規模達 5-8 SP 且需 tie-break 業務語意決策，非小型修正）。

**新增延後項目**:
- **AI-2416（P3）**：真實金流 Phase D-2，代收後 transfer 分潤/提現（需 Phase D-1 帳戶已上線）。

**下一 Sprint 候選**:
- AI-2407 定價規則選取語意評估（range 查詢 containment→overlap 修正 + 同優先級 tie-break 業務語意決策，Sprint 54 主軸）、AI-2416 Phase D-2 分帳、AI-1903 真人 live 走查（需環境）。

**里程碑**：**真實金流閉環擴展至賣家 onboarding**——付款（Phase A）+ 權威狀態（Phase B）+ 退款（Phase C）+ 賣家 Connect 帳戶開通（Phase D-1）皆真實。剩代收後分潤（Phase D-2）。活躍延後：AI-2407（P3）/ AI-2409（P4）/ AI-2416（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 52 (2026-07-03)

**主題**: 真實金流 Phase C——退款真串接（Stripe Refund）（5 SP，US-001~002 全數完成）

**完成**:
- **AI-2412 → ✅ 完成（US-001+US-002，後端）**：退款由 stub/mock 改真實。StripePaymentGateway.processRefund 真 Refund.create（payment_intent 全額退款）；PaymentStateService refundOrderPayment toggle-aware（stripe 真退款+存 stripe_refund_id / mock 保留）；PaymentWebhookService charge.refunded 權威 REFUNDED；createCheckoutSession 補 pi metadata order_id；V61 stripe_refund_id。

**驗證**:
- 後端單元 19（StripePaymentGateway 6 含 TC-S006 + PaymentStateServiceStripe 7 含退款 005~007 + PaymentWebhookService 6 含 UT-WH-006）+ 真 DB 整合 21（mock/Phase A/B 不退步）全過；mvn 0 error、checkstyle 綠；`make validate-schema` 無漂移（V61）；`make validate-e2e` **54 passed / 6 skipped / 0 failed**（持平，後端聚焦）。catch(Exception)/@Deprecated=0。
- 誠實：只做全額退款（partial 另立 AI-2415）；測試以 WireMock（真退款端到端於測試模式人工驗證，AI-2414）；餘 confirmPayment/getPaymentStatus stub（Checkout 未用）；⚠️ V61 schema。

**新增延後項目**:
- **AI-2415（P4）**：部分退款（partially_refunded）評估。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S52、AI-2414 真金流上線 checklist、AI-2413 Phase D 分帳、AI-2407 定價語意、AI-1903 真人 live 走查（需環境）。

**里程碑**：**真實金流付款閉環完整**——付款（Phase A）+ 權威狀態（Phase B）+ 退款（Phase C）皆真實。剩分帳（Phase D）+ 上線 checklist。活躍延後：AI-2413（P3）/ AI-2414（P2 上線前）/ AI-2415（P4）/ AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 51 (2026-07-03)

**主題**: 真實金流 Phase B——webhook 事件驅動的權威付款狀態（5 SP，US-001~002 全數完成）

**完成**:
- **AI-2411 → ✅ 完成（US-001+US-002，後端）**：補 Phase A「買家未回跳」缺口。PaymentWebhookService 解析 Stripe 事件（checkout.session.completed[paid]→SUCCESS+Order PAID 權威、payment_intent.payment_failed→FAILED）；StripeWebhookController 驗簽後委派一律回 2xx；抽 markStripePaymentSucceeded/Failed 共用核心（回跳與 webhook 雙路徑一致）；V60 processed_stripe_events 去重 + 雙層冪等。

**驗證**:
- 後端單元 9（PaymentWebhookServiceTest 5 UT-WH-001~005 + PaymentStateServiceStripe 4）+ 真 DB 整合 21（mock/Phase A 不退步 + Spring context 載入新 bean）全過；mvn 0 error、checkstyle 綠；`make validate-schema` 無漂移（V60）；`make validate-e2e` **54 passed / 6 skipped / 0 failed**（持平，後端聚焦無新前端 E2E）。catch(Exception)/@Deprecated=0。
- 誠實：只做付款成功/失敗（退款留 Phase C/AI-2412）；失敗路徑 best-effort（Checkout 惰性建 pi）；測試模式 secret 空跳驗簽，生產須配 STRIPE_WEBHOOK_SECRET（上線 checklist AI-2414）；⚠️ V60 schema。

**新增延後項目**:
- **AI-2414（P2，上線前）**：真金流上線 checklist（webhook secret/端點、Stripe 測試模式端到端人工驗證、真金鑰）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S51、AI-2412 Phase C 退款、AI-2414 上線 checklist、AI-2413 Phase D 分帳、AI-2407 定價語意、AI-1903 真人 live 走查（需環境）。

**里程碑**：**真實金流付款閉環具上線基礎**——Phase A 卡片付款 + Phase B webhook 權威狀態。活躍延後：AI-2412（P3）/ AI-2413（P3）/ AI-2414（P2 上線前）/ AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 50 (2026-07-03)

**主題**: 真實金流 Phase A——卡片付款 MVP（Stripe Checkout hosted，平台代收）（8 SP，US-001~002 全數完成）

**完成**:
- **AI-2410 → ✅ 完成（US-001 後端 + US-002 前端）**：承 S49 評估分階段路線 + PO 拍板（Checkout hosted + 先平台代收）。V59 payments 加 Stripe 欄位；接回孤兒 gateway（createCheckoutSession/retrieve）；STRIPE_PAYMENT_ENABLED toggle（預設關=mock）；initiateStripeCheckout + confirmStripeCheckout（回跳 retrieve，冪等）；端點 /pay/checkout + /return；前端 orders/[id] 重導 + success/cancel + E2E-M11-013。真實卡片收款閉環（灰度）。

**驗證**:
- 後端單元 9（StripePaymentGateway 5 含 WireMock TC-S004/005 + PaymentStateService 4）+ 真 DB 整合 25（mock 路徑不退步 + Spring context 載入新依賴）全過；mvn 0 error、checkstyle 綠；`make validate-schema` 無漂移（V59）；`make validate-e2e` **54 passed / 6 skipped / 0 failed**（+1 E2E-M11-013）。catch(Exception)/@Deprecated=0。
- 誠實：Phase A 僅回跳 retrieve（買家未回跳狀態滯後，webhook 權威狀態留 Phase B/AI-2411，上線前必要）；平台代收、分帳/提現留 Phase D；confirm/refund/getStatus 仍為既有 stub（退款留 Phase C）；測試以 WireMock + 前端 mock（不打真 Stripe）；⚠️ V59 打破 schema-free。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S50、**AI-2411 Phase B webhook 驅動狀態（P2，上線前必要，建議緊接）**、AI-2412 Phase C 退款、AI-2413 Phase D 分帳、AI-2407 定價規則語意、AI-1903 真人 live 走查（需環境）。

**里程碑**：**真實金流首個可運行階段落地**——Stripe Checkout 卡片付款閉環（toggle 灰度）。活躍延後：AI-2411（P2）/ AI-2412（P3）/ AI-2413（P3）/ AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 49 (2026-07-03)

**主題**: 真實金流（Stripe）上線評估——決策先行 spike（backlog #10）（5 SP，US-001~002 全數完成）

**完成**:
- **backlog #10 評估 → ✅ 完成（US-001+US-002，spike）**：產出 [PAYMENT_INTEGRATION_ASSESSMENT.md](../07_design/PAYMENT_INTEGRATION_ASSESSMENT.md)——揭穿「Stripe 已整合」假象（兩套並行付款程式碼：上線純 Mock + 孤兒 Gateway 抽象層無人注入，S14/S21 遺留死碼）；real/stub/missing 速查表；分階段路線（Phase A 卡片 MVP→B webhook→C 退款→D 分帳）；§mock↔real toggle + §Connect vs 手動分帳 + §Stripe.js 選型 + §待 PO 決策 6 項。**不改 production code、無 schema 變更**。

**驗證**:
- 純文件、不動 code；既有測試狀態沿用 S48（後端單元 22 + 整合 54 + validate-e2e 53 passed/0 fail）；catch(Exception)/@Deprecated=0；schema-free（V58）。
- 誠實：spike 型（決策文件非可運行功能，SP 偏輕）；真實金流實作（13 SP + 外部依賴）分 AI-2410~2413 另立；Stripe scaffolding 為 S14/S21 遺留孤兒死碼。

**新增延後項目**:
- **AI-2410（P3）**：真實金流 Phase A 卡片付款 MVP（需 PO 決策 + Stripe 帳號）。
- **AI-2411（P3）**：Phase B webhook 驅動狀態。
- **AI-2412（P3）**：Phase C 退款真串接。
- **AI-2413（P3）**：Phase D 分帳/提現（需 PO 決策 Connect vs 手動）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S49、**金流 §待 PO 決策 6 項 → 決策後啟動 AI-2410 卡片付款 MVP**、AI-2407 定價規則語意、AI-1903 真人 live 走查（需環境），或其他新功能。

**里程碑**：**真實金流評估完成**——backlog #10 拆為 Phase A~D + 6 項 PO 決策 + 4 實作 US（AI-2410~2413）。活躍延後：AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ AI-2410~2413（P3，金流實作）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 48 (2026-07-03)

**主題**: PRODUCT/cart 漲價——M12 進階定價 PRODUCT 側收官（8 SP，US-001~002 全數完成）

**完成**:
- **AI-2406c → ✅ 完成（US-001 後端 + US-002 前端）**：承 S46 界線（PRODUCT 另立）。破**兩道閘門**——閘門 2（結構性）applyProductRule 由 discount-only 擴充支援漲價型（MANUAL_OVERRIDE/SEASONAL/WEEKDAY_WEEKEND，對齊 ROOM config key，保留 discountPercent 向後相容）+ 閘門 1 RedisCartService 折扣閘門放寬含漲價；CartItemResponse priceAdjustmentType + 有號 discountAmount；下單自動繼承。前端 cart/page 首次顯示 item 雙向定價（折扣刪除線+綠標/漲價不刪除線+橙標）+ E2E-M11-012。

**驗證**:
- 後端單元 22（PricingService 18 含 ProductEffectivePriceTests 4 + Cart 4）+ 真 DB 整合 54（含 IT-EP-004 漲價 + cart/order/PRODUCT 折扣不退步）全過；mvn 0 error、checkstyle 綠；`make validate-e2e` **53 passed / 6 skipped / 0 failed**（+1 E2E-M11-012）+ schema 無漂移。catch(Exception)/@Deprecated=0。**schema-free**（V58）。
- 誠實：SP 初估 3→探勘修正 8（兩道閘門，PRODUCT 計價核心結構性是 discount-only）；checkout 不 itemize PRODUCT 未改；PRODUCT/ROOM 兩套計算器對齊 config key 未合併；向後相容保住 S44 折扣。

**新增延後項目**:
- **AI-2409（P4）**：定價計算器統一評估（PRODUCT/ROOM 兩套計算器分歧）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S48、AI-2407 定價規則選取語意、AI-2409 計算器統一、AI-2202f 開放窗清除、AI-2408 reason i18n、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能。

**里程碑**：**M12 進階定價全面收官**——ROOM+PRODUCT 折扣+漲價皆「顯示與收費一致」。活躍延後：AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 47 (2026-07-03)

**主題**: 開放窗語意實作——區分「未開放 vs 可訂」（7 SP，US-001~002 全數完成）

**完成**:
- **AI-2202e → ✅ 完成（US-001 後端 + US-002 前端）**：承 S45 決策文件與 PO 拍板選項 A + 追加滾動視窗 + host UI。V58 migration rooms 加 open_until_date + booking_window_days（皆 nullable、NULL=無限制、backfill 免異動）；抽 Room.resolveOpenUntil（取最早生效）三層一律呼叫；三層一致（getCalendar 補 NOT_OPEN【計算產物非持久化】、checkAvailability 超窗擋、createBooking+reschedule 超窗擋訂 E-3002）；前端 MonthCalendar NOT_OPEN 灰底禁選不刪除線 + RoomForm 雙欄位 + E2E-ROOM-10/11。

**驗證**:
- 後端單元 9（RoomOpenWindow 5 + BookingServiceOpenWindow 4）+ 真 DB 整合 38（含 API-M06-016 三層一致）全過；mvn 0 error、checkstyle 綠；`make validate-schema` **無漂移**（V58 對齊）；`make validate-e2e` **52 passed / 6 skipped / 0 failed**（+2 NOT_OPEN E2E；既有不退步）。catch(Exception)/@Deprecated=0。
- 誠實：**V58 結束 S42~S46 連續零-migration**（PO 已知悉）；NOT_OPEN 計算非持久化；開放窗擋訂集中 BookingService caller 層（RoomCalendarService 未改）；部分更新無法清窗回 NULL（既有慣例）；reason 為後端英文字串；只做 ROOM。

**新增延後項目**:
- **AI-2202f（P4）**：開放窗清除機制（部分更新慣例無法清回 NULL）。
- **AI-2408（P4）**：availability reason 錯誤碼化 + 前端 i18n。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S47、AI-2406c PRODUCT/cart 漲價評估、AI-2407 定價規則選取語意評估、AI-2202f 開放窗清除、AI-2408 reason i18n、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能。

**里程碑**：**開放窗語意三層一致落地**——收掉「未開放 vs 可訂」產品缺口；連續零-migration 於本 Sprint（V58）結束。活躍延後：AI-2406c（P3）/ AI-2407（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 46 (2026-07-03)

**主題**: 定價機制真正統一——漲價型規則計入 ROOM booking（M12 進階定價收官）（8 SP，US-001~002 全數完成）

**完成**:
- **AI-2406b → ✅ 完成（US-001 後端 + US-002 前端）**：承 S45 決策文件與 PO 拍板**選項 B**，漲價型規則（週末/旺季/手動調高）計入 ROOM booking。後端放寬三處折扣閘門（tryDynamicPricing `<baseTotal`→`≠0`、getCalendar 逐日 `<0`→`≠0`、calculateTotalAmount toggle 開即採 adjustedTotal），使 availability/月曆/建單 totalAmount 一律含漲價乘數；保留 toggle 關短路+失敗降級（向後相容）；計算核心不動；PricingService 抽 `resolveListingForPricing` 優雅降級（無 Room fallback basePrice、null 回 4xx 非 NPE→500）；DTO 中性調整語意（discountAmount 改有號差額、新增 priceAdjustmentType DISCOUNT/MARKUP/NONE）。前端 ListingDetail/MonthCalendar 雙向顯示（折扣刪除線+綠 badge「省 X」、漲價不刪除線+橙 badge「加價 X」）+ E2E-ROOM-08/09 漲價變體。

**驗證**:
- 後端單元 18（BookingServiceDynamicPricing 4 + PricingService 14，含漲價 UT-BK-DP-004 + 降級 UT-M12-013/014）+ 真 DB 整合 34（BookingControllerE2E 含漲價 API-M06-015 + M12 + booking）全過；mvn 0 error、checkstyle 綠；`make validate-e2e` **50 passed / 6 skipped / 0 failed**（+2 漲價 E2E；折扣不退步）、schema 對齊。**無 schema 變動**（連續 S42~S46 零 migration，Flyway V57）。catch(Exception)/@Deprecated=0。
- 誠實：**行為變更**（toggle 開啟時漲價計入訂房金額，PO 拍板）；只做 ROOM（PRODUCT 另立 AI-2406c）；bestRule priority / range 查詢落差記錄不修（另立 AI-2407）；E2E 編號順延 06/07→08/09；上一對話 Bash 重複輸出環境故障，本 session 開工先 probe 驗環境 + 交叉驗證 commit 落盤後才續作。

**新增延後項目**:
- **AI-2406c（P3）**：PRODUCT/cart 漲價評估（RedisCartService 目前只折扣，評估是否對齊 ROOM）。
- **AI-2407（P3）**：定價規則選取語意評估（bestRule priority 治理 + findActiveRulesForDateRange 逐日精準查詢）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S46、**AI-2202e 開放窗實作（需 PO 拍板 schema）**、AI-2406c PRODUCT 漲價評估、AI-2407 定價規則語意評估、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能。

**里程碑**：**M12 進階定價真正收官**——折扣 + 漲價皆「顯示與收費一致」。活躍延後：AI-2202e（P3）/ AI-2406c（P3）/ AI-2407（P3）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 45 (2026-07-02)

**主題**: 定價區技術債收斂（清死碼 + 語意決策）（5 SP，US-001~002 全數完成）

**完成**:
- **AI-2406 → ✅ 完成（US-001）**：定價機制統一——探勘揭穿「雙定價機制並存」實為**死碼假象**（`room_calendar.price` 寫入路徑 setDatePrice/setDatePriceBulk 零呼叫者、欄位恆 NULL）；移除死碼兩方法 + BookingService 三處讀取移除死欄位 fallback 改直取 basePrice（**行為等價**，順帶修正 calendarBaseTotal NULL→ZERO 潛在低估）；RoomCalendar.price 註解標記停用；確立 MANUAL_OVERRIDE 為唯一手動日價路徑。決策文件 PRICING_MECHANISM_UNIFICATION.md。schema-free。
- **AI-2202d → ✅ 完成（US-002，spike）**：開放窗「未開放 vs 可訂」語意評估——決策文件 CALENDAR_OPEN_WINDOW_ASSESSMENT.md（三層硬語意 + 三選項比較 + NULL 安全過渡），不改 production code。

**驗證**:
- 後端單元 6（計價相關）+ 真 DB 整合 57（booking/M12/cart/order 計價）全過；mvn 0 error；`make validate-e2e` **48 passed / 6 skipped / 0 failed**（US-001 清理不退步；S45 無新增 E2E）、schema 對齊。**無 schema 變動**（連續 S42~S45 零 migration，Flyway V57）。`@Deprecated=0` 維持（用註解非 annotation，Rule 11）。
- 誠實：本 Sprint 為「決策 + 低風險清理」型（5 SP 偏輕，已於規劃揭露並經使用者核准）；US-001 行為等價；重實作/行為變更均誠實另立。

**新增延後項目**:
- **AI-2406b（P2）**：漲價型規則是否計入 booking 總價（計價行為變更，需 PO 決策；選項 A 維持 / B 全面走 PricingService，5-8 SP）。
- **AI-2202e（P3）**：開放窗語意實作（需 PO 拍板 schema；migration + 四處 booking 邏輯 + 前端 + E2E + 既有房源 backfill，5 SP）。
- （後續低風險）room_calendar.price DROP COLUMN（`V58`，無資料無讀寫者，風險極低）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S45、**AI-2406b 漲價計入 booking（需 PO 決策）**、**AI-2202e 開放窗實作（需 PO 拍板 schema）**、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能。

**里程碑**：**定價區技術債收斂**——清除 room_calendar.price 死碼機制、確立 MANUAL_OVERRIDE 為唯一手動日價路徑；開放窗語意產出完整決策文件。活躍延後：AI-2406b（P2）/ AI-2202e（P3）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 44 (2026-07-02)

**主題**: 完成 M12 進階定價全覆蓋（8 SP，US-001~003 全數完成）

**完成**:
- **AI-2403 → ✅ 完成（US-001）**：進階定價接入 PRODUCT 購物車/訂單計價鏈——getCart 讀取重算 getEffectivePrice 折扣（toggle+向後相容），OrderService 繼承（顯示與下單一致），CartItemResponse transient 折扣欄位。schema-free。
- **AI-2405b → ✅ 完成（US-002）**：買家整月日曆每日折扣——getCalendar merge calculatePrice breakdown，MonthCalendar 原價刪除線，E2E-ROOM-07。
- **AI-2303 → ✅ 完成（US-003）**：Inter 字體自 host（next/font/local + committed woff2），消 build 期 Google Fonts 依賴；CJK 系統堆疊不動。

**驗證**:
- 後端單元 23（RedisCartServiceDynamicPricing 3 + RedisCartService 20）+ 真 DB 整合 71（cart/order/M12 45 + calendar 26）全過；前端 tsc/build 0 error（無 next/font/google）；`make validate-e2e` **48 passed / 6 skipped / 0 failed**（含 E2E-ROOM-07）、schema 對齊。**無 schema 變動**（連續 S42~S44 零 migration）。
- 誠實：schema-free（訂單不留折扣前原價欄位）；cart/order/日曆只套折扣型（與 availability 一致）。

**新增延後項目**:
- （無新增；AI-2406 定價機制統一、AI-2202d 日曆開放窗語意續留）

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S44、AI-2406 定價機制統一、AI-2202d 日曆開放窗語意、AI-1903 真人 live 走查（需環境）、真實金流評估（#10），或新功能。

**里程碑**：**M12 進階定價完成全覆蓋**（ROOM 訂房 S43 + PRODUCT 購物車/訂單 S44 折扣皆生效，買家 availability + 整月日曆皆顯示折扣）。活躍延後：AI-2406/AI-2202d（P3）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 43 (2026-07-02)

**主題**: M12 進階定價落地（早鳥/長住/末班車折扣真正生效於 ROOM，10 SP，US-001~004 全數完成）

**完成**:
- **AI-2401 → ✅ 完成（US-001）**：早鳥/末班車折扣語意修正——改以「下單日 vs 入住日」計提前/臨近天數（原用 rule.validFrom，非業界語意）；`CalculatePriceRequest` 加 `bookingDate`；config 讀取加 Number 安全轉型（消 ClassCastException 風險）。
- **AI-2402 → ✅ 完成（US-002）**：定價引擎接入 ROOM 訂房計價鏈——`BookingService` 注入 `PricingService`+`FeatureToggleService`，`calculateTotalAmount`（訂房金額）與 `checkAvailability`（顯示）同步套折扣（toggle 保護 + 無規則向後相容）；`AvailabilityResponse` 補 originalTotalPrice/discountAmount/appliedRuleName。
- **AI-2404 → ✅ 完成（US-003）**：定價規則 config 型別化編輯 UI（discriminated union + 動態子表單，取代黑箱 {}）+ dashboard 快速管理入口。
- **AI-2405 → ✅ 完成（US-004）**：買家 ROOM 折扣顯示（折扣後+原價刪除線+標籤）+ 嵌入既有 PricingCalendarPreview 作賣家預覽 + E2E-ROOM-06。

**驗證**:
- 後端單元 PricingServiceTest 12 + BookingServiceDynamicPricingTest 3 = 15 tests 0 fail；真實 DB 整合 Booking+M12 36 tests 0 fail（不退步）；前端 tsc/build 0 error；`make validate-e2e` **47 passed / 6 skipped / 0 failed**（含 E2E-ROOM-06）、schema 對齊。**無 schema 變動**（沿用 jsonb config，Flyway V57）。
- 誠實：M12 三折扣引擎與前後端骨架早已存在但「未接線 + 語意錯誤」，本 Sprint 為接線 + 修正 + 補完 UI，非從零。

**新增延後項目**:
- **AI-2403（P2）**：進階定價接入 PRODUCT/Cart 計價鏈（本 Sprint 只接 ROOM）。
- **AI-2405b（P3）**：買家整月日曆每日折扣顯示（需擴充 getCalendar 回 discount）。
- **AI-2406（P3）**：定價機制統一（room_calendar 手動日價 vs 規則，架構債）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41+S42+S43、AI-2403 PRODUCT/Cart 折扣、AI-2405b 買家日曆折扣、AI-1903 真人 live 走查（需環境）、AI-2202d 日曆開放窗語意，或其他新功能。

**活躍延後**：AI-2202d / AI-2303 / AI-2403 / AI-2405b / AI-2406（皆非安全，P2~P3）；**活躍 DEF 仍為 0**（DEF-021/022 已結案）。

---

### Sprint 42 (2026-07-02)

**主題**: 收尾技術債（7 SP，US-001~003 全數完成）

**清償 / 完成**:
- **AI-2302 → ✅ 完成（US-001）**：backend pre-commit 提速——2 個慢速 `@SpringBootTest` 核心測試（`ReviewServiceCacheIntegrationTest`、`SellerDashboardServiceCacheTest`）加 `@Tag("slow")`，`pre-commit`/`Makefile check-backend` 加 `-DexcludedGroups=slow`；CI（act）不加排除 → pre-push 仍完整跑（零覆蓋損失）。順帶移除 pre-commit 對 test DB 的依賴（quick test 排除後為純單元）。實測 test DB DOWN 下 455 tests 0 fail。
- **DEF-022 → ✅ 完成（US-003）**：E2E 硬等待清除（歷時 S35→39→42）——5 檔冗餘 `waitForTimeout` 刪除 + 改顯式等待 + 補斷言，保留 STOMP 例外。**連帶根治** flaky（auth helper 註冊連結碰撞 → 改 goto；原生 alert teardown → dialog 處理器）。

**部分完成 / 殘留**:
- **AI-2202c（整月日曆語意細化）→ 🟡 部分（US-002）**：交付 **Part A** 每日價格顯示（純前端，basePrice fallback）。**Part B「未開放 vs 可訂」顯式標記需後端新語意**（room_calendar 無「開放窗」概念）→ 另立 **AI-2202d** 待評估。

**驗證**:
- 後端 pre-commit quick test 於 test DB DOWN 下 455 tests 0 fail、無 DB 連線錯誤；前端 tsc/eslint/build 0 error；`make validate-e2e` **46 passed / 6 skipped / 0 failed**、schema 對齊無漂移。**無 production code/schema 變動**（後端僅測試 @Tag）。
- 誠實：validate-e2e 反覆 4 次才綠（2 次 Turbopack build 抓 Inter 暫時性網路失敗 + DEF-022 清 sleep 後浮現的既有 flaky），第 5 次全綠，全程未放寬守門（未用 `E2E_GATE_STRICT=0`）。

**新增延後項目**:
- **AI-2202d（P3）**：整月日曆「未開放 vs 可訂」語意（需後端開放窗語意 + 端點擴充）。
- **AI-2303（P3）**：Inter 字體建置期 Google Fonts 依賴（評估 next/font/local 自 host，同源 DEF-015）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41+S42、AI-1903 真人 live 走查（需環境）、AI-2202d 日曆語意、AI-2303 Inter 離線化，或回歸新功能開發。

**活躍 DEF**：**0 個 DEF**（DEF-021 已決策結案、DEF-022 已完成）；活躍延後轉為 Action Item 型 AI-2202d / AI-2303（皆 P3 非急迫）。

---

### Sprint 41 (2026-07-02)

**主題**: S41 技術債徹底清償 + 整月日曆（12 SP，US-001~006 全數完成）

**清償 / 完成**:
- **AI-2301 → ✅ 完成（US-001）**：test DB↔act port 衝突制度化——`make validate-release` 於 act 前自動 `test-db-down`（冪等，涵蓋直接執行 + pre-push 兩路徑）+ `LOCAL_CI_VALIDATION.md` 開發者心智模型文件化。
- **AI-2101b → ✅ 完成（US-002）**：E2E 登入 helper 完全統一——`auth.ts` 擴充（`registerAndLogin` 回傳 userId、新增 `loginOnly`）+ 重構 at-m10-chat、at-m17-001/002/003/004。
- **AI-2202 → ✅ 完成（US-003 + US-004）**：api.ts 端點契約清理（pricing base path 對齊 + 移除 listings.update/delete 死碼 + bookings.calendar realign）+ 整月日曆（read-only 後端 `GET /v2/bookings/calendar` + MonthCalendar 前端 + E2E-ROOM-05）。
- **DEF-021 → ✅ 已決策（US-006）**：CJK 字體維持系統堆疊為 accepted fallback；選項 B（自 host woff2）記錄為選配未來任務待拍板。

**部分完成 / 殘留**:
- **AI-1903（買家 live 走查）→ 🟡 部分（US-005）**：交付自動全棧走查證據（`make validate-e2e` 47 passed/0 fail）+ 手動 live 走查 checklist（`BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`）。**殘留**：真 DB 落地的跨角色資料流（order→pay→notify→ship→review）需 cross-role seed + 部署環境，續留待真人於 live 環境走查。

**驗證**:
- 後端 BookingControllerE2ETest 18 tests 0 fail（新增 calendar API-M06-013/014）；前端 tsc/eslint/build 0 error；`make validate-e2e` **47 passed / 5 skipped / 0 failed**、schema 對齊無漂移。後端 read-only 無 DB/migration 變動。

**新增延後項目**:
- **AI-2302（P3）**：後端 pre-commit 核心測試（@SpringBootTest 逐一啟動 Spring）耗時 → 評估移出 quick test 或平行化。
- **AI-2202c（P3）**：整月日曆語意細化（未開放 vs 可訂顯式標記 + 整月價格顯示）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41、AI-1903 真人 live 走查（需環境）、AI-2202c 整月日曆語意細化、AI-2302 後端 pre-commit 提速，或回歸新功能開發。

**活躍 DEF**：DEF-022（E2E 硬等待，P3）；DEF-021 已決策結案（accepted fallback）。

---

### Sprint 40 (2026-07-02)

**完成**:
- **AI-2201（availability 端點 + 詳情頁即時可用性）→ ✅ 完成（US-001 後端 + US-002 前端）**：
  - 後端：`GET /v2/bookings/availability` GET+@RequestBody → @RequestParam（read-only、無 DB/schema）;更新 BookingControllerE2ETest（API-M06-009/010 body→queryParam）+ BookingIntegrationTest（content→param）。
  - 前端：booking service `checkAvailability` + `AvailabilityResponse`;ListingDetail ROOM 改以 availability 為主（可訂+總價 / 不可訂+原因），未確認可預訂禁用加購。
- **US-003**：at-room-booking E2E（可訂加購 / 不可訂禁用）。
- **完整 `make validate-release` 通過**：後端 act **330 tests 0 fail**、前端 act 0 error、E2E **45 passed / 6 skipped / 0 failed**、schema 無漂移。

**誠實揭露 / 環境**:
- 首次後端變動 read-only（僅 controller 參數綁定）。
- test DB（5432/6379）與 act（6379）port 衝突 → 已釐清「commit 啟 test-db-up、validate-release 前 test-db-down」流程（AI-2301）。

**續延後**:
- DEF-021（CJK 字體）續延（P3）;整月日曆 UI + api.ts 端點契約（AI-2202）續延。

**新增 Action Items（S41）**:
- AI-1906 檢查點 push（已過完整守門，強烈建議清償）、AI-1903 買家 live 走查、AI-2301 test DB/act port 衝突制度化、AI-2202 整月日曆/端點契約、AI-2101b 登入 helper 完全統一。

---

### Sprint 39 (2026-07-02)

**完成**:
- **AI-2103b（ROOM 訂房補強）→ ✅ 補強完成（US-001+002）**：ROOM 訂房閉環原已存在（checkout 內聯建 booking）;本次補 booking service `createBooking` 抽取 + 日期衝突（409/E-4001）等錯誤優雅可讀提示 + 詳情頁 ROOM 加購前日期驗證。
- **AI-2104（ROOM 閉環 E2E）→ ✅ 完成（US-003）**：mock 覆蓋 詳情計價加購 → checkout 建 booking 成功 → 409 衝突優雅提示。
- **AI-2101（E2E 共用 helper）→ ✅ 完成（US-004）**：新增 `e2e/helpers/auth.ts`（waitForURL 收斂 DEF-022）,收斂 4 檔 + 通知 flaky timeout 修。全棧 **44 passed / 6 skipped / 0 failed**。

**誠實揭露**:
- `GET /v2/bookings/availability` 為 GET+@RequestBody（瀏覽器不可呼叫）→ 詳情頁即時可用性檢查改另立 **AI-2201**;US-001 縮為 checkout 409 優雅處理。
- US-004 實際僅 4 檔可收斂（at-m10-chat 回傳 userId 保留專屬、at-m17-* beforeEach 內聯）→ 剩餘另立 **AI-2101b**。
- ROOM 購買路徑（booking vs order 平行）未統一 → **AI-2203**。

**續延後**:
- DEF-021（CJK 字體）：續延後（P3）。DEF-022（E2E 硬等待）：US-004 已收斂登入 helper 部分,其餘隨 AI-2101b。

**新增 Action Items（S40）**:
- AI-1903 買家 live 走查、AI-2201 availability 端點修復 + 詳情頁即時可用性、AI-2202 端點契約清理、AI-2101b 登入 helper 完全統一、AI-2203 ROOM 路徑決策文檔化。

---

### Sprint 38 (2026-07-02)

**完成**:
- **AI-2103（商品詳情頁）→ ✅ 完成（US-001）**：新建 `/listings/[id]`（(storefront) 公開路由 + 401 引導）;PRODUCT 數量加購 + ROOM 日期計價加購 + 三態;listing service 補 getListingById/getListingPrice;cartEvents 使 Header 購物車數即時更新;首頁連結由評價頁改導向詳情頁。無後端/DB 變動（端點/DTO 皆已存在）。
- **AI-1905（有資料 E2E）→ ✅ 完成（US-002）**：以 **page.route mock**（免後端 seed）補首頁有資料網格 + 分頁翻頁 + 詳情導覽 + 加購 + 401/404 鑑別。全棧 **41 passed / 6 skipped / 0 failed**。

**續延後**:
- DEF-021（CJK 字體）：續延後（P3）。
- DEF-022（E2E 硬等待）：併入 AI-2101（E2E 共用登入 helper 抽取，S39）。

**新增 Action Items（S39）**:
- AI-1903 買家 live 走查（需 live 環境）、AI-2101 E2E 共用 helper 抽取（含 DEF-022）、AI-2103b ROOM 完整訂房流程、AI-2104 登入態真實加購 E2E（P3）、AI-2102 secret 掃描器測試（P3）。

---

### Sprint 37 (2026-07-02)

**完成**:
- **AI-1901（買家頁全頁套版）→ ✅ 完成（US-001）**：沿用 S36 DEF-020 route-group 基建，新增 `app/(auth)/layout.tsx` 承載共用 Header/Footer，10 頁移除自包 nav 改用 StorefrontShell；Header 加 auth-aware 帳號選單（useSyncExternalStore + authStore）。build/tsc/lint 0 error；`make validate-e2e` at-buyer-pages 不退步。
- **AI-2001（m15 flaky）→ ✅ 完成（US-002）**：根因為 `loadMedia` catch 觸發原生 `alert()`，dialog 於 teardown 間歇崩潰；以 dialog 處理器 + 明確等待 + 真斷言對症修復（非重跑掩蓋）。**解鎖 `make validate-release` 守門唯一阻礙**。
- **US-003 版型一致性 E2E**：E2E-BUYER-04/05（多頁 Header/Footer 唯一 + 帳號選單登出）。
- **計畫外必要工作**：(1) 修 E2E 登入 helper 與 SearchBar submit 碰撞（US-001 副作用，7 檔）；(2) secret 掃描器正則收緊（消除 password 表單標籤誤報，經使用者核准）。全棧 **37 passed / 6 skipped / 0 failed**。

**續延後**:
- DEF-021（CJK 字體）：續延後（P3）。
- DEF-022（E2E 硬等待）：續延後，將併入 AI-2101（E2E 共用登入 helper 抽取）一併處理。

**新增 Action Items（S38）**:
- AI-2101（E2E 共用登入 helper 抽取，含 DEF-022，P2）、AI-2103（商品詳情頁評估，P2）、AI-2102（secret 掃描器正/負案例測試，P3）；AI-1903 買家 live 走查續延 S38。

---

### Sprint 36 (2026-07-01)

**完成**:
- **DEF-019（安全，物流/賣家側 IDOR）→ ✅ 完成（AI-1902）**：S33 已修訂單付款側；S36 補完物流/賣家側——LogisticsService.createLogistics 加 tenant-based 擁有權檢查（`order.tenantId==當前租戶`、admin 放行、越權 403/E_1007）、PaymentService.processOrderPayment（`/v2/payments`）加 user-based 檢查。新增 LogisticsServiceOwnershipTest + PaymentServiceOwnershipTest（各 3 tests）。**實測揪出並修好 S33 遺留的 M07 5 個失敗**（`@Transactional` 一級快取致 Order 影子 `userId` 為 null → 測試 builder 補 `.userId` + 訂單擁有者對齊呼叫者）。乾淨 DB：M07 8 + M11 4 整合 0 fail、單元 353 0 fail。**活躍安全 DEF 歸零**。
- **誠實揭露**：processBookingPayment（預訂付款側）與 processOrderPayment 屬同類但非 DEF-019（訂單/物流）範圍，未在本次處理；如需擁有權檢查應另立項評估。無 entity/migration 變更，schema 不受影響。
- **DEF-020（架構債，S37 買家頁套版前置）→ ✅ 完成（US-002）**：Architect 三項建議全數償還——route-group `app/(storefront)/layout.tsx` 承載 Header/Footer、`"use client"` 邊界下推（Shell 改 grid-only server、僅 Header client）、搜尋/篩選改走 URL（server page searchParams → props 傳 client HomeContent，免 useSearchParams+Suspense；移除 nonce 連帶償還 F-06）；key-remount 顯示 skeleton 避免 effect 同步 setState。build/type-check/lint 0 error；**at-homepage E2E 4 tests 全棧全綠**（含搜尋改走 URL），既有 E2E 不退步（唯 m15 既有 flaky）。

**續延後**:
- DEF-021（CJK 字體）/ DEF-022（E2E 硬等待）：見 Sprint 35 記錄，續於後續處理。

---

### Sprint 35 (2026-07-01)

**新增延後**:
- DEF-020（🟡 中，架構債，→ Sprint 36 導入前償還，Architect 審查建議）: 版型 Shell 由「page 內手包 StorefrontShell」改為 App Router route-group `app/(storefront)/layout.tsx` 承載 TOP/Footer；`"use client"` 邊界下推至葉節點；搜尋/購物車全站狀態改走 URL。理由：現為單一店面頁，route-group 重用效益 S36 增買家頁才顯現，現在做屬投機抽象（Rule 2）
- DEF-021（🟡 低，技術債）: CJK 字體品牌一致性 — Turbopack 無法 self-host next/font CJK，S35 改系統字體堆疊；後續評估 `next/font/local` + 子集化 Noto Sans TC woff2
- DEF-022（🟡 低，測試穩定性）: E2E 硬等待 — at-homepage 註冊/登入 helper 用 `waitForTimeout` 固定 sleep（沿用 at-buyer-pages 模式），CI 慢時可能 flaky；後續改 `waitForURL`/`waitForResponse`

**續延後**:
- DEF-019（物流/賣家側，→ Sprint 36 AI-1902）: 付款側已於 S33 修；剩 createLogistics（tenant-based，恐涉 M11 測試資料）+ processOrderPayment。S35 為賣場版型 Sprint，安全項順延但未遺漏

**更新**:
- Sprint 35 主題「賣場店面版型基礎 + 首頁改版（意象若水 RUOSHUI 套版）」：US-001~005（P1，18 SP）全完成；US-006（DEF-019 盤點 Buffer）延 S36
- 前端 build/type-check/lint 0 error；at-homepage E2E 4 tests 全綠；活躍 DEF：**1**（DEF-019 物流賣家側，安全）+ 3 個新技術/架構債（DEF-020/021/022，非安全）
- 誠實紀錄：AC-005-2 E2E 覆蓋度部分達成（缺「有資料網格 + 分頁翻頁」需 seed，AI-1905）；E2E-HOME-03 真 bug 為 Header 誤傳 value 給 SearchBar（非 SearchBar 本身 bug）

### Sprint 34 (2026-07-01)

**移除延後（已完成）**:
- DEF-017 ✅ **Sprint 34 US-001 落地清償**（歷時 Sprint 28→34，三度誠實回退後成功）：raw SQL 種 id=FIXED_TENANT_ID 租戶列（解 @GeneratedValue + FK 根因）+ JDBC UPDATE listing tenant_id（解 insertable=false 影子欄位）+ null 安全租戶檢查 + 修 @AfterAll cleanup（先刪 product_skus）+ IT-M16-307 跨租戶測試。乾淨 DB M16 43 tests 0 fail

**續延後**:
- DEF-019（物流/賣家側，→ Sprint 35）: 付款側已於 S33 修；剩 createLogistics（tenant-based，恐涉 M11 測試資料）+ processOrderPayment
- 買家 live 走查（AI-1703，→ Sprint 35）: 需 live 環境

**更新**:
- Sprint 34 主題「安全修復落地」：US-001（P1，DEF-017 落地，AI-1701）✅ 完成；US-002（DEF-019 物流）+ US-003（買家 live）因 context/風險考量延 S35
- `@Test`：M16 IT-M16-307 新增（跨租戶）；乾淨 DB M16 43 tests 0 fail
- 活躍 DEF：**1**（DEF-019 物流賣家側；DEF-017 清償、DEF-018 + DEF-019 付款側已清償）
- 里程碑：DEF-017 為近期最難項（三 Sprint、三次 commit 前攔下回退），最終以測試基建重做落地，main 全程未污染

### Sprint 33 (2026-07-01)

**新增延後**:
- （無新增 DEF）

**部分完成**:
- DEF-019（付款側 ✅）: US-002 修 getOrderPaymentState（讀）+ mockPaymentSuccess/Failure/mockRefund（寫）→ checkOrderOwnership 擁有權檢查，越權 403，21 tests 0 fail。**剩餘物流/賣家側**（createLogistics 租戶語意 + processOrderPayment）續 Sprint 34

**深入診斷後續延（誠實回退）**:
- DEF-017（🟡 中，安全，→ Sprint 34 AI-1701）: US-001 三層根因完整診斷（NPE→403→FK）——真因為 `Tenant.id` @GeneratedValue 使 @WithErpSecurity 硬編的 FIXED_TENANT_ID 在 tenants 表無列、listings.tenant_id 有 FK。修法（null 安全租戶檢查）已驗證正確，缺 M16 tenant seeding 整套重做（raw SQL 種 FIXED_TENANT_ID 租戶）。依紀律三度 commit 前本地攔下、誠實回退（main 未污染）

**更新**:
- Sprint 33 主題「安全修復收尾」：US-002（P1，DEF-019 付款側，AI-1702）完成；US-001（P1，DEF-017，AI-1701）三層根因診斷+延 S34；US-003（Buffer）併發慣例記錄（AI-1704）、live 走查延 S34（AI-1703）
- `@Test` 690→691（付款越權 E2E）；catch(Exception)=0、@Deprecated=0、Flyway V57（無新 migration）
- 活躍 DEF：2（DEF-017 ERP / DEF-019 物流賣家側；DEF-019 付款側已清償）
- 誠實紀錄：更正 Sprint 32 對 DEF-017 的 E_3003 誤判（實為 NPE→FK 三層）；DEF-019 部分交付

### Sprint 32 (2026-07-01)

**新增延後**:
- DEF-019（🟡 中，安全）: 訂單付款/物流讀寫無擁有權檢查（IDOR 姊妹）— US-001 修 getOrder 時盤點揪出（getOrderPaymentState 讀 + pay/fail/refund 寫 + logistics/payment service），非 US-001 committed 範圍，誠實延後

**移除延後（已完成）**:
- DEF-018 ✅ Sprint 32 US-001（getOrder 加 owner/admin 擁有權檢查，越權 403/E_1007，最小爆炸半徑；補 otherBuyerCannotGetOrder E2E；訂單 E2E 20 tests 0 fail）

**再驗證後續延後（誠實回退）**:
- DEF-017（🟡 中，安全，→ Sprint 33 AI-1603）: US-003（Buffer/擇機）套正確修法 + 新增跨租戶測試 IT-M16-307 **通過（修法邏輯正確）**，但原 5 個同租戶 M16 測試回 500（真因：@BeforeAll 種的 listing 在測試交易中 findById 查不到 → E_3003）。依「一次嘗試綠才留」紀律於 commit 前本地攔下、再度誠實回退（main 未污染）。**修法已驗證正確，僅缺 M16 seeding 重做**，縮小 Sprint 33 範圍

**更新**:
- Sprint 32 主題「安全修復 + 買家閉環驗證」：US-001（P1 安全，DEF-018 getOrder IDOR，AI-1601）完成；US-002（P1，買家頁面 E2E，AI-1602）完成；US-003（Buffer，DEF-017，AI-1603）調查+誠實延後
- `@Test` 靜態計數：US-001 +1（otherBuyerCannotGetOrder）；US-002 前端 e2e +3（buyer pages spec）
- 活躍 DEF：2（DEF-017 ERP / DEF-019 付款物流 IDOR；DEF-018 已清償）
- 誠實紀錄：US-001 盤點揪出 DEF-019；US-003 二度驗證仍需測試資料重做，延 Sprint 33

### Sprint 26 (2026-07-01)

**新增延後**:
- DEF-013（🟡 低）: M09 MQ 通知缺端到端驗證 — US-002 backend-only 盤點發現，套用 REALTIME_ASYNC_E2E_DOD（延 Sprint 27 US-003）
- DEF-015（🟡 低）: 前端 next/font/google 建置期外部抓取 — DEF-014 驗證時發現，離線 build 失敗（延 Sprint 27 US-002）

**移除延後（已完成）**:
- DEF-009 ✅ Sprint 26 US-006（Logistics jsonb 統一 Map + @JdbcTypeCode）
- DEF-010 ✅ Sprint 26 US-004（移除 SHIPPING→CANCELLED + 一致性不變量）
- DEF-011 ✅ Sprint 26 US-004（cancelLogistics 錯誤碼 E_7500 系列）
- DEF-012 ✅ Sprint 26 US-003（廣播 conversationId 改由 conversation 取得）
- DEF-014 ✅ Sprint 26（validate-e2e.sh API_URL 修正，e2e strict）

**更新**:
- Sprint 26 承諾 7 SP + Buffer 2 SP = 9 SP 全完成（US-001~006）
- 計畫外重大工作：本地優先 CI 整套（停用雲端自動 CI、validate-e2e/release、pre-push v4→v5、push 降頻）
- `@Test` 靜態 668（+9）、catch(Exception)=0、@Deprecated=0、Flyway V56（無新 migration）
- **活躍 DEF 降至 2 個低優先**（DEF-013/015），技術債近清零、backlog 見底
- **新增 Action Items（Sprint 27）**：AI-1101 產品方向決策（P1，需人工）、AI-1102 DEF-015、AI-1103 DEF-013、AI-1104 pre-push v5 實測、AI-1105 守門腳本回歸

### Sprint 25 (2026-06-29，進行中)

**新增延後**:
- DEF-009（🟡 中優先，實際低急迫）: Logistics.logisticsData jsonb 映射慣例統一 — US-002 全庫盤點發現的唯一慣例不一致，列為技術債，不在本 US 動工

**移除延後（已完成）**:
- （無）

**更新**:
- US-001（P0，AI-901）✅ 完成：建立 `make validate-schema` schema 漂移守門關卡，雙向驗證（正向 exit 0 / 負向 exit 1 攔下 missing column）
- US-002（P1，AI-902）✅ 完成：[ENTITY_MIGRATION_AUDIT.md](../06_quality/ENTITY_MIGRATION_AUDIT.md) — 51 entity 全數通過 validate，零孤兒表、零 `SqlTypes.ARRAY` 殘留、3 個歷史 `TEXT[]` 全部封閉；固化防漂移慣例

### Sprint 24 (2026-06-29)

**新增延後**:
- （無正式 DEF 項目）Buffer US-004（SSH pre-push 優化）+ US-005（M11 物流取消流程）未啟動，改以 Retro Action Items 追蹤（AI-804 / AI-903，延續 Sprint 25）

**移除延後（已完成）**:
- （無，本 Sprint 無活躍 DEF 項目）

**更新**:
- Sprint 24 承諾範圍 100% 完成：US-001~003（7 SP，P0+P1）
- AI-801（TestSecurityContextHelper）+ AI-803（M13 Redis TTL）完成；AI-802 / AI-804 延續 Sprint 25
- **計畫外重大事件**：GitHub E2E 暴露 backend 啟動失敗，投入 9 個 commit 修復 schema 漂移（Flyway V48~V55：補齊 5 張缺漏建表 + 統一 ARRAY→jsonb）
- **根因**：本地 act 用 `ddl-auto=update`，GitHub E2E 用 schema 驗證 → entity/migration 漂移本地偵測不到（已記入 Retro AI-901 P0）
- Buffer 容量（3 SP）被 E2E 救火完全佔用，連續 3 Sprint 以來首次 Buffer 0% 啟動
- Sprint 24 v2026.09.26-01 發布
- **新增 Action Items（Sprint 25）**：AI-901 本地 schema 驗證關卡（P0）、AI-902 entity↔migration 一致性盤點（P1）、AI-903 M11 取消流程業務規則確認

---

### Sprint 23 (2026-06-27)

**新增延後**:
- （無新增延後項目）

**移除延後（已完成）**:
- DEF-007: M11 物流與訂單履約整合 ✅ Sprint 23 US-004 完成（createLogistics + 狀態同步，4 個整合測試）
- DEF-008: ShippingTemplate 接入結帳流程 ✅ Sprint 23 US-005 Buffer-A 完成（V47 + shippingFee + 免運邏輯，3 個整合測試）

**更新**:
- Sprint 23 完成，6/6 US 全數達成（含 Buffer-A + Buffer-B，100% Buffer 利用率）
- Sprint 23 Integration Tests: ~317 tests, 0 Failures（新增 +15）
- Sprint 23 Unit Tests: ~326（新增 +3）
- act CI（make validate-all）整體通過（2026-06-27 19:48:47）
- Sprint 23 v2026.09.12-01 發布
- Sprint 24 開始規劃（TestSecurityContextHelper + Redis Cache TTL + M10 WebSocket 評估）
- **DEF 清零**: 所有活躍延後項目（DEF-007/008）全數完成，無新增 DEF

---

### Sprint 22 (2026-06-27)

**新增延後**:
- DEF-007（升級為 🔴 高優先級）: M11 物流與訂單整合 → Sprint 23 P1（AI-704 延續 AI-603）
- DEF-008: ShippingTemplate 接入結帳流程 → Sprint 23 Buffer

**移除延後（已完成）**:
- DEF-005: M10 IM SA 需求分析 ✅ Sprint 22 US-004 完成，Victoria APPROVED
- DEF-006: M11 Provider Stub 強化 ✅ Sprint 22 US-005 完成

**更新**:
- Sprint 22 完成，5/5 US 全數達成（含 Buffer-A + Buffer-B，100% Buffer 利用率）
- Sprint 22 Integration Tests: ~302 tests, 0 Failures（新增 +9）
- Sprint 22 Unit Tests: ~323（新增 +3）
- Sprint 22 v2026.08.29-01 發布
- Sprint 23 開始規劃（M10 IM 後端 REST + M11 訂單整合）

---

### Sprint 21 (2026-06-27)

**新增延後**:
- DEF-005: M10 IM SA 需求分析（Buffer-B 連續兩次延後，Sprint 22 必須執行）
- DEF-006: M11 Provider Stub 強化（Buffer-C 未啟動）
- DEF-007: M11 物流與訂單整合（Sprint 21 未規劃，Sprint 23+）
- DEF-008: ShippingTemplate 接入結帳流程（Sprint 21 未規劃，Sprint 23+）

**移除延後**:
- (無)

**更新**:
- Sprint 21 完成，6/6 US 全數達成（含 Buffer-A US-006）
- Sprint 21 Integration Tests: 293 tests, 0 Failures
- Sprint 21 v2026.08.15-01 已發布
- Sprint 22 開始規劃（M12 動態定價 + M13 商家工作台）

---

### Sprint 12 (2026-06-15)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- Sprint 12 完成，M18 知識管理 Phase 2-A + M07 Payment Mock + M09 通知模板已交付
- Sprint 12 v12.0.0 已發布 (release/v2026.06.15-01)
- Sprint 13 開始規劃 (M18 Phase 2-B + M08 評價系統)

### Sprint 11 (2026-06-01)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- Sprint 11 完成，M04 購物車 + M06 預訂完整化已交付
- Sprint 11 QA 驗證完成 (2026-05-14)
- 374 tests PASS
- CI Pipeline 因 GitHub 帳單額度問題等待 2026-06-01 恢復

### Sprint 10 (2026-05-18)

**新增延後**:
- (無)

**移除延後**:
- DEF-004: listings.tags 欄位類型修復 ✅ 已完成 (V8__Fix_Listings_Tags_Column_Type.sql)

**更新**:
- Sprint 10 完成，M16 ERP 進銷存模組已交付
- Sprint 10 進入發布評審階段 (2026-05-13)
- CI Pipeline 因 GitHub 帳單額度問題等待 2026-06-01 恢復

### Sprint 9 (2026-05-06)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- DEF-001: Sprint 6 完成，移至已完成延後項目

---

## 使用說明

### 添加新延後項目

1. 在「活躍延後項目」區塊新增列
2. 填寫所有欄位（ID、標題、原始 Sprint、延後原因、前置需求、預估 SP）
3. 在「Sprint 歷史紀錄」區塊新增 entry
4. 狀態標記為 ⚠️ 待處理

### 完成延後項目

1. 將項目從「活躍延後項目」移到「已完成延後項目」
2. 填寫完成 Sprint
3. 狀態改為 ✅ 已完成

### Sprint Planning 前檢查清單

- [ ] 閱讀本文件
- [ ] 確認所有 ⚠️ 待處理 項目是否已具備執行條件
- [ ] 將具備條件的項目納入 Sprint Plan
- [ ] 更新本文件的狀態欄位

---

**文件版本**: v2.23
**最後更新**: 2026-07-04（Sprint 53：真實金流 Phase D-1 Stripe Connect Express 帳戶 onboarding——AI-2413（後端聚焦，8 SP）。V62 migration tenants 加 4 個 Connect 欄位；StripePaymentGateway 新增 createConnectAccount/createAccountLink/getConnectAccountStatus；TenantStripeConnectService（onboarding 發起/複用/狀態查詢，toggle 保護）；SellerDashboardController onboarding/status 端點；PaymentWebhookService 擴充 account.updated dispatch（反查 tenant 回填狀態，沿用 V60 去重）。後端單元 20（TC-S007~010 + UT-CONNECT-001~006 + UT-WH-007~008）+ 真 DB 整合 4（IT-CONNECT）+ **全量回歸 536 tests 0 fail**、validate-schema V62 無漂移、catch(Exception)/@Deprecated=0。同時交付 AI-2414（`STRIPE_PRODUCTION_CHECKLIST.md`）+ AI-1903 部分（更新既有走查 checklist，真人執行續留）。誠實：只做帳戶 onboarding，代收後 transfer 分潤另立 AI-2416（Phase D-2）；不含前端；只做 Express。AI-2413/AI-2414 移入已完成；新增延後 AI-2416（P3 Phase D-2）；AI-2407 定價語意評估延後 Sprint 54 主軸（前置調查發現規模 5-8 SP）；活躍 DEF=0
**歷史版本 v2.22**: 2026-07-03（Sprint 52：真實金流 Phase C 退款真串接——AI-2412（後端聚焦）。StripePaymentGateway.processRefund 由 stub 改真 Refund.create（payment_intent 全額退款）；PaymentStateService.mockRefund 重構 refundOrderPayment toggle-aware（stripe 真退款+存 stripe_refund_id / mock 保留）；PaymentWebhookService charge.refunded 權威 REFUNDED（冪等+V60 去重）；createCheckoutSession 補 pi metadata order_id（補 Phase B best-effort）；V61 stripe_refund_id。後端單元 19（TC-S006+退款 005~007+UT-WH-006）+ 真 DB 整合 21（不退步）、validate-schema V61 無漂移、validate-e2e **54 passed/0 fail**、catch(Exception)/@Deprecated=0。**真實金流付款閉環完整**（付款+權威狀態+退款）。誠實：只做全額退款；測試不打真 Stripe。⚠️ V61 schema。AI-2412 移入已完成；新增延後 AI-2415（P4 部分退款）；活躍 DEF=0
**歷史版本 v2.21**: 2026-07-03（Sprint 51：真實金流 Phase B webhook 驅動權威狀態——AI-2411（後端聚焦）。PaymentWebhookService 解析 Stripe 事件（checkout.session.completed[paid]→SUCCESS+Order PAID 權威、payment_intent.payment_failed→FAILED）補 Phase A 未回跳缺口；StripeWebhookController 驗簽後委派一律回 2xx；抽 markStripePaymentSucceeded/Failed 共用核心（雙路徑一致）；V60 processed_stripe_events 去重 + 雙層冪等。後端單元 9（UT-WH-001~005）+ 真 DB 整合 21（不退步）、validate-schema V60 無漂移、validate-e2e **54 passed/0 fail**、catch(Exception)/@Deprecated=0。誠實：只做成功/失敗（退款留 Phase C）；失敗 best-effort；測試模式跳驗簽。⚠️ V60 schema。AI-2411 移入已完成；新增延後 AI-2414（P2 上線 checklist）；活躍 DEF=0
**歷史版本 v2.20**: 2026-07-03（Sprint 50：真實金流 Phase A 卡片付款 MVP——AI-2410（Stripe Checkout hosted，平台代收）。V59 payments 加 Stripe 欄位 + STRIPE method/PROCESSING；接回孤兒 gateway（createCheckoutSession/retrieve）；STRIPE_PAYMENT_ENABLED toggle（預設關=mock 不退步）；PaymentStateService initiate/confirmStripeCheckout（回跳 retrieve，冪等）；端點 /pay/checkout + /return；前端 orders/[id] 重導 + success/cancel + E2E-M11-013。後端單元 9（WireMock TC-S004/005）+ 真 DB 整合 25（mock 不退步）、validate-schema 無漂移、validate-e2e **54 passed/0 fail**（+1）、catch(Exception)/@Deprecated=0。⚠️ V59 打破 schema-free。誠實：Phase A 僅回跳 retrieve、webhook 權威狀態留 Phase B（AI-2411，上線前必要）；平台代收分帳留 Phase D。AI-2410 移入已完成；活躍 DEF=0
**歷史版本 v2.19**: 2026-07-03（Sprint 49：真實金流評估 spike——backlog #10。US-001+US-002 產出 PAYMENT_INTEGRATION_ASSESSMENT.md（揭穿「Stripe 已整合」假象：兩套並行付款程式碼＝上線純 Mock + 孤兒 Gateway 抽象層無人注入【S14/S21 遺留死碼】；real/stub/missing 速查表；分階段路線 Phase A 卡片 MVP→B webhook→C 退款→D 分帳；§mock↔real toggle + §Connect vs 手動 + §Stripe.js 選型 + §待 PO 決策 6 項）。**不改 code、無 schema**；既有測試沿用 S48（validate-e2e 53/0）。真實金流實作（13 SP+外部依賴）分 AI-2410~2413 另立。新增延後 AI-2410~2413（P3 金流實作）；活躍 DEF=0
**歷史版本 v2.18**: 2026-07-03（Sprint 48：PRODUCT/cart 漲價——AI-2406c M12 進階定價 PRODUCT 側收官。破兩道閘門：閘門 2【結構性】applyProductRule 由 discount-only 擴充支援漲價型（MANUAL_OVERRIDE/SEASONAL/WEEKDAY_WEEKEND，對齊 ROOM config key、保留 discountPercent 向後相容）+ 閘門 1 RedisCartService 折扣閘門 `<現價`→`≠現價`；CartItemResponse priceAdjustmentType + 有號 discountAmount；下單自動繼承；前端 cart/page 首次顯示 item 雙向定價 + E2E-M11-012。後端單元 22 + 真 DB 整合 54（含 IT-EP-004）、validate-e2e **53 passed/0 fail**（+1）、schema 無漂移、catch(Exception)/@Deprecated=0、**schema-free**（V58）。**M12 進階定價全面收官**（ROOM+PRODUCT 折扣+漲價皆顯示=收費）。SP 初估 3→探勘修正 8（兩道閘門）。新增延後 AI-2409（P4 計算器統一）；活躍 DEF=0
**歷史版本 v2.17**: 2026-07-03（Sprint 47：開放窗語意實作——AI-2202e 區分「未開放 vs 可訂」（PO 拍板選項 A + 滾動視窗 + host UI）。US-001 後端 V58 migration【rooms 加 open_until_date + booking_window_days，皆 nullable NULL=無限制、backfill 免異動】+ Room.resolveOpenUntil（取最早生效）三層一律呼叫 + 三層一致【getCalendar 補 NOT_OPEN 計算產物非持久化、availability 超窗擋、createBooking+reschedule 超窗擋訂 E-3002】；US-002 前端 MonthCalendar NOT_OPEN 灰底禁選不刪除線 + RoomForm 雙欄位 + E2E-ROOM-10/11。後端單元 9 + 真 DB 整合 38（含 API-M06-016）、validate-schema **無漂移**、validate-e2e **52 passed/0 fail**（+2）、catch(Exception)/@Deprecated=0。⚠️ **V58 結束 S42~S46 連續零-migration**。新增延後 AI-2202f（P4 清窗）/ AI-2408（P4 reason i18n）；活躍 DEF=0
**歷史版本 v2.16**: 2026-07-03（Sprint 46：定價機制真正統一——AI-2406b 漲價型規則計入 ROOM booking（PO 拍板選項 B）。US-001 後端放寬三處折扣閘門【availability/月曆/建單 totalAmount 一律含漲價乘數，保留 toggle 關短路+失敗降級】+ PricingService 抽 resolveListingForPricing 優雅降級 + DTO 中性調整語意（discountAmount 有號差額 + priceAdjustmentType）；US-002 前端漲價雙向顯示（漲價不刪除線+橙 badge）+ E2E-ROOM-08/09。後端單元 18 + 真 DB 整合 34、validate-e2e **50 passed/0 fail**（+2 漲價 E2E）、schema 對齊、**無 schema 變動**（連續 S42~S46 零 migration）、catch(Exception)/@Deprecated=0。**M12 進階定價收官**（折扣+漲價皆顯示=收費）。新增延後 AI-2406c（P3 PRODUCT 漲價）/ AI-2407（P3 定價規則語意）；活躍 DEF=0
**歷史版本 v2.15**: 2026-07-02（Sprint 45：定價區技術債收斂——US-001 AI-2406 定價機制統一（揭穿 room_calendar.price 死碼假象、移除死碼 + 三處讀取簡化【行為等價】、確立 MANUAL_OVERRIDE 唯一路徑、決策文件）+ US-002 AI-2202d 開放窗語意評估（spike 決策文件）。後端單元 6 + 真 DB 整合 57、validate-e2e **48 passed/0 fail**、schema 對齊、**無 schema 變動**（連續 S42~S45 零 migration）、@Deprecated=0。新增延後 AI-2406b（P2，PO 決策）/ AI-2202e（P3，PO 拍板 schema）；活躍 DEF=0
**歷史版本 v2.14**: 2026-07-02（Sprint 44：完成 M12 進階定價全覆蓋——US-001 AI-2403 PRODUCT/Cart 折扣（getCart 重算，OrderService 繼承）+ US-002 AI-2405b 買家日曆每日折扣（getCalendar merge）+ US-003 AI-2303 Inter 自 host 離線化。後端單元 23 + 真 DB 整合 71、validate-e2e **48 passed/0 fail**、schema 對齊、**無 schema 變動**（連續 S42~S44 零 migration）。**M12 進階定價全覆蓋達成**（ROOM+PRODUCT+買家顯示）。活躍延後 AI-2406/AI-2202d（P3）；活躍 DEF=0
**歷史版本 v2.13**: 2026-07-02（Sprint 43：M12 進階定價落地——US-001 AI-2401 早鳥/末班車語意修正（bookingDate）+ US-002 AI-2402 定價引擎接入 ROOM 計價鏈（toggle+向後相容）+ US-003 AI-2404 config 型別化編輯 UI + US-004 AI-2405 買家折扣顯示+賣家預覽+E2E-ROOM-06。後端單元 15 + 真 DB 整合 36 全過、validate-e2e **47 passed/0 fail**、schema 對齊、**無 schema 變動**。新增延後 AI-2403/2405b/2406。活躍 DEF=0
**歷史版本 v2.12**: 2026-07-02（Sprint 42：收尾技術債——US-001 AI-2302 pre-commit 提速（@Tag slow + excludedGroups，移除 DB 依賴）+ US-002 AI-2202c Part A 日曆每日價格（純前端）+ US-003 DEF-022 E2E 硬等待清除（連帶根治 auth helper/alert flaky）。validate-e2e **46 passed/0 fail**、schema 對齊、後端 quick test 455 tests 0 fail（無 DB）。**無 production/schema 變動**。**活躍 DEF 歸零**（DEF-021 決策結案、DEF-022 完成）；新增 AI-2202d/AI-2303（P3）
**歷史版本 v2.11**: Sprint 41：技術債徹底清償 + 整月日曆——US-001 AI-2301 test DB↔act port 制度化 + US-002 AI-2101b 登入 helper 完全統一 + US-003 AI-2202a 端點契約清理 + US-004 AI-2202b 整月日曆（read-only 後端）+ US-005 AI-1903 買家走查（自動證據+checklist，真人殘留）+ US-006 DEF-021 CJK 字體決策。validate-e2e 47 passed/0 fail。read-only 無 schema。活躍 DEF=1（DEF-022）
**歷史版本 v2.10**: 2026-07-02（Sprint 40：ROOM 可用性 UX 完成——US-001 availability 端點 @RequestParam（後端 read-only）+ US-002 詳情頁即時可用性 + US-003 E2E。**完整 make validate-release 通過**（後端 act 330 tests 0 fail + E2E 45 passed/0 failed）。無 DB/schema 變動。活躍 DEF=1 非安全（DEF-021 CJK 字體）
**歷史版本 v2.9**: Sprint 39：ROOM 訂房閉環補強——US-001+002 booking service 抽取 + 衝突優雅處理、US-003 ROOM 閉環 E2E（AI-2104）、US-004 E2E 共用 helper 抽取（AI-2101，收斂 4 檔 + 收 DEF-022）。全棧 44 passed/0 failed。無後端/DB 變動。誠實：availability 端點 GET+body 不可用→AI-2201。活躍 DEF=2 非安全（DEF-021 CJK 字體 / DEF-022 剩餘隨 AI-2101b）
**下次審查**: **檢查點徵詢後 push S41~S52（AI-1908，累積 12 Sprint commit，本地各層驗證通過含 validate-schema 無漂移 + validate-e2e 54/0 fail，含 V59/V60/V61 金流 schema；完整 make validate-release + 徵詢後 push，嚴禁 --no-verify）**；**M12 進階定價已收官**；**真實金流付款閉環完整**（付款 Phase A + 權威狀態 Phase B + 退款 Phase C 皆真實）。Sprint 53 建議：AI-2414 真金流上線 checklist（端到端人工驗證）+ AI-2413 Phase D 分帳（需 PO 決策 Connect vs 手動）+ AI-2407 定價語意 + AI-1903 真人 live 走查（需環境）。**極強烈建議：12 Sprint push 債（含完整真實金流 + V59/V60/V61 三支 schema）務必盡快清償——重大未落地風險。**
