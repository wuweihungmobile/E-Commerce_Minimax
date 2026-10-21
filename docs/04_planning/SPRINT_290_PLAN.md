# Sprint 290 Plan — IDE Problems 面板清理：241 筆警告／資訊 → 0

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 289 總結，回覆「請繼續完成任務！」，並**另外明確交辦一件事**：IDE Problems 面板有 241 筆同類問題，附一筆範例請求解決——`SecurityConfig.java` 第 166 行（`.map(String::trim)`），訊息為 `Null type safety: parameter 'this' provided via method descriptor Function<String,String>.apply(String) needs unchecked conversion to conform to '@Nonnull String'`。

對「繼續」的解讀（推論，不是使用者說的）：總結裡沒有任何 `==>` 逐項回覆，依既有慣例（[[inline-decision-replies-mean-proceed]] 的「無 `==>` 變體」）不替使用者決定 `DEF-397`。本輪以使用者**明確交辦**的 IDE 問題為主；總結「下一輪候選」的其餘項目不排入：

| 候選 | 本輪處置 | 理由 |
|---|---|---|
| 核對 `afa2edbc` 的雲端 CI | **已做** | run 39470335748，headSha 與本機一致，三個 job 皆 success |
| `DEF-397`（PRD §16.4.1 `Content-Type` charset） | 不排入 | 政策決定，總結已標「等你裁決」，沒有回覆就不替使用者決定 |
| `targetTenantId` 不驗證租戶存在 | 不排入 | 後端行為變更，要另跑全量 `mvn verify`；與本輪「只動註解與 IDE 設定」的性質不同，混在一起會讓驗證失去焦點 |
| `DEF-394` 前端管理介面、`getCurrentTenant()` 語意稽核 | 不排入 | 需求不明／規模大 |

開工先核對上輪收尾，不採信貼回的總結：`git status` 乾淨，`main`＝`origin/main`＝`afa2edbc`。

### 1.2 成功標準（開工時定義）

1. IDE 診斷（`getDiagnostics`，即 Problems 面板的資料來源）對整個工作區回報 **0 筆**（Error／Warning／Information 皆無）。這是使用者的原始訴求，也是唯一能直接證明「面板乾淨」的證據。
2. `mvn -o clean test-compile` 成功，且日誌有 `Compiling N source files`（[[mvn-phantom-build-success-after-failure]]：只有 `BUILD SUCCESS` 沒有這行＝沒編）。
3. 被修改的測試類別全數通過，且**測試數量不因抑制／移除而減少**。
4. `src/main` 零變動（`git diff --stat -- backend/src/main` 為空）：這輪只動註解與 IDE 設定，不動任何執行邏輯。
5. 配合既有慣例（CLAUDE.md Rule 11）：`unchecked` 用最小範圍的 `@SuppressWarnings("unchecked")`，未使用欄位沿用前人 `5adb6ebb` 的 `@SuppressWarnings("unused")`。

## 2. 診斷：241 筆是什麼

原始診斷：238 Warning＋3 Information＋**0 Error**，涉及 74 個檔案（main 91／test 150）。依 JDT 問題代碼分組：

| # | 訊息類型（JDT 代碼） | 筆數 | main／test | 根因 | 處置 |
|---|---|---:|---|---|---|
| A | `Null type safety: … method descriptor … needs unchecked conversion to conform to '@Nonnull …'`（67109822） | 135 | 87／48 | 全是 `Type::method` 方法參考；`nullAnalysis.mode: "automatic"` 啟用了 Eclipse null 分析 | `.vscode/settings.json` 改 `"disabled"` |
| B | `Potential null pointer access`（536871364） | 4 | 4／0 | 同上：關閉後消失；讀碼確認三處都是分析器無法推論的流程，不是缺陷（§2.2） | 隨 A 消失，不改程式 |
| C | `Type safety: …unchecked…`（16777748／16777786／16777747） | 71 | 0／71 | Mockito 的 `any(RedisScript.class)`、`any(ParameterizedTypeReference.class)`、`ArgumentCaptor.forClass(List.class)`、`mock(ValueOperations.class)` 等以 raw `Class` 取得泛型物件 | 最小範圍 `@SuppressWarnings("unchecked")`（25 處） |
| D | `A generic array of … is created for a varargs parameter`／`Potential heap pollution via varargs parameter`（67109438／67109670） | 22 | 0／22 | 泛型 varargs：`givenBatches(List<UUID>...)`（兩個 Timeout 測試各 6 筆）、Mockito `thenReturn(a, b, …)` 傳入 `List`／`Map`／`Optional`（10 筆） | `givenBatches` 加 `@SafeVarargs`（2 處，消 12 筆）；其餘 `@SuppressWarnings("unchecked")`（8 處） |
| E | `The value of the field … is not used`（570425421） | 5 | 0／5 | 欄位只宣告（或只寫不讀） | `@SuppressWarnings("unused")`（5 處） |
| F | `At least one of the problems in category 'unused' is not analysed …`（1102，Information） | 3 | 0／3 | 掛在**實際有被使用**的欄位上的多餘 `@SuppressWarnings("unused")` | 移除（3 處） |
| G | `Unnecessary @SuppressWarnings("unchecked")`（536871547） | 1 | 0／1 | `inv.getArgument(2)` 本身是泛型方法，指派給 `List<String>` 不產生 unchecked | 移除（1 處） |

