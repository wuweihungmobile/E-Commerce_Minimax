# Greenfield 專案開發快速啟動指令集
# Greenfield Project Quick Start Prompts

**版本**: v0.02
**適用情境**: Greenfield - 全新專案開發
**最後更新**: 2025-10-22

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
我想開發一個全新專案，請使用 AISDLC v0.02 的 Greenfield 情境協助我。

專案資訊：
- 專案名稱：[填寫專案名稱]
- 業務目標：[簡述業務目標]
- 需求來源：[截圖/文字描述/混合]

請載入 AISDLC_INIT.md，啟動 greenfield-complete-flow，並開始執行 Greenfield SOP。
```

### 極速啟動（5分鐘體驗）

```
我想快速體驗 AISDLC v0.02 的 Greenfield 流程。

使用範例專案：「待辦事項管理 Web App」
- 用戶可以新增/編輯/刪除待辦事項
- 支援分類和優先級
- 需要用戶登入

請快速執行需求提取和技術選型階段。
```

### 平台特化啟動

#### Web 平台
```
我要開發一個全新的 Web 應用，請使用 greenfield-web 情境。

專案資訊：
- 專案類型：Web App
- 目標用戶：[描述目標用戶]
- 核心功能：[列出 3-5 個核心功能]

請載入 sd-web-architect 和 qa-web-tester agents，執行 Web 平台專用的技術選型。
```

#### iOS 平台
```
我要開發一個全新的 iOS App，請使用 greenfield-ios 情境。

專案資訊：
- 專案類型：iOS Native App
- iOS 版本支援：iOS 15+
- 核心功能：[列出核心功能]

請載入 sd-mobile-architect 和 qa-mobile-tester agents，執行 iOS 技術選型。
```

#### Android 平台
```
我要開發一個全新的 Android App，請使用 greenfield-android 情境。

專案資訊：
- 專案類型：Android Native App
- Android 版本支援：API 26+
- 核心功能：[列出核心功能]

請載入 sd-mobile-architect 和 qa-mobile-tester agents，執行 Android 技術選型。
```

#### 跨平台 Mobile
```
我要開發一個跨平台 Mobile App，請使用 greenfield-mobile 情境。

專案資訊：
- 專案類型：跨平台 Mobile App（iOS + Android）
- 跨平台方案偏好：[React Native / Flutter / 其他]
- 核心功能：[列出核心功能]

請載入 sd-mobile-architect，協助評估跨平台方案並執行技術選型。
```

---

## 📊 階段推進指令

### 階段 1：專案啟動與需求收集

```
請執行 Greenfield SOP 的階段 1：專案啟動與需求收集。

我已準備：
- [ ] 專案背景說明文件
- [ ] 業務目標描述
- [ ] 初步需求（截圖/文字/混合格式）
- [ ] 利害關係人清單

請 PM/PO agent (Victoria) 協助我梳理業務目標並創建初步 PRD。
```

### 階段 2：需求深度提取與分析

```
專案啟動已完成，請進入階段 2：需求深度提取與分析。

使用 requirements-extraction workflow，請 SA agent (Amanda) 執行多格式需求提取：
- 分析我提供的需求素材
- 識別功能需求和非功能需求
- 提取隱含需求
- 進行編號選項協議（Numbered Options Protocol）確認

請開始執行。
```

### 階段 3：技術棧選型

```
需求分析已完成，請進入階段 3：技術棧選型。

請執行 tech-stack-selection-flow workflow：
- 評估前端技術選項（React/Vue/Angular/Next.js）
- 評估後端技術選項（Node.js/Python/Java/Go）
- 評估資料庫選項（PostgreSQL/MongoDB/MySQL）
- 評估部署方案（Cloud/On-Premise）

專案特性：
- 預期用戶規模：[小/中/大]
- 團隊技術背景：[描述團隊熟悉的技術]
- 效能要求：[低/中/高]
- 預算限制：[有/無]

請 SD architect (Marcus) 提供 3 組完整的技術棧方案並比較優劣。
```

### 階段 4：需求驗證與文檔化

```
技術棧選型已完成，請進入階段 4：需求驗證與文檔化。

執行 validation-documentation workflow：
- BA agent (Beatrice) 與利害關係人驗證需求
- SA agent (Amanda) 生成完整 FRD
- 確保 User Stories 符合 INVEST 原則
- 確保 Acceptance Criteria 清晰可測試

