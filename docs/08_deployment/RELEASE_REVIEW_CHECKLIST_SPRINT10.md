# Sprint 10 Release Review Checklist

**Sprint**: Sprint 10 (M16 ERP)
**發布版本**: v1.0-Sprint10
**評審日期**: 2026-05-13
**預計發布日期**: 2026-06-01 (CI 額度恢復後)

---

## 🔴 發布前確認 (PM/PO 需要確認)

### 功能完成確認

| 功能 | 描述 | Backend | Frontend | 備註 |
|------|------|----------|----------|------|
| 供應商管理 | CRUD 供應商基本資訊 | ✅ | ✅ | |
| 採購單管理 | DRAFT→SUBMITTED→RECEIVED/PARTIAL_RECEIVED 狀態機 | ✅ | ✅ | |
| 庫存台帳 | 按 SKU 查看即時庫存、異動記錄 | ✅ | ✅ | |
| 庫存異動 | INBOUND/OUTBOUND/RESERVE/RELEASE/ADJUST 手動異動 | ✅ | ✅ | |
| 低庫存預警 | 查詢低庫存商品列表 | ✅ | ✅ | |

### 測試結果確認

| 測試類別 | 結果 | 測試數 | 備註 |
|----------|------|--------|------|
| Backend Unit Tests | ✅ PASS | 360/360 | |
| M16ErpIntegrationTest | ✅ PASS | 36/36 | |
| M16ErpE2ETest | ✅ PASS | 6/6 | |
| Checkstyle | ✅ PASS | 0 violations | |
| Frontend Build | ✅ PASS | 32 pages | |

### 文件確認

| 文件 | 路徑 | 狀態 |
|------|------|------|
| Release Notes | docs/08_deployment/RELEASE_NOTES_v1.0-Sprint10-M16-ERP.md | ✅ |
| Deployment Checklist | docs/08_deployment/DEPLOYMENT_CHECKLIST_SPRINT10.md | ✅ |
| Rollback Plan | docs/08_deployment/ROLLBACK_PLAN_SPRINT10.md | ✅ |

---

## ⚠️ 待解決項目

| 項目 | 狀態 | 說明 | 預計解決 |
|------|------|------|----------|
| CI Pipeline 驗證 | ⏳ PENDING | GitHub Actions 帳單額度問題 | 2026-06-01 |

**影響**: CI Pipeline 無法在本地驗證後立即執行，需等待 GitHub 額度恢復

---

## 🔴 PM/PO 發布決策

### 選項 A: 等待 CI 驗證後發布（推薦）

- **優點**: CI 驗證通過後確認所有功能正常
- **缺點**: 需等待 2026-06-01 額度恢復
- **風險**: 低（本地測試已全部通過）

### 選項 B: 基於本地測試結果提前發布

- **優點**: 可以立即發布
- **缺點**: CI 環境可能有差異
- **風險**: 中（建議等待 CI）

---

## 📋 Release Criteria Check

### Must Have (P0) - 發布必要條件

- [ ] 所有 P0 功能已完成
- [ ] IT 測試 100% 通過
- [ ] 無 High 優先級 Bug
- [ ] Code Freeze 已執行
- [ ] Release Notes 已建立

### Should Have (P1) - 建議條件

- [ ] E2E 測試通過
- [ ] CI Pipeline 驗證通過
- [ ] Deployment Checklist 已確認
- [ ] Rollback Plan 已建立

### Could Have (P2) - 錦上添花

- [ ] 效能測試通過
- [ ] 安全掃描通過
- [ ] 文件完整更新

---

## ✅ 評審簽核

| 角色 | 姓名 | 簽核 | 日期 |
|------|------|------|------|
| PM/PO | | ⏳ | |
| QA | | ⏳ | |
| Dev Lead | | ⏳ | |

---

## 📅 後續行動

| 行動 | 負責人 | 預計日期 |
|------|--------|----------|
| CI Pipeline 驗證 | Dev | 2026-06-01 |
| PM/PO 最終確認 | PM/PO | 2026-06-01 |
| Staging 部署 | DevOps | 2026-06-01 |
| Staging 驗證 | QA | 2026-06-02 |
| Production 部署 | DevOps | 2026-06-02 |

---

**文件狀態**: 📝 草稿 - 待 PM/PO 確認
**最後更新**: 2026-05-13