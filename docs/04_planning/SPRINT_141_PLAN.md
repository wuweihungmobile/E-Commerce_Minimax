# Sprint 141 Plan — 退貨（ReturnRequestService）併發競態技術債查證與修復

**Sprint**: Sprint 141
**日期**: 2026-09-08

---

## 1. 本輪範圍與方法論

延續 Sprint 137~140 做法（不另開 Workflow，主控 session 直接逐筆重讀原始碼、獨立判斷、動手修復）。Sprint 140 完成 ERP 主題後，剩餘 22 筆技術債。本輪聚焦「退貨」主題——`ReturnRequestService` 中與 `ReturnRequest` 生命週期相關的 4 筆候選（`DEF-130`/`131`/`132`/`133`），理由：全部集中在同一個 service、同一個實體，且該 service 已有 Sprint 136 §5.3 針對 `receiveReturn` 的既有 CAS 修法（`updateStatusIfCurrent`）可參考延伸，根因高度重疊。

**查證結果**：逐一重讀原始碼、entity 定義、既有 `receiveReturn` CAS 手法後，**4 筆全數確認為真**：

| ID | 方法 | 根因 | 查證結論 |
|----|------|------|---------|
| DEF-130 | approveReturn | `ReturnRequest` 無 `@Version`，check-then-act | 真實：與 reject/cancel 交錯時，狀態/稽核欄位（`reviewedBy`/`reviewedAt`/`rejectionReason`）可能被悄悄覆寫 |
| DEF-133 | rejectReturn | 同上 | 真實，與 DEF-130 同根因 |
| DEF-131 | cancelReturnRequest | check-then-act，允許狀態集合（REQUESTED/APPROVED）判斷是過期快照 | 真實且較嚴重：可能繞過店家已核准/駁回的終態判斷，買家撤回的請求把已經 APPROVED/REJECTED 的退貨單改回 CANCELLED |
| DEF-132 | createReturnRequest | 可退量檢查是跨列 SUM 聚合，無 DB 約束兜底 | 真實：兩個併發申請都可能讀到彼此 INSERT 之前的舊總量，使同一訂單品項的退貨申請總量超過實際購買量 |

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

比照 Sprint 136~140 慣例：每完成一個邏輯修復單元，立即 `mvn compile` + 執行 `ReturnRequestServiceTest`，確認通過才進入下一項；全部完成後才跑 `mvn -o verify` 全量回歸 + `checkstyle:check@checkstyle-main`/`@checkstyle-test`（Sprint 140 教訓：獨立 execution 才能真正驗證 main+test 兩邊）+ `make validate-schema`/`validate-schema-doc`；另以既有真實 DB 整合測試 `M05ReturnRequestIntegrationTest`（13 案例，涵蓋 create/approve/reject/cancel/receive 全流程）驗證新增的 JPQL 語法在真實 Postgres 上正確可執行。

---

## 3. 修復摘要：核准/駁回（`approveReturn`/`rejectReturn`，DEF-130/133）

**問題**：`ReturnRequest` 無 `@Version`；`approveReturn`/`rejectReturn` 都是「讀 REQUESTED 檢查→setStatus/reviewedBy/reviewedAt(/rejectionReason)→save()」模式。兩者若近乎同時對同一筆退貨單觸發（或與 `cancelReturnRequest` 交錯），最終落在 DB 的狀態與稽核欄位由「最後 flush 的交易」決定，先寫入的一方仍會收到成功回應，但實際結果被悄悄覆蓋。

**修法**：新增 `ReturnRequestRepository.reviewIfStatus`（比照既有 `PurchaseOrderRepository.reviewIfStatus` 模式），`WHERE id=:id AND status=:expectedStatus` 條件式原子 UPDATE 同時寫入 `status`/`reviewedBy`/`reviewedAt`/`rejectionReason`；`approveReturn` 呼叫時 `rejectionReason` 傳 `null`。兩方法共用同一個 CAS，搶輸沿用既有 `E_5017`。

---

## 4. 修復摘要：買家撤回（`cancelReturnRequest`，DEF-131）

