# AISDLC v0.09 Framework（開發專注版）
# AI-assisted Software Development Lifecycle
# AI 輔助軟體開發生命週期框架 - 開發專注版

**版本**: v0.09（開發專注版）
**發布日期**: 2025-01-11
**最後更新**: 2026-04-11
**升級來源**: v0.07
**文檔類型**: 版本說明文檔
**維護狀態**: ✅ 正式版本
**適用對象**: 2 人開發團隊

---

## 🎯 版本摘要

AISDLC v0.09（開發專注版）是基於 v0.07 的精簡版本，專為 **2 人開發團隊**設計，主要特色：

### 🎯 開發專注版核心特色

1. **❌ 移除會議流程**
   - 移除 `06_meeting_minutes/` 目錄
   - 移除 Sprint Kickoff、Review、Retrospective 等會議流程
   - 移除會議記錄模板和相關文檔

2. **✅ 聚焦開發核心**
   - **產品研發** (Product R&D)
   - **規格撰寫** (Specification Writing)
   - **架構設計** (Architecture Design)
   - **系統分析** (System Analysis)
   - **系統開發** (System Development)
   - **系統測試** (System Testing)
   - **開發品質 QA** (Development Quality QA)
   - **安全合規** (Security Compliance)
   - **文件維護** (Document Maintenance)

3. **📁 重新設計目錄結構**
   - `04_project_management/` → `04_planning/`（開發規劃）
   - `05_sprint/` → `05_development/`（迭代開發）
   - 新增 `06_quality/`（品質保證：程式碼品質、安全、效能）
   - 移除 `06_meeting_minutes/`

4. **🔄 迭代制取代 Sprint 制**
   - iteration_1/, iteration_2/ 取代 Sprint 1, Sprint 2
   - 移除 Kickoff 會議，直接進入開發
   - 2 人團隊互審機制

---

## 📊 版本統計

| 項目 | 數量 | 說明 |
|------|------|------|
| **Core Agents** | 7 個 | 中文版 (`*-zh.yaml`)，主要維護 |
| **Specialized Agents** | 14 個 | 專業領域 Agent |
| **Workflows** | 8 個核心 + 情境專屬 | 工作流程定義 |
| **文檔模板** | 50+ 個 | PRD/FRD/SRD/API/Tests/Support |
| **場景 SOP** | 10 個 | 覆蓋主要開發情境 |
| **核心文檔** | 6 個 | 根目錄核心維護文檔 |
| **Skills** | 33 個 | Claude Code Skills (.claude/skills/) |
| **專案目錄** | 8 層 | 01-08 編號目錄（開發專注版） |

---

## 🚀 快速開始

### 30 秒上手

```bash
# 1. 加載框架入口
請閱讀並加載: AISDLC_INIT.md

# 2. 選擇你的開發情境
Greenfield / Brownfield / Refactoring / Integration /
Testing / Security / Performance / DevOps / Documentation / Migration

# 3. 閱讀情境 SOP QuickRef (5 分鐘)
scenarios/[your-scenario]/SOP_QuickRef.md

# 4. 使用對應的 Workflow
根據 AISDLC_INIT.md 中的 workflow-agent 映射表觸發
```

### 5 分鐘深入

詳見: [guides/user/onboarding/QUICK_START_GUIDE.md](guides/user/onboarding/QUICK_START_GUIDE.md)

---

## 📂 目錄結構

