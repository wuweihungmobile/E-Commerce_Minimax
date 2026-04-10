# AISDLC v0.09 快速上手指南

## 🎉 v0.09 新特性 (2025-11-03)

### 核心改善
1. **統一模板系統**: PRD/FRD 從 4 個獨立模板整合為 1 個，使用情境標籤選擇
2. **記憶負擔降低 82.5%**: 核心模板從 40 個降至 7 個
3. **SOP QuickRef**: Integration 情境提供 5 分鐘快速參考（1435行→50行）
4. **清晰的目錄結構**: guides/planning/reports 分類管理

### 從 v0.02/v0.03 演進
- ✅ 所有 16 個 Agents 完整保留
- ✅ 所有 17 個 Workflows 完整保留
- ✅ 所有 10 大情境完整保留

詳見: [CHANGELOG.md](CHANGELOG.md) | [UPGRADE_FROM_V02.md](UPGRADE_FROM_V02.md)

---

## 🎯 5 分鐘開始使用

### 你現在的情況是什麼？

選擇最符合你的情況：

---

## 情況 1：我要開發全新專案 🌱

### ✅ 完全可用！

**第一步：載入框架**
```
對 AI 說：
「請載入 /AISDLC_v0.09/AISDLC_INIT.md，我要開發新專案」
```

**第二步：回答問題**
AI 會問你：
- 什麼平台？(Web/iOS/Android/其他)
- 專案規模？(MVP/中型/大型)
- 團隊情況？

**第三步：準備材料**
- 需求描述 (文字/截圖都可以)
- 目標使用者說明
- 預算和時間限制 (如有)

**第四步：開始執行**
打開並跟隨：[scenarios/greenfield/SOP.md](scenarios/greenfield/SOP.md)

**預期時間**：3-5 天完成需求到開發規劃

**最終產出**：
- PRD/FRD/SRD 完整文件
- User Stories 和 Sprint 計畫
- 技術架構設計
- 開發環境設定指南

**✨ v0.03 使用統一模板**：
- PRD: 使用 `docs_template/core/prd/PRD_Universal_Template.md`（選擇 Greenfield 情境）
- FRD: 使用 `docs_template/core/frd/FRD_Universal_Template.md`（選擇 Standard 情境）
- 不需要在 4 個 PRD 模板中選擇，一個模板搞定！

---

## 情況 2：我要分析既有專案代碼 🔍

### ✅ 部分可用 (Code Analyzer Agent 已完成)

**第一步：載入 Code Analyzer**
```
對 AI 說：
「請載入 /AISDLC_v0.09/agent/specialized/code-analyzer.yaml」
```

**第二步：提供代碼資訊**
告訴 AI：
- 專案語言和框架
- 代碼倉庫位置
- 主要想分析什麼 (架構/品質/技術債務)

**第三步：執行分析**
```
「請分析這個專案的 [具體方面]：
[提供代碼片段或倉庫連結]」
```

**可獲得**：
- 代碼結構分析
- 依賴關係圖
- 技術債務識別
- 重構建議

**限制**：Brownfield 完整 SOP 尚未完成，需手動配合

---

## 情況 3：我要優化系統效能 ⚡

### ✅ 部分可用 (Performance Engineer Agent 已完成)

**第一步：載入 Performance Engineer**
```
對 AI 說：
「請載入 /AISDLC_v0.09/agent/specialized/performance-engineer.yaml」
```

**第二步：提供效能資料**
告訴 AI：
- 當前效能問題 (慢在哪裡)
- 效能測試數據 (如有)
- 目標效能指標

**第三步：分析優化**
```
「我的系統 [描述效能問題]，請幫我分析瓶頸和優化策略」
```

**可獲得**：
- 效能瓶頸分析
- 分層優化建議
- 優化優先級排序
- 監控方案

**限制**：Performance 完整 SOP 尚未完成，需手動配合

---

## 情況 4：我要整合第三方 API/系統 🔗

### ✅ 完全可用！✨ v0.03 有 QuickRef！

