# AISDLC Claude Rules 配置檔
# AISDLC Claude Code Automated Rules Configuration

> **版本**: v1.1
> **適用範圍**: AISDLC v0.09+ 所有專案
> **最後更新**: 2026-04-11

---

## 🔴 本檔案用途

**本檔案是 AISDLC 專案的 Claude Code 自動化規則配置檔**，當 Claude Code 載入 AISDLC_INIT.md 時，應自動套用以下所有規則。

**⚠️ CRITICAL**: 這些規則是強制性的，必須在每個使用 AISDLC 框架的專案中自動執行。

---

## 📋 規則清單

### 1. 溝通語言規範（Communication Language Policy）

**強制規則**:
- ✅ **所有執行過程中的回覆訊息必須使用繁體中文**
- ✅ **所有狀態更新、確認訊息、說明文字必須使用繁體中文**
- ✅ **所有 Todo 任務描述必須使用繁體中文**
- ✅ **專有名詞保持原文**（AISDLC, Workflow, Agent, SOP, metadata, YAML, Markdown）
- ✅ **文檔標題可中英並列**

**範例**:
```
✅ 正確: 「完成！我已成功修正 3 個檔案」
❌ 錯誤: "Perfect! I have successfully modified 3 files"
```

---

### 2. 文檔目錄規範（Document Directory Standards）

**🛑 強制載入規則（CRITICAL）**:
- 🔴 **每次寫檔前，必須先讀取 DEVELOPMENT_DIRECTORY_STRUCTURE.md**
- 📖 **路徑**: `DEVELOPMENT_DIRECTORY_STRUCTURE.md`（位於 AISDLC_v0.09 工作目錄）
- ✅ **確認文件應放置的正確編號目錄**

**強制規則（開發專注版 v0.09）**:
- ✅ **專案文檔必須放置於 `docs/` 目錄**
- ✅ **必須遵循 8 層編號目錄結構（開發專注版 - DEVELOPMENT_DIRECTORY_STRUCTURE.md）**:
  - `docs/01_requirements/` - 需求文檔 (PRD, FRD, User Stories)
  - `docs/02_architecture/` - 架構設計 (SRD, API Specification)
  - `docs/03_testing/` - 測試文檔 (Test Plan, AT, Reports)
  - `docs/04_planning/` - 專案規劃 (Roadmap, Estimation, Task Breakdown)
  - `docs/05_development/` - 迭代執行 (Iteration Plans, Progress Logs)
  - `docs/06_quality/` - 程式碼品質 (Code Quality, Security, Performance)
  - `docs/07_design/` - 設計文檔 (UI/UX, Database, API Design)
  - `docs/08_deployment/` - 部署文檔 (CI/CD, Release Notes)

**驗證命令（開發專注版）**:
```bash
# 檢查 docs/ 目錄是否存在
ls -la docs/

# 檢查所有標準子目錄是否存在（開發專注版）
for dir in 01_requirements 02_architecture 03_testing 04_planning 05_development 06_quality 07_design 08_deployment; do
  if [[ -d "docs/$dir" ]]; then
    echo "✅ docs/$dir/ 存在"
  else
    echo "❌ docs/$dir/ 不存在"
  fi
done
```

---

### 3. 文檔命名規範（Document Naming Standards）

**強制規則**:
- ✅ **使用 PascalCase 或 Snake_Case**
- ✅ **英文命名，避免中文檔名**
- ✅ **包含文檔類型前綴**（PRD_, FRD_, Sprint_, AT_, API_）

**範例**:
```
✅ 正確:
  - PRD_MoneyTracker_Pro.md
  - Sprint_1_Execution_Plan.md
  - Architecture_Design_Document.md
  - API_Specification_Cloud_Sync.md

❌ 錯誤:
  - 專案需求文檔.md
  - sprint1.md
  - arch.md
```

---

### 4. 寫檔強制檢查（File Write Validation）

