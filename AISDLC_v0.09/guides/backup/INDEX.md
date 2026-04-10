# AISDLC v0.02 完整索引

## 📑 文檔導航

快速找到你需要的文檔和資源。

---

## 🌟 新手必讀 (按順序閱讀)

1. **[README.md](README.md)** ⭐⭐⭐⭐⭐
   - AISDLC v0.02 完整介紹
   - v0.01 vs v0.02 對比
   - 10 大情境說明
   - 30 秒快速啟動

2. **[QUICK_START_GUIDE.md](QUICK_START_GUIDE.md)** ⭐⭐⭐⭐⭐
   - 5 分鐘上手指南
   - 根據情況快速定位
   - 常用指令速查
   - 功能可用性速查表

3. **[SCENARIO_SELECTOR.md](SCENARIO_SELECTOR.md)** ⭐⭐⭐⭐
   - 情境選擇決策樹
   - 詳細情境說明
   - 選擇矩陣和問卷
   - 平台選擇指南

---

## 🎯 核心配置文件

### 必須載入
- **[AISDLC_INIT.md](AISDLC_INIT.md)** ⭐⭐⭐⭐⭐
  - 框架初始化配置
  - 情境-Workflow 映射
  - 智能情境識別
  - 載入方式說明

### 重要參考
- **[VERSION_RELEASE_NOTES.md](VERSION_RELEASE_NOTES.md)**
  - 版本發布說明
  - 新增功能列表
  - 已知限制
  - 未來規劃

- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)**
  - 實施摘要
  - 完成度統計
  - 立即可用功能
  - 待完善項目

---

## 🔄 升級相關

- **[UPGRADE_FROM_V01.md](UPGRADE_FROM_V01.md)**
  - 從 v0.01 升級指南
  - 版本對比
  - 升級步驟
  - 相容性說明
  - 常見問題

---

## 🌱 情境專用資源

### Greenfield (新專案開發) - 完全可用 ✅

**主要文檔**：
- **[scenarios/greenfield/SOP.md](scenarios/greenfield/SOP.md)** ⭐⭐⭐⭐⭐
  - 完整標準作業程序
  - 9 個執行階段
  - 時間分配規劃
  - 確認點設計
  - 產出文件清單
  - 最佳實踐和陷阱

**配套資源**：
- scenarios/greenfield/checklist.md (待創建)
- scenarios/greenfield/templates/ (待創建)
- scenarios/greenfield/examples/ (待創建)

### Brownfield (舊專案維護) - 框架可用 🟡
- scenarios/brownfield/SOP.md (待創建)
- 可使用 Code Analyzer Agent

### Refactoring (系統重構) - 框架可用 🟡
- scenarios/refactoring/SOP.md (待創建)
- 可使用 Code Analyzer Agent

### Performance (效能調校) - Agent 可用 ✅
- scenarios/performance/SOP.md (待創建)
- 可使用 Performance Engineer Agent

### Integration (第三方整合) - 框架可用 🟡
- scenarios/integration/SOP.md (待創建)

### DevOps (CI/CD 與部署) - 框架可用 🟡
- scenarios/devops/SOP.md (待創建)

### Testing (測試與 QA) - 框架可用 🟡
- scenarios/testing/SOP.md (待創建)

### Documentation (文件維護) - 框架可用 🟡
- scenarios/documentation/SOP.md (待創建)

---

## 🤖 Agent 配置

### 核心 Agents (繼承 v0.01) ✅
位置：[agent/core/](agent/core/)

1. **sa-analyst.yaml** - Amanda (系統分析師)
2. **ba-business-analyst.yaml** - Beatrice (業務分析師)
3. **pm-po-agent.yaml** - Victoria (產品經理)
4. **sd-architect.yaml** - Marcus (系統設計師)
5. **dev-developer.yaml** - 開發工程師
6. **qa-tester.yaml** - Quincy (測試工程師)
7. **agent-template.yaml** - Agent 模板
8. **agent-configs.md** - 配置說明

### 專用 Agents (v0.02 新增)
位置：[agent/specialized/](agent/specialized/)

#### 已完成 ✅
1. **[code-analyzer.yaml](agent/specialized/code-analyzer.yaml)** ⭐⭐⭐⭐⭐
   - CodeX (代碼分析專家)
   - 代碼結構分析
   - 依賴關係映射
   - 技術債務評估
   - 重構規劃

2. **[performance-engineer.yaml](agent/specialized/performance-engineer.yaml)** ⭐⭐⭐⭐⭐
   - Perf (效能工程師)
   - 效能剖析分析
   - 瓶頸識別
   - 優化策略制定
   - 監控方案設計

