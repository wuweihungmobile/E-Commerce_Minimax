# Testing 測試策略快速啟動指令集
# Testing Strategy Quick Start Prompts

**版本**: v0.09
**適用情境**: Testing - 測試策略與自動化
**最後更新**: 2026-02-17

---

## 🚀 一鍵啟動指令

### 標準啟動

```
我需要建立完整的測試策略和自動化測試。

專案資訊：
- 專案類型：[Web/Mobile/Backend/Fullstack]
- 技術棧：[語言/框架]
- 目前測試狀況：[無/部分/完整]
- 測試目標：[提升覆蓋率/建立自動化/改善品質]

請載入 AISDLC_INIT.md，啟動 testing-strategy-flow，執行 Testing SOP。
```

### 快速測試評估

```
我想評估目前的測試狀況。

請 QA-Lead 評估：
- 測試覆蓋率（Unit/Integration/E2E）
- 測試品質（穩定性/執行速度）
- 測試金字塔是否合理
- 測試自動化程度
- 產出 Test_Coverage_Report
```

---

## 📊 階段推進指令

### 階段 1：測試策略制定

```
請執行 Testing SOP 階段 1：測試策略制定。

請 QA-Lead 制定：
- 測試金字塔（Unit 70% / Integration 20% / E2E 10%）
- 測試類型選擇（Unit/Integration/E2E/Performance/Security）
- 測試覆蓋率目標（X%）
- 測試框架選擇（Jest/PyTest/JUnit/等）
- 測試資料策略（Mock/Fixture/Factory）

產出：Test_Strategy
```

### 階段 2：測試計畫規劃

```
測試策略已制定，請進入階段 2：測試計畫規劃。

請 QA-Lead 規劃：
- 測試範圍（哪些功能需要測試）
- 測試優先級（關鍵路徑優先）
- 測試案例設計（Happy Path/Edge Case/Error Case）
- 測試時程規劃（與開發時程對齊）
- 資源分配（人力/工具/環境）

產出：Test_Plan
```

### 階段 3：測試框架與工具選擇

```
測試計畫已規劃，請進入階段 3：測試框架與工具選擇。

請 QA-Automation 選擇：
- Unit Test Framework（Jest/Mocha/PyTest/JUnit）
- E2E Test Framework（Cypress/Playwright/Selenium）
- API Test Tool（Postman/REST Assured）
- Performance Test Tool（JMeter/k6/Locust）
- Test Runner（Jest/Karma/TestNG）
- CI Integration（GitHub Actions/GitLab CI）

技術棧：[填寫專案技術棧]
產出：Test_Automation_Plan
```

### 階段 4：單元測試實施

```
工具已選定，請進入階段 4：單元測試實施。

實施內容：
- 測試檔案結構（與源碼對應）
- 測試命名規範（describe/it/test）
- Mock/Stub 策略（外部依賴隔離）
- 覆蓋率目標（80%+）
- 斷言庫使用（Expect/Assert/Should）

產出：單元測試套件（X 個測試案例）
```

### 階段 5：整合測試實施

```
單元測試已完成，請進入階段 5：整合測試實施。

實施內容：
- API 測試（Contract Testing）
- 資料庫測試（Repository Layer）
- 第三方整合測試（Mock 第三方 API）
- 測試環境準備（Test Database/Docker Compose）

產出：整合測試套件
```

### 階段 6：E2E 測試實施

```
整合測試已完成，請進入階段 6：E2E 測試實施。

實施內容：
- 用戶流程測試（登入 → 操作 → 登出）
- 跨平台測試（瀏覽器/裝置）
- Page Object Model（POM 模式）
- 測試資料管理（Test Fixtures）
- Headless 模式（CI 環境）

工具：[Cypress/Playwright/Selenium]
產出：E2E 測試套件
```

### 階段 7：測試自動化與 CI 整合

```
E2E 測試已完成，請進入階段 7：測試自動化與 CI 整合。

請 QA-Automation 和 DevOps-Engineer 協作：
- CI Pipeline 整合（每次 PR 都跑測試）
- 並行執行（加速測試執行）
- 測試報告（覆蓋率報告/失敗報告）
- 失敗告警（Slack/Email 通知）
- Flaky Test 管理（Retry 機制）

產出：自動化測試 Pipeline
```

---

## 🔄 常見變體指令

### 變體 1：提升測試覆蓋率

```
目前測試覆蓋率低（X%），需要提升。

目標：提升至 Y%

請 QA-Lead 規劃：
- 識別未覆蓋的代碼（Coverage Report）
- 優先補充關鍵路徑測試
- 制定覆蓋率提升計畫
- 設定 Coverage Gate（低於 Y% 不能合併）
```

### 變體 2：修復 Flaky Tests

