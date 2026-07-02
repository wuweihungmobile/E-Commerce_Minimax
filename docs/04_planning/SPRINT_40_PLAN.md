# Sprint 40 計劃 / Sprint 40 Plan

> **Sprint 編號**: Sprint 40
> **期間**: 2027-04-25 ~ 2027-05-08 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: [SPRINT_39_RETRO.md](../05_development/SPRINT_39_RETRO.md)（AI-2201 / AI-1903 / AI-2202）、S40 探勘（availability 端點 + 後端測試現況）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**ROOM 可用性 UX 完成（含小幅後端）**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「ROOM 可用性 UX 完成（含小幅後端）」 | 近期首次後端變動（read-only，無 DB）|
| availability 端點問題已定位 | ✅ `GET /v2/bookings/availability` 為 GET+@RequestBody（瀏覽器 GET 無法送 body、前端無法呼叫）| S39 因此無法做詳情頁即時可用性 |
| 後端測試現況已查 | ✅ BookingControllerE2ETest（API-M06-009/010）+ BookingIntegrationTest **以 body 呼叫** availability;**測試註解已自標「應改 @RequestParam」** | 改端點需同步更新這些測試為 query params |
| 修法範圍 | ✅ controller GET+@RequestBody → GET+@RequestParam（roomListingId/checkInDate/checkOutDate）;service 簽名不變（內部仍建 AvailabilityRequest）| **read-only、無 DB/schema 變動、無 Flyway** |
| 授權 | ✅ availability 需 `booking:read`;詳情頁 ROOM 日期選擇僅登入者可見（未登入→listing-auth-empty）→ availability 呼叫必已授權 | 無 guest-availability 問題 |
| push 前置 | ⚠️ **本 Sprint 有後端變動** → push 需完整 `make validate-release`（act 跑 backend + frontend + schema + E2E），非僅 validate-e2e | 累積達 S32~S40 |

---

## 1. Sprint 40 目標

> **主題**: ROOM 可用性 UX 完成

修復 S39 揭露的 availability 端點缺陷（GET+@RequestBody 瀏覽器不可用）並補上詳情頁**即時可用性檢查**:買家在商品詳情頁選定入住/退房日期後,即時得知「可預訂 + 總價」或「不可預訂 + 原因」,不可預訂時禁止加購——把日期衝突回饋從 checkout（S39）提前到詳情頁,完成 ROOM 訂房 UX。後端變動限於 controller 參數綁定（read-only、**無 DB/schema 變動**）+ 同步更新既有後端測試。

---

## 2. User Stories

### US-001：availability 端點 @RequestParam 修復 + 後端測試更新（P1）（後端）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready
> **承自**: AI-2201（S39 揭露）

**AC-001-1**: `BookingController.checkAvailability` 由 `@GetMapping + @Valid @RequestBody AvailabilityRequest` 改為 `@GetMapping + @RequestParam`（`roomListingId: UUID`、`checkInDate: LocalDate`、`checkOutDate: LocalDate`,含 `@DateTimeFormat(ISO.DATE)`）;內部仍建 `AvailabilityRequest` 呼叫 `bookingService.checkAvailability`（service 不動）
**AC-001-2**: 更新 `BookingControllerE2ETest`（API-M06-009 可用性、API-M06-010 衝突）:由 `.body(AvailabilityRequest)` 改為 query params;移除「應改 @RequestParam」的 TODO 註解（已修）
**AC-001-3**: 更新 `BookingIntegrationTest` 中呼叫 availability 之處為 query params
**AC-001-4**: 後端 `mvn compile` + 受影響測試（Booking 相關）0 fail（開發-編譯-測試循環）;`make validate-schema` 無漂移（本變動不涉 schema，仍把關）
**AC-001-5**: 無 DB/migration 變動（Flyway 維持 V57）

### US-002：詳情頁 ROOM 即時可用性檢查（P1 旗艦）（前端）

> **SP**: 4 | **優先級**: P1 | **狀態**: 📋 Ready
> **承自**: AI-2201（前端）

**AC-002-1**: `services/booking.ts` 補 `checkAvailability(roomListingId, checkIn, checkOut)` → `GET /v2/bookings/availability?roomListingId=&checkInDate=&checkOutDate=`,型別含 available/nightsCount/totalPrice/currency/unavailableReason
**AC-002-2**: `ListingDetail` ROOM 分支:選定日期後「查詢可用性」→ 顯示「可預訂 · N 晚合計 NT$X」或「不可預訂 · {原因}」;**不可預訂時禁用「加入購物車」**並提示（取代 S39 僅 /price 計價）
**AC-002-3**: 可預訂時加購維持既有帶日期流程;加購前若可用性未查或已不可訂則擋下並提示
**AC-002-4**: 保留 S39 前端日期驗證（入住不早於今日、退房晚於入住）;testid 供 E2E（listing-availability / listing-unavailable / listing-add-cart）
**AC-002-5**: 前端 `npm run build` + `type-check` + `lint` 0 error

### US-003：availability E2E 更新（P2）（前端）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: AI-2104 延伸

**AC-003-1**: `at-room-booking.spec.ts`（或詳情 spec）以 mock availability:可預訂 → 顯示可訂+總價 → 加購成功;不可預訂 → 禁用加購 + 顯示原因（listing-unavailable）
**AC-003-2**: `make validate-e2e` 綠、既有 E2E 不退步

