# Release 流程追蹤表 / Release Tracker

> **文件類型**: 追蹤表 (Tracker)
> **版本**: v1.0
> **建立日期**: 2026-06-11
> **目的**: 追蹤每個 Sprint 的 Release 狀態，避免連續多個 Sprint 跳過 Release

---

## 📋 Release 總覽

| Sprint | Release Tag | PR 號碼 | 合併日期 | 主要功能 | 狀態 |
|--------|-------------|---------|----------|----------|------|
| Sprint 48 | v2027.08.28-01 | - | 2026-07-03 | PRODUCT/cart 漲價——M12 進階定價 PRODUCT 側收官：**後端 PRODUCT 計價支援漲價 + 閘門放寬**(AI-2406c，US-001，承 S46 界線 PRODUCT 另立；破**兩道閘門**——閘門 2【結構性】applyProductRule 由 discount-only 擴充支援漲價型 MANUAL_OVERRIDE price/SEASONAL multiplier/WEEKDAY_WEEKEND weekendMultiplier【對齊 ROOM config key，保留 discountPercent 向後相容】、閘門 1 RedisCartService 折扣閘門 `<現價`→`≠現價`；CartItemResponse 加 priceAdjustmentType + 有號 discountAmount；下單自動繼承【OrderService 未改】) + **前端購物車定價雙向顯示**(AI-2406c，US-002，cart/page 首次顯示 item 層級定價：折扣刪除線+綠標「省」/漲價不刪除線+橙標「加價」，兼補 S44 未顯示折扣；checkout 為 ROOM 訂房頁不 itemize 未改；E2E-M11-012)。⚠️ **行為變更**：toggle 開啟時 PRODUCT 漲價計入(PO 拍板)。驗證：後端單元 22 + 真 DB 整合 54（含 IT-EP-004 漲價）、validate-e2e **53 passed/0 fail**（+1）、schema 無漂移。**M12 進階定價全面收官**(ROOM+PRODUCT 折扣+漲價皆顯示=收費)。schema-free(V58)。誠實：SP 初估 3→探勘修正 8（兩道閘門）、PRODUCT/ROOM 兩套計算器對齊 key 未合併(另立 AI-2409) | ⏳ 待 push（本 Sprint 3 commit，累積 S41~S48，完整守門+徵詢後 push）|
| Sprint 47 | v2027.08.14-01 | - | 2026-07-03 | 開放窗語意實作——區分「未開放 vs 可訂」：**後端開放窗三層 + migration V58**(AI-2202e，US-001，承 S45 決策 PO 拍板選項 A + 追加滾動視窗 + host UI；rooms 加 open_until_date DATE + booking_window_days INT【皆 nullable、既有列 NULL=無限制、backfill 免異動、ADD COLUMN IF NOT EXISTS 冪等】；抽 Room.resolveOpenUntil【取最早生效 min】三層一律呼叫；getCalendar 超窗無記錄日補 NOT_OPEN【計算產物非持久化，抽 appendNotOpenDays 控 NPath】、checkAvailability 超窗 available=false+原因、createBooking+reschedule 超窗擋訂 E-3002【422】；RoomCalendarService 未改【擋在 caller 層更精準】；兩欄 NULL 維持現狀) + **前端開放窗顯示 + 賣家設定**(AI-2202e，US-002，MonthCalendar NOT_OPEN 灰底禁選不刪除線+data-not-open+圖例；ListingDetail 沿用既有不可訂路徑；booking.ts type；room.ts+RoomForm 雙欄位；E2E-ROOM-10/11)。⚠️ **V58 結束 S42~S46 連續零-migration**(PO 已知悉)。驗證：後端單元 9 + 真 DB 整合 38（含 API-M06-016 三層一致）、validate-schema **無漂移**、validate-e2e **52 passed/0 fail**（+2 NOT_OPEN E2E）。誠實：只做 ROOM、NOT_OPEN 計算非持久化、部分更新無法清窗(另立 AI-2202f)、reason 英文字串(另立 AI-2408) | ⏳ 待 push（本 Sprint 3 commit，累積 S41~S47，完整守門+徵詢後 push）|
| Sprint 46 | v2027.07.31-01 | - | 2026-07-03 | 定價機制真正統一——漲價型規則計入 ROOM booking（M12 進階定價收官）：**後端 ROOM 計價全面走 adjustedTotal 含漲價**(AI-2406b，US-001，承 S45 決策 PO 拍板選項 B；BookingService 放寬三處折扣閘門【tryDynamicPricing `<baseTotal`→`≠0`、getCalendar 逐日 `<0`→`≠0`、calculateTotalAmount toggle 開即採 adjustedTotal】使 availability/月曆/建單 totalAmount 三者一律含漲價乘數；保留 toggle 關短路+失敗降級【向後相容】；計算核心不動；PricingService 抽 resolveListingForPricing 優雅降級【無 Room fallback basePrice、null 回 4xx 非 NPE→500】；DTO 中性調整語意【discountAmount 改有號差額 正=折扣/負=加價，新增 priceAdjustmentType DISCOUNT/MARKUP/NONE】) + **前端漲價雙向顯示**(AI-2406b 前端，US-002，ListingDetail/MonthCalendar 折扣維持刪除線+綠 badge「省 X」、漲價改不刪除線+橙 badge「加價 X」；booking.ts 加 priceAdjustmentType；E2E-ROOM-08/09 漲價變體)。⚠️ **行為變更**：toggle 開啟時漲價規則開始計入訂房金額（PO 拍板）。驗證：後端單元 18 + 真 DB 整合 34 全過、validate-e2e **50 passed/0 fail**（+2 漲價 E2E）、schema 對齊。**無 schema 變動**(連續 S42~S46 零 migration)。誠實：只做 ROOM(PRODUCT 另立 AI-2406c)、bestRule priority/range 查詢落差記錄不修(另立 AI-2407)、E2E 編號順延 06/07→08/09 | ⏳ 待 push（本 Sprint 3 commit，累積 S41~S46，完整守門+徵詢後 push）|
| Sprint 45 | v2027.07.17-01 | - | 2026-07-02 | 定價區技術債收斂（清死碼 + 語意決策）：**定價機制統一**(AI-2406，US-001，揭穿「雙定價機制」實為死碼假象——`room_calendar.price` 寫入路徑 setDatePrice/setDatePriceBulk 零呼叫者、欄位恆 NULL；移除死碼 + BookingService 三處讀取移除死欄位 fallback 改直取 basePrice【行為等價，順帶修正 calendarBaseTotal NULL→ZERO 潛在低估】；RoomCalendar.price 註解標記停用；確立 MANUAL_OVERRIDE 為唯一手動日價路徑；決策文件 PRICING_MECHANISM_UNIFICATION.md 就漲價計入 booking 提選項→PO 裁決另立 AI-2406b) + **開放窗語意評估**(AI-2202d，US-002，spike，CALENDAR_OPEN_WINDOW_ASSESSMENT.md 記錄三層硬語意 + 三選項比較【推薦 A open_until_date，需 migration】+ NULL 安全過渡→PO 拍板另立 AI-2202e)。驗證：後端單元 6 + 真 DB 整合 57 全過、validate-e2e **48 passed/0 fail**、schema 對齊。**無 schema 變動**(連續 S42~S45 零 migration)。誠實：決策密集項另立 AI-2406b/AI-2202e；@Deprecated=0 慣例（用註解非 annotation）| ⏳ 待 push（本 Sprint 3 commit，累積 S41~S45，完整守門+徵詢後 push）|
| Sprint 44 | v2027.07.03-01 | - | 2026-07-02 | 完成 M12 進階定價全覆蓋：**PRODUCT 購物車/訂單折扣**(AI-2403，getCart 讀取重算 getEffectivePrice、訂單繼承、toggle+向後相容、CartItemResponse transient 折扣欄位) + **買家整月日曆每日折扣**(AI-2405b，getCalendar merge calculatePrice breakdown、MonthCalendar 原價刪除線、E2E-ROOM-07) + **Inter 字體自 host 離線化**(AI-2303，next/font/local + committed woff2，消 build 期 Google Fonts 依賴)。驗證：後端單元 23 + 真 DB 整合 71、validate-e2e **48 passed/0 fail**、schema 對齊。**無 schema 變動**(連續 S42~S44 零 migration)。M12 進階定價自此 ROOM+PRODUCT+買家顯示全覆蓋。誠實：schema-free(訂單不留原價欄位)、定價機制統一另立 AI-2406 | ⏳ 待 push（本 Sprint 5 commit，累積 S41~S44，完整守門+徵詢後 push）|
| Sprint 43 | v2027.06.19-01 | - | 2026-07-02 | M12 進階定價落地（早鳥/長住/末班車折扣真正生效於 ROOM）：**語意修正**(AI-2401，早鳥/末班車改「下單日 vs 入住日」+ config Number 防護) + **定價引擎接入 ROOM 計價鏈**(AI-2402，BookingService 注入 PricingService，toggle+向後相容+availability 回折扣明細，訂房金額與顯示一致) + **config 型別化編輯 UI**(AI-2404，動態子表單取代黑箱 {} + dashboard 入口) + **買家折扣顯示 + 賣家預覽**(AI-2405，折扣後價+原價刪除線+標籤 + 沿用 PricingCalendarPreview + E2E-ROOM-06)。驗證：後端單元 15 + 真 DB 整合 36 全過、validate-e2e **47 passed/0 fail**、schema 對齊。**無 schema 變動**(沿用 jsonb config)。誠實：只接 ROOM(PRODUCT/Cart 另立 AI-2403)、買家日曆每日折扣另立 AI-2405b | ⏳ 待 push（本 Sprint 5 commit，累積 S41+S42+S43，完整守門+徵詢後 push）|
| Sprint 42 | v2027.06.05-01 | - | 2026-07-02 | 收尾技術債：**backend pre-commit 提速**(AI-2302，2 慢測 @Tag(slow) + `-DexcludedGroups=slow`，pre-push act 仍完整跑=零覆蓋損失，順帶移除 pre-commit 對 test DB 的依賴) + **整月日曆每日價格顯示**(AI-2202c Part A，純前端，basePrice fallback) + **E2E 硬等待清除**(DEF-022，5 檔 waitForTimeout→顯式等待+補斷言，保留 STOMP 例外)；連帶根治既有 flaky（auth helper 與 S37 共用 Header「註冊」連結碰撞→改 goto；原生 alert teardown→dialog 處理器）。本地驗證：validate-e2e **46 passed/0 fail**、schema 對齊、後端 quick test 455 tests 0 fail（無 DB）。**無 production code/schema 變動**（後端僅測試 @Tag）| ⏳ 待 push（本 Sprint 5 commit，承 S41 債累積 S41+S42，完整守門+徵詢後 push）|
| Sprint 41 | v2027.05.22-01 | - | 2026-07-02 | S41 技術債徹底清償 + 整月日曆：**test DB↔act port 制度化**(AI-2301，validate-release 自動 test-db-down) + **E2E 登入 helper 完全統一**(AI-2101b，auth.ts + 重構 m10/m17-001~004) + **api.ts 端點契約清理**(AI-2202a，pricing base path + 移除死碼) + **整月日曆**(AI-2202b，read-only 後端 GET /v2/bookings/calendar + MonthCalendar 前端 + E2E-ROOM-05) + **買家閉環走查**(AI-1903，自動 validate-e2e 證據 + 手動 checklist，真人 live 走查殘留) + **CJK 字體評估**(DEF-021，決策維持系統堆疊)。本地各層驗證通過：validate-e2e **47 passed/0 fail**、schema 對齊、後端 Booking 18 tests 0 fail。read-only 無 schema 變動 | ⏳ 待 push（本 Sprint 8 commit，完整守門+徵詢後 push）|
| Sprint 40 | v2027.05.08-01 | - | 2026-07-02 | ROOM 可用性 UX 完成（含小幅後端）：**availability 端點修復**(AI-2201，@RequestBody→@RequestParam，read-only 無 DB；原 GET+body 瀏覽器不可呼叫) + **詳情頁 ROOM 即時可用性檢查**(選日期→可訂+總價 / 不可訂+原因，不可訂禁用加購) + availability E2E。**完整 make validate-release 通過**（後端 act 330 tests 0 fail + E2E 45 passed/0 failed）。無 DB/schema 變動 | ✅ 已 push（累積 S32~S40，已過完整守門；檢查點徵詢後 push）|
| Sprint 39 | v2027.04.24-01 | - | 2026-07-02 | ROOM 訂房閉環補完（補強既有閉環，非從零）：**booking service 抽取 + 訂房衝突優雅處理**(US-001+002，createBooking 抽取、日期衝突 409/E-4001 等給可讀提示、詳情頁 ROOM 日期驗證) + **ROOM 訂房閉環 E2E**(US-003 AI-2104，mock：詳情計價加購→checkout 建 booking→409 衝突) + **E2E 共用登入 helper 抽取**(US-004 AI-2101，waitForURL 收 DEF-022，收斂 4 檔 + 通知 flaky 修)。全棧 44 passed/0 failed。誠實：availability 端點 GET+body 不可用→免後端 reframe；無後端/DB 變動 | ✅ 已 push（累積 S32~S39，完整守門+徵詢後 push）|
| Sprint 38 | v2027.04.10-01 | - | 2026-07-02 | 買家體驗補完——商品詳情頁：**買家商品詳情頁**(AI-2103，/listings/[id]，(storefront) 公開 + 401 引導；PRODUCT 數量加購 + ROOM 日期計價加購 + 三態；listing service 補 getListingById/getListingPrice；cartEvents 使 Header 購物車數即時更新；首頁連結由評價頁改導向詳情頁) + **有資料 E2E**(AI-1905，page.route mock 免 seed：首頁網格+分頁+詳情導覽+加購+401/404)。全棧 41 passed/0 failed。無後端/DB 變動 | ✅ 已 push（累積 S32~S38，完整守門+徵詢後 push） |
| Sprint 37 | v2027.03.27-01 | - | 2026-07-02 | 買家頁全頁套版 + 清償 push 債：**買家頁全頁套用共用賣場版型**(AI-1901，新增 (auth)/layout.tsx 承載共用 Header/Footer、10 頁移除自包 nav 改用 StorefrontShell、Header 加 auth-aware 帳號選單 useSyncExternalStore) + **m15 flaky 修復**(AI-2001，dialog 處理器+明確等待，解鎖 release 守門) + 買家頁版型一致性 E2E(BUYER-04/05)；順帶修 E2E 登入 helper SearchBar submit 碰撞 + secret 掃描器誤報收緊。全棧 37 passed/0 failed。無後端/DB 變動 | ✅ 已 push（累積 S32~S37，完整守門+徵詢後 push；m15 阻礙已清） |
| Sprint 36 | v2027.03.13-01 | - | 2026-07-02 | 安全收尾 + 版型架構債償還：**DEF-019 物流/賣家側 IDOR 收尾**(createLogistics tenant-based + processOrderPayment user-based，活躍安全 DEF 歸零；順帶修好 S33 遺留 M07 5 失敗) + **DEF-020 版型 Shell route-group 架構重構**(layout + client 邊界下推 + URL 搜尋，at-homepage E2E 全綠) + AI-1907 home-error/重試 E2E(at-homepage 6 tests)；買家頁套版(AI-1901)/live 走查延 S37 | ✅ 已 push（累積 S32~S36，完整守門+徵詢後 push；含 m15 flaky 前置 AI-2001） |
| Sprint 35 | v2027.02.27-01 | - | 2026-07-01 | 前端賣場版型 + 首頁改版：**意象若水 RUOSHUI 設計稿套為全站共用版型**(TOP/Tools/Bottom 共用 + Content 分頁)、5 套色票主題、6 個 DS 元件(shadcn 重建)、共用 StorefrontShell、首頁接真實 /v2/listings + at-homepage E2E(4 tests)；DEF-019 物流賣家側 + 買家 live 走查延 S36 | ✅ 已 push（累積 S32~S35，完整守門+徵詢後 push） |
| Sprint 34 | v2027.02.13-01 | - | 2026-07-01 | 安全修復落地：**DEF-017 ERP 手動庫存租戶隔離清償**(AI-1701，歷時 S28→34 三度回退後落地：raw SQL 種 FIXED_TENANT_ID 租戶+null 安全檢查+IT-M16-307，乾淨 DB 43 tests 0 fail)；DEF-019 物流/賣家側 + 買家 live 走查延 S35 | ✅ 已 push（累積 S32~S34 共 8 commit，完整守門+徵詢後 push） |
| Sprint 33 | v2027.01.30-01 | - | 2026-07-01 | 安全修復收尾：DEF-019 訂單付款 IDOR 修復(AI-1702，getOrderPaymentState+pay/fail/refund，403)；DEF-017 三層根因完整診斷(NPE→403→FK，@GeneratedValue+FK)延 S34；DEF-019 物流/賣家側續 S34 | ✅ 已 push（累積 S32~S34，完整守門+徵詢後 push） |
| Sprint 32 | v2027.01.16-01 | - | 2026-07-01 | 安全修復 DEF-018 getOrder IDOR(AI-1601，403/E_1007，最小爆炸半徑) + 買家頁面 E2E 驗證(AI-1602，at-buyer-pages 30 passed)；揪出 DEF-019 付款物流 IDOR；DEF-017 二度驗證(修法正確缺 seeding)延 S33 | ✅ 已 push（累積 S32+S33，完整守門，徵詢後 push） |
| Sprint 31 | v2027.01.02-01 | - | 2026-07-01 | 買家閉環後端驗證(BuyerJourney E2E) + roomTitle 填充(AI-1502) + DEF-016 audit 持久化(V57)；揪出 getOrder IDOR(DEF-018) | ✅ 已 push（S29+30+31 累積批次 fb221f3） |
| Sprint 30 | v2026.12.19-01 | - | 2026-07-01 | EPIC-BUYER 買家端閉環完成(純前端)：M06 預訂管理 + M08 評價(提交/列表) + M11 物流追蹤(訂單詳情) | ✅ 已 push（S29+30+31 累積批次） |
| Sprint 29 | v2026.12.05-01 | - | 2026-07-01 | EPIC-BUYER 買家端閉環起手(純前端)：M05 訂單前端(列表/詳情/取消/狀態日誌) + M09 通知收件匣 + M07 Mock 付款(訂單詳情整合) | ✅ 已 push（S29+30+31 累積批次） |
| Sprint 28 | v2026.11.21-01 | - | 2026-07-01 | 品質硬化(M14/M18 測試 0→13) + 商家營運總覽儀表板(營收/訂單/趨勢) + US-004 調查(ERP 租戶隔離 no-op→DEF-017、audit→DEF-016) | ✅ 已 push（本地優先驗證） |
| Sprint 27 | v2026.11.07-01 | - | 2026-07-01 | DEF-013 通知端到端斷鏈修復 + DEF-015 前端離線 build + pre-push v5 完整守門實證 + 產品方向決策(PRODUCT_BACKLOG) | ✅ 已 push（本地優先驗證；活躍 DEF 歸零） |
| Sprint 26 | v2026.10.24-01 | - | 2026-07-01 | 本地優先 CI（停用雲端自動 CI）+ WS/即時 DoD 制度化 + M11 取消技術債清償(DEF-010/011) + 廣播 conversationId(DEF-012) + Logistics jsonb 統一(DEF-009) + e2e strict 守門 | ✅ 已 push（本地優先驗證；雲端改手動觸發） |
| Sprint 25 | v2026.10.10-01 | - | 2026-06-30 | schema 漂移守門關卡 + Conversation tenant_id(V56) + M10 WebSocket 前端整合(live E2E) + SSH keepalive + M11 取消規則確認 | ✅ 已 push（本地優先驗證；雲端改手動觸發） |
| Sprint 24 | v2026.09.26-01 | - | 2026-06-29 | M10 WebSocket STOMP 即時訊息 + M13 Redis TTL + 整合測試標準化 + E2E schema 修復(V48~V55) | ✅ |
| Sprint 23 | v2026.09.12-01 | - | 2026-06-27 | M10 IM Migration(V45/V46) + M11 物流履約整合 + 運費接入 + M13 @Cacheable | ✅ |
| Sprint 22 | v2026.08.29-01 | - | 2026-06-27 | 詳見 RELEASE_NOTES_v2026.08.29-01.md | ✅ |
| Sprint 21 | v2026.08.15-01 | - | 2026-06-27 | MQ 一致性 + Stripe Phase 3 + M11 Provider + M14 統計 + 運費模板 | ✅ |
| Sprint 20 | v2026.08.01-01 | - | 2026-06-26 | MQ 技術債清零 + @Deprecated 清零 + M09 通知歷史 + M08 評分統計 | ✅ |
| Sprint 19 | v2026.07.18-01 | - | 2026-06-25 | 詳見 RELEASE_NOTES_v2026.07.18-01.md | ✅ |
| Sprint 18 | v2026.07.03-01 | - | 2026-06-24 | 詳見 RELEASE_NOTES_v2026.07.03-01.md | ✅ |
| Sprint 17 | v2026.06.19-01 | #17 | 2026-06-10 | US-004/005 完成 - Flyway 啟用 + sellerReply 清理 | ✅ |
| Sprint 16 | v2026.06.06-01 | #15 | 2026-06-06 | M08 評價多圖 + M07 結算強化 + Pre-commit | ✅ |
| Sprint 15 | v2026.06.04-01 | #13, #14 | 2026-06-04 | M08 商家回覆 + 評價標記 | ✅ |
| Sprint 14 | v2026.05.16-02 | #12 | 2026-05-16 | M09 MQ 通知 + M07 Stripe 整合 | ✅ |
| Sprint 13 | v2026.05.16-01 | #11 | 2026-05-16 | M18 知識庫版本控制 + 排程發布 | ✅ |
| Sprint 12 | v2026.05.15-01 | #10 | 2026-05-15 | M18 知識管理 + M07 金流準備 + M09 通知模板 | ✅ |
| Sprint 11 | v2026.05.12-01 | #7 | 2026-05-12 | CI/CD Pipeline 修復 | ✅ |
| Sprint 10 | v2026.05.09-01 | #5 | 2026-05-09 | M16 ERP Backend | ✅ |
| Sprint 9 | - | - | - | M15 CMS Backend | ⚠️ 未正式 Release |
| Sprint 8 | - | - | - | M15 CMS Backend | ⚠️ 未正式 Release |
| Sprint 1-7 | - | - | - | 初期開發階段 | ⚠️ 無記錄 |

