# M06 預訂系統測試案例 / Booking System Test Cases

> **模組**: M06 預訂系統
> **版本**: v1.0
> **建立日期**: 2026-04-28
> **依據**: API_M06_Booking.md, E-Commerce_FRD_v1.0.md, SPRINT_04_PLAN.md
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API E2E)
> **Sprint 4 範圍**: US-M06-001 (驗收), US-M06-002 (驗收), US-M06-003 (驗收)
> **負責人**: QA (Quincy)
> **注意**: M06 Backend 功能已實作，Sprint 4 聚焦於驗收測試

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 | Sprint | 狀態 |
|----------|----|----|----|------|--------|------|
| UT | 2 | 1 | 1 | 4 | Sprint 4 | ⏳ 待實現 |
| IT | 3 | 2 | 1 | 6 | Sprint 4 | ⏳ 待實現 |
| API E2E | 5 | 3 | 0 | 8 | Sprint 4 | ⏳ 待實現 |
| ISO (多租戶隔離) | 2 | 0 | 0 | 2 | Sprint 4 | ⏳ 待實現 |
| NFR | 1 | 1 | 0 | 2 | Sprint 4 | ⏳ 待實現 |
| **合計** | 13 | 7 | 2 | **22** | | |

---

## 1. AC-AT 映射矩陣

### 1.1 US-M06-001: 建立預訂

| AC ID | 驗收標準 | AT ID | 測試類型 | 優先級 | 狀態 |
|-------|----------|-------|----------|--------|------|
| AC-M06-001-1 | 買家可建立房源預訂 | AT-M06-001-P0-01 | API E2E | P0 | ⏳ |
| AC-M06-001-1 | 買家可建立房源預訂 | AT-M06-001-P0-02 | IT | P0 | ⏳ |
| AC-M06-001-1 | 買家可建立房源預訂 | AT-M06-001-P0-03 | UT | P0 | ⏳ |
| AC-M06-001-2 | 預訂包含入住/退房日期、guest 數量 | AT-M06-001-P0-04 | API E2E | P0 | ⏳ |
| AC-M06-001-2 | 預訂包含入住/退房日期、guest 數量 | AT-M06-001-P1-01 | IT | P1 | ⏳ |
| AC-M06-001-3 | 日期衝突回傳錯誤 (E-4001) | AT-M06-001-P0-05 | API E2E | P0 | ⏳ |
| AC-M06-001-3 | 日期衝突回傳錯誤 (E-4001) | AT-M06-001-P0-06 | API E2E | P0 | ⏳ |
| AC-M06-001-3 | 日期衝突回傳錯誤 (E-4001) | AT-M06-001-P0-07 | API E2E | P0 | ⏳ |
| AC-M06-001-3 | 日期衝突回傳錯誤 (E-4001) | AT-M06-001-P0-08 | IT | P0 | ⏳ |
| AC-M06-001-3 | 日期衝突回傳錯誤 (E-4001) | AT-M06-001-P0-09 | API E2E | P0 | ⏳ |
| AC-M06-001-NFR-01 | 並發預訂防超賣 (Race Condition) | AT-M06-001-NFR-01 | API E2E | P0 | ⏳ |

### 1.2 US-M06-002: 預訂日曆鎖定

| AC ID | 驗收標準 | AT ID | 測試類型 | 優先級 | 狀態 |
|-------|----------|-------|----------|--------|------|
| AC-M06-002-1 | 預訂成功後日期格鎖定 | AT-M06-002-P0-01 | IT | P0 | ⏳ |
| AC-M06-002-1 | 預訂成功後日期格鎖定 | AT-M06-002-P0-02 | UT | P0 | ⏳ |
| AC-M06-002-2 | 其他預訂無法重疊鎖定日期 | AT-M06-002-P0-03 | API E2E | P0 | ⏳ |
| AC-M06-002-2 | 其他預訂無法重疊鎖定日期 | AT-M06-002-P1-01 | IT | P1 | ⏳ |

### 1.3 US-M06-003: 取消預訂

