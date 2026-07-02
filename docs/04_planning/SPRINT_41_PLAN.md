# Sprint 41 計劃 / Sprint 41 Plan

> **Sprint 編號**: Sprint 41
> **期間**: 2027-05-09 ~ 2027-05-22 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: [SPRINT_40_RETRO.md](../05_development/SPRINT_40_RETRO.md)（AI-2301 / AI-2101b / AI-2202 / AI-1903）、活躍 DEF-021、S41 五 Agent 探勘（日曆/契約、登入 helper、port、字體、走查）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者指示「**徹底清償 S41 全候選（技術債 + 收尾）**」——AI-2301 + AI-2101b + AI-2202 + AI-1903 + DEF-021 全數執行

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者指示徹底執行全部 5 候選項目 | 技術債清償 + 一個功能（整月日曆）|
| S40 已 push、與 origin/main 同步 | ✅ 領先 0（HEAD=origin/main=2e33c6d）| 無 push 債,本 Sprint 從乾淨基線起 |
| 技術現況已調查 | ✅ 5 個 Explore Agent 完成（日曆/契約、登入 helper、port、字體、走查）| 各項可行性已釐清（見下） |
| AI-2301 修法 | ✅ `validate-release` 前自動 `test-db-down`（Makefile:215 前插一行，冪等）+ 文件化 | 同時涵蓋直接執行與 pre-push hook 兩條路徑 |
| AI-2101b 範圍 | ✅ 5 spec 需重構（at-m10-chat、at-m17-001/002/003/004）→ 共用 `e2e/helpers/auth.ts` | 需擴充 helper（回傳 userId + login-only 變體）|
| AI-2202 後端 | ✅ 資料層已備妥（`room_calendar` + `RoomCalendarRepository.findByListingIdAndCalendarDateBetween` + `CalendarResponse` DTO）| **read-only 新端點,無 entity/migration/schema 變動**（如 S40 小幅後端）|
| AI-1903 限制 | ⚠️ **真人部署環境走查無法由 AI 代做** | 交付：自動全棧走查（make validate-e2e 證據）+ 手動 live 走查 checklist 文件;殘留誠實標記 |
| DEF-021 限制 | ⚠️ `@font-face` 靜態 woff2 技術可行（無 CSP 阻擋）,但需外部下載 + 子集化數 MB 字體 + repo 肥大,P3 成本高 | 交付：技術評估 + 決策建議（不強行 ship 大型二進位資產）|
| push 前置 | ⚠️ **本 Sprint 有後端變動（US-004）** → push 需完整 `make validate-release` | push 需使用者明確授權;嚴禁 --no-verify |

---

## 1. Sprint 41 目標

> **主題**: S41 技術債徹底清償 + 整月日曆功能

一次清償 S40 遺留的全部 Action Items 與活躍 DEF：**(1)** 制度化 test DB↔act port 衝突（讓 `validate-release` 自動處理,不再靠人工記憶）;**(2)** 完全統一 E2E 登入 helper（消除 5 個 spec 的重複 inline 登入碼）;**(3)** 清理 api.ts 死碼/錯配端點契約,並補上**整月日曆**（買家在詳情頁一眼看整月可訂狀況,取代 S40 的區間查詢）;**(4)** 買家閉環走查（自動 E2E 證據 + 手動 checklist）;**(5)** CJK 字體技術決策。後端變動限於**新增 read-only 月可用性端點**（無 DB/schema/Flyway 變動）。

---

## 2. User Stories

### US-001：test DB ↔ act port 衝突制度化（P2）（AI-2301，工具/CI）

> **SP**: 1 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: AI-2301（S40 Retro）

**AC-001-1**: `Makefile` `validate-release` 於 `validate-all`（act）前**自動執行 `test-db-down`**（冪等 `docker rm -f ... || true`）,消除 `nk-test-redis` 佔 6379 與 act redis 的衝突;直接執行與 pre-push hook（呼叫 `make validate-release`）兩路徑皆涵蓋
**AC-001-2**: `docs/08_deployment/LOCAL_CI_VALIDATION.md` 明文化工作流：「**commit 用 `make test-db-up`（pre-commit 核心 @SpringBootTest 需 5432/6379）;`validate-release` 已自動 test-db-down**」,並說明為何 act 需獨佔 6379
**AC-001-3**: `Makefile` `validate-release` help 文字更新反映自動 down;`validate-schema`/`validate-e2e` 用非標準 port（55432/56379）不受影響,不動
**AC-001-4**: 不改 `act-compat.yml` 的 5432/6379（鏡像雲端 CI,須保持）