---

## 📊 Release 統計

| 項目 | 數值 |
|------|------|
| 建立 Release Tag 次數 | 39 (Sprint 10-48，連續) |
| 已 push（已 Release） | 31 (Sprint 10-40) |
| 待 push（Tag 已建、尚未 push） | 8 (Sprint 41~48，本地各層驗證通過含 validate-schema/validate-e2e；累積後檢查點徵詢+完整 validate-release 後 push) |
| 跳過 Release 次數 | 2 (Sprint 8-9) |
| 最近一次 Release Tag | v2027.08.28-01 (Sprint 48，⏳ 待 push) |
| 最近一次已 push Release | v2027.05.08-01 (Sprint 40，隨 S32~S40 累積批次 2e33c6d) |
| 最近一次跳過 | Sprint 8-9 |
| 連續 Release Tag 開始 | Sprint 10 |

---

## ⏳ Sprint 35 Release（最新）

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.02.27-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 前端賣場版型 + 首頁改版：意象若水 RUOSHUI 設計稿套為全站共用版型（TOP/Tools/Bottom 共用 + Content 分頁）、5 套色票主題、6 個 DS 元件、共用 StorefrontShell、首頁接真實 `/v2/listings` + at-homepage E2E（4 tests） |
| **測試狀態** | 前端 build/type-check/lint 0 error；at-homepage E2E 4 tests 全綠；全棧 33 passed（唯一失敗為既有 flaky m15，非本 Sprint）；活躍 DEF=1（DEF-019 物流賣家側） |
| **Flyway** | V57（無新 migration；純前端變更） |
| **Release Notes** | [RELEASE_NOTES_v2027.02.27-01.md](../08_deployment/RELEASE_NOTES_v2027.02.27-01.md) |
| **狀態** | ✅ 已 push（累積 S32+S33+S34+S35，完整守門 + 檢查點徵詢後 push） |

