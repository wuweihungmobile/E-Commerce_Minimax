# Sprint 90 Plan — M07 結算逆轉發起/確認表單

**Sprint**: Sprint 90
**日期**: 2026-07-09
**主題**: Sprint 88 retro 排定「三者最佳化順序」第二項的後半——M07 跨週期退款調整單機制的結算逆轉發起/確認表單（後端已於 Sprint 86 完成雙重授權機制）。

---

## 1. 探查結果（現況）

- `SettlementController`（`/v2`）已有 `POST /admin/settlements/{id}/reverse/initiate?reason=`、`POST /admin/settlements/{id}/reverse/confirm`，皆 `@PreAuthorize("hasAuthority('settlement:reverse')")`（`SUPER_ADMIN` 全權限自動持有；`CFO` 角色僅有 `TENANT_READ/ORDER_READ/ADMIN_READ/SETTLEMENT_REVERSE` 四權限，Sprint 86 新增）。`initiateReversal`/`confirmReversal`（`SettlementReversalService`）皆用 `settlementRepository.findById(statementId)`，**完全不做租戶篩選**——與 Sprint 85 M16 審批端點同一慣例：授權完全交給 `@PreAuthorize`，一旦通過即視為平台級動作，非漏洞。
- **發現一個會讓表單無法運作的探查缺口**：`initiate`/`confirm` 端點本身雖已就位，但**沒有任何端點能讓 SUPER_ADMIN/CFO 跨租戶查詢「哪些結算單是 PAID／REVERSAL_PENDING 狀態」**。既有 `GET /admin/settlements/pending`（`SettlementReviewer.getPendingReviewStatements`）僅過濾 `PENDING_REVIEW` 狀態（審核流程用，非逆轉流程）；既有 `GET /v2/settlements/{id}`（`SettlementGenerator.getStatementById`）用 `findByIdAndTenantId(id, TenantContext.getCurrentTenant())` 綁死呼叫者自己的租戶，SUPER_ADMIN/CFO 呼叫查別租戶會直接查不到（回 404，非 403）。若不補上，「發起/確認表單」形同無法使用——沒有列表可看，SuperAdmin/CFO 無從得知要對哪個 `statementId` 操作。
- 前端目前對 M07 完全零資產：無 `services/settlement.ts`、無任何 `/dashboard/settlements` 或 `/admin/settlements` 頁面。
- `SettlementReversalServiceTest`（既有 5 個案例）已示範完整的 mock 慣例（`paidStatement()`/`reversalPendingStatement(role)` builder helper），可直接延用命名風格新增測試。

## 2. 架構決策

1. **後端新增一個唯讀列表端點，而非讓前端假裝有資料來源**：比照 Sprint 89 M16 探查邏輯（發現後端回應缺口即直接補齊，非新設計），新增 `GET /v2/admin/settlements/reversal-candidates`（`@PreAuthorize("hasAuthority('settlement:reverse')")`——與 initiate/confirm 用同一權限，確保能看列表的人一定也能操作，反之未必，符合最小權限）。回傳狀態為 `PAID`（可發起）與 `REVERSAL_PENDING`（可確認）的結算單，兩者混合在同一清單，前端依狀態決定顯示「發起」或「確認」按鈕。
2. **不比照 `getPendingReviewStatements` 的 `isSuperAdmin ? 跨租戶 : 限自己租戶` 分支**：該分支是為「一般 ADMIN 只能管自己租戶」的審核流程設計；但逆轉機制的 `@PreAuthorize` 本身已經只放行 `SUPER_ADMIN`/`CFO`，且 `initiate`/`confirm` 對這兩種角色一律不做租戶篩選（見上）。故新列表端點對兩種角色一視同仁、皆為跨租戶列表，僅保留可選的 `tenantId` 查詢參數供聚焦查詢單一租戶，不做「限自己租戶」回退分支。
3. **`SettlementStatementRepository` 新增 2 個查詢方法**：`findByStatusInOrderByGeneratedAtDesc(List<SettlementStatus>, Pageable)`、`findByTenantIdAndStatusInOrderByGeneratedAtDesc(UUID, List<SettlementStatus>, Pageable)`，比照既有單狀態版本命名慣例。
4. **前端不做「列表頁＋詳情頁」兩頁**：比照 Sprint 89 M16 SuperAdmin 頁面設計決策——新端點回應已含發起/確認所需全部欄位（金額、幣別、租戶、`reversalInitiatedByRole` 等），直接在列表卡片內建動作按鈕。
5. **確認按鈕的角色互斥檢查僅前端體驗優化**：卡片渲染時若 `currentUser.role === statement.reversalInitiatedByRole` 則停用「確認」按鈕並提示原因，实际安全邊界仍在後端 `E_1007`。
6. **不建立商家自助 `/dashboard/settlements` 列表/詳情/提交審核頁**：Sprint 88 retro 明確排定的候選項目文字是「M07 結算逆轉發起/確認表單」，範圍聚焦逆轉本身；商家自助結算頁面是 Sprint 80/81 遺留、未曾被排入任何 Sprint 的獨立缺口，不在本次隱性擴大範圍內處理，於下方「範圍外」明確記錄（大聲揭示，非默默略過）。

## 3. 後端變更

1. `SettlementStatementRepository.java`：新增 2 個查詢方法（如上）。
2. `SettlementReversalService.java`：新增 `getReversalCandidateStatements(int page, int size, UUID tenantIdOverride)`，狀態集合 `{PAID, REVERSAL_PENDING}`。
3. `SettlementController.java`：新增 `GET /v2/admin/settlements/reversal-candidates`（`@PreAuthorize("hasAuthority('settlement:reverse')")`，參數 `tenantId?`/`page`/`size`）。
4. `SettlementReversalServiceTest.java`：新增 `getReversalCandidateStatements` 測試（含/不含 `tenantIdOverride`、混合 PAID+REVERSAL_PENDING 結果）。

## 4. 前端實作清單（依 CLAUDE.md 開發-編譯-測試循環，一次一個檔案）

1. `lib/api.ts`：`admin` 區塊新增 `settlements: { reversalCandidates, reverseInitiate(id), reverseConfirm(id) }`。
2. 新建 `services/settlement.ts`：對應上述端點的 TypeScript service + DTO 型別（`SettlementStatementDto` 比照後端 `SettlementStatementResponse` 欄位）。
3. 新頁面 `app/admin/settlements/reversal/page.tsx`：列表 + 依狀態顯示「發起逆轉」（含原因輸入）或「確認逆轉」按鈕，比照 `app/admin/purchase-orders/page.tsx` 的卡片+切換原因輸入模式；比照 `/admin/*` 既有慣例不做 client-side 角色守衛，純依賴後端 403。

## 5. 測試計畫

- 後端：`SettlementReversalServiceTest` 新增案例（如上）。屬新增查詢方法（非修改既有 `initiate`/`confirm` 邏輯分支），依 [[sprint-full-regression-policy]] 跑 `mvn test -DexcludedGroups=slow` 即可，免全量整合回歸。
- 前端：無既有測試基礎設施覆蓋此類頁面，比照 Sprint 89 慣例僅以 `npm run lint` + `npm run build` 驗證。

## 6. 範圍外（延後，明確記錄非默默略過）

- 商家自助 `/dashboard/settlements`（結算單列表/詳情/提交審核 UI）：Sprint 80/81 遺留缺口，非本次「三者最佳化順序」排定範圍，待後續評估是否排入。
- M18 客服工單子系統 → Sprint 91+。
- DEF-043（ROOM 購物車清空）、DEF-044（退款回補庫存）→ 待排入。
