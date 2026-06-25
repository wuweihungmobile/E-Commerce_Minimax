# Sprint 20 計劃 / Sprint 20 Plan

> **Sprint 編號**: Sprint 20
> **期間**: 2026-07-21 ~ 2026-08-01 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-25
> **基於**: [Sprint 19 Retrospective](../05_development/SPRINT_19_RETRO.md) + [TECHNICAL_DEBT_TODO_SCAN.md](../06_quality/TECHNICAL_DEBT_TODO_SCAN.md)

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 19 開發完成 | ✅ 6/6 US 完成（100% SP） | US-001~006 全部 AC 達成 |
| Sprint 19 測試狀態 | ✅ 564 tests, 0 Failures | `mvn verify -Pintegration-test` BUILD SUCCESS |
| Sprint 19 Review | ✅ [SPRINT_19_REVIEW.md](../05_development/SPRINT_19_REVIEW.md) | 建立於 2026-06-25 |
| Sprint 19 Retrospective | ✅ [SPRINT_19_RETRO.md](../05_development/SPRINT_19_RETRO.md) | 4 個 Action Items（AI-401~404） |
| Sprint 19 Release | ✅ [RELEASE_NOTES_v2026.07.18-01.md](../08_deployment/RELEASE_NOTES_v2026.07.18-01.md) | Tag `v2026.07.18-01` 已建立 |
| Sprint 19 Action Items | ✅ AI-401~404 已建立 | 納入 Sprint 20 規劃 |
| 技術債掃描（最新） | ✅ 2026-06-25 重新掃描 | 見下方「技術債現狀」 |

### 技術債現狀（Sprint 20 規劃前掃描）

```bash
# 掃描日期：2026-06-25
# catch(Exception) 生產程式碼（12 處）
grep -rn "catch (Exception" backend/src/main/java/ --include="*.java"

# MQ 模組（AI-403 範圍，7 處）：
#   NotificationProducerService.java: 1
#   NotificationConsumerService.java: 6
# 非 MQ 模組（需評估是否過寬，5 處）：
#   SettlementGenerator.java: 1
#   NotificationService.java: 2（通知容錯機制，可能為合理設計）
#   RedisCartService.java: 1（Redis 快取容錯，可能為合理設計）
#   BookingController.java: 1（控制器層，需細分）

# @Deprecated（11 處）
#   SettlementService.java: 8
#   MediaService.java: 1
#   ReviewDto.java: 1
#   RefreshTokenService.java: 1
```

> ✅ **技術基底穩定**: Sprint 19 完成 Payment 技術債清零、Stripe Webhook 安全驗證、M09 通知偏好 API。Sprint 20 聚焦 MQ 技術債、測試穩定性、M09 功能延伸。

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 20 |
| **開始日期** | 2026-07-21 (週一) |
| **結束日期** | 2026-08-01 (週五) |
| **Sprint 容量** | 15 SP |
| **規劃 SP** | 11 SP |
| **Buffer** | 4 SP（27%，較 Sprint 19 的 30% 下調，AI-404） |
| **團隊** | 2 人 Dev Team |

> **容量說明**: Sprint 19 Velocity 13 SP（100% 規劃）。AI-404 建議 Buffer 從 30% 降至 20%，規劃 SP 從 13 提升至 11 SP。Buffer 4 SP 用於 P2 候選項目（Stripe SDK、M10 新模組）。

---

## 2. Sprint 目標

> **目標**: 完成 MQ 模組技術債清理（AI-403），修復 AdminControllerE2ETest 間歇性失敗（AI-402），清理 @Deprecated 累積債，並推進 M09 通知歷史查詢與 M08 評分統計新功能。

### 具體目標

#### 🎯 P0 必須完成

| 功能 | 優先級 | 依據 |
|------|--------|------|
| MQ `catch(Exception)` 細分（7 處） | P0 | AI-403（Sprint 19 Retro） |
| AdminControllerE2ETest 間歇性失敗修復 | P0 | AI-402（Sprint 19 Retro） |