| AC ID | 驗收標準 | AT ID | 測試類型 | 優先級 | 狀態 |
|-------|----------|-------|----------|--------|------|
| AC-M06-003-1 | 買家可取消未入住預訂 | AT-M06-003-P0-01 | API E2E | P0 | ⏳ |
| AC-M06-003-1 | 買家可取消未入住預訂 | AT-M06-003-P0-02 | IT | P0 | ⏳ |
| AC-M06-003-2 | 取消後日期格釋放 | AT-M06-003-P0-03 | API E2E | P0 | ⏳ |
| AC-M06-003-2 | 取消後日期格釋放 | AT-M06-003-P1-01 | IT | P1 | ⏳ |
| AC-M06-003-2 | 取消後可重新預訂相同時段 | AT-M06-003-P1-02 | API E2E | P1 | ⏳ |

---

## 2. 詳細測試案例

### 2.1 建立預訂 (US-M06-001)

#### AT-M06-001-P0-01: 建立預訂-成功

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-01 |
| **測試類型** | API E2E |
| **描述** | 買家可成功建立房源預訂 |
| **AC 映射** | AC-M06-001-1 |
| **測試資料 (Request)** | `{ "listingId": "uuid-of-room-listing", "checkInDate": "2026-06-01", "checkOutDate": "2026-06-03", "guestCount": 2, "specialRequests": "需要嬰兒床" }` |
| **預期結果 (Asserts)** | - HTTP Status: 201 Created<br>- response.code: 201<br>- response.message: "Booking created"<br>- data.bookingId: 非 null UUID<br>- data.status: "CREATED"<br>- data.listingId: 與請求一致<br>- data.checkInDate: "2026-06-01"<br>- data.checkOutDate: "2026-06-03"<br>- data.guestCount: 2<br>- data.totalPrice: > 0 |
| **優先級** | P0 |
| **測試步驟** | 1. 取得 BUYER 登入 token<br>2. POST /api/v2/bookings 帶入 Request Body<br>3. 驗證 HTTP 201 和 response 結構 |

---

#### AT-M06-001-P0-02: 建立預訂-成功 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-02 |
| **測試類型** | IT (Integration Test) |
| **描述** | 整合測試：BookingService.createBooking() 成功流程 |
| **AC 映射** | AC-M06-001-1 |
| **測試資料** | bookingRequest: listingId=有效 ROOM listing, checkInDate=未來日期, checkOutDate>checkInDate, guestCount<=maxGuests |
| **預期結果** | - createBooking() 回傳非 null Booking 物件<br>- booking.status = CREATED<br>- booking.tenantId 正確設定 |
| **優先級** | P0 |

---

#### AT-M06-001-P0-03: 建立預訂-總金額計算 (UT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-03 |
| **測試類型** | UT (Unit Test) |
| **描述** | BookingService 計算總金額正確 |
| **AC 映射** | AC-M06-001-1 |
| **測試資料** | 日曆單價 $100/晚 x 3 晚 = $300 |
| **預期結果** | totalAmount = $300 |
| **優先級** | P0 |

---

#### AT-M06-001-P0-04: 建立預訂-預訂資訊完整性

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-04 |
| **測試類型** | API E2E |
| **描述** | 預訂包含入住/退房日期、guest 數量 |
| **AC 映射** | AC-M06-001-2 |
| **測試資料** | `{ "listingId": "uuid", "checkInDate": "2026-07-01", "checkOutDate": "2026-07-05", "guestCount": 4 }` |
| **預期結果** | - data.checkInDate: "2026-07-01"<br>- data.checkOutDate: "2026-07-05"<br>- data.guestCount: 4<br>- data.totalPrice: 日曆單價 x 4 晚 |
| **優先級** | P0 |

---

#### AT-M06-001-P1-01: 建立預訂-Guest 數量驗證

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P1-01 |
| **測試類型** | IT |
| **描述** | Guest 數量不得超過房源上限 |
| **AC 映射** | AC-M06-001-2 |
| **測試資料** | room.maxGuests=4, guestCount=10 |
| **預期結果** | HTTP 400, error.code: E-4005 |
| **優先級** | P1 |

---

