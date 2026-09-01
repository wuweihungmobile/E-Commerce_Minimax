# Sprint 104 Plan — DEF-049：整合測試耗時的兩支真槓桿

**Sprint**: Sprint 104
**日期**: 2026-09-01
**AI 編號**: AI-2438
**主題**: DEF-049 連續三輪（S101／S102／S103）被列為候選卻未動。本輪動它——但在改任何設定之前，先查證它擋住的到底是什麼。查證的結果是：**DEF-049 記錄的兩個關鍵前提都不準確**，而真正最大的一支槓桿它從未提及。

---

## 1. 缺口盤點結果

### 起點

DEF-049 的記錄在 S102 已被修正過一次（原方向 (1)「收斂 context 設定」被推翻），改指向 `reuseForks`，並留下明確的第一步：

> 需先確認當初「SecurityContext 汙染」的顧慮是否仍成立

本輪從這一步開始。

### 🔴 發現 A：`reuseForks=false` 這個設定從來沒有被查證過

`reuseForks=false` 由 commit `d444b2d`（2026-06-15）引入。該 commit 的標題是：

> `fix(DB): V30 migration 移除不存在的 tenant_id 索引`

——與測試 fork 毫無關係。這個設定是夾帶在一次 migration 修復裡的順手改動，commit message 完全沒有提到它，也沒有任何佐證說明「SecurityContext 汙染」實際發生過。時間點正落在 CLAUDE.md 記載的「CI Pipeline 修復了 20+ 次都失敗，每次都是盲目猜測」那段期間（2026-06-12 ~ 06-17）。

換句話說，過去三個 Sprint 一直繞著走、S102 稱為「第一支槓桿」的這個設定，**它的理由本身就是一個未經驗證的猜測**。

### 發現 B：今天真正的汙染源只有一個，而且不是 SecurityContext

逐一稽核 138 個測試類別（87 單元 + 51 整合／E2E）的結果：

| 汙染管道 | 現況 |
|---|---|
| `SecurityContextHolder` | **無缺口**——所有設定 context 的測試都有對應的 `clearContext()`，零例外 |
| `TenantContext`（ThreadLocal） | **一個缺口**：`WithErpSecurity.WithErpSecurityContextFactory` 在建立 SecurityContext 時**順帶**寫入 `TenantContext.setCurrentTenant/setCurrentUser`，而 Spring Security 的 `WithSecurityContextTestExecutionListener` 只負責還原 SecurityContext，對 `TenantContext` 一無所知。全庫只有 `M16ErpIntegrationTest` 使用此註解 |
| `mockStatic` / `MockedStatic` | 整合階段零使用（僅一個單元測試用） |
| `System.setProperty` | 全測試樹零使用 |
| 跨類別 static 可變狀態 | 無——24 筆 static 欄位全部是各類別自己的測試資料 ID，不跨類別共享 |
| 外部資源 | `RateLimitFilterIntegrationTest` 的 Redis 連線有 `@AfterAll` 回收 |

也就是說，那句註解寫的「SecurityContext pollution」**指錯了對象**：SecurityContext 是唯一沒有問題的那個，有問題的是它順手帶進來的 `TenantContext`。

### 🔴 發現 C：整合測試 job 有 19.5% 的時間在重跑單元測試

這是本輪最大的發現，而 DEF-049 從未提及。

CI 的 `Backend Integration Tests & Package` job 執行的是 `mvn verify -Dspring.profiles.active=integration-test`。**`verify` 階段包含 `test` 階段**——`pom.xml` 沒有任何 `<profiles>` 區段（`-Pintegration-test` 是一個不存在的 Maven profile，`integration-test` 只是 Spring 的 `@ActiveProfiles`，兩者在註解與 Makefile 說明中被混為一談），failsafe 是無條件綁在 `verify` 上的，surefire 也照跑不誤。

以 Sprint 103 的雲端 run `33511000120` 逐 plugin 取時間戳（非推估）：

| 階段 | 起訖 | 耗時 |
|---|---|---|
| `maven-surefire-plugin:test`（**1074 個單元測試**） | 13:12:09.8 → 13:16:34.4 | **4m25s** |
| `maven-failsafe-plugin:integration-test`（404 個整合測試） | 13:16:34.4 → 13:33:58.5 | 17m24s |
| `checkstyle-main` + `checkstyle-test` | 13:33:58.5 → 13:34:29.6 | 31s |
| **`mvn verify` 合計** | | **22m36s** |

同一個 run 的 `Backend Unit Tests` job 已經把**同樣這 1074 個測試**跑過一次（363s）。兩個 job 平行執行，重複的那一份不提供任何額外覆蓋：

