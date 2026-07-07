# Sprint 81 Plan：DEF-040 結算單審核租戶隔離 + US-005 Transfer Webhook 同步（修正版）

**Sprint**: Sprint 81
**期間**: 2026-07-07
**主軸**: 承接 Sprint 80 retro 記錄的兩個 🔴 高優先候選項目，使用者已明確指示「一起處理」

---

## 🔴 規劃前修正：US-005 原定的 Stripe 事件名稱不存在

Sprint 80 retro 記錄的 US-005 原文為「補 `transfer.paid`/`transfer.failed` webhook 同步」。實際查閱 Stripe 官方文件（`docs.stripe.com/api/events/types`）確認：**`Transfer` 物件只有三種 webhook 事件：`transfer.created`、`transfer.reversed`、`transfer.updated`，並不存在 `transfer.paid`/`transfer.failed`**（那是 `Payout` 物件的事件，屬於「Stripe 餘額 → 銀行帳戶」的出款層，與本案「平台 → 賣家 Connect 帳戶」的 transfer 層是不同資源）。

修正後的真正需求：`transfer.reversed`（transfer 被撤銷，例如爭議或人工撤銷，代表錢被拿回去，是「資金真的異動」的權威訊號）。`transfer.created`/`transfer.updated` 對本案無決策價值（`transfer.created` 我方已在 `TransferService.createTransferForStatement` 同步取得回應處理；`transfer.updated` 僅 metadata/description 變更，不影響資金狀態），依簡潔優先原則不處理。

---

## US-101（DEF-040 修復）：結算單審核租戶隔離

### 問題

`SettlementReviewer.getPendingReviewStatements`/`approveStatement`/`rejectStatement` 完全沒有租戶過濾，任一租戶的 `ADMIN`（具 `admin:read`/`admin:write` 權限）可查看/批准/駁回**其他租戶**的結算單。Sprint 80 新增 Transfer 分潤後，`approveStatement` 現在會觸發真實（test mode）資金轉移，風險從「看到別人財務數字」升級為「觸發別人的資金轉移」。

### 架構決策：套用 DEF-038 / Sprint 80 TransferController 既有慣例

`DEFERRED_ITEMS_TRACKER.md` 記錄此案有兩種可能設計：(A) 租戶內 Admin 審核自己租戶；(B) 平台級財務團隊跨租戶審核。**本 Sprint 採用 (A) + SUPER_ADMIN 例外**，理由：

1. 這正是 `DEF-038` 修復後 `TenantContextFilter` 的既定規則（`ADMIN` 僅能操作自己租戶，`SUPER_ADMIN` 才能跨租戶指定）。
2. Sprint 80 剛完成的 `TransferController`/`TransferService.checkTenantAccess` 已是同一結算領域內的一致模式（`ADMIN` 限自己租戶查詢/重試，`SUPER_ADMIN` 可跨租戶）。
3. 若改採 (B)，`Transfer` 相關功能也需要同步改為要求 `SUPER_ADMIN`，等於推翻 Sprint 80 剛做的設計，不符合「配合程式碼庫既有慣例」原則。

若日後平台實際運作方式需要「跨租戶財務審核團隊」，屆時 `SUPER_ADMIN` 角色即可滿足，不需要放寬一般 `ADMIN` 的權限範圍。

### 變更點

- `SettlementStatementRepository`：新增 `findByTenantIdAndStatusOrderByGeneratedAtDesc(tenantId, status, pageable)`。
- `SettlementReviewer.getPendingReviewStatements(page, size, isSuperAdmin, tenantIdOverride)`：非 SUPER_ADMIN 強制使用 `TenantContext.getCurrentTenant()`；SUPER_ADMIN 可選填 `tenantIdOverride` 查指定租戶，未填則維持現況（跨租戶總覽，比照既有 Admin 平台總覽情境）。
- `SettlementReviewer.approveStatement`/`rejectStatement`：新增 `isSuperAdmin` 參數，非 SUPER_ADMIN 時比照 `TransferService.checkTenantAccess` 驗證 `statement.getTenantId()` 是否等於呼叫者租戶，不符則拋 `BusinessException(ErrorCode.E_1007)`。
- `SettlementController`：三個 admin 端點改用 `@AuthenticationPrincipal UserPrincipal principal` 判斷 `isSuperAdmin`（比照 `TransferController`），`getPendingReviewStatements` 增加可選 `tenantId` query param（僅 SUPER_ADMIN 生效）。