### US-002：E2E 登入 helper 完全統一（P2）（AI-2101b，測試）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: AI-2101b（S40 Retro）

**AC-002-1**: 擴充 `frontend/e2e/helpers/auth.ts`：(a) `registerAndLogin` 回傳含 `userId`（讀 `localStorage.user`）;(b) 新增 `loginOnly(page, email, password)`（固定帳號登入,不註冊,供 admin 情境）
**AC-002-2**: 重構 `at-m17-001/003/004.spec.ts` 的 inline `beforeEach` 登入 → 呼叫共用 `registerAndLogin`（drop-in）
**AC-002-3**: 重構 `at-m17-002.spec.ts`（固定 `admin@nextkey.local`）→ 呼叫 `loginOnly`
**AC-002-4**: 重構 `at-m10-chat.spec.ts`：移除本地重複的 `registerAndLogin`/`loginExisting` → 用共用 helper（`userId` 取自 helper 回傳,第二次登入用 `loginOnly`）
**AC-002-5**: 全部沿用安全 submit 選擇器 `button[type="submit"]:not(:has-text("搜尋"))`（已一致）;`npm run lint` + `tsc` 0 error;`npx playwright test --list` 可列出全部 spec（結構正確）

### US-003：api.ts 端點契約清理（P2）（AI-2202a，前端）

> **SP**: 1 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: AI-2202（S40 Retro）

**AC-003-1**: 逐一核對 `frontend/src/lib/api.ts` 死碼/錯配並修正（**先查使用點,有用則對齊、無用則移除**）：
  - `pricing.*`（:143-150）base path `/v2/pricing` → 後端實為 `/v2/dashboard/pricing`（對齊或標記）
  - `listings.update`/`listings.delete`（:21-22）→ 後端 `DashboardListingController` 無 PUT/DELETE（確認使用點後對齊/移除/標 TODO）
  - `bookings.calendar`（:86，死碼且未使用）→ 由 US-004 接管（realign 至真實月端點路徑）
**AC-003-2**: 補 `listings.effectivePrice` → `GET /v2/listings/{id}/effective-price`（後端有、api.ts 缺）;僅在確有前端需求時加,否則文件記錄反向缺口
**AC-003-3**: 任何移除前確認無 import/呼叫（grep）;`npm run build` + `tsc` + `lint` 0 error,不破壞既有頁面

### US-004：整月日曆（後端 read-only 端點 + 前端日曆）（P1 旗艦）（AI-2202b）

> **SP**: 5 | **優先級**: P1 | **狀態**: 📋 Ready
> **承自**: AI-2202（S40 Retro）

**AC-004-1（後端）**: 新增 `GET /v2/bookings/calendar?roomListingId={}&month={YYYY-MM}`（或 startDate/endDate）→ 回 `List<CalendarResponse>`（date/status/price）,`@PreAuthorize("hasAuthority('booking:read')")`;實作用既有 `RoomCalendarService.getCalendarRange`（**read-only,無 entity/migration/schema 變動**）;無回傳列之日視為 AVAILABLE（與 `checkAvailability` 邏輯一致）
**AC-004-2（後端測試）**: 新增 controller 測試（月端點回正確區間 + 授權 401/403）;`mvn compile` + 受影響測試 0 fail;`make validate-schema` 無漂移;Flyway 不變
**AC-004-3（前端 service）**: `booking.ts` 補 `getCalendar(roomListingId, month)` + `CalendarDay` 型別;`api.ts` `bookings.calendar` realign 至真實路徑
**AC-004-4（前端 UI）**: `ListingDetail` ROOM 分支新增**整月日曆元件**（月切換、每日狀態著色：可訂/已訂/封鎖、點選帶入 checkIn/checkOut）;沿用 S40 即時可用性（選定區間 → 可訂+總價 / 不可訂+原因 → 禁用加購）;整月日曆與區間查詢並存不衝突
**AC-004-5（前端驗證）**: 專屬 testid（listing-calendar / calendar-day-{date} / calendar-nav）避免 E2E 碰撞;`npm run build`+`tsc`+`lint` 0 error
**AC-004-6（E2E）**: `at-room-booking.spec.ts`（或新 spec）以 mock 日曆：載入整月 → 已訂日標記 → 點選可訂區間 → 可用性+總價 → 加購;不可訂日禁選/禁加購

### US-005：買家閉環走查（自動證據 + 手動 checklist）（P1）（AI-1903）

> **SP**: 2 | **優先級**: P1 | **狀態**: 📋 Ready
> **承自**: AI-1903（順延多 Sprint，需 live 環境）

