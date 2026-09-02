# Sprint 109 Plan — M17 開店流程三段斷鏈一次修通（DEF-061 / DEF-060 / DEF-058）

**Sprint**: Sprint 109
**日期**: 2026-09-02
**AI 編號**: AI-2443
**主題**: 使用者於 Sprint 108 拍板 DEF-060 走 **(a) 前端審核台改接 `tenant-applications`**。執行過程中發現更上游的根因 **DEF-061**——開店申請根本送不出去。三段斷鏈（送不出去 → 看不到 → 測不到）本輪一次修通。

---

## 1. 使用者決策

Sprint 108 呈上兩條路，使用者選 **(a)**：前端審核台改接 S97 既有的 `tenant-applications` 端點，不推翻 S97 設計、不改回 PRD §340 的租戶生命週期。本輪據此執行。

---

## 2. 執行中發現的上游根因：DEF-061（開店申請送不出去）

準備讓 E2E 自建前提（買家送出申請）時，發現路徑對不上：

| 事實 | 值 |
|------|-----|
| `application.yml` | `server.servlet.context-path: /api` |
| `TenantController` | `@RequestMapping("/api/v2")` ← **自己又寫了一次 `/api`** |
| 其餘 40+ controller | `@RequestMapping("/v2/...")` |
| 前端 axios baseURL | `http://localhost:8080/api` |
| 前端呼叫 | `apiClient.post('/v2/tenants/apply')` → `/api/v2/tenants/apply` |

**實測（啟動真實 JAR + curl，非推理）**：

```
修復前：
  POST /api/v2/tenants/apply      -> 401   （與 /api/definitely/not/a/route 同一個回應）
  POST /api/api/v2/tenants/apply  -> 201   ← 端點實際住在這裡

修復後（完全反轉）：
  POST /api/v2/tenants/apply      -> 201 ✅
  POST /api/api/v2/tenants/apply  -> 401
```

> 對照組很重要：`401` 是 Spring Security 對**未知路徑**的回應，所以「401」本身不能證明端點不存在。真正決定性的是 `apply` 屬 permitAll（Guest 可申請），因此它若存在就該回 400/201 而非 401——加倍路徑回 201、前端路徑回 401，兩相對照才是證據。

**影響範圍**：`TenantController`（M17 開店申請／我的店鋪／成員邀請／Feature Toggle）與 `ErpController`（M16 ERP，`@RequestMapping("/api/v2/dashboard")`，前端 `api_erp.ts` 打 `/v2/dashboard/...`）。兩者的前端呼叫全部打不到後端。

**為什麼長期沒被發現**：`SecurityConfig` 第 89–90 行也用加倍路徑（`/api/v2/tenants/apply`），與 controller 自洽，所以後端內部沒有矛盾；而**所有既有測試都用 MockMvc，MockMvc 不套用 `server.servlet.context-path`**，於是測試沿用 `"/api/v2"` 當 base URL 一路全綠，真實部署卻打不到。這是本專案反覆出現的「**測試以固件／機制差異繞過了正在壞掉的那一段**」的又一實例（見 S103／S106／S108）。M17 的 E2E 長期在 skip，也是同一個根因的下游症狀。

**修法**（2 行生產碼 + 2 條 security 規則 + 5 個測試常數）：
- `TenantController`：`/api/v2` → `/v2`
- `ErpController`：`/api/v2/dashboard` → `/v2/dashboard`
- `SecurityConfig`：兩條 permitAll 規則同步（規則路徑同樣相對於 context path，不改會讓 Guest 申請變成 401）
- `TenantControllerE2ETest`／`TenantMemberInviteE2ETest`／`TenantApplicationReviewE2ETest`／`M16ErpE2ETest`／`M16ErpIntegrationTest` 的 base URL 常數

兩支 controller 都留下註解說明「不可再加 `/api`」與實測數據。

### 防回歸：`ControllerRequestMappingConventionTest`（新增）

既有測試層級抓不到這類缺陷（MockMvc 不套 context-path），所以補一個**掃描 class-level `@RequestMapping`** 的守衛：任何以 `/api` 開頭的 class mapping 即失敗。

**已驗證守衛本身有效**（而非恆綠）：暫時把 `TenantController` 改回 `/api/v2`，測試如實紅燈——
```
Expecting empty but was: ["TenantController -> "/api/v2""]
```
另加一個下界斷言（掃描到的 controller 數 > 20），避免 package 改名後「掃不到東西而空過」——那正是本專案屢次踩到的失效模式。

