# Sprint 158 Plan — 重新查證 `DEF-199`/`200`/`201` 是否也有 `DEF-195` 同型誤判

**Sprint**: Sprint 158
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_157_PLAN.md](SPRINT_157_PLAN.md) §7 誠實揭露：`DEF-195` 在 Sprint 155 被記錄為「不排入排程」，字面上容易被誤讀成「判斷不需要修」，但原文其實是「不擅自擴大本輪修復範圍」的排程紀律用語，本輪重新查證後才發現這是真實缺口。該文件同時明確標註**未探查範圍**：「未重新查證追蹤表中其他標記『不排入排程』的項目是否也有類似情況（例如 `DEF-199`/`200`/`201`）」。本輪（使用者要求「繼續完成任務」）針對這三項——與 `DEF-195` 同一輪（Sprint 156）登記、同屬「URL 協定驗證缺口家族」、且同樣標註「不排入排程」——重新查證其死路徑判準是否站得住腳。

---

## 2. 查證過程與結論

`DEF-195` 之所以被誤判，關鍵在於：其「不排入排程」的原始措辭是**排程紀律**（範圍外），而非像 `DEF-199`~`201` 那樣寫明「零前端呼叫點死路徑」的**具體技術判準**。本輪重新逐一核對這三項的死路徑判準是否仍然成立（而非只看措辭字面）：

| 項目 | 原始判準 | 重新查證方法 | 本輪查證結果 |
|------|----------|--------------|--------------|
| `DEF-199`（CMS 自訂頁 `featuredImageUrl`） | 整個 Content Page 功能前端零 UI 入口 | `find frontend/src/app/cms -type d`；`grep -rn "cms/pages\|createPage\|updatePage\|CreatePageRequest"` 全庫 | ✅ 判準成立：`frontend/src/app/cms/` 下僅有 `posts/`、`media/` 兩個功能目錄，**沒有 `pages/` 目錄**；全庫 zero 命中 `createPage`/`updatePage`/`CreatePageRequest`。之前掃到的 `featuredImageUrl` 命中全部屬於 `cms/posts/`（部落格文章，`DEF-197` 的範圍），與 CMS 自訂頁（`pages`）是不同功能 |
| `DEF-200`（知識庫文章 `coverImageUrl`） | `createKnowledgeArticle()` 零呼叫點，同 `DEF-170` | `grep -rn "createKnowledgeArticle" frontend/src --include="*.tsx"` | ✅ 判準成立：零命中。`frontend/src/app/dashboard/knowledge/` 僅有列表頁，確認沒有「新增文章」表單會呼叫此函式 |
| `DEF-201`（評論圖片 `imageUrl`/`images`） | `addImage` 零呼叫點；`createReview` 前端表單從未送出 `images` | `grep -rn "addImage\b"` 全庫；讀 [orders/[id]/page.tsx](../../frontend/src/app/(auth)/orders/[id]/page.tsx#L180-L191) 的 `submitReview` 實際呼叫內容 | ✅ 判準成立：`addImage` 零命中；`submitReview` 呼叫 `ReviewService.createReview` 時只傳 `listingId/orderId/rating/title/content/isAnonymous` 六個欄位，確認未傳 `images` |

**結論**：`DEF-199`/`200`/`201` 的死路徑判準本輪重新查證後**維持成立**，並非 `DEF-195` 那種「排程紀律用語被誤讀為安全判斷」的情況——三者原始記錄本身就是具體可驗證的技術判準（特定函式/目錄零呼叫點），而非模糊的範圍聲明，本輪逐一實測後證實記錄與現狀一致，無需修復。與 `DEF-195` 的關鍵差異：`DEF-195` 的端點本身（`POST /tenant/apply`）是活流程，只是某個欄位未被官方表單使用；`DEF-199`/`200`/`201` 是**整個功能或函式**都未被前端呼叫，兩者判準不同、不能因為其中一個判準錯誤就類推另外三個。

---

## 3. 實作內容

無程式碼變更（本輪為純查證，未發現需要修復的缺口）。

---

## 4. 範圍外（刻意不做，如實揭露）

- **未查證追蹤表中其餘標記「不排入排程」的項目**（如 `DEF-025`/`DEF-031`/`DEF-145`~`147`/`DEF-169`~`177`/`DEF-193`）：這些項目多數已在各自的 Sprint（S137、S146、S154/155）有獨立、具體的查證過程與使用者拍板記錄，不屬於本輪鎖定的「Sprint 156 同批登記、同屬 URL 協定驗證家族」範圍，故本輪不重新查證。若未來要對整份追蹤表做窮舉式重新查證，需另立專案範圍。

---

## 5. 驗證結果

本輪未變更任何程式碼，故不需重跑 `mvn -o verify`/`make validate-e2e`（與 Sprint 156 結束時的基準一致：單元 1340、整合 478、`validate-e2e` 62 passed/4 skipped/0 failed）。僅執行上表列出的 `grep`/`find`/程式碼閱讀查證動作。

---

## 6. 下一步 / Action Items

| # | 項目 | 狀態 |
|---|------|------|
| 1 | 查證 `DEF-199` 死路徑判準是否仍成立 | ✅ 完成，結論：成立，維持不排入排程 |
| 2 | 查證 `DEF-200` 死路徑判準是否仍成立 | ✅ 完成，結論：成立，維持不排入排程 |
| 3 | 查證 `DEF-201` 死路徑判準是否仍成立 | ✅ 完成，結論：成立，維持不排入排程 |
| 4 | 更新 `DEFERRED_ITEMS_TRACKER.md` 三筆記錄，附上本輪查證結果 | ✅ 完成 |

---

## 7. 誠實揭露總結

- 本輪只針對 Sprint 157 明確點名的 `DEF-199`/`200`/`201` 重新查證，未擴大到追蹤表中其他「不排入排程」項目；那些項目各自已有獨立的查證脈絡與使用者決策記錄，非本輪鎖定範圍。
- 三項死路徑判準本輪查證後均維持成立，無程式碼變更；本文件本身即為本輪唯一產出，隨追蹤表一併以 docs-only commit 提交。
