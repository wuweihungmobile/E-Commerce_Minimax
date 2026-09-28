# Sprint 216 Plan — 一般買家付不了款（DEF-298），以及修好之後會露出來的兩個金流缺口（DEF-299／300）

**Sprint**: Sprint 216
**日期**: 2026-09-28

## 1. 起點

Sprint 215 收尾後（已 push、雲端 CI 全綠），`DEFERRED_ITEMS_TRACKER.md` 沒有 AI 可獨立處理、且不需使用者決定的活躍待辦（`myTodoList.md` 三項仍需使用者操作實際環境；DEF-292／294／295／297 皆標「不排入排程」、等使用者決定）。使用者貼上 Sprint 215 總結並說「請繼續完成任務！」，沒有指定新的項目。

這裡「繼續」的解讀是**推論**：延續 Sprint 210～215 的既有模式，自選一個掃描角度找新缺陷；人工項目（SMTP／維運／Stripe）依先前約定跳過，DEF-294／295／297 仍待使用者決定，本輪不動。

## 2. 掃描角度與查證

### 2.1 起初的角度：取消／退款的各個入口是否做了同一套補償

延續 Sprint 215「同一個不變量是否在每個入口都落實」的做法，這次套在「訂單進入 CANCELLED」上。先排除一個：換發 token 時會重新從資料庫讀帳號狀態與租戶（`AuthService.refreshToken`），沒有「權限沿用舊 token」的問題。

逐一比對能讓訂單變成 CANCELLED／REFUNDED 的入口時，發現「已付款訂單取消後卡在 REFUNDING」（§2.4）。為了確認「買家能不能走到已付款」，去查付款端點的權限，結果發現更根本的問題（§2.2），於是把角度調整為：**買家前端會呼叫的端點，在生產權限表下買家到底呼叫得到嗎**。

### 2.2 DEF-298：一般買家付不了款

`POST /v2/orders/{id}/pay`（Mock 付款）、`/pay/fail`、`/pay/checkout`（Stripe 結帳）都要求 `order:update`，而生產 `RolePermissionMapping` 的 BUYER **從初始 commit 起就沒有** `order:update`（`git show e358b1a`；付款端點自 Sprint 12 `7a86b46` 建立起就要求它）。前端買家結帳頁（`checkout/product`、`checkout/mixed`）與訂單詳情頁都呼叫這三個端點。結果：一般買家建立訂單（預扣庫存、佔用優惠券）之後，付款一律 403，畫面顯示「建立訂單失敗」。

盤點方式：腳本列出全部 310 個端點與 `@PreAuthorize`，以生產權限表對 BUYER 求值（96 放行／186 拒絕／27 無註解／1 無法機械判定），再比對拒絕清單中前端有使用的路徑，逐一看呼叫它的頁面。買家頁面會呼叫、卻被拒的**只有這三個付款端點**；FAQ／知識庫／媒體等被拒端點只在後台頁面使用。

**為什麼 215 輪都沒發現**：

- 整合測試的 `IntegrationTestConfiguration` 用一份手抄權限清單取代真實 `RolePermissionMapping`，那份清單多給了 BUYER `order:update`。Sprint 128 已知這份清單不可信，當時修的是「權限碼不在枚舉裡」那個方向，沒有檢查「清單比生產多給」這個方向。
- Playwright E2E 從未對真實後端走到付款：結帳測試停在「看得到結帳頁」，唯一的付款相關測試把回跳端點 mock 掉了。

**紅燈實測**（真實 Spring Security filter chain）：把手抄清單的 BUYER 對齊生產（拿掉 `order:update`）後，既有的 `BuyerOrderJourneyE2ETest` 5 個案例中 2 個失敗，都失敗在付款那一步（`Expected status code <200> but was <403>`）；建單、他人不可讀取／取消等 3 個案例照常通過。

**不能用「給 BUYER `order:update`」來修**：這個權限同時打開 `PATCH /v2/orders/{id}/status`、`POST /v2/orders/{id}/refund`、`POST /v2/payments/refund`，而它們在服務層都放行「訂單本人」——買家就能自己退款、把自己的訂單改成已出貨／已完成。

### 2.3 DEF-299：啟用 Stripe 後，Mock 付款仍能不收錢就把訂單標成已付款

Mock 付款（`PaymentStateService.mockPaymentSuccess`、舊版 `PaymentService.processPayment`）建立一筆「直接成功」的付款並把訂單轉 PAID，**完全不看是否已啟用真實金流**。Phase 1（未啟用 Stripe）這是設計，但一啟用 Stripe，還能呼叫就等於不付錢拿商品。

