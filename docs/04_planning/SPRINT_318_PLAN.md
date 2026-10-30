# Sprint 318 Plan — DEF-440（取消與維護只動這間房自己的日曆列）：先補量、再補真實棧測試

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 317](SPRINT_317_PLAN.md) 的總結，加一句『請繼續完成任務！』，**沒有任何 `==>` 回覆，也沒有互動選項的答案**。我的解讀（推論，不是使用者說的）：只做總結自己列為『下次無 `==>` 的「請繼續」我可以直接做的』那一項——`DEF-440`；追蹤表對它標了『不需產品決策』。不碰：`DEF-419`（權限模型決策）、舊規則資料遷移、Sprint 306 的 `make down-clean` 流程、Docker 建置快取；Sprint 316 總結列的兩個『可選量測』（『另一間店鋪的店主』打 `API_M17` 九個端點、編輯頁進場角色守門）需要使用者決定要不要，所以不排入。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，不再詢問。Sprint 317 的純文件回填 commit `4e6088d2`（領先遠端 1 個）是本輪 commit 的祖先，會隨這次 push 一起帶出。

### 1.2 成功標準（開工時定義）

1. **先查、先量、後寫**：追蹤表 `DEF-440` 的『五個寫入路徑』是 Sprint 317 用『共用批次查詢（M4）』與『取消（R1）』兩組突變外推出來的；標記維護、解除維護沒有分開量，封鎖／解封有沒有對外入口也沒查。先查入口、分開量，再決定要補什麼。
2. 新增的測試在未突變的程式碼上穩定通過：全新資料庫、疊在殘留資料上、連跑多次。
3. 每個新案例都有牙齒：對應機制的突變**恰好**擊殺它、失敗落在預期的步驟，**對照組**（『自己這間房有生效』）也各有一個突變證明它不是擺設。
4. 全量後端 `mvn verify` 通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數（976＋3＝979）。
5. 追蹤表如實更正（包含我上一輪登記的『五個寫入路徑』）；量測中發現的新缺陷只登記、不順手補。

### 1.3 動手前後用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 追蹤表 `DEF-440`：『取消、封鎖、解封、標記維護、解除維護』五個寫入路徑共用 `lockCalendarRange` | 共用屬實，但**封鎖、解封沒有對外入口**：`blockDateRange`／`unblockDateRange` 全庫零生產呼叫者（對 `src/main` 與 `docs` 的 grep；[API_M02_Listing_Center.md](../02_architecture/api/API_M02_Listing_Center.md) §1.2 第 6 點的比較表早就寫明『無任何 Controller 暴露，零生產呼叫點』；`DEF-380`、Sprint 280 使用者拍板不實作逐日批次封鎖）。真實棧能測的只有三條：取消（`BookingService` 三個呼叫點）、標記維護（`POST`）、解除維護（`DELETE`）。 |
| 追蹤表建議：『封鎖／解封／維護各補一個對照』 | 封鎖／解封不成立（沒有入口）；維護有兩個端點，各補一個。 |
| Sprint 317 的 M4 量測：『只有 1 個案例（`PlatformSelfRunWriteIsolationE2ETest.storelessAdminCannotMarkMaintenanceOnSystemRoom`）轉紅』 | 沿用，不重量；但 M4 同時影響三條可達路徑，無法說哪一條有人守 → 補 R2（標記）、R3（解除）兩組單一路徑突變（R1＝取消，Sprint 317 已量）。 |
| 既有的維護『整合測試』`M17MaintenanceWorkflowIntegrationTest` | 名為整合測試，實際把 `RoomCalendarRepository`／`BookingRepository`／`ListingRepository` 都換成 `@MockBean`，stub 的是 `findByRoomListingIdAndCalendarDateBetweenWithLockNowait(any(), any(), any())`——**看不到那條查詢有沒有房源條件**。 |
| （新發現）M7 突變下標記維護案例回 `500` | 我預測會是 `400 E-4001`。日誌顯示 `current transaction is aborted`（`RoomCalendarService.java:481`）→ 追到 `fillMissingCalendarEntries` 的恢復路徑在 PostgreSQL 上無效 → 另寫一次性探針做真實競態重現 → 新登記 `DEF-441`（§9）。 |

## 2. 做法

### 2.1 三個案例，而不是追蹤表建議的一個

