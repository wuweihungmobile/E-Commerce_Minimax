# Sprint 317 Plan — DEF-439（不同店鋪的房源同一段日期互不衝突）：先量測、再補真實棧測試

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 316](SPRINT_316_PLAN.md) 的總結，加一句『請繼續完成任務！』，**沒有任何 `==>` 回覆，也沒有互動選項的答案**。我的解讀（推論，不是使用者說的）：只做總結自己列為『下次無 `==>` 的「請繼續」我可以直接做的』那一項——`DEF-439`；追蹤表對它標了『不需產品決策』。不碰：`DEF-419`（權限模型決策）、舊規則資料遷移、Sprint 306 的 `make down-clean` 流程、Docker 建置快取；Sprint 316 總結列的兩個『可選量測』（『另一間店鋪的店主』打 `API_M17` 九個端點、編輯頁進場角色守門）依 Sprint 316 計畫書的說法需要使用者決定要不要，所以不排入。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，不再詢問。Sprint 316 的純文件回填 commit `21efcfa3`（領先遠端 1 個）是本輪 commit 的祖先，會隨這次 push 一起帶出。

### 1.2 成功標準（開工時定義）

1. **先量、後寫**：追蹤表 `DEF-439` 的『風險形狀』明寫是推論（『現有測試抓不到』沒有實測）。先用『房源 id 被拿掉』的突變對現有測試實測，再決定要補什麼。
2. 新增的測試在未突變的程式碼上穩定通過：全新資料庫、疊在殘留資料上、連跑多次。
3. 每個新案例都有牙齒：對應機制的突變擊殺它、而且失敗落在預期的步驟；並證明新案例不是多餘的（現有測試對這些突變的覆蓋是白箱或偶然）。
4. 全量後端 `mvn verify` 通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數（974＋2＝976）。
5. 追蹤表如實更正（包含我上一輪寫錯的推論）；量測中發現的新缺口只登記、不順手補。

### 1.3 動手前後用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 追蹤表 `DEF-439`：『若有人把可用性檢查或 Redis 鎖的鍵改成只看日期、不看房源……現有測試抓不到』（原文自己標明『沒有實測這個突變，這是推論』） | **推論是錯的**。對現有 29 個測試類別（317 案例，基準全綠）做 5 組『房源 id 被拿掉』的突變，**5 組全被擊殺**。但擊殺的方式見 §3：鎖鍵由斷言字串格式的白箱測試擊殺、鎖的參數由 mock 單元測試擊殺、日曆查詢與唯一約束由 2～82 個『偶然撞到共用資料庫殘留』的案例擊殺——**沒有任何一個測試以『不同店鋪的房源互不衝突』為目的**。 |
| 追蹤表：『`otherStoreOwnerToken` 只用來測授權 403，沒有任何案例讓兩間不同店鋪的房源同時被訂』 | 屬實（讀碼）。`otherStoreOwnerToken()` 建的另一家店沒有房源。 |
| Sprint 316 計畫書 §7.9：『`DEF-439` 的風險形狀是推論』 | 本輪實測。推論的方向（沒有專屬測試）成立，細節（抓不抓得到）不成立。 |
| （新發現）突變 M3 的 46 個紅燈 | 其中 **44 個是假失敗**：`Unresolved compilation problem: BookingPricingFixtures cannot be resolved`，是 IDE 的 Java 語言服務在突變期間寫壞的 class（已知陷阱）；全部扣除，真實擊殺只有 2 個。 |
| （新發現）取消／封鎖／維護路徑 | §3.2：真實棧測試 0 個守住『取消一間房的訂房不會釋放別間房同日期的日曆』→ 新登記 `DEF-440`。 |

## 2. 做法

### 2.1 兩個案例，而不是追蹤表建議的一個

追蹤表建議的是『兩間不同店鋪各一間房源，同一位買家對同一段日期各訂一次，兩次都 201』。照做只守得住**日曆那一半**：順序執行的訂房在每次結束時就釋放鎖，看不到鎖鍵；而追蹤表自己列的突變目標包含『Redis 鎖的鍵』。所以拆成兩個案例，各對一個機制（皆在 `BookingApiRealStackIntegrationTest`，真實 PostgreSQL＋真實 Redis＋完整 HTTP／JWT／權限鏈）：