#### 🏗️ P1 重要項目

| 功能 | 優先級 | 依據 |
|------|--------|------|
| 非 MQ `catch(Exception)` 審查與細分（5 處） | P1 | Sprint 20 技術債掃描 |
| `@Deprecated` 清理（11 處） | P1 | 技術債管理，Sprint 19 Retro |
| M09 通知歷史查詢 API | P1 | 業務需求，Sprint 19 Review 未完成項目 |
| M08 評分統計端點（平均 / 分布） | P1 | 業務需求，Sprint 19 Buffer-C 未執行 |

#### 🔧 P2 擴充項目（Buffer 視進度決定）

| 功能 | 優先級 | 依據 |
|------|--------|------|
| Stripe SDK Phase 3 整合 | P2 | Sprint 19 Retro 技術問題 |
| M10 新模組初探（PM/PO 確認） | P2 | 業務規劃 |

---

## 3. Sprint 20 User Stories

---

### US-001: MQ catch(Exception) 細分（2 SP）

**描述**:
作為 Dev，我需要將 MQ 模組（NotificationProducerService + NotificationConsumerService）7 處 `catch(Exception)` 過寬的例外處理，細分為具體例外類型，確保訊息佇列錯誤可精確追蹤。

**驗收標準**:
- [ ] AC-001: 掃描確認 MQ 7 處 `catch(Exception)` 位置（NotificationProducerService 1 + NotificationConsumerService 6）
- [ ] AC-002: 每處改為具體例外類型（如 `MessagingException`、`BusinessException`、`DataAccessException` 等）
- [ ] AC-003: 每個 catch 區塊有對應的 ErrorCode 或明確的 fallback 邏輯
- [ ] AC-004: `mvn test` 所有單元測試 100% 通過
- [ ] AC-005: `grep -rn "catch (Exception" backend/src/main/java/com/nextkey/ecommerce/infrastructure/mq/` 結果為 0

**技術備註**:
```bash
# 掃描 MQ 模組 catch(Exception)
grep -rn "catch (Exception" backend/src/main/java/com/nextkey/ecommerce/infrastructure/mq/ --include="*.java"
# 目標：NotificationProducerService.java line 101
#        NotificationConsumerService.java lines 58, 80, 113, 138, 156, 180
```
- MQ 消費者的 `catch(Exception)` 可能是為了防止訊息重試風暴（Dead Letter Queue 保護），需先確認意圖再細分
- 若某處確實需要寬泛 catch（如 MQ 消費者的 top-level handler），需加上詳細日誌和 ErrorCode 標記

**依賴**:
- Sprint 19 Payment catch 細分模式（參考 US-001 做法）

**Story Points**: 2 SP
**負責人**: Dev David
**優先級**: P0

---

### US-002: AdminControllerE2ETest 間歇性失敗修復（1 SP）

**描述**:
作為 Dev，我需要找到並修復 `testUpdateTenantFeatureToggle_asAdmin_shouldSucceed` 在全套測試中偶發失敗（500）的根本原因，確保測試在全套執行環境下穩定通過。

**驗收標準**:
- [ ] AC-001: 識別測試失敗的根本原因（DB trigger 依賴 / feature toggle 初始狀態 / 測試資料污染）
- [ ] AC-002: 修復測試隔離問題，確保不依賴其他測試的執行順序
- [ ] AC-003: 全套測試（`mvn verify -Pintegration-test`）連續執行 3 次，`AdminControllerE2ETest` 均無失敗
- [ ] AC-004: 單獨執行 `AdminControllerE2ETest` 仍然通過（回歸驗證）

**技術備註**:
```bash
# 重現問題
mvn verify -Pintegration-test -Dtest="AdminControllerE2ETest#testUpdateTenantFeatureToggle_asAdmin_shouldSucceed"
# 全套執行（多次）觀察間歇性
mvn verify -Pintegration-test
```
- 已知線索：500 原因可能是 BOOKING_ENABLED feature toggle 在其他測試中被刪除/修改，導致本測試找不到預期資料
- 參考：`TestDatabaseInitializer.java` 中 system tenant + feature toggles 初始化邏輯
- 修復方向：在測試的 `@BeforeEach` 或 `@Sql` 中明確重建所需的 feature toggle，不依賴全域初始化狀態

