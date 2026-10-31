# Sprint 320 Plan — DEF-442（批准／駁回結算單的讀後寫）：先用真實競態量測，確認屬實後比照 SettlementReversalService 修

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 319](SPRINT_319_PLAN.md) 的總結，加一句『請繼續完成任務！』，**沒有任何 `==>` 回覆，也沒有互動選項的答案**。我的解讀（推論，不是使用者說的）：只做總結自己列為『下次說「請繼續」我可以直接做的』那一項——`DEF-442`；不碰 `DEF-419`、舊規則資料遷移、`make down-clean` 流程、Docker 建置快取，也不做兩個『可選量測』（另一間店鋪的店主打九個端點、編輯頁進場守門）。

`DEF-442` 在追蹤表上標明『未驗證，讀碼推論』，所以第一步是**量測，不是修**；量測屬實才修。

長期授權（2026-10-29，使用者明文：『做完一輪後，直接 Commit + Push to main，請記住』）：本輪 commit 後直接 push，不再詢問。Sprint 319 的純文件回填 commit `e91f8de6`（領先遠端 1 個）是本輪 commit 的祖先，會隨這次 push 一起帶出。

### 1.2 成功標準（開工時定義）

1. **紅燈先行、而且是真實競態**：兩位管理員同時批准／駁回同一張結算單，在**未修改的程式碼**上用真實 PostgreSQL 量測；紅燈要紅在『一致性不變量』上，不是測試本身的問題（有一個不含併發的循序對照案例必須是綠的）。
2. **預測先寫下來**：動手前把『我預期會看到什麼』登記進本文件 §3，跑完逐一比對，不符的如實揭露。
3. **修法有牙齒**：突變驗證預先登記每組『恰好該紅的案例』與失敗訊息方向，結果逐一比對。
4. 全量後端 `mvn -o clean verify`（全新資料庫）通過、Checkstyle 0；push 後雲端 CI 全綠並核對整合測試數。
5. 追蹤表如實更新；過程中發現的相鄰問題只登記、不順手修。

### 1.3 動手前後用工具核對的事實

| 說法（出處） | 核對結果 |
|---|---|
| 追蹤表 `DEF-442`：`SettlementStatement` 沒有 `@Version`／`@DynamicUpdate` | 屬實（讀實體全文）。 |
| 『沒有使用 `SettlementStatementRepository` 裡已存在的條件式更新 `updateStatusIfCurrent`（`SettlementReversalService` 的逆轉流程用它）』 | 屬實。全庫 grep：生產呼叫者原本只有 `SettlementReversalService.initiateReversal`（`SettlementReversalService.java:69`）。 |
| 『批准 vs 駁回——兩者可能互相覆寫』 | **屬實，兩個順序都重現**（§3）：結算單 `REJECTED` 但 transfer 已建立且訂單被釋放（批准先、駁回後）；或結算單 `APPROVED`、transfer 已建立、訂單卻已被釋放（駁回先、批准後）。兩種都是金流層面的狀態損毀，且兩邊都回 `200`。 |
| 『批准 vs 批准——後到者可能把先到者已推進的 `PAID`／`FAILED` 覆寫回 `APPROVED`；`DEF-441` 修好後，後到者會在 `TransferService` 佔位時撞唯一索引、以 `E-5014` 失敗並整個回滾，這個組合因此被擋下』 | **部分屬實**。修復前、有 V64 同形唯一索引時：後到者的交易確實整個回滾、資料庫停在先到者的狀態（狀態是安全的），**但它的 HTTP 回應是 `500 E-9900`，不是 `E-5014`**——`TransferService` 丟的 `E-5014` 被 `approveStatement` 的 `try/catch` 吞掉，而 `createTransferForStatement` 是 `@Transactional`（加入呼叫端的交易），例外穿出它的交易代理時外層交易已被標成 rollback-only，提交時 `UnexpectedRollbackException`（日誌三行依序為 `Transfer already claimed by concurrent request` → `Transfer trigger failed after settlement approval` → `UnexpectedRollbackException … marked as rollback-only`）。『`PAID` 被改回 `APPROVED`』在修復前**沒有重現**（要贏家恰好在後到者的兩次讀取之間提交；讀碼推論兩次讀取之間沒有其他 SQL，所以窗口極窄，測試無法在中間卡住它）；用突變 M3（忽略搶占結果）才確定重現（§4.2）。 |
| Sprint 319 計畫書 §7 第 2 點：『輸家的 `E-5014` 被 `approveStatement` 的 try／catch 吞掉，但共用交易已 rollback-only，整個批准回滾——**未重現**』 | **屬實，這輪重現了**（上一列的日誌）。 |
| Sprint 319 計畫書 §7 第 3 點：『讓輸家成功會覆寫贏家狀態——讀碼推論』 | **屬實**，突變 M3 確定重現（§4.2）。 |

