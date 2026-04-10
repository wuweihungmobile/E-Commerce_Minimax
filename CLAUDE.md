# CLAUDE.md
# Claude Code Project Guidance for AISDLC Framework

**Last Updated**: 2025-01-11
**Current Version: v0.09（開發專注版）
**Document Purpose**: Provide guidance to Claude Code when working with AISDLC framework

---

> **🔴 Important Notice 🔴**
>
> This file provides critical guidance for Claude Code (claude.ai/code) when working with the AISDLC Framework.
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
   - ✅ 正確：「修正 Workflow metadata」
   - ❌ 錯誤：「Fix Workflow metadata」

4. **專有名詞保持原文（不翻譯）**
   - 保持原文：AISDLC, Workflow, Agent, SOP, metadata, YAML, Markdown
   - 原因：專有名詞，翻譯後詞不達意

5. **文檔內的標題可以中英並列**
   - ✅ 允許：`# 問題修復報告 / Issue Resolution Report`
   - ✅ 允許：`# 問題修復報告`（僅繁體中文更佳）

### 實際範例（Examples）

**錯誤示範 ❌**：
```
Perfect! I can confirm that all work has been completed successfully.

## ✅ Work Completion Summary

Both final documents have been successfully created and updated.
```

**正確示範 ✅**：
```
完美！我可以確認所有工作都已成功完成。

## ✅ 工作完成總結

兩份最終文檔都已成功建立和更新。
```

### 為什麼這很重要？

- 使用者是繁體中文使用者
- 保持一致的溝通體驗
- 避免語言混雜造成閱讀困擾
- 提升專業性和親和力

**🔴 請嚴格遵守此規範，每次執行任務時都必須使用繁體中文回覆！🔴**

---

## 🔴 開發-編譯-測試循環強制規則（2025-01-11 新增）

**CRITICAL: 開發 AISDLC 框架或使用 AISDLC 進行專案開發時，必須嚴格遵守以下規則**

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

### 為什麼需要這個循環？

**問題場景（不使用循環）**:
- 開發 10 支程式 → 一次編譯 → 發現 50 個錯誤 → 花 3 小時修復 → 仍有錯誤
- 後果：錯誤累積，難以定位問題源頭

**正確做法（使用循環）**:
- 開發第 1 支 → 編譯 → 3 個錯誤 → 5 分鐘修復 → 測試通過
- 開發第 2 支 → 編譯 → 1 個錯誤 → 2 分鐘修復 → 測試通過
- 優勢：問題即時發現，容易定位，修復成本低

### 詳細規範文檔

完整執行指南請參考：[Development_Build_Test_Cycle.md](AISDLC_v0.09/guides/user/process/Development_Build_Test_Cycle.md)

**🔴 此規則適用於所有使用 AISDLC 的開發情境！🔴**

---

## 🔴 AISDLC 升版執行強制規則（2025-12-11 新增）

**CRITICAL: 執行 AISDLC 升版時，Agent 必須嚴格遵守以下規則**

### 強制執行流程

**原則**: 分段讀取 SOP → 立即執行 → 逐項打勾 → 才能進入下一階段

**執行步驟**:

1. **🔴 第一步：讀取 CheckList 檔案**
   - 路徑: `AISDLC_v{OLD}/AISDLC_UPGRADE_SOP_CheckList.md`
   - 目的: 了解升版包含哪些階段和檢查點

2. **🔴 第二步：逐階段執行（階段 1-7）**

   對於每個階段 N (N = 1 to 7):

   a. **讀取 SOP 該階段完整內容**
      - 使用 Read 工具讀取 `AISDLC_v0.09_UPGRADE_SOP.md`
      - 找到「## 階段 N」標題
      - 讀取該階段的所有內容（包含所有子步驟）
      - **必須找到「階段 N 步驟清單」** (如：階段 1 步驟清單 第 520-527 行)

   b. **按照步驟清單順序執行**
      - 從步驟清單的第一項開始執行
      - 嚴格按照清單順序，不可跳過任何步驟
      - 不可假設步驟內容，必須從 SOP 讀取命令

   c. **每完成一個子步驟，立即打勾**
      - 使用 Edit 工具修改 `AISDLC_UPGRADE_SOP_CheckList.md`
      - 將 `- [ ]` 改為 `- [x]`
      - 不可批次打勾，必須逐項打勾

   d. **該階段所有步驟完成後，才能進入下一階段**
      - 驗證該階段 CheckList 所有項目已打勾
      - 才能繼續讀取下一階段 SOP

3. **🔴 第三步：最終驗證**
   - 執行命令: `grep -c "^- \[ \]" AISDLC_v{OLD}/AISDLC_UPGRADE_SOP_CheckList.md`
   - 預期結果: 0 (所有項目已完成)
   - 如有未完成項目: 立即停止並報告