---

## 3. DEF-060（修）：前端審核台改接開店申請

**`/admin/tenants`**：「審核中」分頁改查 `GET /v2/admin/tenant-applications`（原本查 `/v2/admin/tenants?status=PENDING_REVIEW`，生產環境恆空），分頁計數同步改為申請數，並提供**行內核准／駁回**（駁回需填原因）。

刻意**不新增後端詳情端點、也不另開審核詳情頁**：`TenantApplicationSummaryResponse` 已含店名／描述／經營類型／聯絡方式／送出時間，資訊比原本的租戶審核頁還完整；再開一頁就得為「依 applicationId 取單筆」新增端點，違反 Rule 2。

**兩個刻意的實作選擇**：
- 成功／失敗訊息改為**畫面內元素**（`data-testid="action-message"`），不用原生 `alert()`——`alert()` 會讓 Playwright teardown 崩潰（既有 flaky 的已知成因），且畫面內訊息才可被斷言。
- 關鍵元素一律加 `data-testid`。S107 的教訓：`:has-text("核准")` 是子字串比對，會命中「已核准 (N)」tab。

**`/admin/tenants/[id]/review`**：移除核准／駁回按鈕與對應 handler，收斂為唯讀的店鋪詳情頁，並在原處留下註解說明「審核對象是申請不是租戶、請勿把按鈕加回來」。留著一組永遠會拋 `E_2005` 的按鈕，正是 DEF-060 的缺陷本體。

---

## 4. DEF-058（修）：`at-m17-002` 終於能自建前提

DEF-061 + DEF-060 修好後，前提可以由測試自己建立——**買家送申請走的就是前端走的那條路**。

改寫後：註冊新買家 → 以其 token `POST /api/v2/tenants/apply` → admin 登入 → 切「審核中」→ 依**本次專屬店名**定位自己那張卡（不用 `.first()`，避免併行干擾）→ 核准／駁回 → 斷言 API 200 + 訊息含店名 + 該卡從清單消失。

**本 spec 不再有任何 `test.skip()`**。原本的三態擺盪（S105 記 54/6、S106 記 53/7、S107 首跑 1 failed）到此結束：每次執行都真的在測，前提不成立就該紅燈。

---

## 5. 測試變更

| 測試 | 類型 | 說明 |
|------|------|------|
| `ControllerRequestMappingConventionTest`（新增 2 案例） | 單元 | 路徑慣例守衛 + 守衛有效性下界。已實測會對 DEF-061 紅燈 |
| `at-m17-002.spec.ts`（改寫 2 案例） | E2E | 自建前提、移除全部 `test.skip()`、改用 `data-testid` |
| 5 個既有測試類別的 base URL 常數 | 整合／E2E | 隨 DEF-061 修正路徑（67 tests 全過） |

---

## 6. 範圍外（未動）

- **舊的 `Tenant` 審核端點**（`reviewTenant`／`approveTenant`／`rejectTenant`）：S97 已知情且刻意保留，前端不再引用後它們成為純後端死碼。本輪不刪除（移除 PRD 標為 P0 的端點是產品決策，非工程決策），僅在前端留註解說明審核入口不在那裡。
- **PRD §340 的租戶生命週期圖**（`[申請開店] → PENDING_REVIEW → [Admin 審核] → ACTIVE`）與實作仍分歧：使用者選 (a) 等於確認實作為準，PRD 該段落已過時。未在本輪改 PRD。

---

## 7. 方法論教訓

1. **「同一個機制的另一個實例」可以推廣，「同一個樣式的另一個位置」不行。** 本輪對 `TenantController` 有直接實測（201/401 反轉）；`ErpController` 是同一個機制（context-path + 自帶 `/api`）的另一個實例，故可據此推論。但當時無法取得 ERP 的授權探測證據，計畫書就如實標明兩者的證據強度不同，而不是含混地一起宣稱「已驗證」。
2. **測試全綠不等於功能可用**：五個測試類別長期用 `/api/v2` 全綠，正因為 MockMvc 不套 context-path——測試與生產走的不是同一條路。**當測試與真實部署在某個維度上不同（context path、交易註解、樂觀鎖），那個維度上的缺陷就是測試的盲區**（S106 是交易註解、S103 是樂觀鎖、本輪是 context path）。
3. **修完要證明守衛會失敗**：新增的慣例守衛先還原缺陷跑出紅燈，才算數。
