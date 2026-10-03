# Sprint 241 Plan — 文件對齊（1/3）：M03 會員與 M04 購物車的 API 規格依實作改寫、路由漂移守門，並修復購物車錯誤輸入回 500（DEF-335）

**Sprint**: Sprint 241
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（順序為我的安排，屬推論）

[SPRINT_240_PLAN.md](SPRINT_240_PLAN.md) §8 排定下一步是**文件對齊**——使用者在 Sprint 232 結尾選了「比照 Sprint 203」（PRD 內文不動只加修訂註記、API／SRD 依實作改寫、FRD 補 M06／M07／M09 章、TC 文件加現況聲明不重寫；見 `DEF-322`、`DEF-323` (b)）。工作量大，**我拆成三輪，每輪都能獨立驗證**：

| 輪 | 內容 |
|----|------|
| **Sprint 241（本輪）** | **M03 會員與 M04 購物車**的 API 規格依實作改寫＋路由漂移守門 |
| Sprint 242 | M05 訂單、M06 訂房的 API 規格（含付款端點、商家端訂房列表、Sprint 235～240 的契約變更）；把它們納入路由守門 |
| Sprint 243 | SRD（訂單狀態圖、token 效期與 Cookie、寄信 Mock 的過時說法）、FRD 補 M06／M07／M09 章、PRD 修訂註記、TC 文件現況聲明、環境變數文件（只改文件；compose 傳遞環境變數屬 Docker 設定，依 CLAUDE.md 須使用者明確指示） |

先做 M03／M04 的理由：Sprint 235～240 對這兩個模組的契約改動最多（登入的有效角色、購物車的多店鋪模型），而且是前端與 API 使用者最先碰到的。

### 1.2 方法

- **逐端點讀 Controller、DTO、Service**，文件裡的每個宣稱（欄位、限制、狀態碼、錯誤碼、TTL、格式）都對照程式碼；M03 另讀 `JwtTokenService`、`LoginAttemptService`、`RefreshTokenService`、`LoginRateLimitFilter`、`UserPrivacyService`、`OAuthService`。
- **形狀與行為的宣稱，用真實服務驗證，不憑讀碼**：新增 `CartRealStackIntegrationTest`（真實 PostgreSQL＋真實 Redis＋完整 HTTP／JWT／權限鏈），驗證空購物車、單店鋪分組、總件數、重複加入的形狀與累加行為。
- **路由有沒有記載，用機械比對**：新增 `ApiRouteDocDriftTest`。

### 1.3 查出的差異（文件 vs 實作）

**M04 購物車**（v1.0 是 Sprint 4 的 4 個端點與單一店鋪的心智模型）

| 差異 | 說明 |
|------|------|
| 端點 | 列了**不存在**的 `GET /cart/items`；漏了 `DELETE /cart`（清空）、`GET /cart/count`、`POST /cart/apply-promo`、`DELETE /cart/promo`、`GET /cart/validate-promo`（實作共 9 個端點） |
| 狀態碼 | 加入購物車寫 `201`，實作是 `200` |
| 錯誤碼 | 數量驗證寫 `E-5006`，實作是 `E-9000`（帶 `errors[]`）；`E-5006` 是結帳時的「無效的數量」（422） |
| 多店鋪 | 完全沒有：Sprint 237 的 `stores[]`、`storeId`／`storeName`、優惠券與運費以店鋪為單位、`E-5020`；Sprint 239 的 `storeActive` |
| `cartItemKey` | 實際格式是 `listingId[:skuId]`，**日期不是 key 的一部分**（同一個房源再加入會累加數量、日期以最後一次為準）。`CartController` 與 `RedisCartService` 的註解都寫成含日期——**註解已改對** |
| 回應封包 | 舊的數字 `code`／巢狀 `error` → 扁平封包（見 [API_Error_Codes.md](../02_architecture/API_Error_Codes.md)） |

**M03 會員與權限**（v1.0 §1～§5 是 2026-04-09 的原文，§6～§9 是 Sprint 204 依實作撰寫、本輪未動）

