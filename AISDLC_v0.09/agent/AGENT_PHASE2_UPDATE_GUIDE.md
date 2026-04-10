# Agent Phase 2 批次更新指南

**建立日期**: 2025-10-23
**用途**: Phase 2 Agent配置更新的完整規格
**狀態**: ✅ sa-analyst 已更新，其餘16個待更新

---

## 更新狀態追蹤

### 核心 Agents (7個)
- [x] 04.sa-analyst ✅ 已更新
- [ ] 02.ba-business-analyst
- [ ] 03.pm-po-agent
- [ ] 05.sd-architect
- [ ] 06.dev-developer
- [ ] 07.qa-tester
- [ ] 01.agent-template (參考用)

### 專業 Agents (10個 - 高優先級)
- [ ] code-analyzer (Brownfield/Refactoring必需)
- [ ] performance-engineer (Performance必需)
- [ ] integration-specialist (Integration必需)
- [ ] devops-engineer (DevOps必需)
- [ ] security-engineer (Security必需)
- [ ] qa-lead
- [ ] qa-automation
- [ ] technical-writer
- [ ] dev-senior
- [ ] compliance-officer

---

## 各Agent更新規格

### 02. ba-business-analyst

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Peer-Review"
      role: "Peer"
      description: "與SA進行FRD/PRD交叉審查，從業務角度驗證"
      applicable_scenarios: ["Greenfield", "Brownfield", "All with BA"]
    - pattern: "Lead-Support"
      role: "Support"
      description: "提供業務分析專業建議"
      applicable_scenarios: ["Greenfield", "Integration"]

scenario_usage:
  frequency: "Medium (3/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐⭐ Must Keep"
  primary_scenarios:
    - scenario: "Greenfield"
      role: "Business Validator"
      responsibilities: "業務需求驗證、利害關係人溝通"
    - scenario: "Brownfield"
      role: "Business Analyst"
      responsibilities: "業務流程分析、改進建議"

  supporting_scenarios:
    - scenario: "Integration"
      role: "Business Process Advisor"
      contributions: "業務流程整合建議"
```

### 03. pm-po-agent

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Lead-Support"
      role: "Lead"
      description: "主導產品決策和優先級排序"
      applicable_scenarios: ["Greenfield"]
    - pattern: "Sequential-Handoff"
      role: "Provider"
      description: "產出PRD交接給SA"
      applicable_scenarios: ["Greenfield", "All with PRD"]

scenario_usage:
  frequency: "High (6/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐⭐ Must Keep"
  primary_scenarios:
    - scenario: "Greenfield"
      role: "Product Owner"
      responsibilities: "產品願景、PRD產出、優先級決策"
    - scenario: "Brownfield"
      role: "Product Manager"
      responsibilities: "功能優先級、改進方向"

  supporting_scenarios:
    - scenario: "Performance"
      role: "Priority Advisor"
      contributions: "效能優化優先級"
    - scenario: "Refactoring"
      role: "Business Value Advisor"
      contributions: "重構業務價值評估"
    - scenario: "Integration"
      role: "Integration Planning"
      contributions: "整合優先級和範圍"
    - scenario: "Testing"
      role: "Test Priority"
      contributions: "測試範圍優先級"
```

### 05. sd-architect

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Lead-Support"
      role: "Lead"
      description: "主導技術架構設計"
      applicable_scenarios: ["Greenfield", "Performance", "Integration"]
    - pattern: "Sequential-Handoff"
      role: "Receiver & Provider"
      description: "接收FRD產出SRD，交接給Dev"
      applicable_scenarios: ["All scenarios"]
    - pattern: "Peer-Review"
      role: "Primary & Peer"
      description: "技術設計審查"
      applicable_scenarios: ["All technical scenarios"]

