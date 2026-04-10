# AISDLC CI/CD 防禦性架構缺口改善計畫
# Defensive CI/CD Gap Improvement Plan

**版本**: v1.0 | **建立日期**: 2026-03-22
**計畫類型**: CI/CD 架構增強 | **優先級**: MEDIUM
**前置計畫**: [CICD_STRATEGIC_RESTRUCTURE_PLAN.md](../archive/CICD_STRATEGIC_RESTRUCTURE_PLAN.md)（已完成並歸檔）

---

## 1. 評估背景

### 1.1 評估對象

針對「防禦性 API CI/CD 基礎架構藍圖 (Defensive API CI/CD Blueprint)」提案，以 **三個思維** 和 **三大心法** 為分析框架，與 AISDLC 現有 CI/CD 架構進行交叉比對。

### 1.2 評估方法：COT 思維鏈分析

使用 **三個思維 × 三大心法** 的 3×3 矩陣進行系統性缺口分析。

---

## 2. COT 分析：三個思維

### 2.1 防護網思維 (Defense-in-Depth)

**核心問題**：是否每個風險都有多層防護？

```
分析鏈：
  提案四道防線: Pre-commit → CI Testing → Build & Scan → Deployment
  AISDLC 四層:  Layer 0    → Layer 1    → Layer 2     → Layer 3

  映射結果：
  ✅ Pre-commit    ↔ Layer 0 (Secret Detection + SCA + License)
  ✅ CI Testing    ↔ Layer 1 (Lint + Build + Unit Test + Coverage)
  ✅ Build & Scan  ↔ Layer 2 (Integration + Contract + Mutation)
                     + Security_Scan_Integration (SAST/Container/DAST)
  ✅ Deployment    ↔ Layer 3 (Canary + Smoke Test + Rollback Gate)
```

**結論**：✅ **完整覆蓋**。AISDLC 四層架構與提案四道防線完全對應，且 AISDLC 更細緻（含情境矩陣、分級阻塞策略、超時熔斷）。

### 2.2 靜態分析思維 (Static Analysis)

**核心問題**：是否在程式碼執行前就攔截問題？

```
分析鏈：
  攔截層級        提案                    AISDLC
  ────────────────────────────────────────────────
  語法/格式       ESLint/flake8           ✅ pre-commit (Ruff/ESLint)
  安全機密        gitleaks/trufflehog     ✅ Layer 0 (TruffleHog/Gitleaks)
  程式碼漏洞      (未明確提及)             ✅ SAST (Semgrep/CodeQL)
  依賴漏洞        (未明確提及)             ✅ SCA (npm audit/pip-audit)
  授權合規        (未明確提及)             ✅ License Compliance
```

**結論**：✅ **AISDLC 更完整**。提案僅涵蓋靜態分析的前兩項，AISDLC 已擴展至 SAST、SCA、授權合規共五項。

### 2.3 自動化測試思維 (Automated Testing)

**核心問題**：是否所有測試都能無人值守執行？

```
分析鏈：
  測試類型          提案               AISDLC              差距
  ─────────────────────────────────────────────────────────────
  Unit Test         ✅ ≥80%           ✅ Coverage Gate      無
  Integration Test  ✅ Testcontainers ⚠️ 有但無隔離模式    ← 缺口 A
  Contract Test     ✅ API 契約       ✅ Pact/Schema        無
  Mutation Test     ❌                ✅ refactoring 情境   AISDLC 更完整
  E2E Test          ❌                ✅ Layer 3            AISDLC 更完整
  Smoke Test        ✅ Health Check   ✅ Layer 3            無
  Load Test         ❌                ✅ Performance Gate   AISDLC 更完整
```

**結論**：⚠️ **發現 1 個缺口**。Integration Test 的**容器化隔離模式 (Testcontainers)** 未被明確規範。

---

## 3. COT 分析：三大心法

### 3.1 靜態攔截 (Pre-commit)

**核心問題**：低級錯誤是否在 commit 前就被攔截？

```
分析鏈：
  提案「五秒護城河」:
    git commit → Husky/pre-commit 攔截 → Lint + Format + Secret Scan → 通過才允許 commit

  AISDLC 現狀：
    git commit → pre-commit-config-template.yaml 攔截 →
      TruffleHog/Gitleaks + Private Key Detection + YAML/JSON Validation +
      Large File Block + Merge Conflict Check + 語言專屬 Lint (Ruff/ESLint)

  比對結果：
    ✅ AISDLC pre-commit 配置已涵蓋提案所有本地攔截項目
    ✅ 且額外包含：大檔案防護、合併衝突偵測、多格式驗證
```

**結論**：✅ **完整覆蓋且更全面**。

### 3.2 隔離測試 (Isolation)

**核心問題**：每次測試的起點是否都是乾淨、可預測的？

