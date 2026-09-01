# 執行檢查清單 / Execution Checklist

> **文件類型**: 流程標準 (Process Standard)
> **版本**: v1.0
> **更新日期**: 2026-06-10
> **依據**: [SPRINT_FINAL_APPROVAL_PROCESS.md](./SPRINT_FINAL_APPROVAL_PROCESS.md)
> **相關**: Sprint 產出文件的形式（Plan / Retro 併入規則）見 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md)（Sprint 93 起適用）
> **目的**: 確保所有 Sprint 執行時遵守統一的品質標準，特別是 Final Approval 必須執行完整 mvn test

---

## 🔴 Sprint Final Approval 強制檢查點

> **重要**: 本檢查清單是從 Sprint 16 的教訓 (§1.3 重要補述) 中建立的，確保 Final Approval 不再隱藏技術債。

### Final Approval 前必須執行

| 檢查項目 | 強制命令 | 預期結果 | 如失敗的處置 |
|---------|---------|---------|-------------|
| **🔴 完整 mvn test** | `cd backend && mvn clean test 2>&1 \| grep -E "(Tests run:\|BUILD\|Failures:\|Errors:)"` | 0 Failures, 0 Errors | 如有既有技術債，明確標示於 Final Approval 文件中 |
| **🔴 Sprint 範圍測試** | `mvn test -Dtest='*Sprint*'` (如適用) | 100% 通過 | 必須修復，否則不可合併 |
| **🔴 mvn compile** | `cd backend && mvn clean compile` | BUILD SUCCESS | 必須修復才能繼續 |

---

## 1. Sprint 開發期間檢查

### 1.1 每日開發

| 檢查項目 | 頻率 | 說明 |
|---------|------|------|
| 本地編譯 | 每次 commit 前 | `mvn clean compile` |
| 單元測試 | 每次 commit 前 | `mvn test` |
| Checkstyle | 每次 commit 前 | `mvn checkstyle:check` |
| 程式碼覆蓋率 | 每個 US 完成後 | `mvn jacoco:check` |

### 1.2 US 完成檢查

| 檢查項目 | 標準 | 備註 |
|---------|------|------|
| 單元測試覆蓋率 | >= 80% (新代碼) | 使用 `mvn jacoco:report` 檢視 |
| 測試通過率 | 100% | 本 US 相關的測試 |
| 文件更新 | 必要時 | API Spec, TC, etc. |

---

## 2. Sprint 結束 Final Approval 檢查

### 2.1 測試驗證

| 檢查項目 | 標準 | 失敗時動作 |
|---------|------|-----------|
| **完整 mvn test** | 489 tests, 0 Failures, 0 Errors | 如有既有技術債失敗，標示於 Final Approval |
| Sprint 範圍測試 | 100% 通過 | 必須修復，否則不可合併 |
| 整合測試 | 100% 通過 | 必須修復 |
| E2E 測試 | 100% 通過 | 必須修復 |

### 2.2 技術債分類

當完整 mvn test 有失敗時，必須分類：

| 分類 | 代碼 | 說明 | Final Approval 動作 |
|------|------|------|---------------------|
| **A. Sprint 影響** | AI-A | Sprint 開發導致的失敗 | 必須修復，不可標示為技術債 |
| **B. 既有技術債** | AI-B | 與本 Sprint 無關的長期問題 | 明確標示，列入 Sprint N+1 處理 |
| **C. 環境問題** | AI-C | Redis, Database 連線等 | 修復後驗證 |
| **D. 資料問題** | AI-D | 測試資料不一致 | 修復後驗證 |

### 2.3 四方審議

| 角色 | 審查內容 | 決議 |
|------|---------|------|
| **Architect** | 架構異動是否合理 | APPROVED / REJECTED |
| **SA** | 需求對齊度與文件完整性 | APPROVED / REJECTED |
| **SD** | 技術設計、程式碼品質、效能、安全 | APPROVED / REJECTED |
| **QA** | 測試覆蓋率、測試品質、測試結果 | APPROVED / REJECTED |

---

## 3. Release 檢查

### 3.1 合併前檢查

| 檢查項目 | 標準 |
|---------|------|
| ✅ 完整 mvn test 已執行 | 489 tests, Failures + Errors 已記錄 |
| ✅ 四方審議全部 APPROVED | Final Approval 文件已建立 |
| ✅ 技術債已標示 | 如有，既有技術債已明確標示 |
| ✅ PR 已建立 | 連結到 Final Approval 文件 |

### 3.2 Release 動作

| 動作 | 命令 |
|------|------|
| 合併至 main | `git checkout main && git merge feature/Sprint{N}` |
| 建立 Release Tag | `git tag vYYYY.MM.DD-01 && git push origin main --tags` |
| 建立 GitHub Release | `gh release create vYYYY.MM.DD-01 --title "Sprint {N} Release"` |

---

## 4. 快速命令參考

### 4.1 完整測試

```bash
# 完整 mvn test（Final Approval 前必須執行）
cd backend && mvn clean test

# 只看摘要結果
cd backend && mvn test 2>&1 | grep -E "(Tests run:|BUILD|Failures:|Errors:)"

# 單一 Sprint 測試（如 SprintCurrent 標籤存在）
cd backend && mvn test -Dtest='*SprintCurrent*'
```

### 4.2 程式碼品質

```bash
# 編譯
cd backend && mvn clean compile

# Checkstyle
cd backend && mvn checkstyle:check

# 覆蓋率
cd backend && mvn jacoco:check
```

### 4.3 完整檢查序列

```bash
# 完整的 Final Approval 前檢查
cd backend && mvn clean compile && mvn checkstyle:check && mvn test && mvn jacoco:check
```

---

## 5. 歷史版本

| 版本 | 日期 | 修改內容 |
|------|------|----------|
| v1.0 | 2026-06-10 | 初始建立，基於 Sprint 16 教訓 (§1.3 重要補述) |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-10
**作者**: Claude Code (AI Assistant)
**依據**: Sprint 16 Final Approval §1.3 重要補述