scenario_usage:
  frequency: "High (8/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐⭐ Must Keep"
  primary_scenarios:
    - scenario: "Greenfield"
      role: "Lead Architect"
      responsibilities: "架構設計、技術選型、SRD產出"
    - scenario: "Performance"
      role: "Performance Architect"
      responsibilities: "架構層面效能優化設計"
    - scenario: "Integration"
      role: "Integration Architect"
      responsibilities: "整合架構設計、API設計"
    - scenario: "Refactoring"
      role: "Refactoring Architect"
      responsibilities: "重構架構設計"

  supporting_scenarios:
    - scenario: "DevOps"
      role: "Infrastructure Advisor"
      contributions: "基礎設施架構建議"
    - scenario: "Security"
      role: "Security Architecture"
      contributions: "安全架構審查"
    - scenario: "Testing"
      role: "Test Architecture"
      contributions: "測試架構設計"
    - scenario: "Documentation"
      role: "Technical Review"
      contributions: "技術文檔審查"
```

### 06. dev-developer

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Sequential-Handoff"
      role: "Receiver"
      description: "接收SRD進行開發評估"
      applicable_scenarios: ["All scenarios"]
    - pattern: "Peer-Review"
      role: "Peer"
      description: "技術實作可行性審查"
      applicable_scenarios: ["All scenarios"]

scenario_usage:
  frequency: "High (6/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐⭐ Must Keep"
  primary_scenarios:
    - scenario: "Greenfield"
      role: "Developer"
      responsibilities: "開發評估、實作建議"
    - scenario: "Brownfield"
      role: "Code Analyst"
      responsibilities: "既有代碼分析、改進建議"

  supporting_scenarios:
    - scenario: "Performance"
      role: "Performance Developer"
      contributions: "代碼層面效能優化"
    - scenario: "Integration"
      role: "Integration Developer"
      contributions: "API整合實作評估"
    - scenario: "Testing"
      role: "Testability Advisor"
      contributions: "可測試性建議"
    - scenario: "Refactoring"
      role: "Refactoring Developer"
      contributions: "重構實作評估"
```

### 07. qa-tester

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Sequential-Handoff"
      role: "Receiver"
      description: "接收SRD/FRD產出測試計畫"
      applicable_scenarios: ["All scenarios"]
    - pattern: "Peer-Review"
      role: "Peer"
      description: "測試性審查"
      applicable_scenarios: ["All scenarios"]

scenario_usage:
  frequency: "High (7/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐⭐ Must Keep"
  primary_scenarios:
    - scenario: "Greenfield"
      role: "QA Engineer"
      responsibilities: "測試計畫、AC驗證、測試案例設計"
    - scenario: "Testing"
      role: "Test Lead"
      responsibilities: "測試策略、測試執行"

  supporting_scenarios:
    - scenario: "Performance"
      role: "Performance Tester"
      contributions: "效能測試計畫"
    - scenario: "Integration"
      role: "Integration Tester"
      contributions: "整合測試計畫"
    - scenario: "Security"
      role: "Security Tester"
      contributions: "安全測試計畫"
    - scenario: "Brownfield"
      role: "Regression Tester"
      contributions: "回歸測試策略"
    - scenario: "Refactoring"
      role: "Refactoring Validator"
      contributions: "重構驗證測試"
```

---

## 專業 Agents 更新規格

### code-analyzer

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Lead-Support"
      role: "Support"
      description: "提供代碼分析專業支援"
      applicable_scenarios: ["Brownfield", "Refactoring"]
    - pattern: "Iterative-Refinement"
      role: "Analyzer"
      description: "迭代進行代碼品質改進"
      applicable_scenarios: ["Brownfield", "Refactoring"]

scenario_usage:
  frequency: "Medium (2/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐ Keep (Brownfield/Refactoring必需)"
  primary_scenarios:
    - scenario: "Brownfield"
      role: "Code Analyzer"
      responsibilities: "代碼結構分析、技術債務識別、品質評估"
    - scenario: "Refactoring"
      role: "Refactoring Advisor"
      responsibilities: "重構範圍識別、複雜度分析、重構建議"
```

