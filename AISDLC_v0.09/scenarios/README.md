# AISDLC 情境目錄
# Scenarios Directory

**版本**: v0.09
**最後更新**: 2026-04-11

---

## 概覽

AISDLC v0.09 提供 **10 大開發情境**，每個情境都有完整的 SOP（標準作業程序）、Primary Agents 配置和文檔模板。

---

## 📂 目錄結構

```
scenarios/
├── README.md                          # 本文件
├── SCENARIO_AGENT_MAPPING.md          # 情境 Agent 配置映射表
├── SCENARIO_TRANSITION_GUIDE.md       # 情境銜接指南（跨情境切換）
├── ERROR_RECOVERY_GUIDE.md            # 錯誤恢復機制指南
├── FRONTEND_SPECIFIC_GUIDE.md         # 前端開發特化指引
├── SCALING_GUIDE.md                   # 專案規模化調整指引
│
├── greenfield/                        # 新專案開發
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   ├── SOP_QuickRef.md
│   ├── Parallel_Execution_Guide.md
│   └── checklists/                    # 輔助清單與模板 (9個)
│
├── brownfield/                        # 舊專案維護與改造
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   └── SOP_QuickRef.md
│
├── refactoring/                       # 程式碼重構與品質改善
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   └── SOP_QuickRef.md
│
├── migration/                         # 技術棧遷移
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   └── SOP_QuickRef.md
│
├── performance/                       # 效能優化
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   └── SOP_QuickRef.md
│
├── integration/                       # 系統整合
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   └── SOP_QuickRef.md
│
├── devops/                            # DevOps 建置與 CI/CD 實作
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   └── SOP_QuickRef.md
│
├── testing/                           # 測試策略與自動化
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   └── SOP_QuickRef.md
│
├── documentation/                     # 技術文檔撰寫
│   ├── SOP.md
│   ├── SOP_DeepDive.md
│   └── SOP_QuickRef.md
│
└── security/                          # 安全合規
    ├── SOP.md
    ├── SOP_DeepDive.md
    └── SOP_QuickRef.md
```

---

## 🗺️ 十大情境總覽

| 情境代碼 | 中文名稱 | Primary Agents | DeepDive | QuickRef |
|---------|---------|----------------|----------|----------|
| `greenfield` | 新專案開發 | pm-po, sa-analyst | ✅ | ✅ |
| `brownfield` | 舊專案維護與改造 | sa-analyst, sd-architect | ✅ | ✅ |
| `refactoring` | 程式碼重構與品質改善 | sa-analyst, sd-architect | ✅ | ✅ |
| `migration` | 技術棧遷移 | sd-architect, sa-analyst | ✅ | ✅ |
| `performance` | 效能優化 | performance-engineer | ✅ | ✅ |
| `integration` | 系統整合 | integration-specialist, sd-architect | ✅ | ✅ |
| `devops` | DevOps 建置與 CI/CD | devops-engineer | ✅ | ✅ |
| `testing` | 測試策略與自動化 | qa-lead | ✅ | ✅ |
| `documentation` | 技術文檔撰寫 | technical-writer | ✅ | ✅ |
| `security` | 安全合規 | compliance-officer | ✅ | ✅ |

---

## 📄 SOP 文件說明

每個情境包含最多三層 SOP 文件：

| 文件 | 用途 | 建議閱讀時間 |
|------|------|------------|
| `SOP_QuickRef.md` | 5分鐘快速掌握核心流程，適合熟悉情境者快速複習 | ~5 分鐘 |
| `SOP.md` | 完整標準作業程序，包含所有 Stage 和人機確認點 | ~30 分鐘 |
| `SOP_DeepDive.md` | 深度技術指南，包含進階場景和邊界案例處理 | ~60 分鐘 |

> **建議閱讀順序**: QuickRef → SOP → DeepDive（按需）

---

## 📚 根目錄共用指南

| 文件 | 說明 | 使用時機 |
|------|------|---------|
| `SCENARIO_AGENT_MAPPING.md` | 情境 Agent 配置映射表 | 確認情境對應的 Primary/Supporting Agents |
| `SCENARIO_TRANSITION_GUIDE.md` | 情境銜接指南 | 在不同情境間切換（如 Greenfield → Brownfield）|
| `ERROR_RECOVERY_GUIDE.md` | 錯誤恢復機制指南 | SOP 執行中遇到錯誤或中斷時 |
| `FRONTEND_SPECIFIC_GUIDE.md` | 前端開發特化指引 | 涉及前端架構、UI/UX 設計時 |
| `SCALING_GUIDE.md` | 專案規模化調整指引 | 小型專案擴展為大型系統時 |

---

## 🚀 快速啟動

使用 `AISDLC [情境代碼]` 指令自動載入對應情境：

```
AISDLC greenfield-web    電商網站新專案
AISDLC brownfield        既有系統維護
AISDLC refactoring       代碼品質改善
AISDLC migration         DB 或技術棧遷移
AISDLC performance       API 效能優化
AISDLC integration       第三方 API 整合
AISDLC devops            CI/CD Pipeline 建置
AISDLC testing           測試策略規劃
AISDLC documentation     技術文件整理
AISDLC security          安全合規審查
```

---

## 🔗 相關文檔

- [AISDLC_INIT.md](../AISDLC_INIT.md) - 框架初始化與 Agent 自動載入配置
- [guides/user/onboarding/SCENARIO_SELECTOR.md](../guides/user/onboarding/SCENARIO_SELECTOR.md) - 情境選擇器（互動式引導）
- [guides/system/agent/Specialized_Agent_Selection_Guide.md](../guides/system/agent/Specialized_Agent_Selection_Guide.md) - Specialized Agent 選擇指南

---

**維護者**: AISDLC Framework Team
