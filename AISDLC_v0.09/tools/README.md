# AISDLC 工具目錄

## 🔧 目錄說明

此目錄用於存放 AISDLC 框架的輔助工具與腳本。

---

## 🚀 快速安裝 AISDLC（從 GitHub）

### 方法 1：一行安裝（公開倉庫推薦）🌟

#### Mac / Linux

```bash
# 一行安裝到當前目錄
curl -fsSL https://raw.githubusercontent.com/wuweihungmobile/AISDLC/main/AISDLC_v0.09/tools/init_project.sh | bash

# 指定版本和目錄
curl -fsSL https://raw.githubusercontent.com/wuweihungmobile/AISDLC/main/AISDLC_v0.09/tools/init_project.sh | bash -s -- -v 0.08 -d ./my-project
```

#### Windows (PowerShell)

```powershell
# 一行安裝到當前目錄
irm https://raw.githubusercontent.com/wuweihungmobile/AISDLC/main/AISDLC_v0.09/tools/init_project.ps1 | iex

# 指定版本和目錄（需先下載）
irm https://raw.githubusercontent.com/wuweihungmobile/AISDLC/main/AISDLC_v0.09/tools/init_project.ps1 -OutFile init.ps1; .\init.ps1 -v 0.08 -d .\my-project
```

---

### 方法 2：私有倉庫安裝（SSH Key 或 PAT Token）

> ⚠️ **注意**：私有倉庫的遠端下載需攜帶授權憑證，否則 GitHub 會回傳 404。

#### 方式 A：使用 SSH Key

##### Mac / Linux

```bash
# 先下載腳本（需 PAT 或已設定 SSH）
curl -H "Authorization: token YOUR_GITHUB_PAT" -fsSL https://raw.githubusercontent.com/wuweihungmobile/AISDLC/main/AISDLC_v0.09/tools/init_project.sh -o init.sh

# 使用 SSH 執行
bash init.sh --ssh -d ~/my-project
```

##### Windows (PowerShell)

```powershell
# 先下載腳本（需 PAT）
$headers = @{ Authorization = "token YOUR_GITHUB_PAT" }
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/wuweihungmobile/AISDLC/main/AISDLC_v0.09/tools/init_project.ps1" -Headers $headers -OutFile init.ps1

# 使用 SSH 執行
.\init.ps1 -SSH -Dir C:\Projects\MyApp
```

#### 方式 B：使用 PAT Token（無 SSH Key 時推薦）

##### Mac / Linux

```bash
# 下載腳本
curl -H "Authorization: token YOUR_GITHUB_PAT" -fsSL https://raw.githubusercontent.com/wuweihungmobile/AISDLC/main/AISDLC_v0.09/tools/init_project.sh -o init.sh

# 使用 PAT Token 執行（腳本會自動用 HTTPS + Token 下載）
bash init.sh --token YOUR_GITHUB_PAT -d ~/my-project
```

##### Windows (PowerShell)

```powershell
# 下載腳本（同上）
$headers = @{ Authorization = "token YOUR_GITHUB_PAT" }
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/wuweihungmobile/AISDLC/main/AISDLC_v0.09/tools/init_project.ps1" -Headers $headers -OutFile init.ps1

# 使用 PAT Token 執行
.\init.ps1 -Token YOUR_GITHUB_PAT -Dir C:\Projects\MyApp
```

---

### 方法 3：Clone 後安裝

```bash
# 公開倉庫
git clone https://github.com/wuweihungmobile/AISDLC.git

# 私有倉庫（使用 SSH）
git clone git@github.com:wuweihungmobile/AISDLC.git
```

#### Mac / Linux

```bash
cd AISDLC
./AISDLC_v0.09/tools/init_project.sh -d ~/my-project        # 公開倉庫
./AISDLC_v0.09/tools/init_project.sh -d ~/my-project --ssh  # 私有倉庫
./AISDLC_v0.09/tools/init_project.sh -h                     # 查看說明
```

#### Windows (PowerShell)

```powershell
cd AISDLC
.\AISDLC_v0.09\tools\init_project.ps1 -Dir C:\Projects\MyApp         # 公開倉庫
.\AISDLC_v0.09\tools\init_project.ps1 -Dir C:\Projects\MyApp -SSH    # 私有倉庫
.\AISDLC_v0.09\tools\init_project.ps1 -Help                          # 查看說明
```

