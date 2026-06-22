# 技術債掃描報告 / Technical Debt TODO Scan

> **Sprint**: Sprint 18 (US-005 日常開發支援)
> **建立日期**: 2026-06-23
> **掃描範圍**: `backend/src/main/java/**` + `frontend/src/**`
> **負責人**: Dev
> **文件版本**: v1.0
> **基於**: [SPRINT_18_TASKS.md US-005](../../05_development/SPRINT_18_TASKS.md)

---

## 📋 掃描摘要

| 技術債類型 | 數量 | 嚴重性 | 建議處理 Sprint |
|-----------|------|--------|-----------------|
| **@Deprecated 方法** | 11 | 🟡 中 | Sprint 19-20 |
| **Mock 實作** (待真實整合) | 68 | 🟡 中 | Sprint 19-21 |
| **catch (Exception) 過寬** | 22 | 🟠 中高 | Sprint 19-20 |
| **throw RuntimeException** | 4 | 🟡 中 | Sprint 19-20 |
| **Phase 標記 (分期實作)** | 5 | 🟢 低 | 持續追蹤 |
| **註解標示待實作** | 12 | 🟡 中 | 視情況 |
| **TODO/FIXME/XXX/HACK** | 0 | 🟢 極低 | N/A |
| **總計** | **~122** | - | - |

**整體評估**: 🟢 **專案程式碼品質良好**，無 TODO/FIXME/XXX/HACK 等高優先級技術債。主要技術債為 Mock 實作與 @Deprecated 方法，屬於「已規劃但尚未實作」而非「被遺忘的程式碼」。

---

## 🔍 詳細分析

### 1. @Deprecated 方法 (11 處)

**目的**: 標記已棄用但保留向後相容性的方法。

| 模組 | 檔案 | 行數 | 棄用數量 | 委派目標 |
|------|------|------|----------|----------|
| `core/settlement` | SettlementService.java | 43-88 | **8** | SettlementGenerator/Reviewer |
| `core/media` | MediaService.java | 412 | 1 | 新方法 |
| `api/dto` | ReviewDto.java | 88 | 1 | 新版 DTO |
| `infrastructure/security` | RefreshTokenService.java | 99 | 1 | 新版 API |

**風險評估**: 🟢 **低風險** - 純委派方法，內部邏輯已遷移至新模組
- SettlementService 8 個委派方法：在 Sprint 16 結算模組重構時建立
- 全部已委派至 SettlementGenerator/SettlementReviewer
- 完全向後相容

**清理建議**:
- **Sprint 19-20**: 確認無外部呼叫後，移除 @Deprecated 標記或完全刪除
- **檢查方式**: `grep -rn "SettlementService\." backend/src/ frontend/src/`

---

### 2. Mock 實作 (68 處)

**目的**: Phase 1 階段使用 Mock 實作，避免依賴真實第三方服務。

#### 2.1 Mock 模組清單

| 模組 | 用途 | 嚴重性 | 替代方案 | 預估 SP |
|------|------|--------|----------|---------|
| `core/payment/PaymentService` | 支付處理 | 🔴 高 | Stripe/LinePay SDK | 5-8 SP |
| `core/payment/PaymentStateService` | 支付狀態 | 🔴 高 | 整合真實金流 | 3-5 SP |
| `core/chat/ChatService` | 即時聊天 | 🟡 中 | WebSocket + DB | 8-10 SP |
| `core/notification/NotificationService` | 通知發送 | 🟡 中 | Email/SMS/Push Gateway | 5-8 SP |
| `core/admin/AdminService` | 平台管理 | 🟡 中 | 真實統計查詢 | 3-5 SP |
| `core/logistics/LogisticsService` | 物流追蹤 | 🟡 中 | 串接黑貓/新竹 | 5-8 SP |
| `core/erp/*` | ERP 整合 | 🟠 中高 | 串接 ERP API | 8-12 SP |
| `core/cms/media/MediaService` | 媒體儲存 | 🟡 中 | 串接 S3/MinIO | 3-5 SP |
| `infrastructure/payment/*` | 金流 Gateway | 🔴 高 | 完善 SDK 整合 | 8-10 SP |