**第一步：快速掌握 (5 分鐘)**
```
閱讀 scenarios/integration/SOP_QuickRef.md
```

**✨ v0.03 新增**: 5 分鐘快速參考卡
- 核心流程 5 步驟
- 關鍵決策點清單
- 常見陷阱提醒
- 必要產出清單

**第二步：執行整合**
跟隨: [scenarios/integration/SOP.md](scenarios/integration/SOP.md)

**最終產出**:
- API 研究報告
- 認證設計
- 資料映射規格
- 錯誤處理策略
- 整合測試計畫

**✨ v0.03 使用統一模板**:
- PRD: 使用 `PRD_Universal_Template.md`（選擇 Integration 情境）
- FRD: 使用 `FRD_Universal_Template.md`（選擇 Integration 情境）

---

## 情況 5-8：其他情境 (DevOps/測試/文件等)

### ⏳ 框架已規劃，詳細 SOP 待補充

**當前狀態**：
- ✅ 情境已定義和規劃
- ✅ 目錄結構已建立
- ✅ 專用 Agents 已完成
- ⏳ 詳細 SOP QuickRef 待補充

**臨時方案**：
1. 參考 Greenfield SOP 的結構
2. 使用核心 Agents (SA/SD/QA 等)
3. 手動調整流程

**或者**：
```
對 AI 說：
「我的情況是 [具體描述]，請基於 AISDLC v0.09 框架幫我規劃流程」
```

AI 會基於框架設計給出建議流程。

---

## 🎓 學習路徑建議

### 完全新手 (0 基礎)
**時間投入**: 1-2 小時

1. 閱讀 [README.md](README.md) (30 分鐘)
2. 閱讀 [SCENARIO_SELECTOR.md](guides/SCENARIO_SELECTOR.md) (20 分鐘)
3. 選擇一個簡單場景試用 (30-60 分鐘)

### 有 v0.01 使用經驗
**時間投入**: 30 分鐘

1. 閱讀 [README.md](README.md) 的 v0.01 vs v0.03 對比 (10 分鐘)
2. 了解新增的 10 大情境 (10 分鐘)
3. 直接開始使用新情境 (10 分鐘)

### 團隊導入
**時間投入**: 半天

1. 技術負責人閱讀完整文檔 (1-2 小時)
2. 選擇適合團隊的情境 (30 分鐘)
3. 小範圍試用並調整 (1-2 小時)
4. 團隊培訓和推廣 (1 小時)

---

## 🛠️ 常用指令速查

### 啟動框架
```
# 智能識別情境
「請載入 AISDLC_INIT.md (v0.03) 並幫我識別專案情境」

# 直接指定情境
「請載入 AISDLC_INIT.md，我要開發新的 [平台] [專案類型]」

# 使用情境代碼
「AISDLC v0.09 greenfield-web」
「AISDLC v0.09 brownfield」
```

### 載入專用 Agent
```
「請載入 code-analyzer agent 幫我分析代碼」
「請載入 performance-engineer agent 幫我優化效能」
```

### 切換情境
```
「現在切換到 [新情境]」
```

### 尋求幫助
```
「我在 [階段名稱] 遇到困難，具體是 [描述問題]」
「請解釋 [概念/步驟]」
```

---

## 📊 功能可用性速查表

| 功能/情境 | 可用性 | 說明 |
|----------|-------|------|
| **Greenfield** | ✅ 完全可用 | 完整 SOP + 9 階段流程 |
| **Code Analysis** | ✅ Agent 可用 | Code Analyzer 完成 |
| **Performance** | ✅ Agent 可用 | Performance Engineer 完成 |
| **Brownfield** | ⏳ 部分可用 | 使用 Code Analyzer + 手動 |
| **Refactoring** | ⏳ 部分可用 | 使用 Code Analyzer + 手動 |
| **Integration** | ⏳ 框架可用 | Agent 待補充 |
| **DevOps** | ⏳ 框架可用 | Agent 待補充 |
| **Testing** | ⏳ 框架可用 | Agent 待補充 |
| **Documentation** | ⏳ 框架可用 | Agent 待補充 |

