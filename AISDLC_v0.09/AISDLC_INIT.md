# AISDLC v0.09（開發專注版）框架初始化配置文件

> ⚠️ **重要**：使用任何 AISDLC workflow 前，必須先載入此配置！

## 🎯 v0.09（開發專注版）版本說明

v0.09 開發專注版核心特色：
- **移除會議流程**：專為 2 人團隊設計，移除所有會議相關流程
- **8 層目錄結構**：重新設計專案目錄，新增 06_quality/（品質保證）
- **迭代制開發**：取代 Sprint 制，快速迭代、精簡高效
- **品質優先**：強化程式碼品質、安全合規、效能優化
- **文檔驅動**：需求→設計→實作→測試，完整可追溯
- **按需載入機制**：Token 效率優化，初始載入僅需 ~200 tokens

📖 **完整版本歷史與變更**: [FILE_DIRECTORY_RULES.md](FILE_DIRECTORY_RULES.md) | [AISDLC_v0.10_UPGRADE_SOP.md](AISDLC_v0.10_UPGRADE_SOP.md)

---

## 📚 核心參考指南 (Guides)

AISDLC v0.09 提供以下核心參考指南，幫助您標準化開發流程：

### ID 命名與追蹤體系
- **[AISDLC_ID_Naming_Convention.md](guides/system/naming/AISDLC_ID_Naming_Convention.md)** - 統一 ID 命名規範
  - 定義 10 種標準 ID 格式：F-XXX, NFR-XXX, UR-XXX, BR-XXX, EPIC-XXX, US-XXX, AC-XXX-Y, API-XXX, TC-XXX-Y-Z, BUG-XXX
  - 完整追蹤鏈範例：需求 → 設計 → 開發 → 測試
  - 需求追蹤矩陣 (RTM) 範例
  - ID 版本管理與變更處理規則

### 估算與規劃標準
- **[Estimation_Standards.md](guides/system/planning/Estimation_Standards.md)** - 估算標準化指南
  - Story Points 估算方法與參考表
  - Effort Units (EU) 定義與轉換規則（1 EU = 1 人日）
  - Planning Poker 流程與最佳實踐
  - 三點估算法（樂觀/最可能/悲觀）
  - 估算準確度追蹤與改進

### 文檔品質標準
- **[Document_Quality_Checklist.md](guides/system/quality/Document_Quality_Checklist.md)** - 文檔品質檢查清單 🆕
  - 4 大類檢查：文件完整性、文件品質、可讀性測試、技術文檔專項
  - 19 個階段文檔的檢查標準
  - 15 分鐘可讀性測試方法
  - API Spec、Architecture、User Story 專項檢查

### 架構設計指南
- **[C4_Model_Guidelines.md](guides/system/architecture/C4_Model_Guidelines.md)** - C4 架構設計指南
  - C4 Model 四個層級詳解（Context, Container, Component, Code）
  - 視覺化工具推薦（Structurizr, PlantUML, Mermaid）
  - AISDLC 各階段的 C4 使用時機
  - 完整範例與最佳實踐

### 平台與技術選型
- **[Platform_Agent_Selection_Guide.md](guides/system/agent/Platform_Agent_Selection_Guide.md)** - 跨平台 Agent 選擇指南
  - 平台識別決策樹（Web/iOS/Android/跨平台/後端）
  - 各平台對應的 SD-Architect 專家選擇
  - 技術棧推薦與 Agent 對照表

### 情境專用工具（Greenfield）
- **[Standard_Confirmation_Questions.md](scenarios/greenfield/checklists/Standard_Confirmation_Questions.md)** - 72 個標準確認問題
- **[Completeness_Checklist.md](scenarios/greenfield/checklists/Completeness_Checklist.md)** - 120 項完整性檢查清單
- **[Cost_Estimation_Template.md](scenarios/greenfield/checklists/Cost_Estimation_Template.md)** - 完整成本估算模板
- **[Parallel_Execution_Guide.md](scenarios/greenfield/Parallel_Execution_Guide.md)** - 並行執行協調指南

**使用時機**：
- 🆕 新專案啟動：使用 ID 命名規範 + 估算標準 + 確認問題清單
- 🏗️ 架構設計：使用 C4 Model 指南
- 🔧 技術選型：使用平台選擇指南 + 成本估算模板
- ✅ 品質檢查：使用完整性檢查清單 + 文檔品質檢查清單 🆕
- 📋 文檔交付前：使用文檔品質檢查清單進行最終驗證 🆕

---

## 🎬 專案初始化指南 (v0.09+ 新增)

> **🔴 重要**: 在使用任何 AISDLC 情境 SOP 前，請先完成專案初始化設定！

### 📦 前置作業：取得 AISDLC 框架源碼

> **🔴 必須先完成此步驟**，後續的自動/手動初始化都需要本地已有框架檔案。

```bash
# 方式 1: 公開倉庫（HTTPS）
git clone https://github.com/wuweihungmobile/AISDLC.git

# 方式 2: 私有倉庫（SSH，需先設定 SSH Key）
git clone git@github.com:wuweihungmobile/AISDLC.git

# 方式 3: 私有倉庫（HTTPS + Personal Access Token）
git clone https://<YOUR_PAT>@github.com/wuweihungmobile/AISDLC.git
```

