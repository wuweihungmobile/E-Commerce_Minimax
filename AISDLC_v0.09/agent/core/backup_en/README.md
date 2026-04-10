# Agent 英文版備份 (English Version Archive)

本目錄保存 AISDLC Core Agent 的英文版配置檔案，供歷史參考使用。

## 📋 狀態說明

- **主要維護版本**：中文版 (`*-zh.yaml`) - 位於上層 `core/` 目錄
- **備份版本**：英文版 (本目錄) - **不再主動更新**
- **遷移日期**：2025-10-30
- **版本**：v0.06

## 📁 檔案清單

本目錄包含以下 7 個核心 Agent 的英文版配置：

| # | 檔案名稱 | Agent 名稱 | 角色 |
|---|---------|-----------|------|
| 1 | `01.agent-template.yaml` | Template | Agent 模板 |
| 2 | `02.ba-business-analyst.yaml` | Beatrice | 業務分析師 (BA) |
| 3 | `03.pm-po-agent.yaml` | Victoria | 產品經理/產品負責人 (PM/PO) |
| 4 | `04.sa-analyst.yaml` | Amanda | 系統分析師 (SA) |
| 5 | `05.sd-architect.yaml` | Marcus | 系統設計師/架構師 (SD) |
| 6 | `06.dev-developer.yaml` | David | 軟體開發者 (Dev) |
| 7 | `07.qa-tester.yaml` | Quincy | 品質保證工程師 (QA) |

## 🎯 使用說明

### 何時參考此目錄

- 需要對照中英文術語翻譯時
- 需要了解原始英文版本的表述方式
- 進行國際化或英文文檔撰寫時
- 學習或培訓時需要英文版本參考

### 重要提醒

⚠️ **本目錄的檔案為靜態備份，不會隨著框架更新而修改**

- 所有新功能和 Bug 修復僅在中文版進行
- 如需最新版本，請使用上層 `core/` 目錄的中文版
- 本備份僅作為歷史參考

## 📝 版本差異

### Phase 2 內容（v0.03-phase2）

英文版與中文版都包含 Phase 2 的以下內容：

```yaml
# Phase 2: Collaboration Patterns & Scenario Usage
collaboration_patterns:
  primary_patterns: [...]
  supporting_patterns: [...]

scenario_usage:
  frequency: [...]
  irreplaceability: [...]
  primary_scenarios: [...]
  supporting_scenarios: [...]
  notes: [...]
```

## 🔄 恢復英文版

如果未來需要恢復英文版作為主要維護版本：

```bash
# 1. 備份當前中文版
mkdir -p ../archive_zh
mv ../*-zh*.yaml ../archive_zh/

# 2. 將英文版移回 core 目錄
mv *.yaml ../

# 3. 更新文檔說明
```

## 📞 聯絡資訊

如有任何問題或需要協助，請參考：

- 主要文檔：`AISDLC_v0.06/CLAUDE.md`
- Agent 使用指南：`AISDLC_v0.06/agent/README.md`
- 框架整合指南：`AISDLC_v0.06/INTEGRATION_GUIDE.md`

---

**最後更新**：2025-10-30
**維護者**：AISDLC Framework Team