| 差異 | 說明 |
|------|------|
| Token | Access **15 分鐘**（文件 30 分）、Refresh 預設 **7 天**（文件 30 天；compose 設 30）、`expiresIn` 是**毫秒**（文件是秒）、Refresh Token **不是 HttpOnly Cookie**（後端沒有任何 Cookie 處理，前端存 `localStorage`）、JWT claim 是 `role`／`tenantId`（文件 `userType`／`roles`）、Refresh Token 帶隨機 `jti`（`DEF-315`）、沒有 `type` claim（`DEF-323` (b)） |
| 停用帳號 | 登入回 `401 E-1001`（與帳密錯誤同一個回應），文件寫 `403 Account suspended` |
| 缺漏的行為 | 登入鎖定（15 分鐘**固定視窗**內累計超過 5 次，第 6 次起 `E-1004`；`DEF-220`／`DEF-293`）、Refresh Token 輪替與重放偵測（`DEF-219`／Sprint 213）、登出的兩種行為（帶 token 只撤銷那一顆；不帶撤銷全部）、註冊與登入的限流（每 IP、每路徑 30 次／分，不含換發與 OAuth）、**有效角色（Sprint 240）** |
| 缺漏的端點 | OAuth 登入與連結（`POST /v2/auth/oauth/login`、`/link`）、資料匯出（`GET /me/data-export`）、自助刪除帳戶（`DELETE /me`）——共 4 個 |
| `GET /me` | `tenants[].role` 是使用者角色不是店鋪角色（文件範例的 `OWNER` 不存在）；只列 `users.tenant_id` 指向的那一家 |
| 錯誤碼表 | 文末是 v1.0 的 `E-3001`～`E-4001`（早已標「請勿引用」），換成實際的常見錯誤碼 |

### 1.4 寫文件時發現的實作缺陷（先實測，再決定修或登記）

**`DEF-335` 🟡：購物車對三種使用者輸入錯誤回 HTTP 500（本輪修復）。** 讀 `RedisCartService.addItem` 寫錯誤碼那一節時發現：刊登項目不存在、ROOM 沒給日期、退房不晚於入住，都丟通用的 `IllegalArgumentException`；`GlobalExceptionHandler` 沒有專屬處理，落入 `handleGenericException`，回 `500 E-9900「發生未預期的錯誤」`，還在日誌留一筆 ERROR。**v1.0 規格寫的契約本來就是 `E-3000` 404／`E-4003` 400／`E-4004` 400——是實作漂移，所以修實作、不改文件。**

- **先實測（紅燈）**：`CartRealStackIntegrationTest` 在未修復的程式上 4 個案例 3 個回 **500**（唯一綠的是合法輸入的對照）。
- **為什麼 200+ 個 Sprint 沒發現**：`CartControllerE2ETest` 用的 `IntegrationTestConfiguration` 把 `RedisCartService` 換成記憶體版的 mock，mock 不會丟出真實服務的例外；`RedisCartServiceTest` 把 `IllegalArgumentException` 當成預期（單元測試固定的是缺陷本身）；前端只送合法輸入。我自己的第一個探針也打到了 mock（得到 `200`），改用不匯入該設定的真實服務才重現。
- **修法**：三處改丟 `BusinessException`（`E-3000`、`E-4003`、`E-4004`，皆為既有錯誤碼）；三個單元測試改為斷言錯誤碼。`IllegalArgumentException` 在主程式碼其餘的拋出點（`StripePaymentGateway` 的內部防呆、`OAuthProvider` 的死碼）都不是使用者輸入，**已逐一確認，沒有同型缺陷**。

**`DEF-336` 🟢：清空購物車不清除已套用的優惠券標記（登記，未修）。** `RedisCartService.clearCart` 只刪項目的 Hash，優惠券標記是另一個鍵（`cart:promo:{user}:{tenant}:{store}`，TTL 30 天）沒被刪。**真實 Redis 實測**：套用優惠券→`DELETE /cart`→重新加入同一家店鋪的商品，購物車又顯示「已套用 …、折 20」。每次讀取都重新驗證（失效就退回 0 折扣），結帳成功會清掉該店鋪的標記，所以不會多給折扣，只是顯示上與「清空」的直覺不一致；暫時探針已移除，**不為缺陷寫永久測試**。

### 1.5 更正與偽陽性

- 我原本要在 `CartControllerE2ETest` 加探針驗證 `DEF-335`，得到 `200`——那是 mock 的回應，不是真實行為（見 §1.4）。已還原，改寫成真實服務的測試。
- 我第一版的路由守門「缺漏檢查」太鬆（文件任何地方提到路由就算有記載），**用文件突變驗證才發現**：拿掉端點章節標題後測試仍綠，因為資料模型標題（`#### ApplyPromoRequest — \`POST …\``）也提到同一條路由。已收緊成只認「端點章節標題」與 `- **端點**:` 行，並放寬章節編號寫法（`4.2b` 也算）；5 個文件突變全部被抓到（§4）。

## 2. 使用者決策與需要使用者知悉的行為變更

本輪沒有新的使用者決策（依 Sprint 232 Q4「比照 Sprint 203」）。行為變更：

1. **購物車三種使用者輸入錯誤從 `500 E-9900` 變成 `404 E-3000`／`400 E-4003`／`400 E-4004`**（API 使用者可見；前端只送合法輸入，不受影響）。
2. M03、M04 規格文件改寫（文件，不動資料）。
3. 新增守門測試：日後有人新增、刪除或改名 M03／M04 的路由而沒更新規格，`mvn test` 會失敗。