> 💡 **提示**：私有倉庫需要 SSH Key 或 PAT Token 才能存取。
> - SSH Key 設定：參考 [GitHub SSH 文件](https://docs.github.com/en/authentication/connecting-to-github-with-ssh)
> - PAT Token 建立：參考 [GitHub PAT 文件](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)

---

### 🚀 自動初始化（推薦方式，< 1 分鐘）

**使用 AISDLC 提供的自動初始化腳本**：

**macOS / Linux / Git Bash (Windows)**：
```bash
# 方式 1: 在已 clone 的框架目錄中執行（推薦，不需重新下載）
cd AISDLC
./AISDLC_v0.09/tools/init_project.sh -d ~/my-project

# 方式 2: 完整路徑執行
bash /path/to/AISDLC/AISDLC_v0.09/tools/init_project.sh -d ~/my-project
```

**Windows PowerShell**：
```powershell
# 方式 1: 在已 clone 的框架目錄中執行（推薦，不需重新下載）
cd AISDLC
.\AISDLC_v0.09\tools\init_project.ps1 -Dir C:\Projects\MyApp

# 方式 2: 完整路徑執行
PowerShell -ExecutionPolicy Bypass -File "C:\path\to\AISDLC\AISDLC_v0.09\tools\init_project.ps1" -Dir C:\Projects\MyApp
```

**自動初始化腳本會自動執行**：
- ✅ 偵測作業系統（macOS/Linux/Windows）
- ✅ 智慧偵測本地框架（已有則跳過下載）
- ✅ 建立 AISDLC 框架連結（`AISDLC/framework/`）
- ✅ 建立標準 docs/ 目錄結構
- ✅ 複製 CLAUDE.md 與 .claude/skills/ 到專案根目錄

---

### 📋 手動初始化（備用方式，5 分鐘）

如果自動腳本不可用，可手動執行以下步驟：

```bash
# 步驟 1: 建立 AISDLC 框架連結
mkdir -p AISDLC
ln -s /path/to/AISDLC_ALL/AISDLC_v0.09 AISDLC/framework

# 步驟 2: 建立專案文檔目錄（遵循 DEVELOPMENT_DIRECTORY_STRUCTURE.md - 開發專注版）
mkdir -p docs/{01_requirements,02_architecture,03_testing,04_planning,05_development,06_quality,07_design,08_deployment}

# 步驟 3: 複製 Claude Code 設定檔與 Skills
cp AISDLC/framework/../CLAUDE.md ./CLAUDE.md
# 如果框架包含 .claude/skills/，一併複製
if [ -d "AISDLC/framework/.claude/skills" ]; then
  mkdir -p .claude/skills
  cp -r AISDLC/framework/.claude/skills/* .claude/skills/
fi
```

### 完整初始化指南

📖 **詳細步驟請參閱**: [PROJECT_INITIALIZATION_GUIDE.md](guides/user/onboarding/PROJECT_INITIALIZATION_GUIDE.md)

包含內容：
- ✅ 三種框架整合方式（符號連結/完整拷貝/Git Submodule）
- ✅ 九種情境專屬目錄設定
- ✅ 文檔產出位置規範
- ✅ 驗證檢查清單
- ✅ 常見問題解答

### 專案文檔目錄結構規範

```
your-project/
├── AISDLC/                           # AISDLC 框架（符號連結或完整拷貝）
│   └── framework/ -> AISDLC_v0.09/
├── CLAUDE.md                         # Claude Code 設定檔
├── .claude/skills/                   # Claude Code Skills（自動部署）
└── docs/                             # 專案文檔輸出目錄（開發專注版）
    ├── 01_requirements/              # 需求文檔 (PRD, FRD, User Stories)
    ├── 02_architecture/              # 架構設計 (SRD, API Specification)
    ├── 03_testing/                   # 測試文檔 (Test Plan, Test Cases, Reports)
    ├── 04_planning/                  # 開發規劃 (Roadmap, Estimation, Task Breakdown)
    ├── 05_development/               # 開發文檔 (Iteration Plans, Progress Logs)
    ├── 06_quality/                   # 品質保證 (Code Quality, Security, Performance)
    ├── 07_design/                    # 設計文檔 (UI/UX, Database, API Design)
    └── 08_deployment/                # 部署文檔 (CI/CD, Release Notes)
```

### 為什麼需要專案初始化？

1. **統一文檔位置** - 所有團隊成員知道文檔放在哪裡
2. **符合 AISDLC 規範** - 確保九種情境 SOP 順利執行
3. **版本控制友好** - 清晰的目錄結構易於 Git 管理
4. **提升協作效率** - 標準化減少溝通成本

---

## 🚀 按需載入機制 (v0.09 完全繼承)

你現在要使用 AISDLC v0.09 (AI 輔助軟體開發生命週期) 框架，採用**情境感知按需載入**方式，並享受簡化後的文檔體系。

### 🤖 Claude Code 自動化規則載入

**⚠️ CRITICAL**: 當 Claude Code 載入本檔案時，**必須自動載入並套用** AISDLC Claude Rules 和對應的 Agents。

**自動載入流程**:
```yaml
step_1: 讀取 AISDLC/framework/AISDLC_INIT.md（本檔案）
step_2: 自動讀取 AISDLC/framework/tools/AISDLC_CLAUDE_RULES.md
step_3: 自動套用所有 Claude Rules（溝通語言、文檔規範、寫檔檢查等）
step_4: 自動偵測當前作業系統
step_5: 檢查專案是否已初始化
  - 如果未初始化: 提示執行 tools/init_project.sh
  - 如果已初始化: 繼續
step_6: 識別專案情境類型（透過問答或指令解析）
step_7: 🔴 從「Agent 自動載入配置表」讀取對應情境的配置
step_8: 🔴 自動載入 Primary Agents（讀取 YAML 並套用規則）
step_9: 🔴 記錄 Supporting Agents 列表（按需載入）
step_10: 載入對應 Workflows
step_11: 🔴 確認 .claude/skills/ 已部署（33 個 Claude Code Skills）
step_12: 顯示載入狀態確認（含可用 Skills 列表）
step_13: 開始執行 SOP
```

**🔴 Agent 自動載入執行範例**:
```
情境識別: greenfield

🔄 正在自動載入 Agents...
✅ 讀取配置: auto_load_config.greenfield
✅ 載入 Primary Agent: agent/core/03.pm-po-agent-zh.yaml (Victoria - PM/PO)
✅ 載入 Primary Agent: agent/core/04.sa-analyst-zh.yaml (Amanda - SA)
📋 記錄 Supporting Agents:
   - sd-architect-zh.yaml (Stage 3 時載入)
   - qa-tester-zh.yaml (Stage 4 時載入)
   - dev-developer-zh.yaml (Stage 5 時載入)
✅ 載入 Workflows: requirements-extraction, validation-documentation, user-story-design, api-specification
✅ SOP 路徑: scenarios/greenfield/SOP.md

🎯 準備就緒，開始執行 Greenfield SOP...
```

**Claude Rules 配置檔位置**:
- 📄 [tools/AISDLC_CLAUDE_RULES.md](tools/AISDLC_CLAUDE_RULES.md)

**包含的自動化規則**:
1. ✅ 溝通語言規範（繁體中文）
2. ✅ 文檔目錄規範（docs/ 標準結構）
3. ✅ 文檔命名規範（PascalCase/Snake_Case）
4. ✅ 寫檔強制檢查（路徑驗證）
5. ✅ 專案初始化標準
6. ✅ ID 命名規範（AISDLC ID）
7. ✅ 文檔品質標準
8. ✅ AISDLC 升版執行規範

---

## 🤖 Agent 自動載入配置表 (v0.09+ 新增)

> **🔴 CRITICAL**: 當 Claude Code 識別到專案情境後，**必須自動載入**以下配置表中對應的 Agents。

### 自動載入配置 (Auto-Load Configuration)

```yaml
# 情境自動載入配置
auto_load_config:
  greenfield:
    primary_agents:
      - path: "agent/core/03.pm-po-agent-zh.yaml"
        role: "專案啟動、商業價值決策"
      - path: "agent/core/04.sa-analyst-zh.yaml"
        role: "需求分析、FRD 產出"
    supporting_agents:
      - path: "agent/core/02.ba-business-analyst-zh.yaml"
        load_at: "Stage 2 - 需求驗證與業務可行性確認"
      - path: "agent/core/05.sd-architect-zh.yaml"
        load_at: "Stage 3 - 技術架構設計"
      - path: "agent/core/07.qa-tester-zh.yaml"
        load_at: "Stage 4 - 驗收標準定義"
      - path: "agent/core/06.dev-developer-zh.yaml"
        load_at: "Stage 5 - 工時估算與實施開發"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 8 - CI/CD 與部署準備"
    optional_agents:
      - path: "agent/specialized/integration-specialist-zh.yaml"
        load_at: "Stage 2 - 硬體整合/第三方系統整合需求分析"
        condition: "專案涉及硬體整合（條碼掃描器、手機內建相機掃碼、IoT 感測器）、第三方系統整合（支付閘道、社群登入、地圖服務）、或跨平台裝置功能呼叫時載入"
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 3 - 技術選型階段（安全敏感專案）"
        condition: "專案涉及敏感資料、認證授權、合規要求、電商金流/支付整合（Stripe/ECPay 等）、或使用者個資處理（GDPR/個資法）時載入"
      - path: "agent/specialized/compliance-officer-zh.yaml"
        load_at: "Stage 3 - 合規需求確認"
        condition: "專案涉及 GDPR/PCI-DSS/ISO 27001 等法規合規時載入"
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 3 - 平台架構設計"
        condition: "專案涉及 iOS/Android/macOS 行動端開發時載入"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "Stage 6 - User Story 撰寫（行動端測試）"
        condition: "專案涉及行動端應用需要專業化行動測試時載入"
    workflows:
      - "requirements-extraction"
      - "validation-documentation"
      - "user-story-design"
      - "api-specification"
      - "consistency-check"
      - "interaction-analysis"
      - "sprint-execution"
    sop_path: "scenarios/greenfield/SOP.md"

  brownfield:
    primary_agents:
      - path: "agent/core/04.sa-analyst-zh.yaml"
        role: "現況分析、需求提取、影響分析"
      - path: "agent/specialized/dev-senior-zh.yaml"
        role: "技術評審、代碼分析、方案設計"
    supporting_agents:
      - path: "agent/specialized/code-analyzer-zh.yaml"
        load_at: "Stage 2 - 代碼理解與架構分析"
      - path: "agent/core/05.sd-architect-zh.yaml"
        load_at: "Stage 4 - 變更設計與技術方案（新增功能或架構變更時）"
      - path: "agent/core/02.ba-business-analyst-zh.yaml"
        load_at: "Stage 3 - 業務邏輯變更的影響驗證"
      - path: "agent/core/03.pm-po-agent-zh.yaml"
        load_at: "Stage 3 - 變更優先級決策與商業價值評估"
      - path: "agent/core/07.qa-tester-zh.yaml"
        load_at: "Stage 6 - 測試策略與測試計畫"
      - path: "agent/core/06.dev-developer-zh.yaml"
        load_at: "Stage 10 - Sprint 執行與開發測試"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 8 - 部署方案與回滾計畫"
    optional_agents:
      - path: "agent/specialized/integration-specialist-zh.yaml"
        load_at: "Stage 4 - 涉及第三方整合或硬體整合時"
        condition: "新增功能涉及外部 API、硬體設備（條碼掃描、IoT）整合時載入"
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 2 - 平台識別為 Mobile 時"
        condition: "變更涉及 Android/iOS/macOS 行動平台時載入"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "Stage 6 - 涉及 Mobile 平台測試時"
        condition: "變更涉及行動裝置功能時載入"
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 3 - 涉及安全性變更時"
        condition: "變更涉及認證授權、資料保護、安全漏洞修復時載入"
      - path: "agent/specialized/compliance-officer-zh.yaml"
        load_at: "Stage 3 - 涉及法規合規需求時"
        condition: "變更由法規/會計準則/資安合規驅動時載入"
    workflows:
      - "requirements-extraction"
      - "change-management"
      - "consistency-check"
      - "api-specification"
      - "user-story-design"
      - "interaction-analysis"
      - "sprint-execution"
      - "validation-documentation"
    sop_path: "scenarios/brownfield/SOP.md"

  refactoring:
    primary_agents:
      - path: "agent/core/05.sd-architect-zh.yaml"
        role: "重構架構設計、技術棧遷移策略"
      - path: "agent/specialized/code-analyzer-zh.yaml"
        role: "代碼品質分析、技術債量化、遷移影響評估"
    supporting_agents:
      - path: "agent/core/04.sa-analyst-zh.yaml"
        load_at: "Stage 1 - 需求重新分析與業務邏輯提取（技術棧遷移時）"
      - path: "agent/core/02.ba-business-analyst-zh.yaml"
        load_at: "Stage 2 - 業務邏輯完整性驗證（技術棧遷移時）"
      - path: "agent/core/03.pm-po-agent-zh.yaml"
        load_at: "Stage 3 - 重構目標優先級與 ROI 決策（X-Large 規模時）"
      - path: "agent/specialized/dev-senior-zh.yaml"
        load_at: "Stage 4 - 技術決策與重構策略制定"
      - path: "agent/core/07.qa-tester-zh.yaml"
        load_at: "Stage 6 - 回歸測試規劃與驗證"
      - path: "agent/core/06.dev-developer-zh.yaml"
        load_at: "Stage 5 - 重構實作與開發測試循環"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 5 - CI/CD 重建與部署策略（技術棧遷移時）"
      - path: "agent/specialized/technical-writer-zh.yaml"
        load_at: "Stage 7 - 前後對比成果報告；Stage 8 - 知識沉澱與文件更新"
    optional_agents:
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 3 - 平台識別為 Mobile 時"
        condition: "重構涉及新增 Android/iOS/macOS 行動平台時載入"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "Stage 6 - 涉及 Mobile 平台測試時"
        condition: "重構涉及行動裝置功能時載入"
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 4 - 涉及安全敏感模組重構時"
        condition: "重構涉及認證/授權/支付/敏感資料模組時載入"
      - path: "agent/specialized/integration-specialist-zh.yaml"
        load_at: "Stage 2 - 涉及第三方整合或硬體整合時"
        condition: "重構涉及條碼掃描、IoT、第三方 API 整合時載入"
      - path: "agent/specialized/performance-engineer-zh.yaml"
        load_at: "Stage 2 - 效能基準線建立（技術棧遷移時）；Stage 6 - 效能驗證與基準對比"
        condition: "重構目標包含效能改善或技術棧遷移時載入。技術棧遷移場景需在 Stage 2 建立效能基準線，Stage 6 進行前後對比驗證"
      - path: "agent/specialized/compliance-officer-zh.yaml"
        load_at: "Stage 2 - 涉及法規合規需求時（技術棧遷移分析階段）"
        condition: "重構涉及支付處理(PCI-DSS)、個人資料(GDPR)、電商金流整合、或其他法規合規需求時載入"
    workflows:
      - "refactoring-planning-flow"
      - "requirements-extraction"
      - "change-management"
      - "user-story-design"
      - "api-specification"
      - "consistency-check"
      - "interaction-analysis"
      - "validation-documentation"
      - "sprint-execution"
    sop_path: "scenarios/refactoring/SOP.md"

  migration:
    primary_agents:
      - path: "agent/core/05.sd-architect-zh.yaml"
        role: "遷移架構設計、技術棧映射、並行運行策略"
      - path: "agent/core/04.sa-analyst-zh.yaml"
        role: "需求重新分析、業務邏輯提取與驗證"
    supporting_agents:
      - path: "agent/specialized/code-analyzer-zh.yaml"
        load_at: "Stage 1 - 舊系統代碼品質與遷移影響分析"
      - path: "agent/core/02.ba-business-analyst-zh.yaml"
        load_at: "Stage 1 - 業務邏輯完整性驗證"
      - path: "agent/core/03.pm-po-agent-zh.yaml"
        load_at: "Stage 2 - 遷移優先級與 ROI 決策"
      - path: "agent/specialized/dev-senior-zh.yaml"
        load_at: "Stage 2 - 技術棧映射與策略制定"
      - path: "agent/core/06.dev-developer-zh.yaml"
        load_at: "Stage 3-6 - 遷移實作與開發測試循環"
      - path: "agent/core/07.qa-tester-zh.yaml"
        load_at: "Stage 7 - 跨系統驗證與測試規劃"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 8 - CI/CD 重建與部署策略"
    optional_agents:
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 2 - 遷移架構設計（行動端 API 設計與平台技術選型）；Stage 6 - 行動端實作"
        condition: "遷移涉及新增 Android/iOS/macOS 行動平台時載入。Stage 2 需參與確認行動端 API 設計方向、macOS Desktop 技術選型（SwiftUI/Electron/Tauri）及掃碼 SDK 規格，Stage 6 主導行動端實作"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "Stage 7 - 涉及 Mobile 平台測試時"
        condition: "遷移涉及行動裝置功能時載入"
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 2 - 涉及安全敏感模組時"
        condition: "遷移涉及認證/授權/支付/敏感資料模組時載入"
      - path: "agent/specialized/integration-specialist-zh.yaml"
        load_at: "Stage 2 - 涉及硬體整合架構設計時；Stage 6 - 涉及硬體整合實作時"
        condition: "遷移涉及條碼掃描、IoT、第三方 API 整合時載入。需在架構設計階段評估硬體 SDK 選型與跨平台相容性"
      - path: "agent/specialized/performance-engineer-zh.yaml"
        load_at: "Stage 1 - 舊系統效能基準線建立；Stage 7 - 遷移後效能基準對比驗證"
        condition: "建議所有遷移專案都載入。Stage 1 建立舊系統效能基準線（回應時間、吞吐量、資源使用率），Stage 7 與基準線對比驗證無退化"
      - path: "agent/specialized/compliance-officer-zh.yaml"
        load_at: "Stage 2 - 涉及法規合規需求時"
        condition: "遷移涉及支付處理(PCI-DSS)、個人資料(GDPR)、醫療資料(HIPAA)等合規需求時載入"
      - path: "agent/specialized/technical-writer-zh.yaml"
        load_at: "Stage 9 - 知識沉澱與文檔撰寫"
        condition: "大規模遷移建議載入，負責遷移映射手冊、ADR、經驗教訓文檔撰寫"
    workflows:
      - "migration-planning-flow"
      - "requirements-extraction"
      - "change-management"
      - "user-story-design"
      - "api-specification"
      - "consistency-check"
      - "interaction-analysis"
      - "validation-documentation"
      - "sprint-execution"
    sop_path: "scenarios/migration/SOP.md"

  performance:
    primary_agents:
      - path: "agent/specialized/performance-engineer-zh.yaml"
        role: "效能分析與優化"
    supporting_agents:
      - path: "agent/core/05.sd-architect-zh.yaml"
        load_at: "Stage 4 - 架構級優化方案設計（快取層、非同步架構、負載均衡）"
      - path: "agent/specialized/dev-senior-zh.yaml"
        load_at: "Stage 5 - 代碼優化實作（N+1 查詢、演算法複雜度、記憶體洩漏修復）"
      - path: "agent/specialized/qa-automation-zh.yaml"
        load_at: "Stage 2 - 效能基準測試設計；Stage 6 - 效能回歸測試與持續監控"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 4 - 基礎設施優化與監控設定；Stage 7 - 監控體系建立與告警配置"
    optional_agents:
      - path: "agent/specialized/code-analyzer-zh.yaml"
        load_at: "Stage 3 - 代碼級效能熱點分析（CPU Profiling、記憶體洩漏）"
        condition: "需要深入分析代碼複雜度、熱點函數、記憶體洩漏時載入"
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 3 - 安全與效能權衡評估；Stage 5 - 安全敏感模組效能優化"
        condition: "優化涉及加密效能、安全標頭、TLS 配置、支付流程等安全相關效能時載入"
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 2 - 行動端效能基準測試；Stage 4 - 行動端架構優化策略"
        condition: "專案涉及 Android/iOS/macOS 行動端效能優化時載入"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "Stage 6 - 行動端效能驗證與測試"
        condition: "專案涉及行動端效能測試（Cold Start、Frame Rate、掃碼回應）時載入"
      - path: "agent/specialized/integration-specialist-zh.yaml"
        load_at: "Stage 2 - 掃碼/硬體整合效能基準測試；Stage 4 - 掃碼效能優化策略"
        condition: "專案涉及掃碼槍/手機相機掃碼/NFC/藍牙等硬體整合效能優化時載入（掃碼回應時間、識別準確率等）"
    workflows:
      - "performance-optimization-flow"
      - "interaction-analysis"
      - "consistency-check"
    sop_path: "scenarios/performance/SOP.md"

  integration:
    primary_agents:
      - path: "agent/specialized/integration-specialist-zh.yaml"
        role: "第三方整合規劃與跨系統整合設計"
    supporting_agents:
      - path: "agent/core/04.sa-analyst-zh.yaml"
        load_at: "Stage 2 - 整合需求分析、系統邊界定義、System of Record 識別"
      - path: "agent/core/05.sd-architect-zh.yaml"
        load_at: "Stage 3 - 整合架構設計（含規範資料模型、API Gateway、異質技術棧適配層）"
      - path: "agent/core/07.qa-tester-zh.yaml"
        load_at: "Stage 6 - 整合測試規劃（含 Contract Testing、E2E 測試）"
      - path: "agent/core/06.dev-developer-zh.yaml"
        load_at: "Stage 4 - 認證與授權實作（含 Token 管理、Webhook 驗證）"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 7 - 監控與告警設計（含 API 健康檢查、跨系統追蹤）"
    optional_agents:
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 3 - 整合架構設計（安全審查）；Stage 4 - 認證授權設計"
        condition: "整合涉及支付（Stripe/ECPay）、OAuth/SSO、跨系統統一認證、敏感資料交換、或異質技術棧間 API 安全設計時載入"
      - path: "agent/specialized/performance-engineer-zh.yaml"
        load_at: "Stage 7 - 高頻 API 呼叫或大量資料同步時"
        condition: "整合涉及高頻 API 呼叫（>100 req/s）、大量資料同步（CDC/Batch）、即時性要求高（<500ms）時載入"
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 1 - 確認行動端整合範圍（若已知有行動端需求）；Stage 3 - 行動端整合架構設計（離線優先/掃碼 SDK）；Stage 5 - 行動端資料同步策略"
        condition: "整合涉及 Android/iOS/macOS 行動端 API 對接、離線同步、掃碼整合時載入。若行動端為未來規劃，Stage 1 需確認 API 設計相容性"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "Stage 6 - 行動端整合測試（掃碼回應時間、離線同步正確性、推播可靠性）"
        condition: "整合涉及行動端功能測試（掃碼操作、離線同步、推播通知）時載入"
    workflows:
      - "api-specification"
      - "interaction-analysis"
      - "requirements-validation"
      - "document-consistency-check"
      - "user-story-design"
      - "sprint-execution"
    sop_path: "scenarios/integration/SOP.md"
    quickref: "scenarios/integration/SOP_QuickRef.md"

  devops:
    primary_agents:
      - path: "agent/specialized/devops-engineer-zh.yaml"
        role: "CI/CD 設計與部署、容器化、基礎設施自動化、監控告警"
    supporting_agents:
      - path: "agent/core/05.sd-architect-zh.yaml"
        load_at: "Stage 2 - 基礎設施架構設計與環境規劃（含多技術棧架構）"
      - path: "agent/specialized/qa-automation-zh.yaml"
        load_at: "Stage 3 - CI Pipeline 自動化測試整合（含測試覆蓋率門檻設計）"
      - path: "agent/core/06.dev-developer-zh.yaml"
        load_at: "Stage 3-4 - Pipeline 腳本開發、部署腳本實作（含 DB Migration 腳本）"
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 3 - DevSecOps 安全掃描整合（SAST/SCA/Container Scan）"
    optional_agents:
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 1 - 確認行動端 CI/CD 範圍（若已知有行動端需求）；Stage 3 - 行動端建置 Pipeline 設計（Gradle/Xcode/fastlane）；Stage 4 - 行動端分發策略（Firebase App Distribution/TestFlight/Play Store）"
        condition: "專案包含或計畫包含 Android/iOS/macOS 行動端應用時載入。若行動端為未來規劃，Stage 1 需確認 CI/CD 架構是否預留行動端建置空間"
      - path: "agent/specialized/performance-engineer-zh.yaml"
        load_at: "Stage 6 - 效能監控與負載測試整合（k6/Locust 整合 CI/CD）"
        condition: "需要效能基準測試或負載測試整合至 CI/CD 時載入"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "Stage 3 - 涉及行動端自動化測試時（Espresso/XCUITest/Appium CI 配置）"
        condition: "行動端 CI 需整合 UI 自動化測試（Appium/Espresso/XCTest）時載入"
    workflows:
      - "devops-setup-flow"
      - "testing-strategy-flow"
      - "interaction-analysis"
      - "api-specification"
      - "consistency-check"
      - "sprint-execution"
    sop_path: "scenarios/devops/SOP.md"

  testing:
    primary_agents:
      - path: "agent/specialized/qa-lead-zh.yaml"
        role: "測試策略制定"
    supporting_agents:
      - path: "agent/specialized/qa-automation-zh.yaml"
        load_at: "自動化測試設計時"
      - path: "agent/core/06.dev-developer-zh.yaml"
        load_at: "測試實作支援時"
      - path: "agent/core/07.qa-tester-zh.yaml"
        load_at: "驗收測試與品質驗證時"
    optional_agents:
      - path: "agent/specialized/qa-web-tester-zh.yaml"
        load_at: "Web 前端測試時"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "行動端測試時"
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 2 - 安全測試案例設計（SAST/DAST/依賴掃描）"
        condition: "測試策略包含安全測試、滲透測試、合規測試時載入"
      - path: "agent/specialized/performance-engineer-zh.yaml"
        load_at: "Stage 2 - 效能/負載測試案例設計"
        condition: "測試策略包含效能基準測試、負載測試、壓力測試時載入"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 3-4 - 測試環境建置與 CI/CD 整合"
        condition: "需建置測試環境（Docker）或整合 CI/CD Pipeline 時載入"
      - path: "agent/specialized/integration-specialist-zh.yaml"
        load_at: "Stage 2 - 跨模組整合測試案例設計（Contract Testing）"
        condition: "系統含多模組整合（電商+民宿+內容+知識管理等複合型系統）、第三方 API 整合（支付/OAuth/地圖）、QR Code 掃碼整合，或需進行 API Contract Testing（Pact）時載入"
      - path: "agent/specialized/compliance-officer-zh.yaml"
        load_at: "Stage 1 - 測試策略制定（合規測試項目識別）"
        condition: "系統涉及電商金流（PCI-DSS）、個人資料處理（GDPR/個資法）、訂閱付費內容（付費牆保護驗證）、或需要合規測試設計時載入"
    workflows:
      - "testing-strategy-flow"
      - "consistency-check"
      - "sprint-execution"
      - "interaction-analysis"
    sop_path: "scenarios/testing/SOP.md"

  documentation:
    primary_agents:
      - path: "agent/specialized/technical-writer-zh.yaml"
        role: "技術文檔撰寫"
    supporting_agents:
      - path: "agent/core/04.sa-analyst-zh.yaml"
        load_at: "需求文檔審查時"
      - path: "agent/core/05.sd-architect-zh.yaml"
        load_at: "架構文檔審查時"
      - path: "agent/specialized/dev-senior-zh.yaml"
        load_at: "代碼範例審查時"
    optional_agents:
      - path: "agent/specialized/security-engineer-zh.yaml"
        load_at: "Stage 6 - 安全與合規文檔撰寫時"
        condition: "文檔涉及安全架構、威脅模型、安全測試報告時載入"
      - path: "agent/specialized/compliance-officer-zh.yaml"
        load_at: "Stage 6 - 合規對照文檔撰寫時"
        condition: "文檔涉及法規合規（GDPR/PCI-DSS/ISO 27001）時載入"
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 3 - 行動端架構文件撰寫時；Stage 6 - 多平台安全文檔撰寫時"
        condition: "文檔涉及 Android/iOS/macOS 行動端架構（QR Code 掃描規格、App 架構、Mobile API）或行動端安全時載入"
      - path: "agent/core/07.qa-tester-zh.yaml"
        load_at: "Stage 2-5 - 文檔驗收測試（技術準確性、範例可執行性）"
        condition: "需要驗證文檔技術準確性、程式碼範例可執行性時載入"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 5 - Docs as Code CI/CD Pipeline 建置"
        condition: "需建置文檔自動化部署（GitHub Pages/Vercel）或文檔 CI Pipeline 時載入"
    workflows:
      - "documentation-flow"
      - "documentation-reconstruction"
      - "consistency-check"
      - "api-specification"
      - "interaction-analysis"
    sop_path: "scenarios/documentation/SOP.md"

  security:
    primary_agents:
      - path: "agent/specialized/security-engineer-zh.yaml"
        role: "安全評估與設計、威脅建模、漏洞分析"
      - path: "agent/specialized/compliance-officer-zh.yaml"
        role: "合規差距分析、法規解讀、稽核準備"
    supporting_agents:
      - path: "agent/specialized/qa-lead-zh.yaml"
        load_at: "Stage 4 - 安全測試策略與規劃"
      - path: "agent/core/04.sa-analyst-zh.yaml"
        load_at: "Stage 1 - 需求分析、威脅建模資料流圖"
      - path: "agent/core/05.sd-architect-zh.yaml"
        load_at: "Stage 2 - 安全架構設計、加密策略"
      - path: "agent/specialized/dev-senior-zh.yaml"
        load_at: "Stage 3 - 安全加固實施、漏洞修復"
      - path: "agent/specialized/devops-engineer-zh.yaml"
        load_at: "Stage 2-3 - 安全 CI/CD Pipeline 建置"
    optional_agents:
      - path: "agent/core/02.ba-business-analyst-zh.yaml"
        load_at: "Stage 1 - 合規差距分析的業務需求分析"
        condition: "涉及業務合規需求（金融/醫療/電商法規）時載入"
      - path: "agent/specialized/technical-writer-zh.yaml"
        load_at: "Stage 5 - 安全文檔與合規報告撰寫"
        condition: "需要正式安全文檔交付時載入"
      - path: "agent/core/03.pm-po-agent-zh.yaml"
        load_at: "Stage 2 - 安全預算和優先級決策"
        condition: "安全改善涉及預算決策時載入"
      - path: "agent/specialized/sd-mobile-architect-zh.yaml"
        load_at: "Stage 2 - 行動端安全架構設計"
        condition: "涉及 Android/iOS/macOS 行動平台安全時載入"
      - path: "agent/specialized/qa-mobile-tester-zh.yaml"
        load_at: "Stage 4 - 行動端安全測試"
        condition: "涉及行動裝置安全測試時載入"
      - path: "agent/specialized/qa-web-tester-zh.yaml"
        load_at: "Stage 4 - Web 前端安全測試（XSS/CSRF/CSP 驗證）"
        condition: "涉及 Web 前端安全測試、跨瀏覽器安全驗證時載入"
      - path: "agent/specialized/integration-specialist-zh.yaml"
        load_at: "Stage 1 - 第三方整合安全邊界分析；Stage 2 - 支付整合安全設計（Stripe/ECPay）；Stage 3 - Webhook 簽章驗證、OAuth 安全實施"
        condition: "系統涉及支付整合（Stripe/ECPay/藍新）、OAuth/SSO 第三方登入、Webhook 接收、或跨系統 API 安全設計時載入。複合型系統（電商+民宿+內容+知識管理）的整合點安全審查必載"
    workflows:
      - "security-assessment"
      - "consistency-check"
      - "api-specification"
      - "interaction-analysis"
      - "testing-strategy-flow"
      - "sprint-execution"
    sop_path: "scenarios/security/SOP.md"
```

### Agent 載入指令格式

**Claude Code 自動執行**:
```
當偵測到情境為 [scenario] 時，執行：
1. READ: auto_load_config.[scenario].primary_agents[*].path
2. APPLY: 載入所有 Primary Agents 的規則和人格設定
3. STORE: Supporting Agents 列表，按需載入
4. LOAD: 對應的 Workflows
5. READY: 開始執行 SOP
```

---

## 📋 情境-Workflow 快速映射表 (v0.09)

### 核心情境 Workflows (完整保留)

| 情境簡稱 | 中文名稱 | Primary Agent | Supporting Agents | 適用場景 | CI/CD 基線 | SOP QuickRef |
|---------|---------|---------------|-------------------|---------|-----------|-------------|
| `greenfield` | 新專案開發 | pm-po + sa-analyst | sd-architect, qa-tester | 從零開始的新專案 | L0 + L1 + SAST + 🔔Notify (Standard) | - |
| `brownfield` | 舊專案維護 | sa-analyst + dev-senior | sd-architect, ba, pm-po, code-analyzer, qa-tester, dev, devops | 既有系統修改維護 | L0 + L1 + SAST + Regression + 🔔Notify (Standard) | - |
| `refactoring` | 系統重構 | sd-architect + code-analyzer | sa, ba, pm-po, dev-senior, qa-tester, dev, devops ⭐ | 代碼重構/部分技術棧替換 | L0 + L1 + SAST + Mutation + 🔔Notify (Standard) | - |
| `migration` | 技術棧遷移 | sd-architect + sa-analyst | code-analyzer, ba, pm-po, dev-senior, dev, qa-tester, devops ⭐ | **全棧遷移**/DB遷移/系統現代化 | L0 + L1 + SAST + Container + L2(Contract) + L3(Canary) + 🔔Notify (Advanced) | - |
| `performance` | 效能調校 | performance-engineer | sd-architect, dev-senior, **qa-automation** ⭐ | 系統效能優化 | L0 + L1 + Container + 🔴Benchmark + 🔔Notify (Advanced) | - |
| `integration` | 第三方整合 | integration-specialist | sd-architect, qa-tester, dev-developer, devops-engineer ⭐ | API 整合開發 | L0 + L1 + SAST + Container + Contract + 🔔Notify (Advanced) | ✅ 已建立 |
| `devops` | DevOps/CI/CD | devops-engineer | sd-architect, qa-automation, dev-developer, security-engineer ⭐ | 部署自動化 | L0 + L1 + IaC SAST + Container + L2 + L3 + 🔔Notify (Advanced) | - |
| `testing` | 測試與 QA | qa-lead | qa-automation, dev-developer, **qa-tester** ⭐ | 測試策略制定 | L0 + L1 + SAST + L2(Full) + 🔔Notify (Standard) | - |
| `documentation` | 文件維護 | technical-writer | sa-analyst, sd-architect, **dev-senior** ⭐ | 知識文件管理 | L0 + 📝DocPipeline + 🔔Notify(選配) (Basic) | - |
|  |  |  | 選用: **security-engineer**, **compliance-officer**, **sd-mobile-architect** ⭐ v0.09新增 | 安全合規文檔 |  |  |
| `security` | 安全與合規 | security-engineer + **compliance-officer** ⭐ | sa-analyst, sd-architect, qa-lead, dev-senior, devops ⭐ | 安全評估與合規檢查 | L0 + L1 + SAST + Container + DAST + Compliance + 🔔Notify (Enhanced) | - |

> **🔒 CI/CD 基線說明 (v0.09 CI/CD 強化)**:
> - **L0 (Layer 0)**: Security Baseline — Secret Detection + SCA + License Compliance（**所有情境強制**）
> - **L1 (Layer 1)**: Build & Verify — Lint + Build + Unit Test + Coverage Gate
> - **L2 (Layer 2)**: Quality Assurance — Integration/Contract/Mutation/Perf Test（情境選配）
> - **L3 (Layer 3)**: Deploy & Validate — Staging Deploy + Canary + Rollback Gate（情境選配）
>
> **🛡️ 安全掃描等級 (P1 Security Integration)**:
> - **Basic**: 僅 L0 → `documentation`
> - **Standard**: L0 + SAST → `greenfield`, `brownfield`, `refactoring`, `testing`
> - **Advanced**: L0 + SAST + Container Scan → `migration`, `integration`, `performance`, `devops`
> - **Enhanced**: L0 + SAST + Container + DAST + Compliance Gate → `security`
>
> 📖 **Layer 0 詳細配置**: [Layer0_Security_Baseline_Template.md](docs_template/scenario_specific/devops/Layer0_Security_Baseline_Template.md)
> 📖 **安全掃描整合**: [Security_Scan_Integration_Template.md](docs_template/scenario_specific/devops/Security_Scan_Integration_Template.md)
>
> **⚡ Performance Benchmark Gate (P2)**:
> - **🔴 強制**: `performance` — Micro-Benchmark (PR) + Full Load Test (Nightly)
> - **⚠️ 選配**: `greenfield`, `brownfield`, `refactoring`, `migration` — 僅 Micro-Benchmark
> - **❌ 不適用**: 其他情境
>
> 📖 **效能基準關卡**: [Performance_Benchmark_Gate_Template.md](docs_template/scenario_specific/devops/Performance_Benchmark_Gate_Template.md)
>
> **📝 Documentation Pipeline (P2)**:
> - **🔴 強制**: `documentation` — Doc Lint + Link Check + Build + Deploy
> - **⚠️ 選配**: `greenfield`, `brownfield`, `migration`, `integration` — 僅 Doc Lint + Link Check
> - **❌ 不適用**: 其他情境
>
> 📖 **文檔 Pipeline**: [Documentation_Pipeline_Template.md](docs_template/scenario_specific/devops/Documentation_Pipeline_Template.md)
>
> **🔔 Event-Driven Agent Notification (P3)**:
> - **🔴 強制**: 所有程式碼相關情境 — PR 事件通知（Agent 結果匯聚 → PR Comment + Slack）
> - **🔴 強制部署通知**: `greenfield`, `brownfield`, `migration`, `integration`, `devops`, `security`
> - **⚠️ 選配**: `documentation` — 文檔變更輕量通知
> - **情境專屬觸發**: `migration` (canary 進度)、`refactoring` (mutation-test)、`security` (enhanced-SAST)、`performance` (benchmark)
>
> 📖 **Agent 通知系統**: [Event_Driven_Agent_Notification_Template.md](docs_template/scenario_specific/devops/Event_Driven_Agent_Notification_Template.md)

**v0.09 說明**: Integration 情境已有 QuickRef (5分鐘快速參考)，其他情境的 QuickRef 將陸續建立。

**Phase 2 優化** ⭐：
- Performance 情境新增 qa-automation (效能測試自動化、持續監控)
- Documentation 情境新增 dev-senior (複雜技術文檔審查、代碼範例)
- Testing 情境新增 qa-tester (驗收測試、品質驗證)，可選載入 qa-web-tester / qa-mobile-tester
- Testing 情境補齊 Workflows: sprint-execution (測試迭代執行)、interaction-analysis (前後端互動測試)
- Security 情境升級 compliance-officer 為 Primary Agent，新增 sa-analyst, sd-architect, dev-senior, devops 為 Supporting Agents ⭐
- Security 情境補齊 Workflows: api-specification (API 安全審查)、interaction-analysis (前後端安全互動) ⭐

### 基礎 Workflows (完整保留)

| Workflow 簡稱 | Primary Agent | Supporting Agents | 說明 |
|--------------|---------------|-------------------|------|
| `requirements-extraction` | sa-analyst | ba-business-analyst | 需求提取 |
| `validation-documentation` | sa-analyst | ba, pm-po | 需求驗證 |
| `user-story-design` | sa-analyst + sd-architect | qa-tester | 設計規劃 |
| `change-management` | sa-analyst | ba, pm-po, sd-architect, qa | 變更管理 |
| `api-specification` | sd-architect | qa-tester | API 生成 |
| `consistency-check` | sa-analyst | sd-architect, qa-tester | 品質檢查 |
| `interaction-analysis` | sd-architect | dev-developer | 交互分析 |
| `sprint-execution` | dev-developer | qa-tester, sd-architect, devops-engineer | Sprint 執行與開發測試 |

---

## 🎯 Claude Code Skills 整合 (v0.09+ 新增)

> **🔴 重要**: AISDLC v0.09 提供 33 個 Claude Code Skills，可透過 `/skill-name` 指令快速觸發 AISDLC 的 Agent、情境和 Workflow 能力。

### Skills 部署

安裝腳本 (`tools/init_project.sh`) 會自動將 `.claude/skills/` 複製到專案根目錄。Claude Code 啟動時自動掃描 `.claude/skills/<name>/SKILL.md` 發現所有 Skills。

### Skills 概覽

| 類別 | 數量 | 說明 |
|------|------|------|
| DevOps | 5 | CI/CD、容器、監控 |
| Integration | 10 | 第三方服務整合 |
| Code Quality | 4 | 分析、重構、效能、測試 |
| Security/Compliance/Docs | 3 | 安全、合規、文檔 |
| Agents | 6 | SA/BA/SD/QA/Dev/PM |
| Workflows | 2 | Sprint/Release |
| Scenario/Dev | 3 | 棕地分析、資料庫遷移、行動開發 |
| **總計** | **33** | - |

### 常用 Skills 快速參考

```bash
# Agent Skills（觸發 AISDLC Agent 角色）
/sa-analyst             # SA 需求分析
/ba-analyst             # BA 業務驗證
/sd-architect           # SD 架構設計
/qa-testing             # QA 測試策略
/dev-review             # Dev 代碼審查
/pm-planning            # PM 產品規劃

# Scenario Skills（觸發 AISDLC 情境 SOP）
/brownfield-analysis    # 系統分析
/devops-github-actions  # GitHub Actions CI/CD
/integration-stripe     # Stripe 支付整合
/security-audit         # 安全審計

# Workflow Skills（觸發協作流程）
/sprint-planning        # Sprint 規劃
/code-review            # 代碼審查流程
/release-management     # 發布管理
```

### Skills 與 Agent 自動載入的關係

- **Skills** 是快捷入口，觸發後會自動引用對應的 Agent YAML 和 SOP
- **Agent 自動載入** 是底層機制，完整 SOP 執行時自動觸發
- 兩者互補：Skills 適合快速任務，Agent 自動載入適合完整 SOP 執行

📖 **完整 Skills 清單**: [.claude/skills/README.md](.claude/skills/README.md)

---

## 📚 v0.09 文檔模板系統 (簡化架構 - v0.03 繼承)

### Tier 1: 核心模板 (7個 - 必須掌握)

| 模板名稱 | 用途 | v0.03/v0.09 特性 |
|---------|------|-----------|
| **PRD_Universal_Template.md** | 產品需求文檔 | ✨ 統一模板 (整合 4 個變體) |
| **FRD_Universal_Template.md** | 功能需求文檔 | ✨ 統一模板 (整合 4 個變體) |
| **SRD_Module_Template.md** | 系統設計文檔 | ✅ 保持不變 |
| **API_Specification_Template.md** | API 規格文檔 | ✅ 保持不變 |
| **AT_Module_Template.md** | 驗收測試文檔 | ✅ 保持不變 |
| **Test_Report_Template.md** | 測試報告 | ✅ 保持不變 |
| **Technical_Doc_Template.md** | 技術文檔 | 🔄 待建立 |

**使用方式**:
- **PRD**: 使用 `PRD_Universal_Template.md`，在文檔元數據選擇專案類型 (Greenfield/Brownfield/Sprint/Integration)
- **FRD**: 使用 `FRD_Universal_Template.md`，在文檔元數據選擇需求類型 (Standard/Refactoring/Performance/Integration)

### Tier 2: 情境專用模板 (按需使用)
位於 `docs_template/scenario_specific/`，根據情境載入對應模板。

### Tier 3: 支援模板 (選用)
位於 `docs_template/support/`，用於操作、監控等輔助文檔。

---

## 🎯 Agent 角色說明 (v0.09 - 完整保留)

> 📝 **v0.09 檔案命名規則**：
> - **核心 Agents**：`0X.{name}-zh.yaml`（如：`04.sa-analyst-zh.yaml`）
> - **專業化 Agents**：`{name}-zh.yaml`（如：`performance-engineer-zh.yaml`）
> - 所有 Agent 配置均為中文版（`-zh.yaml`後綴）
> - 英文版已歸檔至 `agent/core/archive_en/`，僅供術語對照參考

### 核心 Agents (agent/core/)
- **`04.sa-analyst-zh.yaml`** (Amanda): 系統分析師 - 需求分析與驗證
- **`02.ba-business-analyst-zh.yaml`** (Beatrice): 業務分析師 - 商業需求驗證
- **`03.pm-po-agent-zh.yaml`** (Victoria): 產品經理 - 商業價值決策
- **`05.sd-architect-zh.yaml`** (Marcus): 系統設計師 - 技術架構設計
- **`06.dev-developer-zh.yaml`** (David): 開發工程師 - 實作評估
- **`07.qa-tester-zh.yaml`** (Quincy): 測試工程師 - 驗收準則與測試

### 專業化 Agents (agent/specialized/)

**通用專業化 Agents:**
- **`code-analyzer-zh.yaml`** (CodeX): 代碼分析專家 - 代碼品質與重構
- **`performance-engineer-zh.yaml`** (Perf): 效能工程師 - 系統效能優化
- **`integration-specialist-zh.yaml`** (IntegX): 整合專家 - 第三方整合
- **`devops-engineer-zh.yaml`** (DevOps): DevOps 工程師 - CI/CD 與部署
- **`dev-senior-zh.yaml`** (Senior): 資深開發者 - 技術評審
- **`security-engineer-zh.yaml`** (SecEng): 安全工程師 - 安全評估
- **`compliance-officer-zh.yaml`** (CompOff): 合規專員 - 合規檢查
- **`technical-writer-zh.yaml`** (DocX): 技術文件專家 - 文檔撰寫

**QA 專業化 Agents:**
- **`qa-lead-zh.yaml`** (QA-Lead): 測試負責人 - 測試策略制定
- **`qa-automation-zh.yaml`** (AutoQA): 自動化測試專家 - 測試自動化
- **`qa-web-tester-zh.yaml`** (WebQA): Web 測試專家 - Web 應用測試
- **`qa-mobile-tester-zh.yaml`** (MobileQA): Mobile 測試專家 - iOS/Android 測試

**架構專業化 Agents:**
- **`sd-web-architect-zh.yaml`** (WebArch): Web 架構師 - Web 技術架構設計
- **`sd-mobile-architect-zh.yaml`** (MobileArch): Mobile 架構師 - iOS/Android 架構設計

**v0.09 保證**: 所有 Agents 配置完全保留，僅目錄結構優化 (詳見 UPGRADE_FROM_V05.md)。

### Specialized Agent 推薦條件 (快速參考)

**常用 Specialized Agents**:
- **code-analyzer**: 代碼品質分析、重構、技術債務評估
- **performance-engineer**: 效能優化、回應時間改善、併發處理
- **integration-specialist**: 第三方 API 整合、OAuth/SSO 認證
- **devops-engineer**: CI/CD 設定、容器化部署、基礎設施自動化
- **security-engineer**: 安全需求 (OWASP)、敏感數據處理、合規要求

📖 **完整推薦條件表格**: [guides/system/agent/Specialized_Agent_Selection_Guide.md](guides/system/agent/Specialized_Agent_Selection_Guide.md)
- 14 個 Specialized Agents 的詳細推薦條件
- 典型觸發關鍵詞與使用場景
- 不推薦場景與替代方案

---

## 🔧 按需載入執行規則 (v0.09 完全繼承)

### 1. 智能情境識別與自動載入
```yaml
when_user_starts_project:
  step_1: 識別專案情境類型 (新專案/舊專案/重構等)
  step_2: 識別平台類型 (Web/iOS/Android/跨平台)
  step_3: 🔴 查詢「Agent 自動載入配置表」取得對應配置
  step_4: 🔴 自動讀取並載入 Primary Agents YAML
  step_5: 🔴 記錄 Supporting Agents（按需載入）
  step_6: 載入對應 Workflows
  step_7: [v0.09 保留] 推薦使用 QuickRef (如有)
  step_8: 顯示載入狀態並套用規則
  step_9: 推薦統一模板並說明情境選擇方式
  step_10: 開始執行情境專用 SOP
```

### 1.0 Agent 自動載入詳細流程 (v0.09+ 新增)

**🔴 CRITICAL**: 以下流程為強制執行

```yaml
auto_load_sequence:
  phase_1_read_config:
    action: "讀取 auto_load_config.[scenario]"
    output: "Primary Agents 列表、Supporting Agents 列表、Workflows 列表"

  phase_2_load_primary:
    action: "讀取每個 Primary Agent 的 YAML 檔案"
    apply:
      - "persona.core_principles → 套用為 Agent 行為準則"
      - "collaboration_rules → 套用為協作規則"
      - "scenario_usage → 確認當前情境適用性"
    display: "顯示已載入的 Agent 名稱和角色"

  phase_3_store_supporting:
    action: "儲存 Supporting Agents 列表"
    trigger: "當執行到對應 Stage 時自動載入"
    example: "Stage 3 開始時，自動載入 sd-architect-zh.yaml"

  phase_4_load_workflows:
    action: "載入對應的 Workflow 定義"
    path: "workflow/*.md"

  phase_5_ready:
    action: "顯示載入確認訊息"
    proceed: "開始執行 SOP"
```

**Supporting Agent 按需載入範例**:
```
執行到 Stage 3 - 技術架構設計：
🔄 自動載入 Supporting Agent...
✅ 載入: agent/core/05.sd-architect-zh.yaml (Marcus - SD)
✅ 套用 SD 規則: 架構設計原則、技術決策準則
📋 Marcus 已加入協作，負責技術架構設計
```

### 1.1 Agent 載入時序說明

**📋 為什麼需要按需載入？**

AISDLC 採用 **On-Demand Loading** 機制，原因：
- **Token 效率**: 初始載入僅需 ~200 tokens（vs 完全載入 ~2000 tokens）
- **聚焦任務**: 每個階段只載入相關 Agents，減少干擾
- **成本優化**: 減少 70-85% 的 Token 使用量

**🔄 基本載入原則**:
1. **Primary Agents** (PM/PO, SA) - 專案啟動時立即載入，全程參與
2. **Supporting Agents** (BA, SD, QA, Dev) - 按需載入，在特定階段參與
3. **Specialized Agents** - 根據專案需求選擇性載入

**載入時機範例**:
- **Stage 1-2**: PM/PO + SA (需求收集與分析)
- **Stage 2**: + BA (需求驗證)
- **Stage 3**: + SD-Architect (技術選型與架構設計)
- **Stage 4**: + QA (驗收準則定義)
- **Stage 5**: + Dev (工時估算與可行性評估)

📖 **詳細的 9 階段 Agent 載入對照表**: [scenarios/greenfield/SOP.md](scenarios/greenfield/SOP.md)
- 完整的 Stage-by-Stage Agent 載入說明
- 各階段 Agent 職責與協作模式
- 平台相關 Agent 選擇指南

### 2. v0.09 情境識別問答流程
```
LLM 主動詢問：
「請告訴我您的專案情況：

1. 這是新專案還是既有專案？
   - 新專案 (Greenfield)
   - 既有專案維護/修改 (Brownfield)
   - 系統重構 (Refactoring)
   - 技術棧遷移 (Migration) ✨ 全棧替換/DB遷移/系統現代化
   - 效能優化 (Performance Tuning)
   - 第三方整合 (Integration) ✨ 有 5分鐘 QuickRef
   - DevOps 設定 (CI/CD)
   - 測試策略 (Testing)
   - 文件整理 (Documentation)
   - 安全合規 (Security)

2. 目標平台是什麼？
   - Web 應用
   - iOS 原生
   - Android 原生
   - 跨平台 (React Native / Flutter)
   - 後端 API
   - 其他

基於您的回答，我會：
✅ 載入最適合的 Agents 和 Workflows
✅ 推薦使用統一模板 (PRD/FRD Universal)
✅ 提供 QuickRef 快速參考 (如有)
✅ 說明如何選擇文檔模板情境標籤
```

### 3. v0.09 載入顯示範例
```
用戶：我要整合第三方支付 API

LLM 回應：
🔄 正在進行情境分析...
✅ 識別情境：Integration (第三方整合)
✅ 推薦 Workflow：integration-analysis-flow

🔄 正在載入 AISDLC v0.09 配置...
✅ 載入 integration-specialist (IntegX) - 整合專家
✅ 載入 sd-architect (Marcus) - 系統設計師
✅ 載入 qa-tester (Quincy) - 測試工程師
✅ 套用所有 Agent 規則和整合最佳實踐

✨ v0.09 特性 (v0.03 繼承)：
📖 Integration QuickRef 可用！
   - 5分鐘快速掌握核心流程
   - 路徑: scenarios/integration/SOP_QuickRef.md
   - 建議先閱讀 QuickRef 再執行完整 SOP

📝 建議使用文檔模板：
   - PRD: PRD_Universal_Template.md (選擇 Integration 情境)
   - FRD: FRD_Universal_Template.md (選擇 Integration 情境)

✅ 準備就緒

開始執行第三方整合流程...
建議先閱讀 QuickRef (5分鐘)，然後依序進行：
1. API 研究 (30min)
2. 認證設計 (20min)
3. 資料轉換 (30min)
4. 錯誤處理 (20min)
5. 測試計畫 (30min)
```

### 4. 核心執行原則 (完整保留)
- **優先級**：Agent Rules > Scenario Context > Workflow Steps > User Input
- **確認點**：所有 🔴 標記的點必須等待確認
- **不確定就問**：遇到模糊情況立即詢問
- **情境感知**：根據專案情境調整建議和檢查點

### 5. Token 效率策略 (v0.09 保留)
- ✅ 只載入當前情境需要的 agents
- ✅ 統一模板減少模板選擇 token 消耗
- ✅ QuickRef 降低初次學習 token 消耗
- ✅ 同一對話中已載入的 agent 不重複載入
- ✅ 情境切換時智能卸載不需要的 agents
- ❌ 不預載所有情境的配置

---

## ⚡ 快速啟動指令（含自動載入）

### 🚀 推薦方式：One-Command Launch (< 1 分鐘)

**格式**：`AISDLC [scenario-code] [project-brief]`

**範例**：
```
- 「AISDLC greenfield-web 電商網站,功能包含購物車結帳」
- 「AISDLC integration Stripe 支付整合」
- 「AISDLC performance 後端 API 回應時間優化」
```

**支援的 scenario-code**：
`greenfield` / `brownfield` / `refactoring` / `migration` / `performance` / `integration` / `devops` / `testing` / `documentation` / `security`

**🔴 AI 自動處理（含 Agent 自動載入）**：
```
1. 解析情境代碼 → 查詢 auto_load_config.[scenario]
2. 自動載入 Primary Agents（讀取 YAML、套用規則）
3. 記錄 Supporting Agents（按需載入清單）
4. 載入對應 Workflows
5. 提取初始需求
6. 推薦文檔模板和 QuickRef (如有)
7. 直接進入 SOP 執行階段
```

### 🎯 Agent 自動載入確認輸出

當使用快速啟動指令時，系統會顯示：
```
📌 AISDLC v0.09 啟動
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔍 情境識別: [scenario-name]
📋 專案概述: [project-brief]

🤖 Agent 自動載入:
┌─────────────────────────────────────────┐
│ Primary Agents (已載入)                  │
├─────────────────────────────────────────┤
│ ✅ [Agent Name] - [Role]                │
│ ✅ [Agent Name] - [Role]                │
├─────────────────────────────────────────┤
│ Supporting Agents (按需載入)             │
├─────────────────────────────────────────┤
│ 📋 [Agent Name] - [Load Condition]      │
│ 📋 [Agent Name] - [Load Condition]      │
└─────────────────────────────────────────┘

📂 已載入 Workflows:
   - [workflow-1]
   - [workflow-2]

📖 SOP 路徑: [sop-path]
📖 QuickRef: [quickref-path] (如有)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 準備就緒，開始執行...
```

---

### 🎯 互動方式：Interactive Quick Start (< 2 分鐘)

**指令**：`AISDLC 快速啟動` 或 `AISDLC quick-start`

AI 會透過 3 個問題引導您選擇：
1. 專案類型 (新專案/既有專案/重構/其他)
2. 主要目標 (開發/優化/整合/測試/文檔)
3. 目標平台 (Web/iOS/Android/跨平台/後端)

---

### 📋 其他啟動方式

**直接指定情境**：
```
「請載入 AISDLC v0.09，我的專案是 [情境類型]」
```

**使用情境快捷碼**：
```
「AISDLC v0.09 [情境碼]」
```

📖 **完整啟動指南**: [guides/user/onboarding/QUICK_START_GUIDE.md](guides/user/onboarding/QUICK_START_GUIDE.md)
- 6 種啟動方式詳細說明
- 預設範本 (ecommerce-web, mobile-app, api-service 等)
- 啟動時間對比與選擇建議

---

## 🔍 執行驗證 (v0.09)

### 情境識別檢查
- [ ] 是否正確識別了專案情境？
- [ ] 是否正確識別了目標平台？
- [ ] 載入的 Agents 是否符合情境需求？
- [ ] 推薦的 Workflow 是否適合當前任務？
- [ ] **[v0.09]** 是否推薦了統一模板並說明情境選擇？
- [ ] **[v0.09]** 是否提供了 QuickRef (如有)？

### Agent 載入檢查
- [ ] 是否載入了正確的 Primary Agents？
- [ ] 是否載入了所有 Supporting Agents？
- [ ] 平台特化 Agents 是否按需載入？
- [ ] 是否準備好執行所有 Agent 規則？

### v0.09 文檔模板檢查
- [ ] 是否推薦使用統一模板 (PRD/FRD Universal)？
- [ ] 是否說明如何選擇情境標籤？
- [ ] 文件模板路徑是否正確 (docs_template/core/...)?
- [ ] 是否提供了情境使用指引？

### 情境適配檢查
- [ ] 文件模板是否符合情境需求？
- [ ] 檢查點是否包含情境特定項目？
- [ ] 是否套用了平台最佳實踐？
- [ ] **[v0.09]** 是否提供了 QuickRef 路徑 (如有)？

---

## 📊 Token 效率對比 (v0.09 保留)

```yaml
v0.02 情境載入:
  初始化 + 情境識別: ~250 tokens
  情境專用 agents: +350 tokens
  總計: ~600 tokens

v0.09 優化載入:
  初始化 + 情境識別: ~250 tokens
  情境專用 agents: +350 tokens
  統一模板說明: +50 tokens (一次性)
  總計: ~650 tokens (初次) / ~600 tokens (後續)

優勢:
  - 文檔選擇 token 消耗: -80% (不需比較 4 個模板)
  - QuickRef 學習 token 消耗: -95% (5分鐘 vs 完整 SOP)
  - 記憶負擔: -82.5% (7 個核心模板 vs 40 個)
```

---

## 🎯 情境切換機制 (v0.09 保留)

### 在同一對話中切換情境
```
用戶：現在我要切換到效能優化

LLM：
🔄 正在切換情境...
✅ 卸載不需要的 Agents
✅ 載入 performance-engineer (Perf)
✅ 載入 sd-architect (Marcus)
✅ 載入 dev-senior (Senior)
✅ 情境切換完成

📝 文檔模板建議：
   - FRD: 使用 FRD_Universal_Template.md (選擇 Performance 情境)

現在進入效能調校模式，會進行：
1. 效能基準測試
2. 瓶頸分析
3. 優化策略制定
4. 優化實施與驗證
```

---

## 🎯 v0.09 初始化確認

當你載入此配置時，請回應：

```
✅ AISDLC v0.09 已啟用！

核心特性：
- 🤖 Agent 自動載入機制（情境識別後自動載入對應 Agents）
- 統一模板系統 (PRD/FRD 整合，記憶負擔降低 82.5%)
- 21 個專業 Agents (7 核心 + 14 專用)
- 10 大開發情境全覆蓋
- 按需載入機制 (初始化 ~200 tokens，節省 70-85%)

🔴 Agent 自動載入配置已載入：
- 10 個情境的 Primary/Supporting Agents 配置
- 自動載入觸發條件定義
- Supporting Agents 按需載入時機

映射表已載入：
- 10 個情境專用 Workflows
- 7 個核心 Workflows
- 情境-Agent-Workflow 自動綁定

執行方式：
1. 情境識別 → 🔴 自動載入 Primary Agents → 記錄 Supporting Agents
2. 載入 Workflows → QuickRef (如有) → 執行 SOP
3. Stage 切換時 → 🔴 自動載入對應 Supporting Agents
4. 情境切換 → 智能卸載/載入管理

🚀 快速開始：
- One-Command: 「AISDLC [scenario-code] [project-brief]」
- 互動模式: 「AISDLC 快速啟動」
- 指定情境: 「AISDLC v0.09 [情境碼]」

準備接收情境指令！請告訴我您的專案情況。
```

---

## 📚 v0.09 快速參考

### 統一模板使用範例

**建立 Integration 專案的 PRD**:
```markdown
1. 使用: docs_template/core/prd/PRD_Universal_Template.md
2. 在文檔元數據設定: 專案類型 = Integration
3. 展開 "[For Integration Only] 整合專案資訊"
4. 參考「情境使用指引表」填寫對應章節
```

**建立 Performance 優化的 FRD**:
```markdown
1. 使用: docs_template/core/frd/FRD_Universal_Template.md
2. 在文檔元數據設定: 需求類型 = Performance
3. 展開 "[For Performance Only] 效能優化資訊"
4. 重點填寫第 8 章「效能優化需求」
```

### QuickRef 使用範例

**執行 Integration 專案**:
```markdown
1. 快速掌握 (5分鐘):
   閱讀 scenarios/integration/SOP_QuickRef.md

2. 詳細執行:
   參考 scenarios/integration/SOP.md

3. 進階處理:
   查閱 scenarios/integration/SOP_DeepDive.md
```

---

---

**版本**: v0.09
**發布日期**: 2026-03-20
**最後更新**: 2026-03-20
**維護者**: AISDLC Framework Team

📖 **相關文檔**:
- [檔案目錄規則](FILE_DIRECTORY_RULES.md)
- [下次升版 SOP](AISDLC_v0.10_UPGRADE_SOP.md)
- [Specialized Agent 選擇指南](guides/system/agent/Specialized_Agent_Selection_Guide.md)
- [快速啟動指南](guides/user/onboarding/QUICK_START_GUIDE.md)
