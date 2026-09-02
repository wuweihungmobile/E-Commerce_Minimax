# Sprint 106 Plan — 瀏覽數遞增改為原子敘述（DEF-055）

**Sprint**: Sprint 106
**日期**: 2026-09-02
**AI 編號**: AI-2440
**主題**: 處理 S105 系統性盤點留下的首要待辦 DEF-055（`incrementViewCount` ×3）。**紅燈實測推翻了 DEF-055 的原始定性**：`PostService` 那一路不是「系統性少計」，而是**每一次瀏覽都完全沒有寫進資料庫**——根因不是讀後寫競態，是 `@Transactional(readOnly = true)` 讓遞增永遠不會被 flush。

---

## 1. 為什麼本輪做 DEF-055

S105 把它列為範圍外第 1 順位，理由寫得很明確：同型、修法機械化、證據已完整記錄，唯一沒做的原因是「屬排名／分析而非金額或安全，依 Rule 3 不順手擴大」。它同時是該輪掃描中**併發度最高**的路徑——每一次文章／貼文瀏覽都會走。

三處實作在 S105 被判定為「完全同型」：

```java
// PostService / FaqService / KnowledgeBaseService，修復前
Article article = repository.findById(id).orElseThrow(...);
article.incrementViewCount();   // 記憶體 +1
repository.save(article);       // 整列寫回
```

本輪先寫紅燈把「同型」這件事驗證一次，而不是直接照著改。

---

## 2. 紅燈實測：三處同型，但失效模式有兩種

新增 `ViewCountConcurrencyIntegrationTest`（4 個案例，真實 PostgreSQL）。修復前 **4/4 全紅**：

| # | 案例 | 結果 | 意義 |
|---|------|------|------|
| 1 | 單執行緒瀏覽貼文 1 次 | `expected: 1 but was: 0` | 🔴 **不是少計，是完全沒計** |
| 2 | 10 執行緒併發瀏覽貼文 | `expected: 10 but was: 0` | 同上，與併發無關 |
| 3 | 10 執行緒併發瀏覽 FAQ 文章 | `expected: 10 but was: 1` | 讀後寫，**10 次只存活 1 次** |
| 4 | 10 執行緒併發瀏覽知識庫文章 | `expected: 10 but was: 1` | 同上，且熱門排名以此值排序 |

### 2.1 推翻點：Post 的根因是唯讀交易，不是競態

案例 1 是**單執行緒**的，刻意設計成不含任何併發——它一失敗就證明 Post 那一路的問題出在別的地方。

```java
@Transactional(readOnly = true)                       // ← 根因
public M15Dto.PostResponse getPublishedPostBySlug(...) {
    ...
    post.incrementViewCount();
    postRepository.save(post);                        // 對受管實體而言等同 no-op
    ...
}
```

Hibernate 在唯讀交易下是 `FlushMode.MANUAL`，提交時**不會**做自動 flush；`save()` 對一個已受管理的實體只是 `merge()`，不會自己發 SQL。兩者相加的結果是：**遞增只存在於記憶體，交易一結束就消失**。回應裡的數字是對的（DTO 從記憶體物件建出來），所以從 API 看不出任何異常——重新整理頁面才會發現數字沒動。

DEF-055 記錄的「瀏覽數系統性少計」對 FAQ／知識庫成立（案例 3、4），但對 Post **低估了**：不是少計，是零計。這與 S103 的情況同一類——**橫向掃描的樣式比對找對了位置，卻推錯了後果**，因為它只比對程式碼形狀，沒有看方法上的交易屬性。

### 2.2 FAQ／知識庫：與 DEF-053 相同的 90% 靜默漏失

案例 3、4 是純粹的讀後寫競態，`FaqArticle`／`KnowledgeArticle` 都沒有 `@Version`，10 次併發只存活 1 次——與 S105 DEF-053（10 筆退款只存活 1 筆）**完全相同的比例**，且同樣零例外零日誌。

`KnowledgeArticleRepository.findPopularByCategoryId` 以 `ORDER BY a.viewCount DESC` 取熱門文章，所以這不只是統計數字失真，而是**熱門排名失真**：越熱門的文章併發越高、漏失越多，排名被系統性壓低。

---

## 3. 修法

三支原生 `@Modifying` UPDATE，沿用 S102／S103／S105 的既有範式：

