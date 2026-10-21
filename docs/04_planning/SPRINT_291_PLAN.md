# Sprint 291 Plan — DEF-394 收尾：`targetTenantId` 指定的店鋪不存在時改回 404，不再靜默回空清單或建出孤兒範本

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 290 總結，回覆「請繼續完成任務！」。

對「繼續」的解讀（推論，不是使用者說的）：總結裡沒有任何 `==>` 逐項回覆，依既有慣例（[[inline-decision-replies-mean-proceed]] 的「無 `==>` 變體」）不替使用者決定 `DEF-397`，只做總結列為「還沒做」、AI 能獨立處理且不涉產品決策的項目：

| 候選 | 本輪處置 | 理由 |
|---|---|---|
| 核對 `544be7a4` 的雲端 CI | **已做** | run 39525034180，三個 job（Backend Unit Tests／Frontend Lint & Build／Backend Integration Tests & Package）皆 success，`headSha` 與本機一致 |
| `targetTenantId` 不驗證租戶存在 | **本輪主題** | Sprint 287 自己留下的限制；預期行為有現成慣例可循（`E-2000`→404）；後端行為變更，需要全量 `mvn verify`，所以不跟「只動註解」的 Sprint 290 混在一起 |
| `DEF-397`（PRD §16.4.1 `Content-Type` charset） | 不排入 | 政策決定，總結已標「等你裁決」，沒有回覆就不替使用者決定 |
| `DEF-394` 前端管理介面 | 不排入 | PRD／FRD 沒有管理員畫面的需求，屬新功能而非缺口（§7） |
| 150+ 處 `getCurrentTenant()` 語意稽核 | 不排入 | `DEF-330(c)` 已記「超出單次 Sprint 範圍」 |
| 前端既有 ESLint warning | 只更正數字、未處理 | 實測是 99 筆不是 10 筆（§5），範圍與做法需要先決定 |

開工先核對上輪收尾，不採信貼回的總結：`git status` 乾淨；`git fetch` 後 `main`＝`origin/main`＝`544be7a4`（`rev-list --left-right --count HEAD...origin/main` 為 `0 0`）。

### 1.2 成功標準（開工時定義）

1. **修復前先有紅燈**：ADMIN 帶不存在的 `targetTenantId`，列表與建立都應回 404 `E-2000`，且建立後不得留下歸屬該 ID 的資料列——先對**未修改**的程式碼實測為紅，證明缺陷真的存在（`API_M09_Notification.md` 原本只寫「讀碼確認、未實測」）。
2. 修復後轉綠，且**非 ADMIN** 帶不存在的 `targetTenantId` 的行為不變（仍被忽略、仍 200），否則一般使用者能靠 404／200 探測任意租戶 ID 是否存在。
3. 每個新守門都有突變驗證：弱化、反轉、換錯誤碼、兩個呼叫點各自還原、「對所有人都檢查」的錯誤實作、收窄成「僅 ACTIVE」。
4. 全量 `mvn verify` 通過（生產邏輯有改，依 [[sprint-full-regression-policy]]）；checkstyle（主程式＋測試）0 違規、PMD 通過。
5. 現行文件與程式碼一致：原記為「讀碼確認、未實測」的限制改成實測結果。

## 2. 實測：修復前的行為

`notification_templates.tenant_id` 在 V28 定義為 `UUID`（可為 `NULL`），只有索引、沒有外鍵；全部 Flyway migration 只有 V28 提到這張表。`NotificationTemplateRepository.searchTemplates` 以 `t.tenantId = :tenantId` 等值比對查詢。

先寫好 4 個整合測試（`IT-M09-021`～`024`），對**未修改**的生產程式碼執行（`failsafe:integration-test`，真實 PostgreSQL／Redis）：

| 測試 | 情境 | 修復前實測 |
|---|---|---|
| `IT-M09-021` | ADMIN 帶不存在的 `targetTenantId` 查列表，應 404 `E-2000` | **紅**：`Status expected:<404> but was:<200>` |
| `IT-M09-022` | ADMIN 帶不存在的 `targetTenantId` 建立，應 404 `E-2000` 且不留資料列 | **紅**：`Status expected:<404> but was:<200>` |
| `IT-M09-023` | 非 ADMIN 帶不存在的 `targetTenantId` 查列表，仍應 200 | 綠（本來就如此） |
| `IT-M09-024` | 非 ADMIN 帶不存在的 `targetTenantId` 建立，仍應 200 且歸屬自己的租戶 | 綠（本來就如此） |

