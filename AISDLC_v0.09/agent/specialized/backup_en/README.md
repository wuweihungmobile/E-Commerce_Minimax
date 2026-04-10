# Specialized Agents 英文版備份 (English Version Archive)

本目錄保存 AISDLC Specialized Agent 的英文版配置檔案，供歷史參考使用。

## 📋 狀態說明

- **主要維護版本**：中文版 (`*-zh.yaml`) - 位於上層 `specialized/` 目錄
- **備份版本**：英文版 (本目錄) - **不再主動更新**
- **歸檔日期**：2025-10-30
- **版本**：v0.06

## 📁 檔案清單

本目錄包含 14 個 Specialized Agent 的英文版配置：

### QA 專業化 (4 個)

| # | 檔案名稱 | Agent 名稱 | 角色 |
|---|---------|-----------|------|
| 1 | `qa-automation.yaml` | AutoQA | 測試自動化專家 |
| 2 | `qa-lead.yaml` | TestLead | QA 主管 |
| 3 | `qa-web-tester.yaml` | WebQA | Web 測試工程師 |
| 4 | `qa-mobile-tester.yaml` | MobileQA | Mobile 測試工程師 |

### 開發專業化 (2 個)

| # | 檔案名稱 | Agent 名稱 | 角色 |
|---|---------|-----------|------|
| 5 | `dev-senior.yaml` | TechLead | 資深開發者 |
| 6 | `code-analyzer.yaml` | CodeX | 代碼分析專家 |

### 架構專業化 (2 個)

| # | 檔案名稱 | Agent 名稱 | 角色 |
|---|---------|-----------|------|
| 7 | `sd-web-architect.yaml` | WebArch | Web 架構師 |
| 8 | `sd-mobile-architect.yaml` | MobileArch | Mobile 架構師 |

### 運維與安全 (2 個)

| # | 檔案名稱 | Agent 名稱 | 角色 |
|---|---------|-----------|------|
| 9 | `devops-engineer.yaml` | DevOpsX | DevOps 工程師 |
| 10 | `security-engineer.yaml` | SecOps | 安全工程師 |

### 其他專業化 (4 個)

| # | 檔案名稱 | Agent 名稱 | 角色 |
|---|---------|-----------|------|
| 11 | `performance-engineer.yaml` | PerfX | 效能工程師 |
| 12 | `integration-specialist.yaml` | IntegX | 整合專家 |
| 13 | `technical-writer.yaml` | DocX | 技術文檔專家 |
| 14 | `compliance-officer.yaml` | ComplianceX | 合規專員 |

## 🎯 使用說明

### 何時參考此目錄

- 需要對照中英文術語翻譯時
- 需要了解原始英文版本的表述方式
- 進行國際化或英文文檔撰寫時
- 學習或培訓時需要英文版本參考
- 與國際團隊協作時的術語對照

### 重要提醒

⚠️ **本目錄的檔案為靜態備份，不會隨著框架更新而修改**

- 所有新功能和 Bug 修復僅在中文版進行
- 如需最新版本，請使用上層 `specialized/` 目錄的中文版
- 本備份僅作為歷史參考和術語對照

## 📝 版本資訊

### 包含內容

所有英文版均包含 Phase 2 的完整內容：

```yaml
# Phase 2: Collaboration Patterns & Scenario Usage
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

### 與中文版的關係

- **內容完整性**：中英文版本內容完全對應
- **結構一致性**：YAML 結構完全相同
- **術語保留**：技術術語、工具名稱、法規名稱在中英文版中均保持英文
- **差異點**：僅描述性文字為不同語言

## 🔄 恢復英文版

如果未來需要恢復英文版作為主要維護版本：

```bash
# 1. 備份當前中文版
cd /path/to/AISDLC_v0.06/agent/specialized
mkdir -p archive_zh
mv *-zh.yaml archive_zh/

# 2. 將英文版移回 specialized 目錄
mv archive_en/*.yaml ./

# 3. 更新文檔說明
# - 更新 README.md
# - 更新 CLAUDE.md
```

## 📊 統計資訊

**總檔案數：** 14 個英文版 Agent YAML

**總代碼行數：** 約 6,683 行

**檔案大小分布：**
- 最小：283 行 (code-analyzer.yaml)
- 最大：597 行 (qa-mobile-tester.yaml)
- 平均：約 477 行

## 🔗 相關資源

### 主要文檔
- **中文版 Agents**：[../README.md](../README.md)
- **Core Agents 英文版備份**：[../../core/archive_en/README.md](../../core/archive_en/README.md)
- **框架說明**：[../../../CLAUDE.md](../../../CLAUDE.md)

### 協作相關
- **協作模式說明**：[../../AGENT_COLLABORATION_PATTERNS.md](../../AGENT_COLLABORATION_PATTERNS.md)
- **Phase 2 更新指南**：[../../AGENT_PHASE2_UPDATE_GUIDE.md](../../AGENT_PHASE2_UPDATE_GUIDE.md)

## 🌐 術語對照建議

使用本備份進行術語對照時的建議：

### 保持英文的術語
- 技術框架：React, Vue, Angular, Flutter, SwiftUI
- 開發工具：Docker, Kubernetes, Jenkins, Terraform
- 測試工具：Selenium, Appium, JMeter, Gatling
- 法規標準：GDPR, HIPAA, PCI-DSS, SOC 2, ISO 27001
- 技術協議：REST, GraphQL, gRPC, WebSocket
- 品質指標：Cyclomatic Complexity, Code Smells

### 可參考的翻譯
- Workflow → 工作流程
- Pattern → 模式
- Scenario → 情境
- Agent → Agent（保持原文）
- Core Principles → 核心原則
- Best Practices → 最佳實踐

## 📞 聯絡資訊

如有任何問題或需要協助，請參考：

- 主要文檔：`AISDLC_v0.06/CLAUDE.md`
- Agent 使用指南：`AISDLC_v0.06/agent/README.md`
- Specialized Agents 指南：`AISDLC_v0.06/agent/specialized/README.md`
- 框架整合指南：`AISDLC_v0.06/INTEGRATION_GUIDE.md`

---

**最後更新**：2025-10-30
**歸檔原因**：改為中文版為主要維護版本
**維護者**：AISDLC Framework Team
**版本**：v0.06

## ⚡ 快速查詢

### 按專業領域查詢

**需要 QA/測試相關：**
- `qa-automation.yaml` - 測試自動化
- `qa-lead.yaml` - 測試策略與管理
- `qa-web-tester.yaml` - Web 測試
- `qa-mobile-tester.yaml` - Mobile 測試

**需要開發相關：**
- `dev-senior.yaml` - 技術決策與指導
- `code-analyzer.yaml` - 代碼分析與重構

**需要架構相關：**
- `sd-web-architect.yaml` - Web 架構設計
- `sd-mobile-architect.yaml` - Mobile 架構設計

**需要運維/安全相關：**
- `devops-engineer.yaml` - CI/CD、容器化
- `security-engineer.yaml` - 安全架構、威脅建模

**需要其他專業：**
- `performance-engineer.yaml` - 效能優化
- `integration-specialist.yaml` - API 整合
- `technical-writer.yaml` - 技術文檔
- `compliance-officer.yaml` - 法規遵循

---

**注意**：本備份為靜態版本，不再更新。請使用上層目錄的中文版進行實際開發工作。
