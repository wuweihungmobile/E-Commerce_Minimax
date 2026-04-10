# 情境 Agent 配置映射表
# Scenario-Agent Mapping Guide

**版本**: v0.03-phase2
**建立日期**: 2025-10-23
**用途**: 定義每個情境使用的 Agents 及其角色分工

---

## 📋 文檔目的

本文檔提供：
1. **10大情境的完整Agent配置表**
2. **Primary vs Supporting Agents 清單**
3. **協作模式說明**
4. **Phase 2 優化建議實施記錄**

---

## 🎯 情境Agent配置總覽

| 情境 | Primary Agents | Supporting Agents | 主要協作模式 | 配置評分 |
|------|---------------|-------------------|-------------|---------|
| **Greenfield** | pm-po, sa | ba, sd, qa, dev; 選用: security-engineer, compliance-officer, sd-mobile-architect, qa-mobile-tester, integration-specialist ⭐ | Lead-Support | 10/10 ✅ (v0.09 優化) |
| **Brownfield** | sa, dev-senior | code-analyzer, qa, sd; 選用: security-engineer, compliance-officer, sd-mobile-architect, qa-mobile-tester, integration-specialist ⭐ | Lead-Support | 10/10 ✅ (v0.09 優化) |
| **Refactoring** | sd | code-analyzer, dev-senior, qa | Lead-Support | 9/10 ✅ |
| **Performance** | performance-engineer | sd, dev-senior, qa-automation; 選用: devops-engineer, code-analyzer, security-engineer ⭐ | Lead-Support + Iterative | 10/10 ✅ (v0.09 優化) |
| **Integration** | integration-specialist | sd, qa, dev | Lead-Support | 9/10 ✅ |
| **DevOps** | devops-engineer | sd, qa-automation | Lead-Support + Parallel | 10/10 ✅ |
| **Testing** | qa-lead | qa-automation, qa-tester, dev | Parallel-Convergence | 9/10 ✅ |
| **Documentation** | technical-writer | sa, sd, dev-senior; 選用: security-engineer, compliance-officer, sd-mobile-architect ⭐ | Sequential-Handoff + Peer-Review | 10/10 ✅ (v0.09 優化) |
| **Security** | security-engineer | compliance-officer, qa-lead, sd | Peer-Review | 10/10 ✅ |
| **Migration** | sd, sa | code-analyzer, dev-senior, qa, devops-engineer, integration-specialist | Lead-Support + Sequential | 10/10 ✅ 🆕 |

---

## 🔹 Greenfield - 全新專案開發

### Agent 配置
```yaml
Primary Agents:
  - pm-po-agent (Lead)
    Role: 產品決策者，主導需求收集和優先級排序
    Deliverables: PRD

  - sa-analyst
    Role: 需求分析，將PRD轉化為詳細功能需求
    Deliverables: FRD, User Stories

Supporting Agents:
  - ba-business-analyst
    Role: 業務驗證，利害關係人溝通
    Contributions: 業務邏輯驗證、PRD/FRD審查

  - sd-architect
    Role: 技術可行性評估，架構設計
    Deliverables: SRD, 架構設計

  - qa-tester
    Role: 測試性評估，AC驗證
    Deliverables: 測試計畫, AT

  - dev-developer
    Role: 開發實作可行性評估
    Contributions: 實作建議、技術風險識別

Optional Agents: ⭐ v0.09 新增
  - security-engineer
    Role: 安全架構設計（Stage 3 按需載入）
    Contributions: 威脅模型、安全架構、OWASP 評估
    Condition: 專案涉及敏感資料、認證授權、合規要求時

  - compliance-officer
    Role: 合規需求確認（Stage 3 按需載入）
    Contributions: 合規對照表、資料隱私政策
    Condition: 專案涉及 GDPR/PCI-DSS/ISO 27001 等法規時

  - sd-mobile-architect
    Role: 行動端架構設計（Stage 3 按需載入）
    Contributions: Mobile 架構設計、原生/跨平台策略
    Condition: 專案涉及 iOS/Android/macOS 行動端開發時

  - qa-mobile-tester
    Role: 行動端測試（Stage 6 按需載入）
    Contributions: Mobile 測試策略、裝置測試矩陣
    Condition: 專案涉及行動端應用需要專業化測試時

  - integration-specialist
    Role: 第三方整合分析（Stage 2 按需載入）
    Contributions: API 研究、整合設計、硬體整合
    Condition: 專案涉及硬體整合或第三方系統整合時
```