- `POST /v2/orders/{id}/pay`：原本擋住這件事的只是「買家剛好沒有 `order:update`」，修 DEF-298 就會打開。
- 舊版 `POST /v2/payments`：要求 `order:create` 或 `booking:create`，**買家今天就能呼叫**（前端沒有使用，但 API 開放），付款方式還由呼叫端自填（`LINE_PAY`／`CREDIT_CARD`／`MOCK`）。

**紅燈實測**：啟用 Stripe 開關後，未修正的程式碼上三個入口（`/pay`、`/pay/fail`、`POST /v2/payments`）都回 **200**；對照組（未啟用 Stripe 時同一個買家 Mock 付款成功）通過。

### 2.4 DEF-300：已付款訂單被取消後卡在「退款中」

`OrderService.cancelOrder` 對 PAID 訂單會轉 CANCELLED → REFUNDING。但 `OrderStateMachine.canRefund` 只接受 PAID／CANCELLED：

- 退款端點 `POST /v2/orders/{id}/refund` 回 `E-5012 無效的退款狀態`；
- Stripe 的 `charge.refunded` webhook 把付款標成 REFUNDED，卻不動訂單；
- REFUNDING→REFUNDED 又只允許付款子系統轉換（DEF-245，使用者先前拍板含 ADMIN 都不可直接指定）。

唯一會把 REFUNDING 轉成 REFUNDED 的是舊版 `POST /v2/payments/refund`，但它只是 Mock，不呼叫 Stripe。所以這些訂單的錢沒有任何正規途徑退回。DEF-298 修好之前，一般買家根本到不了 PAID；修好之後，「付款後改變主意取消」就會直接撞上這裡。

**紅燈實測**：買家付款 → 取消（得到 REFUNDING）→ ADMIN 呼叫退款端點，得到 `422 E-5012`。

## 3. 修復

- **DEF-298**：付款三端點改為 `hasAuthority('order:create') or hasAuthority('order:update')`。只多放行 BUYER（唯一「有 `order:create`、沒有 `order:update`」的角色），原本能呼叫的角色一個都沒少；「付款屬於下單的一部分」也與舊版 `POST /v2/payments` 用 `order:create` 的既有慣例一致。能不能付「這一筆」仍由服務層的「本人或 ADMIN」檢查決定。退款、改狀態端點不動。
- **DEF-299**：`PaymentStateService.requireMockPaymentAllowed()` 是唯一的判斷——啟用 Stripe 時回 `422 E-6004 無效的付款方式`（既有錯誤碼，不需新增）。`mockPaymentSuccess`、`mockPaymentFailure`、舊版的訂單付款與訂房付款都在擁有權檢查之後呼叫它。判斷與回給前端的 `paymentProvider` 相同；前端在 Stripe 模式本來就不顯示 Mock 付款按鈕，所以畫面行為不變。
- **DEF-300**：`OrderStateMachine.canRefund` 加入 REFUNDING。退款端點與 Stripe 退款 webhook 因此都能完成這些訂單的退款。
- **測試固件**：`IntegrationTestConfiguration` 的 BUYER 拿掉 `order:update`，與生產一致。其他角色的差異見 §6（只登記）。

## 4. 測試

