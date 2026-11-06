# 測試策略準則 / Testing Strategy Guidelines

> **版本**: v1.0
> **建立日期**: 2026-04-26
> **適用**: Sprint 3-A 補漏項目
> **目的**: 避免未來測試缺口，記錄 IT vs E2E 測試適用場景

---

## 1. 背景說明

在 Sprint 3 QA 驗證過程中，發現以下問題：

1. **AdminControllerE2ETest 環境問題**: 7 個測試因環境問題失敗
2. **RBAC 測試缺口**: StoreOwner/STORE_STAFF 角色 RBAC 測試不足
3. **IT 測試限制**: @WithMockUser 在 MockMvc 環境有已知限制

這些問題的根本原因：
- RegisterRequest.userType 不支援 "ADMIN"
- JWT authorities 無法在 E2E 測試中即時更新
- IT 環境無法完全模擬 Spring Security filter chain

---

## 2. IT vs E2E 測試適用場景

### 2.1 測試環境分類

| 環境 | 框架 | 適用場景 | 限制 |
|------|------|----------|------|
| IT (整合測試) | SpringBootTest + MockMvc | Business logic, Repository, Service 層測試 | 無法完整模擬 Security filter chain |
| E2E (端到端測試) | REST Assured + MockMvc | HTTP 層, Security, RBAC, JWT | 環境配置複雜 |

### 2.2 適用場景對照表

| 場景 | IT 測試 | E2E 測試 | 備註 |
|------|---------|----------|------|
| 基本 CRUD | ✅ 適用 | ✅ 適用 | IT 可驗證核心邏輯 |
| JWT 認證流程 | ❌ 不適用 | ✅ 適用 | IT 無法完整模擬 JWT filter |
| RBAC 角色校驗 | ⚠️ 有限制 | ✅ 適用 | @WithMockUser 有已知限制 |
| 多租戶隔離 | ✅ 適用 | ✅ 適用 | IT 可驗證 TenantContext |
| Security filter chain | ❌ 不適用 | ✅ 適用 | IT MockMvc 配置與真實環境有差異 |
| 錯誤處理 (4xx) | ⚠️ 有限制 | ✅ 適用 | IT 可能返回 500 而非預期 4xx |
| API 端點驗證 | ✅ 適用 | ✅ 適用 | IT/E2E 皆可 |

### 2.3 決策樹：選擇 IT 還是 E2E？

```
測試場景
    │
    ├── 是否涉及 Security filter chain?
    │   ├── YES → 使用 E2E 測試
    │   └── NO
    │       ├── 是否涉及 JWT token?
    │       │   ├── YES → 使用 E2E 測試
    │       │   └── NO
    │       │       ├── 是否需要完整的 HTTP 請求/響應?
    │       │       │   ├── YES → 使用 E2E 測試
    │       │       │   └── NO → 使用 IT 測試
    │       │       └── (其他 Business logic)
```

---

## 3. @WithMockUser 已知限制

### 3.1 問題描述

`@WithMockUser` 在 Spring Security 的 MockMvc 環境中：

1. **無法完全模擬真實 HTTP 請求的 Spring Security filter chain**
2. **可能返回 500 而非預期的 401/403**
3. **無法測試 JWT 解析邏輯**

### 3.2 具體問題

```java
// IT 測試中的問題
@Test
@WithMockUser(roles = "BUYER")
void testSomething() {
    mockMvc.perform(post("/api/v2/admin/tenants/123/approve"))
            .andExpect(status().isForbidden()); // 可能得到 500 而非 403
}
```

問題原因：
- MockMvc 的 SecurityMockMvcRequestPostProcessors 使用預設的 filter chain 配置
- 與真實 HTTP 請求的 Spring Security 配置有差異

### 3.3 解決方案

**方案 A（推薦）**: 使用 `JwtTokenService.generateAccessToken()` 直接產生測試 token

```java
// 直接產生 SUPER_ADMIN token，繞過 register → update role → re-login 的流程
String adminToken = jwtTokenService.generateAccessToken(
    adminUser.getId(),
    adminUser.getEmail(),
    "SUPER_ADMIN",  // 直接指定 role
    null
);

// 在 E2E 測試中使用
given()
    .header("Authorization", "Bearer " + adminToken)
    .when()
    .post("/api/v2/admin/tenants/123/approve")
    .then()
    .statusCode(200);
```

**方案 B**: 使用 Spring Security Test 的 SecurityMockMvcRequestPostProcessors

```java
// 使用 user() 而非 @WithMockUser
mockMvc.perform(post("/api/v2/admin/tenants/123/approve")
        .with(user(adminEmail).roles("SUPER_ADMIN")))
    .andExpect(status().isForbidden());
```

**方案 C**: 跳過 Security filter chain，僅測試 Business logic

