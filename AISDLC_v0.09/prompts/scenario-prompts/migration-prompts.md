# Migration 技術棧遷移快速啟動指令集
# Migration Quick Start Prompts

**版本**: v0.09
**適用情境**: Migration - 全技術棧遷移、資料庫平台遷移、系統現代化
**最後更新**: 2026-02-16

---

## 🚀 一鍵啟動指令

### 標準啟動

```
我需要進行技術棧遷移，將既有系統遷移到新的技術棧。

遷移資訊：
- 專案路徑：[路徑]
- 前端：[舊框架] → [新框架]
- 後端：[舊框架] → [新框架]
- 資料庫：[舊 DB] → [新 DB]
- 新平台：[如 Android/macOS]（如適用）
- 是否為生產系統：[是/否]

請載入 AISDLC_INIT.md，識別為 migration 情境，執行 Migration SOP。
```

### 快速規模評估

```
我想快速評估遷移規模與風險。

請 Code-Analyzer (CodeX) + SA (Amanda) 執行：
- 舊系統代碼掃描（頁面/API/Service/表數量）
- 技術棧複雜度評估
- 遷移風險分級（低/中/高）
- 預估遷移時程

專案路徑：[路徑]
```

---

## 📊 階段推進指令

### 階段 1：現況分析與需求提取

```
請執行 Migration SOP 階段 1：現況分析與需求提取。

請 SA (Amanda) + Code-Analyzer (CodeX) + Performance-Engineer 協作：
- 舊系統全面掃描（前端/後端/DB/業務邏輯）
- 頁面、API、Service、表數量統計
- 效能基準線建立（回應時間、吞吐量）
- Stored Procedure / View / Trigger 清單
- 業務邏輯提取與驗證

分析範圍：[全系統/特定模組]
```

### 階段 2：遷移架構設計

```
現況分析已完成，請進入階段 2：遷移架構設計。

請 SD-Architect (Marcus) + Dev-Senior + PM/PO (Victoria) 協作：
- 技術棧映射表（舊→新逐項對應）
- 遷移策略選擇（分層漸進/Strangler/Big Bang）
- 並行運行架構設計
- 遷移順序與優先級
- 預估時程與 ROI 評估
```

### 階段 3：資料庫遷移

```
遷移架構已確認，請進入階段 3：資料庫遷移。

請 SD-Architect + Dev-Senior 協作（觸發 /database-migration）：
- Schema 轉換（資料型別映射）
- SQL 語法轉換
- Stored Procedure 遷移策略（轉應用層/轉 PL/pgSQL/移除）
- 資料遷移計畫（靜態→動態→增量同步）
- 推薦遷移工具選型

來源 DB：[Oracle/MySQL/MSSQL]
目標 DB：[PostgreSQL/MySQL]
```

### 階段 4：後端遷移設計

```
DB 遷移計畫已完成，請進入階段 4：後端遷移設計。

請 SD + Dev-Senior + QA 協作：
- API 契約定義（RESTful 端點映射）
- Service 層功能替換映射
- 認證/授權機制遷移
- 中介層/排程任務對應
- 錯誤碼統一

舊後端：[框架名稱]
新後端：[框架名稱]
```

### 階段 5：前端遷移設計

```
後端遷移設計已完成，請進入階段 5：前端遷移設計。

請 SD + Dev-Senior 協作：
- 元件映射表（舊元件→新元件）
- 狀態管理遷移
- 路由系統映射
- UI 元件庫替代方案
- 頁面遷移優先順序

舊前端：[框架名稱]
新前端：[框架名稱]
```

### 階段 6：新平台開發設計

```
前端遷移設計已完成，請進入階段 6：新平台開發設計。

請 SD-Mobile-Architect + Integration-Specialist 協作（觸發 /mobile-development）：
- 行動端架構設計（原生 vs 跨平台）
- 共用 API 設計
- 掃碼/硬體功能整合規格
- 離線支援策略
- 推播通知設計

目標平台：[Android/iOS/macOS]
硬體需求：[掃碼器/NFC/藍牙]
```

### 階段 7：驗證與測試規劃

```
遷移設計已完成，請進入階段 7：驗證與測試規劃。

請 QA (Quincy) + Code-Analyzer + Performance-Engineer 協作：
- DB 遷移驗證（Schema 對齊/資料完整性）
- 跨系統一致性驗證（API 響應比對/業務計算比對）
- 效能基準對比（與階段 1 基準線對比）
- 行動端驗證（多裝置/掃碼/離線測試）
- 部署驗證（藍綠/金絲雀/回滾）
```

### 階段 8：部署與切換

```
測試驗證已通過，請進入階段 8：部署與切換。

請 DevOps + SD 協作（觸發 /devops-github-actions、/release-management）：
- CI/CD Pipeline 建立（新技術棧）
- 並行運行啟動
- 漸進式流量切換計畫
- 舊系統退役時間表
- 監控與告警設定
```