結果 `Tests run: 24, Failures: 2`。誠實標示：建立路徑的回應是 200，所以 `save` 已執行、孤兒範本已寫入——但「資料列確實存在」那個斷言在修復前沒有被執行到（前一個狀態碼斷言先失敗），這一段是由程式碼推論（`createTemplate` 唯一的寫入就是 `save`），不是直接量到；列表回應的本文為空也是由查詢條件推論，實測量到的是狀態碼 200。

**測試手法的觀察**：`failsafe:integration-test` 單獨執行時，即使有 2 個失敗仍印 `BUILD SUCCESS`、exit 0（失敗要到 `verify` 目標才擋建置），判讀一律看 `Tests run:` 那一行。

## 3. 修法與設計決定

- `NotificationTemplateService` 新增 `requireExistingTenant(UUID)`（`tenantRepository.existsById`，不存在拋 `BusinessException(E_2000)`，`GlobalExceptionHandler` 對應 404）。
- `NotificationTemplateController` 的列表與建立各有一個三元運算解析 `targetTenantId`：只在 `isAdmin(principal) && targetTenantId != null` 的那一支呼叫 `requireExistingTenant`，另一支（沿用呼叫者自己的租戶）不變。
- 不新增錯誤碼（`E-2000`「找不到租戶」是 `AdminService`、`TenantService` 等十幾處既有的慣例），所以不必動 `ErrorCode`／`GlobalExceptionHandler`／錯誤碼文件那四處同步。

**我替使用者做的決定（可一句話推翻）**

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | 回 404 `E-2000`，而不是 400／422 或新錯誤碼 | 沿用既有慣例，不增加錯誤碼同步成本 | 想改狀態碼，改 `requireExistingTenant` 拋的錯誤碼即可 |
| 2 | **列表也檢查**，不只擋建立 | 打錯 UUID 靜默回 200 空清單，會被誤認為「該店鋪沒有範本」，與建立是同一類問題 | 只想擋建立：把列表那一處三元運算還原成 `targetTenantId`（突變 M3 就是這個形狀） |
| 3 | **只對 ADMIN／SUPER_ADMIN 檢查**，非管理員的 `targetTenantId` 仍被忽略、不檢查 | 維持 Sprint 287 既有行為；檢查會讓一般使用者靠 404／200 探測任意租戶 ID 是否存在 | — |
| 4 | **只看租戶在不在、不看狀態** | 管理員本來就要能處理停權／終止店鋪的範本；與 Sprint 287 的 get／update／delete 旁路一致（那三個完全不看狀態） | 想限制為 ACTIVE：把 `existsById` 換成 `findByIdAndStatus(…, ACTIVE).isPresent()`（突變 M7 就是這個形狀） |
| 5 | **不加資料庫外鍵** | 既有資料是否已有孤兒未查；加外鍵是 schema 變更（還要 `make sync-schema-doc`），超出本輪範圍 | — |

## 4. 驗證

### 4.1 新增測試

| 層級 | 案例 | 守的意圖 |
|---|---|---|
| 單元（`NotificationTemplateServiceTest`，25→**27**） | `TC-T021` 存在時原樣回傳 ID | 回傳值可直接當解析結果；stub 明寫 `thenReturn(true)`，不靠 Mockito 對 `boolean` 預設回 `false` |
| | `TC-T022` 不存在時拋 `E_2000` 並 `verify(existsById)` | 守門真的有去查，且錯誤碼正確 |
| 整合（`M09NotificationTemplateIntegrationTest`，20→**25**） | `IT-M09-021` ADMIN 列表 404 | 列表不再靜默回空 |
| | `IT-M09-022` ADMIN 建立 404＋`existsByTemplateCodeAndTenantId` 為 false | 不留孤兒範本 |
| | `IT-M09-023` 非 ADMIN 列表仍 200 | 不成為租戶存在性探測口（列表路徑） |
| | `IT-M09-024` 非 ADMIN 建立仍 200 且歸屬自己的租戶 | 同上（建立路徑，兩條路徑各自把關，不能只測其中一條） |
| | `IT-M09-025` ADMIN 對 `SUSPENDED`／`TERMINATED` 店鋪仍查得到 | 釘住「不看狀態」這個設計決定 |

