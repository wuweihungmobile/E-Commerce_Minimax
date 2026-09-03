# Sprint 119 Plan — DEF-044：退貨申請與退貨入庫確認（前端串接）

**Sprint**: Sprint 119
**日期**: 2026-09-03
**AI 編號**: AI-2452（延續）
**主題**: Sprint 118 後端核心已就緒，本輪為純前端串接。1 US，5 SP。

---

## 1. 範圍

依 [SPRINT_118_PLAN.md](SPRINT_118_PLAN.md) §8 排定：

> 前端：買家的退貨申請頁與進度檢視、店家後台的審核台與收貨確認表單。後端 API 已就緒，前端為純串接。

## 2. 交付

### 2.1 買家（`(auth)` route group，比照 `/support/tickets` 版型）

| 路由 | 內容 |
|------|------|
| `/returns` | 我的退貨申請列表（狀態徽章、空狀態引導） |
| `/returns/new?orderId=` | 申請表單：帶入訂單品項供勾選數量＋填寫原因；訂單狀態非 DELIVERED/COMPLETED 時擋下 |
| `/returns/[id]` | 詳情：品項、原因、駁回原因（如有）、進度時間軸；REQUESTED/APPROVED 可撤回 |

`orderId` query string 於 client 端以 `new URLSearchParams(window.location.search)` 讀取，
**不用 `useSearchParams`**——比照既有付款回跳頁 `orders/[id]/payment/success/page.tsx` 的作法，
避免 App Router 對 `useSearchParams` 的 Suspense 邊界要求。

新增入口：訂單詳情頁（`DELIVERED`/`COMPLETED` 時）「申請退貨」按鈕；
`StorefrontHeader` 帳號選單加「退貨申請」連結（緊鄰既有「客服工單」）。

### 2.2 店家（`dashboard`，比照 `/dashboard/support/tickets` 版型）

| 路由 | 內容 |
|------|------|
| `/dashboard/returns` | 審核列表 |
| `/dashboard/returns/[id]` | REQUESTED：核准／駁回（駁回可填理由）；APPROVED：收貨確認表單（逐品項可售／不可售數量輸入，前端先擋加總不得超過申請數量）；RECEIVED/REJECTED：唯讀顯示結果 |

`dashboard` 首頁 `QUICK_LINKS` 加入「退貨審核」入口。

### 2.3 Service 層

新增 `frontend/src/services/returns.ts`（比照 `services/support.ts` 分層），
`lib/api.ts` 新增 `returns`（買家）／`dashboardReturns`（店家）端點群組。

錯誤訊息不另建對照表——`GlobalExceptionHandler` 已將 `BusinessException.getUserMessage()`
（`ErrorCode` 中文訊息，AI-2418）回傳給前端，直接顯示 `err.response.data.message`
即為可讀中文（如 E-5019「此訂單狀態不允許申請退貨」），與 `orders/[id]`、`support/tickets`
等既有頁面同一慣例。

## 3. 刻意的簡化（純串接原則下）

**店家審核頁看不到商品名稱**：`ReturnDto.ItemResponse` 只帶 `orderItemId`／`skuId`，
無品名/規格可顯示。買家自己的頁面可用 `GET /v2/orders/{id}`（買家本人 owner-only）
反查訂單補上品名/圖片，但**店家無法用同一端點查看買家的訂單**（`OrderService.getOrder`
明確拒絕非本人非 admin，且目前無 dashboard 側訂單詳情端點）。Sprint 118 已拍板
「後端 API 已就緒，前端為純串接」，故未新增後端 join，店家頁品項僅顯示
`品項 #{orderItemId 前 8 碼}`。已記錄 **🟢 DEF-069**（低優先，待評估）。

## 4. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `npm run type-check`（`tsc --noEmit`） | 通過，0 錯誤 |
| ② | `npm run lint` | 0 錯誤；既有 94 個 warning（`import/no-anonymous-default-export` 等）為既有模式，本輪新增檔案僅新增同款 1 個 warning（`services/returns.ts`，與 `support.ts`/`order.ts` 等既有 service 檔一致） |
| ③ | `npm run build`（Turbopack production build） | 成功，新路由 `/returns`、`/returns/[id]`、`/returns/new`、`/dashboard/returns`、`/dashboard/returns/[id]` 皆正確產生 |

**範圍外**：未新增 E2E 規格——比照同型的既有前例（M18 客服工單前端，`support/tickets` 系列頁面
同樣無對應 E2E spec），後端 11 個整合測試已覆蓋狀態機與 IDOR 等核心行為，前端本輪為純串接。

## 5. 範圍外（承 Sprint 118 §9）

- 逆向物流追蹤、自動退款、退貨期限規則：維持 Sprint 118 的範圍外裁定。
- DEF-069（店家審核頁商品名稱顯示）：待使用者拍板是否排入後續 Sprint。
