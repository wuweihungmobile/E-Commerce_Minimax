# Sprint 112 Plan — DEF-043：ROOM 訂房結帳誤刪整個購物車

**Sprint**: Sprint 112
**日期**: 2026-09-02
**AI 編號**: AI-2446
**主題**: 追蹤表高優先級已清空後，從中優先級挑出風險/成本比最好的一項。1 US，1 SP。

---

## 1. 為什麼挑這一項

追蹤表活躍項目中，🔴 高優先級為空，🟡 中優先級有 4 項：

| 項目 | 為什麼不是它 |
|------|-------------|
| DEF-044 退款不回補庫存 | 條目本身寫明「**需業務決策**：退款是否應自動回補庫存、回補時點」——這不是工程能自己決定的 |
| DEF-051 ERP 讀後寫競態 | 需併發條件才觸發，記錄為低風險；價值不如必然發生的缺陷 |
| DEF-021 CJK 字體 | 純外觀技術債 |
| **DEF-043 訂房清空整車** | **必然發生**（每次混合購物車訂房都中）、**使用者可見的資料遺失**、且**修法已有先例**（PRODUCT 側 AI-2422 已定調），不需要新的產品決策 |

選 DEF-043。它是唯一一個「決定已經做過了，只是沒套用到這一側」的項目。

---

## 2. 缺陷比追蹤表記載的更嚴重

追蹤表寫的是「若購物車混有尚未結帳的 PRODUCT 項目會被誤刪」。實際讀 `checkout/page.tsx` 後發現範圍更大：

```
frontend/src/app/(auth)/checkout/page.tsx
  L111  const roomItems = cartData.items.filter(item => item.listingType === 'ROOM')
  L124  const roomItem = roomItems[0]        // ← 只訂第一間（註解自承 assuming one at a time）
  L137  await apiClient.delete(API_ENDPOINTS.cart.clear)   // ← 卻清空整車
```

所以被靜默刪除的不只 PRODUCT 項目，**還包括第二個以後的 ROOM 項目**。
使用者把兩間房加進購物車、送出，畫面顯示「預訂成功」，實際只訂到一間，
另一間連購物車紀錄都沒了——**而且沒有任何提示**。

---

## 3. 修法：與 PRODUCT 側對齊

```diff
- // Clear cart after successful booking
- await apiClient.delete(API_ENDPOINTS.cart.clear)
+ // 🔴 只移除「這次真的訂掉」的那一個 ROOM 項目，不可清空整車（DEF-043）。
+ await apiClient.delete(API_ENDPOINTS.cart.remove(roomItem.cartItemKey))
```

並在 `BookingItem` 介面補上 `cartItemKey: string`（後端 `CartDto.CartItemResponse` 本來就有回傳，
只是這個本地型別沒宣告）。

**為什麼修前端而不是把清車搬到後端**（追蹤表列的另一個選項）：
`BookingService.createBooking` 是**通用訂房 API**，`BookingController` 有兩處呼叫（帶/不帶
idempotency key），可在沒有購物車的情況下直接訂房。把購物車移除耦合進去，會讓訂房 API 變成
購物車感知的，且得處理「這次訂房不來自購物車」的分支。前端修法只有 2 行、且與
`cart/page.tsx:133` 既有慣例完全一致。

---

## 4. 驗證

### 4.1 紅燈先行

新增 `E2E-ROOM-12`（`frontend/e2e/at-room-booking.spec.ts`），沿用該 spec 既有的
`page.route` mock 模式（**不需後端 seed**，因此不受固件狀態影響）：
混合購物車（ROOM `k-room` + PRODUCT `k-product`）→ 完成訂房 → 斷言購物車 DELETE 的路徑。

**斷言的是機制，不是畫面**：

```ts
expect(cartDeletePaths.some(p => p.endsWith('/v2/cart/items/k-room'))).toBe(true);
expect(cartDeletePaths.some(p => p.endsWith('/v2/cart'))).toBe(false);   // 絕不整車清空
```

先對**未修復**的程式碼執行 → 如期失敗：

```
> 258 | expect(cartDeletePaths.some((path) => path.endsWith('/v2/cart/items/k-room'))).toBe(true);
  Expected: true
  Received: false
```

（失敗在第一條斷言，因為未修復時根本不會呼叫單項移除端點。）

測試裡先斷言「預訂成功！」可見才檢查購物車呼叫——否則若訂房根本沒送出，
兩條購物車斷言會因為「沒走到清車那一步」而假綠。

### 4.2 🔴 測試初版是「時序假綠」，被守門攔下

套用修復後在**本機 dev server** 跑出 `12 passed`，我一度據此報告「綠燈驗證完成」。
**那是錯的。** 完整 `make validate-release` 在 **production build** 下，同一個測試以完全相同的錯誤失敗：

```
> 258 | expect(cartDeletePaths.some(p => p.endsWith('/v2/cart/items/k-room')))
  Expected: true   Received: false
```

