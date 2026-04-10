# AISDLC Agent 配置目錄

本目錄包含 AISDLC 框架的所有 Agent 配置檔案。

## 📂 目錄結構

```
agent/
├── core/                          # 核心 Agent (中文版 - 主要維護)
│   ├── 01.agent-template-zh_OK.yaml
│   ├── 02.ba-business-analyst-zh.yaml
│   ├── 03.pm-po-agent-zh.yaml
│   ├── 04.sa-analyst-zh.yaml
│   ├── 05.sd-architect-zh.yaml
│   ├── 06.dev-developer-zh.yaml
│   ├── 07.qa-tester-zh.yaml
│   └── archive_en/                # 英文版備份 (僅供參考)
│       ├── README.md
│       └── [7 個英文版 Agent YAML]
├── specialized/                    # 專業化 Agent
│   ├── code-analyzer.yaml
│   ├── compliance-officer.yaml
│   ├── dev-senior.yaml
│   ├── devops-engineer.yaml
│   ├── integration-specialist.yaml
│   ├── performance-engineer.yaml
│   ├── qa-automation.yaml
│   ├── qa-lead.yaml
│   ├── qa-mobile-tester.yaml
│   ├── qa-web-tester.yaml
│   ├── sd-mobile-architect.yaml
│   ├── sd-web-architect.yaml
│   ├── security-engineer.yaml
│   └── technical-writer.yaml
├── AGENT_COLLABORATION_PATTERNS.md
├── AGENT_PHASE2_UPDATE_GUIDE.md
└── README.md (本檔案)
```

## 🎯 維護策略 (2025-10-30 更新)

### ✅ 主要維護版本：中文版

- **位置**：`core/*-zh.yaml`
- **狀態**：**主要維護版本，所有更新在此進行**
- **使用者**：所有開發團隊（主要為中文使用者）
- **更新頻率**：隨框架演進持續更新

### 📦 歷史參考版本：英文版

- **位置**：`core/archive_en/*.yaml`
- **狀態**：**靜態備份，不再主動更新**
- **用途**：
  - 術語對照參考
  - 國際化文檔撰寫
  - 英文培訓教材
  - 歷史版本查閱

## 📋 核心 Agent 清單 (Core Agents)

所有核心 Agent 均已包含 Phase 2 擴充內容 (v0.03-phase2)：

| # | Agent ID | 名稱 | 角色 | 情境使用頻率 | 不可替代性 |
|---|----------|------|------|------------|-----------|
| 1 | agent-template | Template | Agent 模板 | - | - |
| 2 | ba-business-analyst | Beatrice | 業務分析師 | Medium (3/9) | ⭐⭐⭐⭐⭐ |
| 3 | pm-po | Victoria | 產品經理/產品負責人 | Medium-High (6/9) | ⭐⭐⭐⭐ |
| 4 | sa-analyst | Amanda | 系統分析師 | High (9/9) | ⭐⭐⭐⭐⭐ |
| 5 | sd-architect | Marcus | 系統設計師/架構師 | High (8/9) | ⭐⭐⭐⭐⭐ |
| 6 | dev-developer | David | 軟體開發者 | Medium-High (6/9) | ⭐⭐⭐⭐⭐ |
| 7 | qa-tester | Quincy | 品質保證工程師 | High (7/9) | ⭐⭐⭐⭐⭐ |

## 🔧 專業化 Agent (Specialized Agents)

提供特定領域的專業能力，依需求選用：

### 開發專業化
- `dev-senior.yaml` - 資深開發者
- `code-analyzer.yaml` - 代碼分析師

### 架構專業化
- `sd-web-architect.yaml` - Web 架構師
- `sd-mobile-architect.yaml` - Mobile 架構師

### QA 專業化
- `qa-lead.yaml` - QA 主管
- `qa-automation.yaml` - 自動化測試工程師
- `qa-web-tester.yaml` - Web 測試工程師
- `qa-mobile-tester.yaml` - Mobile 測試工程師

### 其他專業化
- `devops-engineer.yaml` - DevOps 工程師
- `security-engineer.yaml` - 安全工程師
- `performance-engineer.yaml` - 效能工程師
- `integration-specialist.yaml` - 整合專家
- `technical-writer.yaml` - 技術文件撰寫員
- `compliance-officer.yaml` - 合規專員

## 📝 Agent 配置結構

每個 Agent YAML 檔案包含以下區塊：

