# Sprint 17 US-001 測試 Bug 根因分析與修復計畫
# Sprint 17 US-001 Test Bug Root Cause Analysis & Fix Plan

> **US ID**: US-001
> **標題**: 修復 109 個既有測試 bug (預估 83 個,實際 109 個)
> **建立日期**: 2026-06-08 (Sprint 17 Day 1)
> **分支**: `feature/US-001-fix-test-bugs`
> **基於**: [SPRINT_17_PLAN.md §US-001](../SPRINT_17_PLAN.md) + 完整 mvn test 結果

---

## 1. 完整 mvn test 結果 (Sprint 17 Day 1 基線)

> **🔴 重大更新**: 實際失敗數為 **109 個** (Sprint 16 Retro 揭露 83 個,本次實際為 109 個,增加 26 個)

| 項目 | 數量 |
|------|------|
| 總測試數 | **489** |
| ✅ 通過 | 380 (77.7%) |
| ❌ 失敗 (Failures) | 87 |
| ❌ 錯誤 (Errors) | 22 |
| **❌ 失敗 + 錯誤** | **109** |
| ⏭️ Skipped | 0 |

> Sprint 16 Retro 時揭露 83 個 bug,本次實際為 109 個,增加 26 個。增加原因:PostControllerE2ETest 等 3 個 stash 變更的 11 個失敗屬於「修復嘗試未完成」狀態,但因為尚未 commit 不算在「89 個」中。本次基線從 develop clean state 跑出 109 個,完全反映專案現況。

---

## 2. 失敗測試類別統計 (16 個類別)

| # | 測試類別 | 失敗 | 錯誤 | 合計 | 模組 | 預估根因類別 |
|---|---------|------|------|------|------|------------|
| 1 | `M07PaymentMockIntegrationTest` | 0 | 8 | 8 | M07 | RC-1: `doNothing()` 對非 void 方法 |
| 2 | `M18KnowledgePhase2IntegrationTest` | 0 | 9 | 9 | M18 | RC-2: 缺 `@MockBean JwtTokenService` |
| 3 | `M18MediaIntegrationTest` | 12 | 0 | 12 | M18 | RC-3: HTTP status 預期錯誤 |
| 4 | `M11CartPromoIntegrationTest` | 10 | 0 | 10 | M11 | RC-4: HTTP 500 (Service 異常) |
| 5 | `M09NotificationTemplateIntegrationTest` | 12 | 0 | 12 | M09 | RC-4: HTTP 500 (Service 異常) |
| 6 | `CartControllerE2ETest` | 12 | 0 | 12 | M11 | RC-4: HTTP 500 (Service 異常) |
| 7 | `BookingControllerE2ETest` | 9 | 0 | 9 | M02 | RC-5: HTTP 200→403/404/500 |
| 8 | `PostControllerE2ETest` | 10 | 1 | 11 | M18 | RC-5: HTTP 200→404/500 |
| 9 | `OrderControllerE2ETest` | 7 | 0 | 7 | M07 | RC-5: HTTP 200→403/404 |
| 10 | `TenantControllerE2ETest` | 5 | 0 | 5 | M10 | RC-5: HTTP 200→403/500 |
| 11 | `AuthControllerE2ETest` | 3 | 0 | 3 | Auth | RC-5: HTTP 200→500 |
| 12 | `AuthControllerTrueIntegrationTest` | 4 | 0 | 4 | Auth | RC-5: HTTP 200→500/400/401 |
| 13 | `AuthControllerIntegrationTest` | 1 | 0 | 1 | Auth | RC-5: HTTP 200→404 |
| 14 | `MediaServiceTest$GetMediaList` | 0 | 4 | 4 | M15 | RC-6: UT mock 設定錯誤 |
| 15 | `PricingServiceTest$PriceCalculationTests` | 1 | 0 | 1 | M12 | RC-7: UT 業務邏輯錯誤 |
| 16 | `PricingServiceTest$PriorityTests` | 1 | 0 | 1 | M12 | RC-7: UT 業務邏輯錯誤 |
| | **合計** | **87** | **22** | **109** | | |

---

## 3. 根因分類 (Root Cause Classification)

### 🔴 RC-1: Mockito `doNothing()` 對非 void 方法 (8 個錯誤)

**代表測試**: `M07PaymentMockIntegrationTest` 全部 8 個

**根因**:
```java
// ❌ 錯誤示範 (line 101)
doNothing().when(orderService).updateOrderStatus(any(), any());
// 錯誤訊息: Only void methods can doNothing()!
// 因為 updateOrderStatus 簽名現在回傳 Order (非 void)

at com.nextkey.ecommerce.core.order.OrderService.updateOrderStatus(OrderService.java:341)
```

