# AISDLC v0.02 情境快速查找表
# Scenario Quick Reference Card

**版本**: v0.02
**用途**: 快速選擇正確的情境與指令
**最後更新**: 2025-10-22

---

## 🎯 我該選擇哪個情境？

### 快速決策樹

```
┌─ 我的任務是什麼？
│
├─ 📦 開發全新專案
│  └─ 選擇：Greenfield
│     啟動指令：請使用 Greenfield 情境協助我開發新專案
│
├─ 🔧 修改既有系統
│  └─ 選擇：Brownfield
│     啟動指令：請使用 Brownfield 情境協助我修改既有系統
│
├─ ♻️ 重構既有代碼
│  └─ 選擇：Refactoring
│     啟動指令：請使用 Refactoring 情境協助我重構代碼
│
├─ ⚡ 優化系統效能
│  └─ 選擇：Performance
│     啟動指令：請使用 Performance 情境協助我優化效能
│
├─ 🔌 整合第三方服務
│  └─ 選擇：Integration
│     啟動指令：請使用 Integration 情境協助我整合第三方 API
│
├─ 🚀 建立 CI/CD 流程
│  └─ 選擇：DevOps
│     啟動指令：請使用 DevOps 情境協助我建立 CI/CD
│
├─ ✅ 建立測試策略
│  └─ 選擇：Testing
│     啟動指令：請使用 Testing 情境協助我建立測試策略
│
└─ 📚 撰寫技術文檔
   └─ 選擇：Documentation
      啟動指令：請使用 Documentation 情境協助我撰寫文檔
```

---

## 📋 情境對照表

