# AISDLC 檔案目錄維護與分類規則
# AISDLC File & Directory Maintenance Rules

> **🔴 極度重要文檔 🔴**
>
> 本檔案是 AISDLC 框架的**核心維護文檔**，定義版本中所有目錄與檔案的結構和規則。
>
> - 📍 **文檔定位**: 版本目錄與檔案的**權威定義文檔**
> - 🔄 **維護要求**: 任何目錄或檔案的新增、修改、刪除時，**必須立即更新**此文檔
> - ⚠️ **使用場景**: 所有檔案操作（寫入、歸檔、刪除、維護）都應遵循此規則
> - 📋 **內容範圍**: 詳細描述每個目錄的用途、每個檔案的定義和維護規則

---

**版本**: v0.09
**最後更新**: 2026-03-20
**文檔類型**: 架構設計文檔 | 核心維護文檔
**適用範圍**: AISDLC v0.09 及後續版本
**英文檔名**: FILE_DIRECTORY_RULES.md

---

## 🔴 **維護規範** - 極度重要！

### 📌 本檔案的唯一用途

**本檔案僅用於定義目錄與檔案的結構和規則**，包括：

1. ✅ **目錄結構定義** - 詳細描述每個目錄的用途和內容
2. ✅ **檔案分類規則** - 定義哪些檔案屬於哪個類別（層次 1/2/3）
3. ✅ **升版拷貝規則** - 明確哪些檔案應該拷貝，哪些不應該拷貝
4. ✅ **維護操作規範** - 所有檔案寫入、歸檔、刪除的標準流程
5. ✅ **命名規範** - 檔案和目錄的命名標準

### ❌ 不應記錄在本檔案的內容

以下內容**不應**寫入本檔案：

- ❌ **詳細的歷史維護記錄** → 應記錄在 `build/logs/CHANGELOG_v0.0X.md`
- ❌ **詳細的升版執行記錄** → 應記錄在 `AISDLC_v0.0X_UPGRADE_SOP.md`
- ❌ **詳細的檔案變更說明** → 應記錄在 `build/reports/phase/` 報告中
- ❌ **逐一列舉所有檔案** → 應使用 `tree` 命令查看實際結構

### 🎯 維護原則

> **檔案目錄規則應該是精簡、權威的結構定義，而不是詳細的歷史記錄文檔。**

- **權威原則**: 本檔案是目錄結構的唯一權威來源
- **精簡原則**: 只定義規則和結構，避免冗長的歷史記錄
- **即時更新原則**: 目錄/檔案變更後立即更新此文檔
- **易查閱原則**: 維護者能快速找到所需的結構定義和規則

---

## 🎯 文檔目的與定位

### 主要目的

本文檔是 AISDLC 框架的**檔案目錄權威定義文檔**，明確規範：

1. **目錄結構定義** - 詳細描述每個目錄的用途和內容
2. **檔案分類規則** - 定義哪些檔案屬於哪個類別（層次 1/2/3）
3. **升版拷貝規則** - 明確哪些檔案應該拷貝，哪些不應該拷貝
4. **維護操作規範** - 所有檔案寫入、歸檔、刪除的標準流程
5. **變更追蹤機制** - 目錄或檔案變更時的更新要求

### 文檔定位

| 屬性 | 說明 |
|------|------|
| **重要程度** | 🔴 極度重要 - 核心維護文檔 |
| **維護頻率** | 即時更新 - 目錄/檔案變更時立即修改 |
| **影響範圍** | 所有版本的檔案組織和維護操作 |
| **使用者** | 框架維護者、升版執行者、文檔管理者 |
| **相關文檔** | AISDLC_INIT.md, UPGRADE_SOP, CLAUDE.md |

### 核心原則

- ✅ **可執行內容永久保留** - agent/workflow/templates/scenarios 必須拷貝
- 📦 **臨時建置文件週期歸檔** - build/ 目錄歸檔而非拷貝
- 🔒 **跨版本文檔單一來源** - AISDLC_ALL 主目錄的文檔所有版本共享
- 📝 **即時更新要求** - 目錄/檔案變更後立即更新此文檔
- 🎯 **明確分類定義** - 每個檔案都有明確的層次和維護規則

---

## 🔴 寫檔強制規則（CRITICAL - MUST CHECK BEFORE ANY Write/Edit OPERATION）

### ⚠️ 執行 Write/Edit 前必須檢查

**每次使用 Write/Edit 工具前必須執行以下檢查**：

#### 1. 🛑 確認正確目錄

參考下表確認檔案應該放在哪個子目錄：

| 檔案類型 | 正確目錄 | 命名格式 | 範例 |
|---------|---------|---------|------|
| 分析報告 | `build/reports/analysis/` | `{TOPIC}_{TYPE}.md` | `SOP_Fix_Summary.md` |
| 階段報告 | `build/reports/phase/` | `{PHASE_NAME}_REPORT.md` | `UPGRADE_COMPLETION_REPORT.md` |
| 驗證報告 | `build/reports/verification/` | `{TOPIC}_REPORT.md` | `Structure_Verification_Report.md` |
| KPI 報告 | `build/reports/kpi/` | `{KPI_TOPIC}_REPORT.md` | `Framework_Performance_Analysis.md` |
| 活躍計劃 | `build/planning/active/` | `{PLAN_NAME}.md` | `Guides_Reorganization_Plan.md` |
| 歸檔計劃 | `build/planning/archive/` | `{PLAN_NAME}.md` | `Phase2_Feature_Development_Plan.md` |
| 版本日誌 | `build/logs/` | `CHANGELOG_v{VERSION}.md` | `CHANGELOG_v0.09.md` |

#### 2. ✅ 確認檔案命名格式符合規範

- 分析報告: `{TOPIC}_{TYPE}.md`
- 階段報告: `{PHASE_NAME}_REPORT.md`
- 驗證報告: `{TOPIC}_REPORT.md`
- KPI 報告: `{KPI_TOPIC}_REPORT.md`
- 計劃文檔: `{PLAN_NAME}.md`
- 版本日誌: `CHANGELOG_v{VERSION}.md`

#### 3. ❌ 絕不寫入這些位置

**禁止寫入的位置**：
- ❌ `/tmp/*` - 工作目錄外
- ❌ `/var/*` - 系統目錄
- ❌ `AISDLC_v0.xx/` (版本根目錄) - 禁止臨時檔案
  - 根目錄僅允許 6 個核心文檔
  - 其他檔案應放在 `build/` 對應子目錄

### 快速檢查清單

寫檔前快速確認：

```
□ 已確認檔案類型（分析/階段/驗證/KPI/計劃/日誌）
□ 已確認正確目錄（build/reports/? build/planning/?）
□ 已確認命名格式符合規範
□ 確認不是寫入禁止位置
```

---

## 📊 三層次文件分類體系

### 層次 1: AISDLC_ALL 專案主目錄

**定義**: 跨版本的專案級文檔，所有版本共享

**位置**: `/AISDLC_ALL/` (根目錄)

**目錄結構**:
```
AISDLC_ALL/
├── README.md                           # 專案總覽（跨版本）
├── CLAUDE.md                           # Claude Code 專案指引
├── HOW_TO_SUPERVISE_CLAUDE.md          # Claude 監督指南
├── .claude/                            # Claude Code 設定目錄
├── backups/                            # 版本備份目錄
│   └── AISDLC_v{VERSION}_backup_{DATE}.tar.gz  # 備份壓縮檔命名格式
├── build_archives/                     # 歷史建置檔案歸檔目錄
│   ├── v0.03/
│   ├── v0.04/
│   └── v0.05/
└── AISDLC_v{VERSION}/                  # 版本目錄（層次 2）
    ├── AISDLC_v0.01/
    ├── AISDLC_v0.02/
    ├── AISDLC_v0.03/
    ├── AISDLC_v0.04/
    ├── AISDLC_v0.05/
    ├── AISDLC_v0.06/
    ├── AISDLC_v0.07/
    ├── AISDLC_v0.08/
    └── AISDLC_v0.09/
```