- 87 個單元測試類別中，唯一帶 Spring context 的是 `SellerDashboardServiceCacheTest`，而它**自帶** `@ActiveProfiles("integration-test")`；
- 其餘 86 個是純 Mockito 測試，Spring profile 對它們毫無意義。

所以兩個 job 的 `-Dspring.profiles.active` 差異（`test` vs `integration-test`）**對任何一個單元測試都不產生行為差異**。這 4m25s 是純粹的浪費。

### 發現 D：DEF-049 對 `reuseForks` 的定性也不準確

DEF-049 記載 `reuseForks=true` 是「**單一設定變更，影響最大**」。實測下來，它不是單一設定變更——單獨翻這個開關會撞上兩道物理限制：

| 限制 | 數字 |
|---|---|
| 51 個測試類別共有 **27 種相異的 Spring context 設定**（`@SpringBootTest`／`@WebMvcTest` × `@ActiveProfiles` × `@AutoConfigureMockMvc` × `@Import` × `@MockBean` 集合） | 全部快取 = 27 個 context |
| 每個 context 各自持有一組 HikariCP 連線池（預設上限 10） | 27 × 10 = **270 條連線** vs 測試 postgres `max_connections=100` |
| 每個 Spring Boot context 的堆記憶體 | 本機 JVM 預設最大堆 **2.0 GB**（8 GB 實體記憶體） |

而且 failsafe 預設的 `runOrder=filesystem` 是**分散的**（實測順序：`SettlementScheduledJob → ReviewServiceCache → LogisticsProvider → M09Template → M09History → M12InventoryConcurrency → M02Room → TransferController → …`），同設定的類別不相鄰，光開 `reuseForks` 而不動執行順序，context 快取幾乎不會命中。

因此 `reuseForks=true` 必須配套：**執行順序（讓同設定類別相鄰）** + **快取上限（讓連線與記憶體放得下）** + **ThreadLocal 隔離（讓共用 JVM 安全）**。

---

## 2. 技術決策

### 決策 1：以「結構上不可能洩漏」取代「逐一稽核」

發現 B 找到的缺口只有一個，最小改動是去修 `WithErpSecurity` 那一處。但 `reuseForks=true` 改變的是**整個測試套件從此必須永久維持的不變量**——往後每一個新測試都得記得清 ThreadLocal。這個 codebase 反覆出現的正是「新增功能時漏加某個必要步驟」的模式（見 DEFERRED_ITEMS_TRACKER 中連續 12 個 IDOR 缺口）。

因此採用單一全域 JUnit 擴充 `ThreadLocalIsolationExtension`，在每個測試方法與每個測試類別結束後清除兩組 ThreadLocal，以 JUnit Platform 自動偵測註冊，對全部 138 個測試類別生效。已查證 classpath 上**沒有任何第三方函式庫**會被自動偵測一併註冊（波及範圍僅本擴充），且**沒有任何測試在 `@BeforeAll` 設定 context**（故 `afterEach` 清理不會破壞既有測試）。

### 決策 2：`skipUnitTests` 只作用於 CI，本機全量回歸不受影響

發現 C 的修法是讓 CI 的整合 job 略過單元測試。刻意**不使用** `-DskipTests`（那會連 failsafe 一起跳過），改以 `-DskipUnitTests=true` 啟用一個只設定 surefire `<skipTests>` 的 Maven profile。

- CI 整合 job：`mvn verify -DskipUnitTests=true` → 只跑整合測試
- 本機 `mvn verify`：**行為完全不變**，單元＋整合都跑，全量回歸的覆蓋不減

🔴 **這裡第一次的寫法是錯的，被 `make validate-release` 攔下**——詳見 4.9。

### 決策 3：surefire 與 failsafe 一起改，不留矛盾設定

發現 A／B 推翻的是「每個測試類別必須獨立 JVM」這個前提本身，而該前提在 `pom.xml` 中出現**兩次**（surefire 一次、failsafe 一次，註解一字不差）。只改 failsafe 會讓同一份 codebase 同時存在「已知不成立的理由」與「依它設定的組態」——正是 Rule 7 要求公開解決而非放著的矛盾。因此兩者一起改。

DEF-049 也明確記載單元測試 job 是同一個結構問題（`timeout-minutes: 12`，Sprint 103 實測 8m01s、已用 67%），只是當時仍有餘裕故未動。

### 決策 4：`spring.test.context.cache.maxSize` 設為 3

