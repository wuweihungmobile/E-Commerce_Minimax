# Performance 效能優化快速啟動指令集
# Performance Optimization Quick Start Prompts

**版本**: v0.09
**適用情境**: Performance - 效能優化
**最後更新**: 2026-02-17

---

## 🚀 一鍵啟動指令

### 標準啟動

```
我需要優化系統效能。

效能問題：
- 問題描述：[慢/高延遲/低吞吐量/記憶體洩漏]
- 影響範圍：[特定功能/整體系統]
- 目前指標：[回應時間/吞吐量/CPU使用率]
- 目標指標：[期望改善程度]

請載入 AISDLC_INIT.md，啟動 performance-optimization-flow，執行 Performance SOP。
```

### 快速效能診斷

```
系統效能變差，需要快速診斷瓶頸。

請 Performance-Engineer (Perf) 執行：
- 建立效能基準（Baseline）
- 效能剖析（Profiling）
- 瓶頸識別（前端/後端/資料庫/網路）
- 產出 Performance_Analysis_Report

系統資訊：[技術棧/部署環境]
```

---

## 📊 階段推進指令

> 💡 以下階段編號對應 [SOP 完整版](../../scenarios/performance/SOP.md) 的 7 階段流程。

### 階段 1：啟動和情境確認

```
請載入 AISDLC_INIT.md，我要進行效能優化。

效能問題描述：[描述問題現象]
問題類型：[回應時間/吞吐量/資源使用/啟動時間]
影響範圍：[全系統/特定功能/特定場景]
目標指標：[期望達成的效能目標]
```

### 階段 2：效能基準測試

```
情境已確認，請進入階段 2：建立效能基準。

使用效能測試工具：
- 前端：Lighthouse, WebPageTest
- 後端：JMeter, k6, Artillery
- 資料庫：慢查詢日誌分析
- 指標：回應時間、吞吐量、錯誤率、資源使用率

測試場景：[描述用戶場景]
產出：Performance_Baseline_Report
```

### 階段 3：瓶頸深度分析

```
基準測試已完成，請進入階段 3：瓶頸分析。

請 Performance-Engineer (Perf) 分層分析：
- 前端瓶頸（渲染/網路/資源載入）
- 後端瓶頸（計算/I/O/並發）
- 資料庫瓶頸（慢查詢/索引/鎖）
- 網路瓶頸（延遲/頻寬/CDN）

產出：瓶頸分析報告（標註優先級）
```

### 階段 4：優化策略制定

```
瓶頸已識別，請進入階段 4：優化策略制定。

請 Performance-Engineer 提供分級優化策略：
- Quick Wins（快速見效，低成本）
- High Impact（高影響，中等成本）
- Architecture Changes（架構變更，高成本）
- 評估 ROI（投入 vs 收益）

產出：Optimization_Strategy
```

### 階段 5：優化實施指引

```
優化策略已制定，請進入階段 5：優化實施。

實施順序（從 Quick Wins 開始）：
1. [優化項目 1]
2. [優化項目 2]
3. [優化項目 3]

請 Senior Developer 協助實施，並在每項優化後進行驗證。
```

### 階段 6：效能驗證與對比

```
優化實施已完成，請進入階段 6：效能驗證與對比。

驗證內容：
- A/B 測試（對照組 vs 優化版本）
- 流量分配：50/50 或 10/90（金絲雀）
- 對比維度：回應時間、吞吐量、資源使用、用戶體驗
- 測試時長：[X 天]

產出：Performance_Improvement_Report
```

### 階段 7：監控與持續優化

```
優化已上線，請進入階段 7：持續監控設定。

請 DevOps-Engineer 設定：
- 效能監控 Dashboard
- 告警規則（回應時間/錯誤率閾值）
- 自動化效能測試（每日/每週）
- 效能劣化檢測

產出：Performance_Monitoring_Setup
```

---

## 🔄 常見變體指令

### 變體 1：前端效能優化

