# Sprint 27 計劃 / Sprint 27 Plan

> **Sprint 編號**: Sprint 27
> **期間**: 2026-10-25 ~ 2026-11-07 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_26_RETRO.md](../05_development/SPRINT_26_RETRO.md) + [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md)
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy（sprint-planning skill）

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 26 承諾 US 完成 | ✅ 4/4（7 SP）+ 2/2 Buffer（2 SP）= 9 SP | US-001~006 全達成 |
| Sprint 26 收尾 | ✅ Review/Retro/Release Notes 完成 | 本 Sprint 規劃即接續其後 |
| Sprint 26 commits | ✅ 已 push origin/main（本地優先驗證） | 雲端改手動觸發 |
| 活躍 DEF | **DEF-013、DEF-015（僅 2 個低優先）** | backlog 接近見底 |
| Sprint 26 Retro Action Items | AI-1101~1105 共 5 項 | 本 Sprint 主要來源 |
| 🔴 Product Backlog / Roadmap | 🔴 **無正式文件**；活躍 DEF 僅 2 個低優先 | **AI-1101：需 PM/PO 人工拍板產品方向** |
| 技術債指標 | catch(Exception)=0、@Deprecated=0、@Test=668 | 維持零技術債 |

> 🔴 **本 Sprint 為「輕量收尾型」**：技術債已近清零、無正式 backlog。最關鍵項是 **US-001 產品方向決策（需人工）**——它決定 Sprint 28+ 是否還有開發內容。

---

## 1. Sprint 27 目標

> **主題**: 產品方向決策 + 殘餘技術債清零 + 本地守門收尾驗證

Sprint 26 清償了 DEF-009~012/014 並完成本地優先 CI 整套工程，活躍 DEF 僅剩 2 個低優先、且無正式 product backlog。Sprint 27 三件事：

1. **產品方向決策**（AI-1101，**P1**）：PM/PO 拍板 Sprint 28+ 走向（新功能 epic / 既有模組強化 / 正式轉維護模式），必要時建立 backlog/roadmap
2. **殘餘技術債清零**（DEF-015/013）：前端字型本地化（離線 build 風險）+ M09 MQ 通知端到端驗證
3. **本地守門收尾驗證**（AI-1104/1105）：首次端到端實測 pre-push v5 完整守門 + 守門腳本最小回歸

---

## 2. Sprint 目標對齊

| 目標 | 對應 Action Item / DEF | 類型 |
|------|----------------------|------|
| 產品方向決策（決定 Sprint 28+ 內容） | AI-1101（P1） | 規劃 / 人工決策 |
| 前端字型本地化（DEF-015） | AI-1102（P1） | 技術債 |
| M09 MQ 通知端到端驗證（DEF-013） | AI-1103（P2） | 品質 |
| pre-push v5 完整守門端到端實測 | AI-1104（P2） | 流程 |
| 本地守門腳本最小回歸 | AI-1105（Buffer） | 流程 |

---

## 3. Deferred Items 審查

| 狀態 | 說明 |
|------|------|
| 高優先級 DEF | 無 |
| 中/低優先級 DEF | DEF-013（M09 MQ 通知 e2e）、DEF-015（前端字型建置期外部抓取） |
| 本 Sprint 處理 | DEF-015（US-002）、DEF-013（US-003）→ **處理後活躍 DEF 歸零** |

---

## 4. User Stories

### US-001：產品方向決策（AI-1101，P1）🔴 需人工拍板

> **SP**: 1 | **優先級**: P1 | **狀態**: 📋 規劃中

**目標**: backlog 接近見底（活躍 DEF 僅 2 個低優先、無正式 roadmap）。由 PM/PO 決定 Sprint 28+ 走向，避免後續 Sprint 無正式開發來源、淪為隨意挑任務。

**AC-001-1**: PM/PO（人工）在三個方向中拍板：(a) 新功能 epic（需求 → PRD/FRD）、(b) 既有模組強化（列出候選）、(c) 正式轉「維護/硬化模式」（降頻 Sprint、僅處理 incident + 技術債）
**AC-001-2**: 若選 (a)/(b)，建立 `PRODUCT_BACKLOG.md`（RICE 排序）作為後續 Sprint 來源
**AC-001-3**: 若選 (c)，文件化維護模式運作方式（觸發條件、Sprint 節奏）

> 🔴 **確認點**: 本 US 無法由 AI 獨立完成，須使用者以 PM/PO 身分拍板產品方向。

---

### US-002：前端字型本地化（AI-1102 / DEF-015，P1）

> **SP**: 1 | **優先級**: P1 | **狀態**: 📋 規劃中

**目標**: `app/layout.tsx` 使用 `next/font/google`（Geist / Geist Mono），build 期向 fonts.googleapis.com 抓取，離線/網路不穩時 `npm run build` 失敗 → `make validate-e2e` exit 2。改為無外部網路依賴。

**AC-002-1**: 改用 `next/font/local`（自帶字型檔）或移除外部字型，`npm run build` 不再有建置期外部抓取
**AC-002-2**: 離線環境 `make validate-e2e` build 階段穩定通過
**AC-002-3**: UI 外觀無回歸（字型 fallback 合理）

---

### US-003：M09 MQ 通知端到端驗證（AI-1103 / DEF-013，P2）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 規劃中

**目標**: 依 [REALTIME_ASYNC_E2E_DOD.md](../06_quality/REALTIME_ASYNC_E2E_DOD.md)，為 M09 MQ 通知（NotificationProducer → Consumer）補「觸發 → 消費 → 可觀測結果」端到端驗證，補上 backend-only 非同步功能的端到端防線。