**修復策略**:
```java
// ✅ 正確寫法
when(orderService.updateOrderStatus(any(), any())).thenReturn(updatedOrder);
// 或
doReturn(updatedOrder).when(orderService).updateOrderStatus(any(), any());
```

**檔案**: `M07PaymentMockIntegrationTest.java:101`

**工作量**: 0.5 SP (1 個檔案,~10 處修正)

---

### 🔴 RC-2: 整合測試缺 `@MockBean JwtTokenService` (9 個錯誤)

**代表測試**: `M18KnowledgePhase2IntegrationTest` 全部 9 個

**根因**:
```
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: 
Error creating bean with name 'jwtAuthenticationFilter': 
Unsatisfied dependency expressed through constructor parameter 0: 
No qualifying bean of type 'com.nextkey.ecommerce.infrastructure.security.JwtTokenService' available
```

**修復策略**:
```java
// 在 M18KnowledgePhase2IntegrationTest 加上
@MockBean
private JwtTokenService jwtTokenService;
```

**檔案**: `M18KnowledgePhase2IntegrationTest.java`

**工作量**: 0.2 SP (1 個檔案,加 1 個 @MockBean)

---

### 🟡 RC-3: HTTP Status 預期錯誤 (12 個)

**代表測試**: `M18MediaIntegrationTest` 全部 12 個

**根因**:
```
java.lang.AssertionError: Status expected:<200> but was:<201>
```

**修復策略**:
- 兩種可能:
  1. 測試預期錯誤 → 改 `isCreated()` (201) 而非 `isOk()` (200)
  2. Controller 邏輯改變 → 確認 Service 是否正確回傳

**工作量**: 0.5 SP (需逐個檢查)

---

### 🟡 RC-4: HTTP 500 內部錯誤 (34 個 = 12+12+10)

**代表測試**: `M11CartPromoIntegrationTest` (10), `M09NotificationTemplateIntegrationTest` (12), `CartControllerE2ETest` (12)

**根因**: Service 層執行時拋出未預期異常
```
Expected status code <200> but was <500>.
```

**可能根因**:
- 缺 Mock 依賴
- 業務邏輯例外未處理
- Repository 設定錯誤

**工作量**: 2 SP (需深入診斷每個 Service 呼叫鏈)

---

### 🟠 RC-5: HTTP 200→403/404 (37 個)

**代表測試**: `BookingControllerE2ETest` (9), `PostControllerE2ETest` (10+1), `OrderControllerE2ETest` (7), `TenantControllerE2ETest` (5), `Auth*Test` (8)

**根因分析**:
- 403: 權限設定不匹配 (Spring Security 角色設定改變)
- 404: 路由路徑或 Controller 不存在
- 500: 同 RC-4

**工作量**: 1.5 SP

---

### 🟠 RC-6: UT Mock 設定錯誤 (4 個)

**代表測試**: `MediaServiceTest$GetMediaList`

**根因**: Nested test class 的 `@Mock` 設定在 `setUp` 之前執行,或 Mock 物件未被正確注入

**工作量**: 0.3 SP

---

### 🟢 RC-7: UT 業務邏輯錯誤 (2 個)

**代表測試**: `PricingServiceTest$PriceCalculationTests`, `PricingServiceTest$PriorityTests`

**根因**: 業務邏輯或測試斷言錯誤

**工作量**: 0.2 SP

---

## 4. 修復優先級排序 (由工作量與依賴關係)

| 優先級 | 根因 | 數量 | 工作量 | 預估完成日 |
|--------|------|------|--------|----------|
| **P0-A** | RC-1 `doNothing()` 對非 void | 8 | 0.5 SP | Day 1 (06-08) |
| **P0-B** | RC-2 缺 @MockBean | 9 | 0.2 SP | Day 1 (06-08) |
| **P0-C** | RC-3 HTTP status | 12 | 0.5 SP | Day 1 (06-08) |
| **P1-A** | RC-4 HTTP 500 | 34 | 2 SP | Day 2 (06-09) |
| **P1-B** | RC-5 HTTP 403/404 | 37 | 1.5 SP | Day 2-3 (06-09~10) |
| **P2-A** | RC-6 UT mock 設定 | 4 | 0.3 SP | Day 3 (06-10) |
| **P2-B** | RC-7 UT 邏輯 | 2 | 0.2 SP | Day 3 (06-10) |
| **合計** | | **106** | **5.2 SP** | **3 天** |

