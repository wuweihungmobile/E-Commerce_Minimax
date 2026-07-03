# Sprint 47 Retrospective / Sprint 47 回顧會議

> **Sprint 編號**: Sprint 47
> **期間**: 2027-08-01 ~ 2027-08-14
> **回顧日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 7 SP（US-001~002）|
| 完成 SP | 7 SP（全數）|
| 主題 | 開放窗語意實作——區分「未開放 vs 可訂」|

---

## 2. 做得好的（What went well）

- **決策文件 → 實作的第二次乾淨銜接**：繼 S46（AI-2406b）後，S47 又一次把 S45 spike 產出的決策文件（CALENDAR_OPEN_WINDOW_ASSESSMENT.md）直接轉為實作。開工前先讀決策文件 + Explore 探勘四處落點，AC/任務落點精準到行號，實作幾乎無返工。**「spike 先行界定、決策後才實作」的節奏連續兩個 Sprint 驗證有效**。
- **單一真相源 helper 杜絕三層分歧**：`resolveOpenUntil` 抽為 Room 領域方法，calendar/availability/booking 三層一律呼叫，避免「顯示未開放但後端接受訂房」的不一致風險。整合測試 API-M06-016 一次斷言三層，把「三層一致」編碼為測試（Rule 9）。
- **NULL 安全過渡讓 schema 變更風險可控**：兩欄 nullable 無 DEFAULT → 既有列 NULL = 無限制，backfill 免異動；既有 38 整合測試 + validate-e2e 52 全過證實既有房源零衝擊。打破零-migration 慣例的代價被降到最低。
- **schema 守門先行**：改 entity + migration 後**立即** `make validate-schema`（乾淨 Flyway DB + ddl-auto=validate），在寫三層邏輯前就確認 entity↔schema 對齊，呼應記憶教訓「本地 act 抓不到 schema-validation」。
- **NOT_OPEN 用計算產物而非持久化**：避免 densify（逐日 seed CLOSED 列）與稀疏 lazy 模型的衝突——這正是 S45 評估文件否決選項 B 的理由，實作忠實採納。

---

## 3. 待改善的（What to improve）

- **checkstyle NPath 再次在「往既有大方法加迴圈」時觸發**：getCalendar 加 NOT_OPEN 迴圈後 NPath 480>200（S46 calculatePrice 也曾如此）。→ 教訓：往既有高複雜度方法加條件/迴圈前，先預期會超標、直接抽 helper，而非等 checkstyle 擋下才抽。已抽 `appendNotOpenDays` 解決。
- **部分更新無法清除開放窗**：`updateRoom` 沿用「非 null 才更新」慣例，賣家無法把已設開放窗清回無限制。→ 屬既有全域慣例限制（非本 Sprint 引入），記錄待評估；若產品需要「清除窗」需另立顯式機制（如專屬端點或 sentinel 值）。
- **未開放原因為後端英文字串**：ListingDetail 顯示 "Date ... is not open for booking" 原字串。→ 與既有不可訂原因一致，但長期宜考慮錯誤碼→前端 i18n 訊息映射（availability reason 目前無碼），列入 backlog。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S47 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2202f | 開放窗清除機制評估 | 支援賣家把已設 open_until_date/booking_window_days 清回無限制（部分更新慣例限制）| SD Marcus | P4 | 待評估 |
| AI-2408 | availability reason 錯誤碼化 + 前端 i18n | availability unavailableReason 目前為後端英文字串，評估碼化 + 前端訊息映射 | SA Amanda | P4 | 待評估 |
| AI-2406c | PRODUCT/cart 漲價評估 | RedisCartService 目前只折扣，評估對齊 ROOM | PM Victoria + SD Marcus | P3 | 待評估 |
| AI-2407 | 定價規則選取語意評估 | bestRule priority + findActiveRulesForDateRange 逐日精準查詢 | SD Marcus | P3 | 待評估 |
| AI-1903 | 買家閉環 live 走查（真人）| 於 live 環境走查真 DB 跨角色資料流 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 46 → Sprint 47 Action Items 追蹤結果

| Action Item | 內容 | Sprint 47 達成狀態 |
|------------|------|---------------------|
| AI-2202e | 開放窗語意實作 | ✅ 完成（PO 拍板選項 A + 滾動視窗 + host UI → US-001 後端三層 + US-002 前端落地）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S47 累積 7 Sprint，待徵詢後完整守門 push）|
| AI-2406c | PRODUCT 漲價評估 | 🟡 續留（P3 待評估）|
| AI-2407 | 定價規則語意評估 | 🟡 續留（P3 待評估）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S42 | 7 |
| S43 | 10 |
| S44 | 8 |
| S45 | 5 |
| S46 | 8 |
| **S47** | **7** |

> **觀察**：S47 = 7 SP，實作型 sprint（含 schema），落在健康區間。品質：後端單元 9 + 真 DB 整合 38 全過、validate-e2e **52 passed/0 fail**（+2 NOT_OPEN E2E）、**validate-schema 無漂移**（V58 對齊）、catch(Exception)/@Deprecated=0。**開放窗語意三層一致落地**，收掉「未開放 vs 可訂」產品缺口。連續零-migration 於 S46（V57）後、本 Sprint（V58）結束。

---

## 7. 下一步

> **檢查點**：Sprint 47 已完成（US-001 `fbfba4a` + US-002 `cf019da` + 收尾，本地各層驗證通過含 validate-schema 無漂移 + validate-e2e 52/6/0）。**push 債已累積 S41~S47（7 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。Sprint 48 候選：AI-2406c PRODUCT/cart 漲價評估、AI-2407 定價規則選取語意評估、AI-2202f 開放窗清除機制、AI-2408 availability reason i18n、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能開發。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