取消、標記維護、解除維護是三段不同的程式（各自可以獨立壞掉），所以各一個案例，皆在 `BookingApiRealStackIntegrationTest`（真實 PostgreSQL＋真實 Redis＋完整 HTTP／JWT／權限鏈），沿用 `DEF-439` 的 `roomInAnotherStore()`：

1. `cancel_oneStoresBooking_doesNotReleaseAnotherStoresSameDates`：兩家店鋪的房間同一段日期都被同一位買家訂走；取消 B 店的訂房。
   - **對照**：B 店兩晚回到 `AVAILABLE`、不指向任何訂房 →
   - A 店兩晚仍是 `BOOKED` 且指向 A 店的訂房 →
   - 另一位買家訂 A 店同一段日期仍得 `400 E-4001`（不會被重複預訂）。
2. `maintenance_markedOnOneStoresRoom_doesNotTouchAnotherStoresBookedDates`：A 店的房間那兩晚已被訂走；B 店店主對自己房間同一段日期 `POST …/maintenance` → `200`。
   - A 店兩晚仍是 `BOOKED`、仍指向 A 店的訂房 →
   - **對照**：B 店兩晚成為 `MAINTENANCE`。
3. `maintenance_clearedOnOneStoresRoom_doesNotClearAnotherStoresMaintenance`：兩家店鋪的房間同一段日期都在維護中；B 店店主對自己房間同一段日期 `DELETE …/maintenance` → `200`。
   - A 店兩晚仍是 `MAINTENANCE` →
   - **對照**：B 店兩晚回到 `AVAILABLE`。

對照組放在同一個案例裡，是因為沒有它『別家沒被動到』也可能只是這個操作整個沒生效（Rule 9）。

### 2.2 前提怎麼建立

取消、標記維護兩個案例用**真實的訂房 API** 建前提。解除維護案例的『維護中』前提列**直接寫入資料庫**（`insertNights`，比照 `CombinedCheckoutIntegrationTest` 直接插入 `room_calendar` 列的先例），不經『標記維護』：這樣它只會因『解除維護』而紅，不會因為前提的建立方式連帶變紅（若前提也走標記維護，R2 會在前提階段就把它殺掉，歸因不乾淨）。

### 2.3 斷言順序：取消與另外兩個案例不同，原因如下

- **標記維護、解除維護**：先看『A 店那間房沒被動到』，再看『B 店有生效（對照）』。因為在 R2／R3／M4 下 B 店根本沒生效（標記維護時查到的是 A 店的列，B 店反而沒被標到），若先看對照，紅燈會落在 B 店的斷言，看不出 A 店被改寫。
- **取消**：先看 B 店釋放（對照），再看 A 店。因為在 R1／M4 下 B 店照樣被釋放（A 店也被一併釋放），對照仍成立，紅燈照樣落在 A 店那一步；對照組自己的牙齒由 C1 證明（取消不生效 → 紅在 B 店那一步）。兩種順序在各自的突變下的歸因都乾淨（§4.2 逐一讀過行號）。

### 2.4 其他設計

- **B 店店主的 token**：`roomInAnotherStore()` 回傳的房源帶著 `getOwner()`／`getTenant()`，新增 `ownerTokenOf(room)` 產生 `STORE_OWNER` 的 JWT（`STORE_OWNER` 持有 `room:update`，案例實測 `200`）。
- **日期**：取消 60 天後、標記維護 75 天後、解除維護 90 天後（與 `DEF-439` 的 30／45 天後互不重疊）。每個案例用的是自己新建的房間，正確程式碼下彼此本來就互不影響；錯開日期是為了突變下的失敗歸因乾淨（`DEF-439` 的 M2／M7 例外，見 §4.2）。
- **輔助方法**：新增 `assertNights(房源, 入住, 退房, 狀態, 訂房 id)`（兩晚各一列、狀態與指向的訂房都吻合；`bookingId` 為 `null` 代表不指向任何訂房）、`insertNights`、`ownerTokenOf`；把 `assertNightsBookedBy` 改成一行委派給 `assertNights(…, "BOOKED", …)`（行為不變，避免複製貼上同一段查詢）。都放在測試類別內，不動被 M05／M06 兩組契約測試共用的 `ContractFixture`（Rule 3）。

### 2.5 文件

