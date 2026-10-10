# Sprint 248 Plan — 店鋪成員管理 UI（DEF-321 (a)）

**Sprint**: Sprint 248
**日期**: 2026-10-10

## 1. 起點與範圍

### 1.1 起點

Sprint 247（DEF-353 訂房納入結算）完成後，使用者確認下一輪方向：接續 Sprint 232 收尾拍板、原排入 Sprint 238～242 但被訂房功能線（DEF-345 起）插隊延後的四項之一——**店鋪成員管理 UI**（`DEFERRED_ITEMS_TRACKER.md` DEF-321 (a)）。PRD §10.1.2 已定義路由 `/dashboard/members`，後端 7 個端點（列表／邀請／我的邀請／接受／拒絕／改角色／移除）自 Sprint 98／99／210 起即存在，但前端零呼叫點，店主只能用裸 API 加店員。

### 1.2 動工前與使用者確認的兩個落地細節

調查後端發現 `AddMemberRequest.userId` 只吃 UUID，無 email 查詢端點；且「我的邀請」（接受／拒絕）兩個端點無 `@PreAuthorize`，服務層邏輯顯示受邀當下對方可能還只是一般買家，進不去僅限 StoreOwner 的 `/dashboard/members`。這兩點照後端現狀直接做會做出不太能用或放錯位置的東西，經 `AskUserQuestion` 徵詢：

| 題目 | 使用者選擇 |
|------|-----------|
| 邀請表單輸入方式 | 新增 email 查詢 userId 的小型後端端點（而非直接要求輸入 userId，或先跳過邀請子功能） |
| 「我的邀請」畫面位置 | 放在帳戶頁面 `/account`（任何登入使用者都看得到），而非僅限店主的 `/dashboard/members` |

### 1.3 方法

- 後端新增 `GET /v2/tenants/:id/members/lookup`（email→userId），權限與既有 `inviteMember` 相同（僅該店鋪 StoreOwner）；回應只含 `userId`／`displayName`／`email`／`avatarUrl`，不透露是否已是成員（避免邀請送出前就多一層使用者列舉面）。
- 前端新增 `/dashboard/members`（店主視角：成員列表、以 email 查詢後兩步驟確認邀請、移除成員／撤回邀請）與 `(auth)/account` 新增「待確認的店鋪邀請」卡片（接受／拒絕）。
- 遵循既有慣例（非本輪發明）：`services/<feature>.ts` 單例 class（比照 `returns.ts`）、`getApiErrorInfo()` 取得後端 `code` 後本地覆寫文案（因 `BusinessException.getUserMessage()` 回傳的是 `ErrorCode` 罐頭文字而非拋出時的 `details`）、無 Dialog／Toast library（比照既有頁面：行內 toggle-state 確認、`Alert`／`data-testid="action-message"` 色塊）。
- 角色選擇不做 UI：`parseInviteRole`／`updateMemberRole` 服務層目前只接受 `STORE_STAFF`，加一個只有一種選項的下拉是無意義的介面（YAGNI）；`updateMemberRole` 端點本輪未建對應 UI——現有成員只會是 `STORE_OWNER`（擋改）或 `STORE_STAFF`（唯一合法目標值本身），此端點目前功能上是 no-op，暫不建 UI，待未來出現第二個可指派角色再補。

## 2. 使用者決策與假設

### 2.1 已拍板（本輪 `AskUserQuestion`）

見 §1.2 兩題。

### 2.2 本輪假設（PRD／既有決議未明定）

| 題目 | 本輪採用 | 理由 |
|------|----------|------|
| email 查詢是否透露「已是成員」 | 不透露，統一回使用者基本資料；衝突情境留給送出邀請時的 `E-4092` | 查詢端點的用途單純是「這個 email 對應哪個 userId」，與「能不能邀請」是兩個問題，合併判斷會把查詢做成另一個使用者列舉面 |
| 前端成員列表是否顯示 `updateMemberRole`（改角色）控制項 | 不顯示 | 服務層目前只接受 `STORE_STAFF`，現有成員要嘛是擋改的 `STORE_OWNER`、要嘛已經是唯一合法目標值，介面上的「改角色」按鈕永遠無事可做 |
| `/dashboard` 首頁 `QUICK_LINKS` 是否加入捷徑 | 加入（`店鋪成員管理`） | 該陣列是使用者發現新頁面的實際入口，不加入等同功能做了但沒人找得到 |

## 3. 實作內容（清單）

