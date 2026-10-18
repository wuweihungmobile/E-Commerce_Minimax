# Sprint 285 Plan — 真實雙角色訂單生命週期＋評價真實瀏覽器實測

## 1. 起點與範圍

### 1.1 起點

Sprint 284 總結列出 3 個待決方向，使用者以 `AskUserQuestion` 選擇**「真實跨角色實測」**，取代純讀碼複核。

動機：S77/78、S284 三輪純讀碼複核皆無新發現，邊際收益可能已達天花板；DEF-337／DEF-355／DEF-356 皆是透過真實 `make up` 瀏覽器操作才被發現，純讀碼完全看不到。

### 1.2 方法

比照既有配方（真實 Postgres／Redis＋打包 JAR＋真實瀏覽器）：

- `postgres:18-alpine`（55432）＋`redis:7-alpine`（56379），皆 DOCKER_POLICY 核准映像
- `mvn package -DskipTests` 打包 JAR → `java -jar --spring.profiles.active=test`（`ddl-auto=validate`，Flyway 建表）
- `npm run build && npm run start`（`NEXT_PUBLIC_API_URL=http://localhost:8080/api`，**不可含 `/v2`**）
- 一次性 Playwright spec 暫時放入 `frontend/e2e/`，跑完立即刪除並 `git status` 確認乾淨
- 直接重用既有 helpers（`e2e/helpers/auth.ts`／`mailbox.ts`／`store.ts` 的 `seedStore`），不重寫開店流程

### 1.3 本輪選定流程

兩個真實角色（賣家／買家）走一次完整**商品訂單生命週期＋評價**：

1. 賣家：`seedStore`（RETAIL_ONLY＋1 件商品，走真實 register→verify email→apply→admin approve→建立商品）
2. 買家：註冊→瀏覽賣場→加入購物車→結帳付款（Mock）
3. 買家通知頁核對 `ORDER_CONFIRMED`／`ORDER_PAID`／`PAYMENT_SUCCESS`
4. 賣家 dashboard：確認訂單（→CONFIRMED）→建立物流單（→SHIPPING）
5. 買家通知頁核對 `ORDER_SHIPPED`
6. **探針 A**：訂單仍在 SHIPPING（未 DELIVERED，UI 的「寫評價」按鈕此時不會出現）時，買家直接呼叫 `POST /v2/reviews` API——驗證 `ReviewService.verifyPurchase`（讀碼：`backend/.../core/review/ReviewService.java:683`）是否真的不檢查訂單狀態
7. 賣家標記送達（→DELIVERED）→買家通知頁核對 `ORDER_DELIVERED`
8. 賣家標記完成（→COMPLETED）→買家通知頁核對 `ORDER_COMPLETED`／`REVIEW_REQUEST`
9. 買家經正常 UI 提交評價 → 核對自己訂單頁與公開 `listings/[id]` 頁都正確顯示評分與內容
10. **探針 B**：賣家呼叫 `POST /{reviewId}/replies` API 回覆（讀碼：`ReviewList.tsx` 未渲染任何 reply 欄位，前端無此 UI）→ 重新載入買家訂單頁與公開 listing 頁，確認回覆有沒有在任何真實畫面出現過

### 1.4 假設與限制（可被使用者推翻）

- 探針 A／B 是直接呼叫 API，不是單純「使用者在 UI 上會做的操作」；目的是驗證「UI 隱藏按鈕/欄位是否只是前端裝飾、後端或其他畫面有沒有獨立把關／顯示」——這正是 DEF-331(b) 當年被發現的方式。判斷結果時仍以**真實畫面有無變化**為準，不只看 API 回應碼。
- 本輪只涵蓋商品（PRODUCT）訂單生命週期，不含訂房（ROOM）；訂房入住/退房/no-show 已有真實測試覆蓋（S245/246）。
- 若 Mock 付款在 `test` profile 被功能開關擋下，依現有 E2E 慣例（開關預設允許 Mock 付款於測試環境）處理；若不然則視為本輪的額外發現。
- 審視 FRD §6D 後發現 M08（評價與社交互動）「商家/房東回覆評價」為文件明載、後端已完整實作（`ReviewReplyService`、DB 唯一索引）的功能，但目前已知 DEF-373（回覆授權邏輯三處不一致，未修復）——探針 B 若證實「連 UI 都完全沒有」，屬於 DEF-373 之外的新發現，另行登記。

