# Sprint 177 Plan — BookingDto.UpdateRequest 缺少日期未來性驗證（DEF-229）

**Sprint**: Sprint 177
**日期**: 2026-09-21

## 1. 起點

Sprint 176 §9 誠實揭露記錄「本輪掃描以格式類（`@Email`）與數值範圍類（`@Min`/`@Max`/`@Size` 等）為主，未系統性檢查 `@Pattern`/`@NotEmpty` 等其他 Bean Validation 約束類型在 Create/Update 間是否也有類似不對稱」。本輪針對此方向展開系統性掃描。

## 2. 掃描方法與結果

`grep -rl "@Pattern\|@NotEmpty\|@Digits\|@Future\|@Past\|@AssertTrue\|@AssertFalse"` 找出全庫 14 個使用這些約束類型的檔案，逐一比對其中屬於同一資源 Create/Update 配對的欄位交集：

- **`@Pattern`（URL 協定黑名單 `^(?!\s*(?i:javascript|data|vbscript|file):).*$` 與電話格式 `^[0-9]{8,15}$`）**：`CmsDto`（Banner `imageUrl`/`linkUrl`）、`M15Dto`（Post `featuredImageUrl`）、`ProductDto`/`RoomDto`（`coverImageUrl`）、`BookingDto`（`guestPhone`，`DEF-227` 已修復）逐一核對，Create/Update 兩側**皆已對稱**。`TenantApplicationRequest`/`TenantUpdateRequest` 的 `businessType` `@Pattern` 也對稱（`logoUrl`/`businessLicenseUrl`/`coverImageUrl` 欄位名稱不同、語意不同，非同一欄位的 Create/Update 配對，`coverImageUrl` 刻意不加 `@Pattern` 已於 Sprint 155 記錄為死欄位，非本輪範圍）。
- **`@NotEmpty`**：出現於 `erp/PurchaseOrderCreateRequest.items`、`erp/PurchaseOrderReceiveRequest.items`、`returns/ReturnDto.CreateRequest.items`、`returns/ReturnDto.ReceiveRequest.items`——確認 `PurchaseOrderUpdateRequest`（`erp/PurchaseOrderUpdateRequest.java`）本身**沒有 `items` 欄位**（程式碼註解明確記載「items 不允許在 update 時修改，若需修改應取消後重新建立」，PRD §9.15 既定設計），`Create`/`Receive`/`Reject` 彼此是不同動作的獨立 DTO，非同一資源的 Create/Update 欄位配對，不構成本比對情境。
- **`@Digits`/`@AssertTrue`/`@AssertFalse`**：全庫掃描結果 0 筆使用，無比對對象。
- **`@Future`/`@Past`/`@FutureOrPresent`/`@PastOrPresent`**：另以 `grep -rn "@Future\|@Past\b\|@FutureOrPresent\|@PastOrPresent"` 確認全庫**僅出現於 `BookingDto.java`**，且僅在 `CreateRequest`：`checkInDate` 為 `@FutureOrPresent`、`checkOutDate` 為 `@Future`。`UpdateRequest` 同名欄位（`BookingDto.java:77-78`，修復前）完全沒有對應註解——確認列為本輪修復對象（`DEF-229`）。

## 3. 缺口說明

`BookingDto.CreateRequest`（38-44 行）：
```java
@NotNull(message = "Check-in date is required")
@FutureOrPresent(message = "Check-in date must be today or in the future")
private LocalDate checkInDate;

@NotNull(message = "Check-out date is required")
@Future(message = "Check-out date must be in the future")
private LocalDate checkOutDate;
```

`BookingDto.UpdateRequest`（修復前，77-78 行）：
```java
private LocalDate checkInDate;
private LocalDate checkOutDate;
```

`BookingController.updateBooking`（`PUT /{bookingId}`）已掛 `@Valid @RequestBody BookingDto.UpdateRequest request`，故補上驗證註解即會立即生效，不需改動 Controller。

