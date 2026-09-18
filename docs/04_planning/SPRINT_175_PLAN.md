# Sprint 175 Plan — BookingDto.UpdateRequest 缺少欄位驗證，guestCount 可超過房源人數上限（DEF-227）

**Sprint**: Sprint 175
**日期**: 2026-09-19

## 1. 起點

Sprint 174 §8 誠實揭露記錄了兩個未擴大查證的方向：(1) 其餘 6 個含 `isBefore`/`isAfter` 日期比較邏輯的 Service（`OrderService`/`AdminService`/`RedisCartService`/`PricingService`/`AnalyticsService`）是否有同型缺口；(2) `BookingService` 是否還有其他 `update` 類方法存在類似驗證不對稱。

本輪先處理方向 (1)：逐一查證後確認皆非同型缺口——`PricingService` 的其餘 `isBefore`/`isAfter` 皆為查詢/篩選邏輯（`DEF-225` 已修復其唯一的 create/update 驗證缺口）；`RedisCartService.addItem` 的日期驗證（`endDate.isAfter(startDate)`）沒有對應的 update 方法會碰觸日期欄位（`updateItem` 只接受 `quantity`）；`OrderService` 的兩處日期檢查（`validateRoomOrderRequest`/`createBooking`）皆位於已查證零前端呼叫點的死碼路徑（`room-booking-dual-path-gotcha`，`DEF-161` 已於 Sprint 144 確認），無對應 update 方法；`AdminService`/`AnalyticsService` 的 `isAfter`/`isBefore` 皆為報表/唯讀查詢的區間判斷，非 create/update 驗證情境。此方向查證後**無新發現**。

轉向方向 (2)：把「create 有驗證、update 沒有」的掃描角度從日期範圍擴大到 DTO 欄位層級的 Bean Validation 註解本身，重新檢視 `BookingDto`（`DEF-226` 剛修完日期範圍，順勢查證同一個 DTO 類別的其他欄位）。

## 2. 缺口說明

`grep` 全庫數值範圍驗證註解（`@Min`/`@Max`/`@DecimalMin`/`@DecimalMax`/`@Positive`）分布，確認 `RoomDto`（`DEF-196` 前例）的 `CreateRequest`/`UpdateRequest` 兩個區塊已完全對稱（`latitude`/`longitude`/`basePrice`/`maxGuests`/`roomCount`/`bookingWindowDays` 皆兩邊都有相同驗證，`UpdateRequest` 一律省略 `@NotNull`，只留範圍約束——確立的既有 partial update 慣例）。但 `BookingDto` 對照後發現明顯不對稱：

`CreateRequest`：
- `guestCount`：`@NotNull` + `@Min(value = 1, ...)`
- `guestName`：`@NotBlank` + `@Size(max = GUEST_NAME_MAX_LENGTH, ...)`
- `guestPhone`：`@Pattern(regexp = "^[0-9]{8,15}$", ...)`
- `guestEmail`：`@Email`
- `specialRequests`：`@Size(max = 1000, ...)`

`UpdateRequest`（`BookingDto.java:76-84`，修復前）：五個同名欄位**完全沒有任何驗證註解**。

`BookingController.updateBooking`（`PUT /{bookingId}`）已有 `@Valid @RequestBody BookingDto.UpdateRequest request`，故只要幫 `UpdateRequest` 補上驗證註解就會立即生效，不需改 Controller。

進一步查證服務層：`resolveBookableRoom`（`createBooking` 專用）除了 DTO 層 `@Min(1)`，還有一道業務規則檢查：
```java
if (request.getGuestCount() > room.getMaxGuests()) {
    throw new BusinessException(ErrorCode.E_4005, "Guest count exceeds capacity: max " + room.getMaxGuests());
}
```
`grep -n "getMaxGuests" BookingService.java` 確認全檔案僅此一處使用——`updateBookingFields`（`updateBooking` 專用）完全沒有對應檢查，只要 DTO 通過驗證（原本連 `@Min(1)` 都沒有）就直接 `booking.setGuestCount(request.getGuestCount())` 寫入。這比 `DEF-226` 更嚴重：不只是「本該乾淨拒絕卻裸露 500」，而是**業務規則本身完全沒有被檢查**——可以把一間限住 2 人的房源訂單改成 20 人，回應 200 無任何錯誤，且沒有任何後續流程會再次核對這個不變量。