## 2. 執行紀錄

真實環境：`postgres:18-alpine`(55432)＋`redis:7-alpine`(56379) docker 容器、`mvn clean package -DskipTests` 打包 JAR（⚠️ 第一次 `mvn package`（無 `clean`）靜默未重建 JAR，無任何錯誤訊息但檔案 mtime 未變——與 [[mvn-phantom-build-success-after-failure]] 同類陷阱，之後一律加 `clean`）、`java -jar --spring.profiles.active=test` 指向真實 DB/Redis、`npm run build && npm run start`。

一次性 Playwright spec（`frontend/e2e/sprint285-real-cross-role-walkthrough.spec.ts`，跑完即刪）完整走了一次：`seedStore`（RETAIL_ONLY＋1 商品）→買家加入購物車→結帳付款（Mock）→通知頁核對 `ORDER_PAID`/`PAYMENT_SUCCESS`→賣家確認訂單→通知頁核對 `ORDER_CONFIRMED`→賣家建立物流單出貨→通知頁核對 `ORDER_SHIPPED`→賣家標記送達→通知頁核對 `ORDER_DELIVERED`→買家經真實 UI 提交評價→賣家標記完成→通知頁核對 `ORDER_COMPLETED`/`REVIEW_REQUEST`→買家 `/reviews/product/[id]` 頁核對評價顯示。**全程 3 次失敗排查皆為測試腳本自身問題**（selector 撞衫、`createOrder` 其實吃購物車而非 body 帶 listingId、JSON 欄位名 `reviewId` 非 `id`），修正後最終全綠，過程中**主線商品訂單生命週期的 7 種通知全數正確顯示，是乾淨結果**。

## 3. 發現與結論

### 3.1 🔴 DEF-390（新登記）：評分統計端點對真實 Redis 必定 500（已修復，見 §4）

**真實瀏覽器screenshot 直接看到**：`/reviews/product/[id]` 頁同時顯示「尚無評價」**與** 2 則真實評價卡片（矛盾畫面）。追查後端日誌找到根因：

```
org.springframework.data.redis.serializer.SerializationException: Cannot serialize
  at ...JdkSerializationRedisSerializer.serialize
  at ...ReviewService.getRatingStats（@Cacheable(value = "ratingStats", key = "#listingId")）
```

`RedisConfig.java` 的 `redisCacheManagerBuilderCustomizer` 只替 `dashboardStats` 這個 cache name 註冊了 Jackson JSON 序列化器；其餘 cache name（含 `ratingStats`）沒有專屬設定，會落回 Spring Boot 預設的 `JdkSerializationRedisSerializer`——而 `ReviewDto.RatingStats`（`@Data @Builder`，無 `implements Serializable`）無法被 JDK 序列化，**每一次**呼叫 `GET /v2/reviews/listing/{id}/stats` 都會在寫入快取時拋例外，變成 500（`E-9900`）。前端 `ReviewList.tsx` 用 `.catch(() => null)` 吞掉這個錯誤，直接當作「沒有統計」顯示「尚無評價」——這就是畫面矛盾的來源。

**為什麼 285 輪 Sprint 都沒抓到**：`IntegrationTestConfiguration` 把 Redis 換成 mock（見 [[integration-test-config-mocks-redis]]），mock 不會真的序列化，結構性看不到這個問題；前端又把錯誤吞掉、不會顯示任何異常訊息，手動點畫面也只會覺得「這個商品還沒人評價」，不會意識到是 bug。**純讀碼也看不出來**——程式碼單獨看完全合理，只有真的對真實 Redis 跑過一次才會現形。

**實際影響**：只要正式環境（或任何有真實 Redis 的環境）呼叫過一次這支 API，`/reviews/product/[id]` 頁的星等與則數統計永遠是「尚無評價」，即使商品其實有大量評價；且每次呼叫都在後端留下一筆 500 錯誤 log。

