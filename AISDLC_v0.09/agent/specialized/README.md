# AISDLC Specialized Agents (專業化 Agent)

本目錄包含 AISDLC 框架的 14 個專業化 Agent 配置檔案，提供特定領域的專業能力。

## 📋 維護策略（2025-10-30 更新）

### ✅ 中文版狀態

**所有 14 個 Specialized Agents 均已完成中文化！**

- **主要維護版本**：中文版 (`{agent-name}-zh.yaml`) - 位於本目錄
- **歷史參考版本**：英文版 (`{agent-name}.yaml`) - **已歸檔至 `archive_en/`**
- **歸檔日期**：2025-10-30
- **版本**：v0.06

**中文化完成度：100% (14/14)** ✅

### 📦 英文版歸檔

- **歸檔位置**：[archive_en/](archive_en/) 目錄
- **狀態**：靜態備份，不再主動更新
- **用途**：術語對照、歷史參考、英文文檔撰寫
- **說明文檔**：[archive_en/README.md](archive_en/README.md)

---

## 🎯 Specialized Agents 清單

### 1. QA 專業化 Agent (4 個)

| # | Agent ID | 英文版 | 中文版 | 角色名稱 | 專業領域 |
|---|----------|--------|--------|----------|----------|
| 1 | qa-automation | ✅ | ✅ | AutoQA | 測試自動化、框架開發、E2E 測試 |
| 2 | qa-lead | ✅ | ✅ | TestLead | 測試策略、品質度量、團隊管理 |
| 3 | qa-web-tester | ✅ | ✅ | WebQA | Web 應用測試、瀏覽器相容性 |
| 4 | qa-mobile-tester | ✅ | ✅ | MobileQA | Mobile 應用測試、iOS/Android |

**使用場景：** Testing, Greenfield, Brownfield
**協作模式：** Lead-Support, Sequential-Handoff, Parallel-Convergence

---

### 2. 開發專業化 Agent (2 個)

| # | Agent ID | 英文版 | 中文版 | 角色名稱 | 專業領域 |
|---|----------|--------|--------|----------|----------|
| 5 | dev-senior | ✅ | ✅ | TechLead | 技術決策、代碼審查、架構指導 |
| 6 | code-analyzer | ✅ | ✅ | CodeX | 代碼分析、重構規劃、技術債務 |

**使用場景：** Greenfield, Brownfield, Refactoring
**協作模式：** Lead-Support, Peer-Review, Iterative-Refinement

---

### 3. 架構專業化 Agent (2 個)

| # | Agent ID | 英文版 | 中文版 | 角色名稱 | 專業領域 |
|---|----------|--------|--------|----------|----------|
| 7 | sd-web-architect | ✅ | ✅ | WebArch | Web 應用架構、前端/後端設計 |
| 8 | sd-mobile-architect | ✅ | ✅ | MobileArch | Mobile 應用架構、原生/跨平台 |

**使用場景：** Greenfield, Brownfield, Performance
**協作模式：** Lead-Support, Sequential-Handoff

---

### 4. 運維與安全 Agent (2 個)

| # | Agent ID | 英文版 | 中文版 | 角色名稱 | 專業領域 |
|---|----------|--------|--------|----------|----------|
| 9 | devops-engineer | ✅ | ✅ | DevOpsX | CI/CD、容器化、IaC、監控 |
| 10 | security-engineer | ✅ | ✅ | SecOps | 安全架構、威脅建模、漏洞評估 |

**使用場景：** DevOps, Security, Greenfield, Brownfield
**協作模式：** Lead-Support, Peer-Review

---

### 5. 其他專業化 Agent (4 個)

| # | Agent ID | 英文版 | 中文版 | 角色名稱 | 專業領域 |
|---|----------|--------|--------|----------|----------|
| 11 | performance-engineer | ✅ | ✅ | PerfX | 效能分析、優化、負載測試 |
| 12 | integration-specialist | ✅ | ✅ | IntegX | API 整合、第三方服務、資料轉換 |
| 13 | technical-writer | ✅ | ✅ | DocX | 技術文檔、API 文檔、開發者指南 |
| 14 | compliance-officer | ✅ | ✅ | ComplianceX | 法規遵循、GDPR、HIPAA、PCI-DSS |

**使用場景：** Performance, Integration, Documentation, Security
**協作模式：** Lead-Support, Sequential-Handoff, Iterative-Refinement

---

## 📖 使用指南

### 選擇合適的 Specialized Agent

根據專案需求和情境選擇：

#### 新專案開發 (Greenfield)
- **必選**：dev-senior
- **推薦**：qa-automation, devops-engineer, security-engineer
- **平台相關**：sd-web-architect 或 sd-mobile-architect

#### 舊專案改造 (Brownfield)
- **必選**：code-analyzer, dev-senior
- **推薦**：qa-lead, security-engineer

#### 重構專案 (Refactoring)
- **必選**：code-analyzer, dev-senior
- **推薦**：qa-automation

#### 測試情境 (Testing)
- **必選**：qa-lead, qa-automation
- **平台相關**：qa-web-tester 或 qa-mobile-tester

#### 安全情境 (Security)
- **必選**：security-engineer
- **推薦**：compliance-officer (如有合規需求)

#### 效能優化 (Performance)
- **必選**：performance-engineer
- **推薦**：相關平台架構師

#### 整合情境 (Integration)
- **必選**：integration-specialist
- **推薦**：security-engineer (API 安全)