**依賴**:
- 需要 PostgreSQL + Redis 運行（整合測試環境）

**Story Points**: 1 SP
**負責人**: Dev David
**優先級**: P0

---

### US-003: 非 MQ catch(Exception) 審查與細分（1 SP）

**描述**:
作為 Dev，我需要審查生產程式碼中 5 處非 MQ 模組的 `catch(Exception)`，判斷哪些是過寬（需細分）、哪些是合理設計（需文件化），並完成必要的細分。

**驗收標準**:
- [ ] AC-001: 審查 5 處（SettlementGenerator / NotificationService × 2 / RedisCartService / BookingController）
- [ ] AC-002: 對每處給出「過寬→細分」或「合理設計→加說明」的決策
- [ ] AC-003: `BookingController` 的 `catch(Exception)` 必須細分（控制器層不應有寬泛 catch）
- [ ] AC-004: 合理設計的 `catch(Exception)`（如 Redis 快取容錯）加上說明性 Log 或 comment
- [ ] AC-005: `mvn test` 所有單元測試 100% 通過

**技術備註**:
```bash
# 5 處位置
# SettlementGenerator.java:80   → 結算生成，可能為容錯設計
# NotificationService.java:97   → 通知容錯（跳過而非拋出），可能為合理設計
# NotificationService.java:134  → 同上
# RedisCartService.java:357     → Redis 快取 fallback，可能為合理設計
# BookingController.java:88     → 控制器層，應細分為具體例外
```

**依賴**:
- US-001（MQ 細分先完成，統一風格）

**Story Points**: 1 SP
**負責人**: Dev David
**優先級**: P1

---

### US-004: @Deprecated 清理（1 SP）

**描述**:
作為 Dev，我需要清理生產程式碼中 11 處 `@Deprecated` 標記，判斷哪些已有替代方案（可直接刪除舊方法）、哪些仍被呼叫（需遷移呼叫方），消除技術債累積。

**驗收標準**:
- [ ] AC-001: 掃描 11 處 `@Deprecated`（SettlementService × 8, MediaService × 1, ReviewDto × 1, RefreshTokenService × 1）
- [ ] AC-002: 確認每個 `@Deprecated` 方法的呼叫方（是否仍被使用）
- [ ] AC-003: 無呼叫方的方法直接刪除
- [ ] AC-004: 仍有呼叫方的方法，遷移呼叫方後再刪除
- [ ] AC-005: `mvn test` 所有單元測試 100% 通過
- [ ] AC-006: `grep -rn "@Deprecated" backend/src/main/java/ --include="*.java"` 降至 0 或有明確技術決策說明

**技術備註**:
```bash
# 掃描所有 @Deprecated
grep -rn "@Deprecated" backend/src/main/java/ --include="*.java"
# 確認呼叫方
grep -rn "methodName" backend/src/main/ --include="*.java"
```
- `SettlementService` 8 處：確認是否有新版方法替代，若有則直接移除舊方法
- `RefreshTokenService` 1 處：安全相關，遷移時需謹慎

**依賴**:
- 無

**Story Points**: 1 SP
**負責人**: Dev David
**優先級**: P1

---

### US-005: M09 通知歷史查詢 API（3 SP）

**描述**:
作為用戶，我想查詢自己的通知歷史記錄（最近 N 筆），以便了解收到哪些通知及是否已讀，方便後續跟進。

**驗收標準**:
- [ ] AC-001: `GET /v2/notifications/history` API 回傳登入用戶最近 N 筆通知記錄（支援 `page` / `size` 參數）
- [ ] AC-002: 回應包含：`notificationId`, `type`, `channel`, `title`, `body`, `isRead`, `createdAt`
- [ ] AC-003: 支援標記已讀：`PUT /v2/notifications/history/{id}/read`（更新 `isRead` 為 true）
- [ ] AC-004: 未讀通知數量端點：`GET /v2/notifications/history/unread-count`
- [ ] AC-005: Flyway Migration 建立 `notification_history` 表（含 `is_read`、`user_id`、`tenant_id` 欄位）
- [ ] AC-006: `mvn verify -Pintegration-test` 所有測試 100% 通過（含新增測試）

