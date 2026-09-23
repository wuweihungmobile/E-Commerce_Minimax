# Sprint 190 Plan — 公開端點資料範圍複查（DEF-264）

**Sprint**: Sprint 190
**日期**: 2026-09-23

## 1. 起點

延伸 Sprint 189「統一端點型別/權限錯配」主題的精神——本輪改查另一個容易被忽略的角落：`SecurityConfig` 明文 `permitAll()` 的公開端點，其回傳資料本身是否夾帶了不該讓匿名訪客看到的欄位。公開端點常見的成因是「原本設計給某個特定情境用（如自助編輯表單預填），後來被發現剛好能重用一個既有的公開端點，於是直接加欄位進去，沒有意識到呼叫者身分已經涵蓋了匿名訪客」。

自選掃描範圍：逐一檢視 `SecurityConfig.authorizeHttpRequests()` 中所有 `permitAll()` 規則對應的 Controller 方法與回傳 DTO。

## 2. 調查方法與結果

逐條核對 `SecurityConfig` 的 `permitAll()` 清單（主控 session 直接讀碼，清單本身有限、範圍明確，未派背景 agent）：

- `/v2/auth/register`/`/login`/`/refresh`：認證流程本身，回傳僅 token/使用者基本資訊，非資料列表類端點，略過。
- `/v2/tenants/apply`：回傳 `TenantApplicationResponse`（僅申請本身的欄位），無既有店鋪資料外洩疑慮。
- `/v2/tenants/{id}`：**發現 🟡 `DEF-264`**（詳見 §2.1）。
- `/v2/public/**`：`grep` 全庫確認**目前沒有任何 Controller 對應此路徑前綴**，是一條沒有實際端點的保留/死規則，不構成風險，僅記錄供未來注意（若日後真的掛上端點，需重新審視其回傳範圍）。
- `/v2/posts`、`/v2/posts/**`：CMS 前台公開文章瀏覽，Sprint 82（`DEF-034`）已確認為刻意設計的公開端點範圍，本輪未重新翻案。
- `/v2/listings/*/card`：`PostController.getListingCard` 回傳 `M15Dto.ListingCardResponse`——逐欄核對（`listingId`/`listingType`/`title`/`coverImageUrl`/`basePrice`/`currentPrice`/`currency`/`availability`/`tenantName`/`ctaUrl`/`isActive`/`statusReason`），皆為專門為嵌入卡片設計的展示欄位，無內部/敏感欄位。**確認無缺陷**。
- `GET /v2/cms/pages/*`、`GET /v2/cms/banners/active`、`POST /v2/cms/banners/*/click`：Sprint 82（`DEF-034`）已確認的既有公開端點，本輪未重新翻案。
- `/actuator/health`：標準健康檢查端點，僅回傳存活狀態，非資料端點。
- `/ws/**`：WebSocket 握手本身放行，實際身份驗證在 STOMP CONNECT frame（`StompAuthChannelInterceptor`），Sprint 75（`DEF-035`）已系統性審查過，本輪未重新翻案。
- `POST /v2/payments/webhook/stripe`：Sprint 160（`DEF-202`）與 Sprint 188（`DEF-262`）已系統性審查過簽章驗證與可達性，本輪未重新翻案。

### 2.1 `DEF-264`：`GET /v2/tenants/{id}` 外洩內部採購審批門檻

`TenantController.getTenantDetails`（Javadoc 明文「Role: Guest+ (public access)」，`SecurityConfig` 對應 `permitAll()`）呼叫 `TenantService.getTenantDetails`，ACTIVE 狀態租戶的回傳 DTO 包含 `purchaseOrderApprovalThreshold`。

查證此欄位的來歷（`git log`/`DEFERRED_ITEMS_TRACKER.md` AI-2423 條目）：Sprint 85（`AI-2419`）新增 `Tenant.purchaseOrderApprovalThreshold`（M16 ERP 採購審批門檻，PRD §6.7.2，店鋪自助設定金額上限，超過需 SuperAdmin 核准），Sprint 89（`AI-2423`）**刻意**把它加進 `getTenantDetails` 的回傳——但 Sprint 89 的原始動機是「`TenantEditForm` 進編輯表單時看不到目前已設定的門檻值，只能盲寫覆蓋」，是針對「店鋪自助編輯情境」的修復，並非「刻意公開給任何訪客」的設計決策。問題在於 `TenantEditForm.tsx` 實際串接的 API（`API_ENDPOINTS.tenants.detail(tenantId)` → `/v2/tenants/{id}`）剛好正是這個公開端點（前端另有 `/v2/tenants/my`、`/v2/admin/tenants/{id}` 兩個authenticated 端點可選，但編輯表單重用了最方便的既有端點），使得 Sprint 89 補上的欄位連帶被公開端點一起放行。

