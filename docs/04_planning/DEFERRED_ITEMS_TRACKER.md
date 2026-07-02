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
| AI-2202d | 整月日曆「未開放 vs 可訂」語意 | Sprint 42（AI-2202c Part A 交付時界定） | S42 交付每日價格顯示（Part A，純前端）。「未開放 vs 可訂」顯式標記需**後端新語意**——現無「開放窗」概念，`room_calendar` 的 AVAILABLE row 為偶發，前端一律把「無記錄」視為可訂，無法區分「房源尚未開放該日曆」與「可訂」 | 需後端 room_calendar 開放窗語意設計 + 端點回傳擴充 | 3 | ⚠️ 待評估（P3，需後端語意，非純前端）|
| AI-2303 | Inter 字體建置期 Google Fonts 依賴 | Sprint 42（validate-e2e 暫時性 build 失敗發現） | Turbopack build 仍以 next/font/google 抓 `Inter`（拉丁），網路抖動即 build 失敗（S42 validate-e2e 前 3 次有 2 次因此失敗，非程式）。同源 DEF-015（該次移除的是死碼 Geist；Inter 仍在用）。後續評估 `next/font/local` 自 host Inter 子集徹底離線化 | 需 Inter woff2 子集 + 驗證 Turbopack 相容 | 1 | ⚠️ 待處理（P3，低急迫，暫時性失敗可重跑）|

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
| DEF-022 | E2E 硬等待（waitForTimeout 固定 sleep） | Sprint 35 | Sprint 42 | **歷時 S35→39→42**。S39（AI-2101）已收斂登入 helper 部分；S42（US-003）完成餘下清除：5 檔（at-m11-cart-checkout、at-m15-e2e、at-m17-001/002）冗餘 `waitForTimeout` 刪除、可替換者改顯式等待（`waitForURL`/`waitForResponse`/`expect().toBeVisible()`/`toHaveClass()`）並順帶補斷言（Rule 9）；**保留** at-m10-chat STOMP SUBSCRIBE settle 例外（無 client 可觀察訊號）。**連帶根治**移除 sleep 後浮現的既有 flaky：auth helper 與 S37 共用 Header「註冊」連結碰撞（`.first()` 恆選 header 連結 + re-render 不穩定 → 改 `goto('/register')`，Playwright 快照佐證）+ 原生 alert teardown（at-m15 檔案級 dialog beforeEach + at-m17-002 dialog 處理器）。validate-e2e 46 passed/0 fail |

---

## Sprint 歷史紀錄

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

**文件版本**: v2.12
**最後更新**: 2026-07-02（Sprint 42：收尾技術債——US-001 AI-2302 pre-commit 提速（@Tag slow + excludedGroups，移除 DB 依賴）+ US-002 AI-2202c Part A 日曆每日價格（純前端）+ US-003 DEF-022 E2E 硬等待清除（連帶根治 auth helper/alert flaky）。validate-e2e **46 passed/0 fail**、schema 對齊、後端 quick test 455 tests 0 fail（無 DB）。**無 production/schema 變動**。**活躍 DEF 歸零**（DEF-021 決策結案、DEF-022 完成）；新增 AI-2202d/AI-2303（P3）
**歷史版本 v2.11**: Sprint 41：技術債徹底清償 + 整月日曆——US-001 AI-2301 test DB↔act port 制度化 + US-002 AI-2101b 登入 helper 完全統一 + US-003 AI-2202a 端點契約清理 + US-004 AI-2202b 整月日曆（read-only 後端）+ US-005 AI-1903 買家走查（自動證據+checklist，真人殘留）+ US-006 DEF-021 CJK 字體決策。validate-e2e 47 passed/0 fail。read-only 無 schema。活躍 DEF=1（DEF-022）
**歷史版本 v2.10**: 2026-07-02（Sprint 40：ROOM 可用性 UX 完成——US-001 availability 端點 @RequestParam（後端 read-only）+ US-002 詳情頁即時可用性 + US-003 E2E。**完整 make validate-release 通過**（後端 act 330 tests 0 fail + E2E 45 passed/0 failed）。無 DB/schema 變動。活躍 DEF=1 非安全（DEF-021 CJK 字體）
**歷史版本 v2.9**: Sprint 39：ROOM 訂房閉環補強——US-001+002 booking service 抽取 + 衝突優雅處理、US-003 ROOM 閉環 E2E（AI-2104）、US-004 E2E 共用 helper 抽取（AI-2101，收斂 4 檔 + 收 DEF-022）。全棧 44 passed/0 failed。無後端/DB 變動。誠實：availability 端點 GET+body 不可用→AI-2201。活躍 DEF=2 非安全（DEF-021 CJK 字體 / DEF-022 剩餘隨 AI-2101b）
**下次審查**: **檢查點徵詢後 push S41+S42（AI-1908，累積 15 commit，本地各層驗證通過含 validate-e2e 46/0 fail，完整 make validate-release + 徵詢後 push，嚴禁 --no-verify）**；Sprint 43 候選：買家 live 走查（AI-1903，需環境）+ 日曆「未開放 vs 可訂」語意（AI-2202d）+ Inter 字體離線化（AI-2303），或回歸新功能開發