**技術備註**:
- 可能的表結構：
  ```sql
  CREATE TABLE notification_history (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    tenant_id     BIGINT NOT NULL,
    type          VARCHAR(100) NOT NULL,
    channel       VARCHAR(50) NOT NULL,
    title         VARCHAR(500),
    body          TEXT,
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP DEFAULT NOW(),
    CONSTRAINT idx_user_notif_history UNIQUE (user_id, id)
  );
  ```
- `NotificationService.sendNotification()` 發送成功後寫入 history
- API 需分頁（預設 page=0, size=20）

**依賴**:
- Sprint 19 US-005 M09 通知偏好設定（`notification_preferences` 表已存在）

**Story Points**: 3 SP
**負責人**: PM/PO Victoria + Dev David
**優先級**: P1

---

### US-006: M08 評分統計端點（2 SP）

**描述**:
作為平台管理者或買家，我想查看商品的評分統計（平均分 / 各星級分布），以便了解商品口碑。

**驗收標準**:
- [ ] AC-001: `GET /v2/products/{productId}/reviews/stats` 回傳：`averageRating`, `totalReviews`, `distribution`（1~5 星各分布數量）
- [ ] AC-002: 無評分時回傳 `averageRating: null`, `totalReviews: 0`, `distribution` 全為 0
- [ ] AC-003: 評分計算基於現有 `reviews` 表，無需新增 Migration
- [ ] AC-004: `mvn test` 所有單元測試 100% 通過（含新增測試）
- [ ] AC-005: API 文件（或 Swagger）已更新

**技術備註**:
- 可用 Spring Data JPA Projection 或 `@Query` 實作：
  ```sql
  SELECT AVG(rating), COUNT(*), SUM(CASE WHEN rating=5 THEN 1 ELSE 0 END), ...
  FROM reviews WHERE product_id = :productId
  ```
- 不需要 Flyway Migration（利用現有 `reviews.rating` 欄位）
- 可快取（`@Cacheable`，Redis TTL 5 分鐘），避免每次即時計算

**依賴**:
- Sprint 18 M08 評分系統已完成（`reviews` 表已存在）

**Story Points**: 2 SP
**負責人**: Dev David
**優先級**: P1

---

### US-007: Sprint 20 日常開發支援（1 SP）[Buffer 預留]

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
| P0 | US-001 | MQ catch(Exception) 細分（7 處） | 2 |
| P0 | US-002 | AdminControllerE2ETest 間歇性失敗修復 | 1 |
| P1 | US-003 | 非 MQ catch(Exception) 審查（5 處） | 1 |
| P1 | US-004 | @Deprecated 清理（11 處） | 1 |
| P1 | US-005 | M09 通知歷史查詢 API | 3 |
| P1 | US-006 | M08 評分統計端點 | 2 |
| P2 | US-007 | 日常開發支援 | 1 |
| **規劃合計** | | | **11 SP** |
| **Buffer** | US-008+ | 待決定 | 4 SP |
| **總容量** | | | **15 SP** |

### 4.2 Buffer 候選項目（視進度決定）

| 候選 | 說明 | SP |
|------|------|----|
| Buffer-A | Stripe SDK Phase 3 整合（替換原生 HMAC） | 2 |
| Buffer-B | M10 新模組初探（PM/PO 確認功能範圍） | 2 |

---