| 檔案 | 變更 |
|------|------|
| `backend/.../api/dto/MemberCandidateResponse.java` | 新增：email 查詢回應 DTO |
| `backend/.../api/controller/TenantController.java` | 新增 `GET /tenants/:id/members/lookup` |
| `backend/.../core/tenant/TenantService.java` | 新增 `lookupMemberCandidateByEmail` |
| `backend/.../core/tenant/TenantServiceTest.java` | 新增 3 案例（成功／非 StoreOwner／查無使用者） |
| `backend/.../api/controller/TenantMemberLookupIntegrationTest.java` | 新增：真實 PostgreSQL／HTTP／權限鏈整合測試 3 案例 |
| `frontend/src/lib/api.ts` | `API_ENDPOINTS.tenants.members.*` |
| `frontend/src/services/tenantMember.ts` | 新增：成員管理 service（型別、label map、API 呼叫） |
| `frontend/src/app/dashboard/members/page.tsx` | 新增：店主視角頁面 |
| `frontend/src/app/(auth)/account/page.tsx` | 新增「待確認的店鋪邀請」卡片 |
| `frontend/src/app/dashboard/page.tsx` | `QUICK_LINKS` 加入捷徑 |
| `frontend/e2e/at-dashboard-members-real.spec.ts` | 新增：真實瀏覽器 E2E（邀請→接受→移除） |
| [API_M17_Tenant.md](../02_architecture/api/API_M17_Tenant.md) | 新增 §11（email 查詢端點）；錯誤碼表補 `E-2001` |
| [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) | DEF-321 (a) 標記已完成；新增 DEF-355／DEF-356（見 §5） |
| [RELEASE_TRACKER.md](RELEASE_TRACKER.md) | Sprint 248 列 |
| 本檔 | 計畫書 |

## 4. 守門與測試

| 層 | 內容 | 結果 |
|----|------|------|
| 單元 | `TenantServiceTest` 新增 3 案例 | ✅ 全數通過（該檔共 53 個測試，0 失敗） |
| 整合（真實 PostgreSQL） | 新增 `TenantMemberLookupIntegrationTest`（3 案例：成功查詢／email 不存在 403 E-2001／非該店 StoreOwner 403 E-4031） | ✅ 全數通過 |
| 編譯 | 每次新增方法／端點後 `mvn -o clean compile`／`test-compile`，確認有 `Compiling N source files` 字樣 | ✅ 確認為真實編譯結果 |
| 前端型別／Lint／建置 | `tsc --noEmit`（0 錯誤）、`eslint`（0 error，僅既有慣例性 warning）、`next build`（成功，`/dashboard/members` 在路由清單中） | ✅ 通過 |
| 全量回歸 | `mvn -o clean verify`（改動生產邏輯，依規範跑全量） | ✅ BUILD SUCCESS：整合 811（808+3 新案例，0 回歸）；checkstyle（main+test）0 違規 |
| 真實瀏覽器 E2E（手動，非守門一部分，驗證功能本身） | `make up` 起 dockerized 全棧 → 發現並繞過兩個與本功能無關的既有基礎設施缺陷（DEF-355／DEF-356，見 §5）→ 改用 `npm run dev`（正確 `NEXT_PUBLIC_API_URL`）+ 既有真實後端容器跑 `at-dashboard-members-real.spec.ts` → 第一次執行發現並修正本頁自身的 hydration 缺陷（§5 第 3 點）→ 3 案例全數通過 | ✅ 通過 |
| 本地完整守門 | `make validate-release`（等價雲端 CI：act backend-unit + backend-integration + frontend + schema 漂移守門 + 完整 Playwright E2E 套件，含新規格） | ✅ 第二次執行全數通過：E2E 141 passed／4 skipped／0 failed（4.6 分鐘）；三個 act job 皆成功；schema 無漂移；FULL 記錄已寫，30 分內放行 push。**第一次執行有 1 個不相關 spec 失敗，見 §6，已排除後確認綠燈** |

## 5. 過程中發現但與本功能無關的缺陷（誠實揭露）

