# Sprint 19 任務清單 / Sprint 19 Tasks

> **Sprint 編號**: Sprint 19
> **期間**: 2026-07-07 ~ 2026-07-18 (2 週)
> **文件版本**: v1.0
> **建立日期**: 2026-06-24
> **更新日期**: 2026-06-24
> **依據**: [SPRINT_19_PLAN.md](../04_planning/SPRINT_19_PLAN.md)

---

## 📋 任務追蹤總覽

| 狀態 | 數量 |
|------|------|
| ✅ 已完成 | 2 |
| 🔄 進行中 | 0 |
| ⏳ 待處理 | 4 |
| **總計** | **6** |

---

## 🎯 Sprint 19 承諾的 User Stories

---

### US-001: Payment 例外處理細分（2 SP）

**負責人**: Dev
**優先級**: P0
**狀態**: ✅ **已完成**（2026-06-24）

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 識別 Payment 模組所有 `catch(Exception)` 位置 | ✅ | 10 處（2 Gateway × 4 方法 + 2 Controller） |
| AC-002: 改為至少 2 個具體例外類型（StripeException / BusinessException 等） | ✅ | stub 移除 try-catch；repo 改 DataAccessException；Controller 改 RuntimeException |
| AC-003: 每個 catch 區塊有對應 ErrorCode | ✅ | [E_5012] refund、[E_6001] status、E_9000/E_9001 webhook |
| AC-004: mvn test 286 Unit Tests 100% 通過 | ✅ | BUILD SUCCESS |

**具體任務**:
- [x] T-001-1: `grep -rn "catch.*Exception" backend/src/main/java/...payment...` 掃描所有位置
- [x] T-001-2: StripePaymentGateway.java — stub 方法移除 try-catch；repository 方法改 DataAccessException
- [x] T-001-3: LinePayPaymentGateway.java — 同上
- [x] T-001-4: StripeWebhookController.java — 驗證邏輯移出 try，修復 BusinessException 被吞 Bug
- [x] T-001-5: mvn test 286 tests, 0 Failures — BUILD SUCCESS

---

### US-002: RuntimeException 統一 ErrorCode（1 SP）

**負責人**: Dev
**優先級**: P0
**狀態**: ✅ **已完成**（2026-06-24）

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: ~~新增 `E_2003`（OAuth）~~ → 計劃錯誤，已修正 | ✅ | E_2003 已存在（Tenant context ambiguous）；E_1008 已覆蓋 OAuth；生產程式碼無 OAuth RuntimeException |
| AC-002: 新增 `E_9906`（Storage operation failed） | ✅ | 原計劃 E_9001 已存在（Invalid email format），改為 E_9906 |
| AC-003: OAuth RuntimeException 替換 | ✅ | 生產程式碼不存在，僅 E2E 測試輔助（test helper，不需修改） |
| AC-004: Storage RuntimeException 替換（3 處） | ✅ | StorageService.java uploadFile/getObject/deleteObject → BusinessException(E_9906) |
| AC-005: mvn test 286 Unit Tests 100% 通過 | ✅ | BUILD SUCCESS |

**具體任務**:
- [x] T-002-1: `grep -rn "throw new RuntimeException" backend/src/` 確認所有位置
- [x] T-002-2: ErrorCode.java 新增 E_9906（Storage operation failed）
- [x] T-002-3: OAuth — 生產程式碼無 RuntimeException，跳過
- [x] T-002-4: StorageService.java 3 處 RuntimeException → BusinessException(E_9906)
- [x] T-002-5: mvn test 286 tests, 0 Failures — BUILD SUCCESS

---

### US-003: Stripe Webhook Signature 驗證（3 SP）

**負責人**: Dev
**優先級**: P0
**狀態**: ⏳ **待處理**

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: PaymentWebhookController 加上 Stripe-Signature header 驗證 | ⏳ | |
| AC-002: 使用 `Webhook.constructEvent()` 驗證 signature | ⏳ | |
| AC-003: 驗證失敗返回 400 + 對應 ErrorCode | ⏳ | |
| AC-004: 新增 Webhook 安全測試（有效/無效/缺少 signature） | ⏳ | |
| AC-005: `stripe.webhook.secret` 設定在 application.yml，不 hardcode | ⏳ | |
| AC-006: mvn test 所有測試 100% 通過 | ⏳ | |

**具體任務**:
- [ ] T-003-1: 確認 Stripe SDK 版本是否包含 `Webhook.constructEvent()`
- [ ] T-003-2: `application.yml` 新增 `stripe.webhook.secret` 設定（含 test/prod profile）
- [ ] T-003-3: `PaymentWebhookController.java` 加上 signature 驗證邏輯
- [ ] T-003-4: 驗證失敗時拋出對應 ErrorCode（新增 E_5015 或使用現有碼）
- [ ] T-003-5: 建立測試：`PaymentWebhookControllerTest`（有效/無效/缺少 3 個案例）
- [ ] T-003-6: 本地 Stripe CLI 驗證（`stripe listen --forward-to localhost:8080/...`）
- [ ] T-003-7: mvn test + mvn verify -Pintegration-test 全部通過