---

## 🎯 根據專案階段選擇

### 專案啟動階段
→ 使用 **Greenfield** 情境
- 需求分析
- 技術選型
- 架構設計

### 開發階段
→ 使用核心 Workflows (繼承 v0.01)
- requirements-extraction
- user-story-design
- api-specification

### 維護階段
→ 使用 **Brownfield** 情境
- 功能修改
- Bug 修復
- 功能增強

### 優化階段
→ 使用專用情境
- **Refactoring**: 代碼品質提升
- **Performance**: 效能優化
- **Testing**: 測試補強

### DevOps 階段
→ 使用 **DevOps** 情境
- CI/CD 建置
- 容器化
- 監控設定

### 交接階段
→ 使用 **Documentation** 情境
- 文件補充
- 知識庫建立
- API 文件生成

---

## 💡 最佳實踐

### 1. 先識別情境
不確定用哪個？
→ 使用 [SCENARIO_SELECTOR.md](guides/SCENARIO_SELECTOR.md)

### 2. 充分準備材料
根據選定情境的「前置準備」章節準備

### 3. 不要跳過確認點
所有 🔴 確認點都很重要

### 4. 詳細回答問題
AI 問問題時，詳細回答能得到更好結果

### 5. 保存過程文檔
所有產出文件都要妥善保存

---

## 🚨 常見問題

### Q: 我的情況比較特殊，不完全符合任何情境？
A: 可以：
1. 選擇最接近的情境
2. 告訴 AI 你的特殊情況
3. AI 會根據框架調整流程

### Q: Greenfield 以外的情境可以用嗎？
A: 可以，但：
- Code Analysis 和 Performance 有專用 Agent
- 其他情境參考 Greenfield SOP 結構
- 配合核心 Agents 手動調整

### Q: 可以組合多個情境嗎？
A: 可以！例如：
- Greenfield → Testing → DevOps → Documentation
- Brownfield → Performance → Refactoring

### Q: Token 消耗會很多嗎？
A: 不會
- 初始化 ~250 tokens
- 情境專用配置 ~350 tokens
- 總計約 600 tokens (節省 75%)

### Q: 需要全部學完才能用嗎？
A: 不需要
- 選你需要的情境
- 專注該情境的 SOP
- 其他情境用到再學

---

## 📞 獲取幫助

### 文檔資源
- [完整 README](README.md)
- [情境選擇器](guides/SCENARIO_SELECTOR.md)
- [Greenfield SOP](scenarios/greenfield/SOP.md)
- [實施摘要](reports/execution/IMPLEMENTATION_SUMMARY.md)

### 即時幫助
在 AI 對話中：
```
「我需要幫助：[描述你的問題]」
「請解釋 [概念]」
「這個步驟該怎麼做？」
```

### 社群支援
- Issues: 報告問題
- Discussions: 討論和建議
- Wiki: 範例和最佳實踐

---

## 🎉 開始你的旅程

### 推薦起點

**如果你是第一次使用 AISDLC：**
→ 從 **Greenfield 簡單範例** 開始
1. 準備一個小想法
2. 跟隨 Greenfield SOP
3. 體驗完整流程 (2-4 小時)

**如果你熟悉 v0.01：**
→ 直接使用 **你需要的新情境**
1. 閱讀對應 SOP (如有)
2. 載入對應 Agents
3. 開始執行

**如果你要團隊導入：**
→ 進行 **試點專案**
1. 選擇小型專案試用
2. 收集團隊回饋
3. 調整後推廣

---

**準備好了嗎？選擇你的情境，開始吧！** 🚀

---

## 🚀 完整啟動方式詳解 (6 種方法)

### 方法 1: 一鍵啟動 (推薦給新手)

**特點**: 最簡單，全自動情境識別

**使用方式**:
```
對 AI 說:
「請載入 AISDLC v0.09 並幫我開始專案」
```

