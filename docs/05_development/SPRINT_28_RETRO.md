# Sprint 28 Retrospective 報告 / Sprint 28 Retrospective Report

> **Sprint 編號**: Sprint 28
> **期間**: 2026-11-08 ~ 2026-11-21 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-07-01
> **基於**: [SPRINT_28_PLAN.md](../04_planning/SPRINT_28_PLAN.md), [SPRINT_28_REVIEW.md](./SPRINT_28_REVIEW.md), [SPRINT_27_RETRO.md](./SPRINT_27_RETRO.md)

---

## 1. Sprint 28 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | 品質硬化（補測試防護網）+ 營運後台深化，為 EPIC-BUYER 鋪路 |
| **規劃 SP（承諾）** | 7 SP（P0+P1+P2，US-001~003） |
| **完成 SP** | 8 SP（承諾 7 + Buffer 1，US-001~004 全完成） |
| **完成 US** | 3/3 承諾 + 1/1 Buffer |
| **測試結果** | `@Test` 683（+13）；catch(Exception)=0、@Deprecated=0 |
| **團隊** | 2 人 Dev Team + AISDLC Agents（PM/SA/SD/Dev/QA） |
| **重大事件** | US-004 Buffer 揪出 ERP 租戶隔離安全隙（記 DEF-017）；pre-push v5 完整守門攔下整合回歸 |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **品質先行落實** | 深化後台前先補 M14/M18 防護網（+13 測試） | 在有測試的基礎上加功能 |
| 🔴 **US-004 揪出租戶隔離安全隙** | ERP 手動庫存異動擁有權檢查為 no-op → 記 DEF-017（修法會打破 5 個耦合整合測試，誠實回退） | 補測試/深化揪 bug 模式再次見效（延續 DEF-013） |
| ✅ **完整守門攔下回歸** | ERP 修法打破 5 個 M16 整合測試，由 pre-push v5（validate-release）在 push 前攔下 | 驗證「上 GIT = 完整測試程序」的價值（pre-commit 抓不到整合回歸） |
| ✅ **買家/商家後台從佔位進化** | `/dashboard` Welcome → 營運總覽 + 營收趨勢 | 後端分析數據首次前端可視化 |
| ✅ **EPIC-BUYER 基礎就緒** | 品質 + 後台深化完成，S29 可安心啟動買家端閉環 | 依 PRODUCT_BACKLOG 推進 |

---

## 2. 做得好的地方（What Went Well）

| 項目 | 說明 | 證據 |
|------|------|------|
| **品質先行決策正確** | 先補 0 測試模組防護網，US-004 深化時立即受益（能安心改） | Analytics/FAQ +13 測試 |
| **補測試連帶揪 bug + 誠實回退** | US-004 清 placeholder 揪出租戶隔離 no-op；修法打破 5 整合測試即誠實回退記 DEF-017（非硬修） | Rule 12 大聲失敗 |
| **完整守門發揮作用** | 回歸未進 origin —— pre-push v5 validate-release 在 push 前攔下 | 守門設計驗證 |
| **Buffer 範圍紀律** | audit log 持久化改動面大，依 AC 記 DEF-016 而非硬塞 | Rule 2/3 |
| **前端遵守 Next 16 慣例** | async fetch、setState 皆 await 後、cleanup cancel flag、沿用既有 service 模式 | lint 0 errors |
| **設計誠實調整** | AnalyticsController 租戶範圍 → M13/M14 併為統一視圖，避免建重複 admin 面板 | 明載於 plan/review |

---

## 3. 需要改善的地方（What Could Be Improved）

