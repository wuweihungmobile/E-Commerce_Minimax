# M02 房源管理測試案例 / Room Management Test Cases

> **模組**: M02 房源中心
> **版本**: v1.0
> **建立日期**: 2026-04-10
> **依據**: API_M02_Listing_Center.md, SRD_Database_Schema.md, SRD_System_Architecture.md
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API)

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 3 | 2 | 2 | 7 |
| IT | 2 | 2 | 1 | 5 |
| API | 2 | 3 | 1 | 6 |
| **合計** | 7 | 7 | 4 | **18** |

---

## 1. 單元測試 (Unit Tests)

### 1.1 Room Calendar 日期衝突判斷

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M02-001 | 日曆衝突-日期已被預訂 | P0 | 某日期 status = BOOKED | 1. 嘗試預訂該日期 | 拋出 DateConflictException |
| UT-M02-002 | 日曆衝突-日期被封鎖 | P0 | 某日期 status = BLOCKED | 1. 嘗試預訂該日期 | 拋出 DateConflictException |
| UT-M02-003 | 日曆衝突-日期可用 | P0 | 某日期 status = AVAILABLE | 1. 嘗試預訂該日期 | 預訂成功 |
| UT-M02-004 | 日曆衝突-跨多日預訂部分衝突 | P0 | 部分日期已預訂 | 1. 嘗試預訂 5 天 (其中 2 天已預訂) | 拋出 DateConflictException |
| UT-M02-005 | 日曆衝突-入住退房同一天 | P1 | 無 | 1. checkIn = checkOut | 拋出 InvalidDateRangeException |
| UT-M02-006 | 日曆衝突-退房早於入住 | P1 | 無 | 1. checkOut < checkIn | 拋出 InvalidDateRangeException |

### 1.2 房源可用數量計算

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M02-007 | 可用房間數-totalRooms=5, booked=2 | P1 | 總 5 間，已預訂 2 間 | 1. 查詢可用數量 | available = 3 |

---

## 2. 整合測試 (Integration Tests)

### 2.1 房源上架/編輯/下架

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M02-001 | 房源上架-成功 | P0 | Host 登入 + BOOKING_ENABLED | 1. POST /api/v2/dashboard/listings<br>Body: {"listingType":"ROOM",...} | 201, status = DRAFT |
| IT-M02-002 | 房源上架-BOOKING_ENABLED未啟用 | P0 | Host + BOOKING_ENABLED = false | 1. POST /api/v2/dashboard/listings (ROOM) | 403, FEATURE_DISABLED |
| IT-M02-003 | 房源編輯-更新資訊 | P1 | 已有 ROOM 房源 | 1. PUT /api/v2/dashboard/listings/{id}<br>Body: {"title":"新標題","maxGuests":6} | 200, 更新成功 |
| IT-M02-004 | 房源下架-改為INACTIVE | P1 | ACTIVE 房源 | 1. PUT /api/v2/dashboard/listings/{id}/status<br>Body: {"status":"INACTIVE"} | 200, status = INACTIVE |

### 2.2 日曆查詢與預訂衝突

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M02-005 | 日曆查詢-單日可用 | P0 | 房源有日曆資料 | 1. GET /api/v2/listings/{id}/calendar?start=2026-05-01&end=2026-05-01 | 200, 回傳該日可用狀態和價格 |
| IT-M02-006 | 日曆查詢-多日 | P1 | 房源有日曆資料 | 1. GET /api/v2/listings/{id}/calendar?start=2026-05-01&end=2026-05-07 | 200, 回傳 7 天日曆 |
| IT-M02-007 | 預訂衝突-Redis鎖防範雙重預訂 | P0 | 可用日期 | 1. 同時發送 2 個相同日期預訂請求 | 一個成功，一個失敗或等待 |
| IT-M02-008 | 預訂衝突-跨租戶隔離 | P1 | 兩個獨立租戶 | 1. Tenant A 預訂某日期<br>2. Tenant B 查詢同一日期 | Tenant B 仍可預訂（不應衝突） |

---

