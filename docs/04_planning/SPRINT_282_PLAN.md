# Sprint 282 Plan — DEF-336 清空購物車未清除優惠券標記

## 1. 範圍與起點

使用者貼回 Sprint 281 總結，指示「請繼續完成任務！」，無 `==>` 回覆，也未要求回歸 AISDLC 全流程盤點。依既有慣例（S223／S229／S281 同型情境），由總結自己列出的「下一步」候選中挑選 AI 能獨立處理、無需產品決策的項目。

核對 Sprint 281 總結：`git log`／`gh run list` 確認兩次 commit（`384b78b9`、`195c72a2`）皆已 push，headSha 與雲端 CI run（[39049947088](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39049947088)、[39050922835](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39050922835)）一致，conclusion 皆為 `success`，總結無造假。

獨立盤點追蹤表：🔴 高優先級與 🟡 中優先級項目全數已結案或已有不需產品決策的明確處置；🟢 低優先級約 30 餘項裡絕大多數是零呼叫點死碼或使用者已拍板「不排入排程」。範圍明確、無需產品決策、可直接動工的是總結點名的 **DEF-336**（清空購物車不清除已套用的優惠券標記）。

**本輪範圍外的判斷**：`DEF-348`（移除 MinIO/S3 預設憑證）維持不處理——使用者已於 Sprint 244 拍板「維持 yml 預設、上線檢查強制覆寫」，翻案需重新確認且工作量較大（需同步整合測試設定），非本輪可擅自認定的「AI 能獨立處理」範圍。

## 2. 讀碼調查

- 讀 `RedisCartService.clearCart`（修復前）：僅 `redisTemplate.delete(cartKey)` 刪除購物車 Hash，從未觸碰促銷碼鍵。
- 讀 `getPromoKey(userId, tenantId, storeId)`：促銷碼鍵以（使用者, 買家租戶, 店鋪）為粒度——Sprint 237（`DEF-319`）起購物車可橫跨多家店鋪、各自獨立套用促銷碼。
- 比對 `DEF-292`（`RefreshTokenService.blacklistAllRefreshTokens` 用 `redisTemplate.keys()` 掃描整個 keyspace 的已登記反模式，因未量測生產影響而「不排入排程」）：若用前綴掃描（`cart:promo:{user}:{tenant}:*`）找出要刪的促銷碼鍵，會在新程式碼裡重蹈同一個反模式。改採「清空前先讀出購物車目前橫跨哪些店鋪」的做法——與既有 `resolveStoreIdForValidation`／`getCart` 內部解析 storeId 的邏輯一致（讀 Hash 裡每個項目的 `listingId`，查 `Listing.tenantId`），不需要 `KEYS`/`SCAN`。
- 複核時確認一個相關但不同根因觸發路徑的缺口：`removeItem` 逐一移除單一項目後，不檢查該店鋪是否已無剩餘項目，故透過「逐一移除同店鋪所有項目」而非 `clearCart` 清空購物車時，該店鋪的促銷碼鍵依然不會被清除。此路徑與 `DEF-336` 原始登記文字（明確聚焦 `clearCart`）不同，另行登記為 `DEF-389`，不在本輪修復範圍。

## 3. 使用者決策

不需要——範圍明確、無產品或資料模型決策（追蹤表原登記已寫好修法方向「讓 `clearCart` 一併刪該使用者的優惠券標記」）。

## 4. 實作內容

| 項目 | 檔案 | 說明 |
|------|------|------|
| 修復 | `backend/src/main/java/com/nextkey/ecommerce/core/cart/RedisCartService.java` | 新增私有方法 `resolveStoreIdsInCart(cartKey)`：讀出購物車 Hash 裡所有項目的 `listingId`，查出對應的 `Listing.tenantId` 集合；`clearCart` 改為先呼叫此方法取得店鋪集合，刪除購物車鍵後逐一刪除這些店鋪的促銷碼鍵（`getPromoKey`） |
| 測試 | `backend/src/test/java/com/nextkey/ecommerce/core/cart/RedisCartServiceTest.java` | `StoreScoped` 巢狀類別新增 3 個測試：單店鋪套用促銷碼後清空不復活、雙店鋪皆套用促銷碼時清空兩家都清除、空購物車清空時只刪購物車本身不多刪；`stubValueOps()` 新增 `redisTemplate.delete(String)` 連動移除測試用 `valueStore` 的 stub，使測試能用 `getAppliedPromoCode()` 驗證標記真的消失，而不只是驗證呼叫次數（Rule 9：測試驗證意圖） |
| 文件 | `docs/02_architecture/API_M04_Cart.md` | §4.6 由「已知限制」改寫為「已修復」；新增已知限制說明 `DEF-389`；檔頭版本 v2.0→v2.1，新增修訂紀錄列 |
| 文件 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | `DEF-336` 標記已修復並說明修法；新增 `DEF-389` 條目（🟢 低優先級，未修） |
| 文件 | `docs/04_planning/RELEASE_TRACKER.md` | 新增 Sprint 282 row |

## 5. 測試

依 CLAUDE.md 開發-編譯-測試循環，修改 `clearCart` 後立即 `mvn -o clean test-compile` 確認編譯成功，再跑 `RedisCartServiceTest`。

- `RedisCartServiceTest`：38/38 通過（原 35 + 新增 3）。
- **突變驗證**：`cp` 備份修復後的 `RedisCartService.java` → `git stash push` 單獨還原該檔案至修復前版本（測試檔案不還原）→ `mvn -o clean test-compile` 確保採用舊版生產碼 → 重跑 `RedisCartServiceTest`：3 個新測試全數轉紅（2 個斷言失敗於 `getAppliedPromoCode` 仍回傳舊值、1 個因程式碼不再呼叫該 stub 而被 Mockito 嚴格模式判定 `UnnecessaryStubbingException`），其餘既有 35 個測試維持綠燈不受影響，證實新測試非巧合通過。`git stash pop` 還原，以 `diff` 核對還原後檔案與修復版本位元組級一致，重新 `mvn -o clean test` 確認 38/38 轉綠。
- 全量 `mvn -o clean verify`：**單元 2276/2276、整合 857/857，0 失敗**（單元 +3，與本輪新增的 3 個測試方法數一致；整合數與 Sprint 281 相同，本輪未新增整合測試）。Checkstyle 0 違規；PMD 正常完成；`BUILD SUCCESS`，總耗時 16 分 48 秒。以 `grep -lE "FAILURE!|<<< ERROR" target/surefire-reports/*.txt target/failsafe-reports/*.txt` 交叉確認全部報告零筆命中，不只信任背景任務通知的 exit code 與終端機文字輸出。

## 6. 範圍外

- **DEF-389**（`removeItem` 逐一清空單店鋪所有項目不會清除該店鋪促銷碼標記）：本輪複核時新發現，已登記，未修復——修法需在 `removeItem` 刪除項目後檢查該店鋪是否已無剩餘項目，屬不同程式碼路徑的獨立改動，非本輪 `DEF-336` 範圍。
- **DEF-348 後續**（移除 MinIO/S3 預設憑證值）：使用者已有方向決策（維持 yml 預設、上線檢查強制覆寫），移除預設值本身需同步整合測試設定，工作量較大，留待下一輪評估是否排入。
- 追蹤表其餘 🟢 低優先級約 30 項技術債：非本輪範圍。

## 7. 下一步／Action Items

- `DEF-389` 可視優先序排入下一輪。
- `DEF-348` 後續（移除硬編碼預設憑證）工作量較大，待使用者視排程決定是否排入。
- 追蹤表低優先級區其餘技術債可作為下一輪候選。