### 協作模式
**主要模式**: Lead-Support
```
pm-po (Lead) 主導產品方向
    ↓
sa + ba + sd + qa + dev (Support) 提供專業建議
    ↓
pm-po 整合決策
    ↓
🔴 人類確認
```

**次要模式**: Sequential-Handoff
```
pm-po → PRD → 🔴 → sa → FRD → 🔴 → sd → SRD → 🔴
```

### 工作流程
1. pm-po 主導需求收集
2. ba 驗證業務需求
3. sa 產出 FRD
4. sd 設計架構和 SRD
5. qa 定義測試策略
6. dev 評估實作方案

---

## 🔹 Brownfield - 既有系統分析

### Agent 配置
```yaml
Primary Agents:
  - sa-analyst (Lead)
    Role: 主導既有系統分析
    Deliverables: 系統現況分析報告, FRD (改進需求)

  - dev-senior
    Role: 資深技術決策，複雜系統評估
    Deliverables: 技術債務分析, 改進建議

Supporting Agents:
  - code-analyzer
    Role: 代碼結構分析，品質評估
    Deliverables: 代碼分析報告, 重構建議

  - qa-tester
    Role: 測試覆蓋度分析
    Contributions: 測試差距識別

  - sd-architect
    Role: 架構層面分析
    Contributions: 架構改進建議

Optional Agents: ⭐ v0.09 新增
  - security-engineer
    Role: 安全性變更評估（Stage 3 按需載入）
    Contributions: 安全漏洞修復方案、認證授權變更評估
    Condition: 變更涉及認證授權、資料保護、安全漏洞修復時

  - compliance-officer
    Role: 合規驅動變更（Stage 3 按需載入）
    Contributions: 合規對照表、法規影響分析
    Condition: 變更由法規/會計準則/資安合規驅動時

  - sd-mobile-architect
    Role: 行動端架構分析（Stage 2 按需載入）
    Contributions: Mobile 架構評估、跨平台擴展設計
    Condition: 變更涉及 Android/iOS/macOS 行動平台時

  - qa-mobile-tester
    Role: 行動端測試（Stage 6 按需載入）
    Contributions: Mobile 測試策略、裝置相容性測試
    Condition: 變更涉及行動裝置功能時

  - integration-specialist
    Role: 第三方整合分析（Stage 4 按需載入）
    Contributions: API 研究、硬體整合設計
    Condition: 新增功能涉及外部 API 或硬體設備整合時
```

### 協作模式
**主要模式**: Lead-Support
```
sa (Lead) 主導分析流程
    ↓
dev-senior + code-analyzer + qa + sd (Support)
    ↓
sa 整合分析結果
    ↓
🔴 人類確認改進方向
```

---

## 🔹 Performance - 效能優化 ⭐ Phase 2 已優化

### Agent 配置
```yaml
Primary Agent:
  - performance-engineer (Lead)
    Role: 主導效能分析和優化策略
    Deliverables: 效能分析報告, 優化方案, 測量結果

Supporting Agents:
  - sd-architect
    Role: 架構層面優化建議
    Contributions: 架構瓶頸分析, 架構優化方案

  - dev-senior
    Role: 代碼層面優化建議
    Contributions: 代碼優化, 演算法改進

  - qa-automation ⭐ Phase 2 新增
    Role: 效能測試自動化
    Contributions: 自動化效能測試, 持續監控

Optional Agents: ⭐ v0.09 新增
  - devops-engineer
    Role: 基礎設施優化（Stage 4 按需載入）
    Contributions: 負載均衡、容器資源調整、監控告警設定
    Condition: 優化涉及部署架構、基礎設施擴展、監控設定時

  - code-analyzer
    Role: 代碼級效能分析（Stage 2 按需載入）
    Contributions: 熱點函數分析、複雜度評估、記憶體洩漏檢測
    Condition: 需要深入代碼級效能分析時

  - security-engineer
    Role: 安全與效能權衡（Stage 3 按需載入）
    Contributions: 加密效能評估、安全標頭優化、TLS 配置
    Condition: 優化涉及安全相關效能（加密、認證、安全掃描）時
```

