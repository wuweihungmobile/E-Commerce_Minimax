# Sprint 121 Plan — DEF-067：移除庫存盤點孤兒骨架

**Sprint**: Sprint 121
**日期**: 2026-09-04
**AI 編號**: AI-2453
**主題**: 使用者拍板移除技術債。1 US，1 SP。

---

## 0. 前置：DEFERRED_ITEMS_TRACKER 的一次過時漂移

回歸 AISDLC 流程盤點 Sprint 121 候選項目時，原本依 tracker 狀態欄選中 DEF-064
（顯示「方向已拍板，待排程」）。動工前依 Rule 8（讀後寫）核對程式碼，發現 DEF-064
早已於 Sprint 117（同日 2026-09-03 拍板即動工，`commit e9d599d`）完整實作並上線，
tracker 這一列誤留了 3 個 Sprint（118-120）沒有回填完成狀態。已修正並移至「已完成延後
項目」，詳見 `DEFERRED_ITEMS_TRACKER.md` 的更正記錄。改選 DEF-067 作為本 Sprint 主題。

## 1. 缺陷

`inventory_checks` 表與 `InventoryCheck` entity 是孤兒：庫存盤點為 PRD §6.7.2 **P1** 功能，
實體與資料表都在（`V50` 建），但**沒有任何生產程式碼讀或寫**——後端沒有
`InventoryCheckRepository`／`InventoryCheckService`，前端沒有任何頁面或型別引用它。

與同源的 `inventory` 孤兒表（DEF-066，Sprint 116 移除）不同，`inventory_checks`
**從未被誤讀成資料來源**（`inventory` 至少還被四個讀取點誤讀，讀到空資料），純粹是
尚未實作功能的骨架。骨架本身也不完整——`InventoryCheck` 只有單頭表的彙總欄位
（`varianceCount`／`totalItemsChecked`），沒有任何逐 SKU 明細子表，就算接上 CRUD
也做不出「盤盈/盤虧記錄」（PRD 對此功能的原文描述）。

## 2. 裁決

使用者面對兩條路：(a) 移除孤兒骨架（技術債清理）；(b) 啟動完整功能設計（PRD 目前只有
一行摘要、無 US/AC/API 規格，需先走 SA/PM 需求分析）。**使用者拍板選 (a)**——庫存盤點
若日後要做，應先走完整 AISDLC 需求分析，而非在殘留骨架上接功能。

PRD §14.2.5 的 `BV-2A-04`（ERP 庫存準確率 = 盤點差異次數 / 總盤點次數 ≤ 2%）KPI
目前無法量測，此為既有事實，不因本次移除而改變或惡化。

## 3. 修法

比照 DEF-066（`V72`）的既有模式：

1. 刪除 `InventoryCheck.java` entity（無 repository/service，無需連帶移除）。
2. 新增 `V75__Drop_Orphan_Inventory_Checks_Table.sql`：`DROP TABLE IF EXISTS inventory_checks;`，
   遷移檔頭記錄背景與使用者裁決依據。
3. 確認無殘留引用：`grep -rl "InventoryCheck" backend/src frontend/src` 僅命中被刪除的
   entity 自身；`inventory_checks` 未出現在 `SRD_Database_Schema.md`／PRD §8.2（原本就
   不在 14 張已文件化的表之列，`make validate-schema-doc` 通過不代表本次改動被涵蓋，
   純屬巧合——DEF-068 記錄的涵蓋率缺口依然存在，未因本次變動而改善）。

## 4. 範圍外

- PRD §6.7.2「庫存盤點」功能本身的落地：需另立專案走完整需求分析（US/AC/API），
  規模與本次骨架清理不同量級，見上方裁決說明。
- DEF-068（SRD schema 文件涵蓋率缺口）：與本次改動無關，仍待使用者拍板，見
  `DEFERRED_ITEMS_TRACKER.md`。

## 5. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `mvn -o compile`（改動前先 `rm -rf target/maven-status`，比照 S116 教訓避免假成功） | BUILD SUCCESS；`target/classes` 確認 `InventoryCheck*.class` 已移除，非殘留舊 class |
| ② | `make validate-schema`（entity↔migration 漂移守門） | ✅ 對齊，無漂移 |
| ③ | `make validate-schema-doc`（migration↔文件漂移守門） | ✅ 75 個遷移，文件與 PRD §8.2 一致 |
| ④ | 後端全量回歸 `mvn -o verify`（`make test-db-up` 起真實 PostgreSQL） | **單元 1034 / 整合 454，0 失敗**（與 S120 基準相同，本輪不新增/移除測試案例）；checkstyle／PMD 0 violations；`M16ErpInventoryLedgerIntegrationTest`／`M16ErpStockMovementDisplayIntegrationTest`／`M05ReturnRequestIntegrationTest` 等既有相鄰測試皆綠，確認移除孤兒骨架未影響任何既有功能。總耗時 15:49 min |

ℹ️ **一次環境陷阱記錄**：`mvn -o verify` 第一次執行時 `SellerDashboardServiceCacheTest` 3 案例報 `Failed to load ApplicationContext`（`Connection refused: localhost:5432`）——忘記先 `make test-db-up`，非本次改動 regression（比照既有記憶 `backend-integration-test-profile-needs-real-db`）。啟動測試 DB 後重跑即全綠。
