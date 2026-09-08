# Sprint 140 Plan — ERP（PurchaseOrderService + LogisticsService）併發競態技術債查證與修復

**Sprint**: Sprint 140
**日期**: 2026-09-08

---

## 1. 本輪範圍與方法論

延續 Sprint 137~139 做法（不另開 Workflow，主控 session 直接逐筆重讀原始碼、獨立判斷、動手修復）。Sprint 139 完成 CMS 主題後，剩餘 29 筆技術債。本輪聚焦「ERP」主題——`PurchaseOrderService`（`DEF-126`/`127`/`128`/`129`）與 `LogisticsService`（`DEF-123`/`158`/`159`）共 7 筆候選，理由：兩個 service 都是採購/物流履約鏈路上的狀態機，且都呈現同一種模式（無 `@Version`/`@DynamicUpdate` + check-then-act 狀態檢查），可歸為同一主題批次處理。

**查證結果**：逐一重讀原始碼、entity 定義、既有 Repository CAS 方法後，**7 筆全數確認為真**，但查證過程中發現 2 筆的原始判定需要更正：

| ID | 方法 | Sprint 136 原始判定 | 主控 session 查證結論 |
|----|------|---------------------|----------------------|
| DEF-126 | cancelPurchaseOrder | inventory_or_stock，與 receive 交錯 | 確認為真，與 127/128/129 同根因 |
| DEF-127 | receivePurchaseOrder | inventory_or_stock，收貨數量/庫存台帳完整性 | 確認為真，且**更嚴重**：`item.receivedQuantity` 本身是讀後寫，不只是「內控失效」 |
| DEF-128 | submitPurchaseOrder | other，**併發提交可能繞過金額審批門檻判斷** | ⚠️ **原始假設不成立**：`totalAmount` 於建立後不再變動，併發 submit 必算出相同的審批結果；真正的風險是與 cancel/receive 交錯的全欄位覆寫 |
| DEF-129 | updatePurchaseOrder | other，全欄位 lost update | 確認為真 |
| DEF-123 | createLogistics | other，同一訂單可建立兩筆有效物流單 | 確認為真，DB 無唯一約束兜底 |
| DEF-158 | cancelLogistics | other，影響侷限於顯示欄位 | ⚠️ **後果比原始標籤更嚴重**：與併發的 `updateLogisticsStatus(DELIVERED)` 交錯時，可繞過「已送達不可取消」的業務規則 |
| DEF-159 | updateLogisticsStatus | other，僅顯示層/追蹤資訊不一致 | ⚠️ **後果比原始標籤更嚴重**：訂單側 `order.setStatus+save()` 全欄位覆寫可能悄悄復原其他併發流程已提交的 Order 欄位，且原文已預告「但 Order.status=DELIVERED 這個真正驅動業務邏輯（結算...）」 |

**誠實揭露**：本輪查證推翻了 DEF-128 的原始風險描述（「繞過審批門檻」），但仍確認該方法有真實但性質不同的併發風險（全欄位覆寫），因此仍列入修復範圍——這與 Sprint 137 §1 推翻 DEF-145/146/147（死流程、修復無效益）不同，DEF-128 是「原因錯誤但問題仍在」，而非「問題不存在」。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

比照 Sprint 136~139 慣例：每完成一個邏輯修復單元，立即 `mvn compile` + 執行相關測試類別，確認通過才進入下一項；全部完成後才跑 `mvn -o verify` 全量回歸 + `checkstyle:check@checkstyle-main`/`@checkstyle-test`（🔴 本輪發現獨立呼叫 `mvn checkstyle:check` 只驗證 `checkstyle-main` execution，`checkstyle-test` 是另一個獨立 execution，必須明確指定兩者或跑完整 `mvn verify` 才能真正驗證 test 原始碼；見第 5 節）+ `make validate-schema`/`validate-schema-doc`。

---

## 3. 修復摘要：採購單狀態機（`PurchaseOrderService`，DEF-126/127/128/129）

**根因**：`PurchaseOrder` 無 `@Version` 也無 `@DynamicUpdate`；`updatePurchaseOrder`/`submitPurchaseOrder`/`receivePurchaseOrder`/`cancelPurchaseOrder` 四個方法共用「讀狀態→`canXxx()`檢查→改欄位→`save()`」模式，且 `receivePurchaseOrder` 額外涉及 `PurchaseOrderItem.receivedQuantity` 的讀後寫（同一交易記憶體物件，非原生 UPDATE）。四個方法任兩個交錯執行都可能：(a) 全欄位覆寫對方已提交的欄位；(b) 狀態機檢查基於過期快照，讓一個操作「看起來成功」但實際被另一個操作的後續提交覆蓋；(c) `receivePurchaseOrder` 若被併發呼叫，`item.receivedQuantity` 的增量可能遺失，即使 `productInventoryRepository.increaseTotalQty`（Sprint 113 DEF-051 已修復為原生 UPDATE）本身正確，也會造成「實際庫存已重複入帳、但收貨台帳只記一次」的帳實不符，且沒有任何 DB 約束能事後偵測這個落差。