### Phase 2 優化
```diff
+ 新增 qa-automation 為 Supporting Agent
+ 理由: 效能測試自動化需要專業支援
+ 影響: 提升效能測試覆蓋度和持續監控能力
```

### v0.09 增強
```diff
+ 新增 devops-engineer, code-analyzer, security-engineer 為 Optional Agents
+ 新增 consistency-check Workflow
+ 理由: 效能優化可能涉及基礎設施、代碼分析、安全權衡
+ 影響: 覆蓋更完整的效能優化場景
```

### 協作模式
**主要模式**: Lead-Support + Iterative-Refinement
```
Iteration循環:
  performance-engineer 識別瓶頸
      ↓
  sd + dev-senior + qa-automation 提供優化建議
      ↓
  performance-engineer 實施優化
      ↓
  測量驗證
      ↓
  達標? → 是 → 🔴 完成
       → 否 → 繼續迭代
```

---

## 🔹 Integration - 第三方系統整合

### Agent 配置
```yaml
Primary Agent:
  - integration-specialist (Lead)
    Role: 主導整合分析和設計
    Deliverables: API研究報告, 整合設計, 資料對應表

Supporting Agents:
  - sd-architect
    Role: 整合架構設計
    Deliverables: 整合架構圖, API設計

  - qa-tester
    Role: 整合測試設計
    Deliverables: 整合測試計畫

  - dev-developer
    Role: 整合實作評估
    Contributions: 實作難度評估, API客戶端設計
```

### 協作模式
**主要模式**: Lead-Support
```
integration-specialist (Lead)
    ↓ API研究
sd (架構設計) + qa (測試) + dev (實作)
    ↓
integration-specialist 整合設計方案
    ↓
🔴 人類確認
```

---

## 🔹 Documentation - 技術文檔生成 ⭐ Phase 2 已優化 + v0.09 增強

### Agent 配置
```yaml
Primary Agent:
  - technical-writer (Lead)
    Role: 主導技術文檔撰寫
    Deliverables: 技術文檔, API文檔, 知識庫

Supporting Agents:
  - sa-analyst
    Role: 功能文檔審查
    Contributions: 功能描述, 用戶指南

  - sd-architect
    Role: 技術架構文檔審查
    Contributions: 架構圖, 技術決策文檔

  - dev-senior ⭐ Phase 2 新增
    Role: 複雜技術文檔審查
    Contributions: 深度技術細節審查, 代碼範例

Optional Agents: ⭐ v0.09 新增
  - security-engineer
    Role: 安全文檔撰寫（Stage 6 按需載入）
    Contributions: 安全架構文檔, 威脅模型, 安全測試計畫
    Condition: 文檔涉及安全架構、威脅模型、安全測試報告時

  - compliance-officer
    Role: 合規文檔撰寫（Stage 6 按需載入）
    Contributions: 合規對照表, 資料隱私政策, 稽核檢查清單
    Condition: 文檔涉及法規合規（GDPR/PCI-DSS/ISO 27001）時

  - sd-mobile-architect
    Role: 行動端安全文檔（Stage 6 按需載入）
    Contributions: 行動端安全規範, Certificate Pinning 策略
    Condition: 文檔涉及 Android/iOS/macOS 行動端安全時
```

### Phase 2 優化
```diff
+ 新增 dev-senior 為 Supporting Agent
+ 理由: 複雜技術文檔需要資深開發者審查
+ 影響: 提升技術文檔深度和準確性
```

