# 情境銜接指南 / Scenario Transition Guide

**版本 / Version**: v0.09
**建立日期 / Created**: 2025-11-10
**文檔目的 / Purpose**: 定義 AISDLC 十大情境之間的銜接關係、轉換時機、文檔傳遞規範與情境組合機制

---

## 📋 文檔概述 / Document Overview

### 目的 / Purpose

本指南提供：
1. **情境轉換矩陣** - 哪些情境之間可以銜接
2. **轉換時機判斷** - 何時應該從一個情境切換到另一個情境
3. **文檔傳遞規範** - 情境轉換時需要傳遞哪些文檔
4. **實際案例走查** - 常見的情境組合流程範例

### 適用對象 / Target Audience

- 專案經理 (PM/PO)
- 系統分析師 (SA)
- 開發團隊成員
- 任何使用 AISDLC 框架的實務工作者

---

## 🗺️ 情境轉換矩陣 / Scenario Transition Matrix

### 完整轉換關係表

| 從情境 (From) | 可轉換到 (To) | 轉換時機 | 機率 | 優先級 |
|--------------|--------------|---------|------|--------|
| **Greenfield** | Integration | 需要整合第三方服務 | 80% | High |
| **Greenfield** | Testing | 開發完成,準備測試 | 100% | Critical |
| **Greenfield** | Security | 需要安全審查 | 60% | High |
| **Greenfield** | Performance | 效能要求高 | 40% | Medium |
| **Greenfield** | DevOps | 準備部署 | 100% | Critical |
| **Greenfield** | Documentation | 需要產出文檔 | 90% | High |
| **Brownfield** | Refactoring | 需要重構 | 70% | High |
| **Brownfield** | Integration | 新增整合功能 | 50% | Medium |
| **Brownfield** | Testing | 改造完成後測試 | 100% | Critical |
| **Brownfield** | Security | 安全漏洞修復 | 30% | Medium |
| **Brownfield** | Performance | 效能問題優化 | 60% | High |
| **Brownfield** | DevOps | 更新部署流程 | 80% | High |
| **Brownfield** | Documentation | 更新文檔 | 70% | High |
| **Refactoring** | Testing | 重構後驗證 | 100% | Critical |
| **Refactoring** | Performance | 效能驗證 | 50% | Medium |
| **Refactoring** | Documentation | 更新技術文檔 | 80% | High |
| **Integration** | Testing | 整合測試 | 100% | Critical |
| **Integration** | Security | API 安全審查 | 40% | Medium |
| **Integration** | Documentation | API 文檔 | 90% | High |
| **Performance** | Testing | 效能測試驗證 | 100% | Critical |
| **Performance** | Refactoring | 效能重構 | 30% | Medium |
| **Security** | Testing | 安全測試 | 100% | Critical |
| **Security** | Documentation | 安全文檔 | 80% | High |
| **Testing** | DevOps | 測試通過後部署 | 100% | Critical |
| **Testing** | Documentation | 測試報告文檔 | 70% | High |
| **DevOps** | Documentation | 部署文檔 | 60% | Medium |
| **Documentation** | Any | 文檔可在任何時間點產出 | - | - |
| **Migration** | Testing | 遷移後全面驗證 | 100% | Critical |
| **Migration** | DevOps | CI/CD 重建與部署 | 100% | Critical |
| **Migration** | Security | 遷移後安全審查 | 70% | High |
| **Migration** | Performance | 遷移後效能對比 | 80% | High |
| **Migration** | Documentation | 遷移手冊與技術文檔 | 90% | High |
| **Migration** | Integration | 新增第三方/硬體整合 | 50% | Medium |
| **Brownfield** | Migration | 需要全面技術棧替換 | 20% | High |
| **Refactoring** | Migration | 重構範圍擴大至技術棧遷移 | 15% | High |

---

## 🔗 情境組合機制 / Scenario Combination Mechanism

> **🆕 v0.09+**: 現實專案常需要同時或交替使用多個情境。以下定義標準的組合模式。

### 組合原則

1. **主情境 + 輔情境** — 以一個情境為主線，另一個按需穿插
2. **Agent 疊加** — 組合情境時，兩個情境的 Agent 同時可用
3. **Workflow 合併** — 兩個情境的 Workflow 取聯集
4. **SOP 順序** — 主情境 SOP 為主線，輔情境 SOP 在特定階段穿插

