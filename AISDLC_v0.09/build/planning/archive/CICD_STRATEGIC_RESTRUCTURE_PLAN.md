# AISDLC v0.09 CI/CD 戰略重構計畫

**版本**: v1.1 | **建立日期**: 2026-03-22 | **最後更新**: 2026-03-22
**計畫類型**: 戰略架構改善 | **優先級**: HIGH

---

## 1. 戰略分析思考鏈

### 1.1 解構與依賴分析：摩擦成本與斷鏈風險

**核心發現**：目前 11 個情境 Workflow 中，CI/CD 僅作為獨立的 `devops` 情境存在，而非貫穿所有情境的基礎設施層。這導致以下摩擦成本：

```
斷鏈熱區圖：

  sa-analyst ──(手動交接)──→ dev-senior ──(手動交接)──→ qa-tester
       ↑                          ↑                        ↑
    [無自動化驗證]            [無自動化建置]           [無自動化測試觸發]
    文件一致性靠人工           編譯結果靠本地           測試覆蓋率靠自覺
```

| 斷鏈位置 | 摩擦成本 | 風險等級 |
|---------|---------|---------|
| `sa→dev` 需求交接 | 規格與實作不一致，發現延遲 | HIGH |
| `dev→qa` 測試交接 | 手動觸發測試、環境不一致 | HIGH |
| `dev→devops` 部署交接 | 部署腳本未版控、環境飄移 | MEDIUM |
| `security` 跨情境滲透 | 安全掃描僅在 security 情境觸發，其他情境裸奔 | CRITICAL |
| `migration` 技術棧切換 | 無 Canary/Blue-Green 保護，回滾靠祈禱 | CRITICAL |

### 1.2 關鍵洞察

> **CI/CD 不應是一個「情境」，而應是所有情境的「神經系統」。**

當前架構將 `devops` 視為與 `greenfield`、`testing` 平行的選項，但實務上每個情境都需要自動化建置、測試和部署能力。這是架構性缺陷，非功能性缺陷。

---

## 2. AISDLC v0.09 CI/CD 強化矩陣

### 2.1 CI/CD 介入節點映射