### v0.09 增強
```diff
+ 新增 security-engineer, compliance-officer, sd-mobile-architect 為 Optional Agents
+ 新增 Stage 6: 安全與合規文檔（選用階段）
+ 新增 Workflows: api-specification, interaction-analysis
+ 理由: 文檔維護涉及安全合規需求時，需要專業 Agent 參與
+ 影響: 支援安全敏感專案的完整文檔體系建立
```

### 協作模式
**主要模式**: Sequential-Handoff + Peer-Review
```
sa/sd 產出技術內容
    ↓
technical-writer 轉化為文檔
    ↓
dev-senior peer review
    ↓
technical-writer 修訂
    ↓
🔴 人類批准
```

---

## 🔹 DevOps - CI/CD 與自動化部署

### Agent 配置
```yaml
Primary Agent:
  - devops-engineer (Lead)
    Role: 主導 DevOps 流程設計
    Deliverables: CI/CD Pipeline, 部署腳本, 監控配置

Supporting Agents:
  - sd-architect
    Role: 基礎設施架構設計
    Contributions: 雲端架構, 容器化策略

  - qa-automation
    Role: 自動化測試整合
    Contributions: 測試自動化整合CI/CD
```

### 協作模式
**主要模式**: Lead-Support + Parallel-Convergence
```
devops-engineer 分配任務
    ↓
┌─────────────────┬────────────────┐
│ devops: CI/CD   │ qa-auto: Test  │
│ sd: Infra       │               │
└─────────────────┴────────────────┘
    ↓
devops-engineer 整合
    ↓
🔴 人類確認
```

---

## 🔹 Refactoring - 代碼重構

### Agent 配置
```yaml
Primary Agent:
  - sd-architect (Lead)
    Role: 主導重構策略設計
    Deliverables: 重構計畫, 架構改進設計

Supporting Agents:
  - code-analyzer
    Role: 識別重構範圍
    Deliverables: 複雜度分析, 重構優先級

  - dev-senior
    Role: 重構技術決策
    Contributions: 重構策略, 風險評估

  - qa-tester
    Role: 重構驗證測試
    Deliverables: 回歸測試計畫
```

### 協作模式
**主要模式**: Lead-Support + Iterative-Refinement

---

## 🔹 Testing - 測試策略與執行

### Agent 配置
```yaml
Primary Agent:
  - qa-lead (Lead)
    Role: 主導測試策略
    Deliverables: 測試策略, 測試計畫, 測試報告

Supporting Agents:
  - qa-automation
    Role: 自動化測試開發
    Deliverables: 自動化測試框架, 測試腳本

  - qa-tester
    Role: 測試案例設計和執行
    Deliverables: 測試案例, 測試結果

  - dev-developer
    Role: 可測試性支援
    Contributions: 測試工具支援
```

### 協作模式
**主要模式**: Parallel-Convergence
```
qa-lead 分配測試任務
    ↓
┌─────────────┬──────────────┬───────────┐
│ qa-auto     │ qa-tester    │ qa-mobile │
│ (自動化)     │ (手動測試)    │ (行動端)   │
└─────────────┴──────────────┴───────────┘
    ↓
qa-lead 整合測試報告
    ↓
🔴 人類確認
```

---

## 🔹 Security - 安全設計與審查

### Agent 配置
```yaml
Primary Agent:
  - security-engineer (Lead)
    Role: 主導安全設計和審查
    Deliverables: 安全架構, 威脅建模, 安全測試計畫

Supporting Agents:
  - compliance-officer
    Role: 合規審查
    Deliverables: 合規檢查清單, 審計準備

  - qa-lead
    Role: 安全測試策略
    Contributions: 安全測試計畫

  - sd-architect
    Role: 架構安全審查
    Contributions: 安全架構建議
```

### 協作模式
**主要模式**: Peer-Review
```
security-engineer 產出安全設計
    ↓
sd + qa-lead peer review
    ↓
security-engineer 修訂
    ↓
compliance-officer 合規審查
    ↓
🔴 人類批准
```

---

## 🔹 Migration - 技術棧遷移 🆕