## 2. 做法

### 2.1 量測方法（真實 PostgreSQL，不靠 mock、不靠睡眠）

[SettlementReviewRaceRealStackIntegrationTest](../../backend/src/test/java/com/nextkey/ecommerce/integration/SettlementReviewRaceRealStackIntegrationTest.java)：一個『對手』交易（`TransactionTemplate`＋`JdbcTemplate`）先對結算單那一列取得 `FOR UPDATE` 鎖並停住；兩個請求（MockMvc，走完整 HTTP／JWT／權限鏈，兩位不同的 `SUPER_ADMIN`）各自先讀到 `PENDING_REVIEW`、通過狀態檢查，再卡在第一個寫入（結算單那一列的 `UPDATE`；修復前、有唯一索引的批准 vs 批准，後到者則是卡在帶著 `settlement_statement_id` 欄位、撞上對手 transfer 索引項目的 `INSERT INTO transfers`）——第一個請求被觀察到卡住之後才送出第二個，所以先後順序是確定的；用 `pg_stat_activity`（`wait_event_type = 'Lock'`）確認兩個請求都在等鎖後才放行。『兩個請求都在對方寫入前讀過』因此是確定的，不是碰運氣。

斷言的是**一致性不變量**（贏家與輸家各自的回應，加上資料庫最終狀態：結算單狀態與審核人、transfer 筆數、訂單是否仍被認領、批准／駁回稽核筆數、駁回原因），設計上不綁定修法的機制（悲觀鎖版本理應通過同一組斷言；實際只驗證過條件式更新這一種修法）。案例用 `SoftAssertions`，紅燈時一次列出所有違反的不變量。

案例：
- **循序對照（不含併發）**：批准後再駁回、再批准都得 `409`。它必須是綠的，否則下面的紅燈不能歸因於競態。
- **批准 vs 批准**、**批准 vs 駁回（批准先）**、**駁回 vs 批准（駁回先）**、**駁回 vs 駁回**。
- **贏家已付款後的批准**：對手交易模擬贏家（把結算單改成 `PAID` 並寫入一筆 `COMPLETED` 的 transfer，未提交），一個批准請求（先前讀到的是 `PENDING_REVIEW`）才寫入。

### 2.2 修法與沒選的方案

`approveStatement`／`rejectStatement` 在狀態前置檢查之後、**任何寫入之前**，先呼叫既有的 `SettlementStatementRepository.updateStatusIfCurrent(id, PENDING_REVIEW, APPROVED／REJECTED)`；回 0 就丟 `409 E-5014`（輸家不留稽核、不建立 transfer、不釋放訂單）。這是追蹤表自己建議的做法，也是 `SettlementReversalService.initiateReversal`（Sprint 137，`DEF-138`）已經在用的模式（Rule 11：配合程式碼庫既有慣例）。成功之後的流程不變（仍 `setStatus`＋`save`，與前例一致：整列寫回時 `status` 必須是搶占後的值，否則會把搶占結果寫回舊值）。

