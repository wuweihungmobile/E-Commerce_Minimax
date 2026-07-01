# Sprint 31 Review / Sprint 31 評審會議

> **Sprint 編號**: Sprint 31
> **期間**: 2026-12-20 ~ 2027-01-02
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_31_PLAN.md](../04_planning/SPRINT_31_PLAN.md), [SPRINT_30_RETRO.md](./SPRINT_30_RETRO.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 買家閉環後端驗證 + 品質/後端小補強

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| roomTitle 填充（US-001, AI-1502, P1） | ✅ 達成 | BookingListResponse 填 roomTitle（批次查詢避免 N+1）+ 2 單元測試 |
| 買家閉環後端 E2E（US-002, AI-1501, P1） | ✅ 達成 | BuyerOrderJourneyE2ETest（3 測試）+ 揪出 getOrder IDOR（DEF-018） |
| DEF-016 audit log 持久化（US-003, Buffer） | ✅ 達成 | AuditLog entity + V57 + AdminService 寫入 + 測試；validate-schema 通過 |

**Sprint 目標達成率**: 100%（P1 ×2 + Buffer ×1 全完成）；本 Sprint 含後端變更，逐項本地驗證通過。

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | roomTitle 填充（AI-1502） | 2 | P1 | ✅ 完成 | `9763147` |
| US-002 | 買家閉環 E2E（AI-1501）+ DEF-018 發現 | 3 | P1 | ✅ 完成 | `02baed5` |
| US-003 | DEF-016 audit log 持久化（Buffer） | 3 | P2 | ✅ 完成 | `19be99f` |
| **完成合計** | | **8 SP** | | ✅ 100% | |

> Sprint 31 Plan commit：`bcc59de`。

---

## 3. 交付內容

### US-001：BookingListResponse roomTitle 填充（AI-1502）
- `BookingService.getUserBookings` 批次查 listing 標題（`findAllById`，避免 N+1）→ 映射 roomTitle（缺 listing 退回 "Unknown"）。
- `toBookingListResponse` 加 roomTitle 參數。
- `BookingServiceRoomTitleTest`（2 測試：正常填充 / 缺 listing 退回 Unknown）。
- 修復 S30 前端「訂房 #id」降級的後端根因。

### US-002：買家閉環後端 E2E（AI-1501 自動化）
- `BuyerOrderJourneyE2ETest`（3 測試，對真實 PostgreSQL）：建立 ROOM 訂單→Mock 付款（CREATED→PAID）→付款狀態查詢；已付款訂單在買家列表；取消擁有權隔離（行為驗證）。
- 🔴 **補測試揪出安全隙**：`getOrder` 無擁有權檢查（僅需 `order:read`、無 per-user/tenant 過濾、無全域 tenant filter）→ 任何登入者可取任意訂單 → 記 **DEF-018**（IDOR）。修法涉廣用方法（比照 DEF-017/ERP 教訓恐打破多個 E2E），累積期不硬修，待完整守門處理。

### US-003：DEF-016 Admin audit log 持久化
- `AuditLog` entity + `AuditLogRepository`（分頁 findByTenantId）+ `V57__Create_AuditLog_Table.sql`。
- `AdminService` 注入 repository + `recordAudit` helper，於 6 個關鍵操作寫入（租戶審核/駁回/狀態更新、用戶狀態、feature toggle 設定/更新），與既有 log.info 並存；稽核失敗 `catch(RuntimeException)` 不中斷主流程。
- `AdminServiceIntegrationTest` 補 DEF-016 稽核持久化測試。
- **make validate-schema 通過**（entity↔migration 無漂移，schema 守門）。

---

## 4. 測試狀態

| 測試類型 | Sprint 30 後 | Sprint 31 後 | 變化 |
|---------|------------|------------|------|
| `@Test`（後端靜態計數） | 683 | **689** | +6（roomTitle 2 + BuyerJourney 3 + audit 1） |
| 前端 lint / build | 通過 | 通過（本 Sprint 無前端變更） | — |
| catch(Exception) 生產 | 0 | **0** | recordAudit 用 catch(RuntimeException) |
| @Deprecated 生產 | 0 | **0** | — |
| Flyway | V56 | **V57**（audit_log） | 新 migration |
| 活躍 DEF | 2（DEF-016/017） | **2**（DEF-017 ERP / DEF-018 IDOR；DEF-016 已清償） | US-002 揪出 DEF-018、US-003 清 DEF-016 |

---

## 5. Definition of Done 驗核

- [x] US-001~002（P1）所有 AC 達成；US-003（Buffer）完成
- [x] `mvn compile` 0 errors；Checkstyle 0 violations
- [x] `@Test` 靜態計數淨增（683→689）；既有測試無退步（AdminServiceIntegrationTest 5/5、BuyerJourney 3/3、roomTitle 2/2）
- [x] catch(Exception) 生產 0、@Deprecated 生產 0
- [x] 後端變更本地跑相關整合測試；**US-003 schema 變更跑 make validate-schema（無漂移）**
- [ ] pre-push v5 完整守門（make validate-release）—— 隨 S29+30+31 批次 push 執行
- [x] Sprint 31 Review / Retrospective / Release Notes 建立

> **誠實備註**：本 Sprint 含後端/schema 變更，於累積期以本地整合測試 + validate-schema 逐項把關；完整守門（act + schema + e2e）於 push 時最終驗證整批（S29~S31）。getOrder IDOR（DEF-018）為誠實記錄的新發現，未於累積期硬修（避免 ERP 式回歸）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
