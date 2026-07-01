# Release Notes - v2027.01.02-01 (Sprint 31)

**發布日期**: 2027-01-02（規劃）／實作完成 2026-07-01
**發布類型**: Minor（買家閉環後端驗證 + 品質/後端補強）
**Sprint**: Sprint 31
**狀態**: ⏳ 待 push（累積 Sprint 29+30+31，檢查點徵詢後跑完整守門）

> Sprint 31 主題：買家閉環後端驗證（AI-1501）+ 後端小補強（roomTitle）+ DEF-016 audit 持久化

---

## 新功能 ✨

- **Admin 操作稽核持久化（US-003，DEF-016）**：新增 `AuditLog` entity + `V57` migration，`AdminService` 6 個關鍵操作（租戶審核/駁回/狀態更新、用戶狀態、feature toggle 設定/更新）改為寫入 `audit_log` 表（與既有 log.info 並存），可查詢稽核歷史。

## 改進 🚀

- **BookingListResponse roomTitle 填充（US-001，AI-1502）**：修復 S30 發現的後端缺口 —— 「我的預訂」列表原以「訂房 #id」降級，改為批次查詢 listing 標題（避免 N+1）填入 roomTitle。

## 品質 🛡️

- **買家閉環後端 E2E（US-002，AI-1501）**：新增 `BuyerOrderJourneyE2ETest`（3 測試，對真實 PostgreSQL）——建立 ROOM 訂單→Mock 付款→付款狀態→買家列表→取消擁有權隔離。
- `@Test` 683→**689**（roomTitle 2 + BuyerJourney 3 + audit 1）。

## 安全（調查發現）🔒

- **getOrder IDOR（→ DEF-018）**：US-002 補測試揪出 `OrderService.getOrder` 無擁有權檢查（僅需 `order:read`、無 per-user/tenant 過濾、無全域 tenant filter）→ 任何登入者可取任意訂單。修法涉廣用方法（比照 DEF-017/ERP 教訓恐打破多個 E2E），本版**未含程式修復**，記 [DEF-018](../04_planning/DEFERRED_ITEMS_TRACKER.md)，待可跑完整守門時處理（Sprint 32 AI-1601）。

## 資料庫遷移 🗄️

- **V57__Create_AuditLog_Table.sql**：建立 `audit_log` 表（DEF-016）。`make validate-schema` 通過（entity↔migration 無漂移）。

## 重大變更 ⚠️

- 無破壞性 API 變更。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| **DEF-018 getOrder IDOR（安全）** | Sprint 32（AI-1601，需完整守門修） |
| 買家閉環前端瀏覽器 E2E 尚待手動驗證 | Sprint 32（AI-1602） |
| DEF-017 ERP 租戶隔離 | Sprint 32+（AI-1603，需 M16 測試資料重做） |

## 驗證狀態 ✅

- 後端 `@Test` 靜態計數：**689**（+6）
- catch(Exception) 生產 = 0（recordAudit 用 catch(RuntimeException)）、@Deprecated 生產 = 0
- Checkstyle 0 violations；make validate-schema 通過（無漂移）
- AdminServiceIntegrationTest 5/5、BuyerOrderJourneyE2ETest 3/3、BookingServiceRoomTitleTest 2/2 本地通過
- 活躍 DEF：2（DEF-017 ERP / DEF-018 IDOR；DEF-016 已清償）
- pre-push v5 完整守門（act + schema + e2e）—— 隨 S29+30+31 批次 push 執行

## 內含 Commit（Sprint 31）

| US / 項目 | Commit |
|----------|--------|
| Sprint 31 Plan | `bcc59de` |
| US-001 roomTitle 填充（AI-1502） | `9763147` |
| US-002 買家閉環 E2E + DEF-018 發現 | `02baed5` |
| US-003 DEF-016 audit log 持久化 | `19be99f` |

> 本 Release 與 Sprint 29（v2026.12.05-01）、Sprint 30（v2026.12.19-01）一同 push（累積批次，完整守門一次驗證）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
