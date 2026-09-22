# Sprint 183 Plan — 裸實體序列化外洩 + 認證子系統深度審查（DEF-249~251）

**Sprint**: Sprint 183
**日期**: 2026-09-22

## 1. 起點

Sprint 182 修復 `DEF-244~247` 後，`DEFERRED_ITEMS_TRACKER.md` 僅剩 `DEF-235`/`DEF-236`/`DEF-241`/`DEF-248` 四項低優先級「不排入排程」項目。本輪選定兩個角度：

1. `DEF-093`（Sprint 130 修復的 `ListingController` 裸實體序列化外洩 `passwordHash`）當時只在單一 Controller 做點狀修復，從未系統性檢查全庫是否還有同型漏洞——這是本輪第一個角度。
2. `DEF-244`（Sprint 182 的公開註冊端點租戶奪取）提示認證子系統過去可能未被充分審查過——這是本輪第二個角度，深入審查密碼重設、refresh token 失效、email 驗證、帳號枚舉/暴力破解、JWT 簽章金鑰等面向。

## 2. 掃描方法與結果

派出 2 個背景唯讀調查 agent：

- **Agent 1（裸實體序列化）**：系統性掃描全庫約 40 支 Controller 的所有端點回傳型別，確認 `User.passwordHash` 目前**仍然沒有 `@JsonIgnore`**（`DEF-093` 修復只改了呼叫端，從未補上根因防線），且 `spring.jpa.open-in-view` 為預設 `true`、全庫未註冊 Hibernate Jackson 模組——代表任何裸實體回傳端點若有 LAZY 指向 `User` 的關聯，會**真的**觸發懶載入並序列化出資料，而非拋出例外。找到 🔴 CRITICAL 新缺陷：`ArticleVersionController` 兩個端點直接回傳裸 `ArticleVersion`/`Page<ArticleVersion>`，透過 `createdBy`（LAZY `User`）與 `article.author`（LAZY `User`，經 `article` 關聯）兩條獨立路徑外洩密碼雜湊。逐一核對其餘全部端點確認皆已使用專用 DTO，無其他同型漏洞。
- **Agent 2（認證子系統）**：查證密碼重設（功能完全不存在，非漏洞但為產品缺口）、refresh token 失效機制（登出/輪替/重放偵測皆正確，`DEF-219` 現況與記錄一致，無缺陷）、email 驗證（`emailVerified` 欄位從未被任何地方強制檢查，與 `DEF-247` 同一模式家族）、帳號枚舉與暴力破解（登入端點已妥善防護 `DEF-220`，但**註冊端點完全無任何限流**）、JWT 簽章金鑰（`application.yml` 的 `jwt.secret` 有寫在原始碼裡的不安全預設值，且無啟動期驗證）。

## 3. 修復內容

### 3.1 DEF-249（🔴 CRITICAL）：ArticleVersionController 裸實體序列化外洩密碼雜湊

`ArticleVersionController.getArticleVersions`/`getArticleVersion`（`knowledge:read`，`STORE_OWNER`/**`STORE_STAFF`**/`ADMIN`/`SUPER_ADMIN` 皆持有）直接回傳裸 `ArticleVersion`/`Page<ArticleVersion>` 實體。`ArticleVersion.createdBy`（`@ManyToOne(LAZY)` → `User`）由 `KnowledgeBaseService.createVersionSnapshot`（`.createdBy(article.getAuthor())`）寫入，而 `KnowledgeArticle.author` 是 `nullable=false`，保證每筆版本紀錄的 `createdBy` 皆為真實、非 null 的 `User`；`restoreVersion` 也會自動呼叫 `createVersionSnapshot`，確保只要文章被還原過一次，版本歷史必然存在。`User.passwordHash` 沒有 `@JsonIgnore`，`spring.jpa.open-in-view` 為預設 `true`，Controller 回傳裸實體時 Hibernate session 在序列化階段仍開著，Jackson 呼叫 `getCreatedBy()`/`getArticle().getAuthor()` 會真的觸發懶載入並遞迴序列化整個 `User`。