`API_M06_Booking.md` v2.7：§1.2 設計重點第 2 點補記『釋放與維護用同一個單位』並指向三個案例；`API_M02_Listing_Center.md` v2.2：§4.7、§4.8 各補一句『只動這間房自己的日曆列』。追蹤表：`DEF-440` 完成並更正『五個寫入路徑』、新登記 `DEF-441`。

## 3. 修改前的量測（補量兩組單一路徑突變）

### 3.1 方法

- **受測集合**：與 Sprint 317 完全相同的 29 個類別（11 單元＋18 整合／E2E；含 `M17MaintenanceWorkflowIntegrationTest`、`PlatformSelfRunWriteIsolationE2ETest`、`BookingCancellationRefundIntegrationTest`、`BookingTimeoutIntegrationTest`…），現在有 **319 案例**（317＋Sprint 317 新增的 2 個）。**這不是整個測試套件**。
- **每組突變前重建全新的 PostgreSQL／Redis**；失敗集合取自 surefire／failsafe 的 XML；每組結束逐位元組還原並核對 SHA-256。
- **R2**＝`markMaintenance` 改成掃『所有房源』在該段日期的列（`roomCalendarRepository.findAll()` 在記憶體篩日期）；**R3**＝`unmarkMaintenance` 同樣改法。與 Sprint 317 的 R1（`releaseDateRange` 同樣改法）是同一個形狀，所以三條可達路徑可以並排比較。

### 3.2 結果

| # | 突變 | 擊殺 | 擊殺者的性質 |
|---|---|---|---|
| Z0 | （無） | 0 紅，319 案例全綠 | 基準 |
| R2 | `markMaintenance` 掃所有房源 | 12 | **真實棧 1 個**：`PlatformSelfRunWriteIsolationE2ETest.storelessAdminCannotMarkMaintenanceOnSystemRoom`（`expected: 2`）——該案例**剛好**對兩間房標同一段日期，不是專門守這件事（與 M4 的那一個是同一個案例）。其餘 11 個是 mock：`M17MaintenanceWorkflowIntegrationTest` 2（`Status expected:<200> but was:<500>`）、`RoomCalendarServiceTest` 9（`Unnecessary stubbings`、`找不到刊登項目`、`calendar is null`…）——突變把被 stub 的查詢換掉的副作用，不是行為守門 |
| R3 | `unmarkMaintenance` 掃所有房源 | 4 | **真實棧 0 個**。`M17MaintenanceWorkflowIntegrationTest` 1（`Wanted but not invoked`）、`RoomCalendarServiceTest` 3（同為 mock 副作用） |
| R1 | `releaseDateRange` 掃所有房源 | （沿用 Sprint 317）真實棧 0 個 | 只有 `RoomCalendarServiceTest.releaseDateRange_*` 2 個 mock 單元測試 |
| M4 | 共用批次查詢拿掉房源條件 | （沿用 Sprint 317）真實棧 1 個 | 同上，那個碰巧的維護 E2E |

**結論**：三條可達路徑的真實棧守門數是——取消 **0**、標記維護 **1（巧合）**、解除維護 **0**；封鎖／解封沒有入口。追蹤表『這個行為在真實棧沒有任何測試守住』成立（不像 Sprint 317 的 `DEF-439` 那樣被實測推翻），但範圍要從『五條』縮成『三條』。

### 3.3 為什麼不重量 R1 與 M4

Sprint 317 已用同一組測試、同一份生產程式碼量過；自那之後生產程式碼沒有變動，測試只多了 2 個與取消、維護無關的案例（`DEF-439`），沒有理由改變結果。

## 4. 測試與突變驗證

### 4.1 新案例在未突變程式碼上的基準

| 做法 | 結果 |
|---|---|
| 三個新案例各自單獨跑（全新資料庫） | 各 1 案例、0 失敗（33.6／32.9／30.8 秒） |
| 整個 `BookingApiRealStackIntegrationTest`（20 既有＋3 新）連跑 3 次：第 1 次全新資料庫，第 2、3 次**不重建**、疊在前次殘留之上 | 每次 23 案例、0 失敗（58／50／48 秒）；`names.txt` 確認三個新案例都有執行 |
| Checkstyle（測試原始碼，單獨）`mvn -o checkstyle:check@checkstyle-test` | 0 違規 |

### 4.2 突變驗證（全新資料庫、只跑三個新案例）

