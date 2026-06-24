# Sprint 19 計劃 / Sprint 19 Plan

> **Sprint 編號**: Sprint 19
> **期間**: 2026-07-07 ~ 2026-07-18 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-24
> **基於**: [Sprint 18 Retrospective](../05_development/SPRINT_18_RETRO.md) + [TECHNICAL_DEBT_TODO_SCAN.md](../06_quality/TECHNICAL_DEBT_TODO_SCAN.md)

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 18 開發完成 | ✅ 8/8 US 完成 (158% SP) | US-001~008 全部 AC 達成 |
| Sprint 18 測試狀態 | ✅ 555 tests, 0 Failures | `mvn verify -Pintegration-test` BUILD SUCCESS |
| Sprint 18 Review | ✅ [SPRINT_18_REVIEW.md](../05_development/SPRINT_18_REVIEW.md) | 建立於 2026-06-24 |
| Sprint 18 Retrospective | ✅ [SPRINT_18_RETRO.md](../05_development/SPRINT_18_RETRO.md) | 4 個 Action Items |
| Sprint 18 Release 準備 | ✅ [RELEASE_NOTES_v2026.07.03-01.md](../08_deployment/RELEASE_NOTES_v2026.07.03-01.md) | Sprint 18 結束後執行 tag |
| Sprint 18 Action Items | ✅ AI-301~304 已建立 | 納入 Sprint 19 規劃 |
| 技術債清單 | ✅ [TECHNICAL_DEBT_TODO_SCAN.md](../06_quality/TECHNICAL_DEBT_TODO_SCAN.md) | 122 處分類，Sprint 19 P0 項目確認 |

> ✅ **技術基底穩定**: Sprint 18 完成 ErrorCode 全面遷移 Phase 2，Flyway 正式啟用，Docker 政策建立。Sprint 19 可基於此繼續技術債清理。

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 19 |
| **開始日期** | 2026-07-07 (週一) |
| **結束日期** | 2026-07-18 (週五) |
| **Sprint 容量** | 20 SP |
| **規劃 SP** | 14 SP |
| **Buffer** | 6 SP (30%) |
| **團隊** | 2 人 Dev Team |

> **容量說明**: Sprint 18 Velocity 達 19 SP（規劃+Buffer），Sprint 19 適度提升規劃 SP 至 14 SP（Sprint 17: 12.5, Sprint 18: 12+7）。

---

## 2. Sprint 目標

> **目標**: 消除 P0 技術債（Payment 例外細分、RuntimeException 統一），完成 ErrorCode Phase 3 收尾，並強化 Stripe Webhook 安全性（signature 驗證），同時推進 M09 下一階段新功能。

### 具體目標

#### 🎯 P0 必須完成

| 功能 | 優先級 | 依據 |
|------|--------|------|
| Payment `catch(Exception)` 細分（8 處） | P0 | TECHNICAL_DEBT_TODO_SCAN.md + AI-304 |
| RuntimeException 統一（E_2003 + E_9001） | P0 | TECHNICAL_DEBT_TODO_SCAN.md + AI-304 |
| Stripe Webhook signature 驗證（Phase 3） | P0 | TECHNICAL_DEBT_TODO_SCAN.md |

#### 🏗️ P1 架構與技術債

| 功能 | 優先級 | 依據 |
|------|--------|------|
| ErrorCode Phase 3（剩餘模組遷移） | P1 | AI-303 |
| M09/M10 新功能開發（PM/PO 確認） | P1 | 業務需求 |

---

## 3. Sprint 19 User Stories

---

### US-001: Payment 例外處理細分（2 SP）

**描述**:
作為 Dev，我需要將 Payment 模組 8 處 `catch(Exception)` 過寬的例外處理，細分為具體例外類型（網路錯誤、API 錯誤、業務錯誤），以便精確追蹤金流問題。

**驗收標準**:
- [ ] AC-001: 識別 Payment 模組所有 `catch(Exception)` 位置（PaymentService、PaymentStateService、PaymentWebhookController 等）
- [ ] AC-002: 將 `catch(Exception)` 改為至少 2 個具體例外類型（如 `StripeException`、`BusinessException`）
- [ ] AC-003: 每個 catch 區塊有對應的 ErrorCode（E_5011/E_5012 等，與 Sprint 18 Phase 2C 一致）
- [ ] AC-004: `mvn test` 286 Unit Tests 100% 通過

**技術備註**:
- 參考 [ErrorCode_Refactor_Evaluation.md](../06_quality/ErrorCode_Refactor_Evaluation.md) Phase 3 指引
- StripeException 子類型：CardException（卡片錯誤）、RateLimitException（速率限制）、InvalidRequestException（無效請求）、ApiException（Stripe API 錯誤）、AuthenticationException（認證錯誤）

