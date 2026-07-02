# Sprint 39 計劃 / Sprint 39 Plan

> **Sprint 編號**: Sprint 39
> **期間**: 2027-04-11 ~ 2027-04-24 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: [SPRINT_38_RETRO.md](../05_development/SPRINT_38_RETRO.md)（AI-2103b / AI-2104 / AI-2101 / AI-1903 / AI-1906）、S39 探勘報告（BookingController / availability / checkout 現況）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**ROOM 訂房閉環**」

---

## 🔴 前置條件確認（含重要誠實揭露）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| **ROOM 訂房閉環已大致存在（誠實揭露）** | ⚠️ 後端 BookingController 完整;**checkout 頁已內聯建立 booking**（cart 過濾 ROOM → `POST /v2/bookings`）;S38 詳情頁 ROOM 已能帶日期加購 | 本 Sprint 為**補完/強化**既有閉環,非從零建 |
| 真正缺口 | ✅ 詳情頁**無可用性檢查**（選已訂走日期也能加購→booking 建立才 409）;BookingService **無 createBooking**（checkout 內聯）;ROOM 訂房**無 E2E** | 這些是 S39 聚焦點 |
| 後端 booking API | ✅ POST /v2/bookings（booking:create）、GET /v2/bookings/availability（roomListingId/checkIn/checkOut → available/nightsCount/totalPrice/unavailableReason）| CreateRequest 必填:roomListingId/checkInDate/checkOutDate/guestCount/guestName |
| 日曆端點 | ❌ `/v2/listings/{id}/calendar` 後端未實作（前端 api.ts 有路徑）| S39 用 `/v2/bookings/availability`（免後端變動）;完整日曆 UI 另立項 |
| E2E seed | ✅ 沿用 page.route mock（免後端 seed，比照 S38）| ROOM 閉環 E2E 以 mock 覆蓋 |
| 累積批次 | ⚠️ main 領先 origin/main 32（S32~S38），未 push | push 為檢查點（AI-1906）|

---

## 1. Sprint 39 目標

> **主題**: ROOM 訂房閉環補完 —— 衝突優雅處理 + 服務抽取 + E2E + 測試基建

> **🔴 範圍調整（2026-07-02，實作前發現）**：`GET /v2/bookings/availability` 為 GET+@RequestBody（瀏覽器 GET 無法送 body、前端無法呼叫）。使用者選定**純前端 /price + 409 優雅處理**（免後端）。故原「詳情頁 availability 檢查」US-001 改為「checkout 建立 booking 時優雅處理日期衝突 409/E_4001」;日期可用性回饋落在 checkout（非詳情頁）。US-004（E2E helper 抽取）提為承諾以補足容量。

ROOM 訂房閉環的骨幹（詳情頁帶日期加購 → checkout 建立 booking → bookings 列表）**已存在**;本 Sprint 補上關鍵缺口:(1) checkout 建立 booking **優雅處理日期衝突**（409/E_4001 → 「日期已被預訂,請改期」,取代通用錯誤）+ 詳情頁 ROOM 加購前日期驗證微調;(2) 把 checkout 內聯的 booking 建立**抽取為 `BookingService.createBooking`**（清理、可測）;(3) 補 **ROOM 訂房閉環 E2E**（mock-based，含衝突路徑）;(4) 抽取 **E2E 共用登入 helper**（清 DEF-022）。**無後端/DB 變動**。

---

## 2. User Stories

### US-001：checkout ROOM 訂房衝突優雅處理 + 詳情頁 ROOM 微調（P1 旗艦）（前端）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready
> **承自**: AI-2103b（ROOM 訂房補完，免後端路徑）

**AC-001-1**: `(auth)/checkout` 建立 booking 時,**優雅處理日期衝突**——後端回 409 / `E_4001`（日期已被預訂）時,顯示明確訊息「所選日期已被預訂,請返回修改日期」（取代通用「結帳失敗」）;其他 booking 錯誤（房源非 ACTIVE E_3002、超容量 E_4005 等）亦給對應可讀訊息
**AC-001-2**: 詳情頁 `ListingDetail` ROOM 分支:維持 `/price` 計價顯示;**加購前日期驗證**（退房 > 入住、入住不早於今日）於前端先擋,錯誤訊息清楚;testid 供 E2E（沿用 listing-checkin/checkout/price）
**AC-001-3**: 詳情頁 ROOM 加購成功後,提示可前往購物車/結帳完成訂房（引導閉環）
**AC-001-4**: 前端 `npm run build` + `type-check` + `lint` 0 error;**無後端變動**（availability 端點 GET+body 不可用,改以建立時 409 回饋）

### US-002：BookingService.createBooking 抽取 + checkout 重構（P2）（前端）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: AI-2103b（清理）

**AC-002-1**: `services/booking.ts` 補 `createBooking(request)` → `POST /v2/bookings`（含既有 Idempotency-Key 若 checkout 有用到則保留）,型別對齊後端 CreateRequest/BookingResponse
**AC-002-2**: `(auth)/checkout/page.tsx` 改用 `bookingService.createBooking`（移除內聯 apiClient.post）;**行為完全不變**（同參數、同錯誤處理、同清空購物車與導向）
**AC-002-3**: `type-check` / `lint` 0 error;checkout 既有 E2E（若有）不退步

### US-003：ROOM 訂房閉環 E2E（P2）（前端）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: AI-2104（ROOM 訂房閉環自動化）