```
AISDLC_v0.09/
│
├── 🔴 核心維護文檔 (6 個) - 極度重要
│   ├── AISDLC_INIT.md                        # ⭐ 框架入口 (必讀)
│   ├── README.md                              # 本檔案 - 版本說明
│   ├── FILE_DIRECTORY_RULES.md               # ⭐ 檔案目錄維護與分類規則
│   ├── AISDLC_v0.10_UPGRADE_SOP.md           # ⭐ 下次升版 SOP (v0.09→v0.10)
│   ├── AISDLC_UPGRADE_SOP_CheckList.md       # ⭐ 升版執行檢查清單
│   └── DEVELOPMENT_DIRECTORY_STRUCTURE.md    # ⭐ 專案文檔目錄結構規範
│
├── 📁 agent/ - AI 角色定義
│   ├── core/                          # 7 個核心 Agent (中文版)
│   │   ├── 01.agent-template-zh.yaml
│   │   ├── 02.ba-business-analyst-zh.yaml
│   │   ├── 03.pm-po-agent-zh.yaml
│   │   ├── 04.sa-analyst-zh.yaml
│   │   ├── 05.sd-architect-zh.yaml
│   │   ├── 06.dev-developer-zh.yaml
│   │   ├── 07.qa-tester-zh.yaml
│   │   └── backup_en/                 # 英文版備份 (參考用)
│   ├── specialized/                   # 14 個專業化 Agent
│   └── README.md
│
├── 📁 workflow/ - 工作流程定義
│   ├── core/                          # 8 個核心 Workflow
│   │   ├── requirements-extraction.md
│   │   ├── validation-documentation.md
│   │   ├── user-story-design.md
│   │   ├── change-management.md
│   │   ├── api-specification.md
│   │   ├── consistency-check.md
│   │   ├── interaction-analysis.md
│   │   └── sprint-execution.md
│   └── scenario-specific/             # 場景專屬 Workflow
│
├── 📁 docs_template/ - 文檔模板
│   ├── core/                          # 核心模板
│   │   ├── api/                       # API 規格模板
│   │   ├── prd/                       # 產品需求文檔
│   │   ├── frd/                       # 功能需求文檔
│   │   ├── srd/                       # 系統需求文檔
│   │   └── tests/                     # 測試文檔
│   ├── prd/                           # 獨立 PRD 模板（MVP 定義等）
│   ├── srd/                           # 獨立 SRD 模板（時序圖、資料存取層等）
│   ├── scenario_specific/             # 場景專屬模板
│   │   ├── analysis/
│   │   ├── brownfield/
│   │   ├── devops/
│   │   ├── documentation/
│   │   ├── integration/
│   │   ├── migration/
│   │   ├── performance/
│   │   └── testing/
│   └── support/                       # 支援文檔模板（風險、詞彙表、協作日誌等）
│
├── 📁 scenarios/ - 10 大場景 SOP
│   ├── greenfield/                    # 新專案開發
│   ├── brownfield/                    # 現有專案優化
│   ├── refactoring/                   # 重構
│   ├── integration/                   # 整合
│   ├── testing/                       # 測試
│   ├── security/                      # 安全
│   ├── performance/                   # 效能
│   ├── devops/                        # DevOps
│   ├── documentation/                 # 文檔化
│   └── migration/                     # 技術棧遷移 🆕 2026-02-12
│
├── 📁 prompts/ - 提示詞模板
│   ├── complete-flow/
│   ├── quick-start/
│   └── scenario-prompts/
│
├── 📁 guides/ - 使用指南
│   ├── README.md                      # 指南導航
│   ├── system/                        # AI Agent 技術規範 (7 個子目錄，20 個檔案)
│   │   ├── naming/                    # ID 命名規範
│   │   ├── architecture/              # 架構設計規範
│   │   ├── api/                       # API 設計規範
│   │   ├── testing/                   # 測試規範
│   │   ├── quality/                   # 品質管理規範
│   │   ├── planning/                  # 規劃估算規範
│   │   └── agent/                     # Agent 管理規範
│   ├── user/                          # 人類使用指南 (5 個子目錄)
│   │   ├── onboarding/                # 新手入門 (8 個檔案)
│   │   ├── standards/                 # 標準規範 (2 個檔案)
│   │   ├── technical/                 # 技術指引 (1 個檔案)
│   │   ├── process/                   # 流程管理 (2 個檔案)
│   │   └── sample/                    # 真實情境範例 (21 個檔案)
│   └── backup/                        # 歷史文件備份
│
├── 📁 tools/ - 工具腳本
│   ├── init_project.sh                # 專案初始化腳本（macOS/Linux）
│   ├── init_project.ps1               # 專案初始化腳本（Windows）
│   ├── AISDLC_CLAUDE_RULES.md         # Claude Code 規則
│   └── PROJECT_CLAUDE_Template.md     # 專案 CLAUDE.md 模板
│
├── 📁 .claude/ - Claude Code 配置
│   └── skills/                        # 33 個 Claude Code Skills
│
├── 📁 docs/ - 專案文檔輸出目錄（框架即專案目錄）
│   ├── 01_requirements/               # 需求文檔
│   ├── 02_architecture/               # 架構設計
│   ├── 03_testing/                    # 測試文檔
│   ├── 04_planning/                   # 開發規劃
│   ├── 05_development/                # 開發文檔（迭代制）
│   ├── 06_quality/                    # 品質保證
│   ├── 07_design/                     # 設計文檔
│   └── 08_deployment/                 # 部署文檔
│
├── 📁 build/ - 建置文檔 (層次 3, 不升版拷貝)
│   ├── logs/                          # 版本日誌
│   ├── planning/
│   │   ├── active/                    # 執行中的計劃
│   │   └── archive/                   # 已完成的計劃
│   ├── reports/
│   │   ├── phase/                     # 階段報告
│   │   ├── kpi/                       # KPI 報告
│   │   ├── verification/              # 驗證報告
│   │   └── analysis/                  # 分析報告
│   └── README.md
│
└── 📁 releases/ - 發布資訊
    └── v0.09/
```

