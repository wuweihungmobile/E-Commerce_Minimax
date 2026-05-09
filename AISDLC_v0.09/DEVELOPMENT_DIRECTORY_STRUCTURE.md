# 專案文檔目錄結構 - 開發專注版

## 文檔組織原則

本專案文檔遵循 AISDLC v0.09 框架（開發專注版），**專為 2 人開發團隊設計**，移除會議相關流程，採用分層分類的目錄結構，專注於產品研發、規格撰寫、架構設計、系統開發、系統測試、開發品質與文件維護。

**🔴 重要提醒**：
- ✅ **專案文件直接寫入 `AISDLC_v0.0x/docs/` 目錄**
- ✅ **不需要在專案根目錄另建 `docs/` 目錄**
- ✅ **AISDLC 框架本身就是專案工作目錄**

---

## 🎯 開發專注版特色

- ✅ **專注開發**: 移除會議記錄、Kickoff 會議等流程
- ✅ **精簡高效**: 適合 2 人團隊，快速迭代
- ✅ **品質優先**: 強化程式碼品質、安全合規、測試覆蓋
- ✅ **文檔驅動**: 需求→設計→實作→測試，完整可追溯

---

## 目錄結構

```
docs/
├── README.md                           # 本文檔（文檔導覽）
│
├── 01_requirements/                    # 需求文檔
│   ├── PRD_Project_Name.md             # 產品需求文檔（Product Requirements Document）
│   ├── FRD_Module_Name.md              # 功能需求文檔（Functional Requirements Document）
│   ├── Epic_UserStory_Backlog.md       # Epic & User Story Backlog
│   └── Requirement_Change_Log.md       # 需求變更記錄
│
├── 02_architecture/                    # 架構設計文檔
│   ├── SRD_System_Design.md            # 系統需求文檔（System Requirements Document）
│   ├── API_Specification.md            # API 規格文檔
│   ├── Database_Schema.md              # 資料庫架構設計
│   └── Tech_Stack_Selection.md         # 技術選型文檔
│
├── 03_testing/                         # 測試文檔
│   ├── Test_Plan.md                    # 測試計畫
│   ├── Test_Cases.md                   # 測試案例
│   ├── Acceptance_Test_Report.md       # 驗收測試報告
│   └── Bug_Tracking_Log.md             # Bug 追蹤記錄
│
├── 04_planning/                        # 開發規劃
│   ├── Development_Roadmap.md          # 開發路線圖
│   ├── Effort_Estimation.md            # 工作量估算
│   ├── Task_Breakdown.md               # 任務拆解
│   └── Priority_Matrix.md              # 優先級矩陣（RICE/MoSCoW）
│
├── 05_development/                     # 開發文檔
│   ├── Development_Master_Plan.md      # 開發主計畫
│   ├── iteration_1/                    # 迭代 1 文檔
│   │   ├── Iteration_1_Plan.md         # 迭代 1 開發計畫
│   │   ├── Daily_Progress_Log.md       # 每日開發進度記錄
│   │   ├── Technical_Research_POC.md   # 技術研究與 POC
│   │   ├── Implementation_Notes.md     # 實作筆記
│   │   └── Iteration_1_Report.md       # 迭代 1 完成報告
│   ├── iteration_2/                    # 迭代 2 文檔
│   └── Code_Review_Checklist.md        # 程式碼審查檢查清單
│
├── 06_quality/                         # 品質保證文檔
│   ├── Code_Quality_Standards.md       # 程式碼品質標準
│   ├── Security_Compliance_Checklist.md # 安全合規檢查清單
│   ├── Performance_Optimization.md     # 效能優化記錄
│   ├── Technical_Debt_Register.md      # 技術債務登記簿
│   └── QA_Review_Report.md             # QA 審查報告
│
├── 07_design/                          # 設計文檔
│   ├── ui_ux/                          # UI/UX 設計稿
│   ├── database/                       # 資料庫設計
│   └── api/                            # API 設計
│
└── 08_deployment/                      # 部署文檔
    ├── ci_cd/                          # CI/CD 設定
    ├── release_notes/                  # 發布說明
    └── deployment_guide/               # 部署指南
```

---

## 文檔分類說明

### 01_requirements/ - 需求文檔
存放所有需求相關文檔，包括 PRD、FRD、User Stories 等。

**負責角色**: BA + SA

**主要文檔**:
- `PRD_Project_Name.md`: 產品需求文檔（Product Requirements Document）
- `FRD_Module_Name.md`: 功能需求文檔（Functional Requirements Document），按模組拆分
- `Epic_UserStory_Backlog.md`: Epic & User Stories Backlog
- `Requirement_Change_Log.md`: 需求變更記錄

**更新時機**: 需求變更時立即更新

---

### 02_architecture/ - 架構設計文檔
存放系統架構、技術選型、API 設計、資料庫設計等文檔。

**負責角色**: SA + SD

**主要文檔**:
- `SRD_System_Design.md`: 系統需求文檔（System Requirements Document）
- `API_Specification.md`: API 規格文檔
- `Database_Schema.md`: 資料庫架構設計
- `Tech_Stack_Selection.md`: 技術選型文檔

