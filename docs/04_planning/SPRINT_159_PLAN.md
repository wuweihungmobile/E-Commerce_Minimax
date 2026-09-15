# Sprint 159 Plan — 契約層防禦性掃描：Mass Assignment／裸實體回應外洩／缺 `@Valid`／巢狀清單串聯驗證四角度複查

**Sprint**: Sprint 159
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_158_PLAN.md](SPRINT_158_PLAN.md) 完成後，`DEFERRED_ITEMS_TRACKER.md` 已無任何 🔴 高優先級待排程項目（`DEF-188`~`192` 全數已修復並結案，`DEF-038` 等歷史架構項目已結案），且近期 Sprint 154~158 聚焦的「URL 協定白名單驗證」家族（`DEF-103`/`104`/`194`/`195`/`196`/`197`/`198`）本身已窮盡（`DEF-199`/`200`/`201` 經 Sprint 158 重新查證仍為死路徑，維持不修）。本輪（使用者要求「繼續完成任務」）不再延續同一個缺陷家族，改為對後端契約層挑選四個過去 Sprint 未曾系統性檢查過的角度做防禦性掃描，比照過往「無新漏洞」Sprint（S77/S78）的先例——不預設有問題，紅燈先行的前提是先找到真正命中的目標。

---

## 2. 查證過程與結論

| # | 角度 | 查證方法 | 結論 |
|---|------|----------|------|
| 1 | **Mass Assignment**：`@RequestBody` 是否有任何 Controller 直接綁定 JPA `@Entity`（而非專用 DTO），讓呼叫者可透過欄位名稱覆寫不該由使用者提供的欄位（如 `id`/`tenantId`/`status`/`role`） | 對全部 43 個 `*Controller.java` 執行 `grep -rhoP '@RequestBody\s+(@Valid\s+)?\K[\w.]+'`，取出所有出現過的請求型別名稱去重 | ✅ CLEAN：78 個相異型別全數以 `*Request`/`*Dto.XxxRequest` 命名，無一是裸 entity 或 `Map`（除 2 處刻意使用 `Map<String,Boolean>`，見下） |
| 2 | **裸實體回應外洩**（`DEF-093` 同型，複查是否有其他 Controller 重蹈覆轍） | 列出 `entity/`/`model/` 下全部 57 個 `@Entity` 類名，用正則在全部 Controller 搜尋是否有方法簽章直接以這些型別（或其 `List`/`Page`）作為 `ResponseEntity<>` 回傳型別 | ✅ CLEAN：零命中，確認 `DEF-093`（Sprint 130 修復 `ListingController`）後無回歸，其餘 Controller 一律回傳 DTO |
| 3 | **缺 `@Valid` 導致驗證被靜默跳過** | `grep -rn "@RequestBody" controller/ \| grep -v "@Valid"` 找出 5 處未標 `@Valid` 的請求參數（`ShippingTemplateDto.UpdateRequest`／`AdminDto.TenantApproveRequest`／`NotificationDto.MarkReadRequest`／`NotificationTemplateDto.RenderRequest`／`LogoutRequest`），逐一讀取這 5 個 DTO 的欄位宣告確認是否真的帶有 Bean Validation annotation（`@NotNull`/`@Pattern`/`@Size` 等） | ✅ CLEAN（非缺陷）：5 個 DTO 的所有欄位皆**完全沒有任何驗證 annotation**，故缺 `@Valid` 不構成實質繞過——沒有規則可被繞過。`StripeWebhookController` 的 2 處 `@RequestBody`（`String`/`Map<String,Object>`）為 webhook 原始 payload，簽章驗證在別處把關，非此角度範疇 |
| 4 | **巢狀 `List<Dto>` 欄位缺 `@Valid` 導致 cascade 驗證失效**（`DEF-201` 記錄中已預告的同型風險：容器欄位需要 `@Valid` 才能讓內層 item 的約束生效） | 全庫搜尋 DTO 中宣告為 `List<大寫開頭型別>` 且型別名稱像巢狀物件（非 `String`/`UUID` 等基礎型別）的欄位，逐一確認其所屬類別是請求還是回應 DTO | ✅ CLEAN：命中的 9 處中，6 處屬回應 DTO（`OrderResponse.items`／`HistoryListResponse.items`／`RecentActivity.items`／`MediaListResponse.items`／`FaqCategoryDto.articles`／`KnowledgeCategoryDto.children`／`MediaCategoryDto.children`，回應不需要驗證），另 3 處（`ReturnDto.CreateItem`/`ReceiveItem`、`PurchaseOrderCreateRequest.items`、`PurchaseOrderReceiveRequest.items`）皆為真正的請求 DTO，且**已正確標註 `@NotEmpty` + `@Valid`**，無需修復。`OrderDto.CreateRequest`/`CheckoutDto.MixedCheckoutRequest` 等真正的下單／結帳請求本身不接受客戶端送入逐項商品清單（品項一律來自伺服端購物車），故此類請求天生不存在此風險 |

