# AISDLC v0.09 快速上手指南

**版本**: v0.09 | **最後更新**: 2026-05-07

---

## 🎉 v0.09 核心特性

### 主要改善
1. **完整 10 大情境**: 全部情境均有完整 SOP、DeepDive 和 QuickRef（含新增 migration 情境）
2. **統一模板系統**: PRD/FRD 使用 Universal Template，以情境標籤選擇，降低記憶負擔
3. **雙層 guides 架構**: `guides/system/`（AI Agent 技術規格）+ `guides/user/`（人類友善指南）
4. **中文優先 Agents**: 所有 7 個核心 + 14 個專業化 Agent 均提供 `-zh.yaml` 版本
5. **開發專注版目錄**: 精簡 docs/ 結構（8 個目錄），移除會議目錄，聚焦開發產出

---

## 🎯 5 分鐘開始使用

### 你現在的情況是什麼？

---

## 情況 1：我要開發全新專案 🌱

**第一步：載入框架**
```
「請載入 AISDLC_v0.09/AISDLC_INIT.md，我要開發新專案」
```

**第二步：回答問題**
AI 會詢問：平台（Web/iOS/Android）、專案規模（MVP/中型/大型）、團隊情況

**第三步：跟隨 SOP**
開啟 [scenarios/greenfield/SOP.md](../../scenarios/greenfield/SOP.md)（或先讀 5 分鐘版 [SOP_QuickRef.md](../../scenarios/greenfield/SOP_QuickRef.md)）

**最終產出**：PRD/FRD/SRD 完整文件、User Stories、Sprint 計畫、技術架構設計

**使用模板**：
- PRD: `docs_template/core/prd/PRD_Universal_Template.md`（選 Greenfield 情境）
- FRD: `docs_template/core/frd/FRD_Universal_Template.md`
- SRD: `docs_template/core/srd/SRD_Module_Template.md`

---

## 情況 2：我要維護既有系統 🔧

**第一步：載入框架**
```
「請載入 AISDLC_v0.09/AISDLC_INIT.md，我的情境是 brownfield」
```

**第二步：跟隨 SOP**
開啟 [scenarios/brownfield/SOP.md](../../scenarios/brownfield/SOP.md)（含 DeepDive 進階指南）

**Primary Agents**: sa-analyst、dev-senior
**Supporting**: code-analyzer（代碼分析）、sd-architect（架構建議）

**可獲得**：系統現況分析、技術債評估、影響範圍評估、變更計畫、回歸測試策略

---

## 情況 3：我要重構代碼品質 ♻️

**第一步：載入框架**
```
「請載入 AISDLC_v0.09/AISDLC_INIT.md，我的情境是 refactoring」
```

**第二步：跟隨 SOP**
開啟 [scenarios/refactoring/SOP.md](../../scenarios/refactoring/SOP.md)

**Primary Agents**: sd-architect
**Supporting**: code-analyzer（範圍識別）、dev-senior（技術決策）、qa-tester（回歸驗證）

**可獲得**：技術債評估、重構優先序、安全重構計畫、品質指標改善

---

## 情況 4：我要整合第三方 API/系統 🔗

**第一步：快速掌握（5 分鐘）**
先閱讀 [scenarios/integration/SOP_QuickRef.md](../../scenarios/integration/SOP_QuickRef.md)

**第二步：執行整合**
跟隨 [scenarios/integration/SOP.md](../../scenarios/integration/SOP.md)

**Primary Agents**: integration-specialist
**Supporting**: sd-architect（整合架構）、qa-tester（整合測試）、dev-developer（實作評估）

**最終產出**：API 研究報告、認證設計、資料映射規格、錯誤處理策略、整合測試計畫

---

## 情況 5：我要優化系統效能 ⚡

**第一步：載入框架**
```
「請載入 AISDLC_v0.09/AISDLC_INIT.md，我的情境是 performance」
```

**第二步：跟隨 SOP**
開啟 [scenarios/performance/SOP.md](../../scenarios/performance/SOP.md)

**Primary Agents**: performance-engineer
**Supporting**: code-analyzer（瓶頸定位）、sd-architect（架構層優化）

**可獲得**：效能瓶頸分析、分層優化建議、優先級排序、監控方案

---

## 情況 6：技術棧遷移 🚀