### performance-engineer

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Lead-Support"
      role: "Lead"
      description: "主導效能優化分析和策略"
      applicable_scenarios: ["Performance"]
    - pattern: "Iterative-Refinement"
      role: "Optimizer"
      description: "迭代效能優化"
      applicable_scenarios: ["Performance"]

scenario_usage:
  frequency: "Low (1/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐ Keep (Performance必需)"
  primary_scenarios:
    - scenario: "Performance"
      role: "Performance Lead"
      responsibilities: "效能剖析、瓶頸識別、優化策略、測量驗證"
```

### integration-specialist

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Lead-Support"
      role: "Lead"
      description: "主導系統整合分析和設計"
      applicable_scenarios: ["Integration"]
    - pattern: "Sequential-Handoff"
      role: "Provider"
      description: "產出整合規格交接給Dev"
      applicable_scenarios: ["Integration"]

scenario_usage:
  frequency: "Low (1/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐ Keep (Integration必需)"
  primary_scenarios:
    - scenario: "Integration"
      role: "Integration Lead"
      responsibilities: "API研究、認證設計、資料對應、錯誤處理策略"
```

### devops-engineer

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Lead-Support"
      role: "Lead"
      description: "主導DevOps流程設計"
      applicable_scenarios: ["DevOps"]
    - pattern: "Parallel-Convergence"
      role: "Parallel Worker"
      description: "並行進行CI/CD和基礎設施配置"
      applicable_scenarios: ["DevOps"]

scenario_usage:
  frequency: "Low (1/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐ Keep (DevOps必需)"
  primary_scenarios:
    - scenario: "DevOps"
      role: "DevOps Lead"
      responsibilities: "CI/CD設計、容器化、監控設定、自動化部署"
```

### security-engineer

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Peer-Review"
      role: "Primary"
      description: "主導安全審查"
      applicable_scenarios: ["Security", "All scenarios"]
    - pattern: "Lead-Support"
      role: "Lead"
      description: "主導安全設計"
      applicable_scenarios: ["Security"]

scenario_usage:
  frequency: "Medium (1/9 primary + cross-cutting)"
  irreplaceability: "⭐⭐⭐⭐⭐ Must Keep (Security必需)"
  primary_scenarios:
    - scenario: "Security"
      role: "Security Lead"
      responsibilities: "安全架構、威脅建模、安全測試、合規審查"

  supporting_scenarios:
    - scenario: "Greenfield"
      role: "Security Advisor"
      contributions: "安全設計建議"
    - scenario: "Integration"
      role: "Integration Security"
      contributions: "API安全審查"
```

### qa-lead

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Parallel-Convergence"
      role: "Coordinator"
      description: "整合多平台測試結果"
      applicable_scenarios: ["Testing"]
    - pattern: "Lead-Support"
      role: "Lead"
      description: "主導測試策略制定"
      applicable_scenarios: ["Testing"]

scenario_usage:
  frequency: "Medium (3/9 scenarios)"
  irreplaceability: "⭐⭐⭐ Keep (與qa-tester互補)"
  primary_scenarios:
    - scenario: "Testing"
      role: "Test Strategy Lead"
      responsibilities: "測試策略、團隊協調、品質管理"

  supporting_scenarios:
    - scenario: "Greenfield"
      role: "QA Strategy"
      contributions: "測試策略建議"
    - scenario: "Security"
      role: "Security Test Lead"
      contributions: "安全測試策略"