**攻擊情境**：任何持有 `STORE_STAFF`（租戶內最低信任等級員工角色）帳號的使用者，呼叫 `GET /v2/knowledge/articles/{articleId}/versions` 即可取得該文章作者（通常是 `STORE_OWNER`/`ADMIN`）的密碼雜湊，離線暴力破解後可完整接管店鋪/系統管理帳號——明確的權限提升到帳號接管路徑。

**修法（雙管齊下）**：
1. **根因防線**：`User.passwordHash` 補上 `@JsonIgnore`。`DEF-093` 修復時的既有教訓（見 `[[listing-raw-entity-passwordhash-leak]]`）明確指出應該「雙管齊下」但當時只做了呼叫端修復，本輪一次性堵住所有現在及未來可能出現的同類外洩路徑，不再逐一依賴每個 Controller 都正確改用 DTO。
2. **端點層修復**：新增 `ArticleVersionDto`（比照既有 `KnowledgeArticleDto` 的 `authorId`/`authorName` 命名慣例，只挑純量欄位），`KnowledgeBaseService.getArticleVersions`/`getArticleVersion` 改回傳此 DTO，不再夾帶 `createdBy`/`article`/`category`/`tenant` 等關聯物件本身。

**紅燈驗證**：`@JsonIgnore` 這道根因防線用 `git stash` 單獨還原 `User.java`（純新增註解，未變更任何簽章），新增 `UserTest.serialization_neverIncludesPasswordHash` 直接用 `ObjectMapper` 序列化含 `passwordHash` 的 `User`，修復前明文出現在 JSON 中，修復後完全消失。端點層 DTO 轉換因涉及回傳型別變更（`ArticleVersion`→`ArticleVersionDto`），`git stash` 會產生編譯錯誤而非行為差異，改採獨立讀碼＋交叉驗證的替代嚴謹度（比照 `DEF-244` 手法）：本人逐一直接讀完 `User.java`、`ArticleVersionController.java`、`ArticleVersion.java`、`KnowledgeArticle.java`、`KnowledgeBaseService.createVersionSnapshot`/`getArticleVersions`/`getArticleVersion`/`restoreVersion`、`application.yml` 的 `open-in-view` 設定、`RolePermissionMapping` 的 `KNOWLEDGE_READ` 授予範圍，確認 agent 回報的每一個事實皆屬實後才動手修復。

### 3.2 DEF-250（🟡 MEDIUM）：註冊端點完全無限流

`POST /v2/auth/register` 與登入端點同樣被 `RateLimitFilter`（每租戶限流）排除（註冊前無租戶身分可綁定），但不像登入端點有 `LoginAttemptService` 這層 per-account 鎖定補防——完全沒有任何節流機制。可被用來：(1) 無限速率呼叫，依 `E_1005`（email 已註冊）vs 201（成功）的回應差異枚舉任意 email 是否已在系統註冊，比登入端點的枚舉防護更弱（登入至少有 rate limit）；(2) 發動 bcrypt 雜湊運算成本高的 CPU 資源耗盡攻擊；(3) 無限量灌入垃圾帳號（此系統目前也沒有 email 驗證把關，見 §5 誠實揭露）。

**修法**：擴大既有 `LoginRateLimitFilter`（Sprint 168，`DEF-220`）的涵蓋範圍到 `POST /v2/auth/register`，重用同一套 per-IP token bucket（30/分鐘，與登入端點既有判準一致：足以涵蓋共用 IP/NAT 合法併發情境，又遠低於真正自動化攻擊量級）。改用「路徑+IP」而非單純 IP 作為 Redis key 維度（`KEY_PREFIX` 由 `ratelimit:login_ip:` 改為 `ratelimit:auth_ip:`，key 組成加入 `request.getServletPath()`），讓登入與註冊的配額互相獨立，登入 burst 不會誤耗盡註冊配額。未重新命名類別（`LoginRateLimitFilter` 現在也涵蓋註冊端點，名稱略不精確，但重新命名會牽動 3 個既有檔案的無關 churn，權衡後保留現名、僅更新 Javadoc 說明）。