**第一步：載入框架**
```
「請載入 AISDLC_v0.09/AISDLC_INIT.md，我的情境是 migration」
```

**第二步：跟隨 SOP**
開啟 [scenarios/migration/SOP.md](../../scenarios/migration/SOP.md)（5 分鐘版：SOP_QuickRef.md）

**Primary Agents**: sd-architect、sa-analyst

**可獲得**：遷移風險評估、逐步遷移計畫、相容性驗證策略、回滾方案

---

## 情況 7-10：其他情境

所有情境均有完整 SOP，直接指定情境即可：

```
「AISDLC devops」       → DevOps/CI/CD 建置  (Primary: devops-engineer)
「AISDLC testing」      → 測試策略規劃      (Primary: qa-lead)
「AISDLC documentation」→ 技術文件整理      (Primary: technical-writer)
「AISDLC security」     → 安全合規審查      (Primary: security-engineer)
```

---

## 🔀 情境選擇決策樹（1 分鐘快速定位）

> **不確定要選哪個情境？** 跟著以下決策樹走，1 分鐘找到答案。

### 主決策樹

```
┌─────────────────────────────────────────────────────────────────┐
│                    您的專案是什麼狀態？                          │
└─────────────────────────────────────────────────────────────────┘
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
           ▼                  ▼                  ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
    │   🆕 新專案   │   │  📦 既有專案  │   │  🔧 特定任務  │
    │  (無代碼庫)   │   │   (有代碼庫)  │   │   (跨專案)   │
    └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
           │                  │                  │
           ▼                  ▼                  ▼
    ┌──────────────┐   見「既有專案決策樹」   見「特定任務決策樹」
    │  GREENFIELD  │
    └──────────────┘
```

### 既有專案決策樹

```
┌─────────────────────────────────────────────────────────────────┐
│                    您的主要目標是什麼？                          │
└─────────────────────────────────────────────────────────────────┘
       │            │           │           │           │
       ▼            ▼           ▼           ▼           ▼
  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
  │ 新增功能 │ │ 改善品質 │ │ 提升效能 │ │ 整合系統 │ │ 其他目標 │
  │ 修改功能 │ │ 重構代碼 │ │ 優化速度 │ │ 第三方API│ │  ↓ 見下  │
  │ 修復 Bug│ │ 減技術債 │ │ 降資源耗 │ │ 資料對接 │ └─────────┘
  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
       ▼           ▼           ▼           ▼
  BROWNFIELD   REFACTORING  PERFORMANCE  INTEGRATION
```

**其他目標（既有專案）**：

| 目標 | 情境 |
|------|------|
| 建立自動化 CI/CD 部署 | 🚀 DEVOPS |
| 補充測試 / 建立測試策略 | ✅ TESTING |
| 整理技術文檔 | 📚 DOCUMENTATION |
| 技術棧遷移升級 | 🔄 MIGRATION |

### 特定任務決策樹

```
安全評估 / 滲透測試 / 漏洞修復 / 合規檢查 / ISO / GDPR
                            ↓
                     🔒 SECURITY
```

### 情境快速對照表

| 情境 | 適用時機 | 預計時間 | Primary Agent |
|------|----------|----------|---------------|
| 🌱 **Greenfield** | 新專案從零開始 | 3-5 天 | pm-po → sa-analyst → sd-architect |
| 🏚️ **Brownfield** | 既有專案新增/修改功能 | 1-3 天 | sa-analyst + dev-senior |
| ♻️ **Refactoring** | 代碼重構/技術債清理 | 2-4 天 | sd-architect |
| ⚡ **Performance** | 效能優化/速度提升 | 1-2 天 | performance-engineer |
| 🔌 **Integration** | 第三方 API 整合 | 0.5-2 天 | integration-specialist |
| 🔄 **Migration** | 技術棧遷移升級 | 2-4 天 | sd-architect + sa-analyst |
| 🚀 **DevOps** | CI/CD Pipeline 建置 | 1-3 天 | devops-engineer |
| ✅ **Testing** | 測試策略/自動化測試 | 1-2 天 | qa-lead |
| 📚 **Documentation** | 技術文檔撰寫 | 0.5-1 天 | technical-writer |
| 🔒 **Security** | 安全評估/合規檢查 | 1-3 天 | security-engineer |

---

