# Core Agents 核心代理

## 概述

本目錄包含 AISDLC 框架的 **7 個核心 Agent 配置文件**（中文版），這些 Agent 模擬軟體開發生命週期中的關鍵角色。

**維護狀態**（2025-10-30 更新）：
- ✅ **主要維護版本**：中文版 (`*-zh.yaml`) - 所有更新、新功能、Bug 修復都在此進行
- 📦 **歷史參考版本**：英文版備份於 [archive_en/](archive_en/) - 僅供術語對照和歷史參考，不再更新

---

## 📁 目錄結構

```
core/
├── README.md                          # 本文件
├── 01.agent-template-zh_OK.yaml       # Agent 模板（中文版）
├── 02.ba-business-analyst-zh.yaml     # 業務分析師 (Beatrice)
├── 03.pm-po-agent-zh.yaml             # 產品經理/PO (Victoria)
├── 04.sa-analyst-zh.yaml              # 系統分析師 (Amanda)
├── 05.sd-architect-zh.yaml            # 系統設計師 (Marcus)
├── 06.dev-developer-zh.yaml           # 開發人員 (Developer)
├── 07.qa-tester-zh.yaml               # QA 測試工程師 (Quincy)
└── archive_en/                        # 英文版歸檔目錄
    ├── README.md
    ├── 01.agent-template.yaml
    ├── 02.ba-business-analyst.yaml
    ├── 03.pm-po-agent.yaml
    ├── 04.sa-analyst.yaml
    ├── 05.sd-architect.yaml
    ├── 06.dev-developer.yaml
    └── 07.qa-tester.yaml
```

---

## 🎭 核心 Agent 列表

### 1. Agent Template 代理模板
**檔案**: [01.agent-template-zh_OK.yaml](01.agent-template-zh_OK.yaml)

- **用途**: 新 Agent 開發的標準模板
- **包含**: 完整的 Phase 2 內容結構（collaboration_patterns, scenario_usage）
- **使用時機**: 創建任何新的 Core 或 Specialized Agent 時

---

### 2. Business Analyst (BA) 業務分析師
**檔案**: [02.ba-business-analyst-zh.yaml](02.ba-business-analyst-zh.yaml)
**Agent ID**: `ba-business-analyst`
**角色名稱**: Beatrice

#### 核心職責
- 與利益相關者溝通，理解業務需求
- 驗證需求的業務價值和可行性
- 確保需求符合業務目標和市場定位
- 提供業務流程和商業邏輯的專業建議

#### 主要協作模式
- **Peer-Review**: 與 SA 共同審查 FRD，確保業務需求正確轉化為功能需求
- **Support**: 支援 PM/PO 進行業務價值評估和優先級排序

#### 適用情境
- 高頻使用 (High - 7+ scenarios)
- 所有需要業務驗證的情境

---

### 3. Product Manager/Product Owner (PM/PO) 產品經理
**檔案**: [03.pm-po-agent-zh.yaml](03.pm-po-agent-zh.yaml)
**Agent ID**: `pm-po-agent`
**角色名稱**: Victoria

#### 核心職責
- 定義產品願景和路線圖
- 撰寫並維護 PRD (Product Requirements Document)
- 決策產品功能的優先級和範圍
- 平衡業務價值、技術可行性和資源限制

#### 主要協作模式
- **Lead-Support**: 主導需求文檔驗證工作流，與 SA/BA 協作
- **Stakeholder-Liaison**: 與 BA 共同進行利益相關者溝通

#### 適用情境
- 高頻使用 (High - 7+ scenarios)
- 所有需要產品決策和 PRD 的情境

---

### 4. System Analyst (SA) 系統分析師
**檔案**: [04.sa-analyst-zh.yaml](04.sa-analyst-zh.yaml)
**Agent ID**: `sa-analyst`
**角色名稱**: Amanda

