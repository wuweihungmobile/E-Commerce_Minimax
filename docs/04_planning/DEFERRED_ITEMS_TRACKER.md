# Deferred Items Tracker / 延後項目追蹤器

> **用途**: 追蹤所有被延後到未來 Sprint 的工作項目
> **更新原則**: 每個 Sprint Review 後必須更新此文件
> **審查時機**: Sprint Planning 前必須先閱讀此文件

---

## 活躍延後項目 / Active Deferred Items

### 🔴 高優先級 - 下一 Sprint 應優先處理

*目前無高優先級延後項目。*

---

### 🟡 中優先級 - 未來 Sprint 處理

*目前無中優先級延後項目（DEF-013、DEF-015 已於 Sprint 27 完成 → 活躍 DEF 歸零）。*

---

## 已完成延後項目 / Completed Deferred Items

| ID | 標題 | 移出 Sprint | 完成 Sprint | 備註 |
|----|------|-------------|-------------|------|
| DEF-001 | Booking E2E 完整預訂流程測試 | Sprint 5 | Sprint 6 | 已實作統一端點 + Feature Toggle + 測試資料，E2E 測試通過 |
| DEF-004 | listings.tags 欄位類型修復 | Sprint 7 | Sprint 10 | V8__Fix_Listings_Tags_Column_Type.sql migration 已建立，測試通過 (6/6) |
| DEF-005 | M10 IM SA 需求分析 | Sprint 21 Buffer-B | Sprint 22 | M10_IM_REQUIREMENTS.md 建立，PM/PO Victoria APPROVED |
| DEF-006 | M11 Provider Stub 強化 | Sprint 21 Buffer-C | Sprint 22 | HCT/TCAT 追蹤號改為 {Provider}-{yyyyMMdd}-{HEX8} 格式 |
| DEF-007 | M11 物流與訂單履約流程整合 | Sprint 21 | Sprint 23 | createLogistics 前置驗證 + 訂單狀態同步 SHIPPING/DELIVERED，4 個整合測試通過 |
| DEF-008 | ShippingTemplate 接入訂單結帳流程 | Sprint 21 | Sprint 23 | V47 Migration + shippingFee 欄位 + 免運門檻邏輯，3 個整合測試通過 |
| DEF-009 | Logistics.logisticsData jsonb 映射慣例統一 | Sprint 25 | Sprint 26 | US-006：統一為 Map + @JdbcTypeCode(SqlTypes.JSON)，validate-schema 通過 |
| DEF-010 | OrderStateMachine 一致性清理（SHIPPING→CANCELLED） | Sprint 25 | Sprint 26 | US-004：移除 SHIPPING→CANCELLED + 轉換表↔canCancel 一致性不變量測試 |
| DEF-011 | cancelLogistics 錯誤碼修正（→ E_7500 系列） | Sprint 25 | Sprint 26 | US-004：改 E_7500（not found）/E_7502（delivered）+ 3 單元測試 |
| DEF-012 | ChatService.toMessageResponse 廣播 conversationId 補正 | Sprint 25 | Sprint 26 | US-003：改由 conversation 關聯取 id + 廣播 payload 測試 |
| DEF-014 | e2e 乾淨 DB 註冊回 401 致 10 spec 失敗 | Sprint 26 | Sprint 26 | 真因為 validate-e2e.sh 誤設 NEXT_PUBLIC_API_URL（雙 /v2），非產品 bug；修正後 27 passed/5 skip/0 fail，e2e 改 strict 預設 |
| DEF-013 | M09 MQ 通知缺端到端驗證 | Sprint 26 | Sprint 27 | US-003：補 produce→佇列→consume 端到端測試（NotificationProduceConsumeTest，真實 ObjectMapper + 共用佇列）。**揪出並修復真 bug**：producer 用 Stream(XADD)、consumer 用 List(RPOP) 同 key 型別不相容 → 通知永不被消費；改為兩端一致 List（leftPush/rightPop） |
| DEF-015 | 前端 next/font/google 建置期外部抓取 | Sprint 26 | Sprint 27 | US-002：layout.tsx 的 Geist 變數從未被 CSS/Tailwind 消費（死碼），移除 next/font/google import → 離線 build 不再抓 Google，零視覺影響 |

---

## Sprint 歷史紀錄

### Sprint 26 (2026-07-01)

**新增延後**:
- DEF-013（🟡 低）: M09 MQ 通知缺端到端驗證 — US-002 backend-only 盤點發現，套用 REALTIME_ASYNC_E2E_DOD（延 Sprint 27 US-003）
- DEF-015（🟡 低）: 前端 next/font/google 建置期外部抓取 — DEF-014 驗證時發現，離線 build 失敗（延 Sprint 27 US-002）