```java
// IT 測試中，直接呼叫 Service 層方法，不經過 Controller
adminService.approveTenant(tenantId, request);
```

---

## 4. RBAC 測試策略

### 4.1 RBAC 測試矩陣

| 角色 | Admin API | Store API | Dashboard API | 備註 |
|------|-----------|-----------|---------------|------|
| SUPER_ADMIN | ✅ 全部允許 | ✅ 全部允許 | ✅ 全部允許 | 最高權限 |
| ADMIN | ✅ 全部允許 | ❌ 無權限 | ❌ 無權限 | 平台管理員 |
| BUYER | ❌ 全部 403 | ❌ 無權限 | ❌ 無權限 | 一般會員 |
| SELLER (StoreOwner) | ❌ 全部 403 | ✅ 自己的 | ✅ 自己的 | 店鋪擁有者 |
| SELLER (STORE_STAFF) | ❌ 全部 403 | ✅ 授權範圍 | ✅ 授權範圍 | 店鋪員工 |

### 4.2 RBAC E2E 測試要求

每個 Admin API 必須測試：
- ✅ SUPER_ADMIN 可以訪問
- ✅ ADMIN 可以訪問（如果適用）
- ❌ BUYER 不能訪問（403）
- ❌ SELLER (StoreOwner) 不能訪問（403）
- ❌ SELLER (STORE_STAFF) 不能訪問（403）

### 4.3 新增 RBAC 測試案例

基於 Sprint 3-A 補漏，新增以下測試：

| TC ID | 測試案例 | 角色 | 端點 | 預期結果 |
|-------|----------|------|------|----------|
| API-M17-018 | StoreOwner 呼叫 Admin approve | STORE_OWNER | POST /admin/tenants/:id/approve | 403 |
| API-M17-019 | STORE_STAFF 呼叫 Admin approve | STORE_STAFF | POST /admin/tenants/:id/approve | 403 |
| API-M17-020 | StoreOwner 呼叫 Admin reject | STORE_OWNER | POST /admin/tenants/:id/reject | 403 |
| API-M17-021 | STORE_STAFF 呼叫 Admin reject | STORE_STAFF | POST /admin/tenants/:id/reject | 403 |

---

## 5. SUPER_ADMIN 測試策略

### 5.1 問題

原始 E2E 測試使用 `register → update role → re-login` 流程建立 SUPER_ADMIN：

1. `POST /v2/auth/register` → userType 不支援 "ADMIN"
2. 即使修改 DB 中的 role，JWT authorities 不會自動更新
3. 需要完整的重新認證流程才能獲得新的 authorities

### 5.2 解決方案

使用 `JwtTokenService.generateAccessToken()` 直接產生測試 token：

```java
// 在 @BeforeEach 或 @BeforeAll 中建立測試用戶和 token
private String createSuperAdminToken() {
    User adminUser = User.builder()
            .email(uniqueEmail())
            .role(User.UserRole.SUPER_ADMIN)
            .build();
    adminUser = userRepository.save(adminUser);

    return jwtTokenService.generateAccessToken(
            adminUser.getId(),
            adminUser.getEmail(),
            "SUPER_ADMIN",
            null
    );
}
```

### 5.3 優點

1. **繞過 RegisterRequest.userType 限制**: 不需要經過 AuthService
2. **繞過 JWT authorities 同步問題**: 直接指定 role claim
3. **測試隔離**: 每個測試可以獨立產生自己的 token

---

## 6. 文件同步檢查清單

每次 Sprint 結束時，必須確認：

- [ ] TC_M17_Tenant.md 已更新 Sprint N 測試狀態
- [ ] SPRINT_N_PLAN.md 的測試數量與 TC 一致
- [ ] 所有新增的測試案例都記錄在 TC 中
- [ ] IT vs E2E 測試適用場景已確認
- [ ] RBAC 測試矩陣已更新

---

## 7. 預防未來遺漏的措施

### 7.1 測試規劃階段

1. 確認所有 RBAC 角色都有對應的 E2E 測試
2. 確認 Security filter chain 測試使用 E2E 環境
3. 確認 IT 測試僅用於 Business logic 驗證

### 7.2 測試執行階段

1. IT 測試失敗時，先確認是否涉及 Security filter chain
2. E2E 測試優先驗證 RBAC 和 JWT 邏輯
3. 使用 JwtTokenService 產生測試 token

### 7.3 文件更新階段

1. 更新 TC_M17_Tenant.md 同步測試狀態
2. 更新 SPRINT_N_PLAN.md 測試數量
3. 記錄發現的問題和解決方案

---

## 8. 前端 UI 測試原則（Sprint 329 補記，QA）