驅動沿用 [Sprint 317](SPRINT_317_PLAN.md) §4.2 的做法（`old` 必須恰好出現一次、先乾跑、逐位元組還原並核對、失敗集合取自 failsafe 的 XML、`must`＝預期紅的案例集合必須**恰好**吻合、`must_msg`／`forbid_msg`＝失敗訊息必須／不可出現某字串）。本輪的改進：重建資料庫前先 `docker rm -f -v`（連匿名卷一起移除）；**每組在跑之前就預先登記**『該紅的案例』與『失敗訊息的方向』（`expected: "BOOKED"`／`but was: "AVAILABLE"`…），跑完再比對，而不是看到結果再解釋。

| # | 突變 | 預先登記 | 實際（失敗的那一步） |
|---|---|---|---|
| Z0 | 無 | 3 案例全綠 | 3 案例、0 失敗 |
| R1 | 取消（`releaseDateRange`）掃所有房源 | 取消案例 | **恰好**取消案例。第 531 行（A 店的 `assertNightsBookedBy`）：`expected: "BOOKED"`、`but was: "AVAILABLE"` |
| R2 | 標記維護掃所有房源 | 標記案例 | **恰好**標記案例。第 555 行（A 店仍應是 `BOOKED`）：`but was: "MAINTENANCE"` |
| R3 | 解除維護掃所有房源 | 解除案例 | **恰好**解除案例。第 579 行（A 店仍應是 `MAINTENANCE`）：`but was: "AVAILABLE"` |
| M4 | 三條路徑共用的批次查詢拿掉房源條件 | 三個案例 | **恰好**三個，各紅在與 R1／R2／R3 相同的那一步（531／555／579） |
| C1 | 取消不生效（只釋放『`BOOKED` 且沒有訂房 id』的列＝永遠不成立） | 取消案例 | **恰好**取消案例。第 529 行（B 店對照）：`expected: "AVAILABLE"`、`but was: "BOOKED"` |
| C2 | 標記維護不生效（`setStatus` 改成設回原狀態） | 標記案例 | **恰好**標記案例。第 557 行（B 店對照）：`expected: "MAINTENANCE"`、`but was: "AVAILABLE"` |
| C3 | 解除維護不生效（守門條件加 `|| true`＝一律跳過） | 解除案例 | **恰好**解除案例。第 581 行（B 店對照）：`expected: "AVAILABLE"`、`but was: "MAINTENANCE"` |
| M2 | （`DEF-439`）單日日曆列鎖查詢拿掉房源條件 | 取消案例 | **恰好**取消案例。第 524 行（建立 B 店訂房）：`expected: 201`、`but was: 400`（`E-4001`）——前提不成立 |
| M7 | （`DEF-439`）實體唯一約束縮成只剩日期 | 只觀察 | 三個案例都紅：取消 第 524 行（同 M2）；標記 第 553 行（標記請求回 `500`，見 §1.3 與 `DEF-441`）；解除 第 574 行（`insertNights` 寫入 B 店前提列時唯一約束違反） |

八組有預先登記的（R1、R2、R3、M4、C1、C2、C3、M2）**全部**吻合：恰好該紅的案例、失敗落在預期的行、失敗訊息方向正確（驅動沒有任何 `MSG-MISMATCH`）。M7 沒有預先登記案例集合，只是為了觀察新案例與 `DEF-439` 不變量的關係——**取消案例的前提依賴 `DEF-439`**（需要兩家店鋪的房間同一段日期都訂得成），所以 M2／M7 會在前提階段殺掉它；標記與解除案例只在 M7（唯一約束）下才被波及，M2 下不受影響。

### 4.3 與修改前的對照

| 突變 | 修改前（真實棧） | 新案例（各自單獨跑） |
|---|---|---|
| R1 取消 | 0 個 | 取消案例 |
| R2 標記維護 | 1 個（巧合：兩間房碰巧同一段日期） | 標記案例（專門以此為目的） |
| R3 解除維護 | 0 個 | 解除案例 |
| M4 共用查詢 | 1 個（同上） | 三個案例 |

### 4.4 沒有被單獨驗證過牙齒的斷言（誠實揭露）

