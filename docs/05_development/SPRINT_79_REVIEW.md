# Sprint 79 Review / Sprint 79 評審會議

> **Sprint 編號**: Sprint 79
> **期間**: 2026-07-06
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: `IdempotencyService`/`FeatureToggleService` 單元測試補齊（先前皆為零覆蓋）+ 修復 `DEF-039`；多 Sprint 測試強化計劃（源自 Sprint 66）最後一輪

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `IdempotencyService`/`FeatureToggleService` 範圍探查 | 1 | ✅ 完成 |
| US-002 | 修復 `DEF-039` + `IdempotencyService` 單元測試補齊 | 3 | ✅ 完成 |
| US-003 | `FeatureToggleService` 單元測試補齊 | 1 | ✅ 完成 |

**5 SP 全數完成**。本 Sprint 延續多 Sprint 測試強化計劃，探查 `IdempotencyService`（6 個 public 方法）與 `FeatureToggleService`（2 個 public 方法）。`FeatureToggleService` 設計正確（一律以 `TenantContext.getCurrentTenant()` 查詢，無缺口）；`IdempotencyService` 發現 **DEF-039**：Redis key 未做租戶/使用者範圍化，紅燈測試證實跨租戶重放相同 Idempotency-Key 會導致資料洩漏，修復方案有清楚前例（`RedisCartService.getCartKey`）可循，依指示直接修復未停下確認。

---

## 2. 交付內容

### 探查結論（US-001）

- **`IdempotencyService`（95 行）6 個 public 方法**：`checkAndMark`/`markCompleted`/`getStoredResponse`/`isStillProcessing`/`remove` 的 Redis key 修復前僅為 `"idempotency:" + idempotencyKey`，`isValidUuidV4` 為純格式檢查。
- **`FeatureToggleService`（61 行）2 個 public 方法**：`checkFeatureEnabled`/`isFeatureEnabled` 皆以 `TenantContext.getCurrentTenant()` 查詢，無呼叫端可覆寫的租戶參數。

### 安全修復（US-002，DEF-039）

- **漏洞**：`IdempotencyService` 的 Redis key 未範圍化，`Idempotency-Key` 由客戶端 HTTP header 提供（唯一呼叫端 `BookingController`），僅驗證 UUID v4 格式、不具機密性。不同租戶/使用者若巧合或重放送出相同值，會互相碰撞——後送出的一方被誤判為重複請求，且可透過 `getStoredResponse` 讀到先送出一方已儲存的完整訂房回應內容。
- **紅燈測試**：新增 `IdempotencyServiceTenantIsolationTest.checkAndMark_sameKeyDifferentTenant_mustNotLeakStoredResponse`，使用真實 `HashMap` 模擬 Redis SETNX/GET/SET 語意（而非固定 stub），修復前執行：租戶 B 的 `checkAndMark` 回傳 `false`（應為 `true`），斷言失敗，證實漏洞成立。
- **修復**：新增 `buildKey(idempotencyKey)` private helper，比照 `RedisCartService.getCartKey(userId, tenantId)` 前例，將 `TenantContext.getCurrentTenant()`/`getCurrentUser()` 納入 Redis key（`"idempotency:" + tenantId + ":" + userId + ":" + idempotencyKey`）；`checkAndMark`/`markCompleted`/`getStoredResponse`/`isStillProcessing`/`remove` 五個方法統一改用此 helper。修復後紅燈測試轉綠，且同租戶同使用者重複請求的既有冪等行為（客戶端重試場景）不回歸。

### 測試補強（US-002 + US-003）

- **`IdempotencyServiceTenantIsolationTest.java`（新檔，2 個測試）**：跨租戶洩漏紅燈→綠燈證明 + 同租戶同使用者重複請求不回歸。
- **`IdempotencyServiceTest.java`（新檔，12 個測試）**：涵蓋全部 6 個 public 方法一般行為（含 `isValidUuidV4` 的 null/長度不符/非 v4 版本/含非法字元等邊界案例）。
- **`FeatureToggleServiceTest.java`（新檔，7 個測試）**：涵蓋 2 個方法的啟用/停用/查無紀錄情境 + 跨租戶情境查詢隔離確認（`ArgumentCaptor`）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ 紅燈測試撰寫 → 編譯 → 執行（確認漏洞成立）→ 修復 → 編譯 → 執行（轉綠）→ 補齊其餘測試 → 編譯 → 執行，未累積 |
| `IdempotencyServiceTenantIsolationTest`（新檔） | ✅ 2 tests，紅燈→綠燈，0 fail |
| `IdempotencyServiceTest`（新檔） | ✅ 12 tests，0 fail |
| `FeatureToggleServiceTest`（新檔） | ✅ 7 tests，0 fail |
| 後端全量單元回歸（`mvn test`，`make test-db-up` 後） | ✅ 0 fail（含本 Sprint 新增 21 個） |
| 後端全量整合回歸（`mvn verify -Pintegration-test`） | ✅ **1191 tests 0 fail**（單元/surefire 849 + 整合/failsafe 342），因本 Sprint 修改生產程式碼 `IdempotencyService.java`，依政策執行 |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更） |
| 驗證方式選擇 | 依全量回歸頻率政策：本 Sprint 修改了生產程式碼，需執行 `mvn verify -Pintegration-test`（已執行） |