**更新時機**: 架構變更時立即更新

---

### 03_testing/ - 測試文檔
存放測試計畫、測試案例、測試報告、Bug 追蹤等文檔。

**負責角色**: QA + Dev

**主要文檔**:
- `Test_Plan.md`: 測試計畫（含測試策略、測試範圍）
- `Test_Cases.md`: 測試案例（按模組拆分）
- `Acceptance_Test_Report.md`: 驗收測試報告
- `Bug_Tracking_Log.md`: Bug 追蹤記錄

**更新時機**: 每個迭代更新

---

### 04_planning/ - 開發規劃
存放開發路線圖、工作量估算、任務拆解、優先級矩陣等文檔。

**負責角色**: PM/PO + 開發團隊

**主要文檔**:
- `Development_Roadmap.md`: 開發路線圖（長期規劃）
- `Effort_Estimation.md`: 工作量估算（Story Points / 工時）
- `Task_Breakdown.md`: 任務拆解（User Story → Task）
- `Priority_Matrix.md`: 優先級矩陣（RICE/MoSCoW）

**更新時機**: 每個迭代規劃時更新

---

### 05_development/ - 開發文檔
存放開發迭代計畫、每日進度、技術研究、實作筆記等文檔。

**負責角色**: Dev + SA

**目錄結構**:
```
05_development/
├── Development_Master_Plan.md      # 開發主計畫（總覽）
├── iteration_1/                    # 迭代 1 文檔
│   ├── Iteration_1_Plan.md         # 迭代 1 開發計畫
│   ├── Daily_Progress_Log.md       # 每日開發進度記錄
│   ├── Technical_Research_POC.md   # 技術研究與 POC
│   ├── Implementation_Notes.md     # 實作筆記
│   └── Iteration_1_Report.md       # 迭代 1 完成報告
├── iteration_2/                    # 迭代 2 文檔
└── Code_Review_Checklist.md        # 程式碼審查檢查清單
```

**更新時機**: 每日更新進度記錄，迭代結束時更新報告

**迭代文檔類型說明**:
- **開發計畫** (`Iteration_X_Plan.md`): 迭代的時程與任務分配
- **每日進度記錄** (`Daily_Progress_Log.md`): 每日完成任務與遇到問題
- **技術研究與 POC** (`Technical_Research_POC.md`): 技術驗證與 POC 結果
- **實作筆記** (`Implementation_Notes.md`): 重要設計決策與實作細節
- **完成報告** (`Iteration_X_Report.md`): 迭代完成總結與下一步計畫

---

### 06_quality/ - 品質保證文檔
存放程式碼品質標準、安全合規、效能優化、技術債務等文檔。

**負責角色**: QA + Dev + Security

**主要文檔**:
- `Code_Quality_Standards.md`: 程式碼品質標準（Linting, Coverage, Complexity）
- `Security_Compliance_Checklist.md`: 安全合規檢查清單（OWASP Top 10, GDPR, etc.）
- `Performance_Optimization.md`: 效能優化記錄（效能瓶頸、優化方案）
- `Technical_Debt_Register.md`: 技術債務登記簿（Tech Debt 追蹤）
- `QA_Review_Report.md`: QA 審查報告（每個迭代的品質評估）

**更新時機**: 每個迭代結束時更新

---

### 07_design/ - 設計文檔
存放 UI/UX 設計、資料庫設計、API 設計等文檔。

**負責角色**: UI/UX Designer + SA

**目錄結構**:
```
07_design/
├── ui_ux/                      # UI/UX 設計稿（Figma 連結、設計圖）
├── database/                   # 資料庫 ER Diagram、Schema
└── api/                        # API 設計文檔（Swagger/OpenAPI）
```

**更新時機**: 設計變更時立即更新

---

### 08_deployment/ - 部署文檔
存放 CI/CD 設定、發布說明、部署指南等文檔。

**負責角色**: DevOps + Dev

**目錄結構**:
```
08_deployment/
├── ci_cd/                      # CI/CD 設定文檔
│   ├── CI_CD_Setup.md          # CI/CD Pipeline 設定指南
│   └── Build_Configuration.md  # 建置設定文檔
├── release_notes/              # 發布說明（每個版本）
└── deployment_guide/           # 部署指南
```

**更新時機**: 每次發布時更新

---

## 文檔命名規範

### 檔案命名
- 使用 **PascalCase** 或 **Snake_Case**
- 英文命名，避免中文檔名
- 包含文檔類型前綴（如 PRD_, FRD_, Sprint_）

**範例**:
- ✅ `PRD_MoneyTracker_Pro.md`
- ✅ `Sprint_1_Execution_Plan.md`
- ✅ `Architecture_Design_Document.md`
- ❌ `專案需求文檔.md`
- ❌ `sprint1.md`

### 目錄命名
- 使用 **數字前綴 + 英文小寫 + 底線**
- 便於排序與識別

**範例**:
- ✅ `01_requirements/`
- ✅ `05_development/`
- ❌ `需求文檔/`
- ❌ `Sprint/`

---

## 文檔版本控制