## 🎯 常見情境組合

有些專案需要組合多個情境，以下是常見組合：

### 組合 1：新專案完整開發
```
Greenfield → DevOps → Testing → Documentation
    │           │         │           │
    ▼           ▼         ▼           ▼
  需求分析    建置CI/CD   測試策略    技術文件
  架構設計    自動部署    自動化測試  API文件
```

### 組合 2：既有專案大改造
```
Brownfield → Refactoring → Performance → Testing
    │              │            │           │
    ▼              ▼            ▼           ▼
  影響分析       代碼重構     效能優化    回歸測試
```

### 組合 3：安全加固專案
```
Security → Brownfield → Testing
    │           │          │
    ▼           ▼          ▼
  漏洞掃描    漏洞修復    安全測試
```

### 組合 4：技術升級遷移
```
Migration → Refactoring → Testing → DevOps
    │            │           │          │
    ▼            ▼           ▼          ▼
  遷移計畫     代碼調整    回歸驗證   新部署管線
```

---

## 📊 十大情境可用性速查

| 情境 | 可用性 | Primary Agents | QuickRef | DeepDive |
|------|-------|----------------|----------|----------|
| **greenfield** | ✅ 完全可用 | pm-po, sa-analyst | ✅ | ✅ |
| **brownfield** | ✅ 完全可用 | sa-analyst, dev-senior | ✅ | ✅ |
| **refactoring** | ✅ 完全可用 | sd-architect | ✅ | ✅ |
| **migration** | ✅ 完全可用 | sd-architect, sa-analyst | ✅ | ✅ |
| **performance** | ✅ 完全可用 | performance-engineer | ✅ | ✅ |
| **integration** | ✅ 完全可用 | integration-specialist | ✅ | ✅ |
| **devops** | ✅ 完全可用 | devops-engineer | ✅ | ✅ |
| **testing** | ✅ 完全可用 | qa-lead | ✅ | ✅ |
| **documentation** | ✅ 完全可用 | technical-writer | ✅ | ✅ |
| **security** | ✅ 完全可用 | security-engineer | ✅ | ✅ |

---

## 🛠️ 啟動方式速查（6 種）

### 方法 1：一鍵啟動（新手推薦）
```
「請載入 AISDLC v0.09 並幫我開始專案」
```
AI 自動問答後識別情境，時間：5-10 分鐘

### 方法 2：互動式啟動
```
「請載入 AISDLC v0.09，我要開始一個專案，請提供選項」
```
從選項中選擇情境，時間：3-5 分鐘

### 方法 3：範本式啟動（最快）
```
「請載入 AISDLC v0.09 範本: ecommerce-web」
「請載入 AISDLC v0.09 範本: mobile-app」
「請載入 AISDLC v0.09 範本: api-service」
「請載入 AISDLC v0.09 範本: legacy-upgrade」
「請載入 AISDLC v0.09 範本: api-integration」
「請載入 AISDLC v0.09 範本: performance-tuning」
```
零互動，1-2 分鐘

### 方法 4：自動識別啟動
```
「請載入 AISDLC v0.09 並識別我的專案情境:
我要開發一個 [詳細描述]，技術棧 [技術]，團隊 [人數]，時程 [時間]」
```
AI 分析描述後自動配置，時間：3-5 分鐘

### 方法 5：直接指定情境
```
「請載入 AISDLC v0.09，我的專案是 integration」
```
已知情境直接啟動，時間：2-3 分鐘

### 方法 6：情境快捷碼（資深使用者）
```
「AISDLC gf」   → greenfield   「AISDLC bf」 → brownfield
「AISDLC rf」   → refactoring  「AISDLC mg」 → migration
「AISDLC pf」   → performance  「AISDLC ig」 → integration
「AISDLC do」   → devops       「AISDLC ts」 → testing
「AISDLC dc」   → documentation「AISDLC sc」 → security
```
組合用法：`「AISDLC gf-web」`、`「AISDLC bf-pf」`、`「AISDLC ig-sc」`

時間：30 秒 - 1 分鐘

---

## 📦 六大預配置範本（方法 3 詳細說明）

使用 `「AISDLC 使用範本 [template-name]」` 即可零問答啟動，AI 自動載入所有配置。