## 5. 技術風險評估

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|---------|
| MQ catch(Exception) 某些處為合理容錯設計 | 中 | 低 | 先確認意圖，合理設計的加文件化而非強制細分 |
| AdminControllerE2ETest 根因難以定位 | 中 | 中 | 分析 TestDatabaseInitializer 觸發條件，必要時加 `@Order` 或 `@BeforeEach` 重建資料 |
| M09 通知歷史表設計影響 NotificationService | 低 | 中 | Day 1 確認表結構，先設計再實作，編譯測試循環 |
| @Deprecated 方法仍有隱性呼叫方 | 低 | 中 | 先 grep 確認呼叫方再刪除，mvn test 立即驗證 |

---

## 6. 依賴與外部因素

| 依賴項 | 說明 | 負責方 |
|--------|------|--------|
| Sprint 19 Release Push | `git push origin main && git push origin v2026.07.18-01` 需執行 | Dev |
| M09 通知歷史設計確認 | US-005 API 設計需 PM/PO 確認欄位範圍 | PM/PO Victoria |
| Docker 服務 | 整合測試需要 PostgreSQL + Redis | Dev |

---

## 7. Sprint 20 Definition of Done

- [ ] US-001~007 所有 AC 達成（US-007 如有實際需求）
- [ ] `mvn verify -Pintegration-test` 所有測試 100% 通過（564+ tests, 0 Failures）
- [ ] MQ 模組無 `catch(Exception)` 過寬（AI-403 達成）
- [ ] AdminControllerE2ETest 全套執行 3 次均無失敗（AI-402 達成）
- [ ] `@Deprecated` 清理完成（降至 0 或說明理由保留）
- [ ] M09 通知歷史查詢 API 完成（GET /v2/notifications/history）
- [ ] M08 評分統計端點完成（GET /v2/products/{id}/reviews/stats）
- [ ] Sprint 20 Review 文件建立（SPRINT_20_REVIEW.md）
- [ ] Sprint 20 Retrospective 文件建立（SPRINT_20_RETRO.md）
- [ ] Sprint 20 Release 執行（v2026.08.01-01，Release Notes 已建立）

---

## 8. 執行順序建議

```
Day 1:
  - US-001: MQ catch(Exception) 掃描 + 細分（7 處）
  - Day 1 驗證: mvn test 100%

Day 2:
  - US-002: AdminControllerE2ETest 根因分析 + 修復
  - US-003: 非 MQ catch(Exception) 審查（5 處評估）
  - Day 2 驗證: mvn verify 全套通過 3 次

Day 3:
  - US-004: @Deprecated 清理（11 處）
  - Day 3 驗證: mvn test 100%

Day 4~5:
  - US-005: M09 通知歷史查詢 API（表設計 → Migration → Service → Controller → 測試）
  - Day 5 驗證: mvn verify 100%

Day 6:
  - US-006: M08 評分統計端點（Query → Controller → 測試）
  - Day 6 驗證: mvn test 100%

Day 7~8:
  - Sprint Review / Retro / Release 準備（AI-401：開發完成後即建立，不等 Sprint 結束）
  - Buffer 項目（Stripe SDK / M10，視進度）

Day 9~10:
  - Buffer 項目執行 / 回歸驗證 / Release tag 建立
```

> **注意（AI-401）**: Sprint 開發完成後立即建立 Review + Retro，不等 Sprint 官方結束日（2026-08-01）

---

## 9. Sprint 19 Action Items 追蹤

| Action Item | 內容 | Sprint 20 對應 |
|-------------|------|---------------|
| AI-401 | Review/Retro 在開發完成後即建立 | 執行順序 Day 7~8 已納入 |
| AI-402 | AdminControllerE2ETest 間歇性失敗根因 | US-002（P0） |
| AI-403 | MQ catch(Exception) 細分 | US-001（P0） |
| AI-404 | Sprint 容量重新校準（Buffer 降至 20%） | Buffer 由 6 SP → 4 SP（27%）✅ |

---

## 10. 歷史修改記錄

| 版本 | 日期 | 修改內容 | 修改人 |
|------|------|----------|--------|
| v1.0 | 2026-06-25 | 初始建立，基於 Sprint 19 Retro + 技術債掃描（2026-06-25） | Claude Code |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-25
**建立者**: PM/PO Victoria + SA Amanda + Dev David