```
前端載入慢，需要優化。

問題：
- 首屏載入時間：[X 秒]
- Lighthouse 分數：[X 分]
- 問題：[資源太大/請求太多/渲染慢]

請優化：
- Code Splitting（代碼分割）
- Lazy Loading（延遲載入）
- Image Optimization（圖片優化）
- CDN 配置
```

### 變體 2：資料庫查詢優化

```
資料庫查詢慢，需要優化。

問題查詢：
- SQL：[提供慢查詢 SQL]
- 執行時間：[X 秒]
- 查詢頻率：[X 次/秒]

請優化：
- 索引優化（Missing Index/Redundant Index）
- 查詢重寫（避免 N+1/使用 JOIN）
- 分頁策略（Cursor-based Pagination）
```

### 變體 3：API 回應時間優化

```
API 回應慢，影響用戶體驗。

問題 API：
- 端點：[API 路徑]
- 目前回應時間：[X ms]
- 目標：降至 [Y ms]

請優化：
- Caching（Redis/CDN）
- Database Optimization
- Async Processing（非同步處理）
- Load Balancing
```

### 變體 4：記憶體優化

```
系統記憶體使用率高，有記憶體洩漏。

問題：
- 記憶體使用：[X GB / Y GB]
- 增長趨勢：[持續增長/穩定]
- 懷疑：[記憶體洩漏/資料結構不當]

請使用 Profiling 工具：
- Heap Dump 分析
- 識別記憶體洩漏點
- 優化資料結構
```

### 變體 5：並發處理能力提升

```
系統並發處理能力不足。

目前狀況：
- 並發用戶數：[X]
- 目標並發：[Y]
- 瓶頸：[CPU/I/O/DB 連線]

請優化：
- Connection Pooling
- Async/Non-blocking I/O
- Horizontal Scaling
- Rate Limiting
```

---

## 🆘 疑難排解指令

### 問題 1：不知道瓶頸在哪

```
系統慢，但不知道瓶頸在哪裡。

請 Performance-Engineer 執行系統性診斷：
- APM 工具監控（New Relic/Datadog）
- Distributed Tracing（追蹤請求鏈路）
- Profiling（CPU/Memory/I/O）
- 產出瓶頸分析報告
```

### 問題 2：優化後效果不明顯

```
已經優化，但效果不明顯。

請重新評估：
- 是否優化了真正的瓶頸？
- 優化方法是否正確？
- 是否有其他隱藏瓶頸？
- 需要 A/B 測試驗證
```

### 問題 3：效能劣化

```
系統效能突然變差。

請快速診斷：
- 對比最近的程式碼變更
- 檢查資料量增長
- 檢查外部依賴（API/DB）
- 檢查基礎設施（CPU/Memory/Disk）
```

---

## 📚 參考資源

### 相關文檔
- [Performance SOP](../../scenarios/performance/SOP.md)
- [AISDLC_INIT.md](../../AISDLC_INIT.md)

### 相關 Workflows
- [performance-optimization-flow.md](../../workflow/scenario-specific/performance-optimization-flow.md)

### 相關 Agents
- [performance-engineer-zh.yaml](../../agent/specialized/performance-engineer-zh.yaml) - Performance Engineer（主導）
- [sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus（架構優化）
- [dev-senior-zh.yaml](../../agent/specialized/dev-senior-zh.yaml) - Senior Developer
- [qa-automation-zh.yaml](../../agent/specialized/qa-automation-zh.yaml) - QA Automation
- [devops-engineer-zh.yaml](../../agent/specialized/devops-engineer-zh.yaml) - DevOps Engineer
- [code-analyzer-zh.yaml](../../agent/specialized/code-analyzer-zh.yaml) - CodeX（代碼效能分析，選用）
- [security-engineer-zh.yaml](../../agent/specialized/security-engineer-zh.yaml) - Security Engineer（安全敏感區域優化，選用）

### 文檔模板
- [Performance 文檔模板](../../docs_template/scenario_specific/performance/)

### 相關 Skills
- `/performance-optimization` - 效能分析與優化
- `/devops-monitoring` - 監控告警系統
- `/integration-redis` - Redis 快取整合

---

**版本**: v0.09
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-17
