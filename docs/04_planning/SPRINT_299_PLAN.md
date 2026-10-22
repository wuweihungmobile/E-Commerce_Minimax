# Sprint 299 Plan — 依使用者在 Sprint 298 收尾的三個決定：DEF-400（lint 守門）、DEF-402（訪客日曆維持需登入）、DEF-403（法務佔位頁）

## 1. 起點與範圍

### 1.1 起點（使用者的明確決定，不是推論）

[Sprint 298](SPRINT_298_PLAN.md) 收尾時，我用 `AskUserQuestion` 就三個「只有使用者能決定」的項目各問一題，使用者**本人**回答（真實的互動輸入，不是我的推論）：

| 項目 | 使用者的選擇 | 是不是我建議的 |
|---|---|---|
| `DEF-400`：`lint` 加 `--max-warnings 0` | 依照建議 | 是 |
| `DEF-402`：匿名訪客看不到房源日曆（PRD US-G1） | **維持需登入，改 UI 與 PRD** | **不是**（我建議的是 (a) 新增公開唯讀日曆端點） |
| `DEF-403`：頁尾 `/privacy`、`/terms` 死連結 | 建「即將公告」佔位頁 | 是 |

照使用者的決定做，包括 `DEF-402` 與我的建議不同的那一項。

### 1.2 成功標準（開工時定義）

1. `DEF-400`：現況 0 warning 時 `npm run lint` 仍通過；刻意放一個 warning 時失敗；staged 的 warning 檔被 pre-commit 擋下；CI 與 `make` 自動套用。
2. `DEF-403`：`/privacy`、`/terms` 回 200、頁尾連結點進去不是 404；對未修建置先紅、修復後綠。
3. `DEF-402`：訪客不再呼叫日曆端點、日曆區顯示「登入後可查看即時空房」、沒有錯誤訊息；按「查詢可用性」得到「請先登入」；已登入買家仍看得到真實日曆；PRD／FRD 同步。
4. 新測試必須能因為正確的原因轉紅：修復前的紅燈＋修復的反向突變。

## 2. DEF-400：前端 lint 守門加 `--max-warnings 0`

**改動**：`frontend/package.json` 的 `lint` 改為 `eslint --max-warnings 0`；前端 pre-commit hook（`frontend/.husky/pre-commit`）對 staged 檔的 `eslint --fix` 同樣帶 `--max-warnings 0`（失敗訊息改為「errors and warnings」）；`pre-commit-check` 腳本（開發指南稱它是 hook 的合併手動版）也帶旗標；`docs/06_quality/FRONTEND_PRECOMMIT_GUIDE.md` 的步驟表同步。CI（`.github/workflows/ci.yml:276`、`act-compat.yml:295`）與 `Makefile:260` 都是 `npm run lint`，**不需改動就會自動套用**（`grep` 確認）。

**驗證**（暫放檔皆已刪除，`git status` 只剩預期的兩個修改）：

| 檢查 | 結果 |
|---|---|
| (a) 正向：目前 0 warning，加旗標後 `npm run lint` | 退出碼 0，輸出只有 npm 標頭 |
| (b) **反向突變**：刻意放一個未使用變數（warning）後 `npm run lint` | **退出碼 1**：`✖ 1 problem (0 errors, 1 warning)`、`ESLint found too many warnings (maximum: 0).` |
| (c) hook 路徑：把同一個檔 `git add` 後執行 `frontend/.husky/pre-commit` | **退出碼 1**，被擋下，同樣的訊息 |

這修的正是 Sprint 297 記下的教訓「ESLint 對 warning 的退出碼是 0」：現在 warning 會讓退出碼非 0。

## 3. DEF-403：`/privacy`、`/terms` 佔位頁

新增 `frontend/src/app/(storefront)/privacy/page.tsx`、`terms/page.tsx`（Server Component；放 `(storefront)` 以沿用頁首與頁尾；先讀過 Next 16.4.2 隨附的 `page.md` 與專案既有頁面寫法）：標題＋「內容即將公告。」，**不含任何法律文字**。正式內容仍待使用者提供，有了之後替換佔位文字並更新規格斷言。

新增真實前端 E2E `at-legal-pages.spec.ts`（`E2E-LEGAL-01`：直接開啟回 200、標題與「內容即將公告」；`E2E-LEGAL-02`：從登入頁頁尾的連結點進去不是 404；各對兩個路徑，共 4 個案例）。

| 驗證 | 結果 |
|---|---|
| 修復前（對未修的建置） | **4／4 紅**：`Expected: 200  Received: 404`；從登入頁頁尾點進去看不到標題 |
| 修復後 | 4／4 綠；`npm run build` 頁數 68 → **70** |