**AC-003-1**: 以 page.route mock（免 seed）覆蓋 ROOM 閉環:詳情頁 ROOM 選日期 → mock availability（可預訂）→ 加入購物車 → 前往 checkout → 填訪客資料 → mock `POST /v2/bookings` 成功 → 斷言成功回饋/導向
**AC-003-2**: 鑑別:mock availability **不可預訂** → 詳情頁禁用加購 + 顯示原因（listing-unavailable）
**AC-003-3**: `make validate-e2e` 綠;既有 E2E（at-homepage/at-buyer-pages/at-listing-detail 等）不退步

### US-004：E2E 共用登入 helper 抽取（P2）（前端測試）

> **SP**: 2 | **優先級**: P2（原 Buffer，因 US-001 縮減而提為承諾）| **狀態**: 📋 Ready
> **承自**: AI-2101（含 DEF-022 硬等待）

**AC-004-1**: 9 份重複 `registerAndLogin` 抽為單一共用 helper（`e2e/helpers/auth.ts`）:submit 用 `:not(:has-text("搜尋"))`、以 `waitForURL` 取代固定 `waitForTimeout`（收斂 DEF-022）;各 spec 改 import 共用 helper
**AC-004-2**: `make validate-e2e` 綠、所有既有 spec 不退步（登入流程等價）

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | checkout ROOM 訂房衝突優雅處理 + 詳情頁 ROOM 微調 | 3 | P1（旗艦）|
| US-002 | BookingService.createBooking 抽取 + checkout 重構 | 2 | P2 |
| US-003 | ROOM 訂房閉環 E2E（mock，含衝突路徑）| 3 | P2 |
| US-004 | E2E 共用登入 helper 抽取（含 DEF-022）| 2 | P2 |
| **P1+P2 承諾合計** | | **10 SP** | |

> **Velocity 參考**：S35=18（純前端異常高）, S36=10, S37=10, S38=8。**本 Sprint 10 SP**,健康區間。閉環已存在故聚焦補強;因 availability 端點不可用（GET+body）,US-001 由 availability 檢查改為 checkout 409 優雅處理（縮減）,並提 US-004（helper 抽取）為承諾補足容量。風險集中於 checkout 重構的行為等價。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 詳情頁 availability（service checkAvailability → ListingDetail ROOM 整合）
   ↓ build+type-check+lint 綠
US-002 createBooking 抽取（service → checkout 改用，行為不變）
   ↓ type-check+lint 綠
US-003 ROOM 閉環 E2E（針對 US-001/002 後結構撰寫，mock）
   ↓ make validate-e2e 綠
US-004（Buffer）E2E 共用 helper 抽取
```

**強制**：前端每檔完成後**立即** build/type-check/lint。US-002 **行為等價**為重（checkout 是關鍵付款前流程）。US-003 排最後（針對最終結構）。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **checkout 重構破壞既有訂房**（US-002 最大風險）| 純抽取、行為等價;逐步比對原內聯邏輯（參數/錯誤/清空車/導向）;US-003 E2E 把關 |
| availability 與 /price 語意重疊 | availability 已回 totalPrice,US-001 以 availability 為主（可預訂 + 總價 + 原因）,避免雙來源;若需保留 /price 顯示則明確分工 |
| **加共用元件到關鍵頁 E2E helper 碰撞**（S37 教訓）| checkout/詳情頁互動元件用專屬 testid;沿用 `:not(:has-text("搜尋"))` 慣例 |
| ROOM 閉環 E2E 依賴多步 mock（詳情→availability→cart→checkout→booking）| mock 分端點精準比對 URL;比照 S38 at-listing-detail 的分支 mock 範式 |
| 日曆端點缺失致 UX 受限 | S39 用 availability（區間檢查）即可完成訂房;完整日曆 UI（含後端 /calendar）另立項,誠實記錄 |
| 累積 32 commit 待 push | push 為檢查點（AI-1906）,完整 `make validate-release` + 徵詢,嚴禁 --no-verify |

---

## 6. Definition of Done

- [ ] US-001（AI-2103b）：詳情頁 ROOM availability 檢查完成（可預訂/不可預訂鑑別 + 禁用加購 + 原因）
- [ ] US-002：`BookingService.createBooking` 抽取、checkout 改用且行為等價
- [ ] US-003（AI-2104）：ROOM 訂房閉環 E2E（mock）補齊
- [ ] 前端 `npm run build` / `type-check` / `lint` 0 error
- [ ] `make validate-e2e` 綠、既有 E2E 不退步
- [ ] 無後端/DB 變動（沿用既有端點）;多租戶隔離不破壞
- [ ] Sprint 39 Review / Retrospective / Release Notes 建立
- [ ] （檢查點）S32~S39 累積批次於徵詢後完整守門 push（AI-1906）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| booking service 補強 | `frontend/src/services/booking.ts`（checkAvailability + createBooking + 型別）|
| 詳情頁 availability | `frontend/src/components/storefront/ListingDetail.tsx` |
| checkout 重構 | `frontend/src/app/(auth)/checkout/page.tsx`（改用 service）|
| ROOM 閉環 E2E | `frontend/e2e/at-listing-detail.spec.ts` 或新 `at-room-booking.spec.ts` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）|

---

## 8. 🔴 使用者確認點（2026-07-02 已確認）

1. ✅ **日曆/可用性策略**：使用者選「純前端 /price + 409 優雅處理」（免後端）。原「用 availability 端點」不成立（該端點 GET+@RequestBody、瀏覽器無法送 body）→ 詳情頁保留 /price 計價,日期衝突回饋改在 checkout 建立 booking 時處理 409。不實作後端日曆端點（另立項）。
2. ✅ **ROOM 購買路徑**：維持現況「cart → checkout → 建立 booking」（非走 order,不統一二者）。
3. ✅ **範圍 / SP / 順序**：核准立即開始。US-001（3）+ US-002（2）+ US-003（3）+ US-004（2）= 10 SP;順序 US-001→002→003→004。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
