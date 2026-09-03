# Sprint 120 Plan — DEF-069：退貨審核台補上商品名稱顯示

**Sprint**: Sprint 120
**日期**: 2026-09-03
**AI 編號**: AI-2452（延續）
**主題**: 使用者拍板排入排程。1 US，2 SP。

---

## 1. 缺陷

Sprint 119 前端串接時記錄：店家審核台（`/dashboard/returns/[id]`）看不到商品名稱，
只能顯示 `品項 #{orderItemId 前 8 碼}`。根因是 `ReturnDto.ItemResponse` 只帶
`orderItemId`／`skuId`，無品名/規格；店家又無法呼叫買家專屬的 `GET /v2/orders/{id}`
（IDOR 防護）反查補上，導致收貨確認時只能憑一串截斷 ID 對照商品，可用性差。

## 2. 方向

DEF-069 記錄的兩個選項中選 **(a)**：`ReturnRequestService` 回應時 join
`product_skus`/`listings` 補上 `skuCode`/`productName`，比照 DEF-064 的作法。
理由：改動侷限在既有服務層（不新增端點、不擴大攻擊面），且退貨品項本來就帶
`skuId`（`ReturnRequestItem.skuId` `nullable = false`），只差一個 join。

選項 (b)（新增 dashboard 側訂單詳情端點）需要另外設計一個能安全暴露買家訂單片段
給店家看的端點與其權限模型，複雜度與風險都明顯高於 (a)，故不採用。

## 3. 實作

- `ProductSkuRepository` 新增 `SkuDisplayInfo` 投影 + `findDisplayInfoByIdIn`：
  `LEFT JOIN` `product_skus` 與 `listings`（比照 DEF-064 教訓：SKU 或商品被刪除／
  下架時顯示留白，不讓整列消失）。
- `ReturnDto.ItemResponse` 新增 `skuCode`／`specName`／`productName`。
- `ReturnRequestService.toResponse()`：一次批次查詢該退貨單所有品項的 SKU 顯示資訊
  （`Set<UUID>` 去重後單次 `IN` 查詢），避免逐品項各自 lazy load。
  **已知取捨**：批次僅止於單一退貨單內的品項（通常 1-3 筆），未擴大到整頁
  `getTenantReturnRequests`/`getMyReturnRequests` 的跨退貨單批次——對這個低流量功能，
  多一次小查詢換取程式碼簡單（Rule 2），不做 DEF-064 那種全頁一次 JOIN 投影的規模。
- 前端 `services/returns.ts` 的 `ReturnItem` 型別補上三個欄位；
  `/dashboard/returns/[id]` 改顯示 `productName`（無值時退回原本的截斷 ID 顯示）＋
  `specName`／`skuCode`。買家頁面未動——已透過既有的 `GET /v2/orders/{id}` 反查
  取得更完整的顯示資訊（含封面圖），本輪範圍只在店家側。

## 4. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `mvn -o compile` | 每次改動後立即編譯，皆 BUILD SUCCESS |
| ② | `M05ReturnRequestIntegrationTest` 新增 IT-M05-RETURN-012 | 斷言 `skuCode`／`productName` 與種子資料一致；**12 passed**（含既有 11 案例回歸） |
| ③ | 後端全量回歸 `mvn -o verify` | **單元 1034 / 整合 454，0 失敗**（相對 S119 的 1034/453，+1 個新整合案例）；checkstyle／PMD 0 violations |
| ④ | 前端 `npm run type-check` / `lint` | 通過，0 錯誤 |

## 5. 範圍外

- 買家頁面顯示邏輯（已用 order join 取得更完整資訊，無需改動）。
- 整頁列表層級的批次 JOIN 投影優化（見 §3 已知取捨）：若退貨量成長到需要優化，
  屆時再比照 DEF-064 的 `StockMovementRow` 投影模式重構。