#### 2.2 關鍵 Mock 程式碼範例

**PaymentService.java (line 38-95) - 支付處理 Mock**:
```java
* 處理支付（Mock）
* Phase 1 使用 Mock 支付，不需要真實金流整合
public Payment processPayment(UUID orderId, BigDecimal amount, String paymentMethod) {
    // ... 直接建立 SUCCESS 狀態的 Payment，無真實金流呼叫
    .status(Payment.PaymentStatus.SUCCESS) // Mock 直接成功
    .transactionId(generateMockTransactionId())
}
```

**風險評估**: 🟡 **中風險** - Mock 實作符合 Phase 1 範圍，但**生產環境前必須替換**

**清理建議**:
- **Sprint 19-20**: 整合 Stripe SDK (Payment 模組)
- **Sprint 21+**: 整合 LinePay/物流/ERP 等真實服務
- **前置條件**: 需業務決策第三方服務供應商

---

### 3. catch (Exception) 過寬 (22 處)

**目的**: 攔截所有例外避免服務中斷，但會隱藏特定錯誤。

#### 3.1 分佈統計

| 模組 | 處數 | 嚴重性 | 改進優先級 |
|------|------|--------|------------|
| `infrastructure/payment` | **8** | 🔴 高 | P0 - 金流錯誤需精確 |
| `infrastructure/mq` | **7** | 🟠 中高 | P1 - 訊息可靠性 |
| `api/controller` | 3 | 🟡 中 | P1 - 統一錯誤處理 |
| `core/notification` | 2 | 🟡 中 | P2 |
| `core/settlement` | 1 | 🟢 低 | P2 |
| `core/cart` | 1 | 🟢 低 | P2 |

#### 3.2 關鍵 catch (Exception) 範例

**infrastructure/payment/LinePayPaymentGateway.java:58-79**:
```java
} catch (Exception e) {
    log.error("LinePay API call failed: {}", e.getMessage());
    return PaymentResult.failure("LinePay error: " + e.getMessage());
}
```

**問題**:
- 將 `IOException`、`JSONException`、`BusinessException` 統一當作「LinePay 錯誤」
- 無法區分網路錯誤 vs 業務錯誤
- 導致監控和告警無法精確定位問題

**風險評估**: 🟠 **中高風險** - 影響錯誤追蹤和監控

**清理建議**:
- **Sprint 19 (P0)**: Payment 模組 - 區分網路錯誤、API 錯誤、業務錯誤
- **Sprint 20 (P1)**: MQ 模組 - 訊息處理失敗應拋出 `MessageProcessException`
- **Sprint 21 (P2)**: 其他模組統一改進

---

### 4. throw RuntimeException (4 處)

**目的**: 拋出通用例外，通常是錯誤處理時的 fallback。

| 檔案 | 行數 | 語意 | 改進建議 |
|------|------|------|----------|
| `core/oauth/OAuthService.java` | 70 | OAuth 帳號已連結 | 應為 `E_2003` (OAuth 專用) |
| `infrastructure/storage/StorageService.java` | 106 | 檔案上傳失敗 | 應為 `E_9001` (系統錯誤) |
| `infrastructure/storage/StorageService.java` | 146 | 取得物件失敗 | 應為 `E_9001` |
| `infrastructure/storage/StorageService.java` | 167 | 刪除物件失敗 | 應為 `E_9001` |

**風險評估**: 🟡 **中風險** - 違反 ErrorCode 統一管理原則
- OAuth 帳號衝突無 ErrorCode，前端無法精確處理
- Storage 錯誤混入 RuntimeException，無 i18n 支援