沒選的方案：
- **悲觀鎖（`SELECT … FOR UPDATE` 在 `findById` 就鎖列）**：能同時讓輸家讀到最新值，但這個程式碼庫的狀態轉換慣例是條件式更新，不是鎖；Hibernate 對 PostgreSQL 產生 `FOR UPDATE` 還是 `FOR NO KEY UPDATE` 我沒有驗證（Sprint 319 也留過同一個未驗證點）。前者與外鍵檢查的 `KEY SHARE` 衝突，而 Flyway 遷移裡有外鍵參照結算單的表至少有 `credit_notes`、`adjustment_statements`、`orders`、`bookings`（grep 確認；`transfers` 沒有），所以可能擋住它們的寫入——這一句是推論，沒有量測。
- **`@Version` 樂觀鎖**：59 個實體目前只有 1 個（`ProductInventory`）有（grep 確認），要加就是整個實體的 schema 變更，遠超這個缺陷需要的範圍。

這個修法**沒有**解決的事（量測發現，§9）：搶占只保證狀態轉換不被覆寫，不保證其他欄位——整列寫回仍會用讀取當下的舊金額蓋掉同時入帳的退款（`DEF-444`）；以及 `approveStatement` 的 try/catch 擋不住回滾（`DEF-443`）。

### 2.3 測試

- 新：`SettlementReviewRaceRealStackIntegrationTest`（6 案例，上述）。
- 改：`SettlementReviewerTest`——三個既有的成功案例補上 `updateStatusIfCurrent` 的 stub（Mockito 對沒設定的 `int` 回 0，不補會變成『搶占輸了』）；新增 5 個案例：批准／駁回搶占輸了（`E-5014`，且 `save`、稽核、`transferService`、訂單／訂房／調整單釋放全都沒被呼叫、記憶體狀態不變）、狀態早已不是 `PENDING_REVIEW`（前置檢查直接擋下，`never()` 不打資料庫搶占）、批准／駁回搶占發生在任何寫入與釋放之前（`InOrder`）。
- 整合測試庫是 Hibernate 建的，沒有 Flyway V64 的 `transfers.settlement_statement_id` 唯一索引；案例為每張結算單建立只涵蓋它的同形索引並在收尾移除（先例 `UniqueConstraintRaceRealStackIntegrationTest`）。為什麼必須這樣見 §3.2。
- `HeldTransaction`（持鎖的對手交易）從 Sprint 319 的測試複製了一份（含輔助方法 42 行），沒有抽共用類別——這是第二個使用者，等第三個再抽（Rule 3：不動沒壞的測試）。

### 2.4 文件

`API_M07_Settlement.md` v1.2：§1.2 第 1 點補『同時批准／駁回只有一個成功』、第 3 點補上兩句實測例外（『絕不回滾』與『`E-5014` 乾淨失敗』經 `approveStatement` 走到時並不成立）、§4.5／§4.6 補併發搶占輸了的 `409 E-5014` 與實測行為、§6 補 `E-5014` 的意義、§7 補 `DEF-443`／`DEF-444` 並把 Sprint 319 留下的『遺失更新，讀碼推論、未重現』改為實測結果、§8 補測試連結。

## 3. 修改前的紅燈（未修改的程式碼、真實 PostgreSQL）

### 3.1 第二次預測（跑之前登記，來源是讀碼）與實測

第一次實測（測試庫沒有 V64 唯一索引，詳見 §3.2）之後我修正了測試；下表的預測是**第二次跑之前**登記在本文件草稿裡的，實測結果**逐格符合預測**：