---

## ⏳ Sprint 34 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.02.13-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 安全修復落地：DEF-017 ERP 手動庫存租戶隔離清償（AI-1701，歷時 S28→34 三度回退後落地：raw SQL 種 FIXED_TENANT_ID 租戶 + null 安全檢查 + IT-M16-307，乾淨 DB 43 tests 0 fail）；DEF-019 物流/賣家側 + 買家 live 走查延 S35 |
| **測試狀態** | 乾淨 DB M16 43 tests 0 fail；catch(Exception)=0、@Deprecated=0；活躍 DEF=1（DEF-019 物流賣家側）|
| **Flyway** | V57（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2027.02.13-01.md](../08_deployment/RELEASE_NOTES_v2027.02.13-01.md) |
| **狀態** | ✅ 已 push（累積 S32~S35 共同批次） |

---

## ⏳ Sprint 33 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.01.30-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 安全修復收尾：DEF-019 訂單付款 IDOR 修復（AI-1702，getOrderPaymentState + pay/fail/refund，403）；DEF-017 三層根因完整診斷（NPE→403→FK）延 S34 |
| **測試狀態** | `@Test` 690→691（付款越權 E2E）；catch(Exception)=0、@Deprecated=0；活躍 DEF=2（DEF-017/019 物流賣家側）|
| **Flyway** | V57（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2027.01.30-01.md](../08_deployment/RELEASE_NOTES_v2027.01.30-01.md) |
| **狀態** | ✅ 已 push（累積 S32~S35 共同批次） |