**AI 會做什麼**:
1. 載入框架核心配置
2. 詢問你的專案類型 (新專案/既有專案/整合/優化等)
3. 詢問目標平台 (Web/iOS/Android/其他)
4. 詢問團隊規模和專案複雜度
5. 自動識別最適合的情境
6. 自動載入對應的 Primary + Supporting Agents
7. 推薦使用的文檔模板和 Workflow

**適用場景**: 第一次使用 AISDLC，不確定該用哪個情境

**預期時間**: 5-10 分鐘 (含互動問答)

---

### 方法 2: 互動式快速啟動

**特點**: 半自動，提供選項讓你選擇

**使用方式**:
```
對 AI 說:
「請載入 AISDLC v0.09，我要開始一個專案，請提供選項」
```

**AI 會提供**:
- 10 大情境列表 (greenfield, brownfield, refactoring...)
- 4 大平台選項 (Web, iOS, Android, Backend/Other)
- 3 種規模選項 (MVP/小型, 中型, 大型)
- 建議的 Agent 組合

**你只需要**: 從選項中選擇，AI 會自動配置

**適用場景**: 對 AISDLC 有基本了解，想要快速配置

**預期時間**: 3-5 分鐘

---

### 方法 3: 範本式啟動 (最快速)

**特點**: 使用預設範本，一行指令完成

**6 大預設範本**:

#### 範本 1: ecommerce-web (電商網站)
```
「請載入 AISDLC v0.09 範本: ecommerce-web」
```
**自動配置**:
- 情境: greenfield
- 平台: Web
- Primary Agents: pm-po + sa-analyst
- Supporting: sd-web-architect, qa-web-tester, integration-specialist, security-engineer
- 專業化: performance-engineer (大流量), qa-automation (測試自動化)

#### 範本 2: mobile-app (行動應用)
```
「請載入 AISDLC v0.09 範本: mobile-app」
```
**自動配置**:
- 情境: greenfield
- 平台: iOS + Android
- Primary Agents: pm-po + sa-analyst
- Supporting: sd-mobile-architect, qa-mobile-tester, integration-specialist
- 專業化: qa-automation (自動化測試)

#### 範本 3: api-service (API 服務)
```
「請載入 AISDLC v0.09 範本: api-service」
```
**自動配置**:
- 情境: greenfield
- 平台: Backend
- Primary Agents: sa-analyst + sd-architect
- Supporting: dev-developer, qa-tester
- 專業化: security-engineer (API 安全), technical-writer (API 文檔), performance-engineer (效能優化)

#### 範本 4: legacy-upgrade (舊系統升級)
```
「請載入 AISDLC v0.09 範本: legacy-upgrade」
```
**自動配置**:
- 情境: brownfield + refactoring
- 平台: 根據既有系統
- Primary Agents: sa-analyst + dev-senior
- Supporting: code-analyzer, sd-architect, qa-lead
- 專業化: performance-engineer (優化評估)

#### 範本 5: api-integration (第三方整合)
```
「請載入 AISDLC v0.09 範本: api-integration」
```
**自動配置**:
- 情境: integration
- 平台: 跨平台
- Primary Agents: integration-specialist
- Supporting: sd-architect, qa-tester, security-engineer
- 文檔: 使用 Integration 情境 QuickRef (5 分鐘快速參考)

#### 範本 6: performance-tuning (效能調校)
```
「請載入 AISDLC v0.09 範本: performance-tuning」
```
**自動配置**:
- 情境: performance
- 平台: 根據既有系統
- Primary Agents: performance-engineer
- Supporting: sd-architect, dev-senior, qa-automation
- 專業化: code-analyzer (瓶頸分析)

**適用場景**: 你的專案類型符合上述範本之一

**預期時間**: 1-2 分鐘 (零互動)

---

### 方法 4: 系統自動識別啟動

**特點**: 提供專案描述，AI 自動識別情境

