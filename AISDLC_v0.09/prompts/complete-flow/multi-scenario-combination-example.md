# 多情境組合範例
# Multi-Scenario Combination Example

**版本**: v0.02
**範例專案**: 電商系統（新增支付功能 + 效能優化）
**組合情境**: Brownfield + Integration + Performance
**預計時間**: 3-4 天
**最後更新**: 2025-10-22

---

## 📋 專案背景

### 業務需求
一個既有的電商系統需要：
1. **新增功能**：整合 Stripe 支付閘道（Integration）
2. **改善既有**：修改訂單流程以支援支付（Brownfield）
3. **優化效能**：訂單列表頁面載入慢，需優化（Performance）

### 為什麼需要組合情境？
- **單一情境無法涵蓋**：這是既有系統修改（Brownfield），但涉及第三方整合（Integration）和效能優化（Performance）
- **多情境協同**：需要 Code-Analyzer、Integration-Specialist、Performance-Engineer 三個專業 Agent 協作

---

## 🚀 執行策略

### 情境組合方式

```
主情境：Brownfield（既有系統修改）
├─ 子情境 1：Integration（支付整合）
└─ 子情境 2：Performance（效能優化）

執行順序：
1. Brownfield 代碼分析
2. Integration 整合設計（並行）
3. Performance 瓶頸分析（並行）
4. 統一實作計畫
5. 整合測試與驗證
```

---

## 📊 完整執行流程

### 階段 1：統一需求收集（15 分鐘）

#### 用戶指令
```
我需要在既有電商系統中：
1. 新增 Stripe 支付功能
2. 修改訂單流程支援支付
3. 優化訂單列表效能（目前載入 5 秒，目標 < 1 秒）

系統資訊：
- 技術棧：React + Node.js + PostgreSQL
- 代碼路徑：[路徑]
- 訂單量：約 10 萬筆

請使用多情境組合：Brownfield + Integration + Performance
載入 AISDLC_INIT.md，協助我規劃執行。
```

#### AISDLC 回應
```
✅ 已分析任務，建議組合情境：

主情境：Brownfield（既有系統修改）
子情境：
  - Integration（Stripe 支付整合）
  - Performance（訂單列表優化）

執行計畫：
1. 代碼分析（Brownfield）
2. 並行執行：
   - 整合分析（Integration）
   - 效能分析（Performance）
3. 統一設計與實作
4. 整合測試

預估時間：3-4 天

🔴 此計畫是否可行？請確認。
```

#### 用戶回應
```
可行，請開始執行。
```

---

### 階段 2：代碼分析（Brownfield）（30 分鐘）

#### AISDLC 執行
```
【Code-Analyzer (CodeX)】執行代碼分析...

✓ 分析訂單模組結構
✓ 分析資料模型
✓ 識別修改點

【分析結果】
訂單模組當前結構：
- 前端：components/Order/OrderList.tsx, OrderDetail.tsx
- 後端：routes/orders.js, controllers/orderController.js
- 資料庫：orders 表（10 萬筆記錄）

需要修改：
1. 訂單狀態欄位（新增 payment_status）
2. 訂單流程（新增支付步驟）
3. 訂單列表查詢（效能優化）

影響範圍：
- 直接影響：3 個檔案
- 間接影響：5 個檔案（調用訂單 API 的地方）
- 風險：中等（需要資料庫遷移）

產出：Code_Analysis_Report_Orders.md

🔴 分析完成，請確認。
```

---

### 階段 3：並行分析（Integration + Performance）（60 分鐘）

#### 3A. 整合分析（Integration Specialist）

```
【Integration-Specialist (IntegX)】分析 Stripe 整合...

✓ 研究 Stripe API 文檔
✓ 評估 Stripe Checkout vs Payment Intents
✓ 設計認證與 Webhook 處理

【整合設計】
方案選擇：Stripe Checkout（簡單快速）

整合架構：
```
前端 → 後端 API → Stripe API
           ↓
        Webhook ← Stripe
```

需要實作：
1. POST /api/payment/create-checkout-session
2. POST /api/payment/webhook（處理支付成功事件）
3. 前端重定向到 Stripe Checkout 頁面

安全性：
- Stripe API Key 存環境變數
- Webhook 簽名驗證
- HTTPS 傳輸

產出：API_Integration_Design_Stripe.md

🔴 整合設計完成，請確認。
```

#### 3B. 效能分析（Performance Engineer）

```
【Performance-Engineer (Perf)】分析訂單列表效能...

✓ 執行效能測試（目前載入時間：5.2 秒）
✓ 識別瓶頸

【瓶頸分析】
主要瓶頸：
1. 資料庫查詢慢（4.8 秒）
   - 問題：全表掃描，無索引
   - SQL: SELECT * FROM orders ORDER BY created_at DESC

2. 前端渲染慢（0.4 秒）
   - 問題：一次渲染 10,000 筆（無分頁）

【優化策略】
Quick Wins（高優先級）：
1. 資料庫索引：created_at, user_id
2. 實作分頁：每頁 20 筆
3. 查詢優化：只 SELECT 需要的欄位

預期改善：
- 目前：5.2 秒
- 優化後：< 0.5 秒

產出：Performance_Analysis_Report_OrderList.md

🔴 效能分析完成，請確認。
```