發現 D 的連線與記憶體限制要求快取必須設上限（預設 32 放不下 27 個 context）。**刻意不採用「壓低 HikariCP 連線池」的做法**：`M11PromoConcurrencyIntegrationTest` 與 `M12InventoryConcurrencyIntegrationTest` 各以 10 條執行緒壓同一列，池子若小於執行緒數，這兩個測試會在取得連線處排隊而**不再真的併發**——測試仍然全綠，但綠得沒有意義。這正是 Sprint 97「測試以固件繞過同一段邏輯」的同型陷阱，寧可限制快取數量也不動池子大小。

---

## 3. 實作內容

### 新增：`ThreadLocalIsolationExtension`（測試基礎設施）

`src/test/java/com/nextkey/ecommerce/testsupport/ThreadLocalIsolationExtension.java`

實作 `AfterEachCallback` + `AfterAllCallback`，兩個時機都呼叫 `TenantContext.clear()` 與 `SecurityContextHolder.clearContext()`。

註冊採 JUnit Platform 自動偵測，需要兩個檔案：

| 檔案 | 作用 |
|---|---|
| `src/test/resources/junit-platform.properties` | `junit.jupiter.extensions.autodetection.enabled=true` |
| `src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension` | 指向擴充的 FQCN |

Jupiter 的 `AfterEachCallback` 在使用者自訂的 `@AfterEach` 方法**之後**才執行，故既有 teardown 對 context 的使用與斷言都不受影響。

### 新增：`src/test/resources/spring.properties`

`spring.test.context.cache.maxSize=3`（理由見決策 4）。

### 修改：`backend/pom.xml`

| 位置 | 變更 |
|---|---|
| surefire `<configuration>` | `reuseForks` false → **true**；新增 `runOrder=alphabetical` |
| failsafe `<configuration>` | `reuseForks` false → **true**；新增 `runOrder=alphabetical` |
| 新增 `<profiles>` | `skip-unit-tests`，由 `-DskipUnitTests=true` 啟用，只對 surefire 設 `<skipTests>true</skipTests>` |

原註解「Ensure each test class runs in a separate JVM to avoid SecurityContext pollution」一併改寫——它指錯了對象（SecurityContext 是唯一沒問題的那個），且其結論已被發現 A／B 推翻。

### 修改：`.github/workflows/act-compat.yml`

整合測試 job 的指令加上 `-DskipUnitTests=true`，不再重跑單元測試（發現 C）。

---

## 4. 量測

所有數字皆為本機實跑（Apple Silicon、8 GB 實體記憶體、JVM 預設最大堆 2.0 GB、真實 PostgreSQL + Redis），非推估。

### 4.1 基準（修改前）

指令：`mvn -o verify -Dtest=NoSuchUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Dcheckstyle.skip=true`（隔離出整合測試）

| 項目 | 數值 |
|---|---|
| 測試類別 | 51（其中 49 個帶 Spring context，2 個不帶） |
| 測試數 | **404，0 失敗** |
| 牆鐘 | **31 分 23 秒**（1888 秒） |
| 逐類 `Time elapsed` 總和 | 1767.5 秒 |
| 差額（51 次 JVM fork 開銷 + 編譯） | 約 120 秒 |

耗時分布極平坦——48 個 Spring 類別落在 30～57 秒（最慢 `M02RoomIntegrationTest` 56.9 秒），3 個非 Spring 類別 0.7～4.2 秒。這證實 DEF-049 原記錄的判讀正確：**耗時由 Spring context 啟動主導，不是任何單一測試異常**。

### 4.1b 單元測試基準（修改前）

指令：`mvn -o test -Dcheckstyle.skip=true`

| 項目 | 數值 |
|---|---|
| 頂層測試類別（＝JVM fork 次數） | **88** |
| 測試數 | **1077，0 失敗**（原有 1074 + 本輪新增的 3 個擴充測試） |
| 牆鐘 | **8 分 09 秒**（494 秒） |
| 逐單元 `Time elapsed` 總和 | 408.4 秒 |

單元測試幾乎不帶 Spring context（88 個類別中只有 1 個 `@SpringBootTest`，且自帶 `@ActiveProfiles`），
所以這裡 `reuseForks` 省的是 **88 次 JVM 冷啟動**，不是 context 啟動——量級比整合測試小，但成因相同。

### 4.2 快取命中模擬（改設定前先預測，避免盲目調參）

以實際的 27 種 context 設定 + LRU 快取模擬命中次數：

| 執行順序 | `maxSize=3` | `maxSize=32`（預設） |
|---|---|---|
| filesystem（現況，實際觀測順序） | 14 次 | 22 次 |
| **alphabetical** | **18 次** | 21 次 |

兩個推論：

