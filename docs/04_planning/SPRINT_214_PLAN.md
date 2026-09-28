# Sprint 214 Plan — 帳號鎖定在併發猜測下失效（DEF-293）

**Sprint**: Sprint 214
**日期**: 2026-09-28

## 1. 起點

Sprint 213 收尾後（已 push、雲端 CI 全綠），`DEFERRED_ITEMS_TRACKER.md` 沒有 AI 可獨立處理的活躍待辦（`myTodoList.md` 三項仍需使用者操作實際環境；其餘皆標「不排入排程」）。使用者貼上 Sprint 213 總結並說「請繼續完成任務」，沒有指定新的項目。

這裡「繼續」的解讀是**推論**：延續 Sprint 210～213 的既有模式，自選一個掃描角度找新缺陷；人工項目（SMTP／維運／Stripe）依先前約定跳過，不替使用者做產品決策。

## 2. 掃描角度與查證

角度：**Sprint 213 的教訓是「Redis 上分成兩個獨立指令的『先檢查、再寫入』在併發下會失效，而且從沒人實測」，所以全庫盤點所有 Redis 使用點，找同型缺陷。**

`backend/src/main` 有 12 個檔案使用 Redis，逐一讀過（`RedisConfig`、`RedisStreamConfig` 只是設定，未列入結論）：

| 檔案 | 判定 | 依據 |
|---|---|---|
| `RateLimitFilter`／`LoginRateLimitFilter` | ✅ 無問題 | 單支 Lua 原子讀取-補充-扣除 |
| `IdempotencyService.checkAndMark` | ✅ 無問題 | `setIfAbsent`（單一原子指令） |
| `AccountTokenService` | ✅ 無問題 | 消耗用 `getAndDelete`、冷卻用 `setIfAbsent`（Sprint 204，已有真實 Redis 整合測試） |
| `RefreshTokenService` | ✅ 已於 Sprint 213 修復 | DEF-291 |
| `NotificationProducerService` | ✅ 無問題 | 單一 `leftPush` |
| **`LoginAttemptService`＋`AuthService.login`** | 🔴 **缺陷（DEF-293）** | 見 §2.1，已實測、已修復 |
| **`RedisCartService.addItem`** | 🟢 **缺陷（DEF-294）** | 見 §2.2，已實測、**本輪不修，僅登記** |
| `RedisLockService.releaseLock`／`extendLock` | 🟢 不是活缺陷 | 「GET 再 DEL／EXPIRE」非原子，但**全庫沒有任何生產呼叫者**（死碼）；見 DEF-295 |
| `NotificationConsumerService.processRetryQueue` | 🟢 僅推論 | 「GET 再 DELETE」非原子＋`KEYS` 掃描；見 DEF-295 |

### 2.1 缺陷（DEF-293）：帳號鎖定擋不住「同時發出」的密碼猜測

`AuthService.login` 的順序是：`isLocked`（讀）→ 查使用者 → **`passwordEncoder.matches`（bcrypt，數十到數百毫秒）** → 失敗後才 `recordFailedAttempt`（計入）。檢查與計入之間隔著一次 bcrypt，所以同時到達的 N 個請求會**全部在任何一個計入之前通過鎖定檢查**，各自都拿到一次猜測機會。DEF-220（Sprint 168）設定的門檻（5 次）只擋得住「依序」的猜測。

為什麼這重要：`LoginAttemptService` 的類別註解自己寫明它是用來防「跨多個 IP 輪流對同一帳號嘗試」——因為 `LoginRateLimitFilter` 的 30 次／分鐘是**每個 IP 各自的配額**。一個分散式攻擊者用 k 個 IP 同時對同一帳號各送一批，每個 IP 的配額都用不完，而帳號鎖定在這批請求全部完成之前都還沒有任何一次計入。可獲得的猜測次數約為「同時在飛的請求數」，而不是 5。

**紅燈實測**（未修改任何程式碼、真實 Redis、真實 bcrypt——生產 `SecurityConfig` 也是預設強度的 `new BCryptPasswordEncoder()`）：16 個執行緒同時以錯誤密碼登入同一個帳號，第 0 輪 **16 個全部拿到密碼比對機會**（全數回 `E-1001`，0 個回 `E-1004`）。對不存在的 email 同樣 16/16。

**同一個類別內的另一個縫隙**（一併修，因為新實作本來就得整段重寫）：`recordFailedAttempt` 是 `INCR` 之後**另一個指令** `EXPIRE`（且只在 `count == 1` 時），若兩者之間失敗（連線中斷、程序被終止），留下的 key 永遠沒有 TTL；達門檻後帳號連正確密碼都被擋，且永遠沒有機會歸零（歸零只發生在登入成功，而鎖定期間根本不會驗密碼）。這個縫隙**只用「預先建立無 TTL 的 key」重現後果**，並未實測「兩指令之間失敗」本身（發生機率很低，屬推論）。

### 2.2 補充：`RedisCartService.addItem` 併發加入同一商品會掉數量（DEF-294，僅登記）