---

> 💡 **提示**：
> - **公開倉庫**: 使用方法 1 一行安裝最快
> - **私有倉庫**: 使用方法 2 或 3，需 SSH Key 或 PAT Token
> - **最穩定方式**: 方法 3（先 clone 到本地再執行，腳本會自動偵測本地框架並跳過下載）

### 安裝完成後的目錄結構

```
您的專案目錄/
├── AISDLC_v0.09/          # AISDLC 框架
│   ├── agent/              # AI Agent 定義
│   ├── workflow/           # 工作流程
│   ├── docs/               # 📄 您的專案文件放這裡
│   │   ├── 01_requirements/
│   │   ├── 02_architecture/
│   │   ├── 03_testing/
│   │   └── ...
│   ├── docs_template/      # 文件模板
│   ├── guides/             # 參考指南
│   └── AISDLC_INIT.md      # 框架入口
└── CLAUDE.md               # Claude Code 設定
```

---

## ⚠️ 當前狀態

**工具開發為 P2 項目（Medium Priority），目前使用文檔化替代方案**

- **v0.09 重點**: 快速安裝、GitHub 整合
- **工具開發**: 規劃於未來版本
- **當前方案**: 所有工具功能均有對應的手動流程與文檔指引

---

## 📋 規劃中的工具

### 1. aisdlc-lint (P2-13) - 文檔檢查工具

**狀態**: 📋 規劃中

**功能**:
```yaml
文檔完整性檢查:
  - 必填欄位是否填寫
  - 文檔格式是否正確
  - ID 命名是否符合規範

追溯鏈驗證:
  - US-XXX → AC-XXX-X → AT-XXX-X-Y 鏈結完整性
  - PRD → FRD → SRD 引用正確性
  - API Spec 與 SRD 的對應關係

輸出報告:
  - 文檔完整度評分
  - 斷鏈列表
  - 改進建議
```

**當前替代方案**:
- ✅ 使用 [ERROR_PREVENTION_SYSTEM.md](../ERROR_PREVENTION_SYSTEM.md) 手動檢查清單
- ✅ 追溯鏈檢查清單在 [KPI_VERIFICATION_PLAN.md](../KPI_VERIFICATION_PLAN.md)

**預計實作**:
```yaml
v0.06.1 - 基本版 (2-3 天開發):
  - 必填欄位檢查
  - ID 格式驗證
  - 簡單的追溯鏈檢查

v0.06 - 完整版 (5-7 天開發):
  - 語意分析（內容品質評估）
  - 智能建議改進項目
  - 自動修正常見錯誤
```

**使用範例** (規劃):
```bash
# 檢查單一文檔
./tools/aisdlc-lint/lint.sh docs/planning/PRD-MyProject.md

# 檢查整個專案
./tools/aisdlc-lint/lint.sh docs/

# 輸出報告
./tools/aisdlc-lint/lint.sh docs/ --report=json > lint-report.json
```

---

### 2. checkpoint-manager - Checkpoint 管理工具

**狀態**: 📋 規劃中

**功能**:
```yaml
Checkpoint 儲存:
  - 自動記錄當前 Session 狀態
  - 儲存決策記錄
  - 記錄已產出文檔清單

Checkpoint 恢復:
  - 列出所有可用 Checkpoint
  - 一鍵恢復到指定 Checkpoint
  - 驗證文檔完整性

Checkpoint 管理:
  - 列出所有 Checkpoint
  - 刪除舊 Checkpoint
  - 匯出/匯入 Checkpoint (團隊協作)
```

**當前替代方案**:
- ✅ 使用 [CHECKPOINT_RECOVERY_SYSTEM.md](../CHECKPOINT_RECOVERY_SYSTEM.md) 手動記錄
- ✅ 使用 [PHASE5_CHECKPOINT_SYSTEM.md](../PHASE5_CHECKPOINT_SYSTEM.md) Phase 5 專用