## 3. API E2E 測試 (API E2E Tests)

### 3.1 房源列表 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M02-001 | GET /api/v2/listings?type=ROOM-房源列表 | P0 | 有 ROOM 資料 | 1. GET /api/v2/listings?type=ROOM | 200, data.items 包含房源列表 |
| API-M02-002 | GET /api/v2/listings/:id-房源詳情 | P0 | 有房源 | 1. GET /api/v2/listings/{roomId} | 200, data 包含完整房源資訊 |
| API-M02-003 | GET /api/v2/listings/:id-不存在 | P1 | 無 | 1. GET /api/v2/listings/invalid-uuid | 404, Listing not found |

### 3.2 日曆查詢 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M02-004 | GET /api/v2/listings/:id/calendar-成功 | P0 | 有日曆資料 | 1. GET /api/v2/listings/{id}/calendar?start=2026-05-01&end=2026-05-03 | 200, 回傳日曆和動態價格 |
| API-M02-005 | GET /api/v2/listings/:id/calendar-含已預訂日期 | P0 | 某日期已預訂 | 1. GET /api/v2/listings/{id}/calendar (含已預訂日) | 回傳日曆中該日 status = BOOKED |
| API-M02-006 | GET /api/v2/listings/:id/calendar-日期範圍無效 | P1 | 無 | 1. GET /api/v2/listings/{id}/calendar?start=2026-05-05&end=2026-05-01 | 400, 日期範圍無效 |

### 3.3 店鋪房源管理 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M02-007 | GET /api/v2/dashboard/listings-店鋪房源列表 | P0 | Host 登入 | 1. GET /api/v2/dashboard/listings?type=ROOM | 200, data.items 包含店鋪房源 |
| API-M02-008 | POST /api/v2/dashboard/listings-建立房源 | P0 | Host + BOOKING_ENABLED | 1. POST /api/v2/dashboard/listings<br>Body: {"listingType":"ROOM","title":"新房","basePrice":2000} | 201, status = DRAFT |
| API-M02-009 | PUT /api/v2/dashboard/listings/:id/status-更新狀態 | P1 | Host + 自有房源 | 1. PUT /api/v2/dashboard/listings/{id}/status<br>Body: {"status":"ACTIVE"} | 200, status 更新 |

---

## 4. Redis 分散式鎖測試

### 4.1 預訂衝突防護

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| LOCK-001 | Redis鎖-單一請求成功 | P0 | 可用日期 | 1. 發送預訂請求 | 成功取得鎖，預訂成功 |
| LOCK-002 | Redis鎖-併發請求唯一成功 | P0 | 可用日期 | 1. 同時發送 10 個相同日期預訂 | 只有 1 個成功，其餘失敗或等待超時 |
| LOCK-003 | Redis鎖-鎖自動釋放 | P0 | 取得鎖後超時 | 1. 取得鎖後不釋放等待超時<br>2. 另一請求取得同一鎖 | 第二個請求在超時後成功取得鎖 |
| LOCK-004 | Redis鎖-正常流程釋放鎖 | P1 | 預訂完成 | 1. 成功預訂並完成交易<br>2. 釋放鎖 | 鎖立即釋放，下一個請求可立即取得 |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| Redis 分散式鎖防止雙重預訂 | LOCK-001, LOCK-002, LOCK-003, LOCK-004 | P0 |
| 日曆可用日期計算正確 | UT-M02-001 ~ UT-M02-006, IT-M02-005 | P0 |
| 房源日曆狀態: AVAILABLE, BOOKED, BLOCKED, MAINTENANCE | UT-M02-001 ~ UT-M02-003 | P0 |
| 跨租戶日曆隔離 | IT-M02-008 | P1 |

---

## 📝 房源日曆狀態說明

| 狀態 | 說明 | 是否可預訂 |
|------|------|-----------|
| AVAILABLE | 可預訂 | ✅ |
| BOOKED | 已被預訂 | ❌ |
| BLOCKED | 房東主動封鎖 | ❌ |
| MAINTENANCE | 維護中 | ❌ |

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-04-10
