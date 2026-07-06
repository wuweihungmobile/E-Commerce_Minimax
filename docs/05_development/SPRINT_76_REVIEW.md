# Sprint 76 Review / Sprint 76 評審會議

> **Sprint 編號**: Sprint 76
> **期間**: 2026-07-06
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: `LogisticsService` 範圍探查 + 除 `createLogistics` 外其餘 6 個方法的租戶擁有權檢查修復（`DEF-036`，新發現）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `LogisticsService`（+`ShippingTemplateService`）範圍探查 | 1 | ✅ 完成 |
| US-002 | 修復 `LogisticsService` 租戶擁有權檢查缺口（`DEF-036`） | 3 | ✅ 完成 |

**4 SP 全數完成**。使用者於指派任務時特別提醒「`checkOrderTenant` 已存在不代表全部方法都安全，不要假設」，本 Sprint 逐一審視 `LogisticsService` 全部 7 個 public 方法後證實此提醒完全命中：`createLogistics`（`DEF-019`，Sprint 36 已修復）是唯一有租戶擁有權檢查的方法，其餘 6 個方法（含 2 個寫入操作）皆無檢查，已全數修復並補齊測試。附帶檢查 `ShippingTemplateService` 時另發現 1 項低風險缺口（`DEF-037`），使用者審閱後決策擱置僅記錄。

---

## 2. 交付內容

### 探查結論（US-001）

- **`LogisticsService`（`core/logistics/LogisticsService.java`，329 行）7 個 public 方法**：`createLogistics` 已有 `DEF-019` 的 `checkOrderTenant` 擁有權檢查與對應單元測試（`LogisticsServiceOwnershipTest`）；`getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus`/`cancelLogistics` 皆直接 `findById`/`findByOrderId` 後即讀取或寫入，**完全沒有租戶擁有權檢查**。`LogisticsController` 僅以 `hasAuthority('order:read')`/`'order:update'` 把關，此權限分散在各租戶角色上，任一租戶皆可跨租戶讀取物流單詳情/追蹤歷史，或竄改其物流狀態（`updateLogisticsStatus`/`cancelLogistics` 屬寫入操作，`DELIVERED` 狀態變更還會連動竄改他租戶訂單狀態為 `DELIVERED`）。與 `DEF-019`/`DEF-024`/`DEF-028`/`DEF-032` 同一 tenant-based IDOR 模式。
- **`ShippingTemplateService`（`core/logistics/ShippingTemplateService.java`，139 行）6 個 public 方法**：`createTemplate`/`getTemplates`/`updateTemplate`/`deleteTemplate` 皆已由 Controller 傳入 `TenantContext` 解析的 `tenantId` 並正確過濾；僅 `calculateFee(templateId, orderAmount)` 未驗證 `templateId` 租戶歸屬（`DEF-037`，新發現，記錄後使用者決策擱置）。

### 新發現並修復：`DEF-036`（`LogisticsService` 除 `createLogistics` 外全數缺口）

- **問題**：6 個方法皆無租戶擁有權檢查，屬跨租戶 IDOR；其中 `updateLogisticsStatus`/`cancelLogistics` 為寫入操作，風險更高（可竄改他租戶物流/訂單狀態）。
- **紅燈證明**：新增 `LogisticsServiceTenantAccessTest.java`（新檔，8 個測試）。以 `git stash` 暫時將 `LogisticsService.java` 還原至修復前版本執行，5 個「預期拋 `E_1007`」的跨租戶案例（`getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus`）**全數因未拋出例外而失敗**，另 3 個「應成功」案例因 `orderRepository` stub 未被呼叫觸發 `UnnecessaryStubbingException`，兩者皆證實漏洞成立且測試 fixture 接線正確。
- **修復**：新增 `checkLogisticsTenant(Logistics)` helper（依 `logistics.getOrderId()` 反查 `Order` 後委派既有 `checkOrderTenant`），6 個方法皆於狀態/業務邏輯檢查**之前**呼叫（IDOR 正確順序，避免向未授權者洩漏物流/訂單狀態）；`getLogisticsByOrderId` 因無 `Logistics` 實體可查，改為先 `orderRepository.findById(orderId)` 取得 `Order` 後直接呼叫 `checkOrderTenant`；`checkOrderTenant` 的例外訊息由「create」改為通用措辭以反映其現已被多方法共用。
- **轉綠**：`git stash pop` 復原修復後，`LogisticsServiceTenantAccessTest` 8 tests 0 fail。
- **既有測試同步更新**：`LogisticsServiceCancelTest`（`DEF-011` 錯誤碼測試）因 `cancelLogistics` 新增擁有權檢查，`cancel_delivered_throwsE7502`/`cancel_inTransit_setsReturned` 兩案例補上 `orderRepository.findById`/`TenantContext.setCurrentTenant` 的本租戶 fixture 後恢復通過，3 tests 0 fail。

### 附帶記錄：`DEF-037`（`ShippingTemplateService.calculateFee` 跨租戶查詢，已決策擱置）

