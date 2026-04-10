# AISDLC 專案文檔輸出目錄（開發專注版 v0.09）

**版本**: v0.09
**最後更新**: 2026-01-12
**目錄類型**: 專案工作目錄

---

## 🔴 重要說明

**此目錄是 AISDLC 專案的主要工作目錄**

- ✅ **所有專案文檔直接寫入此目錄的子目錄中**
- ✅ **遵循 8 層編號目錄結構（開發專注版）**
- ✅ **不需要在其他地方建立專案目錄**

---

## 📁 目錄結構（開發專注版 v0.09）

```
docs/
├── 01_requirements/        # 需求文檔
│   ├── PRD_*.md           # Product Requirements Document
│   ├── FRD_*.md           # Functional Requirements Document
│   ├── User_Stories_*.md  # User Stories
│   └── Epic_Backlog_*.md  # Epic Backlog
│
├── 02_architecture/        # 架構設計
│   ├── SRD_*.md           # System Requirements Document
│   ├── API_Specification_*.md  # API 規格文檔
│   ├── Architecture_Diagram_*.md  # 架構圖
│   └── Database_Schema_*.md     # 資料庫架構
│
├── 03_testing/            # 測試文檔
│   ├── Test_Plan_*.md     # 測試計畫
│   ├── Test_Cases_*.md    # 測試案例
│   ├── AT_*.md            # Acceptance Tests
│   └── Test_Report_*.md   # 測試報告
│
├── 04_planning/           # 專案規劃（取代 04_project_management）
│   ├── Roadmap_*.md       # 產品路線圖
│   ├── Effort_Estimation_*.md  # 工作量估算
│   └── Task_Breakdown_*.md     # 任務分解
│
├── 05_development/        # 迭代執行（取代 05_sprint）
│   ├── Iteration_*_Plan.md      # 迭代計畫
│   ├── Progress_Log_*.md        # 進度日誌
│   └── Sprint_Report_*.md       # Sprint 報告
│
├── 06_quality/            # 程式碼品質（取代 06_meeting_minutes）
│   ├── Code_Quality_Report_*.md  # 程式碼品質報告
│   ├── Security_Audit_*.md       # 安全稽核
│   └── Performance_Analysis_*.md # 效能分析
│
├── 07_design/             # 設計文檔
│   ├── UI_UX_Design_*.md  # UI/UX 設計
│   ├── Database_Design_*.md  # 資料庫設計
│   └── ER_Diagram_*.md    # 實體關聯圖
│
└── 08_deployment/         # 部署維運
    ├── CI_CD_Config_*.md  # CI/CD 配置
    ├── Release_Notes_*.md # 發布說明
    ├── Deployment_Guide_*.md  # 部署指南
    └── CHANGELOG_*.md     # 變更日誌
```

---

## 🎯 各子目錄用途

### 01_requirements/ - 需求文檔
**用途**: 存放所有需求相關文檔

**典型文檔**:
- `PRD_ProjectName.md` - 產品需求文檔
- `FRD_ModuleName.md` - 功能需求文檔
- `User_Stories_Sprint_1.md` - 使用者故事
- `Epic_Backlog.md` - Epic 待辦清單

**產生時機**: 需求分析階段、Workflow 1-2 執行後

---

### 02_architecture/ - 架構設計
**用途**: 存放系統架構與技術設計文檔

**典型文檔**:
- `SRD_SystemName.md` - 系統需求文檔
- `API_Specification_Payment.md` - API 規格
- `Architecture_Diagram.md` - 系統架構圖
- `Database_Schema.md` - 資料庫架構

**產生時機**: 架構設計階段、Workflow 3-5 執行後

---

### 03_testing/ - 測試文檔
**用途**: 存放所有測試相關文檔

**典型文檔**:
- `Test_Plan_Acceptance_Testing.md` - 測試計畫
- `AT_Core_Transaction_Module.md` - 驗收測試
- `Test_Report_Sprint_1.md` - 測試報告

**產生時機**: 測試設計與執行階段

---

### 04_planning/ - 專案規劃
**用途**: 存放專案規劃、工作量估算、任務分解

**典型文檔**:
- `Roadmap_2026_Q1.md` - 產品路線圖
- `Effort_Estimation_Resource_Planning.md` - 工作量估算
- `Task_Breakdown_Sprint_1.md` - 任務分解

**產生時機**: 專案啟動、Sprint 規劃

**🆕 開發專注版變更**: 取代 `04_project_management/`，更聚焦於開發規劃

---

### 05_development/ - 迭代執行
**用途**: 存放迭代計畫、進度日誌、Sprint 報告