**預計實作**:
```yaml
v0.06 - 基本版 (3-4 天開發):
  - save-checkpoint.sh - 儲存 Checkpoint
  - restore-checkpoint.sh - 恢復 Checkpoint
  - list-checkpoints.sh - 列出 Checkpoint

v0.06.1 - 進階版:
  - 自動 Checkpoint (Token 90% 時)
  - Checkpoint 分支管理
  - 團隊協作功能（匯出/匯入）
```

**使用範例** (規劃):
```bash
# 儲存 Checkpoint
./tools/checkpoint-manager/save-checkpoint.sh "Completed PRD"

# 列出所有 Checkpoint
./tools/checkpoint-manager/list-checkpoints.sh

# 恢復到指定 Checkpoint
./tools/checkpoint-manager/restore-checkpoint.sh CP-20251024-1430

# 匯出 Checkpoint (供團隊成員使用)
./tools/checkpoint-manager/export-checkpoint.sh CP-20251024-1430 -o checkpoint.zip
```

---

### 3. traceability-checker - 追溯鏈檢查工具

**狀態**: 📋 規劃中

**功能**:
```yaml
追溯鏈分析:
  - 掃描所有專案文檔
  - 建立追溯矩陣 (Traceability Matrix)
  - 識別斷鏈與錯誤引用

視覺化:
  - 產出追溯鏈流程圖 (Mermaid)
  - 文檔間依賴關係圖

報告產出:
  - 追溯鏈完整度評分 (KPI-Q2)
  - 斷鏈詳細列表
  - 修正建議
```

**當前替代方案**:
- ✅ 使用 [KPI_VERIFICATION_PLAN.md](../KPI_VERIFICATION_PLAN.md) 手動檢查清單
- ✅ 追溯鏈定義在各文檔模板的 Traceability 章節

**預計實作**:
```yaml
v0.06 - 基本版 (3-4 天開發):
  - 掃描 US/AC/AT ID
  - 檢查引用存在性
  - 產出追溯矩陣

v0.06.1 - 進階版:
  - 視覺化流程圖
  - 變更影響分析
  - 自動修復建議
```

**使用範例** (規劃):
```bash
# 檢查整個專案的追溯鏈
./tools/traceability-checker/check.sh docs/

# 產出追溯矩陣
./tools/traceability-checker/check.sh docs/ --output=matrix --format=markdown

# 視覺化追溯鏈
./tools/traceability-checker/check.sh docs/ --visualize --output=traceability.mmd
```

---

### 4. template-validator - 模板驗證工具

**狀態**: 📋 規劃中

**功能**:
```yaml
模板驗證:
  - 檢查文檔是否符合模板結構
  - 驗證必填章節是否存在
  - 檢查 Markdown 格式正確性

模板選擇建議:
  - 根據專案類型推薦模板
  - 檢查是否使用了正確的模板

品質評估:
  - 內容完整度評分
  - 章節深度分析
```

**當前替代方案**:
- ✅ 使用 [docs_template/](../docs_template/) 中的模板說明
- ✅ 使用 [SMART_DEFAULTS.md](../SMART_DEFAULTS.md) 模板選擇指引

**預計實作**: v0.06 (2-3 天開發)

**使用範例** (規劃):
```bash
# 驗證文檔是否符合模板
./tools/template-validator/validate.py docs/planning/PRD-MyProject.md --template=PRD_Universal

# 評估文檔品質
./tools/template-validator/validate.py docs/planning/PRD-MyProject.md --score
```

---

### 5. workflow-selector - Workflow 選擇輔助工具

**狀態**: 📋 規劃中

**功能**:
```yaml
互動式選擇:
  - 詢問專案特性（5 個問題）
  - 自動推薦適合的 Workflow 組合

視覺化決策樹:
  - 產出決策樹圖
  - 說明選擇理由

一鍵啟動:
  - 產出完整的啟動指令
  - 自動載入推薦的 Agents
```

**當前替代方案**:
- ✅ 使用 [QUICK_WINS_GUIDE.md](../QUICK_WINS_GUIDE.md) QW-6 的決策樹
- ⏳ P1-5: Workflow Selection Matrix (待完成)

**預計實作**: v0.06.1 或與 P1-5 整合 (1-2 天開發)

