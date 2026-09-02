# Sprint 107 Plan — 兩個租戶範圍缺口的相反結論（DEF-057 修復 / DEF-037 結案）

**Sprint**: Sprint 107
**日期**: 2026-09-02
**AI 編號**: AI-2441
**主題**: 兩個表面同型的「服務層方法沒有租戶過濾」，經查證後結論**相反**：DEF-057 是自相矛盾的疏漏（修），DEF-037 是刻意的設計（結案並在程式碼裡釘死）。兩者的語意皆由使用者拍板。

---

## 1. 為什麼把這兩件放在同一輪

Sprint 106 修 DEF-055 時，發現 `KnowledgeBaseService.incrementViewCount` 沒有租戶範圍，依 Rule 3 記錄為 DEF-057 未動。而追蹤表上還躺著一個 Sprint 76 記錄、狀態長期是「⚠️ 已記錄，**不排入排程**（待使用者決策）」的 DEF-037——`ShippingTemplateService.calculateFee` 同樣沒有租戶過濾。

兩者用 grep 找出來會長得一模一樣：**同類別其他方法都做了租戶隔離，只有這一個沒有**。承 S103／S106 的教訓（樣式比對找對位置但推錯後果），本輪先各自查證使用現場，再決定。

**結論相反**——這正是把它們放在一起處理的價值：同一個樣式，兩種答案，而且判準可以寫下來。

---

## 2. DEF-057：不是設計，是自相矛盾

### 證據：同一個類別對「他租戶的文章」有三種反應

| 端點 | 實作 | 他租戶的文章 |
|---|---|---|
| `GET /v2/knowledge` 列表 | `findByTenantIdAndStatus` | 看不到 |
| `GET /v2/knowledge/{id}` 詳情 | `findByIdAndTenantId` | **404** |
| `POST /v2/knowledge/{id}/view` | `findById`（無租戶） | **200，且真的 +1** |

也就是說：**一篇你被禁止閱讀的文章，你可以幫它衝瀏覽數**。而 `viewCount` 正是 `KnowledgeArticleRepository.findPopularByCategoryId`（`ORDER BY a.viewCount DESC`）的排序欄位——他租戶因此能操縱本租戶的熱門排名。

### 為什麼判定是疏漏而非設計

1. **知識庫沒有任何公開端點**：`KnowledgeArticleController` 的每一個方法都要 `knowledge:*` 權限，沒有匿名入口。
2. **前端只有一個入口**：`frontend/src/app/dashboard/knowledge/page.tsx`（後台管理頁），而列表已租戶隔離——正常使用者根本點不到別家的文章，要走這條路只能手刻 API 呼叫。
3. **姊妹服務有做**：`FaqService.incrementViewCount` 是租戶範圍的。兩支同型方法只有一支漏掉。

三點加起來：加上租戶條件**不會弄壞任何走得到的流程**。

### 紅燈

新增 2 個案例（測試類別由 4 → 6）：

| 案例 | 修復前 |
|---|---|
| 他租戶遞增 → 應查無文章 | 🔴 `Expecting code to raise a throwable`（沒被拒，回 200 且 +1）|
| 本租戶遞增 → 正常 +1 | ✅ 守衛型，修復前本就通過，如實記錄不計入紅燈 |

### 修法

租戶條件下沉到 UPDATE 的 WHERE 子句，沿用 Sprint 106 為 FAQ 建立的同一個形狀：

```sql
UPDATE knowledge_articles SET view_count = COALESCE(view_count, 0) + 1
WHERE id = :articleId AND tenant_id = :tenantId
```

更新 0 筆即拋 `E_4000`。服務層取 `getCurrentTenant()` 傳入——與同類別其餘方法完全一致。

---

## 3. DEF-037：是設計，正式結案並在程式碼裡釘死

`ShippingTemplateService.calculateFee(templateId, orderAmount)`（`POST /v2/shipping-templates/{id}/calculate-fee`，僅需 `product:read`）不驗證 `templateId` 是否屬於當前租戶。