#### AT-M06-001-P0-05: 建立預訂-完整重疊衝突

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-05 |
| **測試類型** | API E2E |
| **描述** | 相同日期區間（完整重疊）回傳 E-4001 錯誤 |
| **AC 映射** | AC-M06-001-3 |
| **前置條件** | 預訂 A: checkIn=2026-06-01, checkOut=2026-06-03 已存在 |
| **測試資料** | `{ "listingId": "same-listing", "checkInDate": "2026-06-01", "checkOutDate": "2026-06-03", "guestCount": 2 }` |
| **預期結果** | - HTTP Status: 400 Bad Request<br>- response.code: 400<br>- response.error.code: "E-4001"<br>- response.message: "Date conflict" |
| **優先級** | P0 |

---

#### AT-M06-001-P0-06: 建立預訂-部分重疊衝突

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-06 |
| **測試類型** | API E2E |
| **描述** | 第二個預訂的 check-in 在第一個預訂區間內（部分重疊） |
| **AC 映射** | AC-M06-001-3 |
| **前置條件** | 預訂 A: checkIn=2026-06-01, checkOut=2026-06-05 |
| **測試資料** | `{ "listingId": "same-listing", "checkInDate": "2026-06-03", "checkOutDate": "2026-06-07", "guestCount": 2 }` |
| **預期結果** | - HTTP Status: 400 Bad Request<br>- response.error.code: "E-4001" |
| **優先級** | P0 |

---

#### AT-M06-001-P0-07: 建立預訂-第二個預訂包含第一個

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-07 |
| **測試類型** | API E2E |
| **描述** | 第二個預訂的區間完全包含第一個預訂 |
| **AC 映射** | AC-M06-001-3 |
| **前置條件** | 預訂 A: checkIn=2026-06-02, checkOut=2026-06-04 |
| **測試資料** | `{ "listingId": "same-listing", "checkInDate": "2026-06-01", "checkOutDate": "2026-06-05", "guestCount": 2 }` |
| **預期結果** | - HTTP Status: 400 Bad Request<br>- response.error.code: "E-4001" |
| **優先級** | P0 |

---

#### AT-M06-001-P0-08: 建立預訂-日期衝突 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-08 |
| **測試類型** | IT |
| **描述** | 整合測試：日期衝突時 BookingService 拋出正確例外 |
| **AC 映射** | AC-M06-001-3 |
| **測試資料** | 預訂 A: 6/1-6/3 已被預訂 |
| **預期結果** | 呼叫 createBooking() 拋出 BookingConflictException 或类似异常 |
| **優先級** | P0 |

---

#### AT-M06-001-P0-09: 建立預訂-連續日期不衝突

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-P0-09 |
| **測試類型** | API E2E |
| **描述** | A 的 check-out = B 的 check-in（相鄰日期）不視為衝突 |
| **AC 映射** | AC-M06-001-3 |
| **前置條件** | 預訂 A: checkIn=2026-06-01, checkOut=2026-06-03 |
| **測試資料** | `{ "listingId": "same-listing", "checkInDate": "2026-06-03", "checkOutDate": "2026-06-05", "guestCount": 2 }` |
| **預期結果** | - HTTP Status: 201 Created<br>- 預訂 B 建立成功（6/3 checkout, 6/3 checkin 中間無重疊） |
| **優先級** | P0 |

---

#### AT-M06-001-NFR-01: 並發預訂防超賣 (Race Condition)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-001-NFR-01 |
| **測試類型** | API E2E (並發測試) |
| **描述** | 同時 2 個使用者嘗試預訂同一日期，只有 1 個成功 |
| **AC 映射** | AC-M06-001-1, AC-M06-001-3 |
| **前置條件** | 房源可用庫存 = 1 |
| **測試資料** | 使用者 A 和 B 同時發送：`{ "listingId": "same-listing", "checkInDate": "2026-08-01", "checkOutDate": "2026-08-03", "guestCount": 2 }` |
| **預期結果** | - 1 個成功 (HTTP 201)<br>- 1 個失敗 (HTTP 400, E-4001)<br>- 資料庫只有 1 筆預訂記錄 |
| **優先級** | P0 |
| **NFR 關聯** | 預訂 API 回應時間 < 300ms |

