# Sprint 29 Review / Sprint 29 評審會議

> **Sprint 編號**: Sprint 29
> **期間**: 2026-11-22 ~ 2026-12-05
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_29_PLAN.md](../04_planning/SPRINT_29_PLAN.md), [PRODUCT_BACKLOG.md](../04_planning/PRODUCT_BACKLOG.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: EPIC-BUYER 買家端閉環**起手** —— 讓買家在 UI 完成「下單 → 付款 → 收通知 → 查訂單」

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| M05 訂單前端（US-001, P0） | ✅ 達成 | 我的訂單列表 + 詳情 + 取消 + 狀態日誌 |
| M09 通知收件匣前端（US-002, P0） | ✅ 達成 | 收件匣 + 未讀數 + 篩選 + 標記已讀 + 刪除 |
| M07 付款前端（US-003, P0，對 Mock） | ✅ 達成 | 訂單詳情整合 Mock 付款（CREATED→PAID）+ 狀態呈現 |

**Sprint 目標達成率**: 100%（P0 ×3 = 8 SP 全完成）；買家最小可用閉環打通。

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | M05 訂單前端（列表/詳情/取消/狀態日誌） | 3 | **P0** | ✅ 完成 | `ae3073b` |
| US-002 | M09 通知收件匣前端 | 2 | **P0** | ✅ 完成 | `31d2b5c` |
| US-003 | M07 付款前端（Mock，訂單詳情整合） | 3 | **P0** | ✅ 完成 | `c2bdc17` |
| **完成合計** | | **8 SP** | | ✅ 100% | |

> Sprint 29 Plan 文件 commit：`ed2e7e6`（已先 push）。

---

## 3. 交付內容

### US-001：M05 訂單前端
- `services/order.ts`：`OrderService`（getOrders 分頁 / getOrder 詳情 / cancelOrder / getOrderLogs）+ 型別對齊 `OrderDto`；`ORDER_STATUS_LABELS`、`orderStatusBadgeVariant`、`isCancellable` helper（9 狀態）。
- `lib/api.ts`：補 `orders.logs` 端點。
- `(auth)/orders`：我的訂單列表（分頁、狀態徽章、商品/訂房標記、空狀態）。
- `(auth)/orders/[id]`：訂單詳情（項目明細/金額/運費、收件或訂房資訊、備註、**狀態日誌時間軸**）+ 取消訂單（可填原因）。

### US-002：M09 通知收件匣前端
- `services/notificationInbox.ts`：`NotificationInboxService`（getNotifications 分頁+unreadOnly / getUnreadCount / markAsRead 單筆+全部 / deleteNotification）+ 通知型別中文標籤。**刻意與既有 `notification.ts`（模板管理）分離**，避免混淆兩種關注點。
- `lib/api.ts`：新增買家 `notifications` 端點群（list/unread-count/read/delete）。
- `(auth)/notifications`：收件匣（未讀數、全部/僅未讀篩選、標記已讀單筆+全部、刪除、**訂單類通知可跳訂單詳情**）。

### US-003：M07 付款前端（Mock）
- `services/payment.ts`：`OrderPaymentService`（getPaymentState / pay / payFail），使用 **OrderPaymentController 訂單層端點**（狀態機驅動，付款成功自動 CREATED→PAID）。
- `lib/api.ts`：`orders` 補訂單付款端點（payment/pay/payFail/refund）。
- `(auth)/orders/[id]`：付款面板 —— `canPay` 時顯示應付金額 + 確認付款（模擬）+ 模擬付款失敗；付款成功顯示交易編號/時間；付款後即時刷新訂單與狀態日誌。取消判斷改以後端狀態機 `payment.canCancel` 為權威（fallback 前端啟發式）。

---

## 4. 測試狀態

| 測試類型 | Sprint 28 後 | Sprint 29 後 | 變化 |
|---------|------------|------------|------|
| `@Test` 方法總數（後端靜態計數） | 683 | **683** | 0（本 Sprint 純前端，後端零變更） |
| 前端 lint | 0 errors | **0 errors** | 新增 3 service（anonymous default warning 與既有慣例一致） |
| 前端 type-check / build | 通過 | **通過**（37→38 頁，新增 /orders、/orders/[id]、/notifications） | — |
| catch(Exception) / @Deprecated 生產 | 0 / 0 | **0 / 0** | — |
| 活躍 DEF | 2 | **2**（DEF-016 audit / DEF-017 ERP，皆非本 Sprint 範圍） | — |

> **設計對齊**：三個 service 皆沿用既有 `product.ts` 的 class + `apiClient.get<ApiResponse<T>>().data.data` 解包模式；頁面沿用 `(auth)` 群組（cart/checkout 慣例）+ `@/components/ui/*`。

---

## 5. Definition of Done 驗核

- [x] US-001~003 所有 AC 達成（買家可「下單 → 付款 → 收通知 → 查訂單」）
- [x] 前端 lint（0 errors）/ type-check / build 通過
- [x] 新增 service 型別對齊後端 DTO；api.ts 端點補齊（orders.logs、notifications、orders 付款）
- [x] 遵守 Next 16/React 19 嚴格 effect（async fetch、cleanup cancel flag、useCallback）
- [x] 既有測試無退步（`@Test` 683）；後端無破壞性變更
- [ ] pre-push v5 完整守門（make validate-release）—— 隨本批 push 執行
- [x] Sprint 29 Review / Retrospective / Release Notes 建立

> **誠實備註**：本 Sprint DoD 為「lint/type-check/build 通過」（靜態），已達成。新買家流程對**執行中後端**的真實 E2E（登入買家→實際下單付款收通知）屬手動驗證，建議於有 demo 資料的環境走一次（列入 Retro 行動）。付款/收件匣端點的買家授權（`order:update` 等）以後端為權威，前端已對 4xx 做錯誤呈現。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