| 情境 | CI/CD Touchpoints | 自動化觸發器 | Pipeline 類型 |
|------|-------------------|-------------|--------------|
| `greenfield` | **Scaffold → Build → Test → Deploy-Dev** | repo init, first commit | `bootstrap-pipeline` |
| `brownfield` | **Lint → Build → Regression → Deploy-Staging** | PR opened, branch push | `guard-pipeline` |
| `refactoring` | **Static Analysis → Build → Mutation Test → Diff Coverage** | PR opened (refactor/* branch) | `safety-net-pipeline` |
| `migration` | **Dual-Build → Contract Test → Canary Deploy → Rollback Gate** | migration/* branch push | `migration-pipeline` |
| `performance` | **Build → Benchmark → Load Test → SLA Gate** | perf/* branch, schedule (nightly) | `perf-pipeline` |
| `integration` | **Mock Server → Contract Test → E2E → API Health** | integration/* branch, webhook | `integration-pipeline` |
| `devops` | **IaC Validate → Plan → Apply → Smoke Test** | infra/* branch push | `infra-pipeline` |
| `testing` | **Build → Unit → Integration → E2E → Report** | test/* branch, PR merge | `quality-gate-pipeline` |
| `documentation` | **Doc Lint → Link Check → Build → Deploy-Docs** | docs/* branch, doc file change | `docs-pipeline` |
| `security` | **SAST → SCA → DAST → Secret Scan → Compliance Gate** | 所有 PR (mandatory), schedule | `devsecops-pipeline` |

### 2.2 Pipeline 層級架構

```
Layer 0: Security Baseline (所有情境強制)
├── Secret Detection (pre-commit hook)
├── Dependency Scan (SCA)
└── License Compliance

Layer 1: Build & Verify (所有情境強制)
├── Lint + Format Check
├── Compile / Build
└── Unit Test + Coverage Gate

Layer 2: Quality Assurance (情境選配)
├── Integration Test
├── Contract Test (integration/migration)
├── Mutation Test (refactoring)
└── Performance Benchmark (performance)

Layer 3: Deploy & Validate (情境選配)
├── Deploy to Staging
├── Smoke Test
├── Canary / Blue-Green (migration)
└── Rollback Gate
```

---

## 3. 事件驅動協作藍圖

### 3.1 核心事件與 Agent 觸發鏈

```yaml
# 事件驅動 Agent 協作模型
events:
  pr_opened:
    triggers:
      - agent: code-analyzer    # SAST + Code Quality
        action: static-analysis
        blocking: true
      - agent: security-engineer # Secret + Dependency Scan
        action: security-scan
        blocking: true
      - agent: qa-automation     # Unit + Integration Test
        action: run-test-suite
        blocking: true
    on_all_pass:
      - agent: devops-engineer
        action: deploy-preview
        blocking: false

  pr_approved:
    triggers:
      - agent: devops-engineer
        action: deploy-staging
      - agent: qa-tester
        action: smoke-test-staging
    on_all_pass:
      - notify: pm-po  # 部署預覽可供驗收

  release_tagged:
    triggers:
      - agent: devops-engineer
        action: deploy-production
        strategy: canary  # 預設 Canary 部署
      - agent: performance-engineer
        action: post-deploy-benchmark
      - agent: security-engineer
        action: dast-scan-production
```

### 3.2 情境專屬觸發規則

**Migration 情境（最高風險）**：
```
migration/* branch push
  → dual-build (舊棧 + 新棧同時建置)
  → contract-test (驗證 API 相容性)
  → canary-deploy (5% → 25% → 50% → 100%)
  → 每階段自動 rollback gate (錯誤率 > 1% 即回滾)
  → performance-engineer 比對新舊棧 latency
```

**Refactoring 情境**：
```
refactor/* branch push
  → build + unit-test
  → mutation-test (驗證測試品質)
  → diff-coverage-check (變更行必須 ≥ 80% 覆蓋)
  → code-analyzer 產出重構影響報告
```

### 3.3 非同步協作時序圖

```
Developer     CI Pipeline      code-analyzer    qa-automation    devops-engineer
    |              |                 |                |                |
    |--PR Open---->|                 |                |                |
    |              |--Static Scan--->|                |                |
    |              |--Run Tests------|--------------->|                |
    |              |                 |                |                |
    |              |<--Report--------|                |                |
    |              |<--Results-------|----------------|                |
    |              |                 |                |                |
    |              |---[All Pass]----|----------------|--Deploy------->|
    |              |                 |                |                |
    |<--Feedback---|                 |                |                |
```

---

## 4. 避坑指南：3 大高風險區與緩解策略

### 風險 1：Pipeline 死結 (Deadlock)

**場景**：`security-engineer` 掃描阻塞 PR → `dev-developer` 無法合併 → `qa-tester` 無環境測試 → 全線停擺。

**緩解策略**：
- **超時熔斷機制**：安全掃描設定 10 分鐘硬超時，超時視為 Warning 而非 Block
- **分級阻塞**：Critical/High 漏洞阻塞合併，Medium/Low 僅警告
- **旁路通道**：`hotfix/*` 分支可跳過非關鍵掃描（需 security-engineer 事後審核）

```yaml
security_scan:
  timeout: 600s
  blocking_severity: [critical, high]
  warning_severity: [medium, low]
  bypass_branches: ["hotfix/*"]
  bypass_requires: post_merge_audit
```

### 風險 2：Performance 情境 Pipeline 瓶頸

**場景**：Load Test 耗時 30+ 分鐘，成為所有 PR 的瓶頸。

**緩解策略**：
- **分層執行**：PR 階段僅跑 Micro-Benchmark（< 2 分鐘），Nightly 跑 Full Load Test
- **快取基線**：效能基線結果快取，僅比對差異
- **專屬 Runner**：Performance 測試使用獨立的 CI Runner，不佔用通用資源

```yaml
performance_test:
  pr_level: micro-benchmark     # < 2min
  nightly_level: full-load-test # 30-60min
  baseline_cache: true
  dedicated_runner: perf-runner-pool
```

### 風險 3：Migration 情境回滾失敗

**場景**：Canary 部署後發現問題，但 DB Schema 已變更，無法直接回滾。

**緩解策略**：
- **Forward-Only Migration**：DB Migration 必須支援向前相容（Expand-Contract Pattern）
- **雙寫驗證**：新舊系統並行寫入，驗證資料一致性後才切流量
- **自動化回滾腳本**：每個 Migration PR 必須附帶 Rollback Script，CI 驗證回滾可執行

```yaml
migration_deploy:
  strategy: canary
  canary_steps: [5%, 25%, 50%, 100%]
  rollback_trigger:
    error_rate: "> 1%"
    latency_p99: "> 2x baseline"
  db_migration:
    pattern: expand-contract
    require_rollback_script: true
    dual_write_verification: true
```

---

## 5. 實作路徑建議（優先級排序）

| 優先級 | 項目 | 涉及情境 | 預估工作量 | 狀態 |
|-------|------|---------|-----------|------|
| P0 | Layer 0 安全基線（所有 PR 強制） | 全部 | 1-2 天 | ✅ 已完成 (2026-03-22) |
| P0 | Layer 1 Build + Unit Test Gate | greenfield, brownfield | 1 天 | ✅ 已完成 (2026-03-22) |
| P1 | Migration 專屬 Pipeline（Canary + Rollback） | migration | 3-5 天 | ✅ 已完成 (2026-03-22) |
| P1 | Security 掃描整合至所有情境 | 全部 | 2-3 天 | ✅ 已完成 (2026-03-22) |
| P2 | Performance Micro-Benchmark Gate | performance | 2 天 | ✅ 已完成 (2026-03-22) |
| P2 | Documentation Pipeline（Link Check + Deploy） | documentation | 1 天 | ✅ 已完成 (2026-03-22) |
| P3 | 事件驅動 Agent 通知系統 | 全部 | 3-5 天 | ✅ 已完成 (2026-03-22) |

---

## 6. 對 AISDLC_INIT.md 的建議修改

在情境-Workflow 映射表中新增 **CI/CD 基線欄位**，明確每個情境的最低自動化要求：

```markdown
| 情境 | ... | CI/CD 基線 (Mandatory) |
|------|-----|----------------------|
| greenfield | ... | L0 + L1 |
| brownfield | ... | L0 + L1 + Regression |
| migration  | ... | L0 + L1 + L2(Contract) + L3(Canary) |
| security   | ... | L0(Enhanced) + L1 + DAST |
```

這使 CI/CD 從「可選情境」升級為「每個情境的強制基礎設施層」。
