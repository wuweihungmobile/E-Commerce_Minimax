# Sprint 42 計劃 / Sprint 42 Plan

> **Sprint 編號**: Sprint 42
> **期間**: 2027-05-23 ~ 2027-06-05 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: [SPRINT_41_RETRO.md](../05_development/SPRINT_41_RETRO.md)（AI-2302 / AI-2202c）、活躍 DEF-022、S42 三 Agent 探勘（日曆價格、pre-commit、waitForTimeout）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**收尾技術債**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「收尾技術債」 | 小型技術債收斂，回歸健康 SP |
| S41 狀態 | ✅ 已完成（10 commit，未 push，push 債 1 Sprint）| 本 Sprint 承接 |
| 技術現況已調查 | ✅ 3 個 Explore Agent（日曆價格、pre-commit、waitForTimeout）| 各項可行範圍已釐清 |
| AI-2302 修法 | ✅ 僅 2 個 @SpringBootTest 核心測試慢；加 `@Tag("slow")` + pre-commit `-DexcludedGroups=slow`（兩者 pre-push act 仍跑，零覆蓋損失）| 額外好處：backend pre-commit 不再需 test DB |
| AI-2202c 範圍 | ✅ Part A 每日價格顯示 = 純前端（後端已回 price）；Part B「未開放 vs 可訂」需後端新語意 → **Part B 另立，非收尾範圍** | 誠實界線 |
| DEF-022 範圍 | ✅ 5 檔 47 處：1 處保留（at-m10-chat STOMP settle），46 處刪冗餘/改顯式等待 | 機械式重構 |
| push 前置 | ⚠️ 本 Sprint 有後端變動（@Tag 測試註解）→ push 需完整 `make validate-release` | 承 S41 push 債，累積後徵詢 push |

---

## 1. Sprint 42 目標

> **主題**: 收尾技術債

清償 S41 遺留的小型技術債，讓開發體驗與測試穩定性再上一階，並補齊整月日曆的價格顯示：**(1)** 加速 backend pre-commit（排除 2 個慢速 @SpringBootTest，順帶移除 pre-commit 對 test DB 的依賴）；**(2)** 整月日曆每日價格顯示（純前端，後端已回 price）；**(3)** 清除 E2E 硬等待（waitForTimeout → 顯式等待，降 flaky）。後端變動限於**測試 `@Tag` 註解**（無 production code、無 schema）。

---

## 2. User Stories

### US-001：backend pre-commit 提速（P1）（AI-2302）

> **SP**: 2 | **優先級**: P1 | **狀態**: 📋 Ready

**AC-001-1**: 為 2 個慢速 `@SpringBootTest` 核心測試加 `@Tag("slow")`：`ReviewServiceCacheIntegrationTest`、`SellerDashboardServiceCacheTest`
**AC-001-2**: `backend/hooks/pre-commit`（quick test 步驟）與 `Makefile` `check-backend` 加 `-DexcludedGroups=slow`
**AC-001-3**: 驗證 pre-commit quick test **不需 test DB** 即可通過（排除 2 慢測後，其餘核心測試為純單元）；實測 `test-db-down` 狀態下跑 quick test 綠
**AC-001-4**: 不動 CI（act `mvn test`/`mvn verify` 無 `excludedGroups`）→ pre-push act 仍完整跑這 2 測試（零覆蓋損失）
**AC-001-5**: 更新 `LOCAL_CI_VALIDATION.md`：backend commit 不再強制 `test-db-up`（quick test 已無 DB 依賴）

### US-002：整月日曆每日價格顯示（P2）（AI-2202c Part A）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-002-1**: `MonthCalendar` 建 `priceByDate` map（保留目前丟棄的 `d.price`）；新增 `basePrice` + `currency` props 供無記錄之日 fallback
**AC-002-2**: `ListingDetail` 傳入 `listing.basePrice` / `listing.currency` 給 `MonthCalendar`
**AC-002-3**: 每日格顯示價格（可訂日）；版面調整（cell 由純日號 → 日號 + 小字價格）；不可訂日維持灰刪除線
**AC-002-4**: 沿用 `formatPrice`（與 ListingDetail 一致）；`npm run build`+`tsc`+`lint` 0 error
**AC-002-5**: E2E-ROOM-05（或補一小案）驗證日曆格顯示價格；不破壞既有

> **註（誠實界線）**：AI-2202c Part B「未開放 vs 可訂」顯式標記需後端新語意（現無「開放窗」概念，AVAILABLE row 為偶發），**另立 AI-2202d 待評估，非本 Sprint 範圍**。