### 常用組合模式

| 組合 | 主情境 | 輔情境 | 使用時機 | 範例 |
|------|--------|--------|---------|------|
| **遷移+新功能** | Migration | Greenfield | 技術棧遷移同時新增功能（如行動端） | 經銷存系統遷移+新增掃碼 |
| **重構+遷移** | Refactoring | Migration | 代碼改善過程中發現需技術棧替換 | 重構時決定換 DB |
| **重構+新功能** | Refactoring | Greenfield | 重構同時需要加新功能 | 改善架構+新增報表 |
| **新專案+整合** | Greenfield | Integration | 新專案需大量第三方整合 | 電商+支付+物流整合 |
| **維護+效能** | Brownfield | Performance | 既有系統改版+效能優化 | 加功能+優化查詢 |

### 組合執行方法

**方法 1：Sequential（順序執行）**
```
主情境 SOP Stage 1-N → 切換 → 輔情境 SOP Stage 1-M
```
適用：兩個情境較獨立，前後有明確分界點

**方法 2：Interleaved（交錯執行）**
```
主情境 Stage 1-3 → 穿插輔情境 Stage → 繼續主情境 Stage 4-N
```
適用：輔情境僅在某些階段需要

**方法 3：Parallel（平行執行）**
```
主情境 Stage 1-N ∥ 輔情境 Stage 1-M（共用相同 Agent Pool）
```
適用：兩個情境可同時進行且不互相衝突

### 組合範例：Migration + Greenfield（遷移+新增行動端）

```
Phase 1: Migration Stage 1-2 (現況分析 + 遷移設計)
    ↓
Phase 2: Migration Stage 3 (DB 遷移) ∥ Greenfield Stage 1-2 (行動端需求分析)
    ↓
Phase 3: Migration Stage 4-5 (後端+前端遷移) ∥ Greenfield Stage 3-5 (行動端設計+開發)
    ↓
Phase 4: 整合驗證 (Migration Stage 7 + Greenfield Testing)
    ↓
Phase 5: 統一部署 (DevOps)
```

**Agent 配置**：Migration Agents ∪ Greenfield Agents（取聯集）

---

## 🎯 常見情境組合流程 / Common Scenario Combinations

### 1️⃣ 全新專案完整流程 (Greenfield Full Stack)

```
階段 1: Greenfield (需求分析 + 設計)
   ↓
   📄 輸出: PRD, FRD, SRD, 架構設計
   ↓
階段 2: Integration (第三方服務整合,如適用)
   ↓
   📄 輸出: API 研究報告, 整合設計, 資料映射規格
   ↓
階段 3: Security (安全設計與審查)
   ↓
   📄 輸出: 安全架構, 威脅建模, 安全需求文件
   ↓
階段 4: Testing (測試策略 + 測試執行)
   ↓
   📄 輸出: 測試計畫, 測試案例, 測試報告
   ↓
階段 5: Performance (效能測試 + 優化,如需要)
   ↓
   📄 輸出: 效能基準報告, 優化報告
   ↓
階段 6: DevOps (CI/CD + 部署)
   ↓
   📄 輸出: Pipeline 配置, 部署腳本, 監控設定
   ↓
階段 7: Documentation (最終文檔整理)
   ↓
   📄 輸出: API 文檔, 使用者指南, 維護手冊
```

**總時程估算**: 4-12 週（依專案規模）

**關鍵銜接點**:
- ✅ Greenfield → Integration: PRD/FRD 需明確列出第三方服務需求
- ✅ Integration → Security: API 整合設計需包含安全考量
- ✅ Security → Testing: 安全需求需納入測試範圍
- ✅ Testing → Performance: 測試通過後才進行效能優化
- ✅ Performance → DevOps: 效能指標需納入監控

---

### 2️⃣ 既有系統改造流程 (Brownfield Improvement)