> 背景：Sprint 328 換皮改了 144 個檔案，其中 4 支 E2E 要改（同名連結、同 role 元素數量、**斷言 class 名**、頁面頂欄裡的功能連結）；`at-m15-e2e` 把原生 `alert()` 當同步訊號，並曾因原生對話框造成 Playwright session 崩潰（S37 AI-2001）。證據與完整檢視見 [SPRINT_329_PLAN.md](../04_planning/SPRINT_329_PLAN.md) §3.4。

### 8.1 三層守門（由便宜到貴）

| 層 | 工具 | 守什麼 | 位置 |
|---|---|---|---|
| 靜態 | ESLint `design-tokens/*`（5 條規則） | 色盤／陰影／hex、原生表單／表格元素、原生對話框、靜默失效的 class、已有元件的配方被手刻 | `frontend/eslint-rules/design-tokens.mjs`，`npm run lint`（pre-commit／CI／pre-push） |
| 契約（執行期，mock） | `at-ui-design-system.spec.ts` | 控制項實際幾何（computed style）、表格、標題、Toast、ConfirmDialog、Dialog 遮罩、換皮演練、狀態登錄表語意 | `frontend/e2e/`，全程 mock、不需後端 seed、不吃登入限流額度 |
| 整合（真實後端） | 各 `at-*-real.spec.ts` | 流程與資料 | `frontend/e2e/` |

### 8.2 E2E 選擇器與斷言原則

1. **斷言語意與實際樣式，不斷言 class 名**：選取態 → `aria-pressed`；刪除線 → `toHaveCSS('text-decoration-line', …)`；通知 → `getByTestId('toast')`＋`data-kind`；確認框 → `confirm-dialog-confirm`／`confirm-dialog-cancel`。換皮改 class 名時，這些都不該紅。
2. **不要用原生對話框當同步訊號**；全站已沒有原生 `alert()`／`confirm()`（`no-native-dialog` 守門），所以也不需要 `page.on('dialog')` 的全域 workaround。
3. **新增共用元素前先查同名／同 role 的泛用查詢**（`getByRole('link', { name })`、`getByRole('combobox')`、`locator('select')`…）：Playwright strict mode 遇到 2 個就失敗。目前 E2E 沒有 `getByRole('alert'|'status'|'dialog')` 查詢。
4. 破壞性操作要測「**取消與 ESC 不送出、確定才送出**」（E2E-UIDS-05）——Playwright 預設自動關閉原生 `confirm()`（回 `false`），所以原生版本的刪除流程在 E2E 裡根本走不到「確定」。
5. 契約測試要量**實際 computed style**（高度、圓角、框色、字級、內距），並用 `tokenColor()` 把 token 解析成 rgb 再比對，不要寫死色碼（換主題也成立）；量動畫會誤判，換皮演練時關掉 `transition`。
6. **機械式／大量重構之後，用 `git diff` 做「屬性丟失」審計**：列出 diff 中被移除、卻沒有在新增行重新出現的屬性（`data-testid`、`id`、`role`、`aria-*`、`htmlFor`、`name`、`placeholder`、`data-*`…）。把整個元素換成元件的轉換器最容易丟屬性——Sprint 329 的轉換器丟了 2 個 `data-testid`，`type-check`、`lint`、契約規格、探針全都沒抓到，是完整守門抓到的（`E2E-BLOGCAT-03` 失敗）。審計是便宜的，完整守門是 8 分鐘。

### 8.3 契約規格的突變驗證

契約規格自己也要證明「會紅」：預先登記突變（把某頁的 `<Input>` 換回手刻、改壞 `field-styles.ts`、把 Dialog 遮罩改回不透明、讓 ConfirmDialog 的取消回 `true`…），確認對應案例紅、不相干的改動維持綠。做法見記憶 `mutation-validation-driver-pattern`；本輪結果在 SPRINT_329_PLAN §7。

---

## 9. 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| Sprint 3-A 計劃 | `docs/04_planning/SPRINT_03-A_PLAN.md` | 補漏執行計劃 |
| TC M17 Tenant | `docs/03_testing/TC_M17_Tenant.md` | 測試案例文檔 |
| Sprint 3 QA 驗證報告 | `docs/03_testing/SPRINT_03_QA_VERIFICATION.md` | 發現問題的來源 |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-26
**負責人**: QA (Quincy)

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 |
|------|------|----------|
| v1.0 | 2026-04-26 | 初始版本（基於 Sprint 3-A 補漏項目） |
| v1.1 | 2026-04-26 | 更新 RBAC 測試案例 ID（API-M17-010~013 → API-M17-018~021）以避免 ID 衝突 |
| v1.2 | 2026-11-06 | Sprint 329：新增 §8「前端 UI 測試原則」（三層守門、E2E 選擇器與斷言原則、契約規格突變驗證）；原 §8 相關文件順延為 §9 |