### 3.2 🔴 DEF-391（新登記）：`ReviewService.verifyPurchase` 不檢查訂單狀態，可在訂單剛建立（未付款）時就送出評價（已修復，見 §4）

**探針 A 實測確認**：買家建立一筆訂單（狀態 `CREATED`，**尚未付款**）後，直接呼叫 `POST /v2/reviews` 帶該 `orderId`，**回應 200，評價建立成功**，且在真實畫面（`/reviews/product/[id]`）上與正常評價並列顯示、外觀完全無法區分。

讀碼確認（`ReviewService.java:683` `verifyPurchase`）：只驗證「訂單屬於本人」與「訂單項目含此 listing」，從未檢查訂單狀態。前端 `orders/[id]/page.tsx:194-195` 的 `canReview` 已限制「只有 `DELIVERED`／`COMPLETED` 才顯示撰寫評價按鈕」——後端沒有比照把關，純粹是前端裝飾性限制，任何人用瀏覽器開發工具或直接呼叫 API 就能繞過。

**定性**：這不是產品決策（沒有人會希望「訂單剛建立、錢都還沒付」就能被評價），是 DEF-331(b)（Sprint 251，修了「根本沒驗證購買證明」）**修得不完整**遺留的縫隙——補齊到與前端已確立的 `DELIVERED`／`COMPLETED` 門檻一致即可，不需使用者裁決方向。

### 3.3 🟡 DEF-392（新登記，未修復，需使用者決定優先順序）：賣家回覆評價功能「有後端、零前端」

讀碼＋探針 B 雙重確認：
- 後端功能完整：`POST/GET /v2/reviews/{id}/replies`（`ReviewReplyService`）運作正常（探針 B 實測 200，成功建立回覆）。FRD §6D 明文列為既有功能（「賣家/房東：回覆評價」），`review_replies` 表有唯一索引。
- **前端完全沒有對應 UI**：`frontend/src/components/reviews/ReviewList.tsx` 不渲染任何 reply 欄位；`frontend/src/app/dashboard/` 底下沒有任何評價管理頁面（FRD 寫的「查詢自己租戶名下的評價管理列表」「標記已/未處理」也都沒有 UI）。探針 B 實測：回覆建立成功後，重新整理買家的 `/reviews/product/[id]` 頁與 `/orders/[id]` 頁，回覆文字**完全沒有出現在任何畫面**。
- 與既有 DEF-373（回覆授權邏輯三處不一致）不同範疇：DEF-373 假設「這功能本來就能被用到」在談授權細節；本項是「這功能目前沒有任何人能透過產品介面用到」，範疇更大。
- **不unilaterally動工**：補完整個賣家評價管理 UI（回覆、標記已讀、管理列表）是有感的新 UI 工作量，不是單純 bug fix，優先順序留給使用者決定。

### 3.4 確認為正確行為（非缺陷）：評價列表/統計需登入才能查

`GET /v2/reviews/listing/{id}` 與 `.../stats` 未帶 `Authorization` header 時回 401。核對 FRD §6D：「任何**登入者**可查詢評價列表」——文件本來就是這樣定的（登入才能看），不是公開瀏覽。唯一值得留意的衍生點：公開賣場頁 `/listings/[id]` 本身不含任何評分/評價摘要（純讀碼＋實測 `ratingOnListingPage` 計數為 0 共同確認），未登入訪客點擊「查看評價」會撞到登入門檻——這是否要調整屬產品/UX 取捨，不登記為 DEF，留待使用者未來若想重新檢視 M08（PRD 本列 P2）時一併考慮。

### 3.5 主線商品訂單生命週期：乾淨

`ORDER_PAID`／`PAYMENT_SUCCESS`／`ORDER_CONFIRMED`／`ORDER_SHIPPED`／`ORDER_DELIVERED`／`ORDER_COMPLETED`／`REVIEW_REQUEST` 七種通知，在兩個真實角色（賣家／買家）交替操作的真實瀏覽器＋真實 DB 全程中，全數正確觸發並正確顯示於買家通知頁——S250 的通知接線在真實跨角色場景下成立，未發現缺陷。

