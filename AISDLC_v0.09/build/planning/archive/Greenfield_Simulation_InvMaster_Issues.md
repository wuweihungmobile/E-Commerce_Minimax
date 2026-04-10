# Greenfield 情境模擬問題彙整報告

**模擬日期**: 2026-03-23
**模擬專案**: InvMaster 經銷存管理系統
**模擬情境**: React(Next.js) + Spring Boot + PostgreSQL + Android + macOS
**發現問題數**: 25 項

---

## 問題分類統計

| 嚴重等級 | 數量 | 說明 |
|---------|------|------|
| 🔴 Critical | 3 | 流程錯誤或安全隱患，必須立即修正 |
| 🟡 High | 9 | 重要遺漏，影響企業級專案的完整執行 |
| 🟢 Medium | 10 | 改進建議，可提升 SOP 品質和適用範圍 |
| ⚪ Low | 3 | 次要建議，可選擇性改進 |

---

## 🔴 Critical 問題 (3 項)

### #15 [Critical] WebSocket 配置範例存在安全隱患
- **位置**: SOP Stage 5, 步驟 5.2.4
- **問題**: Spring Boot WebSocket 範例使用 `.setAllowedOrigins("*")`，允許所有來源連接
- **影響**: 生產環境中可能被跨站 WebSocket 劫持攻擊
- **改善**: 加註「開發環境專用」警告，補充生產環境安全配置範例

### #21 [Critical] 缺少跨平台整合測試流程指引
- **位置**: SOP Stage 10, 步驟 10.3
- **問題**: 開發-編譯-測試循環只覆蓋單一技術棧，缺少跨平台整合測試指引
- **影響**: 多平台專案（Web+Mobile+Desktop）無法有效驗證端到端資料流
- **改善**: 新增「跨平台整合測試流程」章節，包含 API→WebSocket→Multi-Client 測試策略

### #23 [Critical] 完全缺少軟刪除 vs 硬刪除策略
- **位置**: SOP Stage 4/5 全域
- **問題**: 經銷存系統幾乎所有刪除都是軟刪除，但 SOP 無相關指引
- **影響**: 資料庫設計可能遺漏 deleted_at/is_active 欄位，導致歷史紀錄丟失
- **改善**: 在 Stage 5 資料庫設計加入「刪除策略設計」章節

---

## 🟡 High 問題 (9 項)

### #1 [High] Stage 1 情境識別缺少 Hybrid 架構快速判斷
- **位置**: SOP Stage 1, 步驟 1.2
- **問題**: 多平台混合架構識別需跳到 Stage 3 才能確認 Type A/B/C/D
- **改善**: Stage 1 增加「多平台快速識別」引導問題

### #4 [High] NFR 引導問題偏向 Mobile App，缺少 Web/Backend 引導
- **位置**: SOP Stage 2, 步驟 2.2
- **問題**: NFR 模板缺少瀏覽器相容性、資料庫效能、印表機整合、資料合規等引導
- **改善**: 補充「Web/Backend 系統專用 NFR 引導問題」

### #5 [High] 缺少領域專用業務規則引導
- **位置**: SOP Stage 2, 人機協作點 4
- **問題**: 經銷存系統有大量領域特定業務規則（FIFO/LIFO、稅金計算、帳款對帳），SOP 無引導
- **改善**: 增加「常見產業領域業務規則提示」或提示 AI 需要主動詢問

### #8 [High] macOS Desktop 平台 Agent 分類不當
- **位置**: SOP Stage 3, 平台 Architect 選擇表
- **問題**: macOS + SwiftUI 歸類為 `sd-web-architect`，但 SwiftUI 是 Apple 原生框架
- **改善**: macOS SwiftUI 應可選擇 `sd-mobile-architect`，或新增說明

### #12 [High] BA-SA 審查流程缺少 AI-to-AI 審查指引
- **位置**: SOP Stage 4, 步驟 4.5
- **問題**: 2 人團隊中 BA 和 SA 都是 AI 角色，缺少 AI 互審的操作指引
- **改善**: 增加「AI Agent 協作模式下的審查簡化方案」

### #16 [High] 缺少業務流程型 Epic 拆解範例
- **位置**: SOP Stage 6, 步驟 6.2.4
- **問題**: 範例只有簡單的「搜尋功能」，缺少多步驟審核流程的拆解範例
- **改善**: 增加「含審核節點的業務流程 Epic 拆解範例」

### #22 [High] 缺少修改操作的變更影響分析指引
- **位置**: SOP Stage 10
- **問題**: 修改操作可能產生連鎖影響（如修改單價影響未結帳訂單），缺少分析指引
- **改善**: 新增「變更影響分析（CIA）檢查清單」

