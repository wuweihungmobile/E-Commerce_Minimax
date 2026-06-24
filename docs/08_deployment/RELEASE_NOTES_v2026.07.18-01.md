# Release Notes - v2026.07.18-01

**發布日期**: 2026-07-18
**發布類型**: Minor（安全強化 + 技術債清理 + 新功能）
**Sprint**: Sprint 19
**Git Tag**: `v2026.07.18-01`
**基於 Commit**: `f5a9ed6` (feat(notification): Sprint 19 US-005/006 — M09 通知用戶偏好設定 API)
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 🎯 發布摘要

Sprint 19 完成 **P0 技術債全部清零**（Payment `catch(Exception)` 10 處細分、`throw RuntimeException` 生產碼降至 0 處）、**Stripe Webhook HMAC-SHA256 安全驗證**（防止偽造請求攻擊）、以及 **M09 通知用戶偏好設定 API**（GET/PUT /v2/notifications/preferences）。同時透過掃描確認 ErrorCode Phase 3 在 Sprint 18 已全部清零，無需額外遷移。

### 關鍵指標

| 項目 | 數值 |
|------|------|
| User Stories 完成 | 6 / 6（100%）|
| Story Points | 13 SP（規劃 13 SP）|
| 達成率 | 100% |
| 測試狀態 | 564 tests (295 Unit + 269 Integration), 0 Failures, 0 Errors |
| 新增測試 | 9 個（Webhook 6 + Preference 3）|
| 新增 ErrorCode | E_9906（Storage）+ E_5015（Stripe Webhook）|
| 新增 Flyway Migration | V41__Create_User_Notification_Preferences.sql |
| CI 狀態 | ✅ Checkstyle 0 violations, PMD 0 violations |

---

## 新功能 ✨

### M09 通知系統 — 用戶偏好設定（US-005）

#### 通知偏好查詢 API

- **新增 API**: `GET /v2/notifications/preferences`
- **功能**: 查詢登入用戶所有通知偏好設定
- **預設行為**: 未設定偏好的通知類型預設 `enabled: true`
- **需認證**: 需要 Bearer Token（登入用戶）

**回應範例**:
```json
{
  "success": true,
  "data": [
    {
      "notificationType": "ORDER_CONFIRMED",
      "channel": "EMAIL",
      "enabled": true
    },
    {
      "notificationType": "PAYMENT_SUCCESS",
      "channel": "PUSH",
      "enabled": false
    }
  ]
}
```

#### 通知偏好更新 API（Upsert）

- **新增 API**: `PUT /v2/notifications/preferences`
- **功能**: 新增或更新單一通知偏好（不存在則建立，存在則更新）
- **請求體**: `{ "notificationType": "ORDER_CONFIRMED", "channel": "EMAIL", "enabled": false }`
- **需認證**: 需要 Bearer Token（登入用戶）

**請求體範例**:
```json
{
  "notificationType": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "enabled": false
}
```

#### sendNotification 偏好整合

- **行為變更**: `NotificationService.sendNotification()` 發送前檢查用戶偏好
- **跳過條件**: `enabled: false` 時記錄 WARN log 並跳過發送
- **Broadcast 例外**: Broadcast 通知不受偏好限制（強制發送）

---

## 安全性強化 🔐

### Stripe Webhook HMAC-SHA256 Signature 驗證（US-003）

- **新增服務**: `StripeSignatureVerifierService`（`infrastructure/payment/` 目錄）
- **驗證演算法**: HMAC-SHA256（等同 Stripe SDK `Webhook.constructEvent()` 標準）
- **驗證流程**:
  1. 解析 `Stripe-Signature` header（`t=timestamp,v1=hash`）
  2. 計算 `{timestamp}.{rawPayload}` 的 HMAC-SHA256
  3. 比較計算值與 header 中的 `v1` 值
  4. 驗證 timestamp 有效期（防止重放攻擊）
- **驗證失敗回應**: `400 Bad Request` + `E_5015`（Stripe webhook signature verification failed）
- **設定**: `stripe.webhook-secret: ${STRIPE_WEBHOOK_SECRET:}`（`application.yml`，不 hardcode）
- **測試涵蓋**: 6 個測試案例（空 secret / 有效簽名 / 無效簽名 / 缺少 header / 空 header / 過期 timestamp）

> **安全效益**: 防止未授權第三方偽造 Stripe Webhook 請求，確保支付回調的真實性。

---

## 技術改進 🔧

### Payment 例外處理細分（US-001）

- **問題**: Payment 模組 10 處 `catch(Exception)` 過寬，金流錯誤無法精確追蹤
- **修改範圍**:
  - `StripePaymentGateway.java`：stub 方法移除 try-catch；repository 方法改 `DataAccessException`
  - `LinePayPaymentGateway.java`：同上
  - `StripeWebhookController.java`：驗證邏輯移出 try，改為具體例外
- **隱藏 Bug 修復**: `BusinessException` 在 Webhook Controller 被 `catch(Exception)` 吞掉導致錯誤碼遺失，已修復
- **ErrorCode 對應**: `[E_5012]` 退款、`[E_6001]` 訂單狀態、`E_9000`/`E_9001` Webhook

### RuntimeException 統一 ErrorCode（US-002）

