# Documentation 文檔管理快速啟動指令集
# Documentation Management Quick Start Prompts

**版本**: v0.02
**適用情境**: Documentation - 技術文檔撰寫與維護
**最後更新**: 2025-10-22

---

## 🚀 一鍵啟動指令

### 標準啟動

```
我需要建立或改善專案的技術文檔。

專案資訊：
- 專案名稱：[名稱]
- 專案類型：[Web/Mobile/Backend/Library]
- 目前文檔狀況：[無/部分/需要改善]
- 文檔目標：[新建/補充/重構]

請載入 AISDLC_INIT.md，啟動 documentation-flow，執行 Documentation SOP。
```

### 快速文檔盤點

```
我想快速盤點目前的文檔狀況。

請 Technical-Writer (DocX) 評估：
- 現有文檔清單
- 文檔完整性（缺少哪些文檔）
- 文檔品質（過時/不清楚/錯誤）
- 文檔可維護性
- 產出 Documentation_Audit_Report
```

---

## 📊 階段推進指令

### 階段 1：文檔現況盤點與規劃

```
請執行 Documentation SOP 階段 1：文檔現況盤點與規劃。

盤點內容：
- 現有文檔清單（README/API Docs/User Guide/等）
- 文檔位置（Git/Wiki/Confluence/等）
- 文檔品質評估（完整性/正確性/可讀性）
- 識別缺失文檔、過時文檔

規劃內容：
- 設計文檔層次架構（Diátaxis 框架）
- 確認目標讀者（開發者/使用者/維運人員）
- 制定命名規範與模板標準
- 確認是否需要安全與合規文檔（Stage 6 觸發條件）

產出：Documentation_Inventory + Documentation_Architecture
```

### 階段 2：核心文檔撰寫

```
規劃已完成，請進入階段 2：核心文檔撰寫。

撰寫優先順序：
1. README（專案介紹/快速開始）
2. API Documentation（OpenAPI/Swagger 規格）
3. Architecture Documentation（C4 Model/系統架構圖）
4. ADR（架構決策記錄）

工具建議：
- API: Swagger UI / Redoc / Scalar
- 架構圖: Mermaid.js / C4-PlantUML / Draw.io

請使用清晰的語言、完整的程式碼範例、適當的架構圖表。
```

### 階段 3：開發者指南與範例

```
核心文檔已完成，請進入階段 3：開發者指南與範例。

撰寫內容：
- Getting Started Guide（5 分鐘上手）
- 開發環境設定指南（環境變數/依賴安裝）
- 程式碼範例集（Quick Start/常見場景/進階/錯誤處理）
- API 使用範例（認證/請求/回應）
- CONTRIBUTING.md（Git 流程/程式碼風格）

確保：範例可執行、有完整輸出說明、涵蓋常見場景
```

### 階段 4：故障排除與 FAQ

```
開發者指南已完成，請進入階段 4：故障排除與 FAQ。

建立內容：
- 常見問題 FAQ（按類別分組）
- 故障排除步驟（症狀 → 原因 → 解決步驟）
- 常見錯誤訊息與解決方案
- 效能問題診斷指引
- 日誌查看與調試方法（指令範例）

產出：Troubleshooting Guide、FAQ
```

### 階段 5：文檔維護與版本管理

```
文檔內容已完成，請進入階段 5：文檔維護與版本管理。

建立維護機制：
- 文檔更新觸發條件（代碼變更/版本發布）
- CHANGELOG 模板與維護規範
- Docs as Code 工作流程（PR Review/CI 自動檢查）
- 文檔審查清單（技術正確性/可讀性/連結有效性）
- 自動化工具配置（markdownlint/lychee link checker/Vale）

發布平台：Docusaurus / MkDocs / GitHub Pages

產出：文檔維護 SOP、CHANGELOG 範本、CI/CD 配置
```

### 階段 6：安全與合規文檔（選用）⭐ v0.09 新增

```
> ⚠️ 觸發條件：專案涉及敏感資料/合規要求/多平台部署時執行

請執行 Documentation SOP 階段 6：安全與合規文檔。

需載入 Agents：Security-Engineer + Compliance-Officer
（如涉及 Mobile: 加載 SD-Mobile-Architect）

撰寫內容：
- 安全架構文檔（認證/授權/加密策略）
- 威脅模型（STRIDE 分析）
- 資料流圖（標示敏感資料保護點）
- 合規對照表（GDPR/PCI-DSS/ISO 27001 vs 實施措施）
- 行動端安全文檔（如涉及 Android/iOS/macOS）
- 安全文檔維護計畫

產出：Security_Architecture.md、Threat_Model.md、Compliance_Mapping.md
```

---

## 🔄 常見變體指令

### 變體 1：API 文檔生成

```
我需要生成 API 文檔。

API 類型：[REST/GraphQL/gRPC]

請使用工具：
- REST：Swagger/OpenAPI（自動生成）
- GraphQL：GraphQL Playground（內建文檔）
- gRPC：protoc-gen-doc

產出：詳細的 API 規格文檔（端點/參數/回應/範例/錯誤碼）
```

