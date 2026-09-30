# Sprint 222 Plan — 訂房付款前端（DEF-303 (1)）

**Sprint**: Sprint 222
**日期**: 2026-09-30

## 1. 起點

Sprint 221 補上訂房付款後端；本輪補前端付款步驟（使用者回覆 DEF-303「前端訂房流程完全沒有付款步驟。==> 以下請處理，符合邏輯」的後半）。

## 2. 內容

- `services/bookingPayment.ts`（新）＋ `lib/api.ts`：付款狀態沿用既有 `GET /v2/orders/bookings/{id}/payment`，其餘三個端點為 Sprint 221 新增。
- `components/bookings/BookingPaymentCard.tsx`（新，三處共用）：依後端 `paymentProvider` 顯示「確認付款（模擬）」或「前往付款」（重導 Stripe）；已付款顯示付款資訊；沒有可付款也沒有付款紀錄（已取消）不顯示；付款狀態載入失敗降級為提示，不擋住頁面。
- `bookings/[id]`：加付款卡片，付款後重新載入訂房詳情。
- `bookings/[id]/payment/success|cancel`（新）：Stripe 回跳頁，比照訂單版。
- `checkout`（訂房結帳）：完成畫面保留「預訂成功！」與預訂編號（既有 E2E 斷言），副標題改「預訂已建立，請於下方完成付款」並帶付款卡片。
- `checkout/mixed`：Mock 模式訂單付款後接著付訂房；失敗導向訂房詳情重試。

## 3. 測試

- 新 `at-booking-payment.spec.ts`（10 案例，全程 mock）：詳情 Mock 付款、stripe 提供者重導、付款失敗訊息、回跳成功（有／缺 session／處理中）、取消頁、已取消不顯示、狀態載入失敗降級、結帳完成畫面付款、合併結帳兩邊付款。
- 既有 `at-room-booking.spec.ts` 兩個會走到完成畫面的案例補 mock 付款狀態端點（否則未登入的 401 會觸發 axios 重導 /login）。
- 突變驗證：合併結帳拿掉訂房付款 → BPAY-10 轉紅，已還原。
- `tsc --noEmit` 乾淨；eslint 0 error（93 個既有 warning）；兩個 spec 共 22 案例全過（chromium）。

## 4. 已知限制

- 合併結帳在 **Stripe 模式**仍是分開付款：先導向訂單的 Stripe 頁，訂房要到「我的預訂」另付一次（合併成單一 session 需要新的後端概念，未做）。
- 沒有以真實後端＋真實瀏覽器走完整流程（全程 mock）；沒有跑 `make validate-e2e`。
- 訂房取消退款（DEF-312）、逾時（DEF-311）仍等使用者決定。
