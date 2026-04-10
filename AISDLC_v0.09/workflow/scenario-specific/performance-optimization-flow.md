# Performance Optimization Flow
# 效能優化流程

**版本**: v0.09
**最後更新**: 2026-02-17

---

## Workflow Metadata

```yaml
workflow_metadata:
  name: "performance-optimization-flow"
  version: "v0.09"
  scenario: "performance"
  description: "系統性診斷和優化系統效能，包含基準測試、瓶頸分析、策略制定、優化實施、驗證和監控"
  primary_agent: "performance-engineer-zh.yaml"
  supporting_agents:
    - "sd-architect-zh.yaml"
    - "dev-senior-zh.yaml"
    - "qa-automation-zh.yaml"
    - "devops-engineer-zh.yaml"       # 基礎設施優化、監控體系建立與告警配置
  optional_agents:
    - "code-analyzer-zh.yaml"         # 深入代碼複雜度分析時
    - "security-engineer-zh.yaml"     # 效能優化涉及安全敏感區域時（如認證/加密/支付/DDoS防護）
    - "sd-mobile-architect-zh.yaml"   # 涉及 Android/iOS/macOS 行動端效能優化時
    - "qa-mobile-tester-zh.yaml"      # 涉及行動端效能測試（Cold Start/Frame Rate/掃碼回應）時
  sop_reference: "scenarios/performance/SOP.md"
  trigger_conditions:
    - "效能指標未達標"
    - "使用者投訴回應慢"
    - "系統負載增加"
    - "定期效能審查"
```

---

## 適用場景
- **使用時機**：效能問題診斷、回應時間改善、吞吐量提升
- **適用專案**：效能敏感系統、高負載應用、效能優化專案
- **執行頻率**：按需執行或定期（每月/每季）

---

# 角色與責任

## 主要負責人
**Agent 角色**：Performance-Engineer (Perf)
**責任**：效能分析、瓶頸診斷、優化策略制定

## 參與者（Supporting Agents）
- **SD-Architect (Marcus)**：架構級優化設計
- **Dev-Senior**：代碼級優化實施
- **QA-Automation**：效能測試自動化
- **DevOps-Engineer**：基礎設施優化、監控體系建立與告警配置

## 選用參與者（Optional Agents）
- **Code-Analyzer (CodeX)**：深入代碼複雜度分析
- **Security-Engineer**：安全與效能權衡評估（支付/加密/TLS）
- **SD-Mobile-Architect**：行動端架構優化（Android/iOS/macOS）
- **QA-Mobile-Tester**：行動端效能測試（Cold Start/Frame Rate/掃碼）

---

# 執行步驟

## 步驟 1：啟動與情境確認 (20 分鐘)
**執行者**：Performance-Engineer
**對應 SOP**：階段 1

**作業內容**：
1. 載入 AISDLC_INIT.md，識別 performance 情境
2. 確認效能問題描述與影響範圍
3. 確認目標指標（回應時間/吞吐量/資源使用率）
4. 確認技術棧與部署環境

**確認點** 🔴：情境確認
- 效能問題描述清楚
- 目標指標已定義
- 環境資訊完整

**產出**：情境確認記錄

## 步驟 2：效能基準測試 (40-60 分鐘)
**執行者**：Performance-Engineer + Dev-Senior
**對應 SOP**：階段 2

**作業內容**：
1. 定義效能 KPIs（P50/P95/P99/QPS）
2. 設計測試場景
3. 執行負載測試（JMeter/k6/Artillery）
4. 收集基準數據
5. 分析當前效能

**確認點** 🔴：基準測試確認
- 審查測試場景
- 確認基準數據準確
- 確認目標值合理

**產出**：效能基準報告、負載測試結果、KPI 基準數據

## 步驟 3：瓶頸深度分析 (1-1.5 小時)
**執行者**：Performance-Engineer + SD-Architect (Marcus) + Dev-Senior
> 💡 **可選**：需要深入分析代碼複雜度時，可加入 Code-Analyzer (CodeX)
**對應 SOP**：階段 3

**作業內容**：
1. 應用層分析（CPU/Memory Profiling）
2. 資料庫層分析（慢查詢、索引）
3. 網路層分析（延遲、頻寬）
4. 基礎設施分析（資源使用）
5. Root Cause 分析（5 Whys）

**確認點** 🔴：瓶頸分析確認
- 審查瓶頸清單
- 確認優先級排序
- 確認 Root Cause

**產出**：瓶頸分析報告、Root Cause 分析、優化機會清單

## 步驟 4：優化策略制定 (1-1.5 小時)
**執行者**：Performance-Engineer + SD-Architect (Marcus)
**對應 SOP**：階段 4