### US-004（Buffer）：買家閉環 live 走查（P3）

> **SP**: 2 | **優先級**: Buffer/P3 | **狀態**: 📋 Ready
> **承自**: AI-1903（需 live 環境）

**AC-004-1**: live 環境走查買家閉環（首頁→詳情→可用性→加購→checkout→booking/order→通知）,含 S37~S40 全成果;記錄缺陷。環境允許則執行,否則順延並誠實記錄

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | availability 端點 @RequestParam 修復 + 後端測試更新（後端）| 3 | P1 |
| US-002 | 詳情頁 ROOM 即時可用性檢查（前端）| 4 | P1（旗艦）|
| US-003 | availability E2E 更新 | 2 | P2 |
| **P1+P2 承諾合計** | | **9 SP** | |
| US-004 | 買家 live 走查（Buffer）| 2 | P3 |

> **Velocity 參考**：S36=10, S37=10, S38=8, S39=10。**本 Sprint 9 SP**,健康區間。含後端變動故保守;後端變動雖小（controller 參數綁定）但需連動測試更新 + 完整 validate-release。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 後端 availability @RequestParam（controller → 更新後端測試）
   ↓ mvn compile + Booking 測試 0 fail、validate-schema 無漂移
US-002 前端即時可用性（booking service checkAvailability → ListingDetail ROOM 整合）
   ↓ build+type-check+lint 綠
US-003 availability E2E（針對 US-002 後結構撰寫，mock）
   ↓ make validate-e2e 綠
US-004（Buffer）live 走查
```

**強制**：
- 後端（US-001）：controller 改完**立即** `mvn compile` + 受影響 Booking 測試,失敗立即修。
- **US-001 先於 US-002**——前端 checkAvailability 依賴修好的 @RequestParam 端點（雖 mock E2E 不依賴真實端點,但真實整合需先修後端）。
- 前端每檔完成後**立即** build/type-check/lint。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **改後端端點打破既有測試**（US-001 最大風險）| 已定位 3 處測試（BookingControllerE2ETest ×2 + BookingIntegrationTest）以 body 呼叫;同步改 query params;mvn 受影響測試把關 |
| 後端變動引入 schema 漂移疑慮 | 本變動純 controller 參數綁定,不涉 entity/migration;仍跑 `make validate-schema`（記憶 `local-ci-cannot-catch-schema-validation`）|
| 前端即時可用性 UX 與 /price 重疊 | availability 已回 totalPrice → 以 availability 為主（可訂+總價+原因）,移除/整合 S39 的 /price-only 計價,避免雙來源 |
| **加共用元件到關鍵頁 E2E helper 碰撞**（S37 教訓）| 詳情頁互動元件用專屬 testid;沿用共用登入 helper |
| **push 需完整 validate-release**（本 Sprint 有後端）| push 前 `make validate-release`（act backend+frontend + schema + E2E）,非僅 validate-e2e;嚴禁 --no-verify（AI-1906）|
| 累積 38→ commit 待 push | 檢查點徵詢後完整守門一次 push |

---

## 6. Definition of Done

- [ ] US-001（AI-2201 後端）：availability 端點改 @RequestParam、3 處後端測試更新、mvn Booking 測試 0 fail、validate-schema 無漂移
- [ ] US-002（AI-2201 前端）：詳情頁 ROOM 即時可用性（可訂/不可訂鑑別 + 禁用加購 + 原因 + 總價）
- [ ] US-003：availability E2E（可訂加購 / 不可訂禁用）補齊
- [ ] 後端 `mvn` 相關測試 0 fail;前端 `build`/`type-check`/`lint` 0 error
- [ ] `make validate-e2e` 綠、既有 E2E 不退步;無 DB/migration 變動
- [ ] Sprint 40 Review / Retrospective / Release Notes 建立
- [ ] （檢查點）S32~S40 累積批次於徵詢後**完整 `make validate-release`**（含後端 act）後 push（AI-1906）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端端點修復 | `backend/.../api/controller/BookingController.java`（checkAvailability @RequestParam）|
| 後端測試更新 | `backend/.../api/controller/BookingControllerE2ETest.java`、`backend/.../integration/BookingIntegrationTest.java` |
| 前端 service | `frontend/src/services/booking.ts`（checkAvailability + 型別）|
| 詳情頁即時可用性 | `frontend/src/components/storefront/ListingDetail.tsx` |
| availability E2E | `frontend/e2e/at-room-booking.spec.ts` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）|

---

## 8. 🔴 待使用者確認點

1. **後端變動範圍**：僅 `BookingController.checkAvailability` 由 @RequestBody 改 @RequestParam（read-only、**無 DB/schema/Flyway 變動**）+ 同步更新 3 處既有後端測試。push 需完整 `make validate-release`（act 跑 backend）。是否同意此後端變動範圍?
2. **/price vs availability**：詳情頁 ROOM 改以 availability 為主（回可訂+總價+原因）,整合/取代 S39 的 /price-only 計價。是否同意?
3. **範圍 / SP / 順序**：US-001（3 後端）+ US-002（4 前端）+ US-003（2）= 9 SP,US-004 Buffer;順序 US-001→002→003→004。是否核准?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