### 4.2 突變驗證（8 個，全部 KILLED，失敗集合與預期逐一吻合）

驅動腳本（備份→套用→等 class 比源碼新→跑指定測試→還原→`cmp`＋sha256；`old` 必須恰好出現一次，否則該突變直接標 INVALID）放在 scratchpad，不進 repo。每個突變都跑 `NotificationTemplateServiceTest`＋`M09NotificationTemplateIntegrationTest`（`-Dmaven.test.failure.ignore=true`，單元失敗時整合測試仍會跑，才看得到完整擊殺面）。

| # | 突變 | 擊殺者（實測，與預期完全一致） |
|---|---|---|
| M1 | `requireExistingTenant` 條件加 `&& targetTenantId == null`（永遠不拋） | `TC-T022`、`IT-021`、`IT-022` |
| M2 | 條件反轉（存在時才拋） | `TC-T021`、`TC-T022`、`IT-018`、`IT-019`、`IT-021`、`IT-022` |
| M3 | 列表呼叫點還原成不檢查 | **只有** `IT-021` |
| M4 | 建立呼叫點還原成不檢查 | **只有** `IT-022` |
| M5a | 列表在三元運算之前對**所有人**檢查 | **只有** `IT-023` |
| M5b | 建立在三元運算之前對**所有人**檢查 | **只有** `IT-024` |
| M6 | 錯誤碼改 `E_1007` | `TC-T022`、`IT-021`、`IT-022` |
| M7 | 「存在」收窄成「存在且 ACTIVE」（`findByIdAndStatus`） | `TC-T021`、`TC-T022`、`IT-025` |

M3／M4 與 M5a／M5b 都只擊殺各自對應的那一個案例，證明兩個呼叫點各自獨立受到覆蓋。每個突變跑完都確認 class 時間戳追上還原後的源碼；全部結束後兩個被突變的檔案 sha256 與突變前逐位元組相同，並以殘留字串掃描確認沒有任何突變殘留。

**誠實標示：M1 第一次執行是無效的**。第一輪的 M1 在 15 秒後以 exit 1 結束，沒有任何測試結果（surefire 的 JVM 載入階段 `TestEngine with ID 'junit-jupiter' failed to discover tests`），驅動正確地把它判成 INVALID 而不是 KILLED／SURVIVED；成因推測是 IDE 的 Java 語言服務同時在寫 `target`（未驗證）。重跑後 M1 有效且被擊殺。另外，M7 是在第一輪之後才補上的（先補了 `IT-025` 才有東西可殺）。

### 4.3 其他驗證

| 項目 | 結果 |
|---|---|
| `mvn -o clean test-compile` | 主程式 386／測試 302 檔真的重新編譯（`Compiling N source files`）。**第一次 `clean` 因 `Failed to delete …/target/classes` 失敗**（IDE 同時寫 `target`，與既有記憶一致），重試即成功 |
| 單元＋整合基準（含 `IT-M09-025`） | `NotificationTemplateServiceTest` 27／27、`M09NotificationTemplateIntegrationTest` 25／25，0 失敗／0 錯誤／0 跳過 |
| Checkstyle（`checkstyle-main`、`checkstyle-test`）＋ PMD 7.0.0 | 兩個 execution 皆 0 違規；PMD `BUILD SUCCESS`。單獨先跑是因為 `checkstyle-test`／PMD 綁在 `verify` 末段，不先跑的話違規要等全量跑完才看得到 |
| 全量 `mvn -o clean verify`（真實 PostgreSQL／Redis） | **BUILD SUCCESS（26:35）**：主程式 386／測試 302 檔真的重編；單元（surefire）**2295** 個、整合／E2E（failsafe）**878** 個，皆 0 失敗／0 錯誤／0 跳過；日誌沒有重試或不穩定訊號、`[ERROR]` 行 0。`ApiRouteDocDriftTest` 2／2（文件路由漂移守門，本輪改了 `API_M09_Notification.md`）、`NotificationTemplateServiceTest` 27／27、`M09NotificationTemplateIntegrationTest` 25／25；Checkstyle 主程式／測試各 0 違規、PMD 與 `failsafe:verify` 皆通過 |
| `make validate-e2e` | 沒跑：`frontend/src` 全文搜尋 `targetTenantId` 為 0 筆，前端沒有任何地方送這個參數，新行為在前端不可達 |

