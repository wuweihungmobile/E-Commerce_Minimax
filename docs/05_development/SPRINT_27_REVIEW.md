# Sprint 27 Review / Sprint 27 評審會議

> **Sprint 編號**: Sprint 27
> **期間**: 2026-10-25 ~ 2026-11-07
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_27_PLAN.md](../04_planning/SPRINT_27_PLAN.md), [SPRINT_26_RETRO.md](./SPRINT_26_RETRO.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 產品方向決策 + 殘餘技術債清零 + 本地守門收尾驗證（輕量收尾型）

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| 產品方向決策（AI-1101, P1，需人工） | ✅ 達成 | PM/PO 拍板方向 (b)；產出 PRODUCT_BACKLOG（RICE + EPIC-BUYER）+ Sprint 28 計畫 |
| 前端字型本地化（DEF-015） | ✅ 達成 | Geist 為死碼，移除 next/font/google → 離線 build 通過、零視覺影響 |
| M09 MQ 通知端到端（DEF-013） | ✅ 達成（+ 揪出真 bug） | 補 produce→consume 測試時發現 producer/consumer Redis 型別不相容，已修 |
| pre-push v5 完整守門實測（AI-1104） | ✅ 達成 | make validate-release 全綠（act+schema+e2e），FULL 快取放行實證 |
| 守門腳本最小回歸（AI-1105, Buffer） | ⏸️ 未啟動 | Buffer，順延（容量用於 DEF-013 真 bug 修復） |

**Sprint 目標達成率**: 100%（承諾 P1+P2 5 SP 全完成；Buffer US-005 未啟動）

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | 產品方向決策（AI-1101）🔴 人工 | 1 | **P1** | ✅ 完成 | `ec429cb` |
| US-002 | 前端字型本地化（DEF-015） | 1 | P1 | ✅ 完成 | `e697dbe` |
| US-003 | M09 MQ 通知端到端（DEF-013） | 2 | P2 | ✅ 完成（+真 bug 修復） | `e697dbe` |
| US-004 | pre-push v5 完整守門實測（AI-1104） | 1 | P2 | ✅ 完成 | （validate-release 全綠） |
| US-005 | 守門腳本最小回歸（Buffer） | 1 | Buffer | ⏸️ 未啟動 | — |
| **完成合計** | | **5 SP** | | ✅ 承諾 100% | |

---

## 3. 測試狀態

| 測試類型 | Sprint 26 後 | Sprint 27 後 | 變化 |
|---------|------------|------------|------|
| `@Test` 方法總數（靜態計數） | 668 | **670** | +2（NotificationProduceConsumeTest） |
| `catch (Exception)` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| `@Deprecated` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| 活躍 DEF | DEF-013/015（2） | **0** ✅ | 全清償 |
| pre-push v5 完整守門 | 未端到端實測 | **✅ 實測全綠**（act+schema+e2e 26 passed/6 skip/0 fail） | US-004 |

---

## 4. US-001：產品方向決策（AI-1101）

- PM/PO 拍板 **方向 (b) 強化既有模組**。
- 3 路並行盤點（後端成熟度 / 前端覆蓋 / PRD Phase 2）交叉比對 → [PRODUCT_BACKLOG.md](../04_planning/PRODUCT_BACKLOG.md)（RICE 排序）。
- **核心洞察**：後端 16/18 高成熟，但買家端前端（M05 訂單 / M07 付款 / M11 物流無 UI；M08 評價 / M09 通知收件匣薄）缺失 → **EPIC-BUYER 買家端閉環**。
- 二次拍板「先補品質再加功能」→ [SPRINT_28_PLAN.md](../04_planning/SPRINT_28_PLAN.md)（測試補強 + 後台深化）；EPIC-BUYER 自 Sprint 29 起。

---

## 5. US-003：M09 MQ 通知端到端（DEF-013）🔴 揪出真 bug

| 項目 | 內容 |
|------|------|
| 發現 | producer 用 `opsForStream().add()`（Stream）、consumer 用 `opsForList().rightPop()`（List）讀同一 key → Redis 型別不相容（WRONGTYPE）被吞 → `sendToQueue` 的通知永不被消費（正式路徑） |
| 潛伏原因 | 3 個既有測試（mock listOps / ReflectionTestUtils 直呼 / 整個 RedisTemplate mock）都繞過真實傳遞 —— 正是 REALTIME_ASYNC_E2E_DOD 預警 |
| 修復 | producer 改 `opsForList().leftPush()`（與 consumer 對齊，FIFO） |
| 測試 | 新增 `NotificationProduceConsumeTest`（真實 ObjectMapper + 共用佇列，2 測試），produce→佇列→consume→Notification+歷史 |

> **價值**：DEF-013 兌現了 Sprint 25/26 建立的「真實傳遞 E2E DoD」—— 一個低優先技術債的補測試任務，揪出了一個正式環境的通知斷鏈 bug。

---

## 6. US-004：pre-push v5 完整守門實測（AI-1104）

- `make validate-release` 首次端到端跑完並全綠：act backend+frontend ✅ + schema 漂移 ✅ + e2e **26 passed / 6 skip / 0 fail** ✅。
- push 時 pre-push 偵測 FULL 記錄 + tree-hash 相符 → **直接放行**（未重跑 30-45 分）。**v5「降頻 + FULL 快取」設計實證可用。**

---

## 7. Sprint 26 Action Items 追蹤

| AI ID | 內容 | 對應 US | 達成狀態 |
|-------|------|--------|---------|
| AI-1101 | 產品方向決策 | US-001 | ✅ 完成（方向 b + backlog） |
| AI-1102 | DEF-015 前端字型本地化 | US-002 | ✅ 完成 |
| AI-1103 | DEF-013 M09 MQ 通知端到端 | US-003 | ✅ 完成（+真 bug 修復） |
| AI-1104 | pre-push v5 完整守門實測 | US-004 | ✅ 完成 |
| AI-1105 | 本地守門腳本最小回歸 | US-005（Buffer） | ⏸️ 未啟動，順延 Sprint 28 |

**Action Items 完成率**: 4/5（承諾項 100%，Buffer 1 項順延）

---

## 8. Definition of Done 驗核

- [x] US-001~004 所有 AC 達成（P1+P2）
- [x] `mvn compile` → 0 errors；Checkstyle → 0 violations
- [x] 所有新增測試通過；既有測試無退步（`@Test` 670 ≥ 668）
- [x] catch(Exception) 生產 **0 處**、@Deprecated 生產 **0 處**
- [x] DEF-015 + DEF-013 完成 → **活躍 DEF 歸零**
- [x] pre-push v5 完整守門首次端到端綠燈（US-004）
- [x] Sprint 27 Review / Retrospective / Release Notes 建立（本文件 + RETRO + v2026.11.07-01）
- [ ] US-005 Buffer（守門腳本回歸）—— 順延 Sprint 28

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + Dev David + QA Quincy + Claude Code