1. **快取大小幾乎不重要**：alphabetical 下 `maxSize=1` 就有 17 次命中、`maxSize=32` 也才 21 次。收益幾乎全部來自「相鄰的同設定類別」，而非「快取很多個 context」。這讓決策 4 的取捨變得便宜——設 3 只比設 32 少 3 次命中，卻把連線數從 270 壓到 30。
2. **`runOrder=alphabetical` 的主要價值不是那多出來的 4 次命中，而是決定性**：filesystem 順序在 macOS 與 Linux runner 上不同，代表本機與雲端的快取行為、耗時、以及任何順序相依的 flaky **本來就不一致**。改成 alphabetical 讓兩邊完全對齊，這比省下的 2 分鐘重要。

### 4.3 Run B：`reuseForks=true`，但**故意不註冊**擴充

這一跑同時回答兩件事：DEF-049 懸置三輪的「當初的汙染顧慮是否仍成立」，以及 fork 重用的實際效益。指令 `mvn -o verify -Dcheckstyle.skip=true -Dmaven.test.failure.ignore=true`（`junit-platform.properties` 的 `autodetection` 暫設為 `false`）。

| 指標 | 基準 | Run B |
|---|---|---|
| 單元階段逐類耗時總和 | 408.4 秒 | **64.0 秒** |
| 整合階段逐類耗時總和 | 1767.5 秒（29.5 分） | **280.2 秒（4.7 分）** |
| 牆鐘（單元＋整合合計） | 8:09 + 31:23 = **39.5 分** | **6 分 19 秒** |
| 整合測試結果 | 404 / 0 失敗 | **404 / 0 失敗** |

**🔴 對 DEF-049 那個問題的答案：顧慮不成立。** 在完全沒有任何 ThreadLocal 清理機制的情況下，404 個整合測試**零失敗**。唯一的失敗是本輪刻意設計的守衛測試 `ThreadLocalIsolationAutoRegistrationTest`（它就是為了在擴充未註冊時失敗而寫的）。

因此必須誠實區分：`ThreadLocalIsolationExtension` **不是在修一個正在發生的 bug，而是一道護欄**——它讓「共用 JVM 不得互相汙染」這個從此必須永久成立的不變量變成結構保證，而不是仰賴 138 個測試類別各自記得清理。

### 🔴 4.4 意外發現：專案歷來記錄的單元測試數全都偏高約 60

Run B 的 surefire 報告 **1017**，基準卻是 **1077**。差 60 個測試是嚴重訊號（設定變更讓測試靜默消失），因此在採信任何加速數字之前先追查。

逐類別比對的結果是：**88 個基準單元類別全數執行，逐類測試數完全一致，零遺漏**。差額全部落在 `@Nested` 類別上，而且形態極為規律——16 個不一致的 nested 類別中有 15 個，基準**恰好是 Run B 的兩倍**（8→4、14→7、12→6、10→5、6→3…）。

以原始碼的 `@Test` 實數裁決：

| Nested 類別 | 原始碼實數 | 基準報告 | Run B 報告 |
|---|---|---|---|
| `PricingServiceTest$LastMinuteDiscountTests` | 1 | 2 | **1** ✅ |
| `PricingServiceTest$PriceCalculationTests` | 4 | 8 | **4** ✅ |
| `OrderPromoCodeTest$CheckoutApplyTests` | 6 | 12 | **6** ✅ |

全樹核對更決定性：單元測試原始碼共 **1003 個 `@Test` + 4 個 `@ParameterizedTest`**，後者的案例數分別為 4／3／3／4 = **14 次展開**。

> **1003 + 14 = 1017**，與 Run B 完全相符。

基準的 1077 需要那 4 個參數化測試展開出 74 個案例才能成立——不可能。實際原因是 `reuseForks=false` 下 surefire 會對同一個 nested 類別印出**兩行**報告並雙重計入（實際觀察到 `TenantServiceTest$MemberInviteTests` 同時出現 `Tests run: 11` 與 `Tests run: 19` 兩行）。

**影響**：Sprint 100 以來各 Plan 記錄的單元測試數（1055／1068／1074）全部偏高約 60，正確量級應為 **1014**（1017 減去本輪新增的 3 個）。Sprint 102 曾記下「文件記載 1068、實際基準應為 1069，差 1」並註明「未回頭考證當時量測條件」——**現在知道那不是差 1，而是計數方法本身結構性不可靠**。整合測試數 404 不受影響（整合測試無 `@Nested`）。

### 4.5 Run C：完整回歸（擴充已註冊 + checkstyle）

指令：`mvn -o verify`（無任何跳過旗標）