**使用範例** (規劃):
```bash
# 互動式選擇 Workflow
./tools/workflow-selector/select.sh

# 輸出範例:
# Q1: 是新專案還是現有專案? [new/existing]
# > new
# Q2: 專案類型? [web/mobile/backend/other]
# > web
# ...
# 推薦: Greenfield Scenario
# Workflows: 1 → 2 → 3 → 5
# 啟動指令: AISDLC greenfield-web "Your project description"
```

---

## 🛠️ 為什麼工具目錄目前是空的？

### v0.06 優化策略

v0.06 的核心理念是**文檔驅動 + 手動流程優先**，原因如下：

```yaml
1. 優先級排序 (P0/P1/P2):
   P0 (Critical): 文檔模板簡化、SOP 優化 ✅
   P1 (High): Agent 協作、快速啟動 ✅
   P2 (Medium): 自動化工具 ⏳

2. 資源分配:
   - Phase 1-4: 聚焦文檔與流程（70% 效益）
   - Phase 5: 策略規劃
   - 未來版本: 自動化工具（增強功能）

3. MVP 原則:
   - 先驗證文檔化方案有效性
   - 根據使用者反饋決定工具優先級
   - 避免過早優化

4. 文檔化替代方案的優勢:
   ✅ 立即可用（無需開發）
   ✅ 平台無關（適用所有 LLM）
   ✅ 易於維護（修改文檔即可）
   ✅ 學習門檻低（無需安裝）
```

---

## 📊 文檔化 vs 工具化對比

| 功能 | 文檔化方案 | 工具化方案 | v0.06 狀態 |
|------|-----------|-----------|-----------|
| **文檔檢查** | 手動檢查清單 | aisdlc-lint | ✅ 文檔化 |
| **追溯鏈驗證** | 手動檢查清單 | traceability-checker | ✅ 文檔化 |
| **Checkpoint 管理** | 手動記錄 | checkpoint-manager | ✅ 文檔化 |
| **模板驗證** | 模板說明 | template-validator | ✅ 文檔化 |
| **Workflow 選擇** | 決策樹圖 | workflow-selector | ⏳ P1-5 待完成 |

**結論**: 文檔化方案已涵蓋所有核心功能，工具化為增強功能。

---

## 🚀 工具開發路線圖

### v0.06.1 (1-2 週後)
```yaml
可能包含:
  ⏳ workflow-selector (與 P1-5 整合)
  ⏳ 簡易的 traceability-checker (基本版)
```

### v0.06.1 (1-2 個月後)
```yaml
可能包含:
  ⏳ aisdlc-lint 基本版
  ⏳ template-validator
```

### v0.06 (3-6 個月後)
```yaml
目標:
  ⏳ 所有工具基本版完成
  ⏳ checkpoint-manager
  ⏳ aisdlc-lint 進階版
  ⏳ traceability-checker 視覺化
```

**時程彈性**: 根據使用者反饋與需求調整優先級

---

## 🤝 貢獻工具

歡迎社群貢獻自動化工具！

### 貢獻指南

```yaml
1. 工具設計原則:
   - 簡單易用（一行指令即可執行）
   - 平台無關（Bash/Python，避免複雜依賴）
   - 輸出清晰（Markdown/JSON 格式）
   - 錯誤友善（提供修正建議）

2. 提交流程:
   - Fork AISDLC Repository
   - 在 tools/[tool-name]/ 建立工具目錄
   - 包含 README.md（使用說明）
   - 包含範例與測試
   - 提交 Pull Request

3. 文檔要求:
   - 清晰的使用說明
   - 輸入/輸出範例
   - 錯誤處理說明
   - 已知限制

4. 測試要求:
   - 至少 3 個測試案例
   - 包含正常與異常情況
   - 提供測試數據
```

**範例 PR 標題**: `[Tool] Add aisdlc-lint basic version`

---

## 📝 手動流程文檔索引

雖然工具尚未開發，但所有功能均有完善的手動流程：

### 文檔檢查與驗證
- [ERROR_PREVENTION_SYSTEM.md](../ERROR_PREVENTION_SYSTEM.md) - 5 大錯誤類別預防
- [KPI_VERIFICATION_PLAN.md](../KPI_VERIFICATION_PLAN.md) - KPI 驗證方法