- **新增錯誤碼**: `E_9906`（Storage operation failed）
- **修改範圍**: `StorageService.uploadFile()` / `getObject()` / `deleteObject()`（3 處）
- **替換**: `throw new RuntimeException(...)` → `throw new BusinessException(ErrorCode.E_9906, ...)`
- **生產程式碼現狀**: `throw RuntimeException` 降至 **0 處** ✅

### ErrorCode Phase 3 確認完成（US-004）

- **掃描命令**: `grep -rn "E_8000\|E_5001" backend/src/main/java/ --include="*.java"`
- **結果**: `E_8000` × 3（PricingService - "Pricing rule not found"，正確語義）+ `E_5001` × 1（OrderService - 訂單狀態轉換，正確語義）
- **結論**: Sprint 18 Phase 2 已清零所有誤用，剩餘 4 處均為**正確語義使用**，無需遷移
- **技術債現狀**: E_8000/E_5001 誤用 **0 處** ✅（Phase 1+2+3 完成）

---

## 資料庫異動 🗄️

| Migration | 說明 |
|-----------|------|
| V41__Create_User_Notification_Preferences.sql | 建立 `user_notification_preferences` 表；含複合唯一索引 `UNIQUE(user_id, notification_type, channel)` |

**V41 DDL 摘要**:
```sql
CREATE TABLE user_notification_preferences (
  id               BIGSERIAL PRIMARY KEY,
  user_id          BIGINT NOT NULL REFERENCES users(id),
  notification_type VARCHAR(100) NOT NULL,
  channel          VARCHAR(50) NOT NULL,
  enabled          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMP DEFAULT NOW(),
  updated_at       TIMESTAMP DEFAULT NOW(),
  CONSTRAINT uq_user_notif_pref UNIQUE (user_id, notification_type, channel)
);
```

---

## Breaking Changes ⚠️

**無** — 本次所有變更均向後相容：
- Stripe Webhook 新增驗證層（`STRIPE_WEBHOOK_SECRET` 未設定時跳過驗證，不阻斷現有行為）
- ErrorCode 替換保留相同 HTTP 狀態碼語義
- 新增 API 不影響現有端點
- `sendNotification` 偏好檢查：偏好不存在時預設通過（enabled: true），不影響現有通知行為

---

## 新增 ErrorCode

| ErrorCode | 說明 | HTTP 狀態碼 |
|-----------|------|-------------|
| `E_9906` | Storage operation failed（儲存操作失敗） | 500 |
| `E_5015` | Stripe webhook signature verification failed（Webhook 簽名驗證失敗） | 400 |

---

## 已知問題與下一步 📋

| 項目 | 狀態 | Sprint 20 計劃 |
|------|------|----------------|
| MQ `catch(Exception)` 細分（7 處） | ⏳ 待執行 | P0，AI-403 |
| `@Deprecated` 清理（11 處） | ⏳ 待確認 | P1 |
| AdminControllerE2ETest 間歇性失敗 | ⏳ 調查中 | P0，AI-402 |
| Stripe SDK 整合（Phase 3） | ⏳ 規劃中 | P2 |
| M09 通知歷史查詢 API | ⏳ 規劃中 | P1 |

---

## 回滾方式 🔄

```bash
# 回滾至 Sprint 18 Release
git checkout v2026.07.03-01

# 注意：Flyway migration V41 已執行
# 如需回滾 V41（user_notification_preferences 表），需手動執行：
# DROP TABLE IF EXISTS user_notification_preferences;
# DELETE FROM flyway_schema_history WHERE version = '41';
```

---

## 相關文件 📄

- **Sprint 計劃**: [SPRINT_19_PLAN.md](../04_planning/SPRINT_19_PLAN.md)
- **Sprint 任務**: [SPRINT_19_TASKS.md](../05_development/SPRINT_19_TASKS.md)
- **Sprint Review**: [SPRINT_19_REVIEW.md](../05_development/SPRINT_19_REVIEW.md)
- **Sprint Retro**: [SPRINT_19_RETRO.md](../05_development/SPRINT_19_RETRO.md)
- **ErrorCode 重構評估**: [ErrorCode_Refactor_Evaluation.md](../06_quality/ErrorCode_Refactor_Evaluation.md)
- **技術債掃描**: [TECHNICAL_DEBT_TODO_SCAN.md](../06_quality/TECHNICAL_DEBT_TODO_SCAN.md)
- **Docker 政策**: [DOCKER_POLICY.md](DOCKER_POLICY.md)

---

## 如何執行 Release

```bash
# 1. 確認測試通過
cd backend && mvn verify

# 2. 建立 git tag
git tag -a v2026.07.18-01 -m "Release Sprint 19: Stripe Webhook 安全驗證 + Payment 技術債清零 + M09 通知偏好"

# 3. push tag（Sprint 官方結束後執行）
git push origin v2026.07.18-01

# 4. 建立 GitHub Release（可選）
gh release create v2026.07.18-01 --title "v2026.07.18-01 - Sprint 19" --notes-file docs/08_deployment/RELEASE_NOTES_v2026.07.18-01.md
```

> ⚠️ **注意**: 實際 Release 執行於 Sprint 19 結束日（2026-07-18）或所有 DoD 項目確認完成後。

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-25
**建立者**: Dev David + PM/PO Victoria
