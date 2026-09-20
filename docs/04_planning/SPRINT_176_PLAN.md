# Sprint 176 Plan — SupplierUpdateRequest.email 缺少格式驗證（DEF-228）

**Sprint**: Sprint 176
**日期**: 2026-09-21

## 1. 起點

Sprint 175 §8 誠實揭露記錄「除 `RoomDto`/`BookingDto` 外，`ProductDto`/`CmsDto`/`ChatDto`/`SupportTicketDto` 等其餘含 Create/Update 配對的 DTO（約 20 個檔案）未逐一系統性比對每個欄位的驗證註解是否對稱」。本輪針對此方向系統性補齊。

## 2. 掃描方法與結果

`grep -rl "class.*Request"` 找出全庫 22 組含 Create/Update 配對的 DTO 檔案，逐一比對「Create 有數值範圍類驗證（`@Min`/`@Max`/`@Positive`/`@Size`/`@DecimalMin`/`@DecimalMax`）、Update 對應欄位仍存在但驗證被拿掉、且 Service 層也沒有手動補等價業務規則檢查」的嚴格模式（`@NotNull`/`@NotBlank` 在 Update 被拿掉屬合理 partial update 設計，不計入候選）。

**結果**：22 組配對中，符合嚴格「數值範圍類」不對稱模式的候選為 0 組——`AddressDto`/`ProductDto`/`ReviewDto`/`ShippingTemplateDto`/`NotificationTemplateDto`/`ReturnDto`/ERP（`PurchaseOrder`/`Supplier`）/`faq`/`knowledge`/`media`/`TenantApplicationRequest`+`TenantUpdateRequest` 等組合的數值範圍驗證皆對稱保留，其餘配對（`AdminDto`/`BookingReviewDto`/`CheckoutDto`/`LogisticsDto`/`OrderDto`/`ChatDto`/`CmsDto`/`SupportTicketDto`）則因欄位語意不同（Create 與所謂「Update」根本沒有共同欄位交集，或該資源本就無 Update 端點）不構成本比對情境。

過程中發現一項**超出「數值範圍」原始比對範疇、但性質相同**的候選：`erp/SupplierCreateRequest.email` 有 `@Email(message = "Invalid email format")`，但 `erp/SupplierUpdateRequest.email` 完全沒有對應註解（格式類驗證而非數值範圍類，故未落在原始 22 組表格的候選欄位，但同屬「Create 有驗證、Update 沒有」的不對稱家族），確認列為本輪修復對象（`DEF-228`）。

另記錄一項**範圍外揭露**（非本輪處理）：`ShippingTemplateDto` 的 `fixedAmount`/`freeThreshold` 在 Create **和** Update **兩側皆完全沒有**任何數值下限驗證（`ShippingTemplateService.createTemplate`/`updateTemplate` 亦無手動負值檢查）——這是對稱性的弱驗證，不符合本輪「Create 有、Update 被拿掉」的不對稱定義，留待未來另案評估。

## 3. 缺口說明

`erp/SupplierCreateRequest.java`（17-28 行）：
```java
@NotBlank(message = "Name is required")
private String name;
private String contactPerson;
@Email(message = "Invalid email format")
private String email;
private String phone;
private String address;
```

`erp/SupplierUpdateRequest.java`（修復前，13-25 行）：五個欄位（`name`/`contactPerson`/`email`/`phone`/`address`）+ `status`，`email` 完全沒有 `@Email` 或任何格式驗證。

`ErpController` 兩個端點皆已掛 `@Valid`：
```java
@PostMapping("/suppliers")
public ... createSupplier(@Valid @RequestBody SupplierCreateRequest request) { ... }

@PutMapping("/suppliers/{id}")
public ... updateSupplier(@PathVariable UUID id, @Valid @RequestBody SupplierUpdateRequest request) { ... }
```

`SupplierService.updateSupplier`（106-138 行）對 `email` 只做 `if (request.getEmail() != null) { supplier.setEmail(request.getEmail()); }`，沒有任何手動格式檢查即直接寫入。故建立供應商時格式不符的 email（如 `not-an-email`）會被 `@Valid` 攔截回 400，但更新時卻會靜默寫入成功，回應 200 無任何錯誤——與 `DEF-227` 同一種「create 有驗證、update 沒有」不對稱模式，差別在於這是純格式驗證缺口（無對應業務規則層級的額外檢查需要，因為 `createSupplier` 端本身也只靠 `@Email` 註解把關，沒有服務層手動邏輯）。

## 4. 修法決策