1. **DEF-355**（🟠 登記未修，需使用者確認）：`docker-compose.yml`／`docker-compose.override.yml`／`.env.example` 的 `NEXT_PUBLIC_API_URL` 預設值結尾多一段 `/v2`，與前端程式碼本身已經以 `/v2/...` 開頭的端點路徑疊加，透過瀏覽器的每個 API 呼叫都變成 `/api/v2/v2/...` 而 404。`scripts/validate-e2e.sh`（本機 E2E 守門唯一路徑）另外明確指定不含 `/v2` 的值，從未觸發此路徑——合理推測 dockerized 前端容器自建立以來可能從未被任何人真正用瀏覽器點過。
2. **DEF-356**（🟠 登記未修，需使用者確認）：`make up` 全新 dev volume 時，Postgres 的 `docker-entrypoint-initdb.d` 把 91 個 Flyway migration 檔當純 SQL 直接執行，但檔名未補零、容器以字母序而非數字序逐一跑過，導致 `V7__M17_Test_Data_Init.sql` 的 E2E 固定帳號 `admin@nextkey.local` 未被建立（多數其他資料仍建立成功），且容器日誌無任何 ERROR，不容易聯想到是初始化順序問題。
3. **本頁自身的 hydration 缺陷（已修，非登記為缺陷）**：`/dashboard/members` 的 nav 列直接在 JSX 內呼叫 `AuthService.getCurrentUser()?.email`（SSR 階段讀不到 `localStorage`），與多個既有頁面（`orders/page.tsx` 等）共用同一行寫法。真實瀏覽器實測：hydration mismatch 導致 React 判定需要重新掛載子樹，連帶清空使用者剛輸入到 Email 欄位的文字，邀請按鈕因此永遠停留在 disabled。本輪僅修正自己新增的這一份檔案（改在既有的 `useEffect` 內讀取、存進 state 再渲染），未觸碰其他頁面的相同寫法（不在本輪範圍內，是否要系統性修正留給使用者決定）。

兩個基礎設施缺陷（DEF-355／DEF-356）依 CLAUDE.md「Docker 管理限制規則」不可由 AI 逕自修改 docker-compose／migration 相關檔案，僅登記待使用者確認方向；為了完成手動瀏覽器驗證，本輪僅在本機暫時繞過（改用 `npm run dev` 搭配正確環境變數跑前端、直接以 SQL 補一筆既有 migration 本來就會寫入的測試固定帳號列），未修改任何 Docker／migration 檔案本身，驗證完已清空暫時建立的本機 Docker 資源與 `.env`（`.env` 為 gitignored 本機檔案，等同 `make setup` 會自動產生的內容，予以保留供後續本機開發使用）。

## 6. 第一次 `make validate-release` 的 1 個失敗 spec 與排查

第一次執行時，`at-account-data-rights.spec.ts` 的「刪除帳戶」案例失敗（逾時等不到 `account-delete-open`，頁面快照顯示未登入狀態）。此 spec 與本功能表面無關，但確實會造訪本輪有修改的 `/account` 頁面，故未直接當作環境雜訊略過，實際排查：

- **具體機制**：帳戶頁新增的 `listMyInvites()` 背景查詢（掛載時自動呼叫，非使用者觸發）若恰好拿到 401，會進入 `apiClient` 共用攔截器既有的 refresh-token 流程（`frontend/src/lib/axios.ts`）；該流程失敗時的既有設計是清空整個 session 並導向登入頁——對一個使用者感知不到的背景查詢而言，這個全域副作用完全不成比例，且與頁面原本「所有 API 呼叫皆使用者點擊觸發」的慣例不同（本頁新增的這次呼叫是本頁第一個掛載時自動觸發的 API 呼叫）。
- **隔離驗證**：以 `--repeat-each=5`（2 workers）單獨重跑該 spec 10 次，第 1 輪兩案例皆因等待下載／導轉逾時而失敗（研判為 Next dev server 對該路由的首次 on-demand 編譯延遲，與本次修改無關），其餘 8 次全數通過，過程中**未再出現任何「未預期登出」的訊號**——不足以單獨確認原始失敗的根因，但也未發現其他可疑之處。
- **已採取的防護**（已修正，非僅觀察）：`listMyInvites()` 改為帶 `_retry: true` 呼叫，讓共用 401 攔截器將此請求視為「已重試過」而直接結束，不觸發 refresh／清空 session 的全域副作用。此修正本身是否為原始失敗的真正根因無法百分之百確認（也可能單純是既有已知的 E2E 高併發登入／註冊速率限制雜訊，見 `e2e-gate-flake-rate-limiters` 長期記憶），但修正後第二次完整執行 `make validate-release`（含這個 spec 在內）141 passed／0 failed，誠實記錄於此，不隱藏這個中間過程。