#### 核心職責
- 深度分析和拆解業務需求
- 撰寫並維護 FRD (Functional Requirements Document)
- 定義詳細的功能規格和驗收標準
- 橋接業務需求與技術實現

#### 主要協作模式
- **Lead-Support**: 主導統一需求提取工作流
- **Peer-Review**: 與 BA 進行 FRD 的業務合理性審查
- **Handoff**: 向 SD 移交完整的功能需求文檔

#### 適用情境
- 最高頻使用 (High - 9 scenarios - 不可替代)
- ⭐⭐⭐⭐⭐ Must Keep (Core role, irreplaceable)

---

### 5. System Designer (SD) 系統設計師/架構師
**檔案**: [05.sd-architect-zh.yaml](05.sd-architect-zh.yaml)
**Agent ID**: `sd-architect`
**角色名稱**: Marcus

#### 核心職責
- 設計系統架構和技術方案
- 撰寫並維護 SRD (System Requirements Document)
- 定義 API 規格和系統接口
- 評估技術可行性和性能需求

#### 主要協作模式
- **Lead-Support**: 主導 User Story 與設計工作流、API 規格生成
- **Peer-Review**: 與 Dev 進行技術可行性審查

#### 適用情境
- 高頻使用 (High - 7+ scenarios)
- ⭐⭐⭐⭐⭐ Must Keep (Technical design is essential)

---

### 6. Developer (Dev) 開發人員
**檔案**: [06.dev-developer-zh.yaml](06.dev-developer-zh.yaml)
**Agent ID**: `dev-developer`
**角色名稱**: Developer

#### 核心職責
- 實現系統功能和技術方案
- 評估開發複雜度和工作量
- 提供實作層面的技術建議
- 進行代碼審查和技術文檔撰寫

#### 主要協作模式
- **Peer-Review**: 與 SD 進行技術可行性評估
- **Support**: 支援 QA 理解實現細節以設計測試

#### 適用情境
- 中高頻使用 (Medium-High - 5 scenarios)
- ⭐⭐⭐⭐ Highly Recommended (Implementation insights critical)

---

### 7. QA Tester QA 測試工程師
**檔案**: [07.qa-tester-zh.yaml](07.qa-tester-zh.yaml)
**Agent ID**: `qa-tester`
**角色名稱**: Quincy

#### 核心職責
- 設計測試策略和驗收測試方案
- 定義測試用例和驗收標準
- 審查需求的可測試性
- 撰寫並維護 AT (Acceptance Tests)

#### 主要協作模式
- **Lead-Support**: 主導文檔一致性檢查工作流
- **Peer-Review**: 審查所有需求文檔的可測試性

#### 適用情境
- 高頻使用 (High - 7+ scenarios)
- ⭐⭐⭐⭐⭐ Must Keep (Quality gate is essential)

---

## 🔄 Agent 配置結構

每個 Core Agent 配置文件都包含以下標準結構：

```yaml
agent:
  id: "{agent-id}"
  name: "{Agent Name}"
  version: "0.04"
  created: "2025-10-XX"
  role: "{角色中文名稱}"

  persona:
    name: "{角色人物名}"
    expertise: [專業領域列表]
    style: "{工作風格描述}"

  core_principles:
    - "{核心原則1}"
    - "{核心原則2}"

  responsibilities:
    primary: [主要職責列表]
    secondary: [次要職責列表]

  collaboration_rules:
    must_collaborate_with: [必須協作的 Agent]
    optional_collaboration: [可選協作的 Agent]

  quality_standards:
    deliverables: [交付物標準]
    validation_criteria: [驗證標準]

  # Phase 2: Collaboration Patterns & Scenario Usage
  collaboration_patterns:
    primary_patterns: [主要協作模式]
    supporting_patterns: [支援協作模式]

  scenario_usage:
    frequency: "{使用頻率}"
    irreplaceability: "{不可替代性評級}"
    primary_scenarios: [主要情境]
    supporting_scenarios: [支援情境]
    notes: |
      補充說明...
```

