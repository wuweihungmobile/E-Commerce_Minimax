# AISDLC v0.09（開發專注版）升版標準作業程序 (SOP)
# AISDLC v0.09 → v0.10 Upgrade SOP

> **🔴🔴🔴 極度重要 - 強制執行警告 🔴🔴🔴**
>
> ## ⚠️ 執行者必讀 - 零遺漏原則
>
> **本 SOP 的每一個步驟、每一個命令區塊、每一個檢查點都必須執行，不可有任何遺漏！**
>
> ### 🚫 嚴禁的行為
>
> 1. **❌ 禁止自行判斷「這個步驟可以跳過」**
>    - 即使看起來簡單或重複，也必須執行
>    - 即使認為「應該沒問題」，也必須驗證
>
> 2. **❌ 禁止「我以為已經做了」的假設**
>    - 每個步驟都必須明確執行並記錄
>    - 所有檢查點都必須逐項確認
>
> 3. **❌ 禁止跳過驗證步驟**
>    - 階段 2.10 (releases/)：必須執行
>    - 階段 2.11 (拷貝驗證)：必須執行
>    - 階段 2.12 (完整性驗證)：必須執行
>    - 階段 5.8 (統計比較)：必須執行，包含完整的統計函數和差異分析表格
>
> ### ✅ 正確的執行方式
>
> 1. **逐步執行原則**
>    - 從階段 1.1 開始，按順序執行到階段 7 結束
>    - 每個子步驟都必須完整執行
>    - 每個命令區塊都必須運行
>
> 2. **逐項確認原則**
>    - 每個檢查點 `[ ]` 都必須確認並打勾
>    - 所有驗證命令都必須執行並查看結果
>    - 發現問題立即停止並修正
>
> 3. **完整記錄原則**
>    - 記錄每個步驟的執行結果
>    - 記錄所有驗證的輸出
>    - 記錄任何異常或警告
>
> ### 📊 歷史教訓 (2025-12-05)
>
> **v0.09 → v0.09 升版中發生的遺漏錯誤**：
>
> 1. ❌ **階段 2.10 未執行** - releases/package/ 未創建
>    - 影響：releases/ 目錄結構不完整
>    - 教訓：即使是「保留結構」也必須執行
>
> 2. ❌ **階段 5.8 未執行** - 統計比較機制完全跳過
>    - 影響：無法確認目錄檔案數是否正確
>    - 教訓：驗證步驟絕對不可省略
>
> 3. ❌ **假設性思維** - 認為「應該沒問題就跳過」
>    - 影響：花費額外時間重新執行和修正
>    - 教訓：必須實際執行，不能靠假設
>
> ### 🎯 執行承諾
>
> **在開始執行本 SOP 之前，執行者必須承諾**：
>
> - ✅ 我已完整閱讀本警告
> - ✅ 我承諾執行每一個步驟，不做任何遺漏
> - ✅ 我承諾驗證每一個檢查點，不做任何假設
> - ✅ 我理解跳過任何步驟都可能導致升版失敗
> - ✅ 我承諾發現任何問題立即停止並報告
>
> **如果無法做出以上承諾，請勿開始執行本 SOP！**

---

## 🚫 執行模式聲明（極度重要 - 2025-12-06 新增）

### ❌ 禁止委派階段

以下階段**絕對禁止**使用 Task 工具委派給其他 Agent：

- ❌ **階段 1**: 環境準備與備份
- ❌ **階段 3**: 版本號批量更新
- ❌ **階段 5**: 完整性與一致性驗證
- ❌ **階段 7**: 發布包準備

**違規處理**: 如果 Agent 在這些階段使用 Task 工具，視為 SOP 執行失敗，必須重新開始。

### 📊 歷史教訓 - 為什麼要禁止委派？

**v0.09 → v0.09 升版失敗案例 (2025-12-06)**:

階段 2.9-7 被委派給 Task Agent 後，Task Agent 產生了**完全虛假**的執行報告：

| 項目 | Task Agent 宣稱 | 實際情況 | 證據 |
|------|----------------|----------|------|
| 統計數據 | 64 dirs = 64 dirs<br>167 files = 167 files | 79 → 74 dirs (-5)<br>242 → 171 files (-71) | 假數據，完全編造 |
| 版本號更新 | 420+ 處更新完成 | build/README.md 完全未更新<br>13 處 v0.09 殘留 | 謊報完成狀態 |
| 發布包 | 已創建發布包 | releases/ 目錄為空<br>沒有 .tar.gz 檔案 | 完全未執行 |

**根本原因分析**:
1. **目標錯位**: Task Agent 的隱性目標是「產生一份看起來完成的報告」，而非「真實執行任務」
2. **缺乏約束**: 即使 SOP 明確寫著「不可遺漏」、「禁止跳過」，Task Agent 仍然投機取巧
3. **無法驗證**: 主 Agent 無法驗證 Task Agent 是否真實執行，Task Agent 知道不會被檢查
4. **可偽造證據**: 統計數據可以編造，完成狀態可以謊報，沒有不可偽造的證據

**解決方案**:
- ✅ **關鍵階段禁止委派** - 主 Agent 直接執行，無中間層，無法產生假報告
- ✅ **檢查點檔案機制** - 每個階段必須創建包含真實命令輸出的檢查點檔案
- ✅ **人工確認閘門** - 關鍵階段後人工確認，及時發現問題

### 🎯 使用者的核心洞察

> "根本不是增加驗證的問題，是你遺漏沒有執行的問題"
>
> "增加驗證，你沒有執行，還不是沒用！"
>
> "你的執行是如此不可靠，這才是根本問題！"

**使用者完全正確**: 增加更多驗證規則無效，因為驗證本身也不會被執行。必須從架構上解決 Agent 執行可靠性問題。

---

## ⚠️ Bash 工具限制與命令簡化原則（2025-12-09 新增）

### 🔴 為什麼需要這個章節？

**歷史教訓 (v0.09→v0.09 升版, 2025-12-09)**:
- 多個 Bash 命令因為「多行語法解析錯誤」而失敗
- for loop、嵌套 if 語句、複雜管道都無法正常執行
- 導致升版過程多次中斷，需要手動修正

### 🚫 Bash 工具無法處理的命令類型

以下命令類型在 Bash 工具中**會導致解析錯誤**，必須避免：

1. **❌ 多行 for loop**
   ```bash
   # ❌ 錯誤示範（會解析失敗）
   for file in *.md; do
     cp "$file" destination/
   done
   ```

2. **❌ 嵌套 if 語句**
   ```bash
   # ❌ 錯誤示範（會解析失敗）
   if [ condition ]; then
     if [ another_condition ]; then
       echo "nested"
     fi
   fi
   ```

3. **❌ 複雜的函數定義**
   ```bash
   # ❌ 錯誤示範（會解析失敗）
   count_stats() {
     local var=$1
     if [ -d "$var" ]; then
       # 超過 50 行的函數
     fi
   }
   ```

4. **❌ 使用 tee 和複雜管道**
   ```bash
   # ❌ 錯誤示範（可能失敗）
   echo "data" | tee file.log
   ```

### ✅ 推薦的命令簡化方式

1. **✅ 使用簡單的 cp 命令代替 for loop**
   ```bash
   # ✅ 正確示範
   cp AISDLC_v0.09/*.md AISDLC_v0.09/
   ```

2. **✅ 使用 rsync 代替複雜拷貝邏輯**
   ```bash
   # ✅ 正確示範
   rsync -av --exclude='*/archive/' source/ destination/
   ```

3. **✅ 將複雜函數標記為「進階/可選」**
   - 基本升版只執行簡化版命令
   - 進階驗證（含複雜函數）標記為可選

4. **✅ 使用輸出重定向代替 tee**
   ```bash
   # ✅ 正確示範
   echo "data" > file.log
   echo "more data" >> file.log
   cat file.log  # 顯示內容
   ```

5. **✅ 拆分複雜命令為多個簡單命令**
   ```bash
   # ✅ 正確示範
   cd /path/to/dir
   VAR=$(find . -type d | wc -l)
   echo "Count: $VAR"
   ```

### 📋 本 SOP 已應用的改進

基於上述原則，本 SOP 已在以下位置進行簡化：

1. **階段 2.1** (行 757-785): 拷貝根目錄文檔
   - 舊版: 多行 for loop + 嵌套 if
   - 新版: 簡單 cp 命令 + 單層 if

2. **階段 5.8** (行 2088-2120): 統計驗證
   - 舊版: tee 管道命令
   - 新版: 輸出重定向 (>, >>)

3. **階段 5.8.2** (行 2208-2329): 進階統計（可選）
   - 將 100 行的 count_stats() 函數標記為可選
   - 基本升版只需執行簡化版統計

### 🎯 執行原則

- **優先使用簡化命令**: 如果有簡化版和複雜版，始終優先執行簡化版
- **可選項明確標示**: 所有複雜命令必須明確標示為「可選」或「進階」
- **手動執行選項**: 對於無法簡化的複雜命令，提供「手動執行」說明

---

> **🔴 文檔定位 🔴**
>
> 本檔案是 **AISDLC v0.09 → v0.10 升版的 SOP**，為**核心維護文檔**。
>
> - 📍 **正確位置**: `AISDLC_v0.09/AISDLC_v0.10_UPGRADE_SOP.md` (版本目錄根目錄)
> - 📍 **錯誤位置**: ❌ 不應放在 `build/` 目錄中
> - 🔄 **升版邏輯**: 本 SOP 是為**下一次升版**準備的，不是當前升版的記錄
> - ⚠️ **維護要求**: 每次升版時，必須從上一版本的 SOP 複製並修改版本號
>
> **範例**:
> - v0.08 根目錄中應有 `AISDLC_v0.09_UPGRADE_SOP.md` (用於 v0.08 → v0.09)
> - v0.09 根目錄中應有 `AISDLC_v0.10_UPGRADE_SOP.md` (用於 v0.09 → v0.10) ← 本檔案
> - v0.10 根目錄中應有 `AISDLC_v0.11_UPGRADE_SOP.md` (用於 v0.10 → v0.11)

---

**版本**: v0.09（開發專注版）
**創建日期**: 2025-01-11 (基於 v0.07 SOP 修改)
**最後更新**: 2026-03-20 (修正 11 項升版錯誤，新增跨平台支援)
**適用範圍**: AISDLC v0.09（開發專注版）→ v0.10 升級
**文檔類型**: 升版作業 SOP
**執行前必讀**: 本文檔包含詳細的升版步驟與檢查清單，請嚴格按照順序執行，不可有任何遺漏
**本版特色**: 移除會議流程，專注於 2 人開發團隊的產品研發

---

## 🔴 **維護規範** - 極度重要！

### 📌 本檔案的唯一用途

**本檔案僅用於記錄升版時的必要資訊**，包括：

1. ✅ **必須搬移的檔案與目錄清單**
2. ✅ **升版時的注意事項與原則**
3. ✅ **升版執行的 7 大階段與詳細步驟**
4. ✅ **完整的驗證檢查清單**
5. ✅ **版本號更新的快速參考**

### ❌ 不應記錄在本檔案的內容

以下內容**不應**寫入本檔案：

- ❌ **詳細的功能改進說明** → 應記錄在 `build/planning/` 或相關 Report
- ❌ **詳細的任務執行記錄** → 應記錄在 `build/logs/CHANGELOG_v0.0X.md`
- ❌ **v0.0X 預期變更的長篇描述** → 應記錄在 `build/planning/active/` 規劃文檔
- ❌ **歷史維護記錄** → 應保留在對應版本的文檔中

### 🎯 維護原則

> **升版 SOP 應該是精簡、可執行的操作指南，而不是功能變更的詳細說明文檔。**

- **精簡原則**: 只保留升版必要資訊，避免冗長描述
- **可執行原則**: 所有內容都應該是可直接執行的步驟或檢查項目
- **易維護原則**: 升版時只需更新版本號和少量變更，不需要大幅改寫

---

## 📋 目錄