| 案例 | 預測（未修改，有唯一索引） | 實測 | 紅燈的違反項目 |
|---|---|---|---|
| 循序對照 | 綠 | 綠 | — |
| 批准 vs 批准 | 先到 `200`；後到的 `INSERT INTO transfers` 撞唯一索引 → `TransferService` 丟 `E-5014` → `approveStatement` 的 `catch` 吞掉，但交易已被標成 rollback-only → 提交時 `UnexpectedRollbackException` → **後到者 `500`**；資料庫停在先到者的狀態 | 先到 `200`；**後到 `500 E-9900`**；資料庫停在先到者的狀態（審核人、1 筆批准稽核、1 筆 transfer 都對）；日誌證實預測的機制（§1.3） | 2 項：後到者的狀態碼、錯誤碼（不是 `409 E-5014`） |
| 贏家已付款 vs 批准 | 後到者 `500`，結算單維持 `PAID` | 後到 `500 E-9900`；結算單維持 `PAID`、transfer `COMPLETED`、沒有多餘稽核 | 2 項：同上 |
| 批准先、駁回後 | 兩個都 `200`；最終 `REJECTED`＋transfer 還在＋訂單被釋放＋批准與駁回稽核各 1 筆（狀態損毀） | **兩個都 `200`**；最終 `REJECTED`，**transfer 仍在**，**訂單已被釋放**，批准與駁回稽核各 1 筆，駁回原因是後到者的 | 6 項（狀態損毀） |
| 駁回先、批准後 | 兩個都 `200`；最終 `APPROVED`＋建立了 transfer＋訂單已釋放＋兩種稽核各 1 筆（狀態損毀） | **兩個都 `200`**；最終 `APPROVED`，**建立了 transfer**，**訂單已被釋放**，批准與駁回稽核各 1 筆，審核人是後到者、駁回原因消失 | 7 項（狀態損毀） |
| 駁回 vs 駁回 | 兩個都 `200`；審核人與原因是後到者的；2 筆駁回稽核 | **兩個都 `200`**；審核人與原因（『原因乙』）是後到者的；2 筆駁回稽核 | 5 項 |

兩種損毀都是金流層面的。測試租戶的 transfer 一律是 `SKIPPED_ONBOARDING_INCOMPLETE`（沒有呼叫 Stripe，§7 第 2 點），所以『賣家真的被撥款』與『下一期又被結算一次』是**推論的後果**，不是這個測試量到的：量到的是『結算單 `REJECTED` 而 transfer 已建立、訂單已被釋放』與『結算單 `APPROVED`、transfer 已建立、訂單卻已被釋放』這兩種彼此矛盾的狀態；在 Connect 帳戶就緒的租戶上，transfer 會是真實撥款，而被釋放的訂單依 `DEF-273` 的設計會被下一期重新認領。

### 3.2 第一次紅燈的預測錯誤（誠實揭露）

第一次對未修改的程式碼跑（測試庫沒有唯一索引）：5 個競態案例全紅、循序對照綠。**我對『批准 vs 批准』的預測有一項錯**：預測只有 1 筆 transfer，實測是 **2 筆**。原因：整合測試庫是 Hibernate 建的，沒有正式環境 `transfers.settlement_statement_id` 的唯一索引，後到者的『是否已有 transfer』檢查發生在贏家提交之前，於是插入了第二筆。**這個紅燈是測試庫的人為現象，不能當成『正式環境會雙重 transfer』的證據**——正式環境有 V64 的唯一索引，第二筆會撞索引、整個回滾（即上表第二次的結果）。同樣地，第一次的『贏家已付款 vs 批准』看到結算單被改回 `APPROVED`，也是缺索引造成的（後到者自己插入了一筆 transfer 並繼續）。所以測試改成為每張結算單建立同形索引，再對**同一份未修改的程式碼**重跑。

## 4. 修改後與突變驗證

### 4.1 修改後

`SettlementReviewerTest` 14／0（9 個原有＋5 個新增）；`SettlementReviewRaceRealStackIntegrationTest` 6／0。全量 verify 之後，真實競態類別另連跑 3 次（每次重新啟動 Spring context），每次 6 案例 0 失敗。

### 4.2 突變驗證（預先登記：每組先寫下『恰好該紅的案例』與失敗訊息方向，跑完逐一比對）

驅動沿用 Sprint 319 的 `bmut.py`（從前一個 session 的暫存目錄複製）；受測集合＝`SettlementReviewerTest`＋`SettlementReviewRaceRealStackIntegrationTest`＋`SettlementBookingIntegrationTest`＋`SettlementExactlyOnceIntegrationTest`，共 33 案例。基準 Z0（全新資料庫）33 案例 0 失敗。判決標準：失敗集合與登記的 `must` **恰好**吻合才算 `KILLED(exactly must)`。