```
階段 1: Brownfield (系統分析 + 影響評估)
   ↓
   📄 輸出: 系統現況分析, 影響分析報告, 改進方案
   ↓
   分支決策點 🔴
   ├─ 代碼品質差? → Refactoring
   ├─ 效能問題? → Performance
   ├─ 新增功能? → Integration (如有第三方服務)
   └─ 安全漏洞? → Security
   ↓
階段 2a: Refactoring (重構)
   ↓
   📄 輸出: 重構計畫, 代碼品質分析報告, 重構成果
   ↓
階段 3: Testing (回歸測試 + 整合測試)
   ↓
   📄 輸出: 測試計畫, 回歸測試報告
   ↓
階段 4: DevOps (更新部署流程)
   ↓
   📄 輸出: 更新後的 Pipeline, 部署腳本
   ↓
階段 5: Documentation (更新技術文檔)
   ↓
   📄 輸出: 更新的系統文檔, 變更記錄
```

**總時程估算**: 2-8 週（依改造範圍）

**關鍵銜接點**:
- ✅ Brownfield → Refactoring: 代碼品質分析需明確指出重構範圍
- ✅ Refactoring → Testing: 重構後必須進行完整回歸測試
- ✅ Testing → DevOps: 測試通過後才能部署

---

### 3️⃣ 效能優化流程 (Performance Optimization)

```
階段 1: Performance (效能分析)
   ↓
   📄 輸出: 效能基準報告, 瓶頸分析報告
   ↓
   分支決策點 🔴
   ├─ 架構問題? → Refactoring
   ├─ 資料庫問題? → Brownfield (資料庫優化)
   └─ 演算法問題? → Refactoring (演算法重構)
   ↓
階段 2: Refactoring (效能重構)
   ↓
   📄 輸出: 重構計畫, 優化後代碼
   ↓
階段 3: Testing (效能測試驗證)
   ↓
   📄 輸出: 效能測試報告, 壓力測試報告
   ↓
階段 4: DevOps (部署 + 監控)
   ↓
   📄 輸出: 效能監控配置, 告警設定
```

**總時程估算**: 1-4 週（依優化複雜度）

---

### 4️⃣ 安全審查流程 (Security Review)

```
階段 1: Security (安全評估)
   ↓
   📄 輸出: 安全評估報告, 威脅建模, 漏洞清單
   ↓
   發現問題? 🔴
   ├─ 架構問題? → Greenfield/Brownfield (重新設計)
   ├─ 代碼問題? → Refactoring (安全重構)
   └─ 配置問題? → DevOps (配置修復)
   ↓
階段 2: Testing (安全測試)
   ↓
   📄 輸出: 安全測試報告, 滲透測試報告
   ↓
階段 3: Documentation (安全文檔)
   ↓
   📄 輸出: 安全架構文檔, 合規文檔
```

**總時程估算**: 1-3 週（依安全要求）

---

### 5️⃣ 快速整合流程 (Quick Integration)

```
階段 1: Integration (API 研究 + 整合設計)
   ↓
   📄 輸出: API 研究報告, 整合設計, 資料映射
   ↓
階段 2: Security (API 安全審查)
   ↓
   📄 輸出: API 安全評估, 資料隱私審查
   ↓
階段 3: Testing (整合測試)
   ↓
   📄 輸出: 整合測試報告
   ↓
階段 4: Documentation (API 文檔)
   ↓
   📄 輸出: API 整合文檔, 錯誤處理指南
```

**總時程估算**: 1-2 週（依整合複雜度）

---

## 📄 情境轉換文檔傳遞規範 / Document Handover Standards

### 從 Greenfield 轉換

| 轉換到 | 必須傳遞的文檔 | 選擇性文檔 |
|-------|--------------|-----------|
| Integration | PRD, FRD (第三方服務需求章節) | SRD (架構設計) |
| Testing | PRD, FRD, SRD, AC (Acceptance Criteria) | 架構圖, API 規格 |
| Security | PRD (安全需求), SRD (架構設計), 資料模型 | FRD |
| Performance | SRD, 架構圖, 效能需求 | PRD |
| DevOps | SRD, 架構圖, 基礎設施需求 | PRD, FRD |
| Documentation | 所有已產出的文檔 (PRD/FRD/SRD/API) | - |

### 從 Brownfield 轉換

