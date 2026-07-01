# Sprint 32 Review / Sprint 32 評審會議

> **Sprint 編號**: Sprint 32
> **期間**: 2027-01-03 ~ 2027-01-16
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_32_PLAN.md](../04_planning/SPRINT_32_PLAN.md), [SPRINT_31_RETRO.md](./SPRINT_31_RETRO.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 安全修復 + 買家閉環驗證

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| DEF-018 getOrder IDOR 修復（US-001, AI-1601, P1 安全） | ✅ 達成 | getOrder 加 owner/admin 擁有權檢查，越權回 403/E_1007；最小爆炸半徑；補越權 E2E |
| 買家閉環前端 E2E 驗證（US-002, AI-1602, P1） | ✅ 達成 | 新增 at-buyer-pages.spec.ts（3 測試）；make validate-e2e 30 passed / 0 fail |
| DEF-017 ERP 租戶隔離（US-003, AI-1603, Buffer） | 🔶 調查 + 誠實延後 | 修法邏輯已驗證正確（跨租戶測試通過），但缺 M16 seeding 重做 → 延 Sprint 33 |

**Sprint 目標達成率**: P1 ×2 全達成（100%）；Buffer（DEF-017）依「擇機/不硬塞」原則調查後誠實延後（AC-003-4）。

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | DEF-018 getOrder IDOR 修復（AI-1601） | 3 | P1（安全） | ✅ 完成 | `be89014` |
| US-002 | 買家頁面 E2E 驗證（AI-1602） | 2 | P1 | ✅ 完成 | `cf1b132` |
| US-003 | DEF-017 ERP 租戶隔離（AI-1603，Buffer） | 3 | P2（安全） | 🔶 調查+延後 | （無程式碼，延 Sprint 33） |
| **P1 完成合計** | | **5 SP** | | ✅ 100% | |

> Sprint 32 Plan commit：`4c07e17`。

---

## 3. 交付內容

### US-001：DEF-018 getOrder IDOR 修復（AI-1601，P1 安全）
- **漏洞**：`OrderService.getOrder`→`findOrderById` 僅 `findById(orderId)`、僅需 `order:read`、無 per-user/tenant 過濾 → 任何登入者可讀他人訂單詳情（IDOR）。
- **修法（最小爆炸半徑）**：在 `getOrder` 加與同類 `cancelOrder`/`getOrderStateLogs` **完全相同的 inline 檢查**（`TenantContext.getCurrentUser()` + isAdmin(ROLE_ADMIN/SUPER_ADMIN) + `userId.equals(order.getUserId())`），越權丟 `E_1007` → **403**。`findOrderById` 不動 → 保留 404 not-found 語意；內部寫入方法（updateOrderStatus 等）與 payment/賣家/admin 流程不受影響。
- **補測試**：`BuyerOrderJourneyE2ETest.otherBuyerCannotGetOrder`（買家 C 讀 A 訂單 → 403；A 讀自己 → 200）。
- **驗證**：本地訂單 E2E `OrderControllerE2ETest + BuyerOrderJourneyE2ETest + OrderPaymentControllerE2ETest` **20 tests 0 fail**（既有不退步）。
- 🔴 **補測試揪出安全隙（延續 DEF-013/017 模式）**：盤點 getOrder 呼叫者時揪出**同類姊妹 IDOR 尚未修**（getOrderPaymentState 讀 + pay/fail/refund 寫 + logistics/payment service）→ 記 **DEF-019**，非 US-001 committed 範圍，誠實延後。

### US-002：買家頁面 E2E 驗證（AI-1602，P1）
- **背景**：EPIC-BUYER（S29/30）買家頁面先前僅 lint/build，**從未瀏覽器端到端驗證**。
- **交付**：`frontend/e2e/at-buyer-pages.spec.ts`（3 測試，沿用 at-m15 登入 helper，新註冊帳號零 seed 依賴 → 穩健不 flaky）：訂單列表空狀態、通知收件匣載入+篩選切換、預訂列表空狀態。
- **驗證**：`make validate-e2e` **30 passed / 5 skip / 0 fail**（含 3 新 buyer spec；backend ddl-auto=validate schema 對齊，US-001 未致漂移）。
- **誠實降級（AC-002-3）**：訂單詳情+內嵌物流、評價提交等**需 seed 訂單/商品**的資料流，為避免 flaky **未自動化**，改列手動 checklist（見 §6）。

### US-003：DEF-017 ERP 租戶隔離（AI-1603，Buffer）— 調查 + 誠實延後
- **修法已驗證正確**：套用正確擁有權檢查（`listingRepository.findById` → `listing.getTenantId().equals(currentTenant)` 否則 E_1007）+ 新增跨租戶測試 `IT-M16-307` → **307 通過（他租戶 SKU → 403）**。
- **但缺測試資料重做**：原 5 個同租戶 M16 測試（301/302/303/305 + M16ErpE2ETest adjust）回 500——**真因**：`findById(testListingId)`（@BeforeAll 種的 listing）在測試交易中查不到（JDBC 建 SKU 無 FK 故插入成功，JPA 卻查不到）→ E_3003（handler 未明列 → 預設 500）。
- **處置**：依「一次嘗試、綠才留」紀律於 **commit 前本地攔下**、**再度誠實回退（main 未污染）**（AC-003-4，比照 DEF-017/ERP 歷史教訓）。修法已驗證正確，Sprint 33 僅需重做 M16 seeding（各測試 SKU 指向交易內可 findById 的 listing，比照已通過的 IT-M16-307）→ 記 [DEF-017 更新](../04_planning/DEFERRED_ITEMS_TRACKER.md) AI-1603。

---

## 4. 測試狀態

| 測試類型 | Sprint 31 後 | Sprint 32 後 | 變化 |
|---------|------------|------------|------|
| `@Test`（後端靜態計數） | 689 | **690** | +1（otherBuyerCannotGetOrder；US-003 的 IT-M16-307 已隨 revert 移除） |
| 前端 e2e spec | 既有 | **+3**（at-buyer-pages） | 買家頁面瀏覽器驗證 |
| make validate-e2e | 27 passed 基準 | **30 passed / 5 skip / 0 fail** | +3 buyer spec |
| catch(Exception) 生產 | 0 | **0** | — |
| @Deprecated 生產 | 0 | **0** | — |
| Flyway | V57 | **V57**（本 Sprint 無新 migration） | — |
| 活躍 DEF | 2（DEF-017/018） | **2**（DEF-017 / DEF-019；DEF-018 已清償） | US-001 揪 DEF-019、清 DEF-018；DEF-017 再驗證延後 |

---

## 5. Definition of Done 驗核

- [x] US-001（P1 安全）所有 AC 達成；US-002（P1）完成；US-003（Buffer）調查後誠實延後（AC-003-4）
- [x] `mvn compile` 0 errors；Checkstyle 0 violations
- [x] `@Test` 靜態計數淨增（+1 後端越權 E2E + 3 前端 e2e）；既有測試無退步（訂單 E2E 20/20；validate-e2e 30 passed）
- [x] catch(Exception) 生產 0、@Deprecated 生產 0
- [x] 後端安全變更本地跑相關整合測試（US-001 訂單 E2E；US-003 M16 targeted 於 commit 前把關並攔下）
- [ ] pre-push v5 完整守門（make validate-release）—— push 前執行（本 Sprint 脫離累積期可即時把關；使用者選擇 S32 全完成後一起 push）
- [x] Sprint 32 Review / Retrospective / Release Notes 建立

> **誠實備註**：US-003（DEF-017）修法邏輯已驗證正確，但因 M16 測試 seeding 需重做（listing 在測試交易 findById 不可見）而**於本地 commit 前攔下並回退**，main 未受污染。此為 Rule 12「大聲失敗、誠實延後」與「一次嘗試綠才留」紀律的實踐，非隱性跳過。

---

## 6. 買家閉環手動驗證 Checklist（US-002 未自動化部分，AC-002-3）

> 需 live 環境 + seed 訂單/商品資料，未納入自動化 e2e（避免 flaky）。建議 Sprint 33 於 live 環境人工走查一次：

- [ ] 建立商品訂單 → `/orders` 列表出現該訂單
- [ ] 點入 `/orders/[id]` → 顯示訂單明細、狀態日誌
- [ ] Mock 付款（canPay）→ 狀態轉 PAID、付款面板更新
- [ ] （賣家）建立物流 → 買家訂單詳情內嵌物流追蹤顯示軌跡
- [ ] 訂單完成 → 逐項商品評價提交 → `/reviews/product/[listingId]` 顯示評價
- [ ] 通知收件匣 `/notifications` 收到訂單/付款相關通知、標記已讀、刪除
- [ ] 預訂 `/bookings` 顯示房型名稱（roomTitle，Sprint 31 US-001 已修）

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