**移除延後（已完成）**:
- DEF-009 ✅ Sprint 26 US-006（Logistics jsonb 統一 Map + @JdbcTypeCode）
- DEF-010 ✅ Sprint 26 US-004（移除 SHIPPING→CANCELLED + 一致性不變量）
- DEF-011 ✅ Sprint 26 US-004（cancelLogistics 錯誤碼 E_7500 系列）
- DEF-012 ✅ Sprint 26 US-003（廣播 conversationId 改由 conversation 取得）
- DEF-014 ✅ Sprint 26（validate-e2e.sh API_URL 修正，e2e strict）

**更新**:
- Sprint 26 承諾 7 SP + Buffer 2 SP = 9 SP 全完成（US-001~006）
- 計畫外重大工作：本地優先 CI 整套（停用雲端自動 CI、validate-e2e/release、pre-push v4→v5、push 降頻）
- `@Test` 靜態 668（+9）、catch(Exception)=0、@Deprecated=0、Flyway V56（無新 migration）
- **活躍 DEF 降至 2 個低優先**（DEF-013/015），技術債近清零、backlog 見底
- **新增 Action Items（Sprint 27）**：AI-1101 產品方向決策（P1，需人工）、AI-1102 DEF-015、AI-1103 DEF-013、AI-1104 pre-push v5 實測、AI-1105 守門腳本回歸

### Sprint 25 (2026-06-29，進行中)

**新增延後**:
- DEF-009（🟡 中優先，實際低急迫）: Logistics.logisticsData jsonb 映射慣例統一 — US-002 全庫盤點發現的唯一慣例不一致，列為技術債，不在本 US 動工

**移除延後（已完成）**:
- （無）

**更新**:
- US-001（P0，AI-901）✅ 完成：建立 `make validate-schema` schema 漂移守門關卡，雙向驗證（正向 exit 0 / 負向 exit 1 攔下 missing column）
- US-002（P1，AI-902）✅ 完成：[ENTITY_MIGRATION_AUDIT.md](../06_quality/ENTITY_MIGRATION_AUDIT.md) — 51 entity 全數通過 validate，零孤兒表、零 `SqlTypes.ARRAY` 殘留、3 個歷史 `TEXT[]` 全部封閉；固化防漂移慣例

### Sprint 24 (2026-06-29)

**新增延後**:
- （無正式 DEF 項目）Buffer US-004（SSH pre-push 優化）+ US-005（M11 物流取消流程）未啟動，改以 Retro Action Items 追蹤（AI-804 / AI-903，延續 Sprint 25）

**移除延後（已完成）**:
- （無，本 Sprint 無活躍 DEF 項目）

**更新**:
- Sprint 24 承諾範圍 100% 完成：US-001~003（7 SP，P0+P1）
- AI-801（TestSecurityContextHelper）+ AI-803（M13 Redis TTL）完成；AI-802 / AI-804 延續 Sprint 25
- **計畫外重大事件**：GitHub E2E 暴露 backend 啟動失敗，投入 9 個 commit 修復 schema 漂移（Flyway V48~V55：補齊 5 張缺漏建表 + 統一 ARRAY→jsonb）
- **根因**：本地 act 用 `ddl-auto=update`，GitHub E2E 用 schema 驗證 → entity/migration 漂移本地偵測不到（已記入 Retro AI-901 P0）
- Buffer 容量（3 SP）被 E2E 救火完全佔用，連續 3 Sprint 以來首次 Buffer 0% 啟動
- Sprint 24 v2026.09.26-01 發布
- **新增 Action Items（Sprint 25）**：AI-901 本地 schema 驗證關卡（P0）、AI-902 entity↔migration 一致性盤點（P1）、AI-903 M11 取消流程業務規則確認

---

### Sprint 23 (2026-06-27)

**新增延後**:
- （無新增延後項目）

**移除延後（已完成）**:
- DEF-007: M11 物流與訂單履約整合 ✅ Sprint 23 US-004 完成（createLogistics + 狀態同步，4 個整合測試）
- DEF-008: ShippingTemplate 接入結帳流程 ✅ Sprint 23 US-005 Buffer-A 完成（V47 + shippingFee + 免運邏輯，3 個整合測試）