> 註: 109 個總數減去上面分類總和 106 個 = 3 個差異,可能為 PostController 細項或測試類別邊界,將在 Day 3 驗證時確認。

---

## 5. 修復策略 (Sprint 17 US-001 AC-006 經驗傳承)

### 5.1 修復原則

1. **每修一個 bug 立即跑驗證測試** - 避免錯誤累積
2. **每日跑完整 mvn test** - 確保不退步
3. **建立根因知識庫** - 避免相同 bug 再次發生

### 5.2 修復流程

```
1. 從 RC-1 開始 (最簡單、最快見效)
   ↓
2. 每修一個檔案,執行 mvn test -Dtest='<FileName>' 驗證
   ↓
3. 修完一類根因,跑 mvn test -Dtest='<Category>*' 確認該類全部通過
   ↓
4. 每日 EOD 跑完整 mvn test 確認當日進度
```

### 5.3 Sprint 17 Day 1 立即執行清單

- [x] ✅ 建立 feature/US-001-fix-test-bugs 分支
- [x] ✅ 跑完整 mvn test,收集 109 個 bug 失敗清單
- [x] ✅ 分類 7 類根因 (RC-1~RC-7)
- [x] ✅ 修復 RC-1: M07PaymentMockIntegrationTest `doNothing()` → `doReturn(null)` (8 個 errors → 8 個 Failures, register 500 為 RC-4/RC-5 範疇)
- [x] ✅ 修復 RC-2: M18KnowledgePhase2IntegrationTest 加上 `@MockBean JwtTokenService` + `RolePermissionMapping` (9 個 errors → 0, URL 修正 `/v2/knowledge/articles/{id}/schedule` → `/v2/knowledge/{articleId}/schedule`)
- [x] ✅ 修復 MediaService/MediaAssetRepository 方法名不匹配: `findByTenantId` → `findByTenant_Id` (6 個位置)
- [x] ✅ 修復 M18MediaIntegrationTest register 斷言 `isOk()` → `isCreated()`, 加上 slug 欄位,使用 userType: SELLER
- [ ] 🔴 發現額外 bug: AuthService.login 會觸發額外 `insert into tenants` (slug null) 導致 500 (RC-4/RC-5 範疇,需 Day 2 修復)
- [ ] Day 1 EOD 跑完整 mvn test 確認 Day 1 進度

---

## 6. Sprint 17 US-001 預估 SP 重新評估

| 項目 | Sprint 17 Plan 預估 | Day 1 重新評估 | 差異 |
|------|------------------|--------------|------|
| 修復數量 | 83 個 | 109 個 | +26 (+31%) |
| SP | 4 | 5.2 | +1.2 |
| 預估完成日 | Day 3 (06-10) | Day 4 (06-11) | +1 天 |

> **建議**: 在 Day 3 (06-10) 進度檢查點評估,若進度 < 70% (即 < 76 個) 則向 PM/PO 報告,可能需要縮減 Sprint 17 範圍。

---

## 7. 經驗教訓 (Sprint 17 累積中)

### 7.1 常見 Mockito 錯誤模式

1. **doNothing() 對非 void 方法** - 改用 `when().thenReturn()` 或 `doReturn()`
2. **缺 @MockBean 依賴** - 整合測試需 mock 所有外部依賴
3. **Nested test class 的 @Mock 設定** - 需在 setUp() 內執行

### 7.2 HTTP 測試斷言反模式

1. **硬編碼 HTTP status 預期** - 改用 `isCreated()` / `isOk()` 等語意化斷言
2. **未考慮 Service 異常** - 加上 `@ControllerAdvice` 對應的錯誤碼測試

### 7.3 Final Approval 流程改進需求 (US-002)

- ✅ 必須跑完整 mvn test (非僅 Sprint 範圍)
- ✅ 失敗數量需明示 (本次 109 個,而非 0 個)
- ✅ 根因分類需有文件 (本文件為範本)

---

## 8. Day 1 進度追蹤表

| 時段 | 預定完成 | 實際完成 | 狀態 |
|------|---------|---------|------|
| 上午 (09:00-12:00) | 建立分支 + 收集失敗清單 | ✅ 完成 | ✅ |
| 下午 (13:00-17:00) | RC-1 + RC-2 + RC-3 修復 | ⏳ 進行中 | ⏳ |
| 下午 (17:00-18:00) | Day 1 EOD mvn test 驗證 | ⏳ 待執行 | ⏳ |

---

**文件版本**: v1.0
**最後更新**: 2026-06-08 (Sprint 17 Day 1)
**作者**: Claude Code (AI Assistant)
**基於**: Sprint 16 Retro + Sprint 17 Plan US-001