## 3. 修法決策

- **DTO 層**：`UpdateRequest` 五個欄位補上與 `CreateRequest` 相同的驗證註解，但**不含** `@NotNull`/`@NotBlank`（這兩個要求欄位必須出現，與 partial update 語意衝突）——比照 `RoomDto.UpdateRequest.maxGuests`（`@Min` 不搭配 `@NotNull`）等既有慣例，Bean Validation 對 `null` 值的 `@Min`/`@Size`/`@Pattern`/`@Email` 皆視為通過（constraint 只驗證非 null 值），故省略欄位仍合法。
- **服務層**：`updateBookingFields` 在寫入 `guestCount` 前查詢房源，比照 `resolveBookableRoom` 複用同一 `ErrorCode.E_4005`（不新增錯誤碼，符合既有慣例）。room 缺失時視為無限制（不阻擋），與 `processDateRangeChange` 的 `assertWithinOpenWindow` 對缺失 room 的既有容錯方式一致，而非比照 `resolveBookableRoom` 對缺失 room 拋 `E_4000`——因為 `updateBooking` 的既有房源查詢慣例（`processDateRangeChange` 內）已確立此容錯方向，本輪配合既有程式碼風格（Rule 11）。

## 4. 修復內容

- `BookingDto.java`（`UpdateRequest`）：
  ```java
  @Min(value = 1, message = "Guest count must be at least 1")
  private Integer guestCount;

  @Size(max = GUEST_NAME_MAX_LENGTH, message = "Guest name too long")
  private String guestName;

  @Pattern(regexp = "^[0-9]{8,15}$", message = "Invalid phone format")
  private String guestPhone;

  @Email(message = "Invalid email format")
  private String guestEmail;

  @Size(max = 1000, message = "Special requests too long")
  private String specialRequests;
  ```
- `BookingService.java`（`updateBookingFields`）：
  ```java
  if (request.getGuestCount() != null) {
      Room room = roomRepository.findByListingId(booking.getRoomListingId()).orElse(null);
      if (room != null && request.getGuestCount() > room.getMaxGuests()) {
          throw new BusinessException(ErrorCode.E_4005, "Guest count exceeds capacity: max " + room.getMaxGuests());
      }
      booking.setGuestCount(request.getGuestCount());
  }
  ```

## 5. 測試

- **DTO 層紅燈先行**：`git stash push -- BookingDto.java` 只暫存 DTO 修復，新增 `BookingDtoValidationTest`（7 案例，比照既有 `RoomDtoValidationTest` 的 `Validator.validate(...)` 手法）：
  - `allFieldsNull_passesValidation`：全部欄位皆為 `null` 應通過（partial update 語意守衛，正/反測試皆需要）。
  - `guestCountLessThanOne_failsValidation`（0 與 -1）：修復前皆通過驗證（無任何違規），修復後皆被拒絕。
  - `guestPhone_formatValidation`/`guestEmail_formatValidation`：非法格式修復前通過、修復後被拒絕；合法格式兩邊皆通過。
  - `guestNameTooLong_failsValidation`/`specialRequestsTooLong_failsValidation`：超長字串修復前通過、修復後被拒絕。
  - `git stash pop` 還原後重跑：5 個攔截類案例（`guestCountLessThanOne`/`guestPhone`/`guestEmail`/`guestNameTooLong`/`specialRequestsTooLong`）由通過轉為正確攔截，其餘 2 個守衛案例（`allFieldsNull`/`guestCountPositive`）修復前後皆通過（無區辨力，如實記錄）。