```

### qa-automation

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Lead-Support"
      role: "Support"
      description: "提供測試自動化專業支援"
      applicable_scenarios: ["Testing", "DevOps", "Performance"]
    - pattern: "Parallel-Convergence"
      role: "Parallel Worker"
      description: "並行進行自動化測試開發"
      applicable_scenarios: ["Testing"]

scenario_usage:
  frequency: "Medium (3/9 scenarios)"
  irreplaceability: "⭐⭐⭐ Keep (與qa-tester互補)"
  primary_scenarios:
    - scenario: "Testing"
      role: "Automation Engineer"
      responsibilities: "自動化框架選擇、自動化測試開發"

  supporting_scenarios:
    - scenario: "DevOps"
      role: "CI/CD Testing"
      contributions: "自動化測試整合"
    - scenario: "Performance"
      role: "Performance Test Automation"
      contributions: "效能測試自動化"
```

### technical-writer

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Sequential-Handoff"
      role: "Receiver"
      description: "接收技術產出轉化為文檔"
      applicable_scenarios: ["Documentation"]
    - pattern: "Peer-Review"
      role: "Peer"
      description: "文檔品質審查"
      applicable_scenarios: ["Documentation"]

scenario_usage:
  frequency: "Medium (2/9 scenarios)"
  irreplaceability: "⭐⭐⭐ Keep (Documentation必需)"
  primary_scenarios:
    - scenario: "Documentation"
      role: "Documentation Lead"
      responsibilities: "技術文檔撰寫、知識庫建立、API文檔"

  supporting_scenarios:
    - scenario: "Greenfield"
      role: "Documentation Support"
      contributions: "文檔結構建議"
```

### dev-senior

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Peer-Review"
      role: "Peer"
      description: "資深技術審查"
      applicable_scenarios: ["Brownfield", "Refactoring", "Complex scenarios"]
    - pattern: "Lead-Support"
      role: "Support"
      description: "提供資深開發建議"
      applicable_scenarios: ["Brownfield", "Performance"]

scenario_usage:
  frequency: "Medium (4/9 scenarios)"
  irreplaceability: "⭐⭐⭐ Keep (Brownfield複雜決策必需)"
  primary_scenarios:
    - scenario: "Brownfield"
      role: "Senior Advisor"
      responsibilities: "複雜技術決策、架構評估"
    - scenario: "Refactoring"
      role: "Refactoring Lead"
      responsibilities: "重構策略、技術風險評估"

  supporting_scenarios:
    - scenario: "Performance"
      role: "Performance Code Reviewer"
      contributions: "代碼層面效能建議"
    - scenario: "Documentation"
      role: "Technical Reviewer"
      contributions: "複雜技術文檔審查"
```

### compliance-officer

```yaml
collaboration_patterns:
  primary_patterns:
    - pattern: "Peer-Review"
      role: "Peer"
      description: "合規審查"
      applicable_scenarios: ["Security"]
    - pattern: "Lead-Support"
      role: "Support"
      description: "提供合規專業建議"
      applicable_scenarios: ["Security"]

scenario_usage:
  frequency: "Low (1/9 scenarios)"
  irreplaceability: "⭐⭐⭐⭐ Keep (Security必需)"
  primary_scenarios:
    - scenario: "Security"
      role: "Compliance Reviewer"
      responsibilities: "法規遵循審查、審計準備、合規文檔"
```

---

## 批次更新指令

### 方式1：手動逐一更新
```bash
# 對每個Agent檔案，在agent:區塊後加入對應的Phase 2配置
# 參考sa-analyst已完成的更新
```

### 方式2：使用腳本批次更新（建議）
```bash
# 創建更新腳本後執行
cd AISDLC_v0.03/agent
# 執行批次更新腳本（待實作）
```

---

## 驗證清單

更新完成後驗證：
- [ ] 所有Agent都有collaboration_patterns欄位
- [ ] 所有Agent都有scenario_usage欄位
- [ ] frequency正確（High/Medium/Low + 數量）
- [ ] irreplaceability星級評分正確
- [ ] primary_scenarios至少有1個
- [ ] YAML語法正確（無縮排錯誤）

---

**最後更新**: 2025-10-23
**狀態**: sa-analyst已更新，其餘待批次更新或逐一更新