#### DevOps 情境
- **必選**：devops-engineer
- **推薦**：qa-automation (CI/CD 測試整合)

#### 文檔情境 (Documentation)
- **必選**：technical-writer
- **推薦**：相關領域專家協作

---

## 🔧 Agent 配置結構

每個 Specialized Agent 包含：

### 1. 基本資訊
```yaml
agent:
  name: "[Agent 名稱]"
  id: "[agent-id]"
  title: "[職位頭銜]"
  icon: "[圖示]"
  whenToUse: "[使用時機]"
```

### 2. Phase 2: 協作模式與情境使用
```yaml
collaboration_patterns:
  primary_patterns: [主要協作模式]
  supporting_patterns: [支援協作模式]

scenario_usage:
  frequency: [使用頻率]
  irreplaceability: [不可替代性評級]
  primary_scenarios: [主要情境]
  supporting_scenarios: [支援情境]
  notes: [說明]
```

### 3. 人格設定
```yaml
persona:
  role: [角色定位]
  style: [工作風格]
  identity: [身份認同]
  focus: [工作重點]
  core_principles: [核心原則]
```

### 4. 專業能力
- `specialized_capabilities`: 專業能力清單
- `tool_expertise`: 工具專業知識
- `best_practices`: 最佳實踐
- `common_patterns`: 常用模式

### 5. 文檔責任與協作規則
- `document_responsibilities`: 文檔責任
- `collaboration_rules`: 協作規則
- `dependencies`: 依賴項目

---

## 🌐 中文版與英文版

### 使用建議

- **中文團隊**：優先使用 `-zh.yaml` 中文版
- **英文參考**：需要術語對照時參考英文版
- **新增 Agent**：建議直接建立中文版

### 檔案命名規則

- **中文版**：`{agent-name}-zh.yaml`
- **英文版**：`{agent-name}.yaml`

### 翻譯策略

**保留英文的部分：**
- Agent ID
- 技術術語（CI/CD, API, REST, GraphQL, Docker, Kubernetes, etc.）
- 工具名稱（Selenium, JMeter, SonarQube, etc.）
- 法規名稱（GDPR, HIPAA, PCI-DSS, SOC 2, etc.）
- Workflow/Pattern/Scenario 名稱

**完全中文化的部分：**
- 標題和註釋
- 描述性文字
- 職責說明
- 核心原則
- 最佳實踐說明

---

## 📊 統計資訊

| 類別 | 數量 | 中文化完成 | 完成率 |
|------|------|----------|--------|
| QA 專業化 | 4 | 4 | 100% |
| 開發專業化 | 2 | 2 | 100% |
| 架構專業化 | 2 | 2 | 100% |
| 運維與安全 | 2 | 2 | 100% |
| 其他專業化 | 4 | 4 | 100% |
| **總計** | **14** | **14** | **100%** |

**總檔案數：** 28 個（14 個英文版 + 14 個中文版）

**總代碼行數：**
- 英文版：約 6,683 行
- 中文版：約 6,700 行

---

## 🔄 與 Core Agents 的關係

### 繼承關係

Specialized Agents 通常繼承自 Core Agents：

- `qa-automation`, `qa-lead`, `qa-web-tester`, `qa-mobile-tester` → 繼承自 `qa-tester`
- `dev-senior` → 繼承自 `dev-developer`
- `sd-web-architect`, `sd-mobile-architect` → 繼承自 `sd-architect`
- `code-analyzer` → 獨立專業化

### 協作關係

Specialized Agents 與 Core Agents 協作模式：

- **Sequential-Handoff**：Core Agent → Specialized Agent → Core Agent
- **Parallel-Convergence**：Core Agent 與 Specialized Agent 並行工作後匯合
- **Lead-Support**：Core Agent 主導，Specialized Agent 提供專業支援
- **Peer-Review**：相互審查和驗證

---

## 📝 最佳實踐

### 1. 選擇原則
- 根據專案特性和情境需求選擇
- 避免過度使用（通常 2-3 個 Specialized Agents 即可）
- 優先選擇高不可替代性（⭐⭐⭐⭐⭐）的 Agent

### 2. 整合方式
- 在 workflow 中明確定義 Specialized Agent 的參與時機
- 確保與 Core Agents 的協作界面清晰
- 維護文檔追蹤鏈的完整性

### 3. 客製化建議
- 可基於現有 Specialized Agent 建立專案特定版本
- 保持 Phase 2 內容的完整性
- 遵循 AISDLC 文檔標準

---

## 🆕 新增 Specialized Agent

如需建立新的 Specialized Agent：

1. 確認是否有類似的現有 Agent 可繼承
2. 使用 Core Agent 對應的中文版模板
3. 定義清楚的專業化領域和使用場景
4. 包含完整的 Phase 2 內容
5. 建立 `-zh.yaml` 中文版
6. 更新本 README

---

## 📞 相關資源

- **Core Agents**：[../core/README.md](../core/README.md)
- **Agent 協作模式**：[../AGENT_COLLABORATION_PATTERNS.md](../AGENT_COLLABORATION_PATTERNS.md)
- **Phase 2 更新指南**：[../AGENT_PHASE2_UPDATE_GUIDE.md](../AGENT_PHASE2_UPDATE_GUIDE.md)
- **框架說明**：[../../CLAUDE.md](../../CLAUDE.md)

---

**維護者**：AISDLC Framework Team
**最後更新**：2025-10-30
**版本**：v0.06
**中文化完成日期**：2025-10-30