**AC-005-1（自動證據）**: 執行 `make validate-e2e`（真後端 JAR:8080 + 真前端:3000 + 乾淨 DB + Playwright,鏡像雲端 e2e job）→ 買家閉環 spec 全綠作為客觀證據;保留 Playwright HTML 報告位置
**AC-005-2（手動 checklist）**: 產出 `docs/03_testing/BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`：完整買家旅程步驟（首頁→詳情→整月日曆→可用性→加購→cart→checkout→booking/order→通知）+ **明載 `make up` 走查需修 `NEXT_PUBLIC_API_URL=http://localhost:8080/api`（避免 DEF-014 `/v2/v2` 雙前綴 401）** + dev profile Flyway 關閉導致無 seed 的注意事項
**AC-005-3（誠實揭露）**: 明載 automated 證據涵蓋「前端+路由+client 接線 + 後端啟動/schema」,但買家 HTTP 多為 mock;真 DB 落地的 order→pay→notify→ship→review 需 cross-role seed + 部署環境,**仍為殘留**（AI-1903 續留待 live 環境）

### US-006：CJK 字體技術決策（P3）（DEF-021）

> **SP**: 1 | **優先級**: P3 | **狀態**: 📋 Ready
> **承自**: DEF-021（活躍，續延多 Sprint）

**AC-006-1**: 產出 `docs/06_quality/CJK_FONT_ASSESSMENT.md`：分析現況（系統字體堆疊 `globals.css:161-163`,Turbopack 無法 self-host next/font CJK subset）+ 可行路徑（`@font-face` 靜態子集 woff2 於 `frontend/public/fonts/`,無 CSP 阻擋,繞過 next/font）+ 成本（外部下載授權字體、子集化工具鏈、離線建置、repo 二進位肥大、Turbopack 相容驗證）
**AC-006-2**: 給明確**決策建議**（維持系統堆疊並關閉 DEF-021 為 accepted-fallback ／ 排入未來正式 task 並刻意 source 字體）;更新 DEFERRED_ITEMS_TRACKER 反映決策
**AC-006-3**: **不**在 P3 下強行加入數 MB 字體二進位（除非使用者明確指示）;誠實揭露這是評估型 US

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | test DB↔act port 制度化（AI-2301）| 1 | P2 |
| US-002 | E2E 登入 helper 完全統一（AI-2101b）| 2 | P2 |
| US-003 | api.ts 端點契約清理（AI-2202a）| 1 | P2 |
| US-004 | 整月日曆（後端 read-only 端點 + 前端）（AI-2202b）| 5 | P1（旗艦）|
| US-005 | 買家閉環走查（自動證據 + 手動 checklist）（AI-1903）| 2 | P1 |
| US-006 | CJK 字體技術決策（DEF-021）| 1 | P3 |
| **承諾合計** | | **12 SP** | |

> **Velocity 參考**：S36=10, S37=10, S38=8, S39=10, S40=9。**本 Sprint 12 SP 略高於近期均值（~9.4）**——誠實揭露（Rule 12）:因使用者指示徹底清償全候選;但 6 項中 4 項為小型（制度化/契約/走查文件/字體評估各 1-2 SP）,僅 US-004 為旗艦（5 SP）。若時間不足,US-006（P3）可續延。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 AI-2301（Makefile validate-release 自動 test-db-down + 文件）
   ↓ 低風險 infra 先行，後續 validate 更順
US-002 AI-2101b（擴充 auth.ts → 重構 5 spec）
   ↓ lint + tsc + playwright --list 綠
US-003 AI-2202a（api.ts 契約清理，先 grep 使用點）
   ↓ build + tsc + lint 綠
US-004 AI-2202b 後端（月端點 → mvn compile + 測試 0 fail、validate-schema 無漂移）
   ↓ 後端綠
US-004 AI-2202b 前端（service → 整月日曆元件 → ListingDetail → E2E mock）
   ↓ build + tsc + lint 綠
US-005 AI-1903（make validate-e2e 自動證據 → 同時驗證 US-002/003/004 前端 → 手動 checklist 文件）
   ↓ E2E 綠
