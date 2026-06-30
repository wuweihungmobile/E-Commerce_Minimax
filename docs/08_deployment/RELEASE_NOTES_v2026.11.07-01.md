# Release Notes - v2026.11.07-01 (Sprint 27)

**發布日期**: 2026-11-07（規劃）／實作完成 2026-07-01
**發布類型**: Patch（技術債清零 + 品質守門驗證 + 規劃）
**Sprint**: Sprint 27
**狀態**: ✅ **已 push origin/main（本地優先驗證全綠）**

> Sprint 27 主題：產品方向決策 + 殘餘技術債清零 + 本地守門收尾驗證（輕量收尾型）

---

## 新功能 ✨

- 無新業務功能（輕量收尾型 Sprint）。

## Bug 修復 🐛

- **🔴 M09 通知端到端斷鏈（DEF-013，正式環境）**：`NotificationProducerService` 用 `opsForStream().add()`（Redis Stream）、`NotificationConsumerService` 用 `opsForList().rightPop()`（Redis List）讀同一 key（`notification:stream`），型別不相容（WRONGTYPE）被吞 → `NotificationService.sendToQueue` 產出的通知**永不被消費**。修復：producer 改用 `opsForList().leftPush()`（與 consumer 對齊，FIFO）。新增 `NotificationProduceConsumeTest`（真實 ObjectMapper + 共用佇列）端到端鎖住。

## 改進 🚀

- **前端離線可建置（DEF-015）**：移除 `layout.tsx` 的 `next/font/google`（Geist 為未被 CSS/Tailwind 消費的死碼）→ 建置期不再向 Google Fonts 抓取，離線 `npm run build` 穩定。零視覺影響、無新增依賴。
- **pre-push v5 完整守門端到端驗證**：`make validate-release`（act + schema + e2e）首次端到端跑完並全綠（26 passed / 6 skip / 0 fail）；FULL 記錄 + tree-hash 快取放行實證。

## 規劃產出 📋

- **PRODUCT_BACKLOG.md**：PM/PO 拍板方向 (b) 強化既有模組；3 路盤點 + RICE 排序 + EPIC-BUYER（買家端閉環）。
- **SPRINT_28_PLAN.md**：「先補品質再加功能」（測試補強 + 後台深化）。

## 資料庫遷移 🗄️

- 無新 Flyway migration（最新仍為 V56）。

## 重大變更 ⚠️

- 無破壞性 API 變更。通知 MQ 修復為內部傳遞結構對齊，對外行為不變（修復後通知才真正送達）。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| US-005 守門腳本最小回歸（AI-1105） | 順延 Sprint 28（AI-1201） |
| M14 Analytics 後端 0 測試 | Sprint 28 US-001 補強 |
| EPIC-BUYER 買家端前端閉環 | Sprint 29 起 |

## 驗證狀態 ✅

- `@Test` 靜態計數：**670**（+2；NotificationProduceConsumeTest）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0
- **活躍 DEF 歸零**（DEF-009~015 全清償）
- Checkstyle 0 violations；前端 lint/type-check/build 通過
- pre-push v5 完整守門：act + schema + e2e 全綠

## 內含 Commit（Sprint 27）

| US / 項目 | Commit |
|----------|--------|
| US-002/003（DEF-015 字型 + DEF-013 通知端到端修復） | `e697dbe` |
| US-001（產品方向 + PRODUCT_BACKLOG + Sprint 28 規劃） | `ec429cb` |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Explore ×3 + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