---

## 🆕 v0.09（開發專注版）新特性

### 1. ❌ 移除會議相關流程

**移除項目**:
- ❌ `docs/06_meeting_minutes/` 目錄
- ❌ Sprint Kickoff 會議流程
- ❌ Sprint Review 會議流程
- ❌ Sprint Retrospective 會議流程
- ❌ 會議記錄模板
- ❌ 利害關係人溝通會議

**保留的溝通方式**:
- ✅ 需求確認（文檔驅動）
- ✅ 設計審查（2 人互審）
- ✅ 程式碼審查（Pull Request）
- ✅ 測試結果確認（文檔驅動）

### 2. 📁 重新設計專案目錄結構

**新的 8 層目錄**:
```
docs/
├── 01_requirements/           # 需求文檔
├── 02_architecture/           # 架構設計
├── 03_testing/                # 測試文檔
├── 04_planning/               # ⬅️ 改名：開發規劃
├── 05_development/            # ⬅️ 改名：開發文檔（迭代制）
├── 06_quality/                # ⬅️ 新增：品質保證
├── 07_design/                 # 設計文檔
└── 08_deployment/             # 部署文檔
```

### 3. 🔄 迭代制開發流程

**迭代文檔結構**:
```
docs/05_development/
├── iteration_1/
│   ├── Iteration_1_Plan.md
│   ├── Daily_Progress_Log.md
│   ├── Technical_Research_POC.md
│   ├── Implementation_Notes.md
│   └── Iteration_1_Report.md
└── iteration_2/
    └── ...
```

### 4. 👥 2 人團隊協作模式

**協作特色**:
- ✅ 互審機制（Code Review）
- ✅ Pull Request 工作流
- ✅ 文檔驅動開發
- ✅ 快速迭代、精簡高效
- ✅ 品質優先（新增 06_quality/）

### 5. 🤖 Agent 自動載入機制（v0.09+ 新增）

- 10 個情境的 Primary/Supporting Agent 自動載入配置
- 按需載入，節省 70-85% token

### 6. 🛠️ 33 個 Claude Code Skills（v0.09+ 新增）

- 透過 `/skill-name` 快速觸發 AISDLC 能力
- 涵蓋 DevOps、整合、QA、安全、架構等

---

## 📖 核心文檔

### 🔴 核心維護文檔 (必讀)

