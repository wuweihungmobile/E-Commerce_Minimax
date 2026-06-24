# Sprint 19 Retrospective 報告 / Sprint 19 Retrospective Report

> **Sprint 編號**: Sprint 19
> **期間**: 2026-07-07 ~ 2026-07-18 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-06-25
> **基於**: [SPRINT_19_PLAN.md](../04_planning/SPRINT_19_PLAN.md), [SPRINT_19_TASKS.md](./SPRINT_19_TASKS.md), [SPRINT_19_REVIEW.md](./SPRINT_19_REVIEW.md), [SPRINT_18_RETRO.md](./SPRINT_18_RETRO.md)

---

## 1. Sprint 19 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | 消除 P0 技術債、ErrorCode Phase 3 收尾、Stripe Webhook 安全強化、M09 新功能 |
| **規劃 SP** | 13 SP |
| **完成 SP** | 13 SP（100%）|
| **完成 US** | 6/6（100%）|
| **測試結果** | 564 tests (295 Unit + 269 Integration), 0 Failures（100%）|
| **團隊** | 2 人 Dev Team |
| **提前完成** | Sprint 正式開始（07-07）前 13 天（2026-06-24）完成所有工作 |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **Payment 技術債清零** | `catch(Exception)` 10 處全部細分，同步修復 BusinessException 被吞 Bug | 金流錯誤可精確追蹤 |
| ✅ **RuntimeException 生產碼降至 0** | StorageService 3 處 → BusinessException(E_9906) | 例外處理規範化 |
| ✅ **Stripe Webhook 安全驗證** | HMAC-SHA256 + E_5015 + 6 個測試案例 | 防偽造請求攻擊 |
| ✅ **ErrorCode Phase 3 確認完成** | 掃描確認 Sprint 18 已清零所有誤用，無額外遷移 | 技術債管理透明化 |
| ✅ **M09 通知偏好 API** | GET/PUT /v2/notifications/preferences + sendNotification 整合 | 用戶控制通知頻道 |

---

## 2. 做得好的地方（What Went Well）

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **100% US 完成** | 6 個 User Story 全部達成所有 AC | US-001~006 全部 ✅ |
| **100% SP 達成** | 規劃 13 SP，完成 13 SP | 無超額亦無遺漏 |
| **100% 測試通過** | 564 tests, 0 Failures | `mvn verify` BUILD SUCCESS |
| **Sprint 前完成** | Sprint 正式開始（07-07）前，全部工作於 2026-06-24 完成 | 持續的高效率 |

### 2.2 技術實現

| 項目 | 說明 | 影響 |
|------|------|------|
| **計劃偏差主動修正** | US-002 原計劃 E_2003 OAuth 不存在，掃描確認後調整為 E_9906 Storage | 避免無效工作，驗證先掃描再開發 ✅ |
| **隱藏 Bug 發現** | US-001 過程中發現 BusinessException 被 `catch(Exception)` 吞掉的 Bug | 技術債清理同步提升系統健壯性 ✅ |
| **E2E 測試驗證流程** | Phase 3 通過實際 grep 掃描確認，而非假設，避免無效遷移 | 數據驅動決策，零浪費 ✅ |
| **Stripe HMAC 原生實作** | 不引入 Stripe SDK（Phase 3 計劃再加），用 Java 原生實現等效演算法 | 降低依賴，測試更純淨 ✅ |
| **M09 完整設計** | sendNotification 整合偏好檢查，broadcast 不受限，Flyway V41 | 業務邏輯完整，無技術捷徑 ✅ |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **Sprint 18 Action Items 完成** | AI-203（ErrorCode Phase 3）、AI-204（Payment catch 細分）均達成 | Sprint 19 US-004 + US-001 完成 |
| **Sprint 19 計劃提前就緒** | Sprint 18 Retro 後立即完成 Sprint 19 計劃（AI-302 達成） | SPRINT_19_PLAN.md 已存在 |
| **Release 機制改善** | Sprint 18 AI-301 建立了 Release 不可跳過的規範 | 本 Sprint 執行 Release 確認 |
| **測試逐步成長** | 從 555 到 564 tests（+9），品質持續提升 | 6 + 3 新測試案例 |

---

## 3. 需要改善的地方（What Could Be Improved）

