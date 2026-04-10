# CLAUDE.md
# Claude Code Project Guidance

**Last Updated**: 2025-01-11
**AISDLC Version**: v0.09
**Document Purpose**: Provide guidance to Claude Code when working with this project using AISDLC framework

---

> **🔴 Important Notice 🔴**
>
> This file provides critical guidance for Claude Code (claude.ai/code) when working with this project.
> All instructions here OVERRIDE default behavior and must be followed exactly.

---

## 🔴 溝通語言規範（Communication Language Policy）

**CRITICAL: 所有執行過程中的回覆必須使用繁體中文**

### 強制規則（Mandatory Rules）

1. **所有任務執行過程中的回覆訊息必須使用繁體中文**
   - ✅ 正確：「完成！我已成功修正 3 個檔案」
   - ❌ 錯誤：「Perfect! I have successfully modified 3 files」

2. **所有狀態更新、確認訊息、說明文字必須使用繁體中文**
   - ✅ 正確：「讓我驗證最終狀態」
   - ❌ 錯誤：「Let me verify the final state」

3. **所有 Todo 任務描述必須使用繁體中文**
   - ✅ 正確：「修正 API 規格」
   - ❌ 錯誤：「Fix API specification」

4. **專有名詞保持原文（不翻譯）**
   - 保持原文：AISDLC, API, PRD, FRD, SRD, Git, Docker, Kubernetes 等
   - 原因：專有名詞，翻譯後詞不達意

**🔴 請嚴格遵守此規範，每次執行任務時都必須使用繁體中文回覆！🔴**

---

## 🔴 開發-編譯-測試循環強制規則（CRITICAL）

**適用範圍**: 本專案所有程式碼開發工作

### 強制執行流程

**原則**: 每完成一支程式（或一個功能單元），**必須立即執行**編譯-測試循環，**絕不累積開發**。

**執行步驟**:

```
開發 1 支程式
    ↓
立即編譯 (Compile/Build)
    ↓
編譯失敗？ → 🔴 立即停止 → 依照錯誤訊息修復 → 重新編譯
    ↓
編譯成功 ✅
    ↓
執行單元測試 (Unit Test)
    ↓
測試失敗？ → 🔴 立即停止 → 依照規格文檔修復 → 重新測試
    ↓
測試通過 ✅
    ↓
繼續開發下一支程式
```

### 絕對禁止的行為

1. **❌ 禁止累積開發多支程式後才編譯**
   - 錯誤範例：開發 5 支程式 → 一次編譯
   - 正確做法：開發 1 支 → 編譯 → 開發下一支

2. **❌ 禁止編譯失敗後繼續開發**
   - 錯誤範例：編譯失敗 → 先開發其他功能 → 稍後再修
   - 正確做法：編譯失敗 → 立即修復 → 編譯成功 → 才繼續

3. **❌ 禁止跳過單元測試**
   - 錯誤範例：編譯成功 → 直接開發下一支
   - 正確做法：編譯成功 → 執行測試 → 測試通過 → 才繼續

4. **❌ 禁止測試失敗後「先跳過」**
   - 錯誤範例：測試失敗 → 註解掉測試 → 繼續開發
   - 正確做法：測試失敗 → 依規格修復 → 測試通過 → 才繼續

### 詳細規範文檔

完整執行指南請參考：[Development_Build_Test_Cycle.md](AISDLC/framework/guides/user/process/Development_Build_Test_Cycle.md)

**🔴 此規則適用於本專案所有開發情境！🔴**

---

## 📂 專案文檔目錄規範

**CRITICAL: 寫入文檔前必須確認正確目錄**

### 專案標準目錄結構（開發專注版 v0.09）

**🔴 重要觀念：AISDLC 框架本身就是專案工作目錄**

本專案遵循 AISDLC v0.09 開發專注版 8 層編號目錄結構（詳見 [DEVELOPMENT_DIRECTORY_STRUCTURE.md](DEVELOPMENT_DIRECTORY_STRUCTURE.md)）：

```
AISDLC_v0.09/                            # 專案工作目錄 ✅
├── AISDLC_INIT.md                       # 框架初始化文件
├── CLAUDE.md                            # Claude Code 專案指引（本檔案）
├── docs/                                # 專案文檔輸出目錄 ✅ 直接寫在這裡！
│   ├── 01_requirements/                 # 需求文檔 (PRD, FRD, User Stories)
│   ├── 02_architecture/                 # 架構設計 (SRD, API Specification)
│   ├── 03_testing/                      # 測試文檔 (Test Plan, AT, Reports)
│   ├── 04_planning/                     # 專案規劃 (Roadmap, Estimation, Task Breakdown)
│   ├── 05_development/                  # 迭代執行 (Iteration Plans, Progress Logs)
│   ├── 06_quality/                      # 程式碼品質 (Code Quality, Security, Performance)
│   ├── 07_design/                       # 設計文檔 (UI/UX, Database, API Design)
│   └── 08_deployment/                   # 部署文檔 (CI/CD, Release Notes)
├── agent/                               # Agent 配置
├── workflow/                            # Workflow 定義
└── tools/                               # 初始化工具
```

