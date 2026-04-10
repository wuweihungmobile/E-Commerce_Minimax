# Brownfield 既有系統維護快速啟動指令集
# Brownfield Maintenance Quick Start Prompts

**版本**: v0.09
**適用情境**: Brownfield - 既有系統維護與修改
**最後更新**: 2026-02-17

---

## 📋 目錄

- [一鍵啟動指令](#一鍵啟動指令)
- [階段推進指令](#階段推進指令)
- [常見變體指令](#常見變體指令)
- [疑難排解指令](#疑難排解指令)
- [進階使用技巧](#進階使用技巧)

---

## 🚀 一鍵啟動指令

### 標準啟動（最常用）

```
我需要修改一個既有系統，請使用 AISDLC v0.09 的 Brownfield 情境協助我。

專案資訊：
- 系統名稱：[填寫系統名稱]
- 現有技術棧：[前端/後端/資料庫技術]
- 修改需求：[簡述要修改的內容]
- 程式碼位置：[Git Repository 路徑]

請載入 AISDLC_INIT.md，啟動 brownfield-analysis-flow，並開始執行 Brownfield SOP。
```

### 快速代碼分析（無需求變更）

```
我需要快速分析既有系統的代碼結構和品質。

系統資訊：
- 專案路徑：[本地路徑或 Git URL]
- 程式語言：[語言]
- 程式碼規模：[約 X 行代碼或 X 個檔案]

請載入 code-analyzer agent (CodeX)，執行 code-analysis-flow：
- 分析代碼結構
- 生成依賴圖
- 評估技術債務
- 產出 Code_Analysis_Report
```

### 緊急 Bug 修復

```
系統出現緊急 Bug，需要快速定位和修復。

Bug 資訊：
- Bug 描述：[描述問題現象]
- 影響範圍：[哪些功能受影響]
- 錯誤訊息：[如有錯誤訊息請提供]
- 優先級：Critical

請快速執行：
1. 代碼分析定位 Bug
2. 影響範圍評估
3. 修復方案建議
4. 回歸測試規劃
```

---

## 📊 階段推進指令

### 階段 1：系統現況分析

```
請執行 Brownfield SOP 的階段 1：系統現況分析。

執行 code-analysis-flow workflow：
- Code-Analyzer agent (CodeX) 分析代碼結構
- 識別核心模組和邊界
- 分析模組間依賴關係
- 評估代碼品質（複雜度、重複度、可維護性）

專案資訊：
- 程式碼路徑：[路徑]
- 主要語言：[語言]
- 框架：[框架名稱]

請產出 Code_Analysis_Report。
```

### 階段 2：需求分析與影響評估

```
代碼分析已完成，請進入階段 2：需求分析與影響評估。

修改需求：
- 需求描述：[詳細描述要修改的功能]
- 需求來源：[業務需求/Bug 修復/效能優化/法規要求]
- 預期效果：[描述修改後的預期效果]

請 SA agent (Amanda) 和 Code-Analyzer (CodeX) 協作：
- 提取詳細功能需求
- 分析修改影響範圍（調用鏈分析）
- 識別受影響的模組和 API
- 評估風險等級
- 生成 FRD_Brownfield 文檔
```

### 階段 3：變更設計與相容性規劃

```
影響評估已完成，請進入階段 3：變更設計與相容性規劃。

請 SD architect (Marcus) 和 Senior Developer 協作：
- 設計變更方案（最小化影響範圍）
- 確保向下相容性（Backward Compatibility）
- 設計資料遷移策略（若涉及 DB 變更）
- 設計 API 版本策略（若涉及 API 變更）
- 生成 SRD 文檔

現有版本：[版本號]
目標版本：[版本號]
```

### 階段 4：回歸測試規劃

```
變更設計已完成，請進入階段 4：回歸測試規劃。

請 QA agent (Quincy) 協作：
- 根據影響範圍確定回歸測試範圍
- 識別關鍵路徑（Critical Path）
- 規劃自動化測試更新
- 規劃手動測試案例
- 生成 Test_Plan 和 AT 文檔

受影響模組：[列出受影響的模組]
```

### 階段 5：實作指導與代碼審查

```
測試規劃已完成，請進入階段 5：實作指導。

請 Senior Developer agent 提供：
- 實作步驟建議（分階段、小步快跑）
- 關鍵代碼審查點
- 常見陷阱提醒
- Rollback 策略

修改範圍：[列出要修改的檔案或模組]
```

### 階段 6：整合測試與驗證

```
實作已完成，請進入階段 6：整合測試與驗證。

執行測試：
- 單元測試（新增/修改的程式碼）
- 整合測試（受影響的模組間互動）
- 回歸測試（確保既有功能不受影響）
- 效能測試（若涉及效能敏感功能）

請產出 Test_Report，標註：
- 通過的測試案例
- 失敗的測試案例
- 發現的新問題
- 修復建議
```

### 階段 7：部署規劃與文檔更新

```
測試驗證已通過，請進入階段 7：部署規劃與文檔更新。

請 DevOps Engineer 和 Technical Writer 協作：
- 規劃部署策略（藍綠部署/金絲雀部署/滾動更新）
- 準備 Rollback 計畫
- 更新技術文檔
- 更新 API 文檔（若有 API 變更）
- 準備 Release Notes

目標環境：[Dev/Staging/Production]
```

---

## 🔄 常見變體指令

### 變體 1：功能新增（Feature Addition）

```
我要在既有系統中新增一個功能模組。

新功能資訊：
- 功能名稱：[功能名稱]
- 功能描述：[詳細描述]
- 與既有功能的關係：[獨立/依賴既有模組]

請使用 Brownfield 情境：
- 分析既有系統，找到最適合的整合點
- 設計新功能與既有系統的介面
- 確保不破壞既有功能
- 規劃段階式上線（Feature Toggle）
```

### 變體 2：Bug 修復（Bug Fix）

```
我要修復既有系統的 Bug。

Bug 資訊：
- Bug ID：[ID]
- 嚴重性：[Critical/High/Medium/Low]
- 問題描述：[描述]
- 重現步驟：[步驟]
- 錯誤日誌：[日誌內容]

請使用 Brownfield 情境快速模式：
- 定位 Bug 根本原因（Root Cause Analysis）
- 評估修復方案（最小修改原則）
- 規劃回歸測試
```

### 變體 3：技術債務償還（Tech Debt Payoff）

```
我要償還系統中累積的技術債務。

債務資訊：
- 債務類型：[代碼重複/過度複雜/過時依賴/缺少測試]
- 影響範圍：[模組名稱]
- 償還原因：[效能問題/可維護性/安全性]

請結合 Brownfield + Refactoring 情境：
- Code-Analyzer 識別技術債務
- 評估償還優先級（ROI 分析）
- 制定分階段償還計畫
```

### 變體 4：安全性修補（Security Patch）

```
系統發現安全漏洞，需要緊急修補。

漏洞資訊：
- 漏洞類型：[SQL Injection/XSS/CSRF/等]
- CVE 編號：[若有]
- 影響版本：[版本號]
- 修補建議：[來源/建議方案]

請使用 Brownfield 情境（緊急模式）：
- 快速評估影響範圍
- 提供修補方案
- 規劃安全測試
- 準備緊急部署
```

### 變體 5：第三方庫升級（Dependency Upgrade）

```
系統依賴的第三方庫需要升級。

升級資訊：
- 庫名稱：[庫名]
- 目前版本：[版本]
- 目標版本：[版本]
- 升級原因：[安全性/新功能/效能]
- Breaking Changes：[是/否]

請使用 Brownfield 情境：
- 分析依賴關係
- 評估 Breaking Changes 影響
- 制定遷移策略
- 規劃相容性測試
```

### 變體 6：效能優化（Performance Tuning）

```
既有系統效能不佳，需要優化。

效能問題：
- 問題描述：[慢查詢/高延遲/記憶體洩漏/等]
- 影響範圍：[特定功能/整體系統]
- 目前指標：[回應時間/吞吐量/等]
- 目標指標：[期望改善程度]

請結合 Brownfield + Performance 情境：
- Code-Analyzer 識別瓶頸
- Performance-Engineer 分析優化方案
- 制定 A/B 測試計畫
```

---

## 🆘 疑難排解指令

### 問題 1：代碼文檔缺失

```
既有系統缺少文檔，無法理解代碼邏輯。

目前狀況：
- 程式碼：有
- 註解：少或無
- 文檔：完全沒有
- 原開發者：已離職

請使用 code-analysis-flow + documentation-flow：
- 從代碼逆向工程產生文檔
- 生成架構圖、類別圖、流程圖
- 產出技術文檔和 API 文檔
```

### 問題 2：不知道影響範圍

```
我想修改某個功能，但不知道會影響哪些地方。

修改內容：
- 要修改的檔案/函數：[名稱]
- 修改原因：[原因]

請 Code-Analyzer 執行影響範圍分析：
- 靜態分析調用鏈（Call Graph）
- 識別所有依賴此功能的模組
- 標註高風險修改點
- 生成 Dependency_Map
```

### 問題 3：測試覆蓋率低

```
既有系統測試覆蓋率很低，不敢修改代碼。

目前狀況：
- 測試覆蓋率：[X%]
- 測試類型：[有哪些測試]
- 修改需求：[要修改的功能]

請使用 testing-strategy-flow：
- 優先為要修改的模組補充測試
- 建立測試安全網（Safety Net）
- 規劃逐步提升覆蓋率的計畫
```

### 問題 4：代碼耦合度高

```
既有系統耦合度高，牽一髮動全身。

問題現象：
- 模組間依賴複雜
- 修改一處影響多處
- 難以隔離測試

請結合 Brownfield + Refactoring 情境：
- 分析耦合點
- 設計解耦策略（引入介面、依賴注入）
- 制定漸進式重構計畫
```

### 問題 5：向下相容性問題

```
修改會破壞向下相容性，如何處理？

問題：
- 需要修改的 API/功能：[名稱]
- Breaking Changes：[描述]
- 現有用戶：[數量/影響程度]

請 SD architect (Marcus) 提供方案：
- API 版本控制策略（v1, v2 並存）
- Deprecated 警告機制
- 平滑遷移計畫（給用戶時間適應）
- Sunset 時程規劃
```

### 問題 6：部署風險高

```
修改涉及核心功能，部署風險高。

風險點：
- 修改範圍：[核心模組/關鍵路徑]
- 用戶影響：[高流量/關鍵業務]
- Rollback 難度：[涉及資料遷移]

請 DevOps Engineer 提供低風險部署策略：
- Feature Toggle（功能開關）
- 藍綠部署（零停機時間）
- 金絲雀發布（逐步放量）
- 自動 Rollback 機制
```

---

## 🎓 進階使用技巧

### 技巧 1：代碼考古（Code Archaeology）

```
我需要理解一段年代久遠的代碼。

代碼資訊：
- 檔案路徑：[路徑]
- 編寫時間：[年份]
- 程式語言：[語言]
- 問題：[看不懂的部分]

請 Code-Analyzer 執行深度分析：
- 追蹤 Git History（若有）
- 分析程式碼意圖（從測試、註解推斷）
- 產生詳細說明文檔
- 提供重構建議（若代碼品質差）
```

### 技巧 2：安全修改模式（Safe Refactoring）

```
我想安全地修改既有代碼，避免引入 Bug。

請遵循安全修改模式：
1. 先補充測試（建立安全網）
2. 小步修改（每次只改一個小地方）
3. 頻繁測試（每步修改後都跑測試）
4. 及時提交（保持可回退）

修改內容：[描述要修改的內容]

請 Senior Developer 指導每一步的執行。
```

### 技巧 3：並行開發與隔離（Feature Branching）

```
多個功能同時開發，如何避免衝突？

開發計畫：
- 功能 A：[描述]（預計 X 週）
- 功能 B：[描述]（預計 Y 週）
- 功能 C：[描述]（預計 Z 週）

請建議：
- Git 分支策略（Feature Branch/Git Flow）
- 功能隔離機制（Feature Toggle）
- 整合測試策略
- 發布協調計畫
```

### 技巧 4：遺留系統現代化（Legacy Modernization）

```
我想逐步現代化遺留系統。

系統現況：
- 年齡：[X 年]
- 技術棧：[老舊技術]
- 問題：[效能差/難維護/安全性]
- 限制：[不能停機/不能全部重寫]

請制定漸進式現代化策略：
- Strangler Fig Pattern（絞殺者模式）
- 識別可獨立抽取的模組
- 逐步遷移到新技術
- 新舊系統共存策略
```

### 技巧 5：資料庫 Schema 變更（DB Migration）

```
我需要變更資料庫 Schema。

變更內容：
- 變更類型：[新增欄位/修改欄位/新增表/等]
- 影響資料量：[X 筆記錄]
- 停機容忍度：[不能停機/可短暫停機]

請制定安全的 DB Migration 策略：
- 向下相容的 Schema 變更（先加後減）
- 資料遷移腳本（可重複執行、可回滾）
- 分批遷移策略（避免鎖表）
- 驗證檢查點
```

### 技巧 6：監控埋點（Monitoring Instrumentation）

```
修改後如何確保系統穩定運行？

請在修改中加入監控埋點：
- 關鍵路徑的效能監控
- 錯誤率監控和告警
- 業務指標監控（轉換率、成功率）
- 日誌增強（便於問題追蹤）

修改範圍：[描述修改內容]

請 DevOps Engineer 設計監控方案。
```

---

## 📚 參考資源

### 相關文檔
- [Brownfield SOP](../../scenarios/brownfield/SOP.md) - 完整執行流程
- [AISDLC_INIT.md](../../AISDLC_INIT.md) - 框架初始化
- [SCENARIO_SELECTOR.md](../../SCENARIO_SELECTOR.md) - 情境選擇指南

### 相關 Workflows
- [brownfield-analysis-flow.md](../../workflow/scenario-specific/brownfield-analysis-flow.md)
- [code-analysis-flow.md](../../workflow/scenario-specific/code-analysis-flow.md)
- [change-management.md](../../workflow/core/change-management.md)
- [consistency-check.md](../../workflow/core/consistency-check.md)

### 相關 Agents
- [sa-analyst-zh.yaml](../../agent/core/04.sa-analyst-zh.yaml) - Amanda
- [code-analyzer-zh.yaml](../../agent/specialized/code-analyzer-zh.yaml) - CodeX
- [dev-senior-zh.yaml](../../agent/specialized/dev-senior-zh.yaml) - Senior Developer
- [sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus
- [qa-tester-zh.yaml](../../agent/core/07.qa-tester-zh.yaml) - Quincy
- [devops-engineer-zh.yaml](../../agent/specialized/devops-engineer-zh.yaml) - DevOps

### 文檔模板
- [PRD_Universal_Template.md](../../docs_template/core/prd/PRD_Universal_Template.md)
- [FRD_Universal_Template.md](../../docs_template/core/frd/FRD_Universal_Template.md)
- [Legacy_System_Analysis_Template.md](../../docs_template/scenario_specific/analysis/Legacy_System_Analysis_Template.md)
- [Gap_Analysis_Template.md](../../docs_template/scenario_specific/analysis/Gap_Analysis_Template.md)

---

## 🔖 快速指令索引

| 使用場景 | 快速指令 | 頁面連結 |
|---------|---------|---------|
| 既有系統修改 | 標準啟動 | [連結](#標準啟動最常用) |
| 代碼分析 | 快速分析 | [連結](#快速代碼分析無需求變更) |
| 緊急修復 | Bug 修復 | [連結](#緊急-bug-修復) |
| 新增功能 | 功能新增 | [連結](#變體-1功能新增feature-addition) |
| 安全修補 | 安全性修補 | [連結](#變體-4安全性修補security-patch) |
| 效能優化 | 效能調校 | [連結](#變體-6效能優化performance-tuning) |
| 缺文檔 | 逆向文檔 | [連結](#問題-1代碼文檔缺失) |
| 不知影響 | 影響分析 | [連結](#問題-2不知道影響範圍) |
| 風險部署 | 低風險部署 | [連結](#問題-6部署風險高) |

---

**版本**: v0.09
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-17
