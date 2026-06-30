# M11 物流取消流程 — 業務規則確認報告 / M11 Cancellation Rules Validation

> **對應**: Sprint 25 US-006（Buffer-B）/ AI-903
> **類型**: 業務規則驗證（BA Validation Report）
> **BA**: Beatrice（ba-analyst skill）
> **建立日期**: 2026-06-30
> **狀態**: ✅ 規則已確認（PM/PO 簽核），**實作延後 Sprint 26**
> **範圍**: 僅調查與規則確認，**不含實作**（依 Sprint 25 Plan）

---

## 1. 利害關係人 / Stakeholders

| 利害關係人 | 角色 | 影響力 | 關注點 | 本次決策 |
|-----------|------|--------|--------|---------|
| 使用者（koalawu） | PM/PO | 高 | 取消政策、退費風險 | ✅ 已確認（SHIPPING 不可取消） |
| 買家 | 終端使用者 | 中 | 能否取消、退費 | 已出貨改走退貨/退款 |
| 賣家/租戶 | 商家 | 中 | 出貨後成本、退運費 | 受退貨流程保障 |
| 物流供應商（HCT/TCAT） | 外部 | 低 | 取消時效 | 本案不需即時取消 API |

---

## 2. 現有流程（As-Is）— 技術現況調查

> 來源：Explore Agent 程式碼調查（2026-06-30）

### 2.1 訂單狀態與取消 gate

