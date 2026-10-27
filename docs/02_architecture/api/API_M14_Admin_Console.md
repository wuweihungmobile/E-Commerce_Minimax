# API 規格文件 - M14 平台總管理台 / API Specification - M14 Platform Admin Console

> **文件編號**: API-M14
> **文件版本**: v1.1（Sprint 307：DEF-422(a)——`activeTenants` 改為資料庫端計數，§3.2、§7 同步）；v1.0（Sprint 276，首次建立；首次有獨立規格文件，DEF-286 範圍）
> **建立日期**: 2026-10-16
> **最後更新**: 2026-10-27
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`AdminController`（`api/controller/`）、`AdminService`（`core/admin/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[AdminServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/admin/AdminServiceTest.java)、[AdminServiceIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/AdminServiceIntegrationTest.java)、[AdminControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/AdminControllerE2ETest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **FRD 狀態**：FRD（`E-Commerce_FRD_v1.0.md`）完全無 M14 章節，僅 PRD §4（Operations）/§9.9 提及
> **⚠️ 本文件撰寫範圍說明**：`AdminController`（`/v2/admin/*`）同時混雜 M17（租戶/開店申請/功能開關）與 M16（採購單審批）端點。**M17 範圍的 14 個端點已在 [API_M17_Tenant.md](./API_M17_Tenant.md) §4.17~4.30 完整記載**（含 `GET /admin/tenants/{tenantId}/stats`——雖然 Controller 原始碼註解寫「US-005 M14」，但 M17 文件已先行認領並記載，本文件不重複）；**M16 範圍的 3 個採購單審批端點記載於 [API_M16_ERP.md](./API_M16_ERP.md)**。本文件僅收錄不屬於 M17／M16 的剩餘 6 個端點：使用者管理（2）、平台統計（1）、系統設定（1）、稽核日誌（1）、維護警示（1）

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M14 |
| **模組名稱** | 平台總管理台 / Platform Admin Console |
| **描述** | SUPER_ADMIN 專用：跨租戶使用者管理、平台統計、系統設定查詢、稽核日誌查詢、維護警示列表 |
| **API 前綴** | `/api/v2/admin/...`（與 M17／M16 共用 `AdminController`，見文件頂部範圍說明） |
| **多租戶隔離** | 本模組 6 個端點**皆跨租戶**（平台層級），無租戶範圍限制，存取邊界完全靠 `@PreAuthorize("hasRole('SUPER_ADMIN')")` |

### 1.2 設計重點

1. **`AdminController` 無法被 `ApiRouteDocDriftTest` 機械防漂移保護**：該類別混雜 M14／M16／M17 三個模組的端點，守門機制要求「一個 Controller 的全部路由只能歸屬一份文件」，三份文件各自只收錄自己範圍的端點，整個類別因此不納入任何 `GUARDED` 登記（比照 `API_M17_Tenant.md` §7 既有的處理方式）。本文件 §4 的端點規格刻意不使用會被機械掃描到的宣告格式，詳見 §7。
2. **使用者狀態是自由字串，無列舉驗證**：`PUT /users/{userId}/status` 的 `status` 查詢參數直接寫入 `User.status`（純 `String` 欄位，非 `@Enumerated`），**不驗證合法值**。全庫唯一讀取此欄位的邏輯是 `AuthService` 登入檢查 `"ACTIVE".equals(user.getStatus())`——所以任何非 `"ACTIVE"` 的字串（包括打錯字）效果上都等同封鎖登入，無法透過這個欄位區分「停權」「停用」「打錯字」等不同語意。
3. **平台統計皆為真實資料**：`totalTenants`／`activeTenants`／`totalUsers`／`totalListings`／`totalOrders`／`pendingTenantReviews`／`totalPlatformGMV` 皆查詢真實資料庫；`totalPlatformGMV` 原為 `訂單總數 × 固定單價 1500` 的假數字，已於 Sprint 280（DEF-384）改為真實加總 `totalAmount`，見 §7。
4. **系統設定端點完全是靜態硬編碼**：`GET /system-config` 回傳的 4 個鍵值與 `platformName`／`version`／`environment` 皆是程式碼裡的字面常數，不讀取任何資料庫或設定檔——`environment` 永遠回傳字面字串 `"production"`，與實際執行環境（開發/測試/生產）無關。
5. **維護警示是 M09 通知系統上線前的人工替代方案**：`GET /maintenance-warnings` 查詢 `room_calendar` 中 `status=MAINTENANCE` 且關聯 `bookingId` 非空的記錄，反查對應訂房的入住日期與房客 Email，供 Admin 人工聯繫；入住日為今日或明日視為「緊急」（`urgent=true`）。M09 訊息通知中心已於 Sprint 272 建立完整規格（見 [API_M09_Notification.md](./API_M09_Notification.md)），但本端點並未被取代或整合，兩者目前並存。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M14-001 | SUPER_ADMIN 查詢/管理平台使用者 | `GET /v2/admin/users`、`PUT /v2/admin/users/{userId}/status` | ✅ 已實作 |
| US-M14-002 | SUPER_ADMIN 查看平台統計 | `GET /v2/admin/stats` | ✅ 已實作（含假資料欄位，見 §7） |
| （無對應 US） | SUPER_ADMIN 查詢系統設定 | `GET /v2/admin/system-config` | ✅ 已實作（硬編碼，見 §7） |
| US-M14-003 | SUPER_ADMIN 查詢稽核日誌 | `GET /v2/admin/audit-logs` | ✅ 已實作 |
| TC-LO2-M17-003 | SUPER_ADMIN 查看維護警示列表以人工通知房客 | `GET /v2/admin/maintenance-warnings` | ✅ 已實作 |

FRD 完全無 M14 章節（見文件頂部狀態說明）。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M14-001 | GET | `/v2/admin/users` | 平台使用者列表（可依租戶/角色/狀態/關鍵字篩選＋真分頁） | `hasRole('SUPER_ADMIN')` |
| API-M14-002 | PUT | `/v2/admin/users/{userId}/status` | 更新使用者狀態（自由字串，見 §1.2 第 2 點） | `hasRole('SUPER_ADMIN')` |
| API-M14-003 | GET | `/v2/admin/stats` | 平台統計（GMV 為真實加總，Sprint 280 修復，見 §7 DEF-384） | `hasRole('SUPER_ADMIN')` |
| API-M14-004 | GET | `/v2/admin/system-config` | 系統設定（硬編碼，見 §7） | `hasRole('SUPER_ADMIN')` |
| API-M14-005 | GET | `/v2/admin/audit-logs` | 稽核日誌（可依操作類型/時間區間篩選＋分頁） | `hasRole('SUPER_ADMIN')` |
| API-M14-006 | GET | `/v2/admin/maintenance-warnings` | 維護警示列表 | `hasRole('SUPER_ADMIN')` |

共 6 個端點（本文件範圍，見文件頂部範圍說明）。**本文件不受 `ApiRouteDocDriftTest` 機械防漂移保護，見 §7。**

---

## 3. 資料模型 / Data Model

### 3.1 `AdminDto.UserManagementResponse`（§4.1／4.2 共用）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `userId` / `email` / `fullName` | — | |
| `role` | String | `User.UserRole` 原始字串 |
| `status` | String | 自由字串，見 §1.2 第 2 點 |
| `tenantId` / `tenantName` | — | 無租戶（系統租戶／一般消費者）時 `tenantName` 為 `null` |
| `createdAt` / `lastLoginAt` | Instant | `lastLoginAt` 無登入紀錄時為 `null` |

### 3.2 `AdminDto.PlatformStatsResponse`（§4.3）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `totalTenants` / `activeTenants` | Integer | 真實查詢（`Tenant` 全表計數／`TenantRepository.countByStatus(ACTIVE)` 資料庫端計數；Sprint 307／DEF-422 起不再 `findAll()` 載入全部租戶，見 §7） |
| `totalUsers` / `totalListings` / `totalOrders` | Integer | 真實查詢（對應表格全表計數，不限狀態） |
| `totalPlatformGMV` | BigDecimal | 真實加總：跨租戶 `SUM(orders.total_amount)`，限已付款未取消/退款狀態（`PAID`/`CONFIRMED`/`SHIPPING`/`DELIVERED`/`COMPLETED`）。Sprint 280 修復（DEF-384），見 §7 |
| `pendingTenantReviews` | Integer | 真實查詢（`tenant_applications.status = PENDING` 計數，歷史 DEF-059 已修正計數來源） |

### 3.3 `AdminDto.SystemConfigResponse`（§4.4）

全部欄位皆為程式碼字面常數，見 §1.2 第 4 點，§7。

### 3.4 `AdminDto.AuditLogResponse`（§4.5）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` / `tenantId` / `userId` | UUID | `tenantId` 可為 `null`（平台層級操作） |
| `action` / `entityType` | String | 自由字串，無列舉限制（記錄時由各呼叫端自訂） |
| `entityId` | UUID | |
| `oldValue` / `newValue` / `reason` | String | 皆可為 `null` |
| `createdAt` | Instant | |

### 3.5 `AdminDto.MaintenanceWarningResponse`（§4.6）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `bookingId` | UUID | |
| `checkInDate` | LocalDate | |
| `guestEmail` | String | |
| `urgent` | boolean | 入住日 ≤ 今日+1 天 |

---

## 4. 端點規格 / Endpoint Specifications

**以下 6 個端點為 `AdminController` 端點（不受機械防漂移保護，見 §7）：**

### 4.1 平台使用者列表

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/users

**權限**：`hasRole('SUPER_ADMIN')`。查詢參數：`page`（預設 0）、`size`（預設 20，上限 100）、`tenantId`／`role`／`status`／`keyword`（皆選填，`keyword` 比對 email 與姓名的 `LIKE` 模糊查詢，大小寫不敏感）。真分頁（歷史 Sprint 64 修正過「取全部資料記憶體內分頁」的缺陷）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `role` 無法解析成 `UserRole` | 400 | `E-9000` |

### 4.2 更新使用者狀態

**端點（不受機械防漂移保護，見 §7）**：PUT /v2/admin/users/{userId}/status

**權限**：`hasRole('SUPER_ADMIN')`。查詢參數 `status`（必填，自由字串，不驗證合法值，見 §1.2 第 2 點）。成功後記錄稽核日誌（`action=USER_STATUS_UPDATED`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 使用者不存在 | 404 | `E-1006` |

### 4.3 平台統計

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/stats

**權限**：`hasRole('SUPER_ADMIN')`。無查詢參數。回應見 §3.2，`totalPlatformGMV` 為真實加總（Sprint 280 修復，§7 DEF-384）。

### 4.4 系統設定

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/system-config

**權限**：`hasRole('SUPER_ADMIN')`。無查詢參數。**全部回應內容為程式碼字面常數**，不反映任何實際資料庫設定或部署環境（§1.2 第 4 點）。

### 4.5 稽核日誌

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/audit-logs

**權限**：`hasRole('SUPER_ADMIN')`。查詢參數：`page`（預設 0）、`size`（預設 20，上限 100）、`action`（選填，精確比對）、`startDate`／`endDate`（選填，`LocalDate`，內部轉換為當日 00:00:00 UTC／23:59:59.999999999 UTC 的 `Instant` 區間）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `startDate`／`endDate` 無法解析成日期 | 400 | `E-9000` |

### 4.6 維護警示列表

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/maintenance-warnings

**權限**：`hasRole('SUPER_ADMIN')`。無查詢參數。回應依 `checkInDate` 升序排序（即將入住者在前）。見 §1.2 第 5 點。

---

## 5. 權限矩陣 / Permission Matrix

本模組 6 個端點**全部**僅限 `hasRole('SUPER_ADMIN')`，無其他角色可呼叫（含 `ADMIN` 角色——`AdminController` 的 `@PreAuthorize` 一律寫 `hasRole('SUPER_ADMIN')`，不是 `hasAnyRole('ADMIN', 'SUPER_ADMIN')`，故持有 `ADMIN` 角色但非 `SUPER_ADMIN` 的帳號**無法**呼叫本模組任何端點），不另列角色 × 端點矩陣表。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-1006` | 404 | §4.2 使用者不存在 |
| `E-9000` | 400 | §4.1 `role` 篩選值非法；§4.5 日期查詢參數型別轉換失敗 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `ApiRouteDocDriftTest` 無法覆蓋本文件 | `AdminController` 混雜 M14／M16／M17 三模組端點，無法整個類別納入守門（會強制要求單一文件記載另外兩模組不相關的端點）。本文件 §4 因此完全沒有機械防漂移，`Controller` 新增/刪除/改名路由不會被任何測試攔截，純靠人工對照維持同步 |
| ~~`totalPlatformGMV` 是假資料~~（**DEF-384，Sprint 280 已修復**） | `AdminService.getPlatformStats` 原以 `訂單總數 × 常數 1500` 計算，已改用 `OrderRepository.sumTotalAmountByStatusIn` 跨租戶真實加總 `totalAmount`，比照 `AnalyticsService.getRevenueStats`（M13）既有的狀態集合慣例 |
| **`activeTenants` 原本 `findAll()` 載入全部租戶，一列壞狀態就讓整個平台統計變 500（DEF-422(a)）——Sprint 307 已修復** | `getPlatformStats` 原用 `tenantRepository.findAll().stream().filter(ACTIVE).count()`。Flyway 建的 `tenants.status` 只是 `VARCHAR(20)`、沒有 CHECK 約束（V1），只要有一列值不在 `TenantStatus` 內，載入時就丟 `No enum constant`，`GET /v2/admin/stats`（管理後台首頁 `/admin` 的資料來源）整個回 500。**實測（Flyway 實跑 94 支遷移的獨立資料庫、自己種一列 `LEGACY_STATUS`，`PlatformStatsCorruptRowFlywayIntegrationTest`）：修復前回 500，後端日誌根因 `No enum constant …TenantStatus.LEGACY_STATUS`**；Hibernate 產生的一般整合測試資料庫對列舉欄位有 CHECK，放不進這種值，所以既有測試重現不出來。修法：新增 `TenantRepository.countByStatus`，`activeTenants` 由資料庫端計數。測試：`PlatformStatsCorruptRowFlywayIntegrationTest`（2 個案例，第一個是『固件會咬人』的前置斷言）、`AdminServiceTest.GetPlatformStats`（補上先前完全沒有的 `activeTenants` 斷言與 `verify(never()).findAll()`）；5 個突變全數擊殺（含『壞資料列消失且退回 `findAll()`』只有前置斷言會紅）。**同一個 DEF 的 (b) 廣播的兩處 `findAll()` 沒有修，理由見追蹤表 `DEF-422`** |
| 使用者狀態欄位無列舉驗證 | `PUT /users/{userId}/status` 接受任意字串寫入 `User.status`；全庫唯一消費端（`AuthService` 登入檢查）只區分「等於 `ACTIVE`」與「其他」，故不同的非 `ACTIVE` 字面值效果相同，但無法用此欄位記錄/區分停權原因 |
| `GET /system-config` 完全硬編碼 | 無任何資料庫或設定檔來源，`environment` 欄位值永遠是字面字串 `"production"`，與實際執行環境無關；若有呼叫端依此欄位做環境判斷邏輯會得到錯誤結果 |
| 維護警示與 M09 通知系統並存未整合 | Sprint 272 已建立完整的 M09 訊息通知規格，但 `getMaintenanceWarnings` 仍是獨立的人工查詢工具，兩者沒有互相取代或串接 |
| `AuditLogResponse.action`／`entityType` 無列舉限制 | 由各呼叫端（目前已知至少 `FEATURE_TOGGLE_SET`／`FEATURE_TOGGLE_UPDATED`／`USER_STATUS_UPDATED`／`CONNECT_ONBOARDING_INITIATED`／`CONNECT_STATUS_SYNCED` 等）自行決定字串值，沒有集中列舉，新增稽核事件類型無強制命名規範 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §4（Operations，M14 平台總管理台）／§9.9；§5.5.3（維護警示）／v0.9_R02_Loop_02 Q-LO2-106 |
| FRD | 無章節（見文件頂部狀態說明） |
| 相關文件 | [API_M17_Tenant.md](./API_M17_Tenant.md)（`AdminController` 的 M17 範圍端點）、[API_M16_ERP.md](./API_M16_ERP.md)（採購單審批端點） |
| 實作 | `AdminController`（`api/controller/`，本文件範圍為 §2 列出的 6 個端點）；`AdminService`（`core/admin/`） |
| 契約測試 | [AdminServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/admin/AdminServiceTest.java)、[AdminServiceIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/AdminServiceIntegrationTest.java)、[AdminControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/AdminControllerE2ETest.java)、Flyway 資料庫：[PlatformStatsCorruptRowFlywayIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/PlatformStatsCorruptRowFlywayIntegrationTest.java)（DEF-422） |

---

**文件狀態**：v1.1（Sprint 307：DEF-422(a)，`activeTenants` 改為資料庫端計數）；v1.0（Sprint 276 新建，DEF-286 範圍）。