| 轉換到 | 必須傳遞的文檔 | 選擇性文檔 |
|-------|--------------|-----------|
| Refactoring | 系統現況分析, 代碼品質報告, 重構範圍 | 影響分析報告 |
| Testing | 影響分析報告, 變更範圍, AC | 代碼品質報告 |
| Performance | 系統現況分析, 效能問題描述 | 監控數據 |
| Security | 系統現況分析, 已知安全問題 | 代碼審查報告 |
| DevOps | 現有部署流程, 變更範圍 | 系統架構圖 |
| Documentation | 變更記錄, 影響範圍 | 所有分析報告 |

### 從 Refactoring 轉換

| 轉換到 | 必須傳遞的文檔 | 選擇性文檔 |
|-------|--------------|-----------|
| Testing | 重構計畫, 變更範圍, 回歸測試需求 | 代碼品質改善報告 |
| Performance | 重構前後效能對比 | 架構變更說明 |
| Documentation | 重構成果報告, 架構變更 | 設計決策記錄 (ADR) |

### 從 Integration 轉換

| 轉換到 | 必須傳遞的文檔 | 選擇性文檔 |
|-------|--------------|-----------|
| Testing | API 研究報告, 整合設計, 測試案例需求 | 資料映射規格 |
| Security | API 規格, 資料傳輸設計, 認證機制 | 第三方服務安全文檔 |
| Documentation | API 整合設計, 錯誤處理指南 | API 研究報告 |

### 從 Performance 轉換

| 轉換到 | 必須傳遞的文檔 | 選擇性文檔 |
|-------|--------------|-----------|
| Testing | 效能基準報告, 優化後目標, 測試需求 | 瓶頸分析報告 |
| Refactoring | 瓶頸分析報告, 優化建議 | 效能監控數據 |

### 從 Security 轉換

| 轉換到 | 必須傳遞的文檔 | 選擇性文檔 |
|-------|--------------|-----------|
| Testing | 安全需求清單, 安全測試案例 | 威脅建模 |
| Documentation | 安全架構, 合規文檔 | 安全測試報告 |

### 從 Testing 轉換

| 轉換到 | 必須傳遞的文檔 | 選擇性文檔 |
|-------|--------------|-----------|
| DevOps | 測試報告, 測試通過證明, 部署檢查清單 | 測試案例 |
| Documentation | 測試報告, 已知問題清單 | 測試案例 |

### 從 DevOps 轉換

| 轉換到 | 必須傳遞的文檔 | 選擇性文檔 |
|-------|--------------|-----------|
| Documentation | Pipeline 配置, 部署腳本, 監控設定 | 部署記錄 |

---

## 🔄 情境轉換操作步驟 / Transition Procedures

### 標準轉換流程 (5 步驟)

#### 步驟 1️⃣: 確認轉換時機 🔴 (人機協作點)

**觸發條件檢查清單**:
- [ ] 當前情境的關鍵產出文件已完成
- [ ] 已通過品質檢查 (QA Gate)
- [ ] 利害關係人已確認當前階段成果
- [ ] 已識別出下一個情境的需求

**範例對話**:
```
使用者: 「我們已經完成 Greenfield 的 PRD/FRD/SRD,現在需要整合 Stripe 支付」
AI: 「✅ 識別轉換需求: Greenfield → Integration
     ✅ 檢查必要文檔: PRD ✓, FRD ✓, SRD ✓
     ✅ 建議載入: Integration 情境
     請確認是否進行轉換?」
使用者: 「確認」🔴
```

---

#### 步驟 2️⃣: 準備文檔傳遞包 (Document Handover Package)

**自動化檢查**:
AI 系統會自動檢查並列出需要傳遞的文檔

**範例輸出**:
```
📦 文檔傳遞包 (Greenfield → Integration)

必須文檔 ✅:
- [x] PRD (v1.2) - 包含支付功能需求
- [x] FRD (v1.1) - 第 4.2 節「支付流程」
- [ ] ⚠️ 缺少: 第三方服務需求規格

選擇性文檔:
- [x] SRD (v1.0) - 架構設計
- [ ] API 規格 (尚未產出)

🔴 人機協作: 是否補充缺少的文檔?
```

---

#### 步驟 3️⃣: 執行情境切換

