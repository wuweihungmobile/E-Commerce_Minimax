# Sprint 32 Retrospective 報告 / Sprint 32 Retrospective Report

> **Sprint 編號**: Sprint 32
> **期間**: 2027-01-03 ~ 2027-01-16 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-07-01
> **基於**: [SPRINT_32_PLAN.md](../04_planning/SPRINT_32_PLAN.md), [SPRINT_32_REVIEW.md](./SPRINT_32_REVIEW.md), [SPRINT_31_RETRO.md](./SPRINT_31_RETRO.md)

---

## 1. Sprint 32 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | 安全修復 + 買家閉環驗證 |
| **規劃 SP（承諾）** | 5 SP（P1）+ 3 SP（Buffer） |
| **完成 SP** | 5 SP（P1 US-001+002 全完成）；Buffer US-003 調查後誠實延後 |
| **測試結果** | 後端 `@Test` 689→690；validate-e2e 30 passed/5 skip/0 fail；catch(Exception)=0、@Deprecated=0 |
| **團隊** | 2 人 Dev Team + AISDLC Agents |
| **重大事件** | 清償延後最久的 P1 安全項 DEF-018；買家頁面首次瀏覽器驗證；DEF-017 二度誠實回退（但已定位精確根因） |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **DEF-018 IDOR 清償（P1 安全）** | getOrder 加擁有權檢查，最小爆炸半徑，越權 403 | 脫離累積期後首個完整清償的安全發現 |
| ✅ **買家頁面首次瀏覽器 E2E** | at-buyer-pages.spec.ts 納入 strict 守門 | EPIC-BUYER 前端從「僅 build」升級為「瀏覽器驗證」 |
| 🔴 **補測試再揪 DEF-019** | US-001 盤點揪出付款/物流同類 IDOR | 延續 DEF-013/017/018「補測試揪 bug」模式 |
| ✅ **DEF-017 精確定位 + 誠實回退** | 修法已驗證正確、跨租戶測試通過；但 M16 seeding 需重做 → commit 前攔下回退 | 紀律實踐；Sprint 33 範圍大幅縮小 |

---

## 2. 做得好的地方（What Went Well）

| 項目 | 說明 | 證據 |
|------|------|------|
| **最小爆炸半徑安全修法** | DEF-018 只改 getOrder、複製類內既有 inline pattern，不動共用 findOrderById | 訂單 E2E 20 tests 0 fail（payment/賣家/admin 不受影響） |
| **盤點先行（Rule 8）** | US-001/003 皆先派 Explore agent 盤點呼叫者/關聯再改 | 精準改動、揪出 DEF-019、定位 DEF-017 真因 |
| **本地守門先行、commit 前攔下** | US-003 於 commit 前跑 M16 targeted，紅燈即攔 → main 未污染 | 比 Sprint 28 當年「push 後才被守門攔」更早一步 |
| **穩健不 flaky 的 e2e** | buyer spec 用新註冊帳號 + 空狀態斷言，零 seed 依賴 | validate-e2e 30 passed 一次過（含記取 M15 flaky 教訓） |
| **誠實延後高風險（Rule 12）** | DEF-017 二度回退、DEF-019 誠實記錄，不硬塞 | AC-003-4；main 綠 |

---

## 3. 需要改善的地方（What Could Be Improved）

