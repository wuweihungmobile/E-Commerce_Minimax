# Refactoring 代碼重構快速啟動指令集
# Refactoring Quick Start Prompts

**版本**: v0.09
**適用情境**: Refactoring - 代碼重構與品質改善、技術棧遷移
**最後更新**: 2026-02-15

---

## 🚀 一鍵啟動指令

### 標準啟動

```
我需要重構既有代碼，提升代碼品質和可維護性。

重構資訊：
- 專案路徑：[路徑]
- 重構原因：[技術債務/效能問題/可維護性]
- 重構範圍：[全局/特定模組]
- 時間限制：[有/無]

請載入 AISDLC_INIT.md，啟動 refactoring-planning-flow，執行 Refactoring SOP。
```

### 快速品質評估

```
我想快速評估代碼品質，識別重構優先級。

請 Code-Analyzer (CodeX) 執行：
- 代碼複雜度分析（Cyclomatic Complexity）
- Code Smell 檢測
- 代碼重複度分析
- 技術債務量化
- 產出 Technical_Debt_Register

專案路徑：[路徑]
```

---

## 📊 階段推進指令

### 階段 1：代碼品質分析

```
請執行 Refactoring SOP 階段 1：代碼品質分析。

使用 code-analysis-flow：
- 複雜度分析（圈複雜度、認知複雜度）
- Code Smell 識別（Long Method, Large Class, Feature Envy 等）
- 代碼重複檢測
- 可維護性指數計算
- 產出 Code_Analysis_Report

分析範圍：[全專案/特定模組]
```

### 階段 2：重構目標設定

```
品質分析已完成，請進入階段 2：重構目標設定。

請 SD architect (Marcus) 和 Code-Analyzer (CodeX) 協作：
- 根據分析報告識別重構熱點
- 評估重構 ROI（投入vs收益）
- 設定可測量的品質目標
  * 降低複雜度：從 X 降至 Y
  * 提升覆蓋率：從 X% 提升至 Y%
  * 消除重複：從 X% 降至 Y%
- 產出 Refactoring_Plan
```

### 階段 3：重構策略制定

```
重構目標已設定，請進入階段 3：重構策略制定。

請 Senior Developer 提供重構策略：
- 選擇重構手法（Extract Method, Move Method, Replace Conditional 等）
- 制定安全重構步驟（小步快跑）
- 規劃測試保護網
- 設定重構檢查點
- 估算工作量

重構範圍：[列出要重構的模組/類別/函數]
```

### 階段 4：測試安全網建立

```
重構策略已制定，請進入階段 4：建立測試安全網。

請 QA agent (Quincy) 和 QA-Automation 協作：
- 補充缺失的單元測試
- 建立 Characterization Tests（特徵測試）
- 設定測試覆蓋率門檻（建議 80%+）
- 配置自動化測試 Pipeline

目標：確保重構前測試全綠，作為重構安全基線。
```

### 階段 5：逐步重構執行

```
測試安全網已建立，請進入階段 5：逐步重構執行。

請 Senior Developer 指導執行：
- 每次只重構一小部分
- 每步重構後立即跑測試
- 遵循 Red-Green-Refactor 循環
- 頻繁提交（保持可回退）

重構順序：[依優先級列出]
```

### 階段 6：重構驗證與對比

```
重構執行已完成，請進入階段 6：重構驗證與對比。

驗證項目：
- 功能驗證（所有測試通過）
- 品質對比（重構前 vs 重構後）
  * 複雜度變化
  * 重複度變化
  * 可維護性指數變化
- 效能對比（確保無效能衰退）
- 產出對比報告
```

### 階段 7：前後對比與成果展示

```
重構驗證已通過，請進入階段 7：前後對比與成果展示。

請 Code-Analyzer + Technical-Writer 協作：
- 生成品質對比報告（重構前 vs 重構後）
  * 複雜度、重複度、可維護性指數變化
- 量化改善指標與 ROI 計算
- 程式碼範例對比（Before/After）
- 效能對比分析（技術棧遷移時，對照階段 2 基準線）
```

### 階段 8：知識沉澱與文件更新

```
成果展示已完成，請進入階段 8：知識沉澱與文件更新。

請 Technical-Writer 協助：
- 更新技術文檔（反映新的代碼結構）
- 記錄重構決策（ADR）
- 彙整經驗教訓（Lessons Learned）
- 更新重構 Playbook（成功模式與失敗案例）
- 準備團隊分享（重構經驗、最佳實踐）
```

---

### 🆕 技術棧遷移指令（情境選擇指引）

> **⚠️ 情境選擇**：
> - **全技術棧遷移**（前後端+DB 同時替換）→ 使用 **Migration SOP**
> - **部分技術棧替換**（僅換單層框架）→ 使用本 **Refactoring SOP**

#### 全技術棧遷移（→ Migration SOP）
```
我需要進行全技術棧遷移。

遷移資訊：
- 前端：[舊框架] → [新框架]（如 Vue 3 → React/Next.js）
- 後端：[舊框架] → [新框架]（如 Python API → Spring Boot）
- 資料庫：[舊 DB] → [新 DB]（如 Oracle → PostgreSQL）
- 新平台：[如 Android/macOS]（如適用）
- 硬體整合：[如掃碼器/NFC]（如適用）

請載入 AISDLC_INIT.md，識別為 migration 情境，
執行 Migration SOP，並觸發 /database-migration、/mobile-development 等相關 Skills。
```