### 絕對禁止的行為

1. **❌ 禁止假設步驟順序**
   - 錯誤範例: 看到 CheckList 編號 1.2, 1.2.1，假設 1.2 在前
   - 正確做法: 必須讀取 SOP「階段 N 步驟清單」確認順序

2. **❌ 禁止只讀取 CheckList 就執行**
   - CheckList 只是勾選用，不包含完整命令和說明
   - 必須從 SOP 讀取完整步驟內容

3. **❌ 禁止跨階段執行**
   - 必須完成階段 1 所有步驟，才能開始階段 2
   - 不可同時執行多個階段

4. **❌ 禁止依賴「常識」或「記憶」**
   - 每個步驟都必須從 SOP 實際讀取
   - 不可依賴之前的升版經驗

5. **❌ 禁止批次打勾**
   - 每完成一個子步驟，立即打勾一次
   - 不可完成多個步驟後才一次打勾

### 執行範例

**正確執行階段 1 的流程**:

```
1. Agent: 讀取 AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md
2. Agent: 發現階段 1 有 7 個主要步驟
3. Agent: 讀取 SOP「階段 1」完整內容 (第 512-800 行)
4. Agent: 找到「階段 1 步驟清單」(第 520-527 行)
5. Agent: 看到正確順序是:
   1.1 → 1.2.1 → 1.2 → 1.3 → 1.4 → 1.4.1 → 1.5
6. Agent: 執行 1.1，完成後打勾
7. Agent: 執行 1.2.1 (在 1.2 之前！)，完成後打勾
8. Agent: 執行 1.2，完成後打勾
9. Agent: ... 繼續執行剩餘步驟
10. Agent: 驗證階段 1 所有步驟已打勾
11. Agent: 開始讀取階段 2 SOP
```

**錯誤執行範例**:

```
❌ Agent: 看到 CheckList 有 1.1, 1.2, 1.2.1
❌ Agent: 假設順序是 1.1 → 1.2 → 1.2.1
❌ Agent: 沒有讀取 SOP「階段 1 步驟清單」
❌ Agent: 直接執行 1.1 → 1.2 → 發現錯誤
```

### 為什麼需要這些規則？

**歷史教訓 (2025-12-11)**:
- Agent 看到 CheckList 編號 1.2, 1.2.1，假設 1.2 在前
- 沒有讀取 SOP 第 520-531 行的「階段 1 步驟清單」和「執行順序說明」
- 導致 1.2.1 (備份 CheckList) 未在 1.2 之前執行
- 這正是 SOP 警告的「禁止假設」行為

**根本問題**: Agent 依賴「常識」而非「實際讀取文件」

**解決方案**: 強制分段讀取 SOP，讓 Agent 無法跳過任何步驟

---

## Repository Overview

This is the **AISDLC (AI-assisted Software Development Lifecycle) Framework** - a structured system for AI-assisted software development. The framework uses AI agents (simulating team roles like SA, BA, PM/PO, SD, QA) and standardized workflows to guide development from requirements gathering through implementation.

**Core Philosophy**: Document-driven development with strong human-AI collaboration checkpoints to prevent AI hallucination and ensure specification compliance.

**Latest Version: v0.09** (Released: 2025-11-15)
- Main improvements: Core maintenance document mechanism enhancement
- File reorganization: FILE_CLASSIFICATION_RULES.md → FILE_DIRECTORY_RULES.md
- New: AISDLC_v0.09_UPGRADE_SOP.md (prepared for next upgrade)
- New: build/logs/ directory for version-specific logs

## Architecture

The framework operates on three pillars:

1. **Agents** ([agent/](AISDLC_v0.01/agent/)) - AI personas simulating development roles with specific expertise
2. **Workflows** ([workflow/](AISDLC_v0.01/workflow/)) - Reusable standardized processes for different development phases
3. **Docs** ([docs_template/](AISDLC_v0.01/docs_template/)) - Standard document templates (PRD/FRD/SRD/API specs)

### Key Concept: On-Demand Loading

The framework uses a token-efficient on-demand loading mechanism defined in [AISDLC_INIT.md](AISDLC_v0.01/AISDLC_INIT.md):
- Initial load: ~200 tokens (vs ~2000 for traditional full load)
- Agents are loaded only when their workflows are triggered
- Saves 70-85% of token usage

## Working with AISDLC Files

### 🔴 寫檔強制檢查清單（CRITICAL - MUST CHECK BEFORE ANY Write/Edit OPERATION）

**⚠️ STOP! 每次使用 Write/Edit 工具前必須執行：**