**依賴**:
- Sprint 18 ErrorCode Phase 2C 已完成（E_5011/E_5012 已建立）

**Story Points**: 2 SP
**負責人**: Dev David
**優先級**: P0

---

### US-002: RuntimeException 統一 ErrorCode（1 SP）

**描述**:
作為 Dev，我需要新增 2 個新 ErrorCode（E_2003 + E_9001）並替換現有的 `throw RuntimeException` 用法，消除 4 處技術債。

**驗收標準**:
- [ ] AC-001: 新增 `E_2003`（OAuth account already linked）到 ErrorCode.java
- [ ] AC-002: 新增 `E_9001`（Storage operation failed）到 ErrorCode.java（可細分 E_9001a/b/c）
- [ ] AC-003: OAuth 相關 `throw RuntimeException` 替換為 `E_2003`（預估 1 處）
- [ ] AC-004: Storage 相關 `throw RuntimeException` 替換為 `E_9001`（預估 3 處）
- [ ] AC-005: `mvn test` 286 Unit Tests 100% 通過

**技術備註**:
- `grep -rn "throw new RuntimeException" backend/src/` 先確認所有位置
- 與 US-001 同步執行（ErrorCode 同一批新增）

**依賴**:
- US-001（共用 ErrorCode 新增作業）

**Story Points**: 1 SP
**負責人**: Dev David
**優先級**: P0

---

### US-003: Stripe Webhook Signature 驗證（3 SP）

**描述**:
作為 Dev/Security，我需要完成 Stripe Webhook 的 signature 驗證（Phase 3），確保 Webhook 請求確實來自 Stripe，防止偽造請求攻擊。

**驗收標準**:
- [ ] AC-001: `PaymentWebhookController` 加上 `Stripe-Signature` header 驗證
- [ ] AC-002: 使用 Stripe SDK 的 `Webhook.constructEvent()` 驗證 signature
- [ ] AC-003: 驗證失敗時返回 `400 Bad Request` + 對應 ErrorCode
- [ ] AC-004: 新增 Webhook 端點安全測試（至少 3 個：有效/無效/缺少 signature）
- [ ] AC-005: `.env` / `application.yml` 加上 `stripe.webhook.secret` 設定，不 hardcode
- [ ] AC-006: `mvn test` 所有測試 100% 通過（含新增 Webhook 測試）

**技術備註**:
- Stripe Webhook Secret 格式：`whsec_...`
- 本地開發可用 Stripe CLI：`stripe listen --forward-to localhost:8080/v2/payments/webhook`
- 測試用 mock Webhook event

**依賴**:
- 無（獨立功能）

**Story Points**: 3 SP
**負責人**: Dev David
**優先級**: P0

---

### US-004: ErrorCode Phase 3 — 剩餘模組遷移（3 SP）

**描述**:
作為 Dev，我需要完成 ErrorCode 全面遷移的 Phase 3，將剩餘模組（CheckoutService、UserService、StoreService、OrderService 等）的 `E_8000` / `E_5001` 濫用，遷移至對應專用錯誤碼。

**驗收標準**:
- [ ] AC-001: 掃描並確認剩餘 `E_8000` 使用位置（預估 0 處，Phase 2 已完成；確認其他模組）
- [ ] AC-002: 掃描並確認剩餘 `E_5001` 使用位置（預估 8 處：CheckoutService 等）
- [ ] AC-003: 新增對應專用錯誤碼（若需要）
- [ ] AC-004: 遷移所有剩餘誤用至新錯誤碼
- [ ] AC-005: `mvn test` 286 Unit Tests 100% 通過

**技術備註**:
```bash
# 執行此命令確認剩餘位置
grep -rn "E_8000\|E_5001" backend/src/main/java/ --include="*.java"
```

**依賴**:
- Sprint 18 ErrorCode Phase 1+2 已完成

**Story Points**: 3 SP
**負責人**: Dev David
**優先級**: P1

---

### US-005: M09 下一階段新功能（3 SP）

**描述**:
作為 PM/PO，我需要確認 M09 通知系統或其他模組的下一階段需求，並完成至少 1 個有業務價值的新功能。

**驗收標準**:
- [ ] AC-001: PM/PO 確認 M09 下一階段範圍（或 M10 新模組）
- [ ] AC-002: 完成至少 1 個新 API 或業務邏輯
- [ ] AC-003: 所有既有測試 100% 通過（含新增測試）

**技術備註**:
- M09 候選功能：推播通知設定 / 通知偏好 / 通知歷史查詢
- M10 候選功能：（待 PM/PO 確認）

**依賴**:
- PM/PO 確認功能範圍