**典型文檔**:
- `Iteration_1_Plan.md` - 迭代 1 計畫
- `Progress_Log_Week_1.md` - 每週進度日誌
- `Sprint_Report_Sprint_1.md` - Sprint 總結報告

**產生時機**: 每個 Sprint/Iteration 開始與結束

**🆕 開發專注版變更**: 取代 `05_sprint/`，更明確表達迭代開發

---

### 06_quality/ - 程式碼品質
**用途**: 存放程式碼品質、安全、效能相關文檔

**典型文檔**:
- `Code_Quality_Report_Sprint_1.md` - 程式碼品質報告
- `Security_Audit_Report.md` - 安全稽核報告
- `Performance_Analysis_Report.md` - 效能分析報告

**產生時機**: Code Review、安全掃描、效能測試後

**🆕 開發專注版變更**: 新增目錄，取代 `06_meeting_minutes/`（2 人團隊不需要會議記錄）

---

### 07_design/ - 設計文檔
**用途**: 存放 UI/UX 設計、資料庫設計

**典型文檔**:
- `UI_UX_Design_Mockups.md` - UI/UX 設計稿
- `Database_Design.md` - 資料庫設計文檔
- `ER_Diagram.md` - 實體關聯圖

**產生時機**: 設計階段

---

### 08_deployment/ - 部署維運
**用途**: 存放 CI/CD 配置、發布說明、部署指南

**典型文檔**:
- `CI_CD_Pipeline_Config.md` - CI/CD 管線配置
- `Release_Notes_v1.0.0.md` - 發布說明
- `Deployment_Guide.md` - 部署指南
- `CHANGELOG_v1.0.0.md` - 變更日誌

**產生時機**: 發布準備、部署執行

---

## 📝 文檔命名規範

### 通用規則
- **格式**: 使用 PascalCase 或 Snake_Case
- **語言**: 英文命名（避免中文檔名）
- **前綴**: 包含文檔類型前綴

### 範例

**需求文檔** (`01_requirements/`):
```
PRD_MoneyTracker_Pro.md
FRD_User_Authentication.md
User_Stories_Sprint_1.md
Epic_Backlog_Phase_1.md
```

**架構設計** (`02_architecture/`):
```
SRD_Payment_System.md
API_Specification_Cloud_Sync.md
Architecture_Diagram_Microservices.md
```

**測試文檔** (`03_testing/`):
```
Test_Plan_Acceptance_Testing.md
AT_Core_Transaction_Module.md
Test_Report_Sprint_1.md
```

**專案規劃** (`04_planning/`):
```
Roadmap_2026_Q1.md
Effort_Estimation_Resource_Planning.md
Task_Breakdown_Sprint_1.md
```

**迭代執行** (`05_development/`):
```
Iteration_1_Plan.md
Progress_Log_Week_1.md
Sprint_Report_Sprint_1.md
```

**程式碼品質** (`06_quality/`):
```
Code_Quality_Report_Sprint_1.md
Security_Audit_Report.md
Performance_Analysis_Report.md
```

---

## 🔍 使用指南

### 開始新專案

1. **確認目錄結構存在**:
   ```bash
   cd /path/to/AISDLC_ALL/AISDLC_v0.09
   ls -la docs/
   ```

2. **如果子目錄缺失，執行初始化**:
   ```bash
   bash tools/init_project.sh
   ```

3. **開始撰寫文檔**:
   ```bash
   # 撰寫 PRD
   vim docs/01_requirements/PRD_YourProject.md

   # 撰寫 SRD
   vim docs/02_architecture/SRD_System_Design.md
   ```

### 文檔追蹤與版本控制

建議在每個子目錄中建立 `.gitkeep` 或 `README.md` 來追蹤空目錄：

```bash
# 為每個子目錄建立 README
for dir in docs/*/; do
  echo "# $(basename $dir)" > "$dir/README.md"
  echo "此目錄存放 $(basename $dir) 相關文檔" >> "$dir/README.md"
done
```

---

## 🔗 相關文檔

- [DEVELOPMENT_DIRECTORY_STRUCTURE.md](../DEVELOPMENT_DIRECTORY_STRUCTURE.md) - 完整目錄結構說明
- [PROJECT_INITIALIZATION_GUIDE.md](../guides/user/onboarding/PROJECT_INITIALIZATION_GUIDE.md) - 專案初始化指南
- [Document_Quality_Checklist.md](../guides/system/quality/Document_Quality_Checklist.md) - 文檔品質檢查清單

---

**維護者**: AISDLC Framework Team
**最後更新**: 2026-01-12
**版本**: v0.09（開發專注版）