**既有「鎖住漏洞」的測試**：`LoginRateLimitFilterTest.shouldNotFilter_onlyExcludesPostLogin` 先前明確斷言 `filter.shouldNotFilter(registerPost)` 為 `true`（即註冊路徑被排除、不受限流）——把漏洞行為斷言為正確功能。已重寫為 `shouldNotFilter_coversBothPostLoginAndPostRegister`，斷言登入與註冊兩者皆不被排除。

**紅燈驗證**：`git stash` 還原 `LoginRateLimitFilter.java`（未變更任何方法簽章），新增的 3 個測試（`shouldNotFilter_coversBothPostLoginAndPostRegister`、`registerPath_deniedRequest_returns429`、`loginAndRegister_useIndependentRateLimitKeys`）修復前全數轉紅（前二者證實註冊路徑完全不受限流，第三者因舊版只呼叫一次 Redis——註冊路徑被排除、根本沒有觸發 Redis 呼叫——驗證呼叫次數不符）。真實 Redis 整合測試（`LoginRateLimitFilterIntegrationTest`/`RateLimitFilterIntegrationTest`）確認 key 命名空間變更後，既有的 token bucket 時序邏輯不受影響。

### 3.3 DEF-251（🟠 HIGH）：JWT 簽章金鑰有不安全的硬編碼後備預設值

`application.yml`：`jwt.secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production-min-32-chars}`（`docker-compose.yml` 亦有類似後備值）。此字串長度足夠（59 字元 > 32 bytes），`Keys.hmacShaKeyFor` 不會因長度不足而拋錯，問題在於它是**寫在公開原始碼裡的固定字串**。全庫沒有任何地方（`JwtTokenService` 建構子、`@PostConstruct`、或其他啟動期檢查）驗證 `JWT_SECRET` 環境變數是否真的被設定。若任何部署環境（尤其是快速上線的 staging/demo，或維運疏漏）忘記設定這個環境變數，系統會**靜默**採用這個公開字串簽署所有 access/refresh token——任何讀過原始碼的人都能偽造任意使用者（含 `SUPER_ADMIN`）的合法簽章 JWT，完全繞過整個認證系統，且沒有任何 fail-fast 機制提醒維運人員設定錯誤。

**修法**：`JwtTokenService` 建構子新增檢查，若傳入的 `secret` 等於這個已知的公開預設字串，直接拋出 `IllegalStateException` 讓應用程式無法啟動（fail-fast），訊息明確指出需設定 `JWT_SECRET` 環境變數。確認測試用的 `application-test.yml`/`application-integration-test.yml` 皆使用各自獨立、非預設值的密鑰字串，此檢查不影響任何既有測試。

**紅燈驗證**：`git stash` 還原 `JwtTokenService.java`（建構子簽章未變），新增測試 `constructor_withInsecureDefaultSecret_throwsIllegalStateException` 修復前確實未拋出例外（靜默接受不安全密鑰），修復後正確拋出並可無視外部環境變數，因為它拒絕的是一個特定的值而非泛用金鑰驗證邏輯，如果不小心改動了原始碼中的常量字串或 GitHub Actions 中的預設字串，即使程式本身能正確拒絕，也需要另外檢查生產環境的實際環境變數配置——這是本項修法的殘留風險，僅記錄不在本輪處理。

## 4. 測試總覽

- **紅燈先行**：`DEF-249` 的 `@JsonIgnore` 根因防線與 `DEF-250`/`DEF-251` 皆用 `git stash`（三者皆未變更既有方法簽章）做行為紅燈驗證；`DEF-249` 的端點層 DTO 轉換因回傳型別變更改採獨立讀碼＋交叉驗證的替代嚴謹度，理由與過程見 §3.1。
- **新增測試**：`UserTest`（新檔，1 案例）、`KnowledgeBaseServiceTest` +1、`LoginRateLimitFilterTest` +2（另修改 1 個既有測試方法）、`JwtTokenServiceTest` +1。單元測試合計 +5。
- **既有測試修正（鎖住漏洞行為 → 改為驗證正確行為）**：`LoginRateLimitFilterTest.shouldNotFilter_onlyExcludesPostLogin`。
- **既有測試因回傳型別變更而更新（非行為缺陷，純介面調整）**：`KnowledgeBaseServiceTest.getArticleVersions_returnsPage`、`M18KnowledgePhase2IntegrationTest`（`getArticleVersions_success`/`getArticleVersion_success` 兩案例改用 `ArticleVersionDto` 建構測試固件）。
- **環境踩坑（非程式碼問題，記錄供未來參考）**：本輪第一次執行 `mvn verify` 時，因在同一時間點才剛執行 `make test-db-up`、容器尚未完全就緒即讓 Maven 開始跑整合測試，導致近 40 個測試類別以 `ApplicationContext failure threshold exceeded`／rate limit 測試以「fail-open 恆放行」的方式大量假性失敗（兩個彼此獨立、我完全沒有改動的類別 `RateLimitFilterIntegrationTest` 也同樣失敗，是判斷「並非我的程式碼改動導致」的關鍵線索）。重新確認 `make test-db-up` 完全就緒後乾淨重跑，全數轉綠，證實純屬啟動時序問題。