- **服務層紅燈先行**：`git stash push -- BookingService.java` 只暫存服務層修復，`BookingServiceUpdateDateChangeTest` 新增 `updateBooking_guestCountExceedsRoomCapacity_throwsE4005`（既有 room mock 為 `maxGuests(2)`，request 送 `guestCount(5)`）：
  - 修復前：流程一路執行到 `bookingRepository.save(booking)`（未 stub 回傳值，Mockito 預設回傳 `null`）→ `buildBookingResponse(null)` 拋 `NullPointerException`，而非預期的 `BusinessException(E_4005)`。此為測試環境的偶然現形方式；`bookingRepository.save` 在生產環境會回傳真實已儲存的實體，故生產環境的實際後果是**靜默寫入成功**（回應 200，訂房人數已超過房源容量且無任何錯誤或警告）——與 `DEF-225` 同一種「靜默業務邏輯損毀」性質，而非崩潰。
  - `git stash pop` 還原修復後重跑：轉為預期的 `BusinessException(E_4005)`，且斷言 `never()` 呼叫 `roomCalendarService.releaseDateRange`/`bookingRepository.save`。
- `mvn -o test -Dtest=BookingServiceUpdateDateChangeTest,BookingDtoValidationTest`：18 個測試，0 failed。
- `mvn -o checkstyle:check`：0 違規。
- `mvn -o verify`（完整回歸，`make test-db-up` 真實 postgres/redis）：**1561 個單元測試（+8）+ 482 個整合測試（持平），0 failed**，`BUILD SUCCESS`；checkstyle（main+test）0 違規。
- **前端呼叫點查證**：與 `DEF-226` 同一結論——`frontend/src/lib/api.ts` 的 `bookings` 端點註冊表無 `update`，`PUT /v2/bookings/{id}` 前端零呼叫點，但為掛 `@PreAuthorize("hasAuthority('booking:update')")` 的真實可達 REST 端點，非內部不可達死碼，故本輪直接修復。
- 未執行 `make validate-e2e`：本輪修改僅新增輸入驗證與一項業務規則檢查，不改變任何既有成功路徑（合法欄位值）的回應格式。

## 6. 驗證結果

- 紅燈階段：見 §5，DTO 層 5 個欄位、服務層 1 項業務規則檢查皆證實修復前後行為差異。
- `mvn -o verify` 完整回歸：**1561 個單元測試（+8）+ 482 個整合測試（持平），0 failed**，checkstyle 0 違規。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-227`（🟢 低優先級表）：狀態直接記為「✅ 已修復（Sprint 175）」，發現與修復同輪完成，比照 `DEF-223`~`DEF-226` 同 Sprint 內發現並處理的先例。

---

## 8. 誠實揭露總結

- **未擴大檢查 `BookingDto` 以外的其他 DTO 是否也有同型「Create 有欄位驗證、Update 完全沒有」的不對稱**：本輪的 `RoomDto` 對照已確認該檔案完全對稱（既有前例，非本輪修復），但除 `RoomDto`/`BookingDto` 外，`ProductDto`/`CmsDto`/`ChatDto`/`SupportTicketDto` 等其餘含 Create/Update 配對的 DTO（見 `grep -rl "class.*CreateRequest\|class.*UpdateRequest"` 列出的約 20 個檔案）未逐一系統性比對每個欄位的驗證註解是否對稱，只做了本輪具體發現缺口的 `BookingDto`。若未來時間允許，可寫一支腳本化工具比對每組 Create/Update DTO 的欄位交集與各自的驗證註解集合，系統性找出所有不對稱案例，而非像本輪一樣仰賴個別查證。
- **`updateBookingFields` 的其餘欄位（`guestName`/`guestPhone`/`guestEmail`/`specialRequests`）本輪只補了 DTO 層格式驗證，未額外檢查是否有對應的業務規則層級檢查缺口**——這四個欄位在 `createBooking` 端本身也只有 DTO 層格式驗證，沒有額外的服務層業務規則（不像 `guestCount` 有房源容量檢查），故對稱性已達成，非本輪遺漏。
- **`Room` 缺失時的容錯處理（`.orElse(null)` 視為無限制）沿用 `updateBooking` 既有的 `processDateRangeChange` 慣例，而非 `resolveBookableRoom` 對 `createBooking` 缺失 room 直接拋 `E_4000` 的做法**——兩種既有慣例在同一個 Service 內本就不一致（`createBooking` 路徑房源缺失即失敗，`updateBooking` 路徑視為無限制放行），本輪選擇配合 `updateBooking` 自己既有的風格而非跨方法統一，避免引入超出本次缺口範圍的行為變更。