這是與 `DEF-227`（Sprint 175）**同一個 `UpdateRequest` 區塊**遺漏的欄位——`DEF-227` 修復了 `guestCount`/`guestName`/`guestPhone`/`guestEmail`/`specialRequests` 五個欄位的驗證不對稱，但 `checkInDate`/`checkOutDate` 兩個欄位當時未落在該輪「Bean Validation 註解缺失」的比對範圍內（Sprint 173/174 已針對這兩個欄位處理了**日期先後順序**的服務層驗證 `DEF-226`，容易讓人誤以為此欄位已完整覆蓋），本輪確認遺漏的是**日期本身是否為未來日期**這條獨立規則，兩者互不重疊。

**查證服務層是否已有等價保護**：`BookingService.handleDateChange`（621 行）：
```java
LocalDate newCheckIn = request.getCheckInDate() != null ? request.getCheckInDate() : booking.getCheckInDate();
LocalDate newCheckOut = request.getCheckOutDate() != null ? request.getCheckOutDate() : booking.getCheckOutDate();

if (newCheckOut.isBefore(newCheckIn)) {
    throw new BusinessException(ErrorCode.E_4003, "Check-out must be after check-in");
}
```
`DEF-226`（Sprint 174）補上的檢查只驗證合併後 `checkOut` 不早於 `checkIn`（兩者的**相對順序**），從未檢查任一日期是否已經是過去日期（**絕對時間**）。兩項檢查可以同時滿足又同時違反業務意圖：例如把 `checkInDate`/`checkOutDate` 都改成過去某兩天（仍維持 `checkOut > checkIn`），`isBefore` 檢查通過，但實際上把一筆有效訂房的入住/退房日期整段搬到了過去。後續 `processDateRangeChange` 呼叫 `roomCalendarService.lockDateRange`/`isDateRangeAvailable`，這兩個方法本身只關心「該日期範圍在日曆上是否已被佔用」，不會排斥過去日期（過去的日期通常本就沒有其他訂房佔用，反而容易鎖定成功）。故此輸入會直接寫入成功，回應 200 無任何錯誤或警告，與 `DEF-225`/`DEF-227` 同一種「靜默業務邏輯損毀」性質。

## 4. 修法決策

- 純 DTO 層修復：`UpdateRequest.checkInDate`/`checkOutDate` 補上與 `CreateRequest` 相同的 `@FutureOrPresent`/`@Future` 註解，**不含** `@NotNull`（保留 partial update 語意——欄位省略仍合法），比照 `DEF-227` 對同一 `UpdateRequest` 其餘欄位的既有修復慣例（Rule 11：配合既有慣例）。
- 不需改動 `BookingService`：`DEF-226` 已確立的順序檢查（`newCheckOut.isBefore(newCheckIn)`）與本輪的未來性檢查是兩條獨立規則，DTO 層驗證會在進入 Controller 方法前就攔截，不需要在服務層重複實作。

## 5. 修復內容

`BookingDto.java`（`UpdateRequest`）：
```java
@FutureOrPresent(message = "Check-in date must be today or in the future")
private LocalDate checkInDate;

@Future(message = "Check-out date must be in the future")
private LocalDate checkOutDate;
```

## 6. 測試

- **紅燈先行**：`BookingDtoValidationTest` 新增 4 案例：
  - `checkInDatePast_failsValidation`：`checkInDate` 為昨天應被拒絕——修復前實測失敗（`Expecting actual not to be empty`，即驗證器對過去日期完全沒有產生違規），證實缺口存在。
  - `checkInDateTodayOrFuture_passesValidation`：`checkInDate` 為今天或明天應通過驗證。
  - `checkOutDateNotStrictlyFuture_failsValidation`：`checkOutDate` 為今天或昨天皆應被拒絕（`@Future` 要求嚴格未來，today 不算）——修復前實測失敗，證實缺口存在。
  - `checkOutDateFuture_passesValidation`：`checkOutDate` 為明天應通過驗證。
