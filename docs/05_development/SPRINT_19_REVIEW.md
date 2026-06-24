# Sprint 19 Review / 衝刺回顧報告

> **Sprint 編號**: Sprint 19
> **期間**: 2026-07-07 ~ 2026-07-18 (2 週)
> **文件版本**: v1.0
> **建立日期**: 2026-06-25
> **基於**: [SPRINT_19_PLAN.md](../04_planning/SPRINT_19_PLAN.md) + [SPRINT_19_TASKS.md](./SPRINT_19_TASKS.md)

---

## 1. Sprint 概述

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 19 |
| **開始日期** | 2026-07-07 (週一) |
| **結束日期** | 2026-07-18 (週五) |
| **實際完成日期** | 2026-06-24 (Day -13 — 提前 Sprint 開始前完成) |
| **規劃 SP** | 13 SP |
| **Buffer SP** | 6 SP (預留，本 Sprint 未使用) |
| **實際完成 SP** | 13 SP |
| **達成率** | 100% (規劃 SP 全部完成) |

---

## 2. Sprint 目標達成狀態

### 🎯 原始 Sprint 目標

> **目標**: 消除 P0 技術債（Payment 例外細分、RuntimeException 統一），完成 ErrorCode Phase 3 收尾，並強化 Stripe Webhook 安全性（signature 驗證），同時推進 M09 下一階段新功能。

### ✅ 目標達成狀態

| 目標項目 | 達成狀態 | 備註 |
|----------|----------|------|
| Payment `catch(Exception)` 細分 | ✅ **達成** | 10 處全部細分，BusinessException 被吞 Bug 同步修復 |
| RuntimeException 統一 ErrorCode | ✅ **達成** | 生產程式碼 `throw RuntimeException` 降至 0 處 |
| Stripe Webhook Signature 驗證 | ✅ **達成** | HMAC-SHA256，E_5015，6 個測試案例 |
| ErrorCode Phase 3 收尾 | ✅ **達成** | 掃描確認 Sprint 18 Phase 2 已清零，無需再遷移 |
| M09 下一階段新功能 | ✅ **達成** | 通知用戶偏好設定 API（GET/PUT /v2/notifications/preferences） |

---

## 3. User Stories 完成狀態

### 3.1 完成的所有 User Stories

| US ID | 標題 | SP | 負責人 | 狀態 | 驗收標準達成 |
|-------|------|-----|--------|------|--------------|
| US-001 | Payment 例外處理細分 | 2 | Dev | ✅ 已完成 | 4/4 AC |
| US-002 | RuntimeException 統一 ErrorCode | 1 | Dev | ✅ 已完成 | 5/5 AC |
| US-003 | Stripe Webhook Signature 驗證 | 3 | Dev | ✅ 已完成 | 6/6 AC |
| US-004 | ErrorCode Phase 3 — 剩餘模組遷移 | 3 | Dev | ✅ 已完成 | 5/5 AC |
| US-005 | M09 通知用戶偏好設定 API | 3 | PM/PO + Dev | ✅ 已完成 | 5/5 AC |
| US-006 | 日常開發支援 | 1 | Dev | ✅ 已完成 | 3/3 AC |
| **合計** | | **13 SP** | | **6/6 ✅** | **28/28 AC** |

---

## 4. 重大成就

### 4.1 Payment 例外處理細分（US-001）

| 項目 | 詳情 |
|------|------|
| **問題** | Payment 模組 `catch(Exception)` 過寬，無法精確追蹤金流問題 |
| **掃描結果** | 10 處（2 Gateway × 4 方法 + StripeWebhookController 2 處） |
| **修改方式** | stub 方法移除 try-catch；repository 方法改 DataAccessException；Controller 改 RuntimeException |
| **隱藏 Bug 修復** | BusinessException 在 Webhook controller 被 `catch(Exception)` 吞掉，導致錯誤碼遺失 |
| **ErrorCode 對應** | [E_5012] refund, [E_6001] status, E_9000/E_9001 webhook |
| **測試驗證** | 286 Unit Tests, 0 Failures |

### 4.2 RuntimeException 統一 ErrorCode（US-002）