**與 DEF-057 的關鍵差異**：它有一個說得通的正當用途——**買家跨店比價／試算運費**。回傳內容只有 `feeType`／`orderAmount`／`shippingFee` 等計算參數，不含 PII，也沒有任何寫入或竄改路徑。

使用者於本輪拍板：**維持開放**。

程式碼變更只有 javadoc，但那正是重點——這個項目自 Sprint 76 起被記錄，此後每一輪租戶範圍橫向掃描都會把它撿起來重新評估一次。結案的價值不在改了什麼，而在**讓它不再消耗判斷成本**：

- 明確寫出「刻意不做租戶過濾」與可接受的理由
- 明確寫出「**請勿順手加上租戶過濾**」，並說明改變此設計的前提（買家比價情境不再需要）
- 交叉引用 DEF-057，說明兩者表面同型但結論相反

承 S106 §8 的教訓（**記錄下來 ≠ 送達使用現場**）：只把決策寫進追蹤表，下一輪掃描的人不會讀到；寫在方法的 javadoc 上，他一定會讀到。

---

## 4. 判準：「沒有租戶過濾」什麼時候是缺陷？

兩案對照，可重複使用的三個問題：

| 問題 | DEF-057（知識庫瀏覽數） | DEF-037（運費試算） |
|---|---|---|
| **同類別其他端點怎麼做？** | 列表、詳情都隔離 → 只有它沒有 | CRUD 都隔離，但它是**查詢**不是管理 |
| **有沒有正當的跨租戶用途？** | ❌ 沒有公開端點、前端只有後台入口 | ✅ 買家跨店比價／試算運費 |
| **跨租戶呼叫能造成什麼？** | 操縱他人熱門排名（`viewCount` 是排序欄位） | 只讀運費計算參數，無 PII、無寫入 |

**第一個問題最容易誤導**。兩案在第一個問題上都是「只有它沒有」，若只看這個就會把 DEF-037 一起「修」掉、打斷買家比價。真正分辨兩者的是**第二、三個問題**——去看使用現場和後果，而不是看程式碼形狀是否一致。

DEF-057 之所以是缺陷，關鍵不在「它沒有租戶過濾」，而在**同一個類別對同一份資料給出互相矛盾的答案**：讀不到、卻改得動。

---

## 5. 測試變更

| 檔案 | 變更 | 說明 |
|---|---|---|
| `ViewCountConcurrencyIntegrationTest` | 新增 2（4 → 6） | 租戶隔離紅燈 1 + 正常路徑守衛 1 |
| `KnowledgeBaseServiceTest` | 簽名更新 2 | `incrementViewCount(id)` → `(id, tenantId)` |

DEF-037 無程式碼行為變更，故無測試變更；其「刻意開放」的語意由 javadoc 承載。

---

## 6. 意外攔截：E2E 守門擋下 release，兩個既有 flaky 同一個根因

`make validate-release` 的 E2E 守門在本輪擋下了 release，而且**擋了兩次、失敗在兩個不同的 spec**，都與本輪改動無關（本輪動的是 M18 知識庫與 M15 CMS 的**後端**）。查下去兩者是**同一個根因**。

### 共同根因：在 client-rendered 應用裡等錯了訊號

```js
await page.goto(...);                              // 或 click 一個 <Link>
await page.waitForLoadState('domcontentloaded');   // ← 立刻返回，什麼都沒保證
```

Next.js 的 client-side 導航**不會觸發新的 document load**，而首次載入時 `domcontentloaded` 又發生在 **React hydration 之前**。這行 await 因此既不保證「導航完成」，也不保證「頁面可互動」——後續的 locator 可能在**上一頁**求值，或 click 落在**還沒接上事件處理器**的按鈕上（空操作）。

| # | spec | 表徵 | 實際發生的事 |
|---|---|---|---|
| 1 | `at-m17-002` › Admin 審核通過申請 | `element was detached from the DOM, retrying` → 30s 逾時 | 沒等到導航完成，locator 仍在**列表頁**求值 |
| 2 | `at-m15-e2e` › CMS 列表頁篩選功能 | `toHaveClass(/bg-blue-100/)` 輪詢 **14 次**都是 `bg-gray-100` | hydration 未完成，click 是**空操作**，篩選狀態從未改變 |

