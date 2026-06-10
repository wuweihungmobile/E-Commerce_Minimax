# Sprint Final Approval 流程 / Sprint Final Approval Process

> **文件類型**: 流程標準 (Process Standard)
> **版本**: v1.0
> **建立日期**: 2026-06-10
> **依據**: [SPRINT_16_FINAL_APPROVAL.md](../06_quality/SPRINT_16_FINAL_APPROVAL.md) §1.3 重要補述
> **目的**: 確保未來所有 Sprint 的 Final Approval 都必須跑完整 mvn test，避免技術債被隱藏

---

## 🔴 背景：Sprint 16 的教訓

### 問題描述

Sprint 16 的 Final Approval 僅驗證 Sprint 16 新增/修改的 34 個測試通過率 100%，但實際執行完整 `mvn test` 時發現：

| 測試範圍 | 通過 | 失敗 | 錯誤 |
|---------|------|------|------|
| Sprint 16 範圍內測試 | 34 (100%) | 0 | 0 |
| **完整專案測試 (489 個)** | **406 (83%)** | **61** | **22** |

### 根本原因

1. **Final Approval 只看 Sprint 範圍內測試** - 沒有強制執行完整 mvn test
2. **83 個既有測試 bug 被隱藏** - 這些 bug 與 Sprint 16 開發無關，但是長期技術債
3. **JPA 衝突直到 Commit 階段才被發現** - 沒有在 Final Approval 時發現 `media.MediaAsset` vs `cms.MediaAsset` 的 `@Table` 衝突

### 教訓

> **🔴 Final Approval 必須跑完整 mvn test，不能只看 Sprint 範圍內的測試**

---

## 1. Sprint Final Approval 流程定義

### 1.1 觸發時機

Sprint 結束且所有 User Stories 完成後，在合併至 `main` 分支前，**必須**執行 Final Approval 流程。

### 1.2 流程步驟

```
Sprint 結束
    ↓
Step 1: 執行完整 mvn test
    ↓
Step 2: 產生 Final Approval 文件
    ↓
Step 3: 四方審議 (Architect / SA / SD / QA)
    ↓
Step 4: 如有任何失敗，明確標示為「已知技術債 + Action Item」
    ↓
Step 5: 核准後合併至 main + 建立 Release Tag
```

---

## 2. Step 1: 執行完整 mvn test

### 2.1 強制命令

```bash
# 在合併至 main 前，必須執行此命令
cd backend && mvn clean test

# 如只想看摘要結果，使用
mvn test 2>&1 | grep -E "(Tests run:|BUILD|Failures:|Errors:)"
```

### 2.2 結果判定

| 結果類型 | 判定 | 後續動作 |
|---------|------|----------|
| **0 Failures, 0 Errors** | ✅ PASS | 正常流程，進入 Step 2 |
| **有 Failures 或 Errors** | ⚠️ 有技術債 | 必須在 Final Approval 文件中明確標示 |

### 2.3 技術債分類

當有測試失敗時，必須分類：

| 分類 | 說明 | 範例 |
|------|------|------|
| **A. Sprint 影響** | Sprint 16 新增/修改導致的失敗 | JPA 衝突修復引入的問題 |
| **B. 既有技術債** | 與本 Sprint 無關的長期 bug | doNothing() 對非 void 方法 |
| **C. 環境問題** | 測試環境配置問題 | Redis, Database 連線 |
| **D. 資料問題** | 測試資料不一致 | 預期資料與實際不符 |

---

## 3. Step 2: 產生 Final Approval 文件

### 3.1 文件命名規則

```
docs/06_quality/SPRINT_{N}_FINAL_APPROVAL.md
```

範例：
- `SPRINT_16_FINAL_APPROVAL.md`
- `SPRINT_17_FINAL_APPROVAL.md`

### 3.2 文件內容結構

