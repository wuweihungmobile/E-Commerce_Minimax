# Sprint 93 Plan — API 限流機制（Token Bucket）

**Sprint**: Sprint 93
**日期**: 2026-07-18
**主題**: PRD §3.3/§13.4/§16.4.2 明列的 **Phase 1 已凍結**「每租戶 100 req/min」API 限流機制，重新全面比對 PRD v1.0 Final 全文後發現的落地缺口（AI-2427），92 個 Sprint 以來從未被任何追蹤文件記錄。

---

## 1. 缺口盤點結果（背景）

主動重新逐行核對 PRD 全文（而非只看既有的 `PRODUCT_BACKLOG.md`/`DEFERRED_ITEMS_TRACKER.md`，兩者本身可能過時，如 Sprint 91 M18 客服工單子系統的先例）後，發現：

- **PRD §3.3** 條款 4：「面對跨年搶房或雙 11 大促，API Gateway 需啟動令牌桶 (Token Bucket) 限流」
- **PRD §13.4**（Phase 1 多租戶容量指標，明文標註「已凍結，不可變更」）：「API 限流策略｜每租戶 100 req/min｜由 API Gateway 執行，超過限流回傳 429」
- **PRD §16.4.2**：定義 429 回應需含 `X-RateLimit-Limit`/`X-RateLimit-Remaining`/`X-RateLimit-Reset`/`Retry-After` headers、錯誤碼 `E-6001`

全庫搜尋 `RateLimit`/`TokenBucket`/`Bucket4j`/`resilience4j` 皆零結果，兩份追蹤文件也從未提及。與 M18 客服工單同一種「規格明確要求、但沒人注意到」的疏漏模式，且此項規格細節（錯誤碼、headers）比 M18 更完整，判斷為高信心缺口，無業務判斷空間，直接排入本 Sprint。

## 2. 規格落差與工程決策

1. **`E-6001` 撞碼**：PRD §16.4.2 寫的限流錯誤碼 `E-6001` 與既有 `ErrorCode.E_6001`（付款失敗）衝突。改用已預留、已映射 429、從未被使用過的 `ErrorCode.E_9904`（"已超過速率限制"）。
2. **回應 body 格式**：PRD §17.2.3 定義巢狀 `{error:{code,message,details,requestId,timestamp}}`，現有 `ApiResponse` 是扁平格式且無 `requestId` 欄位（專案全域無 `X-Request-ID`/trace id 機制可串接）。依 Rule 11（配合既有慣例）沿用 `ApiResponse.error(code, message)`，不臨時新增 `requestId` 機制。
3. **限流維度**：PRD 全文僅明確要求「每租戶」，未提及 per-IP/per-user 疊加，故只實作每租戶維度。
4. **例外路徑**：`/v2/auth/**`（登入前無租戶身分可綁定，比照 `TenantContextFilter.shouldNotFilter` 既有排除範圍）、`/actuator/**`（健康檢查，非業務 API 流量）不納入限流。PRD 未提及此例外，屬工程範圍決策。
5. **Fail-open**：Redis 故障時放行請求並記錄 error log，避免快取層故障波及全站可用性。PRD 未定義，屬工程決策。
6. **不接 FeatureToggle**：`FeatureToggleService.isFeatureEnabled` 對查無 toggle row 的租戶預設回傳 `false`（fail-open =「預設關閉」），若用它包一層會讓 PRD 要求「所有租戶皆適用」的 Phase 1 機制預設對所有既有租戶失效，違反 PRD 明確要求，故限流邏輯不透過 toggle 開關，一律啟用。

## 3. 實作內容