**Story Points**: 3 SP（可調整）
**負責人**: PM/PO Victoria + Dev David
**優先級**: P1

---

### US-006: Sprint 19 日常開發支援（1 SP）[Buffer 預留]

**描述**:
處理緊急 Bug、PM/PO 臨時需求、團隊技術支援等。

**驗收標準**:
- [ ] AC-001: 緊急 Bug 修復（如有）
- [ ] AC-002: PM/PO 臨時需求（如有）
- [ ] AC-003: 團隊技術支援（如有）

**Story Points**: 1 SP
**負責人**: Dev David
**優先級**: P2

---

## 4. Sprint 容量規劃

### 4.1 Story Points 分配

| 優先級 | US ID | 標題 | SP |
|--------|-------|------|----|
| P0 | US-001 | Payment catch 細分 | 2 |
| P0 | US-002 | RuntimeException 統一 ErrorCode | 1 |
| P0 | US-003 | Stripe Webhook Signature 驗證 | 3 |
| P1 | US-004 | ErrorCode Phase 3 剩餘模組 | 3 |
| P1 | US-005 | M09 下一階段新功能 | 3 |
| P2 | US-006 | 日常開發支援 | 1 |
| **規劃合計** | | | **13 SP** |
| **Buffer** | US-007+ | 待決定 | 6 SP |
| **總容量** | | | **20 SP** |

### 4.2 Buffer 候選項目（視進度決定）

| 候選 US | 說明 | SP |
|---------|------|----|
| Buffer-A | MQ `catch(Exception)` 細分（7 處） | 1.5 |
| Buffer-B | @Deprecated 清理（SettlementService 等） | 1 |
| Buffer-C | M08 評分統計端點（計算平均/分布） | 2 |
| Buffer-D | Admin 商品管理後台功能 | 3 |

---

## 5. 技術風險評估

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|---------|
| Stripe Webhook 本地測試環境設定複雜 | 中 | 中 | 使用 Stripe CLI mock + 測試用 dummy secret |
| ErrorCode Phase 3 掃描發現比預期更多誤用 | 低 | 低 | 預留 Buffer-A 吸收 |
| M09 需求不明確導致延遲 | 中 | 中 | US-005 Day 1 先做 PM/PO 確認，確認前不實作 |
| Payment 例外細分影響現有測試 | 低 | 中 | 每次修改後立即 `mvn test`，編譯-測試循環 |

---

## 6. 依賴與外部因素

| 依賴項 | 說明 | 負責方 |
|--------|------|--------|
| Stripe Webhook Secret | 需從 Stripe Dashboard 取得 `whsec_...` | Dev |
| M09 需求確認 | US-005 需要 PM/PO 確認功能範圍 | PM/PO Victoria |
| Sprint 18 Release 完成 | Sprint 19 開始前，Sprint 18 tag 應已建立 | Dev |

---

## 7. Sprint 19 Definition of Done

- [ ] US-001~005 所有 AC 達成
- [ ] `mvn verify -Pintegration-test` 所有測試 100% 通過（無 Failures）
- [ ] Payment 模組無 `catch(Exception)` 過寬
- [ ] `throw RuntimeException` 降至 0 處（或僅剩測試用）
- [ ] Stripe Webhook 有 signature 驗證（安全性）
- [ ] ErrorCode `E_8000`/`E_5001` 濫用降至 0（Phase 3 完成後）
- [ ] Sprint 19 Review 文件建立
- [ ] Sprint 19 Retrospective 文件建立
- [ ] Sprint 19 Release 執行（v2026.07.18-01）

---

## 8. 執行順序建議

```
Day 1:
  - US-001: Payment catch 細分（識別 + 修正）
  - US-002: RuntimeException 統一（E_2003/E_9001 新增 + 替換）
  - Day 1 驗證: mvn test 100%

Day 2:
  - US-003: Stripe Webhook signature 驗證（實作 + 測試）
  - Day 2 驗證: mvn test 100% + Stripe CLI mock 測試

Day 3:
  - US-004: ErrorCode Phase 3 掃描 + 遷移
  - Day 3 驗證: mvn test 100%，確認 E_8000/E_5001 降至 0

Day 4:
  - US-005: M09 新功能（PM/PO 確認 → 實作）
  - Day 4 驗證: 整合測試通過

Day 5+:
  - Sprint Review / Retro / Release 準備
  - Buffer 項目（視進度）
```

---

## 9. 歷史修改記錄

| 版本 | 日期 | 修改內容 | 修改人 |
|------|------|----------|--------|
| v1.0 | 2026-06-24 | 初始建立，基於 Sprint 18 Retro + 技術債清單 | Claude Code |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-24
**建立者**: PM/PO Victoria + SA Amanda + Dev David