1. `create_sameDatesInAnotherStoresRoom_doNotConflict`（日曆那一半）：同一位買家同一段日期先訂 A 店的房、再查／訂 B 店的房。
   - A 訂成功 →
   - **對照**：A 店該房同日期的可用性是 `BOOKED`，另一位買家訂 A 店該房得 `400 E-4001` →
   - B 店該房同日期的可用性為 `true`，總價是它自己的每晚 800 元 × 2 晚 →
   - B 訂成功 `201`，回應帶 B 店自己的店鋪 id、房源 id、金額 1600 →
   - 兩間房的日曆各兩晚、都是 `BOOKED`、各自指向自己的訂房。
2. `create_sameDatesInAnotherStoresRoom_notBlockedByTheDateLock`（鎖那一半）：用服務自己的 `RoomCalendarService.lockDateRange` 占住 A 店房間的那兩晚（模擬『另一個請求正在處理』，測試因此不綁定鍵的字串格式），然後 A 店同日期的訂房得 `400 E-4001`（**對照：鎖真的有效**）、B 店房間同日期的訂房照樣 `201`；`finally` 釋放。

對照組放在同一個案例裡，是因為沒有它『B 訂得成』可能只是衝突檢查整個失效（Rule 9）。

### 2.2 為什麼量測顯示『已有覆蓋』還是補

(a) **鎖**：鎖鍵與鎖參數只有白箱測試（真實 Redis 的鍵字串、mock 的呼叫參數）。鍵的格式或呼叫方式重構時它們本來就該跟著改；沒有任何真實棧測試表達『別間房的訂房不被這間房的鎖擋住』這個行為。
(b) **日曆查詢與唯一約束**：覆蓋全來自『每個案例各建一間房、卻訂同樣的日期、共用同一個從不清資料的資料庫』的巧合。§3.3 證明單獨跑一個現有案例時 M2／M3／M7 全部存活；只要有一天讓案例各自清資料（本來就是好的衛生），這些覆蓋就無聲消失。

### 2.3 輔助方法放在測試類別內，不動共用的 ContractFixture

`roomInAnotherStore()`（另一家 `ACTIVE` 店鋪＋店主＋一間每晚 800 元、最多 2 人的房間＋`rooms` 列）與 `assertNightsBookedBy()`（那間房兩晚的日曆列都是 `BOOKED` 並指向給定訂房）放在 `BookingApiRealStackIntegrationTest` 內，風格比照同檔的 `storeStaffToken()`／`otherStoreOwnerToken()`，不動被 M05／M06 兩組契約測試共用的 `ContractFixture`（Rule 3）。B 店房間定價刻意與 A 店不同（800 對 1000），回應的總價因此能證明資料來自 B 店自己的房間。

### 2.4 兩個設計決定

- **同一位買家**（照追蹤表）：這是對房源層衝突檢查更嚴格的版本——若有人把鍵做成含使用者 id 卻不含房源 id，同一位買家會撞到自己。日後若新增『同一位買家不可同時持有重疊住宿』的產品規則，這個案例會紅，那時需要連同規則一起調整測試（那是正確的訊號，不是誤報）。
- **兩個案例的日期刻意錯開**（A 是 30 天後、B 是 45 天後）：正確程式碼下兩者本來就互不影響（房源 id 不同）；錯開是為了突變下的失敗歸因乾淨。第一輪兩案例共用日期時，M2／M7 讓 B 案例也被 A 案例留下的列波及（見 §6 第 3 點）。

### 2.5 文件

`API_M06_Booking.md` v2.6：§1.2 設計重點第 2 點補記『鎖鍵與日曆列的單位都是房源 id＋日期』並指向這兩個案例。追蹤表：`DEF-439` 完成並更正我上一輪寫錯的推論、新登記 `DEF-440`。

## 3. 修改前的量測（現有測試對『房源 id 被拿掉』的反應）

### 3.1 受測集合與方法