#### 部分技術棧替換（→ Refactoring SOP）
```
我需要替換系統的 [前端/後端/DB] 框架。

替換資訊：
- 替換層：[前端/後端/DB]
- 舊框架：[名稱]
- 新框架：[名稱]
- 其他層不變

請載入 AISDLC_INIT.md，啟動 refactoring-planning-flow，
執行 Refactoring SOP（技術棧替換模式）。
```

---

## 🔄 常見變體指令

### 變體 1：降低複雜度（Complexity Reduction）

```
代碼複雜度過高，難以理解和維護。

問題代碼：
- 檔案/函數：[名稱]
- 圈複雜度：[目前值]
- 目標：降至 [目標值]

請使用重構手法：
- Extract Method（提取方法）
- Replace Conditional with Polymorphism（多型替換條件）
- Decompose Conditional（分解條件）
```

### 變體 2：消除重複（DRY - Don't Repeat Yourself）

```
代碼中存在大量重複。

重複情況：
- 重複度：[X%]
- 重複類型：[Copy-Paste/相似邏輯]
- 影響範圍：[模組名稱]

請使用重構手法：
- Extract Method（提取共用方法）
- Pull Up Method（上移方法到父類）
- Form Template Method（形成模板方法）
```

### 變體 3：改善命名（Naming Improvement）

```
變數、函數、類別命名不清晰。

問題：
- 命名風格不一致
- 縮寫過多
- 名稱無法表達意圖

請執行批量重命名：
- 遵循命名規範
- 使用有意義的名稱
- 確保 IDE 重構工具支援（避免手動修改）
```

### 變體 4：解耦（Decoupling）

```
模組間耦合度高，難以測試和維護。

耦合問題：
- 高耦合模組：[模組 A, 模組 B]
- 耦合類型：[直接依賴/資料耦合/控制耦合]

請使用重構手法：
- Extract Interface（提取介面）
- Dependency Injection（依賴注入）
- Strategy Pattern（策略模式）
```

### 變體 5：資料結構優化（Data Structure Refactoring）

```
資料結構不合理，影響效能和可讀性。

問題：
- 原始資料結構：[描述]
- 問題：[效能差/難理解/難擴展]
- 目標：[改善效能/提升可讀性]

請使用重構手法：
- Replace Array with Object（物件替換陣列）
- Encapsulate Collection（封裝集合）
- Replace Data Value with Object（物件替換資料值）
```

---

## 🆘 疑難排解指令

### 問題 1：不知從何開始重構

```
代碼問題很多，不知道從哪裡開始。

請 Code-Analyzer 提供重構路線圖：
- 識別重構熱點（Hot Spots）
- 評估重構優先級（Quick Wins vs 高影響）
- 制定分階段計畫
- 估算每階段工作量
```

### 問題 2：沒有測試，不敢重構

```
既有代碼沒有測試，不敢動手重構。

請使用測試優先策略：
1. 先寫 Characterization Tests（捕捉現有行為）
2. 建立測試覆蓋基線
3. 小步重構，頻繁測試
4. 逐步提升測試品質
```

### 問題 3：重構範圍太大

```
需要重構的範圍太大，無法一次完成。

請制定漸進式重構計畫：
- Strangler Fig Pattern（絞殺者模式）
- 設定重構邊界
- 每次重構一小塊
- 新舊代碼共存（Feature Toggle）
```

### 問題 4：重構後效能變差

```
重構後功能正確，但效能變差。

請執行效能分析：
- 重構前後效能對比
- 識別效能瓶頸
- 優化關鍵路徑
- 使用 Performance-Engineer 協助
```

---

## 🎓 進階使用技巧

### 技巧 1：TDD 重構（Test-Driven Refactoring）

```
我想用 TDD 方式安全重構。

請遵循 TDD 循環：
1. Red：寫一個失敗的測試
2. Green：寫最少的代碼讓測試通過
3. Refactor：重構代碼，保持測試綠燈

重構目標：[描述]
```

### 技巧 2：並行重構（Parallel Refactoring）

```
多人協作重構，如何避免衝突？

請建議：
- 模組邊界劃分（減少交集）
- Branch by Abstraction（抽象分支）
- Feature Toggle 隔離
- 頻繁整合（持續整合）
```

### 技巧 3：重構度量追蹤（Metrics Tracking）

```
我想追蹤重構進度和效果。

請建立重構儀表板：
- 代碼複雜度趨勢圖
- 測試覆蓋率趨勢圖
- 技術債務趨勢圖
- 重構投入 vs 收益分析
```

---

## 📚 參考資源

### 相關文檔
- [Refactoring SOP](../../scenarios/refactoring/SOP.md)
- [AISDLC_INIT.md](../../AISDLC_INIT.md)

### 相關 Workflows
- [refactoring-planning-flow.md](../../workflow/scenario-specific/refactoring-planning-flow.md)
- [code-analysis-flow.md](../../workflow/scenario-specific/code-analysis-flow.md)

### 相關 Agents
- [code-analyzer-zh.yaml](../../agent/specialized/code-analyzer-zh.yaml) - CodeX
- [dev-senior-zh.yaml](../../agent/specialized/dev-senior-zh.yaml) - Senior Developer
- [sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus

### 文檔模板
- [Legacy_System_Analysis_Template.md](../../docs_template/scenario_specific/analysis/Legacy_System_Analysis_Template.md) - 系統分析模板
- [Impact_Analysis_Template.md](../../docs_template/scenario_specific/analysis/Impact_Analysis_Template.md) - 影響分析模板
- [Gap_Analysis_Template.md](../../docs_template/scenario_specific/analysis/Gap_Analysis_Template.md) - 差距分析模板

---

**版本**: v0.09
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-15