#### 待創建 🔴
3. integration-specialist.yaml - 整合專家
4. devops-engineer.yaml - DevOps 工程師
5. qa-lead.yaml - 測試負責人
6. qa-automation.yaml - 自動化測試專家
7. technical-writer.yaml - 技術文件專家
8. dev-senior.yaml - 資深開發者

### 平台特化 Agents (規劃中) 🔴
9. sd-web-architect.yaml - Web 架構師
10. sd-mobile-architect.yaml - Mobile 架構師
11. qa-web-tester.yaml - Web 測試專家
12. qa-mobile-tester.yaml - Mobile 測試專家

---

## 🔄 Workflow 定義

### 核心 Workflows (繼承 v0.01) ✅
位置：workflow/core/ (需從 v0.01 複製)

1. requirements-extraction.md
2. validation-documentation.md
3. user-story-design.md
4. change-management.md
5. api-specification.md
6. consistency-check.md
7. interaction-analysis.md

### 情境專用 Workflows (v0.02 新增) 🔴
位置：workflow/scenario-specific/ (待創建)

1. greenfield-complete-flow.md
2. brownfield-analysis-flow.md
3. refactoring-planning-flow.md
4. performance-optimization-flow.md
5. integration-analysis-flow.md
6. devops-setup-flow.md
7. testing-strategy-flow.md
8. documentation-flow.md
9. code-analysis-flow.md
10. tech-stack-selection-flow.md

---

## 📝 Prompt 模板

### 快速啟動 (待創建)
- prompts/quick-start/greenfield-quick.md
- prompts/quick-start/brownfield-quick.md
- prompts/quick-start/performance-quick.md

### 完整流程 (待創建)
- prompts/complete-flow/greenfield-complete.md
- prompts/complete-flow/brownfield-complete.md

### 情境專用 (待創建)
- prompts/scenario-prompts/greenfield-prompts.md
- prompts/scenario-prompts/brownfield-prompts.md
- prompts/scenario-prompts/refactoring-prompts.md
- prompts/scenario-prompts/performance-prompts.md
- prompts/scenario-prompts/integration-prompts.md
- prompts/scenario-prompts/devops-prompts.md
- prompts/scenario-prompts/testing-prompts.md
- prompts/scenario-prompts/documentation-prompts.md

---

## 📄 文檔模板

### 需求文檔 (繼承 v0.01)
- docs_template/prd/PRD_Template.md
- docs_template/frd/FRD_Module_Template.md

### 系統文檔 (繼承 v0.01)
- docs_template/srd/SRD_Module_Template.md
- docs_template/srd/API_Specification_Template.md
- docs_template/srd/API_Index_Template.md

### 測試文檔 (繼承 v0.01)
- docs_template/tests/Acceptance_Test_Template.md
- docs_template/tests/Test_Plan_Template.md

### 分析文檔 (v0.02 新增，待創建) 🔴
- docs_template/analysis/Code_Analysis_Report_Template.md
- docs_template/analysis/Refactoring_Plan_Template.md
- docs_template/analysis/Dependency_Map_Template.md
- docs_template/analysis/Technical_Debt_Register_Template.md

### 效能文檔 (v0.02 新增，待創建) 🔴
- docs_template/performance/Performance_Analysis_Report_Template.md
- docs_template/performance/Optimization_Strategy_Template.md
- docs_template/performance/Performance_Test_Plan_Template.md
- docs_template/performance/Performance_Monitoring_Setup_Template.md

### 運維文檔 (v0.02 新增，待創建) 🔴
- docs_template/operations/DevOps_Setup_Guide_Template.md
- docs_template/operations/CI_CD_Pipeline_Template.md
- docs_template/operations/Deployment_Guide_Template.md

---

## 📚 輔助文檔

### 使用指南
- [README.md](README.md) - 主要說明文檔
- [QUICK_START_GUIDE.md](QUICK_START_GUIDE.md) - 快速上手
- [SCENARIO_SELECTOR.md](SCENARIO_SELECTOR.md) - 情境選擇

### 參考資料
- [VERSION_RELEASE_NOTES.md](VERSION_RELEASE_NOTES.md) - 版本說明
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - 實施摘要
- [UPGRADE_FROM_V01.md](UPGRADE_FROM_V01.md) - 升級指南

### Claude Code 專用
- [CLAUDE.md](../CLAUDE.md) - Claude Code 使用指南 (位於根目錄)

---

## 🔍 快速查找

### 我想要...

**開發新專案**
→ [Greenfield SOP](scenarios/greenfield/SOP.md)

**分析既有代碼**
→ [Code Analyzer Agent](agent/specialized/code-analyzer.yaml)

**優化系統效能**
→ [Performance Engineer Agent](agent/specialized/performance-engineer.yaml)

**選擇適合的情境**
→ [SCENARIO_SELECTOR](SCENARIO_SELECTOR.md)

**快速上手**
→ [QUICK_START_GUIDE](QUICK_START_GUIDE.md)