合計 135＋4＋71＋22＋5＋3＋1＝241。

### 2.1 為什麼 135 筆是誤報而不是真問題

- 逐筆抽出診斷範圍內的原始碼文字：135 筆**全部**是 `Type::method` 方法參考，含 `Integer::sum` 被報成「參數 1／2 需要 unchecked conversion to 'int'」的拆箱變體。
- 專案 `backend/src` 內沒有任何 `@Nullable`／`@NonNull`／`@Nonnull`／`@NonNullApi`／`@NonNullByDefault`（grep 0 筆；有的只有 Bean Validation 的 `@NotNull`，那不是 Eclipse null 分析認得的註解）。null 分析在這個專案裡沒有任何東西可以分析，只剩對 JDK／Spring 簽章的誤報。
- 訊息字面說的是：方法參考的接收者（`this`）要求 `@Nonnull`，而 `Function.apply` 的參數沒有 null 註解。
- 設定自初始 commit（`e358b1a1`）就是 `"automatic"`；`5adb6ebb` 當年處理 IDE 警告時沒動它。專案是 Spring Boot 3.2.5，推論是 `automatic` 因 classpath 上有 Spring／JSR-305 的 null 註解而啟用分析（機制是推論，未逐行追蹤擴充套件原始碼）。**但因果關係有實測佐證，不靠推論**：改設定後語言服務重建，135＋4 筆全部消失，其餘 102 筆的位置與型態與原清單逐項吻合。
- javac／Maven／CI 不做這項分析：`mvn -o clean test-compile` 的 34 筆 `[WARNING]` 全是既有的 SpotBugs 註解缺失與註解處理器訊息，沒有任何一筆提到本輪涉及的檔案。

### 2.2 B 類 4 筆被一併關掉：逐一讀碼，皆非缺陷

| 位置 | 為什麼分析器覺得「可能是 null」 | 為什麼實際不會 |
|---|---|---|
| `SupportTicketService.java:96`（`ticket`） | `ticket` 先設為 `null`，迴圈內才指派 | 迴圈每一輪不是 `break` 就是丟例外；最後一輪（`TICKET_NUMBER_MAX_ATTEMPTS = 3`）在 catch 裡 `throw e`，迴圈不可能正常結束而 `ticket` 仍為 `null` |
| `TenantService.java:531`、`:536`（`toggle`） | 前面有 `toggle == null` 判斷，分析器無法把布林變數 `isNewToggle` 與它關聯 | `isNewToggle` 就是 `toggle == null`；第 529 行之後兩條分支都保證非 null |
| `JwtTokenService.java:54`（`secret`） | 第 49 行寫了 `secret != null &&` | 多餘的防衛：`@Value` 注入的是字串（佔位符無法解析時 Spring 在呼叫建構子之前就會失敗），不會是 `null`；測試中也沒有 `new JwtTokenService(null, …)` |

### 2.3 被否決的替代方案