**🛑 強制讀取規則（CRITICAL）**:
1. **寫入框架檔案時**: 必須先讀取 `FILE_DIRECTORY_RULES.md`
2. **寫入專案文檔時**: 必須先讀取 `DEVELOPMENT_DIRECTORY_STRUCTURE.md`

**強制規則**:
- 🛑 **每次使用 Write/Edit 工具前必須先讀取對應規範文件**
- ✅ **確認檔案應放置的正確目錄**
- ✅ **確認檔案命名格式符合規範**
- ❌ **絕不寫入工作目錄外**（禁止: /tmp/*, /var/*, 系統目錄）
- ❌ **絕不在版本根目錄創建臨時檔案**

**快速規則 - AISDLC 框架 (build/ 目錄)**:
- 📊 分析報告 → `build/reports/analysis/{TOPIC}_{TYPE}.md`
- 📋 階段報告 → `build/reports/phase/{PHASE_NAME}_REPORT.md`
- ✅ 驗證報告 → `build/reports/verification/{TOPIC}_REPORT.md`
- 📈 KPI 報告 → `build/reports/kpi/{KPI_TOPIC}_REPORT.md`
- 📝 計劃文檔 → `build/planning/active/{PLAN_NAME}.md`
- 📦 歸檔計劃 → `build/planning/archive/{PLAN_NAME}.md`
- 📜 版本日誌 → `build/logs/CHANGELOG_v{VERSION}.md`

**快速規則 - 使用 AISDLC 的專案（開發專注版 docs/ 目錄）**:
- 📄 PRD/FRD/User Stories → `docs/01_requirements/`
- 🏗️ SRD/API Specification → `docs/02_architecture/`
- ✅ Test Plan/Test Cases/Reports → `docs/03_testing/`
- 📊 Roadmap/Estimation/Task Breakdown → `docs/04_planning/`
- 🔄 Iteration Plans/Progress Logs → `docs/05_development/`
- 🛡️ Code Quality/Security/Performance → `docs/06_quality/`
- 🎨 UI/UX/Database Design → `docs/07_design/`
- 🚀 CI/CD/Release Notes → `docs/08_deployment/`

**檢查清單**:
```
□ 已確認檔案類型
□ 已確認正確目錄
□ 已確認命名格式符合規範
□ 確認不是寫入禁止位置
```

---

### 5. 專案初始化標準（Project Initialization Standards）

**🔴 重要觀念**：
- ✅ **AISDLC 框架本身就是專案工作目錄**
- ✅ **專案文件直接寫入 AISDLC_v0.09/docs/ 目錄**
- ✅ **不需要在其他地方建立專案目錄**

**強制規則**:
- ✅ **必須在 AISDLC_v0.09 目錄內工作**
- ✅ **必須確保標準 docs/ 子目錄結構存在**
- ✅ **必須確保 CLAUDE.md 與 .claude/skills/ 已部署到專案根目錄**

**自動化腳本（推薦）**:
```bash
# 在已 clone 的框架目錄中執行（自動偵測本地框架，跳過下載）
cd /path/to/AISDLC
./AISDLC_v0.09/tools/init_project.sh -d ~/my-project
```

**手動初始化步驟** (如腳本不可用):
```bash
# 步驟 1: 進入 AISDLC_v0.09 目錄（即專案工作目錄）
cd /path/to/AISDLC/AISDLC_v0.09

# 步驟 2: 建立專案文檔目錄（遵循 DEVELOPMENT_DIRECTORY_STRUCTURE.md）
mkdir -p docs/{01_requirements,02_architecture,03_testing,04_planning,05_development,06_quality,07_design,08_deployment}

# 步驟 3: 確認 .claude/skills/ 已存在（33 個 Claude Code Skills）
ls .claude/skills/ | wc -l   # 應顯示 35（33個skill目錄 + README.md + SKILL_DEVELOPMENT_PLAN.md）
```

---

### 6. AISDLC_INIT.md 載入規範（AISDLC_INIT.md Loading Policy）

**強制規則**:
- ✅ **使用任何 AISDLC workflow 前，必須先載入 AISDLC_INIT.md**
- ✅ **載入時自動套用所有 Claude Rules**
- ✅ **自動偵測專案情境並載入對應 Agents**

**載入流程**:
```
1. 讀取 AISDLC_INIT.md
2. 讀取 tools/AISDLC_CLAUDE_RULES.md (本檔案)
3. 自動套用所有 Claude Rules
4. 識別專案情境類型
5. 載入對應 Primary 和 Supporting Agents
6. 開始執行專案 Workflow
```

---

### 7. ID 命名規範（ID Naming Convention）

**強制規則**:
- ✅ **遵循 AISDLC ID 命名規範**
  - F-XXX: Feature
  - NFR-XXX: Non-Functional Requirement
  - EPIC-XXX: Epic
  - US-XXX: User Story
  - AC-XXX-Y: Acceptance Criteria
  - API-XXX: API Endpoint
  - TC-XXX-Y-Z: Test Case
  - BUG-XXX: Bug
  - TECH-XXX: Technical Task

**參考文檔**:
- [AISDLC_ID_Naming_Convention.md](../guides/system/naming/AISDLC_ID_Naming_Convention.md)

---

### 8. 文檔品質標準（Document Quality Standards）

**強制規則**:
- ✅ **所有文檔交付前必須執行品質檢查**
- ✅ **使用 Document_Quality_Checklist.md 進行驗證**
- ✅ **確保文檔可讀性測試通過（15 分鐘測試法）**

**檢查清單**:
- [ ] 文檔完整性檢查
- [ ] 文檔品質檢查
- [ ] 可讀性測試（15 分鐘）
- [ ] 技術文檔專項檢查

**參考文檔**:
- [Document_Quality_Checklist.md](../guides/system/quality/Document_Quality_Checklist.md)

---

### 9. 開發-編譯-測試循環強制規則（Development-Build-Test Cycle）

> **新增日期**: 2025-01-11 | **適用範圍**: 開發 AISDLC 框架或使用 AISDLC 進行專案開發時

**強制執行原則**: 每完成一支程式（或一個功能單元），**必須立即執行**編譯-測試循環，**絕不累積開發**。

**執行步驟**:
```
開發 1 支程式 → 立即編譯 → 編譯失敗？→ 🔴 立即停止 → 依錯誤修復 → 重新編譯
                ↓
           編譯成功 ✅ → 執行單元測試 → 測試失敗？→ 🔴 立即停止 → 依規格修復 → 重新測試
                                          ↓
                                     測試通過 ✅ → 繼續開發下一支程式
```

**絕對禁止**:
- ❌ 累積開發多支程式後才一次編譯
- ❌ 編譯失敗後繼續開發其他功能
- ❌ 跳過單元測試
- ❌ 測試失敗後「先跳過」（例如：將測試註解掉）

**參考文檔**:
- [Development_Build_Test_Cycle.md](../guides/user/process/Development_Build_Test_Cycle.md)

---

### 10. AISDLC 升版執行規範（AISDLC Upgrade Execution Policy）

**強制規則** (僅適用於 AISDLC 框架維護者):
- 🛑 **執行升版前必須先讀取 AISDLC_UPGRADE_SOP_CheckList.md**
- 🛑 **每完成一個步驟，立即打勾**
- 🛑 **絕對禁止跳過任何步驟**
- 🛑 **升版完成前必須驗證 CheckList 所有項目已打勾**

**驗證命令**:
```bash
# 檢查未完成項目數量（必須為 0）
grep -c "^- \[ \]" AISDLC_v{OLD}/AISDLC_UPGRADE_SOP_CheckList.md
```

---

## 🚀 自動化執行流程

### 當 Claude Code 載入 AISDLC_INIT.md 時，自動執行:

```yaml
step_1:  讀取 AISDLC_INIT.md（本檔案）
step_2:  自動讀取 tools/AISDLC_CLAUDE_RULES.md（本檔案）
step_3:  自動套用所有 Claude Rules（溝通語言、文檔規範、寫檔檢查等）
step_4:  自動偵測當前作業系統（macOS/Linux/Windows）
step_5:  檢查專案是否已初始化
  - 如果未初始化: 提示執行 tools/init_project.sh
  - 如果已初始化: 繼續
step_6:  識別專案情境類型（透過問答或指令解析）
step_7:  🔴 從「Agent 自動載入配置表」讀取對應情境的配置
step_8:  🔴 自動載入 Primary Agents（讀取 YAML 並套用規則）
step_9:  🔴 記錄 Supporting Agents 列表（按需載入）
step_10: 載入對應 Workflows
step_11: 🔴 確認 .claude/skills/ 已部署（33 個 Claude Code Skills）
step_12: 顯示載入狀態確認（含可用 Skills 列表）
step_13: 開始執行 SOP
```

---

## ✅ 規則驗證清單

### 專案啟動時驗證:

```
□ 已套用溝通語言規範（繁體中文）
□ 已確認工作目錄為 AISDLC_v0.09
□ 已確認 docs/ 目錄結構存在
□ 已確認 CLAUDE.md 與 .claude/skills/ 已部署
□ 已載入對應專案情境的 Agents
□ 已準備好執行所有文檔命名規範
□ 已準備好執行寫檔強制檢查
```

### 文檔產出時驗證:

```
□ 檔案命名符合規範（PascalCase/Snake_Case）
□ 檔案放置於正確目錄（docs/*/）
□ 檔案包含正確的文檔元數據
□ 使用繁體中文撰寫（專有名詞除外）
□ 遵循 AISDLC ID 命名規範
□ 通過文檔品質檢查清單
```

---

## 🔧 規則自訂化（Optional）

專案可根據需求在 `CLAUDE.md` 中新增專案特定規則：

```markdown
## 專案特定 Claude Rules