US-006 DEF-021（字體評估 + 決策文件）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：
- 後端（US-004）：controller/service 改完**立即** `mvn compile` + 受影響 Booking/Calendar 測試,失敗立即修（開發-編譯-測試循環）。
- 前端每檔完成後**立即** build/type-check/lint。
- **US-004 後端先於前端**——前端 `getCalendar` 依賴真實端點（mock E2E 不依賴,但真實整合需先修後端）。
- US-005 的 `make validate-e2e` 放在前端 US 之後,一次驗證 US-002/003/004 前端成果 + 作 AI-1903 證據（雙重用途）。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **api.ts 移除端點打破既有頁面**（US-003）| 移除前 grep 全 `frontend/src` 確認無 import/呼叫;有用則對齊 base path 而非移除;build+tsc 把關 |
| **新後端端點引入 schema 漂移疑慮**（US-004）| 純新增 read-only controller + 用既有 repository 查詢,不涉 entity/migration;仍跑 `make validate-schema`（記憶 `local-ci-cannot-catch-schema-validation`）|
| **整月日曆加到關鍵詳情頁 → E2E helper/選擇器碰撞**（S37 教訓）| 日曆元件用專屬 testid;沿用共用登入 helper（本 Sprint US-002 正好統一）|
| **重構登入 helper 破壞既有綠 E2E**（US-002）| helper 行為對齊（登入優先/自動註冊）;`playwright --list` 驗證結構;真實 E2E 由 US-005 的 validate-e2e 把關 |
| **AI-1903 無法真人部署走查** | 誠實交付自動證據 + 手動 checklist;殘留明確標記續留（不謊稱完成）|
| **DEF-021 強行 ship 大字體資產** | P3 不強行加二進位;交付評估 + 決策;需使用者拍板才動 |
| **本 Sprint 有後端變動 → push 需完整 validate-release** | push 需使用者授權;先 `make test-db-down`（US-001 已自動化）;嚴禁 --no-verify |
| 12 SP 略高 | US-006（P3）為可續延緩衝;逐 US commit,隨時可停在乾淨檢查點 |

---

## 6. Definition of Done

- [ ] US-001（AI-2301）：`validate-release` 自動 test-db-down + LOCAL_CI 文件化;不動 act-compat 標準 port
- [ ] US-002（AI-2101b）：auth.ts 擴充（userId + loginOnly）+ 5 spec 重構;lint/tsc/playwright --list 綠
- [ ] US-003（AI-2202a）：api.ts 契約清理（pricing/listings/bookings.calendar）;build/tsc/lint 綠、不破壞頁面
- [ ] US-004（AI-2202b）：後端月端點（read-only）+ 測試 0 fail + validate-schema 無漂移;前端整月日曆 + E2E;build/tsc/lint 綠
- [ ] US-005（AI-1903）：`make validate-e2e` 綠（自動證據）+ 手動 live 走查 checklist 文件;殘留誠實標記
- [ ] US-006（DEF-021）：CJK 字體評估 + 決策文件;tracker 更新
- [ ] 後端 `mvn` 相關測試 0 fail;前端 `build`/`type-check`/`lint` 0 error;`make validate-e2e` 綠;無 DB/migration 變動
- [ ] Sprint 41 Review / Retrospective / Release Notes 建立;trackers 更新
- [ ]（檢查點）本 Sprint 變動於徵詢使用者後**完整 `make validate-release`** 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| port 制度化 | `Makefile`（validate-release 自動 test-db-down）、`docs/08_deployment/LOCAL_CI_VALIDATION.md` |
| E2E helper | `frontend/e2e/helpers/auth.ts`、`at-m10-chat.spec.ts`、`at-m17-001/002/003/004.spec.ts` |
| 端點契約 | `frontend/src/lib/api.ts` |
| 整月日曆後端 | `backend/.../api/controller/BookingController.java`（+ DTO 若需）、對應測試 |
| 整月日曆前端 | `frontend/src/services/booking.ts`、`frontend/src/components/storefront/*`（日曆元件）、`ListingDetail.tsx`、`at-room-booking.spec.ts` |
| 買家走查 | `docs/03_testing/BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md` + Playwright 報告 |
| 字體決策 | `docs/06_quality/CJK_FONT_ASSESSMENT.md` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers（`docs/04_planning/`）|

---

## 8. 🔴 使用者授權範圍（本次已明確指示徹底執行）

1. **後端變動範圍**：US-004 僅**新增** read-only `GET /v2/bookings/calendar`（用既有 repository,**無 DB/schema/Flyway 變動**）+ 新增測試。push 需完整 `make validate-release`。
2. **AI-1903 誠實界線**：交付自動 E2E 證據 + 手動 checklist 文件;真人部署環境走查與 cross-role seed 落地驗證為殘留,續留。
3. **DEF-021 誠實界線**：交付技術評估 + 決策建議;P3 不強行加入大型字體二進位,除非使用者拍板。
4. **push**：本 Sprint 完成後,push 仍需使用者於檢查點明確授權（承本專案 commit-often/push-rarely 慣例）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