---

## ⏳ Sprint 32 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.01.16-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 安全修復 DEF-018 getOrder IDOR（AI-1601，403/E_1007，最小爆炸半徑）+ 買家頁面 E2E 驗證（AI-1602，at-buyer-pages 30 passed）；揪出 DEF-019 付款物流 IDOR |
| **測試狀態** | US-001 +1（otherBuyerCannotGetOrder）、前端 e2e +3（buyer pages）；活躍 DEF=2（DEF-017/019）|
| **Flyway** | V57（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2027.01.16-01.md](../08_deployment/RELEASE_NOTES_v2027.01.16-01.md) |
| **狀態** | ✅ 已 push（累積 S32~S35 共同批次） |

---

## ✅ Sprint 31 Release（最近一次已 push）

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.01.02-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 買家閉環後端驗證（BuyerOrderJourneyE2ETest）+ BookingListResponse roomTitle 填充（AI-1502）+ DEF-016 Admin audit log 持久化（AuditLog + V57）；US-002 揪出 getOrder IDOR（DEF-018） |
| **測試狀態** | 後端 `@Test` 689（+6）, catch(Exception)=0, @Deprecated=0, make validate-schema 無漂移, 活躍 DEF=2（DEF-017/018） |
| **Flyway** | **V57**（audit_log） |
| **Release Notes** | [RELEASE_NOTES_v2027.01.02-01.md](../08_deployment/RELEASE_NOTES_v2027.01.02-01.md) |
| **狀態** | ✅ 已 push（S29+30+31 累積批次 fb221f3） |

