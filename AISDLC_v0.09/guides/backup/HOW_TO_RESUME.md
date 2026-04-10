# 如何從中斷點恢復執行
# How to Resume from Checkpoint

**版本**: v0.02
**最後更新**: 2025-10-22

---

## ✅ 當前狀態

**執行狀態**: ✅ **階段 1-3 全部完成 (100%)**

根據 [CHECKPOINT_LOG.md](CHECKPOINT_LOG.md) 和 [EXECUTION_SUMMARY_20251022.md](EXECUTION_SUMMARY_20251022.md)：

- ✅ **階段 1**: 緊急修復 (100%)
- ✅ **階段 2**: 核心內容完善 (100%)
- ✅ **階段 3**: 補充內容 (100%)

**總計**: 81+ 個核心檔案已全部創建，約 1.5MB 文檔產出。

---

## 🎯 如果需要繼續執行其他任務

雖然 **A：完整補齊方案** 已 100% 完成，但如果你想執行可選的擴充任務，可參考以下方式：

### 方式 1：文檔一致性檢查

```
請執行 AISDLC v0.02 的文檔一致性全局檢查。

檢查項目：
1. 所有內部連結是否有效（markdown links）
2. 所有 Agent/Workflow 引用是否正確
3. 所有模板路徑是否存在
4. 術語使用是否一致
5. 版本號是否統一為 v0.02

請產出：Consistency_Check_Report.md
```

---

### 方式 2：創建更多完整範例

```
請為以下情境創建完整的端到端範例（參考 end-to-end-greenfield-example.md 的詳細度）：

1. Refactoring 範例
   - 專案：重構一個複雜的訂單處理模組
   - 展示：代碼分析 → 重構規劃 → 安全重構 → 驗證

2. Performance 範例
   - 專案：優化電商首頁載入速度
   - 展示：效能基準 → 瓶頸分析 → 優化策略 → A/B 測試

3. Integration 範例
   - 專案：整合 Stripe 支付閘道
   - 展示：API 研究 → 整合設計 → 實作 → Webhook 處理

請創建這些範例並保存至 prompts/complete-flow/
```

---

### 方式 3：補充工具文檔

```
請創建以下工具的使用文檔：

1. Static Analysis Tools
   - ESLint / Pylint / RuboCop
   - SonarQube
   - 使用指南和最佳實踐

2. Performance Testing Tools
   - JMeter / k6 / Locust
   - Lighthouse / WebPageTest
   - 使用指南和測試場景

3. CI/CD Tools
   - GitHub Actions / GitLab CI / Jenkins
   - 配置範例和最佳實踐

4. Monitoring Tools
   - Prometheus + Grafana
   - New Relic / Datadog
   - Dashboard 設定指南

請創建這些文檔並保存至 docs_template/tools/
```

---

## 📋 中斷恢復指令模板

### 如果你之前在執行某個任務時中斷了

**查看中斷點**：
```
請閱讀 AISDLC_v0.02/CHECKPOINT_LOG.md
告訴我最後完成的檢查點是哪一個。
```

**從中斷點恢復**：
```
請參考 CHECKPOINT_LOG.md，從 CHECKPOINT_[X.X.X] 繼續執行。

我想繼續執行：[描述任務]
```

---

## 🔍 驗證完成度

### 快速驗證

執行驗證腳本：
```bash
cd AISDLC_v0.02
./verify_completion.sh
```

### 手動驗證

檢查各類別檔案數量：

```bash
# Agents
ls -1 agent/specialized/*.yaml | wc -l
# 預期：10+（包含原有的 code-analyzer, performance-engineer）

# Core Workflows
ls -1 workflow/core/*.md | wc -l
# 預期：7

# Scenario Workflows
ls -1 workflow/scenario-specific/*.md | wc -l
# 預期：10

# SOPs
find scenarios -name "SOP.md" | wc -l
# 預期：7-8

# Templates
find docs_template -name "*Template.md" | wc -l
# 預期：32+

# Prompts
find prompts -name "*.md" | wc -l
# 預期：15
```

---

## 📊 查看執行總結

### 完整執行報告
閱讀：[EXECUTION_SUMMARY_20251022.md](EXECUTION_SUMMARY_20251022.md)

### 檢查點日誌
閱讀：[CHECKPOINT_LOG.md](CHECKPOINT_LOG.md)

### 原始執行計劃
閱讀：[v0.02_完整的優化計劃_20251020.md](v0.02_完整的優化計劃_20251020.md)

---

## 🚀 開始使用 AISDLC v0.02

如果你想開始實際使用框架，而非繼續擴充：

### 第 1 步：快速體驗（5 分鐘）
```
請閱讀並載入 /AISDLC_v0.02/AISDLC_INIT.md

我想體驗 AISDLC v0.02 的核心功能。

請提供 5 分鐘快速體驗（參考 prompts/quick-start/5-minute-start.md）。
```

### 第 2 步：選擇情境
參考：[prompts/quick-start/scenario-quick-reference.md](prompts/quick-start/scenario-quick-reference.md)

### 第 3 步：執行完整流程
參考：[prompts/complete-flow/end-to-end-greenfield-example.md](prompts/complete-flow/end-to-end-greenfield-example.md)

---

## 💡 常見問題

### Q: 我忘記上次執行到哪裡了
**A**: 閱讀 `CHECKPOINT_LOG.md`，查看最後一個 `[已完成]` 的檢查點。

### Q: 如何確認所有任務已完成
**A**: 執行 `./verify_completion.sh` 或閱讀 `EXECUTION_SUMMARY_20251022.md`。

### Q: Token 用完了怎麼辦
**A**:
1. 記錄當前檢查點
2. 產出「恢復執行指令」
3. 等待 Token 重置後，使用恢復指令繼續

### Q: 我想修改某個檔案
**A**: 直接編輯即可，所有檔案都是標準 Markdown/YAML 格式。

### Q: 如何新增自己的情境
**A**:
1. 參考現有情境結構（如 `scenarios/greenfield/`）
2. 創建新情境目錄
3. 創建 SOP.md
4. 更新 AISDLC_INIT.md 的情境映射表

---

## 📞 需要協助

### 技術問題
```
我在使用 AISDLC v0.02 時遇到問題。

問題描述：[詳細描述]
相關檔案：[檔案路徑]
錯誤訊息（若有）：[錯誤訊息]

請協助排查。
```

### 功能建議
```
我想為 AISDLC v0.02 建議新功能。

建議功能：[描述]
使用場景：[何時需要]
預期效果：[期望達成什麼]

請評估可行性。
```

---

## 🎉 恭喜！

AISDLC v0.02 已完整建置完成，現在你可以：

1. ✅ 使用 8 個完整情境進行開發
2. ✅ 調用 10+ 個專業 Agents
3. ✅ 執行 17 個標準化 Workflows
4. ✅ 使用 32+ 個文檔模板
5. ✅ 參考 15 個 Prompt 範例

**開始你的 AISDLC 之旅吧！** 🚀

---

**版本**: v0.02
**最後更新**: 2025-10-22
**維護者**: AISDLC Framework Team