| 替代方案 | 為什麼不 |
|---|---|
| 把 87 個 main 的方法參考改寫成 lambda | 為了迎合 IDE 誤報去改 87 行正式程式碼，違反 Rule 3（精準改動）；lambda 也比 `String::trim` 難讀 |
| 保留 null 分析，另放 `.settings/org.eclipse.jdt.core.prefs` 逐項關閉問題類別 | 多一個 IDE 專屬檔案進版控；`.vscode/settings.json` 本來就是專案共享的 IDE 設定，單行修改最小 |
| E 類改為刪除未使用欄位 | 前人 `5adb6ebb` 的明確做法是加 `@SuppressWarnings("unused")`（全專案已有 45 處，含 `@Autowired` 與 `static final`），Rule 11 配合慣例；刪除可日後另議 |
| C、D 類改成 `@Captor`／`ArgumentMatchers.<T>any()`／鏈式 `thenReturn` | 每處都要改測試邏輯，風險大於收益；既有 `RefreshTokenServiceTest`、`SettlementGeneratorClaimTest` 等已用方法層級／區域變數層級的 `@SuppressWarnings("unchecked")` |

## 3. 執行與驗證

### 3.1 設定改動與 IDE 重新分析

`.vscode/settings.json`：`java.compile.nullAnalysis.mode` 由 `"automatic"` 改 `"disabled"`，並加三行註解說明原因與還原方法。

改完立刻查詢，診斷**全空**——這不是成功：語言服務正在重建工作區，診斷會先被清掉。**沒有採信**，等重建完成後再查，結果為 **102 筆，全在 21 個測試檔，main 為 0**。

### 3.2 測試程式碼修改（21 檔，+40／−4，全是註解）

| 修改 | 數量 |
|---|---:|
| 新增 `@SuppressWarnings("unchecked")`（方法層級 26＋區域變數／巢狀類別方法 7） | 33 |
| 新增 `@SafeVarargs`（`BookingTimeoutServiceTest`、`OrderTimeoutServiceTest` 的 `givenBatches`，方法只讀陣列、為 private，Java 21 允許） | 2 |
| 新增 `@SuppressWarnings("unused")` | 5 |
| 移除多餘的 `@SuppressWarnings("unused")` | 3 |
| 移除多餘的 `@SuppressWarnings("unchecked")` | 1 |

F 類 3 筆移除前先確認欄位真的有被讀取：`M16ErpIntegrationTest.testSellerUserId`（6 處）、`BookingControllerE2ETest.tenantRepository`（3 處）、`BookingControllerE2ETest.jdbcTemplate`（實例方法 4 處）。同檔其他真正沒用到的欄位（`testSupplierId`、`testInventoryId` 等）的抑制**保留**。

### 3.3 驗證

| 項目 | 結果 |
|---|---|
| `mvn -o clean test-compile` | `BUILD SUCCESS`，主程式 386 檔、測試 302 檔**真的重新編譯**（1:39）；34 筆 `[WARNING]` 皆為既有、與本輪無關 |
| 被修改的 13 個單元測試類別 | **179 個全過**，0 失敗／0 錯誤／0 跳過（`RedisCartServiceTest` 外層 0、巢狀類別合計 41） |
| 被修改的 8 個整合／E2E 測試類別（`failsafe:integration-test -Dit.test=…`，對真實 PostgreSQL／Redis） | **125 個全過**（25＋7＋8＋19＋8＋41＋15＋2），0 失敗／0 錯誤／0 跳過，`BUILD SUCCESS`（2:24） |
| `checkstyle-test`（測試原始碼）＋ PMD 7.0.0（pom 綁在 `verify`，含測試） | Checkstyle **0 個違規**；PMD `BUILD SUCCESS`（50.7 秒）。補這一步是因為 `mvn test` 不會觸發綁在 `verify` 的 `checkstyle-test`（[[sprint-full-regression-policy]] 的已知漏洞），而這輪改的全是測試原始碼 |
| IDE 診斷（最終） | 整個工作區**每一筆 `diagnostics` 皆為空**（Error／Warning／Information 皆 0）：241 → 102 → **0** |
| 正向對照（§3.4） | 探針檔：未使用欄位與無效 unchecked 轉型**有**警告，`String::trim` 與已抑制欄位**沒有**；探針與其 `.class` 已刪，未入版控 |
| `git diff --stat -- backend/src/main` | 空（零變動） |
| push／雲端 CI | `0d9ff326` 已 push（pre-push 輕量守門：act Backend Unit 2m30s／Frontend Lint & Build／schema 漂移守門全過）；雲端 run [39523474421](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39523474421) 三個 job 皆 success（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build），以 `gh run view --json` 交叉確認 `headSha` 與本機一致（約 13 分鐘） |