**特徵**:
- ✅ 永久保留，不隨版本變化
- ❌ 不參與版本升級 COPY
- 📝 內容為專案元數據和歷史歸檔

**目錄用途說明**:

| 目錄/檔案 | 用途 | 維護規則 |
|----------|------|---------|
| `README.md` | 專案總覽、框架介紹 | 跨版本維護，重大變更時更新 |
| `CLAUDE.md` | Claude Code 專案指引 | 框架結構變更時更新 |
| `HOW_TO_SUPERVISE_CLAUDE.md` | Claude 監督使用指南 | 根據需要更新 |
| `.claude/` | Claude Code 設定目錄 | Claude Code 自動管理 |
| `backups/` | 版本備份壓縮檔存放 | 每次升版前創建備份 |
| `build_archives/` | 歷史版本建置檔案歸檔 | 新版本發布時歸檔舊版本 build/ |
| `AISDLC_v{VERSION}/` | 各版本獨立目錄 | 版本升級時創建新目錄 |

---

## 🎬 專案文檔規範（使用 AISDLC 的專案）

**適用範圍**: 所有使用 AISDLC 框架的專案

**🔴 重要觀念（2025-01-12 更新）**：
- ✅ **AISDLC 框架本身就是專案工作目錄**
- ✅ **專案文件直接寫入 AISDLC_v0.09/docs/ 目錄**
- ✅ **不需要在其他地方建立專案目錄**
- ✅ **不需要建立符號連結或 AISDLC/framework/ 結構**

### 專案標準目錄結構

```
AISDLC_v0.09/                         # 工作目錄（框架即專案目錄）
├── AISDLC_INIT.md                    # 框架初始化配置
├── AISDLC_PROJECT_CONFIG.md          # 專案配置檔（可選）
├── docs/                             # 專案文檔輸出目錄（開發專注版）
│   ├── README.md                     # docs/ 目錄使用說明
│   ├── 01_requirements/              # 需求文檔 (PRD, FRD, User Stories)
│   ├── 02_architecture/              # 架構設計 (SRD, API Specification)
│   ├── 03_testing/                   # 測試文檔 (Test Plan, Test Cases, Reports)
│   ├── 04_planning/                  # 開發規劃 (Roadmap, Estimation, Task Breakdown)
│   ├── 05_development/               # 開發文檔 (Iteration Plans, Progress Logs)
│   ├── 06_quality/                   # 品質保證 (Code Quality, Security, Performance)
│   ├── 07_design/                    # 設計文檔 (UI/UX, Database, API)
│   └── 08_deployment/                # 部署文檔 (CI/CD, Release Notes)
├── agent/                            # Agent 配置
├── workflow/                         # Workflow 定義
├── guides/                           # 使用指南
└── [其他框架目錄]
```

### 專案初始化

**自動初始化**（推薦）：
```bash
# 進入 AISDLC 框架目錄
cd /path/to/AISDLC_ALL/AISDLC_v0.09

# 執行初始化腳本（檢查並建立 docs/ 子目錄）
bash tools/init_project.sh          # macOS/Linux
# 或
powershell tools/init_project.ps1   # Windows
```

**手動初始化**：
```bash
# 進入 AISDLC 框架目錄
cd /path/to/AISDLC_ALL/AISDLC_v0.09

# 建立 docs/ 子目錄（如果不存在）
mkdir -p docs/{01_requirements,02_architecture,03_testing,04_planning,05_development,06_quality,07_design,08_deployment}
```

### 專案文檔規範文件

📖 **目錄結構標準**: [DEVELOPMENT_DIRECTORY_STRUCTURE.md](DEVELOPMENT_DIRECTORY_STRUCTURE.md) - 🆕 **必讀！詳細說明 8 層編號目錄結構**
📖 **詳細規範**: [PROJECT_DOCUMENTATION_STANDARDS.md](guides/user/standards/PROJECT_DOCUMENTATION_STANDARDS.md)
📖 **初始化指南**: [PROJECT_INITIALIZATION_GUIDE.md](guides/user/onboarding/PROJECT_INITIALIZATION_GUIDE.md)
📖 **檢查清單**: [PROJECT_INITIALIZATION_CHECKLIST.md](guides/user/onboarding/PROJECT_INITIALIZATION_CHECKLIST.md)

### 目錄用途說明

**🔴 重要**: `docs/` 目錄是專案文檔的輸出位置，直接位於 AISDLC_v0.09 框架目錄下：
- **位置**: `AISDLC_v0.09/docs/`
- **用途**: 存放實際專案的需求、架構、測試文檔（PRD, FRD, SRD, AT 等）
- **目錄標準**: 8 層編號目錄 (01-08)，開發專注版
- **詳見**: [docs/README.md](docs/README.md) 或 [DEVELOPMENT_DIRECTORY_STRUCTURE.md](DEVELOPMENT_DIRECTORY_STRUCTURE.md)（v2.0 開發專注版）
- **初始化**: 使用 `init_project.sh` 或 `init_project.ps1` 自動創建標準結構

**📝 目錄結構歷史變更**:
- **v0.09 初期（2025-01-10）**: 6 個未編號目錄 → 8 個編號目錄 (01-08)
- **v0.09 開發專注版（2025-01-11）**: 移除 06_meeting_minutes/，調整為開發專注版
  - 04_project_management/ → 04_planning/（開發規劃）
  - 05_sprint/ → 05_development/（開發文檔，迭代制）
  - 新增 06_quality/（品質保證，含程式碼品質、安全合規、效能優化）
  - 移除會議相關流程，專注於 2 人開發團隊的產品研發
- **v0.09 架構簡化（2025-01-12）**: 移除符號連結機制，框架即專案目錄
  - 移除: `your-project/AISDLC/framework/` 符號連結結構
  - 簡化: 直接在 AISDLC_v0.09/ 目錄下工作
- **v0.09 Agent 自動載入機制（2025-01-23）**: AISDLC_INIT.md 新增 Agent 自動載入配置表
  - 新增: `auto_load_config` YAML 配置，定義 10 個情境的 Primary/Supporting Agents
  - 新增: Agent 自動載入詳細流程（5 階段執行規範）
  - 更新: 快速啟動指令整合自動載入確認輸出
- **影響檔案**: AISDLC_INIT.md, DEVELOPMENT_DIRECTORY_STRUCTURE.md (v2.0), FILE_DIRECTORY_RULES.md, PROJECT_INITIALIZATION_GUIDE.md
- **參考文檔**: [DEVELOPMENT_DIRECTORY_STRUCTURE.md](DEVELOPMENT_DIRECTORY_STRUCTURE.md), [docs/README.md](docs/README.md)

---

### 層次 2: AISDLC_v0.xx 開發流程

**定義**: 版本特定的可執行內容，是框架的核心運行時

**位置**: `/AISDLC_ALL/AISDLC_v0.xx/`

**目錄結構概覽**:

#### 🔹 核心文檔 (根目錄)

**必須拷貝的核心文檔**:
```
AISDLC_v0.09/
├── AISDLC_INIT.md                    # 核心入口（必讀）
├── README.md                         # 版本說明
├── FILE_DIRECTORY_RULES.md           # 檔案目錄規則
├── AISDLC_v0.0X_UPGRADE_SOP.md       # 下次升版 SOP
├── AISDLC_UPGRADE_SOP_CheckList.md   # 升版執行檢查清單 (v0.09+)
└── DEVELOPMENT_DIRECTORY_STRUCTURE.md # 開發目錄結構規範 (v0.09+)
```

**維護規則**:
- ✅ 升版時必須拷貝這 6 個核心檔案
- ✅ `AISDLC_v0.0X_UPGRADE_SOP.md` 拷貝後需重新命名並更新版本號
- ✅ `README.md` 拷貝後需針對新版本重寫
- **檔名格式**: `AISDLC_v{NEXT_VERSION}_UPGRADE_SOP.md`
- **位置要求**: 必須在版本根目錄