---

## 📖 使用指南

### 1. 選擇合適的 Agent

根據工作流需求選擇：
- **需求分析階段**: SA (主導), BA (驗證), PM/PO (決策)
- **設計階段**: SD (主導), Dev (審查), QA (可測試性)
- **變更管理**: SA (分析), SD (影響評估), QA (回歸測試)
- **文檔檢查**: QA (主導), SA (需求一致性), SD (設計一致性)

### 2. 創建新 Agent

使用 [01.agent-template-zh_OK.yaml](01.agent-template-zh_OK.yaml) 作為基礎：

```bash
# 複製模板
cp 01.agent-template-zh_OK.yaml 0X.new-agent-zh.yaml

# 修改以下必填項目：
# - agent.id
# - agent.name
# - agent.role
# - persona (name, expertise, style)
# - core_principles
# - responsibilities
# - collaboration_rules
# - quality_standards
# - collaboration_patterns (Phase 2)
# - scenario_usage (Phase 2)
```

### 3. 翻譯原則

如需參考英文版進行術語對照，請查閱 [archive_en/](archive_en/)：

**保持英文的項目**：
- Agent ID（如 `sa-analyst`）
- 文件類型縮寫（PRD, FRD, SRD, AT, API）
- 技術術語（API, CI/CD, Docker, Kubernetes）
- 工作流名稱（unified-requirements-extraction）
- 協作模式名稱（Lead-Support, Peer-Review）

**完全中文化的項目**：
- 標題、描述性文字
- 角色職責、核心原則
- persona 欄位（name, expertise, style）
- 所有使用者可見的說明文字

### 4. Phase 2 內容要求

所有 Core Agent 必須包含 Phase 2 內容：

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "[協作模式：如 Lead-Support]"
      role: "[角色：Lead, Support, Peer...]"
      description: "[具體職責描述]"
      applicable_scenarios: ["情境1", "情境2"]

scenario_usage:
  frequency: "[High/Medium/Low]"
  irreplaceability: "[⭐ 評級與說明]"
  primary_scenarios:
    - scenario: "[情境名稱]"
      role: "[主要角色]"
      responsibilities: "[具體職責]"
```

---

## 📦 英文版歸檔

**歸檔位置**: [archive_en/](archive_en/)

**用途**：
- ✅ 術語對照參考
- ✅ 歷史版本追溯
- ✅ 英文文檔撰寫時的用語參考

**維護狀態**:
- ❌ 不再主動更新
- ✅ 保持靜態以供參考

詳細說明請參閱 [archive_en/README.md](archive_en/README.md)

---

## 🔗 相關資源

- **上層目錄**: [../README.md](../README.md) - Agent 總覽
- **Specialized Agents**: [../specialized/README.md](../specialized/README.md) - 14 個專業化 Agent
- **框架文檔**: [../../CLAUDE.md](../../CLAUDE.md) - AISDLC 框架使用指南
- **工作流定義**: [../../workflow/](../../workflow/) - 所有工作流配置

---

## ⚠️ 重要注意事項

1. **不要修改 `archive_en/` 中的文件** - 這些是靜態參考備份
2. **所有更新都在 `-zh.yaml` 文件進行** - 中文版是主要維護版本
3. **必須包含 Phase 2 內容** - collaboration_patterns 和 scenario_usage 是必需的
4. **保持命名一致性** - Agent ID 和檔案命名必須匹配
5. **遵循翻譯原則** - 技術術語保持英文，描述性內容使用中文

---

## 📝 版本歷史

- **v0.06** (2025-10-30): 完整中文化 7 個 Core Agents，補全 Phase 2 內容，歸檔英文版
- **v0.03-phase2**: 新增 collaboration_patterns 和 scenario_usage
- **v0.03**: 初始版本，7 個核心 Agent 定義

---

**維護者**: AISDLC Framework Team
**最後更新**: 2025-10-30
**文檔版本**: 1.0