| 項目 | 詳情 |
|------|------|
| **問題** | StorageService 3 處 `throw RuntimeException` 無對應錯誤碼 |
| **新增 ErrorCode** | `E_9906`（Storage operation failed）— 原計劃 E_9001 已存在，改為 E_9906 |
| **修正計劃偏差** | E_2003（OAuth）生產程式碼不存在，僅 E2E 測試輔助，無需修改 |
| **修改位置** | `StorageService.uploadFile()` / `getObject()` / `deleteObject()` |
| **測試驗證** | 286 Unit Tests, 0 Failures |

### 4.3 Stripe Webhook HMAC-SHA256 Signature 驗證（US-003）

| 項目 | 詳情 |
|------|------|
| **安全強化** | 確保 Webhook 請求確實來自 Stripe，防止偽造請求攻擊 |
| **實作方式** | Java 原生 HMAC-SHA256（等同 Stripe SDK `Webhook.constructEvent()` 演算法） |
| **新增元件** | `StripeSignatureVerifierService`，注入 `StripeWebhookController` |
| **新增 ErrorCode** | `E_5015`（Stripe webhook signature verification failed） |
| **驗證失敗回應** | `400 Bad Request` + E_5015 |
| **測試涵蓋** | 6 個案例：空 secret / 有效 / 無效簽名 / 缺少 header / 空 header / 過期 timestamp |
| **同步修正** | StripeWebhookController 原 E_9001 誤用修正為正確錯誤碼 |
| **測試驗證** | 292 Unit Tests, 0 Failures |

### 4.4 ErrorCode Phase 3 收尾確認（US-004）

| 項目 | 詳情 |
|------|------|
| **掃描命令** | `grep -rn "E_8000\|E_5001" backend/src/main/java/ --include="*.java"` |
| **掃描結果** | E_8000 × 3（PricingService - "Pricing rule not found"）+ E_5001 × 1（OrderService - 訂單狀態轉換） |
| **評估結論** | 4 處均為**正確語義**，非誤用；Sprint 18 Phase 2 已清零所有誤用 |
| **不需新增 ErrorCode** | 現有碼已正確對應語義，無需遷移 |
| **技術債現狀** | E_8000/E_5001 誤用：**0 處** ✅ |

### 4.5 M09 通知用戶偏好設定 API（US-005）

| 項目 | 詳情 |
|------|------|
| **新增 API** | `GET /v2/notifications/preferences` — 查詢偏好（含未設定者預設 enabled:true） |
| **新增 API** | `PUT /v2/notifications/preferences` — upsert 單一偏好 |
| **Flyway Migration** | `V41__Create_User_Notification_Preferences.sql`（含複合唯一索引） |
| **新增元件** | `UserNotificationPreference` entity / repository / `NotificationPreferenceService` / `NotificationPreferenceController` |
| **sendNotification 整合** | `NotificationService.sendNotification()` 加入 `isEnabled()` 偏好檢查；broadcast 不受限 |
| **測試涵蓋** | 3 個案例：TC-001 預設 enabled / TC-002 upsert 建立 / TC-003 isEnabled false |
| **測試驗證** | 295 Unit Tests, 0 Failures |

---

## 5. 關鍵指標

### 5.1 測試覆蓋

| 指標 | Sprint 18 | Sprint 19 | 變化 |
|------|-----------|-----------|------|
| 單元測試數量 | 286 Unit | 295 Unit | +9 |
| 整合測試數量 | 269 Integration | 269 Integration | = |
| **總測試數** | 555 tests | **564 tests** | **+9** |
| 失敗率 | 0% | **0%** | = |
| 新增測試 | 14 (ReviewSearch) | **9 (Webhook:6 + Preference:3)** | +9 |
| Checkstyle 違規 | 0 | **0** | = |
| PMD 違規 | 0 | **0** | = |

### 5.2 技術債清理成果

| 指標 | Sprint 18 末 | Sprint 19 末 | 改善 |
|------|-------------|-------------|------|
| `catch(Exception)` 過寬 (Payment 模組) | 10 處 | **0 處** | 🔽 -10 |
| `throw RuntimeException` (生產程式碼) | 3 處 | **0 處** | 🔽 -3 |
| ErrorCode E_8000/E_5001 誤用 | ~0 處 | **0 處（已確認）** | = |
| 新增 ErrorCode | 74 | **76 (E_9906, E_5015)** | +2 |
| Stripe Webhook 安全驗證 | ❌ | **✅** | ✅ |

### 5.3 新增 API

| API | Method | 描述 |
|-----|--------|------|
| `/v2/notifications/preferences` | GET | 查詢登入用戶所有通知偏好 |
| `/v2/notifications/preferences` | PUT | upsert 單一通知偏好設定 |

