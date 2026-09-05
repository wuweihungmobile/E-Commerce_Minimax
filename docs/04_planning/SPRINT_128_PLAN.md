# Sprint 128 Plan — DEF-072 CORS 標頭 + DEF-073 通知模板權限碼 + DEF-074 媒體庫欄位名

**Sprint**: Sprint 128
**日期**: 2026-09-05

---

## 1. 缺口盤點結果

待辦清單（`DEFERRED_ITEMS_TRACKER.md`）高優先級區為空；中優先級僅 DEF-021（使用者 2026-09-03
再次確認維持現狀），低優先級 DEF-025／DEF-031 皆為使用者已決策不排程。故延續第九輪以來的方法論
重新掃描一輪。

**本輪掃描角度（新）：前後端 API 契約漂移的系統性全掃。**

挑選理由：Sprint 127 的 DEF-071（前端送 `skuId`、後端 `@NotNull` 要 `itemId`，整條 ERP 收貨路徑
必定 400）是「順藤摸瓜意外」發現的，而該類缺陷後果極重卻從未被系統性掃過。可機械判定的前提已查證：
**後端全庫無任何 Jackson `PropertyNamingStrategy` 設定**，故 JSON 欄位名 == Java 欄位名，
前端欄位名必須逐字相符。

**掃描規模**：17 個切片覆蓋全部 29 支前端 service × 43 支後端 Controller，逐一比對 **173 個
exported service 函式**。每個發現再派 3 位「預設駁倒」的驗證者（契約序列化層／生產執行路徑／
歷史與既有涵蓋），2/3 認定成立才採信。

**結果**：29 個原始發現 → **22 個通過驗證、7 個被駁倒**（駁倒率 24%）。

被駁倒的 7 項（記錄以免日後重複調查）：平台工單跨租戶回覆、知識庫建分類缺 slug、`autoPublish`
無效、`UpdateCategoryRequest.isActive`、通知收件匣 `data.orderId`、上傳媒體缺 `originalName`、
上傳媒體 `categoryId` 必填——皆因行號/內容與實際不符，或該路徑實質不可達。

## 2. 使用者決策（AskUserQuestion 拍板紀錄）

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | 22 個確認發現遠超單一 Sprint，本輪修哪一組？ | **三個主流程根因**：DEF-072 CORS 標頭、DEF-073 通知模板權限碼、DEF-074 媒體庫欄位名。其餘一律登記為 DEF-075~DEF-091 待排程 |

## 3. DEF-072：`Idempotency-Key` 未列入 CORS 白名單（本輪後果最重）

**缺陷**：`SecurityConfig.java:120` 的 `setAllowedHeaders` 只有 `Authorization`／`Content-Type`／
`X-Tenant-ID`。前端訂房結帳（`booking.ts:253` → `POST /v2/bookings`）與 Sprint 126 新建的
合併結帳（`checkout.ts:72` → `POST /v2/checkout/mixed`）都會帶 `Idempotency-Key` 自訂標頭。

**機制（紅燈實測後更正過一次）**：原先推測是「Spring `checkHeaders` 回 null → 403 Invalid CORS
request」。實測 `checkHeaders(["authorization","content-type","idempotency-key"])` 實際回傳
`["authorization", "content-type"]`——**非 null**，因為 Spring 的實作是
`result.isEmpty() ? null : result`，只要有一個標頭命中就回非空子集。所以**伺服器不會報錯**，
是**瀏覽器**收到缺少 `idempotency-key` 的 `Access-Control-Allow-Headers` 後自行封鎖真正的 POST。
使用者看到的是通用錯誤文案，`err.response` 為 `undefined`，真因完全不可見——這正是它長期存活的原因。

**為何既有測試抓不到**：後端全庫零 preflight 測試（`grep` 無 `Access-Control-Request-Headers`）；
前端 `at-room-booking.spec.ts` 的建立訂房案例全用 `page.route('**/v2/bookings')` 攔截回固件，
繞過瀏覽器 CORS。

**修復**：`SecurityConfig.java` 白名單加入 `Idempotency-Key`（一行）。

**紅燈測試先行**（新增 `SecurityConfigCorsTest`，4 案例，修復前 2 失敗）：
- `bookingPreflight_allowsIdempotencyKey`／`mixedCheckoutPreflight_allowsIdempotencyKey`：
  斷言 `checkHeaders` 回傳集合**包含全部**請求標頭（刻意不用「非 null」斷言——非 null 正是本缺陷
  難以察覺的原因）
- `existingAllowedHeaders_stillAllowed`：既有三個標頭不受影響（最小爆炸半徑）
- `unknownHeader_stillRejected`：任意標頭仍被拒，避免修法退化成 `*` 全開

## 4. DEF-073：通知模板四個權限碼在生產端不存在

**缺陷**：`NotificationTemplateController` 的 create/update/delete/render 四支端點以
`@PreAuthorize("hasAuthority('notification_template:*')")` 把關，但這四個碼**從未進入
`Permission` 枚舉**。生產端唯一授權來源是 `JwtAuthenticationFilter:61` →
`RolePermissionMapping.getAuthorities(role)`，它只發出 `"ROLE_"+role`、`role.name()` 與
**枚舉裡實際存在的**權限碼。`SUPER_ADMIN` 是 `EnumSet.allOf(Permission.class)`——枚舉沒有的
東西 `allOf` 也給不了，故**連 SUPER_ADMIN 都拿不到，四支端點對所有角色必定 403**。

**使用者可見後果**：刪除模板必定 `alert('刪除失敗，請稍後再試')`；模板預覽的 403 被
`page.tsx:100-106` 的 catch 吞掉，改寫成「渲染失敗／無法渲染模板，請檢查變數設定」——**誤導使用者
去查變數設定，真因是權限碼缺漏**。M09 模板管理實際只有唯讀列表（list/detail 用
`isAuthenticated()`，不受影響）。