---

### US-004: ErrorCode Phase 3 — 剩餘模組遷移（3 SP）

**負責人**: Dev
**優先級**: P1
**狀態**: ⏳ **待處理**

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 掃描剩餘 E_8000 使用位置（確認 Phase 2 是否已清零） | ⏳ | |
| AC-002: 掃描剩餘 E_5001 使用位置 | ⏳ | |
| AC-003: 新增必要的專用錯誤碼 | ⏳ | |
| AC-004: 遷移所有剩餘誤用 | ⏳ | |
| AC-005: mvn test 286 Unit Tests 100% 通過 | ⏳ | |

**具體任務**:
- [ ] T-004-1: 掃描 `grep -rn "E_8000\|E_5001" backend/src/main/java/ --include="*.java"`
- [ ] T-004-2: 依掃描結果建立遷移清單
- [ ] T-004-3: 新增必要 ErrorCode（如有需要）
- [ ] T-004-4: 逐模組遷移（每遷移一個模組立即 mvn test）
- [ ] T-004-5: 最終驗證 E_8000/E_5001 使用數為 0

---

### US-005: M09 下一階段新功能（3 SP）

**負責人**: PM/PO + Dev
**優先級**: P1
**狀態**: ⏳ **待處理**

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: PM/PO 確認 M09 下一階段範圍 | ⏳ | Sprint 開始後第一件事 |
| AC-002: 完成至少 1 個新 API 或業務邏輯 | ⏳ | |
| AC-003: 所有既有測試 100% 通過 | ⏳ | |

**具體任務**:
- [ ] T-005-1: PM/PO 確認 M09 範圍（或 M10 新模組）
- [ ] T-005-2: SA 細化 User Story 和 AC
- [ ] T-005-3: Dev 實作新功能
- [ ] T-005-4: 建立測試
- [ ] T-005-5: 驗證 mvn test 通過

---

### US-006: 日常開發支援（1 SP）

**負責人**: Dev
**優先級**: P2
**狀態**: ⏳ **待處理**

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 緊急 Bug 修復（如有） | ⏳ | |
| AC-002: PM/PO 臨時需求（如有） | ⏳ | |
| AC-003: 團隊技術支援（如有） | ⏳ | |

**具體任務**:
- [ ] T-006-1: 處理緊急 Bug（如有）
- [ ] T-006-2: 處理臨時需求（如有）
- [ ] T-006-3: 技術支援（如有）

---

## 📊 Sprint 19 進度追蹤

### 每日進度

| 日期 | Day | 完成任務 | 備註 |
|------|-----|----------|------|
| 2026-07-07 | Day 1 | - | Sprint 19 開始 |
| 2026-07-08 | Day 2 | - | |
| 2026-07-09 | Day 3 | - | |
| 2026-07-10 | Day 4 | - | |
| 2026-07-11 | Day 5 | - | |
| 2026-07-14 | Day 6 | - | |
| 2026-07-15 | Day 7 | - | |
| 2026-07-16 | Day 8 | - | |
| 2026-07-17 | Day 9 | - | |
| 2026-07-18 | Day 10 | - | Sprint 19 Review + Release |

### Story Points 追蹤

| US ID | 標題 | SP | 已完成 SP | 剩餘 SP |
|-------|------|-----|-----------|---------|
| US-001 | Payment catch 細分 | 2 | 2 | 0 |
| US-002 | RuntimeException 統一 ErrorCode | 1 | 1 | 0 |
| US-003 | Stripe Webhook signature 驗證 | 3 | 0 | 3 |
| US-004 | ErrorCode Phase 3 剩餘模組 | 3 | 0 | 3 |
| US-005 | M09 下一階段新功能 | 3 | 0 | 3 |
| US-006 | 日常開發支援 | 1 | 0 | 1 |
| **規劃合計** | | **13** | **3** | **10** |

---

## 🔴 Sprint 19 Definition of Done

- [ ] US-001~006 所有 AC 達成
- [ ] `mvn verify -Pintegration-test` 所有測試 100% 通過（無 Failures）
- [ ] Payment 模組無 `catch(Exception)` 過寬
- [ ] `throw RuntimeException` 降至 0 處
- [ ] Stripe Webhook 有 signature 驗證
- [ ] ErrorCode `E_8000`/`E_5001` 濫用降至 0
- [ ] Sprint 19 Review 文件建立
- [ ] Sprint 19 Retrospective 文件建立
- [ ] Sprint 19 Release 執行（v2026.07.18-01）

---

## 📝 歷史修改記錄

| 版本 | 日期 | 修改內容 | 修改人 |
|------|------|----------|--------|
| v1.0 | 2026-06-24 | 初始建立，依據 SPRINT_19_PLAN.md | Claude Code |
| v1.1 | 2026-06-24 | US-001 完成：Payment catch(Exception) 細分（10 處），修復 BusinessException 吞掉 Bug | Claude Code |
| v1.2 | 2026-06-24 | US-002 完成：StorageService 3 處 RuntimeException → BusinessException(E_9906)；新增 E_9906；修正計劃中 E_2003/E_9001 錯誤 | Claude Code |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-24
