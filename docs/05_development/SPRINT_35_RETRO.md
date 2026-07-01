# Sprint 35 Retrospective 報告 / Sprint 35 Retrospective Report

> **Sprint 編號**: Sprint 35
> **期間**: 2027-02-14 ~ 2027-02-27
> **報告日期**: 2026-07-01
> **基於**: [SPRINT_35_PLAN.md](../04_planning/SPRINT_35_PLAN.md), [SPRINT_35_REVIEW.md](./SPRINT_35_REVIEW.md), [SPRINT_34_RETRO.md](./SPRINT_34_RETRO.md)

---

## 1. Sprint 35 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | 賣場店面版型基礎 + 首頁改版（意象若水 RUOSHUI 設計稿套版） |
| **規劃 SP（承諾）** | 18 SP（P1）+ 2 SP（Buffer） |
| **完成 SP** | 18 SP（US-001~005 全完成）；US-006 延 S36 |
| **測試結果** | build/type-check/lint 0 error；at-homepage E2E 4 tests 通過；既有 E2E 不退步 |
| **重大事件** | 🎨 前端首度導入設計系統版型；從 6.3MB bundled 設計稿完整逆向抽出資產 |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| 🎨 **賣場版型基礎建立** | 主題色票系統 + Design System 元件 + 共用 Shell（TOP/Tools/Bottom） | 全站改版有可重用地基 |
| 🔬 **設計稿完整逆向** | 從 bundled HTML（manifest+template）抽出 6 元件 + 43 CSS + 5 色票 + 資料模型 | 重建忠於設計、非憑空猜測 |
| 🧭 **Next.js 16 破壞性變更遵循** | 依 `frontend/AGENTS.md` 先讀 node_modules 內建文件再開發 | 避免用過時 API |

---

## 2. 做得好的地方（What Went Well）

| 項目 | 說明 | 證據 |
|------|------|------|
| **先調查後動工** | 逆向設計稿 + Explore 盤點前端架構 + 讀 Next16 文件，才寫碼 | FRD + 4 項架構決策確認 |
| **開發-編譯-測試循環** | 每個 US 完成即 build+lint+tsc，不累積 | 每階段 0 error 才進下一步 |
| **不破壞既有** | shadcn 中性 token 完全保留，rs-* 平行新增（Rule 3/7） | 既有頁面零影響、lint 0 新增 error |
| **E2E 揪出真 bug** | 首跑抓到 SearchBar 受控無 onChange + loading 卡死 → 修復 | E2E-HOME-03 由紅轉綠 |
| **誠實處理技術限制** | Turbopack CJK / React19 hooks / listings 授權 三處據實調整並記錄 | Review §5、本文 §3 |

---

## 3. 需要改善的地方（What Could Be Improved）

| 問題 | 根本原因 | 影響程度 | 處置 |
|------|----------|---------|------|
| **🟡 SearchBar 受控/非受控混用初期出錯** | 傳 value 未配 onChange → 唯讀輸入 + loading 卡死 | 🟡 中（E2E 才抓到） | 已修（改非受控 + nonce 強制重跑）；記取「受控元件必配 onChange」 |
| **🟡 CJK 字體無法 self-host** | Turbopack 對 next/font CJK 大量 unicode-range 子集無法解析 | 🟢 低（有系統字體 fallback） | 系統 CJK 堆疊；未來評估 local woff2 子集化 |
| **🟢 全站改版僅完成基礎+首頁** | 範圍大、分階段 | 🟢 中 | (auth) S36、dashboard/admin/cms S37（FRD §7） |
| **🟢 首頁商品需登入才顯示** | `/v2/listings` 需 product:read | 🟢 低 | 未登入顯示引導；若要公開需後端授權調整（另評估） |
| **🟢 累積批次已達 S32~S35** | 連四 Sprint 累積未 push | 🟢 中 | 檢查點徵詢後完整守門一次 push |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1901 | (auth) 買家頁套共用 Shell | cart/checkout/orders/bookings/notifications/reviews 套 StorefrontShell（Tools 隱藏，D2） | Dev David | P1 | Sprint 36 |
| AI-1902 | DEF-019 物流/賣家側 IDOR | 順延自 AI-1801（先盤點 M11 seeding，tenant-based 檢查） | Dev David + SD Marcus | **P1（安全）** | Sprint 36 |
| AI-1903 | 買家閉環 live 走查 | 順延自 AI-1802（需 live 環境） | QA Quincy | P1 | Sprint 36 |
| AI-1904 | dashboard/admin/cms 套 Shell（seller 變體） | 全站改版階段三 | Dev David | P2 | Sprint 37 |
| AI-1905 | 首頁商品「有資料」E2E + 分頁翻頁 | 需 seed 商品，補自動化（目前手動 checklist） | QA Quincy | P3 | 後續 |
| AI-1906 | 檢查點徵詢後 push S32~35 累積批次 | 完整守門（make validate-release）綠燈後 push | Dev David | P1 | 檢查點 |

---

## 5. Sprint 34 → Sprint 35 Action Items 追蹤結果

| Action Item | 內容 | Sprint 35 達成狀態 |
|-------------|------|------------------|
| AI-1801 | DEF-019 物流/賣家側 IDOR | ⏸️ 延 S36（AI-1902；本 Sprint 聚焦版型，安全項未遺漏） |
| AI-1802 | 買家 live 走查 | ⏸️ 延 S36（AI-1903，需 live 環境） |
| AI-1803 | push S32~34 累積批次 | ⏸️ 併入 S32~35 批次（AI-1906，檢查點徵詢後） |
| AI-1804 | 守門腳本最小回歸 | ⏸️ 順延（Buffer，連十 Sprint） |

> **說明**：Sprint 35 為使用者新指示（賣場版型套版）插入的功能 Sprint；S34 遺留的安全/走查 Action Items（AI-1801/1802）順延 S36，**未遺漏**（活躍 DEF-019 仍在追蹤）。

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 31 | 5+3 | 8 | 100% |
| Sprint 32 | 5+3 | 5 | P1 100% |
| Sprint 33 | 6+2 | ~2 | 部分 |
| Sprint 34 | 6+2 | 3 | P1 主項完成 |
| Sprint 35 | 18+2 | **18** | **P1 100%** |

> **觀察**：Sprint 35 SP 顯著高於近期（18 vs 3~8）——因前端純新增（低耦合、無既有測試衝突、AI 快速迭代），且逆向設計稿已備妥完整規格。此為健康的高產出，非估算失準；但下 Sprint 涉及既有頁面改造（AI-1901），耦合升高，SP 應回歸保守。

---

## 7. 技術債趨勢

| 指標 | Sprint 34 後 | Sprint 35 後 |
|------|------------|------------|
| 前端共用版型 | ❌ 無（各頁自建 nav） | ✅ StorefrontShell（TOP/Tools/Bottom） |
| 前端 build/lint/tsc | 綠 | **綠**（0 error） |
| 前端 E2E spec 數 | 8 | **9**（+at-homepage） |
| 活躍 DEF | 1（DEF-019 物流賣家側） | **1**（DEF-019，續 S36） |
| 累積未 push commit | 9（S32~34） | 待計（S32~35） |

---

## 8. 下一步方向（Sprint 36 預覽）

> **Sprint 36（規劃中）**: **買家店面全頁套版 + 安全收尾** —— AI-1901 (auth) 買家頁套 StorefrontShell（Tools 隱藏）；AI-1902 DEF-019 物流/賣家側 IDOR（清最後活躍安全 DEF）；AI-1903 買家 live 走查。並在檢查點徵詢後把 S32~35 累積批次經完整守門一次 push。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