| 項目 | 結果 |
|---|---|
| 單元測試（surefire） | **1017 tests, 0 failures, 0 errors, 0 skipped** |
| 整合測試（failsafe） | **404 tests, 0 failures, 0 errors, 0 skipped** |
| checkstyle-main / checkstyle-test | 皆 **0 違規** |
| BUILD | **SUCCESS**（**7 分 06 秒**） |

守衛測試 `ThreadLocalIsolationAutoRegistrationTest` 在此轉綠——與 4.6 的紅燈構成完整對照，證明擴充**確實被自動偵測註冊並生效**，而不只是「檔案放在那裡」。

**與 Sprint 103 同一條指令的對照**：S103 的 `mvn -o verify` 為 **32 分 54 秒**，本輪為 **7 分 06 秒**（−78%）。

### 4.6 紅燈驗證（先於修復執行）

| 驗證 | 做法 | 結果 |
|---|---|---|
| 守衛測試在擴充未註冊時確實失敗 | `autodetection=false`，單獨跑該類別 | 🔴 **失敗**：`nextTestSeesCleanTenantContext` 讀到殘留的 `...0104ff` |
| 同一測試在擴充註冊後通過 | Run C | ✅ 通過 |

刻意用「同一類別內的方法順序」（`@TestMethodOrder`）而非跨類別順序來驗證：前者由 JUnit 保證，與 surefire 的 `runOrder` 無關，不會因日後有人調整執行順序而**悄悄失去守衛作用**（那正是最危險的失效方式——所有測試依舊全綠，護欄卻已經不在）。

### 4.7 為什麼效益遠大於預測：預測模型漏掉了 JVM 暖機

4.2 的模擬預測「省下 18 次 context 啟動 ≈ 561 秒」。實際省下的是 **1298 秒**，是預測的 2.3 倍。差距來自一個模擬沒有涵蓋的效應：

| | 基準（每類獨立 JVM） | Run B／C（共用 JVM） |
|---|---|---|
| context 啟動次數 | 48 | **30** |
| 平均每次啟動耗時 | **31.2 秒** | **6.6 秒** |
| 合計 | 1496.5 秒（佔牆鐘 **79%**） | 198.5 秒 |

共用 JVM 之後，第一個 context 仍要 33 秒（`AddressControllerE2ETest`），但**後續每一個只要 5～8 秒**——類別載入、Hibernate／Spring 中繼資料解析、JIT 暖機全部只付一次。

換句話說：**快取命中（少 18 次啟動）只是次要效益，主要效益是剩下那 30 次啟動各自便宜了 5 倍**。這也回頭修正了 DEF-049 的隱含模型——它把耗時歸因於「context 啟動次數」，但真正的成本結構是「每次啟動在冷 JVM 裡特別貴」。

### 4.8 綜合對照

| 指標 | 修改前 | 修改後 | 變化 |
|---|---|---|---|
| 本機 `mvn -o verify` 完整回歸 | 32 分 54 秒（S103 實測） | **7 分 06 秒** | **−78%** |
| 單元階段逐類耗時總和 | 408.4 秒 | 64.0 秒 | −84% |
| 整合階段逐類耗時總和 | 1767.5 秒 | 280.2 秒 | −84% |
| Spring context 啟動 | 48 次 × 31.2 秒 | 30 次 × 6.6 秒 | −87% |
| JVM fork 次數 | 88（單元）+ 51（整合） | 1 + 1 | — |
| 測試數 | 單元 1017 / 整合 404 | **完全相同** | 0 |

雲端效益已於 2026-09-01 當日回填，見 §4.11。

### 🔴 4.9 第一次的 `skipUnitTests` 寫法在 pom 裡引入了一個靜默回歸

本輪最初把跳過條件直接寫進 surefire 的 `<configuration>`：

```xml
<skipTests>${skipUnitTests}</skipTests>   <!-- 搭配 <properties> 的 skipUnitTests=false -->
```

三種情境（預設跑、`-DskipUnitTests=true` 跳）在本機都測過、都正確，Run C 也全綠。**但它打斷了第四種情境**：POM 裡的顯式 `<skipTests>` 會**蓋過命令列的 `-DskipTests`**，使 `mvn package -DskipTests` 不再跳過測試。

這個回歸由 `make validate-release` 攔下——`validate-schema` 的 `mvn -q package -DskipTests` 建 JAR 時真的跑起了單元測試，而該階段的 test DB 早已被 `test-db-down` 關掉（讓出 5432/6379 給 act），於是 `SellerDashboardServiceCacheTest` 三個案例以 `Failed to load ApplicationContext` 失敗：