## 4. DEF-402：房源頁日曆對訪客維持需登入，改 UI 與 PRD

### 4.1 為什麼不是公開端點

使用者選擇的是 (b)。`GET /v2/bookings/calendar` 與 `/availability` 維持需要 `booking:read`，不新增任何公開資料。（Sprint 298 查到的相關事實仍有效：若日後改走 (a)，`CalendarResponse` 含 `bookingId` 與 `appliedRuleName`，公開版本必須砍掉。）

### 4.2 改了什麼

| 檔案 | 異動 |
|---|---|
| `frontend/src/components/storefront/MonthCalendar.tsx` | 登入狀態用 `useSyncExternalStore(subscribeAuth, getAuthenticatedSnapshot, getAuthenticatedServerSnapshot)`（**比照共用 Header 的既有慣例**）；訪客的 effect 早退、**不發日曆請求**；日曆區顯示 `data-testid="calendar-login-hint"` 的提示「登入後可查看即時空房與每日價格，仍可手動輸入日期」（沿用原本錯誤訊息的位置，改用中性的灰字而不是紅字）；訪客的 `aria-busy` 不再恆為 true |
| `frontend/src/services/authStore.ts` | 新增 `getAuthenticatedSnapshot()`（是否持有 accessToken 的 boolean）與 `getAuthenticatedServerSnapshot()`（SSR／hydration 一律 `false`），放在既有的 email／角色快照函式旁 |
| `frontend/src/components/storefront/ListingDetail.tsx` | 「查詢可用性」遇到 401 顯示「請先登入以查詢可用性」（原本是「查詢可用性失敗，請稍後再試」） |
| `frontend/e2e/at-room-booking.spec.ts` | `E2E-ROOM-05`／`07`／`09`／`10` 改以已登入買家身分跑（見 §4.4） |
| `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md`（v1.0.7 → v1.0.8） | RTM-US-G1 的驗收條件、§9.13 `pricing-calendar` 的角色（`Guest+` → `Buyer+`；該端點後端**未實作**）、頁首修訂註記 |
| `docs/01_requirements/E-Commerce_FRD_v1.0.md`（v1.17 → v1.18） | `/api/v2/bookings/calendar` 角色 `Guest+` → `Buyer+`、修訂記錄 |

**設計理由與一次改寫**：專案採 React 19 嚴格 hooks 規則，檔內註解明講不可在 effect 內同步 `setState`。**第一版**我用惰性 `useState(() => !AuthService.isAuthenticated())`，安全性靠「`MonthCalendar` 只在 `ListingDetail` 於瀏覽器端載完房源（`state === "ok"`）後才掛載，不會被伺服器渲染」——這是一個**脆弱的前提**，而且我當時沒發現專案已經有一套既定做法。gate 失敗後讀 `StorefrontHeader` 才看到：共用 Header 用 `useSyncExternalStore(subscribeAuth, …, getAuthServerSnapshot)`（`services/authStore.ts`，註解明講目的就是避開 effect 內同步 `setState` 與 hydration 不一致，而且登入／登出時由 `notifyAuthChange` 即時通知）。**改為比照這套慣例**（Rule 11：配合既有慣例），好處：不依賴父層怎麼渲染、登入／登出會即時反映。因為被測的實作改了，突變驗證在新實作上**重跑**（§4.3 的結果是新實作上的）。

### 4.3 驗證

新增真實後端 E2E `at-guest-room-calendar-real.spec.ts`：`E2E-GUESTCAL-01`（訪客看到提示、切換月份**不得**發出日曆請求、沒有錯誤訊息）、`E2E-GUESTCAL-02`（訪客按「查詢可用性」看到「請先登入以查詢可用性」）、`E2E-GUESTCAL-03`（**對照**：已登入買家仍收到 200 的日曆回應、沒有登入提示、沒有錯誤訊息）。