**🛑 強制讀取規則（依據檔案類型）**:
1. **寫入 AISDLC 框架檔案時**:
   - 必須先讀取: `AISDLC_v0.0x/FILE_DIRECTORY_RULES.md`
   - 確認檔案應放在 build/ 的哪個子目錄

2. **寫入專案文檔時**:
   - 必須先讀取: `AISDLC/framework/DEVELOPMENT_DIRECTORY_STRUCTURE.md`
   - 確認文檔應放在 docs/ 的哪個編號目錄 (01-08)

3. **✅ 確認檔案命名格式符合規範**
   - 範例: `{PHASE_NAME}_REPORT.md`, `{TOPIC}_{TYPE}.md`

4. **❌ 絕不寫到工作目錄外（禁止: /tmp/*, /var/*, 系統目錄）**

**快速規則 - AISDLC 框架 (build/ 目錄)：**
- 📊 分析報告 → `build/reports/analysis/{TOPIC}_{TYPE}.md`
- 📋 階段報告 → `build/reports/phase/{PHASE_NAME}_REPORT.md`
- ✅ 驗證報告 → `build/reports/verification/{TOPIC}_REPORT.md`
- 📈 KPI 報告 → `build/reports/kpi/{KPI_TOPIC}_REPORT.md`
- 📝 計劃文檔 → `build/planning/active/{PLAN_NAME}.md`
- 📦 歸檔計劃 → `build/planning/archive/{PLAN_NAME}.md`
- 📜 版本日誌 → `build/logs/CHANGELOG_v{VERSION}.md`

**快速規則 - 使用 AISDLC 的專案 (docs/ 目錄 - 開發專注版)：**
- 📄 PRD/FRD/User Stories → `docs/01_requirements/`
- 🏗️ SRD/API Specification → `docs/02_architecture/`
- ✅ Test Plan/Test Cases/Reports → `docs/03_testing/`
- 📊 Roadmap/Estimation/Task Breakdown → `docs/04_planning/`
- 🔄 Iteration Plans/Progress Logs → `docs/05_development/`
- 🛡️ Code Quality/Security/Performance → `docs/06_quality/`
- 🎨 UI/UX/Database Design → `docs/07_design/`
- 🚀 CI/CD/Release Notes → `docs/08_deployment/`

**🔴 開發專注版特色（2025-01-11 更新）**:
- ✅ **移除會議目錄**: 不再有 `docs/06_meeting_minutes/`
- ✅ **專注開發**: 04_planning（開發規劃）、05_development（迭代執行）
- ✅ **品質優先**: 06_quality（程式碼品質、安全合規、效能優化）
- ✅ **適合 2 人團隊**: 精簡高效，快速迭代

**違規範例（絕對禁止）：**
- ❌ `/tmp/report.md` - 工作目錄外
- ❌ `AISDLC_v0.09/report.md` - 版本根目錄臨時檔案
- ❌ `/var/log/analysis.md` - 系統目錄

---

### 🔴🔴🔴 升版強制執行機制（CRITICAL - UPGRADE EXECUTION ENFORCEMENT）

**⚠️ STOP! 當使用者要求執行 AISDLC 升版時，必須強制執行以下步驟：**

#### 第一步：強制讀取 CheckList 檔案

- **🛑 絕對禁止直接開始升版！**
- **✅ 必須先讀取**: `AISDLC_v{OLD_VERSION}/AISDLC_UPGRADE_SOP_CheckList.md`
- **✅ 確認檔案存在且完整**
- **✅ 使用 CheckList 中的項目建立 TodoWrite 任務清單**

#### 第二步：逐項執行並打勾

- **🛑 每完成一個步驟，必須立即回到 CheckList 檔案打勾**
- **使用 Edit 工具將 `- [ ]` 改為 `- [x]`**
- **絕對禁止跳過任何步驟**
- **絕對禁止在未執行的情況下打勾**

**🔴🔴🔴 CheckList 路徑強制驗證（2025-12-12 新增）：**

**歷史慘痛教訓 (2025-12-12)**:
- Agent 在 v0.06 → v0.09 升版過程中，誤觸了 `AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md`
- 導致 v0.09 CheckList（應該是乾淨範本）被勾選了 7 個項目
- 影響下一次升版 (v0.09 → v0.09) 的執行

**根本原因**:
- 升版時，工作目錄中同時存在兩個 CheckList 檔案：
  - `AISDLC_v{OLD}/AISDLC_UPGRADE_SOP_CheckList.md` ← **應該修改**
  - `AISDLC_v{NEW}/AISDLC_UPGRADE_SOP_CheckList.md` ← **絕不可修改**
- Agent 沒有明確驗證檔案路徑，導致誤觸目標版本 CheckList

**強制執行規則**:

1. **🛑 每次使用 Edit 工具修改 CheckList 前，必須先驗證路徑**
   ```bash
   # 執行此命令驗證正確的 CheckList 路徑
   echo "正確路徑: AISDLC_v{OLD}/AISDLC_UPGRADE_SOP_CheckList.md"
   echo "錯誤路徑: AISDLC_v{NEW}/AISDLC_UPGRADE_SOP_CheckList.md"

   # 範例：v0.06 → v0.09 升版
   # ✅ 正確: AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md
   # ❌ 錯誤: AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md
   ```

2. **🛑 Edit 工具的 file_path 參數必須包含 {OLD} 版本號**
   - ✅ 正確範例: `file_path: "AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md"`
   - ❌ 錯誤範例: `file_path: "AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md"`

3. **🛑 在修改 CheckList 前，必須自我檢查：**
   - 我是在執行 v{OLD} → v{NEW} 升版嗎？
   - 我的 file_path 是否包含 `AISDLC_v{OLD}`？
   - 如果 file_path 包含 `AISDLC_v{NEW}`，**立即停止！這是錯誤路徑！**

4. **🛑 升版完成後，必須驗證目標版本 CheckList 保持乾淨**
   ```bash
   # 執行此命令驗證目標版本 CheckList 是否乾淨
   grep -c "^- \[x\]" AISDLC_v{NEW}/AISDLC_UPGRADE_SOP_CheckList.md
   # 預期結果: 0（所有項目未勾選）
   # 如果 > 0，表示誤觸了目標版本 CheckList，必須立即報告！
   ```

**防呆機制（Fool-Proof Mechanism）**:

- **原則**: 在整個升版過程中，**絕不允許 Agent 讀取或修改** `AISDLC_v{NEW}/AISDLC_UPGRADE_SOP_CheckList.md`
- **唯一例外**: 階段 7（最終驗證）可以「只讀驗證」目標版本 CheckList 是否乾淨
- **任何其他情況**: 如果 Agent 試圖修改目標版本 CheckList，視為嚴重錯誤

#### 第三步：驗證完成度

- **🛑 升版完成前，必須驗證 CheckList 所有項目已打勾**
- **執行命令**: `grep -c "^- \[ \]" AISDLC_v{OLD_VERSION}/AISDLC_UPGRADE_SOP_CheckList.md`
- **預期結果**: 0（所有項目已完成）
- **如有未完成項目**: 立即停止並報告

#### 為什麼需要這個機制？

**歷史慘痛教訓 (2025-12-09)**:
- Agent 在升版過程中**直接讀取 SOP**，完全忽略 CheckList
- 即使 CheckList 檔案存在，Agent 也不知道要使用它
- 導致階段 6 和階段 7 被跳過，發布包未創建
- 使用者極度不滿

**核心問題**:
- SOP 文字警告可以被忽略
- CheckList 勾選框無法偽造
- 必須強制 Agent 使用 CheckList

**解決方案**:
- ✅ CLAUDE.md 明確要求「必須先讀取 CheckList」
- ✅ 每個步驟完成後立即打勾
- ✅ 最終驗證未勾選項目數量必須為 0

#### CheckList 檔案位置

- **v0.06 → v0.09 升版**: `AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md`
- **v0.09 → v0.09 升版**: `AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md`
- **命名規則**: `AISDLC_UPGRADE_SOP_CheckList.md`（固定名稱）

#### 強制執行流程

```
1. 使用者：「開始執行升版」
2. Agent：讀取 AISDLC_v{OLD}/AISDLC_UPGRADE_SOP_CheckList.md
3. Agent：建立 TodoWrite 任務（基於 CheckList）
4. Agent：執行第一個任務
5. Agent：打勾第一個 CheckList 項目（Edit 工具）
6. Agent：執行第二個任務
7. Agent：打勾第二個 CheckList 項目
... 重複直到所有項目完成
43. Agent：驗證 grep -c "^- \[ \]" 結果為 0
44. Agent：報告升版完成
```

**🔴 違反此機制將視為執行失敗！🔴**

---

### Critical Files

1. **[AISDLC_INIT.md](AISDLC_v0.01/AISDLC_INIT.md)** - Must be loaded first before using any workflow. Contains workflow-agent mapping table and on-demand loading rules.

2. **[Project_README.md](AISDLC_v0.01/Project_README.md)** - Primary user documentation with 30-second integration guide and complete usage scenarios.

3. **[INTEGRATION_GUIDE.md](AISDLC_v0.01/INTEGRATION_GUIDE.md)** - How to integrate AISDLC into any project, including LLM tool-specific instructions.

4. **[EXECUTION_CHECKLIST.md](AISDLC_v0.01/EXECUTION_CHECKLIST.md)** - Quality assurance checklist for verifying correct workflow execution.

5. **🔴 AISDLC_v{NEXT_VERSION}_UPGRADE_SOP.md** - **極度重要！** Upgrade SOP for next version. Must be maintained in version root directory.
   - Location: `AISDLC_v{CURRENT}/AISDLC_v{NEXT}_UPGRADE_SOP.md`
   - Example: [AISDLC_v0.09/AISDLC_v0.09_UPGRADE_SOP.md](AISDLC_v0.09/AISDLC_v0.09_UPGRADE_SOP.md)
   - **Critical**: Always copy and update version numbers during upgrades
   - **Wrong location**: ❌ build/planning/active/ (that's for active planning, not maintenance docs)

6. **🔴 FILE_DIRECTORY_RULES.md** - **極度重要！** File & Directory Maintenance Rules. Authoritative definition of all directories and files in the version.
   - **Chinese Name**: 檔案目錄維護與分類規則
   - Location: `AISDLC_v{VERSION}/FILE_DIRECTORY_RULES.md`
   - Example: [AISDLC_v0.09/FILE_DIRECTORY_RULES.md](AISDLC_v0.09/FILE_DIRECTORY_RULES.md)
   - **Critical**: Must be immediately updated when any directory or file changes
   - **Usage**: All file operations (write, archive, delete, maintenance) must follow this document

### Reference Guides (guides/)

Location: [guides/](AISDLC_v0.09/guides/)

**🆕 v0.09 Structure Update**: The guides/ directory has been reorganized into a clear two-tier structure:

- **[guides/system/](AISDLC_v0.09/guides/system/)** - Technical specifications for AI Agents (7 subdirectories, 13 files)
- **[guides/user/](AISDLC_v0.09/guides/user/)** - User-friendly guides for humans (4 subdirectories, 8 files)
- **[guides/README.md](AISDLC_v0.09/guides/README.md)** - Navigation hub with quick-start paths

**Key System Guides (for AI Agents):**

1. **[AISDLC_ID_Naming_Convention.md](AISDLC_v0.09/guides/system/naming/AISDLC_ID_Naming_Convention.md)** - Unified ID naming standards
   - 10 core ID types: EPIC, F, US, AC, AT, TC, API, NFR, BUG, TECH
   - Format definitions, examples, and usage scenarios
   - Requirements traceability matrix (RTM) examples
   - **When to use**: New project setup, requirements analysis, documentation

2. **[Document_Quality_Checklist.md](AISDLC_v0.09/guides/system/quality/Document_Quality_Checklist.md)** - Comprehensive document quality checklist
   - 4 major categories: Completeness, Quality, Readability, Technical Documentation
   - 19 stage-specific document standards
   - 15-minute readability test method
   - Specialized checks for API Spec, Architecture, User Story
   - **When to use**: Before document delivery, quality gate checkpoints, Greenfield SOP Stage 9

3. **[C4_Model_Guidelines.md](AISDLC_v0.09/guides/system/architecture/C4_Model_Guidelines.md)** - Architecture design guide
   - C4 model layer requirements and standards
   - **When to use**: Architecture design, SRD writing

4. **[Estimation_Standards.md](AISDLC_v0.09/guides/system/planning/Estimation_Standards.md)** - Estimation standardization
   - Story Points, Velocity, RICE scoring
   - **When to use**: Sprint planning, effort estimation

**Key User Guides (for Humans):**

- **[QUICK_START_GUIDE.md](AISDLC_v0.09/guides/user/onboarding/QUICK_START_GUIDE.md)** - 5-minute introduction to AISDLC
- **[SCENARIO_SELECTOR.md](AISDLC_v0.09/guides/user/onboarding/SCENARIO_SELECTOR.md)** - Find the right development scenario
- **[SMART_DEFAULTS.md](AISDLC_v0.09/guides/user/technical/SMART_DEFAULTS.md)** - Intelligent default configurations
- **[Code_Review_Guidelines.md](AISDLC_v0.09/guides/user/process/Code_Review_Guidelines.md)** - Code review best practices

**Directory Structure:**
```
guides/
├── README.md                    # Navigation hub
├── system/                      # For AI Agents (technical specs)
│   ├── naming/
│   ├── architecture/
│   ├── api/
│   ├── testing/
│   ├── quality/
│   ├── planning/
│   └── agent/
├── user/                        # For Humans (user-friendly guides)
│   ├── onboarding/
│   ├── standards/
│   ├── technical/
│   └── process/
└── archive/                     # Historical documents
```

**Note**: All guides should be referenced throughout the development lifecycle to ensure consistency and quality. Start with [guides/README.md](AISDLC_v0.09/guides/README.md) for quick navigation.

### Agent Configuration Files

**Locations:**
- **Core Agents**: [agent/core/](AISDLC_v0.09/agent/core/) - 7 核心 Agent（中文版，主要維護）
- **Specialized Agents**: [agent/specialized/](AISDLC_v0.09/agent/specialized/) - 14 專業化 Agent
- **Archive**: [agent/core/archive_en/](AISDLC_v0.09/agent/core/archive_en/) - 英文版備份（僅供參考）

**維護策略（2025-10-30 更新）：**
- ✅ **主要維護版本**：中文版 (`*-zh.yaml`) - 所有更新、新功能、Bug 修復都在此進行
- 📦 **歷史參考版本**：英文版備份於 `archive_en/` - 僅供術語對照和歷史參考，不再更新
- 🔄 **保留 `-zh` 後綴**：清楚標示為中文版，避免混淆

**Agent 檔案命名規則：**
- Core Agents: `0X.{role}-{type}-zh.yaml` (e.g., `04.sa-analyst-zh.yaml`)
- Specialized Agents: `{specialty}-{role}.yaml` or `{specialty}-{role}-zh.yaml`

**核心 Agent 及其角色：**
- **sa-analyst** (Amanda) - System Analyst, primary for requirements analysis
- **ba-business-analyst** (Beatrice) - Business Analyst, stakeholder validation
- **pm-po-agent** (Victoria) - Product Manager, business value decisions
- **sd-architect** (Marcus) - System Designer, technical architecture
- **dev-developer** (David) - Developer, implementation assessment
- **qa-tester** (Quincy) - QA Engineer, acceptance criteria and test scenarios

所有核心 Agent 均包含 **Phase 2: Collaboration Patterns & Scenario Usage** (v0.03-phase2)

### Workflow Files

Location: [workflow/](AISDLC_v0.01/workflow/)

Core workflows:
1. **unified-requirements-extraction** - Multi-format requirement analysis (screenshots, text, mixed)
2. **requirements-validation-and-documentation** - Deep validation → PRD/FRD generation
3. **user-story-and-design** - User stories → SRD generation
4. **requirements-change-management** - Handle requirement changes with traceability
5. **api-specification-generation** - Generate detailed API specs (mandatory when APIs exist)
6. **document-consistency-check** - Verify document consistency across PRD/FRD/SRD/API
7. **frontend-backend-interaction-analysis** - Design and document FE-BE interaction flows

Each workflow has corresponding prompts in [prompts/workflow-prompts/](AISDLC_v0.01/prompts/workflow-prompts/)

### Document Templates

Location: [docs_template/](AISDLC_v0.01/docs_template/)

Standard templates for:
- **PRD** (Product Requirements Document) - by PM/PO
- **FRD** (Functional Requirements Document) - by SA with BA validation
- **SRD** (System Requirements Document) - by SD, includes technical specs
- **API Specifications** - Mandatory detailed specs for each API endpoint
- **Acceptance Tests** - Test scenarios and criteria

Document flow: `PRD → FRD → SRD → API Specs → Implementation → AT → Test Report`

## Key Development Principles

### 1. Human-AI Collaboration Checkpoints

All workflows include mandatory human confirmation points (marked with 🔴):
- Never skip confirmation points
- Use "ask when uncertain" principle - AI must ask, never assume
- All critical decisions require human approval
- Document all collaboration in requirement-analysis-template

### 2. Zero Hallucination Measures (2025-09 Enhancement)

Critical anti-hallucination mechanisms:
- **Zero-Speculation Principle**: AI cannot assume anything unclear
- **30-Minute Hard Timeout**: Auto-pause if no human response
- **Multi-Agent Cross-Validation**: Consensus required for key decisions
- **100% Specification Compliance Gateway**: Mandatory pre-delivery verification
- **Document Consistency Validation**: Post-implementation verification against specs

### 3. Traceability Chain

Maintain complete traceability: `Business Need → User Story → AC → AT → Implementation`

Every document must reference and link to:
- Source documents (what it's based on)
- Derived documents (what's generated from it)

### 4. API Documentation is Mandatory

When system includes APIs:
- Each API endpoint MUST have a separate specification file: `API_[Module]_[Endpoint].md`
- Follow [API_Specification_Template.md](AISDLC_v0.01/docs_template/srd/API_Specification_Template.md)
- Maintain [API_Index.md](AISDLC_v0.01/docs_template/srd/API_Index_Template.md) for navigation
- Establish bidirectional links between API specs and SRD

## Common Development Workflows

### Modifying Workflow Definitions

When editing workflows in [workflow/](AISDLC_v0.01/workflow/):
1. Follow the structure in [workflow-template.md](AISDLC_v0.01/workflow/workflow-template.md)
2. Update workflow-agent mapping in [AISDLC_INIT.md](AISDLC_v0.01/AISDLC_INIT.md) if adding new workflows
3. Ensure all mandatory confirmation points are marked with 🔴
4. Test with corresponding prompts in [prompts/workflow-prompts/](AISDLC_v0.01/prompts/workflow-prompts/)

### Adding New Agent Types

When creating new agent configurations:
1. Use [agent-template-zh_OK.yaml](AISDLC_v0.09/agent/core/01.agent-template-zh_OK.yaml) as base (中文版模板)
2. Define clear `core_principles`, `collaboration_rules`, and `quality_standards`
3. **Include Phase 2 content**: `collaboration_patterns` and `scenario_usage`
4. Add to workflow-agent mapping table in [AISDLC_INIT.md](AISDLC_v0.01/AISDLC_INIT.md)
5. Document agent persona and responsibilities
6. **Name with `-zh.yaml` suffix** for Chinese version (Core Agents)
7. For Specialized Agents, consider creating `-zh.yaml` version for Chinese teams

### Creating New Document Templates

When adding templates to [docs_template/](AISDLC_v0.01/docs_template/):
1. Follow existing template structure (metadata, traceability, content sections)
2. Include clear traceability sections linking to upstream/downstream docs
3. Use standard ID formats: `US-XXX`, `AC-XXX-X`, `AT-XXX-X-Y`, `API-XXX`
4. Update [Doc_README.md](AISDLC_v0.01/docs_template/Doc_README.md) with new template info

### Updating Prompt Templates

Location: [prompts/](AISDLC_v0.01/prompts/)

When modifying prompts:
1. Maintain consistent structure across workflow-prompts subdirectories
2. Keep prompts focused on triggering specific workflows
3. Update [prompts/README.md](AISDLC_v0.01/prompts/README.md) if adding new prompts
4. Ensure prompts reference correct AISDLC_INIT.md loading sequence

## File Organization Standards

### Naming Conventions

- Agent files: `0X.{role}-{type}.yaml` or `0X.{role}-{type}-zh.yaml` (Chinese version)
- Workflow files: `{workflow-name}.md` (kebab-case)
- Document templates: `{DocType}_Template.md` (PascalCase with underscores)
- API specs: `API_[Module]_[Endpoint].md`

### Directory Structure

```
AISDLC_v0.09/                    # Latest version (recommended)
├── agent/                       # Agent configurations
│   ├── core/                    # 7 Core Agents (Chinese - Primary)
│   │   ├── 01.agent-template-zh_OK.yaml
│   │   ├── 02.ba-business-analyst-zh.yaml
│   │   ├── ...
│   │   ├── 07.qa-tester-zh.yaml
│   │   └── archive_en/          # English versions (Archive)
│   │       ├── README.md
│   │       └── [English Agent YAMLs]
│   ├── specialized/             # 14 Specialized Agents
│   │   ├── qa-automation.yaml
│   │   ├── dev-senior.yaml
│   │   └── ...
│   ├── AGENT_COLLABORATION_PATTERNS.md
│   ├── AGENT_PHASE2_UPDATE_GUIDE.md
│   └── README.md
├── scenarios/                   # 9 Development Scenarios
│   ├── greenfield/
│   ├── brownfield/
│   ├── refactoring/
│   ├── integration/
│   ├── testing/
│   ├── security/
│   ├── performance/
│   ├── devops/
│   └── documentation/
├── workflow/                    # Workflow process definitions
│   └── {workflow}/
├── docs_template/               # Document templates
│   ├── prd/
│   ├── frd/
│   ├── srd/
│   │   └── api/
│   └── tests/
├── prompts/                     # User-facing prompt templates
│   ├── workflow-prompts/
│   ├── complete-flow/
│   └── examples/
└── [Core Documentation]
    ├── AISDLC_INIT.md
    ├── Project_README.md
    ├── INTEGRATION_GUIDE.md
    └── EXECUTION_CHECKLIST.md

AISDLC_v0.01/                    # Legacy version (reference)
├── agent/                       # Mixed English/Chinese
└── ...
```

## Multi-language Support & Maintenance Strategy

### Core Agents (核心 Agent)

**Primary Version: Chinese (`*-zh.yaml`)**
- **Location**: `AISDLC_v0.09/agent/core/`
- **Status**: ✅ **Actively maintained** - All updates, new features, and bug fixes
- **Content**: All 7 core agents include Phase 2: Collaboration Patterns & Scenario Usage
- **Files**:
  - `01.agent-template-zh_OK.yaml`
  - `02.ba-business-analyst-zh.yaml`
  - `03.pm-po-agent-zh.yaml`
  - `04.sa-analyst-zh.yaml`
  - `05.sd-architect-zh.yaml`
  - `06.dev-developer-zh.yaml`
  - `07.qa-tester-zh.yaml`

**Archive Version: English (`*.yaml`)**
- **Location**: `AISDLC_v0.09/agent/core/archive_en/`
- **Status**: 📦 **Static backup** - For reference only, terminology comparison
- **Purpose**: Historical reference, English terminology lookup
- **Note**: Not actively updated

### Specialized Agents (專業化 Agent)

- **Location**: `AISDLC_v0.09/agent/specialized/`
- **Status**: Currently English, Chinese versions available on demand
- **Count**: 14 specialized agents for domain-specific expertise
- **Categories**:
  - QA Specialization: `qa-automation`, `qa-lead`, `qa-web-tester`, `qa-mobile-tester`
  - Development: `dev-senior`, `code-analyzer`
  - Architecture: `sd-web-architect`, `sd-mobile-architect`
  - Operations: `devops-engineer`, `security-engineer`, `performance-engineer`
  - Others: `integration-specialist`, `technical-writer`, `compliance-officer`

### Why Chinese-First?

1. **Primary Users**: Chinese-speaking development teams
2. **Maintenance Efficiency**: Reduces overhead, prevents version inconsistency
3. **Focus on Value**: Prioritizes feature improvements over translation sync
4. **Clear Separation**: `-zh` suffix clearly identifies Chinese versions
5. **Flexibility**: English versions preserved in archive for reference

## Testing and Validation

### Verifying Workflow Changes

After modifying a workflow:
1. Check against [EXECUTION_CHECKLIST.md](AISDLC_v0.01/EXECUTION_CHECKLIST.md)
2. Verify all 🔴 confirmation points are present and properly positioned
3. Ensure workflow references correct agent files from mapping table
4. Test with corresponding prompt template

### Document Template Validation

For template changes:
1. Verify all traceability sections are present
2. Check ID format consistency
3. Ensure template includes quality standards section
4. Validate against existing generated documents

### Consistency Checks

Use document-consistency-check workflow principles:
1. Verify cross-references between documents are valid
2. Check PRD → FRD → SRD → API traceability chain
3. Ensure consistent terminology across related docs
4. Validate all internal markdown links

## Important Notes

### Do NOT:
- Skip or remove confirmation points (🔴) from workflows
- Modify AISDLC_INIT.md workflow-agent mappings without updating corresponding workflows
- Create documents without proper traceability sections
- Allow AI to assume or speculate - always require explicit confirmation
- Skip document-consistency-check after implementation changes
- **Create new Core Agents in English** - use Chinese (`-zh.yaml`) versions
- **Modify files in `archive_en/`** - they are static backups for reference only
- Remove Phase 2 content (`collaboration_patterns`, `scenario_usage`) from agents

### DO:
- Load AISDLC_INIT.md before using any workflow
- Follow on-demand loading pattern for token efficiency
- Maintain complete traceability chains in all documents
- Document all human-AI collaboration decisions
- Generate mandatory API specifications when system includes APIs
- Run consistency checks after document updates
- Use standard ID formats and naming conventions
- **Use Chinese versions (`*-zh.yaml`) for all Core Agent references**
- **Include Phase 2 content** (collaboration_patterns, scenario_usage) in new agents
- **Reference English versions in `archive_en/`** only for terminology comparison
- **Consider creating `-zh.yaml` versions** for frequently used Specialized Agents

## Version Control Considerations

When this framework is integrated into projects:
- Some projects use Git submodules for multi-repo collaboration (frontend/backend sharing same docs)
- Document changes should be tracked with change history
- Use requirements-change-management workflow for systematic change handling
- Maintain backward compatibility in template changes when possible

## Getting Help

For understanding AISDLC usage:
1. Start with [Project_README.md](AISDLC_v0.01/Project_README.md) - 30-second quick start
2. Check [INTEGRATION_GUIDE.md](AISDLC_v0.01/INTEGRATION_GUIDE.md) for setup questions
3. Review [workflow/README.md](AISDLC_v0.01/workflow/README.md) for workflow selection guide
4. Consult [prompts/README.md](AISDLC_v0.01/prompts/README.md) for usage templates
5. Use [EXECUTION_CHECKLIST.md](AISDLC_v0.01/EXECUTION_CHECKLIST.md) for troubleshooting