| 文檔 | 用途 | 何時閱讀/更新 |
|------|------|---------------|
| [AISDLC_INIT.md](AISDLC_INIT.md) | 框架入口、Agent 自動載入配置 | **每次使用前必讀** |
| [FILE_DIRECTORY_RULES.md](FILE_DIRECTORY_RULES.md) | 檔案目錄維護與分類規則 | 目錄/檔案變更時立即更新 |
| [AISDLC_v0.10_UPGRADE_SOP.md](AISDLC_v0.10_UPGRADE_SOP.md) | v0.09→v0.10 升版 SOP | 執行下次升版時 |
| [AISDLC_UPGRADE_SOP_CheckList.md](AISDLC_UPGRADE_SOP_CheckList.md) | 升版執行強制檢查清單 | 升版過程中逐項打勾 |
| [DEVELOPMENT_DIRECTORY_STRUCTURE.md](DEVELOPMENT_DIRECTORY_STRUCTURE.md) | 專案文檔目錄結構規範 | 初次設定專案目錄時 |
| [README.md](README.md) | 版本說明、快速開始 | 初次使用 |

### 📚 重要參考文檔

| 文檔 | 位置 | 用途 |
|------|------|------|
| **CLAUDE.md** | AISDLC_ALL/ | Claude Code 專案指引 |
| **各目錄 README.md** | 各目錄 | 目錄內容說明 |
| **CHANGELOG** | build/logs/ | 版本變更日誌 |

---

## 🔧 使用場景

### 10 大場景 SOP

| 場景 | SOP 位置 | 適用情況 |
|------|----------|----------|
| **Greenfield** | [scenarios/greenfield/](scenarios/greenfield/) | 新專案開發 |
| **Brownfield** | [scenarios/brownfield/](scenarios/brownfield/) | 現有專案優化 |
| **Refactoring** | [scenarios/refactoring/](scenarios/refactoring/) | 程式碼重構 |
| **Integration** | [scenarios/integration/](scenarios/integration/) | 系統整合 |
| **Testing** | [scenarios/testing/](scenarios/testing/) | 測試專案 |
| **Security** | [scenarios/security/](scenarios/security/) | 安全強化 |
| **Performance** | [scenarios/performance/](scenarios/performance/) | 效能優化 |
| **DevOps** | [scenarios/devops/](scenarios/devops/) | DevOps 流程 |
| **Documentation** | [scenarios/documentation/](scenarios/documentation/) | 文檔化專案 |
| **Migration** | [scenarios/migration/](scenarios/migration/) | 技術棧遷移 🆕 |

每個場景包含:
- `SOP_QuickRef.md` - 快速參考 (5 分鐘)
- `SOP.md` - 標準 SOP
- `SOP_DeepDive.md` - 深度指南（部分場景）

---

## 🎓 學習路徑

### 1. 新用戶 (從零開始)

**時間**: 約 30 分鐘

1. 閱讀本 README.md (10 分鐘)
2. 閱讀 [AISDLC_INIT.md](AISDLC_INIT.md) (5 分鐘)
3. 閱讀 [guides/user/onboarding/QUICK_START_GUIDE.md](guides/user/onboarding/QUICK_START_GUIDE.md) (10 分鐘)
4. 使用 [guides/user/onboarding/SCENARIO_SELECTOR.md](guides/user/onboarding/SCENARIO_SELECTOR.md) 選擇情境 (5 分鐘)
5. 閱讀對應情境的 `SOP_QuickRef.md`
6. 開始工作

### 2. 框架維護者

**時間**: 約 1 小時

1. 詳細閱讀 [FILE_DIRECTORY_RULES.md](FILE_DIRECTORY_RULES.md) (20 分鐘)
2. 閱讀 [AISDLC_v0.10_UPGRADE_SOP.md](AISDLC_v0.10_UPGRADE_SOP.md) (30 分鐘)
3. 了解核心維護文檔的維護規則 (10 分鐘)

---

## 📋 版本對照

| 版本 | 發布日期 | 主要改進 | Core Agents | Scenarios | 根目錄核心文檔 |
|------|----------|----------|-------------|-----------|----------------|
| **v0.09** | 2025-01-11 | 開發專注版、Agent 自動載入、33 Skills | 7 個 (中文) | 10 個 | 6 個 |
| v0.08 | 2025-11-15 | 核心維護文檔機制強化 | 7 個 (中文) | 9 個 | 5 個 |
| v0.07 | 2025-10-30 | 中文化 + 結構優化 | 7 個 (中文) | 9 個 | 3 個 |
| v0.03 | 2025-10-23 | 模板簡化統一化 | 7 個 (混合) | 9 個 | 5 個 |
| v0.01 | 2025-10-18 | 初始版本 | - | - | - |