| 問題 | 根本原因 | 影響程度 | 處置 |
|------|----------|---------|------|
| **🟡 DEF-017 二度未能落地** | M16 測試 seeding：@BeforeAll 種的 listing 在測試交易 findById 不可見 → 加檢查即 500 | 🟡 中（安全） | Sprint 33 主要項（非 Buffer）：重做 M16 seeding（AI-1701）；修法已驗證正確 |
| **🟡 DEF-019 付款/物流 IDOR 未修** | US-001 盤點揪出的同類安全隙，非 committed 範圍 | 🟡 中（安全） | Sprint 33（AI-1702）：讀取類比照 getOrder、寫入類個別設計 |
| **🟡 買家閉環真實資料流仍缺自動化 E2E** | 需 seed 訂單/商品，自動化易 flaky | 🟡 中 | 手動 checklist（Review §6）；live 環境走查（AI-1703） |
| **🟢 前端 commit 於 validate-e2e 執行時失敗（exit 128）** | pre-commit 前端 lint 與併發 validate-e2e 搶用前端資源 | 🟢 低（流程） | 教訓：validate-e2e 執行時勿 commit 前端檔（AI-1704 記錄慣例） |
| **🟢 E_3003 映射 500** | GlobalExceptionHandler 未明列 E_3003 → 預設 500 | 🟢 低 | 併 DEF-017 修復時評估 → 422（資料完整性） |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1701 | **DEF-017 M16 seeding 重做 + 落地** | 各測試 SKU 指向交易內可 findById 的 listing（比照已通過 IT-M16-307）；套已驗證修法；完整守門 | Dev David + SD Marcus | **P1（安全）** | Sprint 33 |
| AI-1702 | **DEF-019 付款/物流 IDOR 修復** | 讀取（getOrderPaymentState）比照 getOrder；寫入（pay/fail/refund、logistics）盤點角色語意個別設計 + 補越權測試 | Dev David | **P1（安全）** | Sprint 33 |
| AI-1703 | 買家閉環 live 手動走查 | 依 Review §6 checklist 於 live 環境走完整資料流 | QA Quincy | P1 | Sprint 33 |
| AI-1704 | 守門與 commit 併發慣例記錄 | validate-e2e 執行時勿 commit 前端檔（避免 exit 128） | Dev David | P3（Buffer） | Sprint 33 |
| AI-1705 | 守門腳本最小回歸 | 順延自 AI-1605（連七 Sprint 未動） | Dev David | P3（Buffer） | Sprint 33+ |

---

## 5. Sprint 31 → Sprint 32 Action Items 追蹤結果

| Action Item | 內容 | Sprint 32 達成狀態 |
|-------------|------|------------------|
| AI-1601 | DEF-018 getOrder 擁有權修復 | ✅ 完成（US-001） |
| AI-1602 | 買家閉環前端 E2E 驗證 | 🔶 部分（US-002 補 3 空狀態自動化 spec；需 seed 的資料流改手動 checklist → AI-1703） |
| AI-1603 | DEF-017 ERP 租戶隔離 | 🔶 調查+誠實延後（修法已驗證正確、缺 M16 seeding → AI-1701） |
| AI-1604 | audit 覆蓋擴展評估 | ⏸️ 未啟動（P3，順延） |
| AI-1605 | 守門腳本最小回歸 | ⏸️ 未啟動 → 順延 AI-1705 |

**Action Items 完成率**: 1/5 完成 + 2 部分（安全修復類 DEF-018 完成；DEF-017/買家驗證部分推進）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 28 | 7+1 | 8 | 100%+ |
| Sprint 29 | 8 | 8 | 100% |
| Sprint 30 | 8 | 8 | 100% |
| Sprint 31 | 5+3 | 8 | 100%（含 Buffer） |
| Sprint 32 | 5+3 | **5**（P1 全完成；Buffer 調查延後） | **P1 100%** |

> **Sprint 32 觀察**: P1（安全+驗證）5 SP 全完成。Buffer（DEF-017）依「擇機/不硬塞」調查後誠實延後——是有意識的風險控管，非未達標。首個「安全修復主題」Sprint。

---

## 7. 技術債趨勢

| 指標 | Sprint 31 後 | Sprint 32 後 |
|------|------------|------------|
| `catch(Exception)` 生產 | 0 ✅ | **0** ✅ |
| `@Deprecated` 生產 | 0 ✅ | **0** ✅ |
| `@Test` 方法數（後端） | 689 | **690** |
| 前端 e2e spec | 既有 | **+3**（buyer pages） |
| Flyway 版本 | V57 | **V57**（無新 migration） |
| 活躍 DEF | 2（DEF-017/018） | **2**（DEF-017 / DEF-019；DEF-018 清償） |
| IDOR 安全隙（訂單讀取） | getOrder 未修 | ✅ getOrder 已修（403） |

---

## 8. 下一步方向（Sprint 33 預覽）

> **Sprint 33（規劃中）**: **安全修復收尾** —— AI-1701 DEF-017 M16 seeding 重做 + 落地（P1，修法已驗證）；AI-1702 DEF-019 付款/物流 IDOR（P1）；AI-1703 買家閉環 live 手動走查。重心：把 Sprint 32 定位精確的兩個安全項（DEF-017/019）在完整守門節奏下逐一清償，達成訂單/ERP 全面租戶隔離。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
