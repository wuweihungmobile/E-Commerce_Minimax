# Sprint 33 Review / Sprint 33 評審會議

> **Sprint 編號**: Sprint 33
> **期間**: 2027-01-17 ~ 2027-01-30
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_33_PLAN.md](../04_planning/SPRINT_33_PLAN.md), [SPRINT_32_RETRO.md](./SPRINT_32_RETRO.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 安全修復收尾（訂單/ERP 全面租戶隔離）

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| DEF-017 ERP 租戶隔離（US-001, AI-1701, P1） | 🔶 調查完成 → 延 S34 | 三層根因完整診斷；修法已驗證正確，缺 M16 tenant seeding 整套重做（超出本 Sprint） |
| DEF-019 訂單付款 IDOR（US-002, AI-1702, P1） | ✅ 付款側達成 | 4 個訂單付款方法加 checkOrderOwnership，越權 403；物流/賣家側部分延後 |
| 買家 live 走查 + 併發慣例（US-003, Buffer） | 🔶 慣例已記；live 走查延 S34 | AI-1704 慣例記於 Retro；AI-1703 需 live 環境 |

**Sprint 目標達成率**: US-002（P1 付款側）完成並驗證；US-001（P1）因根因深達測試基建而誠實延後（非未達標，是有意識風險控管）。

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | DEF-017 ERP 租戶隔離（AI-1701） | 3 | P1（安全） | 🔶 調查+延 S34 | （無程式碼；診斷見 `5497347`） |
| US-002 | DEF-019 訂單付款 IDOR（AI-1702） | 3 | P1（安全） | ✅ 付款側完成 | `32b5590` |
| US-003 | 買家 live 走查 + 併發慣例（Buffer） | 2 | P3 | 🔶 部分（慣例已記） | （Retro 記錄） |
| **完成合計** | | **~2 SP**（US-002 付款側） | | | |

> Sprint 33 Plan commit：`5497347`。

---

## 3. 交付內容

### US-001：DEF-017 ERP 租戶隔離（AI-1701）— 三層根因診斷 + 誠實延後
- 本 Sprint 深入查 stack trace，**逐層釐清真因**（更正 Sprint 32 誤判的「E_3003 findById 查不到」）：
  1. `Listing.tenantId` 為 `@Column(insertable=false)` 影子欄位（@ManyToOne tenant 的 tenant_id），@BeforeAll 用 `.tenantId(...)` 建 listing、未設 `.tenant` 關聯 → tenant_id 存 null → 服務端 `.equals()` **NPE → E-9900/500**。
  2. 加 null 安全後變 **403**（tenant_id 仍 null）。
  3. 改以 JDBC 補寫 tenant_id → **FK violation**：`Tenant.id` 為 @GeneratedValue，`@WithErpSecurity` 硬編的 FIXED_TENANT_ID 在 tenants 表**無對應列**，listings.tenant_id 有 FK。
- **結論**：M16 tenant seeding 需整套重做（raw SQL 種 FIXED_TENANT_ID 租戶列，比照 TestDatabaseInitializer），非 Buffer/單 Sprint 可容納。生產修法（null 安全租戶檢查）**已驗證正確**（跨租戶測試通過）。依紀律三度於 commit 前本地攔下、誠實回退（**main 未污染**）→ [DEF-017](../04_planning/DEFERRED_ITEMS_TRACKER.md) 延 Sprint 34。

### US-002：DEF-019 訂單付款 IDOR 修復（AI-1702）
- `PaymentStateService` 的 `getOrderPaymentState`（讀）+ `mockPaymentSuccess/Failure/mockRefund`（寫）原無擁有權過濾 → 任何登入者可查詢/付款/退款他人訂單（DEF-018 付款側姊妹）。
- **修法**：新增 `checkOrderOwnership` helper（買家限本人 `userId.equals(order.getUserId())`、admin 放行、越權 403/E_1007），套用四個訂單付款方法。
- **補測試**：`BuyerOrderJourneyE2ETest.otherBuyerCannotAccessOrderPayment`（買家 D 讀/付 A 訂單 → 403）。
- **驗證**：本地 **21 tests 0 fail**（OrderController 12 + BuyerJourney 5 + OrderPayment 4）——寫入側擁有權檢查未打破既有付款測試。
- **誠實部分交付（AC-002-4）**：`LogisticsService.createLogistics`（賣家側，**租戶語意**非買家擁有權）+ `PaymentService.processOrderPayment` 留 DEF-019 續修（Sprint 34）。

### US-003（Buffer）：買家 live 走查 + 併發慣例
- **AI-1704（已記錄）**：`make validate-e2e` 執行時**勿 commit 前端檔**——pre-commit 前端 lint 會與併發的 validate-e2e 搶用前端資源導致 `git commit` exit 128（本 Sprint 實際踩到並重試成功）。詳見 Retro §4。
- **AI-1703（延 S34）**：買家閉環 live 手動走查需 live 環境，順延。

---

## 4. 測試狀態

| 測試類型 | Sprint 32 後 | Sprint 33 後 | 變化 |
|---------|------------|------------|------|
| `@Test`（後端靜態計數） | 690 | **691** | +1（otherBuyerCannotAccessOrderPayment；DEF-017 的 IT-M16-307 隨 revert 移除） |
| catch(Exception) 生產 | 0 | **0** | — |
| @Deprecated 生產 | 0 | **0** | — |
| Flyway | V57 | **V57**（無新 migration） | — |
| 活躍 DEF | 2（DEF-017/019） | **2**（DEF-017 / DEF-019；DEF-019 付款側已修，降為物流/賣家側） | US-002 修 DEF-019 付款側 |
| 訂單付款 IDOR（讀+寫） | 未修（DEF-019） | ✅ 已修（403 隔離） | — |

---

## 5. Definition of Done 驗核

- [x] US-002（P1 付款側）AC 達成並本地驗證（21 tests 0 fail）
- [x] US-001（P1）誠實延後（根因完整診斷、修法已驗證、main 未污染）
- [x] `mvn compile` 0 errors；Checkstyle 0 violations
- [x] `@Test` 靜態計數淨增（+1 付款越權 E2E）；既有訂單/付款 E2E 無退步
- [x] catch(Exception) 生產 0、@Deprecated 生產 0
- [x] 後端安全變更本地跑相關整合測試（US-002 付款/訂單 E2E；US-001 M16 targeted 於 commit 前把關並攔下）
- [ ] pre-push v5 完整守門（make validate-release）—— push 前執行（累積 S32+S33，檢查點徵詢後）
- [x] Sprint 33 Review / Retrospective / Release Notes 建立

> **誠實備註**：US-001（DEF-017）連續三個 commit 前的本地驗證都攔下並回退，這是 Rule 12「大聲失敗、誠實延後」與「一次嘗試綠才留」紀律的實踐——每次都更深入根因（NPE→403→FK），最終完整診斷並明確 Sprint 34 修法路徑（raw SQL 種 tenant）。US-002 誠實部分交付（付款側修完、物流側續留）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
