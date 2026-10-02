# Sprint 233 Plan — 安全硬化：JWT 預設密鑰防護補洞（DEF-320）與 bcrypt 成本對齊 FRD NFR（DEF-323 (a)）

**Sprint**: Sprint 233
**日期**: 2026-10-02

## 1. 缺口盤點結果

### 1.1 起點與排程（排程順序為我的安排，屬推論）

Sprint 232 結尾我以 `AskUserQuestion` 提出四個決定，使用者全部答覆（見 §2）。這等於授權了**多個 Sprint** 的工作；做法比照 [[inline-decision-replies-mean-proceed]]：把決定排成依賴順序的 Sprint，每個 Sprint 自成一體（實作→測試→突變驗證→全量驗證→文件→commit→push→等雲端 CI→回填）。我的排程如下（**順序是我依「相依＋風險＋影響」排的，不是使用者指定的**）：

| Sprint | 內容 | 為何排這個位置 |
|--------|------|----------------|
| **233（本輪）** | DEF-320 JWT 預設密鑰防護補洞；DEF-323 (a) bcrypt 12 | 小、互不相依、不需決定；先做是因為它是安全硬化，且沒有理由等 |
| 234～235 | **DEF-319 根因**（同店結帳）：訂房 → 訂單；含 Flyway 回填、優惠券租戶解析 | 影響最大（商家端對真實客人完全無效、週結算不納入）；分兩輪降低風險 |
| 236～237 | 文件對齊（比照 Sprint 203）＋ token 儲存文件改成現況（DEF-323 (b)） | 要等 DEF-319 修完，否則 API 規格又要重寫一次 |
| 238 | 店鋪成員管理 UI（後端 7 個端點齊備） | 獨立 |
| 239 | 修 CMS 嵌入卡片連結（DEF-321 (c)） | 小 |
| 240 | DEF-318 通知事件（預訂確認、支付失敗） | 獨立 |
| 241～242 | 店鋪前台 `/stores/[slug]`（新後端端點＋4 頁＋FRD 補規格） | 最大且最不確定，放最後 |

`/privacy`、`/terms` 需要使用者提供法務文字，我不能代寫，不排入。

