# AISDLC v0.09 Prompts 目錄

> **💬 使用者指令模板庫**
>
> 本目錄提供與 AISDLC Framework 互動的標準指令模板，涵蓋 10 大情境、快速啟動指令和完整流程範例。
> 直接複製指令對 AI 說即可使用。

---

**版本**: v0.09
**最後更新**: 2026-04-11
**維護者**: AISDLC Framework Team

---

## 📁 目錄結構

```
prompts/
├── README.md                               # 本檔案 - 目錄總覽
│
├── quick-start/                            # ⚡ 快速啟動指令（新手首選）
│   ├── 5-minute-start.md                  # 5 分鐘體驗核心功能
│   ├── common-commands.md                 # 常用指令速查表
│   ├── scenario-quick-reference.md        # 10 大情境快速參考
│   └── troubleshooting-quick-guide.md     # 常見問題排解指令
│
├── scenario-prompts/                       # 🎯 10 大情境專用指令集
│   ├── greenfield-prompts.md              # 新專案開發
│   ├── brownfield-prompts.md              # 舊專案維護
│   ├── refactoring-prompts.md             # 程式碼重構
│   ├── migration-prompts.md               # 技術棧遷移
│   ├── performance-prompts.md             # 效能優化
│   ├── integration-prompts.md             # 第三方整合
│   ├── devops-prompts.md                  # DevOps/CI/CD
│   ├── testing-prompts.md                 # 測試策略
│   ├── documentation-prompts.md           # 技術文件
│   └── security-prompts.md                # 安全合規
│
└── complete-flow/                          # 📚 完整流程範例
    ├── README.md                           # 範例使用說明
    ├── end-to-end-greenfield-example.md   # Greenfield 端到端完整範例
    └── multi-scenario-combination-example.md # 多情境組合範例
```

---

## 🎯 快速導航 - 我應該用哪個指令？

### ⚡ 第一次使用 AISDLC

1. 閱讀 [quick-start/5-minute-start.md](quick-start/5-minute-start.md) — 5 分鐘體驗
2. 使用 [quick-start/scenario-quick-reference.md](quick-start/scenario-quick-reference.md) — 找到你的情境
3. 開啟對應的 [scenario-prompts/](scenario-prompts/) — 複製指令開始

---

### 🎯 我知道要用哪個情境

| 情境 | 指令檔案 | Primary Agents |
|------|---------|----------------|
| 新專案開發 | [greenfield-prompts.md](scenario-prompts/greenfield-prompts.md) | pm-po, sa-analyst |
| 舊專案維護 | [brownfield-prompts.md](scenario-prompts/brownfield-prompts.md) | sa-analyst, sd-architect |
| 程式碼重構 | [refactoring-prompts.md](scenario-prompts/refactoring-prompts.md) | sa-analyst, sd-architect |
| 技術棧遷移 | [migration-prompts.md](scenario-prompts/migration-prompts.md) | sd-architect, sa-analyst |
| 效能優化 | [performance-prompts.md](scenario-prompts/performance-prompts.md) | performance-engineer |
| 第三方整合 | [integration-prompts.md](scenario-prompts/integration-prompts.md) | integration-specialist |
| DevOps/CI/CD | [devops-prompts.md](scenario-prompts/devops-prompts.md) | devops-engineer |
| 測試策略 | [testing-prompts.md](scenario-prompts/testing-prompts.md) | qa-lead |
| 技術文件 | [documentation-prompts.md](scenario-prompts/documentation-prompts.md) | technical-writer |
| 安全合規 | [security-prompts.md](scenario-prompts/security-prompts.md) | compliance-officer |

---

### 🔧 我需要常用指令速查

→ [quick-start/common-commands.md](quick-start/common-commands.md)

包含：框架載入、Agent 切換、Workflow 執行、文件產出、情境切換等常用指令。

---

### 🚨 我遇到問題，需要排解

→ [quick-start/troubleshooting-quick-guide.md](quick-start/troubleshooting-quick-guide.md)

包含：AI 偏離 SOP、文件不一致、確認點無回應、Token 不足等常見問題的應對指令。

---

### 📚 我想看完整執行範例

→ [complete-flow/](complete-flow/)

| 範例 | 情境 | 適合對象 | 閱讀時間 |
|------|------|---------|---------|
| [Greenfield 端到端](complete-flow/end-to-end-greenfield-example.md) | 單一情境 | 新手 | 15-20 分鐘 |
| [多情境組合](complete-flow/multi-scenario-combination-example.md) | 3 個情境組合 | 有基礎者 | 10-15 分鐘 |

---

## 📊 統計資訊

- **quick-start/ 檔案數**: 4 個
- **scenario-prompts/ 檔案數**: 10 個（對應 10 大情境）
- **complete-flow/ 檔案數**: 3 個（含 README）
- **總計**: 17 個 Markdown 檔案

---

## 🔄 維護指引

### 新增 Prompt 檔案規則

```
問題：這個 Prompt 屬於哪個類型？
│
├─ 特定情境的啟動/執行指令 → scenario-prompts/
├─ 通用快速指令、排解問題 → quick-start/
└─ 完整端到端示範範例    → complete-flow/
```

### 檔案命名規則
- scenario-prompts: `{情境名稱}-prompts.md`（小寫 kebab-case）
- quick-start: `{用途描述}.md`
- complete-flow: `{範例描述}-example.md`

---

## 🔗 相關文檔

- [AISDLC_INIT.md](../AISDLC_INIT.md) — 框架初始化，Prompt 執行前必讀
- [scenarios/](../scenarios/) — 各情境完整 SOP（Prompt 對應的執行流程）
- [guides/user/onboarding/QUICK_START_GUIDE.md](../guides/user/onboarding/QUICK_START_GUIDE.md) — 使用者快速上手
- [guides/user/onboarding/SCENARIO_SELECTOR.md](../guides/user/onboarding/SCENARIO_SELECTOR.md) — 不確定用哪個情境時