| 驗證 | 結果 |
|---|---|
| 修復前（對未修的建置，**逐一單跑**——`serial` 模式下第一個失敗會讓後面「沒跑」） | 01 **紅**（找不到提示）、02 **紅**（找不到「請先登入」）、03（對照）**綠**（也證明 `BUYER` 有 `booking:read`） |
| 修復後（第一版實作〔惰性 `useState`〕，連同 DEF-403 的規格） | 7／7 綠 |
| 第一版實作的突變 **Ma**：`guest` 恆為 true（登入者也被當訪客） | 03 **紅**（沒有收到日曆回應，`waitForResponse` 逾時），01／02 綠 |
| 第一版實作的突變 **Mb**（第一次）：拿掉 effect 的早退 | 01 紅，但紅在**更前面**的「不得出現『日曆載入失敗』」斷言——請求照發、照 401、照顯示錯誤，所以最後那條「不得發出日曆請求」斷言**沒有被獨立驗證到** |
| 調整 01 的斷言順序（先驗「原因」＝請求，再驗「症狀」＝錯誤訊息）後，突變 **Mb** 重跑 | 01 **紅在 `訪客不應呼叫需要登入的日曆端點`**（`Received` 非空），02／03 綠 |
| **實作改為 `authStore` 慣例後**（§4.2），在新實作上**重跑**突變 **Ma**（`const guest = true`） | 03 **紅**（`waitForResponse` 逾時），01／02 綠 |
| 新實作上重跑突變 **Mb**（拿掉早退） | 01 **紅在 `訪客不應呼叫需要登入的日曆端點`**（`Received` 非空），02／03 綠 |
| 新實作上兩個突變都還原逐位元組一致（sha256），最終狀態重建後 | 3／3 綠（突變前、同一份原始碼的新實作建置上另跑過：`at-room-booking` 12／12、訪客日曆 3／3、legal 4／4＝19／19） |

### 4.4 受影響的既有測試（gate 抓到，我第一版漏看）

`at-room-booking.spec.ts` 以**訪客身分＋`page.route` mock** 跑，全檔有 9 處 mock 日曆端點。其中 `E2E-ROOM-05`／`07`／`09`／`10` 斷言**日曆格的內容**（已訂日禁選、折扣價與原價刪除線、漲價價、未開放日）；訪客不再呼叫日曆端點後，mock 不會被用到，這四個案例在 gate 失敗（`toBeDisabled`、`toContainText`）。其餘案例（`01`／`04`／`06`／`08`／`11` 只斷言可訂性結果，`02`／`03`／`12` 是 checkout 流程）不受影響。

**修法**：這四個案例改以已登入買家的身分跑——helper `asLoggedInBuyer` 在載入頁面前放 `accessToken`。**只放 `accessToken`** 是刻意的：共用 Header 只有在同時有 `user` 資料時才會取購物車數量，所以不會多發沒被 mock 的請求而被 401 踢回登入頁。修後 `at-room-booking` 12／12 通過。

**我的失誤**：第一版計畫書寫「不受影響」，依據是 `grep` 的輸出被我用 `head -14` 截斷——只看到前兩個日曆 mock（`01`、`04`）就下了結論；全檔實際有 9 處。這是「全稱說法要用不截斷的輸出」的又一例；gate 比我先抓到。

**為什麼這些案例原本沒抓到 `DEF-402`**：訪客身分＋mock 讓訪客路徑永遠成功——它們斷言的其實是「已登入買家看到的日曆」，卻假裝是訪客（「測試固件繞過同一段邏輯」）。現在它們的身分與斷言對齊了。

## 5. 驗證總表

| 檢查 | 結果 |
|---|---|
| `npm run lint`（帶新旗標）、`npx tsc --noEmit`（改動後） | 皆退出碼 0，lint 無任何輸出 |
| `npm run build` | 退出碼 0，70／70 頁（含 `/privacy`、`/terms`） |
| 新規格（`at-legal-pages`＋`at-guest-room-calendar-real`） | 7／7 綠；修復前與突變的紅燈見 §3、§4 |
| 文件守門：`ApiRouteDocDriftTest` 2／2、`DocMarkdownFenceTest` 9／9、`TechStackDocDriftTest` 2／2；`./scripts/validate-schema-doc.sh`（PRD 有改，pre-commit 會觸發它） | 全過 |
| `make validate-e2e`（完整：重建 JAR 與前端、乾淨 DB、全部 Playwright 規格） | **第二次完整執行：188 案例，184 通過、4 跳過、0 失敗、0 不穩定（6.1 分鐘）**，閘門顯示「本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）」；新增的 `E2E-GUESTCAL-01`～`03`（第 56／58／59 個）與 `E2E-LEGAL-01`／`02`（兩個路徑，第 66～69 個）都在其中且通過；整套最前面 6 個案例這次全過。**第一次執行有 8 個失敗**（176 通過、4 跳過、10.1 分鐘），見 §6 第 3、5 點：4 個是 DEF-402 的直接後果（已修），4 個是整套最前面 6 個案例中的註冊逾時（**重跑未重現，成因未查明**） |
| pre-push 守門與雲端 CI | 見 §9：pre-push 輕量守門通過；雲端 run 39806469533 三個 job 皆 success（`headSha` 一致），雲端 ESLint 步驟第一次執行 `eslint --max-warnings 0` 且沒有 problems |