**清理建議**:
- **Sprint 19**: 新增 `E_2003` (OAuth account already linked)
- **Sprint 19**: 新增 `E_9001` (Storage operation failed) 或細分為 `E_9001a/b/c`
- **與 US-004 ErrorCode 重構整合**: 同一 Sprint 一起處理

---

### 5. Phase 標記 (5 處)

**目的**: 標記分期實作的進度。

| 檔案 | 行數 | 標記 | 說明 |
|------|------|------|------|
| `api/dto/AddMemberRequest.java` | 20 | Phase 1 | 直接輸入 userId，Phase 2 可改為 email |
| `api/dto/faq/FaqArticleDto.java` | 31 | Phase 2-C | 關鍵字高亮（已實作）|
| `api/controller/faq/FaqArticleController.java` | 96 | Phase 2-C | FAQ 進階功能（已實作）|
| `api/controller/faq/FaqCategoryController.java` | 84 | Phase 2-C | 分類統計（已實作）|
| `api/controller/payment/StripeWebhookController.java` | 72 | **Phase 3** | **待實作 Stripe signature 驗證** |

**風險評估**: 🟢 **低風險** - 4 處已完成，1 處為 Phase 3 規劃

**清理建議**:
- **Sprint 19**: 評估 Phase 3 Stripe signature 驗證的優先級
- **已完成的 Phase 2-C**: 移除 Phase 標記或加上「✅ 已完成」

---

### 6. 註解標示待實作 (12 處)

**目的**: 註解中標明「實際需要」、「模擬實現」等說明。

#### 6.1 關鍵註解清單

| 檔案 | 行數 | 標記 | 嚴重性 |
|------|------|------|--------|
| `core/oauth/OAuthService.java` | 89, 105, 107, 114 | 模擬實現 | 🟡 中 |
| `core/cms/media/MediaService.java` | 128 | 從 S3/MinIO 刪除實際檔案 | 🟡 中 |
| `core/erp/StockMovementService.java` | 197 | 簡化版本 | 🟢 低 |
| `api/controller/BookingController.java` | 95 | 不建議但允許 | 🟢 低 |
| `api/controller/payment/StripeWebhookController.java` | 79, 102 | 實際實作 | 🟡 中 |
| `core/oauth/OAuthService.java` | 89 | 可用 OAuth2Client 替換 | 🟡 中 |
| `infrastructure/redis/RedisLockService.java` | 25 | test stability | 🟢 低 |
| `api/controller/TenantController.java` | 193 | simplified approach | 🟢 低 |
| `core/payment/PaymentService.java` | 83, 122, 156, 181 | Mock | 🟠 中高 |
| `core/notification/NotificationService.java` | 221 | Mock Helper | 🟡 中 |

**風險評估**: 🟡 **中風險** - 主要是 Mock 實作，已在第 2 節統計

**清理建議**:
- **與 Mock 實作同步處理**
- 對於「簡化版本」類註解：建立對應的 Improvement Ticket

---

## 📊 模組技術債熱力圖

```
模組                          技術債密度
─────────────────────────────────────────
infrastructure/payment         ████████████ 14 (高)
infrastructure/mq              ████████ 7 (中高)
core/payment                   ███████ 6 (中高)
core/notification              ████ 4 (中)
core/oauth                     ████ 4 (中)
core/chat                      ██ 1 (低)
core/settlement                ████████ 9 (中)
core/admin                     ███ 3 (中)
core/logistics                 ██ 2 (中)
core/erp                       ██ 2 (中)
core/cms/media                 ██ 2 (中)
infrastructure/storage         ███ 3 (中)
api/dto                        █ 1 (低)
infrastructure/security        █ 1 (低)
infrastructure/redis           █ 1 (低)
其他模組                       ░ 0 (極低)
```

**集中度**: 技術債集中在 `infrastructure/payment` (14) 和 `core/settlement` (9)

---

## 🎯 Sprint 19-21 清理計劃

### Sprint 19 (P0 - 高優先級)