取消案例最後的『另一位買家訂 A 店同一段日期得 `400 E-4001`』：它是『A 店日曆仍 `BOOKED`』的必然結果，我試過的突變都在更前面的 `assertNightsBookedBy`（第 531 行）先紅，**沒有任何一個突變是由這一行獨自擊殺的**。保留它是因為它表達『不會被重複預訂』這個真正重要的意圖（日曆列的實作日後若改成別的機制，這一行仍然有意義）；我沒有構造出『日曆列仍是 `BOOKED`、但訂房被放行』這種形狀（那是 `DEF-439` 領域的另一類缺陷），所以不宣稱它被驗證過。

標記維護案例**沒有**另外斷言『A 店訂房的 `under_maintenance` 旗標沒被加上』：它與日曆列在同一個迴圈、同一個交易裡一起寫入，日曆列沒被改到就不可能有旗標，斷言不可能獨自失敗（開工前自審刪掉，比照 Sprint 317 的做法）。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**18 分 57 秒**；單元 **2433／0 失敗／0 錯誤／0 跳過**（與 Sprint 317 相同，本輪沒有改單元測試）；整合 **979／0 失敗／0 錯誤／0 跳過**（976＋新增 3＝979）；Checkstyle main／test 都是 0 違規；日誌 `<<< FAILURE`／`<<< ERROR`／`Unresolved compilation` 標記各 0 個。`BookingApiRealStackIntegrationTest` 23 案例（原 20＋3）。`clean` 先清掉所有 class，所以這是 javac 對本輪新增測試碼的真正編譯（不受 IDE 預編影響）。 |
| 修改前的量測（§3） | 基準 Z0＋R2＋R3 共 3 組，每組前重建全新資料庫、結束逐位元組還原並核對（驅動回報 `restored=True`）。 |
| 新案例的突變驗證（§4.2） | 基準 Z0＋9 組突變；8 組預先登記者全數吻合，M7 只觀察。 |
| 穩定性（§4.1） | 整個類別連跑 3 次（第 1 次全新資料庫、後 2 次疊在殘留上）各 23 案例 0 失敗。 |
| Checkstyle（測試原始碼，單獨） | 0 違規；verify 內的 main／test 見第一列。 |
| IDE Problems | 全部條目診斷 **0**，包含本輪改過的 `BookingApiRealStackIntegrationTest`，以及被突變後還原的 `RoomCalendarService`、`BookingService`、`RoomCalendarController`（清單裡另有已刪除的探針檔的殘留條目，診斷同樣為空）。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行：4 個類別共 **14 個案例，0 失敗**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`。 |
| 還原核對 | 所有突變都逐位元組／SHA-256 核對還原；`git diff --stat -- backend/src/main` 為空；`git status` 只剩本輪預期的檔案。一次性探針（§9）的原始碼與編譯產物已刪除。 |
| 沒有執行的項目 | Playwright 完整 E2E 與 `make validate-schema-doc`：本輪沒有任何生產程式碼、前端或 schema／遷移變更（只改測試與文件），見 §7 第 7 點。 |

## 6. 過程中的失誤與修正

1. **我上一輪登記的『五個寫入路徑』有兩條不存在**：`DEF-440` 把『封鎖、解封』列為路徑，建議『封鎖／解封各補一個對照』，但登記時沒有先查它們有沒有對外入口——`API_M02` 的文件早就寫了『零生產呼叫點』。這是『把推論寫進追蹤表』的又一例；本輪開工先查入口，追蹤表已更正（原文保留作證據）。
2. **我先前在對話中把數字講錯**：把這次的驗證說成『10 組（8 組針對 `DEF-440`、2 組看 `DEF-439`）』，實際是『基準 Z0＋9 組突變』（針對 `DEF-440` 的 R1、R2、R3、M4、C1、C2、C3 共 7 組；`DEF-439` 的 M2、M7 共 2 組），而且 M7 沒有預先登記案例集合。本計畫書與追蹤表已用正確的說法。
3. **M7 下標記維護案例的結果與我的預測不同**（預測 `400 E-4001`、實際 `500`）：沒有當成突變的副作用略過，追日誌發現 `current transaction is aborted`，再用一次性探針做真實競態重現，登記 `DEF-441`。
4. **一個 `grep` 寫得太寬**：`grep -E "ERROR|FAIL"` 命中 Hibernate 建表 DDL 裡的列舉值 `'FAILED'`，輸出一大片；改成只抓 `Tests run:`／`BUILD`。
5. **工作目錄漂移**：多次指令的 `cd` 讓 shell 的工作目錄殘留在子目錄（環境訊息提示），之後每個指令以專案根目錄的絕對路徑 `cd` 開頭。
6. **『同型寫法是不是只有這一處』這個核查，我是 push 之後才做的**：登記 `DEF-441` 時只寫了 `RoomCalendarService` 一處；寫記憶時才想到該對全部 `catch (DataIntegrityViolationException)` 做一次唯讀 grep，結果同型的還有 3 處。這件事本該在登記之前做。已 push 的 commit 裡的 `DEF-441` 因此只有單一處，範圍補充放在下一個純文件 commit（追蹤表與本計畫書 §9 以後者為準）。

## 7. 限制（誠實揭露）

1. **受測集合不是整個套件**：29 個類別、319 案例，是依『內容比對碰到這些程式碼』挑的，沿用 Sprint 317 的集合。
2. **突變是人工挑的，而且形狀單一**：R1／R2／R3 都是『掃全部房源』。**沒有量『呼叫端傳錯 id』**：取消路徑有三個呼叫點（`cancelBooking`、改期的 `processDateRangeChange`、逾時的 `cancelExpiredUnpaidBooking`），新案例只走 `cancelBooking`；另外兩個呼叫點的『釋放用錯房源』只由既有案例的『自己的日期有釋放』間接涵蓋，沒有量。
3. **封鎖／解封沒有測**：沒有入口，真實棧碰不到；它們只有 `RoomCalendarServiceTest` 的 mock 單元測試。
4. **解除維護案例的前提列是直接寫進資料庫的**，只涵蓋『`MAINTENANCE` 且不指向訂房』一種形狀；『維護前是 `BOOKED`、保留訂房 id』（解除後恢復 `BOOKED` 並清除旗標）沒有跨房間版本。現行案例對 R3／M4 已有牙齒，這個形狀不會增加對這兩個突變的覆蓋，所以不補。
5. **沒有量真實並行**：取消／維護與訂房的 `NOWAIT` 鎖互動（鎖衝突轉 `E-4001`）不在本輪範圍；`DEF-441` 的探針是唯一碰到並行的部分。
6. **§4.4 的那幾個斷言沒有被單獨驗證過牙齒。**
7. **本輪沒有執行 Playwright 完整 E2E 與 `make validate-schema-doc`**：沒有任何生產程式碼、前端或 schema／遷移變更（只改測試與文件）。依 Sprint 306 以來的慣例，仍跑了全量後端 `mvn clean verify`（見 §5）。
8. **`DEF-441` 只有探針、沒有入庫的案例，也沒有修**：探針重現的是『另一個交易先寫入同一列』的等價情境，不是兩個標記維護請求真的同時打；『標記維護 vs 訂房建立日曆列』的機制相同但沒有另行重現；同型的另外三處（§9）只讀了碼，沒有重現。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『請繼續完成任務！』（無 `==>`）＝只做 `DEF-440`，不碰 `DEF-419` 等待使用者處理的項目，也不做 Sprint 316 總結列的兩個『可選量測』。
2. **先補量 R2／R3 再補測試**（追蹤表的『五條』沒有分開量，而且其中兩條沒有入口）。
3. **三個案例**（取消／標記／解除），不是追蹤表建議的一個；**封鎖／解封不補**（沒有入口）。
4. 解除維護案例的前提列**直接寫進資料庫**，不經『標記維護』。
5. 把 `assertNightsBookedBy` 泛化成 `assertNights`（原方法改成一行委派，行為不變）；輔助方法放在測試類別內，**不動共用的 `ContractFixture`**。
6. 追蹤表如實更正我上一輪登記的『五個寫入路徑』，原文保留作證據。
7. 量測順帶發現的 `500` 追到 `DEF-441`，寫了一次性探針重現（**不入庫**），**只登記、不修**（修它要動併發路徑，需要設計）——延續 Sprint 316／317 的做法；push 之後又對全部 18 處 `catch (DataIntegrityViolationException)` 做了唯讀核查，把同型的另外 3 處一併寫進 `DEF-441`（只讀碼、未重現）。
8. 不執行完整 E2E 與 schema 文件守門（見 §7 第 7 點）。
9. `API_M06_Booking.md` 升 v2.7、`API_M02_Listing_Center.md` 升 v2.2 補記契約；其他文件引用的舊版號（既有的版號漂移）照 Sprint 309／310 慣例不碰。

## 9. 新登記的缺陷

- **`DEF-441`**（🟢 低風險，不需產品決策）：`RoomCalendarService.fillMissingCalendarEntries` 的『唯一約束衝突後改讀對方剛建立的列』恢復路徑在真實 PostgreSQL 上必然失敗——兩個交易同時為同一間房的同一個缺列日期補列時，後到的請求回 `500 E-9900`（交易整個回滾，沒有資料損毀）。**實測**：一次性探針（用完已刪）讓一個交易先寫入尚未提交的 (房源, 日期) 列，同時以 `STORE_OWNER` 打 `POST /v2/dashboard/rooms/{id}/maintenance`：請求被擋住，對方提交後回 `500 E-9900`，日誌是第 477 行唯一約束違反、第 481 行 `current transaction is aborted`，資料庫最後只剩對方那一列。對照 `bookDateRange` 同型的 `catch` 直接丟 `E-4001`，沒有這個問題。`RoomCalendarServiceTest.markMaintenance_concurrentCreationConflict_recoversExistingEntry` 用 mock 驗證『恢復』，看不到 PostgreSQL 的交易中止語意。**同型寫法另有三處**（`TransferService.claimTransferSlotOrExisting`、`ChatService.createConversation`、`SupportTicketService.createTicket`，都是 `@Transactional` 方法內捕捉唯一約束違反後繼續用同一個交易）：我 push 之後對 `src/main` 全部 18 處 `catch (DataIntegrityViolationException)` 逐處讀過 catch 區塊與交易註解，其餘 14 處直接丟例外、不受影響；這三處只是**讀碼推論**，沒有逐一重現。詳見追蹤表。

## 10. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 318 列。本輪的 commit `17bd31ce` 已 push（`d09fa12a..17bd31ce`，連同 Sprint 317 的純文件回填 commit `4e6088d2` 一起帶出），依使用者 2026-10-29 的長期授權（『做完一輪後，直接Commit + Push to main』）直接 push，沒有再詢問；pre-push 輕量守門通過（後端單元與前端 Lint／建置兩個 act job 皆成功、schema 漂移無漂移）。雲端 CI run [41107644272](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/41107644272) 三個 job 皆 success（Backend Unit Tests 2 分 26 秒／Frontend Lint & Build 54 秒／Backend Integration Tests & Package 6 分 59 秒），整個 run 10 分 10 秒（2026-10-30 18:31:20Z～18:41:30Z）；以 `gh run view --json status,conclusion,headSha,jobs` 交叉確認 headSha 為 `17bd31ce`，並下載整合 job 的完整雲端日誌（約 60 MB）確認整合測試 **979／0 失敗／0 錯誤／0 跳過**、`BUILD SUCCESS`、`<<< FAILURE`／`<<< ERROR` 標記 0 個，`BookingApiRealStackIntegrationTest` 23（含新增 3）、`M17MaintenanceWorkflowIntegrationTest` 5、`RoomCalendarLockRealRedisIntegrationTest` 2、`BookingIntegrationTest` 2、`TenantStaffAccessIntegrationTest` 2 與本機一致。回填 commit 只改文件（也補進 push 之後才做的 `DEF-441` 範圍核查，見 §6 第 6 點與 §9），照 Sprint 306 以來的慣例留在本機，隨下一個有功能變更的 Sprint 一起帶出。

本輪的 commit 範圍：測試 `BookingApiRealStackIntegrationTest`（新增 3 個案例與 3 個輔助方法、`assertNightsBookedBy` 改為委派）；文件 `API_M06_Booking.md`（v2.7）、`API_M02_Listing_Center.md`（v2.2）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-440` 完成並更正、新登記 `DEF-441`）、`RELEASE_TRACKER.md` 與本計畫書。沒有生產程式碼、Flyway 遷移或前端變更。

**待使用者處理**：`DEF-419`、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-441`（低風險；先把探針正式化為入庫的真實競態案例〔紅燈先行〕，再把恢復路徑改成不碰資料庫，並同步改掉 mock 單元測試；同型的另外三處〔`TransferService`、`ChatService`、`SupportTicketService`〕先逐一用探針確認是否真的失效）。另有兩個**可選的量測**（不是缺陷，需要使用者決定要不要）：『另一間店鋪的店主』對 `API_M17` §2.1 九個端點的結果、編輯頁是否值得加進場角色守門。