---

## ⏳ Sprint 30 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.12.19-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | EPIC-BUYER 買家端閉環**完成**（純前端）：M06 預訂管理（我的預訂/詳情/取消）+ M08 評價（提交/列表/評分統計）+ M11 物流追蹤（訂單詳情軌跡時間軸） |
| **測試狀態** | 後端 `@Test` 683（純前端無變化）, 前端 lint 0 errors/type-check/build 通過, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration；後端零變更） |
| **Release Notes** | [RELEASE_NOTES_v2026.12.19-01.md](../08_deployment/RELEASE_NOTES_v2026.12.19-01.md) |
| **狀態** | ✅ 已 push（S29+30+31 累積批次 fb221f3） |

---

## ⏳ Sprint 29 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.12.05-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | EPIC-BUYER 買家端閉環起手（純前端）：M05 訂單前端（列表/詳情/取消/狀態日誌）+ M09 通知收件匣（未讀/篩選/已讀/刪除）+ M07 Mock 付款（訂單詳情整合，CREATED→PAID） |
| **測試狀態** | 後端 `@Test` 683（純前端無變化）, 前端 lint 0 errors/type-check/build 通過, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration；後端零變更） |
| **Release Notes** | [RELEASE_NOTES_v2026.12.05-01.md](../08_deployment/RELEASE_NOTES_v2026.12.05-01.md) |
| **狀態** | ✅ 已 push（S29+30+31 累積批次 fb221f3） |