- `BuyerOrderEndpointAuthorizationTest`（新，單元，13 案例）：用**生產**的 `RolePermissionMapping` 產生 BUYER 權限，以 Spring Security 的 `DefaultMethodSecurityExpressionHandler` 對控制器方法上真實的 `@PreAuthorize` 求值。10 個「買家必須能呼叫」（建單、訂單列表、讀單、狀態時間軸、取消、付款狀態、三個付款端點、Stripe 回跳）＋3 個「買家不可呼叫」（兩個退款端點、改狀態）。不依賴整合測試的手抄清單。
- `BuyerOrderJourneyE2ETest`（改，5→6 案例）：固件對齊後既有的付款案例即為 DEF-298 的整合紅燈；新增「買家付款後取消 → REFUNDING → ADMIN 退款 → 訂單與付款皆 REFUNDED」。
- `MockPaymentUnderStripeIntegrationTest`（新，4 案例）：對照組（未啟用 Stripe 可付款）＋啟用 Stripe 後 `/pay`、`/pay/fail`、`POST /v2/payments` 都回 `422 E-6004`，訂單維持 CREATED、沒有成功（或失敗）付款紀錄。買家登入前先歸屬專用租戶，開關只設在該租戶上，不動共用的系統租戶。
- `PaymentStateServiceTest`（新增 1 案例）：REFUNDING 訂單收到 Stripe 退款 webhook 後轉 REFUNDED。
- `PaymentServiceOwnershipTest`／`PaymentServiceConcurrencyTest`：補 `@Mock PaymentStateService`（新依賴）。
- **被改正的既有測試（第一次全量驗證抓到，兩者都把缺陷行為當成正確行為鎖住）**：
  - `OrderControllerE2ETest` API-M06-007：原本斷言「BUYER 呼叫 `PATCH /status` 得 422（狀態機拒絕）**而非 403**」，只在手抄清單多給 BUYER `order:update` 時成立，而且斷言的正是「買家能通過授權改自己訂單狀態」。改為斷言 403 `E-1007` 且訂單仍為 CREATED；狀態機的非法轉換由既有的 `OrderServiceTest.updateOrderStatus_invalidTransition_throwsE5001` 涵蓋。
  - `M07PaymentMockIntegrationTest`（6 案例失敗）：setUp 把 `FeatureToggleService.isFeatureEnabled(anyString())` 一律 mock 成 true，等於在「已啟用 Stripe」下斷言 Mock 付款成功——正是 DEF-299。這個類別測的是 Phase 1 Mock 模式，改為明確把 `STRIPE_PAYMENT_ENABLED` 設成 false（其他功能維持 true）。兩個退款案例原本以買家身分退款（買家自己退款），改由 ADMIN 執行。
- **紅燈先行（每一項都是把修正暫時拿掉、只留新測試）**：
  - 權限單元測試：拿掉控制器修正 → 當時 11 案例中 3 個失敗，正好是三個付款端點；8 個（含 3 個「不可呼叫」）照常通過。之後補上的 `getOrders`／`getOrderLogs` 兩案例與本輪修正無關（原本就放行），沒有做紅燈。
  - DEF-299：拿掉兩個服務檔的修正 → 3 個「啟用 Stripe」案例都得到 200，對照組通過。
  - DEF-300：未修正時整合案例在退款那一步得到 `422 E-5012`（付款、取消兩步通過）；拿掉 `canRefund` 修正 → webhook 單元案例失敗。
  - 每次都以 `git stash` 暫存修正、跑完 `git stash pop`，並用 grep 確認修正確實還原。

## 5. 驗證結果