### US-003：E2E 硬等待清除（P2）（DEF-022）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-003-1**: 刪除冗餘的 post-`goto` settle（後接 `isVisible`/URL 斷言已自動等待者）：at-m11-cart-checkout、at-m15-e2e 的 ~21 處
**AC-003-2**: 將 post-action settle 改顯式等待（`waitForURL` / `waitForResponse` / `expect().toBeVisible()` / `toHaveClass()`）：at-m17-001/002、at-m11、at-m15 的 ~25 處；native alert 流程用 `waitForResponse`（不用 toBeVisible）
**AC-003-3**: **保留** `at-m10-chat.spec.ts:57`（STOMP SUBSCRIBE settle，無 client 可觀察訊號，移除會 flaky）——保留註解說明
**AC-003-4**: 僅 console.log 無斷言處，改顯式等待時**順帶補對應斷言**（提升測試意圖，Rule 9）
**AC-003-5**: `npx playwright test --list` 結構正確；`make validate-e2e` 綠、不退步

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | backend pre-commit 提速（AI-2302）| 2 | P1 |
| US-002 | 整月日曆每日價格顯示（AI-2202c Part A）| 2 | P2 |
| US-003 | E2E 硬等待清除（DEF-022）| 3 | P2 |
| **承諾合計** | | **7 SP** | |

> **Velocity 參考**：S37=10, S38=8, S39=10, S40=9, S41=12。**本 Sprint 7 SP**，收尾型 sprint 回歸健康偏保守區間（S41 清償量大後回穩）。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 AI-2302（@Tag slow 2 測試 → pre-commit/Makefile excludedGroups → 實測無 DB 綠 → 文件）
   ↓ 後端測試註解，mvn 驗證
US-002 AI-2202c Part A（MonthCalendar priceByDate + props → ListingDetail 傳入 → 顯示 → build/tsc/lint）
   ↓ 前端綠
US-003 DEF-022（刪冗餘 + 改顯式等待 + 補斷言 → playwright --list）
   ↓
make validate-e2e（一次驗證 US-002/003 前端 + 全棧不退步）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：US-001 加 @Tag 後**立即** `mvn test -Dtest=... -DexcludedGroups=slow` 確認排除生效且無 DB 可過；前端每檔完成 build/tsc/lint；US-003 後 validate-e2e。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| 排除慢測導致 pre-commit 漏測 | 2 測試 pre-push act（`mvn test`/`mvn verify` 無 excludedGroups）仍完整跑 → 上 GIT 前零覆蓋損失；已探勘確認 |
| @Tag 排除語法與 -Dtest 交互 | `-DexcludedGroups` 與 `-Dtest`（類別選擇）為獨立 JUnit5 filter，可組合；實測驗證 |
| 日曆格加價格破版 | cell `aspect-square` → 兩行版面調整；build 驗證；不可訂日維持既有樣式 |
| **DEF-022 移除 sleep 造成新 flaky** | 保留 STOMP 例外；post-action 一律改 `waitForResponse`/`waitForURL`（非裸刪）；native alert 流程用 waitForResponse；以 validate-e2e 實跑把關 |
| 本 Sprint 有後端（測試）變動 | push 需完整 validate-release；承 S41 push 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001（AI-2302）：2 測試 @Tag("slow") + pre-commit/Makefile excludedGroups；實測無 DB quick test 綠；CI 不動；LOCAL_CI 文件更新
- [ ] US-002（AI-2202c Part A）：日曆每日價格顯示 + basePrice fallback；build/tsc/lint 0 error
- [ ] US-003（DEF-022）：冗餘 waitForTimeout 刪除 + 可替換者改顯式等待 + 補斷言；保留 STOMP 例外
- [ ] `make validate-e2e` 綠、既有不退步；無 production code/schema/migration 變動
- [ ] Sprint 42 Review / Retro / Release Notes + trackers 更新
- [ ]（檢查點）承 S41 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| pre-commit 提速 | `backend/src/test/.../ReviewServiceCacheIntegrationTest.java`、`SellerDashboardServiceCacheTest.java`（@Tag）、`backend/hooks/pre-commit`、`Makefile`、`docs/08_deployment/LOCAL_CI_VALIDATION.md` |
| 日曆價格 | `frontend/src/components/storefront/MonthCalendar.tsx`、`ListingDetail.tsx`、`at-room-booking.spec.ts` |
| E2E 硬等待 | `frontend/e2e/at-m17-001.spec.ts`、`at-m17-002.spec.ts`、`at-m11-cart-checkout.spec.ts`、`at-m15-e2e.spec.ts`（at-m10-chat 保留 1 處）|
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers（`docs/04_planning/`）|

---

## 8. 🔴 待使用者確認點

1. **範圍**：US-001 AI-2302 pre-commit 提速（含移除 DB 依賴）+ US-002 AI-2202c Part A 日曆價格（純前端）+ US-003 DEF-022 E2E 硬等待清除 = 7 SP。是否核准?
2. **AI-2202c Part B 另立**：「未開放 vs 可訂」需後端新語意，另立 AI-2202d 非本 Sprint。是否同意?
3. **DEF-022 深度**：清除全部可替換 waitForTimeout（保留 STOMP 例外），並順帶補斷言。是否同意此深度?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