- **`OrderStateMachine.canCancel(status)`** → 僅 `CREATED / PAID / CONFIRMED` 回 true（[OrderStateMachine.java:131-136](../../backend/src/main/java/com/nextkey/ecommerce/core/order/OrderStateMachine.java#L131-L136)）
- **`OrderService.cancelOrder()`** 以 `canCancel()` 為守門；PAID 取消會轉 REFUNDING（[OrderService.java:376-418](../../backend/src/main/java/com/nextkey/ecommerce/core/order/OrderService.java#L376-L418)）
- ⚠️ **不一致**：`OrderStateMachine` 轉換表卻允許 `SHIPPING → CANCELLED`（[OrderStateMachine.java:74-85](../../backend/src/main/java/com/nextkey/ecommerce/core/order/OrderStateMachine.java#L74-L85)），與 `canCancel()` 排除 SHIPPING 矛盾

### 2.2 物流單

- **`Logistics.LogisticsStatus`** = PENDING / PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY / DELIVERED / FAILED / RETURNED（7 態，**無 CANCELLED**）
- **`LogisticsService.cancelLogistics()`** 已實作（Mock）：任意非 DELIVERED → RETURNED；拒絕 DELIVERED（[LogisticsService.java:201-217](../../backend/src/main/java/com/nextkey/ecommerce/core/logistics/LogisticsService.java#L201-L217)）
- ⚠️ **錯誤碼誤用**：使用 E_7000/E_7002（語意為 Supplier/PO），應為 E_7500 系列物流專用碼
- **物流單建立時機**：訂單轉 **SHIPPING 時**才建立 logistics（DEF-007 履約整合）→ **CONFIRMED 以前無物流單**

### 2.3 供應商 Provider

- **`LogisticsProvider`** 介面：`getProviderCode` / `createShipment` / `trackShipment` —— **無 `cancelShipment`**
- HCT / TCAT 皆為 Mock stub，無遠端取消呼叫

### 2.4 訂單取消 ↔ 物流取消

- `OrderService.cancelOrder()` **未連動** `cancelLogistics()`（鬆散耦合，透過 orderId 外鍵）

---

## 3. 目標流程（To-Be）— 已確認業務規則

> 🔴 **PM/PO 決策（2026-06-30）**：**SHIPPING（已出貨）訂單不可取消，改走退貨/退款流程**

| # | 業務規則 | 確認結果 |
|---|---------|---------|
| AC-006-1 | **CONFIRMED → CANCELLED** | ✅ **允許**。此時尚無物流單，僅變更訂單狀態（PAID 者轉 REFUNDING）。現況 `canCancel()` 行為**正確**。 |
| AC-006-1 | **SHIPPING → CANCELLED** | ❌ **不允許**（經 PM/PO 確認）。已出貨改走退貨/退款。`canCancel()` 排除 SHIPPING **正確**；`OrderStateMachine` 轉換表的 `SHIPPING→CANCELLED` 為**待清理的不一致**。 |
| AC-006-2 | 是否需呼叫 HCT/TCAT API 取消 | ❌ **不需要**。可取消狀態（CREATED/PAID/CONFIRMED）皆無物流單，無遠端單可取消。`LogisticsProvider.cancelShipment` **本案不需新增**。 |
| AC-006-3 | 訂單取消是否連動物流取消 | ❌ **不需要**。可取消狀態無物流單 → `cancelOrder` 無需連動 `cancelLogistics`。 |

### 為何這組規則自洽？

物流單在 **SHIPPING 時才建立**；而可取消狀態（CREATED/PAID/CONFIRMED）皆在 SHIPPING **之前**。因此「可取消的訂單一定還沒有物流單」→ 自然不需要 provider 取消 API，也不需要訂單↔物流連動。整組規則內部一致。

---

## 4. 差異分析

| 項目 | As-Is | To-Be（確認後） | 影響 |
|------|-------|----------------|------|
| canCancel gate | CREATED/PAID/CONFIRMED | **不變**（確認正確） | 無 |
| OrderStateMachine 表 | 允許 SHIPPING→CANCELLED | 應**限縮/移除** | 程式碼一致性（Sprint 26 清理） |
| cancelLogistics 錯誤碼 | E_7000/E_7002 誤用 | 改 E_7500 系列 | 技術債（Sprint 26） |
| provider cancelShipment | 無 | **維持無**（本案不需） | 無 |
| cancelOrder↔logistics | 未連動 | **維持不連動**（本案不需） | 無 |

---

## 5. 風險與緩解

| 風險 | 機率 | 影響 | 緩解 |
|------|------|------|------|
| OrderStateMachine 表允許 SHIPPING→CANCELLED 被其他路徑誤用 | 低 | 中 | 實務上 `cancelOrder` 以 `canCancel()` 守門，SHIPPING 進不來；仍建議 Sprint 26 清理轉換表 |
| 退貨/退款流程尚未完整定義 | 中 | 中 | 屬獨立題目（M07 退款 / 退貨流程），另立需求，不在 M11 取消範圍 |
| cancelLogistics 錯誤碼誤用造成 API 回應語意不清 | 低 | 低 | 列技術債 Sprint 26 修正 |

---

## 6. 結論與建議（AC-006-3）

### 6.1 結論

- ✅ **業務規則已明確並經 PM/PO 確認**：SHIPPING 不可取消、改走退貨/退款；可取消狀態無物流單故不需 provider API 與連動。
- ✅ 現況 `canCancel()` 與 `cancelLogistics()` 主邏輯**符合**確認後的規則。
- ⚠️ 發現 2 項**待清理項目**（非阻擋、非本 Sprint 範圍）。

### 6.2 是否納入本 Sprint 實作？

**否。** US-006 依 Sprint 25 Plan 為「僅調查與規則確認，不含實作」。本 Sprint **不動程式碼**（Rule 3：精準改動）。

### 6.3 延後 Sprint 26 的實作項目（已登記 DEF）

| 項目 | 內容 | 優先 |
|------|------|------|
| DEF-010 | `OrderStateMachine` 一致性清理：移除/限縮轉換表的 `SHIPPING→CANCELLED`，與 `canCancel()` 對齊 | 低 |
| DEF-011 | `cancelLogistics` 錯誤碼由 E_7000/E_7002 改為 E_7500 系列物流專用碼 | 低 |

> 退貨/退款流程（已出貨後）屬 M07/獨立需求，非 M11 取消範圍，另案處理。

---

## 7. 驗證記錄 / 簽核

| 項目 | 驗證者 | 結果 |
|------|--------|------|
| SHIPPING 取消政策 | PM/PO（koalawu） | ✅ 不可取消，改走退貨/退款 |
| CONFIRMED 取消規則 | BA Beatrice | ✅ 確認現況正確 |
| provider API / 連動需求 | BA Beatrice | ✅ 確認本案不需 |
| 待清理項目 | BA Beatrice | ✅ 登記 DEF-010 / DEF-011（Sprint 26） |

**下一步行動**:
1. ✅ 本報告歸檔（US-006 完成）
2. ✅ DEF-010 / DEF-011 登記至 DEFERRED_ITEMS_TRACKER
3. ⏭️ Sprint 26 視容量處理 DEF-010 / DEF-011（含補取消測試）

---

**基於**: AISDLC v0.09 BA Agent（Beatrice）+ Explore Agent 程式碼調查