- **受測集合**：依**內容比對**（不是檔名）挑出與這兩個機制有關的測試——碰到 `RoomCalendarService`／`lockDateRange`／`bookDateRange`／`isDateRangeAvailable`／`RedisLockService`，或 `POST /v2/bookings`／`createBooking`／`buildBookingCore`／`CombinedCheckout` 的類別：11 個單元類別＋18 個整合／E2E 類別。**這不是整個測試套件**；沒有碰到這些程式碼的類別無法偵測這些突變，但我沒有逐一證明。
- **基準 Z0**：317 案例（單元 128＋整合／E2E 189）、0 失敗、0 錯誤，445 秒（含重建資料庫）。
- **每一組突變前都重建全新的 PostgreSQL／Redis**（`make test-db-up`），避免上一組的殘留與被突變的 schema（`ddl-auto: update` 會依實體建表）影響下一組；失敗集合取自 surefire／failsafe 的 XML；每組結束逐位元組還原並核對 SHA-256。
- 每組突變 203～531 秒。

### 3.2 結果

| # | 突變（來源檔） | 擊殺 | 擊殺者的性質 |
|---|---|---|---|
| M1 | `RoomCalendarService.lockDateRangeNoWait`／`unlockDateRange`：日期鎖鍵拿掉房源 id（`room:date:<日期>`，兩處一致） | **4** | `RoomCalendarLockRealRedisIntegrationTest` 2（斷言真實 Redis 裡的鍵字串）、`RoomCalendarServiceTest` 2（mock 比對鍵字串）——**白箱**，釘住『現在的寫法』 |
| M2 | `RoomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait`（可用性檢查與逐晚寫入共用的原生 `FOR UPDATE NOWAIT` 查詢）拿掉房源條件 | **79**（8 個類別） | **偶然**。訊息：57 個『房源日曆衝突: Date range no longer available』、13 個 `E-4001` 回應、5 個 `Status expected:<201> but was:<400>`、2 個『Query did not return a unique result: 2 results』——都是別的案例（`BookingCancellationRefundIntegrationTest` 18、`BookingPaymentIntegrationTest` 17、`BookingApiRealStackIntegrationTest` 14、`BookingTimeoutIntegrationTest` 10、`StoreSuspendedPaymentIntegrationTest` 8、`BookingPaymentApiIntegrationTest` 5、`CombinedCheckoutIntegrationTest` 4、`BookingNoShowIntegrationTest` 3）撞到別間房留下的列 |
| M3 | JPQL `findByListingIdAndCalendarDateBetween`（可用性端點與日曆端點）拿掉房源條件 | **2**（另有 44 個假失敗，已扣除） | `BookingApiRealStackIntegrationTest.availability`（`$.data.available` 預期 true 實際 false）、`.calendar`——**偶然**，同樣撞到別間房留下的列 |
| M7 | 實體 `RoomCalendar` 的唯一約束 `(room_listing_id, calendar_date)` 縮成只剩 `calendar_date`（全新 Hibernate schema） | **82**（9 個類別） | **偶然**。訊息：『Date … is already booked (concurrent booking)』（唯一約束違反被轉成 `E-4001`）等 |
| M8 | `BookingService.buildBookingCore` 取鎖與放鎖改傳店鋪 id、不傳房源 id | **13** | `BookingServiceCreateBookingTest` 7、`BookingPromoCodeTest` 6——**全是 mock 單元測試**（用房源 id stub／驗證）；真實棧 0 個 |

**結論 1**：追蹤表『現有測試抓不到』不成立。但 M1／M8 只有白箱測試擊殺（真實棧 0 個），M2／M3／M7 全靠偶然——**沒有任何測試以這個不變量為目的**。

同族路徑（取消、封鎖、解封、標記維護、解除維護）——它們共用 `RoomCalendarService.lockCalendarRange`：

| # | 突變 | 擊殺 | 擊殺者的性質 |
|---|---|---|---|
| M4 | `RoomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait`（五個寫入路徑共用的批次 `FOR UPDATE NOWAIT` 查詢）拿掉房源條件 | **1** | `PlatformSelfRunWriteIsolationE2ETest.storelessAdminCannotMarkMaintenanceOnSystemRoom`（第 428 行 `expected: 2 but was: 0`）：該案例**剛好**對兩間房標同一段日期，第二間房查到第一間房的列。固件巧合，不是專門守這件事 |
| R1 | `releaseDateRange`（取消訂房釋放日期）改成掃**所有房源**在該段日期的列 | **2** | 只有 `RoomCalendarServiceTest.releaseDateRange_*` 2 個 mock 單元測試，因為它們 stub 的是被我換掉的那個 repository 方法——突變寫法的副作用，不是行為守門。**真實棧 189 個案例 0 個轉紅** |