`addItem` 是「讀出 JSON 物件 → 數量相加 → 整個寫回」的讀後寫。**實測**（用完即刪的暫時性實驗，未留在程式庫）：8 個執行緒同時各對同一使用者、同一商品加入 1 件，20 輪中 **20 輪最終數量都不是 8**（多半只剩 1 件）。

**為什麼本輪不修**：影響面是 UX（快速連點或多分頁時掉數量；結帳會重新驗價與庫存，不涉及金額與超賣）；修法不小——購物車小計是 `BigDecimal`，用 Lua 改寫 JSON 會有精度問題，較穩妥的是 `WATCH`／`MULTI` 樂觀重試，但 `IntegrationTestConfiguration` 把 `RedisTemplate` 整個換成 mock（`execute(SessionCallback)` 回 `null`），會牽動一批既有購物車測試的鷹架。登記為 🟢 不排入排程，是否值得修由你決定。

### 2.3 補充：兩個僅靠讀程式碼、**未量測**的觀察（DEF-295，僅登記）

- `RedisLockService.releaseLock`／`extendLock` 是「GET 比對再 DEL／EXPIRE」，非原子；但**全庫沒有生產呼叫者**（死碼）。生產實際用的是 `forceReleaseLock`（`RoomCalendarService.unlockDateRange`，完全不驗證持有者），若持有者處理超過鎖的 60 秒 TTL，解鎖會刪掉後來者的鎖。`bookDateRange` 另有 `FOR UPDATE NOWAIT` 的資料庫行鎖作為備援，故今天不是可利用的缺口，**推論、未重現**。
- `NotificationConsumerService.processRetryQueue`：`GET` 再 `DELETE` 非原子（多實例才會重複處理，`docker-compose.yml` 目前沒有 replicas）；使用 `KEYS` 掃描（與 DEF-292 同型）；且重試「延遲」（`RETRY_DELAY_SECONDS * retryCount` 當 TTL）實際上是 key 的存活期而非等待時間，`processRetryQueue` 每 5 秒不檢查到期時間就撿走所有 key，所以重試幾乎立即發生，未實作退避。**皆為讀程式碼的推論，未量測。**

## 3. 修復

- `LoginAttemptService.countAttemptAndCheckLocked(email)`（取代 `isLocked`＋`recordFailedAttempt`）：以單支 Lua 腳本在 Redis 內一步完成「`INCR` ＋（目前沒有 TTL 才）`EXPIRE` 15 分鐘」，回傳計入後的計數；`> 5` 即鎖定。
- `AuthService.login`：**先計入、再驗密碼**；驗證失敗後不需要（也不可以）再計入；登入成功才 `resetAttempts`。
- 介面刻意設計成「回傳 `true`＝鎖定」：Mockito 對 boolean 方法的預設值 `false` 就等於放行，避免重蹈 Sprint 213「boolean 方法 mock 預設 false，讓所有成功路徑測試默默走進失敗分支」的覆轍。
- TTL 條件用「目前沒有 TTL（`TTL < 0`）」而非「`count == 1`」：同樣涵蓋第一次計入，並讓舊版遺留的無 TTL key 在下一次計入時自行補上。
- 秒數以 ARGV 傳入：`LoginAttemptService` 用的是 `StringRedisTemplate`（純字串序列化器），沒有 Sprint 213 遇到的 JSON 引號問題（該問題只發生在生產的 `RedisTemplate<String,Object>`）。
- 保留失敗的 `warn` 日誌（原本由 `recordFailedAttempt` 印出），移到 `AuthService` 兩個失敗分支，不留下可觀測性的缺口。

## 4. 測試

- `AuthServiceLoginConcurrencyIntegrationTest`（新，真實 Redis＋真實 bcrypt，不啟動 Spring，4 案例）：
  - 16 個執行緒同時猜錯密碼，重複 10 輪：**用 spy 直接量 `matches` 實際執行 5 次**，其餘 11 個請求一律 `E-1004`。（初版只斷言錯誤碼；規劃突變時發現：若計入發生在 bcrypt 之後但仍是原子的，落敗者的錯誤碼一樣是「5 個 `E-1001` ＋ 11 個 `E-1004`」，只看錯誤碼分辨不出來，所以改量比對次數這個猜測機會的直接度量。）
  - 對不存在的 email 同時猜測同樣受門檻限制。
  - 登入成功會歸零（先失敗 4 次、成功 1 次後，還有完整 5 次機會，第 6 次才鎖定）。
  - 鎖定期間即使密碼正確也拒絕。