```
[ERROR] Tests run: 1017, Failures: 0, Errors: 3, Skipped: 0
[schema-gate] ❌ JAR 建置失敗
make[1]: *** [validate-schema] Error 2
```

**影響範圍比表面更大**：`.github/workflows/act-compat.yml` 的 `Package Backend` 步驟也是 `mvn package -DskipTests`——這個寫法會讓那一步變成**第三次**跑單元測試，把本輪要省的時間又加回去。act 的整合 job 之所以仍然 succeeded，只是因為那時服務容器裡的 DB 還在。

**修法**：改為 Maven profile，只在明確傳入 `-DskipUnitTests=true` 時才啟用，預設組態完全不碰。

```xml
<profile>
  <id>skip-unit-tests</id>
  <activation><property><name>skipUnitTests</name><value>true</value></property></activation>
  ...只對 surefire 設 <skipTests>true</skipTests>
</profile>
```

修正後逐一實測三種情境：

| 情境 | 期望 | 實測 |
|---|---|---|
| `mvn package -DskipTests` | surefire 跳過 | ✅ `Tests are skipped.` |
| `mvn verify -DskipUnitTests=true` | surefire 跳過、failsafe 照跑 | ✅ surefire skipped；failsafe 跑了 3 個 |
| `mvn test`（預設） | surefire 照跑 | ✅ 跑了 3 個 |

**附帶影響**：這使 `pom.xml` 從此真的有了 `<profiles>` 區段——但 DEF-052 記錄的 `-Pintegration-test` 仍然不存在（新 profile 叫 `skip-unit-tests`，且由 property 啟用而非 `-P`）。

---

### 4.10 第二個自找的問題：`pom.xml` 的註解不能放 emoji

修正 4.9 時，我在新的 profile 註解裡依專案慣例加了一個 🔴 標記。本機 `mvn validate`／`package`／`verify` 全部通過，但第二次 `make validate-release` 在第一個 act job 就掛掉：

```
Non-parseable POM: Illegal character 0xd83d found in comment (position: ... @342:11)
```

`0xd83d` 是 🔴（U+1F534）的高代理字元。本機 Maven 的 XML 解析器吞得下，**act 容器裡以 apt 安裝的 Maven 吞不下**——這是一個只在容器環境暴露的差異。

值得記下的是：`d444b2d` 當初引入 `reuseForks` 設定時，註解原本也帶 🔴，後來被改成純英文——很可能就是踩過同一個雷，只是沒有留下記錄。

**專案慣例層面的陷阱**：CLAUDE.md 鼓勵用 🔴 標記重要規範，但這個慣例**不適用於 `pom.xml`**。`.github/workflows/*.yml` 沒問題（既有 6 處，YAML 解析不受影響），依 Rule 3 未動。

修正後以嚴格 XML 解析器交叉驗證（不依賴本機 Maven 的寬容度）：

```
XML 解析: 通過 ／ BMP 以外字元: 無 ／ mvn validate: 通過
```

**這一節與 4.9 合起來的意思是**：本輪兩次被完整驗證程序攔下的問題，都是**只在「本機沒有、但別處有」的條件下才暴露**的——一次是「沒有 test DB 的階段」，一次是「另一個 Maven 版本」。本機全綠從來不是可以跳過 `make validate-release` 的理由。

---

---

### 4.11 雲端實測回填（run 於 2026-09-01 深夜，回填於 2026-09-02 凌晨）

原訂「Sprint 105 開工回填」，但 `9ef2300` 的雲端 run 在 push 後 10 分鐘即完成，故提前回填，不必等下一輪。

**對照組**：S103 `3b19790`（run `33511000120`）vs S104 `9ef2300`（run `33527771681`）。
`9ef2300` 與 `488d686` 的差異僅 `scripts/hooks/pre-commit` 一個檔（見下方註），
backend/frontend 程式碼樹完全相同，故可作為本 Sprint 的雲端量測。

| 指標 | S103 | S104 | 變化 |
|---|---|---|---|
| Backend Unit Tests（job） | 481s（8m01s） | **200s（3m20s）** | −58% |
| Backend Integration（job） | 1469s（24m29s） | **335s（5m35s）** | −77% |
| Frontend Lint & Build（job） | 87s | 78s | 持平 |
| **整個 run** | **1956s（32m36s）** | **598s（9m58s）** | **−69%** |
| `Run Integration Tests`（步驟） | 1357s | **253s** | **−81.4%** |

**回答 §7.2 Action Item 1 的兩個問題**：