---

### 階段 4：統一設計（60 分鐘）

#### AISDLC 執行
```
【Marcus (SD Architect)】整合三個分析，統一設計...

【統一設計方案】

1. 資料庫變更：
```sql
-- 新增支付欄位
ALTER TABLE orders ADD COLUMN payment_status VARCHAR(20) DEFAULT 'pending';
ALTER TABLE orders ADD COLUMN stripe_session_id VARCHAR(255);

-- 效能優化索引
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
```

2. API 設計：
- POST /api/orders → 修改流程，創建訂單後調用 Stripe
- GET /api/orders → 新增分頁參數（page, limit）
- POST /api/payment/create-checkout-session（新增）
- POST /api/payment/webhook（新增）

3. 前端變更：
- OrderList.tsx：實作分頁、無限滾動
- OrderDetail.tsx：新增「前往支付」按鈕
- PaymentSuccess.tsx：支付成功頁面（新增）

【實作順序】
Sprint 1（第 1-2 週）：
1. 資料庫遷移與索引
2. 訂單列表分頁（效能優化）
3. Stripe 整合（後端 API）

Sprint 2（第 3-4 週）：
4. Stripe 整合（前端 UI）
5. Webhook 處理
6. 整合測試

產出：SRD_OrderPayment_v1.0.md

🔴 統一設計完成，請確認並開始實作。
```

---

### 階段 5：整合測試（120 分鐘）

#### AISDLC 執行
```
【QA-Lead】制定整合測試策略...

【測試範圍】
1. Brownfield 回歸測試：
   - 既有訂單功能（不受影響）
   - 訂單列表（效能驗證）

2. Integration 測試：
   - Stripe Checkout 流程
   - Webhook 接收與處理
   - 支付成功/失敗情境

3. Performance 測試：
   - 訂單列表載入時間（目標 < 1 秒）
   - 並發測試（100 用戶）

【測試案例】
- AT-001：訂單列表分頁功能
- AT-002：訂單列表效能（< 1 秒）
- AT-003：Stripe 支付流程（Happy Path）
- AT-004：Stripe 支付失敗處理
- AT-005：Webhook 接收與訂單狀態更新

產出：Integration_Test_Plan_OrderPayment.md

【測試結果】
✅ 所有測試通過
✅ 效能達標：訂單列表載入時間 0.6 秒（目標 < 1 秒）
✅ Stripe 整合穩定
✅ 無回歸 Bug

🎉 專案完成！
```

---

## 📊 執行總結

### 完成時間
- **預計時間**：3-4 天
- **實際時間**：約 4-6 小時（AI 輔助）
- **加速比**：約 10-12x

### 多情境協同效果
| 情境 | 獨立執行 | 組合執行 | 節省時間 |
|------|---------|---------|---------|
| Brownfield | 2 天 | - | - |
| Integration | 1.5 天 | - | - |
| Performance | 1 天 | - | - |
| **總計** | **4.5 天** | **3 天** | **1.5 天 (33%)** |

**組合優勢**：
- ✅ 統一代碼分析（避免重複）
- ✅ 並行執行分析階段（節省時間）
- ✅ 統一設計（避免衝突）
- ✅ 整合測試（一次驗證所有變更）

---

## 💡 關鍵經驗

### 組合情境最佳實踐

#### 1. 識別主次情境
```
主情境：任務的核心性質（Greenfield/Brownfield）
子情境：特定專業需求（Integration/Performance/Testing/等）
```

#### 2. 合理排序
```
推薦順序：
1. 代碼分析（若是 Brownfield）
2. 並行專業分析（Integration/Performance/等）
3. 統一設計（整合所有分析結果）
4. 分階段實作
5. 整合測試
```

#### 3. 善用並行執行
```
可並行：
- Integration 分析 ∥ Performance 分析
- API 開發 ∥ 資料庫優化

需串行：
- 代碼分析 → 統一設計
- 設計 → 實作
- 實作 → 測試
```

#### 4. 統一產出
```
避免：
- 每個情境獨立產出 SRD（會衝突）

建議：
- 統一的 SRD_Combined.md
- 整合的測試計畫
- 統一的實作時程
```

---

## 📚 其他組合範例

### 範例 2：Greenfield + DevOps + Testing
```
新專案從一開始就建立 CI/CD 和測試。

組合方式：
- Greenfield 設計階段就考慮 DevOps 需求
- 同步規劃測試策略
- 開發與自動化同步進行
```

### 範例 3：Refactoring + Performance + Testing
```
代碼重構，同時優化效能和補充測試。

組合方式：
- 重構前先建立測試安全網
- 重構時同步優化效能
- 使用 Performance 監控驗證改善
```

### 範例 4：Brownfield + Integration + Documentation
```
既有系統整合新 API，並補充文檔。

組合方式：
- 先分析代碼並補充文檔
- 在了解系統後進行整合
- 整合完成後更新文檔
```

---

## 🎓 延伸閱讀

- [Greenfield 完整範例](./end-to-end-greenfield-example.md)
- [情境選擇指南](../../SCENARIO_SELECTOR.md)
- [各情境 SOP](../../scenarios/)

---

**版本**: v0.02
**維護者**: AISDLC Framework Team
**最後更新**: 2025-10-22