**結論 2**：『取消一間房的訂房，不會把別間房（別家店鋪）同日期的日期釋放』在真實棧沒有任何測試守住。風險形狀比 `DEF-439` 嚴重（別家的日期被釋放＝可重複預訂），但**沒有證據顯示現行行為有錯**，只是沒人守。依 Sprint 316 的慣例（量測揭露的新缺口只登記、不順手補）→ 新登記 `DEF-440`。

### 3.3 偶然覆蓋的直接證據

單獨跑**一個**現有案例（`create_returnsTheStoreBuyerAndRoomIds`，全新資料庫、只訂一次房、沒有其他案例留下的列）：Z0、M2、M3、M7 **全部存活**（各跑 1 個案例，0 失敗）。也就是說 M2／M3／M7 的 79／2／82 個擊殺，來自案例之間共用資料庫的累積殘留，不是任何單一案例的設計。

## 4. 測試與突變驗證

### 4.1 新案例在未突變程式碼上的基準

| 做法 | 結果 |
|---|---|
| 兩個新案例單獨跑（全新資料庫） | 2 案例、0 失敗（53 秒） |
| 整個 `BookingApiRealStackIntegrationTest`（18 既有＋2 新）連跑 3 次：第 1 次全新資料庫，第 2、3 次**不重建**、疊在前次殘留之上 | 每次 20 案例、0 失敗（55／48／48 秒） |
| 含新案例的完整受測集合（11 單元類別＋18 整合／E2E 類別，全新資料庫） | 319 案例（317＋2）、0 失敗、0 錯誤（205 秒）。這一次是在把鎖案例的日期錯開**之前**跑的；錯開只改一個日期，之後的單獨基準、3 次穩定性與全量 `clean verify` 都涵蓋它 |

### 4.2 突變驗證（全新資料庫、只跑兩個新案例）

驅動沿用 [Sprint 316](SPRINT_316_PLAN.md) §4.2 的做法：`old` 必須恰好出現一次、先乾跑、逐位元組還原並核對、失敗集合取自 failsafe 的 XML。本輪新增：每組突變前重建資料庫；用 `must`（預期紅的案例集合必須**恰好**吻合）與 `must_msg`／`forbid_msg`（失敗訊息必須／不可出現某字串）把紅燈歸因到單一步驟。

| # | 突變 | 預期擊殺 | 實際（失敗的那一步） |
|---|---|---|---|
| M1 | 日期鎖鍵拿掉房源 id（鎖與放鎖兩處一致） | 鎖案例 | 只有鎖案例紅。第 370 行：B 店房間的訂房預期 `201`、實際 `400 E-4001`（B 的鎖鍵與被占住的 A 相同） |
| M8 | `buildBookingCore` 取鎖／放鎖改傳店鋪 id | 鎖案例 | 只有鎖案例紅。第 366 行（**對照**）：A 店房間的鎖被占住，A 的訂房卻沒被擋下（預期 `400`、實際 `201`） |
| M2 | 日曆列鎖查詢（可用性檢查與逐晚寫入共用）拿掉房源條件 | 日曆案例 | 只有日曆案例紅。第 334 行：B 店房間的訂房預期 `201`、實際 `400 E-4001`（查到 A 店房間的 `BOOKED` 列） |
| M7 | 實體唯一約束縮成只剩 `calendar_date` | 日曆案例 | 只有日曆案例紅。第 334 行：B 店房間的訂房 `400 E-4001`（唯一約束違反被轉成衝突） |
| M3 | 可用性端點的 JPQL 拿掉房源條件 | 日曆案例 | 只有日曆案例紅。第 328 行：B 店房間的 `available` 預期 `true`、實際 `false`（回應裡混進了 A 店的列） |
| M10 | `getCalendarRange` 回空（讓『可用性』對照失效） | 日曆案例 | 只有日曆案例紅。第 319 行（**對照**）：A 店房間的 `available` 預期 `false`、實際 `true` |

6 組突變各自**恰好**擊殺預期的那一個案例（另一個仍綠），失敗訊息與行號吻合設計。兩個對照組都有牙齒：M10 殺日曆案例的可用性對照、M8 殺鎖案例的『鎖有效』對照——沒有對照組，『B 店訂得成』可能只是衝突檢查整個失效。