| Repository | 敘述 | 租戶條件 |
|---|---|---|
| `PostRepository.incrementViewCount` | `UPDATE posts SET view_count = COALESCE(view_count, 0) + 1 WHERE id = :postId` | 呼叫端已先以 `findByTenantIdAndSlug` 取得該貼文 |
| `FaqArticleRepository.incrementViewCount` | 同型 + `AND tenant_id = :tenantId` | ✅ 下沉到 WHERE 子句 |
| `KnowledgeArticleRepository.incrementViewCount` | 同型，無租戶條件 | ⚠️ 見 §4.3 |

服務層對應改為：

- **`PostService.getPublishedPostBySlug`**：`readOnly = true` **移除**（本方法本來就要寫入），改呼叫原子 UPDATE。專案沒有讀寫分離路由，`readOnly` 在此不承擔任何其他職責。
- **`FaqService` / `KnowledgeBaseService.incrementViewCount`**：不再載入實體，直接看 UPDATE 的回傳筆數，0 筆即拋 `E_4000`——錯誤語意與修復前完全相同（查無文章／不屬本租戶），但少了一次 SELECT。

三個實體的 `incrementViewCount()` 方法**已移除**。改用原子 UPDATE 後它們成為零呼叫死碼，留著等於留一個「無徵兆退回讀後寫」的入口——與 S101 移除 `computeDiscount` 舊多載、S102 移除 `incrementUsageCount`、S103 移除 `ProductInventory.reserve()/release()` 同一個「大聲失敗」的理由。

`@Modifying` **刻意不加 `clearAutomatically`**：`getPublishedPostBySlug` 在遞增之後仍要用同一個 `Post` 實體組回應，清空持久化上下文會把它 detach（承 S105 對該參數作用範圍的補註）。

---

## 4. 三個需要說明的決策

### 4.1 回應裡的 `viewCount` 要不要包含本次瀏覽？

**要**——維持修復前的語意。原本 DTO 是從遞增後的記憶體物件建出來的，前台 `blog/[slug]/page.tsx` 顯示的「N 次瀏覽」就包含當次。改用原子 UPDATE 後實體快照早於那筆遞增，故在組完回應後顯式補上：

```java
response.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
```

**刻意不呼叫 `post.incrementViewCount()` 或任何 setter**：實體仍在持久化上下文中，碰了會讓 Hibernate 髒檢查在提交時整列寫回、覆蓋原生 UPDATE 的結果——那是 S105 記下的「等於白修」陷阱。單元測試對此加了守衛：斷言 `post.getViewCount()` 在方法返回後仍為 0。

### 4.2 瀏覽不再更動 `updated_at`

原生 UPDATE 不觸發 `@PreUpdate`，因此 FAQ／知識庫文章的 `updated_at` 不再因為「有人看了一眼」而變動（Post 那一路本來就沒有真的寫入，無變化）。

這是**刻意的**：`updated_at` 的語意是「內容最後修改時間」，瀏覽不是修改。已查證後端沒有任何排序或業務邏輯依賴該欄位，前端也只是原樣顯示。

### 4.3 知識庫的 `incrementViewCount` 沒有租戶範圍 → 記錄為 DEF-057，本輪不改

`KnowledgeBaseService.incrementViewCount` 用的是 `findById`，而**同類別其餘方法一律用 `findByIdAndTenantId`**；姊妹服務 `FaqService` 的同名方法也有租戶範圍。端點是 `@PreAuthorize("hasAuthority('knowledge:read')")`，因此 A 租戶的使用者可以遞增 B 租戶文章的瀏覽數，進而影響 B 租戶的熱門排名。

本輪**只改併發語意、不改權限語意**：加上 `AND tenant_id = ?` 會讓原本會成功的跨租戶呼叫改為 404，屬行為變更，且是安全議題而非本 Sprint 的主題。依 Rule 3 記錄為 DEF-057，並在 repository 與 service 的 javadoc 各留一則說明，避免下一輪掃描把「沒有租戶條件」誤讀為疏漏而順手加上。

---

## 5. 測試變更