### 3.1 流程問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **Sprint Review/Retro/Release 集中在 Sprint 後** | 開發早於 Sprint 開始，但收尾文件在 Sprint 結束後才建立 | 🟠 中 — 文件時效性降低 |
| **Buffer 項目未使用** | 13 SP 完成率 100%，但預留 6 SP Buffer 全部未用 | 🟡 低 — Buffer 估算偏高 |
| **整合測試需要 Docker** | `mvn verify` 需要手動啟動 Docker，無法純靠 CI 驗證 local | 🟡 低 — 影響本地開發體驗 |

### 3.2 技術問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **AdminControllerE2ETest 間歇性失敗** | 測試順序相依（全套運行時偶發 500），單獨執行通過 | 🟡 低 — 非 Sprint 19 引入，屬既有問題 |
| **Stripe SDK 未加入（Phase 3 待議）** | 本 Sprint 用原生 HMAC 替代，SDK 整合留待 Phase 3 | 🟡 低 — 功能正確，依賴管理待完善 |
| **MQ catch(Exception) 7 處未處理** | Sprint 19 技術債掃描識別，但未在本 Sprint 執行 | 🟡 低 — 排入 Sprint 20 |

### 3.3 估算偏差

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **Buffer 6 SP 全部預留未用** | Sprint 19 任務難度低於預期（US-004 確認無需遷移） | 🟡 低 — 規劃保守 |
| **US-004 難度低於預期** | Phase 3 原估 3 SP（需遷移），實際 0 行程式碼變更（掃描確認） | 🟡 低 — 可從掃描結果更早調整 SP |

---

## 4. Action Items（改善行動）

### AI-401: Sprint Review/Retro 在開發完成後即建立（不等 Sprint 結束）

| 項目 | 內容 |
|------|------|
| **問題** | Sprint 開發提前完成，但 Review/Retro 文件延至 Sprint 結束後才建立，降低文件時效性 |
| **行動** | 當所有 US 完成後，立即建立 Review + Retro 文件，不等 Sprint 官方結束日 |
| **負責人** | PM/PO Victoria + SA Amanda |
| **驗收** | SPRINT_19_REVIEW.md 和 SPRINT_19_RETRO.md 在 US 完成後 24 小時內建立 |
| **ID** | AI-401 |

### AI-402: AdminControllerE2ETest 間歇性失敗根因調查

| 項目 | 內容 |
|------|------|
| **問題** | `testUpdateTenantFeatureToggle_asAdmin_shouldSucceed` 在全套測試執行時偶發失敗（500），單獨執行通過 |
| **行動** | 調查測試資料污染根因（可能是 DB trigger 或 feature toggle 初始狀態相依），Sprint 20 修復 |
| **負責人** | Dev David |
| **截止日期** | Sprint 20 Day 2 |
| **驗收** | 全套測試執行 5 次均無此測試失敗 |
| **ID** | AI-402 |

### AI-403: MQ catch(Exception) 細分排入 Sprint 20 P0

| 項目 | 內容 |
|------|------|
| **問題** | MQ 模組仍有 7 處 `catch(Exception)` 過寬，與 Payment 模組同等技術債 |
| **行動** | Sprint 20 規劃時，MQ catch 細分列為 P0，與 Payment 細分同樣標準處理 |
| **負責人** | Dev David |
| **截止日期** | Sprint 20 |
| **驗收** | MQ 模組無 `catch(Exception)` 過寬 |
| **ID** | AI-403 |

### AI-404: Sprint 容量重新校準（Buffer 降至 20%）

| 項目 | 內容 |
|------|------|
| **問題** | Sprint 18/19 連續兩個 Sprint Buffer 使用率差異大（87% vs 0%），估算不穩定 |
| **行動** | Sprint 20 規劃將 Buffer 從 6 SP（30%）調整為 4 SP（20%），並更積極引入 Buffer 項目 |
| **負責人** | PM/PO Victoria |
| **截止日期** | Sprint 20 計劃時 |
| **驗收** | Sprint 20 Buffer 使用率 > 50%，或計劃 SP 提高至 16-18 SP |
| **ID** | AI-404 |

---

## 5. Sprint 18 Action Items 執行結果

（追蹤 Sprint 18 Retro 承諾的改善行動）

| Action Item | Sprint 18 承諾 | 執行結果 |
|-------------|---------------|----------|
| AI-301（Sprint 18）| Sprint Release 不再跳過 | ✅ Sprint 18 Release v2026.07.03-01 已執行 |
| AI-302（Sprint 18）| Sprint N+1 計劃在 Sprint N 最後 2 天完成 | ✅ SPRINT_19_PLAN.md 於 Sprint 18 內建立（2026-06-24） |
| AI-303（Sprint 18）| ErrorCode Phase 3 排入 Sprint 19 | ✅ US-004 完成（掃描確認清零，無遷移需求） |
| AI-304（Sprint 18）| Payment catch 細分列 Sprint 19 P0 | ✅ US-001 完成（10 處全部細分） |