| 情境 | 適用場景 | 主要 Agents | 核心產出 | 預估時間 | 快速啟動指令 |
|------|---------|------------|---------|---------|-------------|
| **Greenfield** | 全新專案開發 | PM/PO, SA, SD | PRD, FRD, SRD, API Specs | 3-5 天 | [連結](#greenfield-全新專案開發) |
| **Brownfield** | 既有系統修改 | SA, CodeX, Senior Dev | Code Analysis, FRD, SRD | 2-4 天 | [連結](#brownfield-既有系統維護) |
| **Refactoring** | 代碼重構 | CodeX, Senior Dev, SD | Code Analysis, Refactoring Plan | 1-3 天 | [連結](#refactoring-代碼重構) |
| **Performance** | 效能優化 | Perf, SD, DevOps | Performance Report, Optimization Strategy | 1-2 天 | [連結](#performance-效能優化) |
| **Integration** | 第三方整合 | IntegX, SD, QA | API Analysis, Integration Design | 1-2 天 | [連結](#integration-第三方整合) |
| **DevOps** | CI/CD 自動化 | DevOps, SD, QA | Pipeline Design, Monitoring Setup | 2-3 天 | [連結](#devops-cicd-自動化) |
| **Testing** | 測試策略 | QA-Lead, QA-Auto | Test Strategy, Test Plan | 1-2 天 | [連結](#testing-測試策略) |
| **Documentation** | 技術文檔 | DocX, SA, SD | Documentation Architecture, Docs | 1-2 天 | [連結](#documentation-技術文檔) |

---

## 🚀 各情境快速啟動指令

### Greenfield（全新專案開發）

```
我想開發一個全新專案。

專案資訊：
- 專案名稱：[填寫]
- 業務目標：[填寫]
- 平台：[Web/iOS/Android/Mobile]

請使用 Greenfield 情境，載入 AISDLC_INIT.md，執行 greenfield-complete-flow。
```

**相關資源**：
- [Greenfield SOP](../../scenarios/greenfield/SOP.md)
- [Greenfield Prompts](../scenario-prompts/greenfield-prompts.md)

---

### Brownfield（既有系統維護）

```
我需要修改既有系統。

系統資訊：
- 系統名稱：[填寫]
- 技術棧：[填寫]
- 修改需求：[填寫]

請使用 Brownfield 情境，載入 AISDLC_INIT.md，執行 brownfield-analysis-flow。
```

**相關資源**：
- [Brownfield SOP](../../scenarios/brownfield/SOP.md)
- [Brownfield Prompts](../scenario-prompts/brownfield-prompts.md)

---

### Refactoring（代碼重構）

```
我需要重構代碼，改善品質。

重構資訊：
- 重構範圍：[模組/檔案]
- 重構原因：[技術債務/可維護性/效能]

請使用 Refactoring 情境，載入 AISDLC_INIT.md，執行 refactoring-planning-flow。
```

**相關資源**：
- [Refactoring SOP](../../scenarios/refactoring/SOP.md)
- [Refactoring Prompts](../scenario-prompts/refactoring-prompts.md)

---

### Performance（效能優化）

```
我需要優化系統效能。

效能問題：
- 問題描述：[慢/高延遲/記憶體洩漏]
- 目前指標：[X ms]
- 目標指標：[Y ms]

請使用 Performance 情境，載入 AISDLC_INIT.md，執行 performance-optimization-flow。
```

**相關資源**：
- [Performance SOP](../../scenarios/performance/SOP.md)
- [Performance Prompts](../scenario-prompts/performance-prompts.md)

---

### Integration（第三方整合）

```
我需要整合第三方 API/服務。

整合資訊：
- 第三方服務：[名稱]
- 整合目的：[支付/簡訊/地圖]
- API 文檔：[連結]

請使用 Integration 情境，載入 AISDLC_INIT.md，執行 integration-analysis-flow。
```

**相關資源**：
- [Integration SOP](../../scenarios/integration/SOP.md)
- [Integration Prompts](../scenario-prompts/integration-prompts.md)

---

### DevOps（CI/CD 自動化）

```
我需要建立 CI/CD Pipeline。

專案資訊：
- 專案類型：[Web/Mobile/Backend]
- CI 工具：[GitHub Actions/GitLab CI/Jenkins]
- 部署平台：[AWS/GCP/Azure]

請使用 DevOps 情境，載入 AISDLC_INIT.md，執行 devops-setup-flow。
```

**相關資源**：
- [DevOps SOP](../../scenarios/devops/SOP.md)
- [DevOps Prompts](../scenario-prompts/devops-prompts.md)

---

### Testing（測試策略）

```
我需要建立測試策略和自動化測試。

專案資訊：
- 專案類型：[Web/Mobile/Backend]
- 目前測試狀況：[無/部分/需改善]
- 測試目標：[提升覆蓋率/建立自動化]

請使用 Testing 情境，載入 AISDLC_INIT.md，執行 testing-strategy-flow。
```

**相關資源**：
- [Testing SOP](../../scenarios/testing/SOP.md)
- [Testing Prompts](../scenario-prompts/testing-prompts.md)

---

### Documentation（技術文檔）

```
我需要建立或改善技術文檔。

專案資訊：
- 專案名稱：[填寫]
- 目前文檔狀況：[無/部分/需改善]
- 文檔目標：[新建/補充/重構]

請使用 Documentation 情境，載入 AISDLC_INIT.md，執行 documentation-flow。
```

**相關資源**：
- [Documentation SOP](../../scenarios/documentation/SOP.md)
- [Documentation Prompts](../scenario-prompts/documentation-prompts.md)

---

## 🔀 組合情境使用

### 常見組合範例

#### 組合 1：Greenfield + Integration
```
我要開發新專案，需要整合支付 API。

請結合 Greenfield + Integration 情境：
1. 先執行 Greenfield 需求分析
2. 在技術設計階段加入 Integration 評估
3. 產出完整的整合方案
```

#### 組合 2：Brownfield + Performance
```
既有系統需要修改，同時優化效能。

請結合 Brownfield + Performance 情境：
1. 先分析既有系統
2. 識別效能瓶頸
3. 在修改時同步優化效能
```

#### 組合 3：Greenfield + DevOps + Testing
```
全新專案，從一開始就建立完整的 DevOps 和測試。

請結合 Greenfield + DevOps + Testing：
1. Greenfield 設計階段考慮 CI/CD
2. 規劃測試策略（測試金字塔）
3. 同步建立 Pipeline 和自動化測試
```

---

## 🆘 遇到問題？

### 不確定選哪個情境
```
我不確定應該選擇哪個情境。

我的情況：
[描述你的任務和目標]

請協助我選擇最適合的情境。
```

### 需要組合多個情境
```
我的任務可能需要多個情境。

任務描述：
[描述你的任務]

請協助我規劃如何組合情境。
```

### 想自訂流程
```
AISDLC 的標準流程不完全符合我的需求。

我需要：
[描述你的特殊需求]

請協助我自訂流程。
```

---

## 📚 延伸閱讀

- [AISDLC_INIT.md](../../AISDLC_INIT.md) - 框架初始化與詳細說明
- [SCENARIO_SELECTOR.md](../../SCENARIO_SELECTOR.md) - 情境選擇詳細指南
- [QUICK_START_GUIDE.md](../../QUICK_START_GUIDE.md) - 30 秒快速啟動
- [所有情境 Prompts](../scenario-prompts/) - 各情境詳細指令集

---

**版本**: v0.02
**維護者**: AISDLC Framework Team
**最後更新**: 2025-10-22