| 檔案 | 變更 | 說明 |
|---|---|---|
| `ViewCountConcurrencyIntegrationTest` | **新增 4** | 真實 PostgreSQL；紅燈 4/4，綠燈 4/4 |
| `FaqServiceTest` | 改寫 1 + 新增 1 | 原案例斷言「記憶體數字 +1 並 save()」 |
| `KnowledgeBaseServiceTest` | 改寫 1 + 新增 1 | 同上 |
| `PostServiceTest` | **新增 2** | 該路徑先前零單元測試覆蓋 |

**兩個被改寫的案例，正是這個缺陷長期隱形的原因**（承 S97「所有相關測試都用固件繞過同一段邏輯」、S103、S105）：

```java
// 修復前的斷言——repository 被 mock，缺陷存在時照樣全綠
assertThat(article.getViewCount()).isEqualTo(before + 1);
verify(articleRepository).save(article);
```

真正的丟失更新發生在 DB 層，mock 掉 repository 就等於把待測的東西整個移出視野。改寫為斷言「有委派給原子敘述、且**不再**走 `save()`／`findById()`」，併發正確性交由整合測試以真實 DB 驗證。

`PostServiceTest` 新增的兩個案例中，`getPublishedPostBySlug_draftPost_throwsE4101` 在修復前本來就會通過（守衛型），如實記錄、不計入紅燈範圍。

---

## 6. 範圍外（延後）

| # | 項目 | 理由 |
|---|------|------|
| 1 | 🟡 **DEF-057（新記錄）** 知識庫瀏覽數遞增無租戶範圍 | 見 §4.3。屬權限語意，需要自己的紅燈與 tenant 隔離測試 |
| 2 | 🟢 DEF-056（`ChatService` 未讀計數） | 自癒型缺陷，低優先 |
| 3 | 🟡 DEF-051（ERP 側庫存讀後寫） | 承 S103，維持不排程（有 `@Version`，無資料正確性風險） |
| 4 | DEF-047 / DEF-048 / DEF-052 | 續列 |
| 5 | 第九輪 PRD 全文掃描 | 續列 |

---

## 7. 方法論教訓

1. **「同型」是待驗證的假設，不是前提。** DEF-055 記錄三處「完全同型」，樣式上確實如此，但其中一處的交易屬性讓它變成一個**性質完全不同**、而且更嚴重的缺陷。承 S103：樣式比對找對位置、推錯後果——這次錯的維度是「方法上的註解」，不是「實體的併發防護」。**每一輪都出現一個新的、樣式比對看不到的維度**，這本身就是「掃到之後仍要各自驗證」的理由。
2. **刻意留一個不含併發的案例在併發測試裡。** 案例 1 是單執行緒的，如果整個測試類別都是 10 執行緒，Post 那兩個失敗會被理所當然地解讀為「競態嘛，跟另外兩個一樣」，根因就會被錯過，修完還是零計。**當你要驗證的是「同型」，就必須有一個案例能把「不同型」顯示出來。**
3. **靜默失效有兩種，唯讀交易那種更難發現。** 讀後寫至少還會寫進去一部分；唯讀交易吞掉的寫入是 100%，而且 API 回應看起來完全正常——**只有重新整理頁面或直接查 DB 才看得到**。

---

## 8. 一個「已經記錄過卻還是被重新發現」的項目

本輪跑第一次紅燈時用了 `mvn verify -Pintegration-test`，Maven 印出
`The requested profile "integration-test" could not be activated because it does not exist.`
我把它當成新發現寫進了本文件初稿——**但 DEF-052 早在 Sprint 104 就完整記錄了同一件事**（含根因：failsafe 無條件綁在 `verify` 階段，`-P` 對它毫無作用；以及 `Makefile:102` 與 pom 註解把它跟 Spring 的 `@ActiveProfiles("integration-test")` 混為一談）。

值得記下來的不是這個事實，而是**它為什麼會被重新發現**：DEF-052 待處理的三個修正點是「Makefile 說明、pom 註解、日後任何引用」，但真正把這個錯誤指令送到我手上的是**跨 session 記憶**（`mvn-test-excludes-integration-test-files`），那不在 DEF-052 的清單裡。**記錄下來 ≠ 送達使用現場**——一個只寫在追蹤表裡、沒有回頭清掉所有引用點的更正，下一輪一定會被重新發現一次。該記憶已於本輪一併更正。

---

**文件版本**: v1.0｜**建立者**: Claude Code（AISDLC v0.09 Sprint Planning）｜**基於**: SPRINT_105_PLAN.md §6 範圍外第 1 順位