```
分析鏈：
  提案核心概念：
    「保證每次測試的起點都是空資料庫」
    → 使用 Testcontainers 即時啟動 Docker DB
    → 測試完畢立刻銷毀
    → 消除狀態殘留 (State Leak)

  AISDLC 現狀：
    Layer 2 提及 "Integration Test" ← 存在
    Migration Pipeline 提及 "DB Dry-Run" ← 存在
    但是...
    ❌ 未規範「如何確保測試環境隔離」
    ❌ 未提及 Testcontainers 或等效機制
    ❌ 未定義「測試資料庫生命週期管理」
    ❌ 未防範「測試間狀態污染」

  這是一個實踐模式級的缺口：
    「做什麼」有了（Integration Test），
    「怎麼做到隔離」沒有。
```

**結論**：⚠️ **缺口 B — 測試隔離模式未規範**。

### 3.3 確定性同步 (WaitUntil)

**核心問題**：非同步操作是否有確定性的就緒確認？

```
分析鏈：
  提案隱含概念：
    → Testcontainers 啟動後，需等待 DB 就緒才能跑測試
    → 部署完成後，需確認 /health 通過才算成功
    → Canary 部署需等待指標穩定才決定是否繼續

  AISDLC 現狀：
    ✅ Smoke Test + Health Check（Layer 3）← 部署後就緒確認
    ✅ Canary 錯誤率門檻（> 1% 回滾）← 部署穩定性確認
    ❌ 未規範「容器/服務啟動就緒等待策略」
    ❌ 未規範「資料庫遷移完成確認機制」
    ❌ 未定義「就緒探針 (Readiness Probe)」模式

  缺口範圍：
    部署階段的 WaitUntil ← 已有（Smoke Test）
    測試階段的 WaitUntil ← ❌ 缺失
    服務啟動的 WaitUntil ← ❌ 缺失
```

**結論**：⚠️ **缺口 C — 測試/服務啟動的確定性同步模式缺失**。

---

## 4. 缺口總結與改善項目

### 4.1 已確認的 3 個缺口

| 缺口 | 來源 | 嚴重度 | 影響範圍 |
|------|------|--------|---------|
| **A: 不可變產物模式** | 自動化測試思維 | MEDIUM | L2→L3 交接 |
| **B: 測試隔離模式 (Testcontainers)** | 隔離測試心法 | HIGH | 所有含 Integration Test 的情境 |
| **C: 確定性同步模式 (WaitUntil)** | 確定性同步心法 | MEDIUM | 測試/部署階段 |

### 4.2 為什麼這些缺口重要？

**缺口 A — 不可變產物**：
- 問題：沒有明確「build once, deploy many」原則，可能在不同環境重新建置，導致「CI 過了但 Production 壞了」
- 影響：環境飄移風險

**缺口 B — 測試隔離**：
- 問題：Integration Test 若共用資料庫，測試 A 的殘留資料會導致測試 B 誤判
- 影響：測試結果不可靠、間歇性失敗 (Flaky Tests)

**缺口 C — 確定性同步**：
- 問題：容器啟動後立即跑測試，可能因 DB 尚未就緒而失敗
- 影響：CI 間歇性失敗、開發者信任度下降

---

## 5. 改善實作計畫

### 改善 A：不可變產物模式 (Immutable Artifact Pattern)

**目標**：在 Layer 2→Layer 3 交接處明確定義「Build Once, Deploy Many」原則。

**修改目標檔案**：[CICD_Pipeline_Template.md](../../docs_template/scenario_specific/devops/CICD_Pipeline_Template.md)

**新增內容位置**：在 Layer 1 與 Layer 2 之間，或作為 Layer 1 的子節

**新增內容概要**：
```yaml
# 不可變產物原則 (Immutable Artifact Pattern)
immutable_artifact:
  principle: "Build Once, Deploy Many"
  rules:
    - 在 Layer 1 Build 階段產出唯一 Artifact（Docker Image / JAR / Bundle）
    - 後續 Layer 2 測試、Layer 3 部署皆使用同一 Artifact
    - 禁止在 Staging/Production 重新建置
  tagging:
    format: "{app}-{git-sha}-{timestamp}"  # 唯一標識
    registry: "Container Registry / Artifact Repository"
  verification:
    - SHA256 校驗確保部署產物與 CI 產物一致
```

**適用情境**：所有含 Docker 部署的情境（greenfield, brownfield, migration, integration, devops）

**預估工作量**：0.5 天

---

### 改善 B：測試隔離模式 (Test Isolation with Testcontainers)

**目標**：為 Layer 2 Integration Test 提供容器化隔離的標準模式。

**修改目標檔案**：[CICD_Pipeline_Template.md](../../docs_template/scenario_specific/devops/CICD_Pipeline_Template.md)

**新增內容位置**：Layer 2 Quality Assurance 章節