**AC-003-1**: 設計可觀測的端到端驗證（觸發事件 → 通知落地/狀態可查）
**AC-003-2**: 測試涵蓋成功路徑；錯誤/重試路徑至少有一個案例
**AC-003-3**: 納入本地守門（整合測試或 e2e），符合 DoD

---

### US-004：pre-push v5 完整守門端到端實測（AI-1104，P2）

> **SP**: 1 | **優先級**: P2 | **狀態**: 📋 規劃中

**目標**: pre-push v5（上 GIT = `make validate-release`）至今的 push 多為純文件、走略過路徑，完整守門（act + schema + e2e，~30-45 分）尚未端到端實跑。本 US 在首次含 backend/frontend 變更的 push 時確認 v5 全流程綠燈。

**AC-004-1**: 一次真實的 backend 或 frontend 變更 push，觸發 `make validate-release` 完整跑完並綠燈
**AC-004-2**: 確認 FULL 記錄 + tree-hash 快取（手動 validate-release 後 push 直接放行）如預期運作
**AC-004-3**: 若發現 v5 流程缺陷，修正並更新 LOCAL_CI_VALIDATION.md

---

### US-005（Buffer）：本地守門腳本最小回歸（AI-1105）

> **SP**: 1 | **優先級**: Buffer | **狀態**: 📋 規劃中

**目標**: DEF-014（validate-e2e.sh 的 API_URL 雙 /v2 bug）暴露守門腳本自身無回歸測試。為關鍵前置加最小自我檢查。

**AC-005-1**: 為 validate-*.sh 關鍵組裝（如 API_URL、port 檢查）加最小斷言/自我檢查
**AC-005-2**: 不擴大改動面（Rule 2/3），僅補防呆

---

## 5. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 產品方向決策（AI-1101）🔴 人工 | 1 | **P1** |
| US-002 | 前端字型本地化（DEF-015） | 1 | P1 |
| US-003 | M09 MQ 通知端到端驗證（DEF-013） | 2 | P2 |
| US-004 | pre-push v5 完整守門實測（AI-1104） | 1 | P2 |
| US-005 | 守門腳本最小回歸（Buffer） | 1 | Buffer |
| **P1+P2 合計** | | **5 SP** | |
| **含 Buffer 合計** | | **6 SP** | |

> Velocity 對齊：S22=8 / S23=12 / S24=7 / S25=11 / S26=9。Sprint 27 承諾 5 SP（含 1 項人工決策 US-001），保留 1 SP Buffer，總 6 SP **刻意保守** —— 因 backlog 見底，本 Sprint 偏收尾，且 US-001 拍板結果可能改變 Sprint 28+ 規模。

---

## 6. 執行順序建議

```
US-001（產品方向決策）→ 🔴 最先：需 PM/PO 拍板，決定 Sprint 28+ 是否有開發來源
US-002（DEF-015 字型）→ 清掉離線 build 風險（影響 validate-e2e 穩定性）
US-003（DEF-013 MQ e2e）→ 活躍 DEF 最後一項，清完歸零
US-004（v5 守門實測）→ 與 US-002/003 的真實 code push 一併驗證
US-005（Buffer：腳本回歸）→ 容量有餘再做
```

> US-004 可「搭便車」：US-002/003 本就會產生 backend/frontend 變更，其 push 即為 v5 完整守門的首次實測。

---

## 7. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| 🔴 US-001 產品方向未拍板 → Sprint 28+ 無開發來源 | 中 | 高 | 本 Sprint 最優先；AI 提供三方向利弊分析供 PM/PO 決策 |
| pre-push v5 首次完整跑（~30-45 分）發現環境問題 | 中 | 中 | US-004 預留處理時間；必要時 E2E_GATE_STRICT=0 暫時放行並記錄 |
| next/font/local 字型授權/外觀差異 | 低 | 低 | 選用開源等寬/無襯線字型；UI 回歸檢查 |
| MQ e2e 可觀測性設計複雜 | 中 | 中 | US-003 為 P2；以最小可觀測（狀態查詢）起步，不過度設計 |

---

## 8. 測試規劃（QA Quincy）

| US | 測試重點 | 類型 |
|----|---------|------|
| US-002 | 離線 build 通過；UI 字型無回歸 | 手動 / build |
| US-003 | MQ 觸發→消費→可觀測 | 整合 / e2e |
| US-004 | make validate-release 完整綠燈；快取放行 | 流程驗證 |
| US-005 | 守門腳本前置自我檢查 | 腳本單元 |

---

## 9. Definition of Done（Sprint 27）

- [ ] US-001 產品方向由 PM/PO 拍板並文件化（backlog 或維護模式）
- [ ] US-002~004 所有 AC 達成
- [ ] `mvn compile` → 0 errors；Checkstyle → 0 violations
- [ ] 所有新增測試通過；既有測試無退步（`@Test` 靜態計數 ≥ 668）
- [ ] catch(Exception) 生產 **0 處**、@Deprecated 生產 **0 處**
- [ ] DEF-015 + DEF-013 完成 → **活躍 DEF 歸零**
- [ ] pre-push v5 完整守門首次端到端綠燈（US-004）
- [ ] Sprint 27 Review / Retrospective / Release Notes 建立

---

## 10. Action Items 追蹤（來自 Sprint 26 Retro）

| AI ID | 內容 | Sprint 27 對應 US |
|-------|------|-----------------|
| AI-1101 | 產品方向決策 | US-001（P1，人工） |
| AI-1102 | DEF-015 前端字型本地化 | US-002（P1） |
| AI-1103 | DEF-013 M09 MQ 通知端到端驗證 | US-003（P2） |
| AI-1104 | pre-push v5 完整守門端到端實測 | US-004（P2） |
| AI-1105 | 本地守門腳本最小回歸 | US-005（Buffer） |

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