## 3. 實作內容

**程式碼**
- `RedisCartService.addItem`：三處 `IllegalArgumentException` → `BusinessException`（`E-3000`／`E-4003`／`E-4004`）；`updateItem`／`CartController` 的 `cartItemKey` 註解改對（共三處）。

**文件**
- [API_M04_Cart.md](../02_architecture/API_M04_Cart.md) → **v2.0**（全文改寫，9 個端點）。
- [API_M03_Auth.md](../02_architecture/api/API_M03_Auth.md) → **v2.0**（§1～§5 改寫；新增 §10～§13、JWT 規格、常見錯誤碼；§6～§9 不動）。
- [API_Index.md](../02_architecture/API_Index.md) → v1.2（M03 補四個端點、新增 M04 區塊、完整性揭露）。

**測試**
- `ApiRouteDocDriftTest`（新，單元）、`CartRealStackIntegrationTest`（新，真實 PostgreSQL＋Redis）、`RedisCartServiceTest`（三個斷言改為錯誤碼）。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元 | `ApiRouteDocDriftTest`（2）：Controller 的每條路由都有「宣告行」、文件宣告的每條路由都存在（涵蓋 `AuthController`＋`OAuthController`→M03 共 13 條、`CartController`→M04 共 9 條）；守門本身有東西可守。`RedisCartServiceTest`：三個錯誤輸入斷言 `BusinessException` 與錯誤碼 |
| 真實 PostgreSQL＋Redis＋完整 HTTP 鏈（+8） | `CartRealStackIntegrationTest`：不存在的刊登項目→404 `E-3000`、ROOM 沒給日期→400 `E-4003`、退房不晚於入住→400 `E-4004`、合法輸入照常（對照）；空購物車形狀（`stores: []`、運費／折扣／應付金額 0）、單一店鋪分組（項目帶 `storeId`／`storeName`／`storeActive`、`stores[0]` 的件數與金額、頂層合計一致）、`/cart/count` 是總件數、重複加入同一個刊登項目累加數量且 ROOM 日期以最後一次為準 |

### 突變驗證（Rule 9：測試必須在被守的東西被拿掉時失敗）

**程式碼（4 個，`RedisCartServiceTest`＋`CartRealStackIntegrationTest`，39 個測試）**

| # | 突變 | 結果 |
|---|------|------|
| MC1 | 不存在的刊登項目改回丟 `IllegalArgumentException` | 2 個轉紅（單元＋HTTP） |
| MC2 | ROOM 沒給日期改回丟 `IllegalArgumentException` | 2 個轉紅 |
| MC3 | 退房不晚於入住改回丟 `IllegalArgumentException` | 2 個轉紅 |
| MC4 | 不存在的刊登項目丟 `BusinessException` 但錯誤碼改成 `E-9000`（400） | 2 個轉紅（錯誤碼斷言守住） |

**文件（5 個，對 `ApiRouteDocDriftTest`；改文件→跑守門→還原）**

| # | 突變 | 結果 |
|---|------|------|
| DM1 | 拿掉 M04 `POST /cart/apply-promo` 的端點章節標題 | 抓到：缺少端點 |
| DM2 | M04 多宣告一個不存在的端點章節（`GET /v2/cart/items`，章節編號 `4.2b`） | 抓到：宣告了不存在的端點 |
| DM3 | M04 `PUT …/items/{cartItemKey}` 的路徑變數改名成 `{key}` | 抓到：缺少＋多出各一 |
| DM4 | 拿掉 M03 資料匯出的 `- **端點**:` 行 | 抓到：缺少端點 |
| DM5 | 前綴碰撞：拿掉 `GET /v2/cart` 的章節標題（`GET /v2/cart/count` 還在） | 抓到：缺少端點（路徑比對是精確的，`/count` 蓋不過 `/cart`） |

**第一次文件突變有 2 個存活（DM1、DM5），原因是守門太鬆（§1.5）——這是用突變驗證才抓到的我自己的測試缺陷**，收緊後重跑全部通過。程式碼突變每次都先備份、等 class 比源碼新才跑、還原後以 `cmp` 與 `sha256` 確認逐位元組一致。

## 5. 驗證結果