修法各自改為等**能證明目標狀態已到達**的訊號：前者 `waitForURL('**/admin/tenants/*/review')`，後者等列表資料的 `GET /v2/dashboard/posts` 回應（該請求由 `useEffect` 內的 `loadPosts()` 發出，回應到達即證明 hydration 已完成）再確認「載入中…」消失。

### `at-m17-002` 另有兩個獨立缺陷

**(a) locator 用子字串比對，抓到完全不同的按鈕。**

```js
page.locator('button:has-text("核准"), ...')   // :has-text 是子字串比對
```

列表頁 `/admin/tenants` 有一個篩選 tab 叫「**已核准 (N)**」——含有「核准」兩字，所以會命中。失敗 log 裡解析到的按鈕 class 是 `border border-input bg-background … h-8 rounded-md px-3 text-xs`，正是 `variant="outline" size="sm"`（tab 按鈕），而審核詳情頁真正的核准鈕是 `variant="default"`（`h-9 px-4 py-2`）。**證據直接指向抓錯了按鈕**；該 tab 隨 `tabCounts` 重新渲染而被拔離 DOM，於是 click 進入無盡重試。已改用 `getByRole('button', { name: '核准', exact: true })`。

**(b) 斷言是恆真的。**

```js
expect(successVisible || true).toBeTruthy();   // 永遠為真
```

**這個測試從來沒有驗證過核准流程**——即使按鈕點錯、核准根本沒發生也照樣綠燈。承 S97「測試以固件繞過同一段邏輯」、S105／S106「mock 掉 repository 後斷言記憶體數字」，只是換到 E2E 層。已改為真斷言：等 `POST /approve` 回應並斷言 `200`，再斷言成功分支特有的導頁（成功會在 2 秒後導回列表，失敗分支是 `alert` 不導頁；斷言導頁可避開與那 2 秒 redirect 的競賽）。

### 為什麼長期沒被抓到：測試在三態之間隨機擺盪

`at-m17-002` 的待審核資料來自**同批並行執行的 `at-m17-001` 的副作用**，順序不保證。於是它長期在 **skip／pass／fail 三態之間隨機擺盪**：

| Sprint | E2E 結果 | 當時的解讀 |
|---|---|---|
| S105 | 54 passed / 6 skipped | — |
| S106 | 53 passed / 7 skipped | 「條件式 skip 相依執行時資料狀態、總數未變、**非回歸**」 |
| S107 首跑 | **1 failed** / 54 passed | 本輪才第一次真的執行到 |

S106 那個結論**對，但不完整**：對「不是回歸」是對的，對「這個測試本身是壞的」則沒看出來。同一份證據再往前一步就會問：**一個在 skip 與 pass 之間隨機擺盪的測試，它到底在測什麼？**

### 讓它真的跑到，然後被打臉：測試的前提根本建立不起來（DEF-058）

只修 locator／等待訊號還不夠——若測試照樣 skip，我等於**交付一個沒被跑過的修正**。所以先讓它跑到：在 `beforeEach` 以 `POST /v2/tenants/apply`（Guest 端點）自建待審核資料。

跑到了，然後失敗在新的地方：`getByRole('button', { name: '核准', exact: true })` 逾時。追下去：

> 核准鈕只在 `tenant.status === 'PENDING_REVIEW'` 時渲染，而 `/v2/tenants/apply` 建立的是 **`TenantApplication`（status=`PENDING`）**，**不是** admin 列表所看的 **`Tenant`（status=`PENDING_REVIEW`）**——兩個不同的實體。我的 seeding 打錯了對象。

也就是說：**這個測試的前提，測試自己建立不出來**。它依賴的資料要嘛來自 `at-m17-001` 的副作用（順序不保證），要嘛來自環境殘留。

