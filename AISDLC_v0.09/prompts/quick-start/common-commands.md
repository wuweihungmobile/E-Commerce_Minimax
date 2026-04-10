# AISDLC v0.02 常用指令速查
# Common Commands Quick Reference

**版本**: v0.02
**用途**: 最常用指令的快速參考
**最後更新**: 2025-10-22

---

## 🚀 啟動與初始化

### 框架初始化
```
請載入 AISDLC v0.02 框架。
執行指令：請閱讀並載入 /AISDLC_v0.02/AISDLC_INIT.md
```

### 查看可用情境
```
請列出 AISDLC v0.02 所有可用的情境及其適用場景。
```

### 情境選擇協助
```
我不確定應該使用哪個情境。

我的任務是：[描述你的任務]

請協助我選擇最適合的情境。
```

---

## 📊 執行 Workflows

### 啟動標準 Workflow
```
請執行 [workflow-name] workflow。

[提供必要的輸入資訊]
```

### 從特定階段開始
```
我已經完成了 [workflow-name] 的階段 1-3。

請從階段 4 開始繼續執行。
```

### 暫停與恢復
```
# 暫停
請暫停當前 workflow 並保存檢查點。

# 恢復
我想從上次的檢查點繼續執行 [workflow-name]。
```

---

## 🤖 Agent 調用

### 調用特定 Agent
```
請調用 [agent-name] agent 協助我。

任務：[描述任務]
```

### 常用 Agents 快速調用

#### 需求分析 - SA Agent (Amanda)
```
請 SA agent (Amanda) 協助我分析需求。

需求素材：[截圖/文字/混合]
```

#### 技術設計 - SD Agent (Marcus)
```
請 SD architect (Marcus) 協助我設計系統架構。

需求：[FRD 連結或描述]
```

#### 代碼分析 - Code Analyzer (CodeX)
```
請 Code-Analyzer (CodeX) 分析代碼品質。

專案路徑：[路徑]
```

#### 效能分析 - Performance Engineer (Perf)
```
請 Performance-Engineer (Perf) 協助我優化效能。

效能問題：[描述]
```

#### 整合分析 - Integration Specialist (IntegX)
```
請 Integration-Specialist (IntegX) 評估第三方 API。

API 名稱：[名稱]
API 文檔：[連結]
```

---

## 📝 文檔生成

### 生成 PRD
```
請根據以下需求生成 PRD。

業務目標：[描述]
需求描述：[描述]
利害關係人：[列表]
```

### 生成 FRD
```
請根據 PRD 生成詳細的 FRD。

PRD 位置：[路徑或內容]

包含：
- User Stories
- Acceptance Criteria
- 功能規格
```

### 生成 SRD
```
請根據 FRD 生成 SRD。

FRD 位置：[路徑]
技術棧：[已選定的技術棧]

包含：
- 系統架構
- 資料模型
- API 設計
```

### 生成 API 規格
```
請為以下 API 生成詳細規格。

API 端點：[端點名稱]
功能：[描述]
參考 FRD：[連結]
```

---

## ✅ 驗證與檢查

### 文檔一致性檢查
```
請執行 consistency-check workflow。

檢查範圍：
- PRD → FRD → SRD → API Specs 追蹤鏈
- User Stories → Acceptance Criteria → Acceptance Tests
- 術語一致性
- 連結有效性
```

### 需求變更管理
```
需求發生變更，請執行 change-management workflow。

變更內容：[描述變更]
影響文檔：[列出受影響的文檔]
```

### 品質門檻檢查
```
請檢查以下文檔是否符合品質標準。

文檔：[文檔路徑]
標準：[具體標準或使用預設標準]
```

---

## 🔄 常見任務組合指令

### 需求提取 → 驗證 → 文檔化
```
我有一份需求素材，請協助完整處理。

素材：[截圖/文字/混合]

請執行：
1. requirements-extraction（需求提取）
2. validation-documentation（驗證與文檔化）
3. 產出 PRD 和 FRD
```