| 組 | 突變 | 登記的恰好該紅 | 結果 |
|---|---|---|---|
| M1 | 拿掉批准的搶占 | 單元 4（兩個成功案例因 stub 沒用到＋搶占輸了＋順序）＋整合 3（批准 vs 批准、已付款 vs 批准、駁回先批准後） | `KILLED(exactly must)` |
| M2 | 拿掉駁回的搶占 | 單元 3＋整合 2（批准先駁回後、駁回 vs 駁回） | `KILLED(exactly must)` |
| M3 | 批准的搶占照做，但**忽略它的結果** | 單元 1（搶占輸了）＋整合 3（同 M1） | `KILLED(exactly must)`，**訊息方向有一項預測落空**（見下） |
| M4 | 駁回的搶占照做、忽略結果 | 單元 1＋整合 2 | `KILLED(exactly must)` |
| M5 | 批准的搶占預期狀態寫成 `PENDING`（永遠搶不到） | 單元 4＋整合 5（含循序對照，其餘因為請求從不卡住而被『等不到卡住』逾時擊殺） | `KILLED(exactly must)` |
| M6 | 駁回的搶占預期狀態寫成 `PENDING` | 單元 3＋整合 3＋既有真實棧測試 2（駁回後釋放訂單／訂房） | `KILLED(exactly must)` |
| M7 | 批准的搶占移到稽核之後 | 單元 2（順序＋輸了仍有寫入）＋整合 3（循序對照、批准 vs 批准、批准先駁回後） | `KILLED(exactly must)` |
| M8 | 駁回的搶占移到釋放之後 | 單元 2＋整合 2＋既有真實棧測試 2 | `KILLED(exactly must)` |
| M9 | 拿掉批准的前置檢查 | 單元 1（`never()` 不打資料庫搶占） | `KILLED(exactly must)` |
| M10 | 拿掉駁回的前置檢查 | 單元 1（同一個案例） | `KILLED(exactly must)` |

**10 組的失敗集合都與登記的完全一致**。細節：

1. **M3 的一項訊息方向預測落空**：我登記『批准 vs 批准的後到者回 `500 E-9900`』，實際是 **`200`**——忽略搶占結果時，後到者會卡在搶占的 `UPDATE` 等贏家提交，之後『是否已有 transfer』的檢查才看得到贏家的那一筆並直接回傳，於是審核人被改成後到者、批准稽核 2 筆。失敗集合仍然吻合（那個案例本來就在該紅的集合裡），只有 `E-9900` 這個訊息方向沒出現（驅動標出 `MSG-MISMATCH missing=['E-9900']`）。
2. **M3 是『已付款被改回已批准』的確定性重現**：『贏家已付款 vs 批准』案例在 M3 下，後到的批准回 **`200`**，結算單從 `PAID` 被改回 **`APPROVED`**，審核人變成後到者，而 transfer 仍是 `COMPLETED`（錢已送出）。這證實追蹤表推測的覆寫確實會發生，也證實修法裡『搶占結果必須被遵守』這一行是在防它。修復前的程式碼沒有重現它（§3.1：後到者先撞到 transfer 唯一索引而回滾），因為要重現需要贏家恰好在後到者的兩次讀取之間提交，窗口極窄；M3 讓後到者在搶占處多等一下，把窗口撐開。
3. **M5／M6 的『永遠搶不到』會讓請求從不卡住**：`UPDATE … WHERE status = 'PENDING'` 對 `PENDING_REVIEW` 的列不符合條件，PostgreSQL 不會去等鎖，直接回 0 列，所以競態案例的『等兩個請求都卡住』逾時（逾時訊息 `等不到 N 個請求卡在結算單的鎖上`）而紅——是被擊殺，但擊殺的原因是『請求太快就回了』而不是狀態斷言。
4. **M9／M10 證明前置檢查只剩 `never()` 斷言在守**：拿掉前置檢查，外部行為不變（搶占本身也會擋下），整合測試完全看不到；唯一擊殺者是單元測試的 `verify(settlementRepository, never()).updateStatusIfCurrent(...)`（Sprint 245 同款教訓）。
5. **M7／M8（搶占放在寫入之後）連『贏家』也會失敗**：可觀察的事實是循序對照案例的第一次批准（M7）、以及先到的請求（預期 `200`）回了 `409`，整合案例因此也擊殺它，不只 `InOrder` 的單元案例。機制是我的推論（沒有另外看 SQL 順序）：搶占的 `@Modifying(flushAutomatically = true)` 會先 flush 已改好的整列 `UPDATE`（狀態已寫成 `APPROVED`／`REJECTED`），之後的條件式更新就搶不到，贏家自己回 `409`。