```markdown
# Sprint {N} Final Approval / Sprint {N} 最終審議核准

> **Sprint 編號**: Sprint {N}
> **期間**: YYYY-MM-DD ~ YYYY-MM-DD
> **完成日期**: YYYY-MM-DD
> **🔴 重要里程碑**: [Sprint 主要完成項目]

---

## 1. Sprint 完成狀態總覽

| US | 標題 | SP | 狀態 | 負責人 |
|----|------|----|------|--------|
| ... | ... | ... | ... | ... |

---

## 2. 完整 mvn test 結果

### 2.1 測試摘要

| 項目 | 數值 |
|------|------|
| 總測試數 | 489 |
| 通過 | XXX |
| 失敗 (Failures) | XX |
| 錯誤 (Errors) | XX |
| 通過率 | XX% |

### 2.2 Sprint 範圍 vs 完整測試對照

| 項目 | Sprint 範圍內 | 完整 mvn test |
|------|-------------|---------------|
| 測試數量 | XX | 489 |
| 通過 | XX | XXX |
| 失敗 | 0 | XX |
| 錯誤 | 0 | XX |

### 2.3 失敗測試分類 (如有)

| 分類 | 數量 | 說明 | Action Item |
|------|------|------|-------------|
| A. Sprint 影響 | X | ... | AI-XXX |
| B. 既有技術債 | X | ... | AI-XXX |
| C. 環境問題 | X | ... | AI-XXX |
| D. 資料問題 | X | ... | AI-XXX |

---

## 3. 四方審議

[Architect / SA / SD / QA 審議內容]

---

## 4. 簽核

| 角色 | 姓名 | 日期 | 狀態 |
|------|------|------|------|
| Human User | - | - | ⏳ |
| Architect | Claude Code (AI) | YYYY-MM-DD | ✅ APPROVED |
| SA | Claude Code (AI) | YYYY-MM-DD | ✅ APPROVED |
| SD | Claude Code (AI) | YYYY-MM-DD | ✅ APPROVED |
| QA | Claude Code (AI) | YYYY-MM-DD | ✅ APPROVED |
```

---

## 4. Step 3 & 4: 四方審議 + 技術債標示

### 4.1 四方審議角色

| 角色 | 職責 |
|------|------|
| **Architect** | 審查架構異動是否合理 |
| **SA** | 審查需求對齊度與文件完整性 |
| **SD** | 審查技術設計、程式碼品質、效能、安全 |
| **QA** | 審查測試覆蓋率、測試品質、測試結果 |

### 4.2 技術債標示要求

當有測試失敗時，**必須**在 Final Approval 文件中明確標示：

```markdown
## 2.3 技術債說明

> **🔴 已知技術債** (不影響本 Sprint Final Approval 核准)

| ID | 測試類別 | 失敗數 | 原因 | 建議處理 |
|----|---------|--------|------|----------|
| AI-101 | M07PaymentMockIntegrationTest | 8 | doNothing() 對非 void 方法 | Sprint 17 修復 |
| AI-102 | M18KnowledgePhase2IntegrationTest | 9 | 待診斷 | Sprint 17 修復 |

**說明**: 以上技術債與本 Sprint 開發無關，是既有長期問題。本 Sprint 的新增/修改測試 (XX 個) 100% 通過。
```

### 4.3 審議結論

四方審議後，必須明確给出以下結論之一：

| 結論 | 條件 | 後續動作 |
|------|------|----------|
| **✅ APPROVED** | 所有 Sprint 範圍內測試通過，技術債已明確標示 | 正常合併流程 |
| **❌ REJECTED** | Sprint 範圍內有測試失敗（不含既有技術債） | 必須修復後重新審議 |

---

## 5. Step 5: 合併 + Release

### 5.1 合併條件

| 條件 | 說明 |
|------|------|
| ✅ | mvn test 執行完成（可允許既有技術債失敗） |
| ✅ | Final Approval 文件已建立 |
| ✅ | 四方審議全部 APPROVED |
| ✅ | 技術債已明確標示並列入 Action Item |

### 5.2 Release 動作

```bash
# 1. 合併至 main
git checkout main
git merge feature/Sprint{N}

# 2. 建立 Release Tag
git tag v2026.06.{DD}-01
git push origin main --tags

# 3. 建立 GitHub Release
gh release create v2026.06.{DD}-01 --title "Sprint {N} Release" --notes "$(cat RELEASE_NOTES.md)"
```

---

## 6. 檢查清單 (Checklist)

### Final Approval 前必須確認

- [ ] 執行 `mvn clean test` 完成
- [ ] 記錄測試結果（通過/失敗/錯誤數）
- [ ] 區分 Sprint 範圍內 vs 既有技術債
- [ ] 如有技術債，在 Final Approval 文件中明確標示
- [ ] 四方審議完成並簽核
- [ ] 合併至 main + 建立 Release Tag

### Release 動作

- [ ] PR 合併至 main
- [ ] Release Tag 建立 (`vYYYY.MM.DD-01`)
- [ ] GitHub Release 建立
- [ ] Sprint Planning 中的 Release 狀態更新

---

## 7. 範本下載

此流程的 Final Approval 文件範本位於：

```
docs/06_quality/SPRINT_16_FINAL_APPROVAL.md
```

可直接複製並修改為下一個 Sprint 的 Final Approval 文件。

---

## 8. 歷史版本

| 版本 | 日期 | 修改內容 |
|------|------|----------|
| v1.0 | 2026-06-10 | 初始建立，基於 Sprint 16 教訓 |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-10
**作者**: Claude Code (AI Assistant)
**依據**: Sprint 16 Final Approval §1.3 重要補述