### 紅燈測試（新 `SettlementReviewerTest.java`）

- 非 SUPER_ADMIN 批准/駁回他租戶結算單 → 拒絕（`E_1007`）。
- 非 SUPER_ADMIN 批准/駁回自己租戶結算單 → 成功。
- SUPER_ADMIN 批准/駁回任意租戶結算單 → 成功。
- 非 SUPER_ADMIN 查詢待審清單 → 僅回自己租戶。
- SUPER_ADMIN 帶 `tenantIdOverride` → 僅回指定租戶；不帶 → 全租戶。

E2E 補充（`SettlementControllerE2ETest`，新檔）：跨租戶 403 情境 + SUPER_ADMIN 放行情境。

---

## US-102（US-005 修正版）：`transfer.reversed` Webhook 同步

### 設計

- `Transfer.TransferStatus` 新增 `REVERSED`。
- `TransferRepository` 新增 `findByStripeTransferId(String stripeTransferId)`。
- `TransferService.handleTransferReversedWebhook(String stripeTransferId)`：依 `stripeTransferId` 查 `Transfer`；查無記錄則記 warn 並返回（可能是本系統外觸發的 transfer，不可假設一定是我方資料）；查有記錄則設 `status=REVERSED` 並將對應 `SettlementStatement.status` 改回 `FAILED`（需人工重新處理，不自動重新分潤，也不做資金收回邏輯——沿用 Sprint 80「不處理 clawback」的既有範圍界線，本 Sprint 僅負責讓系統狀態忠實反映 Stripe 側已發生的事實）。
- `PaymentWebhookService`：新增建構子依賴 `TransferService`；`dispatch` 新增 `case "transfer.reversed"` 解析 `obj.path("id")` 為 `stripeTransferId`，呼叫上述方法。既有事件去重（`ProcessedStripeEvent`）機制沿用，不需額外處理。

### 測試

- `TransferServiceTest` 新增：查有記錄 → 狀態轉 `REVERSED` + 結算單轉 `FAILED`；查無記錄 → 不拋例外、log warn。
- `PaymentWebhookServiceTest` 新增：`transfer.reversed` → 呼叫 `handleTransferReversedWebhook`；重送 → skip。

---

## 執行順序（開發-編譯-測試循環，逐檔案）

1. `Transfer.java`（+REVERSED enum）→ compile
2. `TransferRepository.java`（+findByStripeTransferId）→ compile
3. `TransferService.java`（+handleTransferReversedWebhook）→ compile → `TransferServiceTest` 新增案例 → test
4. `PaymentWebhookService.java`（+依賴 +case）→ compile → `PaymentWebhookServiceTest` 新增案例 → test
5. `SettlementStatementRepository.java`（+findByTenantIdAndStatusOrderByGeneratedAtDesc）→ compile
6. `SettlementReviewer.java`（三方法加租戶檢查）→ compile → 新 `SettlementReviewerTest.java` → test
7. `SettlementController.java`（UserPrincipal 接線）→ compile
8. 新 `SettlementControllerE2ETest.java` → test
9. 全量回歸 `mvn verify -Pintegration-test`（`make test-db-up`/`down`）+ `make validate-schema`
10. 更新 `DEFERRED_ITEMS_TRACKER.md`（DEF-040 移至已完成）+ `SPRINT_81_RETRO.md` + release notes
11. commit（**此 commit 涉及生產授權邏輯變更 + 金流狀態同步，push 前依既有規則停下來徵詢使用者同意**）

---

**文件版本**: v1.0
**建立日期**: 2026-07-07