**操作指令**:
```
使用者: 「請切換到 Integration 情境,整合 Stripe 支付」

AI 執行:
1. ✅ 載入 Integration SOP
2. ✅ 載入相關 Agents (integration-specialist, sd, qa, dev)
3. ✅ 載入文檔傳遞包 (PRD, FRD, SRD)
4. ✅ 設定情境上下文 (從 Greenfield 轉換)

AI 回應:
「✅ 已切換到 Integration 情境
 ✅ 已載入文檔: PRD (v1.2), FRD (v1.1), SRD (v1.0)
 ✅ 主導 Agent: Integration Specialist
 ✅ 支援 Agents: SD, QA, Dev

 接下來我們將進行:
 階段 1: API 研究與分析 (Stripe Payment API)
 階段 2: 整合設計
 階段 3: 資料映射設計

 準備好開始了嗎?」🔴
```

---

#### 步驟 4️⃣: 情境銜接驗證 (Transition Validation)

**自動驗證檢查清單**:
- [ ] 文檔完整性驗證
- [ ] Agent 角色正確載入
- [ ] 情境上下文正確設定
- [ ] 前一情境的輸出與當前情境的輸入匹配

**範例驗證輸出**:
```
🔍 情境銜接驗證

文檔檢查:
✅ PRD 包含支付功能需求 (第 3.4 節)
✅ FRD 包含支付流程設計 (第 4.2 節)
✅ SRD 包含系統架構圖
⚠️ 注意: 尚未定義具體的支付閘道選擇

Agent 檢查:
✅ Integration Specialist (Primary) - 已載入
✅ SD Architect (Supporting) - 已載入
✅ QA Tester (Supporting) - 已載入
✅ Dev Developer (Supporting) - 已載入

情境上下文:
✅ 前一情境: Greenfield
✅ 當前情境: Integration
✅ 預期下一情境: Testing (整合測試)

🔴 驗證通過,可以開始 Integration 流程
```

---

#### 步驟 5️⃣: 開始新情境流程

**自動進入新情境的起始步驟**:

```
🎯 Integration 情境 - 階段 1: API 研究與分析

步驟 1.1: 確認整合需求
根據 PRD/FRD 分析:
- 整合目標: Stripe 支付閘道
- 需要功能: 信用卡支付, 訂閱管理, Webhook 處理
- 資料流向: 前端 → 後端 → Stripe → Webhook → 後端

步驟 1.2: API 研究
Integration Specialist 將協助你研究 Stripe API:
- Stripe API 文檔分析
- SDK 選擇建議
- 認證機制研究
- 費率結構分析

🔴 準備好開始 API 研究了嗎?
```

---

## 🚨 情境轉換常見問題與解決方案 / Common Issues & Solutions

### 問題 1: 文檔不完整導致轉換失敗

**症狀**:
```
❌ 轉換失敗: Greenfield → Integration
原因: FRD 缺少第三方服務需求章節
```

**解決方案**:
```
選項 A: 回到 Greenfield 補充缺失內容
選項 B: 在 Integration 階段補充需求 (不建議,會增加返工)
選項 C: 使用現有文檔繼續,並在過程中補充 (風險較高)

🔴 建議: 選擇 A,回到 Greenfield 補充
```

---

### 問題 2: 多情境並行衝突

**症狀**:
```
⚠️ 並行衝突警告:
當前情境: Integration (進行中)
新請求: Performance (效能優化)

衝突原因: Integration 尚未完成,效能基準線不明確
```

**解決方案**:
```
建議處理順序:
1. 完成 Integration (預計 1 週)
2. 進行 Testing (整合測試, 預計 3 天)
3. 建立效能基準線
4. 開始 Performance 優化

🔴 是否接受此建議?或選擇強制並行?(不建議)
```

---

### 問題 3: 循環轉換檢測

**症狀**:
```
🔄 循環轉換警告:
路徑: Brownfield → Refactoring → Testing → Brownfield → Refactoring
次數: 已循環 2 次

可能原因: 重構範圍定義不清,測試持續失敗
```

**解決方案**:
```
建議中斷循環:
1. 召開回顧會議 🔴
2. 重新評估重構策略
3. 縮小重構範圍,分階段進行
4. 加強測試覆蓋率

是否暫停當前流程,進行回顧?
```

---

### 問題 4: 關鍵文檔遺失

**症狀**:
```
❌ 關鍵文檔遺失:
當前情境: Testing
缺少: AC (Acceptance Criteria)

影響: 無法定義測試通過標準
```