## 6. 過程中的失誤與修正

1. **第一次的突變 Mb 揭露我寫的斷言順序讓請求斷言沒有牙齒**（§4.3）：突變驗證抓到，調整順序後重跑。沒有做這一步，「不得發出日曆請求」會是一條看起來有、實際從未被執行到的斷言。
2. **`stack.sh down` 之後有一個程序還佔著 3000／8080**（程序還在收尾），gate 會因此中止；多清一次並確認埠淨空後才跑 gate（這個狀況發生了兩次）。
3. **第一版漏看了 4 個既有案例**（§4.4）：我用 `head -14` 截斷 `grep` 輸出，9 處日曆 mock 只看到前 2 處，就在計畫書寫「不受影響」；gate 第一次執行抓到 `E2E-ROOM-05`／`07`／`09`／`10` 失敗。
4. **第一版 `MonthCalendar` 沒有沿用專案既有慣例**（§4.2）：另起爐灶用惰性 `useState` 讀 `localStorage`，安全性還要靠「父層只在瀏覽器端掛載」這個脆弱前提；gate 失敗後讀 `StorefrontHeader` 才發現 `authStore` 的外部 store 慣例，改寫並在新實作上重跑突變。
5. **gate 第一次執行的另外 4 個失敗：既無法證明與我的改動無關，也不能斷言是環境**：`at-account-data-rights` 兩個、`at-account-security` 兩個，全部是整套最前面 6 個案例（`[1/188]`～`[6/188]`，後端與 Next 剛啟動的冷啟動階段）中需要註冊使用者的那幾個，訊息為 `submitRegister: 註冊失敗，仍停在 /register（status=undefined, rateLimited=false）`＝6 秒內沒觀察到註冊回應；第 7～188 個案例除我造成的 4 個之外全部通過。對乾淨堆疊單獨重跑這兩支規格 7／7 通過，第二次整套重跑前 6 個案例全過，**沒有重現**。那次整套耗時 10.1 分鐘、第二次 6.1 分鐘，與機器當時負載較高一致，但**我沒有查明成因**（沒有那次的後端日誌可看，gate 收尾會刪掉它）；「冷啟動競態」是推論。記憶裡 S107 的教訓是「重跑就綠不等於沒有缺陷」——這裡只能如實標示為「未重現、成因未查明」，若日後再現要先留下後端日誌。

## 7. 限制（誠實揭露）

1. **訪客的日曆格仍顯示底價且全部可點**（我只換掉了錯誤訊息，沒有隱藏格子）：訪客可能點到實際已被訂的日期，要到登入後查詢可用性才知道；UI 以提示文字說明。
2. **登出後已載入的日曆資料不會被清掉**：登入／登出由 `notifyAuthChange` 即時通知，登出後 `guest` 變 true、提示出現、不再發新請求，但畫面上已載入的狀態與價格仍留著（沒有實測這個行為，來自讀碼）。
3. **`localStorage` 有 `accessToken` 但已過期的使用者被視為已登入**，仍會呼叫端點並可能得到 401（既有行為，由 axios 攔截器處理刷新）——這條路徑沒有實測。
4. `--max-warnings 0` 對 hook 只涵蓋 **staged 的檔案**，對 CI 涵蓋全專案；未 staged 檔案的既有 warning 在本機 commit 時不會被擋（現況為 0，雲端 CI 的 `npm run lint` 會擋）。
5. `/privacy`、`/terms` 仍是佔位頁，正式內容待使用者提供。
6. 本輪沒有新增 ESLint 規則防止空字串 `SelectItem`（`DEF-401` 的子問題，`DEF-400` 的決定沒有涵蓋它），仍待使用者裁決。