**從 v0.01 升級**
→ [UPGRADE_FROM_V01](UPGRADE_FROM_V01.md)

**了解完成狀態**
→ [IMPLEMENTATION_SUMMARY](IMPLEMENTATION_SUMMARY.md)

**查看新功能**
→ [VERSION_RELEASE_NOTES](VERSION_RELEASE_NOTES.md)

---

## 📊 文檔狀態圖例

| 圖例 | 說明 | 範例 |
|------|------|------|
| ⭐⭐⭐⭐⭐ | 必讀重點 | README.md |
| ✅ | 已完成可用 | Greenfield SOP |
| 🟡 | 部分可用 | Brownfield 情境 |
| 🔴 | 待創建 | 其他 Agents |

---

## 🗂️ 目錄結構總覽

```
AISDLC_v0.02/
│
├── 📘 核心文檔
│   ├── README.md ⭐⭐⭐⭐⭐
│   ├── QUICK_START_GUIDE.md ⭐⭐⭐⭐⭐
│   ├── SCENARIO_SELECTOR.md ⭐⭐⭐⭐
│   ├── AISDLC_INIT.md ⭐⭐⭐⭐⭐
│   ├── VERSION_RELEASE_NOTES.md
│   ├── IMPLEMENTATION_SUMMARY.md
│   ├── UPGRADE_FROM_V01.md
│   ├── INDEX.md (本文件)
│   └── CLAUDE.md (位於根目錄)
│
├── 🤖 agent/ - Agent 配置
│   ├── core/ (8 個，繼承 v0.01) ✅
│   └── specialized/ (8 個，2 完成 6 待建) 🟡
│
├── 🔄 workflow/ - Workflow 定義
│   ├── core/ (7 個，繼承 v0.01) ✅
│   └── scenario-specific/ (10 個，待創建) 🔴
│
├── 🎯 scenarios/ - 情境專用資源
│   ├── greenfield/ (完整 SOP) ✅
│   ├── brownfield/ (待創建) 🔴
│   ├── refactoring/ (待創建) 🔴
│   ├── performance/ (待創建) 🔴
│   ├── integration/ (待創建) 🔴
│   ├── devops/ (待創建) 🔴
│   ├── testing/ (待創建) 🔴
│   └── documentation/ (待創建) 🔴
│
├── 💬 prompts/ - Prompt 模板
│   ├── scenario-prompts/ (待創建) 🔴
│   ├── quick-start/ (待創建) 🔴
│   └── complete-flow/ (待創建) 🔴
│
└── 📄 docs_template/ - 文檔模板
    ├── prd/ (繼承 v0.01) ✅
    ├── frd/ (繼承 v0.01) ✅
    ├── srd/ (繼承 v0.01) ✅
    ├── tests/ (繼承 v0.01) ✅
    ├── analysis/ (待創建) 🔴
    ├── performance/ (待創建) 🔴
    └── operations/ (待創建) 🔴
```

---

## 🎓 學習路徑推薦

### 第一次使用 (1-2 小時)
1. [README.md](README.md) (30 分鐘)
2. [QUICK_START_GUIDE.md](QUICK_START_GUIDE.md) (20 分鐘)
3. [SCENARIO_SELECTOR.md](SCENARIO_SELECTOR.md) (20 分鐘)
4. 選擇情境試用 (30-60 分鐘)

### 深入學習 (半天)
1. 完整閱讀主要文檔 (2 小時)
2. 詳讀 Greenfield SOP (1 小時)
3. 試用專用 Agents (1-2 小時)

### 團隊導入 (1 天)
1. 技術負責人學習 (半天)
2. 試點專案實踐 (半天)
3. 團隊培訓和推廣 (ongoing)

---

## 💡 使用技巧

### 善用搜尋
在瀏覽器中使用 Ctrl/Cmd+F 搜尋關鍵字

### 書籤重點
將常用文檔加入書籤：
- QUICK_START_GUIDE.md
- SCENARIO_SELECTOR.md
- 你常用情境的 SOP

### 列印速查表
可以列印或截圖：
- 情境選擇矩陣
- 常用指令速查
- 功能可用性速查表

---

## 📞 需要幫助？

### 找不到想要的文檔？
1. 查看本索引的「快速查找」區塊
2. 使用目錄結構總覽定位
3. 檢查文檔狀態圖例

### 文檔還未創建？
1. 查看 IMPLEMENTATION_SUMMARY.md 了解完成狀態
2. 參考已完成的類似文檔
3. 提交 Issue 建議優先完成

### 不知道從哪開始？
→ 直接閱讀 [QUICK_START_GUIDE.md](QUICK_START_GUIDE.md)

---

**本索引會隨著 v0.02 的完善持續更新** 📝

最後更新：2025-10-18

[回到 README](README.md)