### Agent 配置
```yaml
Primary Agents:
  - sd-architect (Lead)
    Role: 遷移架構設計、技術棧映射、並行運行策略
    Deliverables: 遷移架構設計、技術映射表、部署策略

  - sa-analyst
    Role: 需求重新分析、業務邏輯提取與驗證
    Deliverables: 現況分析報告、業務邏輯清單、需求映射

Supporting Agents:
  - code-analyzer
    Role: 舊系統代碼品質分析、遷移影響評估
    Deliverables: 代碼分析報告、遷移複雜度評估

  - dev-senior
    Role: 遷移技術決策、跨平台開發指導
    Contributions: 框架選擇建議、技術風險評估

  - qa-tester
    Role: 遷移驗證測試、跨系統比對
    Deliverables: 遷移驗證計畫、資料一致性報告

  - devops-engineer
    Role: 遷移期 CI/CD、並行部署、切換策略
    Contributions: 部署腳本、藍綠部署、回滾方案

  - integration-specialist
    Role: 新舊系統整合、API Gateway 設定
    Contributions: 路由規則、並行運行架構

Optional Agents:
  - pm-po-agent (Stage 1)
    Role: 遷移範圍確認、業務優先級決策

  - performance-engineer (Stage 7)
    Role: 遷移後效能基準測試

  - security-engineer (Stage 7)
    Role: 新系統安全審查

  - qa-automation (Stage 7)
    Role: 自動化回歸測試

  - sd-mobile-architect (Stage 6)
    Role: 行動端架構設計（新平台）
```

### 協作模式
**主要模式**: Lead-Support + Sequential-Handoff
```
sd + sa (Lead) 現況分析與架構設計
    ↓
code-analyzer (舊系統分析)
    ↓
🔴 人類確認遷移策略
    ↓
分層遷移執行:
  DB層 → 後端層 → 前端層 → 新平台
    ↓ (每層完成後)
qa + devops 驗證與部署
    ↓
🔴 人類確認切換
```

### 工作流程
1. sd + sa 主導現況分析與需求提取
2. code-analyzer 掃描舊系統、量化技術債
3. sd 設計遷移架構與技術映射
4. 分層執行: DB 遷移 → 後端遷移 → 前端遷移 → 新平台
5. qa + devops 驗證與部署
6. 知識沉澱與文檔更新

---

## 📊 Agent 使用頻率統計

### High Frequency (7+ scenarios)
```yaml
- sa-analyst: 10/10 scenarios (所有情境)
  Primary: 4 | Supporting: 6

- sd-architect: 9/10 scenarios
  Primary: 3 | Supporting: 6

- qa-tester: 8/10 scenarios
  Primary: 1 | Supporting: 7
```

### Medium Frequency (3-6 scenarios)
```yaml
- dev-developer: 6/10 scenarios
  Primary: 1 | Supporting: 5

- dev-senior: 5/10 scenarios (Phase 2: +1, Migration: +1)
  Primary: 1 | Supporting: 4

- pm-po-agent: 6/10 scenarios
  Primary: 1 | Supporting: 5

- ba-business-analyst: 3/10 scenarios
  Primary: 0 | Supporting: 3

- qa-automation: 4/10 scenarios (Phase 2: +1, Migration: +1)
  Primary: 0 | Supporting: 4

- qa-lead: 3/10 scenarios
  Primary: 1 | Supporting: 2

- code-analyzer: 3/10 (Brownfield + Refactoring + Migration)
```

### Low Frequency (1-2 scenarios - Specialized) / Optional Agent (多情境)
```yaml
- performance-engineer: 2/10 (Performance + Migration optional)
- integration-specialist: 2/10 (Integration + Migration)
- devops-engineer: 2/10 (DevOps + Migration)
- security-engineer: 4/10 (Security主導 + Greenfield/Brownfield/Performance 選配)
  # 更新說明: v0.09 新增 Greenfield/Brownfield/Performance Optional Agent 配置
- compliance-officer: 3/10 (Security主導 + Greenfield/Brownfield 選配)
  # 更新說明: v0.09 新增 Greenfield/Brownfield Optional Agent 配置
- technical-writer: 2/10 (Documentation + support)
- sd-mobile-architect: 3/10 (Greenfield/Brownfield/Migration optional — 涉及行動端時)
  # 更新說明: v0.09 新增 Greenfield/Brownfield Optional Agent 配置，對齊 SCENARIO_AGENT_MAPPING
```