| 範本代碼 | 適用情境 | 預設技術棧 | 預估週期 | 規模 |
|---------|---------|-----------|---------|------|
| `ecommerce-web` | Greenfield Web | React + Node.js + PostgreSQL | 2-3 月 | 中大型 |
| `mobile-app` | Greenfield Mobile | React Native + Firebase | 1.5-2.5 月 | 中型 |
| `api-service` | Greenfield Backend | Node.js/Python + PostgreSQL | 1-2 月 | 中型 |
| `legacy-upgrade` | Brownfield | 依原系統 | 2-4 月 | 大型 |
| `api-integration` | Integration | 依主系統 | 2-4 週 | 小中型 |
| `performance-tuning` | Performance | 依系統 | 1-3 週 | 小中型 |

**範本涵蓋內容**：技術棧預設、核心模組清單、Primary/Supporting Agent 配置、推薦文檔模板

> 詳細範本規格請參考 [QUICK_START_TEMPLATES.md](QUICK_START_TEMPLATES.md)

**可客製化**：
```
「我想改用 Vue.js 而不是 React」     → 更新技術棧配置
「暫時不需要後台管理模組」            → 調整模組範圍
「使用 Tutorial Mode（學習模式）」    → 啟用互動教學
```

---

## 🎓 學習路徑建議

| 對象 | 時間投入 | 建議路徑 |
|------|---------|---------|
| 完全新手 | 1-2 小時 | 執行 [Tutorial Mode](TUTORIAL_MODE.md)（Level 1，30 分鐘）→ 選簡單情境試用 |
| 有 v0.0x 使用經驗 | 30 分鐘 | 直接選對應情境 SOP_QuickRef 開始 |
| 團隊導入 | 半天 | 技術負責人讀完整 SOP → 試點專案 → 收集回饋 → 推廣 |

### 新手學習路徑（Tutorial Mode）

```
Level 1 基礎（30 分鐘）: Todo App → 學會情境選擇、需求分析、產出 PRD
Level 2 標準（2 小時）: Blog Platform → 掌握完整文檔流程（FRD/SRD/API）
Level 3 進階（4 小時）: E-commerce → 多情境組合、客製化、錯誤恢復
```

啟動教學模式：
```
「AISDLC tutorial greenfield」           → Greenfield 入門教學
「AISDLC tutorial greenfield level2」    → 標準流程教學
「AISDLC tutorial」                      → 顯示教學選單
```

---

## 🆕 新專案初始化流程

使用 AISDLC 開始新專案前，先確認工作環境：

**快速初始化（0 秒）**：
```bash
# AISDLC 框架本身就是工作目錄，直接開始即可
cd /path/to/AISDLC_v0.09
# 文件直接寫入 docs/ 目錄
```

**docs/ 目錄結構（開發專注版）**：
- `docs/01_requirements/` — PRD/FRD/User Stories
- `docs/02_architecture/` — SRD/API Specification
- `docs/03_testing/` — Test Plan/Test Cases/Reports
- `docs/04_planning/` — Roadmap/Estimation
- `docs/05_development/` — Iteration Plans/Sprint Reports
- `docs/06_quality/` — Code Quality/Security/Performance
- `docs/07_design/` — UI/UX/Database Design
- `docs/08_deployment/` — CI/CD/Release Notes

> 詳細初始化步驟：[PROJECT_INITIALIZATION_GUIDE.md](PROJECT_INITIALIZATION_GUIDE.md)
> 逐項確認清單：[PROJECT_INITIALIZATION_CHECKLIST.md](PROJECT_INITIALIZATION_CHECKLIST.md)

---

## 🎯 根據專案階段選擇

| 階段 | 使用情境 | 核心 Workflows |
|------|---------|--------------|
| 專案啟動 | greenfield | requirements-extraction, user-story-design |
| 需求變更 | greenfield/brownfield | change-management, validation-documentation |
| API 設計 | 任何 | api-specification, interaction-analysis |
| 維護優化 | brownfield → performance → refactoring | consistency-check |
| 技術升級 | migration | requirements-extraction, user-story-design |
| 發布上線 | devops | sprint-execution |
| 品質保障 | testing | consistency-check |

---

## 💡 最佳實踐