> **Sprint 18 Action Items 達成率**: 4/4（100%）✅

---

## 6. 團隊速度（Velocity）趨勢

| Sprint | 規劃 SP | 完成 SP | 達成率 |
|--------|---------|---------|--------|
| Sprint 16 | 14 SP | 14 SP | 100% |
| Sprint 17 | 12.5 SP | 12.5 SP | 100% |
| Sprint 18 | 12 SP（+7 Buffer）| 19 SP | 158% |
| **Sprint 19** | **13 SP（+6 Buffer）** | **13 SP** | **100%** |

> **觀察**: Sprint 19 Velocity 回歸到規劃 SP（無 Buffer 使用），主因是 US-004 實際無需遷移（3 SP 任務 0 行程式碼），節省的精力用於 US-005 的完整設計。
> **建議**: Sprint 20 考慮提高規劃 SP 至 16-18 SP，並降低 Buffer 至 4 SP。

---

## 7. 技術健康度指標

| 指標 | Sprint 18 末 | Sprint 19 末 | 趨勢 |
|------|-------------|-------------|------|
| 測試總數 | 555 | **564** | 🔼 +9 |
| 單元測試數 | 286 | **295** | 🔼 +9 |
| 整合測試數 | 269 | 269 | → 穩定 |
| 測試通過率 | 100% | **100%** | → 維持 |
| ErrorCode 濫用 (E_8000/E_5001) | ~0 | **0（確認）** | ✅ 清零確認 |
| `catch(Exception)` 過寬 (Payment) | 10 處 | **0 處** | 🔽 清零 |
| `throw RuntimeException` (生產碼) | 3 處 | **0 處** | 🔽 清零 |
| 自定義 ErrorCode 數量 | 74 | **76** | 🔼 +2 |
| Stripe Webhook 安全驗證 | ❌ | **✅** | ✅ 新建立 |
| @Deprecated 數量 | 11 | 11 | → 待清理 |
| MQ catch(Exception) 過寬 | 7 處 | 7 處 | → 待清理（AI-403） |
| Checkstyle 違規 | 0 | **0** | → 維持 |
| PMD 違規 | 0 | **0** | → 維持 |

---

## 8. 下一個 Sprint 展望

### Sprint 20 建議目標

> **建議目標**: 清理 MQ 技術債（P0），修復 AdminControllerE2ETest 間歇性失敗（P0），推進 M09 通知歷史查詢或 M10 新模組，同時考慮 Stripe SDK 整合。

### 預期 Sprint 20 內容

| 類別 | User Story | SP | 優先級 |
|------|-----------|-----|--------|
| 技術債 P0 | MQ `catch(Exception)` 細分（7 處） | 1.5 | P0 |
| 測試穩定性 | AdminControllerE2ETest 間歇性失敗修復 | 1 | P0 |
| 技術債 P1 | `@Deprecated` 清理（SettlementService 等） | 1 | P1 |
| 新功能 | M09 通知歷史查詢 API | 2-3 | P1 |
| 新功能 | M08 評分統計端點（平均/分布） | 2 | P1 |
| 新功能/依賴 | Stripe SDK 整合（Phase 3） | 2 | P2 |
| **小計** | | **~10-11 SP** | |
| Buffer | 預留 4 SP（20%） | 4 | - |
| **總計** | | **~14-15 SP** | |

---

## 9. 結語

Sprint 19 達成了所有規劃目標，技術基礎持續強化：Payment 模組技術債清零，Stripe Webhook 安全驗證正式上線，M09 通知偏好設定 API 完整交付。最值得注意的是 US-004 的「掃描確認清零」——通過實際數據驗證（而非假設），避免了 3 SP 的無效遷移工作。

**核心成就**：P0 技術債全部消除（Payment catch + RuntimeException），安全性強化（Stripe Webhook），新功能交付（M09 偏好設定），測試數量成長至 564 tests（100% 通過）。

Sprint 20 的焦點應鎖定 MQ 技術債（AI-403）、測試穩定性（AI-402），並逐步引入 Stripe SDK 依賴規範化（Phase 3）。

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-25
**建立者**: PM/PO Victoria + SA Amanda + Dev David + QA Quincy