1. **`RateLimitFilter`**（新檔，`api/filter/`）：`OncePerRequestFilter`，Redis + 單支 Lua script 原子讀取-補充-扣除 token bucket（容量 100、每 60 秒補滿），注入 `StringRedisTemplate`（本專案第一支 Lua script，`IdempotencyService` 等既有 Redis 機制皆非原子多步驟操作，無前例可抄）。
2. **`SecurityConfig`**：`addFilterAfter(rateLimitFilter, TenantContextFilter.class)`（需在 `TenantContext` 已解析完成、其 `finally` clear 之前執行），`corsConfigurationSource` 的 `exposedHeaders` 新增 4 個限流 header（否則前端 JS 讀不到）。
3. **測試**：
   - `RateLimitFilterTest`（單元，Mockito 模擬 script 回傳值）：路徑排除、null 租戶跳過、允許時 headers、拒絕時 429+body、Redis 故障 fail-open。
   - `RateLimitFilterIntegrationTest`（真 Redis，需 `make test-db-up`；刻意不用 `@SpringBootTest`+`IntegrationTestConfiguration`，因後者將 `RedisConnectionFactory`/`RedisTemplate` 標為 `@Primary` mock，會讓測試看不到真實 Lua script 行為）：同租戶燒光 100 配額後必然出現拒絕、不同租戶配額互相獨立。

## 4. 驗證結果

- 後端單元測試 7 tests 0 fail（`RateLimitFilterTest`）
- 真 Redis 整合測試 2 tests 0 fail（`RateLimitFilterIntegrationTest`，`make test-db-up`）
- Checkstyle 0 violations、PMD 0 violations
- 全量回歸 `mvn verify -Pintegration-test`：詳見 commit 訊息（因異動核心 Security filter chain，比照 DEF-038 前例執行完整回歸而非僅 `mvn test`）
- `make validate-schema`：無 migration，schema-free

**誠實記錄全量回歸過程中抓到的兩個真實 bug（皆在 push 前修復並重跑驗證，未帶病上路）**：

1. **第一輪**：`mvn verify -Pintegration-test` 335/369 個整合測試失敗（`NullPointerException: Cannot invoke "String.split(String)" because "result" is null`，發生於 `RateLimitFilter.doFilterInternal`）。根因：既有 `IntegrationTestConfiguration`（供絕大多數 `@SpringBootTest` 整合測試使用）將 `RedisConnectionFactory` 標為 `@Primary` Mockito mock，導致 `StringRedisTemplate.execute(...)` 靜默回傳 `null`（非拋出 `DataAccessException`），而原始程式碼未防禦此情況直接對 `null` 呼叫 `.split(":")`。修復：新增 `result == null` 檢查，比照 Redis 故障同樣 fail-open，並新增單元測試 `redisScriptReturnsNull_failsOpenAndContinuesChain`。
2. **第二輪**：修復第 1 項後仍有 27 個測試失敗（`M08Review*IntegrationTest`/`M18KnowledgePhase2IntegrationTest`），根因不同：這 4 個測試用 `@WebMvcTest`（Web 層窄切片），不會自動配置 `RedisAutoConfiguration`，導致 `SecurityConfig` 需要的 `RateLimitFilter` 建構子找不到 `StringRedisTemplate` bean，整個測試 context 啟動失敗。原以為 `@Autowired(required=false)` 可讓此依賴變成可選，但驗證後發現此標記對「單一建構子」無效（Spring 仍會嘗試完整解析該建構子的所有參數，`required=false` 只在存在多個建構子可退回選擇時才有意義）——這是修復過程中自我推翻的一次誤判，實測後才發現不成立。改用 Spring 官方文件記載、對此情境真正有效的 `ObjectProvider<StringRedisTemplate>` 建構子參數（`getIfAvailable()` 於 bean 不存在時回傳 `null` 而非拋例外），並新增測試 `noRedisTemplateBean_skipsRateLimitEntirely` 驗證此路徑。

第三輪完整 `mvn verify -Pintegration-test` 全數通過：**369 tests 0 failures 0 errors**，`make validate-schema` 無漂移（無 migration）。

## 5. 範圍外（延後）

- A2：PRD §1.5.1 會員資料 Export + 帳戶刪除（被遺忘權）——需先補規格再排 Sprint，排入下一輪。
- PRD §16.4.2 原文的巢狀錯誤格式（`error:{...,requestId,...}`）與 `E-6001` 碼——與現行 `ApiResponse`/`ErrorCode` 慣例有落差，本 Sprint 依 Rule 11 選擇沿用既有慣例，落差記錄於此供未來 PRD Errata 對齊。
- Per-IP/per-user 疊加限流、租戶自訂限流值（differentiated quota）——PRD 未要求，未實作。