> **排程更新（Sprint 234）**：上表 234 起的 Sprint 編號已順延，現行排程見 [SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §1.1（插入「系統租戶假設全面稽核與止血」與「`DEF-327`～`DEF-329`」兩輪）。本表保留原貌作為當時的決定紀錄。

### 1.2 查證（動手前讀程式碼，非憑記憶）

1. **出貨的 JWT 預設密鑰共 3 個**（[SPRINT_232_PLAN.md](SPRINT_232_PLAN.md) §1.3 C1 已讀碼確認，本輪再核對全庫）：`application.yml`:78（`...-key-change-in-production-min-32-chars`）、`docker-compose.yml`:73（`...-key-please-change-in-production`）、`.env.example`:19（`...-key-please-change-in-production-min-32-chars`）。`JwtTokenService` 只用 `equals` 擋第一個。其餘出現 `JWT_SECRET` 之處（CI workflow、`docker-compose.test.yml`、`scripts/validate-*.sh`、測試）都是**明確的測試值**，不是預設值，本來就該被接受。
2. **bcrypt**：`SecurityConfig` 是 `new BCryptPasswordEncoder()`（Spring 預設強度 10）；FRD NFR-SEC-002（P0）與 SRD 要求 ≥ 12。**為什麼沒被測試抓到**：`PasswordEncoderTest`（UT-M03-001～005）用**自己 `new` 出來的** `BCryptPasswordEncoder(10)`，註解還寫「BCrypt 標準 cost factor = 10，我們使用 Spring Security 預設值」，**完全沒碰正式的 bean**——無論 `SecurityConfig` 怎麼設都不會失敗，還把 10 當成既定標準。這是「測試以固件繞過同一段邏輯」的又一例。
3. **登入沒有「為了等化時序而預先算好的假雜湊」**（`grep` 主程式碼無 `$2a$` 字面值、無 dummy）：不會因為成本提高而讓「帳號不存在」與「密碼錯誤」的時間差變大到新增漏洞——原本就有差（找不到使用者直接回 E-1001、不跑 bcrypt），而註冊端點本來就公開「Email 已被註冊」（DEF-284），帳號存在與否已不是秘密，故不處理。
4. **`make setup` 會把 `.env.example` 複製成 `.env`，裡面是佔位密鑰**——修復後照流程 `make setup && make up` 的開發者會遇到後端**拒絕啟動**。這是我的改動直接造成的，必須一併處理（見 §3）。本機沒有 `.env`，所以我自己沒被影響。

### 1.3 更正

無（本輪沒有需要撤回的推論）。**我對 `Makefile` 的第一版修改有轉義錯誤**（`tr -d '\\n'` 會刪掉字母 n 而不是換行），在暫存目錄實際執行該配方才發現，已移除不必要的 `tr`。

## 2. 使用者決策（AskUserQuestion 拍板紀錄，2026-10-02 Sprint 232 結尾）

| # | 問題 | 使用者裁定 |
|---|------|-----------|
| 1 | DEF-319 根因怎麼修（訂單／訂房蓋成下單者的租戶） | **同店結帳**（推薦選項）：訂房改蓋房源所屬店鋪；訂單改蓋商品所屬店鋪，購物車含多家店商品時要求分開結帳；Flyway 回填歷史資料；一併處理優惠券以哪個租戶解析 |
| 2 | 安全 NFR：bcrypt ≥ 12（FRD P0）與 HttpOnly Cookie | **bcrypt 12＋改文件**（推薦選項）：bcrypt 改 12；token 維持 localStorage，PRD／SRD／API 文件改成現況 |
| 3 | PRD 載明、後端已做完卻缺席的前端（可複選） | **四項全選**：店鋪成員管理 UI、修 CMS 卡片連結、店鋪前台 `/stores`、通知事件 DEF-318 |
| 4 | 其餘數十項規格與實作矛盾 | **比照 Sprint 203**（推薦選項）：文件對齊實作；PRD 內文不動只加修訂註記；API／SRD 依實作改寫；FRD 補 M06／M07／M09 章；TC 文件加「現況聲明」不重寫 |

## 3. 實作內容

**DEF-320（JWT 預設密鑰）**：`JwtTokenService` 改為拒絕任何**含 `change-in-production`（不分大小寫）**的密鑰，取代「只比對 `application.yml` 那一個字串」——涵蓋三處出貨預設值與日後同型的變體。新增 `JwtSecretShippedDefaultsTest`：**直接讀三個出貨檔案**取出實際預設值，逐一確認建構 `JwtTokenService` 會被拒絕（比單純列舉已知字串可靠：日後有人改了某個預設值、換成沒被拒絕的字串，這裡會失敗）。找不到預設值也會失敗，不會默默通過。

**DEF-323 (a)（bcrypt）**：`SecurityConfig.BCRYPT_STRENGTH = 12`，`passwordEncoder()` 使用它。**既有 cost 10 的雜湊仍可驗證**（成本編在雜湊字串裡），不需遷移；只有新註冊與重設密碼產生的雜湊是 cost 12。`PasswordEncoderTest` 改為**取正式 bean**（`new SecurityConfig(null,null,null,null).passwordEncoder()`）並斷言 `$2a$12$`；刪掉原本的加密耗時斷言（時間不是意圖，且在慢的 CI 機器上會讓測試不穩；成本直接編在雜湊裡，檢查格式就夠了）；新增「既有 cost 10 雜湊仍可驗證」案例。

**開發流程不被這次修復弄壞**：`make setup` 建立 `.env` 時，自動把佔位的 `JWT_SECRET` 換成 `openssl rand -base64 48` 產生的隨機值；`.env.example` 加註說明「佔位值不可原樣使用，含 `change-in-production` 的值後端一律拒絕啟動」。**未動 `docker-compose.yml`**（CLAUDE.md Docker 規則：未經明確指示不改）——其預設值保持不變，`docker compose up` 沒設 `JWT_SECRET` 時，後端現在會**以清楚的訊息拒絕啟動**，而不是靜默用公開字串簽發 token（這是修復的本意）。

**bcrypt 12 引出的 E2E 不穩，一併修正（我的改動直接造成的）**：第一次完整 `make validate-e2e` 有 1 個失敗（`E2E-BPAYR-08`：「應已登入並持有 accessToken」，null）。頁面快照停在登入頁、按鈕還是「Signing in...」（disabled）——登入請求**仍在進行**，`helpers/auth.ts` 的 `submitLogin` 卻只固定等 4 秒就放行。成本改 12 後，成功的登入在雙 worker 並行負載下偶爾超過 4 秒，token 還沒存好就繼續往下。**不是邏輯錯誤，是固定等待視窗太窄**。修法：`submitLogin` 改為等到「有結果」——成功（離開 `/login`，`waitUntil: 'commit'`）或失敗（登入頁出現 `data-testid="login-error"` 的錯誤區塊），上限 20 秒；登入頁的錯誤 `<div>` 加上這個 `data-testid`（純測試用屬性，不改行為）。**副作用是變快**：`registerAndLogin` 對新 Email 的第一次嘗試會立刻看到錯誤，不必再白等 4 秒（E2E 總耗時 7.2 → 5.9 分鐘）。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元 | `JwtTokenServiceTest` +2（另外兩個出貨佔位字串與大小寫變體都拒絕；真正的密鑰不被誤擋）；`JwtSecretShippedDefaultsTest` +1（讀三個出貨檔案）；`PasswordEncoderTest`：改為測正式 bean、斷言 `$2a$12$`、+1 既有 cost 10 雜湊仍可驗證 |
| 突變驗證（Rule 9） | 同時暫時還原兩處修復（JWT 改回只比對單一字串、`SecurityConfig` 改回 `new BCryptPasswordEncoder()`），**4 個針對性測試轉紅**：`JwtSecretShippedDefaultsTest.everyShippedDefault_isRejected`、`JwtTokenServiceTest.constructor_withOtherShippedPlaceholders...`、`PasswordEncoderTest.encode_passwordCannotBeDecrypted`、`PasswordEncoderTest.encode_verifyCostFactor`；從備份還原，`grep` 確認無殘留 |
| 開發流程 | `make setup` 的 `.env` 產生配方在暫存目錄實際以 `make` 執行：產生 64 字元隨機密鑰、不含佔位標記 |
| E2E 輔助函式 | `submitLogin` 改為「等到有結果」；第一次完整 E2E 失敗的 `E2E-BPAYR-08` 重跑通過（見 §3、§5）。**未另外為輔助函式寫測試**：它由全部 121 個 E2E 案例的每一次登入實際行使 |

## 5. 驗證結果

- **`mvn -o clean verify`**（checkstyle main＋test、PMD、JAR 打包）：**BUILD SUCCESS，15 分 21 秒**；單元 **1981**（+4）／整合 **686**（不變）／**0 失敗**；checkstyle 0 違規；PMD 通過。bcrypt 12 沒有讓全量驗證明顯變慢（Sprint 232 為 17 分 24 秒，雲端與本機負載不同，**不宜直接比較**）。
- **定點執行**：`JwtTokenServiceTest` 26、`JwtSecretShippedDefaultsTest` 1、`PasswordEncoderTest` 9 全過；各自經突變驗證（見 §4）。
- **`make validate-e2e`**：第一次 **115 通過／1 失敗／4 略過**（失敗＝`E2E-BPAYR-08`，原因見 §3）；修正輔助函式、重建前端後重跑 **121 個測試：117 通過／4 略過（與 Sprint 231、232 基準相同的 4 個）／0 失敗，5.9 分鐘**；schema 對齊。
- **前端**：`tsc --noEmit`、`eslint`（登入頁與輔助函式）、`next build` 皆通過。
- **push 與雲端 CI**：已 push（2026-10-02，`da002b9..7cf0e78 main -> main`；pre-push 輕量守門通過）。✅ 雲端 CI 全綠 run **37019154415**，三個 job 全部 success，共 10 分 36 秒：Backend Unit 2m43s／Frontend Lint & Build 0m58s／Backend Integration & Package 7m47s。**觀察（未歸因，記於 [SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §5）**：整合 job 的 7m47s 是近五次中最長（前四次依序 5m01s、6m02s、6m00s、4m15s）。bcrypt 12 讓每次註冊／登入變慢而整合測試大量註冊登入，是一個**假說**，但雲端 runner 耗時本來就有 ±1.5 分鐘的變異，單一資料點不下結論。

## 6. 範圍外（延後）與已知限制

- **DEF-323 (b)（token 儲存文件改成現況）**：排入文件對齊 Sprint（236～237），與 API_M03／SRD／PRD 的修訂一起做。
- **登入時自動把 cost 10 雜湊升級成 cost 12（rehash on login）未做**：既有使用者的雜湊維持 cost 10 直到他們改密碼。要做需要在 `AuthService.login` 驗證成功後 `passwordEncoder.upgradeEncoding` 並回寫，屬於新行為，等有需要再議。
- bcrypt 12 讓每次登入／註冊的雜湊慢約數倍（本機單次約 0.25～0.5 秒量級，**未做嚴謹量測**）：全量 `mvn verify` 與 `make validate-e2e` 的耗時變化記在 §5。

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. ~~**Sprint 234：DEF-319 訂房側**~~ **排程已更新（Sprint 234 計畫書 §1.1）**：本輪讀碼時發現的退貨申請與客服工單缺口與 Sprint 232 同型，故 Sprint 234 改做「系統租戶假設全面稽核與止血」（`DEF-325`），Sprint 235 處理 `DEF-327`～`DEF-329`，**DEF-319 順延為 Sprint 236（訂房）～237（訂單）**。內容不變：訂房側先做單一房源、沒有拆單問題的一半（`buildBookingCore` 改蓋房源所屬租戶＋Flyway 回填歷史訂房＋優惠券租戶解析的決定，若 PRD 沒寫清楚以 `AskUserQuestion` 請使用者拍板，不自己猜）；`at-seller-dashboard-real.spec.ts` 的 SDASH-03／04 移除 `test.fail()`。
2. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新。
