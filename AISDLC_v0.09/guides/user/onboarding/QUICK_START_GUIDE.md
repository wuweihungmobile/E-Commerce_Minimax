# AISDLC v0.09 快速上手指南

**版本**: v0.09 | **最後更新**: 2026-04-11

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

**Primary Agents**: sa-analyst、sd-architect

**可獲得**：系統現況分析、影響範圍評估、變更計畫、回歸測試策略

---

## 情況 3：我要重構代碼品質 ♻️

**第一步：載入框架**
```
「請載入 AISDLC_v0.09/AISDLC_INIT.md，我的情境是 refactoring」
```

**第二步：跟隨 SOP**
開啟 [scenarios/refactoring/SOP.md](../../scenarios/refactoring/SOP.md)

**Primary Agents**: sa-analyst、sd-architect
**Specialized**: code-analyzer-zh（瓶頸分析）

**可獲得**：技術債評估、重構優先序、安全重構計畫、品質指標改善

---

## 情況 4：我要整合第三方 API/系統 🔗

**第一步：快速掌握（5 分鐘）**
先閱讀 [scenarios/integration/SOP_QuickRef.md](../../scenarios/integration/SOP_QuickRef.md)

**第二步：執行整合**
跟隨 [scenarios/integration/SOP.md](../../scenarios/integration/SOP.md)

**Primary Agents**: integration-specialist、sd-architect

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
**Specialized**: code-analyzer-zh（瓶頸定位）、sd-architect（架構層優化）

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
「AISDLC security」     → 安全合規審查      (Primary: compliance-officer)
```

---

## 📊 十大情境可用性速查

| 情境 | 可用性 | Primary Agents | QuickRef | DeepDive |
|------|-------|----------------|----------|----------|
| **greenfield** | ✅ 完全可用 | pm-po, sa-analyst | ✅ | ✅ |
| **brownfield** | ✅ 完全可用 | sa-analyst, sd-architect | ✅ | ✅ |
| **refactoring** | ✅ 完全可用 | sa-analyst, sd-architect | ✅ | ✅ |
| **migration** | ✅ 完全可用 | sd-architect, sa-analyst | ✅ | ❌ |
| **performance** | ✅ 完全可用 | performance-engineer | ✅ | ✅ |
| **integration** | ✅ 完全可用 | integration-specialist | ✅ | ✅ |
| **devops** | ✅ 完全可用 | devops-engineer | ✅ | ✅ |
| **testing** | ✅ 完全可用 | qa-lead | ✅ | ✅ |
| **documentation** | ✅ 完全可用 | technical-writer | ✅ | ✅ |
| **security** | ✅ 完全可用 | compliance-officer | ✅ | ✅ |

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

## 🎓 學習路徑建議

| 對象 | 時間投入 | 建議路徑 |
|------|---------|---------|
| 完全新手 | 1-2 小時 | 讀 [SCENARIO_SELECTOR.md](SCENARIO_SELECTOR.md) → 選簡單情境試用 |
| 有 v0.0x 使用經驗 | 30 分鐘 | 直接選對應情境 SOP_QuickRef 開始 |
| 團隊導入 | 半天 | 技術負責人讀完整 SOP → 試點專案 → 收集回饋 → 推廣 |

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

---

## 🚨 常見問題

**Q: 不確定用哪個情境？**
A: 使用 [SCENARIO_SELECTOR.md](SCENARIO_SELECTOR.md) 互動式引導，或對 AI 說「幫我選擇情境」

**Q: 可以組合多個情境嗎？**
A: 可以！例如：greenfield → testing → devops → documentation，或 brownfield → performance → refactoring

**Q: Token 消耗多嗎？**
A: 不多。初始化 ~250 tokens，情境配置 ~350 tokens，總計 ~600 tokens（節省 75%）

**Q: 前端開發有特殊指引嗎？**
A: 是的，查看 [scenarios/FRONTEND_SPECIFIC_GUIDE.md](../../scenarios/FRONTEND_SPECIFIC_GUIDE.md)

**Q: 大型專案怎麼辦？**
A: 查看 [scenarios/SCALING_GUIDE.md](../../scenarios/SCALING_GUIDE.md)

---

## 📚 文檔資源導覽

| 文件 | 說明 | 路徑 |
|------|------|------|
| 框架初始化 | 載入 Agents 和 Workflows | `AISDLC_INIT.md` |
| 情境選擇器 | 互動式情境引導 | `guides/user/onboarding/SCENARIO_SELECTOR.md` |
| 情境決策樹 | 快速決策路徑 | `guides/user/onboarding/SCENARIO_DECISION_TREE.md` |
| 情境銜接指南 | 跨情境切換 | `scenarios/SCENARIO_TRANSITION_GUIDE.md` |
| Agent 配置映射 | 各情境 Agent 配置 | `scenarios/SCENARIO_AGENT_MAPPING.md` |
| 代碼審查指南 | 最佳實踐 | `guides/user/process/Code_Review_Guidelines.md` |
| ID 命名規範 | 文件 ID 格式 | `guides/system/naming/AISDLC_ID_Naming_Convention.md` |

---

**準備好了嗎？選擇你的情境，開始吧！** 🚀

[情境選擇器](SCENARIO_SELECTOR.md) | [情境決策樹](SCENARIO_DECISION_TREE.md) | [初始化文件](../../AISDLC_INIT.md)