**解決方案**:
```
緊急補救措施:
1. 從 FRD 提取功能需求
2. 自動生成 AC 草稿 (需人工審查 🔴)
3. 與利害關係人確認 AC
4. 更新文檔傳遞包

🔴 是否執行緊急補救?預計耗時 2-4 小時
```

---

## 📊 情境轉換時程規劃建議 / Timing Guidelines

### 小型專案 (1-3 人, 1-3 個月)

| 情境組合 | 總時程 | 建議分配 |
|---------|--------|---------|
| Greenfield → Testing → DevOps | 4-6 週 | 3週 + 1週 + 1週 |
| Brownfield → Testing → DevOps | 2-4 週 | 1.5週 + 0.5週 + 1週 |
| Integration → Testing → DevOps | 1-2 週 | 0.5週 + 0.5週 + 0.5週 |

### 中型專案 (4-10 人, 3-12 個月)

| 情境組合 | 總時程 | 建議分配 |
|---------|--------|---------|
| Greenfield → Integration → Security → Testing → Performance → DevOps | 10-16 週 | 4週 + 2週 + 2週 + 2週 + 2週 + 2週 |
| Brownfield → Refactoring → Testing → DevOps | 6-10 週 | 3週 + 3週 + 2週 + 2週 |

### 大型專案 (10+ 人, 12+ 個月)

| 情境組合 | 總時程 | 建議分配 |
|---------|--------|---------|
| 完整流程 (全部情境) | 20-32 週 | 依模組並行處理 |

**並行處理建議**:
- Documentation 可與其他情境並行
- Security Review 可分階段穿插
- Performance Optimization 可獨立模組並行

---

## 🎓 情境轉換最佳實踐 / Best Practices

### ✅ 推薦做法 (DOs)

1. **✅ 始終完成當前情境再轉換**
   - 確保所有產出文件完整
   - 通過品質檢查 (QA Gate)
   - 利害關係人確認

2. **✅ 明確定義轉換觸發條件**
   - 在專案開始時規劃情境路徑
   - 設定清楚的里程碑 (Milestone)
   - 預留緩衝時間

3. **✅ 維護完整的文檔傳遞包**
   - 使用標準化的文檔模板
   - 建立文檔版本控制
   - 保持文檔追蹤記錄

4. **✅ 進行轉換驗證**
   - 使用自動化檢查清單
   - 確認 Agent 配置正確
   - 驗證情境上下文

5. **✅ 記錄轉換決策**
   - 為什麼選擇此轉換路徑
   - 跳過哪些情境及原因
   - 學習與改進建議

---

### ❌ 避免做法 (DON'Ts)

1. **❌ 不要過早轉換**
   - 當前情境未完成就轉換
   - 文檔不完整就轉換
   - 缺少利害關係人確認

2. **❌ 不要跳過關鍵情境**
   - 跳過 Testing (永遠不要!)
   - 跳過 Security (高風險系統)
   - 跳過 Documentation (長期維護困難)

3. **❌ 不要過度並行**
   - 同時進行 3+ 個情境
   - 資源不足卻強行並行
   - 缺乏並行協調機制

4. **❌ 不要忽略循環警告**
   - 陷入 Refactoring → Testing 循環
   - 重複 Performance → Testing 循環
   - 未解決根本問題就繼續

5. **❌ 不要缺少回顧與調整**
   - 未總結轉換經驗
   - 未調整後續轉換策略
   - 未更新文檔傳遞規範

---

## 📚 相關文檔 / Related Documents

- [SCENARIO_AGENT_MAPPING.md](SCENARIO_AGENT_MAPPING.md) - 情境 Agent 配置映射表
- [各情境 SOP](.) - 九大情境的詳細流程
- [AISDLC_INIT.md](../AISDLC_INIT.md) - AISDLC 框架初始化
- [EXECUTION_CHECKLIST.md](../EXECUTION_CHECKLIST.md) - 執行檢查清單

---

## 🔄 文檔維護記錄 / Document History

| 版本 | 日期 | 變更內容 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-11-10 | 初版建立,定義九大情境轉換關係 | Claude Code |

---

**維護者 / Maintainer**: AISDLC Framework Team
**最後更新 / Last Updated**: 2025-11-10
**文檔狀態 / Status**: ✅ Active