- `calculateFee` 未驗證 `templateId` 是否屬於當前租戶，任一登入使用者可查得他租戶的 `feeType`/`fixedAmount`/`freeThreshold`。無 PII、無寫入風險，且是否應收斂涉及「買家跨租戶比價試算」既有使用情境的業務判斷，記入 `DEFERRED_ITEMS_TRACKER.md` 後**使用者已確認擱置，不修復**。

### 文件

- **`SPRINT_76_PLAN.md`**（新檔）：本 Sprint 計劃，含前置範圍探查、既有測試覆蓋現況、US-001/002 完整 AC。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-036`（已完成，記入「已完成延後項目」）+ `DEF-037`（記入「中優先級」，已決策擱置）。
- **`RELEASE_NOTES_v2028.09.23-01.md`**（新檔）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ 完成修復 → `git stash` 還原至修復前執行紅燈測試確認失敗 → `git stash pop` 復原修復 → 執行轉綠測試確認通過 → 同步更新既有 `LogisticsServiceCancelTest` fixture → 重跑確認 |
| `LogisticsServiceTenantAccessTest` 修復前（紅燈階段） | 🔴 8 tests，5 Failures（跨租戶案例「expected code to raise a throwable」，正確反映「修復前完全未攔截」的漏洞本質）+ 3 Errors（`UnnecessaryStubbingException`，因程式碼當時不查 `orderRepository`） |
| `LogisticsServiceTenantAccessTest` 修復後 | ✅ 8 tests，0 fail |
| `LogisticsServiceCancelTest` 修復後 | ✅ 3 tests，0 fail（`DEF-011` 錯誤碼行為不變，新增擁有權 fixture 後恢復通過） |
| 後端單元回歸（`mvn verify -Pintegration-test` 單元階段） | ✅ **749 tests，0 fail** |
| 全量回歸（`mvn verify -Pintegration-test`） | ✅ **BUILD SUCCESS**：單元 749 + 整合（failsafe）342 = **1091 tests，0 fail** |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更），backend 啟動成功、entity 與 Flyway schema 對齊 |
| 驗證方式選擇 | 依全量回歸頻率政策：本 Sprint 修改生產程式碼（`LogisticsService.java`），故執行全量 `mvn verify -Pintegration-test`，非僅 `mvn test` |

---

## 4. 誠實揭露（Rule 12）

1. **`DEF-036` 存在了 40 個 Sprint 才被發現**：`DEF-019`（`createLogistics` 擁有權修復）於 Sprint 36 完成，但同一檔案其餘 6 個方法的缺口一直到 Sprint 76 才被發現——證明「單一方法已修復」不能作為「同檔案其餘方法安全」的依據，這正是本 Sprint 任務指派時使用者特別提醒的風險，探查後完全命中。
2. **`DEF-037` 記錄後未修復，非遺漏**：探查過程中發現 `ShippingTemplateService.calculateFee` 亦缺租戶過濾，但因其風險屬性（無 PII、無寫入）與 `LogisticsService` 的缺口（可讀取完整物流/收件人資訊、可竄改訂單狀態）明顯不同，且是否應限制涉及既有買家比價情境的業務判斷，依規範先記錄不擅自修改，經使用者確認後正式決策擱置。
3. **`checkOrderTenant` 訊息措辭調整**：原訊息「Not authorized to create logistics for this order」僅適用 `createLogistics`，本 Sprint 讓 6 個方法共用同一 helper 後，訊息改為通用的「Not authorized to access logistics for this order」，屬必要的精準調整（非無關重構）。
4. **未變更既有的 `E_7000` 錯誤碼使用**：探查中觀察到 `getLogistics`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus` 於「物流不存在」情境仍沿用 `E_7000`（其官方定義為「找不到供應商」，非物流），與 `DEF-011`（`cancelLogistics` 已修正為 `E_7500`）同一類錯誤碼誤用問題，但屬語意正確性而非安全缺口，非本 Sprint 鎖定的擁有權/租戶檢查範圍，故未列為 `DEF` 追蹤，僅在此誠實記錄觀察，供後續 Sprint 參考。

---

## 5. Demo 重點

- **驗證「已修復方法≠同檔案其餘方法安全」的假設**：延續 Sprint 68/70/72/73/74/75 建立的主動審視方法，本 Sprint 特別驗證使用者提醒的風險——同一 Service 內部分方法已有防護不代表全部安全，逐一列出全部 7 個 public 方法後精準定位缺口範圍。
- **紅燈測試透過暫時還原程式碼證明修復必要性**：不同於直接假設修復方向正確，本 Sprint 用 `git stash` 將生產程式碼暫時還原至修復前版本，實際執行新測試觀察失敗型態（跨租戶案例確實未被攔截），確保紅燈是「真紅」而非測試本身寫錯。
- **修復範圍精準，抵抗「順便修正 E_7000 誤用」的誘惑**：探查中發現的 `E_7000` 錯誤碼語意問題與本 Sprint 主題（擁有權）無關，明確選擇僅記錄觀察、不擴大修改範圍，符合 Rule 3。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