**根因是測試，不是修復。** checkout 的順序是 `setBookingId()` 先觸發 render、**DELETE 在那之後才發出**，
兩者沒有順序保證。初版把「預訂成功！」畫面當成「請求已送出」的訊號——
dev server 較慢，render 到斷言之間的空檔剛好夠請求送達；production build 較快，斷言先跑完。

**修法**：改用 `expect.poll` 等待請求本身被觀察到，不再以畫面推論。
否定斷言（絕不整車清空）放在 poll 之後才安全——確認移除請求已發生，才知道流程真的走到那一步。

**這個錯誤的失敗方向是假綠，所以特別危險**：修復若是錯的，測試會紅、我會發現；
修復對但測試時序錯，會變成「dev 綠、CI 紅」的 flaky，很容易被「重跑一次就好」帶過。

### 4.3 完整驗證矩陣（每一列都實際跑過）

| # | 程式碼 | 測試版本 | 條件 | 結果 |
|---|--------|----------|------|------|
| 1 | 有缺陷 | 初版（畫面訊號） | dev | 🔴 紅 |
| 2 | 已修復 | 初版 | dev | ⚠️ 綠 — **時序假綠** |
| 3 | 已修復 | 初版 | **production build** | 🔴 紅 — `validate-release` 攔下 |
| 4 | 已修復 | `expect.poll` | production build ×3 | ✅ 3 passed |
| 5 | **還原缺陷** | `expect.poll` | production build | 🔴 **紅** |

**第 5 列是關鍵。** 第 4 列只證明「修復後會過」，不能證明「有缺陷時會被抓到」——
**加了重試機制的測試最容易在無聲中失去偵測能力**。第 5 列紅了，這個測試才算數。

### 4.3 同型實例掃描

- `grep cart.clear frontend/src` → 修復後**零命中**（本輪移除的是唯一一處）。
- PRODUCT 結帳頁本來就正確（已宣告 `cartItemKey`、不清整車，後端 `createOrderFromCart` 逐項 `removeItem`）。
- 其他 E2E spec 沒有任何一個斷言依賴「清整車」這個舊行為。

### 4.4 守門

🔴 **雲端 CI 沒有 E2E job**（只有 Backend Unit／Backend Integration & Package／Frontend Lint & Build 三個）。
本輪改的是結帳流程，若只靠 pre-push 的 `validate-push`（backend-unit + schema + frontend build/lint）
等於**這個變更完全沒有 E2E 覆蓋**。故本輪走完整 `make validate-release`（含 `validate-e2e`）再 push。

---

## 5. 範圍外（已觀察、刻意未動）

| 項目 | 為什麼不動 |
|------|-----------|
| **「只訂 roomItems[0]」本身** | 多房一次訂是功能缺口（`assuming one at a time for now`），不是本 DEF 的範圍。本輪修的是「沒訂到的不該被刪」，修完後第二間房會**留在購物車**，使用者可以再訂一次——從資料遺失變成可恢復 |
| **購物車移除失敗時的錯誤處理** | `await delete(...)` 在 try 內，失敗會走 catch 設 error；但 `setBookingId` 已先執行、render 先檢查 `bookingId`，所以成功畫面仍會顯示、錯誤不可見。目前不會造成錯誤畫面，但也代表移除失敗時使用者不會知道。**未改**——這是既有的錯誤處理設計，不在本 DEF 範圍 |
| **`API_ENDPOINTS.cart.clear` 現已無呼叫點** | 後端 `DELETE /v2/cart` 是真實且合理的端點（購物車頁未來可能要「全部清空」按鈕），移除定義屬過度清理 |
| **E2E-M11-009 是弱測試** | 該測試用 `console.log` 當作找不到成功訊息時的 fallback，等於不會失敗。**不是本輪的爛攤子**，未順手改（Rule 3） |

---

## 6. 方法論

1. **挑項目的判準是「決定是否已經做過」，不是「嚴重性排序」。** DEF-044 看起來比 DEF-043 嚴重，
   但它卡在業務決策上；工程能自己完成的是 DEF-043。挑一個自己做不完的項目開工，不是勇敢是浪費。
2. **讀條目不等於讀程式碼。** 追蹤表說「PRODUCT 項目會被誤刪」，實際讀 `checkout/page.tsx` 才發現
   第二個 ROOM 項目也會。**條目是別人當時的理解，不是現場。**
3. **斷言機制而非畫面。** 這個缺陷在畫面上完全看不出來（照樣顯示「預訂成功」），
   只能從「呼叫了哪個端點」判定。畫面斷言在這裡是無效的。
4. **「本機綠」不是證明，「在暴露問題的那個條件下綠」才是。** 初版測試在 dev 綠、production 紅。
   修好之後我沒有回頭用 dev 驗證，而是重建 production bundle 重跑——
   **用比出問題時更寬鬆的條件去驗證修復，等於沒有驗證。**
5. **把測試改寬鬆（加重試/逾時）之後，必須重新證明它會紅。** `expect.poll` 正是最容易把測試
   改成永遠會過的那種修改。所以還原缺陷、重建、再跑一次確認紅燈——這一步不能省。