### 1. 基本資訊 (agent)
- `name`: Agent 名稱
- `id`: 唯一識別 ID
- `title`: 職位頭銜
- `icon`: 代表圖示
- `whenToUse`: 使用時機
- `customization`: 客製化設定

### 2. Phase 2: 協作模式與情境使用 (v0.03-phase2)
- `collaboration_patterns`: 協作模式定義
  - `primary_patterns`: 主要協作模式
  - `supporting_patterns`: 支援性協作模式
- `scenario_usage`: 情境使用說明
  - `frequency`: 使用頻率
  - `irreplaceability`: 不可替代性評級
  - `primary_scenarios`: 主要情境
  - `supporting_scenarios`: 支援性情境
  - `notes`: 補充說明

### 3. 人格設定 (persona)
- `role`: 角色定位
- `style`: 工作風格
- `identity`: 身份認同
- `focus`: 工作重點
- `core_principles`: 核心原則

### 4. 文檔責任 (document_responsibilities)
- `primary_documents`: 主要負責文檔
- `collaborative_documents`: 協作參與文檔
- `quality_standards`: 品質標準

### 5. 支援的工作流程 (supported_workflows)
- `primary_workflows`: 主要工作流程
- `supporting_workflows`: 支援的工作流程

### 6. 協作規則 (collaboration_rules)
- `upstream_collaboration`: 上游協作
- `downstream_collaboration`: 下游協作
- `peer_collaboration`: 同級協作

### 7. 依賴項目 (dependencies)
- `data`: 數據文件
- `tasks`: 任務文件
- `templates`: 模板文件
- `checklists`: 檢查清單

## 🚀 使用指南

### 新增或修改 Agent

1. **使用模板**：複製 `01.agent-template-zh_OK.yaml` 作為起點
2. **填寫欄位**：根據註釋指導填寫所有必要欄位
3. **Phase 2 內容**：確保包含協作模式和情境使用定義
4. **測試整合**：驗證與 AISDLC 工作流程的整合
5. **文檔更新**：更新本 README 的 Agent 清單

### 引用 Agent

在工作流程或其他配置中引用 Agent：

```yaml
# 引用核心 Agent (中文版)
agent: "../agent/core/04.sa-analyst-zh.yaml"

# 引用專業化 Agent
agent: "../agent/specialized/qa-automation.yaml"
```

### 選擇合適的 Agent

參考以下指標：

- **使用頻率**：該 Agent 在多少情境中被使用
- **不可替代性**：該 Agent 的核心價值和重要性
- **協作模式**：該 Agent 如何與其他 Agent 協作
- **主要情境**：該 Agent 在哪些情境中扮演關鍵角色

## 📚 相關文檔

- `AGENT_COLLABORATION_PATTERNS.md` - Agent 協作模式詳細說明
- `AGENT_PHASE2_UPDATE_GUIDE.md` - Phase 2 更新指南
- `core/archive_en/README.md` - 英文版備份說明
- `../CLAUDE.md` - AISDLC 框架整體說明
- `../INTEGRATION_GUIDE.md` - 框架整合指南

## 🔄 版本歷史

### v0.06 (2025-10-30)
- ✅ 補全所有核心 Agent 的 Phase 2 內容
- ✅ 將英文版移至 `archive_en/` 作為歷史參考
- ✅ 確立中文版為主要維護版本
- ✅ 所有 Agent 行數與英文版對齊 (包含 Phase 2)

### v0.03 (Phase 2)
- 新增 Phase 2: Collaboration Patterns & Scenario Usage
- 新增 14 個專業化 Agent

### v0.02
- 建立 specialized/ 目錄結構

### v0.01
- 初始版本，7 個核心 Agent

## ❓ 常見問題

### Q: 為什麼改為只維護中文版？

A: 主要使用者為中文團隊，維護單一語言版本可以：
- 降低維護成本
- 避免版本不一致
- 專注於功能改進而非翻譯同步

### Q: 還能找到英文版嗎？

A: 可以！英文版備份在 `core/archive_en/` 目錄，隨時可供參考。

### Q: Specialized Agents 會有中文版嗎？

A: 目前 specialized agents 為英文版。未來若有需求，會根據使用頻率逐步中文化。

### Q: 如何貢獻新的 Agent？

A:
1. 使用 agent-template-zh_OK.yaml 作為模板
2. 填寫完整的 Phase 2 內容
3. 確保與現有 Agent 的協作關係定義清楚
4. 提交 Pull Request 並更新本 README

---

**維護者**：AISDLC Framework Team
**最後更新**：2025-10-30
**版本**：v0.06