### 3.6 🟢 DEF-393（新登記）：`at-booking-payment-real.spec.ts` 斷言過時（與本輪評價修復無關，`validate-release` 跑 E2E 時意外發現）

`make validate-release` 第三次（act 全量 CI＋schema 皆過）的 E2E 關卡失敗，排查後確認與 DEF-390／391 完全無關：`E2E-BPAYR-02` 斷言「Stripe 未啟用時發起訂房結帳回 400 `E-6002`」，但 `PaymentStateService.initiateStripeCheckout` 的程式碼註解明載 **DEF-341（Sprint 279）** 早已把這個檢查改為 `FeatureToggleService.checkFeatureEnabled`（`E-2004`，403），這條 E2E 斷言從未同步更新。

**值得留意但未深究的旁支觀察**：查過 Sprint 281~284 的紀錄，「全量驗證」的敘述都只提到 `mvn -o clean verify`（後端單元＋整合測試），沒看到任何一輪提及 E2E／Playwright 結果——懷疑完整 `validate-e2e` 關卡可能有一段時間沒被真的跑過，才讓這條過時斷言活了 6 個 Sprint 沒被抓到。這只是本輪意外觀察到的旁支線索，沒有逐輪查證確認，僅此記錄供未來參考，不代表本輪有查過每一輪的實際執行細節。

**修復**：斷言改為期望 403／`E-2004`，補充註解連結 DEF-341。`at-m10-chat.spec.ts` 同批失敗但是既有已知的 STOMP 連線偶發 flaky（與本輪任何變更無關），重跑即過，不需修改。

## 4. 本輪異動

### 4.1 DEF-390 修復：`ratingStats` cache 註冊 Jackson JSON 序列化

- `RedisConfig.java`：`redisCacheManagerBuilderCustomizer` 新增 `withCacheConfiguration("ratingStats", ...)`，沿用與 `dashboardStats` 相同的 Jackson 序列化器（抽出共用 `jsonConfig`），TTL 比照設為 5 分鐘（新增常數 `RATING_STATS_TTL`）。
- `ReviewService.java`：`updateReview`／`deleteReview` 原本沒有 `@CacheEvict("ratingStats")`（只有 `createReview` 有）——序列化問題修好後，若不補上，更新評分或軟刪除評價會留下過期快取直到 TTL 到期，等於把「crash 掩蓋的新鮮資料」換成「真的會過期的舊資料」，因此順手補上（`allEntries=true`，因方法參數拿不到 listingId，SpEL 無法精準定鍵）。

### 4.2 DEF-391 修復：`verifyPurchase` 補齊訂單/訂房狀態門檻

- `ReviewService.java`：`verifyPurchase` 的訂單分支新增「必須 `DELIVERED` 或 `COMPLETED`」、訂房分支新增「必須 `CHECKED_OUT` 或 `COMPLETED`」，與前端既有 `canReview`（`orders/[id]/page.tsx`、`bookings/[id]/page.tsx`）完全一致。
- `ErrorCode.java`：新增 `E_1098`（訂單尚未送達）、`E_1099`（訂房尚未完成）。
- `GlobalExceptionHandler.java`：窮舉 `switch` 補上這兩個新碼（403 FORBIDDEN，與同函式內既有的 `E_1007` 同組）——**這個窮舉 switch 本身就是 Sprint 171（DEF-223）特意設計的防呆機制，新增 ErrorCode 忘記分類會直接編譯失敗**，而非靜默落入 500；本輪新增時確實立即編譯失敗，印證機制有效。

### 4.3 測試與驗證