---

### 2.2 預訂日曆鎖定 (US-M06-002)

#### AT-M06-002-P0-01: 預訂成功後日期格鎖定 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-002-P0-01 |
| **測試類型** | IT |
| **描述** | 建立預訂後，RoomCalendar 日期格狀態變為 BOOKED |
| **AC 映射** | AC-M06-002-1 |
| **測試步驟** | 1. 建立預訂 6/1-6/3<br>2. 查詢 RoomCalendarService.getDateRangeStatus(6/1, 6/3) |
| **預期結果** | - 所有日期格狀態 = "BOOKED"<br>- bookingId 正確關聯 |
| **優先級** | P0 |

---

#### AT-M06-002-P0-02: lockDateRange-成功取得鎖 (UT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-002-P0-02 |
| **測試類型** | UT |
| **描述** | RoomCalendarService.lockDateRange() 成功取得鎖 |
| **AC 映射** | AC-M06-002-1 |
| **測試資料** | Redis 可用，日期 6/1-6/3 未被鎖 |
| **預期結果** | lockDateRange() 回傳非 null lockValue |
| **優先級** | P0 |

---

#### AT-M06-002-P0-03: 其他預訂無法重疊鎖定日期

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-002-P0-03 |
| **測試類型** | API E2E |
| **描述** | 預訂 A 鎖定日期後，預訂 B 嘗試預訂相同日期失敗 |
| **AC 映射** | AC-M06-002-2 |
| **前置條件** | 預訂 A: 6/1-6/3 已確認 |
| **測試資料** | 預訂 B: `{ "listingId": "same-listing", "checkInDate": "2026-06-01", "checkOutDate": "2026-06-03" }` |
| **預期結果** | - HTTP 400, E-4001<br>- 日曆仍顯示預訂 A |
| **優先級** | P0 |

---

#### AT-M06-002-P1-01: lockDateRange-日期已被鎖 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-002-P1-01 |
| **測試類型** | IT |
| **描述** | 其他預訂已鎖定日期時，lockDateRange 回傳 null |
| **AC 映射** | AC-M06-002-2 |
| **測試資料** | 其他預訂已鎖定 6/1-6/3 |
| **預期結果** | lockDateRange() 回傳 null |
| **優先級** | P1 |

---

### 2.3 取消預訂 (US-M06-003)

#### AT-M06-003-P0-01: 取消預訂-成功

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-003-P0-01 |
| **測試類型** | API E2E |
| **描述** | 買家可成功取消未入住預訂 |
| **AC 映射** | AC-M06-003-1 |
| **前置條件** | 買家已有 CREATED 狀態預訂 |
| **測試資料** | POST /api/v2/bookings/{bookingId}/cancel |
| **預期結果** | - HTTP Status: 200 OK<br>- response.code: 200<br>- response.message: "Booking cancelled"<br>- data.status: "CANCELLED"<br>- data.cancelledAt: 非 null |
| **優先級** | P0 |

---

#### AT-M06-003-P0-02: 取消預訂-成功 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-003-P0-02 |
| **測試類型** | IT |
| **描述** | 整合測試：BookingService.cancelBooking() 成功 |
| **AC 映射** | AC-M06-003-1 |
| **測試資料** | CREATED 狀態預訂 |
| **預期結果** | - cancelBooking() 成功<br>- booking.status = CANCELLED |
| **優先級** | P0 |

---

#### AT-M06-003-P0-03: 取消後日期格釋放

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-003-P0-03 |
| **測試類型** | API E2E |
| **描述** | 取消預訂後，RoomCalendar 日期格狀態變回 AVAILABLE |
| **AC 映射** | AC-M06-003-2 |
| **前置條件** | 預訂 A: 6/1-6/3 已確認 |
| **測試步驟** | 1. POST /api/v2/bookings/{bookingId}/cancel<br>2. 查詢日曆狀態 |
| **預期結果** | - HTTP 200, status=CANCELLED<br>- 日曆日期格狀態 = "AVAILABLE"<br>- bookingId = null |
| **優先級** | P0 |

---