**影響**：任何人（含未登入訪客、競爭對手）皆可對任一租戶 ID 探得其內部 ERP 採購審批金額門檻，包含「是否啟用此機制」本身（`null` = 未啟用 vs 有值 = 已啟用並可得知確切金額）。屬內部經營設定不當外洩，非帳號/金流/PII 類高風險缺口，判定 🟡 MEDIUM。

`contactEmail`/`contactPhone`/`storeDescription`/`logoUrl` 等其餘欄位經核對 `TenantControllerE2ETest.getTenantDetails_activeStatus_returnsFullInfo`（該測試本身即以匿名呼叫驗證這些欄位維持可見）確認為刻意的公開店面資訊設計，不在本次修復範圍內。

## 3. 修復範圍與實作

`TenantService.getTenantDetails`：

- 新增呼叫者租戶成員資格判斷：`TenantContext.getCurrentUser()` 取得目前使用者 ID（匿名訪客為 `null`），搭配既有 `tenantMemberRepository.existsByTenantIdAndUserId(tenantId, currentUserId)`（重用同檔案 `getFeatureToggles` 已建立的判斷模式，非新引入的查詢方法）判斷是否為本租戶成員。
- `purchaseOrderApprovalThreshold` 欄位改為 `isTenantMember ? tenant.getPurchaseOrderApprovalThreshold() : null`，非本租戶成員（含匿名訪客、其他租戶成員）一律隱藏。
- 未動 `contactEmail`/`contactPhone`/`storeDescription`/`logoUrl` 等既有公開欄位，也未動 `TenantUpdateResponse`（`updateTenant` 成功後的回應，呼叫者本就是已驗證的 owner，該欄位在那裡本就合理可見）。

未新增 Controller 層邏輯——`getTenantDetails` 只有這一個呼叫端（`grep` 確認），修法收斂在 Service 層單一入口即可。

## 4. 測試

`TenantServiceTest`：

- 既有 `getTenantDetails_activeStatus_returnsFullInfo` 更名為 `getTenantDetails_activeStatus_tenantMember_returnsFullInfo`，補上 `TenantContext.setCurrentUser(TEST_USER_ID)` 與 `tenantMemberRepository.existsByTenantIdAndUserId(...)` 回傳 `true` 的情境（代表 Sprint 89 原始意圖的合法使用情境：店鋪成員查看/編輯自己的店鋪），確認門檻值維持可見。
- 新增 `getTenantDetails_activeStatus_nonMember_hidesPurchaseOrderApprovalThreshold`（DEF-264 案例）：不設定 `TenantContext.setCurrentUser`（模擬匿名訪客），確認 `purchaseOrderApprovalThreshold` 為 `null`，同時確認 `contactEmail`/`storeName` 等既有公開欄位不受影響仍可見。

**紅燈先行（修復前對未修改的程式碼實際執行，非事後回想）**：`git stash` 暫存 `TenantService.java` 的修復，還原後執行兩個測試：
- `..._nonMember_hidesPurchaseOrderApprovalThreshold` 如期失敗（`expected: <null> but was: <5000.00>`），證實缺陷存在。
- `..._tenantMember_returnsFullInfo` 觸發 `UnnecessaryStubbingException`（`existsByTenantIdAndUserId` 的 stub 從未被呼叫），證實舊程式碼完全沒有任何呼叫者身分判斷邏輯。

還原修復（`git stash pop`）後兩案例皆轉綠燈，`TenantServiceTest` 全數 38 案例通過。

`TenantControllerE2ETest`（HTTP 層真實整合測試）核對後確認 `getTenantDetails_activeStatus_returnsFullInfo` 該測試建立的 `activeTenant` 從未設定 `purchaseOrderApprovalThreshold`（DB 預設 `null`）、也未斷言該欄位，不受本次修復影響，未改動。

## 5. 驗證結果

`mvn -o clean verify`（真實 postgres/redis）：詳見下方版本歷史記錄的測試統計。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-264`：✅ 已修復（Sprint 190）。

## 7. 誠實揭露總結

- 本輪未派出背景唯讀 agent——`SecurityConfig` 的 `permitAll()` 清單本身條目有限（12 條規則），直接逐條讀碼比協調 agent 更有效率。
- `/v2/public/**` 是一條沒有對應任何 Controller 的死規則，記錄於此供未來若真的掛上端點時重新審視，非本輪修復範圍。
- `DEF-264` 的根因是「Sprint 89 的合法修復意圖（店鋪成員編輯時預填門檻）被綁定在錯誤的端點上（公開端點而非 authenticated 端點）」，而非單純的「忘記加權限檢查」——這解釋了為何 Sprint 89 當時有寫測試（`getTenantDetails_activeStatus_returnsFullInfo`）卻仍然放行：那個測試驗證的是「ACTIVE 狀態下欄位確實回傳」，從未驗證過「呼叫者身分是否應該看得到」，因為 Sprint 89 的問題框架本身就沒有意識到這是一個公開端點。