| 項目 | 預估 SP | 負責人 | 預期效益 |
|------|---------|--------|----------|
| **Payment catch 細分** (8 處) | 2 | Dev | 金流錯誤精確追蹤 |
| **OAuth ErrorCode 新增** (E_2003) | 0.5 | Dev | 統一錯誤處理 |
| **Storage ErrorCode 新增** (E_9001) | 0.5 | Dev | 統一錯誤處理 |
| **Stripe signature 驗證 (Phase 3)** | 3 | Dev | 安全性強化 |
| **小計** | **6** | - | - |

### Sprint 20 (P1 - 中優先級)

| 項目 | 預估 SP | 負責人 | 預期效益 |
|------|---------|--------|----------|
| **MQ catch 細分** (7 處) | 1.5 | Dev | 訊息處理可靠性 |
| **Notification 模組** | 2 | Dev | 真實通知整合 |
| **@Deprecated 清理** (8-11 處) | 1 | Dev | 程式碼整潔 |
| **小計** | **4.5** | - | - |

### Sprint 21+ (P2 - 持續)

| 項目 | 預估 SP | 負責人 | 預期效益 |
|------|---------|--------|----------|
| **Logistics 整合** | 5-8 | Dev | 真實物流追蹤 |
| **ERP 整合** | 8-12 | Dev | 真實 ERP 對接 |
| **Chat WebSocket** | 8-10 | Dev | 即時聊天 |
| **Admin 統計** | 3-5 | Dev | 真實管理數據 |
| **Media S3/MinIO 整合** | 3-5 | Dev | 真實雲端儲存 |
| **小計** | **27-40** | - | 需多分多 Sprint |

**總計預估**: 37.5-50.5 SP（跨 Sprint 19-24，約 3-5 個 Sprint）

---

## ✅ 驗證指標

### 程式碼品質指標

| 指標 | 當前 | 目標 | 改善方式 |
|------|------|------|----------|
| TODO/FIXME 數量 | 0 | 0 | ✅ 維持 |
| @Deprecated 數量 | 11 | 0 | Sprint 19-20 清理 |
| Mock 實作比例 | 68 處 | <10 處 | 逐步整合真實服務 |
| catch (Exception) 數量 | 22 | <5 | 細分例外處理 |
| throw RuntimeException | 4 | 0 | 統一 ErrorCode |
| 單元測試覆蓋率 | 555 tests | 維持 | 持續新增 |

### 風險降低指標

| 風險 | 當前評估 | Sprint 19 後 | Sprint 21 後 |
|------|----------|--------------|--------------|
| 生產環境金流風險 | 🔴 高 | 🟡 中 | 🟢 低 |
| 錯誤追蹤精確度 | 🟠 中 | 🟢 高 | 🟢 高 |
| 程式碼可維護性 | 🟡 中 | 🟢 高 | 🟢 高 |
| 技術債累積速度 | 🟢 低 | 🟢 低 | 🟢 低 |

---

## 📚 參考資料

- [SPRINT_18_TASKS.md US-005](../../05_development/SPRINT_18_TASKS.md)
- [ErrorCode_Refactor_Evaluation.md](ErrorCode_Refactor_Evaluation.md) - US-004 評估
- [FRONTEND_PRECOMMIT_GUIDE.md](FRONTEND_PRECOMMIT_GUIDE.md) - US-002 文件
- [SPRINT_16_FINAL_APPROVAL.md](SPRINT_16_FINAL_APPROVAL.md) - 技術債歷史
- [SPRINT_17_RETRO.md](../05_development/SPRINT_17_RETRO.md) - AI-204 行動項目

---

## 📝 文件修訂紀錄

| 版本 | 日期 | 作者 | 變更內容 |
|------|------|------|----------|
| v1.0 | 2026-06-23 | Claude Code (Dev) | 初版建立。掃描 122 處技術債，分類為 6 大類，建立 Sprint 19-21 清理計劃 |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-23
**作者**: Claude Code (AI Assistant)