---

## ✅ Sprint 28 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.11.21-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 品質硬化（M14 Analytics/M18 FAQ 測試 0→13）+ 商家營運總覽儀表板（營收/訂單/趨勢）+ US-004 調查（ERP 租戶隔離 no-op→DEF-017、audit→DEF-016） |
| **測試狀態** | `@Test` 靜態計數 683（+13）, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2026.11.21-01.md](../08_deployment/RELEASE_NOTES_v2026.11.21-01.md) |
| **狀態** | ✅ 完成 |

---

## 🔴 Sprint 22 Release 規劃

**目標**: Sprint 22 結束（2026-08-29）執行 Release，Tag = `v2026.08.29-01`

### Release 前檢查清單

| 檢查項目 | 標準 | 狀態 |
|---------|------|------|
| 所有 US 完成 | AC 100% 達成 | ⏳ |
| mvn verify 100% 通過 | 0 Failures, 0 Errors | ⏳ |
| Checkstyle | 0 violations | ⏳ |
| Sprint 22 Review 文件 | SPRINT_22_REVIEW.md 建立 | ⏳ |
| Sprint 22 Retro 文件 | SPRINT_22_RETRO.md 建立 | ⏳ |
| RELEASE_TRACKER.md 更新 | 新增 Sprint 22 記錄 | ⏳ |