**問題**：「狀態屬於 {REQUESTED, APPROVED}」的檢查是 check-then-act，與併發的 `approveReturn`/`rejectReturn`/`receiveReturn` 交錯時，買家撤回的判斷可能基於過期快照——例如店家已核准（APPROVED→之後可能已收貨 RECEIVED）或已駁回（REJECTED），但買家的撤回請求讀到的是 commit 前的舊狀態，仍會把已經是終態的退貨單改回 CANCELLED，抹除店家已完成的審核/收貨結果。

**修法**：新增 `ReturnRequestRepository.cancelIfStatusIn`（`WHERE status IN (:expectedStatuses)` 條件式原子 UPDATE），取代 `setStatus(CANCELLED)+save()`；搶輸沿用既有 `E_5017`。

---

## 5. 修復摘要：買家提出申請（`createReturnRequest`，DEF-132）

**問題**：可退量檢查（`sumActiveRequestedQtyByOrderItem`，跨列 SUM 聚合）與建立新 `ReturnRequest` 之間沒有原子保護，`return_requests`/`return_request_items` 的 migration 沒有任何約束能事後偵測「同一訂單品項的退貨申請總量超過實際購買量」這個業務不變量。兩個併發的退貨申請都可能讀到彼此 INSERT 之前的舊總量，各自通過檢查成功寫入。

**修法**：新增 `OrderRepository.findByIdForUpdate`（`SELECT ... FOR UPDATE` 悲觀鎖，比照既有 `UserRepository.findByIdForUpdate`/`KnowledgeArticleRepository.findByIdAndTenantIdForUpdate`/`PurchaseOrderRepository.findByIdAndTenantIdForUpdate` 模式），`createReturnRequest` 改用此方法載入訂單，將整個「讀各品項已申請總量→比對可退量→建立新退貨單」序列化在同一把訂單列鎖之下。選擇悲觀鎖而非資料庫約束的理由與 Sprint 138 §3 相同：可退量檢查是跨列 SUM 聚合，無法用單一 CHECK/UNIQUE 約束表達，悲觀鎖可一次到位、不需搭配重試邏輯。

---

## 6. 驗證

- **編譯**：每個修復單元改動後立即 `mvn compile` 確認真實編譯。
- **checkstyle**：`mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test`（Sprint 140 教訓：兩個獨立 execution 都要驗）**0 violations**。
- **單元測試**：`ReturnRequestServiceTest` 由 2 增至 **7**（+5：`approveReturn_concurrentClaimLost_throwsE5017`、`rejectReturn_concurrentClaimLost_throwsE5017`、`cancelReturnRequest_success_cancelsSuccessfully`、`cancelReturnRequest_concurrentClaimLost_throwsE5017`、`createReturnRequest_usesLockedLookup`），既有 2 個測試（approve/reject 成功案例）同步更新 mock 以 stub 新的 CAS 方法。
- **整合測試**：`M05ReturnRequestIntegrationTest`（13 案例，真實 Postgres）全數通過，證實 `reviewIfStatus`/`cancelIfStatusIn`/`findByIdForUpdate` 的 JPQL 語法在真實 DB 上正確可執行，且既有 create/approve/reject/cancel/receive 全流程業務邏輯未受影響。
- **Schema 守門**：`make validate-schema`（entity↔migration）、`make validate-schema-doc`（migration↔文件）皆通過，本輪未新增任何 Flyway 遷移。
- **全量回歸**：`mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1182**（相對 Sprint 140 的 1177，+5）、整合 **477**（與 Sprint 140 持平，既有整合測試類別內部案例數不變），0 failures/errors；checkstyle（main+test）**0 violations**；總耗時 7:38 min。

---

## 7. 刻意不做的事（避免範圍蔓延）

- 不修復其餘 18 筆技術債（房源日曆/客服工單/租戶功能開關/聊天室/媒體/訂單/付款狀態記錄等領域）——本輪聚焦退貨主題，其餘留待後續 Sprint 依主題分批查證（Sprint 142 起）。
- 不處理 `receiveReturn`——Sprint 136 §5.3 已修復並有真實併發整合測試（`IT-M05-RETURN-RACE`），非本輪範圍。
- 不新增 `return_request_items` 層級的資料庫約束作為第二層防禦——悲觀鎖已完全消除本輪識別出的競態視窗，額外約束需要能表達「跨列 SUM 不超過購買量」的 CHECK 約束（PostgreSQL 不支援跨列 CHECK），不可行也無必要（Rule 2 簡潔優先）。