**綜合結論**：四個角度本輪皆為 CLEAN，**無程式碼變更**。與 Sprint 77/78（多 Sprint 測試強化計劃期間）的「連續無新漏洞」先例一致——不是每輪掃描都必然命中，發現漏洞前必須先有具體證據，不可預設有問題（見 [[e-commerce-multi-sprint-test-loop]]）。

---

## 3. 實作內容

無（純查證 Sprint，未發現需要修復的缺口，`docs/` only）。

---

## 4. 範圍外（刻意不做，如實揭露）

- **前端層的 mass assignment / over-posting**（例如前端表單是否有隱藏欄位被使用者用瀏覽器開發工具竄改後送出）：本輪僅檢查後端 Controller 契約層，未涵蓋前端表單欄位層級的防護；後端 DTO 白名單已是第一道防線，前端層風險相對次要。
- **`Map<String, Boolean>`/`Map<String, Object>` 的鍵值本身是否有上限或格式驗證**（`AdminController.updateTenantFeatureToggle`/`StripeWebhookController`）：這 2 處確認皆有其他把關機制（前者路徑變數指定 `featureKey`、僅讀取固定鍵 `enabled`，且端點本身要求 `SUPER_ADMIN`；後者為 webhook 簽章驗證），未逐一複查是否有更細緻的鍵值格式風險，因非本輪四個角度鎖定範圍。
- **窮舉式重新查證 `DEFERRED_ITEMS_TRACKER.md` 其餘「不排入排程」項目**：與 Sprint 158 §4 相同的範圍聲明，本輪同樣未擴大。

---

## 5. 驗證結果

本輪未變更任何程式碼，故不需重跑 `mvn -o verify`/`make validate-e2e`（與 Sprint 156~158 結束時的基準一致：單元 1340、整合 478、`validate-e2e` 62 passed/4 skipped/0 failed）。僅執行上表列出的 `grep`/`find`/程式碼閱讀查證動作。

---

## 6. 下一步 / Action Items

| # | 項目 | 狀態 |
|---|------|------|
| 1 | Mass Assignment（裸 entity 綁定請求）掃描 | ✅ 完成，結論：CLEAN |
| 2 | 裸實體回應外洩（`DEF-093` 同型回歸）掃描 | ✅ 完成，結論：CLEAN |
| 3 | 缺 `@Valid` 掃描 | ✅ 完成，結論：CLEAN（5 處缺標註但無驗證規則可繞過） |
| 4 | 巢狀 `List<Dto>` 缺 cascade `@Valid` 掃描 | ✅ 完成，結論：CLEAN |

---

## 7. 誠實揭露總結

- 本輪四個角度皆為「防禦性複查已知漏洞類別是否有其他實例」，而非追蹤表點名的既定項目——因追蹤表目前無任何待排程項目。
- 未做前端層 over-posting 防護複查、未逐一複查 `Map` 型別請求的鍵值層級風險，範圍聲明見 §4。
- 本文件本身即為本輪唯一產出，隨兩份追蹤表一併以 docs-only commit 提交。