**🔴 升版 SOP CheckList 機制 (自 v0.09 起新增)**:

**檔案**: `AISDLC_UPGRADE_SOP_CheckList.md`
**位置**: 版本根目錄 (與 `AISDLC_v0.0X_UPGRADE_SOP.md` 同層級)
**目的**: 強制執行升版 SOP 的所有步驟，防止遺漏關鍵階段

**功能說明**:
1. **同步維護**: 與 `AISDLC_v0.0X_UPGRADE_SOP.md` 同步維護，包含 SOP 所有章節的 CheckList
2. **升版前備份**: 🆕 執行升版前必須先備份為 `.bk` 檔案，升版失敗時可快速恢復（2025-12-10 新增）
3. **逐項打勾**: 執行升版過程中，每完成 SOP 中的一個步驟，必須立即回到**舊版本**的 CheckList 檔案打勾
4. **完成標準**: CheckList 所有項目全部打勾後，升版才算 100% 完成
5. **升版拷貝**: 與 UPGRADE_SOP 一起拷貝到新版本
6. **狀態重置**: 拷貝到新版本時，所有勾選狀態清除，恢復為未勾選 `[ ]`

**CheckList 結構**:
- 階段 1: 準備階段檢查點 (15 項) 🆕 包含 1.2.1 備份 CheckList
- 階段 2: 目錄與檔案拷貝檢查點 (13 步驟) 🆕 含 2.9.1 .claude/
- 階段 3: 版本號更新檢查點 (12 步驟) 🆕 含 3.6.2-3.6.5
- 階段 4: 內容調整檢查點 (4 步驟)
- 階段 5: 驗證檢查點 (9 步驟) 🆕 含 .claude/ 驗證
- 階段 6: 歸檔檢查點 (2 步驟，可跳過但需明確標記)
- 階段 7: 發布準備檢查點 (5 步驟，🔴 絕對不可跳過)
- 總計: 60 個檢查點 🆕 (自 v0.09 起)

**強制驗證機制**:
- ✅ 統計表顯示完成百分比 (必須達到 100%)
- ✅ 最終驗證章節確認所有階段已完成
- ✅ 發布包檔案存在驗證 (如 `releases/AISDLC_v0.10_release_YYYY-MM-DD.tar.gz`)
- ❌ 禁止在未執行的情況下打勾

**歷史教訓 (2025-12-09)**:
- 問題: v0.09→v0.10 升版時，Agent 完全跳過階段 7
- 後果: 發布包未創建，使用者無法使用升版成果
- 解決: 引入 CheckList 機制，使用物理勾選框和統計表強制驗證執行

**維護要求**:
- 🔄 任何 UPGRADE_SOP 的變更都必須同步更新 CheckList
- 🔄 CheckList 版本號必須與 UPGRADE_SOP 一致
- 🔄 升版時檢查 CheckList 是否與 SOP 同步

**可能存在的其他根目錄檔案**:

| 檔案模式 | 類型 | 升版處理 | 說明 |
|---------|------|---------|------|
| `AISDLC_UPGRADE_SOP_CheckList.md.bk` | 🆕 CheckList 備份 | ❌ 不拷貝 | 升版前自動備份，失敗時用於快速恢復 (2025-12-10) |
| `AISDLC_v0.XX_UPGRADE_SOP_改善計畫報告.md` | 臨時分析報告 | ❌ 不拷貝 | SOP 改善過程的分析報告，屬於 build/ 性質 |
| `UPGRADE_FROM_V{OLD}.md` | 歷史升版記錄 | ❌ 不拷貝 | 特定升版的歷史記錄，應移至 build/reports/ |
| `*.backup` | 備份檔案 | ❌ 不拷貝 | 臨時備份檔案，完成後應刪除 |
| `*.original` | 原始檔案備份 | ❌ 不拷貝 | 臨時備份檔案，完成後應刪除 |
| `*.{DATE}` | 日期備份 | ❌ 不拷貝 | 臨時備份檔案，完成後應刪除 |

**臨時檔案清理原則**:
- 🧹 升版前應清理所有 `*.backup`, `*.original`, `*.{DATE}` 檔案
- 🧹 臨時分析報告應移至 `build/reports/analysis/`
- 🧹 歷史升版記錄應移至 `build/reports/phase/` 或 `build/planning/archive/`

