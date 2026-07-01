# Sprint 31 Retrospective 報告 / Sprint 31 Retrospective Report

> **Sprint 編號**: Sprint 31
> **期間**: 2026-12-20 ~ 2027-01-02 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-07-01
> **基於**: [SPRINT_31_PLAN.md](../04_planning/SPRINT_31_PLAN.md), [SPRINT_31_REVIEW.md](./SPRINT_31_REVIEW.md), [SPRINT_30_RETRO.md](./SPRINT_30_RETRO.md)

---

## 1. Sprint 31 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | 買家閉環後端驗證 + 品質/後端小補強 |
| **規劃 SP（承諾）** | 5 SP（P1）+ 3 SP（Buffer） |
| **完成 SP** | 8 SP（US-001~003 全完成） |
| **測試結果** | `@Test` 683→689（+6）；catch(Exception)=0、@Deprecated=0；validate-schema 無漂移 |
| **團隊** | 2 人 Dev Team + AISDLC Agents |
| **重大事件** | 首個含後端/schema 變更的累積期 Sprint；補測試揪出 DEF-018（IDOR）；清償 DEF-016 |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **後端 Sprint 於累積期安全推進** | 逐項本地整合測試 + validate-schema，未 push 即嚴格把關 | 證明累積期也能安全做後端變更 |
| 🔴 **補測試揪出 getOrder IDOR** | US-002 買家 E2E 揪出 getOrder 無擁有權檢查 → DEF-018 | 延續 DEF-013/017「補測試揪 bug」模式 |
| ✅ **DEF-016 清償（含 schema）** | AuditLog entity + V57 + AdminService 寫入，validate-schema 無漂移 | 首次於累積期完成 migration，schema 守門奏效 |
| ✅ **誠實不硬修高風險項** | DEF-018 修法涉廣用方法，比照 ERP 教訓延後至完整守門 | Rule 3/12 |

---

## 2. 做得好的地方（What Went Well）

| 項目 | 說明 | 證據 |
|------|------|------|
| **累積期後端風險控管** | 每個後端變更本地跑整合測試；schema 變更跑 validate-schema | AdminServiceIntegrationTest 5/5、validate-schema 無漂移 |
| **補測試揪安全隙** | US-002 E2E 揪出 getOrder IDOR（DEF-018） | 延續 DEF-013/017 價值 |
| **schema 守門先行** | 記取「本地 act 抓不到 schema-validation」教訓，主動跑 validate-schema | entity↔V57 一致確認 |
| **非侵入式 audit 接入** | 僅新增 recordAudit（保留 log.info、catch 不中斷主流程），不改既有邏輯 | 避免 ERP 式回歸 |
| **N+1 意識** | roomTitle 用批次 findAllById 而非逐筆 | AC-001-2 |
| **誠實延後高風險** | DEF-018 不在累積期硬修 | Rule 12 |

---

## 3. 需要改善的地方（What Could Be Improved）

| 問題 | 根本原因 | 影響程度 | 處置 |
|------|----------|---------|------|
| **🟡 getOrder IDOR（DEF-018）** | getOrder 無擁有權檢查，僅靠 order:read 權限 | 🟡 中（安全） | 完整守門時修（盤點呼叫者 + 補測試），AI-1601 |
| **🟡 買家閉環真實前端 E2E 仍缺** | 後端 E2E 已補（US-002），但前端瀏覽器 E2E 需 live 環境 | 🟡 中 | 需 live 環境手動走查（AI-1602，延續 AI-1501） |
| **🟡 累積批次已達 15 commit 未 push** | 使用者選擇連續累積 S29+30+31 | 🟡 中 | 本 Sprint 後 push（完整守門一次驗證整批） |
| **🟢 DEF-017 ERP 仍未處理** | 需 M16 測試資料重做，風險高 | 🟢 低 | 順延（AI-1603） |
| **🟢 audit 僅覆蓋 AdminService** | 本 Sprint 聚焦管理操作 | 🟢 低 | 其他需 audit 操作後續評估 |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1601 | **DEF-018 getOrder 擁有權修復** | 盤點 getOrder 呼叫者（買家/賣家/admin/payment）；設計擁有權檢查 + 補測試；於可跑完整守門時處理 | Dev David + SD Marcus | **P1（安全）** | Sprint 32 |
| AI-1602 | **買家閉環前端 E2E 驗證** | live 環境手動走完整買家旅程（下單→付款→通知→物流→評價） | QA Quincy | P1 | Sprint 32 |
| AI-1603 | DEF-017 ERP 租戶隔離 | 需 M16 測試資料重做，風險高 | Dev David | P2 | Sprint 32+ |
| AI-1604 | audit 覆蓋擴展評估 | 評估其他需稽核的操作（訂單/金流/ERP） | SD Marcus | P3 | 後續 |
| AI-1605 | 守門腳本最小回歸 | 順延自 AI-1505（連五 Sprint 未動） | Dev David | P3（Buffer） | Sprint 32 |

---

## 5. Sprint 30 → Sprint 31 Action Items 追蹤結果

| Action Item | 內容 | Sprint 31 達成狀態 |
|-------------|------|------------------|
| AI-1501 | 買家閉環手動 E2E 驗證 | 🔶 部分（US-002 補後端自動化 E2E；前端瀏覽器 E2E 順延 AI-1602） |
| AI-1502 | 後端小缺口修補（roomTitle） | ✅ 完成（US-001） |
| AI-1503 | DEF-016/017 排程 | 🔶 DEF-016 完成（US-003）；DEF-017 順延 AI-1603 |
| AI-1504 | 買家商品瀏覽頁評估 | ⏸️ 未啟動（順延，非本 Sprint 重點） |
| AI-1505 | 守門腳本最小回歸 | ⏸️ 未啟動 → 順延 AI-1605 |

**Action Items 完成率**: 2/5 完成 + 2 部分（後端補強類完成；前端驗證/長尾類順延）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 27 | 5 | 5 | 100% |
| Sprint 28 | 7+1 | 8 | 100%+ |
| Sprint 29 | 8 | 8 | 100% |
| Sprint 30 | 8 | 8 | 100% |
| Sprint 31 | 5+3 | **8** | **100%（含 Buffer）** |

> **Sprint 31 觀察**: 連四 Sprint 8 SP。首個含後端/schema 的累積期 Sprint，靠本地整合測試 + validate-schema 安全推進。

---

## 7. 技術債趨勢

| 指標 | Sprint 30 後 | Sprint 31 後 |
|------|------------|------------|
| `catch(Exception)` 生產 | 0 ✅ | **0** ✅ |
| `@Deprecated` 生產 | 0 ✅ | **0** ✅ |
| `@Test` 方法數 | 683 | **689** |
| Flyway 版本 | V56 | **V57**（audit_log） |
| 活躍 DEF | 2（DEF-016/017） | **2**（DEF-017 / DEF-018；DEF-016 清償） |
| audit 持久化 | ❌ 僅 log.info | ✅ AuditLog 持久化（Admin 操作） |

---

## 8. 下一步方向（Sprint 32 預覽）

> **Sprint 32（規劃中）**: **安全修復 + 買家閉環驗證** —— AI-1601 修 getOrder IDOR（DEF-018，P1 安全，需完整守門）；AI-1602 買家閉環前端手動 E2E；擇機 DEF-017 ERP。重心：把累積的安全發現（DEF-017/018）在可跑完整守門的節奏下逐一清償。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