**為何存活至今（命中專案已記錄的警訊模式）**：`IntegrationTestConfiguration` 是**手工維護的
`RolePermissionMapping` 複本**（`getAuthoritiesForRole`，註解還寫著「與真實 RolePermissionMapping
的實作保持一致」），它用 Mockito spy **整個替換掉**真實 mapping，並且把
`notification_template:*` 錯掛在 **BUYER** 底下。M09 整合測試用註冊出來的買家帳號跑，
因此一路綠燈，完全掩蓋了生產端的缺口。

**修復**：
- `Permission.java`：新增 `NOTIFICATION_TEMPLATE_READ/CREATE/UPDATE/DELETE` 四個常數。
- `RolePermissionMapping.java`：STORE_OWNER 與 ADMIN 各授予四項；STORE_STAFF 僅 READ
  （依本檔既有慣例——員工在設定類模組唯讀，寫入權只開放日常操作模組如工單/退貨）；
  SUPER_ADMIN 經 `allOf` 自動取得。
- `IntegrationTestConfiguration.java`：把四個碼從 BUYER 移到 SELLER/STORE_OWNER，對齊生產。
- `M09NotificationTemplateIntegrationTest`：註冊後將 `User.role` 提升為 STORE_OWNER 再登入，
  讓 JWT 的 role claim 帶出真實店主授權，測試改走**生產授權路徑**而非靠固件偽造。

**根因層級的防復發守門**（新增 `PreAuthorizePermissionCoverageTest`，4 案例）：
以反射掃描全部 `@RestController` 的 `@PreAuthorize`，擷取 `hasAuthority`／`hasAnyAuthority` 內的
所有權限碼，斷言每個碼都存在於 `Permission` 枚舉。這支測試在修復前即以紅燈**獨立重現**了
手動 grep 的結論。

⚠️ **誠實揭露一項限制**：M09 整合測試通過**不能**證明生產枚舉正確——`IntegrationTestConfiguration`
的 spy 會整個替換掉 `RolePermissionMapping`。真正守住生產事實的是
`PreAuthorizePermissionCoverageTest`（讀真實枚舉），所以守門刻意放在單元測試層而非整合測試層。

## 5. DEF-074：媒體庫列表欄位名不符

**缺陷**：後端 `M15Dto.MediaListResponse` 的欄位是 `items`（`M15Dto.java:315`），前端
`cms.ts:130` 宣告為 `media`。`/cms/media` 頁面 `setMediaList(data.media)` 得到 `undefined`，
接著 `page.tsx:178` 的 `mediaList.length === 0` 對 `undefined` 取 `length` 直接拋 TypeError。
`totalCount`／`totalPages` 欄位名相符會正常顯示，故畫面呈現「全部 (N) 有數字但格子區崩潰」的
自相矛盾狀態——證明這條路徑從未真正運作過。

**修法選擇**：改前端對齊後端。理由是後端 `items` 是 DTO 真實欄位名，且本專案各列表 DTO 自訂欄位名
（通知模板用 `templates`，前端讀得到），無統一慣例可循；前端只有兩個消費端，爆炸半徑最小。

**注意**：前端實際打的是 `/v2/dashboard/media`（`PostController`，`hasAnyRole(...)`），
**不是** `MediaController` 的 `/v2/media`（需 `media:read`），故本項不受 DEF-075 的權限缺口阻擋。

## 6. 掃描過程中的額外發現：DEF-075（權限碼孤兒的完整規模）

修 DEF-073 時，把「`@PreAuthorize` 引用的碼是否存在於枚舉」做成全庫檢查，發現
**notification_template 只是冰山一角**。共 22 個碼不在枚舉，分成性質不同的兩類：

**(a) 任何角色都到不了（含 SUPER_ADMIN）——13 個**，端點必定 403：
`dashboard:read`、`faq:read/create/update/delete`、`knowledge:read/create/update/delete`、
`media:read/create/update/delete`。**登記為 DEF-075**。

**(b) 僅 SUPER_ADMIN 到得了——5 個**：`cms:read/create/update/publish`、`notification:create`。
註解形如 `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:read')")`，SUPER_ADMIN 仍可
到達，故**不是**「必定 403」；問題是「除 SUPER_ADMIN 外沒有任何角色到得了」，究竟是刻意的平台專屬
設計還是漏授權，需產品判斷。**登記為 DEF-092**。

守門測試對兩類分別處理：(a) 放在 `KNOWN_UNMAPPED_PENDING_DEF_075` 明列清冊，
(b) 由測試**自動辨識**註解是否帶 SUPER_ADMIN fallback 並與 `SUPER_ADMIN_FALLBACK_ONLY` 比對。
兩者都會讓**新增**的同類碼失敗；另有第三支測試斷言「清冊不得包含已修好的碼」，
確保清單只減不增、不會淪為永久豁免。

## 7. 本輪未修、已登記待排程的項目

DEF-075 ~ DEF-092，詳見 `DEFERRED_ITEMS_TRACKER.md`。其中最值得優先的是 **ERP 採購單/供應商四項
（DEF-076~DEF-079）**——Sprint 127 只修了收貨頁，建單頁、檢視頁、編輯頁、供應商檢視/編輯頁
同樣從未運作過。

## 8. 驗證

- 新增測試：`SecurityConfigCorsTest`（4）、`PreAuthorizePermissionCoverageTest`（4）
- 修改測試：`M09NotificationTemplateIntegrationTest`（改走生產授權路徑，12 案例）
- 前端：`tsc --noEmit` 0 error、`eslint` 0 error（2 個既有 warning 與本次改動無關，未動）
- 後端全量回歸：見 §9
- 無 migration
