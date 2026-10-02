# Sprint 235 Plan — 被移除或只是受邀的成員不得再帶店鋪租戶與店員角色（DEF-329），Stripe Connect 只限真正店鋪的店主（DEF-327）

**Sprint**: Sprint 235
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（沿用 Sprint 234 的排程，順序為我的安排，屬推論）

[SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §1.1 把本輪排給 `DEF-329`（JWT 租戶解析不看成員狀態）與 `DEF-327`（Stripe Connect onboarding 不驗店主）；`DEF-328`（共用限流桶）已在 Sprint 234 因為 E2E 守門實測而提前修復。**`DEF-329` 必須先於 DEF-319**：DEF-319 做完後真實店鋪租戶會擁有真實消費者的訂單與客服工單，被移除或只是受邀的人若仍帶著店鋪租戶登入，就多了一條讀取這些資料的路徑。

使用者對 Sprint 234 留下的兩件事尚未回覆：`DEF-326`（SELLER／HOST 共用系統租戶，產品決策）與 §6.4（限流單位的工程決策，請使用者否決或調整）。本輪沒有新的使用者決策，**但有兩個會影響既有資料或既有行為的變更，已在 §2 標明**。

### 1.2 `DEF-329` 的真實全棧重現（修復前，不靠推論）

稽核 agent 只讀碼提出（Sprint 234 B7）。本輪先寫 `frontend/e2e/at-store-member-revocation-real.spec.ts`（打包 JAR＋PostgreSQL＋Redis＋Playwright，完全不 mock，資料全由真實流程建立：店主開店→受邀人註冊→店主邀請→接受→移除），再以探針副本（只記錄不斷言，用完即刪）觀察四個情境，判斷依據是**登入／換發回傳的 JWT 本身**（`role`、`tenantId`），那是之後所有授權與租戶過濾的唯一來源：

| 情境（修復前） | 實測的 JWT |
|----------------|-----------|
| 店主邀請為 STORE_STAFF，**受邀人尚未接受**就登入 | `role=BUYER`，**`tenantId`＝店鋪**（只是被邀請，就帶真實店鋪租戶） |
| 受邀人接受後登入（對照組） | `role=STORE_STAFF`，`tenantId`＝店鋪（合法） |
| **店主移除該成員**後，被移除者重新登入 | **仍是 `STORE_STAFF`＋店鋪租戶**——移除收不回存取 |
| 被移除者用移除前取得的 refresh token 換發 | **仍是 `STORE_STAFF`＋店鋪租戶** |

### 1.3 讀碼補充與更正

1. **範圍比稽核報告更廣**：`PostController` 推導貼文租戶時，**第一來源是成員資格（不看狀態），不是 JWT 的租戶**——被移除、角色仍是 `STORE_STAFF` 的人仍通過 `@PreAuthorize`，貼文就建在原店鋪。`TenantService` 的四個成員資格檢查（公開詳情的成員判斷、功能開關檢視、功能開關修改、成員名單）與兩個「我的店鋪」清單也都用不看狀態的查詢。
2. **更正我自己寫測試時的一個錯誤假設**：我原本想用「被移除且角色是 SELLER 的成員」測 `PostController`，執行時才發現**邀請端點只允許 `STORE_STAFF`**（`parseInviteRole`；`AddMemberRequest` 欄位註解寫的 `STORE_MANAGER`／`SELLER`／`HOST` 是過時的），所以那個情境根本不可達。改用更貼近真實的「遺留資料」情境（見下一點）。
3. **遺留資料**：修復前就被移除的人，`users.role` 仍殘留 `STORE_STAFF`。只修登入租戶解析的話，他們下次登入會帶「系統租戶＋店員權限」——是另一種外洩。所以需要一次性的資料修正（Flyway `V87`），詳見 §3。
4. **`PostController.getTenantIdFromUserId` 是不可達的程式碼**：它只在 `extractTenantIdInfoFromUserDetails` 取得 principal 後才會被呼叫，而該方法用反射找 `getPrincipal()`，`UserPrincipal` 沒有這個方法（`@AuthenticationPrincipal UserDetails` 也接不到它），永遠回 `null`。所以這個備援路徑的突變**無法被任何測試觀察到**（等價突變，§4 的 D03）；仍一併改成只認有效成員，避免日後被啟用時重現同一個缺陷。
5. **Stripe Connect 的角色與稽核報告的描述一致且更糟**：兩個端點是 `hasRole('SELLER')`；核准開店後的角色是 `STORE_OWNER`，所以**真正的店主從來呼叫不了，目前唯一能呼叫的就是沒有店鋪的 SELLER**。這個功能對真實店鋪從來沒有可用過（前端也沒有任何呼叫）。

## 2. 使用者決策與需要使用者知悉的行為變更

本輪沒有新的使用者決策。但有兩個**行為變更**（我的判斷，請使用者否決或調整）：

1. **`V87` 資料遷移會改既有資料庫的使用者角色**：沒有任何有效（ACTIVE）成員資格、角色卻仍是 `STORE_STAFF` 的人，收回成 `BUYER`。不變量是「角色 `STORE_STAFF` 只應出現在至少有一筆 ACTIVE 成員資格的人身上」；不動其他任何列，可重複執行。正式環境部署前可先用同一個 `SELECT` 看會影響多少人（`SELECT id, email FROM users u WHERE role='STORE_STAFF' AND NOT EXISTS (SELECT 1 FROM tenant_members m WHERE m.user_id=u.id AND m.status='ACTIVE')`）。
2. **Stripe Connect 兩個端點的角色由 `SELLER` 改成 `STORE_OWNER`**：真正的店主第一次能呼叫它們（仍受每租戶的 `STRIPE_CONNECT_ENABLED` 開關控制，預設關閉，所以預設沒有任何新曝險）；沒有店鋪的 `SELLER` 不能再呼叫（原本唯一能呼叫的就是漏洞情境）。同一個 controller 的 `GET /v2/seller/dashboard` **本輪不動**（仍是 `hasRole('SELLER')`，真正的店主同樣呼叫不了），併入 `DEF-326` 的決定。

## 3. 實作內容

**`DEF-329`**
- `AuthService.resolveTenantForUser`：只採**有效（ACTIVE）**成員（`findByUserIdAndStatus`）。受邀未接受（INVITED）與已移除（REMOVED）的人登入、換發都不再帶店鋪租戶。
- `TenantService.removeMember`：移除後呼叫 `revokeStaffRoleIfNoActiveMembership`——被移除者若角色是 `STORE_STAFF` 且沒有其他有效成員資格，收回成 `BUYER`；仍是別間店鋪有效店員者、其他角色者不動。
- 成員資格檢查只認有效成員：`TenantMemberRepository` 新增 `existsByTenantIdAndUserIdAndStatus`，**移除**不看狀態的 `existsByTenantIdAndUserId`（危險且已無人使用）；`TenantService` 四個檢查與兩個「我的店鋪」清單、`PostController` 兩處租戶推導改用有效成員查詢。`UserPrivacyService` 的資料匯出仍用不看狀態的 `findByUserId`——那裡要列出全部歷史成員資格，是對的。
- `V87__Revoke_Staff_Role_Without_Active_Membership.sql`：遺留資料修正（§2 第 1 點）。

**`DEF-327`**
- `TenantStripeConnectService.requireStoreOwner`：租戶必須是真實店鋪租戶（`TenantContext.isStoreTenant`），且呼叫者在資料庫裡是該店鋪的店主（`existsByTenantIdAndUserIdAndStoreRole(…, STORE_OWNER)`）；放在 service 層，涵蓋 `initiateOnboarding` 與 `getAccountStatus`，日後任何新的呼叫路徑也被涵蓋。檢查先於功能開關，不洩漏開關狀態。
- `SellerDashboardController`：兩個 Stripe Connect 端點 `hasRole('SELLER')` → `hasRole('STORE_OWNER')`。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元（+12） | `TenantServiceTest` +8：`removeMember` 收回角色 3（無其他有效成員資格→降成 BUYER／仍是別間店有效店員→不動／角色不是 STORE_STAFF→不動且不查成員）；`getFeatureToggles` 2、`getTenantMembers` 2（非有效成員→`E-4031` 且不查資料／有效成員照常）；`getTenantsByUser` 1（只依有效成員資格列店鋪）。`TenantStripeConnectServiceTest` +4：系統租戶的呼叫者、非店主、沒有使用者脈絡、狀態查詢——都 `E-1007` 且不碰 Stripe。既有的 6 個認證／換發／OAuth 測試檔與 `TenantServiceTest` 的 stub 由「不看狀態」改成「有效成員」（機械式更新；這些測試原本就釘住「有成員資格→帶店鋪租戶」，修復後仍通過，順便證明有效成員不受影響） |
| HTTP＋真實 PostgreSQL（+7） | `StoreMemberRevocationE2ETest`：邀請／接受／拒絕／移除／登入／換發都走真實端點，斷言回傳的 **JWT 本身**。受邀未接受、拒絕邀請、已移除、已移除後用舊 refresh token 換發——都不得帶店鋪租戶與店員角色；接受後帶店鋪租戶與 `STORE_STAFF`（對照組）；被一間店移除但仍是另一間店的有效店員→角色不動、租戶改為仍有效的那間；**遺留資料**（修復前就被移除、`user.role` 仍是 `STORE_STAFF`）嘗試建立貼文→不得建在原店鋪（`PostController` 的成員資格推導） |
| 遷移邏輯（+2） | `RevokeStaffRoleMigrationIntegrationTest`：讀 `V87` 的實際 SQL 對種好的資料執行（交易內、結束回滾）：已移除／只剩受邀／完全沒有成員列的 `STORE_STAFF` 收回成 `BUYER`；有效店員、被一間店移除但仍是另一間店有效成員者、本來就是 BUYER 者、店主一律不動；可重複執行。Flyway 本身能否套用由 `make validate-schema-doc`（對乾淨 PostgreSQL 套用 87 個遷移）驗證，已通過 |
| 控制器整合（+2） | `M13SellerStripeConnectIntegrationTest`：兩個端點 `STORE_OWNER`→200、自助註冊的 `SELLER`→403 且不得呼叫到服務（原本相反：SELLER 200） |
| 真實後端 Playwright（+4） | `at-store-member-revocation-real.spec.ts`（MREV-01～04）：完全不 mock，資料全由真實流程建立。**修復前實測轉紅**：MREV-01 失敗（受邀未接受的人 JWT 帶店鋪租戶）；探針副本確認其餘三個情境（接受後的對照、移除後登入、移除後換發）的 JWT 內容，見 §1.2 |

### 突變驗證（Rule 9：測試必須在守門被拿掉時失敗）

一次只改一處，跑相關測試類別，結束後從備份還原並以 `cmp` 驗證。

| # | 改動 | 結果 |
|---|------|------|
| D01 | `AuthService.resolveTenantForUser` 改回不看狀態（＝**修復前行為**） | 被抓到：受邀、拒絕邀請、已移除的真實 DB 測試（完整紅燈清單見當時的突變輸出，未另存） |
| D02 | `PostController`（TenantContext 路徑）不看成員狀態 | 被抓到：遺留資料的貼文測試 |
| D03 | `PostController`（`getTenantIdFromUserId`）不看成員狀態 | **存活，且無法被任何測試觀察到**：該方法是不可達程式碼（§1.3 第 4 點）。等價突變，不是測試缺口 |
| D04 | `getTenantsByUser` 不看成員狀態 | **初次存活**（沒有任何測試覆蓋這個方法）→ 補測試後被抓到 |
| D05 | `getTenantsListByUser` 不看成員狀態 | 被抓到 |
| D06 | 公開詳情的成員判斷改認錯狀態 | 被抓到 |
| D07 | `getFeatureToggles` 成員檢查改認錯狀態 | **初次存活**（既有測試從不設定使用者，成員檢查被整段略過）→ 補測試後被抓到 |
| D08 | `updateFeatureToggle` 成員檢查改認錯狀態 | 被抓到（5 個既有測試） |
| D09 | `getTenantMembers` 成員檢查改認錯狀態 | **初次存活**（沒有任何單元測試）→ 補測試後被抓到 |
| D10 | `removeMember` 不收回店員角色 | 被抓到：真實 DB 的移除登入與換發、單元 3 |
| D11 | 收回角色時不檢查角色（任何角色都降級） | 被抓到 |
| D12 | 收回角色時不檢查是否仍是別間店的有效成員 | 被抓到：真實 DB 與單元 |
| C01 | `requireStoreOwner` 不排除系統租戶 | 被抓到 |
| C02 | `requireStoreOwner` 不檢查店主身分 | 被抓到 |
| C03 | `initiateOnboarding` 不呼叫守門 | 被抓到 |
| C04 | `getAccountStatus` 不呼叫守門 | 被抓到 |
| C05 | onboarding 端點角色改回 `SELLER` | 被抓到（店主 200 與 SELLER 403 兩邊都轉紅） |
| C06 | status 端點角色改回 `SELLER` | 被抓到 |

**初次存活的三個（D04、D07、D09）是測試缺口，不是巧合**：它們都是成員資格相關的 service 方法，而 `TenantServiceTest` 對它們的覆蓋有三種洞——整個方法沒有測試（`getTenantsByUser`、`getTenantMembers`）、測試從不設定使用者所以檢查被整段略過（`getFeatureToggles`）。這與 Sprint 234 的 M04 是同一類（Rule 9）：改完程式碼、跑綠既有測試，不等於守門有測試。

## 5. 驗證結果

- **`mvn -o clean verify`**（checkstyle main＋test、PMD、JAR 打包）：**BUILD SUCCESS，15 分 10 秒**；單元 **2018**（Sprint 234 為 2006，+12）／整合 **707**（696，+11：真實 DB 撤銷流程 7、遷移邏輯 2、Stripe Connect 控制器 2）／**0 失敗**／0 略過；checkstyle 0 違規（主程式與測試）；PMD 通過。
- **`make validate-schema-doc`**：對乾淨 PostgreSQL 依序套用 **87 個** Flyway 遷移（含 `V87`），SRD／PRD 文件與實際 schema 一致，通過。`V87` 的語法與可套用性因此在真實 PostgreSQL 18 上確認過。
- **`make validate-e2e`**：**129 個測試 125 通過／4 略過（既有基準）／0 失敗（3.5 分鐘）**；schema 對齊（`ddl-auto=validate` 對 Flyway 乾淨重建的資料庫啟動成功，含 `V87`）。新增的 4 個成員撤銷案例（MREV-01～04）全綠，修復前 MREV-01 實測轉紅（§4）。第一次就通過、沒有出現 Sprint 234 的限流失敗（那兩個修正已就位）
- **前端**：`tsc --noEmit`、`eslint` 通過（本輪只新增一個 spec，前端 `src` 沒有變動）。
- **push 與雲端 CI**：已 push（2026-10-03，`ae63b79..172f0fe main -> main`，含 `a947ce8`（`validate-schema-doc` 的 `pg_isready` 修復）；pre-push 輕量守門通過）。✅ 雲端 CI 全綠 run **37050004694**，三個 job 全部 success，共 10 分 40 秒：Backend Unit 2m30s／Frontend Lint & Build 1m06s／Backend Integration & Package **8m03s**。
- **耗時觀察（承 Sprint 233、234）**：本機全量 `mvn verify` 本輪 15 分 10 秒（Sprint 234：17 分 19 秒；Sprint 233：15 分 21 秒）。雲端整合 job 近七次為 5m01s／6m02s／6m00s／4m15s／7m47s／7m46s／**8m03s（本輪）**：Sprint 233 起連續三次落在 7m46s～8m03s，高於 bcrypt 12 之前的四次（4m15s～6m02s），第三個資料點仍指向同一個方向；**仍未量測、不能算證實**（本輪另外多了 23 個測試，也可能貢獻一小部分）。

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **已簽發的 access token 在有效期內（15 分鐘）仍可使用**：`JwtAuthenticationFilter` 直接信任 JWT 的角色與租戶，不複驗成員狀態（`DEF-331` (d)）。被移除的人最多還有 15 分鐘；refresh 不需要撤銷——換發會重新解析租戶與角色，拿到的是降級後的 token（真實 DB 測試與 Playwright 皆驗證）。
2. **`V87` 的實際影響人數未知**：正式環境有多少遺留的「已移除但仍是 `STORE_STAFF`」使用者，本輪無法也不該去查正式資料庫；§2 附了部署前可先跑的 `SELECT`。
3. **移除成員時角色降成 `BUYER`，不是還原成原本的角色**：`acceptInvite` 會覆蓋 `user.role`（原本可能是自助註冊的 `SELLER`／`HOST`），原值沒有保留。
4. **邀請端點只允許 `STORE_STAFF`**（`AddMemberRequest` 欄位註解寫的其他角色是過時的）。這個過時註解本輪沒有順手改（Rule 3）。
5. **Stripe 實際行為仍未驗證**：撥款流向是推論，`STRIPE_CONNECT_ENABLED` 是否對系統租戶開過未知；本輪只確認「該擋的人被擋下」，沒有任何東西打到真實 Stripe。
6. **`GET /v2/seller/dashboard` 本輪沒動**：仍是 `hasRole('SELLER')`，真正的店主呼叫不了、沒有店鋪的 SELLER 反而能讀到系統租戶的統計（併入 `DEF-326`）。
7. **`DEF-331` 新增 (f)**：`TenantService.updateFeatureToggle` 的註解寫「驗證呼叫者是店主」，實際只驗「是有效成員」；目前 controller 的 `@PreAuthorize` 擋住店員所以不可達，但 service 層沒有自己的店主檢查（讀碼，未實測）。

### 6.2 待使用者決定／確認（累積）

| 項目 | 內容 | 我的建議 |
|------|------|----------|
| `DEF-326` | SELLER／HOST 自助註冊後落在系統租戶，持有商品／房源／定價／運費／CMS／貼文寫入權限，彼此與平台自營資料互通（[SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §6.1） | 未歸屬店鋪者不得寫入 |
| Sprint 234 §6.4 | 沒有店鋪的使用者的限流單位（已登入→使用者、匿名→來源 IP，容量 100 次／分）是工程決策，PRD 未定義 | 同意（有疑慮可調整容量或加全域上限） |
| 本輪 §2 | `V87` 會改既有資料庫的使用者角色；Stripe Connect 端點由 `SELLER` 改為 `STORE_OWNER` | 同意（部署前先跑 §2 的 `SELECT` 看影響人數） |

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果。
2. **Sprint 236～237：DEF-319（訂房 → 訂單）**。`DEF-329` 已先修完，前置條件滿足。訂房先做（單一房源、沒有拆單問題）：`buildBookingCore` 改蓋房源所屬租戶＋Flyway 回填歷史訂房＋優惠券與運費模板以哪個租戶解析——若 PRD 沒寫清楚，以 `AskUserQuestion` 請使用者拍板，不自己猜；順便收斂三份 `SYSTEM_TENANT_UUID`（`DEF-330` (b)）；`at-seller-dashboard-real.spec.ts` 的 SDASH-03／04 移除 `test.fail()`。
3. **待使用者**：§6.2 的三項。
4. 選配：整合測試的 bcrypt 強度（先量測，見 [SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §5）。