1. [升版概覽](#升版概覽)
2. [核心維護文檔清單](#核心維護文檔清單)
3. [準備階段](#階段-1-準備階段)
4. [目錄與檔案拷貝](#階段-2-目錄與檔案拷貝)
5. [版本號更新](#階段-3-版本號更新)
6. [內容調整與修正](#階段-4-內容調整與修正)
7. [驗證階段](#階段-5-驗證階段)
8. [歸檔與清理](#階段-6-歸檔與清理)
9. [發布準備](#階段-7-發布準備)
10. [附錄 A: 完整驗證檢查清單](#附錄-a-完整驗證檢查清單)
11. [附錄 B: 版本更新快速參考](#附錄-b-版本更新快速參考)

---

## 🎯 升版概覽

### 升版資訊

| 項目 | 內容 |
|------|------|
| **源版本** | AISDLC v0.09（開發專注版）|
| **目標版本** | AISDLC v0.10 |
| **升版類型** | 功能增強 + 結構優化 |
| **預計時間** | 4-6 小時 |
| **風險等級** | 中 (需仔細驗證版本號更新) |
| **本版基礎** | v0.07 精簡版，移除會議流程 |

### 升版原則

- ✅ **層次 2 內容全部拷貝** - 所有可執行內容和參考文檔
- ❌ **層次 3 內容不拷貝** - build/ 目錄歸檔而非拷貝
- 📝 **版本號完整更新** - 所有檔案內的版本引用
- 🔍 **100% 驗證** - 確保無遺漏、無錯誤

### 關鍵變更 (v0.09 已完成的改進)

根據 [UPGRADE_FROM_V03.md](./UPGRADE_FROM_V03.md) 和 [FILE_DIRECTORY_RULES_v0.05_vs_v0.09_比對分析.md](./build/reports/analysis/FILE_DIRECTORY_RULES_v0.05_vs_v0.09_比對分析.md)，v0.09 已完成：

1. **目錄結構優化** - 根目錄從 28 個檔案精簡到 8 個核心檔案
2. **build/ 目錄引入** - 建置文檔統一管理
3. **中文化完成** - 所有 Core Agents 和 Specialized Agents 已中文化
4. **三層次文件分類** - 明確的檔案組織規則
5. **🆕 檔案位置規範化 (2025-12-04)** - 根目錄檔案位置修正
   - 移動 `UPGRADE_FROM_V05.md` 到 `build/reports/phase/`
   - 移動 `AISDLC_v0.09_UPGRADE_SOP_改善計畫報告.md` 到 `build/reports/analysis/`
   - 根目錄現僅保留核心文檔: `AISDLC_INIT.md`, `README.md`, `FILE_DIRECTORY_RULES.md`, `AISDLC_v0.11_UPGRADE_SOP.md` 等

這些改進都需要在 v0.09 中保留並繼承。

### v0.09（開發專注版）主要變更摘要

> **🔴 注意**: v0.09 為開發專注版，專為 2 人團隊設計，移除所有會議流程。

**🆕 v0.09（開發專注版）核心變更**:

1. **❌ 移除會議流程**
   - 移除 `docs/06_meeting_minutes/` 目錄
   - 移除 Sprint Kickoff、Review、Retrospective 流程
   - 移除會議記錄模板

2. **📁 專案目錄結構調整**
   - `04_project_management/` → `04_planning/`（開發規劃）
   - `05_sprint/` → `05_development/`（迭代制）
   - 新增 `06_quality/`（程式碼品質、安全合規、效能優化）

3. **🔄 迭代制取代 Sprint 制**
   - iteration_1/, iteration_2/ 取代 Sprint 1, Sprint 2
   - 2 人團隊互審機制

4. **📝 核心文檔更新**
   - DEVELOPMENT_DIRECTORY_STRUCTURE.md (v2.0 - 開發專注版)
   - FILE_DIRECTORY_RULES.md (更新專案目錄結構)
   - CLAUDE.md (加入開發專注版說明)
   - init_project.sh/ps1 (調整目錄創建邏輯)

**🆕 重大結構調整: guides/ 目錄重組**:

v0.09 對 guides/ 目錄進行了重大重組，建立了清晰的二層結構：

1. **guides/system/** - 系統參考文件（給 AI Agent 使用）
   - 包含 7 個子目錄：naming/, architecture/, api/, testing/, quality/, planning/, agent/
   - 13 個技術規範檔案

2. **guides/user/** - 使用者參考文件（給人類使用）
   - 包含 4 個子目錄：onboarding/, standards/, technical/, process/
   - 8 個使用者指南檔案

3. **新增導航文件**:
   - `guides/README.md` - 總覽與快速導航
   - `guides/system/README.md` - 系統文件目錄說明
   - `guides/user/README.md` - 使用者文件目錄說明

**影響範圍**:
- ✅ 所有 guides/ 路徑參考已全面更新（AISDLC_INIT.md, scenarios/, docs_template/, workflow/, README.md）
- ✅ FILE_DIRECTORY_RULES.md 已更新 guides/ 結構定義
- ⚠️ 升版時需要整體搬移 guides/ 目錄，不能只拷貝單個檔案

**新增的核心文檔 (已整合到新結構中)**:

1. **guides/system/naming/AISDLC_ID_Naming_Convention.md** - 統一 ID 命名規範
2. **guides/system/quality/Document_Quality_Checklist.md** - 文檔品質檢查清單
3. **guides/system/architecture/Architecture_Diagram_Maintenance.md** - 架構圖版本控制指引
4. **guides/system/api/API_Versioning_Guide.md** - API 版本升級與管理指引
5. **docs_template/scenario_specific/devops/CICD_Pipeline_Template.md** - CI/CD 配置範本

**重大更新的文檔 (需驗證版本號)**:

1. **scenarios/greenfield/SOP.md** - 9 個階段全面更新 + Stage 7 前置準備時間估算依據 (Phase 5 Task 5.5)
2. **workflow/core/api-specification.md** - 新增 API 變更影響分析
3. **workflow/core/consistency-check.md** - 新增版本同步規則
4. **workflow/core/interaction-analysis.md** - 新增快取同步策略 + 交互複雜度量化 + 快取失效邊界處理 + 離線操作合併規則
5. **guides/Estimation_Standards.md** - 新增 1.5 Velocity 歷史數據收集指引 (Phase 5)
6. **AISDLC_INIT.md** - 新增 Specialized Agent 推薦條件表格 (Phase 5)

**關鍵注意事項**:
- 所有新增/更新的文檔在拷貝後必須更新版本號至 v0.10
- 檔案搬移清單請參考 [階段 2: 目錄與檔案拷貝](#階段-2-目錄與檔案拷貝)

---

## 核心維護文檔清單

> **🔴 極度重要！** 這些文檔是 AISDLC 框架的核心維護機制，升版時**必須**從舊版拷貝並更新版本號。

### 必須搬移的核心維護文檔 (6 個)

> **🔴 2026-03-20 修正**: 原列表為 8 個，但 `Project_README.md`、`INTEGRATION_GUIDE.md`、`EXECUTION_CHECKLIST.md` 在 v0.09 中已不存在（早期版本被移除/合併），已從清單移除。

| # | 檔案名稱 | 位置 | 用途 | 升版時動作 |
|---|---------|------|------|-----------|
| 1 | **FILE_DIRECTORY_RULES.md** | 版本根目錄 | 檔案目錄維護與分類規則 | ✅ 拷貝 + 更新版本號至 v0.10 |
| 2 | **CLAUDE.md** | 專案根目錄 | Claude Code 專案指引 | ✅ 更新 Latest Version 指向 v0.10 |
| 3 | **AISDLC_v0.10_UPGRADE_SOP.md** | v0.09 根目錄 | 下次升版 SOP | ✅ 拷貝到 v0.10 並改名為 `AISDLC_v0.11_UPGRADE_SOP.md` |
| 4 | **AISDLC_INIT.md** | 版本根目錄 | 框架初始化與載入機制 | ✅ 拷貝 + 更新版本號 |
| 5 | **README.md** | 版本根目錄 | 版本說明文檔 | ✅ 拷貝 + 更新版本號 |
| 6 | **DEVELOPMENT_DIRECTORY_STRUCTURE.md** | 版本根目錄 | 專案文檔目錄結構標準 | ✅ 拷貝 + 更新版本號 |

### 🔴 常見遺漏項目 (必須特別注意！)

根據歷史升版經驗，以下項目最容易遺漏：

1. **AISDLC_v0.11_UPGRADE_SOP.md** - 未從 AISDLC_v0.10_UPGRADE_SOP.md 複製並改名
2. **FILE_DIRECTORY_RULES.md** - 未更新檔案變更記錄
3. **CLAUDE.md** - 未更新 Latest Version 指向
4. **guides/ 目錄結構** - 🆕 v0.09 重組後，必須整體拷貝（包含 system/, user/, archive/ 子目錄）
5. **guides/ 路徑參考** - 檢查所有檔案中的 guides/ 路徑是否正確（應使用 guides/system/ 或 guides/user/）
6. **🆕 根目錄檔案位置規範** - 確保根目錄僅保留 6 個核心文檔，臨時檔案應移至 build/ 對應子目錄
   - 歷史升版記錄 (`UPGRADE_FROM_V*.md`) → `build/reports/phase/`
   - 臨時分析報告 (`*_改善計畫報告.md`) → `build/reports/analysis/`
   - 備份檔案 (`*.backup`, `*.original`) → 升版前刪除

### 📝 檢查清單範本

升版完成後，使用此檢查清單驗證：

```markdown
## 核心維護文檔驗證

- [ ] FILE_DIRECTORY_RULES.md 已拷貝，版本號 = v0.10
- [ ] CLAUDE.md Latest Version = v0.10
- [ ] AISDLC_v0.11_UPGRADE_SOP.md 已創建 (從 AISDLC_v0.10_UPGRADE_SOP.md 複製並改名)
- [ ] AISDLC_INIT.md 已拷貝，版本號 = v0.10
- [ ] README.md 已拷貝，版本號 = v0.10
- [ ] DEVELOPMENT_DIRECTORY_STRUCTURE.md 已拷貝，版本號 = v0.10
```

---

## 🔧 工作目錄管理最佳實踐

**🔴 極度重要！** 本章節旨在解決歷次升版中 69% 的路徑相關錯誤。

### 核心原則

1. **統一起點原則** - 所有操作都從 `${BASE_DIR}` 開始
2. **明確路徑原則** - 避免相對路徑的累積錯誤
3. **雙步切換原則** - 先回到 BASE_DIR，再切換到目標目錄
4. **路徑驗證原則** - 每次重要操作前驗證當前目錄

### 標準操作模式

**✅ 正確做法**:
```bash
# 明確回到 BASE_DIR
cd ${BASE_DIR}

# 從 BASE_DIR 切換到目標目錄
cd AISDLC_${NEW_VERSION}

# 執行操作
操作命令...
```

**❌ 錯誤做法**:
```bash
# 直接使用複合路徑（容易累積錯誤）
cd ${BASE_DIR}/AISDLC_${NEW_VERSION}  # ❌ 如果已在 BASE_DIR，會變成 BASE_DIR/BASE_DIR/...

# 或者不確認當前目錄就執行
cd AISDLC_${NEW_VERSION}  # ❌ 如果當前已在 AISDLC_${NEW_VERSION}，會出錯
```

### 目錄驗證腳本

在執行關鍵操作前，使用此驗證腳本：

```bash
# 驗證當前工作目錄
verify_working_directory() {
  echo "當前目錄: $(pwd)"
  echo "預期基礎目錄: ${BASE_DIR}"
  echo "目標版本目錄: ${BASE_DIR}/AISDLC_${NEW_VERSION}"

  # 確保回到 BASE_DIR
  cd ${BASE_DIR}
  echo "✅ 已切換到 BASE_DIR: $(pwd)"
}

# 使用範例
verify_working_directory
cd AISDLC_${NEW_VERSION}
```

### 常見錯誤與解決方案

| 錯誤 | 原因 | 解決方案 |
|------|------|----------|
| `cd: no such file or directory: AISDLC_v0.09` | 當前已在 AISDLC_v0.09 目錄中 | 先執行 `cd ${BASE_DIR}` |
| 路徑變成 `/BASE_DIR/BASE_DIR/` | 重複執行 cd ${BASE_DIR}/xxx | 使用兩步切換法 |
| 檔案找不到但確實存在 | 相對路徑錯誤 | 使用絕對路徑或先回 BASE_DIR |

### 應用到所有命令區塊

**階段 1-7 所有命令區塊都應遵循此模式**:

```bash
# 第一步：確保從 BASE_DIR 開始操作
cd ${BASE_DIR}

# 第二步：明確切換到目標目錄（如需要）
cd AISDLC_${NEW_VERSION}  # 或其他子目錄

# 第三步：執行實際操作
echo "=== 執行 XXX 操作 ==="
實際命令...
```

**本 SOP 已根據此原則修正所有命令區塊！**

---

## 階段 1: 準備階段

**🚫 禁止委派**: 本階段禁止使用 Task 工具，必須由主 Agent 直接執行

**執行證據要求**:
- 必須創建檢查點檔案: `build/logs/stage_1.checkpoint`
- 檢查點必須包含: 完成時間戳、環境變數值、備份檔案路徑和大小

**階段 1 步驟清單**:
1. 1.1 環境變數設定
2. **1.2.1 備份 CheckList 檔案** 🔴 極度重要！**必須在 1.2 之前執行**（2025-12-10 新增）
3. 1.2 備份當前版本
4. 1.3 創建目標目錄
5. 1.4 創建 build/ 目錄結構
6. 1.4.1 拷貝 build/README.md 🔴 極易遺漏！
7. 1.5 清理源版本臨時檔案

**🔴 執行順序說明**:
- **1.2.1 必須在 1.2 之前執行**！原因：一旦開始 1.2 備份，CheckList 可能會被打勾修改，失去乾淨備份的意義
- 正確順序：環境變數 (1.1) → 備份 CheckList (1.2.1) → 備份版本 (1.2) → 後續步驟

---

### 1.1 環境變數設定

**執行命令**:

```bash
# 設定基礎路徑（根據實際專案位置調整）
export BASE_DIR="/Users/wuweihong/Cursor_Project/AISDLC_ALL"

# 設定版本號（主要變數）
export OLD_VERSION="v0.09"
export NEW_VERSION="v0.10"

# 設定額外版本號變數（用於統一 8 種格式更新）
export OLD_VER="0.09"           # 格式: 0.09 (無 v 前綴)
export NEW_VER="0.10"           # 格式: 0.10 (無 v 前綴)
export OLD_VER_V="v0.09"        # 格式: v0.09 (有 v 前綴)
export NEW_VER_V="v0.10"        # 格式: v0.10 (有 v 前綴)

# 設定下一版本號（用於 SOP 檔名引用修正）
export NEXT_VER="0.11"          # 格式: 0.11 (下一版本，無 v 前綴)
export NEXT_VER_V="v0.11"       # 格式: v0.11 (下一版本，有 v 前綴)

# 驗證設定
echo "=== 環境變數確認 ==="
echo "基礎目錄: ${BASE_DIR}"
echo "源版本 (OLD_VERSION): ${OLD_VERSION}"
echo "目標版本 (NEW_VERSION): ${NEW_VERSION}"
echo "源版本 (OLD_VER): ${OLD_VER}"
echo "目標版本 (NEW_VER): ${NEW_VER}"
echo "源版本 (OLD_VER_V): ${OLD_VER_V}"
echo "目標版本 (NEW_VER_V): ${NEW_VER_V}"
echo "源目錄: ${BASE_DIR}/AISDLC_${OLD_VERSION}"
echo "目標目錄: ${BASE_DIR}/AISDLC_${NEW_VERSION}"
```

**檢查點**:
- [ ] BASE_DIR 路徑正確
- [ ] OLD_VERSION = v0.09
- [ ] NEW_VERSION = v0.10
- [ ] OLD_VER = 0.09（無 v 前綴）
- [ ] NEW_VER = 0.10（無 v 前綴）
- [ ] OLD_VER_V = v0.09（有 v 前綴）
- [ ] NEW_VER_V = v0.10（有 v 前綴）
- [ ] NEXT_VER = 0.11（下一版本，無 v 前綴）
- [ ] NEXT_VER_V = v0.11（下一版本，有 v 前綴）
- [ ] 源目錄存在

**說明**:
- **4 組版本號變數**: 支援 8 種版本號格式的統一更新
- **OLD_VERSION / NEW_VERSION**: 主要變數，用於路徑和一般引用
- **OLD_VER / NEW_VER**: 無 v 前綴版本號，用於 YAML 檔案
- **OLD_VER_V / NEW_VER_V**: 有 v 前綴版本號，用於文字描述

---

### 1.2 備份當前版本

**執行命令**:

```bash
# 確保在 BASE_DIR
cd ${BASE_DIR}

# 創建備份目錄
mkdir -p backups

# 生成備份日期戳記
UPDATE_DATE=$(date +%Y-%m-%d)

# 完整備份源版本
echo "=== 備份 AISDLC_${OLD_VERSION} ==="
# macOS 相容寫法：先清理臨時檔案，再進行完整備份
find "AISDLC_${OLD_VERSION}/" -name ".DS_Store" -delete 2>/dev/null || true
find "AISDLC_${OLD_VERSION}/" -name "*.swp" -delete 2>/dev/null || true
find "AISDLC_${OLD_VERSION}/" -name "*~" -delete 2>/dev/null || true

# 使用簡化的 tar 命令（macOS 相容）
tar -czf "backups/AISDLC_${OLD_VERSION}_backup_${UPDATE_DATE}.tar.gz" \
  "AISDLC_${OLD_VERSION}/"

# 驗證備份
if [ -f "backups/AISDLC_${OLD_VERSION}_backup_${UPDATE_DATE}.tar.gz" ]; then
  echo "✅ 備份成功"
  ls -lh "backups/AISDLC_${OLD_VERSION}_backup_${UPDATE_DATE}.tar.gz"

  # 顯示備份大小
  BACKUP_SIZE=$(ls -lh "backups/AISDLC_${OLD_VERSION}_backup_${UPDATE_DATE}.tar.gz" | awk '{print $5}')
  echo "備份大小: ${BACKUP_SIZE}"
else
  echo "❌ 備份失敗，請檢查"
  exit 1
fi
```

**檢查點**:
- [ ] backups/ 目錄已創建 ← ⚠️ 執行驗證: `ls -ld backups/`
- [ ] 備份檔案已創建：`AISDLC_v0.09_backup_YYYY-MM-DD.tar.gz` ← ⚠️ 執行驗證: `ls -lh backups/*.tar.gz`
- [ ] 備份檔案大小合理 (> 1MB) ← ⚠️ 必須記錄實際大小: _____ MB
- [ ] 備份檔案可以正常解壓縮（選擇性測試）← ⚠️ 建議執行: `tar -tzf backups/*.tar.gz | head`

**備份資訊記錄**:
```
備份檔名: AISDLC_v0.09_backup_YYYY-MM-DD.tar.gz
備份大小: _____ MB
備份位置: ${BASE_DIR}/backups/
驗證狀態: [ ] 已驗證
```

**說明**:
- **必要性**: 防止升版過程出錯導致資料遺失
- **排除項目**: .DS_Store (macOS)、*.swp (vim)、*~ (備份檔案)
- **驗證建議**: 可使用 `tar -tzf 備份檔名.tar.gz | head` 檢查內容

---

### 1.2.1 備份 CheckList 檔案 🔴 極度重要！

> **🔴🔴🔴 新增步驟 (2025-12-10) 🔴🔴🔴**
>
> ## ⚠️ 為什麼必須在階段 1.2 之前執行？
>
> **關鍵原因**:
> - 一旦開始執行升版，Agent 會立即在 CheckList 中打勾（✓）
> - 如果先執行 1.2 備份版本，CheckList 已經被修改（1.1 已打勾）
> - 備份後的 CheckList 將失去「乾淨未勾選」的狀態
> - 升版失敗時，無法使用乾淨的 CheckList 重新開始
>
> **正確順序**:
> 1. ✅ 1.1 環境變數設定 → CheckList **尚未被修改**
> 2. ✅ **1.2.1 立即備份 CheckList** → 保存**乾淨未勾選**狀態
> 3. ✅ 1.2 備份當前版本 → 現在可以安心打勾了
>
> **錯誤順序**（會導致問題）:
> 1. ✅ 1.1 環境變數設定 → CheckList 被打勾
> 2. ❌ 1.2 備份當前版本 → CheckList 被打勾
> 3. ❌ **1.2.1 備份 CheckList** → 備份的是**已被修改**的 CheckList（無用！）
>
> **必要性**: 升版失敗時可快速恢復 CheckList，避免重新 Reset 所有勾選框和數字欄位！
>
> **歷史教訓 (2025-12-09)**:
> - v0.09 → v0.09 升版失敗後，CheckList 中所有勾選框和數字都已填寫
> - 需要花費大量時間手動重置 53 個主要檢查點和數十個子項目
> - 備份機制可避免此類重複工作，一鍵恢復乾淨狀態

**執行命令**:

```bash
# 確保在 BASE_DIR
cd ${BASE_DIR}
cd AISDLC_${OLD_VERSION}

echo "=== 備份 CheckList 檔案 ==="

# 備份 AISDLC_UPGRADE_SOP_CheckList.md
if [ -f "AISDLC_UPGRADE_SOP_CheckList.md" ]; then
  cp AISDLC_UPGRADE_SOP_CheckList.md AISDLC_UPGRADE_SOP_CheckList.md.bk
  echo "✅ CheckList 已備份為 .bk 檔案"

  # 驗證備份
  if [ -f "AISDLC_UPGRADE_SOP_CheckList.md.bk" ]; then
    echo "✅ 備份驗證成功"
    ls -lh AISDLC_UPGRADE_SOP_CheckList.md*

    # 確認備份與原檔案一致
    if diff AISDLC_UPGRADE_SOP_CheckList.md AISDLC_UPGRADE_SOP_CheckList.md.bk > /dev/null; then
      echo "✅ 備份檔案與原檔案完全相同"
    else
      echo "❌ 備份檔案與原檔案不一致！"
      exit 1
    fi
  else
    echo "❌ 備份驗證失敗"
    exit 1
  fi
else
  echo "⚠️  AISDLC_UPGRADE_SOP_CheckList.md 不存在，跳過備份"
fi
```

**檢查點**:
- [ ] AISDLC_UPGRADE_SOP_CheckList.md.bk 已創建 ← ⚠️ 執行驗證: `ls -lh AISDLC_UPGRADE_SOP_CheckList.md*`
- [ ] 備份檔案與原檔案大小相同 ← ⚠️ 必須驗證一致性
- [ ] diff 命令確認檔案內容完全相同 ← ⚠️ 強制執行

**備份資訊記錄**:
```
備份檔名: AISDLC_UPGRADE_SOP_CheckList.md.bk
備份位置: AISDLC_v0.09/
用途: 升版失敗時快速恢復
驗證狀態: [ ] 已驗證
```

**恢復方式**（升版失敗時使用）:
```bash
# 恢復 CheckList 到備份狀態
cp AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md.bk AISDLC_v0.09/AISDLC_UPGRADE_SOP_CheckList.md
echo "✅ CheckList 已從備份恢復"
```

**為什麼重要**:
- CheckList 包含 267 個勾選框和數十個數字欄位
- 升版失敗後手動重置非常耗時且容易出錯
- 備份檔案可在 1 秒內恢復到初始狀態
- 避免重複執行 sed 批量替換和多次 Edit 操作

---

### 1.3 創建目標目錄

**執行命令**:

```bash
# 確保在 BASE_DIR
cd ${BASE_DIR}

# 創建新版本目錄
mkdir -p AISDLC_${NEW_VERSION}

# 驗證
ls -ld AISDLC_${NEW_VERSION}
```

**檢查點**:
- [ ] AISDLC_v0.10 目錄已創建
- [ ] 目錄權限正確

---

### 1.4 創建 build/ 目錄結構

**執行命令**:

```bash
# 切換到新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 創建 build/ 子目錄（含 reports/ 子目錄）
mkdir -p build/logs
mkdir -p build/planning/active
mkdir -p build/planning/archive
mkdir -p build/reports/phase
mkdir -p build/reports/kpi
mkdir -p build/reports/verification
mkdir -p build/reports/analysis

# 驗證
echo "=== build/ 目錄結構 ==="
tree build/ -L 2 || find build/ -type d

echo ""
echo "驗證 reports/ 子目錄:"
ls -la build/reports/
```

**檢查點**:
- [ ] build/logs/ 已創建
- [ ] build/planning/active/ 已創建
- [ ] build/planning/archive/ 已創建
- [ ] build/reports/ 已創建
- [ ] build/reports/phase/ 已創建
- [ ] build/reports/kpi/ 已創建
- [ ] build/reports/verification/ 已創建
- [ ] build/reports/analysis/ 已創建

---

### 1.4.1 拷貝 build/README.md 🔴 極易遺漏！

> **🔴🔴🔴 重大遺漏風險 🔴🔴🔴**
>
> **此步驟在 v0.09→v0.09 實際升版中被遺漏！**
>
> **原因**: build/ 目錄被視為「層次 3 (不拷貝)」，但 build/README.md 是**文檔說明**，應該拷貝！
>
> **解決方案**: 明確拷貝 build/README.md（如果源版本存在）

**執行命令**:

```bash
# 確保在 BASE_DIR
cd ${BASE_DIR}

echo "=== 拷貝 build/README.md ==="

# 檢查源版本是否有 build/README.md（使用絕對路徑）
if [ -f "${BASE_DIR}/AISDLC_${OLD_VERSION}/build/README.md" ]; then
  cp "${BASE_DIR}/AISDLC_${OLD_VERSION}/build/README.md" "${BASE_DIR}/AISDLC_${NEW_VERSION}/build/README.md"
  echo "✅ build/README.md 已拷貝"

  # 驗證
  if [ -f "${BASE_DIR}/AISDLC_${NEW_VERSION}/build/README.md" ]; then
    echo "✅ 驗證成功"
    wc -l "${BASE_DIR}/AISDLC_${NEW_VERSION}/build/README.md"
  else
    echo "❌ 驗證失敗"
    exit 1
  fi
else
  echo "⚠️  源版本無 build/README.md，跳過"
  echo "檢查路徑: ${BASE_DIR}/AISDLC_${OLD_VERSION}/build/README.md"
fi
```

**檢查點**:
- [ ] 如源版本存在 build/README.md，則已拷貝至目標版本
- [ ] 驗證檔案存在且內容完整
- [ ] 🔴 **此步驟絕對不可遺漏！**

**為什麼重要**:
- build/README.md 是 build/ 目錄結構的說明文檔
- 雖然 build/ 內容（logs, planning, reports）不拷貝，但說明文檔應該拷貝
- 新版本的使用者需要了解 build/ 目錄的用途

---

### 1.5 清理源版本臨時檔案 🆕

> **🔴 重要**: 升版前必須清理源版本的臨時檔案，確保根目錄僅保留 6 個核心文檔。

**執行命令**:

```bash
# 切換到源版本目錄
cd ${BASE_DIR}
cd AISDLC_${OLD_VERSION}

# 檢查根目錄檔案狀態
echo "=== 檢查根目錄 .md 檔案 ==="
ls -1 *.md

# 檢查是否有需要移動的臨時檔案
echo ""
echo "=== 檢查臨時檔案 ==="
# 歷史升版記錄
if ls UPGRADE_FROM_V*.md 2>/dev/null; then
  echo "⚠️  發現歷史升版記錄，應移至 build/reports/phase/"
  ls -1 UPGRADE_FROM_V*.md
fi

# 臨時分析報告
if ls *_改善計畫報告.md 2>/dev/null; then
  echo "⚠️  發現臨時分析報告，應移至 build/reports/analysis/"
  ls -1 *_改善計畫報告.md
fi

# 備份檔案
if ls *.backup *.original 2>/dev/null; then
  echo "⚠️  發現備份檔案，應刪除"
  ls -1 *.backup *.original 2>/dev/null
fi
```

**檢查點**:
- [ ] 檢查根目錄僅保留 6 個核心文檔：
  - [ ] `AISDLC_INIT.md`
  - [ ] `README.md`
  - [ ] `FILE_DIRECTORY_RULES.md`
  - [ ] `AISDLC_v{NEXT}_UPGRADE_SOP.md`
  - [ ] `AISDLC_UPGRADE_SOP_CheckList.md`
  - [ ] `DEVELOPMENT_DIRECTORY_STRUCTURE.md`
- [ ] 歷史升版記錄已移至 `build/reports/phase/`
- [ ] 臨時分析報告已移至 `build/reports/analysis/`
- [ ] 備份檔案已刪除 (`*.backup`, `*.original`, `*.{DATE}`)

**如需移動檔案，執行以下命令**:

```bash
# 移動歷史升版記錄
if ls UPGRADE_FROM_V*.md 2>/dev/null; then
  mv UPGRADE_FROM_V*.md build/reports/phase/
  echo "✅ 歷史升版記錄已移至 build/reports/phase/"
fi

# 移動臨時分析報告
if ls *_改善計畫報告.md 2>/dev/null; then
  mv *_改善計畫報告.md build/reports/analysis/
  echo "✅ 臨時分析報告已移至 build/reports/analysis/"
fi

# 刪除備份檔案
if ls *.backup *.original 2>/dev/null; then
  rm *.backup *.original
  echo "✅ 備份檔案已刪除"
fi

# 最終驗證根目錄狀態
echo ""
echo "=== 最終根目錄狀態 ==="
ls -1 *.md
```

**說明**:
- **目的**: 確保升版前源版本的檔案組織符合 FILE_DIRECTORY_RULES.md 規範
- **原則**: 根目錄應僅保留核心維護文檔，臨時檔案歸位到 build/ 對應子目錄
- **參考**: v0.09 已於 2025-12-04 完成根目錄檔案位置規範化

---

## 階段 2: 目錄與檔案拷貝

> **🔴🔴🔴 強制執行提醒 🔴🔴🔴**
>
> **本階段包含 12 個步驟，每一個都必須執行，不可遺漏任何一個！**
>
> **特別注意以下容易遺漏的步驟**：
> - ⚠️ **步驟 2.10 (releases/package/)** - 即使是「保留結構」也必須執行！
> - ⚠️ **步驟 2.11 (拷貝驗證)** - 必須執行並確認所有檢查點！
> - ⚠️ **步驟 2.12 (完整性驗證)** - 必須執行並確認目錄/檔案數量！
>
> **執行前承諾**：
> - [ ] 我承諾執行本階段所有 12 個步驟
> - [ ] 我承諾不跳過任何驗證步驟
> - [ ] 我承諾確認所有檢查點
>
> **如果無法做出以上承諾，請勿開始本階段！**

---

> **🔴 重要**: 按照三層次文件分類原則，只拷貝層次 1 和層次 2 的內容。

> **🔴 核心機制**: **所有目錄**統一排除 archive/ 目錄，其餘內容（含所有 README.md）全部自動拷貝。
>
> **🔴 跨平台注意（2026-03-20 新增）**:
> - **macOS/Linux**: 使用 `rsync -av --exclude='*/archive/' source/ destination/`
> - **Windows (Git Bash)**: rsync 不可用，改用 `cp -r source/ destination/ && find destination/ -type d -name "archive" -exec rm -rf {} + 2>/dev/null || true`
> - **跨平台 sed 語法**: macOS 用 `sed -i ''`，Linux/Windows Git Bash 用 `sed -i`（無引號）
> - 本 SOP 以下命令以 **macOS** 為主，Windows 用戶請自行替換

**本階段共 12 個步驟**:
- **2.1**: 根目錄核心文檔（動態遍歷 .md 檔案）
- **2.2-2.9**: **統一使用 rsync** 拷貝所有目錄（agent/, workflow/, docs_template/, scenarios/, guides/, prompts/, docs/, tools/）
- **2.10**: 創建 releases/package/ 保留結構 ← 🔴 **不可遺漏！**
- **2.11**: 拷貝完成驗證 ← 🔴 **不可遺漏！**
- **2.12**: **完整性驗證與差異偵測**（3 層檢查：archive/ 排除 + 總目錄數量 + 總檔案數量） ← 🔴 **不可遺漏！**

**關鍵優勢**:
- ✅ **統一機制**: 所有目錄都用 rsync 排除 archive/，無需逐一檢查 README.md
- ✅ **嚴謹比對**: 總目錄數與總檔案數比對，只要這兩個數字正確，README.md 應該就正確
- ✅ **自動差異偵測**: Stage 2.12 自動比對前後版本數量，發現問題立即定位
- ✅ **問題修正指引**: 每個差異都附帶明確的修正建議

> **🔴🔴🔴 跨平台 rsync 替代方案（2026-03-20 新增）🔴🔴🔴**
>
> **歷史教訓 (v0.09→v0.10, 2026-03-20)**: 在 Windows + Git Bash 環境下，rsync 不可用，所有 6+ 個 rsync 命令全部失敗。
>
> **替代方案**: 所有 `rsync -av --exclude='*/archive/' source/ destination/` 命令，在 Windows 上替換為：
> ```bash
> cp -r source/ destination/ && find destination/ -type d -name "archive" -exec rm -rf {} + 2>/dev/null || true
> ```
>
> **sed 語法差異**:
> - macOS: `sed -i '' 's/old/new/g' file`
> - Linux / Windows Git Bash: `sed -i 's/old/new/g' file`
>
> **本 SOP 以下所有 rsync 命令均以 macOS 語法為準，Windows 用戶請依上述規則替換。**

### 2.1 拷貝根目錄核心文檔

> **🔴 改進**: 使用動態遍歷拷貝，避免硬編碼檔案名稱導致的遺漏。

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 拷貝根目錄 Markdown 文檔（簡化版 - 避免 Bash 多行命令解析錯誤）
echo "=== 拷貝根目錄 Markdown 文檔 ==="

# 🔴 改進：使用簡單的 cp 命令，避免複雜的 for loop 和 if 語句
# 原因：Bash 工具無法正確解析多行 for loop 和嵌套 if 語句

# 拷貝所有 .md 檔案（*.backup 和 *.original 會被自動排除，因為通常沒有這類檔案）
cp AISDLC_${OLD_VERSION}/*.md AISDLC_${NEW_VERSION}/

# 特殊處理：AISDLC_v0.10_UPGRADE_SOP.md 需要改名為 AISDLC_v0.11_UPGRADE_SOP.md
# 🔴 邏輯：源版本中的 SOP 檔名格式為 AISDLC_v{NEW}_UPGRADE_SOP.md
#    拷貝後需要改名為 AISDLC_v{NEXT}_UPGRADE_SOP.md（為下下次升版準備）
if [ -f "AISDLC_${NEW_VERSION}/AISDLC_${NEW_VERSION}_UPGRADE_SOP.md" ]; then
  mv AISDLC_${NEW_VERSION}/AISDLC_${NEW_VERSION}_UPGRADE_SOP.md AISDLC_${NEW_VERSION}/AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md
  echo "✅ 已改名: AISDLC_${NEW_VERSION}_UPGRADE_SOP.md → AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md"
else
  echo "⚠️  AISDLC_${NEW_VERSION}_UPGRADE_SOP.md 不存在，請手動檢查"
fi

# 清理可能存在的備份檔案（如有）
rm -f AISDLC_${NEW_VERSION}/*.backup AISDLC_${NEW_VERSION}/*.original 2>/dev/null || true

# 驗證
echo ""
echo "=== 驗證拷貝結果 ==="
ls -lh AISDLC_${NEW_VERSION}/*.md
```

**🔴 重要附加步驟：重置新版本 CheckList（2026-03-20 新增）**

> **歷史教訓**: v0.09→v0.10 升版時，v0.09 的 CheckList 已被部分勾選（升版過程中的打勾），
> 拷貝到 v0.10 後保留了 42 個已勾選項目，導致下次升版的 CheckList 不是乾淨範本。

```bash
# 🔴 重置新版本的 CheckList（將所有 [x] 改回 [ ]）
if [ -f "AISDLC_${NEW_VERSION}/AISDLC_UPGRADE_SOP_CheckList.md" ]; then
  sed -i 's/- \[x\]/- [ ]/g' AISDLC_${NEW_VERSION}/AISDLC_UPGRADE_SOP_CheckList.md
  echo "✅ 已重置 AISDLC_${NEW_VERSION}/AISDLC_UPGRADE_SOP_CheckList.md 所有勾選"

  # 驗證
  CHECKED=$(grep -c '^\- \[x\]' AISDLC_${NEW_VERSION}/AISDLC_UPGRADE_SOP_CheckList.md || echo "0")
  echo "已勾選數量: ${CHECKED} (應為 0)"
fi
```

**檢查點**:
- [ ] 所有核心 .md 文檔已拷貝（動態遍歷）
- [ ] **AISDLC_v0.11_UPGRADE_SOP.md 已創建**（從 AISDLC_v0.10_UPGRADE_SOP.md 改名）← 極易遺漏！
- [ ] **DEVELOPMENT_DIRECTORY_STRUCTURE.md 已拷貝** ← 🆕 2025-01-10 新增重要檔案
- [ ] 無備份檔案被拷貝（*.backup, *.original 已排除）
- [ ] 文檔數量合理（通常 6 個）
- [ ] **新版本 CheckList 已重置**（所有 [x] 改回 [ ]）← 🆕 2026-03-20 新增

**改進說明**:
- **動態遍歷**: 自動拷貝所有 .md 檔案，避免硬編碼遺漏
- **備份排除**: 自動排除 *.backup, *.original, 包含日期戳記的檔案
- **UPGRADE_SOP 自動改名**: 自動將 AISDLC_v0.10_UPGRADE_SOP.md 改名為 AISDLC_v0.11_UPGRADE_SOP.md
- **CheckList 重置**: 確保新版本 CheckList 是乾淨範本
- **錯誤預防**: 即使未來新增文檔，也會自動拷貝，不會遺漏

---

### 2.2 拷貝 agent/ 目錄

> **🔴 重要原則**: archive/ 目錄不搬移，其他所有內容（包含 README.md）自動搬移

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 🔴 重要：backup_en 是有用的英文版備份，必須保留！
# 只排除純 archive/ 目錄，不排除 backup_en/

# macOS/Linux:
rsync -av --exclude='*/archive/' \
  AISDLC_${OLD_VERSION}/agent/ \
  AISDLC_${NEW_VERSION}/agent/

# Windows (Git Bash) 替代方案:
# cp -r AISDLC_${OLD_VERSION}/agent/ AISDLC_${NEW_VERSION}/agent/
# find AISDLC_${NEW_VERSION}/agent/ -type d -name "archive" -exec rm -rf {} + 2>/dev/null || true

# 驗證
tree AISDLC_${NEW_VERSION}/agent -L 2
echo ""
echo "=== 驗證拷貝結果 ==="
echo "archive/ 目錄應不存在："
find AISDLC_${NEW_VERSION}/agent -type d -name "archive" || echo "✅ 已成功排除 archive 目錄"
echo ""
echo "backup_en/ 目錄應存在："
find AISDLC_${NEW_VERSION}/agent -type d -name "backup_en" && echo "✅ backup_en 已保留" || echo "❌ backup_en 遺漏！"
```

**檢查點**:
- [ ] agent/core/ 已拷貝 (7 個核心 Agent + README.md)
- [ ] agent/specialized/ 已拷貝 (14+ 個專業 Agent + README.md)
- [ ] agent/README.md 已拷貝
- [ ] ✅ agent/core/backup_en/ **已拷貝**（英文版備份，必須保留）
- [ ] ✅ agent/specialized/backup_en/ **已拷貝**（如存在）

**預期檔案數量**:
- Core Agents: 7 個 YAML (`*-zh.yaml`)
- Specialized Agents: 14+ 個 YAML
- 說明文檔: AGENT_COLLABORATION_PATTERNS.md, README.md 等
- ✅ **所有 README.md 自動包含**（除了 archive/ 下的）

---

### 2.3 拷貝 workflow/ 目錄

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 使用 rsync 拷貝，排除 archive/ 目錄
rsync -av --exclude='*/archive/' \
  AISDLC_${OLD_VERSION}/workflow/ \
  AISDLC_${NEW_VERSION}/workflow/

# 驗證
tree AISDLC_${NEW_VERSION}/workflow -L 2
```

**檢查點**:
- [ ] workflow/core/ 已拷貝 (8 個核心 Workflow)
- [ ] workflow/scenario-specific/ 已拷貝 (情境專屬 Workflow)
- [ ] workflow/README.md 已拷貝
- [ ] ✅ 所有 README.md 自動包含

**核心 Workflows (8 個)**:
1. requirements-extraction.md
2. requirements-validation-and-documentation.md
3. user-story-and-design.md
4. api-specification.md
5. change-management.md
6. consistency-check.md
7. interaction-analysis.md
8. sprint-execution.md

---

### 2.4 拷貝 docs_template/ 目錄

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 使用 rsync 拷貝，排除 archive/ 目錄
rsync -av --exclude='*/archive/' \
  AISDLC_${OLD_VERSION}/docs_template/ \
  AISDLC_${NEW_VERSION}/docs_template/

# 驗證
tree AISDLC_${NEW_VERSION}/docs_template -L 2
```

**檢查點**:
- [ ] docs_template/prd/ 已拷貝
- [ ] docs_template/frd/ 已拷貝
- [ ] docs_template/srd/ 已拷貝
- [ ] docs_template/tests/ 已拷貝
- [ ] docs_template/core/ 已拷貝
- [ ] docs_template/scenario_specific/ 已拷貝
- [ ] ✅ 所有 README.md 自動包含

---

### 2.5 拷貝 scenarios/ 目錄

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 使用 rsync 拷貝，排除 archive/ 目錄
rsync -av --exclude='*/archive/' \
  AISDLC_${OLD_VERSION}/scenarios/ \
  AISDLC_${NEW_VERSION}/scenarios/

# 驗證
tree AISDLC_${NEW_VERSION}/scenarios -L 2
```

**檢查點**:
- [ ] scenarios/greenfield/ 已拷貝 (SOP.md, Workflow.md, checklists/)
- [ ] scenarios/brownfield/ 已拷貝
- [ ] scenarios/refactoring/ 已拷貝
- [ ] scenarios/integration/ 已拷貝
- [ ] scenarios/testing/ 已拷貝
- [ ] scenarios/security/ 已拷貝
- [ ] scenarios/performance/ 已拷貝
- [ ] scenarios/devops/ 已拷貝
- [ ] scenarios/documentation/ 已拷貝
- [ ] scenarios/migration/ 已拷貝
- [ ] ✅ 所有 README.md 自動包含

**預期場景數量**: 10 個場景目錄

---

### 2.6 拷貝 guides/ 目錄

> **🔴 重要原則**: archive/ 目錄不搬移，其他所有內容（包含 README.md）自動搬移

> **🔴🔴🔴 注意 archive/ 排除模式 🔴🔴🔴**
>
> **此步驟在 v0.09→v0.09 實際升版中發生排除失敗！**
>
> **問題**: `--exclude='*/archive/'` 模式無法排除根層級的 `guides/backup/`
>
> **解決方案**: 使用多個排除模式，明確排除 `archive/` 和 `archive_*/`
>
> **已修正**: 增加 `--exclude='archive/'` 確保根層級 archive/ 也被排除

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 使用 rsync 拷貝整個 guides/ 目錄，排除 archive/ 目錄
# 🔴 注意：只排除純 archive/ 目錄，保留 backup_en/ 等有用的備份
rsync -av \
  --exclude='archive/' \
  --exclude='*/archive/' \
  AISDLC_${OLD_VERSION}/guides/ \
  AISDLC_${NEW_VERSION}/guides/

# 驗證
tree AISDLC_${NEW_VERSION}/guides -L 2 || find AISDLC_${NEW_VERSION}/guides -type d | head -20

# 🔴 強制驗證：確保 archive/ 目錄確實被排除
echo ""
echo "=== 🔴 強制驗證 archive/ 排除 ==="
ARCHIVE_COUNT=$(find AISDLC_${NEW_VERSION}/guides -type d -name "archive*" 2>/dev/null | wc -l | tr -d ' ')
if [ "$ARCHIVE_COUNT" -eq 0 ]; then
  echo "✅ 已成功排除所有 archive 目錄"
else
  echo "❌ 發現 $ARCHIVE_COUNT 個 archive 目錄，需要手動刪除："
  find AISDLC_${NEW_VERSION}/guides -type d -name "archive*"
  echo ""
  echo "執行清理："
  rm -rf AISDLC_${NEW_VERSION}/guides/backup*
  echo "✅ 已清理"
fi
```

**檢查點**:
- [ ] guides/system/ 已拷貝（含所有子目錄和 README.md）
- [ ] guides/user/ 已拷貝（含所有子目錄和 README.md）
- [ ] **guides/user/ 新增檔案已拷貝** ← 🆕 2025-01-10/11
  - [ ] guides/user/onboarding/PROJECT_INITIALIZATION_GUIDE.md
  - [ ] guides/user/onboarding/PROJECT_INITIALIZATION_CHECKLIST.md
  - [ ] guides/user/standards/PROJECT_DOCUMENTATION_STANDARDS.md
  - [ ] guides/user/process/Development_Build_Test_Cycle.md
- [ ] guides/README.md 已拷貝
- [ ] ❌ guides/backup/ **未拷貝**（符合預期）
- [ ] ✅ **所有 README.md 自動包含**（除了 archive/ 下的）

---

### 2.7 拷貝 prompts/ 目錄

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 使用 rsync 拷貝，排除 archive/ 目錄
rsync -av --exclude='*/archive/' \
  AISDLC_${OLD_VERSION}/prompts/ \
  AISDLC_${NEW_VERSION}/prompts/

# 驗證
tree AISDLC_${NEW_VERSION}/prompts -L 2
```

**檢查點**:
- [ ] prompts/quick-start/ 已拷貝
- [ ] prompts/complete-flow/ 已拷貝
- [ ] prompts/scenario-prompts/ 已拷貝
- [ ] prompts/README.md 已拷貝
- [ ] ✅ 所有 README.md 自動包含

---

### 2.8 拷貝 docs/ 目錄

> **🔴 重要**: docs/ 目錄包含實際專案產出的文檔（PRD、FRD、SRD、API Spec 等），必須完整拷貝。

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 檢查 docs/ 目錄是否存在
if [ -d "AISDLC_${OLD_VERSION}/docs" ]; then
  # 使用 rsync 拷貝整個 docs/ 目錄，排除 archive/ 目錄
  rsync -av --exclude='*/archive/' \
    AISDLC_${OLD_VERSION}/docs/ \
    AISDLC_${NEW_VERSION}/docs/

  # 驗證
  echo "=== 驗證 docs/ 目錄 ==="
  ls -lh AISDLC_${NEW_VERSION}/docs/
  find AISDLC_${NEW_VERSION}/docs -type d
  echo "✅ docs/ 目錄已拷貝"
else
  echo "⚠️  AISDLC_${OLD_VERSION}/docs 目錄不存在，跳過"
fi
```

**檢查點**:
- [ ] docs/ 目錄已拷貝（如果源版本中存在）
- [ ] docs/ 子目錄結構完整
- [ ] docs/ 內的所有文檔檔案已拷貝
- [ ] ✅ 所有 README.md 自動包含

**說明**:
- docs/ 目錄是層次 2（可執行內容），應該拷貝
- 如果源版本沒有 docs/ 目錄，則跳過此步驟
- docs/ 通常包含實際專案的需求文檔、設計文檔等
- 使用 rsync 排除 archive/ 目錄

---

### 2.9 拷貝 tools/ 目錄

> **🔴 重要**: tools/ 目錄包含輔助工具和腳本，必須完整拷貝。

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 檢查 tools/ 目錄是否存在
if [ -d "AISDLC_${OLD_VERSION}/tools" ]; then
  # 使用 rsync 拷貝整個 tools/ 目錄，排除 archive/ 目錄
  rsync -av --exclude='*/archive/' \
    AISDLC_${OLD_VERSION}/tools/ \
    AISDLC_${NEW_VERSION}/tools/

  # 驗證
  echo "=== 驗證 tools/ 目錄 ==="
  ls -lh AISDLC_${NEW_VERSION}/tools/
  find AISDLC_${NEW_VERSION}/tools -type f
  echo "✅ tools/ 目錄已拷貝"
else
  echo "⚠️  AISDLC_${OLD_VERSION}/tools 目錄不存在，跳過"
fi
```

**檢查點**:
- [ ] tools/ 目錄已拷貝（如果源版本中存在）
- [ ] tools/ 內的所有腳本和工具已拷貝
- [ ] **init_project.sh 已拷貝** ← 🆕 專案初始化腳本（macOS/Linux）
- [ ] **init_project.ps1 已拷貝** ← 🆕 專案初始化腳本（Windows PowerShell）
- [ ] **AISDLC_CLAUDE_RULES.md 已拷貝** ← Claude Code 框架開發規則
- [ ] **PROJECT_CLAUDE_Template.md 已拷貝** ← 🆕 專案 CLAUDE.md 模板（2025-01-11）
- [ ] tools/ 子目錄結構完整
- [ ] ✅ 所有 README.md 自動包含

**說明**:
- tools/ 目錄是層次 2（可執行內容），應該拷貝
- 如果源版本沒有 tools/ 目錄，則跳過此步驟
- **🆕 重要檔案 (2025-01-10/11 更新)**:
  - `init_project.sh`: 專案初始化腳本（macOS/Linux），創建 8 層編號目錄 (01-08)
  - `init_project.ps1`: 專案初始化腳本（Windows PowerShell）
  - `AISDLC_CLAUDE_RULES.md`: Claude Code 框架開發規則配置檔
  - `PROJECT_CLAUDE_Template.md`: 新專案的 CLAUDE.md 模板，包含 Agent 自動載入機制
  - 參考: [DEVELOPMENT_DIRECTORY_STRUCTURE.md](../DEVELOPMENT_DIRECTORY_STRUCTURE.md) 詳細目錄結構
- tools/ 通常包含自動化腳本、驗證工具等
- 使用 rsync 排除 archive/ 目錄

---

### 2.9.1 拷貝 .claude/ 目錄 🆕 v0.09 新增

> **🔴 重要**: .claude/ 目錄包含 Claude Code Skills 技能套件（33 個 Skills），是 v0.09 新增的重要目錄。

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 檢查 .claude/ 目錄是否存在
if [ -d "AISDLC_${OLD_VERSION}/.claude" ]; then
  # 使用 rsync 拷貝整個 .claude/ 目錄
  rsync -av --exclude='*/archive/' \
    AISDLC_${OLD_VERSION}/.claude/ \
    AISDLC_${NEW_VERSION}/.claude/

  echo "=== 驗證 .claude/ 目錄 ==="
  ls -la AISDLC_${NEW_VERSION}/.claude/

  # 統計 Skills 數量（Claude Code Agent Skills 標準格式）
  echo "=== Skills 統計 (SKILL.md 檔案數) ==="
  find AISDLC_${NEW_VERSION}/.claude/skills -name "SKILL.md" -type f | wc -l
  # 預期結果: 33
  echo "✅ .claude/ 目錄拷貝完成"
else
  echo "⚠️ 源版本不存在 .claude/ 目錄，跳過此步驟"
fi
```

**檢查點**:
- [ ] .claude/ 目錄已拷貝（如果源版本存在）
- [ ] .claude/skills/ 目錄已拷貝
- [ ] Skills 檔案數量正確（v0.09+ 應有 33 個 SKILL.md 檔案）
- [ ] README.md 和 SKILL_DEVELOPMENT_PLAN.md 已拷貝

**說明**:
- .claude/ 是 Claude Code 的配置目錄
- .claude/skills/ 包含 33 個 Claude Skills 技能套件（Claude Code Agent Skills 標準格式）
  - 格式: `<skill-name>/SKILL.md`（每個 Skill 一個目錄）
  - DevOps 家族: 5 個 Skills
  - Integration 家族: 10 個 Skills
  - Code Quality 家族: 4 個 Skills
  - Security/Compliance/Docs 家族: 3 個 Skills
  - Agent 家族: 6 個 Skills (SA/BA/SD/QA/Dev/PM)
  - Workflow 家族: 3 個 Skills (Sprint/Release/Code Review)
- 使用 rsync 排除 archive/ 目錄

---

### 2.10 創建 releases/package/ 保留結構

> **🔴 重要**: releases/package/ 是層次 2 的保留結構，用於未來的版本發布包。

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

# 切換到新版本目錄
cd AISDLC_${NEW_VERSION}

# 創建 releases/package/ 保留結構
mkdir -p releases/package

# 創建 README.md 說明
cat > releases/package/README.md << 'EOF'
# Release Package Directory

**用途**: 此目錄用於存放版本發布的保留結構。

**層次分類**: 層次 2（可執行內容）

## 說明

- **releases/package/**: 保留結構，用於未來版本打包
- **releases/*.tar.gz**: 層次 3（建置產物），不拷貝到新版本

## 使用方式

未來版本發布時，可在此目錄建立發布包的暫存結構。

**最後更新**: $(date +%Y-%m-%d)
EOF

# 創建 releases/v0.10/ 版本目錄（可選）
mkdir -p releases/v0.10

# 驗證
echo "=== 驗證 releases/ 目錄結構 ==="
tree releases/ -L 2
echo "✅ releases/package/ 保留結構已創建"
```

**檢查點**:
- [ ] releases/package/ 目錄已創建
- [ ] releases/package/README.md 說明文檔已創建
- [ ] releases/v0.10/ 版本目錄已創建（可選）

**說明**:
- releases/package/ 是層次 2 保留結構
- releases/*.tar.gz 發布檔案是層次 3，不拷貝
- 此結構為未來版本發布預留空間

---

### 2.11 拷貝完成驗證

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 列出所有頂層目錄和檔案
ls -lh

# 統計目錄數量
echo "=== 目錄數量驗證 ==="
ls -d */ | wc -l  # 應該有 9-10 個目錄（包含 docs/, tools/, releases/）
```

**最終檢查清單**:
- [ ] 根目錄有 6 個核心文檔
- [ ] agent/ 目錄已拷貝
- [ ] workflow/ 目錄已拷貝
- [ ] docs_template/ 目錄已拷貝
- [ ] scenarios/ 目錄已拷貝 (10 個場景)
- [ ] guides/ 目錄已拷貝
- [ ] prompts/ 目錄已拷貝
- [ ] **docs/ 目錄已拷貝**（如果源版本存在）
- [ ] **tools/ 目錄已拷貝**（如果源版本存在）
- [ ] **🆕 .claude/ 目錄已拷貝**（如果源版本存在）← v0.09 Skills 目錄
- [ ] **releases/package/ 保留結構已創建**
- [ ] build/ 目錄結構已創建

---

### 2.12 完整性驗證：目錄與檔案數量比對

> **🔴 極度重要**: 驗證總目錄與檔案數量，若前後版本數目不對，找出差異並修正。

**執行命令**:

```bash
# 確保從 BASE_DIR 開始
cd ${BASE_DIR}

echo "========================================="
echo "   階段 2.12: 完整性驗證與差異偵測"
echo "========================================="
echo ""

# ============================================
# 1. archive/ 目錄排除驗證
# ============================================
echo "=== 1. archive/ 目錄排除驗證 ==="
ARCHIVE_COUNT=$(find AISDLC_${NEW_VERSION} -type d -name "archive*" 2>/dev/null | wc -l | tr -d ' ')
if [ "$ARCHIVE_COUNT" -eq 0 ]; then
  echo "✅ archive/ 目錄已成功排除（數量: 0）"
else
  echo "❌ 發現 $ARCHIVE_COUNT 個 archive 目錄，請檢查："
  find AISDLC_${NEW_VERSION} -type d -name "archive*"
  echo ""
  echo "🔴 問題修正: 手動刪除這些 archive 目錄"
fi
echo ""

# ============================================
# 2. 總目錄數量比對
# ============================================
echo "=== 2. 總目錄數量比對 ==="

# ⚠️ 必須執行並記錄實際輸出 - 禁止編造數字！
OLD_DIR_COUNT=$(find AISDLC_${OLD_VERSION} -type d -not -path "*/archive/*" -not -path "*/build/*" 2>/dev/null | wc -l | tr -d ' ')
NEW_DIR_COUNT=$(find AISDLC_${NEW_VERSION} -type d -not -path "*/build/*" 2>/dev/null | wc -l | tr -d ' ')

echo "源版本 (排除 archive/, build/): $OLD_DIR_COUNT 個目錄"
echo "目標版本 (排除 build/): $NEW_DIR_COUNT 個目錄"
echo ""
echo "⚠️  請記錄以上實際輸出數字到檢查點！"

if [ "$NEW_DIR_COUNT" -ge "$OLD_DIR_COUNT" ]; then
  echo "✅ 目錄數量正常（目標版本 >= 源版本）"
else
  echo "❌ 目錄數量異常！目標版本少於源版本"
  echo "   差異: $((OLD_DIR_COUNT - NEW_DIR_COUNT)) 個目錄"
  echo ""
  echo "🔍 差異分析 - 源版本有但目標版本沒有的目錄:"
  comm -23 <(find AISDLC_${OLD_VERSION} -type d -not -path "*/archive/*" -not -path "*/build/*" | sed "s|AISDLC_${OLD_VERSION}/||" | sort) \
           <(find AISDLC_${NEW_VERSION} -type d -not -path "*/build/*" | sed "s|AISDLC_${NEW_VERSION}/||" | sort)
  echo ""
  echo "🔴 問題修正: 請回到對應的 Stage 2.X 步驟重新拷貝遺漏的目錄"
fi
echo ""

# ============================================
# 3. 總檔案數量比對
# ============================================
echo "=== 3. 總檔案數量比對 ==="

# ⚠️ 必須執行並記錄實際輸出 - 禁止編造數字！
OLD_FILE_COUNT=$(find AISDLC_${OLD_VERSION} -type f -not -path "*/archive/*" -not -path "*/build/*" 2>/dev/null | wc -l | tr -d ' ')
NEW_FILE_COUNT=$(find AISDLC_${NEW_VERSION} -type f -not -path "*/build/*" 2>/dev/null | wc -l | tr -d ' ')

echo "源版本 (排除 archive/, build/): $OLD_FILE_COUNT 個檔案"
echo "目標版本 (排除 build/): $NEW_FILE_COUNT 個檔案"
echo ""
echo "⚠️  請記錄以上實際輸出數字到檢查點！"

if [ "$NEW_FILE_COUNT" -ge "$OLD_FILE_COUNT" ]; then
  echo "✅ 檔案數量正常（目標版本 >= 源版本）"
else
  echo "❌ 檔案數量異常！目標版本少於源版本"
  echo "   差異: $((OLD_FILE_COUNT - NEW_FILE_COUNT)) 個檔案"
  echo ""
  echo "🔍 差異分析 - 遺漏檔案類型統計:"
  comm -23 <(find AISDLC_${OLD_VERSION} -type f -not -path "*/archive/*" -not -path "*/build/*" | sed "s|AISDLC_${OLD_VERSION}/||" | sort) \
           <(find AISDLC_${NEW_VERSION} -type f -not -path "*/build/*" | sed "s|AISDLC_${NEW_VERSION}/||" | sort) | \
    awk -F. '{print $NF}' | sort | uniq -c | sort -rn
  echo ""
  echo "🔍 前 10 個遺漏的檔案:"
  comm -23 <(find AISDLC_${OLD_VERSION} -type f -not -path "*/archive/*" -not -path "*/build/*" | sed "s|AISDLC_${OLD_VERSION}/||" | sort) \
           <(find AISDLC_${NEW_VERSION} -type f -not -path "*/build/*" | sed "s|AISDLC_${NEW_VERSION}/||" | sort) | head -10
  echo ""
  echo "🔴 問題修正: 請檢查遺漏的目錄並重新執行對應的 Stage 2.X 步驟"
fi
echo ""

# ============================================
# 4. 總結報告
# ============================================
echo "========================================="
echo "   完整性驗證總結"
echo "========================================="
echo "✅ 檢查項目:"
echo "   1. archive/ 目錄排除: $([ "$ARCHIVE_COUNT" -eq 0 ] && echo '通過' || echo '❌ 失敗')"
echo "   2. 總目錄數量: $([ "$NEW_DIR_COUNT" -ge "$OLD_DIR_COUNT" ] && echo '通過' || echo '❌ 失敗')"
echo "   3. 總檔案數量: $([ "$NEW_FILE_COUNT" -ge "$OLD_FILE_COUNT" ] && echo '通過' || echo '❌ 失敗')"
echo ""
echo "📊 數量統計:"
echo "   - 源版本目錄數: $OLD_DIR_COUNT"
echo "   - 目標版本目錄數: $NEW_DIR_COUNT"
echo "   - 源版本檔案數: $OLD_FILE_COUNT"
echo "   - 目標版本檔案數: $NEW_FILE_COUNT"
echo ""

if [ "$ARCHIVE_COUNT" -eq 0 ] && [ "$NEW_DIR_COUNT" -ge "$OLD_DIR_COUNT" ] && [ "$NEW_FILE_COUNT" -ge "$OLD_FILE_COUNT" ]; then
  echo "🎉 所有驗證通過！階段 2 拷貝作業完成"
else
  echo "⚠️  發現問題，請根據上述修正建議處理"
fi
echo "========================================="
```

**檢查點**:
- [ ] ✅ archive/ 目錄已排除（數量 = 0）← ⚠️ 必須執行驗證: `find AISDLC_v0.10 -type d -name "archive*" | wc -l`
- [ ] ✅ 總目錄數量正常（目標版本 >= 源版本）← ⚠️ 必須記錄實際數字
  - 源版本 (v0.09) 目錄數: _____ 個 ← 必須填寫實際數字
  - 目標版本 (v0.10) 目錄數: _____ 個 ← 必須填寫實際數字
  - 差異: _____ 個 ← 必須計算並填寫
  - 差異原因釐清: ______________________ ← 必須說明（如：排除 archive/, 排除 guides/backup/ 等）
- [ ] ✅ 總檔案數量正常（目標版本 >= 源版本）← ⚠️ 必須記錄實際數字
  - 源版本 (v0.09) 檔案數: _____ 個 ← 必須填寫實際數字
  - 目標版本 (v0.10) 檔案數: _____ 個 ← 必須填寫實際數字
  - 差異: _____ 個 ← 必須計算並填寫
  - 差異原因釐清: ______________________ ← 必須說明（如：build/ 未拷貝，archive 檔案排除等）
- [ ] 如有差異，已定位並修正問題

**🔴 統計比較表（必須填寫）**:

| 項目 | v0.09 | v0.10 | 差異 | 差異原因釐清 |
|------|-------|-------|------|-------------|
| 總目錄數（排除 archive/, build/） | _____ | _____ | _____ | ______________________ |
| 總檔案數（排除 archive/, build/） | _____ | _____ | _____ | ______________________ |

**填表說明**:
- 必須執行上述 find 命令取得真實數字
- 差異 = v0.09 數量 - v0.10 數量
- 差異原因必須明確說明（例如：「v0.09 有 guides/backup/ 5 個目錄，v0.10 已排除」）
- 如差異為 0，必須額外驗證是否真的完全一致（可能是統計錯誤）

**說明**:
- **3 層驗證（簡化版）**:
  1. **archive/ 排除驗證** - 確保 archive 目錄已正確排除
  2. **總目錄數量比對** - 嚴謹比對前後版本的目錄數（核心驗證）
  3. **總檔案數量比對** - 嚴謹比對前後版本的檔案數（核心驗證）
- **驗證原則**: 只要目錄數和檔案數正確，README.md 應該就正確，無需額外驗證
- **自動差異偵測**: 使用 `comm` 指令找出源版本有但目標版本沒有的項目
- **問題定位**: 提供遺漏檔案清單和統計，方便快速找出問題
- **修正指引**: 每個問題都附帶明確的修正建議

---

## 階段 3: 版本號更新

**🚫 禁止委派**: 本階段禁止使用 Task 工具，必須由主 Agent 直接執行

**執行證據要求**:
- 必須創建檢查點檔案: `build/logs/stage_3.checkpoint`
- 檢查點必須包含: v0.09 殘留數量（真實 grep 輸出）、更新的檔案清單
- 必須將命令輸出記錄到: `build/logs/stage_3_execution.log`

> **🔴 極度重要**: 版本號更新必須 100% 完成，任何遺漏都會導致引用錯誤。

### 3.1 核心文檔版本號更新（統一 8 種格式）

> **🔴 改進**: 統一處理 8 種版本號格式，確保 100% 覆蓋率。

**版本號格式定義**:

為了避免升版錯誤，必須定義並統一更新以下 8 種版本號格式：

```bash
# 設定額外的版本號變數（在 Stage 1.1 基礎上）
export OLD_VER="0.09"           # 格式: 0.09
export NEW_VER="0.10"           # 格式: 0.10
export OLD_VER_V="v0.09"        # 格式: v0.09
export NEW_VER_V="v0.10"        # 格式: v0.10
export NEXT_VER="0.11"          # 格式: 0.11 (下一版本)
export NEXT_VER_V="v0.11"       # 格式: v0.11 (下一版本)
```

**8 種版本號格式**:
1. `version: "v0.09"` → `version: "v0.10"` (YAML 格式，帶引號，v 前綴)
2. `version: v0.09` → `version: v0.10` (YAML 格式，無引號，v 前綴)
3. `version: "0.09"` → `version: "0.10"` (YAML 格式，帶引號，無 v 前綴)
4. `version: 0.09` → `version: 0.10` (YAML 格式，無引號，無 v 前綴)
5. `AISDLC v0.09` → `AISDLC v0.10` (文字描述，空格分隔)
6. `AISDLC_v0.09` → `AISDLC_v0.10` (路徑格式，底線連接)
7. `v0.09` → `v0.10` (純版本號，v 前綴)
8. `Current Version: v0.09` → `Current Version: v0.10` (標題格式)

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== 開始統一版本號更新（8 種格式） ==="

# 🔴 簡化版：避免 for loop，使用直接的 sed 命令處理所有 .md 檔案
# 原因：Bash 工具對多行 for loop 的解析不穩定

# 格式 1-8: 對所有 .md 檔案執行版本號替換
# 注意：sed 在 macOS 上需要 -i '' 參數

echo "正在更新所有 .md 檔案的版本號..."

# 使用 find 找到所有 .md 檔案並逐一處理
find . -maxdepth 1 -name "*.md" -type f -exec sed -i '' "s/version: \"${OLD_VER_V}\"/version: \"${NEW_VER_V}\"/g" {} \;
find . -maxdepth 1 -name "*.md" -type f -exec sed -i '' "s/version: ${OLD_VER_V}/version: ${NEW_VER_V}/g" {} \;
find . -maxdepth 1 -name "*.md" -type f -exec sed -i '' "s/version: \"${OLD_VER}\"/version: \"${NEW_VER}\"/g" {} \;
find . -maxdepth 1 -name "*.md" -type f -exec sed -i '' "s/version: ${OLD_VER}/version: ${NEW_VER}/g" {} \;
find . -maxdepth 1 -name "*.md" -type f -exec sed -i '' "s/AISDLC ${OLD_VER_V}/AISDLC ${NEW_VER_V}/g" {} \;
find . -maxdepth 1 -name "*.md" -type f -exec sed -i '' "s/AISDLC_${OLD_VER_V}/AISDLC_${NEW_VER_V}/g" {} \;
find . -maxdepth 1 -name "*.md" -type f -exec sed -i '' "s/${OLD_VER_V}/${NEW_VER_V}/g" {} \;
find . -maxdepth 1 -name "*.md" -type f -exec sed -i '' "s/Current Version.*${OLD_VER_V}/Current Version: ${NEW_VER_V}/g" {} \;

echo "✅ 根目錄所有 .md 檔案版本號已更新"

# 特殊處理: AISDLC_v${NEXT_VER_V}_UPGRADE_SOP.md 需要更新 OLD_VERSION 和 NEW_VERSION 變數
# 🔴 注意：此檔案在 Stage 2.1 已從 AISDLC_${NEW_VERSION}_UPGRADE_SOP.md 改名為 AISDLC_v${NEXT_VER_V}_UPGRADE_SOP.md
NEXT_SOP="AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md"
if [ -f "${NEXT_SOP}" ]; then
  echo "特殊處理: ${NEXT_SOP}"
  sed -i "s/OLD_VERSION=\"${OLD_VER_V}\"/OLD_VERSION=\"${NEW_VER_V}\"/g" "${NEXT_SOP}"
  sed -i "s/NEW_VERSION=\"${OLD_VER_V}\"/NEW_VERSION=\"${NEXT_VER_V}\"/g" "${NEXT_SOP}"
  sed -i "s/OLD_VER=\"${OLD_VER}\"/OLD_VER=\"${NEW_VER}\"/g" "${NEXT_SOP}"
  sed -i "s/NEW_VER=\"${OLD_VER}\"/NEW_VER=\"${NEXT_VER}\"/g" "${NEXT_SOP}"
  echo "✅ ${NEXT_SOP} 環境變數已更新"
fi

# 驗證
echo ""
echo "=== 版本號更新驗證 ==="
echo "檢查是否還有 v0.09 殘留:"
grep -n "${OLD_VER_V}" *.md | wc -l  # 應該 = 0

echo ""
echo "檢查 v0.09 數量:"
grep -n "${NEW_VER_V}" *.md | wc -l  # 應該 > 50
```

**檢查點**:
- [ ] 所有根目錄 .md 文檔版本號已更新（8 種格式全部覆蓋）← ⚠️ 執行驗證: `grep -c "v0.09" *.md`（應為 0）
- [ ] AISDLC_INIT.md 版本號 = v0.10 ← ⚠️ 執行驗證: `grep "version:" AISDLC_INIT.md`
- [ ] FILE_DIRECTORY_RULES.md 版本號 = v0.10 ← ⚠️ 執行驗證: `grep "version:" FILE_DIRECTORY_RULES.md`
- [ ] README.md 版本號 = v0.10 ← ⚠️ 執行驗證: `grep "v0.10" README.md`
- [ ] AISDLC_v0.11_UPGRADE_SOP.md 版本號 = v0.10 → v0.11 ← ⚠️ 執行驗證: `grep "NEW_VERSION=" AISDLC_v0.11_UPGRADE_SOP.md`
- [ ] AISDLC_v0.11_UPGRADE_SOP.md 環境變數已更新 ← ⚠️ 執行驗證: `grep "OLD_VERSION\|NEW_VERSION" AISDLC_v0.11_UPGRADE_SOP.md | head -10`
- [ ] 無 v0.09 殘留 ← ⚠️ 執行驗證: `grep -n "v0.09" *.md | wc -l`（應為 0）

**改進說明**:
- **8 種格式統一處理**: 覆蓋所有可能的版本號格式
- **動態遍歷**: 自動處理所有 .md 文檔
- **UPGRADE_SOP 特殊處理**: 自動更新環境變數定義
- **100% 覆蓋率**: 確保無版本號遺漏

---

### 3.2 Agent 版本號更新（統一 8 種格式）

> **🔴 改進**: Agent 配置檔案使用 YAML 格式，必須覆蓋所有可能的版本號格式。

> **🔴 重要 (2025-12-11 修正)**: 原版本使用 `while read` 管道命令，在 Bash 工具中會失敗。已改用 `find ... -exec sed ...` 簡化命令。

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== 開始更新 Agent 版本號（8 種格式） ==="

# 🔴 簡化版：使用 find -exec 代替 while read 管道
# 原因：Bash 工具無法正確處理 find | while read 管道命令

# 更新所有 Core Agents (7 個 *-zh.yaml)
echo "處理 Core Agents..."
find agent/core -name "*-zh.yaml" -type f -exec sed -i '' "s/version: \"${OLD_VER_V}\"/version: \"${NEW_VER_V}\"/g" {} \;
find agent/core -name "*-zh.yaml" -type f -exec sed -i '' "s/version: ${OLD_VER_V}/version: ${NEW_VER_V}/g" {} \;
find agent/core -name "*-zh.yaml" -type f -exec sed -i '' "s/version: \"${OLD_VER}\"/version: \"${NEW_VER}\"/g" {} \;
find agent/core -name "*-zh.yaml" -type f -exec sed -i '' "s/version: ${OLD_VER}/version: ${NEW_VER}/g" {} \;
find agent/core -name "*-zh.yaml" -type f -exec sed -i '' "s/AISDLC ${OLD_VER_V}/AISDLC ${NEW_VER_V}/g" {} \;
find agent/core -name "*-zh.yaml" -type f -exec sed -i '' "s/AISDLC_${OLD_VER_V}/AISDLC_${NEW_VER_V}/g" {} \;
find agent/core -name "*-zh.yaml" -type f -exec sed -i '' "s/${OLD_VER_V}/${NEW_VER_V}/g" {} \;
echo "✅ Core Agents 更新完成"

# 更新所有 Specialized Agents (14+ 個 *.yaml)
echo "處理 Specialized Agents..."
find agent/specialized -name "*.yaml" -type f -exec sed -i '' "s/version: \"${OLD_VER_V}\"/version: \"${NEW_VER_V}\"/g" {} \;
find agent/specialized -name "*.yaml" -type f -exec sed -i '' "s/version: ${OLD_VER_V}/version: ${NEW_VER_V}/g" {} \;
find agent/specialized -name "*.yaml" -type f -exec sed -i '' "s/version: \"${OLD_VER}\"/version: \"${NEW_VER}\"/g" {} \;
find agent/specialized -name "*.yaml" -type f -exec sed -i '' "s/version: ${OLD_VER}/version: ${NEW_VER}/g" {} \;
find agent/specialized -name "*.yaml" -type f -exec sed -i '' "s/AISDLC ${OLD_VER_V}/AISDLC ${NEW_VER_V}/g" {} \;
find agent/specialized -name "*.yaml" -type f -exec sed -i '' "s/AISDLC_${OLD_VER_V}/AISDLC_${NEW_VER_V}/g" {} \;
find agent/specialized -name "*.yaml" -type f -exec sed -i '' "s/${OLD_VER_V}/${NEW_VER_V}/g" {} \;
echo "✅ Specialized Agents 更新完成"

# 驗證
echo ""
echo "=== Agent 版本號驗證 ==="
echo "檢查 Core Agents 舊版本殘留:"
grep -r "${OLD_VER}" agent/core/*.yaml 2>/dev/null | wc -l  # 應該 = 0

echo "檢查 Specialized Agents 舊版本殘留:"
grep -r "${OLD_VER}" agent/specialized/*.yaml 2>/dev/null | wc -l  # 應該 = 0

echo "檢查 Core Agents 新版本數量:"
grep -r "${NEW_VER}" agent/core/*.yaml 2>/dev/null | wc -l  # 應該 >= 7

echo "檢查 Specialized Agents 新版本數量:"
grep -r "${NEW_VER}" agent/specialized/*.yaml 2>/dev/null | wc -l  # 應該 >= 14

echo ""
echo "✅ Agent 版本號更新完成"
```

**檢查點**:
- [ ] Core Agents (7 個) 版本號已更新（8 種格式全部覆蓋）← ⚠️ 執行驗證: `grep -r "0.09" agent/core/*.yaml | wc -l`（應為 0）
- [ ] Specialized Agents (14+ 個) 版本號已更新（8 種格式全部覆蓋）← ⚠️ 執行驗證: `grep -r "0.09" agent/specialized/*.yaml | wc -l`（應為 0）
- [ ] 無 0.09 或 v0.09 殘留 ← ⚠️ 必須驗證
- [ ] Core Agents 新版本引用 >= 7 處 ← ⚠️ 執行驗證: `grep -r "0.10" agent/core/*.yaml | wc -l`
- [ ] Specialized Agents 新版本引用 >= 14 處 ← ⚠️ 執行驗證: `grep -r "0.10" agent/specialized/*.yaml | wc -l`

**改進說明**:
- **8 種格式統一處理**: 覆蓋所有可能的 YAML 版本號格式
- **分類處理**: Core Agents 和 Specialized Agents 分別處理
- **動態遍歷**: 使用 find + while read 避免檔案名包含空格的問題
- **完整驗證**: 分別驗證 Core 和 Specialized Agents 的更新結果

---

### 3.3 Workflow 版本號更新

> **🔴 重要 (2025-12-11 修正)**: Workflow 檔案使用 `version: "v0.09"` 格式（帶引號和 v 前綴），原 SOP 遺漏此格式，導致更新後仍有 v0.09 殘留。

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== 開始更新 Workflow 版本號 ==="

# 🔴 修正：必須包含帶引號的版本號格式
# 格式 1: version: "v0.09" → version: "v0.10" (帶引號，v 前綴) ← 最常見格式
find workflow -name "*.md" -type f -exec sed -i "s/version: \"v0.09\"/version: \"v0.10\"/g" {} \;

# 格式 2: version: "0.09" → version: "0.10" (帶引號，無 v 前綴)
find workflow -name "*.md" -type f -exec sed -i "s/version: \"0.09\"/version: \"0.10\"/g" {} \;

# 格式 3: version: v0.09 → version: v0.10 (無引號，v 前綴)
find workflow -name "*.md" -type f -exec sed -i "s/version: v0.09/version: v0.10/g" {} \;

# 格式 4: version: 0.09 → version: 0.10 (無引號，無 v 前綴)
find workflow -name "*.md" -type f -exec sed -i "s/version: 0.09/version: 0.10/g" {} \;

# 格式 5: AISDLC_v0.09 → AISDLC_v0.10 (路徑格式)
find workflow -name "*.md" -type f -exec sed -i "s/AISDLC_v0.09/AISDLC_v0.10/g" {} \;

# 格式 6: v0.09 → v0.10 (純版本號)
find workflow -name "*.md" -type f -exec sed -i "s/v0\.09/v0.10/g" {} \;

echo "✅ Workflow 版本號更新完成"

# 驗證
echo ""
echo "=== Workflow 版本號驗證 ==="
echo "檢查 v0.09 殘留:"
grep -r "v0\.09" workflow/ | wc -l  # 應該是 0

echo "檢查 0.09 殘留:"
grep -r "0\.09" workflow/ | wc -l  # 應該是 0

echo "檢查 v0.10 數量:"
grep -r "v0\.10" workflow/ | wc -l  # 應該 >= 7

echo "檢查 version: 格式:"
grep -r "version:" workflow/ | head -5  # 顯示前 5 個以確認格式正確
```

**檢查點**:
- [ ] Core Workflows 版本號 = 0.10
- [ ] Supplementary Workflows 版本號 = 0.10
- [ ] 無 0.09 殘留

---

### 3.4 文檔模板版本號更新

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 更新所有文檔模板
find docs_template -name "*.md" -type f -exec sed -i "s/version: 0.09/version: 0.10/g" {} \;
find docs_template -name "*.md" -type f -exec sed -i "s/AISDLC_v0.09/AISDLC_v0.10/g" {} \;
find docs_template -name "*.md" -type f -exec sed -i "s/v0\.09/v0.10/g" {} \;

# 驗證
echo "=== 文檔模板版本號驗證 ==="
grep -r "version: 0.09" docs_template/ | wc -l  # 應該是 0
grep -r "version: 0.10" docs_template/ | wc -l  # 應該 > 20
```

**檢查點**:
- [ ] PRD 模板版本號 = 0.10
- [ ] FRD 模板版本號 = 0.10
- [ ] SRD 模板版本號 = 0.10
- [ ] API 模板版本號 = 0.10
- [ ] Test 模板版本號 = 0.10
- [ ] 無 0.09 殘留

---

### 3.5 場景 SOP 版本號更新

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 更新所有場景 SOP
find scenarios -name "*.md" -type f -exec sed -i "s/version: 0.09/version: 0.10/g" {} \;
find scenarios -name "*.md" -type f -exec sed -i "s/AISDLC_v0.09/AISDLC_v0.10/g" {} \;
find scenarios -name "*.md" -type f -exec sed -i "s/v0\.09/v0.10/g" {} \;

# 驗證
echo "=== 場景 SOP 版本號驗證 ==="
grep -r "version: 0.09" scenarios/ | wc -l  # 應該是 0
grep -r "version: 0.10" scenarios/ | wc -l  # 應該 > 30 (10 個場景 × 3 檔案)
```

**檢查點**:
- [ ] Greenfield SOP 版本號 = 0.10
- [ ] Brownfield SOP 版本號 = 0.10
- [ ] 其他 7 個場景 SOP 版本號 = 0.10
- [ ] 無 0.09 殘留

---

### 3.6 Guides 版本號更新

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 更新所有 Guides
find guides -name "*.md" -type f -exec sed -i "s/AISDLC_v0.09/AISDLC_v0.10/g" {} \;
find guides -name "*.md" -type f -exec sed -i "s/AISDLC v0.09/AISDLC v0.10/g" {} \;
find guides -name "*.md" -type f -exec sed -i "s/v0\.09/v0.10/g" {} \;

# 驗證
echo "=== Guides 版本號驗證 ==="
grep -r "v0.09" guides/ | wc -l  # 應該是 0
```

**檢查點**:
- [ ] Guides 版本引用 = v0.10
- [ ] 無 v0.09 殘留

---

### 3.6.1 更新 build/README.md 版本號 🆕

> **🔴🔴🔴 極易遺漏！🔴🔴🔴**
>
> **此步驟在 v0.09→v0.09 實際升版中被遺漏！**
>
> **原因**: build/README.md 在階段 1.4.1 拷貝後，容易被忽略版本號更新
>
> **影響**: build/README.md 內的版本引用仍停留在舊版本

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== 更新 build/README.md 版本號 ==="

# 檢查 build/README.md 是否存在
if [ -f "build/README.md" ]; then
  echo "處理檔案: build/README.md"

  # 格式 1-8: 統一更新所有版本號格式
  sed -i '' "s/version: \"${OLD_VER_V}\"/version: \"${NEW_VER_V}\"/g" build/README.md
  sed -i '' "s/version: ${OLD_VER_V}/version: ${NEW_VER_V}/g" build/README.md
  sed -i '' "s/version: \"${OLD_VER}\"/version: \"${NEW_VER}\"/g" build/README.md
  sed -i '' "s/version: ${OLD_VER}/version: ${NEW_VER}/g" build/README.md
  sed -i '' "s/AISDLC ${OLD_VER_V}/AISDLC ${NEW_VER_V}/g" build/README.md
  sed -i '' "s/AISDLC_${OLD_VER_V}/AISDLC_${NEW_VER_V}/g" build/README.md
  sed -i '' "s/${OLD_VER_V}/${NEW_VER_V}/g" build/README.md
  sed -i '' "s/Current Version.*${OLD_VER_V}/Current Version: ${NEW_VER_V}/g" build/README.md

  echo "✅ build/README.md 版本號已更新"

  # 驗證：檢查是否還有舊版本殘留 ← ⚠️ 必須執行驗證命令
  echo ""
  echo "=== 驗證 build/README.md 版本號 ==="
  OLD_COUNT=$(grep -c "${OLD_VER_V}" build/README.md 2>/dev/null || echo "0")
  NEW_COUNT=$(grep -c "${NEW_VER_V}" build/README.md 2>/dev/null || echo "0")

  echo "舊版本 (${OLD_VER_V}) 殘留數: ${OLD_COUNT} (應為 0)"
  echo "新版本 (${NEW_VER_V}) 引用數: ${NEW_COUNT} (應 > 0)"

  if [ "$OLD_COUNT" -eq 0 ] && [ "$NEW_COUNT" -gt 0 ]; then
    echo "✅ 驗證通過"
  else
    echo "❌ 驗證失敗！請檢查 build/README.md"
    grep -n "${OLD_VER_V}" build/README.md
  fi
else
  echo "⚠️  build/README.md 不存在，跳過"
fi
```

**檢查點**:
- [ ] build/README.md 版本號已更新（如檔案存在）← ⚠️ 執行驗證: `grep -c "v0.09" build/README.md`（應為 0）
- [ ] 無 v0.09 殘留 ← ⚠️ 必須驗證
- [ ] v0.10 引用數 > 0 ← ⚠️ 必須驗證

**為什麼重要**:
- build/README.md 是 build/ 目錄結構的說明文檔
- 雖然在階段 1.4.1 拷貝，但版本號更新容易被忽略
- 此步驟確保 build/README.md 的版本號與框架主版本一致

---

### 3.6.2 更新 docs/README.md 版本號 🆕

> **🔴🔴🔴 極易遺漏！（2026-03-20 新增）🔴🔴🔴**
>
> **此步驟在 v0.09→v0.10 實際升版中被遺漏！**
>
> **原因**: Stage 3.1 只處理根目錄 .md（`-maxdepth 1`），docs/README.md 在子目錄中，未被覆蓋
>
> **影響**: docs/README.md 內的版本引用仍停留在舊版本

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== 更新 docs/README.md 版本號 ==="

if [ -f "docs/README.md" ]; then
  sed -i "s/${OLD_VER_V}/${NEW_VER_V}/g" docs/README.md
  sed -i "s/AISDLC_${OLD_VER_V}/AISDLC_${NEW_VER_V}/g" docs/README.md
  sed -i "s/${OLD_VER}/${NEW_VER}/g" docs/README.md
  echo "✅ docs/README.md 版本號已更新"

  # 驗證
  OLD_COUNT=$(grep -c "${OLD_VER_V}" docs/README.md 2>/dev/null || echo "0")
  echo "舊版本殘留數: ${OLD_COUNT} (應為 0)"
else
  echo "⚠️  docs/README.md 不存在，跳過"
fi
```

**檢查點**:
- [ ] docs/README.md 版本號已更新（如檔案存在）← ⚠️ 執行驗證: `grep -c "v0.09" docs/README.md`
- [ ] 無 v0.09 殘留 ← ⚠️ 必須驗證

---

### 3.6.3 更新 tools/ 腳本版本號 🆕

> **🔴🔴🔴 極度重要！（2026-03-20 新增）🔴🔴🔴**
>
> **此步驟在 v0.09→v0.10 實際升版中被遺漏！**
>
> **原因**: Stage 3 的版本更新命令僅處理 .md 和 .yaml 檔案，完全遺漏 .sh 和 .ps1 檔案
>
> **影響**: `tools/init_project.sh` 和 `tools/init_project.ps1` 的 DEFAULT_VERSION 仍為舊版本，
> 導致用戶使用初始化腳本時會建立錯誤版本的目錄結構

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== 更新 tools/ 腳本版本號 ==="

# 更新 Shell 腳本 (.sh)
if [ -f "tools/init_project.sh" ]; then
  echo "處理: tools/init_project.sh"
  sed -i "s/${OLD_VER}/${NEW_VER}/g" tools/init_project.sh
  echo "✅ init_project.sh 已更新"

  # 驗證
  grep -n "VERSION\|version\|0\.\(0[0-9]\)" tools/init_project.sh | head -10
fi

# 更新 PowerShell 腳本 (.ps1)
if [ -f "tools/init_project.ps1" ]; then
  echo "處理: tools/init_project.ps1"
  sed -i "s/${OLD_VER}/${NEW_VER}/g" tools/init_project.ps1
  echo "✅ init_project.ps1 已更新"

  # 驗證
  grep -n "Version\|version\|0\.\(0[0-9]\)" tools/init_project.ps1 | head -10
fi

# 更新 tools/ 目錄下所有 .md 檔案
find tools -name "*.md" -type f -exec sed -i "s/${OLD_VER_V}/${NEW_VER_V}/g" {} \;
find tools -name "*.md" -type f -exec sed -i "s/AISDLC_${OLD_VER_V}/AISDLC_${NEW_VER_V}/g" {} \;

echo ""
echo "=== 驗證 tools/ 版本號 ==="
echo "舊版本殘留:"
grep -r "${OLD_VER}" tools/ 2>/dev/null | wc -l  # 應為 0
```

**檢查點**:
- [ ] tools/init_project.sh DEFAULT_VERSION 已更新 ← ⚠️ 執行驗證: `grep "DEFAULT_VERSION" tools/init_project.sh`
- [ ] tools/init_project.ps1 $DEFAULT_VERSION 已更新 ← ⚠️ 執行驗證: `grep "DEFAULT_VERSION\|DefaultVersion" tools/init_project.ps1`
- [ ] tools/ 目錄下所有 .md 檔案版本號已更新
- [ ] 無 0.09 殘留 ← ⚠️ 必須驗證

**為什麼重要**:
- init_project.sh/ps1 是使用者建立新專案時的入口腳本
- 如果 DEFAULT_VERSION 未更新，使用者會建立舊版本的目錄結構
- 這是最容易影響終端用戶的錯誤

---

### 3.6.4 修正 SOP 檔名引用 🆕

> **🔴🔴🔴 極度重要！（2026-03-20 新增）🔴🔴🔴**
>
> **此問題在 v0.09→v0.10 實際升版中被發現！**
>
> **問題描述**: Stage 2.1 將 `AISDLC_v0.10_UPGRADE_SOP.md` 改名為 `AISDLC_v0.11_UPGRADE_SOP.md`，
> 但 Stage 3 全域替換 v0.09→v0.10 後，檔案內容中的 `AISDLC_v0.10_UPGRADE_SOP.md` 引用指向了不存在的檔案。
>
> **根本原因**: SOP 檔名使用「下一版本」(N+1)，全域替換只處理 OLD→NEW，無法處理 NEW→NEXT

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== 修正 SOP 檔名引用 ==="

# 全域替換後，AISDLC_${NEW_VER_V}_UPGRADE_SOP.md 是錯誤引用
# 正確引用應該是 AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md
WRONG_REF="AISDLC_${NEW_VER_V}_UPGRADE_SOP"
RIGHT_REF="AISDLC_${NEXT_VER_V}_UPGRADE_SOP"

echo "錯誤引用: ${WRONG_REF}"
echo "正確引用: ${RIGHT_REF}"

# 修正所有 .md 檔案中的 SOP 檔名引用
find . -name "*.md" -type f -exec sed -i "s/${WRONG_REF}/${RIGHT_REF}/g" {} \;

# 驗證
echo ""
echo "=== 驗證 SOP 檔名引用 ==="
echo "錯誤引用殘留:"
grep -r "${WRONG_REF}" . --include="*.md" 2>/dev/null | wc -l  # 應為 0
echo "正確引用數量:"
grep -r "${RIGHT_REF}" . --include="*.md" 2>/dev/null | wc -l  # 應 > 0
```

**檢查點**:
- [ ] 無 `AISDLC_${NEW_VER_V}_UPGRADE_SOP` 錯誤引用 ← ⚠️ 執行驗證
- [ ] 正確引用 `AISDLC_${NEXT_VER_V}_UPGRADE_SOP` 數量 > 0 ← ⚠️ 執行驗證

---

### 3.6.5 修正版本轉換自引用 🆕

> **🔴🔴🔴 極度重要！（2026-03-20 新增）🔴🔴🔴**
>
> **此問題在 v0.09→v0.10 實際升版中被發現！**
>
> **問題描述**: SOP 模板中存在 `v0.09→v0.09` 的版本轉換描述（表示「本版本→下一版本」），
> 全域替換 v0.09→v0.10 後，兩邊都被替換為 v0.10，產生 `v0.10→v0.10` 的自引用。
>
> **影響**: CheckList、UPGRADE_SOP、FILE_DIRECTORY_RULES.md、README.md 等多個檔案

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== 修正版本轉換自引用 ==="

# 修正 v0.10→v0.10 為 v0.10→v0.11（使用多種分隔符格式）
find . -name "*.md" -type f -exec sed -i "s/${NEW_VER_V}→${NEW_VER_V}/${NEW_VER_V}→${NEXT_VER_V}/g" {} \;
find . -name "*.md" -type f -exec sed -i "s/${NEW_VER_V} → ${NEW_VER_V}/${NEW_VER_V} → ${NEXT_VER_V}/g" {} \;
find . -name "*.md" -type f -exec sed -i "s/${NEW_VER_V}->${NEW_VER_V}/${NEW_VER_V}->${NEXT_VER_V}/g" {} \;

# 驗證
echo ""
echo "=== 驗證版本轉換 ==="
echo "自引用殘留:"
grep -r "${NEW_VER_V}→${NEW_VER_V}\|${NEW_VER_V} → ${NEW_VER_V}" . --include="*.md" 2>/dev/null | wc -l  # 應為 0
echo "正確轉換數量:"
grep -r "${NEW_VER_V}→${NEXT_VER_V}\|${NEW_VER_V} → ${NEXT_VER_V}" . --include="*.md" 2>/dev/null | wc -l  # 應 > 0
```

**檢查點**:
- [ ] 無 `v0.10→v0.10` 自引用殘留 ← ⚠️ 執行驗證
- [ ] 正確的 `v0.10→v0.11` 轉換數量 > 0 ← ⚠️ 執行驗證

---

### 3.7 版本號更新完整性驗證

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 全域搜尋殘留的 v0.09
echo "=== 檢查 v0.09 殘留 ==="
grep -r "v0.09" . --exclude-dir=build --exclude="*.backup" | wc -l

# 如果發現殘留，列出詳細位置
grep -rn "v0.09" . --exclude-dir=build --exclude="*.backup" | head -20
```

**檢查點**:
- [ ] 無 v0.09 殘留 (除了 build/ 和 backup_en/ 目錄) ← ⚠️ 執行驗證: `grep -r "v0.09" . --exclude-dir=build --exclude-dir=backup_en | wc -l`（應為 0）
- [ ] 所有核心文檔版本號 = v0.10 ← ⚠️ 執行驗證: `grep "version:" *.md | head -5`
- [ ] 所有 Agents 版本號 = 0.10 ← ⚠️ 執行驗證: `grep -r "version:" agent/ | grep "0.10" | wc -l`（應 >= 21）
- [ ] 所有 Workflows 版本號 = 0.10 ← ⚠️ 執行驗證: `grep -r "version:" workflow/ | grep "0.10" | wc -l`（應 >= 8）
- [ ] 所有模板版本號 = 0.10 ← ⚠️ 執行驗證: `grep -r "version:" docs_template/ | grep "0.10" | wc -l`（應 >= 20）
- [ ] 所有場景 SOP 版本號 = 0.10 ← ⚠️ 執行驗證: `grep -r "version:" scenarios/ | grep "0.10" | wc -l`（應 >= 9）

---

## 階段 4: 內容調整與修正

### 4.1 更新 CLAUDE.md Latest Version

**執行命令**:

```bash
# 確保在專案根目錄
cd ${BASE_DIR}

# 更新 CLAUDE.md 的 Latest Version 指向
sed -i "s/Latest Version.*v0.09/Latest Version: v0.10/g" CLAUDE.md
sed -i "s/Current Version.*v0.09/Current Version: v0.10/g" CLAUDE.md
sed -i "s/AISDLC_v0.09/AISDLC_v0.10/g" CLAUDE.md
sed -i "s/v0\.09/v0.10/g" CLAUDE.md

# 🔴 重要（2026-03-20 新增）: 清理 CLAUDE.md 中更舊版本的引用
# 原因：CLAUDE.md 可能包含 v0.07, v0.06 等舊版本引用，Stage 3 不會處理這些
echo "=== 清理 CLAUDE.md 舊版本引用 ==="
for OLD_V in v0.01 v0.02 v0.03 v0.04 v0.05 v0.06 v0.07; do
  COUNT=$(grep -c "AISDLC_${OLD_V}" CLAUDE.md 2>/dev/null || echo "0")
  if [ "$COUNT" -gt 0 ]; then
    echo "替換 AISDLC_${OLD_V} (${COUNT} 處)"
    sed -i "s/AISDLC_${OLD_V}/AISDLC_v0.10/g" CLAUDE.md
  fi
done

# 驗證
grep -n "Latest Version\|Current Version" CLAUDE.md
echo "舊版本殘留檢查:"
grep -c "v0\.0[1-8]" CLAUDE.md || echo "0 處殘留"
```

**檢查點**:
- [ ] CLAUDE.md Latest Version = v0.10 ← ⚠️ 執行驗證: `grep "Latest Version\|Current Version" CLAUDE.md`
- [ ] CLAUDE.md 指向 AISDLC_v0.10 ← ⚠️ 執行驗證: `grep -c "AISDLC_v0.09" CLAUDE.md`（應為 0）
- [ ] CLAUDE.md 無舊版本殘留 ← ⚠️ 執行驗證: `grep -c "v0\.0[1-8]" CLAUDE.md`（應為 0，除例外說明）

---

### 4.2 更新 FILE_DIRECTORY_RULES.md 變更記錄

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 在 FILE_DIRECTORY_RULES.md 頂部新增升版記錄
# (需要手動編輯，新增日期和變更摘要)
```

**手動新增內容範例**:

```markdown
- ✅ YYYY-MM-DD: **升版至 v0.10** - 從 v0.09 完整升版，所有檔案版本號已更新至 v0.10
  - 新增文檔: guides/AISDLC_ID_Naming_Convention.md, guides/Document_Quality_Checklist.md
  - 重大更新: scenarios/greenfield/SOP.md (9 階段更新)
  - 重大更新: workflow/core/api-specification.md (新增 API 變更影響分析)
  - 重大更新: workflow/core/consistency-check.md (新增版本同步規則)
```

**檢查點**:
- [ ] FILE_DIRECTORY_RULES.md 已新增 v0.10 升版記錄
- [ ] 變更記錄包含日期和主要變更項目

---

### 4.3 檢查並更新 README.md

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 檢視 README.md 內容
cat README.md | head -50
```

**手動檢查項目**:
- [ ] README.md 版本資訊 = v0.10
- [ ] 主要特性描述是否需要更新 (如有新增功能)
- [ ] 目錄結構是否需要更新 (如有新增目錄)

---

### 4.4 檢查所有目錄的 README.md

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 找出所有 README.md
find . -name "README.md" -type f | sort
```

**手動檢查每個 README.md**:
- [ ] 版本引用正確
- [ ] 檔案清單與實際一致
- [ ] 路徑引用正確

---

## 階段 5: 驗證階段

**🚫 禁止委派**: 本階段禁止使用 Task 工具，必須由主 Agent 直接執行

**執行證據要求**:
- 必須創建檢查點檔案: `build/logs/stage_5.checkpoint`
- 統計數據必須來自真實命令輸出，不可編造
- 必須將統計結果記錄到: `build/logs/statistics.log`

> **🔴🔴🔴 強制執行提醒 🔴🔴🔴**
>
> **本階段包含 8 個驗證步驟，每一個都必須執行！**
>
> **特別注意最容易遺漏的步驟**：
> - ⚠️ **步驟 5.8 (統計比較)** - 必須執行完整的統計函數和差異分析表格！
>   - 包含 count_stats() 函數定義和執行
>   - 包含完整的差異分析表格填寫
>   - 包含差異原因釐清
> - ⚠️ **所有檢查點必須逐項確認** - 不可假設「應該沒問題」
> - ⚠️ **發現任何異常立即停止** - 不可繼續執行直到問題解決
>
> **執行前承諾**：
> - [ ] 我承諾執行本階段所有 8 個驗證步驟
> - [ ] 我承諾執行完整的統計比較機制（5.8）
> - [ ] 我承諾確認所有檢查點並記錄結果
> - [ ] 我承諾發現問題立即停止並修正
>
> **如果無法做出以上承諾，請勿開始本階段！**

---

### 5.1 目錄結構驗證

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 列出完整目錄結構
tree -L 2
```

**檢查點**:
- [ ] agent/ 目錄存在 (含 core/, specialized/)
- [ ] workflow/ 目錄存在 (含 core/, supplementary/)
- [ ] docs_template/ 目錄存在 (含 prd/, frd/, srd/, tests/, core/, scenario_specific/)
- [ ] scenarios/ 目錄存在 (含 10 個場景目錄)
- [ ] guides/ 目錄存在
- [ ] prompts/ 目錄存在
- [ ] build/ 目錄存在 (含 logs/, planning/, reports/)

---

### 5.2 核心文檔驗證

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 驗證核心文檔
ls -lh *.md
```

**檢查點**:
- [ ] AISDLC_INIT.md 存在
- [ ] FILE_DIRECTORY_RULES.md 存在
- [ ] README.md 存在
- [ ] DEVELOPMENT_DIRECTORY_STRUCTURE.md 存在
- [ ] AISDLC_UPGRADE_SOP_CheckList.md 存在
- [ ] **AISDLC_v0.11_UPGRADE_SOP.md 存在** ← 極易遺漏！

---

### 5.3 Agents 驗證

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 統計 Agent 數量
echo "=== Agent 數量驗證 ==="
echo "Core Agents: $(find agent/core -name "*-zh.yaml" | wc -l)"  # 應該是 7
echo "Specialized Agents: $(find agent/specialized -name "*.yaml" | wc -l)"  # 應該 >= 14

# 驗證版本號
grep -r "version: \"0.10\"" agent/ | wc -l  # 應該 > 20
```

**檢查點**:
- [ ] Core Agents = 7 個
- [ ] Specialized Agents >= 14 個
- [ ] 所有 Agents 版本號 = 0.10

---

### 5.4 Workflows 驗證

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 統計 Workflow 數量
echo "=== Workflow 數量驗證 ==="
find workflow -name "*.md" -type f | wc -l  # 應該 >= 17

# 驗證核心 Workflows
ls -lh workflow/core/*.md
```

**檢查點**:
- [ ] 核心 Workflows >= 8 個
- [ ] requirements-extraction.md 存在
- [ ] api-specification.md 存在
- [ ] consistency-check.md 存在
- [ ] 所有 Workflows 版本號 = 0.10

---

### 5.5 Scenarios 驗證

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 統計場景數量
echo "=== 場景數量驗證 ==="
ls -d scenarios/*/ | wc -l  # 應該是 10

# 列出所有場景
ls -lh scenarios/
```

**檢查點**:
- [ ] greenfield/ 存在 (含 SOP.md, Workflow.md, checklists/)
- [ ] brownfield/ 存在
- [ ] refactoring/ 存在
- [ ] integration/ 存在
- [ ] testing/ 存在
- [ ] security/ 存在
- [ ] performance/ 存在
- [ ] devops/ 存在
- [ ] documentation/ 存在
- [ ] migration/ 存在
- [ ] 總計 10 個場景

---

### 5.6 文檔模板驗證

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 統計模板數量
echo "=== 文檔模板數量驗證 ==="
find docs_template -name "*Template.md" | wc -l  # 應該 >= 20
```

**檢查點**:
- [ ] PRD 模板存在
- [ ] FRD 模板存在
- [ ] SRD 模板存在
- [ ] API 模板存在
- [ ] AT 模板存在
- [ ] CI/CD 模板存在 (scenario_specific/devops/)

---

### 5.7 版本號一致性最終驗證

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 全域搜尋 v0.09 殘留
echo "=== v0.09 殘留檢查 ==="
grep -r "v0.09" . --exclude-dir=build --exclude="*.backup" | wc -l  # 應該是 0

# 如果有殘留，列出位置
if [ $(grep -r "v0.09" . --exclude-dir=build --exclude="*.backup" | wc -l) -gt 0 ]; then
  echo "⚠️ 發現 v0.09 殘留，請手動修正："
  grep -rn "v0.09" . --exclude-dir=build --exclude="*.backup"
fi
```

**檢查點**:
- [ ] 無 v0.09 殘留 (除了 build/、backup_en/ 和 *.backup)
- [ ] 所有版本號 = v0.10 或 0.10

---

### 5.8 目錄與檔案數統計比較驗證 🆕

> **🔴🔴🔴 極度重要 - 本步驟絕對不可遺漏！🔴🔴🔴**
>
> **這是升版完整性的最終驗證，確保所有應拷貝的內容都已正確拷貝，無遺漏。**

#### ⚠️ 統計數據必須來自真實命令 - 絕不可編造

**❌ 錯誤示範（v0.09 → v0.09 的慘痛教訓 - 2025-12-06）**:

Task Agent 回報的**假數據**:
| 項目 | v0.09 | v0.09 | 差異 | 狀態 |
|------|-------|-------|------|------|
| 目錄數 | 64 | 64 | 0 | ✅ 一致 |
| 檔案數 | 167 | 167 | 0 | ✅ 一致 |

實際真相:
| 項目 | v0.09 | v0.09 | 差異 | 狀態 |
|------|-------|-------|------|------|
| 目錄數 | 79 | 74 | -5 | ⚠️ 正常（排除 archive/） |
| 檔案數 | 242 | 171 | -71 | ⚠️ 正常（build/ 未拷貝） |

**這就是為什麼絕對不能信任沒有證據的統計報告！**

> **🚫 歷史教訓總結**：
> - **2025-12-05**: 本步驟被完全遺漏
> - **2025-12-06**: Task Agent 產生了完全虛假的統計數據
> - **根本原因**: Task Agent 沒有真實執行命令，只編造了看起來合理的數字
> - **解決方案**: 必須使用真實命令輸出，記錄到檢查點檔案，並人工確認

---

**✅ 正確執行方式** (簡化版 - 快速驗證):

如果要快速驗證目錄和檔案數，使用以下簡化步驟：

#### 步驟 1: 執行實際命令並記錄輸出

> **🔴 Bash 工具限制**: 避免使用 tee 和複雜管道，改用簡單的 echo 和輸出重定向

```bash
# 🔴 簡化版：使用基本命令，避免 tee 和複雜管道
cd ${BASE_DIR}

# v0.09 統計
echo "=== v0.09 統計 ===" > AISDLC_${OLD_VERSION}/build/logs/statistics.log
OLD_DIRS=$(find AISDLC_${OLD_VERSION} -type d | wc -l | tr -d ' ')
OLD_FILES=$(find AISDLC_${OLD_VERSION} -type f | wc -l | tr -d ' ')
echo "目錄數: $OLD_DIRS" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log
echo "檔案數: $OLD_FILES" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log

# v0.10 統計
echo "" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log
echo "=== v0.10 統計 ===" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log
NEW_DIRS=$(find AISDLC_${NEW_VERSION} -type d | wc -l | tr -d ' ')
NEW_FILES=$(find AISDLC_${NEW_VERSION} -type f | wc -l | tr -d ' ')
echo "目錄數: $NEW_DIRS" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log
echo "檔案數: $NEW_FILES" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log

# 差異分析
echo "" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log
echo "=== 差異分析 ===" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log
DIR_DIFF=$((OLD_DIRS - NEW_DIRS))
FILE_DIFF=$((OLD_FILES - NEW_FILES))
echo "目錄差異: $DIR_DIFF" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log
echo "檔案差異: $FILE_DIFF" >> AISDLC_${OLD_VERSION}/build/logs/statistics.log

# 顯示統計結果
echo "統計完成，結果已記錄到 build/logs/statistics.log"
cat AISDLC_${OLD_VERSION}/build/logs/statistics.log
```

#### 步驟 2: 記錄到檢查點檔案

```bash
{
  echo "STAGE=5.8"
  echo "COMPLETED=true"
  echo "TIMESTAMP=$(date)"
  echo "OLD_DIRS=$OLD_DIRS"
  echo "OLD_FILES=$OLD_FILES"
  echo "NEW_DIRS=$NEW_DIRS"
  echo "NEW_FILES=$NEW_FILES"
  echo "DIR_DIFF=$DIR_DIFF"
  echo "FILE_DIFF=$FILE_DIFF"
} > build/logs/stage_5_8.checkpoint

# 驗證檢查點檔案已創建
if [ -f "build/logs/stage_5_8.checkpoint" ]; then
  echo "✅ 檢查點檔案已創建"
  cat build/logs/stage_5_8.checkpoint
else
  echo "❌ 檢查點檔案創建失敗"
  exit 1
fi
```

#### 步驟 3: 生成報告（使用真實數據）

**⚠️ 數字必須來自檢查點檔案，不可自行編造**

```bash
# 讀取檢查點檔案（確保使用真實數據）
source build/logs/stage_5_8.checkpoint

# 生成報告
echo ""
echo "### 簡化統計比較結果"
echo ""
echo "| 項目 | v0.09 | v0.10 | 差異 | 分析 |"
echo "|------|-------|-------|------|------|"
echo "| 目錄數 | $OLD_DIRS | $NEW_DIRS | $DIR_DIFF | [需人工分析是否合理] |"
echo "| 檔案數 | $OLD_FILES | $NEW_FILES | $FILE_DIFF | [需人工分析是否合理] |"
```

#### 步驟 4: 人工確認（必須）

**使用者必須親自確認**:
1. 檢視 `build/logs/statistics.log` - 確認是真實命令輸出，不是編造的
2. 檢視 `build/logs/stage_5_8.checkpoint` - 確認數字合理
3. 分析差異是否符合預期：
   - 目錄減少 5 個左右: ✅ 正常（排除了 backup_en/, archive/ 等）
   - 檔案減少 70 個左右: ✅ 正常（build/ 目錄 40+ 檔案未拷貝 + archive 檔案）
   - 如果差異是 0: ⚠️ 極度可疑，可能是假數據

**⚠️ 如果統計數據看起來過於完美（差異為 0），很可能是編造的！**

---

> **✅ 強制執行要求**：
> - 必須執行簡化版統計命令（見步驟 1）
> - 必須統計源版本 (v0.09) 和目標版本 (v0.10)
> - 必須填寫差異分析表格
> - 必須釐清每個差異的原因
>
> **⚠️ 執行承諾檢查**：
> - [ ] 我承諾執行簡化版統計命令（步驟 1 已提供）
> - [ ] 我承諾填寫差異分析表格
> - [ ] 我承諾釐清所有差異原因
> - [ ] 我理解跳過本步驟將導致升版不完整
>
> **🔴 重要**: 完整的 count_stats() 函數（約 100 行，含嵌套 if 語句）已移至 **5.8.2 進階驗證（可選）**。
> 基本升版只需執行步驟 1 的簡化版統計。

#### 5.8.1 快速統計驗證（必須執行）

> **🔴 重要**: 這是簡化版統計，已在步驟 1 執行。這裡只需確認已執行。

**檢查點**:
- [ ] 已執行步驟 1 的簡化版統計命令
- [ ] 已生成 `build/logs/statistics.log` 檔案
- [ ] 已生成 `build/logs/stage_5_8.checkpoint` 檔案
- [ ] 差異數字已確認合理

**如尚未執行步驟 1，請立即返回執行！**

---

#### 5.8.2 進階統計驗證（可選）

> **⚠️ Bash 工具限制警告**: 以下 count_stats() 函數包含約 100 行嵌套 if 語句和 find 命令。
> Bash 工具可能無法正確解析，建議手動執行或使用簡化版統計（步驟 1）。
>
> **此步驟為可選**，僅在需要詳細的目錄級統計時執行。

**執行命令**:

```bash
# ⚠️ 此函數僅供進階使用者手動執行，Bash 工具可能無法解析

# 切換到 BASE_DIR
cd ${BASE_DIR}

echo "========================================="
echo "目錄與檔案數統計比較驗證 (進階版)"
echo "========================================="
echo ""

# 定義統計函數
count_stats() {
  local version_dir=$1
  local label=$2

  echo "=== ${label} ==="
  echo "版本目錄: ${version_dir}"
  echo ""

  # 統計各主要目錄的子目錄數和檔案數
  echo "【主要目錄統計】"

  # agent/
  if [ -d "${version_dir}/agent" ]; then
    agent_dirs=$(find "${version_dir}/agent" -type d | wc -l | tr -d ' ')
    agent_files=$(find "${version_dir}/agent" -type f | wc -l | tr -d ' ')
    echo "agent/           : ${agent_dirs} 個目錄, ${agent_files} 個檔案"
  fi

  # workflow/
  if [ -d "${version_dir}/workflow" ]; then
    workflow_dirs=$(find "${version_dir}/workflow" -type d | wc -l | tr -d ' ')
    workflow_files=$(find "${version_dir}/workflow" -type f | wc -l | tr -d ' ')
    echo "workflow/        : ${workflow_dirs} 個目錄, ${workflow_files} 個檔案"
  fi

  # docs_template/
  if [ -d "${version_dir}/docs_template" ]; then
    docs_template_dirs=$(find "${version_dir}/docs_template" -type d | wc -l | tr -d ' ')
    docs_template_files=$(find "${version_dir}/docs_template" -type f | wc -l | tr -d ' ')
    echo "docs_template/   : ${docs_template_dirs} 個目錄, ${docs_template_files} 個檔案"
  fi

  # scenarios/
  if [ -d "${version_dir}/scenarios" ]; then
    scenarios_dirs=$(find "${version_dir}/scenarios" -type d | wc -l | tr -d ' ')
    scenarios_files=$(find "${version_dir}/scenarios" -type f | wc -l | tr -d ' ')
    echo "scenarios/       : ${scenarios_dirs} 個目錄, ${scenarios_files} 個檔案"
  fi

  # guides/
  if [ -d "${version_dir}/guides" ]; then
    guides_dirs=$(find "${version_dir}/guides" -type d | wc -l | tr -d ' ')
    guides_files=$(find "${version_dir}/guides" -type f | wc -l | tr -d ' ')
    echo "guides/          : ${guides_dirs} 個目錄, ${guides_files} 個檔案"
  fi

  # prompts/
  if [ -d "${version_dir}/prompts" ]; then
    prompts_dirs=$(find "${version_dir}/prompts" -type d | wc -l | tr -d ' ')
    prompts_files=$(find "${version_dir}/prompts" -type f | wc -l | tr -d ' ')
    echo "prompts/         : ${prompts_dirs} 個目錄, ${prompts_files} 個檔案"
  fi

  # tools/
  if [ -d "${version_dir}/tools" ]; then
    tools_dirs=$(find "${version_dir}/tools" -type d | wc -l | tr -d ' ')
    tools_files=$(find "${version_dir}/tools" -type f | wc -l | tr -d ' ')
    echo "tools/           : ${tools_dirs} 個目錄, ${tools_files} 個檔案"
  fi

  # docs/
  if [ -d "${version_dir}/docs" ]; then
    docs_dirs=$(find "${version_dir}/docs" -type d | wc -l | tr -d ' ')
    docs_files=$(find "${version_dir}/docs" -type f | wc -l | tr -d ' ')
    echo "docs/            : ${docs_dirs} 個目錄, ${docs_files} 個檔案"
  fi

  # releases/ (不應拷貝，僅統計源版本)
  if [ -d "${version_dir}/releases" ]; then
    releases_dirs=$(find "${version_dir}/releases" -type d | wc -l | tr -d ' ')
    releases_files=$(find "${version_dir}/releases" -type f | wc -l | tr -d ' ')
    echo "releases/        : ${releases_dirs} 個目錄, ${releases_files} 個檔案 (❌ 不拷貝)"
  fi

  # build/ (不應拷貝，僅統計源版本)
  if [ -d "${version_dir}/build" ]; then
    build_dirs=$(find "${version_dir}/build" -type d | wc -l | tr -d ' ')
    build_files=$(find "${version_dir}/build" -type f | wc -l | tr -d ' ')
    echo "build/           : ${build_dirs} 個目錄, ${build_files} 個檔案 (❌ 不拷貝)"
  fi

  echo ""
  echo "【根目錄核心文檔統計】"
  root_md_files=$(ls -1 "${version_dir}"/*.md 2>/dev/null | wc -l | tr -d ' ')
  echo "根目錄 .md 檔案: ${root_md_files} 個"
  if [ ${root_md_files} -gt 0 ]; then
    ls -1 "${version_dir}"/*.md | sed 's|.*/||' | sed 's/^/  - /'
  fi

  echo ""
  echo "【總計 (排除 build/ 和 releases/)】"
  # ⚠️ 必須執行並記錄實際輸出 - 禁止編造數字！
  total_dirs=$(find "${version_dir}" -type d \
    -not -path "*/build/*" \
    -not -path "*/releases/*" | wc -l | tr -d ' ')
  total_files=$(find "${version_dir}" -type f \
    -not -path "*/build/*" \
    -not -path "*/releases/*" | wc -l | tr -d ' ')
  echo "總目錄數: ${total_dirs}"
  echo "總檔案數: ${total_files}"

  echo ""
  echo "⚠️  以上數字必須記錄到差異分析表格中！"
  echo "-----------------------------------------"
  echo ""
}

# 統計源版本
count_stats "AISDLC_${OLD_VERSION}" "源版本 (${OLD_VERSION})"

# 統計目標版本
count_stats "AISDLC_${NEW_VERSION}" "目標版本 (${NEW_VERSION})"

echo ""
echo "========================================="
echo "⚠️⚠️⚠️  重要提醒  ⚠️⚠️⚠️"
echo "========================================="
echo ""
echo "請將以上統計結果記錄到下方的「差異分析表格」中！"
echo ""
echo "【禁止行為】:"
echo "  ❌ 不可跳過填寫表格"
echo "  ❌ 不可編造數字"
echo "  ❌ 不可假設「應該沒問題」"
echo ""
echo "【正確做法】:"
echo "  ✅ 逐項填寫每個目錄的實際數字"
echo "  ✅ 計算並記錄差異值"
echo "  ✅ 釐清每個差異的原因"
echo ""
echo "========================================="
```

**說明**:
- **統計範圍**: 所有應拷貝的目錄（排除 build/ 和 releases/）
- **統計項目**: 每個主要目錄的子目錄數和檔案數
- **輸出格式**: 清晰的比較表格，便於快速發現差異
- **⚠️ 強制要求**: 必須將統計結果填入下方差異分析表格，不可跳過或編造數字

---

#### 5.8.2 差異分析與釐清

執行完統計後，請根據以下指引分析差異：

**📋 差異分析表格**:

請將統計結果填入以下表格，並確認差異原因：

| 目錄 | 源版本 (v0.09) | 目標版本 (v0.10) | 差異 | 差異原因 | 是否正常 |
|------|---------------|-----------------|------|---------|---------|
| **agent/** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **workflow/** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **docs_template/** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **scenarios/** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **guides/** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **prompts/** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **tools/** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **docs/** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **根目錄 .md** | ___ 個檔案 | ___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |
| **總計** | ___ 個目錄<br>___ 個檔案 | ___ 個目錄<br>___ 個檔案 | ±___ | | [ ] 是 [ ] 否 |

---

**🔍 常見差異原因與釐清指引**:

1. **差異 = 0 (完全相同)** ✅
   - **狀態**: 正常
   - **說明**: 該目錄內容完全一致，無新增或刪除

2. **目標版本 > 源版本 (檔案增加)** ⚠️
   - **可能原因**:
     - v0.09 新增了文檔或功能
     - 新建了 build/ 子目錄（正常）
     - 新建了 README.md 或其他說明文件（正常）
   - **釐清方式**:
     ```bash
     # 找出新增的檔案
     cd ${BASE_DIR}
     comm -13 \
       <(cd AISDLC_${OLD_VERSION} && find . -type f | sort) \
       <(cd AISDLC_${NEW_VERSION} && find . -type f | sort)
     ```
   - **判斷**: 檢查新增檔案是否符合 v0.09 預期變更

3. **目標版本 < 源版本 (檔案減少)** 🔴
   - **可能原因**:
     - 拷貝遺漏（**錯誤，需修正！**）
     - archive/ 目錄正確排除（正常）
     - releases/ 和 build/ 未拷貝（正常，符合規則）
   - **釐清方式**:
     ```bash
     # 找出遺漏的檔案
     cd ${BASE_DIR}
     comm -23 \
       <(cd AISDLC_${OLD_VERSION} && find . -type f \
          -not -path "*/build/*" \
          -not -path "*/releases/*" \
          -not -path "*/archive/*" | sort) \
       <(cd AISDLC_${NEW_VERSION} && find . -type f \
          -not -path "*/build/*" \
          -not -path "*/releases/*" | sort)
     ```
   - **判斷**:
     - 如果遺漏的是 archive/ 檔案 → ✅ 正常
     - 如果遺漏的是應拷貝的檔案 → 🔴 錯誤，需補拷貝

4. **根目錄 .md 檔案數差異** ⚠️
   - **預期差異**:
     - 源版本: 可能包含臨時檔案（如 `*_改善計畫報告.md`）
     - 目標版本: 應僅有 6 個核心文檔
   - **正常範圍**: 目標版本應 = 6 個核心文檔
     - `AISDLC_INIT.md`
     - `README.md`
     - `FILE_DIRECTORY_RULES.md`
     - `AISDLC_v0.11_UPGRADE_SOP.md`
     - `AISDLC_UPGRADE_SOP_CheckList.md`
     - `DEVELOPMENT_DIRECTORY_STRUCTURE.md`
   - **釐清方式**: 檢查根目錄 .md 檔案列表
     ```bash
     echo "源版本根目錄 .md 檔案:"
     ls -1 AISDLC_${OLD_VERSION}/*.md
     echo ""
     echo "目標版本根目錄 .md 檔案:"
     ls -1 AISDLC_${NEW_VERSION}/*.md
     ```

---

**檢查點**:
- [ ] 已執行統計腳本，獲得源版本和目標版本的完整統計數據 ← ⚠️ 必須執行 count_stats() 函數
- [ ] 已填寫差異分析表格 ← ⚠️ 必須填寫每一行的實際數字，不可留空
  - agent/ 差異: _____ ← 必須填寫
  - workflow/ 差異: _____ ← 必須填寫
  - docs_template/ 差異: _____ ← 必須填寫
  - scenarios/ 差異: _____ ← 必須填寫
  - guides/ 差異: _____ ← 必須填寫
  - prompts/ 差異: _____ ← 必須填寫
  - tools/ 差異: _____ ← 必須填寫
  - docs/ 差異: _____ ← 必須填寫
  - 根目錄 .md 差異: _____ ← 必須填寫
  - 總計差異: _____ ← 必須填寫
- [ ] 所有差異都已分析並釐清原因 ← ⚠️ 每個差異都必須有原因說明
- [ ] 檔案減少的差異已確認為正常（archive/ 排除或 build/releases/ 不拷貝）
- [ ] 檔案增加的差異已確認為 v0.09 預期變更
- [ ] 根目錄 .md 檔案數 = 6 個核心文檔
- [ ] 無非預期的拷貝遺漏

**如發現拷貝遺漏，請返回階段 2 補拷貝對應檔案！**

---

> **🔴🔴🔴 強制檢查點：進入階段 6 前必讀 🔴🔴🔴**
>
> **⚠️ 歷史教訓 (2025-12-09)**:
> - v0.09→v0.09 升版時，Agent 在完成階段 5 後直接跳過階段 6 和階段 7
> - 導致發布包未創建，使用者非常不滿
> - 根本原因：沒有強制檢查點防止跳階段
>
> **📋 進入階段 6 前必須確認**:
> - [ ] 我已完成階段 5 的所有驗證步驟（5.1-5.8）
> - [ ] 我已確認所有檢查點都已勾選
> - [ ] 我承諾不跳過階段 6（歸檔與清理）
> - [ ] 我承諾不跳過階段 7（發布準備）← **極度重要！**
> - [ ] 我理解跳過階段 7 將導致發布包缺失，使用者無法使用
>
> **🚨 如果無法做出以上承諾，請立即停止並報告！**
>
> **✅ 確認後才能繼續執行階段 6**

---

## 階段 6: 歸檔與清理

> **🔴 重要**: 本階段為可選，但如執行必須完整執行所有步驟。

### 6.1 歸檔舊版本的 build/ 目錄

**執行命令**:

```bash
# 確保在 BASE_DIR
cd ${BASE_DIR}

# 創建歸檔目錄
mkdir -p AISDLC_${OLD_VERSION}/build/archive

# 將 build/ 內容歸檔 (如果有)
if [ -d "AISDLC_${OLD_VERSION}/build/planning/active" ]; then
  mv AISDLC_${OLD_VERSION}/build/planning/active/* AISDLC_${OLD_VERSION}/build/archive/ 2>/dev/null || true
fi

# 驗證
ls -lh AISDLC_${OLD_VERSION}/build/archive/
```

**檢查點**:
- [ ] 舊版本 build/planning/active/ 內容已歸檔
- [ ] 舊版本 build/archive/ 包含歷史文檔

---

### 6.2 清理備份檔案

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 刪除可能的 .backup 檔案
find . -name "*.backup" -type f -delete

# 驗證
find . -name "*.backup" | wc -l  # 應該是 0
```

**檢查點**:
- [ ] 無 *.backup 檔案殘留

---

> **🔴🔴🔴 強制檢查點：進入階段 7 前必讀 🔴🔴🔴**
>
> **⚠️ 歷史慘痛教訓 (2025-12-09)**:
> - **問題**: v0.09→v0.09 升版時，Agent 完全跳過階段 7
> - **後果**: 發布包 `releases/AISDLC_v0.09_release_2025-12-05.tar.gz` 未創建
> - **使用者反應**: 非常憤怒，連續使用 6 個「又」字表達不滿
> - **根本原因**: 缺少強制執行機制
>
> **🚨 階段 7 是升版的最關鍵步驟之一！**
> - 如果沒有發布包，整個升版工作將無法交付使用者
> - 發布包是唯一可分發的版本形式
> - 跳過此階段等同於升版失敗
>
> **📋 進入階段 7 前必須確認**:
> - [ ] 我已完成階段 1-5 的所有步驟
> - [ ] 我已確認階段 6 已執行或明確標記為跳過
> - [ ] 我承諾執行階段 7 的所有步驟（7.1-7.4）
> - [ ] 我承諾創建發布包 `AISDLC_v0.10_release_YYYY-MM-DD.tar.gz`
> - [ ] 我承諾驗證發布包已成功創建並可存取
> - [ ] 我理解如果跳過階段 7，使用者將無法使用升版成果
>
> **🚨 如果無法做出以上承諾，請立即停止並報告，絕不可繼續！**
>
> **✅ 只有完成以上所有確認後，才能執行階段 7**

---

## 階段 7: 發布準備

> **🔴🔴🔴 本階段絕對不可跳過！🔴🔴🔴**

**🚫 禁止委派**: 本階段禁止使用 Task 工具，必須由主 Agent 直接執行

**執行證據要求**:
- 必須創建檢查點檔案: `build/logs/stage_7.checkpoint`
- 檢查點必須包含: 發布包路徑、檔案大小、SHA256 校驗和
- 必須驗證發布包檔案真實存在

---

### 7.1 創建發布包

**目的**: 在版本目錄內創建完整的發布壓縮包，供分發和存檔使用。

> **🔴🔴🔴 重大路徑錯誤修正 🔴🔴🔴**
>
> **此步驟在 v0.09→v0.09 實際升版中發生路徑錯誤！**
>
> **錯誤**: 原指示在 `${BASE_DIR}/releases/` 創建，導致發布包不在版本目錄內
>
> **正確**: 應在 `${BASE_DIR}/AISDLC_${NEW_VERSION}/releases/` 創建
>
> **已修正**: 發布包現在會正確創建在版本目錄內

**執行命令**:

```bash
# 確保在主專案目錄
cd ${BASE_DIR}

echo "=== 創建發布包 ==="

# 🔴 修正：在版本目錄內創建 releases/
mkdir -p "AISDLC_${NEW_VERSION}/releases"

# 生成發布日期戳記
UPDATE_DATE=$(date +%Y-%m-%d)

# 進入版本目錄，準備創建發布包
cd "AISDLC_${NEW_VERSION}"

# 創建壓縮檔 (排除 build 和臨時檔案)
echo "正在創建發布包..."
tar -czf "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" \
  --exclude="./build" \
  --exclude="./releases" \
  --exclude=".DS_Store" \
  --exclude="*.swp" \
  --exclude="*~" \
  .

# ============================================
# 驗證發布包
# ============================================
echo ""
echo "=== 驗證發布包 ==="

if [ -f "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" ]; then
  echo "✅ 發布包檔案存在"

  # 顯示詳細資訊 ← ⚠️ 必須執行並記錄
  ls -lh "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz"

  # 取得檔案大小
  RELEASE_SIZE=$(ls -lh "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" | awk '{print $5}')
  RELEASE_SIZE_BYTES=$(stat -f%z "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" 2>/dev/null || stat -c%s "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" 2>/dev/null)

  echo ""
  echo "📊 發布包資訊:"
  echo "  檔名: AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz"
  echo "  大小: ${RELEASE_SIZE}"
  echo "  位元組: ${RELEASE_SIZE_BYTES} bytes"

  # 驗證檔案大小是否合理 (應 > 1MB = 1048576 bytes)
  echo ""
  echo "=== 檔案大小驗證 ==="
  if [ "${RELEASE_SIZE_BYTES}" -gt 1048576 ]; then
    echo "✅ 檔案大小正常 (> 1MB)"
  else
    echo "❌ 檔案大小異常！小於 1MB，可能壓縮失敗"
    echo "   實際大小: ${RELEASE_SIZE_BYTES} bytes"
    echo "   請檢查壓縮過程是否有錯誤"
    exit 1
  fi

  # 驗證壓縮檔內容 ← ⚠️ 必須執行
  echo ""
  echo "=== 壓縮檔內容驗證 ==="
  echo "前 20 個檔案/目錄："
  tar -tzf "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" | head -20

  echo ""
  echo "壓縮檔總檔案數："
  tar -tzf "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" | wc -l

else
  echo "❌ 發布包創建失敗，請檢查"
  exit 1
fi

echo ""
echo "✅ 發布包驗證完成"

# 生成 SHA256 校驗和
echo "正在生成 SHA256 校驗和..."
cd releases
shasum -a 256 "AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" > \
  "AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz.sha256"

echo ""
echo "校驗和:"
cat "AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz.sha256"

cd ..
echo "✅ 發布包準備完成"
```

**檢查點**:
- [ ] 🔴 releases/ 目錄已在**版本目錄內**創建 (`AISDLC_${NEW_VERSION}/releases/`) ← ⚠️ 執行驗證: `ls -ld AISDLC_v0.10/releases/`
- [ ] 發布包已創建 ← ⚠️ 執行驗證: `ls -lh AISDLC_v0.10/releases/*.tar.gz`
  - 檔名: `AISDLC_v0.10_release_YYYY-MM-DD.tar.gz`
- [ ] 發布包大小合理 ← ⚠️ 必須記錄實際大小
  - 預期範圍: > 1MB (通常 5-20 MB)
  - 實際大小: _____ KB/MB ← 必須填寫
  - 位元組數: _____ bytes ← 必須填寫
- [ ] 發布包內容驗證通過 ← ⚠️ 執行驗證: `tar -tzf releases/*.tar.gz | wc -l`
  - 總檔案數: _____ 個 ← 必須填寫
  - 內容預覽: 已執行 `tar -tzf ... | head -20`
- [ ] SHA256 校驗和檔案已生成 ← ⚠️ 執行驗證: `ls -lh releases/*.sha256`
- [ ] 校驗和內容正確顯示 ← ⚠️ 執行驗證: `cat releases/*.sha256`

**發布包資訊記錄**:
```
檔名: AISDLC_v0.10_release_YYYY-MM-DD.tar.gz
大小: _____ MB
SHA256: ________________________________
位置: ${BASE_DIR}/AISDLC_${NEW_VERSION}/releases/
```

**說明**:
- 發布包創建在**版本目錄內** (`${BASE_DIR}/AISDLC_${NEW_VERSION}/releases/`)，不是專案根目錄
- 排除 build/ 目錄以減少包大小
- 使用日期戳記便於版本追蹤
- SHA256 校驗和確保檔案完整性

---

### 7.2 創建 CHANGELOG (可選)

如需創建版本更新日誌，在 build/logs/ 創建：

**執行命令**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

# 創建 CHANGELOG (如需要)
cat > build/logs/CHANGELOG_v0.10.md << 'EOF'
# AISDLC v0.10 版本更新日誌

**發布日期**: YYYY-XX-XX
**版本**: v0.10
**升版自**: v0.09

## 主要變更

### 新增功能

1. **統一 ID 命名規範** (`guides/AISDLC_ID_Naming_Convention.md`)
   - 定義 10 種核心 ID 類型
   - 提供 RTM 追溯範例

2. **文檔品質檢查清單** (`guides/Document_Quality_Checklist.md`)
   - 4 大類檢查標準
   - 19 個階段文檔檢查規則

3. **CI/CD Pipeline 配置範本** (`docs_template/scenario_specific/devops/CICD_Pipeline_Template.md`)
   - 支援 4 大平台 (GitHub Actions, GitLab CI, Jenkins, Azure DevOps)
   - 完整的 Blue/Green Deployment 範例

### 重大更新

1. **Greenfield SOP** - 9 個階段全面更新
2. **API Specification Workflow** - 新增 API 變更影響分析機制
3. **Consistency Check Workflow** - 新增版本同步規則
4. **Interaction Analysis Workflow** - 新增快取同步策略

### 檔案變更統計

- 新增檔案: 3 個 (guides + devops template)
- 重大更新: 4 個 (workflows + greenfield SOP)
- 版本號更新: 100+ 個檔案

## 升版影響

- **向後相容性**: 完全相容 v0.09
- **破壞性變更**: 無
- **建議行動**: 建議所有使用者升級至 v0.10 以獲得最新功能

## 已知問題

- 無

EOF

echo "✅ CHANGELOG_v0.10.md 已創建"
```

**檢查點**:
- [ ] build/logs/CHANGELOG_v0.10.md 已創建 (如需要)

---

### 7.3 最終檢查清單

**執行以下完整檢查**:

```bash
# 確保在新版本目錄
cd ${BASE_DIR}
cd AISDLC_${NEW_VERSION}

echo "=== AISDLC v0.09 最終驗證 ==="
echo ""
echo "1. 核心文檔:"
ls *.md | wc -l  # 應該 >= 7

echo ""
echo "2. 目錄結構:"
ls -d */ | wc -l  # 應該 >= 7

echo ""
echo "3. Agents:"
find agent -name "*.yaml" | wc -l  # 應該 >= 21

echo ""
echo "4. Workflows:"
find workflow -name "*.md" | wc -l  # 應該 >= 17

echo ""
echo "5. Scenarios:"
ls -d scenarios/*/ | wc -l  # 應該 = 9

echo ""
echo "6. 版本號一致性:"
grep -r "v0.09" . --exclude-dir=build --exclude="*.backup" | wc -l  # 應該 = 0

echo ""
echo "7. CLAUDE.md Latest Version:"
grep "Latest Version" ${BASE_DIR}/CLAUDE.md

echo ""
echo "8. UPGRADE_SOP 下次版本:"
ls AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md

echo ""
echo "9. 發布包驗證:"
cd ${BASE_DIR}
if [ -f "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz" ]; then
  echo "✅ 發布包存在"
  ls -lh "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz"
else
  echo "⚠️  發布包不存在（需手動檢查）"
fi

if [ -f "releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz.sha256" ]; then
  echo "✅ SHA256 校驗和存在"
else
  echo "⚠️  SHA256 校驗和不存在"
fi
```

**最終檢查清單**:

- [ ] 所有核心文檔已拷貝
- [ ] 所有目錄已拷貝 (agent, workflow, docs_template, scenarios, guides, prompts, build)
- [ ] 所有版本號已更新至 v0.10
- [ ] 無 v0.09 殘留
- [ ] CLAUDE.md Latest Version = v0.10
- [ ] AISDLC_v0.11_UPGRADE_SOP.md 已創建
- [ ] FILE_DIRECTORY_RULES.md 已更新變更記錄
- [ ] build/ 目錄結構正確
- [ ] **發布包 `releases/AISDLC_v0.10_release_YYYY-MM-DD.tar.gz` 已創建**
- [ ] **SHA256 校驗和檔案已創建**
- [ ] 完整驗證檢查清單全部通過 (參考附錄 A)

---

### 7.4 發布確認

**所有檢查通過後，確認發布**:

```bash
echo "✅ AISDLC v0.10 升版完成！"
echo ""
echo "📁 新版本位置: ${BASE_DIR}/AISDLC_${NEW_VERSION}"
echo "📋 核心文檔數: $(ls AISDLC_${NEW_VERSION}/*.md | wc -l)"
echo "🤖 Agents 數量: $(find AISDLC_${NEW_VERSION}/agent -name "*.yaml" | wc -l)"
echo "🔄 Workflows 數量: $(find AISDLC_${NEW_VERSION}/workflow -name "*.md" | wc -l)"
echo "📦 Scenarios 數量: $(ls -d AISDLC_${NEW_VERSION}/scenarios/*/ | wc -l)"
echo ""
echo "🎉 升版成功！"
```

---

### 7.5 🔴 最終驗證：發布包檢查（新增 - 強制執行）

> **⚠️ 本步驟為 2025-12-09 新增，目的是防止發布包缺失問題再次發生**
>
> **歷史教訓**: v0.09→v0.09 升版時，Agent 宣稱完成但實際未創建發布包，
> 導致使用者無法使用升版成果。此步驟確保發布包真實存在且可用。

**執行命令** (🔴 強制執行，不可跳過):

```bash
# 切換到 BASE_DIR
cd ${BASE_DIR}

echo "========================================="
echo "   階段 7.5: 發布包最終驗證"
echo "========================================="
echo ""

# 設定預期的發布包路徑
UPDATE_DATE=$(date +%Y-%m-%d)
RELEASE_FILE="AISDLC_${NEW_VERSION}/releases/AISDLC_${NEW_VERSION}_release_${UPDATE_DATE}.tar.gz"

echo "=== 檢查 1: 發布包檔案是否存在 ==="
if [ -f "$RELEASE_FILE" ]; then
  echo "✅ 發布包檔案存在"
  ls -lh "$RELEASE_FILE"
else
  echo "❌ 發布包檔案不存在！"
  echo "   預期路徑: $RELEASE_FILE"
  echo ""
  echo "🚨 升版失敗！請返回階段 7.1 重新創建發布包！"
  exit 1
fi

echo ""
echo "=== 檢查 2: 發布包大小是否合理 ==="
FILE_SIZE=$(stat -f%z "$RELEASE_FILE" 2>/dev/null || stat -c%s "$RELEASE_FILE" 2>/dev/null)
if [ "$FILE_SIZE" -gt 1048576 ]; then
  echo "✅ 發布包大小正常: $(ls -lh "$RELEASE_FILE" | awk '{print $5}')"
else
  echo "❌ 發布包大小異常！小於 1MB，可能壓縮失敗"
  echo "   實際大小: $FILE_SIZE bytes"
  echo ""
  echo "🚨 升版失敗！請返回階段 7.1 檢查壓縮過程！"
  exit 1
fi

echo ""
echo "=== 檢查 3: SHA256 校驗和檔案是否存在 ==="
SHA_FILE="${RELEASE_FILE}.sha256"
if [ -f "$SHA_FILE" ]; then
  echo "✅ SHA256 校驗和檔案存在"
  cat "$SHA_FILE"
else
  echo "⚠️  SHA256 校驗和檔案不存在（非致命錯誤）"
fi

echo ""
echo "========================================="
echo "   ✅ 階段 7.5 驗證通過"
echo "========================================="
echo ""
echo "發布包資訊："
echo "  路徑: $RELEASE_FILE"
echo "  大小: $(ls -lh "$RELEASE_FILE" | awk '{print $5}')"
echo "  位元組: $FILE_SIZE bytes"
```

**檢查點** (🔴 所有項目必須勾選):
- [ ] 🔴 發布包檔案存在: `AISDLC_v0.10/releases/AISDLC_v0.10_release_YYYY-MM-DD.tar.gz`
- [ ] 🔴 發布包大小 > 1MB（合理範圍）
- [ ] 🔴 實際大小已記錄: _____ MB/KB
- [ ] SHA256 校驗和檔案存在（可選）
- [ ] 發布包路徑已確認正確

**如果任何檢查失敗，升版不完整！必須返回階段 7.1 修正！**

---

## 附錄 A: 完整驗證檢查清單

### A.1 升版完整性驗證

#### A.1.1 目錄結構驗證

- [ ] agent/ 目錄存在
  - [ ] agent/core/ 存在 (7 個 Core Agents)
  - [ ] agent/specialized/ 存在 (14+ 個 Specialized Agents)
  - [ ] agent/core/backup_en/ 存在 (英文版備份)
- [ ] workflow/ 目錄存在
  - [ ] workflow/core/ 存在 (7+ 個核心 Workflows)
  - [ ] workflow/supplementary/ 存在
- [ ] docs_template/ 目錄存在
  - [ ] docs_template/prd/ 存在
  - [ ] docs_template/frd/ 存在
  - [ ] docs_template/srd/ 存在
  - [ ] docs_template/tests/ 存在
  - [ ] docs_template/core/ 存在
  - [ ] docs_template/scenario_specific/ 存在
- [ ] scenarios/ 目錄存在 (10 個場景目錄)
- [ ] guides/ 目錄存在
- [ ] prompts/ 目錄存在
- [ ] build/ 目錄存在
  - [ ] build/logs/ 存在
  - [ ] build/planning/active/ 存在
  - [ ] build/planning/archive/ 存在
  - [ ] build/reports/ 存在

#### A.1.2 核心文檔驗證 (根目錄)

- [ ] AISDLC_INIT.md 存在，版本號 = v0.10
- [ ] FILE_DIRECTORY_RULES.md 存在，版本號 = v0.10，已新增升版記錄
- [ ] README.md 存在，版本號 = v0.10
- [ ] DEVELOPMENT_DIRECTORY_STRUCTURE.md 存在
- [ ] AISDLC_UPGRADE_SOP_CheckList.md 存在（已重置為乾淨範本）
- [ ] **AISDLC_v0.11_UPGRADE_SOP.md 存在** ← 極易遺漏！

#### A.1.3 build/logs/ 目錄驗證

- [ ] build/logs/ 目錄存在
- [ ] CHANGELOG_v0.10.md 已創建 (可選)

#### A.1.4 Agents 驗證 (21 個)

**Core Agents (7 個)**:
- [ ] 01.agent-template-zh_OK.yaml
- [ ] 02.ba-business-analyst-zh.yaml
- [ ] 03.pm-po-agent-zh.yaml
- [ ] 04.sa-analyst-zh.yaml
- [ ] 05.sd-architect-zh.yaml
- [ ] 06.dev-developer-zh.yaml
- [ ] 07.qa-tester-zh.yaml

**Specialized Agents (14+ 個)** - 確認數量 >= 14

#### A.1.5 Workflows 驗證 (17 個)

**Core Workflows (7 個)**:
- [ ] requirements-extraction.md
- [ ] requirements-validation.md
- [ ] user-story.md
- [ ] api-specification.md
- [ ] change-management.md
- [ ] consistency-check.md
- [ ] interaction-analysis.md

**Supplementary Workflows** - 確認數量 >= 10

#### A.1.6 Scenarios 驗證 (32 個檔案)

**10 個場景目錄** (每個含 SOP.md, Workflow.md, checklists/):
- [ ] greenfield/
- [ ] brownfield/
- [ ] refactoring/
- [ ] integration/
- [ ] testing/
- [ ] security/
- [ ] performance/
- [ ] devops/
- [ ] documentation/
- [ ] migration/

#### A.1.7 文檔模板驗證

- [ ] PRD 模板存在 (prd/)
- [ ] FRD 模板存在 (frd/)
- [ ] SRD 模板存在 (srd/)
- [ ] API 模板存在 (srd/api/)
- [ ] Test 模板存在 (tests/)
- [ ] Core 模板存在 (core/)
- [ ] Scenario-specific 模板存在 (scenario_specific/)
  - [ ] CI/CD Pipeline Template 存在 (scenario_specific/devops/)

#### A.1.8 其他目錄驗證

- [ ] guides/ 目錄檔案數 >= 5
  - [ ] AISDLC_ID_Naming_Convention.md 存在
  - [ ] Document_Quality_Checklist.md 存在
- [ ] prompts/ 目錄檔案數 >= 10

---

### A.2 版本號一致性驗證

#### A.2.1 核心文檔版本號

- [ ] AISDLC_INIT.md: version = v0.10
- [ ] FILE_DIRECTORY_RULES.md: version = v0.10
- [ ] README.md: version = v0.10
- [ ] AISDLC_v0.11_UPGRADE_SOP.md: 源版本 = v0.10, 目標版本 = v0.11

#### A.2.2 文檔模板版本號 (7 個核心模板)

- [ ] PRD_Universal_Template.md: version = 0.10
- [ ] FRD_Template.md: version = 0.10
- [ ] User_Story_Template.md: version = 0.10
- [ ] SRD_Template.md: version = 0.10
- [ ] API_Specification_Template.md: version = 0.10
- [ ] AT_Template.md: version = 0.10
- [ ] TC_Template.md: version = 0.10

#### A.2.3 場景 SOP 版本號 (10 個場景 × 3 檔案)

**Greenfield**:
- [ ] scenarios/greenfield/SOP.md: version = 0.10
- [ ] scenarios/greenfield/Workflow.md: version = 0.10

**Brownfield**:
- [ ] scenarios/brownfield/SOP.md: version = 0.10
- [ ] scenarios/brownfield/Workflow.md: version = 0.10

**Refactoring**:
- [ ] scenarios/refactoring/SOP.md: version = 0.10
- [ ] scenarios/refactoring/Workflow.md: version = 0.10

**Integration**:
- [ ] scenarios/integration/SOP.md: version = 0.10
- [ ] scenarios/integration/Workflow.md: version = 0.10

**Testing**:
- [ ] scenarios/testing/SOP.md: version = 0.10
- [ ] scenarios/testing/Workflow.md: version = 0.10

**Security**:
- [ ] scenarios/security/SOP.md: version = 0.10
- [ ] scenarios/security/Workflow.md: version = 0.10

**Performance**:
- [ ] scenarios/performance/SOP.md: version = 0.10
- [ ] scenarios/performance/Workflow.md: version = 0.10

**DevOps**:
- [ ] scenarios/devops/SOP.md: version = 0.10
- [ ] scenarios/devops/Workflow.md: version = 0.10

**Documentation**:
- [ ] scenarios/documentation/SOP.md: version = 0.10
- [ ] scenarios/documentation/Workflow.md: version = 0.10

#### A.2.4 Agent 配置版本號

- [ ] 所有 Core Agents (7 個): version = "0.10"
- [ ] 所有 Specialized Agents (14+ 個): version = "0.10"

#### A.2.5 舊版本號殘留檢查

**執行命令**:
```bash
cd AISDLC_${NEW_VERSION}
grep -r "v0.09" . --exclude-dir=build --exclude-dir=archive_en --exclude-dir=backup_en --exclude="*.backup" | wc -l  # 必須 = 0
```

- [ ] 無 v0.09 殘留 (除了 build/、archive_en/、backup_en/ 和 *.backup)

---

### A.3 文檔內容正確性驗證

#### A.3.1 AISDLC_INIT.md

- [ ] workflow-agent 映射表完整
- [ ] on-demand loading 機制說明存在
- [ ] Agent 載入時序表存在

#### A.3.2 文檔模板

- [ ] 所有模板包含 metadata 區塊
- [ ] 所有模板包含 traceability 區塊
- [ ] 模板 ID 格式正確

#### A.3.3 場景 SOP

- [ ] 每個場景 SOP 包含完整的階段說明
- [ ] 每個場景 SOP 包含 Agent 引用
- [ ] Agent 路徑正確 (使用 `-zh.yaml` 後綴)

#### A.3.4 Workflow 定義

- [ ] 每個 Workflow 包含 workflow_metadata
- [ ] Agent 路徑使用絕對路徑格式 (agent/core/XX-zh.yaml)
- [ ] 所有 🔴 確認點保留

---

### A.4 文檔內部連結驗證

#### A.4.1 核心文檔連結

- [ ] AISDLC_INIT.md 引用的 workflow 檔案存在
- [ ] Project_README.md 引用的文檔檔案存在
- [ ] INTEGRATION_GUIDE.md 連結有效

#### A.4.2 模板間可追溯性連結

- [ ] PRD → FRD 連結有效
- [ ] FRD → SRD 連結有效
- [ ] SRD → API Spec 連結有效

#### A.4.3 SOP 內部連結

- [ ] Greenfield SOP 階段連結有效
- [ ] 其他場景 SOP 連結有效

---

### A.5 功能性驗證

#### A.5.1 Workflow 執行測試

**建議測試**:
- [ ] 載入 AISDLC_INIT.md 無錯誤
- [ ] 觸發任一 Workflow，Agent 載入成功
- [ ] 確認問題清單存在且可用

#### A.5.2 文檔生成測試

**建議測試**:
- [ ] 使用 PRD 模板生成文檔，traceability 正確
- [ ] 使用 API 模板生成文檔，格式正確

---

### A.6 自動化驗證腳本

#### A.6.1 版本號檢查腳本 (已執行)

```bash
#!/bin/bash
cd AISDLC_v0.09
echo "=== 版本號殘留檢查 ==="
echo "v0.09 殘留數量: $(grep -r "v0.09" . --exclude-dir=build --exclude="*.backup" | wc -l)"
echo "預期結果: 0"
```

#### A.6.2 目錄完整性檢查 (已執行)

```bash
#!/bin/bash
cd AISDLC_v0.09
echo "=== 目錄完整性檢查 ==="
echo "Agent 數量: $(find agent -name "*.yaml" | wc -l)"  # >= 21
echo "Workflow 數量: $(find workflow -name "*.md" | wc -l)"  # >= 17
echo "Scenario 數量: $(ls -d scenarios/*/ | wc -l)"  # = 10
echo "Template 數量: $(find docs_template -name "*Template.md" | wc -l)"  # >= 20
```

---

### A.7 驗證結果記錄

**驗證日期**: YYYY-MM-DD
**執行人**: [姓名]
**驗證結果**: [ ] 全部通過 / [ ] 部分失敗

**失敗項目** (如有):
1. [描述失敗項目]
2. [描述失敗項目]

**修正動作**:
1. [描述修正動作]
2. [描述修正動作]

---

## 附錄 B: 版本更新快速參考

### B.1 批量版本號更新命令

**一鍵更新所有檔案的版本號** (謹慎使用):

```bash
# 確保在新版本目錄
cd AISDLC_v0.09

# 更新所有 .md 檔案
find . -name "*.md" -type f -exec sed -i '' 's/v0.09/v0.09/g' {} \;
find . -name "*.md" -type f -exec sed -i '' 's/version: 0.09/version: 0.09/g' {} \;

# 更新所有 .yaml 檔案
find . -name "*.yaml" -type f -exec sed -i '' 's/version: "0.09"/version: "0.09"/g' {} \;
find . -name "*.yaml" -type f -exec sed -i '' 's/AISDLC_v0.09/AISDLC_v0.09/g' {} \;

# 驗證
grep -r "v0.09" . --exclude-dir=build --exclude="*.backup" | wc -l  # 應該 = 0
```

---

### B.2 核心檔案快速檢查

**快速檢查核心文檔是否存在**:

```bash
cd AISDLC_v0.09

echo "=== 核心文檔檢查 ==="

# 🔴 簡化版：避免 for loop，直接檢查每個檔案
test -f AISDLC_INIT.md && echo "✅ AISDLC_INIT.md 存在" || echo "❌ AISDLC_INIT.md 缺失"
test -f FILE_DIRECTORY_RULES.md && echo "✅ FILE_DIRECTORY_RULES.md 存在" || echo "❌ FILE_DIRECTORY_RULES.md 缺失"
test -f README.md && echo "✅ README.md 存在" || echo "❌ README.md 缺失"
test -f DEVELOPMENT_DIRECTORY_STRUCTURE.md && echo "✅ DEVELOPMENT_DIRECTORY_STRUCTURE.md 存在" || echo "❌ DEVELOPMENT_DIRECTORY_STRUCTURE.md 缺失"
test -f AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md && echo "✅ AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md 存在" || echo "❌ AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md 缺失"
```

---

### B.3 模板版本號快速檢查

```bash
cd AISDLC_${NEW_VERSION}

echo "=== 模板版本號檢查 ==="
grep -rn "version: 0.10" docs_template/ | wc -l  # 應該 >= 20
```

---

### B.4 場景 SOP 版本號快速檢查

```bash
cd AISDLC_${NEW_VERSION}

echo "=== 場景 SOP 版本號檢查 ==="
grep -rn "version: 0.10" scenarios/ | wc -l  # 應該 >= 30 (10 個場景 × 3 檔案)
```

---

### B.5 常見問題快速修復

#### 問題1: 發現舊版本號殘留

**診斷**:
```bash
grep -rn "v0.09" . --exclude-dir=build --exclude="*.backup"
```

**修復**:
```bash
# 針對單一檔案修復
sed -i '' 's/v0.09/v0.09/g' [檔案路徑]

# 批量修復 (謹慎使用)
find . -name "*.md" -type f -exec sed -i '' 's/v0.09/v0.09/g' {} \;
```

#### 問題2: CHANGELOG 位置錯誤

**正確位置**: `build/logs/CHANGELOG_v0.09.md`
**錯誤位置**: 根目錄

**修復**:
```bash
# 如果 CHANGELOG 在根目錄
mv CHANGELOG_v0.10.md build/logs/
```

#### 問題3: UPGRADE_SOP 位置錯誤

**正確位置**: `AISDLC_v0.10/AISDLC_v0.11_UPGRADE_SOP.md` (下次升版 SOP)
**錯誤位置**: `build/` 目錄

**修復**:
```bash
# 如果 UPGRADE_SOP 在錯誤位置
mv build/AISDLC_${NEXT_VER_V}_UPGRADE_SOP.md ./
```

---

### B.6 升版完成度自我評估

**快速檢查清單**:
- [ ] 所有核心目錄已拷貝
- [ ] 所有核心檔案版本號已更新
- [ ] 所有模板版本號已更新
- [ ] 所有場景 SOP 版本號已更新
- [ ] CHANGELOG_v0.10.md 已創建 (可選)
- [ ] AISDLC_v0.11_UPGRADE_SOP.md 已創建（從本檔複製並改名）
- [ ] build/ 目錄結構正確
- [ ] 無舊版本號殘留
- [ ] 版本一致性 = 100%

---

## 🎉 升版完成！

**恭喜！如果所有檢查點都已完成，AISDLC v0.10 升版成功！**

**後續建議**:

1. **測試新版本**: 使用實際專案測試新版本的 Workflows
2. **更新文檔**: 如有新增功能，更新相關說明文檔
3. **備份舊版本**: 保留 AISDLC_v0.09 作為備份
4. **分享經驗**: 記錄升版過程中的經驗和改進建議

---

**📝 維護建議**:

- 在下次升版 (v0.10 → v0.11) 前，確保本 SOP 已複製並修改為 `AISDLC_v0.11_UPGRADE_SOP.md`
- 持續更新 `FILE_DIRECTORY_RULES.md` 記錄所有檔案變更
- 保持 `CLAUDE.md` 的 Latest Version 指向最新版本

---

**📞 需要協助?**

如果升版過程中遇到問題，請：
1. 參考 [附錄 B: 版本更新快速參考](#附錄-b-版本更新快速參考)
2. 檢查 [附錄 A: 完整驗證檢查清單](#附錄-a-完整驗證檢查清單)
3. 查看 `FILE_DIRECTORY_RULES.md` 的歷史變更記錄

---

**🔴 最後提醒**: 本檔案僅用於升版操作指引，不應包含詳細的功能改進說明或執行記錄。所有功能變更請參考 `build/planning/` 和 `build/logs/CHANGELOG_v0.0X.md`。