#### AT-M06-003-P1-01: 取消後日曆狀態變回 AVAILABLE (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-003-P1-01 |
| **測試類型** | IT |
| **描述** | RoomCalendarService.releaseDateRange() 釋放日期鎖定 |
| **AC 映射** | AC-M06-003-2 |
| **測試資料** | 預訂已確認，日期格狀態為 BOOKED |
| **預期結果** | releaseDateRange() 後日期狀態回 AVAILABLE |
| **優先級** | P1 |

---

#### AT-M06-003-P1-02: 取消後可重新預訂相同時段

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-003-P1-02 |
| **測試類型** | API E2E |
| **描述** | 取消預訂後，原本鎖定的日期可重新被預訂 |
| **AC 映射** | AC-M06-003-2 |
| **前置條件** | 預訂 A: 6/1-6/3 已取消 |
| **測試資料** | 新預訂 B: `{ "listingId": "same-listing", "checkInDate": "2026-06-01", "checkOutDate": "2026-06-03" }` |
| **預期結果** | - HTTP Status: 201 Created<br>- 預訂 B 建立成功 |
| **優先級** | P1 |

---

## 3. 多租戶隔離測試 (ISO)

### 3.1 租戶資料隔離驗證

#### AT-M06-ISOLATION-01: 買家 A 看不到買家 B 的預訂

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-ISOLATION-01 |
| **測試類型** | API E2E |
| **描述** | 買家 A 建立預訂，買家 B 無法看到買家 A 的預訂資料 |
| **AC 映射** | 多租戶隔離驗證 |
| **前置條件** | 買家 A 和買家 B 是不同租戶 |
| **測試步驟** | 1. 買家 A Token: POST /api/v2/bookings 建立預訂<br>2. 買家 A Token: GET /api/v2/bookings 取得列表<br>3. 買家 B Token: GET /api/v2/bookings 取得列表 |
| **預期結果** | - 買家 A 列表包含自己的預訂<br>- 買家 B 列表不包含買家 A 的預訂 |
| **優先級** | P0 |

---

#### AT-M06-ISOLATION-02: 買家 A 無法取消買家 B 的預訂

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-ISOLATION-02 |
| **測試類型** | API E2E |
| **描述** | 買家 A 嘗試取消買家 B 的預訂回傳 403/404 |
| **AC 映射** | 多租戶隔離驗證 |
| **前置條件** | 買家 B 有 CREATED 狀態預訂 |
| **測試步驟** | 買家 A Token: POST /api/v2/bookings/{買家B的預訂ID}/cancel |
| **預期結果** | - HTTP 403 Forbidden 或 404 Not Found<br>- 買家 B 的預訂狀態不變 |
| **優先級** | P0 |

---

## 4. Error Code 驗證

| TC ID | 錯誤碼 | 描述 | 測試案例 | 優先級 |
|-------|--------|------|---------|--------|
| ERR-M06-001 | E-3000 | LISTING_NOT_FOUND - 房源不存在 | AT-M06-001-IT-001 | P0 |
| ERR-M06-002 | E-4001 | DATE_CONFLICT - 日期已被預訂 | AT-M06-001-P0-05, AT-M06-001-P0-06 | P0 |
| ERR-M06-003 | E-4003 | INVALID_DATE_RANGE - 入住日期晚於退房日期 | AT-M06-001-IT-002 | P0 |
| ERR-M06-004 | E-4005 | GUEST_COUNT_EXCEEDS_CAPACITY - Guest 數量超過上限 | AT-M06-001-P1-01 | P1 |
| ERR-M06-005 | E-4006 | BOOKING_NOT_FOUND - 預訂不存在 | AT-M06-003-IT-001 | P1 |
| ERR-M06-006 | E-4007 | BOOKING_CANNOT_BE_CANCELLED - 已入住不可取消 | AT-M06-003-IT-002 | P1 |

---

## 5. NFR 效能測試

### 5.1 效能需求驗證