1. **先看 QuickRef**：每個情境的 `SOP_QuickRef.md` 只需 5 分鐘，掌握核心流程
2. **不要跳過 🔴 確認點**：所有人機確認點都是防止 AI 幻覺的關鍵
3. **詳細回答 AI 問題**：回答越詳細，產出越準確
4. **善用情境銜接**：[SCENARIO_TRANSITION_GUIDE.md](../../scenarios/SCENARIO_TRANSITION_GUIDE.md) 提供跨情境切換指引
5. **遇到錯誤不慌張**：[ERROR_RECOVERY_GUIDE.md](../../scenarios/ERROR_RECOVERY_GUIDE.md) 提供完整恢復機制
6. **使用範本快速啟動**：6 大範本節省 94% 配置時間（50 分鐘 → 3 分鐘）

---

## 🚨 常見問題

**Q: 不確定用哪個情境？**
A: 使用本指南的「情境選擇決策樹」（見上方），或對 AI 說「幫我選擇情境」，或參考 [SCENARIO_SELECTOR.md](SCENARIO_SELECTOR.md) 詳細問答引導

**Q: 可以組合多個情境嗎？**
A: 可以！例如：greenfield → testing → devops → documentation，或 brownfield → performance → refactoring（見本指南「常見情境組合」）

**Q: Token 消耗多嗎？**
A: 不多。初始化 ~250 tokens，情境配置 ~350 tokens，總計 ~600 tokens（節省 75%）

**Q: 前端開發有特殊指引嗎？**
A: 是的，查看 [scenarios/FRONTEND_SPECIFIC_GUIDE.md](../../scenarios/FRONTEND_SPECIFIC_GUIDE.md)

**Q: 大型專案怎麼辦？**
A: 查看 [scenarios/SCALING_GUIDE.md](../../scenarios/SCALING_GUIDE.md)

**Q: 我是完全新手，從哪裡開始？**
A: 執行 Tutorial Mode：`「AISDLC tutorial greenfield」`，30 分鐘互動學習，有完整引導和即時反饋

**Q: 可以使用固定技術棧（不想每次重新配置）嗎？**
A: 使用六大範本（見「📦 六大預配置範本」）或詳細配置見 [QUICK_START_TEMPLATES.md](QUICK_START_TEMPLATES.md)

**Q: 如何初始化新專案的目錄結構？**
A: 執行 `bash tools/init_project.sh`（macOS/Linux）或 `powershell tools/init_project.ps1`（Windows），詳見 [PROJECT_INITIALIZATION_CHECKLIST.md](PROJECT_INITIALIZATION_CHECKLIST.md)

**Q: 如何恢復中斷的 Workflow？**
A: 記錄中斷前的 Checkpoint ID，重新載入後告知 AI：「繼續從 Checkpoint [ID] 恢復」

---

## 📚 文檔資源導覽

| 文件 | 說明 | 路徑 |
|------|------|------|
| 框架初始化 | 載入 Agents 和 Workflows | `AISDLC_INIT.md` |
| 情境選擇器 | 互動式情境引導（詳細問答版） | `guides/user/onboarding/SCENARIO_SELECTOR.md` |
| 情境銜接指南 | 跨情境切換 | `scenarios/SCENARIO_TRANSITION_GUIDE.md` |
| Agent 配置映射 | 各情境 Agent 配置（權威來源） | `scenarios/SCENARIO_AGENT_MAPPING.md` |
| 快速啟動範本庫 | 6 大預配置範本完整規格 | `guides/user/onboarding/QUICK_START_TEMPLATES.md` |
| 教學模式 | 三級互動式學習系統 | `guides/user/onboarding/TUTORIAL_MODE.md` |
| 專案初始化指南 | 目錄結構、文檔命名規範 | `guides/user/onboarding/PROJECT_INITIALIZATION_GUIDE.md` |
| 專案初始化清單 | 逐項確認清單（可列印版） | `guides/user/onboarding/PROJECT_INITIALIZATION_CHECKLIST.md` |
| 代碼審查指南 | 最佳實踐 | `guides/user/process/Code_Review_Guidelines.md` |
| ID 命名規範 | 文件 ID 格式 | `guides/system/naming/AISDLC_ID_Naming_Convention.md` |

---

**準備好了嗎？選擇你的情境，開始吧！** 🚀

[情境選擇器](SCENARIO_SELECTOR.md) | [初始化文件](../../AISDLC_INIT.md)
