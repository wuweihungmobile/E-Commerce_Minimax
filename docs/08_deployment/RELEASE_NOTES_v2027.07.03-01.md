# Release Notes - v2027.07.03-01 (Sprint 44)

**發布日期**: 2027-07-03（規劃）／實作完成 2026-07-02
**發布類型**: Minor（M12 進階定價全覆蓋：PRODUCT/Cart 折扣 + 買家日曆折扣；Inter 字體離線化；無 schema 變動）
**Sprint**: Sprint 44
**狀態**: ⏳ 待 push（本 Sprint 5 commit；push 債累積 S41+S42+S43+S44，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 44 主題：**完成 M12 進階定價全覆蓋**。延續 S43（ROOM 訂房折扣已生效），把折扣接進 **PRODUCT 購物車/訂單**（商品折扣真正反映在購物車顯示與下單金額），補上 **買家整月日曆每日折扣顯示**，並附帶把 **Inter 字體改自 host**（消除 build 期 Google Fonts 網路依賴）。後端變動限於 **DTO + 服務接線**（無 entity/migration/schema，Flyway 維持 V57；連續 S42~S44 零 migration）。

---

## 新功能 ✨

- **PRODUCT 進階定價折扣真正生效（AI-2403，US-001）**：賣家設定的商品折扣規則，現會**實際反映在購物車顯示與下單金額**。`RedisCartService.getCart` 讀取時對 PRODUCT 項以 `getEffectivePrice`（今日基準）套折扣——`DYNAMIC_PRICING_ENABLED` 開啟且折扣後單價低於現價時，unitPrice/subtotal 為折扣後 + 回 `originalUnitPrice/discountAmount/appliedRuleName`；訂單金額（createOrderFromCart 讀 cart）自動繼承，顯示與收費一致。Redis 只存 basePrice（讀取時算，避免 stale）；toggle 關/無規則時維持原價（向後相容）。
- **買家整月日曆每日折扣顯示（AI-2405b，US-002）**：ROOM 詳情整月日曆的可訂日格顯示「折扣後價 + 原價刪除線」。後端 `BookingService.getCalendar` 以 `PricingService` 逐日 breakdown 取折扣後價（只對可訂日、只套折扣型）；`CalendarResponse` 補 `originalPrice/appliedRuleName`。前端 `MonthCalendar` 顯示刪除線原價（保留 `calendar-price-{date}` testid，新增 `calendar-original-price-{date}`）。

## 改進 🚀

- **Inter 字體自 host 離線化（AI-2303，US-003）**：`layout.tsx` 由 `next/font/google` 改 `next/font/local`，指向 committed 的 Inter latin variable woff2（`src/app/fonts/Inter-latin.woff2`，48KB，OFL 授權，一次性自 Google Fonts 取得）。消除 build 期對 Google Fonts 的網路依賴（原 Turbopack build 抓 Inter，網路抖動即失敗，S42 validate-e2e 曾因此失敗數次）；CJK 系統堆疊不動（DEF-021 決策）。
- **getEffectivePrice 回傳補 appliedRuleName**（AI-2403）：供購物車/訂單折扣顯示規則名，與 ROOM 折扣顯示對齊。

## 測試 / 驗證 ✅

- **後端單元**：`RedisCartServiceDynamicPricingTest` **3 tests 0 fail**（折扣 / toggle 關 / 無折扣 三路徑）；既有 `RedisCartServiceTest` **20 tests 0 fail**（toggle 預設關 → 行為不變）。
- **後端整合（真實 DB）**：cart/order/M12 **45 tests** + getCalendar 回歸（BookingControllerE2E 18 + M12Pricing 8）**26 tests**，皆 0 fail（toggle 關不退步）。
- **前端**：`tsc` 0 error、`npm run build`（Turbopack）0 error、無 `next/font/google` 引用（離線可 build）。
- **本地 E2E 守門（`make validate-e2e`）**：**48 passed / 6 skipped / 0 failed**（含新增 E2E-ROOM-07 日曆折扣；既有全數不退步）。
- **schema 漂移**：無（ddl-auto=validate 對齊）。

## 技術決策 / 已知限制 ⚠️

- **schema-free（誠實揭露 Rule 12）**：訂單/購物車不新增「原價/折扣」持久欄位——訂單 unitPrice 直接為折扣後價（比照 S43 booking.totalAmount），cart（Redis）與 CalendarResponse 折扣資訊為 transient DTO 欄位。避免 order_items migration，維持 S42~S44 零 migration。代價：訂單不留折扣前原價的歷史紀錄（如需帳務對帳可另立含 schema 的項目）。
- **一致性界線**：cart/order 與日曆只套「折扣型」規則（漲價型 weekend/seasonal 不套，與 S43 availability 一致）；計算失敗降級為不套用（不阻斷購物車/日曆）。
- **PRODUCT 折扣基準**：以 basePrice + config.discountPercent 折扣後價，僅當低於現存單價才套（買家 favorable）；SKU priceOverride 若已較低則維持。
- **定價機制並存**：折扣生效時 room_calendar 手動日價不參與（以規則 basePrice 為基準）→ 統一評估另立 **AI-2406**。
- **後端無 schema 變動**：US-001/002 皆為 DTO + 服務接線，沿用既有 jsonb config，Flyway 維持 V57。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V57）。

## 內含 Commit（Sprint 44）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 44 Plan | 075fdb0 | 完成 M12 進階定價全覆蓋（3 US / 8 SP）|
| US-001 AI-2403 | 49cfadf | 進階定價接入 PRODUCT 購物車/訂單計價鏈（getCart 重算 + getEffectivePrice）|
| US-002 AI-2405b | d586a4c | 買家整月日曆每日折扣顯示（getCalendar merge + MonthCalendar 刪除線 + E2E-ROOM-07）|
| US-003 AI-2303 | 3df59f4 | Inter 字體自 host 離線化（next/font/local）|
| Sprint 44 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