**作業內容**：
1. 代碼優化策略（Quick Wins）
2. 資料庫優化策略
3. 快取策略設計
4. 非同步處理策略
5. 架構調整方案（High Impact）
6. ROI 評估

**確認點** 🔴：策略確認
- 審查優化路線圖
- 確認方案選擇
- 確認成本評估

**產出**：優化策略文件、優化路線圖、成本效益分析

## 步驟 5：優化實施指引 (40-60 分鐘)
**執行者**：Dev-Senior + Performance-Engineer
**對應 SOP**：階段 5

**作業內容**：
1. 分階段實施計畫
2. 程式碼範例與最佳實踐
3. 實施檢查清單
4. 常見陷阱提醒

**確認點** 🔴：實施指引確認
- 審查實施計畫
- 確認範例正確
- 確認風險控制措施

**產出**：優化實施計畫、程式碼範例、驗證檢查清單

## 步驟 6：效能驗證與對比 (30-40 分鐘)
**執行者**：QA-Automation + Performance-Engineer
**對應 SOP**：階段 6

**作業內容**：
1. A/B Testing 驗證
2. Regression Testing
3. 效能指標對比（與步驟 2 基準線）
4. ROI 計算

**確認點** 🔴：驗證確認
- 效能達標確認
- 無回歸問題
- ROI 達成

**產出**：效能驗證報告、前後對比分析、ROI 報告

## 步驟 7：監控與持續優化 (30 分鐘)
**執行者**：DevOps-Engineer + Performance-Engineer
**對應 SOP**：階段 7

**作業內容**：
1. 建立監控體系（Prometheus/Grafana）
2. 設定告警規則
3. 設定效能預算
4. 建立持續優化機制

**確認點** 🔴：監控方案確認
- 審查監控指標覆蓋範圍
- 確認告警閾值合理
- 確認效能預算可執行

**產出**：監控方案、告警配置、效能預算

---

# SOP-Workflow 步驟對照表

| Workflow 步驟 | SOP 階段 | 說明 |
|--------------|---------|------|
| 步驟 1：啟動與情境確認 | 階段 1 | 載入框架、確認情境 |
| 步驟 2：效能基準測試 | 階段 2 | 建立效能 Baseline |
| 步驟 3：瓶頸深度分析 | 階段 3 | 分層瓶頸診斷 |
| 步驟 4：優化策略制定 | 階段 4 | 制定優化路線圖 |
| 步驟 5：優化實施指引 | 階段 5 | 實施計畫與範例 |
| 步驟 6：效能驗證與對比 | 階段 6 | A/B 測試與效能對比 |
| 步驟 7：監控與持續優化 | 階段 7 | 監控告警與持續機制 |

---

# 輸出與交付

## 主要交付物
- 效能基準報告
- 瓶頸分析報告
- 優化策略文件
- 優化實施計畫
- 效能對比報告
- 監控方案

## 交付標準
- 效能目標達成
- 優化可持續
- 監控完整

---

## 📚 參考資源

- [Performance SOP 完整版](../../scenarios/performance/SOP.md)
- [Performance QuickRef 快速參考](../../scenarios/performance/SOP_QuickRef.md)
- [Performance DeepDive 深度指南](../../scenarios/performance/SOP_DeepDive.md)
- [Performance 快速啟動指令集](../../prompts/scenario-prompts/performance-prompts.md)
- [AISDLC_INIT.md](../../AISDLC_INIT.md)

### 相關 Agents
- [performance-engineer-zh.yaml](../../agent/specialized/performance-engineer-zh.yaml) - Performance Engineer（主導）
- [sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus（架構優化）
- [dev-senior-zh.yaml](../../agent/specialized/dev-senior-zh.yaml) - Senior Developer（代碼優化）
- [qa-automation-zh.yaml](../../agent/specialized/qa-automation-zh.yaml) - QA Automation（效能測試）
- [devops-engineer-zh.yaml](../../agent/specialized/devops-engineer-zh.yaml) - DevOps（基礎設施與監控）
- [sd-mobile-architect-zh.yaml](../../agent/specialized/sd-mobile-architect-zh.yaml) - Mobile Architect（行動端架構優化，選用）
- [qa-mobile-tester-zh.yaml](../../agent/specialized/qa-mobile-tester-zh.yaml) - Mobile QA（行動端效能測試，選用）

### 相關 Skills
- `/performance-optimization` - 效能分析與優化
- `/devops-monitoring` - 監控告警系統
- `/integration-redis` - Redis 快取整合
- `/integration-database` - 資料庫架構優化（索引、連線池、讀寫分離）
- `/mobile-development` - 行動端效能優化（涉及 Android/iOS/macOS 時）

---

**版本**: v0.09
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-17