---

## 📝 Release 歷史詳細資料

### Sprint 16 (v2026.06.06-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #15 |
| **合併日期** | 2026-06-06 |
| **主要功能** | M08 評價多圖 (9張上限) + M07 結算強化 + Pre-commit Hook |
| **架構異動** | JPA 衝突修復 (media.MediaAsset vs cms.MediaAsset) |
| **技術債** | 83 個既有測試 bug (需 Sprint 17 修復) |
| **測試覆蓋** | 新增 34 個測試，100% 通過 |

### Sprint 17 (v2026.06.19-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #17 |
| **合併日期** | 2026-06-10 |
| **主要功能** | US-004/005 完成 - Flyway 啟用 + sellerReply 清理 + 83個測試 bug 修復 |
| **架構異動** | V38/V39 Migration 建立、sellerReply 欄位移除 |
| **技術債清理** | 83個測試 bug 歸零、已棄用方法移除 |
| **流程改進** | CI/CD Pipeline 優化、Artifact 配額管理改善 |
| **測試覆蓋** | 532 tests, 0 Failures, 0 Errors (100%) |
| **Release** | ✅ 已建立 (v2026.06.19-01) |

### Sprint 15 (v2026.06.04-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #13, #14 (CI hotfix) |
| **合併日期** | 2026-06-04 |
| **主要功能** | M08 商家回覆評價 + 評價標記功能 |
| **重要變更** | ReviewReply Entity 建立 (1:1 with Review) |
| **取消功能** | sellerReply 欄位廢除 (改用 ReviewReply) |

### Sprint 14 (v2026.05.16-02)

| 欄位 | 內容 |
|------|------|
| **PR** | #12 |
| **合併日期** | 2026-05-16 |
| **主要功能** | M09 MQ 通知 + M07 Stripe 整合 + M18 FAQ 進階 |

### Sprint 13 (v2026.05.16-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #11 |
| **合併日期** | 2026-05-16 |
| **主要功能** | M18 知識庫版本控制 + 排程發布 + M08 預訂評價系統 |

### Sprint 12 (v2026.05.15-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #10 |
| **合併日期** | 2026-05-15 |
| **主要功能** | M18 知識管理 + M07 金流準備 + M09 通知模板 |