**新增內容概要**：
```yaml
# 測試隔離模式 (Test Isolation Pattern)
test_isolation:
  principle: "每次測試起點都是乾淨環境"

  # 推薦方案：Testcontainers
  recommended_tool: Testcontainers
  supported_languages:
    - Java/Kotlin (Testcontainers-Java)
    - Node.js (Testcontainers-Node)
    - Python (Testcontainers-Python)
    - Go (Testcontainers-Go)

  lifecycle:
    setup: "測試開始前自動啟動臨時 Docker 容器（DB/Redis/MQ）"
    execution: "測試使用臨時容器，與其他測試完全隔離"
    teardown: "測試完成後自動銷毀容器"

  anti_patterns:
    - "❌ 共用開發資料庫跑 Integration Test（狀態污染）"
    - "❌ 依賴測試執行順序（隱性耦合）"
    - "❌ 手動清理測試資料（不可靠）"

  ci_requirements:
    - "CI Runner 必須支援 Docker-in-Docker 或 Docker Socket"
    - "GitHub Actions: services 關鍵字或 Testcontainers"
    - "GitLab CI: services 關鍵字或 privileged runner"
```

**適用情境**：greenfield, brownfield, migration, integration, testing

**預估工作量**：1 天

---

### 改善 C：確定性同步模式 (Deterministic Sync / WaitUntil Pattern)

**目標**：規範非同步操作的就緒確認機制，消除 CI 間歇性失敗。

**修改目標檔案**：[CICD_Pipeline_Template.md](../../docs_template/scenario_specific/devops/CICD_Pipeline_Template.md)

**新增內容位置**：通用最佳實踐章節（或新增子章節）

**新增內容概要**：
```yaml
# 確定性同步模式 (WaitUntil Pattern)
deterministic_sync:
  principle: "絕不假設服務已就緒，必須確認後才繼續"

  # 場景 1：容器/服務啟動就緒
  service_readiness:
    pattern: "WaitUntil + Health Check"
    implementation:
      - "使用 wait-for-it.sh / dockerize 等待依賴服務就緒"
      - "Testcontainers 內建 WaitStrategy（推薦）"
      - "Kubernetes: Readiness Probe + Init Container"
    timeout: "30 秒硬超時，超時視為啟動失敗"
    anti_pattern: "❌ sleep 10 && run_tests（時間猜測，不可靠）"

  # 場景 2：資料庫遷移完成確認
  migration_readiness:
    pattern: "Migration 腳本回傳 exit code + 版本驗證"
    implementation:
      - "執行 migration → 驗證 schema_version 表 → 確認版本正確"
      - "Migration 失敗立即中止，不繼續執行測試"

  # 場景 3：部署後穩定性確認
  deployment_stability:
    pattern: "已有 — Smoke Test + Health Check (Layer 3)"
    note: "此場景 AISDLC 已完整覆蓋，無需額外改善"
```

**適用情境**：所有含 Integration Test 或容器部署的情境

**預估工作量**：0.5 天

---

## 6. 實作優先級排序

| 優先級 | 改善項目 | 影響範圍 | 工作量 | 狀態 |
|-------|---------|---------|--------|------|
| **P1** | B: 測試隔離模式 (Testcontainers) | 5+ 情境 | 1 天 | ✅ 已完成 (2026-03-23) |
| **P2** | C: 確定性同步模式 (WaitUntil) | 5+ 情境 | 0.5 天 | ✅ 已完成 (2026-03-23) — P1 併入 + 獨立補強 |
| **P2** | A: 不可變產物模式 (Immutable Artifact) | 5+ 情境 | 0.5 天 | ✅ 已完成 (2026-03-23) |

**總預估工作量**：2 天

---

## 7. 修改檔案清單

| 檔案 | 修改類型 | 說明 |
|------|---------|------|
| `docs_template/scenario_specific/devops/CICD_Pipeline_Template.md` | 編輯 | 新增 3 個模式章節 |
| `build/planning/active/Defensive_CICD_Gap_Improvement_Plan.md` | 新建 | 本計畫文件 |

**注意**：此改善不涉及目錄結構變更，不需更新 FILE_DIRECTORY_RULES.md。

---

## 8. 驗收標準

- [x] CICD_Pipeline_Template.md 包含「不可變產物模式」章節（含 6 規則、標籤策略、SHA256 驗證、Multi-stage Dockerfile、GitHub Actions & GitLab CI 整合、最佳實踐 5 原則）
- [x] CICD_Pipeline_Template.md 包含「測試隔離模式」章節（含 Testcontainers 指引）
- [x] CICD_Pipeline_Template.md 包含「確定性同步模式」章節（含 WaitUntil 指引）— Layer 2 內含 4 工具完整範例 + 通用最佳實踐獨立 5 場景章節
- [x] 三大心法在現有架構中均有對應實作指引 — 靜態攔截(L0)、隔離測試(L2 Test Isolation)、確定性同步(WaitUntil)
- [x] 本計畫完成後歸檔至 `build/planning/archive/`
