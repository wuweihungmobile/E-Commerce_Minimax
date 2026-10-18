# Sprint 283 Plan — DEF-389 移除單店鋪最後項目未清除促銷碼標記

## 1. 範圍與起點

使用者貼回 Sprint 282 總結，指示「請繼續完成任務！」，無 `==>` 回覆，也未要求回歸 AISDLC 全流程盤點。依既有慣例（S223／S229／S281／S282 同型情境），由總結自己列出的「下一步」候選中挑選 AI 能獨立處理、無需產品決策的項目。

核對 Sprint 282 總結：`git log`／`gh run list` 確認兩次 commit（`09f444fc`、`be0c4dce`）皆已 push，headSha 與雲端 CI run（[39085356905](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39085356905)、[39086681878](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39086681878)）一致，conclusion 皆為 `success`，工作區乾淨且與 `origin/main` 同步，總結無造假。

獨立盤點追蹤表：`DEF-336` 已於 Sprint 282 修復並結案；總結點名的候選 **DEF-389**（`removeItem` 逐一移除單店鋪所有項目不會連動清除該店鋪促銷碼標記）範圍明確、與剛修復的 `DEF-336` 同根因、追蹤表已寫好建議修法方向，是本輪最適合的目標。

**本輪範圍外的判斷**：`DEF-348` 後續（移除 MinIO/S3 預設憑證值）維持不處理——使用者已於 Sprint 244 拍板「維持 yml 預設、上線檢查強制覆寫」，翻案需重新確認且工作量較大（需同步整合測試設定），非本輪可擅自認定的「AI 能獨立處理」範圍；追蹤表其餘約 30 餘項 🟢 低優先級技術債多數是零呼叫點死碼或已有「不排入排程」的明確決定，非本輪範圍。

## 2. 讀碼調查

- 讀 `RedisCartService.removeItem(UUID, UUID, UUID, UUID)`（修復前）：確認存在性後只 `redisTemplate.opsForHash().delete(cartKey, itemKey)` 刪除該項目的 Hash 欄位，從未檢查該項目所屬店鋪是否因此已無剩餘項目，也完全不觸碰促銷碼鍵。
- 讀 `resolveStoreIdsInCart(cartKey)`（Sprint 282 為 `DEF-336` 新增的既有私有方法）：讀出購物車 Hash 目前所有項目的 `listingId`，查出對應的 `Listing.tenantId` 集合——簽章與語意剛好符合本次需求（「移除後，該項目的店鋪是否還在這個集合裡」），不需要另外寫一個方法。
- 確認 `removeItem(UUID, UUID, String cartItemKey)` 字串多載只是解析 `cartItemKey` 後委派給上述方法，不需要另外修改。

## 3. 使用者決策

不需要——範圍明確，追蹤表 `DEF-389` 原登記已寫好修法方向（「在 `removeItem` 刪除項目後，比照 `resolveStoreIdsInCart` 的解法檢查該店鋪是否已無剩餘項目，若是則一併刪除該店鋪的促銷碼鍵」），無產品或資料模型決策。

## 4. 實作內容

| 項目 | 檔案 | 說明 |
|------|------|------|
| 修復 | `backend/src/main/java/com/nextkey/ecommerce/core/cart/RedisCartService.java` | `removeItem(UUID, UUID, UUID, UUID)` 刪除項目後，呼叫 `listingRepository.findById(listingId)` 取得該項目的店鋪 id，若 `resolveStoreIdsInCart(cartKey)`（刪除後重新讀取）已不含該店鋪，刪除其促銷碼鍵（`getPromoKey`）；`resolveStoreIdsInCart` 的 Javadoc 同步更新為同時服務 `clearCart`（`DEF-336`）與 `removeItem`（`DEF-389`） |
| 測試 | `backend/src/test/java/com/nextkey/ecommerce/core/cart/RedisCartServiceTest.java` | `StoreScoped` 巢狀類別新增 3 個測試：移除店鋪在購物車裡的最後一項時清除該店鋪促銷碼、店鋪仍有其他項目時保留促銷碼、購物車橫跨兩家店鋪時只清除已無項目那家的促銷碼不影響另一家 |
| 文件 | `docs/02_architecture/API_M04_Cart.md` | §4.5（移除項目端點）新增「已修復（`DEF-389`）」說明；§4.6 移除已被取代的「已知限制：`DEF-389`」字樣（該限制已解除）；檔頭版本 v2.1→v2.2，新增修訂紀錄列 |
| 文件 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | `DEF-389` 標記已修復並說明修法；頁首「文件版本」v3.59→v3.60，「最後更新」改記本輪 |
| 文件 | `docs/04_planning/RELEASE_TRACKER.md` | 新增 Sprint 283 row |

## 5. 測試

依 CLAUDE.md 開發-編譯-測試循環，修改 `removeItem` 後立即 `mvn -o clean test-compile` 確認編譯成功（386 個主程式源檔＋300 個測試源檔皆重新編譯），再跑 `RedisCartServiceTest`。

- `RedisCartServiceTest`：**41/41 通過**（原 38 + 新增 3）。
- **突變驗證**：`git stash push --` 單獨還原 `RedisCartService.java` 至修復前版本（測試檔案不還原）→ 重跑 `RedisCartServiceTest`：3 個新測試全數轉紅——`removeItem_lastItemOfStore_removesThatStoresAppliedPromo`／`removeItem_withTwoStores_onlyRemovesThatStoresPromo_leavesOtherStoreUntouched` 兩個斷言失敗（`getAppliedPromoCode` 預期 `null` 卻仍回傳 `"SAVE100"`），`removeItem_storeStillHasOtherItems_keepsAppliedPromo` 因修復前的程式碼不再觸發該測試準備的 stub（第二次 `entries()`／`findById()` 呼叫）被 Mockito 嚴格模式判定 `UnnecessaryStubbingException`——與 Sprint 282 驗證 `DEF-336` 時同款模式。其餘既有 38 個測試維持綠燈不受影響（`Tests run: 41, Failures: 2, Errors: 1`）。`git stash pop` 還原後重跑 `RedisCartServiceTest` 確認 41/41 轉綠。
- 全量 `mvn -o clean verify`：**單元 2279/2279（+3）、整合 857/857，0 失敗**，Checkstyle 0 違規（main／test 皆通過），PMD 正常完成，`BUILD SUCCESS`，總耗時 25 分 20 秒。以 `grep -lE "FAILURE!|<<< ERROR" target/surefire-reports/*.txt target/failsafe-reports/*.txt` 交叉確認全部報告零筆命中，不只信任背景任務通知的 exit code 與終端機文字輸出（首次背景執行因誤加 `&`／`echo $!` 讓工具提早誤判完成、`mvn` 進程被提前中斷，已重新以 `run_in_background: true` 直接執行單一指令修正）。

## 6. 範圍外

- `DEF-348` 後續（移除 MinIO/S3 預設憑證值）：使用者已有方向決策（維持 yml 預設、上線檢查強制覆寫），移除預設值本身需同步整合測試設定，工作量較大，留待下一輪評估是否排入。
- 追蹤表其餘 🟢 低優先級約 30 項技術債：非本輪範圍。

## 7. 下一步／Action Items

- `DEF-348` 後續（移除硬編碼預設憑證）工作量較大，待使用者視排程決定是否排入。
- 追蹤表低優先級區其餘技術債可作為下一輪候選。