### FRD → SRD → API 規格
```
我的 FRD 已完成，請繼續後續設計。

FRD 位置：[路徑]

請執行：
1. user-story-design（設計階段）
2. api-specification（API 規格）
3. 產出 SRD 和 API Specs
```

### 代碼分析 → 重構規劃
```
請分析代碼並提供重構建議。

專案路徑：[路徑]

請執行：
1. code-analysis-flow（代碼分析）
2. refactoring-planning-flow（重構規劃）
3. 產出分析報告和重構計畫
```

---

## 🎯 特殊功能指令

### 平台特化
```
# Web 平台
請使用 sd-web-architect 協助我設計 Web 應用架構。

# iOS 平台
請使用 sd-mobile-architect 協助我設計 iOS App 架構。

# Android 平台
請使用 sd-mobile-architect 協助我設計 Android App 架構。
```

### 技術棧選型
```
請執行 tech-stack-selection-flow。

專案需求：
- 專案類型：[Web/Mobile/Backend]
- 團隊背景：[技術熟悉度]
- 效能要求：[高/中/低]
- 預算：[有限/充裕]

請提供 3 組技術棧方案並比較。
```

### 前後端交互分析
```
請執行 interaction-analysis workflow。

系統類型：[Web/Mobile]
前端技術：[React/Vue/等]
後端技術：[Node.js/Python/等]

分析：
- 通訊協議（REST/GraphQL/等）
- 資料格式
- 狀態管理
- 錯誤處理
```

---

## 🆘 問題排查指令

### 尋求幫助
```
我在執行 [workflow/task] 時遇到問題。

問題描述：[詳細描述]
錯誤訊息（若有）：[錯誤]

請協助排查。
```

### 查看 Workflow 詳細說明
```
請詳細說明 [workflow-name] workflow 的：
- 執行步驟
- 所需輸入
- 預期產出
- 預估時間
```

### 查看 Agent 能力
```
請詳細說明 [agent-name] agent 的：
- 主要職責
- 專業能力
- 可產出的文檔
- 適用情境
```

### 查看模板
```
請提供 [template-name] 模板的：
- 完整結構
- 填寫指引
- 範例
```

---

## 🔖 快速參考表

| 需求 | 指令 | Agent | 產出 |
|------|------|-------|------|
| 分析需求 | requirements-extraction | SA (Amanda) | PRD, FRD初稿 |
| 驗證需求 | validation-documentation | SA + BA | 完整 FRD |
| 設計系統 | user-story-design | SD (Marcus) | SRD |
| 制定 API | api-specification | SD (Marcus) | API Specs |
| 分析代碼 | code-analysis-flow | Code-Analyzer (CodeX) | Code Analysis Report |
| 規劃重構 | refactoring-planning-flow | CodeX + Senior Dev | Refactoring Plan |
| 優化效能 | performance-optimization-flow | Perf | Performance Report |
| 整合 API | integration-analysis-flow | IntegX | Integration Design |
| 建立 CI/CD | devops-setup-flow | DevOps Engineer | Pipeline Design |
| 測試策略 | testing-strategy-flow | QA-Lead | Test Strategy |
| 撰寫文檔 | documentation-flow | DocX | Documentation |

---

## 💡 進階技巧

### 編號選項協議（Numbered Options Protocol）
```
當 AI 提供選項時，直接回應編號即可。

範例：
AI: 請選擇技術棧：
   1. React + Node.js + PostgreSQL
   2. Vue + Python + MongoDB
   3. Next.js + Go + MySQL

你: 1

AI 會自動選擇選項 1 並繼續。
```

### 批次處理
```
我有多個類似任務，請批次處理。

任務清單：
1. [任務 1]
2. [任務 2]
3. [任務 3]

請依序執行。
```

### 自訂確認點
```
請在以下時機點增加額外確認：
- [特定階段]
- [特定決策]

並標註 🔴。
```

---

**版本**: v0.02
**維護者**: AISDLC Framework Team
**最後更新**: 2025-10-22
**使用提示**: 收藏本頁面，隨時查閱常用指令！