**更新**:
- Sprint 23 完成，6/6 US 全數達成（含 Buffer-A + Buffer-B，100% Buffer 利用率）
- Sprint 23 Integration Tests: ~317 tests, 0 Failures（新增 +15）
- Sprint 23 Unit Tests: ~326（新增 +3）
- act CI（make validate-all）整體通過（2026-06-27 19:48:47）
- Sprint 23 v2026.09.12-01 發布
- Sprint 24 開始規劃（TestSecurityContextHelper + Redis Cache TTL + M10 WebSocket 評估）
- **DEF 清零**: 所有活躍延後項目（DEF-007/008）全數完成，無新增 DEF

---

### Sprint 22 (2026-06-27)

**新增延後**:
- DEF-007（升級為 🔴 高優先級）: M11 物流與訂單整合 → Sprint 23 P1（AI-704 延續 AI-603）
- DEF-008: ShippingTemplate 接入結帳流程 → Sprint 23 Buffer

**移除延後（已完成）**:
- DEF-005: M10 IM SA 需求分析 ✅ Sprint 22 US-004 完成，Victoria APPROVED
- DEF-006: M11 Provider Stub 強化 ✅ Sprint 22 US-005 完成

**更新**:
- Sprint 22 完成，5/5 US 全數達成（含 Buffer-A + Buffer-B，100% Buffer 利用率）
- Sprint 22 Integration Tests: ~302 tests, 0 Failures（新增 +9）
- Sprint 22 Unit Tests: ~323（新增 +3）
- Sprint 22 v2026.08.29-01 發布
- Sprint 23 開始規劃（M10 IM 後端 REST + M11 訂單整合）

---

### Sprint 21 (2026-06-27)

**新增延後**:
- DEF-005: M10 IM SA 需求分析（Buffer-B 連續兩次延後，Sprint 22 必須執行）
- DEF-006: M11 Provider Stub 強化（Buffer-C 未啟動）
- DEF-007: M11 物流與訂單整合（Sprint 21 未規劃，Sprint 23+）
- DEF-008: ShippingTemplate 接入結帳流程（Sprint 21 未規劃，Sprint 23+）

**移除延後**:
- (無)

**更新**:
- Sprint 21 完成，6/6 US 全數達成（含 Buffer-A US-006）
- Sprint 21 Integration Tests: 293 tests, 0 Failures
- Sprint 21 v2026.08.15-01 已發布
- Sprint 22 開始規劃（M12 動態定價 + M13 商家工作台）

---

### Sprint 12 (2026-06-15)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- Sprint 12 完成，M18 知識管理 Phase 2-A + M07 Payment Mock + M09 通知模板已交付
- Sprint 12 v12.0.0 已發布 (release/v2026.06.15-01)
- Sprint 13 開始規劃 (M18 Phase 2-B + M08 評價系統)

### Sprint 11 (2026-06-01)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- Sprint 11 完成，M04 購物車 + M06 預訂完整化已交付
- Sprint 11 QA 驗證完成 (2026-05-14)
- 374 tests PASS
- CI Pipeline 因 GitHub 帳單額度問題等待 2026-06-01 恢復

### Sprint 10 (2026-05-18)

**新增延後**:
- (無)

**移除延後**:
- DEF-004: listings.tags 欄位類型修復 ✅ 已完成 (V8__Fix_Listings_Tags_Column_Type.sql)

**更新**:
- Sprint 10 完成，M16 ERP 進銷存模組已交付
- Sprint 10 進入發布評審階段 (2026-05-13)
- CI Pipeline 因 GitHub 帳單額度問題等待 2026-06-01 恢復

### Sprint 9 (2026-05-06)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- DEF-001: Sprint 6 完成，移至已完成延後項目

---

## 使用說明

### 添加新延後項目

1. 在「活躍延後項目」區塊新增列
2. 填寫所有欄位（ID、標題、原始 Sprint、延後原因、前置需求、預估 SP）
3. 在「Sprint 歷史紀錄」區塊新增 entry
4. 狀態標記為 ⚠️ 待處理

### 完成延後項目

1. 將項目從「活躍延後項目」移到「已完成延後項目」
2. 填寫完成 Sprint
3. 狀態改為 ✅ 已完成

### Sprint Planning 前檢查清單

- [ ] 閱讀本文件
- [ ] 確認所有 ⚠️ 待處理 項目是否已具備執行條件
- [ ] 將具備條件的項目納入 Sprint Plan
- [ ] 更新本文件的狀態欄位

---

**文件版本**: v2.1
**最後更新**: 2026-07-01（Sprint 27 US-002/003：DEF-013、DEF-015 完成 → **活躍 DEF 歸零**；DEF-013 揪出並修復 producer/consumer Redis 型別不相容真 bug）
**下次審查**: Sprint 27 Review