## 5. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**19 分 26 秒**；單元 **2443／0 失敗／0 錯誤／0 跳過**（Sprint 319 的 2438＋`SettlementReviewerTest` 新增 5）；整合 **989／0 失敗／0 錯誤／0 跳過**（983＋真實競態新增 6）；Checkstyle main／test 都是 0 違規；日誌 `<<< FAILURE`／`<<< ERROR`／`Unresolved compilation`／`ClassFormatError`／`NoClassDefFoundError` 各 0 個。新增／變動的類別在 verify 內確實執行：`SettlementReviewerTest` 14、`SettlementReviewRaceRealStackIntegrationTest` 6、`SettlementBookingIntegrationTest` 7、`SettlementExactlyOnceIntegrationTest` 6、`UniqueConstraintRaceRealStackIntegrationTest` 4。 |
| 紅燈先行（§3） | 5 個競態案例先在未修改的程式碼上全部紅燈（兩次：缺索引與有索引），循序對照綠；修正測試後第二次預測逐格相符。 |
| 突變驗證（§4.2） | 基準 Z0＋10 組，全部 `KILLED(exactly must)`，預先登記逐一吻合（M3 一項訊息方向預測落空）；被突變的 `SettlementReviewer.java` 與其餘 3 個檔案的 SHA-256 在驅動結束與探針移除後都與事先記錄的一致，無殘留字串。 |
| 穩定性（§4.1） | 真實競態類別在全量 verify 之後連跑 3 次，每次 6 案例 0 失敗。 |
| IDE Problems | 我改動的 4 個檔案診斷 **0**；`getDiagnostics` 列出的所有條目也都是 0 筆。 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 在**所有文件定稿後**執行，結果見 §10。 |
| 沒有執行的項目 | Playwright 完整 E2E 與 `make validate-schema-doc`：本輪沒有前端、Flyway 遷移或 schema 文件變更（只改後端程式碼、測試與文件），見 §7 第 7 點。 |

## 6. 過程中的失誤與修正

1. **第一次紅燈的預測有一項錯**（transfer 筆數），原因是測試庫缺 V64 唯一索引（§3.2）；修正測試、重新預測、重跑，第一次的結果不當成正式環境行為的證據。
2. **zsh 未加引號的萬用字元又中招兩次**：`grep --include=*.md` 報 `no matches found`（整條指令沒跑，不是『0 筆』）；`rm -f target/…/*SettlementReviewerTest*` 在沒有符合檔案時中止整條複合指令。之後一律加引號或改用 `find … -delete`。
3. **M3 的訊息方向預測落空**（§4.2 第 1 點）：我只分析了『已付款 vs 批准』會在搶占處多等一下，沒有把同樣的推理套到『批准 vs 批准』。
4. **工作目錄漂移**：幾次 `cd` 之後工作目錄留在子目錄，之後改用絕對路徑。
5. **一次性探針暫時插進測試類別再移除**：`DEF-444` 的量測用了兩個臨時案例；移除後用事先記錄的 SHA-256 逐檔核對與探針前完全一致，`grep` 確認沒有殘留。

## 7. 限制（誠實揭露）

