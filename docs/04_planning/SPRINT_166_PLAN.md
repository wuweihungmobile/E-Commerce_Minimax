# Sprint 166 Plan — 全庫掃描手動解析（`UUID.fromString`/config 數值）未包 try/catch 的未攔截例外

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 活躍延後項目已無待排程項目（DEF-215/DEF-216 已於 Sprint 164/165 修復），Sprint 165 §6 誠實揭露的分頁 `page`/`size` 回顯欄位落差屬於「零消費端、不影響資料正確性」的既有小落差（`grep` 全庫確認前端無任何頁面讀取回應中的 `page`/`size` 欄位做分頁運算），比照既有 `DEF-172`/`DEF-173` 判準不排入本輪範圍，僅口頭記錄供未來評估。

本輪改選新角度：延伸 Sprint 161~163「未攔截例外落入全域 catch-all 變 500」主題，掃描 `enum valueOf()`/`@PathVariable`/`@RequestParam` 型別轉換之外，**手動呼叫 `UUID.fromString()` 或其他手動數值解析**是否也有同類未防護缺口。

## 2. 掃描範圍與方法

- `grep -rn "UUID\.fromString("` 全庫共 28 處、15 個檔案，逐一檢視呼叫端是否已有 `try/catch`、輸入是否為攻擊者可控。
  - 24 處確認已有 `try/catch`（`IdempotencyService`/`StompAuthChannelInterceptor`/`PostService`/`MediaService`/`RedisCartService` 等）或輸入來源為簽章驗證過的 JWT claim／硬編常數（信任內部值，非外部輸入）。
  - **1 處未防護**：`TenantContextFilter.resolveEffectiveTenantId`（見 §3 `DEF-217`）。
- 同一輪延伸檢查 `Integer.parseInt`/`Long.parseLong`/`new BigDecimal(String)`/`Double.parseDouble` 等其他手動數值解析，共 4 處：
  - `RateLimitFilter`/`StripeSignatureVerifierService` 3 處皆已有防護或輸入為伺服器端自產字串（Lua script 回傳值），非外部輸入。
  - **1 處未防護**：`PricingService.applyProductRule` 的 `discountPercent` 折扣向後相容分支（見 §3 `DEF-218`）。

## 3. 修復內容

### `DEF-217`：`TenantContextFilter` 對 `X-Tenant-ID` header 手動解析未包 try/catch

`resolveEffectiveTenantId`（`TenantContextFilter.java:150-165`）的 SUPER_ADMIN 分支對 `X-Tenant-ID` header 值直接呼叫 `UUID.fromString(requestedTenantId)`，未包 try/catch。此 header 由**已通過身分驗證的 SUPER_ADMIN 使用者**自行提供（平台級跨租戶管理功能，`DEF-038` 既有設計），格式錯誤時（如管理端工具誤傳、手動 curl 打錯字）會拋出未攔截的 `IllegalArgumentException`。

**與 Sprint 162/163 的關鍵差異**：`TenantContextFilter` 是 Servlet Filter，執行於 DispatcherServlet **之前**（`SecurityConfig` 註冊順序：`JwtAuthenticationFilter → TenantContextFilter → RateLimitFilter → ... → AuthorizationFilter`）。`GlobalExceptionHandler`（`@RestControllerAdvice`）只能攔截 DispatcherServlet 分派過程中拋出的例外，對 Filter 鏈中拋出的例外結構性攔不到——這正是 Sprint 162/163 已建立的「全域 400」修法在此處失效的原因，必須在 Filter 內就地處理。

**紅燈先行**（`TenantContextFilterTest`，plain JUnit 直接呼叫 `filter.doFilter(...)`，不需 Spring 容器/DB）：SUPER_ADMIN 帶入 `X-Tenant-ID: not-a-real-uuid`，修復前 `IllegalArgumentException` 直接從 `filter.doFilter(...)` 拋出（未被任何地方攔截）；修復後回應 400 + `E-9000`，`filterChain` 不被呼叫（請求在 filter 內被擋下，不會帶著未設定的 `TenantContext` 繼續往下傳遞）。

**修法**：比照既有 `RateLimitFilter`（唯一先例：filter 內直接寫回應）——包 try/catch 圍住 `resolveEffectiveTenantId(...)` 呼叫，例外時呼叫新增的 `writeInvalidTenantIdResponse`，直接以 `ApiResponse.error(ErrorCode.E_9000.getCode(), "X-Tenant-ID 格式錯誤")` 寫入 400 JSON 回應並 `return`（不呼叫 `filterChain.doFilter`）。`ObjectMapper` 採用與 `RateLimitFilter` 相同的自建（非 `@Autowired`）+ 註冊 `JavaTimeModule` 寫法，理由相同：`TenantContextFilter` 在既有 `TenantContextFilterTest` 中會被直接 `new` 出來（不經 Spring 容器），必須維持無參建構子相容；`ApiResponse.timestamp`（`Instant`）沒有 `JavaTimeModule` 會序列化失敗（紅燈階段已實際踩到，見 §4）。

新增 `TenantContextFilterTest.superAdminInvalidTenantHeaderFormat_doesNotThrowAndShortCircuits`。

### `DEF-218`：`PricingService` PRODUCT 折扣向後相容分支對 `discountPercent` 手動解析未包 try/catch