- 套用修復後重跑 `BookingDtoValidationTest`：11 個測試（既有 7 個 + 新增 4 個）全數轉綠。
- 服務層既有測試套件（`BookingServiceUpdateDateChangeTest`/`BookingServiceOwnershipTest`/`BookingPromoCodeTest`/`BookingServiceCreateBookingTest`，共 52 個測試）重跑全數通過——這些測試直接呼叫 Service 方法（Mockito 單元測試），不經過 `@Valid` 攔截，故不受 DTO 註解變更影響，符合預期。
- `mvn -o checkstyle:check`：0 違規。
- `mvn -o verify`（`make test-db-up` 真實 postgres/redis）：完整回歸結果見 §7。

## 7. 驗證結果

- 紅燈階段：`checkInDatePast_failsValidation`/`checkOutDateNotStrictlyFuture_failsValidation` 修復前失敗（2/4），確認缺口存在；修復後全數轉綠。
- `mvn -o verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1569 個單元測試（+4）+ 482 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。
- **前端呼叫點查證**：與 `DEF-226`/`DEF-227` 相同結論——`frontend/src/lib/api.ts` 的 `bookings` 端點註冊表無 `update`，`PUT /v2/bookings/{id}` 前端零呼叫點，但為掛 `@PreAuthorize("hasAuthority('booking:update')")` 的真實可達 REST 端點，非內部不可達死碼，故本輪直接修復。
- 未執行 `make validate-e2e`：本輪修改僅新增輸入驗證，不改變任何既有成功路徑（合法未來日期）的回應格式。

## 8. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-229`：狀態直接記為「✅ 已修復（Sprint 177）」，發現與修復同輪完成。

---

## 9. 誠實揭露總結

- **本輪掃描的 `@Pattern`/`@NotEmpty`/`@Digits`/`@Future`/`@Past`/`@AssertTrue`/`@AssertFalse` 七種約束類型已是 Jakarta Bean Validation 內建約束的完整清單**（不含本專案未使用的 `@Email`/`@Min`/`@Max`/`@DecimalMin`/`@DecimalMax`/`@Positive`/`@Negative`/`@Size`/`@NotNull`/`@NotBlank`，這些已於 Sprint 173~176 系統性掃描過），加上本輪，全部 Bean Validation 內建約束類型皆已至少掃描過一輪。但**自訂約束（如可能存在的 `@ValidEnum` 等自訂 annotation）未納入本次掃描**，`grep` 過程中未發現本專案有自訂 Bean Validation 約束，如實記錄以防遺漏。
- **`@Future`/`@Past` 系列只出現在 `BookingDto` 一處，樣本數極小（1 組發現）**，不足以判斷這是否為本專案的另一個反覆出現的模式，或僅是單一巧合案例——`BookingDto` 本身已是連續四輪（`DEF-226`/`DEF-227`）被發現有驗證缺口的「熱點」DTO，本輪發現的欄位剛好落在同一個類別，可能只是同一處遺漏被分批發現，而非新的系統性模式。
- 這是延續 Sprint 173~177 五輪的「create 有驗證、update 沒有」掃描角度第五個真實發現（`DEF-225`/`DEF-226`/`DEF-227`/`DEF-228`/`DEF-229`）。隨著本輪完成 Bean Validation 內建約束類型的全面掃描，且系統性掃描的候選集持續縮小（Sprint 176 的 22 組配對僅 1 組、本輪的 14 個候選檔案僅 1 組），這個掃描角度可能已接近完全收斂，下一輪若使用者同意應考慮切換到全新角度（例如重新檢視 [[read-modify-write-race-playbook]] 記錄的併發缺口、或 [[frontend-backend-contract-drift-sweep]] 尚未掃描過的模組）。