## 5. 更正：前端 ESLint warning 是 99 筆，不是 10 筆

Sprint 290 的總結與計畫書寫「雲端 CI 印出 10 筆既有 ESLint warning（5 個前端檔）」。本輪為了評估下一輪範圍而重新量測，**數字不對**：

- 雲端日誌（run 39525034180，`Frontend Lint & Build`）的彙總行：`✖ 99 problems (0 errors, 99 warnings)`。
- 本機 `cd frontend && npm run lint`（與 CI 的 `ESLint` 步驟是同一個指令）：同為 **99 筆、55 個檔**。

| 規則 | 筆數 | 檔數 |
|---|---:|---:|
| `@typescript-eslint/no-unused-vars` | 36 | 17 |
| `react-hooks/exhaustive-deps` | 26 | 25 |
| `import/no-anonymous-default-export`（全在 `src/services/`） | 22 | 22 |
| `@next/next/no-img-element` | 13 | 11 |
| `@next/next/no-location-assign-relative-destination`（`lib/axios.ts`、`services/oauth.ts`） | 2 | 2 |

S290 列的那 5 個檔案確實都在這 99 筆內（`at-storefront-real.spec.ts` 1、`cart/page.tsx` 1、`checkout/page.tsx` 3、`admin/tenants/[id]/review/page.tsx` 2、`blog/[slug]/page.tsx` 3，合計 10 筆）——所以「10」是只看到其中 5 個檔案的結果，不是憑空而來；少算的是另外 50 個檔案的 89 筆。為什麼當時只看到這 5 個檔案，沒有追查。

**本輪沒有處理這 99 筆**，理由：範圍是原認知的近 10 倍；其中 `exhaustive-deps` 與 `no-img-element` 需要逐案判斷行為（專案對這兩條規則已有 `eslint-disable-next-line` 的慣例，各 9 處）；而且 IDE 診斷對這些 `.ts`／`.tsx` 是 0 筆（Sprint 290 的 Problems 面板是 Java），它們只出現在 CI 日誌與終端機。建議做法見最終回報。

## 6. 文件更新

- `docs/02_architecture/api/API_M09_Notification.md`：v1.1 → **v1.2**（檔頭、最後更新、文件尾端「文件狀態」三處）；§2 總表段落、§4.12、§4.14（欄位說明＋錯誤表新增 404 `E-2000` 一列）、租戶範圍段落；§7 原記為「讀碼確認、未實測」的限制改為刪除線＋實測結果。現行文件中提到這個限制的只有這一份（以檔名排除歷史 `SPRINT_*_PLAN.md` 後逐檔掃描）。
- `DEFERRED_ITEMS_TRACKER.md`：`DEF-394` 那列補 Sprint 291 收尾；版本鏈 v3.65 → v3.66。
- `RELEASE_TRACKER.md`：新增 Sprint 291 row（v2030.04.148-01）、統計區、版本歷史 v4.09。
- 本計畫書。
- 記憶：本輪的結論與坑。

## 7. 已知限制／留給未來

- **資料庫層仍沒有外鍵**：檢查只在應用層。日後若新增繞過這兩個端點的寫入路徑，仍可能產生孤兒範本。**既有資料是否已有孤兒範本沒有查**（需要對真實資料庫跑一次 `LEFT JOIN tenants` 找 `tenant_id` 非 `NULL` 且對不到租戶的列）。
- `renderTemplate`（`§4.17`）仍只依呼叫者自己的租戶查找，不在跨租戶範圍（Sprint 287 既有設計，未變）。
- **`DEF-394` 前端管理介面仍未做**：`docs/01_requirements` 全文搜尋通知範本相關字樣，只有 FRD §6C.2 的「通知範本管理 ✅（端點）」與 PRD 的模組／資料表／路線圖條目，沒有任何管理員畫面的需求，所以是新功能而不是缺口；等使用者有需求再排。
- `DEF-397` 仍待使用者裁決（建議不變：修訂 PRD 措辭，見 Sprint 289 計畫書 §5）。
- 前端 99 筆 ESLint warning 尚未處理（§5）。