---

## 🎯 核心理念

AISDLC 框架基於以下核心理念:

1. **文檔驅動開發** (Document-driven Development)
   - 所有需求、設計、實現都有對應文檔
   - 文檔是開發的基礎，不是附屬品

2. **人機協作檢查點** (Human-AI Collaboration Checkpoints)
   - 關鍵決策點必須有人工確認
   - AI 不能自行假設或猜測
   - 使用 🔴 標記所有確認點

3. **零幻覺機制** (Zero Hallucination Measures)
   - Zero-Speculation Principle: AI 不能假設不清楚的事項
   - 30 分鐘硬超時: 無人回應自動暫停
   - 多 Agent 交叉驗證
   - 100% 規格符合性檢查

4. **完整可追溯性** (Full Traceability Chain)
   - Business Need → User Story → AC → AT → Implementation
   - 每個文檔都有上游和下游的連結
   - 變更可以追溯到源頭

5. **按需載入機制** (On-demand Loading)
   - 初始載入 ~200 tokens (vs 傳統 ~2000)
   - Agent 只在需要時載入
   - 節省 70-85% token 使用量

---

## 🔗 相關資源

### 框架核心

- **框架入口**: [AISDLC_INIT.md](AISDLC_INIT.md)
- **檔案目錄規則**: [FILE_DIRECTORY_RULES.md](FILE_DIRECTORY_RULES.md)
- **升版 SOP**: [AISDLC_v0.10_UPGRADE_SOP.md](AISDLC_v0.10_UPGRADE_SOP.md)

### 使用指南

- **快速開始**: [guides/user/onboarding/QUICK_START_GUIDE.md](guides/user/onboarding/QUICK_START_GUIDE.md)
- **場景選擇**: [guides/user/onboarding/SCENARIO_SELECTOR.md](guides/user/onboarding/SCENARIO_SELECTOR.md)
- **教學模式**: [guides/user/onboarding/TUTORIAL_MODE.md](guides/user/onboarding/TUTORIAL_MODE.md)

### Agent 配置

- **Core Agents**: [agent/core/README.md](agent/core/README.md)
- **Specialized Agents**: [agent/specialized/README.md](agent/specialized/README.md)
- **協作模式**: [agent/AGENT_COLLABORATION_PATTERNS.md](agent/AGENT_COLLABORATION_PATTERNS.md)

---

## 🤝 貢獻與反饋

### 發現問題

如發現問題或有改進建議:

1. **文檔問題**: 直接修改並記錄在相關 README.md
2. **功能建議**: 記錄在 `build/reports/analysis/`
3. **Bug 報告**: 記錄在 `build/reports/verification/`

### 維護規則

**重要**: 任何目錄或檔案的變更，必須立即更新 [FILE_DIRECTORY_RULES.md](FILE_DIRECTORY_RULES.md)

---

## 📄 授權

本框架採用 MIT License。

---

## ⚠️ 重要提醒

### 核心維護文檔 (必須了解)

1. **AISDLC_INIT.md** - 每次使用前必讀，包含 Agent 自動載入配置
2. **FILE_DIRECTORY_RULES.md** - 目錄/檔案變更時必須立即更新
3. **AISDLC_v0.10_UPGRADE_SOP.md** - 下次升版的操作指南
4. **AISDLC_UPGRADE_SOP_CheckList.md** - 升版執行時強制逐項打勾

### 升版注意事項

- UPGRADE_SOP 是為**下一次升版**準備的 (不是當前升版的記錄)
- 升版時必須先讀取 CheckList，逐項執行後打勾
- 所有核心維護文檔都標記為 🔴 極度重要

---

**版本**: v0.09
**最後更新**: 2026-04-11
**下一版本**: v0.10（升版 SOP 已準備）