## 5. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1620 個單元測試（+5）+ 486 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-249`/`DEF-250`/`DEF-251`：狀態皆記為「✅ 已修復（Sprint 183）」，發現與修復同輪完成。
- 新增 `DEF-252`（🟡，不排入排程）：`User.emailVerified` 欄位從未被任何地方強制檢查（無 `@PreAuthorize`、無 Service 層檢查、也沒有任何 email 驗證信/驗證端點存在），與 `DEF-247`（功能開關聲稱審核但從未真正建立）同一模式家族。未直接修復的理由：目前沒有任何業務邏輯宣稱「需要 email 驗證才能執行 X」，嚴格來說沒有繞過既有保護（保護從未存在）；「該對哪些操作要求 email 驗證」是需要產品拍板的業務規則決策，非本輪可自行認定的安全缺陷修復範圍。
- 新增 `DEF-253`（🟡，不排入排程）：密碼重設/變更功能完全不存在（後端無對應端點，前端 `/login` 頁有指向 `/forgot-password` 的連結但該頁面不存在，為死連結）。屬產品功能缺口而非安全漏洞（無可攻擊的端點），建議排入未來功能開發 Sprint 而非缺陷修復 Sprint。

## 7. 誠實揭露總結

- `DEF-249` 與 `DEF-093`（Sprint 130）根因完全相同（`User.passwordHash` 缺 `@JsonIgnore` + 裸實體回傳 + `open-in-view` 預設開啟），但當時的修復只處理了觸發它的那一個 Controller，未同步補上欄位層級的根因防線，也未做全庫掃描確認是否還有其他同型端點——這正是本輪重新掃描後找到的結果。本輪的雙管齊下修法（根因 `@JsonIgnore` + 端點 DTO 化）記取這個教訓，往後即使再有 Controller 不慎回傳裸實體，`passwordHash` 也不會外洩。
- 本輪識別但刻意不修的兩項（`DEF-252`/`DEF-253`）皆屬於「需要產品層級判斷、而非單純技術缺陷修復」的範疇，符合本專案一貫的 Rule 2（簡潔優先，不做超出需求的功能）原則——記錄清楚、留待未來有明確業務決策時再排入排程，優於在缺陷修復 Sprint 裡自行猜測業務規則並實作。
- 本輪開工時因流程疏失（`mvn verify` 早於 `make test-db-up` 完成就啟動）導致一次假性大規模紅燈，靠「兩個互相獨立、其中一個我完全沒碰過的類別同時以相同症狀失敗」這個訊號正確判斷為環境問題而非程式碼回歸，避免了誤删或誤改正確程式碼的風險——重跑前沒有先自證「這是環境問題」就貿然動手排查程式碼，會是本輪流程紀律上的疏漏，記錄供未來提醒：任何背景 `mvn verify` 呼叫前，務必先確認 `make test-db-up` 的容器健康狀態已穩定（而非剛下指令），而非依賴事後補救。
- `DEFERRED_ITEMS_TRACKER.md` 目前僅剩 `DEF-235`/`DEF-236`/`DEF-241`/`DEF-248`/`DEF-252`/`DEF-253` 六項低優先級「不排入排程」項目，Sprint 184 開工時仍需自選新掃描角度。
