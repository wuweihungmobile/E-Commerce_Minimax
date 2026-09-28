# Sprint 212 Plan — 結算單同期間併發生成的查證（未發現缺陷，補齊測試覆蓋）

**Sprint**: Sprint 212
**日期**: 2026-09-28

## 1. 起點

Sprint 211 收尾後再次無 AI 可獨立處理的活躍待辦（`myTodoList.md` 三項仍需使用者操作實際環境）。延續「自選掃描角度」的既有模式。

## 2. 本輪嘗試的角度

### 2.1 已排除：`/v2/auth/**` 端點缺乏來源 IP 節流

一開始懷疑 `RateLimitFilter`（每租戶限流）刻意排除整個 `/v2/auth/**`（登入前無租戶身分），可能讓 `POST /v2/auth/register`、`POST /v2/auth/password/forgot` 等會觸發真實寄信的端點毫無節流，在 Sprint 209 真實 SMTP 上線後可被用來對任意 email 位址群發垃圾郵件。

查證後發現此角度**已被涵蓋**：`LoginRateLimitFilter`（Sprint 168 DEF-220 建立、Sprint 183 DEF-250 擴大涵蓋註冊端點、Sprint 204 再擴大涵蓋忘記密碼／重設密碼／Email 驗證與重寄）已針對這整組端點做「路徑＋來源 IP」的 30/分鐘節流，且路徑清單與 `AuthController` 實際端點逐一核對後完全吻合，未發現遺漏或路徑漂移。此角度未發現缺陷。

### 2.2 已排除：Settlement／Transfer 模組的租戶授權

檢查 `SettlementController`／`TransferController`／`SettlementGenerator`／`SettlementReviewer`／`TransferService` 的所有端點與服務方法，確認查詢、審核、逆轉、重試等操作皆正確落實租戶範圍檢查（`findByIdAndTenantId`、`checkTenantAccess`），SKU 狀態更新（`ProductSkuService.updateSku`）也已有白名單驗證（DEF-233）。此角度未發現缺陷。

### 2.3 深入查證：`SettlementGenerator.generateStatementForTenant` 的冪等檢查是否有競態風險

`generateStatementForTenant` 開頭的「查有沒有既有結算單、沒有就建立」冪等檢查（`existingStatements.isEmpty()`）與後續寫入之間沒有原子保護；`statementNumber` 由 `tenantId + periodStart` 決定性推導，理論上兩條併發執行緒算出同一把號碼，落後者應該會在 `settlement_statements(tenant_id, statement_number)` 的 UNIQUE 約束上衝突，拋出未經轉譯的 `DataIntegrityViolationException`。

**這個疑慮並非憑空猜測**：既有的 `SettlementConcurrentClaimIntegrationTest`（DEF-273 的併發測試）Javadoc 自承「刻意讓每條執行緒用不同期間，不會被同期間冪等檢查擋下」——測試作者明確意識到「同期間」是一條**不同、未測試**的路徑；而 `SettlementGeneratorManualTriggerTest` 的 Javadoc 卻逕自宣稱「冪等與併發防護已有測試覆蓋（見 `SettlementGeneratorClaimTest`）」——但 `SettlementGeneratorClaimTest` 是純 Mockito 單元測試，只驗證 mock 回傳值觸發 `IllegalStateException` 的 Java 控制流程，從未在真實 DB 下驗證過任何併發時序。這正是「測試自稱涵蓋、實際從未真正驗證」的既有模式（見 `mock-repository-hides-never-executed-queries` 類案例）。

**查證方法**：新增 `SettlementConcurrentClaimIntegrationTest.concurrentGenerations_sameTenantAndPeriod_producesExactlyOneStatement`——8 條執行緒同時對**同一個** tenantId+period 呼叫 `generateStatementForTenant`（真實 Postgres）。

- 第一次執行（未修改程式碼）：8 條執行緒、0 個技術性例外、恰好 1 張結算單、40 筆訂單全數正確結算。
- 為排除「這次剛好沒撞上窄窗口」的僥倖，暫時在冪等檢查後人為插入 `Thread.sleep(200)` 拉寬 check-then-act 窗口（比真實情境寬非常多個數量級），重新編譯執行：**結果仍然是 0 個技術性例外**，`failures={IllegalStateException=7}`——7 條落後執行緒清一色收到「認領不足」的 `IllegalStateException` 並整筆回滾（連同它們自己剛 `save()` 的重複結算單一併撤銷），從未真正觸發 `settlement_number` 的 UNIQUE 約束衝突。已還原此暫時延遲程式碼。

**結論**：這條路徑其實是安全的，但保護它的**不是**這段冪等檢查本身（它確實沒有原子保護），而是 DEF-273（Sprint 195）既有的「原子認領訂單，認領數不足就整張回滾」機制**順帶接住了它**——落後執行緒的交易會在訂單認領步驟失敗並完整回滾，即使它自己也建立了一筆（statement_number 重複的）結算單，回滾後也一併消失，從未走到需要 UNIQUE 約束出面擋下的地步。**不是本輪定義的缺陷**，但既有測試對此路徑的覆蓋宣稱與實際驗證範圍不符，已一併補齊。

## 3. 修復

無需程式碼修復——查證結論是既有機制已經正確保護此路徑。

## 4. 測試

新增 `SettlementConcurrentClaimIntegrationTest.concurrentGenerations_sameTenantAndPeriod_producesExactlyOneStatement`（真實 Postgres，8 執行緒）：斷言（1）失敗集合是 `{IllegalStateException}` 的子集，不可出現未分類的技術性例外；（2）該租戶＋期間最終恰好 1 張結算單；（3）該張結算單的 `total_orders` 等於全部 40 筆訂單，不漏算不重複算。此測試作為此不變量的永久回歸守門，避免未來重構（例如若有人動到 `markSettled` 的原子認領邏輯）在不知情下重新打開這個窗口。

## 5. 驗證結果

- `SettlementConcurrentClaimIntegrationTest`：2 tests，0 fail（含本輪新增 1 案例）
- checkstyle（main+test）：0 violations
- 全量驗證 `mvn -o verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1791**（持平，本輪未新增單元測試）／整合 **569**（+1）／0 failures／0 errors／0 skipped；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（8:18 min）
- 未變更 entity/migration，未跑 `make validate-schema`
- 未跑 E2E／`make validate-release`

## 6. 後續

- 本輪為查證性質，未發現需修復的缺陷；補齊的測試覆蓋已直接提升既有併發防護機制的驗證完整度。
- `myTodoList.md` 記載的三項人工待辦（真實寄信服務憑證、維運三件事、Stripe 測試模式走查）狀態不變，仍在使用者手上。