### #24 [High] 缺少 Mobile/Desktop App 打包發布流程
- **位置**: SOP Stage 11, 步驟 11.1
- **問題**: 只有 Docker 容器化部署，缺少 APK/macOS App 發布流程
- **改善**: 增加「多平台發布指引」（Android Play Store + macOS App 分發）

### #25 [High] 缺少具體回滾策略
- **位置**: SOP Stage 11, 步驟 11.2
- **問題**: 只提到「回滾方案準備」，缺少 Blue-Green/DB Migration 等具體策略
- **改善**: 新增「回滾策略選項與決策指引」

---

## 🟢 Medium 問題 (10 項)

### #2 [Medium] DevOps Agent 載入時機不一致
- **位置**: SOP Stage 1/8/11
- **問題**: DevOps Agent 在九階段對照表中未列出，但 Stage 8 需要
- **改善**: 在 Agent 載入對照表補充 DevOps Agent 載入時機

### #3 [Medium] AI Agent 執行專案初始化缺少指引
- **位置**: SOP Stage 1, 步驟 1.4
- **問題**: AI Agent 如何執行 init_project.sh 缺少說明
- **改善**: 增加 AI Agent 操作模式說明

### #6 [Medium] 硬體整合確認問題缺少 POS/電子秤/RFID
- **位置**: SOP Stage 2, 步驟 2.3
- **問題**: H1-H6 只覆蓋掃碼器和印表機
- **改善**: 擴充硬體整合確認問題（H7-H12: POS、電子秤、RFID、收銀機）

### #7 [Medium] 完整性檢查 120 項未內嵌 SOP
- **位置**: SOP Stage 2, 步驟 2.4
- **問題**: 需另外讀取 Completeness_Checklist.md，增加 Token 消耗
- **改善**: 可在 SOP 中增加「關鍵檢查項目摘要」

### #9 [Medium] 缺少 Spring Boot + Next.js 整合技術組合
- **位置**: SOP Stage 3, 步驟 3.3
- **問題**: 全端技術組合建議中缺少此常見組合
- **改善**: 在「全端應用技術組合建議」補充 Java Full-stack (Next.js + Spring Boot)

### #10 [Medium] 缺少企業級系統成本估算範例
- **位置**: SOP Stage 3, 步驟 3.3
- **問題**: 成本估算範例只有 MoneyTracker（小型 App）
- **改善**: 增加中型企業系統成本估算範例

### #13 [Medium] 缺少 C4 Level 3 實際範例
- **位置**: SOP Stage 5, 步驟 5.2.1
- **問題**: 只有 Level 3 必要性判斷和繪製建議，缺少實際範例
- **改善**: 增加「企業級系統 C4 Level 3 Component Diagram 範例」

### #14 [Medium] 缺少 Hibernate + Flyway 搭配策略說明
- **位置**: SOP Stage 5, 步驟 5.2.2
- **問題**: Spring Boot + JPA 專案的 auto-ddl vs Flyway 搭配未說明
- **改善**: 在資料庫遷移計畫中補充 JPA + Flyway 最佳實踐

### #17 [Medium] 缺少連續掃描模式的 AC 範例
- **位置**: SOP Stage 6, 步驟 6.2.6
- **問題**: 掃碼 AC 只有單次掃描，缺少批次/連續掃描模式
- **改善**: 補充「連續掃描模式」AC 範例

### #18 [Medium] 跨 Sprint 依賴類型缺少「環境依賴」
- **位置**: SOP Stage 7, 步驟 7.2.1
- **問題**: 缺少 Google Play/Apple Developer 帳號等外部環境依賴
- **改善**: 增加第 5 種依賴類型「環境依賴（Environment Dependency）」

---

## ⚪ Low 問題 (3 項)

### #11 [Low] Kano 問卷對小團隊不適用
- **位置**: SOP Stage 4, 步驟 4.2
- **問題**: 建議 30-50 位受訪者，對 2 人團隊不可行
- **改善**: 增加「小團隊替代方案：專家評估法」

### #19 已確認無問題（CI/CD 模板文件存在）
### #20 已確認無問題（verify_traceability.sh 存在）

---

## 改善優先級建議

### 第一優先（立即修正）
1. #15 WebSocket 安全範例修正
2. #23 軟刪除策略指引新增
3. #21 跨平台整合測試流程

### 第二優先（盡快修正）
4. #4 NFR 引導問題擴充（Web/Backend）
5. #8 macOS Agent 分類修正
6. #24 多平台發布指引
7. #16 業務流程型 Epic 拆解範例
8. #22 變更影響分析指引
9. #25 回滾策略指引

### 第三優先（後續改善）
10. 其餘 Medium/Low 問題
