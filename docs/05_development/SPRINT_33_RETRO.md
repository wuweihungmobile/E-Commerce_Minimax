# Sprint 33 Retrospective 報告 / Sprint 33 Retrospective Report

> **Sprint 編號**: Sprint 33
> **期間**: 2027-01-17 ~ 2027-01-30 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-07-01
> **基於**: [SPRINT_33_PLAN.md](../04_planning/SPRINT_33_PLAN.md), [SPRINT_33_REVIEW.md](./SPRINT_33_REVIEW.md), [SPRINT_32_RETRO.md](./SPRINT_32_RETRO.md)

---

## 1. Sprint 33 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | 安全修復收尾（訂單/ERP 全面租戶隔離） |
| **規劃 SP（承諾）** | 6 SP（P1）+ 2 SP（Buffer） |
| **完成 SP** | ~2 SP（US-002 付款側）；US-001 誠實延後、US-003 部分 |
| **測試結果** | 後端 `@Test` 690→691；US-002 本地 21 tests 0 fail；catch(Exception)=0、@Deprecated=0 |
| **重大事件** | DEF-019 付款側清償；DEF-017 三層根因完整診斷後誠實延後（main 未污染） |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **DEF-019 付款 IDOR 清償** | 4 個訂單付款方法加擁有權檢查，越權 403 | 訂單「讀取＋付款」全面擁有權隔離（承 DEF-018） |
| 🔬 **DEF-017 三層根因完整診斷** | NPE→403→FK，逐層挖到測試基建真因 | 更正 Sprint 32 誤判；Sprint 34 修法路徑明確 |
| ✅ **紀律三度守住 main** | US-001 三次 commit 前本地攔下、回退 | 「一次嘗試綠才留」避免污染，勝於盲目 push |
| ⚠️ **踩到 commit/守門併發雷** | validate-e2e 執行時 commit 前端檔 → exit 128 | 記入慣例（AI-1704） |

---

## 2. 做得好的地方（What Went Well）

| 項目 | 說明 | 證據 |
|------|------|------|
| **統一 helper 修多點 IDOR** | checkOrderOwnership 一次套用 4 個付款方法（DRY） | 21 tests 0 fail |
| **本地守門先行、commit 前攔** | US-001 三次紅燈都在 commit 前攔下 | main 未污染 |
| **逐層追根因（Rule 9）** | 不停在表象（E_3003 誤判）→ 查 stack trace 挖到 FK 真因 | 三層診斷寫入 tracker |
| **誠實更正自身錯誤** | 發現 Sprint 32 E_3003 誤判 → 主動更正 tracker/Review | Rule 12 |
| **誠實部分交付** | DEF-019 付款側修完、物流側續留；不硬塞 | AC-002-4 |

---

## 3. 需要改善的地方（What Could Be Improved）

| 問題 | 根本原因 | 影響程度 | 處置 |
|------|----------|---------|------|
| **🟡 DEF-017 需測試基建重做才能落地** | @WithErpSecurity 硬編 FIXED_TENANT_ID 在 tenants 表無列（@GeneratedValue）+ listings FK | 🟡 中（安全） | Sprint 34（AI-1701）：raw SQL 種 FIXED_TENANT_ID 租戶列 |
| **🟡 DEF-019 物流/賣家側未修** | createLogistics 為租戶語意非買家擁有權，需個別設計 | 🟡 中（安全） | Sprint 34（AI-1702 續）：賣家/admin 角色檢查 |
| **🟡 Sprint 32 根因誤判（E_3003）** | 當時未查 stack trace 就下結論 | 🟡 中（流程） | 教訓：安全修復失敗必查實際例外/stack trace 再定調（AI-1705） |
| **🟢 commit/守門併發致 exit 128** | validate-e2e 佔用前端資源時 commit 前端檔 | 🟢 低（流程） | 慣例：守門執行時勿 commit 前端（AI-1704） |
| **🟢 買家 live 走查仍缺** | 需 live 環境 | 🟢 低 | Sprint 34（AI-1703） |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1701 | **DEF-017 M16 tenant seeding 重做 + 落地** | raw SQL 種 id=FIXED_TENANT_ID 的 tenants 列（比照 TestDatabaseInitializer）→ 套已驗證 null 安全租戶檢查 → M16 全綠 | Dev David + SD Marcus | **P1（安全）** | Sprint 34 |
| AI-1702 | **DEF-019 物流/賣家側 IDOR** | createLogistics（租戶語意）+ processOrderPayment 盤點賣家/admin 角色設計檢查 + 補測試 | Dev David | **P1（安全）** | Sprint 34 |
| AI-1703 | 買家閉環 live 手動走查 | 依 [SPRINT_32_REVIEW §6](./SPRINT_32_REVIEW.md) checklist | QA Quincy | P1 | Sprint 34 |
| AI-1704 | 守門/commit 併發慣例 | validate-e2e 執行時勿 commit 前端檔（避免 exit 128） | Dev David | P3（已記錄本 Retro） | — |
| AI-1705 | 安全修復先查 stack trace 定調 | 修復失敗必看實際例外，勿憑推測（記取 E_3003 誤判） | 全體 | P3（流程） | 持續 |