請開始執行多 agent 協作驗證。
```

### 階段 5：系統架構設計

```
FRD 已完成並驗證通過，請進入階段 5：系統架構設計。

執行 user-story-design workflow：
- SD architect (Marcus) 設計系統架構
- 定義模組邊界和職責
- 設計資料模型（ERD）
- 設計 API 架構（若適用）
- 生成 SRD 文檔

技術棧：[填寫已選定的技術棧]

請開始設計。
```

### 階段 6：User Stories 拆分與優先級排序

```
系統架構設計已完成，請進入階段 6：User Stories 拆分與優先級排序。

請 PM/PO agent (Victoria) 和 SA agent (Amanda) 協作：
- 將 Epic 拆分為 User Stories
- 使用 MoSCoW 方法排序（Must/Should/Could/Won't）
- 規劃 Sprint 範圍（建議 2 週為一個 Sprint）
- 確保每個 Story 可在 1 Sprint 內完成

目標：規劃前 3 個 Sprint 的內容。
```

### 階段 7：API 規格制定（若適用）

```
User Stories 已拆分完成，請進入階段 7：API 規格制定。

執行 api-specification workflow：
- 為每個 API 端點創建詳細規格
- 定義認證機制（JWT/OAuth/API Key）
- 定義請求/回應格式
- 定義錯誤處理規範
- 生成 API_Specification 和 API_Index 文檔

請 SD architect (Marcus) 開始制定 API 規格。
```

### 階段 8：開發準備與移交

```
所有文檔已完成，請進入階段 8：開發準備與移交。

準備開發移交材料：
- 整理所有文檔（PRD, FRD, SRD, API Specs）
- 建立 Git Repository
- 初始化專案結構（使用選定的技術棧）
- 配置開發環境（Docker Compose / 環境變數）
- 創建 Developer_Guideline
- 規劃 Sprint 1 的開發任務

請準備開發移交檢查清單。
```

### 階段 9：文檔一致性檢查

```
開發準備已完成，請執行最終的文檔一致性檢查。

執行 consistency-check workflow：
- 驗證 PRD → FRD → SRD → API Specs 追蹤鏈
- 確保所有 User Stories 有對應的 Acceptance Criteria
- 確保所有 AC 有對應的 Acceptance Tests 規劃
- 檢查文檔間的術語一致性
- 檢查所有內部連結有效性

請生成一致性檢查報告。
```

---

## 🔄 常見變體指令

### 變體 1：從截圖開始

```
我有一組 UI 設計截圖，想開發成真實產品。

截圖資訊：
- 截圖數量：[X 張]
- 包含畫面：[列出主要畫面]
- 設計工具：[Figma/Sketch/手繪/其他]

請使用 Greenfield 情境，從截圖提取需求並啟動專案。
```

### 變體 2：從業務需求文字開始

```
我有一份業務需求文字說明，想開發成系統。

需求來源：
- 格式：Word/PDF/純文字
- 篇幅：[X 頁]
- 詳細程度：[高/中/低]

請使用 Greenfield 情境，提取並結構化需求。
```

### 變體 3：混合格式輸入

```
我有多種格式的需求素材：
- 業務需求文件（Word）
- UI 設計截圖（Figma）
- 利害關係人訪談記錄（文字）
- 競品分析（Excel）

請使用 unified-requirements-extraction workflow 整合所有素材，啟動 Greenfield 專案。
```

### 變體 4：敏捷開發模式

```
我想用敏捷方式開發新專案，規劃 2 週 Sprint。

專案資訊：
- 專案名稱：[名稱]
- Sprint 長度：2 週
- 團隊規模：[X 人]
- 目標：3 個月上線 MVP

請使用 Greenfield 情境，協助規劃 Sprint 0（準備階段）到 Sprint 1 的內容。
```

### 變體 5：MVP 快速驗證

```
我想快速打造 MVP 驗證商業假設。

MVP 範圍：
- 核心功能：[列出最小可行功能集]
- 目標時程：[X 週]
- 品質要求：可運作即可，不追求完美

請使用 Greenfield 情境，聚焦 MVP 範圍，快速產出開發所需文檔。
```

### 變體 6：企業級專案

```
這是一個企業級大型專案，需要嚴謹的文檔和流程。