- `LoginAttemptServiceIntegrationTest`（新，真實 Redis，6 案例）：依序 5 次不鎖、第 6 次鎖；第一次計入設定 15 分鐘 TTL；後續計入**不延長**窗口（否則攻擊者可靠持續嘗試讓鎖定永不結束）；遺留無 TTL 的 key 會補上 TTL；`resetAttempts` 解除鎖定；16 執行緒同時計入恰好 5 個未鎖定（20 輪）。
- `LoginAttemptServiceTest`（改寫，單元，6 案例）：釘住門檻邊界（第 5 次不鎖、第 6 次鎖）、key 與 900 秒參數、Redis 回 `null` 視為未鎖定。
- `AuthServiceTest`（改 3 案例）：`login_wrongPassword_countsAttemptBeforeVerifyingPassword` 以 `InOrder` 釘住「先計入、再比對密碼」的順序，這是本輪缺陷的本質。
- **紅燈先行**：未修復程式碼上，兩個併發測試 16/16 拿到猜測機會而失敗；兩個依序的行為測試（成功歸零、鎖定期間拒絕正確密碼）在舊碼上本來就綠，作為修復前後都要守住的語意基準。
- **突變驗證**（6 種，皆被抓到，已還原並與備份逐位元組比對）：
  - M1 門檻 `>` 改 `>=`（差一錯誤）：`LoginAttemptServiceTest.countAttempt_atThreshold_notLocked` 紅。
  - M2 拿掉 `EXPIRE`：`firstAttemptSetsFifteenMinuteTtl`、`keyStrandedWithoutTtlGetsTtlOnNextAttempt` 紅。
  - M3 `TTL < 0` 改回 `count == 1`：僅 `keyStrandedWithoutTtlGetsTtlOnNextAttempt` 紅（隔離乾淨）。
  - M4 `AuthService` 忽略 `countAttemptAndCheckLocked` 的結果：單元 1 案例＋整合 4 案例全紅。
  - M5 成功登入不歸零：`login_validCredentials_resetsFailedAttempts`、`successfulLoginClearsFailureCount` 紅。
  - M6 計入挪到驗密碼之後（原子但排序回到舊缺陷）：單元 3 案例＋整合 3 案例紅（含 spy 計數那一個）。

## 5. 驗證結果

- 全量驗證 `mvn -o verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1793**（−1：`LoginAttemptServiceTest` 由 7 案例改寫為 6 案例；`AuthServiceTest` 改寫 3 案例、總數不變）／整合 **584**（+10：新增兩個真實 Redis 類別 4＋6）／0 failures／0 errors／0 skipped；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（約 9 分鐘）。
- **過程細節，如實揭露**：突變 M1 那次跑，`surefire` 單元測試失敗就擋住了後面的 `failsafe`，所以**該次沒有看到整合測試的反應**（M1 已被單元測試抓到，判定成立）；M2～M6 改加 `-Dmaven.test.failure.ignore=true`，讓單元與整合各自表態。
- 暫時性實驗（購物車併發加入）的測試檔已刪除，`git status` 只剩本輪預期的檔案；被突變的兩個主程式檔已與突變前備份逐位元組比對一致。
- 未變更 entity／migration，未跑 `make validate-schema`；未跑 E2E／`make validate-release`。E2E 規格對「登入鎖定」無依賴（grep `E-1004`／`locked`／`login_attempt` 的命中都與登入無關：訂房日期、CSP 違規）。

## 6. 決策與已知限制

- **同一帳號「同一瞬間」多個合法登入的取捨（我做的決定，不是你拍板的）**：先計入再驗密碼，意味著同一個帳號同時有 **6 個以上**正確密碼的登入請求在飛時，第 6 個起會被回 `E-1004`（訊息還是「Too many failed login attempts」，此時並不精確）。修復前這種情況全部成功。實務上這需要同一帳號在同一瞬間 6 個登入（多裝置同時登入、自動化腳本），依序的登入不受影響（每次成功都會歸零）。若日後有「偶爾登不進去」的回報，這是優先排查點；放寬方式是提高門檻或另設「進行中」計數，代價是猜測機會變多。
- **鎖定期間計數仍會遞增**：舊版鎖定期間不再計入，新版會繼續遞增（值可以任意大），但 TTL 不會被延長（有測試守住），窗口仍從第一次嘗試起算 15 分鐘。
- **未變更的既有行為**：攻擊者仍可用 5 次錯誤嘗試把已知 email 鎖住 15 分鐘（DEF-220 的既有取捨）；Redis 不可用時登入照舊會失敗（`LoginAttemptService` 一直是 fail-closed，與兩個限流 Filter 的 fail-open 不同）；每 IP 的 30 次／分鐘配額不變。
- **未涵蓋**：沒有對 `/v2/auth/login` 做 HTTP 層的併發測試（同 Sprint 213：`IntegrationTestConfiguration` 把 Redis 換成 mock，`MockMvc` 也不易製造微秒級競態）；本輪直接對 `AuthService.login` 施壓並連真實 Redis，覆蓋競態發生的那一層。未跑 E2E／`make validate-release`；未變更 entity／migration，未跑 `make validate-schema`。

## 7. 後續

- `myTodoList.md` 三項人工待辦狀態不變，仍在使用者手上。
- DEF-292（`blacklistAllRefreshTokens` 用 `KEYS`）、DEF-294、DEF-295 皆為「不排入排程」。