---

## ⭐ Phase 2 優化記錄

### 優化項目
1. **Performance 情境增強**
   - 新增: qa-automation (Supporting Agent)
   - 原因: 效能測試自動化需要專業支援
   - 影響: 提升效能測試覆蓋度和持續監控能力
   - 決策: ✅ 使用者確認實施

2. **Documentation 情境增強**
   - 新增: dev-senior (Supporting Agent)
   - 原因: 複雜技術文檔需要資深開發者審查
   - 影響: 提升技術文檔深度和準確性
   - 決策: ✅ 使用者確認實施

3. **Documentation 情境安全合規增強** ⭐ v0.09 新增
   - 新增: security-engineer, compliance-officer, sd-mobile-architect (Optional Agents)
   - 新增: Stage 6 安全與合規文檔（選用階段）
   - 新增: api-specification, interaction-analysis Workflows
   - 原因: 經銷存管理等安全敏感專案需要安全合規文檔支援
   - 影響: 文件維護情境可完整覆蓋安全合規需求
   - 決策: ✅ 模擬測試後實施

4. **Brownfield 情境安全與行動端增強** ⭐ v0.09 新增
   - 新增: security-engineer, compliance-officer, sd-mobile-architect, qa-mobile-tester, integration-specialist (Optional Agents)
   - 原因: 舊專案維護涉及安全漏洞修復、合規驅動變更、跨平台擴展時需要專業 Agent
   - 影響: Brownfield 可完整覆蓋安全合規與多平台擴展需求
   - 決策: ✅ 模擬測試後實施

5. **Greenfield 情境安全與行動端增強** ⭐ v0.09 新增
   - 新增: security-engineer, compliance-officer, sd-mobile-architect, qa-mobile-tester, integration-specialist (Optional Agents)
   - 原因: 新專案涉及安全敏感資料、多平台部署、硬體整合時需要專業 Agent
   - 影響: Greenfield 可完整覆蓋安全合規與多平台開發需求
   - 決策: ✅ 模擬測試後實施

### 優化後配置評分
```
Performance: 8/10 → 10/10 ⬆
Documentation: 7/10 → 10/10 ⬆
Greenfield: 9/10 → 10/10 ⬆
Brownfield: 10/10 → 10/10 (Optional Agents 補齊)
Performance: 10/10 → 10/10 (Optional Agents + Workflow 補齊)
```

---

## 💡 最佳實踐

### Agent 選擇原則
1. **Primary Agent**: 主導決策和產出的角色
   - 數量: 通常 1-2 個
   - 職責: 最終負責產出品質

2. **Supporting Agents**: 提供專業支援
   - 數量: 建議 ≤ 5 個
   - 職責: 提供專業意見，協助驗證

### 協作效率提升
1. **明確角色分工**: 使用 RACI 矩陣
2. **標準化產出**: 定義交接標準
3. **適時並行**: 識別可並行任務
4. **快速迭代**: 使用 Iterative-Refinement

---

## 📚 相關文檔

- `agent/AGENT_COLLABORATION_PATTERNS.md`: 協作模式詳細說明
- `agent/core/*.yaml`: 各 Agent 的詳細配置
- `scenarios/*/SOP.md`: 各情境的詳細流程
- `agent/AGENT_PHASE2_UPDATE_GUIDE.md`: Agent Phase 2 更新指南

---

**最後更新**: 2026-04-01
**Phase 2 優化**: ✅ 完成
**Migration 情境**: ✅ 新增 (2026-02-12)
**Agent 頻率統計修正**: ✅ 完成 (2026-04-01) — security-engineer/compliance-officer/sd-mobile-architect 頻率更新，對齊 v0.09 Optional Agent 實際配置
**維護者**: AISDLC Framework Team