### 階段 9：知識沉澱

```
遷移部署已完成，請進入階段 9：知識沉澱。

請 Technical-Writer 協助：
- 遷移映射手冊（前端/後端/DB 對照表）
- 架構決策記錄 (ADR)
- 經驗教訓文檔
- 新技術棧開發規範
```

---

## 🔄 常見變體指令

### 變體 1：僅資料庫遷移

```
我只需要遷移資料庫平台。

遷移資訊：
- 來源 DB：[Oracle/MySQL/MSSQL]
- 目標 DB：[PostgreSQL/MySQL]
- 應用層：不變
- 是否有 Stored Procedure：[是/否，約 X 支]

請載入 AISDLC_INIT.md，識別為 migration 情境，
重點執行階段 1（DB 分析）+ 階段 3（DB 遷移）+ 階段 7（驗證）。
```

### 變體 2：前後端遷移（DB 不變）

```
我需要遷移前後端框架，資料庫保持不變。

遷移資訊：
- 前端：[舊] → [新]
- 後端：[舊] → [新]
- 資料庫：不變

請載入 AISDLC_INIT.md，識別為 migration 情境，
跳過階段 3（DB 遷移），執行其餘階段。
```

### 變體 3：系統現代化 + 新平台

```
我需要將舊系統現代化，並新增行動端支援。

遷移資訊：
- 前端：[舊] → [新]
- 後端：[舊] → [新]
- 資料庫：[舊] → [新]
- 新平台：[Android/iOS/macOS]
- 硬體整合：[掃碼/NFC]

請載入 AISDLC_INIT.md，識別為 migration 情境，
執行完整 Migration SOP（含階段 6 新平台開發）。
```

---

## 🆘 疑難排解指令

### 問題 1：遷移範圍太大，不知如何切分

```
遷移範圍太大，無法一次完成。

請 SD-Architect 制定分階段遷移計畫：
- 識別可獨立遷移的模組
- 排定遷移優先順序（依業務價值/風險/依賴關係）
- 設計模組間的過渡期介面
- 確保每階段可獨立上線
```

### 問題 2：新舊系統資料不一致

```
並行運行期間發現新舊系統資料不一致。

請排查：
- CDC 同步是否正常
- 是否有 SP/Trigger 未遷移完整
- 時區/編碼/精度是否一致
- 交易資料的寫入順序是否正確
```

### 問題 3：遷移後效能退化

```
遷移到新技術棧後效能變差。

請 Performance-Engineer 執行（觸發 /performance-optimization）：
- 與階段 1 效能基準線對比
- 識別效能瓶頸（DB 查詢/API 回應/前端載入）
- 針對性優化（索引/快取/查詢改寫）
- 確保達成效能 SLA
```

### 問題 4：Stored Procedure 遷移困難

```
Oracle SP 邏輯複雜，遷移到 PostgreSQL 困難。

請 Dev-Senior 評估：
- 哪些 SP 可轉為應用層 Service
- 哪些 SP 需轉為 PL/pgSQL
- 哪些 SP 可安全移除
- 複雜 SP 的分步遷移策略
```

---

## 📚 參考資源

### 相關文檔
- [Migration SOP](../../scenarios/migration/SOP.md)
- [Migration QuickRef](../../scenarios/migration/SOP_QuickRef.md)
- [Refactoring DeepDive Part 11 - 技術棧遷移深度指南](../../scenarios/refactoring/SOP_DeepDive.md)
- [AISDLC_INIT.md](../../AISDLC_INIT.md)

### 相關 Skills
- `/database-migration` - 資料庫遷移
- `/mobile-development` - 行動端開發
- `/integration-database` - DB 整合方案
- `/integration-api-client` - API 契約設計
- `/devops-github-actions` - CI/CD Pipeline
- `/release-management` - 發布管理
- `/performance-optimization` - 效能優化
- `/security-audit` - 安全審計

### 相關 Agents
- [sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus（遷移架構主導）
- [sa-analyst-zh.yaml](../../agent/core/04.sa-analyst-zh.yaml) - Amanda（需求分析）
- [code-analyzer-zh.yaml](../../agent/specialized/code-analyzer-zh.yaml) - CodeX（代碼分析）
- [dev-senior-zh.yaml](../../agent/specialized/dev-senior-zh.yaml) - Senior Developer
- [sd-mobile-architect-zh.yaml](../../agent/specialized/sd-mobile-architect-zh.yaml) - Mobile Architect
- [integration-specialist-zh.yaml](../../agent/specialized/integration-specialist-zh.yaml) - Integration Specialist
- [performance-engineer-zh.yaml](../../agent/specialized/performance-engineer-zh.yaml) - Performance Engineer

---

**版本**: v0.09
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-16