**使用方式**:
```
對 AI 說:
「請載入 AISDLC v0.09 並識別我的專案情境:

我要開發一個 [詳細描述你的專案]:
- 專案類型: [新專案/既有專案/整合...]
- 目標平台: [Web/iOS/Android/API...]
- 主要功能: [核心功能列表]
- 技術棧: [使用的技術]
- 團隊規模: [人數]
- 時程限制: [預計時間]
」
```

**AI 會分析**:
- 專案類型 → 匹配情境
- 平台特性 → 選擇平台 Agents
- 功能複雜度 → 決定 Specialized Agents
- 團隊和時程 → 調整 Workflow 優先級

**範例**:
```
「請載入 AISDLC v0.09 並識別我的專案情境:

我要開發一個社群媒體平台:
- 專案類型: 全新專案
- 目標平台: Web (React) + iOS/Android (React Native)
- 主要功能: 用戶註冊登入、貼文發佈、即時聊天、推薦演算法
- 技術棧: React, React Native, Node.js, MongoDB, Redis
- 團隊規模: 5 人 (2 前端, 2 後端, 1 QA)
- 時程限制: 6 個月 MVP
」

AI 會識別為:
✅ 情境: greenfield (新專案)
✅ 平台: Web + Mobile (跨平台)
✅ Agents: pm-po, sa-analyst, sd-web-architect, sd-mobile-architect,
          qa-web-tester, qa-mobile-tester, integration-specialist,
          performance-engineer (推薦演算法優化)
✅ 文檔: PRD_Universal_Template.md (Greenfield)
```

**適用場景**: 專案需求明確，希望 AI 幫你選擇最佳配置

**預期時間**: 3-5 分鐘

---

### 方法 5: 直接指定情境啟動

**特點**: 你已經知道要用哪個情境，直接啟動

**使用方式**:
```
對 AI 說:
「請載入 AISDLC v0.09，我的專案是 [情境類型]」
```

**10 大情境類型**:
- `greenfield` - 新專案開發 (從零開始)
- `brownfield` - 舊專案維護 (既有系統修改)
- `refactoring` - 系統重構 (代碼品質提升)
- `performance` - 效能調校 (系統優化)
- `integration` - 第三方整合 (API 整合開發)
- `devops` - DevOps/CI/CD (部署自動化)
- `testing` - 測試與 QA (測試策略制定)
- `documentation` - 文件維護 (知識文件管理)
- `security` - 安全與合規 (安全評估與合規檢查)

**範例**:
```
「請載入 AISDLC v0.09，我的專案是 integration」

AI 會:
✅ 載入 integration 情境配置
✅ 載入 integration-specialist (Primary)
✅ 載入 sd-architect, qa-tester, security-engineer (Supporting)
✅ 提供 Integration 情境 QuickRef (5 分鐘快速參考)
✅ 推薦 PRD/FRD Universal Template (Integration 情境)
```

**適用場景**: 熟悉 AISDLC，明確知道情境類型

**預期時間**: 2-3 分鐘

---

### 方法 6: 情境快捷碼啟動 (最極簡)

**特點**: 一行指令，零互動，適合頻繁使用者

**使用方式**:
```
「AISDLC v0.09 [情境碼]」
```

**情境快捷碼對照表**:

| 快捷碼 | 情境 | 說明 |
|--------|------|------|
| `gf` | greenfield | 新專案開發 |
| `bf` | brownfield | 舊專案維護 |
| `rf` | refactoring | 系統重構 |
| `pf` | performance | 效能調校 |
| `ig` | integration | 第三方整合 |
| `do` | devops | DevOps/CI/CD |
| `ts` | testing | 測試與 QA |
| `dc` | documentation | 文件維護 |
| `sc` | security | 安全與合規 |

**範例**:
```
「AISDLC v0.09 gf」 → 載入 greenfield 情境
「AISDLC v0.09 pf」 → 載入 performance 情境
「AISDLC v0.09 ig」 → 載入 integration 情境
```

