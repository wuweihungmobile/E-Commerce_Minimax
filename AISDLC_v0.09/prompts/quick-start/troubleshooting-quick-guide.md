# AISDLC v0.02 常見問題快速解決指南
# Troubleshooting Quick Guide

**版本**: v0.02
**用途**: 快速解決常見問題
**最後更新**: 2025-10-22

---

## 📋 目錄

- [初始化問題](#初始化問題)
- [情境選擇問題](#情境選擇問題)
- [Workflow 執行問題](#workflow-執行問題)
- [Agent 協作問題](#agent-協作問題)
- [文檔生成問題](#文檔生成問題)
- [效能與 Token 問題](#效能與-token-問題)

---

## 🚨 初始化問題

### 問題 1：不知道如何開始

**症狀**：完全不知道第一步該做什麼

**解決方案**：
```
請載入 AISDLC v0.02 框架。
執行指令：請閱讀並載入 /AISDLC_v0.02/AISDLC_INIT.md

我想開始使用 AISDLC，請提供 5 分鐘快速體驗。
```

**參考資源**：
- [5-minute-start.md](./5-minute-start.md)
- [QUICK_START_GUIDE.md](../../QUICK_START_GUIDE.md)

---

### 問題 2：AISDLC_INIT.md 載入失敗

**症狀**：提示找不到 AISDLC_INIT.md

**可能原因**：
1. 路徑不正確
2. 文件不存在
3. 權限問題

**解決方案**：
```
# 方案 1：使用絕對路徑
請載入 /Users/[你的路徑]/AISDLC_v0.02/AISDLC_INIT.md

# 方案 2：確認文件位置
請確認 AISDLC_INIT.md 是否存在於 AISDLC_v0.02/ 目錄下
```

---

## 🎯 情境選擇問題

### 問題 3：不知道選哪個情境

**症狀**：有任務但不確定該用哪個情境

**解決方案**：
```
我不確定應該選擇哪個情境。

我的任務是：[詳細描述你的任務]

請根據 SCENARIO_SELECTOR.md，協助我選擇最適合的情境。
```

**決策輔助**：
```
問自己：
1. 是新專案還是既有專案？
   - 新專案 → Greenfield
   - 既有專案 → Brownfield

2. 主要目標是什麼？
   - 開發新功能 → Greenfield/Brownfield
   - 改善代碼品質 → Refactoring
   - 提升效能 → Performance
   - 整合第三方 → Integration
   - 建立自動化 → DevOps/Testing
   - 撰寫文檔 → Documentation
```

**參考資源**：
- [scenario-quick-reference.md](./scenario-quick-reference.md)
- [SCENARIO_SELECTOR.md](../../SCENARIO_SELECTOR.md)

---

### 問題 4：需要組合多個情境

**症狀**：單一情境無法滿足需求

**解決方案**：
```
我的任務需要結合多個情境。

主要情境：[Greenfield/Brownfield/等]
次要情境：[Integration/Performance/等]

請協助我規劃如何組合這些情境的 workflows。
```

**常見組合**：
- Greenfield + Integration（新專案 + 第三方整合）
- Brownfield + Performance（既有系統修改 + 效能優化）
- Greenfield + DevOps + Testing（新專案 + CI/CD + 測試）

---

## 🔄 Workflow 執行問題

### 問題 5：Workflow 執行中斷

**症狀**：Workflow 執行到一半停止

**解決方案**：
```
我在執行 [workflow-name] 時中斷了。

上次執行到：[階段 X]
已完成：[列出已完成的部分]

請從上次中斷點繼續執行。
```

**預防措施**：
- AISDLC 有自動 checkpoint 機制
- 重要階段完成後會保存狀態
- 可以隨時恢復執行

---

### 問題 6：不知道 Workflow 在哪個階段

**症狀**：迷失在 Workflow 中，不知道當前進度

**解決方案**：
```
我在執行 [workflow-name]，但不確定當前進度。

請告訴我：
1. 當前執行到哪個階段
2. 已完成哪些階段
3. 還剩哪些階段
4. 預計還需要多久
```

---

### 問題 7：Workflow 跳過了某個階段

**症狀**：感覺 Workflow 沒有執行某個重要階段

**解決方案**：
```
我覺得 [workflow-name] 跳過了 [階段 X]。

請確認：
1. 這個階段是否應該執行
2. 如果跳過，原因是什麼
3. 我可以要求補充執行嗎
```

**注意**：某些階段在特定條件下會自動跳過（例如：系統無 API 時跳過 API 規格階段）

---

## 🤖 Agent 協作問題

### 問題 8：Agent 沒有回應

**症狀**：調用 Agent 後沒有得到回應

**解決方案**：
```
# 方案 1：確認 Agent 是否正確調用
請調用 [agent-name] agent 協助我。

任務：[明確描述任務]

# 方案 2：檢查 Agent 是否存在
請列出目前可用的所有 Agents。

# 方案 3：使用 Agent 全名
請調用 SA agent (Amanda) 協助我分析需求。
```

---

### 問題 9：Agent 提供的選項太多

**症狀**：Agent 提供了很多選項，不知道如何選擇

**解決方案**：
```
[Agent] 提供了太多選項，我不確定如何選擇。

選項：[列出選項]
我的考量：[描述你的限制/偏好]

請根據我的情況推薦最適合的選項，並說明理由。
```

**技巧**：使用「編號選項協議」直接回應數字即可

---

### 問題 10：需要特定 Agent 但不知道調用方式

**症狀**：知道需要某個專業 Agent 但不知道怎麼調用

**解決方案**：
```
# 查看所有可用 Agents
請列出 AISDLC v0.02 所有可用的 Agents 及其職責。

# 查看特定 Agent 詳細資訊
請詳細說明 [agent-name] agent 的：
- 主要職責
- 專業能力
- 適用情境
- 可產出的文檔
```

**常用 Agents 快速參考**：
- 需求分析 → `SA agent (Amanda)`
- 技術設計 → `SD architect (Marcus)`
- 代碼分析 → `Code-Analyzer (CodeX)`
- 效能優化 → `Performance-Engineer (Perf)`
- 第三方整合 → `Integration-Specialist (IntegX)`
- DevOps → `DevOps-Engineer`
- 測試策略 → `QA-Lead`
- 技術文檔 → `Technical-Writer (DocX)`

---

## 📝 文檔生成問題

### 問題 11：生成的文檔不符合預期

**症狀**：文檔內容不是我想要的

**解決方案**：
```
生成的 [文檔類型] 不符合我的預期。

問題：
1. [具體問題 1]
2. [具體問題 2]

我的期望：
- [期望 1]
- [期望 2]

請根據我的反饋重新生成。
```

**技巧**：明確指出問題和期望，AI 會調整

---

### 問題 12：文檔格式需要調整

**症狀**：文檔內容正確但格式不符合公司規範

**解決方案**：
```
文檔內容正確，但需要調整格式。

格式需求：
- [格式要求 1]
- [格式要求 2]
- 參考模板：[提供公司模板路徑或描述]

請按照這個格式重新生成。
```

---

### 問題 13：文檔間追蹤鏈斷裂

**症狀**：PRD/FRD/SRD 之間連結不完整

**解決方案**：
```
請執行 consistency-check workflow。

檢查範圍：
- PRD → FRD → SRD → API Specs 追蹤鏈
- User Stories → Acceptance Criteria → Acceptance Tests
- 文檔間的連結有效性

發現問題請修正。
```

---

## ⚡ 效能與 Token 問題

### 問題 14：Token 用完了

**症狀**：提示 Token 不足，無法繼續

**解決方案**：
```
Token 已接近上限，請：
1. 保存當前 Checkpoint
2. 產出「恢復執行指令」
3. 總結已完成的工作
4. 說明下次如何繼續

我會在 Token 重置後繼續執行。
```

**預防措施**：
- 定期檢查 Token 使用量
- 優先完成重要階段
- 及時保存 Checkpoint

---

### 問題 15：執行速度太慢

**症狀**：每個階段執行時間過長

**可能原因**：
1. 任務過於複雜
2. 需要處理的資料量大
3. 多次反覆確認

**解決方案**：
```
# 方案 1：簡化任務範圍
請聚焦在最核心的部分，其他可以後續補充。

# 方案 2：使用快速模式
請使用快速模式執行 [workflow-name]，跳過非關鍵步驟。

# 方案 3：分階段執行
今天先執行階段 1-3，明天再執行階段 4-6。
```

---

## 🔧 其他常見問題

### 問題 16：確認點太多，中斷太頻繁

**症狀**：覺得確認點 🔴 太多，影響流程

**解決方案**：
```
我信任 AISDLC 的判斷，請減少確認點。

保留確認點：
- [關鍵決策 1]
- [關鍵決策 2]

其他決策請自動推進，使用合理預設值。
```

**注意**：減少確認點會提升速度但可能降低結果符合度

---

### 問題 17：需要自訂流程

**症狀**：標準流程不完全符合公司規範

**解決方案**：
```
AISDLC 的標準流程需要調整以符合公司規範。

調整需求：
1. [調整 1：例如增加審批點]
2. [調整 2：例如使用特定模板]
3. [調整 3：例如整合 JIRA]

請協助我自訂流程。
```

---

### 問題 18：多人協作如何使用 AISDLC

**症狀**：團隊多人想同時使用 AISDLC

**解決方案**：
- **方案 1**：分工執行不同情境
  ```
  成員 A：負責 Greenfield 需求分析
  成員 B：負責 DevOps Pipeline 設定
  成員 C：負責 Testing 策略規劃

  各自執行完後整合產出文檔。
  ```

- **方案 2**：接力執行
  ```
  成員 A 執行階段 1-3 → 保存 Checkpoint
  成員 B 從 Checkpoint 繼續執行階段 4-6
  ```

- **方案 3**：使用版本控制
  ```
  所有產出文檔放入 Git Repository
  使用 PR 流程審查和合併
  ```

---

## 📚 延伸資源

### 核心文檔
- [AISDLC_INIT.md](../../AISDLC_INIT.md) - 框架初始化
- [QUICK_START_GUIDE.md](../../QUICK_START_GUIDE.md) - 快速啟動
- [SCENARIO_SELECTOR.md](../../SCENARIO_SELECTOR.md) - 情境選擇

### 使用指南
- [5-minute-start.md](./5-minute-start.md) - 5 分鐘快速體驗
- [scenario-quick-reference.md](./scenario-quick-reference.md) - 情境快速參考
- [common-commands.md](./common-commands.md) - 常用指令速查

### 範例
- [end-to-end-greenfield-example.md](../complete-flow/end-to-end-greenfield-example.md) - Greenfield 完整範例
- [各情境 Prompts](../scenario-prompts/) - 所有情境指令集

---

## 🆘 仍然無法解決？

如果以上方案都無法解決你的問題：

```
我遇到了一個問題，以上 Troubleshooting Guide 沒有涵蓋。

問題描述：[詳細描述問題]
已嘗試：[列出已嘗試的解決方案]
錯誤訊息（若有）：[貼上錯誤訊息]

請協助我排查。
```

或查閱：
- [AISDLC GitHub Issues](https://github.com/[repo]/issues)（若公開）
- 團隊內部技術支援

---

**版本**: v0.02
**維護者**: AISDLC Framework Team
**最後更新**: 2025-10-22
**使用提示**: 遇到問題先查本指南，90% 的問題都能快速解決！