`applyProductRule`（`PricingService.java:621-641`）的「向後相容」折扣分支對 `config.get("discountPercent")` 直接呼叫 `new BigDecimal(discountPct.toString())`，未包 try/catch——與同檔案其餘**所有**其他 config 數值讀取（`getIntConfig`/`getDoubleConfig`，含同一個方法內先呼叫的 `applyProductMarkup` 內部三處）已有的 null-safe try/catch 防護不一致，是本檔案唯一的例外。

`PricingRule.config` 是 `Map<String, Object>`（`PricingDto` 的 `CreateRuleRequest`/`UpdateRuleRequest` 同型宣告，無型別限制），寫入端 `PricingController`（`POST/PUT /v2/dashboard/pricing/rules`，`room:create`/`product:create`/`room:update`/`product:update` 權限）不驗證 `config` 內容型別，賣家送入非數字字串（如誤填含 `%` 符號的 `"10%"`）會被原樣存入且無任何拒絕。讀取端 `getEffectivePrice` 由 `ListingController.getEffectivePrice`（`GET /v2/listings/{id}/effective-price`，`product:read`/`room:read` 即可呼叫——**一般買家瀏覽商品頁即會觸發**）與 `RedisCartService`（加入購物車時查詢有效售價）共用呼叫。**故此缺陷的影響面比 `DEF-217` 更廣：任一賣家對自己的 PRODUCT 商品設定格式錯誤的折扣規則，會讓「任何」瀏覽或加購該商品的買家都收到未攔截例外，而非僅發生在操作者本人**。

**紅燈先行**（`PricingServiceTest.ProductEffectivePriceTests.discountPercentNonNumericString_doesNotThrow`，沿用既有 mock repository 基礎設施，不需真實 DB）：`config = Map.of("discountPercent", "10%")`、`ruleType = SEASONAL`（先落空 `applyProductMarkup` 的 `multiplier` 讀取，才會進入向後相容分支，與既有 `UT-M12-018` 同一路徑），修復前 `getEffectivePrice` 拋出未攔截的 `NumberFormatException`；修復後回退為原價（比照 `getDoubleConfig` 既有的 null-safe 語意：解析失敗視為未設定，不拋例外）。

**修法**：改用既有的 `getDoubleConfig(rule, "discountPercent")`（本檔案已有、其餘所有 config 讀取都在用的同一個 null-safe 輔助方法）取代直接 `new BigDecimal(discountPct.toString())`，不新增任何抽象。

新增 `PricingServiceTest.ProductEffectivePriceTests.discountPercentNonNumericString_doesNotThrow`。

## 4. 驗證結果

- 紅燈階段：兩個新測試對修復前程式碼皆如預期失敗（`TenantContextFilterTest` 新案例：`IllegalArgumentException` 未攔截直接拋出，測試以 Error 形式失敗；`PricingServiceTest` 新案例：`NumberFormatException` 未攔截拋出，`assertThatCode(...).doesNotThrowAnyException()` 失敗）。
- 修復 `TenantContextFilter` 第一版時，紅燈階段意外揭露 `ApiResponse.timestamp`（`Instant`）在自建 `ObjectMapper` 未註冊 `JavaTimeModule` 時序列化失敗（`InvalidDefinitionException`）；比照 `RateLimitFilter` 既有寫法補上 `JavaTimeModule` 註冊後解決，非新增缺陷，純建構工具方法本身的必要前置設定。
- 綠燈：`mvn -o test -Dtest=TenantContextFilterTest,PricingServiceTest` → 42 passed / 0 failed（`TenantContextFilterTest` 9 個、`PricingServiceTest` 33 個，含兩個新案例）。
- 完整回歸：`mvn -o verify` **1377 個單元測試（+2）+ 480 個整合測試（持平），0 failed**，checkstyle（main + test）0 違規，`BUILD SUCCESS`。

## 5. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-217`（已修復，見 §3）。
- 新增 `DEF-218`（已修復，見 §3）。

---

## 6. 誠實揭露總結

- `UUID.fromString`/手動數值解析的全庫掃描聚焦於「呼叫端是否已有 try/catch」與「輸入是否攻擊者可控」兩個問題，未逐一重新驗證每個「已有 try/catch」案例的錯誤回應內容是否恰當（例如 catch 後的 fallback 值是否合理）——這 24+3 處維持原樣，僅確認例外不會未攔截外洩。
- `TenantContextFilter.resolveEffectiveTenantId` 的其餘分支（`AppConstants.SYSTEM_TENANT_ID` 常數、`userTenantId` 來自簽章 JWT claim）理論上仍可能因程式內部錯誤（而非外部輸入）拋出同一種例外，本次修法用同一個 try/catch 圍住整個方法呼叫，一併涵蓋，未逐分支個別判斷是否「真的需要」防護——判斷是防護範圍寧可涵蓋整個方法呼叫，也不要只精準防護已知的攻擊路徑而遺漏其他分支未來被改動時引入的同類問題。
- `PricingService` 的 ROOM 定價路徑（`calculateAdjustment`）本就全數使用 `getDoubleConfig`/`getIntConfig`，未受此缺陷影響；`DEF-218` 純粹是 PRODUCT 路徑的「向後相容」分支（S44 引入）忘記套用同一慣例，屬於程式碼一致性疏漏而非架構設計缺陷。