#### 🔹 可執行內容目錄 (✅ 版本升級時應 COPY)
```
AISDLC_v0.09/
├── agent/                             # AI 角色定義
│   ├── core/                          # 7 Core Agents (中文版 - 主要維護)
│   │   ├── 0X.{role}-{type}-zh.yaml
│   │   └── backup_en/               # 英文版存檔（僅供參考）
│   ├── specialized/                   # 14 Specialized Agents
│   └── README.md
│
├── workflow/                          # 工作流程定義
│   ├── core/                          # 核心通用 workflow (8個)
│   ├── scenario-specific/             # 情境專屬 workflow (12個) 🔄 2026-02-16 +migration-planning-flow
│   └── README.md
│
├── docs_template/                     # 文檔模板
│   ├── README.md                      # docs_template 使用說明
│   │
│   ├── core/                          # 核心文檔模板
│   │   ├── prd/                       # PRD 模板
│   │   ├── frd/                       # FRD 模板
│   │   ├── srd/                       # SRD 模板
│   │   ├── api/                       # API 規格模板
│   │   └── tests/                     # 測試模板
│   │
│   ├── prd/                           # PRD 專屬模板（MVP 等）
│   │   └── MVP_Definition_Template.md
│   │
│   ├── srd/                           # SRD 專屬模板（進階技術設計）
│   │   ├── Data_Access_Layer_Template.md
│   │   └── Concurrent_Sequence_Diagram_Template.md
│   │
│   ├── scenario_specific/             # 情境專屬模板（部分為預留目錄）
│   │   ├── documentation/             # 🚧 預留目錄 - 技術文檔情境模板
│   │   ├── brownfield/                # ✅ Brownfield 情境模板（3個模板）
│   │   ├── analysis/                  # ✅ 分析情境模板（3個模板）
│   │   ├── migration/                 # ✅ Migration 情境模板（3個模板）🆕 2026-02-16
│   │   ├── integration/               # 🚧 預留目錄 - 第三方整合情境模板
│   │   ├── testing/                   # 🚧 預留目錄 - 測試策略情境模板
│   │   ├── performance/               # 🚧 預留目錄 - 效能優化情境模板
│   │   └── devops/                    # DevOps 情境模板 🔄 2026-03-22 +Layer0+Layer1+Migration+SecurityIntegration+PerfBenchmark+DocPipeline+AgentNotification
│   │       ├── CICD_Pipeline_Template.md           # CI/CD Pipeline 標準配置範本
│   │       ├── Layer0_Security_Baseline_Template.md # 🆕 Layer 0 安全基線完整指南
│   │       ├── Layer1_Build_Verify_Template.md     # 🆕 Layer 1 Build & Verify 完整指南
│   │       ├── Migration_Pipeline_Template.md      # 🆕 Migration 專屬 Pipeline (Canary+Rollback)
│   │       ├── Security_Scan_Integration_Template.md # 🆕 P1 增強安全掃描整合指南 (SAST/Container/DAST)
│   │       ├── Performance_Benchmark_Gate_Template.md # 🆕 P2 效能基準關卡指南 (Micro-Benchmark/Load Test/SLA Gate)
│   │       ├── Documentation_Pipeline_Template.md  # 🆕 P2 文檔 Pipeline 指南 (Doc Lint/Link Check/Build/Deploy)
│   │       ├── Event_Driven_Agent_Notification_Template.md # 🆕 P3 事件驅動 Agent 通知系統 (PR/Deploy/Release 通知)
│   │       ├── pre-commit-config-template.yaml     # 🆕 Pre-commit Hook 配置範本
│   │       ├── github-actions/                     # 🆕 GitHub Actions 範本目錄
│   │       │   ├── security-baseline.yml           # Layer 0 GitHub Actions Workflow
│   │       │   ├── build-verify.yml                # 🆕 Layer 1 GitHub Actions Workflow
│   │       │   ├── migration-pipeline.yml          # 🆕 Migration Pipeline Workflow
│   │       │   ├── security-scan-enhanced.yml      # 🆕 P1 增強安全掃描 Workflow (SAST/Container/DAST)
│   │       │   ├── perf-benchmark.yml              # 🆕 P2 效能基準 Workflow (Micro-Benchmark/Load Test)
│   │       │   ├── docs-pipeline.yml              # 🆕 P2 文檔 Pipeline Workflow (Doc Lint/Link Check/Deploy)
│   │       │   └── agent-notification.yml         # 🆕 P3 事件驅動 Agent 通知 Workflow
│   │       └── gitlab-ci/                          # 🆕 GitLab CI 範本目錄
│   │           ├── security-baseline-template.yml  # Layer 0 GitLab CI Template
│   │           ├── build-verify-template.yml       # 🆕 Layer 1 GitLab CI Template
│   │           ├── migration-pipeline-template.yml # 🆕 Migration Pipeline Template
│   │           ├── security-scan-enhanced-template.yml # 🆕 P1 增強安全掃描 Template
│   │           ├── perf-benchmark-template.yml     # 🆕 P2 效能基準 Template (Micro-Benchmark/Load Test)
│   │           ├── docs-pipeline-template.yml     # 🆕 P2 文檔 Pipeline Template (Doc Lint/Link Check/Deploy)
│   │           └── agent-notification-template.yml # 🆕 P3 事件驅動 Agent 通知 Template
│   └── support/                       # 支援模板
│       ├── operations/                # 🚧 預留目錄 - 營運支援模板
│       ├── monitoring/                # 🚧 預留目錄 - 監控可觀測性模板
│       └── [其他支援模板檔案]
│
├── prompts/                           # 提示詞模板
│   ├── quick-start/
│   ├── complete-flow/
│   └── scenario-prompts/
│
├── scenarios/                         # 場景 SOP (10大情境)
│   ├── greenfield/                   # 全新專案開發
│   │   ├── SOP.md
│   │   ├── SOP_QuickRef.md
│   │   └── checklists/               # 檢查清單目錄
│   ├── brownfield/                   # 既有系統改造
│   ├── refactoring/                  # 代碼重構
│   ├── integration/                  # 第三方整合
│   ├── performance/                  # 效能優化
│   ├── testing/                      # 測試策略
│   ├── security/                     # 安全審查
│   ├── devops/                       # CI/CD 部署
│   ├── documentation/                # 技術文檔
│   └── migration/                    # 技術棧遷移 🆕 2026-02-12
│
├── tools/                             # 工具腳本目錄
│   ├── README.md
│   ├── init_project.sh                # 專案初始化腳本（macOS/Linux）🆕 2025-01-10
│   ├── init_project.ps1               # 專案初始化腳本（Windows PowerShell）🆕 2025-01-10
│   ├── verify_traceability.sh         # 追溯鏈驗證工具 🆕 2026-02-11
│   ├── AISDLC_CLAUDE_RULES.md         # Claude Code 規則（框架開發用）🆕 2025-01-10
│   └── PROJECT_CLAUDE_Template.md     # 專案 CLAUDE.md 模板（新專案用）🆕 2025-01-11
│
├── .claude/                           # Claude Code 配置目錄 🆕 2025-01-22
│   └── skills/                        # Claude Skills 技能套件 (33個) 🔄 2026-02-12
│       ├── README.md                  # Skills 總覽與使用說明
│       ├── SKILL_DEVELOPMENT_PLAN.md  # Skill 開發規劃文檔
│       ├── devops-*/SKILL.md          # DevOps 家族 (5個)
│       ├── integration-*/SKILL.md     # Integration 家族 (10個)
│       ├── brownfield-analysis/SKILL.md   # Brownfield 分析
│       ├── refactoring-code-quality/SKILL.md  # 代碼重構
│       ├── performance-optimization/SKILL.md  # 效能優化
│       ├── testing-strategy/SKILL.md  # 測試策略
│       ├── security-audit/SKILL.md    # 安全審計
│       ├── compliance-audit/SKILL.md  # 合規審查 🆕 2026-02-07
│       ├── documentation-api/SKILL.md # API 文檔
│       ├── sa-analyst/SKILL.md        # SA 需求分析
│       ├── ba-analyst/SKILL.md        # BA 業務驗證
│       ├── sd-architect/SKILL.md      # SD 架構設計
│       ├── qa-testing/SKILL.md        # QA 測試策略
│       ├── dev-review/SKILL.md        # Dev 代碼審查
│       ├── pm-planning/SKILL.md       # PM 產品規劃
│       ├── sprint-planning/SKILL.md   # Sprint 規劃
│       ├── release-management/SKILL.md # 發布管理
│       ├── code-review/SKILL.md       # 代碼審查流程
│       ├── database-migration/SKILL.md  # 資料庫遷移 🆕 2026-02-12
│       └── mobile-development/SKILL.md  # 行動端開發 🆕 2026-02-12
│       # 格式: Claude Code Agent Skills Standard (<name>/SKILL.md)
│
├── releases/                          # 版本發布包管理
│   └── v{VERSION}/                    # 版本發布目錄（如 v0.09/）
│       ├── AISDLC_v{VERSION}_Release_Package.tar.gz         # 發布壓縮檔
│       ├── AISDLC_v{VERSION}_Release_Package.tar.gz.sha256  # SHA256 校驗和
│       ├── RELEASE_NOTES.md           # 該版本發布說明
│       └── package/                   # 預留目錄 - 未來打包功能
│           └── README.md              # 預留目錄說明
│
└── docs/                              # 🎯 專案文檔輸出目錄（開發專注版 8 目錄）
    ├── README.md
    ├── 01_requirements/               # PRD/FRD/User Stories
    ├── 02_architecture/               # SRD/API Specification
    ├── 03_testing/                    # Test Plan/Cases/Reports
    ├── 04_planning/                   # Roadmap/Estimation/Task
    ├── 05_development/                # Iteration Plans/Progress
    ├── 06_quality/                    # Code Quality/Security/Perf
    ├── 07_design/                     # UI/UX/Database Design
    └── 08_deployment/                 # CI/CD/Release Notes
```