---

## 6. 本次 Sprint 亮點

1. **提前完成所有規劃 US**：Sprint 正式開始（07-07）前，所有 13 SP 於 2026-06-24 完成，體現 Sprint 19 前置作業效率
2. **安全性強化**：Stripe Webhook Signature 驗證從 0 到完整實作（HMAC-SHA256），同步修復 BusinessException 被吞的隱藏 Bug
3. **技術債清零**：Payment `catch(Exception)` 10 處全部細分，`throw RuntimeException` 生產程式碼降至 0 處
4. **計劃偏差主動修正**：US-002 原計劃錯誤（E_2003 OAuth 生產程式碼不存在），掃描確認後調整計劃，避免無效工作
5. **Phase 3 零遷移確認**：通過實際掃描驗證 Sprint 18 Phase 2 已清零所有誤用，無需額外遷移
6. **M09 新功能交付**：通知偏好設定 API（GET/PUT）完整實作，包含 sendNotification 偏好整合與 Flyway V41 migration

---

## 7. 未完成項目

| 項目 | 原因 | 下一步 |
|------|------|--------|
| Buffer 項目（MQ catch 細分 / @Deprecated 清理等） | 規劃 SP 已 100% 完成，Buffer 預留 | Sprint 20 規劃 |
| M09 推播通知歷史查詢 | 本 Sprint 範圍為偏好設定 | Sprint 20 規劃 |
| M10 新模組 | PM/PO 確認中 | Sprint 20 規劃 |

---

## 8. Definition of Done 確認

| DoD 項目 | 狀態 | 備註 |
|----------|------|------|
| US-001~006 所有 AC 達成 | ✅ | 28/28 AC 全部達成 |
| `mvn verify -Pintegration-test` 所有測試 100% 通過 | ✅ | 564 tests (295 Unit + 269 Integration), 0 Failures |
| Payment 模組無 `catch(Exception)` 過寬 | ✅ | 全部細分為具體例外類型 |
| `throw RuntimeException` 降至 0 處 | ✅ | 生產程式碼掃描確認 |
| Stripe Webhook 有 signature 驗證 | ✅ | HMAC-SHA256，E_5015，6 個測試案例 |
| ErrorCode `E_8000`/`E_5001` 濫用降至 0 | ✅ | Phase 3 掃描確認 4 處均為正確語義 |
| Sprint 19 Review 文件建立 | ✅ | **此文件** |
| Sprint 19 Retrospective 文件建立 | ⏳ | 待建立 |
| Sprint 19 Release 執行（v2026.07.18-01） | ⏳ | 待執行 |

---

## 9. 下一個 Sprint 建議

### Sprint 20 焦點（建議）

| 類別 | 項目 | SP | 優先級 |
|------|------|-----|--------|
| 技術債 | MQ `catch(Exception)` 細分（7 處） | 1.5 | P0 |
| 技術債 | `@Deprecated` 清理（SettlementService 等） | 1 | P1 |
| 新功能 | M09 通知歷史查詢 API | 2-3 | P1 |
| 新功能 | M08 評分統計端點（平均/分布） | 2 | P1 |
| 新功能 | M10 新模組（PM/PO 確認） | TBD | P2 |
| **小計** | | **~8 SP** | |

---

## 10. 附錄：主要 Commits

| Commit | 內容 | 日期 |
|--------|------|------|
| `f5a9ed6` | feat(notification): Sprint 19 US-005/006 — M09 通知用戶偏好設定 API | 2026-06-24 |
| `8697aaa` | docs(sprint19): US-004 完成 — ErrorCode Phase 3 掃描確認誤用歸零 | 2026-06-24 |
| `bc539f8` | feat(payment): Sprint 19 US-003 — Stripe Webhook HMAC-SHA256 Signature 驗證 | 2026-06-24 |
| `ca9f483` | feat(errorcode): Sprint 19 US-002 — StorageService RuntimeException 統一 ErrorCode | 2026-06-24 |
| `6394a06` | fix(payment): Sprint 19 US-001 — Payment catch(Exception) 細分（10 處） | 2026-06-24 |
| `33b3760` | docs(sprint18/19): Sprint 18 收尾文件 + Sprint 19 規劃完成 | 2026-06-24 |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-25
**建立者**: PM/PO Victoria + SA Amanda + Dev David