### Sprint 11 (v2026.05.12-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #7 |
| **合併日期** | 2026-05-12 |
| **主要功能** | CI/CD Pipeline 修復 |

### Sprint 10 (v2026.05.09-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #5 |
| **合併日期** | 2026-05-09 |
| **主要功能** | M16 ERP Backend 完成 |

---

## ⚠️ 未 Release 的 Sprint

### Sprint 8-9 問題說明

> **歷史問題**: Sprint 8 和 Sprint 9 沒有執行正式的 Release 流程，導致：
> - M15 CMS Backend 功能未能及時進入 Production
> - 程式碼累積在 develop 分支
> - 技術債逐漸累積

### 補救措施

1. ✅ Sprint 10 時已將 M15 CMS 程式碼带入 main
2. ✅ 後續 Sprint 都有執行 Release 流程
3. ⚠️ 建議建立文件記錄 Sprint 8-9 的功能事實上已進入 Production

---

## 📈 Release 頻率趨勢

```
Sprint 10  → ✅ Release (v2026.05.09-01)
Sprint 11  → ✅ Release (v2026.05.12-01)
Sprint 12  → ✅ Release (v2026.05.15-01)
Sprint 13  → ✅ Release (v2026.05.16-01)
Sprint 14  → ✅ Release (v2026.05.16-02)
Sprint 15  → ✅ Release (v2026.06.04-01)
Sprint 16  → ✅ Release (v2026.06.06-01)
Sprint 17  → ✅ Release (v2026.06.19-01)
Sprint 18  → ✅ Release (v2026.07.03-01)
Sprint 19  → ✅ Release (v2026.07.18-01)
Sprint 20  → ✅ Release (v2026.08.01-01)
Sprint 21  → ✅ Release (v2026.08.15-01)
Sprint 22  → ✅ Release (v2026.08.29-01)
Sprint 23  → ✅ Release (v2026.09.12-01)
Sprint 24  → ✅ Release (v2026.09.26-01)
Sprint 25  → ✅ Release (v2026.10.10-01)
Sprint 26  → ✅ Release (v2026.10.24-01)
Sprint 27  → ✅ Release (v2026.11.07-01)
Sprint 28  → ✅ Release (v2026.11.21-01)
Sprint 29  → ✅ Release (v2026.12.05-01)  [已 push，S29+30+31 累積批次]
Sprint 30  → ✅ Release (v2026.12.19-01)  [已 push，S29+30+31 累積批次]
Sprint 31  → ✅ Release (v2027.01.02-01)  [已 push，S29+30+31 累積批次]
Sprint 32  → ⏳ Tag 已建 (v2027.01.16-01)  [待 push，S32~S35 累積批次]
Sprint 33  → ⏳ Tag 已建 (v2027.01.30-01)  [待 push，S32~S35 累積批次]
Sprint 34  → ⏳ Tag 已建 (v2027.02.13-01)  [待 push，S32~S35 累積批次]
Sprint 35  → ⏳ Tag 已建 (v2027.02.27-01)  [待 push，S32~S35 累積批次]
```

**連續建立 Release Tag**: 26 次 (Sprint 10-35，未中斷)
**已 push（已 Release）**: Sprint 10-31（22 次）
**待 push（Tag 已建、尚未 push）**: Sprint 32-35（4 次，累積批次待完整守門 + 檢查點徵詢後 push）

---

## 🔧 使用方式

### 在 Sprint Planning 時

1. 開啟此文件
2. 確認上一個 Sprint 的 Release 狀態
3. 將 Release 追蹤加入 Sprint Planning Template 檢查清單

### 在 Final Approval 時

1. 確認 Release Tag 已建立
2. 確認 PR 已合併至 main
3. 更新此文件的 Release 狀態
4. 建立 GitHub Release (如尚未建立)

### 在 Sprint Retrospective 時

1. 檢視 Release 頻率
2. 識別任何跳過的 Release
3. 討論改善措施

---

## 📚 相關文件

| 文件 | 路徑 |
|------|------|
| Sprint 17 Plan | `docs/04_planning/SPRINT_17_PLAN.md` |
| Sprint 17 Tasks | `docs/05_development/SPRINT_17_TASKS.md` |
| Final Approval Process | `docs/04_planning/SPRINT_FINAL_APPROVAL_PROCESS.md` |
| Execution Checklist | `docs/04_planning/EXECUTION_CHECKLIST.md` |

---

## 📝 歷史版本

| 版本 | 日期 | 修改內容 |
|------|------|----------|
| v1.0 | 2026-06-11 | 初始建立，包含 Sprint 10-16 Release 歷史資料 |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-11
**作者**: Claude Code (AI Assistant)
**維護責任**: PM/PO (每個 Sprint 結束後更新)