### 🛑 寫檔強制檢查

**每次使用 Write/Edit 工具前必須執行**:

1. **確認文檔類型** - 這是什麼類型的文檔？
2. **確認正確目錄** - 應該放在 docs/ 的哪個編號子目錄？
3. **確認命名格式** - 符合 PascalCase 或 Snake_Case 規範？
4. **絕不寫入工作目錄外** - 禁止: /tmp/*, /var/*, 系統目錄

### 快速規則（開發專注版）

| 文檔類型 | 目錄位置 |
|---------|---------|
| PRD, FRD, User Stories | `docs/01_requirements/` |
| SRD, API Specification | `docs/02_architecture/` |
| Test Plan, AT, Test Reports | `docs/03_testing/` |
| Roadmap, Estimation, Task Breakdown | `docs/04_planning/` |
| Iteration Plans, Progress Logs | `docs/05_development/` |
| Code Quality, Security, Performance | `docs/06_quality/` |
| UI/UX, Database Design | `docs/07_design/` |
| CI/CD, Release Notes | `docs/08_deployment/` |

---

## 🔗 AISDLC 框架參考

### 框架位置

- **工作目錄**: AISDLC_v0.09（即框架所在目錄）
- **專案配置**: `AISDLC_PROJECT_CONFIG.md`

### 重要參考文檔

1. **[AISDLC_INIT.md](AISDLC_INIT.md)** - 框架初始化與使用指南
2. **[DEVELOPMENT_DIRECTORY_STRUCTURE.md](DEVELOPMENT_DIRECTORY_STRUCTURE.md)** - 目錄結構標準
3. **[AISDLC_CLAUDE_RULES.md](tools/AISDLC_CLAUDE_RULES.md)** - Claude Code 自動化規則
4. **[Development_Build_Test_Cycle.md](guides/user/process/Development_Build_Test_Cycle.md)** - 開發-編譯-測試循環指南

---

## 🤖 Agent 自動載入與使用

**CRITICAL: Claude Code 執行專案任務時，必須自動載入並扮演對應 Agent 角色**

### 核心 Agent 角色

AISDLC 提供 7 個核心 Agent，每個階段必須載入對應 Agent：

| Agent | 角色 | 使用時機 | 配置檔 |
|-------|------|---------|--------|
| **dev-developer** | 開發者 | 程式碼實作、編譯測試 | [06.dev-developer-zh.yaml](agent/core/06.dev-developer-zh.yaml) |
| **qa-tester** | QA 測試 | 測試設計、執行驗證 | [07.qa-tester-zh.yaml](agent/core/07.qa-tester-zh.yaml) |
| **sa-analyst** | 系統分析 | 需求分析、功能設計 | [04.sa-analyst-zh.yaml](agent/core/04.sa-analyst-zh.yaml) |
| **sd-architect** | 架構設計 | 技術架構、系統設計 | [05.sd-architect-zh.yaml](agent/core/05.sd-architect-zh.yaml) |
| **pm-po-agent** | 專案管理 | 需求優先級、規劃 | [03.pm-po-agent-zh.yaml](agent/core/03.pm-po-agent-zh.yaml) |
| **ba-business-analyst** | 業務分析 | 業務邏輯驗證 | [02.ba-business-analyst-zh.yaml](agent/core/02.ba-business-analyst-zh.yaml) |

### 🔴 強制 Agent 載入規則

#### 1. 開發階段（程式碼實作）

**必須載入**: dev-developer Agent

**載入方式**:
```
請以 dev-developer Agent 角色進行開發，
遵循 agent/core/06.dev-developer-zh.yaml 中的所有規範
```

**強制執行**:
- ✅ 每完成一支程式立即編譯測試（core_principles 第一條）
- ✅ 編譯失敗零容忍、測試失敗零容忍（quality_standards）
- ✅ 遵循 Development_Build_Test_Cycle.md 循環機制

#### 2. 測試階段

**必須載入**: qa-tester Agent

**載入方式**:
```
請以 qa-tester Agent 角色進行測試，
遵循 agent/core/07.qa-tester-zh.yaml 中的所有規範
```

**強制執行**:
- ✅ 設計完整測試場景
- ✅ 驗證符合 AC (Acceptance Criteria)
- ✅ 產出測試報告

#### 3. 架構設計階段

**必須載入**: sd-architect Agent

**載入方式**:
```
請以 sd-architect Agent 角色進行架構設計，
遵循 agent/core/05.sd-architect-zh.yaml 中的所有規範
```

### Agent 自動載入流程

**當 Claude Code 執行任務時**:

```yaml
step_1: 識別任務類型（開發/測試/設計/分析）
step_2: 自動讀取對應 Agent 配置檔（YAML）
step_3: 載入 Agent 的 core_principles
step_4: 載入 Agent 的 quality_standards
step_5: 載入 Agent 的 collaboration_rules
step_6: 按照 Agent 規範執行任務
step_7: 確保所有 quality_standards 都符合
```

### Agent 與 Workflow

- **Agent 配置**: `agent/core/`
- **Workflow 定義**: `workflow/`
- **情境 SOP**: `scenarios/`

**使用範例**:
```
使用者：「請開始開發用戶登入功能」

Claude Code 自動執行：
1. 識別任務類型 = 開發
2. 載入 dev-developer Agent (06.dev-developer-zh.yaml)
3. 讀取 core_principles（包含開發-編譯-測試循環）
4. 開始開發第一支程式
5. 開發完成 → 立即編譯
6. 編譯成功 → 執行單元測試
7. 測試通過 → 繼續下一支程式
```

使用 AISDLC workflow 前，請先載入 `AISDLC_INIT.md`。

---

## 📋 專案開發規範

### 文檔命名規範

- **格式**: 使用 PascalCase 或 Snake_Case
- **語言**: 英文命名（避免中文檔名）
- **前綴**: 包含文檔類型前綴（如 PRD_, FRD_, Sprint_）

**範例**:
- ✅ 正確: `PRD_UserAuthentication.md`, `Sprint_1_Planning.md`, `API_Specification_Payment.md`
- ❌ 錯誤: `用戶需求.md`, `sprint1.md`, `api.md`

### ID 命名規範

遵循 AISDLC ID 命名規範：

- **F-XXX**: Feature
- **NFR-XXX**: Non-Functional Requirement
- **EPIC-XXX**: Epic
- **US-XXX**: User Story
- **AC-XXX-Y**: Acceptance Criteria
- **API-XXX**: API Endpoint
- **TC-XXX-Y-Z**: Test Case
- **BUG-XXX**: Bug
- **TECH-XXX**: Technical Task

詳見：[AISDLC_ID_Naming_Convention.md](guides/system/naming/AISDLC_ID_Naming_Convention.md)

### 文檔品質標準

所有文檔交付前必須執行品質檢查：

- [ ] 文檔完整性檢查
- [ ] 文檔品質檢查
- [ ] 可讀性測試（15 分鐘）
- [ ] 技術文檔專項檢查

詳見：[Document_Quality_Checklist.md](guides/system/quality/Document_Quality_Checklist.md)

---

## 🚀 使用 AISDLC Workflow

### 載入流程

```yaml
step_1: 載入 AISDLC_INIT.md
step_2: 識別專案情境類型（參考 AISDLC_PROJECT_CONFIG.md）
step_3: 載入對應 Agents
step_4: 執行對應 Workflow
```

### 常用情境

- **Greenfield**: 新專案開發
- **Brownfield**: 舊專案維護
- **Refactoring**: 程式碼重構
- **Integration**: 系統整合
- **Testing**: 測試強化
- **Documentation**: 文檔補強

詳見：[SCENARIO_SELECTOR.md](guides/user/onboarding/SCENARIO_SELECTOR.md)

---

## ⚠️ 重要提醒

### DO:
- ✅ 使用繁體中文回覆（專有名詞除外）
- ✅ 每支程式開發完立即編譯測試
- ✅ 文檔寫入前確認正確目錄
- ✅ 遵循 AISDLC ID 命名規範
- ✅ 文檔交付前執行品質檢查

### DO NOT:
- ❌ 使用英文回覆訊息（專有名詞除外）
- ❌ 累積開發多支程式後才編譯
- ❌ 編譯失敗或測試失敗後繼續開發
- ❌ 跳過單元測試
- ❌ 文檔寫入錯誤目錄
- ❌ 註解掉失敗的測試

---

**文檔元數據**:
- **文檔版本**: v1.0
- **建立日期**: 2025-01-11
- **適用 AISDLC 版本**: v0.09+
- **維護者**: 專案團隊
- **文檔狀態**: Active