**修法**：新增 `PurchaseOrderRepository.findByIdAndTenantIdForUpdate`（`SELECT ... FOR UPDATE` 悲觀鎖，比照既有 `UserRepository.findByIdForUpdate`/`KnowledgeArticleRepository.findByIdAndTenantIdForUpdate` 模式），四個寫入方法改用此方法載入採購單，將整個「讀狀態→驗證→改欄位→save()」序列化在同一把列鎖之下——一次悲觀鎖修法同時解決全欄位覆寫（不需要額外加 `@DynamicUpdate`）與 `receivedQuantity` 讀後寫兩類問題。與 `AdminService.approvePurchaseOrder`/`rejectPurchaseOrder`（Sprint 136 §5.1 既有的 `reviewIfStatus` 原生條件式 UPDATE）互不衝突：Postgres 的列鎖讓兩種寫入路徑對同一列自然序列化。

原本挑選悲觀鎖而非比照 Sprint 136/138/139 的 `@DynamicUpdate` 模式，理由是採購單寫入非高頻熱路徑（人工 ERP 操作），且 `receivePurchaseOrder` 的品項讀後寫問題單靠 `@DynamicUpdate` 無法解決，悲觀鎖可一次到位、避免混用兩種併發控制策略。

**測試**：新增 4 個守衛測試（`updatePurchaseOrder_usesLockedLookup`/`submitPurchaseOrder_usesLockedLookup`/`receivePurchaseOrder_usesLockedLookup`/`cancelPurchaseOrder_usesLockedLookup`），斷言四個方法都呼叫 `findByIdAndTenantIdForUpdate` 且不再呼叫不上鎖的 `findByIdAndTenantId`；既有 20 個 update/submit/receive/cancel 測試的 mock 同步改為 stub 新方法（`getPurchaseOrder` 系列 4 個測試維持不變，讀取路徑不上鎖）。

---

## 4. 修復摘要：物流建立（`LogisticsService.createLogistics`，DEF-123）

**問題**：「檢查訂單狀態為 CONFIRMED + 無現存有效物流單」與「呼叫物流商 API + 建立 Logistics」之間沒有原子保護，`logistics` 表對 `order_id` 無唯一約束兜底，兩個併發請求都可能通過檢查各自建立一筆 Logistics，違反「一張訂單至多一筆有效物流單」的業務不變量。

**修法**：比照 Sprint 136 §4.1 的 claim-before-external-call 原則——改用既有 `OrderRepository.updateStatusIfCurrent`（Sprint 136 §5.2 為 `OrderService.updateOrderStatus` 新增的 CAS 方法，本輪重用而非新建）先原子搶占 CONFIRMED→SHIPPING，只有搶到的一方才繼續呼叫物流商 API 並建立 Logistics；搶輸沿用既有的 E_5001。原本方法尾端「`order.setStatus(SHIPPING)` + `save()`」的全欄位覆寫式寫入隨之移除（狀態轉換已由 CAS 完成）。

**測試**：既有 `M11LogisticsOrderIntegrationTest`（`@MockBean` 版 MVC 整合測試）的 IT-SHIP-001 改為 stub 並驗證 `updateStatusIfCurrent(ORDER_ID, CONFIRMED, SHIPPING)`，取代原本對 `orderRepository.save()` 的 `ArgumentCaptor` 驗證。

---

## 5. 修復摘要：物流狀態機（`LogisticsService.cancelLogistics`/`updateLogisticsStatus`，DEF-158/159）

**問題**：`Logistics` 無 `@Version`/`@DynamicUpdate`；`cancelLogistics` 的「`status != DELIVERED`」檢查是 check-then-act，與併發的 `updateLogisticsStatus(DELIVERED)` 交錯時，可能繞過「已送達不可取消」的業務規則（cancelLogistics 讀到舊快照通過檢查，之後才被推進為 DELIVERED，cancelLogistics 的寫入仍會把已送達的物流改成 RETURNED）；`updateLogisticsStatus` 內部同步訂單狀態的 `order.setStatus(DELIVERED)+save()` 也是全欄位覆寫式寫入，可能悄悄復原其他併發流程（例如退款觸發的訂單狀態轉換）已提交的 Order 欄位。