1. **每個競態只測一個交錯**：先到的請求固定是『被觀察到先卡住』的那個；批准先／駁回先、批准×2、駁回×2 都有，但同一組合沒有互換先後再各測一次（對稱情境以程式碼對稱為由，沒有量測）。
2. **贏家已付款的情境用對手交易模擬**（原生 SQL 把結算單改成 `PAID` 並寫入 `COMPLETED` 的 transfer），不是一次真正走過 Stripe 的完整核准——測試租戶的 `STRIPE_TRANSFER_ENABLED` 沒有開，真實核准的 transfer 一律是 `SKIPPED_ONBOARDING_INCOMPLETE`，沒有任何案例呼叫 Stripe。
3. **整合測試庫沒有 Flyway 的索引**：`transfers` 的唯一索引由案例自己建、自己移除，沒有對 Flyway 建出來的資料庫跑。
4. **修復前『已付款被改回已批准』沒有直接重現**（§3.1、§4.2 第 2 點），只在突變 M3 下確定重現。
5. **`submitForReview`（`PENDING → PENDING_REVIEW`）沒有改**：它同樣是讀後寫，但兩個提交寫入的是同一個值，唯一的覆寫風險是『第一個提交的請求被卡住數秒，其間管理員已批准』——這是我的推論（可達性很低），沒有量測。`TransferService.retryFailedTransfer`／`handleTransferReversedWebhook` 也是對結算單狀態的讀後寫，**未量測**。
6. **兩位管理員都是 `SUPER_ADMIN`**；非 `SUPER_ADMIN` 的 `ADMIN` 路徑除了 `checkTenantAccess` 外是同一段程式碼，沒有另外跑。
7. **沒有執行 Playwright 完整 E2E 與 `make validate-schema-doc`**：沒有前端、Flyway 遷移或 schema 文件變更。仍跑了全量後端 `mvn clean verify`（§5）。
8. **`DEF-443` 只實測了一個觸發點**；`DEF-444` 的探針是一次性的、在**已修好 `DEF-442`** 的程式碼上跑（修復前的程式碼依構造有同樣行為，但沒有另外跑）。
9. **前端沒有批准／駁回結算單的畫面**：`grep` `frontend/src` 找不到任何對 `…/approve`、`…/reject` 結算單端點的呼叫，`lib/api.ts` 的 `settlements` 只定義沖正的三個端點。所以這個競態實際上只能由直接打 API 的方式觸發（腳本、管理工具、重送），可達性比有按鈕的情境低；這不改變缺陷屬實，也沒有改動前端（輸家現在得到 `409 E-5014`，前端若將來加畫面，需要處理這個錯誤）。

## 8. 我替使用者做的決定（可一句話推翻）

1. 『請繼續完成任務！』（無 `==>`）＝只做 `DEF-442`，先量測、屬實才修；不碰 `DEF-419` 等待使用者處理的項目，也不做兩個『可選量測』。
2. **修法用既有的條件式更新 `updateStatusIfCurrent`**（追蹤表建議、前例 `SettlementReversalService`），不用悲觀鎖或 `@Version`（理由 §2.2）。搶輸者回 `409 E-5014`。
3. **`submitForReview` 不動**（§7 第 5 點的推論理由）。
4. **`approveStatement` 裡『任何失敗僅記錄，絕不回滾』的註解不改**——量測證明它對穿出 `TransferService` 交易代理的例外不成立，但改註解不等於修正行為；登記為 `DEF-443` 而不是順手改（Rule 3）。
5. **`DEF-444`（整列寫回蓋掉退款）只登記、不修**：它與 `DEF-442` 機制不同（過期的非狀態欄位，而不是狀態被覆寫），修法需要先決定方向（搶占後 `refresh` 讀最新金額？`@DynamicUpdate`？悲觀鎖？），而且會改動金額計算路徑，值得單獨一輪的紅燈先行與突變驗證。探針是一次性的、不入庫。
6. 整合測試庫缺的索引由案例自己建、自己移除（只涵蓋本案例結算單）。
7. `HeldTransaction` 複製一份而不抽共用類別（§2.3）。
8. `API_M07` 升為 v1.2，並更正 Sprint 319 留下的說法（§7 的『遺失更新，讀碼推論、未重現』改為實測結果；§1.2 第 3 點補上實測例外）；其他文件引用的舊版號（既有的版號漂移）照 Sprint 309／310 慣例不碰。