### 4.3 與修改前的對照

| 突變 | 修改前的現有測試 | 新案例（各自單獨跑） |
|---|---|---|
| M1 | 4 個，全是斷言鎖鍵字串的白箱測試 | 鎖案例：真實棧、以行為表達 |
| M8 | 13 個，全是 mock 單元測試 | 鎖案例（經由『鎖有效』對照）：真實棧 |
| M2 | 79 個，靠共用資料庫殘留 | 日曆案例：不依賴其他案例留下的資料 |
| M7 | 82 個，同上 | 日曆案例 |
| M3 | 2 個，同上 | 日曆案例 |

### 4.4 沒有被單獨驗證過牙齒的斷言（誠實揭露）

日曆案例最後的『兩間房的日曆各自指向自己的訂房』（`assertNightsBookedBy`），以及 B 店回應的 `tenantId`／`totalAmount`／`roomListingId`：我試過的六個突變都在更前面的步驟就先紅了，**沒有任何一個突變是由這些斷言獨自擊殺的**。它們編碼的意圖是『B 店的訂房不能串到 A 店的日曆列』，只對『訂得成、但日曆寫到錯的房』這種形狀有牙齒；我沒有構造出這種突變，所以不宣稱它們被驗證過。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**19 分 20 秒**；單元 **2433／0 失敗／0 錯誤／0 跳過**（與 Sprint 316 相同，本輪沒有改單元測試）；整合 **976／0 失敗／0 錯誤／0 跳過**（974＋新增 2＝976）；Checkstyle main／test 都是 0 違規；日誌 `<<< FAILURE`／`<<< ERROR`／`Unresolved compilation` 標記各 0 個。`BookingApiRealStackIntegrationTest` 20 案例（原 18＋2）。這是 javac 第一次真正編譯本輪新增的測試碼（之前是 IDE 的 ECJ 搶先編好，見 §6 第 8 點）。 |
| 修改前的量測（§3） | 基準 Z0 與 7 組突變（M1、M2、M3、M7、M8；同族 M4、R1），再加『單獨跑一個現有案例』的偶然覆蓋探測 4 次（Z0、M2、M3、M7）；每組前重建全新資料庫，每組結束逐位元組還原並核對。 |
| 新案例的突變驗證（§4.2） | 6 組各自**恰好**擊殺預期的那一個案例，失敗落在預期的步驟；第一輪因兩案例共用日期而歸因不乾淨，錯開日期後重跑（§6 第 3 點）。 |
| 穩定性（§4.1） | 整個類別連跑 3 次（第 1 次全新資料庫、後 2 次疊在殘留上）各 20 案例 0 失敗；含新案例的完整受測集合 319 案例 0 失敗。 |
| Checkstyle（測試原始碼，單獨） | `mvn -o checkstyle:check@checkstyle-test`：0 違規（`mvn test` 不會觸發它，所以另外跑；verify 內的 `checkstyle-test` 也是 0）。 |
| IDE Problems | 全部條目診斷 **0**，包含本輪改過的 `BookingApiRealStackIntegrationTest`，以及被突變後還原的 `BookingService`、`RoomCalendarService`。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`。 |
| 還原核對 | 所有突變都逐位元組／SHA-256 核對還原（驅動每輪回報 `restored=True`）；`git diff --stat -- backend/src/main` 為空；`git status` 只剩本輪預期的檔案。 |
| 沒有執行的項目 | Playwright 完整 E2E 與 `make validate-schema-doc`：本輪沒有任何生產程式碼、前端或 schema／遷移變更（只改測試與文件），見 §7 第 8 點。 |

## 6. 過程中的失誤與修正

1. **我上一輪寫的推論是錯的**：追蹤表 `DEF-439` 寫『現有測試抓不到』，同一段話也註明『沒有實測』。實測相反（§3）。這是『把推論寫進追蹤表』的又一例；這次靠『先量測、後動手』在寫任何測試前抓到，追蹤表已更正。
2. **穩定性實驗第一次全紅，是我的實驗設計錯誤，不是測試的問題**：『偶然覆蓋探測』規格的最後一輪是 M7（實體唯一約束被改成只剩日期），Hibernate 在那個全新資料庫裡建出了**被突變的 schema**；緊接著的穩定性規格設定『不重建資料庫』，於是用**未突變**的程式碼對著壞 schema 連跑 3 次，18～19 個案例全紅（順帶再次證明 M7 的威力）。這 3 次結果作廢。驅動新增每組突變可自訂是否重建資料庫（`reset`），重跑：第 1 次重建、第 2、3 次疊在殘留上，每次 20 案例 0 失敗。
3. **兩個新案例第一次用了同一段日期**：M2／M7 下鎖案例也被日曆案例留下的列波及（`OTHERS-RED`）。正確程式碼下兩者本來互不影響，但失敗歸因不乾淨；把鎖案例的日期錯開（30 天後→45 天後），重跑 6 組突變，才得到上面『各自恰好擊殺一個』的結果。
4. **突變 M3 有 44 個 IDE 假失敗**（`Unresolved compilation problem: BookingPricingFixtures cannot be resolved`，IDE 的 Java 語言服務寫壞的 class）：逐筆檢查失敗原因後全數扣除，真實擊殺只有 2 個。其他組沒有這類假失敗（逐組統計過）。
5. **開工前自審，刪掉兩個不可能失敗的斷言**：草稿裡有『被鎖擋下的訂房不可留下紀錄』（`createBooking` 是 `@Transactional`，就算先寫入也會回滾，而且 A 房本來就沒有日曆列）與『A 店回應的 `tenantId`』（`create_returnsTheStoreBuyerAndRoomIds` 已守住）。依 Rule 9 拿掉。
6. **工作目錄漂移**：兩次指令的 `cd` 讓 shell 的工作目錄殘留在子目錄（環境訊息提示），之後每個指令以專案根目錄的絕對路徑 `cd` 開頭。
7. **`shasum -c` 的 `FAILED` 一度看似警訊**：`hashes_orig.txt` 記的是我修改測試檔**之前**的雜湊，測試檔本來就該不同；改為只核對 4 個主程式檔（全 `OK`），並以 `git status`、`git diff --stat -- backend/src/main` 確認主程式無殘留變更。
8. **`mvn test-compile` 回報『Nothing to compile』不能證明編譯通過**（IDE 搶先編好）：改用 `javap -p` 確認新方法已在 class 裡、`grep -a -c "Unresolved compilation"` 確認沒有殘樁，真正的 javac 驗證交給之後的 `mvn clean verify`。

## 7. 限制（誠實揭露）

1. **受測集合不是整個套件**：29 個類別、317 案例，是依『內容比對碰到這些程式碼』挑的，不是整個後端測試（單元 2433＋整合 974）。沒有碰到這些程式碼的類別理論上偵測不到這些突變，但我沒有逐一證明。
2. **突變是人工挑的，沒有窮舉所有『房源 id 被拿掉』的位置**：例如 `bookDateRange` 建新列時的 `createCalendarEntry`、只改鎖或只改放鎖的單邊突變、`lockDateRangeWithWait` 都沒有量。
3. **Flyway 的唯一約束沒有量到**：M7 只改實體註解（整合測試資料庫由 Hibernate 建表、Flyway 關閉）。真實 Flyway 資料庫的 `UNIQUE(room_listing_id, calendar_date)`（`V1__Initial_Schema.sql`）只由完整 E2E 閘門間接涵蓋，沒有專屬案例。
4. **鎖案例用 `lockDateRange` 模擬『另一個請求正在處理』，不是真實並行**；真實並行（同房源）由既有的 `DEF-431` 案例以機率性方式涵蓋，不同房源的真實並行沒有測。
5. **『同一位買家』的選擇**（§2.4）：日後若有『同一位買家不可同時持有重疊住宿』的產品規則，日曆案例會紅，需要連同規則調整。
6. **日期是相對今天的**（30／45 天後）：落在開放窗之外的房源設定下不適用；本輪的房間沒有設定開放窗。
7. **§4.4 的那幾個斷言沒有被單獨驗證過牙齒。**
8. **本輪沒有執行 Playwright 完整 E2E 與 `make validate-schema-doc`**：沒有任何生產程式碼、前端或 schema／遷移變更（只改測試與文件）。依 Sprint 306 以來的慣例，仍跑了全量後端 `mvn clean verify`（見 §5）。
9. **`DEF-440` 只登記、沒有補測試**；M4／R1 是針對它的一次性量測，不是入庫的測試。
10. **同族的封鎖、解封、解除維護路徑沒有各自量**：M4 是它們共用的查詢（5 個寫入路徑一起），R1 只針對取消（`releaseDateRange`）。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『請繼續完成任務！』（無 `==>`）＝只做 `DEF-439`，不碰 `DEF-419` 等待使用者處理的項目，也不做 Sprint 316 總結列的兩個『可選量測』。
2. **先量測再補測試**，而且量測做得比追蹤表要求的更廣（含同族路徑 M4／R1）——目的是不把推論寫成事實；量測結果推翻了追蹤表的一句話。
3. **兩個案例而不是一個**（§2.1）；鎖案例用 `lockDateRange` 占鎖而不是並行。
4. **同一位買家**（沿用追蹤表）。
5. 輔助方法放在測試類別內，**不動共用的 `ContractFixture`**。
6. 兩個案例的日期刻意錯開。
7. 追蹤表如實更正我上一輪的推論：`DEF-439` 標完成（測試缺口已補），但把『抓不到』改寫成實測的『只有白箱或偶然覆蓋』，原文保留作證據。
8. 量測中發現的同族缺口**只登記** `DEF-440`、不補測試（延續 Sprint 316 對 `DEF-439` 的處理方式）。
9. `API_M06_Booking.md` 補記一句契約（v2.6）：鎖鍵與日曆列的單位是『房源 id＋日期』。其他文件引用的 `API_M06` v2.0／v2.1 是既有的版號漂移，照 Sprint 309／310 慣例不碰。
10. 不執行完整 E2E 與 schema 文件守門（見 §7 第 8 點）。

## 9. 新登記的缺陷

- **`DEF-440`**：取消、封鎖、解封、標記維護、解除維護一間房的日期，不會動到別間房（別家店鋪）同日期的日曆列——這個行為在真實棧沒有任何測試守住（測試缺口，不需產品決策）。證據見 §3.2 與追蹤表。

## 10. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 317 列。本輪的 commit `d09fa12a` 已 push（`60d9beeb..d09fa12a`，連同 Sprint 316 的純文件回填 commit `21efcfa3` 一起帶出），依使用者 2026-10-29 的長期授權（『做完一輪後，直接Commit + Push to main』）直接 push，沒有再詢問；pre-push 輕量守門通過（後端單元與前端 Lint／建置兩個 act job 皆成功、schema 漂移無漂移）。雲端 CI run [41002227143](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41002227143) 三個 job 皆 success（Backend Unit Tests 2 分 33 秒／Frontend Lint & Build 1 分 07 秒／Backend Integration Tests & Package 9 分 08 秒，整個 run 11 分 45 秒，2026-10-30 04:19:12Z～04:30:57Z）；以 `gh run view --json status,conclusion,headSha,jobs` 交叉確認 headSha 為 `d09fa12a`，並下載整合 job 的完整雲端日誌（約 56 MB）確認整合測試 **976／0 失敗／0 錯誤／0 跳過**、`BUILD SUCCESS`、`<<< FAILURE`／`<<< ERROR` 標記 0 個，`BookingApiRealStackIntegrationTest` 20（含新增 2）、`TenantStaffAccessIntegrationTest` 2、`RoomCalendarLockRealRedisIntegrationTest` 2、`BookingIntegrationTest` 2 與本機一致。回填 commit 只改文件，照 Sprint 306 以來的慣例留在本機，隨下一個有功能變更的 Sprint 一起帶出。

本輪的 commit 範圍：測試 `BookingApiRealStackIntegrationTest`（新增 2 個案例與 2 個輔助方法）；文件 `API_M06_Booking.md`（v2.6）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-439` 完成並更正推論、新登記 `DEF-440`）、`RELEASE_TRACKER.md` 與本計畫書。沒有生產程式碼、Flyway 遷移或前端變更。

**待使用者處理**：`DEF-419`、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-440`（沿用 `roomInAnotherStore()`／`assertNightsBookedBy()`，補『取消 B 店的訂房不會釋放 A 店同日期』與封鎖／解封／維護的對照，並以 M4／R1 做突變驗證）。另有兩個**可選的量測**（不是缺陷，需要使用者決定要不要）：『另一間店鋪的店主』對 `API_M17` §2.1 九個端點的結果、編輯頁是否值得加進場角色守門。