### 額外命名規範
- [專案特定的命名規則]

### 額外文檔要求
- [專案特定的文檔要求]

### 禁止事項
- [專案特定的禁止事項]
```

---

## 📚 相關文檔

- [AISDLC_INIT.md](../AISDLC_INIT.md) - 框架初始化配置
- [FILE_DIRECTORY_RULES.md](../FILE_DIRECTORY_RULES.md) - 檔案目錄維護規則
- [DEVELOPMENT_DIRECTORY_STRUCTURE.md](../DEVELOPMENT_DIRECTORY_STRUCTURE.md) - 目錄結構標準
- [CLAUDE.md](../../CLAUDE.md) - Claude Code 專案指引（根目錄）
- [PROJECT_INITIALIZATION_GUIDE.md](../guides/user/onboarding/PROJECT_INITIALIZATION_GUIDE.md) - 專案初始化指南
- [PROJECT_DOCUMENTATION_STANDARDS.md](../guides/user/standards/PROJECT_DOCUMENTATION_STANDARDS.md) - 專案文檔標準

---

## 🔄 版本歷史

| 版本 | 日期 | 變更說明 |
|------|------|---------|
| v1.1 | 2026-04-11 | 新增 Rule 9「開發-編譯-測試循環」；更新自動化流程對齊 AISDLC_INIT.md 13步驟；修正 Rule 5 手動初始化路徑；版本編號原 Rule 9 升為 Rule 10 |
| v1.0 | 2025-01-10 | 初版發布，整合 CLAUDE.md, FILE_DIRECTORY_RULES.md, AISDLC_INIT.md 所有規則 |

---

**文檔元數據**:
- **文檔版本**: v1.1
- **建立日期**: 2025-01-10
- **最後更新**: 2026-04-11
- **維護者**: AISDLC Framework Team
- **文檔狀態**: Final