專案特性：
- 專案規模：大型（預計 6-12 個月）
- 團隊規模：[X 人]
- 品質要求：高（金融/醫療等級）
- 需要完整文檔（PRD/FRD/SRD/API/測試/維運）

請使用 Greenfield 情境，執行完整且嚴謹的流程，不跳過任何確認點。
```

---

## 🆘 疑難排解指令

### 問題 1：需求不清楚

```
我的需求還很模糊，不確定如何開始。

目前狀況：
- 只有一個大致的想法：[簡述想法]
- 沒有詳細需求文件
- 不確定技術方向

請使用 Greenfield 情境，協助我從模糊想法逐步梳理出清晰需求。
使用「提問式需求探索」方法，透過提問幫助我釐清需求。
```

### 問題 2：技術選型困難

```
我在技術選型階段遇到困難，無法決定。

困難點：
- 選項太多，不知如何選擇
- 團隊對某些技術不熟悉
- 擔心選錯技術導致後期重構

請 SD architect (Marcus) 執行 tech-stack-selection-flow：
- 提供決策矩陣（功能/效能/學習曲線/生態系統）
- 給出明確的推薦方案和理由
- 說明各方案的風險和緩解策略
```

### 問題 3：User Stories 拆分困難

```
我在 User Stories 拆分階段遇到困難。

困難點：
- Epic 太大，不知如何拆分
- 拆分後的 Story 依賴關係複雜
- 不確定拆分粒度是否合適

請 SA agent (Amanda) 協助：
- 示範如何拆分一個大型 Epic
- 使用垂直切分（Vertical Slicing）方法
- 確保每個 Story 都能獨立交付價值
```

### 問題 4：文檔太多，不知重點

```
產出的文檔很多，我不確定哪些是必須的。

我的情況：
- 專案規模：[小/中/大]
- 團隊規模：[X 人]
- 時間壓力：[有/無]

請根據我的情況，建議：
- 哪些文檔是必須的（Must-have）
- 哪些文檔可以簡化（Nice-to-have）
- 建議的文檔優先級
```

### 問題 5：跨平台還是原生開發？

```
我需要開發 Mobile App，不確定應該選跨平台還是原生。

專案資訊：
- 目標平台：iOS + Android
- 團隊背景：[描述團隊技術背景]
- 效能要求：[低/中/高]
- 預算：[充裕/有限]
- 上線時程：[寬鬆/緊迫]

請 sd-mobile-architect 提供決策分析：
- React Native vs Flutter vs 原生開發
- 各方案的 Pros & Cons
- 明確推薦方案和理由
```

### 問題 6：需求變更頻繁

```
專案進行中需求經常變更，文檔容易不一致。

目前狀況：
- 需求變更頻率：[高/中/低]
- 已產出文檔：[列出已有的文檔]
- 變更內容：[簡述主要變更]

請執行 change-management workflow：
- 追蹤需求變更
- 更新受影響的文檔（PRD/FRD/SRD/API）
- 執行 consistency-check 確保一致性
```

---

## 🎓 進階使用技巧

### 技巧 1：組合多個情境

```
我的專案需要結合多個情境。

情境組合：
- 主情境：Greenfield（新專案開發）
- 次情境：Integration（需要整合第三方支付 API）
- 次情境：DevOps（需要 CI/CD 自動化）

請協助規劃如何組合這些情境的 workflows。
```

### 技巧 2：自定義確認點

```
我想在特定階段增加額外的確認點。

自定義需求：
- 在技術選型後，需要技術長（CTO）審批
- 在 API 設計後，需要安全團隊審查
- 在開發移交前，需要法務審查（GDPR 合規）

請在 Greenfield SOP 中加入這些自定義確認點 🔴。
```

### 技巧 3：並行執行多個階段

```
我想加速專案進度，某些階段可以並行執行。

可並行的任務：
- 前端架構設計 ∥ 後端架構設計
- API 規格制定 ∥ 資料庫設計
- 測試策略規劃 ∥ DevOps 環境準備

請協調多個 agents 並行工作，並標註依賴關係。
```

### 技巧 4：產出特定格式文檔

```
我需要特定格式的文檔以符合公司規範。

格式需求：
- PRD 需要使用公司模板（提供模板路徑）
- API 規格需要符合 OpenAPI 3.0
- 架構圖需要使用 C4 Model