**(a) 整合 job 是否真的少掉 4m25s 的重複單元測試 → 是，已由 log 直接證實**（非推論）。
S104 整合 job 的 log 中，`maven-surefire-plugin:3.1.2:test (default-test)` 明確印出
`Tests are skipped.`（15:52:20），而 `maven-failsafe-plugin` 照常執行
（`integration-test` 15:52:25 → `verify` 15:56:09），產出
`Tests run: 404, Failures: 0, Errors: 0, Skipped: 0`。
**這兩件事必須成對成立才算數**：單元測試被跳過、且整合測試 404 支一支不少——
否則就只是「靠少跑測試換來的加速」。404 與本機基準完全一致。

**(b) fork 重用在雲端 runner 的倍率 → 約 4.37 倍**。
把兩個槓桿拆開：S103 步驟 1357s 中有 265s（4m25s）是重複單元測試，
扣除後整合測試本身約 1092s；S104 扣除 surefire 跳過的約 3s 後約 250s。
1092 ÷ 250 ≈ **4.37 倍**（本機約 4.6 倍，同一量級）。
兩個槓桿合計則是 1357 → 253，**5.36 倍（−81.4%）**。

**這筆數字的可信度邊界**：
- 只有**一個**雲端樣本。S102/S103 已證實同一套測試在不同 run 之間可差 **21%**
  （變異源自 runner 而非測試內容）。但本輪的 4.4 倍遠超過該變異幅度，
  故「效益為真」可以斷言，「倍率精確為 4.37」則不可以。
- **S104 不是耗時樣本序列的第六筆**。前五筆（25m19s／29m48s／23.1m／28m05s／24m29s）
  的意義建立在「測試內容相同」之上，用於觀察 runner 變異；本輪同時改了 fork 設定
  與 CI 指令，序列在此中斷。日後要續記變異，應以 S104 之後的 run 重新起算。

**註：`488d686` 自身的雲端 run（`33527330716`）未跑完，被 concurrency 取消。**
`act-compat.yml:34-36` 設有 `concurrency: group: local-ci-${{ github.ref }}` 與
`cancel-in-progress: true`，而 `9ef2300` 於 15:46:18 推送觸發新 run，
使 `488d686` 的 Integration／Frontend 在 15:46:44／15:47:14 被取消
（各僅跑 1m26s／1m58s，離 45 分／10 分的 `timeout-minutes` 極遠，**非逾時亦非測試失敗**；
其 Backend Unit Tests 已先行 success）。因兩個 commit 的 backend/frontend 程式碼樹相同，
`9ef2300` 的全綠 run 完整涵蓋 `488d686`，**不需補跑**。
**教訓**：想讓某個 commit 取得完整雲端結果，就不要在該 run 跑完前（約 10-30 分）推下一個 commit。

## 5. 範圍外（延後）

- **context 設定收斂（27 種 → 更少）**：4.2 的模擬顯示，即使快取開到 32，命中也只從 18 升到 21——收益有限，因為主要成本已被 JVM 暖機吃掉。原本 DEF-049 方向 (1) 想做的「收斂 `@MockBean`／`@ActiveProfiles`」需要動 30 個以上的測試檔，**投報率遠低於本輪的兩支槓桿**，不排程
- **`forkCount` 平行化 / 雲端 job 分片**（DEF-049 原方向 (2)(3)）：本輪把整合階段壓到 4.7 分鐘後，這兩個方向都失去急迫性，不排程
- **`-Pintegration-test` 這個不存在的 Maven profile**：`pom.xml` 沒有任何 `<profiles>` 區段，`Makefile:102` 的說明與 `pom.xml` 的註解都把它與 Spring 的 `@ActiveProfiles("integration-test")` 混為一談。它是無害的（Maven 只會對不存在的 profile 發出警告），但會誤導——DEF-049 自己的記錄就引用了它。依 Rule 3 不順手修改非必要的相鄰內容，**記為 DEF-052（🟢 文件缺陷）**
- **`@DirtiesContext`（3 個類別）**：在 `reuseForks=false` 下本來就是 no-op，現在才真的開始生效（會逐出自己的 context）。是否仍必要需要各自查證，依 Rule 3 未動
- **DEF-047 / DEF-048 / DEF-051 / DEF-044 / DEF-043**：維持延後，本輪未動

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S100 | 8 |
| S101 | 5 |
| S102 | 3 |
| S103 | 3 |
| **S104** | **5**（DEF-049 記錄的估點即 5；含前提查證、兩支槓桿的實作、一個全域測試擴充 + 3 個擴充測試、四次完整量測跑（基準×2／Run B／Run C）與一次紅燈驗證） |

---

## 7. 下一步 / Action Items

### 7.1 上輪（Sprint 103）Action Items 追蹤