- **後端全量**：`mvn -o clean verify` **BUILD SUCCESS（15 分 22 秒）**——單元 **2076**（+2：`ApiRouteDocDriftTest`）／整合 **746**（+8：`CartRealStackIntegrationTest`）／**0 失敗**／0 略過。
- **`make validate-schema-doc`**：對乾淨 PostgreSQL 套用 **89** 個 Flyway 遷移（本輪沒有新遷移）通過。
- **前端**：沒有改動（`frontend/src` 沒有比上次建置新的檔案），沿用 Sprint 239 的建置。
- **真實後端 E2E**：`E2E_GATE_SKIP_BUILD=1 make validate-e2e`（JAR 是上面 verify 剛建好的版本）**139 個測試：135 通過／4 略過／0 失敗（3.9 分鐘）**；沒有 flaky、沒有 did not run；後端以 `ddl-auto=validate` 啟動確認 entity 與 Flyway schema 對齊。那 4 個略過是既有基準。**跑守門期間我沒有改任何檔案**（Sprint 240 修復前那次有 2 個既有規格失敗、原因未查明，見 [SPRINT_240_PLAN.md](SPRINT_240_PLAN.md) §5；這次與修復後那次都沒有再發生）。
- **突變驗證**：見 §4（程式碼 4 個＋文件 5 個，全部被抓到）。
- **push 與雲端 CI**：（push 後於回填 commit 補上）

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **路由守門只守「端點有沒有記載」**：請求與回應的欄位形狀、狀態碼、錯誤碼的細節仍要人工維護（錯誤碼對照表另有 `ErrorCodeDocDriftTest`）。M04 的形狀宣稱由 `CartRealStackIntegrationTest` 守住；M03 的回應形狀這輪是讀碼對照，**沒有逐一用真實請求驗證**（登入鎖定、換發重放等行為各自已有 Sprint 213／214／219 的真實 Redis 測試）。
2. **M03 §6～§9（Sprint 204）沒有重新核對**：它們在 Sprint 204 依實際行為撰寫，本輪只是原樣保留並納入路由守門。
3. **M05 訂單、M06 訂房與其餘模組的 API 規格仍是舊的**（Sprint 242 處理）；`API_Index.md` 仍不是完整端點清單（`DEF-286`）。
4. **OAuth 兩個端點記載的是程式碼行為**：從未對真實 Google／GitHub 驗證（Sprint 215 紀錄），文件已明載。
5. **店主帳號沒有購物車權限**：只有 `BUYER`（與 `SUPER_ADMIN`）持有 `cart:*`，所以一個已成為店主的帳號不能用同一個帳號購物。**這是 PRD §7.3 權限矩陣的設計**（M04 購物車只有 Buyer 是 RWD，StoreOwner／StoreStaff／Seller／Host／Admin 都是 `—`；M05 買家訂單也只有 Buyer 是 RX），不是缺陷、也不需要決定。（本計畫書最初寫「是否符合產品預期未確認（PRD 的角色矩陣沒查）」，是我當時沒查 PRD，已更正。）

### 6.2 待使用者決定／確認（累積）

| 項目 | 內容 | 我的建議 |
|------|------|----------|
| Sprint 240 `DEF-333` | 系統租戶下既有的商品／房源／貼文（擁有者不能再編輯）；部署前先跑 [SPRINT_240_PLAN.md](SPRINT_240_PLAN.md) §6.2 的預覽 `SELECT` | 先看筆數；有資料的話由管理員下架（不刪除） |
| Sprint 239 §6.1 第 1 點 | 店鋪停權前已成立、尚未付款的訂單，是否也擋掉付款（`E-2010`） | 擋掉 |
| 本輪 §6.1 第 5 點 | ~~店主帳號沒有購物車權限，是否符合產品預期~~ **已查證：是 PRD §7.3 權限矩陣的設計**，不需要決定 | — |
| Sprint 234 §6.4 | 沒有店鋪的使用者的限流單位（PRD 未定義，我的工程決策） | 同意 |
| Sprint 235～237 §2 | `V87`／`V88` 會改既有資料庫的使用者角色與訂房租戶 | 同意（部署前先跑預覽 `SELECT`） |

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果。
2. **Sprint 242**：M05 訂單與 M06 訂房的 API 規格依實作改寫（含 `OrderPaymentController` 6 個端點、`BookingPaymentController` 3 個、`CheckoutController`、`GET /v2/dashboard/bookings`、`PATCH /v2/orders/{id}/status`、`Idempotency-Key`、Sprint 235～240 的契約變更：`storeId`、`E-2010`、`E-5020`、訂單與訂房歸屬店鋪），並把它們納入 `ApiRouteDocDriftTest`。
3. **Sprint 243**：SRD／FRD／PRD／TC／環境變數文件（見 §1.1）。
4. 之後：店鋪成員管理前端（`DEF-321` (a)）→ CMS 卡片連結（`DEF-321` (c)）→ 通知事件（`DEF-318`）→ 店鋪前台 `/stores`（`DEF-321` (b)）。