請在產出文檔時遵循這些格式規範。
```

### 技巧 5：段落式執行（分次進行）

```
我無法一次完成整個流程，想分成多次執行。

執行計畫：
- 第 1 次（今天）：階段 1-3（需求收集到技術選型）
- 第 2 次（明天）：階段 4-6（需求驗證到 Stories 拆分）
- 第 3 次（後天）：階段 7-9（API 規格到文檔檢查）

請在每次結束時：
- 保存中斷點（Checkpoint）
- 產出「恢復執行指令」
- 說明下次執行前需要準備的材料
```

### 技巧 6：品質門檻設定

```
我想設定明確的品質門檻，確保產出品質。

品質要求：
- PRD 必須有 PM/PO 和至少 2 位利害關係人確認
- FRD 的每個 User Story 必須符合 INVEST 原則
- API 規格必須包含完整的錯誤處理和範例
- 所有文檔的追蹤鏈必須 100% 完整

請在每個階段結束時執行品質檢查，不符合標準則不進入下一階段。
```

---

## 📚 參考資源

### 相關文檔
- [Greenfield SOP](../../scenarios/greenfield/SOP.md) - 完整執行流程
- [AISDLC_INIT.md](../../AISDLC_INIT.md) - 框架初始化
- [SCENARIO_SELECTOR.md](../../guides/user/onboarding/SCENARIO_SELECTOR.md) - 情境選擇指南
- [QUICK_START_GUIDE.md](../../guides/user/onboarding/QUICK_START_GUIDE.md) - 快速啟動指南

### 相關 Workflows
- [greenfield-complete-flow.md](../../workflow/scenario-specific/greenfield-complete-flow.md)
- [requirements-extraction.md](../../workflow/core/requirements-extraction.md)
- [tech-stack-selection-flow.md](../../workflow/scenario-specific/tech-stack-selection-flow.md)
- [validation-documentation.md](../../workflow/core/validation-documentation.md)
- [user-story-design.md](../../workflow/core/user-story-design.md)
- [api-specification.md](../../workflow/core/api-specification.md)

### 相關 Agents
- [pm-po-agent-zh.yaml](../../agent/core/03.pm-po-agent-zh.yaml) - Victoria
- [sa-analyst-zh.yaml](../../agent/core/04.sa-analyst-zh.yaml) - Amanda
- [ba-business-analyst-zh.yaml](../../agent/core/02.ba-business-analyst-zh.yaml) - Beatrice
- [sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus
- [sd-web-architect-zh.yaml](../../agent/specialized/sd-web-architect-zh.yaml) - Web 專家
- [sd-mobile-architect-zh.yaml](../../agent/specialized/sd-mobile-architect-zh.yaml) - Mobile 專家

### 文檔模板
- [PRD_Universal_Template.md](../../docs_template/core/prd/PRD_Universal_Template.md)
- [FRD_Universal_Template.md](../../docs_template/core/frd/FRD_Universal_Template.md)
- [SRD_Module_Template.md](../../docs_template/core/srd/SRD_Module_Template.md)
- [API_Specification_Template.md](../../docs_template/core/api/API_Specification_Template.md)

---

## 🔖 快速指令索引

| 使用場景 | 快速指令 | 頁面連結 |
|---------|---------|---------|
| 第一次使用 | 標準啟動 | [連結](#標準啟動最常用) |
| 快速體驗 | 極速啟動 | [連結](#極速啟動5分鐘體驗) |
| Web 開發 | Web 平台啟動 | [連結](#web-平台) |
| iOS 開發 | iOS 平台啟動 | [連結](#ios-平台) |
| Android 開發 | Android 平台啟動 | [連結](#android-平台) |
| 跨平台 App | 跨平台啟動 | [連結](#跨平台-mobile) |
| 需求模糊 | 提問式探索 | [連結](#問題-1需求不清楚) |
| 選型困難 | 決策矩陣分析 | [連結](#問題-2技術選型困難) |
| 需求變更 | 變更管理 | [連結](#問題-6需求變更頻繁) |
| 分次執行 | 段落式執行 | [連結](#技巧-5段落式執行分次進行) |

---

**版本**: v0.02
**維護者**: AISDLC Framework Team
**最後更新**: 2025-10-22