## 8. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | `pre-commit-check` 腳本也帶 `--max-warnings 0` | 它是 hook 的手動版，不帶旗標會讓同一組守門不一致 | 還原那一行 |
| 2 | 「查詢可用性」遇 401 也改成「請先登入以查詢可用性」，**只認 401**（不認 403） | 同一件事的第二條路徑（訪客同樣被說成「失敗」）；403 是已登入但沒權限，說「請先登入」會誤導 | 還原 `catch` 那一段 |
| 3 | 訪客的日曆格維持顯示底價且可點，只換掉錯誤訊息；提示文字是我寫的措辭 | 使用者的決定是「看到提示而不是錯誤訊息」；隱藏格子是更大的 UI 變更 | 改提示文字，或要求隱藏格子 |
| 4 | PRD §9.13 `pricing-calendar`（後端未實作）與 FRD `bookings/calendar` 的角色欄一併改為 `Buyer+`；歷史階段文件 `Stage5_UserStory_Confirmation.md` 與 PRD v0.9_R02 歷史副本不改 | 否則 PRD／FRD 與修訂後的 US-G1 互相矛盾；歷史文件是階段紀錄 | 還原那兩格 |
| 5 | 佔位頁放 `(storefront)`、只寫「內容即將公告。」 | 沿用頁首頁尾、不生成任何法律文字 | 改文字或位置 |
| 6 | 新增兩支永久 E2E（共 7 個案例；單獨跑 7 個案例 21.9 秒） | 這幾個行為沒有任何現有測試守住 | 刪檔 |
| 7 | `MonthCalendar` 比照共用 Header 用 `authStore` 的外部 store，並在 `authStore.ts` 新增兩個 boolean 快照函式 | 配合既有慣例（Rule 11）；不依賴父層怎麼渲染；登入／登出即時反映 | 還原成惰性 `useState`（但安全性要靠「只在瀏覽器端掛載」這個前提） |
| 8 | `at-room-booking` 的 `E2E-ROOM-05`／`07`／`09`／`10` 改以已登入買家身分跑（`asLoggedInBuyer`，只放 `accessToken`） | 日曆本來只給登入者，原本「訪客＋mock」的前提與實際行為不符（也是 DEF-402 沒被發現的原因） | 若要保留訪客前提，就得讓訪客能拿到日曆資料——即 DEF-402 的 (a)，與你的決定相反 |

## 9. 雲端 CI 與狀態

- commit `a57cd1a2`，於 2026-10-22 push（`100cc5ab..a57cd1a2 main -> main`）。以推送日誌，以及 `git fetch` 後本機 HEAD 與 `origin/main` 同為 `a57cd1a2`、ahead／behind 為 0／0 核實；沒有採信背景任務通知的退出碼。
- pre-commit：ESLint（**帶新的 `--max-warnings 0`**）、`tsc --noEmit`、PRD 觸發的 schema 文件守門、secret 掃描全部一次通過；staged 不含 `backend/`，所以不需要 test DB。
- pre-push 輕量守門通過：本機 act 的 Backend Unit Tests、Frontend Lint & Build（ESLint、Type Check、Build 各步驟）皆 `Job succeeded`，schema 漂移守門通過。
- 雲端 CI run [39806469533](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39806469533)：`completed／success`，三個 job（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build）皆 success。`gh run watch` 結束後另以 `gh run view --json status,conclusion,jobs,headSha` 交叉確認，`headSha` 與本機 commit 一致。下載完整雲端日誌（214425 行）確認：`Frontend Lint & Build` 的 ESLint 步驟執行的是 `eslint --max-warnings 0`（**這個新旗標第一次在雲端執行**），沒有 `problems (` 彙總行。
- 雲端三個 job 不含 Playwright E2E；E2E 以本機 `make validate-e2e` 為準（§5）。

## 10. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `frontend/package.json` | `lint`、`pre-commit-check` 帶 `--max-warnings 0`（`DEF-400`） |
| `frontend/.husky/pre-commit` | `eslint --fix` 帶 `--max-warnings 0`（`DEF-400`） |
| `frontend/src/components/storefront/MonthCalendar.tsx`、`ListingDetail.tsx` | 訪客不呼叫日曆端點並顯示登入提示、可訂性查詢 401 訊息（`DEF-402`） |
| `frontend/src/services/authStore.ts` | 新增 `getAuthenticatedSnapshot`／`getAuthenticatedServerSnapshot`（`DEF-402`） |
| `frontend/e2e/at-room-booking.spec.ts` | 4 個日曆內容案例改以已登入買家身分跑、檔頭說明（`DEF-402`） |
| `frontend/src/app/(storefront)/privacy/page.tsx`、`terms/page.tsx` | 新增佔位頁（`DEF-403`） |
| `frontend/e2e/at-guest-room-calendar-real.spec.ts`、`at-legal-pages.spec.ts` | 新增 |
| `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md`、`E-Commerce_FRD_v1.0.md` | PRD v1.0.8、FRD v1.18（`DEF-402`） |
| `docs/06_quality/FRONTEND_PRECOMMIT_GUIDE.md` | hook 步驟表同步（`DEF-400`） |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | v3.71 → v3.72：`DEF-400`／`402`／`403` 結案（保留登記時原文）、`DEF-401` 的子問題措辭更新 |
| `docs/04_planning/RELEASE_TRACKER.md` | v4.19 → v4.20：Sprint 299 row |
| `docs/04_planning/SPRINT_299_PLAN.md` | 新增（本文件） |

後端零變更。
