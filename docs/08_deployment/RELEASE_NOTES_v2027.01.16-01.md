# Release Notes - v2027.01.16-01 (Sprint 32)

**發布日期**: 2027-01-16（規劃）／實作完成 2026-07-01
**發布類型**: Minor（安全修復 + 買家閉環驗證）
**Sprint**: Sprint 32
**狀態**: ⏳ 待 push（S32 全完成後跑完整守門，檢查點徵詢後 push）

> Sprint 32 主題：安全修復（DEF-018 getOrder IDOR）+ 買家閉環前端 E2E 驗證

---

## 安全修復 🔒

- **DEF-018 getOrder IDOR 修復（US-001，AI-1601，P1）**：`OrderService.getOrder` 原僅 `findById`、無擁有權過濾 → 任何登入者可讀他人訂單詳情（含 state logs）。修法比照同類 `cancelOrder`/`getOrderStateLogs` 的 inline 檢查——買家限本人（`userId.equals(order.getUserId())`）、admin（ROLE_ADMIN/SUPER_ADMIN）放行，越權回 **403/E_1007**。`findOrderById` 不動保留 404 not-found 語意；**最小爆炸半徑**——不影響 payment/賣家/admin 內部取單流程。

## 品質 🛡️

- **買家頁面 E2E 驗證（US-002，AI-1602，P1）**：新增 `frontend/e2e/at-buyer-pages.spec.ts`（3 測試）——EPIC-BUYER（S29/30）買家頁面（訂單/通知/預訂）首次瀏覽器端到端驗證（先前僅 lint/build）。皆以新註冊帳號、零 seed 依賴（穩健不 flaky）。
- **越權 E2E**：`BuyerOrderJourneyE2ETest.otherBuyerCannotGetOrder`（買家 C 讀 A 訂單 → 403）。
- 後端 `@Test` 689→**690**；`make validate-e2e` **30 passed / 5 skip / 0 fail**（含 3 新 buyer spec；schema 對齊）。

## 安全（調查發現）🔍

- **DEF-019 訂單付款/物流 IDOR 姊妹（→ Sprint 33 AI-1702）**：US-001 盤點 getOrder 呼叫者時揪出同類 IDOR 尚未修——`getOrderPaymentState`（讀）、`pay/fail/refund`（寫）、`PaymentService.processOrderPayment`、`LogisticsService.createLogistics` 皆 `findById` 無擁有權過濾。非 US-001 committed 範圍，誠實記 [DEF-019](../04_planning/DEFERRED_ITEMS_TRACKER.md)。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| **DEF-017 ERP 手動庫存租戶隔離** | Sprint 33（AI-1701）：修法已驗證正確（跨租戶測試通過），僅缺 M16 測試 seeding 重做（listing 於測試交易 findById 不可見） |
| **DEF-019 付款/物流 IDOR** | Sprint 33（AI-1702，P1 安全） |
| **買家閉環需 seed 資料流** | 未自動化，改手動 checklist（Review §6）；live 走查 Sprint 33（AI-1703） |

## 資料庫遷移 🗄️

- 無新 migration（Flyway 維持 V57）。

## 重大變更 ⚠️

- **行為變更（安全）**：`GET /v2/orders/{orderId}` 現強制擁有權檢查——非訂單擁有者且非 admin 存取回 **403**（原任何登入者可取）。前端買家取單本即帶自己 token，無影響；任何依賴越權取單的呼叫方須調整（不應存在）。

## 驗證狀態 ✅

- 後端 `@Test` 靜態計數：**690**（+1）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0；Checkstyle 0 violations
- 訂單相關 E2E（OrderController + BuyerJourney + OrderPayment）**20 tests 0 fail**（本地）
- `make validate-e2e` 30 passed / 5 skip / 0 fail
- 活躍 DEF：2（DEF-017 ERP / DEF-019 付款物流 IDOR；DEF-018 已清償）
- pre-push v5 完整守門（act + schema + e2e）—— push 前執行

## 內含 Commit（Sprint 32）

| US / 項目 | Commit |
|----------|--------|
| Sprint 32 Plan | `4c07e17` |
| US-001 DEF-018 getOrder IDOR 修復（AI-1601） | `be89014` |
| US-002 買家頁面 E2E 驗證（AI-1602） | `cf1b132` |
| Sprint 32 收尾（Review/Retro/Release Notes + tracker） | （本次） |

> US-003（DEF-017）為調查+誠實延後，無程式碼 commit（修法於本地 commit 前攔下回退，main 未污染）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