```
測試不穩定（時而通過時而失敗）。

問題測試：[列出 Flaky Tests]

請診斷並修復：
- 識別不穩定原因（Timing/Race Condition/外部依賴）
- 增加 Explicit Waits（避免 Implicit Waits）
- 隔離測試（避免測試間互相影響）
- Retry 機制（但不依賴 Retry）
```

### 變體 3：效能測試

```
我需要測試系統效能。

測試需求：
- 測試類型：[負載測試/壓力測試/尖峰測試]
- 目標指標：[回應時間/吞吐量/並發數]
- 測試場景：[描述真實用戶場景]

請使用：JMeter/k6/Locust
產出：Performance_Test_Report
```

### 變體 4：安全測試

```
我需要測試系統安全性。

測試類型：
- OWASP Top 10（SQL Injection/XSS/CSRF/等）
- 依賴漏洞掃描（npm audit/Snyk）
- 滲透測試（Penetration Testing）

請使用：OWASP ZAP/Burp Suite/Snyk
產出：Security_Test_Report
```

### 變體 5：行動裝置測試

```
我需要測試 Mobile App。

測試需求：
- 平台：[iOS/Android/跨平台]
- 測試類型：[功能/UI/效能/相容性]
- 裝置：[真機/模擬器]

請使用：
- iOS：XCTest/XCUITest
- Android：Espresso/UI Automator
- 跨平台：Appium/Detox

產出：Mobile_Test_Suite
```

---

## 🆘 疑難排解指令

### 問題 1：測試執行太慢

```
測試執行時間太長（X 分鐘），影響開發效率。

請優化：
- 並行執行（Test Parallelization）
- 選擇性測試（只跑相關測試）
- 測試分層（快速測試 vs 慢速測試）
- Mock 外部依賴（避免真實 API 調用）
```

### 問題 2：不知道如何測試

```
某些代碼難以測試（Legacy Code/高耦合）。

請使用策略：
- Characterization Tests（先建立安全網）
- 增加 Seam（注入點）
- 重構為可測試（Extract Interface/Dependency Injection）
- Approval Testing（Snapshot Testing）
```

### 問題 3：測試維護成本高

```
測試案例太多，維護困難。

請改善：
- 刪除重複測試
- 合併相似測試
- 使用 Data-Driven Tests（參數化測試）
- Page Object Model（減少重複代碼）
```

### 問題 4：E2E 測試不穩定

```
E2E 測試經常失敗（Flaky）。

常見原因：
- Timing Issues（元素尚未載入）
- Race Conditions（非同步操作）
- 測試資料污染（測試間互相影響）

請修復：
- Explicit Waits（等待特定條件）
- 測試隔離（每個測試獨立資料）
- Retry with Backoff
```

---

## 🎓 進階使用技巧

### 技巧 1：測試驅動開發（TDD）

```
我想使用 TDD 開發新功能。

請遵循 TDD 循環：
1. Red：寫一個失敗的測試
2. Green：寫最少的代碼讓測試通過
3. Refactor：重構代碼，保持測試綠燈

功能：[描述要開發的功能]
```

### 技巧 2：Contract Testing（契約測試）

```
前後端分離開發，需要契約測試。

請使用 Contract Testing：
- Pact（Consumer-Driven Contract）
- 前端定義預期 API 行為
- 後端驗證是否符合契約
- CI 自動執行契約測試
```

### 技巧 3：Visual Regression Testing（視覺回歸測試）

```
我想確保 UI 沒有意外變化。

請使用 Visual Testing：
- Percy / Chromatic / BackstopJS
- Snapshot 截圖對比
- CI 自動檢測視覺差異
- Review 視覺變更
```

### 技巧 4：測試資料管理

```
如何管理測試資料？

請使用策略：
- Test Fixtures（預定義測試資料）
- Factory Pattern（動態生成測試資料）
- Database Seeding（資料庫初始化）
- Test Data Builder（Builder 模式）
```

---

## 📚 參考資源

### 相關文檔
- [Testing SOP](../../scenarios/testing/SOP.md)
- [AISDLC_INIT.md](../../AISDLC_INIT.md)

### 相關 Workflows
- [testing-strategy-flow.md](../../workflow/scenario-specific/testing-strategy-flow.md)

### 相關 Agents
- [qa-lead-zh.yaml](../../agent/specialized/qa-lead-zh.yaml) - QA Lead
- [qa-automation-zh.yaml](../../agent/specialized/qa-automation-zh.yaml) - QA Automation
- [qa-tester-zh.yaml](../../agent/core/07.qa-tester-zh.yaml) - Quincy

### 文檔模板
- [AT_Module_Template.md](../../docs_template/core/tests/AT_Module_Template.md)
- [Test_Report_Template.md](../../docs_template/core/tests/Test_Report_Template.md)
- [Security_Test_Plan_Template.md](../../docs_template/core/tests/Security_Test_Plan_Template.md)
- [Performance_Test_Plan_Template.md](../../docs_template/core/tests/Performance_Test_Plan_Template.md)

---

**版本**: v0.09
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-17