- 純 DTO 層修復：`SupplierUpdateRequest.email` 補上與 `SupplierCreateRequest` 相同的 `@Email(message = "Invalid email format")` 註解。
- 不需改動 `SupplierService`：`@Valid` 在進入 controller 方法前即攔截，格式不符的請求根本不會呼叫到 `updateSupplier`；`createSupplier` 端也是同樣機制（純註解把關、無服務層手動檢查），故兩端修復後對稱一致，符合既有慣例（Rule 11：配合程式碼庫慣例）。
- 不加 `@NotNull`/`@NotBlank`：`email` 在 Update 端維持可省略的 partial update 語意，與 `contactPerson`/`phone`/`address` 等其餘欄位一致。

## 5. 修復內容

`SupplierUpdateRequest.java`：
```java
import jakarta.validation.constraints.Email;

...

@Email(message = "Invalid email format")
private String email;
```

## 6. 測試

- **紅燈先行**：新增 `SupplierDtoValidationTest`（`backend/src/test/java/com/nextkey/ecommerce/api/dto/erp/`，比照既有 `BookingDtoValidationTest`/`RoomDtoValidationTest` 的 `Validator.validate(...)` 手法），4 個案例：
  - `allFieldsNull_passesValidation`：全部欄位為 `null` 應通過驗證（partial update 語意守衛）。
  - `emailInvalidFormat_failsValidation`：`"not-an-email"` 應被拒絕——修復前 `mvn -o test -Dtest=SupplierDtoValidationTest` 實測此案例失敗（`Expecting actual not to be empty`，即驗證器完全沒有產生任何違規），證實缺口存在。
  - `emailValidFormat_passesValidation`：合法格式應通過。
  - `createRequestEmailInvalidFormat_failsValidation`：確認 `CreateRequest` 既有行為做對照基準（本就存在，非本輪修復對象，僅作對照）。
- 套用修復後重跑 `SupplierDtoValidationTest`＋既有 `SupplierServiceTest`：4+11 個測試全數轉綠。
- `mvn -o verify`（`make test-db-up` 真實 postgres/redis）：完整回歸結果見 §7。

## 7. 驗證結果

- 紅燈階段：`emailInvalidFormat_failsValidation` 修復前失敗（1/4），確認缺口存在；修復後全數轉綠。
- `mvn -o verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1565 個單元測試（+4）+ 482 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。
- ⚠️ 過程記錄：首次執行忘記先 `make test-db-up`，11 個帶 `@ActiveProfiles("integration-test")` 的 `@SpringBootTest`（`PaginationBoundaryValidationTest`/`PostControllerFilterValidationTest`/`RequestParamTypeMismatchValidationTest`/`StripeWebhookReachabilityTest`/`SellerDashboardServiceCacheTest`）因 `ApplicationContext` 載入失敗而全數報錯——比照既有 memory 記載的已知陷阱（此類測試需真實 postgres，非 H2），啟動測試 DB 後重跑即全數通過，非本輪修復引入的新問題。

## 8. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-228`：狀態直接記為「✅ 已修復（Sprint 176）」，發現與修復同輪完成。

---

## 9. 誠實揭露總結

- **22 組配對的系統性掃描已完成，但範疇僅限「Create 有驗證、Update 完全拿掉同名欄位驗證」的直接不對稱**；未逐一檢查「Create 和 Update 兩側都缺驗證」（弱驗證但對稱）的情況是否構成獨立缺陷——`ShippingTemplateDto.fixedAmount`/`freeThreshold`（§2 已記錄）與 `erp/SupplierUpdateRequest`（修復前）的 `email` 皆屬此類但後者恰好有 Create 端可對照才被發現；若 Create 端本身就沒有驗證（如 `ShippingTemplateDto`），本輪方法論無法揭露，需要另一種「業務上該有驗證卻兩側都沒有」的掃描角度，非本輪範圍。
- **本輪掃描以格式類（`@Email`）與數值範圍類（`@Min`/`@Max`/`@Size` 等）為主，未系統性檢查 `@Pattern`/`@NotEmpty` 等其他 Bean Validation 約束類型在 Create/Update 間是否也有類似不對稱**——22 組配對逐一人工讀取原始碼時已一併留意主要註解類型，但未寫窮舉腳本逐一列舉每個約束 annotation 種類，仍有遺漏可能。
- 這是延續 Sprint 173~176 四輪的「create 有驗證、update 沒有」掃描角度第四個真實發現（`DEF-225`/`DEF-226`/`DEF-227`/`DEF-228`），累計橫跨 `PricingService`/`BookingService`/`BookingDto`/`SupplierUpdateRequest` 四個不同模組，顯示此為本專案反覆出現的架構性疏漏模式，非單一模組個案。