#### AT-M06-NFR-01: Booking API 回應時間 < 300ms

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-NFR-01 |
| **測試類型** | NFR (Non-Functional Requirements) |
| **描述** | POST /api/v2/bookings API 回應時間需 < 300ms |
| **測試資料** | 建立預訂請求，測量從發送 request 到收到 response 的時間 |
| **測試環境** | 標準負載（無並發） |
| **預期結果** | - 回應時間 < 300ms (p95)<br>- 回應時間 < 500ms (p99) |
| **優先級** | P0 |

---

#### AT-M06-NFR-02: 並發 100 個使用者操作

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M06-NFR-02 |
| **測試類型** | NFR (Load Test) |
| **描述** | 同時 100 個使用者執行預訂/查詢操作 |
| **測試資料** | 100 個並發請求（建立預訂、查詢預訂列表） |
| **測試環境** | 負載測試環境 |
| **預期結果** | - 系統正常回應<br>- 無超賣問題<br>- 錯誤率 < 1%<br>- 平均回應時間 < 1s |
| **優先級** | P1 |

---

## 6. 角色權限測試

| TC ID | 測試案例 | 角色 | 預期結果 | 優先級 |
|-------|---------|------|----------|--------|
| RP-M06-001 | 建立預訂 | STORE_OWNER Token | HTTP 403 Forbidden | P0 |
| RP-M06-002 | 建立預訂 | ADMIN Token | HTTP 403 Forbidden | P0 |
| RP-M06-003 | 取消預訂 | 非本人 BUYER Token | HTTP 403 Forbidden | P0 |

---

## 7. 預訂狀態流轉圖

```
    CREATED ──────► CONFIRMED ──────► COMPLETED
        │                │
        ▼                ▼
    CANCELLED       CANCELLED
```

### 狀態說明

| 狀態 | 說明 | 可轉移到 |
|------|------|----------|
| CREATED | 預訂已建立，等待確認 | CONFIRMED, CANCELLED |
| CONFIRMED | 預訂已確認 | COMPLETED, CANCELLED |
| COMPLETED | 已完成入住 | - |
| CANCELLED | 已取消 | - |

---

## 8. 測試資料夾 (Test Data)

### 8.1 測試資料夾結構

| 資料夾名稱 | 用途 | 備註 |
|------------|------|------|
| TENANT-A | 買家 A 所屬租戶 | UUID: tenant-a-uuid |
| TENANT-B | 買家 B 所屬租戶 | UUID: tenant-b-uuid |
| BUYER-A | 買家 A | UUID: buyer-a-uuid |
| BUYER-B | 買家 B | UUID: buyer-b-uuid |
| ROOM-LISTING-A | 房源（可預訂） | UUID: room-listing-a-uuid |
| ROOM-LISTING-B | 房源（可預訂） | UUID: room-listing-b-uuid |

### 8.2 日曆測試資料

| 日期區間 | 狀態 | 備註 |
|----------|------|------|
| 2026-06-01 ~ 2026-06-03 | AVAILABLE | 測試用：可預訂 |
| 2026-06-10 ~ 2026-06-15 | BOOKED | 已被預訂 A 鎖定 |

---

## 9. 關鍵驗證點總結

| 驗證項目 | AT ID | 優先級 |
|---------|-------|--------|
| 多租戶資料隔離（買家 A 看不到買家 B 預訂） | AT-M06-ISOLATION-01, AT-M06-ISOLATION-02 | P0 |
| 日期衝突檢查（完整重疊） | AT-M06-001-P0-05 | P0 |
| 日期衝突檢查（部分重疊） | AT-M06-001-P0-06 | P0 |
| 日期衝突檢查（包含） | AT-M06-001-P0-07 | P0 |
| 連續日期不衝突 | AT-M06-001-P0-09 | P0 |
| 並發預訂防超賣 | AT-M06-001-NFR-01 | P0 |
| 取消後日期格釋放 | AT-M06-003-P0-03 | P0 |
| BUYER 角色驗證 | RP-M06-001, RP-M06-002 | P0 |
| Error Code 正確性 | ERR-M06-* | P0 |
| 效能測試 (API < 300ms) | AT-M06-NFR-01 | P0 |

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-04-28

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-28 | 初始版本（基於 Sprint 4 Plan + AISDLC 測試規範） | QA (Quincy) |
