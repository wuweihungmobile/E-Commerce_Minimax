# Release Notes - v2028.12.02-01 (Sprint 81)

**發布日期**: 2028-12-02（規劃）／實作完成 2026-07-08
**發布類型**: 🔒 安全修復（結算單審核租戶隔離）+ 💰 金流狀態同步（Transfer 撤銷 webhook）；後端聚焦，無前端變動
**Sprint**: Sprint 81（DEF-040 + US-005 修正版，承接 Sprint 80 retro 記錄的兩個 🔴 高優先候選，使用者指示「一起處理」）
**狀態**: ⏳ 待推送（commit 已完成，等待使用者確認後 push——本 Sprint 涉及生產授權邏輯變更 + 金流狀態同步，不比照一般 Sprint 自動 push）

> Sprint 81 主題：(1) 修復 `SettlementController` 結算單審核端點完全無租戶過濾的缺口（DEF-040，Sprint 80 開發 Transfer 分潤時發現）；(2) 修正 Sprint 80 retro 記錄的 US-005 錯誤假設——`transfer.paid`/`transfer.failed` 事件並不存在，改實作真正存在的 `transfer.reversed` webhook 同步。

---

## 🔒 安全修復：結算單審核租戶隔離（DEF-040）

- **問題**：`SettlementReviewer.getPendingReviewStatements`/`approveStatement`/`rejectStatement` 完全沒有租戶過濾，任一租戶的 `ADMIN`（具 `admin:read`/`admin:write` 權限）可查看/批准/駁回**其他租戶**的結算單；Sprint 80 新增 Transfer 分潤後，`approveStatement` 會觸發真實（test mode）資金轉移，風險由「看到別人財務數字」升級為「觸發別人的資金轉移」。
- **架構決策**：套用 `DEF-038`/Sprint 80 `TransferService.checkTenantAccess` 既有慣例——非 `SUPER_ADMIN` 僅能操作自己租戶結算單，`SUPER_ADMIN` 可跨租戶（可選填 `tenantId` 查詢指定租戶，或維持跨租戶總覽）。理由：與同一結算領域內剛完成的 `TransferController` 保持一致，避免出現兩套不同的租戶隔離規則。
- **修復**：`SettlementStatementRepository` 新增 `findByTenantIdAndStatusOrderByGeneratedAtDesc`；`SettlementReviewer` 三個方法新增 `isSuperAdmin`（+`tenantIdOverride`）參數與 `checkTenantAccess` helper，越權拋 `E_1007`；`SettlementController` 改用 `@AuthenticationPrincipal UserPrincipal` 判斷角色（比照 `TransferController`）。

## 💰 金流狀態同步修正：`transfer.reversed` Webhook（US-005 修正版）

- **規劃修正**：Sprint 80 retro 記錄「補 `transfer.paid`/`transfer.failed` webhook」，動手前查閱 Stripe 官方文件（`docs.stripe.com/api/events/types`）確認此二事件**不存在**（`Transfer` 物件僅有 `transfer.created`/`transfer.reversed`/`transfer.updated`；`transfer.paid`/`transfer.failed` 屬 `Payout` 物件，是不同資源）。
- **修正後實作**：僅處理 `transfer.reversed`（transfer 完成後被撤銷，代表資金真的被拿回，是唯一有決策價值的信號）。`Transfer.TransferStatus` 新增 `REVERSED`；`TransferService.handleTransferReversedWebhook`：查無對應記錄僅記 warn（可能非本系統觸發），查有記錄則轉 `REVERSED` + 結算單轉 `FAILED` 供人工重新處理（不自動重新分潤、不處理資金收回邏輯，沿用 Sprint 80「不處理 clawback」的既有範圍界線）；`PaymentWebhookService` 新增 dispatch case。

## 測試 / 驗證 ✅

- **`SettlementReviewerTest`**（新檔）：8 tests，涵蓋跨租戶批准/駁回拒絕、自己租戶放行、SUPER_ADMIN 跨租戶放行、查詢範圍化三案例，0 fail。
- **`M07SettlementIntegrationTest`**：既有 3 個 admin 測試因 `@AuthenticationPrincipal UserPrincipal` 改用手動建構 `Authentication`（比照 `TransferControllerE2ETest` 的 `authAs` 模式）修復，新增 2 個跨租戶案例，10 tests 0 fail。
- **`TransferServiceTest`**：+2（`transfer.reversed` 查有/無記錄），12 tests 0 fail。
- **`PaymentWebhookServiceTest`**：+2（dispatch + 事件去重重送 skip），10 tests 0 fail。
- **`TransferControllerE2ETest`**：5 tests 確認不受影響，0 fail。
- **後端全量整合回歸**（`mvn verify -Pintegration-test`，`make test-db-up` 後）：詳見 commit 訊息/PR 描述最終數字。
- **schema 漂移守門**：`make validate-schema` 無漂移（本次無 migration，僅 `TransferStatus` enum 新增值，DB 欄位型別為 STRING 不受影響）。

## 技術決策 / 已知限制 ⚠️

- DEF-040 修復選擇「ADMIN 限自己租戶」而非追蹤器原記錄的「平台級財務團隊跨租戶審核」選項，理由是與 Sprint 80 剛完成的 `TransferController` 保持一致；若未來平台實際運作需要跨租戶財務團隊，`SUPER_ADMIN` 角色已可滿足，不需放寬一般 `ADMIN` 權限範圍。
- `transfer.reversed` 目前僅同步狀態供人工重新處理，未實作自動資金收回（clawback）邏輯——與 Sprint 80 的既有範圍界線一致。

## 內含 Commit（Sprint 81）

| US / 項目 | 說明 |
|----------|------|
| Sprint 81 Plan | DEF-040 + US-005 修正版規劃（含 US-005 事件名稱查證修正記錄）|
| US-101 | DEF-040：`SettlementStatementRepository`/`SettlementReviewer`/`SettlementController` 租戶隔離 |
| US-102 | `transfer.reversed` webhook 同步（`Transfer` enum + `TransferRepository` + `TransferService` + `PaymentWebhookService`）|
| Sprint 81 收尾 | Retro / Release Notes + trackers（DEF-040 移至已完成）|

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
**基於**: AISDLC v0.09 Release Management Workflow