### Checkpoint 與恢復
- [CHECKPOINT_RECOVERY_SYSTEM.md](../CHECKPOINT_RECOVERY_SYSTEM.md) - Checkpoint 機制
- [PHASE5_CHECKPOINT_SYSTEM.md](../PHASE5_CHECKPOINT_SYSTEM.md) - Phase 5 專用

### Workflow 選擇
- [QUICK_WINS_GUIDE.md](../QUICK_WINS_GUIDE.md) - QW-6 決策樹
- [AISDLC_INIT.md](../AISDLC_INIT.md) - Workflow-Agent 映射表
- ⏳ P1-5: Workflow Selection Matrix (待完成)

### 模板使用
- [SMART_DEFAULTS.md](../SMART_DEFAULTS.md) - 智能預設值與模板選擇
- [docs_template/](../docs_template/) - 所有模板與使用說明

### 快速啟動
- [QUICK_START_TEMPLATES.md](../QUICK_START_TEMPLATES.md) - 6 個預設範本
- [scenarios/*/SOP_QuickRef.md](../scenarios/) - 各情境快速參考

---

## ❓ 常見問題

### Q1: 為什麼不先開發工具？
**A**:
- 文檔優化帶來 70% 效益，工具僅增強 30%
- 先驗證流程有效性，再自動化
- 資源優先投入高 ROI 項目（文檔與流程）

### Q2: 我可以自己寫工具嗎？
**A**: 當然可以！歡迎貢獻。請參考上方「貢獻工具」章節。

### Q3: 沒有工具會影響使用嗎？
**A**: 不會。所有功能均有手動流程：
- ✅ 文檔檢查 → 使用檢查清單
- ✅ 追溯鏈驗證 → 手動檢查表
- ✅ Checkpoint → 手動記錄
- ✅ Workflow 選擇 → 決策樹圖

### Q4: 工具何時會開發？
**A**:
- **v0.06.1**: 可能包含 workflow-selector (1-2 週)
- **v0.06.1**: 可能包含 aisdlc-lint 基本版 (1-2 個月)
- **v0.06**: 目標完成所有工具基本版 (3-6 個月)

根據使用者反饋調整優先級。

### Q5: 我需要等工具開發完才能使用 AISDLC 嗎？
**A**: **不需要！** v0.06 已經完全可用：
- ✅ 所有 Workflows 可執行
- ✅ 所有文檔模板可用
- ✅ 所有手動流程完善
- 工具只是錦上添花，非必需

---

## 📚 相關資源

### 規劃文檔
- [OPTIMIZATION_PRIORITY_MATRIX.md](../OPTIMIZATION_PRIORITY_MATRIX.md) - P2-13 工具優先級
- [PHASED_IMPLEMENTATION_PLAN.md](../PHASED_IMPLEMENTATION_PLAN.md) - 工具開發時程
- [QUICK_WINS_GUIDE.md](../QUICK_WINS_GUIDE.md) - 快速改善（不含工具）

### 替代方案文檔
- [ERROR_PREVENTION_SYSTEM.md](../ERROR_PREVENTION_SYSTEM.md)
- [CHECKPOINT_RECOVERY_SYSTEM.md](../CHECKPOINT_RECOVERY_SYSTEM.md)
- [KPI_VERIFICATION_PLAN.md](../KPI_VERIFICATION_PLAN.md)
- [SMART_DEFAULTS.md](../SMART_DEFAULTS.md)

---

## 📞 聯絡與反饋

**工具需求反饋**:
- 若您認為某個工具特別重要，請在 GitHub Issues 提出
- 標籤: `enhancement`, `tool-request`
- 說明: 工具用途、預期功能、優先級理由

**工具貢獻**:
- 提交 Pull Request
- 標籤: `contribution`, `tool`
- 包含: 工具程式碼、README、測試、範例

---

**最後更新**: 2026-02-07
**框架版本**: AISDLC v0.09
**安裝腳本版本**: v3.2
**維護者**: AISDLC Framework Team

**注意**: 本目錄將隨著 v0.06.1, v0.06.1, v0.06 逐步填充工具。在此之前，請使用文檔化替代方案。