到這裡本輪停手：無效的 seeding 已移除，核准鈕改回「找不到就 `test.skip()`」——但**保留精確 locator、`waitForURL` 與真斷言**，所以當前提成立時它是真的在測，而前提不成立時乾淨地跳過、不再假失敗。剩下的（找出能穩定造出 `PENDING_REVIEW` 租戶的路徑，讓這兩個測試真正可執行）記錄為 **DEF-058**，不在本輪擴大。

**這一段本身就是教訓**：`at-m17-002` 的 skip 分支長期看起來像是「容忍環境差異」的貼心設計，實際上它掩蓋的是「這個測試從來沒有真的跑過」。而只有**強迫它跑起來**，才會發現前提是造不出來的。

---

## 7. 範圍外（延後）

| # | 項目 | 理由 |
|---|------|------|
| 0 | 🟡 **DEF-058（新記錄）** `at-m17-002` 的前提（存在 `PENDING_REVIEW` 租戶）測試自己建立不出來 | 見 §6。需要找出建立 `Tenant`（而非 `TenantApplication`）的路徑，或提供 E2E 專用 seeding。本輪已把該 spec 修到「前提成立就真的測、不成立就乾淨跳過」，不再假失敗，故不阻擋 release |
| 1 | 🟡 DEF-051（ERP 側庫存讀後寫） | 承 S103／S106，維持不排程（有 `@Version`，無資料正確性風險） |
| 2 | 🟢 DEF-056（`ChatService` 未讀計數） | 自癒型缺陷，低優先 |
| 3 | ⚪ 雲端 unit job 耗時跳升（175~189s → 282s） | S106 觀察到，只加 4 個測試卻 +50%，超出已知 21% runner 變異。**本輪未查**，先多收一個樣本再決定是否值得一輪。承 DEF-049 的耗時預算監控 |
| 4 | DEF-047 / DEF-048 / DEF-043 / DEF-044 / DEF-052 | 續列 |
| 5 | 第九輪 PRD 全文掃描 | 續列 |

---

## 8. 方法論教訓

1. **「同型」在租戶範圍上跟在併發上一樣不可靠。** S103／S106 的教訓是樣式比對推錯後果，本輪是同一件事的第三次實例：兩個 grep 起來一模一樣的缺口，一個要修、一個不能修。**分辨它們的不是程式碼，是使用現場。**
2. **判定疏漏最強的證據是「自相矛盾」，不是「不一致」。** 「只有這支沒做租戶過濾」只是不一致，可能是設計。「詳情端點 404 但遞增端點 200」是自相矛盾——同一份資料、同一個使用者、兩個相反的答案，**沒有任何設計會這樣要求**。
3. **結案要結在程式碼裡，不是只結在追蹤表裡。** DEF-037 從 Sprint 76 起被反覆撿起來評估，因為決策只存在追蹤表。承 S106 §8：更正／決策要放到**下一個人一定會讀到的地方**。這一輪 DEF-037 的程式碼變更只有 javadoc，但那才是真正的交付物。
4. **「重跑就綠」不等於「沒有缺陷」。** §6 的 E2E 失敗重跑一次就過，最省事的處理是記一筆 flaky 然後往下走。實際查下去是三個疊在一起的真缺陷，其中一個讓該測試**從來沒有驗證過它宣稱要驗證的東西**。判準與 S105 的「紅燈必須紅在正確的斷言上」對稱：**綠燈也必須綠在正確的斷言上**。
5. **數字的變動被看到了，結論卻停在半路。** S106 查過 E2E 由 54/6 變 53/7，結論「條件式 skip 相依於執行時資料狀態、總數未變、非回歸」——**對，但不完整**。同一份證據再往前一步就會問：一個在 skip 與 pass 之間隨機擺盪的測試，它到底在測什麼？停在「不是回歸」就錯過了它其實壞掉的事實。

---

**文件版本**: v1.0｜**建立者**: Claude Code（AISDLC v0.09 Sprint Planning）｜**基於**: SPRINT_106_PLAN.md §6 範圍外第 1 順位 + 使用者於本輪對 DEF-057／DEF-037 的語意拍板