### 附加修正：Sprint 77 遺留的 checkstyle-test 違規

第一次執行 `mvn verify -Pintegration-test`（本 Sprint 首次觸發，因 Sprint 77/78 皆為純測試 Sprint、依政策僅需 `mvn test`，未曾跑過綁定在 verify phase 的 `checkstyle-test` execution）時，`EXIT_CODE=1`，非測試失敗，而是 `checkstyle-test` 抓到 `NotificationServiceTest.java`（Sprint 77 新增）3 個未使用 import：`org.mockito.ArgumentMatchers.anyInt`、`org.springframework.data.domain.Page`、`org.springframework.data.domain.PageRequest`。確認與本 Sprint `IdempotencyService`/`FeatureToggleService` 的變更無關後，順手移除此 3 個未使用 import（`PageImpl`/`Pageable` 經檢查仍在使用予以保留），重新執行 `mvn verify -Pintegration-test` 確認 **1191 tests 0 fail**、checkstyle 乾淨。

---

## 4. 誠實揭露（Rule 12）

1. **本 Sprint 發現且修復 1 項真實安全缺口，非勉強找理由標記**：`IdempotencyService` 的跨租戶資料洩漏以真實 Redis 語意模擬（`HashMap` 而非固定 stub）的紅燈測試明確證實，非推論。
2. **修復範圍嚴格限縮**：僅修改 `IdempotencyService.buildKey()` 相關的 5 個方法呼叫點，未觸碰 `isValidUuidV4`（純格式檢查，與租戶隔離無關）與其他不相關程式碼。
3. **`FeatureToggleService` 誠實回報乾淨**：兩個方法皆一律依賴 `TenantContext`，未發現需修復缺口，不因本 Sprint「應該要找到問題」的預期而勉強解讀。
4. **未觸及 `DEF-025`（`BookingService.createBooking` 的 `idempotencyKey` 參數死碼）**：該項目使用者已於 Sprint 71 決策擱置為低優先級技術債，與本 Sprint 修復的 Redis key 範圍化問題屬不同層級問題（死碼參數清潔度 vs 安全缺口），本 Sprint 不重新開啟該決策。
5. **如實記錄 Sprint 77 遺留的 checkstyle-test 違規排查過程，而非默默清理不提**：此違規早在 Sprint 77 就已存在，但因 Sprint 77/78 依政策僅需跑 `mvn test`（未修改生產程式碼），未觸發綁定在 verify phase 的 `checkstyle-test`，直到本 Sprint 首次執行 `mvn verify -Pintegration-test` 才浮現。修正範圍嚴格限縮於移除 3 個確認未使用的 import，未動其餘程式碼。

---

## 5. Demo 重點

- **一次 Sprint 內完成「探查 → 紅燈 → 修復 → 綠燈 → 全量回歸」完整循環**：規模與 Sprint 76（`LogisticsService`）、Sprint 75（`ChatService`）等發現真實漏洞的 Sprint 相當，修復方案有清楚前例（`RedisCartService.getCartKey`）可循，依指示直接動手不停下確認。
- **Redis key 隔離問題的識別方法可複用**：任何 Redis-backed 且 key 部分或全部由客戶端輸入決定的服務，都應檢查 key 是否納入租戶/使用者範圍，這是本輪多 Sprint 測試強化計劃發現的第 2 種漏洞模式（前 11 項多為 Repository 查詢層級缺少 tenantId 過濾的 IDOR，本項則是 Redis key 命名空間缺少範圍化）。
- **多 Sprint 測試強化計劃圓滿結束**：本 Sprint 完成後，源自 Sprint 66 全面盤點的排程模組全部清空，整體回顧詳見 `SPRINT_79_RETRO.md`。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