## 9. 新登記的缺陷

- **`DEF-443`**（🟢）：`approveStatement` 的 `try { createTransferForStatement } catch (RuntimeException) { log }` 擋不住回滾——`createTransferForStatement` 是 `@Transactional`（加入呼叫端的交易），任何 `RuntimeException` 穿出它的交易代理時，外層交易被標成 rollback-only，吞掉例外後提交會 `UnexpectedRollbackException`，整個核准（含稽核）被回滾、HTTP `500 E-9900`，與方法註解和 API_M07 §1.2 第 3 點相反。**實測一個觸發點**（Sprint 320 修復前的批准 vs 批准後到者，日誌三行為證）；該觸發點已被 `DEF-442` 的搶占消除，其他觸發點（Stripe SDK 丟非 `BusinessException`、租戶不存在等）依 Spring 語意應相同，**未逐一實測**。
- **`DEF-444`**（🟢，**實測**）：批准／駁回的整列寫回會蓋掉同時入帳的退款扣款。`SettlementStatement` 沒有 `@Version`／`@DynamicUpdate`；退款對 `PENDING`／`PENDING_REVIEW` 的結算單以原子 `UPDATE` 扣款（`applyRefundDeduction`），若在批准請求『讀取結算單』與『整列寫回』之間提交，就被舊值蓋掉。重現方式：對手交易執行 `UPDATE settlement_statements SET total_refunds = total_refunds + 100, net_settlement_amount = net_settlement_amount - 100`（持鎖、不提交），批准請求先讀到 `total_refunds = 0／net = 900`、卡在搶占，放行後——批准：`total_refunds` 仍 `0.00`、淨額仍 `900.00`、**transfer 金額 `900.00`**（賣家多拿到被退款的 100）；駁回：同樣蓋掉（結算單已作廢，訂單下一期重新結算，金額影響較小）。窗口極窄，但沒有任何保護。詳見追蹤表。

## 10. 狀態

**文件守門測試**（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`）在文件定稿後執行：4 個類別共 **14 個案例，0 失敗**（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1），`BUILD SUCCESS`。

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 320 列。本輪依使用者 2026-10-29 的長期授權（『做完一輪後，直接 Commit + Push to main』）commit 後直接 push，連同 Sprint 319 的純文件回填 commit `e91f8de6` 一起帶出；push 結果與雲端 CI 結果由回填 commit 補在 RELEASE_TRACKER 的 Sprint 320 列。

本輪的 commit 範圍：生產程式碼 `SettlementReviewer`（批准／駁回的條件式搶占）、`SettlementStatementRepository`（只補 Javadoc）；測試 `SettlementReviewRaceRealStackIntegrationTest`（新）、`SettlementReviewerTest`；文件 `API_M07_Settlement.md`（v1.2）、`DEFERRED_ITEMS_TRACKER.md`（`DEF-442` 已完成、新增 `DEF-443`／`DEF-444`）、`RELEASE_TRACKER.md`、本文件。

**待使用者處理**：`DEF-419`、舊規則資料遷移（若要）、Sprint 306 的 `make down-clean` → `make up` → `make dev-admin-password`、Docker 建置快取。

**下次無 `==>` 的『請繼續』我可以直接做的**：`DEF-444`（已實測屬實；先決定修法方向，再比照本輪的紅燈先行與突變驗證），然後 `DEF-443`（修法有兩個方向：把撥款觸發移到核准交易提交之後，或至少讓撥款失敗真的不影響核准；動手前先實測其他觸發點）。另有兩個**可選的量測**（不是缺陷，需要你決定值不值得）：另一間店鋪的店主打九個端點、編輯頁進場守門（見 [SPRINT_318_PLAN.md](SPRINT_318_PLAN.md) §10）。