**進階用法 (組合碼)**:
```
「AISDLC v0.09 gf-web」     → greenfield + Web 平台
「AISDLC v0.09 gf-mobile」  → greenfield + Mobile 平台
「AISDLC v0.09 bf-pf」      → brownfield + performance (舊系統優化)
「AISDLC v0.09 ig-sc」      → integration + security (安全整合)
```

**適用場景**: 資深使用者，頻繁使用 AISDLC，追求極致效率

**預期時間**: 30 秒 - 1 分鐘

---

## 📊 啟動方式對比與選擇建議

| 方法 | 啟動時間 | 互動程度 | 適用對象 | 自動化程度 | Token 消耗 |
|------|---------|---------|---------|-----------|-----------|
| **方法 1: 一鍵啟動** | 5-10 分鐘 | 高 (多次問答) | 🌱 新手 | ⭐⭐⭐⭐⭐ | ~800 tokens |
| **方法 2: 互動式** | 3-5 分鐘 | 中 (選擇選項) | 🌿 初學者 | ⭐⭐⭐⭐ | ~650 tokens |
| **方法 3: 範本式** | 1-2 分鐘 | 低 (零互動) | 🌳 有經驗者 | ⭐⭐⭐⭐⭐ | ~600 tokens |
| **方法 4: 自動識別** | 3-5 分鐘 | 中 (提供描述) | 🌳 有經驗者 | ⭐⭐⭐⭐ | ~700 tokens |
| **方法 5: 直接指定** | 2-3 分鐘 | 低 (指定情境) | 🌲 熟練者 | ⭐⭐⭐ | ~600 tokens |
| **方法 6: 快捷碼** | 30秒-1分鐘 | 極低 (一行) | 🏆 資深使用者 | ⭐⭐⭐ | ~550 tokens |

### 選擇建議:

**如果你是第一次使用 AISDLC**:
→ 使用 **方法 1 (一鍵啟動)** 或 **方法 2 (互動式)**
理由: AI 會引導你了解各個選項，建立正確的框架認知

**如果你的專案符合常見類型**:
→ 使用 **方法 3 (範本式)**
理由: 零配置，預設範本已經過優化，開箱即用

**如果你的專案較特殊或複雜**:
→ 使用 **方法 4 (自動識別)**
理由: AI 會分析你的描述，提供客製化配置

**如果你熟悉 AISDLC 框架**:
→ 使用 **方法 5 (直接指定)** 或 **方法 6 (快捷碼)**
理由: 快速啟動，節省時間和 Token

**如果你是團隊成員頻繁使用**:
→ 使用 **方法 6 (快捷碼)** + 建立團隊標準範本
理由: 極致效率，統一團隊配置

---

## 🎯 啟動後的標準流程

無論使用哪種啟動方式，成功載入後你都會看到:

```
✅ AISDLC v0.09 已成功載入
✅ 情境: [你的情境]
✅ 平台: [你的平台]
✅ 已載入 Agents:
   - Primary: [列表]
   - Supporting: [列表]
   - Specialized: [列表]

📋 推薦文檔模板:
   - PRD: PRD_Universal_Template.md (選擇 [情境] 標籤)
   - FRD: FRD_Universal_Template.md (選擇 [類型] 標籤)
   - SRD: SRD_Module_Template.md

🎯 推薦 Workflow:
   1. requirements-extraction (需求提取)
   2. validation-documentation (需求驗證)
   3. user-story-design (設計規劃)

📖 情境專用資源:
   - SOP: scenarios/[情境]/SOP.md
   - QuickRef: scenarios/[情境]/SOP_QuickRef.md (如有)

🚀 你可以開始了! 請告訴我你的需求或提供需求文檔。
```

**接下來你可以**:
1. 提供需求描述或文檔
2. 上傳截圖或原型
3. 詢問下一步該做什麼
4. 請求執行特定 Workflow
5. 切換到其他情境

---

[回到 README](README.md) | [選擇情境](guides/SCENARIO_SELECTOR.md) | [查看實施摘要](reports/execution/IMPLEMENTATION_SUMMARY.md)