**修法**：
- `Logistics` 加 `@DynamicUpdate`（比照既有模式），解決 `cancelLogistics`（僅碰 `status`）與 `updateLogisticsStatus`（碰 `status`/`pickupTime`/`deliveryTime`）之間的欄位覆寫。
- 新增 `LogisticsRepository.cancelIfNotStatus`（`WHERE status <> DELIVERED` 條件式原子 UPDATE），`cancelLogistics` 改用此方法取代 `setStatus+save()`，把「目前是否仍非 DELIVERED」的判斷下沉到 UPDATE 的 WHERE 子句，徹底杜絕上述繞過；搶輸沿用既有 `E_7502`。
- `updateLogisticsStatus` 內部訂單狀態同步改用既有 `OrderRepository.updateStatusIfCurrent`（`WHERE status = order.getStatus()`，以剛讀到的當下值為期望值）取代 blind `save()`；搶輸僅記錄警告（不拋例外——物流本身已成功記錄為 DELIVERED，訂單同步失敗屬次要，不應讓整個狀態更新請求失敗）。

**測試**：新增 `LogisticsServiceCancelTest.cancel_concurrentlyDelivered_claimLost_throwsE7502`；既有 `cancel_inTransit_setsReturned` 測試的 mock 改為 stub `cancelIfNotStatus`；`M11LogisticsOrderIntegrationTest` 的 IT-SHIP-004 改為 stub 並驗證 `updateStatusIfCurrent(ORDER_ID, SHIPPING, DELIVERED)`。

---

## 6. 驗證

- **編譯**：每個修復單元改動後立即 `mvn compile` 確認真實編譯。
- **checkstyle**：🔴 首次以獨立 `mvn checkstyle:check`（default-cli）驗證顯示 0 violations，但完整 `mvn verify` 的 `checkstyle-test` execution 抓到 `LogisticsServiceCancelTest.java` 的未用 import（`org.mockito.ArgumentMatchers.any`，因改用 `cancelIfNotStatus` 後不再需要 `save(any(...))` 的 stub）——確認 `mvn checkstyle:check`（default-cli）與 `mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test`（POM 綁定的兩個獨立 execution）驗證範圍不同，之後一律以後者或完整 `mvn verify` 為準。修復後 `mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test` **0 violations**。
- **單元測試**：`PurchaseOrderServiceTest` 由 33 增至 **37**（+4 悲觀鎖守衛測試）；`LogisticsServiceCancelTest` 由 3 增至 **4**（+1 併發搶占失敗測試）；`LogisticsServiceOwnershipTest`/`LogisticsServiceTenantAccessTest` 無需修改（其涵蓋的分支未觸及本輪變更的程式碼路徑）。
- **整合測試**：`M11LogisticsOrderIntegrationTest`（4 案例，`@MockBean` 版 MVC 整合測試）2 個案例（IT-SHIP-001/004）更新 mock 與驗證方式，全數通過；`M16ErpIntegrationTest`（41）/`M16ErpInventoryConcurrencyIntegrationTest`（5）/`M16ErpInventoryLedgerIntegrationTest`（6）/`M16ErpStockMovementDisplayIntegrationTest`（6）真實 DB 整合測試全數通過，證實 `PurchaseOrderService` 悲觀鎖修法未影響既有業務邏輯。
- **Schema 守門**：`make validate-schema`（entity↔migration）、`make validate-schema-doc`（migration↔文件）皆通過，本輪未新增任何 Flyway 遷移，`@DynamicUpdate` 註解與新增的悲觀鎖/CAS repository 方法皆不影響 schema。
- **全量回歸**：`mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1177**（相對 Sprint 139 的 1172，+5：PurchaseOrderServiceTest +4、LogisticsServiceCancelTest +1）、整合 **477**（與 Sprint 139 持平，既有整合測試類別內部案例數不變），0 failures/errors；checkstyle（main+test）**0 violations**；總耗時 7:28 min。

---

## 7. 刻意不做的事（避免範圍蔓延）

- 不修復其餘 22 筆技術債（客服工單/房源日曆/租戶功能開關/聊天室/媒體/訂單/付款狀態記錄等領域）——本輪聚焦 ERP 主題，其餘留待後續 Sprint 依主題分批查證（Sprint 141 起）。
- 不處理 `LogisticsService` 其餘唯讀方法（`getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`）——皆為 `@Transactional(readOnly = true)`，不涉及寫入競態，非本輪範圍。
- 不新增 `article_versions`-style 的資料庫唯一約束作為 `createLogistics`/`cancelLogistics` 的第二層防禦——CAS/claim 機制已完全消除本輪識別出的競態視窗，額外約束不會提供實質保護、只增加 schema 複雜度（Rule 2 簡潔優先）。
