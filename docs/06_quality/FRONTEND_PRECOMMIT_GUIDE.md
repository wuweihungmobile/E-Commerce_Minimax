# 前端 Pre-commit Hook 說明文件 / Frontend Pre-commit Hook Guide

> **建立日期**: 2026-06-22
> **Sprint**: Sprint 18 (US-002)
> **負責人**: Dev
> **文件版本**: v1.0
> **基於**: [SPRINT_18_TASKS.md US-002](../../05_development/SPRINT_18_TASKS.md)

---

## 🔴 重要警告

> **絕對禁止使用 `git commit --no-verify` 或 `git push --no-verify` 跳過 CI 驗證！**
> 違反將導致 CI pipeline 失敗、branch 鎖定、20+ 次無效 commit 的歷史教訓。

---

## 📋 概述

前端 Pre-commit Hook 自動執行以下檢查：

| 階段 | 檢查項目 | 執行命令 | 失敗處置 |
|------|----------|----------|----------|
| **1/3** | ESLint 自動修復 | `npx eslint --fix` | 修復 ESLint 錯誤後重試 |
| **2/3** | **TypeScript type-check** ⭐ | `npx tsc --noEmit` | 修復型別錯誤後重試 |
| **3/3** | Next.js Build (已停用) | `npm run build` | 改在 pre-push 階段執行 |

### 為什麼 type-check 很重要？

- ✅ **早期發現型別錯誤**：在 commit 前攔截，避免 CI 失敗
- ✅ **提升程式碼品質**：強制 TypeScript 嚴格模式
- ✅ **減少整合問題**：型別錯誤往往導致 runtime 問題

---

## ✅ 正常流程 (Recommended)

### 情境 1: 正常 commit

```bash
# 1. 修改程式碼
vim frontend/src/components/MyComponent.tsx

# 2. 預先執行 type-check 確認無誤
cd frontend && npm run type-check

# 3. 確認通過後 commit
git add .
git commit -m "feat(frontend): 新增 MyComponent"
# → pre-commit hook 自動執行，type-check 通過，commit 成功
```

### 情境 2: 修復 type-check 錯誤

```bash
# commit 時被 hook 攔截
git commit -m "feat(frontend): 新功能"
# ❌ TypeScript type-check failed
#    Please fix type errors before committing.

# 1. 查看錯誤訊息
cd frontend && npm run type-check
# src/components/MyComponent.tsx(10,7): error TS2322: Type 'string' is not assignable to type 'number'.

# 2. 修正錯誤
vim src/components/MyComponent.tsx

# 3. 重新確認
npm run type-check
# ✅ 通過

# 4. 重新 commit
git add .
git commit -m "feat(frontend): 新功能"
# ✅ 成功
```

---

## ⚠️ 緊急跳過方式 (NOT RECOMMENDED)

### 唯一合法的使用情境

只有以下**緊急情境**才考慮使用 `--no-verify`：

| 情境 | 是否可使用 |
|------|-----------|
| 緊急修復 production bug | ✅ 可考慮 |
| WIP commit (用於暫存進度) | ❌ 不建議 |
| 「先 commit 再修」的投機行為 | ❌ **絕對禁止** |
| CI 一直失敗想跳過驗證 | ❌ **絕對禁止** |

### 使用方式

```bash
# ⚠️ 警告：以下指令會跳過所有 pre-commit 檢查
git commit --no-verify -m "fix(frontend): 緊急修復"

# 推送到 GitHub 前
git push --no-verify origin main
```

### 使用後的必要處置

```bash
# 1. 立即在本地手動執行所有檢查
cd frontend
npm run type-check
npm run lint

# 2. 建立 follow-up issue 追蹤
# 標題: "follow-up: 修復 [commit hash] 跳過的 type-check 錯誤"
# 內容: 說明為何跳過、需要修正的事項、預計完成時間

# 3. 在 PR / commit 訊息中明確標示
git commit -m "fix(frontend): 緊急修復 (no-verify, follow-up: #123)"
```

---

## 🔴 跳過方式的副作用

### 立即影響

| 影響 | 嚴重性 |
|------|--------|
| CI pipeline 可能失敗 | 🔴 高 |
| TypeScript 型別錯誤可能影響 runtime | 🔴 高 |
| 失去 commit 前最後一道防線 | 🟡 中 |

### 長期影響

- 📉 程式碼品質下降
- 🐛 累積技術債
- ⏰ 增加後續修復時間

---

## 🛠️ 故障排除 (Troubleshooting)

### Q1: Hook 沒有被觸發

**症狀**: `git commit` 沒有執行 type-check，直接 commit 成功

**排查步驟**:
```bash
# 1. 確認 .husky 目錄存在
ls -la frontend/.husky/
# 預期: 看到 pre-commit 檔案

# 2. 確認檔案可執行
ls -la frontend/.husky/pre-commit
# 預期: -rwxr-xr-x (有執行權限)

# 3. 確認 husky 已安裝
cd frontend && cat package.json | grep husky
# 預期: "husky": "^9.x.x" 或類似的 devDependency

# 4. 重新安裝 hook
cd frontend && npx husky install
```

### Q2: type-check 一直失敗，但程式碼看起來沒問題

**可能原因**:
- TypeScript 版本不一致
- 缺少必要的 type 定義 (`@types/...`)
- tsconfig.json 設定錯誤

**排查**:
```bash
cd frontend
# 1. 查看 TypeScript 版本
npx tsc --version

# 2. 查看 tsconfig.json
cat tsconfig.json

# 3. 清除快取
rm -rf .next tsconfig.tsbuildinfo
npm run type-check
```

### Q3: 想暫時跳過 type-check 但還是要 ESLint

目前 hook 設計是「全有或全無」(全有或 --no-verify 全跳)。

**建議做法**:
- 短期: 修正 type-check 錯誤後再 commit
- 長期: 改進 hook 設計支援細粒度控制 (Sprint 19+ 評估)

---

## 📊 Hook 效能

| 項目 | 預期時間 |
|------|----------|
| ESLint | ~5-10 秒 |
| **TypeScript type-check** | **~10-30 秒** |
| Next.js Build (pre-push) | ~30-60 秒 |
| **pre-commit 總計** | **~15-40 秒** |

---

## 🔗 相關文件

- [SPRINT_18_TASKS.md US-002](../../05_development/SPRINT_18_TASKS.md#us-002-前端-pre-commit-type-check-實作-2-sp)
- [SPRINT_18_DETAILED_EXECUTION_PLAN.md](../../04_planning/SPRINT_18_DETAILED_EXECUTION_PLAN.md)
- [CLAUDE.md - CI/CD 本地驗證強制規則](../../../CLAUDE.md#-cicd-本地驗證強制規則2026-06-12-新增2026-06-14-更新)
- Backend Pre-commit Hook: `backend/hooks/pre-commit`

---

## 📝 歷史修改記錄

| 版本 | 日期 | 修改內容 | 修改人 |
|------|------|----------|--------|
| v1.0 | 2026-06-22 | 初版建立，Sprint 18 US-002 | Claude Code |