### 版本號規則
- 使用 `v主版本.次版本` 格式（如 v1.0, v1.1, v2.0）
- 主版本變更: 文檔結構或內容重大變更
- 次版本變更: 內容增補或小幅修訂

### 文檔元數據
每個文檔應包含以下元數據（置於文檔開頭或結尾）:

```markdown
## 文檔元數據
- **專案名稱**: MoneyTracker Pro
- **文檔類型**: [PRD/FRD/架構設計/測試計畫...]
- **文檔版本**: v1.0
- **建立日期**: YYYY-MM-DD
- **最後更新**: YYYY-MM-DD
- **負責人**: [姓名 (角色)]
- **文檔狀態**: [Draft/Review/Final]
```

---

## 文檔狀態定義

| 狀態 | 說明 |
|------|------|
| **Draft** | 草稿階段，內容未完成 |
| **Review** | 評審階段，等待 Stakeholder 審核 |
| **Final** | 最終版本，已通過審核 |
| **Archived** | 已歸檔，不再使用（舊版本文檔） |

---

## 快速導覽（2 人團隊）

### 新專案啟動，應該先看哪些文檔?

1. **了解需求**: [PRD_Project_Name.md](01_requirements/PRD_Project_Name.md)
2. **了解架構**: [SRD_System_Design.md](02_architecture/SRD_System_Design.md)
3. **開發規範**: [CLAUDE.md](../CLAUDE.md) 或 專案 CLAUDE.md
4. **開發路線圖**: [Development_Roadmap.md](04_planning/Development_Roadmap.md)

---

### 開發人員（Developer）需要哪些文檔?

1. **需求文檔**: `01_requirements/FRD_*.md`（功能需求）
2. **架構文檔**: `02_architecture/SRD_System_Design.md`（系統架構）
3. **API 文檔**: `02_architecture/API_Specification.md`（API 規格）
4. **測試要求**: `03_testing/Test_Plan.md`（測試計畫）
5. **迭代計畫**: `05_development/iteration_X/Iteration_X_Plan.md`（當前迭代任務）
6. **品質標準**: `06_quality/Code_Quality_Standards.md`（程式碼品質標準）

---

### QA 工程師需要哪些文檔?

1. **需求文檔**: `01_requirements/Epic_UserStory_Backlog.md`（User Stories & AC）
2. **測試計畫**: `03_testing/Test_Plan.md`
3. **測試案例**: `03_testing/Test_Cases.md`
4. **迭代計畫**: `05_development/iteration_X/Iteration_X_Plan.md`
5. **品質報告**: `06_quality/QA_Review_Report.md`

---

### PM/PO 需要哪些文檔?

1. **開發路線圖**: `04_planning/Development_Roadmap.md`
2. **工作量估算**: `04_planning/Effort_Estimation.md`
3. **優先級矩陣**: `04_planning/Priority_Matrix.md`
4. **開發進度**: `05_development/iteration_X/Daily_Progress_Log.md`
5. **User Stories**: `01_requirements/Epic_UserStory_Backlog.md`

---

## 文檔維護原則（2 人團隊）

### 1. 文檔所有權
每份文檔應有明確的負責角色，2 人團隊可互相 Review。

### 2. 文檔更新時機
- **需求變更**: 立即更新相關 PRD/FRD，同步更新 Requirement_Change_Log.md
- **架構變更**: 立即更新 SRD/API Specification
- **迭代結束**: 更新迭代報告、品質報告
- **發布前**: 更新 Release Notes、部署文檔

### 3. 文檔審核流程（簡化版）
1. 建立/更新文檔（狀態: Draft）
2. 另一位團隊成員 Review（狀態: Review）
3. 修正後通過審核（狀態: Final）

### 4. 文檔歸檔
- 舊版本文檔移至 `docs/archive/` 目錄
- 檔名加上版本號（如 `PRD_Project_v1.0.md`）
- 保留最近 2-3 個版本即可

---

## 附錄

### A. 文檔模板位置
- AISDLC 文檔模板: `AISDLC_v0.09/docs_template/`
- 專案自訂模板: `docs/templates/`（可選）

### B. 推薦工具
- **Markdown 編輯器**: VS Code + Markdown Preview Enhanced
- **圖表工具**: draw.io, Mermaid
- **API 文檔**: Swagger/OpenAPI
- **版本控制**: Git + GitHub/GitLab

### C. 2 人團隊協作建議
- **文檔協作**: 使用 Git 進行版本控制，Pull Request 互相 Review
- **進度同步**: 每日更新 Daily_Progress_Log.md，快速同步進度
- **問題追蹤**: 使用 Bug_Tracking_Log.md 或 GitHub Issues
- **知識分享**: 在 Implementation_Notes.md 記錄重要決策與技術細節

---

## 文檔元數據
- **文檔名稱**: 專案文檔目錄結構 - 開發專注版
- **文檔類型**: 文檔導覽
- **文檔版本**: v2.0（開發專注版）
- **建立日期**: 2025-01-11
- **最後更新**: 2025-01-11
- **適用框架**: AISDLC v0.09（開發專注版）
- **文檔狀態**: Final
- **備註**: 專為 2 人開發團隊設計，移除會議相關流程