#### 🔹 參考文檔目錄 (✅ 版本升級時應 COPY)
```
AISDLC_v0.09/
└── guides/                            # 使用指南與標準規範
    ├── README.md                      # Guides 總覽與導航
    │
    ├── system/                        # 系統參考文件（給 AI Agent 使用）
    │   ├── README.md                  # system/ 目錄說明
    │   ├── naming/                    # 命名與識別規範
    │   │   └── AISDLC_ID_Naming_Convention.md
    │   ├── architecture/              # 架構設計規範
    │   │   ├── C4_Model_Guidelines.md
    │   │   ├── Architecture_Diagram_Maintenance.md
    │   │   ├── Web_Architecture_Decision_Tree.md
    │   │   └── Observability_Design_Guide.md
    │   ├── api/                       # API 設計規範
    │   │   └── API_Versioning_Guide.md
    │   ├── testing/                   # 測試規範
    │   │   └── AT_vs_TC_Guide.md
    │   ├── quality/                   # 品質管理規範
    │   │   ├── Document_Quality_Checklist.md
    │   │   └── Security_Design_Checklist.md
    │   ├── planning/                  # 規劃與估算規範
    │   │   ├── Estimation_Standards.md
    │   │   └── Kano_Model_Guide.md
    │   └── agent/                     # Agent 管理規範
    │       ├── Platform_Agent_Selection_Guide.md
    │       └── Specialized_Agent_Selection_Guide.md
    │
    ├── user/                          # 使用者參考文件（給人類使用）
    │   ├── README.md                  # user/ 目錄說明
    │   ├── onboarding/                # 新手入門
    │   │   ├── QUICK_START_GUIDE.md
    │   │   ├── QUICK_START_TEMPLATES.md
    │   │   ├── QUICK_WINS_GUIDE.md
    │   │   ├── SCENARIO_SELECTOR.md
    │   │   ├── TUTORIAL_MODE.md
    │   │   ├── PROJECT_INITIALIZATION_GUIDE.md       # 專案初始化指南 🆕 2025-01-10
    │   │   └── PROJECT_INITIALIZATION_CHECKLIST.md   # 專案初始化檢查清單 🆕 2025-01-10
    │   ├── standards/                 # 標準與規範
    │   │   ├── DOCUMENTATION_READABILITY_GUIDE.md
    │   │   └── PROJECT_DOCUMENTATION_STANDARDS.md    # 專案文檔標準 🆕 2025-01-10
    │   ├── technical/                 # 技術指引
    │   │   └── SMART_DEFAULTS.md
    │   ├── process/                   # 流程與管理
    │   │   ├── Code_Review_Guidelines.md
    │   │   └── Development_Build_Test_Cycle.md       # 開發-編譯-測試循環 🆕 2025-01-11
    │   └── sample/                    # 範例文檔
    │
    └── archive/                       # 歷史文件存檔
        ├── README.md
        ├── HOW_TO_RESUME.md
        ├── INDEX.md
        ├── REVIEW_INDEX.md
        └── REVIEW_QUICK_REFERENCE.md
```

**特徵**:
- ✅ **應在版本升級時 COPY** (從 v0.0X → v0.0Y)
- 🔄 可在新版本中修改和演進
- 📦 是框架的核心可執行部分

---

### 層次 3: 版本建置過程

**定義**: 特定版本建置過程中產生的臨時文檔

**位置**: `/AISDLC_ALL/AISDLC_v0.xx/build/`

**目錄結構**:
```
AISDLC_v0.09/build/
├── README.md                          # build/ 目錄使用說明
│
├── logs/                              # 版本專屬日誌
│   ├── CHANGELOG_v{VERSION}.md        # 版本變更記錄（必須，每版本創建新檔案）
│   ├── CHANGELOG_v{VERSION}_Phase{N}.md  # 階段性變更記錄（可選，大版本分階段時使用）
│   └── FILE_REORGANIZATION_LOG_v{VERSION}.md  # 檔案重組記錄（可選，僅大規模重組時創建）
│
├── planning/                          # 計劃文檔目錄
│   ├── active/                        # 活躍計劃（進行中的專案計劃）
│   │   ├── .gitkeep                   # 保留空目錄結構
│   │   ├── {PLAN_NAME}.md             # 計劃文檔
│   │   └── fix_{purpose}.sh           # 修正腳本（可選）
│   │
│   └── archive/                       # 歸檔計劃（已完成或停止的專案計劃）
│       ├── .gitkeep                   # 保留空目錄結構
│       ├── {PLAN_NAME}.md             # 直接歸檔的計劃（單檔案形式）
│       └── {DATE}_{PROJECT}/          # 按專案歸檔（子目錄形式，適用於多檔案計劃）
│           ├── plan.md
│           ├── report.md
│           └── results.md
│
└── reports/                           # 建置報告目錄
    ├── phase/                         # 階段報告（升版過程的各階段報告）
    │   ├── .gitkeep                   # 保留空目錄結構
    │   └── {PHASE_NAME}_REPORT.md     # 階段報告
    │
    ├── kpi/                           # KPI 報告（關鍵績效指標分析）
    │   ├── .gitkeep                   # 保留空目錄結構
    │   └── {KPI_TOPIC}_REPORT.md      # KPI 報告
    │
    ├── verification/                  # 驗證報告（升版驗證、文檔一致性驗證）
    │   ├── {VERIFICATION_TOPIC}_REPORT.md  # 驗證報告
    │   └── archive/                   # 歷史驗證報告歸檔
    │       ├── .gitkeep               # 保留空目錄結構
    │       └── {OLD_REPORT}.md        # 舊版本或過時的驗證報告
    │
    └── analysis/                      # 分析報告（問題分析、比對分析）
        ├── .gitkeep                   # 保留空目錄結構
        └── {ANALYSIS_TOPIC}_{TYPE}.md # 分析報告
```

**特徵**:
- ❌ 不應在版本升級時 COPY
- 📦 版本發布後應歸檔到 `AISDLC_ALL/build_archives/v0.xx/`
- ⏳ 是臨時性的開發過程記錄
- 🔍 用於追溯該版本的建置歷史

**檔案命名規範**:

#### logs/ 目錄
```
格式: CHANGELOG_v{VERSION}[_Phase{N}].md
範例:
  - CHANGELOG_v0.09.md                # 版本總變更記錄
  - CHANGELOG_v0.09_Phase1.md         # 第一階段變更記錄
  - CHANGELOG_v0.09_Phase2.md         # 第二階段變更記錄

格式: FILE_REORGANIZATION_LOG_v{VERSION}.md
範例:
  - FILE_REORGANIZATION_LOG_v0.09.md  # 檔案重組記錄
```

#### planning/active/ 目錄
```
格式: {DESCRIPTIVE_NAME}_Plan.md
範例:
  - Guides_Reorganization_Plan.md
  - FILE_DIRECTORY_RULES_comprehensive_fix_plan.md
  - Phase2_Feature_Development_Plan.md

格式: fix_{purpose}.sh（修正腳本）
範例:
  - fix_guides_paths.sh
  - fix_version_numbers.sh
```

#### planning/archive/ 目錄
```
單檔案形式:
格式: {PLAN_NAME}.md
範例:
  - Greenfield_Simulation_BnB_Website_Plan.md
  - Greenfield_Simulation_Improvement_Plan.md
  - Greenfield_Simulation_InvMaster_Issues.md
  - Greenfield_Simulation_InvMaster_Plan.md
  - Greenfield_Simulation_StayShop_Issues.md
  - Greenfield_Simulation_StayShop_Improvement_Plan.md
  - Greenfield_Simulation_KnowHub_Issues.md
  - Greenfield_Simulation_KnowHub_Improvement_Plan.md
  - AISDLC_v0.09_Comprehensive_Improvement_Plan.md

多檔案形式（子目錄）:
格式: {DATE}_{PROJECT}/
範例:
  - 20251115_Guides_Reorganization/
    ├── plan.md
    ├── execution_report.md
    └── results.md
```

#### reports/phase/ 目錄
```
格式: {PHASE_NAME}_REPORT.md
範例:
  - UPGRADE_COMPLETION_REPORT.md
  - SOP_FIX_COMPLETION_REPORT.md
  - SOP_ERROR_FIXES_REPORT.md
  - SOP_VERIFICATION_REPORT.md
```

#### reports/kpi/ 目錄
```
格式: {KPI_TOPIC}_REPORT.md
範例:
  - KPI_VERIFICATION_PLAN.md
  - OPTIMIZATION_PRIORITY_MATRIX.md
  - Framework_Performance_Analysis.md
```

#### reports/verification/ 目錄
```
格式: {VERIFICATION_TOPIC}_REPORT.md
範例:
  - UPGRADE_VERIFICATION_REPORT.md
  - DOCUMENT_CONSISTENCY_REPORT.md
  - Structure_Verification_Report.md
```

#### reports/analysis/ 目錄
```
格式: {ANALYSIS_TOPIC}_{TYPE}.md
範例:
  - upgrade_issues_found.md
  - sop_fix_summary.md
  - FILE_DIRECTORY_RULES_comparison_v0.05_vs_v0.09.md
  - FILE_DIRECTORY_RULES_fix_summary.md
```