### 變體 2：用戶手冊撰寫

```
我需要撰寫用戶手冊（非技術用戶）。

目標用戶：[角色/技術程度]

手冊內容：
- 功能介紹（What）
- 操作步驟（How）
- 常見問題（FAQ）
- 疑難排解（Troubleshooting）
- 截圖/影片（視覺輔助）

語言：簡單易懂、避免技術術語
```

### 變體 3：程式碼註解改善

```
我想改善程式碼註解品質。

目前問題：
- 註解太少或沒有
- 註解過時
- 註解無意義（What 而非 Why）

請改善：
- JSDoc / Docstring / JavaDoc
- 說明「為什麼」而非「是什麼」
- 複雜邏輯加註解
- 自動生成 API Docs（從註解）
```

### 變體 4：內部知識庫建立

```
我想建立內部知識庫。

內容類型：
- How-to Guides（如何做某事）
- Troubleshooting Guides（問題排查）
- Runbooks（維運手冊）
- Architecture Decision Records (ADR)
- Lessons Learned（經驗教訓）

平台：Confluence/Notion/Wiki/GitBook
```

### 變體 5：文檔國際化

```
我需要將文檔翻譯成多語言。

語言：[英文/繁中/簡中/日文/等]

策略：
- i18n 架構（檔案結構/命名）
- 翻譯工作流程（誰翻譯/如何審查）
- 保持同步（原文更新時）
- 翻譯工具（Crowdin/Lokalise）
```

---

## 🆘 疑難排解指令

### 問題 1：文檔過時

```
文檔經常過時，與實際不符。

原因：
- 代碼更新，文檔未更新
- 缺少文檔維護流程

請建立機制：
- 代碼變更時提醒更新文檔
- PR Template 包含文檔檢查項
- 定期文檔審查（季度/半年）
- 自動化檢測（Docs Linting）
```

### 問題 2：文檔找不到

```
文檔散落各處，難以找到。

問題：
- 文檔位置不統一（Git/Wiki/Slack/Email）
- 沒有索引或導航
- 搜尋功能差

請改善：
- 集中文檔位置（Single Source of Truth）
- 建立文檔索引（Documentation Map）
- 設定搜尋功能（Algolia DocSearch）
- 建立 FAQ（常見問題快速查找）
```

### 問題 3：文檔難懂

```
文檔寫得太技術，新手看不懂。

問題：
- 術語太多
- 缺少範例
- 假設讀者有背景知識

請改善：
- 定義術語（Glossary）
- 增加範例（Code Examples）
- 從零開始教學（Getting Started）
- 使用圖表（視覺化）
```

### 問題 4：文檔維護成本高

```
文檔太多，維護困難。

請優化：
- 刪除過時文檔（Archive）
- 合併重複文檔
- 自動生成文檔（從程式碼/註解）
- 模組化文檔（單一職責）
```

---

## 🎓 進階使用技巧

### 技巧 1：文檔即代碼（Docs as Code）

```
我想用 Git 管理文檔，像管理代碼一樣。

請使用 Docs as Code：
- Markdown 撰寫文檔
- Git 版本控制
- Pull Request 審查
- CI 自動構建與部署（GitHub Pages/Netlify）
- 連結檢查/拼寫檢查（自動化）

工具：MkDocs/Docusaurus/VuePress/GitBook
```

### 技巧 2：互動式文檔

```
我想讓文檔更互動。

請增加互動功能：
- Code Playground（線上執行範例）
- Try It Out（API 互動測試）
- 影片教學（Loom/YouTube）
- 互動式圖表（可點擊/展開）

工具：CodeSandbox/StackBlitz/Swagger UI
```

### 技巧 3：文檔測試

```
我想確保文檔中的範例程式碼正確。

請測試文檔：
- 提取文檔中的 Code Blocks
- 自動執行測試
- CI 失敗若範例錯誤
- 使用 Doctest（Python）/ JSDoc 測試
```

### 技巧 4：Analytics 追蹤

```
我想知道哪些文檔最常被閱讀。

請設定 Analytics：
- Google Analytics / Plausible
- 追蹤頁面瀏覽
- 追蹤搜尋關鍵字
- 識別文檔缺口（高搜尋但無結果）
```

---

## 📚 參考資源

### 相關文檔
- [Documentation SOP](../../scenarios/documentation/SOP.md)
- [AISDLC_INIT.md](../../AISDLC_INIT.md)

### 相關 Workflows
- [documentation-flow.md](../../workflow/scenario-specific/documentation-flow.md)

### 相關 Agents
- [technical-writer-zh.yaml](../../agent/specialized/technical-writer-zh.yaml) - DocX
- [04.sa-analyst-zh.yaml](../../agent/core/04.sa-analyst-zh.yaml) - Amanda
- [05.sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus

### 文檔模板
- [文檔模板目錄](../../docs_template/scenario_specific/documentation/README.md)（模板規劃中，v0.09+）

---

**版本**: v0.03
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-23