**歸零同時證明新加的抑制都是必要的**：IDE 的「多餘抑制」檢查是開著的（它抓到了 G 類那一筆），如果新加的 40 個註解有任何一個沒有實際抑制到問題，歸零之後會出現新的 `Unnecessary @SuppressWarnings`。

### 3.4 正向對照：證明「全空」不是分析器沒在跑

同一個工作階段裡已經見過一次假象——改設定後**第一次**查詢全空，其實是語言服務正在重建。所以「最後也是全空」本身不能直接當作成功，要證明分析器現在是活的、而且設定確實生效。做法是放一個暫時的探針檔（`ZzIdeDiagnosticsProbe.java`，跑完即刪、不入版控，延續 Sprint 289 的做法），先等它的 `.class` 出現（代表語言服務已編譯它）再查：

| 探針內容 | 預期 | 實測 |
|---|---|---|
| 未使用的 private 欄位 | 有警告（`unused` 分析是活的） | ✅ `The value of the field … is not used`（代碼 570425421，與 E 類同一代碼） |
| `(List<String>) value` 無效轉型 | 有警告（`unchecked` 分析是活的） | ✅ `Type safety: Unchecked cast from Object to List<String>` |
| 加了 `@SuppressWarnings("unused")` 的未使用欄位 | 無警告（抑制有效，且不被誤報成多餘） | ✅ 無 |
| `Function<String,String> f = String::trim`（使用者貼的那一類） | 無警告（null 分析已關閉） | ✅ 無 |

探針與其 `target/test-classes` 內的 `.class` 已刪除，語言服務也清掉了它的診斷；`git status` 沒有出現探針。

### 3.5 過程中的觀察

- 改完檔案後的第一次完整查詢，`BookingCancellationRefundIntegrationTest` 出現一筆 **Error**（`Duplicate annotation of non-repeatable type @SuppressWarnings`），但磁碟上該檔只有一個註解、IDE 回報的行數也與磁碟一致。針對該檔單獨重查後為空。判斷是語言服務讀到了檔案原子寫入（暫存檔改名）期間的過渡狀態；教訓是**看到非預期診斷先對照磁碟內容、再重查，不要直接改檔**。
- 我最初用「往上找最近的方法宣告」自動推測每個警告所屬的方法，結果把呼叫敘述誤判成方法宣告（如 `givenBatches(List.of());`）。改成逐檔讀實際程式碼後才下手改，沒有用那份推測結果。

## 4. 文件更新

- 本計畫書。
- `RELEASE_TRACKER.md`：新增 Sprint 290 row（v2030.04.147-01）、統計區、版本歷史 v4.08。
- 記憶：IDE 問題的診斷方法與這次的結論。

## 5. 已知限制／留給未來

- **關閉 null 分析的代價**：IDE 不再提示「傳 null 給 Spring `@NonNull` 參數」這類真正的 null 契約違規。專案現況沒有依賴這項功能（javac／Maven／CI 都不做 null 分析；本輪實測 139 筆全是誤報／無缺陷）。將來若想導入 null 註解（例如 JSpecify），把設定改回 `"automatic"` 即可，屆時誤報會因為專案真的有註解可分析而減少。
- 設定在 `.vscode/settings.json`，受版控，兩位開發者都會套用；已開啟的 VS Code 視窗，Java 語言服務需要重建一次才生效（本次實測在幾分鐘內完成）。
- 本輪沒有跑全量 `mvn verify`：只動註解與 IDE 設定、`src/main` 零變動，依 [[sprint-full-regression-policy]] 跑受影響的測試即可；push 時雲端 CI 會跑完整的 Backend Integration Tests。
- `getDiagnostics` 只反映 VS Code 當下的分析結果；其他人的 IDE 若用不同的 Java 擴充版本或設定，看到的清單可能不同。
- 雲端 CI 的 `Frontend Lint & Build` 步驟另外印出 **10 筆既有的 ESLint warning**（5 個前端檔：`at-storefront-real.spec.ts`、`cart/page.tsx`、`checkout/page.tsx`、`admin/tenants/[id]/review/page.tsx`、`blog/[slug]/page.tsx`；未使用變數、`<img>`、Hook 依賴）。它們不影響通過、不在這次的 241 筆內（那是 Java 診斷），本輪 0 個前端檔案被動到，所以沒處理；若你想連前端 Problems 一併清，可另列一輪。
- `DEF-397` 仍待使用者裁決（建議選修訂 PRD 措辭，見 Sprint 289 計畫書 §5）。