| Sprint 103 列的項目 | 本輪結果 |
|---|---|
| 1. 橫向掃描剩餘的計數／額度型欄位（`SettlementAdjustment`／`MediaAsset.incrementUsageCount`／`ReviewService.markHelpful`） | ⏳ 未動，續列 |
| 2. DEF-049（整合測試執行時間） | ✅ **本輪完成**，並更正了它記錄的兩個前提 |
| 3. 🟡 DEF-051（ERP 側庫存讀後寫） | ⏳ 未動，維持不排程 |
| 4. 「付款後庫存扣帳失敗」的補償策略 | ⏳ 未動（需業務決策） |
| 5. DEF-047 / DEF-048 / 第九輪 PRD 掃描 | ⏳ 未動，續列 |

### 7.2 本輪產出的 Action Items

| # | 項目 | 說明 |
|---|------|------|
| 1 | ~~**回填雲端 CI 實測**（下一輪開工）~~ | ✅ **已於本輪完成，見 §4.11**（run `33527771681` 當日即完成，不必等下一輪）。(a) 已由 log 直接證實：surefire 印出 `Tests are skipped.`，failsafe 仍跑滿 404 支零失敗；(b) 雲端 fork 重用約 **4.37 倍**，兩個槓桿合計使整合步驟 1357s→253s（**−81.4%**）。**單一樣本，且已知 runner 變異達 21%——「效益為真」可斷言，「倍率精確」不可** |
| 2 | **更正歷史測試計數的引用方式** | 本輪證實 `reuseForks=false` 會重複計入 `@Nested`，S100~S103 記錄的單元測試數偏高約 60。**不回頭改寫歷史 Plan**（那些數字是當時的真實觀測），但日後引用基準數時應以 1014 為準，並改用「原始碼 `@Test` 實數 + 參數化展開」交叉核對 |
| 3 | 橫向掃描剩餘的計數／額度型欄位 | 承 S103，續列 |
| 4 | 🟢 DEF-052（不存在的 `-Pintegration-test` profile） | 新記錄，文件缺陷 |
| 5 | 第九輪 PRD 全文掃描 | 續列 |

---

## 8. 方法論教訓

1. **「連續三輪被跳過的項目」值得先查它的前提，而不是先動它的設定。** 本輪最關鍵的兩個發現（設定來源未經查證、CI 重跑單元測試）都不在 DEF-049 的記錄裡，而 DEF-049 記錄的「第一支槓桿」定性（單一設定變更）也不準確。三輪的延後成本，其實花在一個描述本身就不精確的問題上。
2. **「先預測再實測」會暴露模型的盲點。** 4.2 的模擬預測省 561 秒、實際省 1298 秒。若沒有先寫下預測，就只會看到「變快了」而不會發現**真正的成本結構是 JVM 暖機而非啟動次數**——那個認識才是可以複用到其他專案的東西。
3. **設定變更後，先追查測試數的任何變化，再看效能數字。** 本輪 1077→1017 一度像是「60 個測試被靜默跳過」。若當時直接採信 −78% 的加速，就會把一個計數缺陷當成成果發布。追查的結果反而找到一個存在已久的記錄錯誤。
4. **護欄要能證明自己還活著。** `ThreadLocalIsolationExtensionTest` 只證明「呼叫它會清乾淨」，不能證明它有被註冊。少了 `ThreadLocalIsolationAutoRegistrationTest`，一個打錯字的註冊檔會讓護欄無聲失效而所有測試依舊全綠。**護欄本身需要一個會因護欄失效而失敗的測試。**
5. **「三種情境都測過」不等於測完了。** 4.9 的回歸在本機通過了我自己設想的全部三種情境，卻打斷了第四種——一個我沒想到要列舉的既有用法（`mvn package -DskipTests`）。**在共用組態（pom、workflow、全域設定）上動手時，風險不在「我改的那條路徑」，而在「別人已經依賴、而我沒有列舉的路徑」**。這也正是 S103 記下的同一個模式：修一個缺陷的同時親手做出另一個。這次是完整驗證程序把它攔下來的——這就是為什麼 `make validate-release` 不能因為「本機都綠了」而跳過。
6. **承 S103**：橫向掃描與樣式比對會找對位置、推錯後果。本輪則是另一種——**記錄本身會把「當時的推測」寫成「已確認的事實」**（DEF-049 的「單一設定變更，影響最大」）。傳給下一輪之前，推測與實測必須在文字上可區分。

---

**文件版本**: v1.0｜**建立者**: Claude Code（AISDLC v0.09 Sprint Planning）｜**基於**: DEF-049（Sprint 101 記錄，S102 修正方向，連續三輪列為候選）