**使用指引**:

1. **logs/** - 版本日誌
   - 用途: 記錄版本變更、檔案重組等重大變化
   - 時機: 版本開始時創建 CHANGELOG，重大重組時創建 FILE_REORGANIZATION_LOG
   - 維護: 持續更新直到版本發布

2. **planning/active/** - 活躍計劃
   - 用途: 存放正在執行的改進計劃、功能開發計劃
   - 時機: 開始新計劃時創建，計劃完成後移至 archive/
   - 維護: 定期檢查，完成的計劃及時歸檔

3. **planning/archive/** - 歸檔計劃
   - 用途: 存放已完成或停止的計劃，保留供未來參考
   - 時機: 計劃完成或停止時歸檔
   - 維護: 不再修改，僅供查閱

4. **reports/phase/** - 階段報告
   - 用途: 升版完成報告、SOP 修正報告、階段總結報告
   - 時機: 重要階段完成時創建
   - 維護: 創建後一般不再修改

5. **reports/kpi/** - KPI 報告
   - 用途: 框架效能分析、優化優先級矩陣、KPI 驗證計劃
   - 時機: 定期評估或優化時創建
   - 維護: 根據需要更新

6. **reports/verification/** - 驗證報告
   - 用途: 升版驗證、文檔一致性驗證、問題修正驗證
   - 時機: 重要操作完成後的驗證階段
   - 維護: 驗證完成後歸檔，新驗證創建新報告

7. **reports/analysis/** - 分析報告
   - 用途: 升版問題分析、版本比對分析、修正總結
   - 時機: 問題排查、版本比對、修正總結時創建
   - 維護: 分析完成後一般不再修改

---

## 🔄 版本升級規則

### 升級流程 (v0.0X → v0.0Y)

#### 第 1 步: 備份當前版本
```bash
# 創建備份壓縮檔
cd /AISDLC_ALL
tar -czf AISDLC_v0.0X_backup_$(date +%Y%m%d).tar.gz AISDLC_v0.0X/

# 歸檔 build/ 目錄
mkdir -p build_archives/v0.0X
mv AISDLC_v0.0X/build/* build_archives/v0.0X/
```

#### 第 2 步: 創建新版本目錄
```bash
mkdir -p AISDLC_v0.0Y
```

#### 第 3 步: 複製應 COPY 的內容 (層次 2)

**✅ 應 COPY 的目錄**:
```bash
# 可執行內容
cp -r AISDLC_v0.0X/agent         AISDLC_v0.0Y/
cp -r AISDLC_v0.0X/workflow      AISDLC_v0.0Y/
cp -r AISDLC_v0.0X/docs_template AISDLC_v0.0Y/
cp -r AISDLC_v0.0X/prompts       AISDLC_v0.0Y/
cp -r AISDLC_v0.0X/scenarios     AISDLC_v0.0Y/
cp -r AISDLC_v0.0X/tools         AISDLC_v0.0Y/
cp -r AISDLC_v0.0X/.claude       AISDLC_v0.0Y/

# 參考文檔
cp -r AISDLC_v0.0X/guides        AISDLC_v0.0Y/
cp -r AISDLC_v0.0X/docs          AISDLC_v0.0Y/
```

**✅ 應 COPY 的核心文檔** (v0.09+ 更新):
```bash
cp AISDLC_v0.0X/AISDLC_INIT.md           AISDLC_v0.0Y/
cp AISDLC_v0.0X/FILE_DIRECTORY_RULES.md  AISDLC_v0.0Y/

# UPGRADE_SOP 需複製並修改版本號
cp AISDLC_v0.0X/AISDLC_v0.0Y_UPGRADE_SOP.md \
   AISDLC_v0.0Y/AISDLC_v0.0Z_UPGRADE_SOP.md
# 然後批量替換版本號: v0.0Y → v0.0Z, v0.0X → v0.0Y
```

**📝 應重新創建的文檔**:
```bash
# 這些文檔需要針對新版本重寫
AISDLC_v0.0Y/README.md                    # 新版本說明
AISDLC_v0.0Y/build/logs/CHANGELOG_v0.0Y.md  # 新版本變更記錄（新檔案，非拷貝）
```

#### 第 4 步: 不應 COPY 的內容

**❌ 不應 COPY 的目錄和文件**:
```bash
# build/ 目錄 (已歸檔到 build_archives/)
AISDLC_v0.0X/build/                 # ❌ 不 COPY（目錄內容不拷貝）
# 🔴 例外：build/README.md 應該拷貝（目錄說明文檔）
# 參考: AISDLC_v0.10_UPGRADE_SOP.md 階段 1.4.1

# 臨時或版本特定文件
AISDLC_v0.0X/*_BACKUP_*.tar.gz      # ❌ 不 COPY (備份文件)
```

#### 第 5 步: 更新版本號
```bash
# 批量更新版本號 (v0.0X → v0.0Y)
# macOS: sed -i '' | Linux/Windows Git Bash: sed -i (無引號)
find AISDLC_v0.0Y -type f \( -name "*.md" -o -name "*.yaml" -o -name "*.sh" -o -name "*.ps1" \) | \
  xargs sed -i 's/v0\.0X/v0.0Y/g'

# 手動檢查關鍵文件
# - AISDLC_INIT.md
# - README.md
# - FILE_DIRECTORY_RULES.md
# - AISDLC_v0.0Z_UPGRADE_SOP.md

# 創建新版本 CHANGELOG
# - build/logs/CHANGELOG_v0.0Y.md
```

#### 第 6 步: 創建新版本 build/ 目錄
```bash
# v0.09+ build/ 目錄結構
mkdir -p AISDLC_v0.0Y/build/logs
mkdir -p AISDLC_v0.0Y/build/{planning/{active,archive},reports/{phase,kpi,verification,analysis}}

# 創建 CHANGELOG 新檔案
touch AISDLC_v0.0Y/build/logs/CHANGELOG_v0.0Y.md
```

---

## 📋 文件分類快速參考表

| 文件/目錄 | 層次 | 版本升級 | 歸檔位置 | 說明 |
|----------|-----|---------|---------|------|
| `AISDLC_ALL/README.md` | 1 | ❌ 不 COPY | - | 專案總覽 |
| `AISDLC_ALL/build_archives/` | 1 | ❌ 不 COPY | - | 歷史歸檔庫 |
| `AISDLC_v0.xx/agent/` | 2 | ✅ COPY | - | AI 角色定義 |
| `AISDLC_v0.xx/workflow/` | 2 | ✅ COPY | - | 工作流程 |
| `AISDLC_v0.xx/docs_template/` | 2 | ✅ COPY | - | 文檔模板 |
| `AISDLC_v0.xx/prompts/` | 2 | ✅ COPY | - | 提示詞模板 |
| `AISDLC_v0.xx/scenarios/` | 2 | ✅ COPY | - | 場景 SOP |
| `AISDLC_v0.xx/tools/` | 2 | ✅ COPY | - | 工具腳本 |
| `AISDLC_v0.xx/releases/` | 2 | ❌ 不 COPY | - | 版本發布包 |
| `AISDLC_v0.xx/guides/` | 2 | ✅ COPY | - | 使用指南 |
| `AISDLC_v0.xx/docs/` | 2 | ✅ COPY | - | 補充文檔 |
| `AISDLC_v0.xx/AISDLC_INIT.md` | 2 | ✅ COPY | - | 核心入口 |
| `AISDLC_v0.xx/README.md` | 2 | 📝 重寫 | - | 版本說明 |
| `AISDLC_v0.xx/FILE_DIRECTORY_RULES.md` | 2 | ✅ COPY | - | 檔案目錄規則 |
| `AISDLC_v0.xx/AISDLC_v0.YY_UPGRADE_SOP.md` | 2 | ✅ COPY+修改 | - | 升版 SOP |
| `AISDLC_v0.xx/AISDLC_UPGRADE_SOP_CheckList.md` | 2 | ✅ COPY+重置 | - | 升版 CheckList (v0.09+) |
| `AISDLC_v0.xx/DEVELOPMENT_DIRECTORY_STRUCTURE.md` | 2 | ✅ COPY | - | 開發目錄結構規範 (v0.09+) |
| `AISDLC_v0.xx/.claude/` | 2 | ✅ COPY | - | Claude Code 配置與 Skills |
| `AISDLC_v0.xx/build/README.md` | 2 | ✅ COPY | - | build/ 目錄說明 🔴 |
| `AISDLC_v0.xx/build/logs/CHANGELOG_v0.XX.md` | 3 | 📝 新建 | - | 版本變更記錄 |
| `AISDLC_v0.xx/build/` | 3 | ❌ 不 COPY | `build_archives/v0.xx/` | 臨時建置文件 |

---

## 🏷️ 文件命名規範

### 核心文檔 (大寫 + 下劃線)
```
AISDLC_INIT.md
README.md
FILE_DIRECTORY_RULES.md
AISDLC_v0.0X_UPGRADE_SOP.md
```

### Agent 文件 (編號 + 角色 + 類型)
```
格式: 0X.{role}-{type}.yaml
範例: 04.sa-analyst.yaml

中文版: 0X.{role}-{type}-zh.yaml
範例: 04.sa-analyst-zh.yaml
```

**🔴 Agent 版本號維護規則**

Agent YAML 檔案內部包含版本號，升版時**必須更新**：

```yaml
# 範例：Agent YAML 檔案的 metadata
metadata:
  version: v0.09        # ← 必須更新
  last_updated: 2025-11-01  # ← 日期必須更新
```

**升版更新規則**:
```bash
# 批量更新 agent 版本號
find AISDLC_v0.XX/agent -name "*.yaml" -type f -exec sed -i '' \
  "s/version:.*v0\.OLD/version: v0.NEW/g" {} \;

# 檢查版本號
grep -r "version:" agent/ | grep "v0\."
```

### Workflow 文件 (kebab-case)
```
格式: {workflow-name}.md
範例:
  unified-requirements-extraction.md
  requirements-validation-and-documentation.md
  user-story-and-design.md
```

### 文檔模板 (PascalCase + 下劃線)
```
格式: {DocType}_Template.md
範例:
  PRD_Universal_Template.md
  FRD_Universal_Template.md
  SRD_Template.md
  API_Specification_Template.md
```

### 場景 SOP 文件
```
固定命名:
  SOP_QuickRef.md       # 快速參考 (5分鐘)
  SOP.md                # 標準 SOP (完整版)
  SOP_DeepDive.md       # 深度指南 (可選)
```

### Build 文件 (大寫 + 下劃線 + 描述性)
```
格式: {PREFIX}_{DESCRIPTION}.md
範例:
  PHASE2_COMPLETION_REPORT.md
  V0.04_COMPREHENSIVE_VALIDATION_PLAN.md
  KPI_VERIFICATION_PLAN.md
```

---

## 📂 空目錄管理策略

### 空目錄分類與處理原則

AISDLC 框架中存在三類空目錄，各有不同的用途和管理方式：

#### 🟢 類型 1: 工作區目錄（正常空置）

**定義**: 供使用者在使用 AISDLC 開發專案時存放產出文檔的目錄

**特徵**:
- ✅ 預期為空（框架本身不提供內容）
- ✅ 使用 `.gitkeep` 保留目錄結構
- ✅ Git 追蹤時保留空目錄

**範例目錄**:
```
docs/                              # 專案文檔輸出目錄（開發專注版 8 目錄）
├── 01_requirements/               # ✅ 工作區 - PRD/FRD/User Stories
├── 02_architecture/               # ✅ 工作區 - SRD/API Specification
├── 03_testing/                    # ✅ 工作區 - Test Plan/Cases/Reports
├── 04_planning/                   # ✅ 工作區 - Roadmap/Estimation/Task
├── 05_development/                # ✅ 工作區 - Iteration Plans/Progress
├── 06_quality/                    # ✅ 工作區 - Code Quality/Security/Perf
├── 07_design/                     # ✅ 工作區 - UI/UX/Database Design
└── 08_deployment/                 # ✅ 工作區 - CI/CD/Release Notes

build/                             # 建置過程目錄
├── planning/archive/              # ✅ 工作區 - 歸檔計劃
├── reports/analysis/              # ✅ 工作區 - 分析報告
└── reports/kpi/                   # ✅ 工作區 - KPI 報告
```

**管理規則**:
- ✅ 升版時應 COPY（保留目錄結構）
- ✅ 使用 `.gitkeep` 檔案保留空目錄
- ❌ 不應刪除（即使為空）

---

#### 🟡 類型 2: 預留目錄（未來功能）

**定義**: 為未來版本規劃的功能預留的目錄，尚未實作對應內容

**特徵**:
- 🚧 目前為空，未來會補充內容
- ✅ 使用 `README.md` 說明預留用途
- ✅ Git 追蹤時保留目錄結構

**範例目錄**:
```
docs_template/scenario_specific/  # 情境專屬模板（部分預留）
├── documentation/                 # 🚧 預留 - 技術文檔情境模板 (v0.09+)
├── analysis/                      # ✅ 已實作 - 分析情境模板（3個模板）
├── integration/                   # 🚧 預留 - 第三方整合情境模板 (v0.09+)
├── testing/                       # 🚧 預留 - 測試策略情境模板 (v0.09+)
├── performance/                   # 🚧 預留 - 效能優化情境模板 (v0.09+)
└── devops/                        # ✅ 已實作 - 多個 CI/CD 模板 (2026-03-22 擴充)

docs_template/support/             # 支援模板（部分預留）
├── operations/                    # 🚧 預留 - 營運支援模板 (v0.09+)
├── monitoring/                    # 🚧 預留 - 監控可觀測性模板 (v0.09+)
└── [其他已實作的支援模板檔案]

releases/v0.09/package/            # 🚧 預留 - 發布打包功能 (未來功能)
```

**管理規則**:
- ✅ 升版時應 COPY（保留規劃結構）
- ✅ 必須包含 `README.md` 說明預留用途、開發計劃、預計版本
- ✅ `README.md` 應明確標示狀態：`**狀態**: 🚧 待補充（預留目錄）`
- 📝 未來實作時，移除 README.md 的預留標示，加入實際模板檔案

**README.md 必要內容**:
1. 狀態標示：`🚧 待補充（預留目錄）`
2. 目錄用途說明
3. 預計包含的模板/功能清單（使用 `- [ ]` checkbox）
4. 開發計劃（預計版本、優先級、依賴）
5. 最後更新時間與版本號

---

#### 🔴 類型 3: 錯誤空目錄（應處理）

**定義**: 不屬於上述兩類的空目錄，可能是遺漏、錯誤或廢棄目錄

**特徵**:
- ❌ 未在 FILE_DIRECTORY_RULES.md 中定義
- ❌ 無 `.gitkeep` 或 `README.md`
- ❌ 不符合框架設計意圖

**處理方式**:
1. **確認性質**:
   - 檢查 FILE_DIRECTORY_RULES.md 是否有定義
   - 檢查歷史版本是否曾有內容
   - 確認是否為升版遺漏

2. **處理決策**:
   - **選項 A - 補充內容**: 如果應該有內容但遺漏，補充檔案或模板
   - **選項 B - 轉為預留目錄**: 如果是未來規劃，加入 README.md 說明
   - **選項 C - 刪除目錄**: 如果確認不需要，刪除並從 FILE_DIRECTORY_RULES.md 移除定義

**已處理的錯誤空目錄（v0.09）**:
- ❌ `build/systems/` - 已刪除（v0.03 系統文檔已廢棄，從 FILE_DIRECTORY_RULES 移除定義）
- ❌ `build/testing/` - 已刪除（未定義用途，確認不需要）

---

### 空目錄檢查清單

在版本維護時，應定期檢查空目錄：

```bash
# 1. 掃描所有空目錄
find AISDLC_v0.XX -type d -empty

# 2. 檢查每個空目錄的性質
# - 是否有 .gitkeep？（工作區目錄）
# - 是否有 README.md？（預留目錄）
# - 都沒有？（可能是錯誤空目錄）

# 3. 確認是否在 FILE_DIRECTORY_RULES.md 中定義
grep -r "空目錄名稱" FILE_DIRECTORY_RULES.md
```

### 升版時的空目錄處理

**版本升級時 (v0.0X → v0.0Y)**:

1. **工作區目錄**:
   ```bash
   # 複製目錄結構和 .gitkeep
   cp -r AISDLC_v0.0X/docs AISDLC_v0.0Y/
   cp -r AISDLC_v0.0X/build/planning AISDLC_v0.0Y/build/
   cp -r AISDLC_v0.0X/build/reports AISDLC_v0.0Y/build/
   ```

2. **預留目錄**:
   ```bash
   # 複製目錄結構和 README.md
   cp -r AISDLC_v0.0X/docs_template/scenario_specific AISDLC_v0.0Y/docs_template/
   cp -r AISDLC_v0.0X/docs_template/support AISDLC_v0.0Y/docs_template/

   # 更新 README.md 中的版本號
   find AISDLC_v0.0Y -name "README.md" -type f | \
     xargs sed -i '' 's/v0\.0X/v0.0Y/g'
   ```

3. **錯誤空目錄**:
   - 不應存在於升版來源，如發現應先處理

---

## 📦 歸檔機制

### 歸檔時機

當新版本發布時，舊版本的 `build/` 目錄應歸檔：

```bash
# 範例: v0.09 發布時歸檔 v0.09 的 build/
mkdir -p /AISDLC_ALL/build_archives/v0.09
mv /AISDLC_ALL/AISDLC_v0.09/build/* /AISDLC_ALL/build_archives/v0.09/
```

### 歸檔內容

歸檔的內容包括：
- ✅ 所有 Phase 報告
- ✅ 所有計劃文檔
- ✅ 所有驗證報告
- ✅ 系統機制文檔
- ✅ KPI 和分析報告

### 歸檔結構

```
build_archives/
└── v0.0X/
    ├── planning/
    │   ├── active/
    │   └── archive/
    ├── reports/
    │   ├── phase/
    │   ├── kpi/
    │   ├── verification/
    │   └── analysis/
    ├── systems/
    └── README.md                # 歸檔說明 (應創建)
```

---

## ✅ 實踐檢查清單

### 版本升級檢查清單

- [ ] **第 1 步**: 創建完整備份
  - [ ] 執行 `tar` 創建壓縮檔
  - [ ] 驗證備份完整性

- [ ] **第 2 步**: 歸檔 build/ 目錄
  - [ ] 創建 `build_archives/v0.0X/` 目錄
  - [ ] 移動所有 build/ 內容
  - [ ] 創建歸檔說明 README.md

- [ ] **第 3 步**: 創建新版本目錄
  - [ ] 創建 `AISDLC_v0.0Y/` 目錄

- [ ] **第 4 步**: 複製層次 2 內容
  - [ ] 複製 agent/
  - [ ] 複製 workflow/
  - [ ] 複製 docs_template/
  - [ ] 複製 prompts/
  - [ ] 複製 scenarios/
  - [ ] 複製 tools/
  - [ ] 複製 guides/
  - [ ] 複製 docs/
  - [ ] 複製 AISDLC_INIT.md
  - [ ] 複製 FILE_DIRECTORY_RULES.md

- [ ] **第 5 步**: 創建新版本特定文檔
  - [ ] 創建新的 README.md
  - [ ] 創建 build/logs/CHANGELOG_v0.0Y.md

- [ ] **第 6 步**: 批量更新版本號
  - [ ] 執行 find + sed 更新所有文件
  - [ ] 手動檢查關鍵文件
  - [ ] 更新 UPGRADE_SOP 版本號

- [ ] **第 7 步**: 創建新版本 build/ 結構
  - [ ] 創建 build/ 子目錄
  - [ ] 創建 build/logs/CHANGELOG_v0.0Y.md

- [ ] **第 8 步**: 驗證
  - [ ] 驗證目錄結構完整
  - [ ] 驗證關鍵文件存在
  - [ ] 驗證版本號一致性

### 日常開發檢查清單

- [ ] **創建文件時**
  - [ ] 確定文件屬於哪個層次 (1/2/3)
  - [ ] 放置到正確的目錄
  - [ ] 使用正確的命名規範
  - [ ] **立即更新 FILE_DIRECTORY_RULES.md**

- [ ] **移動文件時**
  - [ ] 更新所有內部引用
  - [ ] 更新相關索引文件
  - [ ] **立即更新 FILE_DIRECTORY_RULES.md**
  - [ ] 記錄到變更日誌

- [ ] **刪除文件時**
  - [ ] 檢查是否有其他文件引用
  - [ ] 更新相關索引
  - [ ] **立即更新 FILE_DIRECTORY_RULES.md**
  - [ ] 考慮是否應歸檔而非刪除

---

## 🔍 常見問題 (FAQ)

### Q1: 為什麼需要三層次分類？

**A**:
- **層次 1** 避免跨版本文件重複
- **層次 2** 明確可執行內容的演進路徑
- **層次 3** 分離臨時建置文件，保持版本目錄整潔

### Q2: build/ 目錄什麼時候歸檔？

**A**: 當新版本發布並穩定後，舊版本的 build/ 目錄應歸檔到 `build_archives/v0.xx/`。通常在下一版本開始開發前執行。

### Q3: 如果忘記歸檔 build/ 就升級了怎麼辦？

**A**:
1. 從備份恢復舊版本的 build/ 目錄
2. 執行歸檔操作
3. 如果沒有備份，build/ 內容將丟失（這就是為什麼第一步是創建備份）

### Q4: 為什麼 v0.09 改變 CHANGELOG.md 的位置？

**A**: 從 v0.09 開始，CHANGELOG 從根目錄移至 `build/logs/CHANGELOG_v0.XX.md`，原因：
1. **版本專屬**：每個版本有獨立的變更記錄
2. **新檔非拷貝**：升版時創建新的 CHANGELOG，記錄該版本變更
3. **更清晰的組織**：變更日誌屬於該版本的建置記錄

### Q5: 為什麼 v0.09 整合 VERIFICATION_CHECKLIST 和 VERSION_UPDATE_CHECKLIST？

**A**: 從 v0.09 開始，這兩個檢查清單整合至 `AISDLC_v0.XX_UPGRADE_SOP.md`，原因：
1. **單一事實來源**：所有升版相關資訊集中在一個檔案
2. **更容易追蹤**：所有檢查項目、命令、步驟都在 UPGRADE_SOP 中
3. **減少遺漏**：檢查清單作為 SOP 的附錄

**整合位置**:
- `附錄 A: 完整驗證檢查清單` ← VERIFICATION_CHECKLIST.md 內容
- `附錄 B: 版本更新快速參考` ← VERSION_UPDATE_CHECKLIST.md 內容

---

## 📚 相關文檔

- [README.md](README.md) - AISDLC v0.09 版本說明
- [AISDLC_INIT.md](AISDLC_INIT.md) - 核心入口與初始化
- [AISDLC_v0.10_UPGRADE_SOP.md](AISDLC_v0.10_UPGRADE_SOP.md) - 下次升版 SOP
- [build/logs/CHANGELOG_v0.09.md](build/logs/CHANGELOG_v0.09.md) - v0.09 版本變更記錄
- [CLAUDE.md](../CLAUDE.md) - Claude Code 專案指引

---

**文檔維護**: 本文檔應隨框架演進持續更新，確保分類規則清晰且可執行。