- **第一次全量** `mvn -o clean verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1811**（+12）全過；整合 **595**（+5）中 **7 個失敗**，`BUILD FAILURE`。7 個全部落在 §4 所列的兩個既有測試（`OrderControllerE2ETest` 1 個、`M07PaymentMockIntegrationTest` 6 個），逐一讀過失敗訊息（422 E-6004 與 403），都是本輪修正讓「把缺陷行為當正確行為」的斷言失效，不是回歸。checkstyle 0 violations、PMD 通過。
- 改正兩個測試並補上權限守門的 `getOrders`／`getOrderLogs` 兩案例後，先單獨跑兩個整合類別（12/12、8/8）與權限守門（13/13）。
- **第二次全量** `mvn -o verify`：單元 **1813**（+14：權限守門 13、webhook 1）／整合 **595**（+5：Stripe 防護 4、買家旅程 1）／0 failures／0 errors／0 skipped；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（9 分 53 秒）。
- 每次紅燈用 `git stash` 暫存的修正都已 `git stash pop` 還原並 grep 確認；`git status` 只有本輪預期的檔案。
- 未變更 entity／migration，未跑 `make validate-schema`；未跑 E2E／`make validate-release`。前端沒有改動；API 回應形狀不變（僅新增「啟用 Stripe 時 Mock 付款回 422 E-6004」這個錯誤情境，前端在 Stripe 模式不會呼叫這些端點）。

## 6. 只登記、未修的項目

- **DEF-301**：賣家以 `PATCH /v2/orders/{id}/status` 把訂單改成 CANCELLED 時，不釋放預扣庫存、不退還優惠券額度；`POST /cancel` 則只允許買家本人或 ADMIN。PRD TC-M05-015 明定「商家取消未付款訂單 → 庫存釋放」。前端賣家頁沒有取消按鈕，今天只有直接打 API 才會走到。
- **DEF-302**：沒有「未付款訂單逾時取消」。建立後沒付款的訂單會一直佔著預扣庫存與優惠券額度。DEF-298 修正前，正式環境每個嘗試付款的買家都會留下一筆（若正式環境已有買家流量，可能已經累積；本輪查不到正式資料庫）。逾時長度與是否自動取消屬產品決策。
- **DEF-303**：付款／退款路徑其餘的不一致（皆讀程式碼確認，未修）：
  - 前端訂房流程沒有任何付款步驟；訂房唯一的付款入口是舊版 `POST /v2/payments`（Mock）。DEF-299 之後，啟用 Stripe 時訂房完全無法付款——但前端本來就不付訂房的款，畫面行為不變。
  - 舊版 `POST /v2/payments` 的訂單付款不扣庫存（`PaymentStateService` 的兩條付款成功路徑都會扣）。
  - 舊版 `POST /v2/payments/refund` 對 Stripe 付款只做 Mock 退款：把付款標成 REFUNDED，卻沒有呼叫 Stripe（要求 `order:update` 或 `booking:update`＋訂單本人或 ADMIN；買家兩個權限都沒有，實際上只有 ADMIN 與自己下過單的店主呼叫得到）。
  - 買家取消 CONFIRMED（已付款）訂單只到 CANCELLED、不進 REFUNDING；M11 文件的寫法（「PAID 者轉 REFUNDING」）可兩解，沒有動。
  - 取消已付款訂單時狀態紀錄寫「Automatic refund triggered」，實際上沒有觸發任何退款，需要 ADMIN 以退款端點處理。PRD 寫的是「若已支付，觸發 M04 退款流程」；是否要自動退款屬產品決策。
  - DEF-044（已付款訂單取消／退款時回補庫存）已以退貨流程結案，但退貨只涵蓋 DELIVERED／COMPLETED，**出貨前取消已付款訂單**這半邊沒有回補庫存。
- **DEF-304**：整合測試的手抄權限清單與生產權限表的其餘差異。多給（整合測試放行、生產拒絕）：SELLER 的 `cart:*`、`order:create`、`media:*`、`notification_template:*`；HOST 的 `booking:create`／`booking:cancel`；STORE_OWNER 與 ADMIN 的 `cart:*`；ADMIN 的 `order:delete`、SUPER_ADMIN 的 `order:delete`／`tenant:create`（這兩個碼不在枚舉內，生產任何角色都拿不到）。少給的更多（STORE_OWNER 少 30 個、ADMIN 少 36 個、SUPER_ADMIN 少 44 個）。多給的部分牽涉「賣家／店主帳號能不能購物」等產品定性，本輪只對齊了 BUYER。根治做法是讓整合測試直接用真實 `RolePermissionMapping`，但可能連帶讓一批依賴手抄清單的測試失敗，需要逐一判斷是測試假設錯還是生產缺陷。

## 7. 決策與已知限制

- **權限寫法（我做的決定）**：用 `order:create or order:update`，而不是只寫 `order:create`。只寫 `order:create` 會讓 SELLER 失去呼叫權（SELLER 沒有 `order:create`、也建立不了訂單，實際上影響不到任何流程），但為了「只多不少」選了前者。
- **行為變更（我做的決定，非你拍板）**：啟用 Stripe 後，三個 Mock 付款入口一律回 `422 E-6004`，連 ADMIN 也一樣。這與 DEF-245 你拍板的「任何人（含 ADMIN）都不可偽造 PAID」一致。
- **未以真實瀏覽器走過**：DEF-298 的證據是生產權限表的機械求值、git 歷史、以及與生產權限一致的整合測試（真實 Spring Security filter chain）；沒有啟動真實前後端用瀏覽器走一次結帳。
- **正式環境資料**：若正式環境已上線且有買家，可能累積了大量付不了款的 CREATED 訂單（佔著預扣庫存，見 DEF-302）。本輪無法查詢正式資料庫，不知道是否存在。
- **Stripe 開關以呼叫者所在租戶判斷**：一般買家即系統租戶。這是既有設計（`paymentProvider`、Stripe 結帳都用同一個判斷），本輪沿用、沒有更動。

## 8. 後續

- `myTodoList.md` 三項人工待辦狀態不變，仍在使用者手上。**啟用 Stripe 前**，本輪的 DEF-299 修正必須已部署——否則任何買家都能用舊版 `POST /v2/payments` 不付錢把訂單標成已付款。
- 建議把「用一般買家帳號（非管理員、非店主）實際走一次：加入購物車 → 結帳 → 付款成功 → 取消 → 管理員退款」列入手動驗證。
- DEF-292、294、295、297 仍為「不排入排程」，等使用者決定；本輪新增的 DEF-301～304 同樣等使用者決定。