---

## 5. Sprint 32 → Sprint 33 Action Items 追蹤結果

| Action Item | 內容 | Sprint 33 達成狀態 |
|-------------|------|------------------|
| AI-1701（S32 版） | DEF-017 M16 seeding 重做 | 🔬 三層根因完整診斷 → 明確路徑，延 S34（重估後改由 raw SQL 種 tenant） |
| AI-1702（S32 版） | DEF-019 付款/物流 IDOR | 🔶 付款側完成（US-002）；物流/賣家側續 S34 |
| AI-1703 | 買家 live 走查 | ⏸️ 延 S34（需 live 環境） |
| AI-1704 | 守門/commit 併發慣例 | ✅ 記錄（本 Retro §4） |
| AI-1705（S32 版） | 守門腳本最小回歸 | ⏸️ 順延（連八 Sprint；本 Sprint 聚焦安全） |

**Action Items 完成率**: 1/5 完成 + 2 部分（安全類 DEF-019 付款側完成、DEF-017 診斷突破）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 29 | 8 | 8 | 100% |
| Sprint 30 | 8 | 8 | 100% |
| Sprint 31 | 5+3 | 8 | 100% |
| Sprint 32 | 5+3 | 5 | P1 100% |
| Sprint 33 | 6+2 | **~2**（US-002 付款側） | **部分**（US-001 延後、US-002 部分） |

> **Sprint 33 觀察**: 本 Sprint 兩 P1 皆安全硬骨頭。DEF-019 付款側順利清償；DEF-017 三層深挖後確認需測試基建重做而延後——完成 SP 偏低，但**根因診斷突破 + main 零污染**是實質價值。安全修復本就難以 SP 線性衡量。

---

## 7. 技術債趨勢

| 指標 | Sprint 32 後 | Sprint 33 後 |
|------|------------|------------|
| `catch(Exception)` 生產 | 0 ✅ | **0** ✅ |
| `@Deprecated` 生產 | 0 ✅ | **0** ✅ |
| `@Test` 方法數（後端） | 690 | **691** |
| 活躍 DEF | 2（DEF-017/019） | **2**（DEF-017 / DEF-019；後者付款側已修） |
| 訂單 IDOR（讀+付款） | getOrder 已修（DEF-018） | ✅ getOrder + 付款讀寫全修 |
| ERP 租戶隔離 | 未修（DEF-017） | 🔬 根因完整診斷，修法待 S34 落地 |

---

## 8. 下一步方向（Sprint 34 預覽）

> **Sprint 34（規劃中）**: **安全修復落地** —— AI-1701 DEF-017 以 raw SQL 種 FIXED_TENANT_ID 租戶列使已驗證修法落地；AI-1702 DEF-019 物流/賣家側 IDOR；AI-1703 買家 live 走查。重心：把 Sprint 33 診斷/部分完成的兩個安全項在完整守門下收尾，達成訂單/ERP/物流全面租戶隔離。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