| 問題 | 根本原因 | 影響程度 | 處置 |
|------|----------|---------|------|
| **🟡 租戶隔離 no-op 潛伏** | ERP placeholder（回傳 tenantId + 空 if）長期未被測試覆蓋 | 🟡 中（記 DEF-017） | 修法會打破 5 個耦合整合測試 → 需連測試資料重做，延後 DEF-017 |
| **🟡 改 service 未先跑整合測試** | pre-commit 僅核心單元測試；US-004 的 ERP 改動打破整合測試，validate-release 才抓到 | 🟡 中 | 改廣泛呼叫者 service 前先跑相關整合測試（AI-1303） |
| **🟢 M18 Knowledge 仍僅 1 測試** | 本 Sprint 聚焦真正 0 覆蓋的 Analytics/FAQ | 🟢 低 | 後續延展 |
| **🟢 AI-1201 守門腳本回歸連兩 Sprint 未動** | S27/S28 容量用於更高價值項 | 🟢 低 | 順延（AI-1301） |
| **Sprint 排期與真實開發脫節（第十一次）** | 文件排期與實際開發日差距 100+ 天 | 🟢 低 | 慢性問題，不影響交付 |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1301 | **EPIC-BUYER 啟動** | 依 PRODUCT_BACKLOG，S29 做 #1 訂單前端 + #2 通知收件匣 + #3 付款前端（買家閉環起手） | PM Victoria + Dev David | **P1** | Sprint 29 |
| AI-1302 | **DEF-016 Admin audit log 持久化** | 建 AuditLog entity + repository + migration，評估其他需 audit 操作 | Dev David | P2 | Sprint 29+ |
| AI-1305 | **DEF-017 ERP 租戶隔離修復** | 釐清 ERP inventory→sku→listing→tenant 關聯，設計正確擁有權檢查 + 補 M16 整合測試資料（連同一起修） | Dev David + SD Marcus | P2（安全） | Sprint 29+ |
| AI-1303 | **低覆蓋模組持續補測試 + 警覺安全/正確性** | 延續 DEF-013 / ERP 教訓：補測試時對租戶隔離、非同步傳遞保持警覺 | QA Quincy | P2 | 常態 |
| AI-1304 | 守門腳本最小回歸 | 順延自 AI-1201（連兩 Sprint 未動），S29 視容量處理 | Dev David | P3（Buffer） | Sprint 29 |

---

## 5. Sprint 27 → Sprint 28 Action Items 追蹤結果

| Action Item | 內容 | Sprint 28 達成狀態 |
|-------------|------|------------------|
| AI-1201 | 守門腳本最小回歸 | ⏸️ 未啟動 → 順延 AI-1304 |
| AI-1202 | 低覆蓋模組測試補強（M14/M18） | ✅ 完成（US-001） |
| AI-1203 | 持續落實真實傳遞 E2E DoD | ✅ 常態（US-004 亦揪出安全隙） |
| AI-1204 | EPIC-BUYER 排程（S29 起） | ✅ 排定（AI-1301） |

**Action Items 完成率**: 3/4（AI-1201 順延）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 24 | 7 | 7 | 100% |
| Sprint 25 | 9+2 | 11 | 100%+ |
| Sprint 26 | 7+2 | 9 | 100%+ |
| Sprint 27 | 5 | 5 | 100% |
| Sprint 28 | 7+1 | **8** | **100%+（承諾 + Buffer）** |

> **Sprint 28 觀察**: 回到正常節奏（8 SP）。品質先行策略讓 US-004 深化順利且揪出安全隙，證明「先補防護網」的價值。

---

## 7. 技術債趨勢

| 指標 | Sprint 27 後 | Sprint 28 後 |
|------|------------|------------|
| `catch(Exception)` 生產 | 0 ✅ | **0** ✅ |
| `@Deprecated` 生產 | 0 ✅ | **0** ✅ |
| `@Test` 方法數 | 670 | **683** |
| M14 Analytics / M18 FAQ 測試 | 0 / 0 | **7 / 6** |
| ERP 租戶隔離（手動庫存異動） | 🔴 no-op（未知） | 🟡 已辨識 → DEF-017（待連測試資料修） |
| 活躍 DEF | 0 | **2**（DEF-016 audit / DEF-017 ERP） |

---

## 8. 下一步方向（Sprint 29 預覽）

> **Sprint 29（規劃中）**: **EPIC-BUYER 買家端閉環起手** —— 依 PRODUCT_BACKLOG RICE Top3：#1 M05 訂單前端（列表/詳情/取消）+ #2 M09 通知收件匣 + #3 M07 付款前端（對 Mock）。讓買家「下單→付款→看通知→查訂單」first-class 可用。可選：DEF-016 audit log、AI-1304 守門腳本回歸。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