- 新增 `ReviewRatingStatsRealRedisIntegrationTest`（`@TestPropertySource(spring.cache.type=redis)`，明確覆寫 `application-integration-test.yml` 全域的 `type=simple`，否則不會真的經過 Redis 序列化）：對真實 Redis 呼叫 `getRatingStats` 兩次，驗證不拋例外且第二次確實從快取讀回。
- `ReviewAuthorizationRealDbIntegrationTest` 新增 5 個案例（DEF-391）：訂單 `CREATED`／`SHIPPING` 應拋 `E_1098`、訂房 `CONFIRMED` 應拋 `E_1099`、訂單 `DELIVERED`／訂房 `CHECKED_OUT`（非僅 `COMPLETED`）應成功。既有 `seedOrder`／`seedBooking` 原本固定建 `COMPLETED`，改為可傳入狀態的多載，預設值不變（既有呼叫點不受影響）。
- `RedisCacheConfigTest` 新增 4 個案例（`TC-CACHE-R001~004`）比照既有 `dashboardStats` 案例：TTL 常數、`createReview`／`updateReview`／`deleteReview` 的 `@CacheEvict` 標注。
- **突變驗證**（備份→移除兩處修復→重跑→確認轉紅→還原→重跑確認轉綠）：移除 DEF-391 的狀態檢查後，3 個新測試正確失敗（`Expecting code to raise a throwable`）；移除 DEF-390 的 cache 設定後，`ReviewRatingStatsRealRedisIntegrationTest` 重現與生產環境日誌**完全相同**的 `SerializationException` 堆疊。還原後 86 個測試（7 個檔案）全數轉回綠燈。
- 真實瀏覽器複測：Sprint 285 的一次性 Playwright 腳本原始探針 A（CREATED 訂單送出評價）與統計頁「尚無評價＋2 則評價並存」畫面，皆是修復前用真實環境實測抓到；修復後的程式碼邏輯已由上述真實 DB／真實 Redis 整合測試覆蓋（腳本本身已依慣例刪除，不重跑瀏覽器複測，避免為同一組邏輯重複建置全棧環境）。
- `make validate-release`（act 全量 CI + schema 漂移 + E2E）：進行中，結果待補。

### 4.4 DEF-393 修復：過時的 E2E 斷言

- `frontend/e2e/at-booking-payment-real.spec.ts`：`E2E-BPAYR-02` 最後一個斷言由「期望 400 `E-6002`」改為「期望 403 `E-2004`」，與 Sprint 279（DEF-341）已修正的實際行為一致。
- 全域搜尋確認全部 E2E spec 僅此一處對 `pay/checkout` 端點做真實（非 `page.route` 攔截）呼叫，無其他同款落差。

### 4.5 `validate-release` 執行歷程（三次嘗試）

1. 第一次：Checkstyle 擋下（`ReviewService.verifyPurchase` NPath Complexity 300 > 200）→ 拆成 `verifyOrderPurchase`／`verifyBookingPurchase` 兩個方法。
2. 第二次：act 全量單元測試擋下兩項——`GlobalExceptionHandlerTest`（獨立維護的期望表未同步新碼 `E_1098`／`E_1099`）、`ErrorCodeDocDriftTest`（`API_Error_Codes.md` 未同步新碼）→ 補齊兩處文件／測試期望表，另順手補上 `API_M08_Review.md` 的對應章節（非測試強制，但文件完整性一致）。
3. 第三次：act 全量單元＋整合測試、schema 漂移皆過；E2E 關卡 2 個失敗——`at-m10-chat`（已知 flaky，見 [[at-m10-chat-stomp-e2e-flaky]]）、`at-booking-payment-real.spec.ts`（DEF-393，見 §3.6）→ 修復 DEF-393。
4. **第四次：全數通過**。Checkstyle 0 違規（unit＋integration job 皆同）；整合測試（failsafe）**863/863**（較 Sprint 283 基準 857 增加 6，對應本輪新增的 5 個 DEF-391 案例＋1 個 `ReviewRatingStatsRealRedisIntegrationTest`）；本機另於修復過程中單獨確認過全量 `mvn test`（純單元）**2285/2285**。E2E：**170 passed／4 skipped**（含先前失敗的 `at-m10-chat` 與已修復的 `at-booking-payment-real.spec.ts` 本輪皆綠），schema 漂移驗證通過。`make validate-release` 回報「完整測試程序全數通過（act + schema + E2E）；已寫 FULL 記錄，30 分內 git push 直接放行」。

## 5. DEF-392 後續：賣家評價管理 UI（未動工，待使用者裁決優先順序）

已於 §3.3 登記，不在本輪範圍內。
