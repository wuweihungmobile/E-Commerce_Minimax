# Sprint 149 Plan — DEF-185：登出從未失效 Refresh Token

**Sprint**: Sprint 149
**日期**: 2026-09-09

---

## 1. 起點

Sprint 148 完成後 `DEFERRED_ITEMS_TRACKER.md` 唯二列出的「待排程」項目為：DEF-103/104/105（Sprint 135 已拍板「不排入排程」的低優先級輸入驗證，非真正待辦）與 `/dashboard/tenants/[id]/features` 補配額用量顯示（新功能，需先確認是否要做，非技術債）。兩者皆非「已拍板、待執行」的具體任務。

使用者拍板本輪方向：**延續 Sprint 130~148 建立的自選掃描慣例**，由 Agent 自選一個尚未掃過的角度做全面稽核。

比對 `DEFERRED_ITEMS_TRACKER.md` 歷史已涵蓋的契約漂移掃描角度（訂單/付款/退貨/評價、CMS/Blog/媒體/通知、客服工單/知識庫/FAQ、租戶/訂房/購物車/儀表板、ERP 採購/供應商），**Auth/OAuth 模組從未被掃過**，選定為本輪角度：比對前端 `services/auth.ts` 與後端 `AuthController`/`OAuthController` 的呼叫契約。

---

## 2. 缺口盤點

### 2.1 掃描方法

逐一比對 `frontend/src/services/auth.ts` 定義的方法與 `AuthController`（`/v2/auth/*`）、`OAuthController`（`/v2/auth/oauth/*`）實際提供的端點：

| 後端端點 | 前端是否有呼叫點 |
|---|---|
| `POST /v2/auth/register`／`login`／`refresh` | ✅ 有（`auth.ts`） |
| `POST /v2/auth/logout` | ❌ **全域零呼叫點** |
| `GET /v2/auth/me` | ❌ 零呼叫點（前端改讀 `localStorage` 快取的使用者資訊，非缺陷——快取資料本就是登入/註冊回應寫入，語意一致） |
| `GET /v2/auth/me/data-export`（會員資料匯出，PRD §1.5.1） | ❌ 零呼叫點（無前端 UI 觸發，功能已存在但無入口） |
| `DELETE /v2/auth/me`（自助帳戶刪除） | ❌ 零呼叫點（同上） |
| `POST /v2/auth/oauth/login`／`link` | ❌ 零呼叫點（OAuth 流程本身是既有未完成 stub，非新問題，見 Sprint 78 記錄） |

`grep` 全庫 `clearAuthData` 呼叫點，確認共 **21 處**：`StorefrontHeader.tsx`（共用 Header 帳號選單登出）+ 20 個 Dashboard 頁面頭部的「登出」按鈕（`dashboard/page.tsx` 主頁 + 19 個子頁面，含 ERP/商品/房型/租戶/定價模組）。逐一查看上下文，**全部是明確的使用者主動點擊「登出」按鈕**，非 401 攔截後的被動登出（`lib/axios.ts` 的 response interceptor 對 refresh 失敗時走的是獨立的 `localStorage.removeItem` 路徑，不經過 `AuthService`，本輪不在範圍內——該路徑用的 refresh token 本身已確認失效，呼叫後端 logout 無實益）。

### 2.2 判定為真實缺陷：DEF-185

`AuthController.logout` 依 `LogoutRequest.refreshToken` 是否帶入而選擇性失效單一 token 或該使用者全部 token（`RefreshTokenService`，Redis-backed，刪除對應 key 即失效），功能完整且早有測試覆蓋。但前端 `AuthService` 從未定義過任何呼叫此端點的方法，21 個登出按鈕全數只呼叫 `AuthService.clearAuthData()`（純 `localStorage.removeItem`）。

`application.yml` 確認 `refresh-token-expiration: 604800000`（7 天），且 refresh token 存於 `localStorage`。後果：使用者點擊「登出」後，若此 token 先前已被竊取（如共用/公用電腦、或未來若真的出現可利用的 XSS sink），攻擊者仍可持該 token 在 **最長 7 天內** 換發新的 access token，「登出」對伺服器端完全沒有作用，僅是前端幻覺。

判定與過往已修復的其他 session/access-control 類缺陷（如 DEF-023/024 IDOR）同等級的真實安全風險，非低優先級 defense-in-depth，本輪同輪修復。

### 2.3 排除的偽陽性

- `GET /v2/auth/me` 零呼叫點：非缺陷。前端登入/註冊回應本就含完整 `user` 物件並快取於 `localStorage`，`getCurrentUser()` 讀快取語意正確，無需額外打 API。
- `data-export`／`DELETE /me`／OAuth 三項零呼叫點：功能性缺口而非本輪掃描角度（會話安全）鎖定的問題，且分別是「已完成後端、缺前端 UI 入口」與「既有未完成 stub」性質，登記於下方§6範圍外，不在本輪修復。

---

## 3. 使用者決策

本輪掃描角度（自選掃描慣例）已由 §1 的 `AskUserQuestion` 拍板；缺陷本身（DEF-185）依既有「安全類缺陷、修法有清楚前例可循，可直接授權修復不需每次詢問」的授權範圍（見 memory `e-commerce-multi-sprint-test-loop`），未另外詢問，比照過往安全修復（DEF-023/024 等）直接修復。

---

## 4. 實作內容

修法純前端，**後端零程式碼變動**（既有端點功能完整且已有測試覆蓋，只是前端從未使用）：

1. `frontend/src/lib/api.ts`：`API_ENDPOINTS.auth` 新增 `logout: '/v2/auth/logout'`。
2. `frontend/src/services/auth.ts`：新增 `async logout(): Promise<void>` ——讀取 `localStorage` 現有 `refreshToken`，`POST` 至新端點失效該 token（best-effort，`try/catch` 吞掉錯誤，網路異常或 token 已過期都不應阻擋使用者登出），最後呼叫既有 `clearAuthData()` 清本機資料。
3. 21 處呼叫點（`StorefrontHeader.tsx` + 20 個 Dashboard 頁面）全數由 `AuthService.clearAuthData()` 改為 `await AuthService.logout()`，對應的 `onClick`/`handleLogout` 改為 `async`。其中 19 個 Dashboard 子頁面的按鈕程式碼完全一致（複製貼上樣板），以腳本批次替換；`dashboard/page.tsx` 主頁與 `StorefrontHeader.tsx`（多帶 `notifyAuthChange()`/`setCartCount(0)`）各自獨立編輯。

不改動：`lib/axios.ts` 的 401 refresh-失敗兜底路徑（該路徑操作的 refresh token 本身已確認失效，呼叫後端 logout 無實益，維持原樣）；後端 `AuthController`/`AuthService`/`RefreshTokenService` 完全不動。

---

## 5. 測試

`frontend/e2e/at-buyer-pages.spec.ts` 的既有 `E2E-BUYER-05`（共用 Header 帳號選單登出 → 轉訪客）新增網路請求斷言：登出按鈕點擊後，必須偵測到一次對 `/v2/auth/logout` 的 `POST` 請求（`page.waitForRequest`，10 秒逾時）。

**紅燈先行且實際執行**：`git stash` 暫存 23 個生產修復檔案（保留這份新增的 E2E 斷言不入 stash），對修復前程式碼建置前端（`npm run build`）+ 啟動全棧，跑 `E2E-BUYER-05`：

```
TimeoutError: page.waitForRequest: Timeout 10000ms exceeded while waiting for event "request"
```

證實缺陷真實存在——修復前的程式碼從未送出這個請求。`git stash pop` 還原修復後重建前端重跑，`5 passed`（含 `E2E-BUYER-05`），轉綠燈。

前端專案無單元測試框架（`package.json` 的 `test` script 為 `echo "No unit tests configured"`），本輪驗證依專案既有慣例以 `tsc --noEmit`/`eslint`/E2E 三層把關，非缺漏。

---

## 6. 驗證結果

- **`npx tsc --noEmit`**：通過，0 error。
- **`npx eslint`**（對 25 個變更檔案）：0 error（1 個 `services/auth.ts` 既有警告 `import/no-anonymous-default-export`，與本輪改動的程式碼行無關，非新增）。
- **`make validate-e2e`**（複製雲端 e2e job：`test-db-down` → 乾淨 PostgreSQL/Redis → 建置 backend JAR（`ddl-auto=validate`）→ Flyway migrate → 建置並啟動 frontend production build → `npx playwright test`）：**57 passed / 4 skipped / 0 failed**，與 Sprint 146/148 既有基準一致，schema 對齊無漂移（backend 以 `ddl-auto=validate` 成功啟動）。
- 後端：零檔案變動，未執行 `mvn verify`（既有端點功能已由其自身測試覆蓋，本輪未觸碰任何後端程式碼）。
- schema：本輪未動任何 entity 欄位或 migration，未執行 `make validate-schema`。

---

## 7. 範圍外（延後）

以下三項為 §2.1 掃描時一併發現、但非本輪「會話安全」角度鎖定範圍的功能性缺口，登記供未來排程參考，非新增 DEF（皆為「已有後端、缺前端入口」或既有已知 stub，性質與缺陷不同）：

- **`GET /v2/auth/me/data-export`（PRD §1.5.1 會員資料匯出）零前端呼叫點**：後端功能完整（Sprint 94 AI-2428），但目前沒有任何頁面提供「匯出我的資料」按鈕觸發它。
- **`DELETE /v2/auth/me`（自助帳戶刪除／被遺忘權）零前端呼叫點**：同上，後端完整（Sprint 94 AI-2428），無前端入口。
- **OAuth 登入/連結（`POST /v2/auth/oauth/login`／`link`）零前端呼叫點**：既有未完成 stub（見 Sprint 78 記錄），非本輪新發現，維持現狀。

---

## 8. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) v2.0 §3.2，本節為必要章節。

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | DEF-185 修復（登出補呼叫後端失效 refresh token） | Sprint 149 自選掃描發現 | ✅ 完成 | 移入已完成延後項目 |
| 2 | `RELEASE_TRACKER` 回填本輪 push 狀態與雲端 CI 結果 | 本輪交付後 | ⬜ 待執行 | push 後同日回填 |
| 3 | DEF-103/104/105 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已拍板不排入排程 |
| 4 | `/dashboard/tenants/[id]/features` 顯示頁補上配額用量 | S147 §6 範圍外 | ⬜ 待排程 | 需先確認是否要做（新功能，非技術債） |
| 5 | 會員資料匯出／自助刪除帳戶補前端入口 | S149 §7 範圍外 | ⬜ 待排程 | 後端已完整，需先確認 UI 位置與是否要做 |
| 6 | OAuth 登入/連結串接 | 既有 stub（S78 記錄） | ⬜ 待排程 | 需先確認是否要做（範圍較大：前端 OAuth flow + provider 設定） |

---

## 9. 誠實揭露總結

- 本輪掃描角度（Auth/OAuth）由 Agent 自選，非使用者指定；選定依據是「尚未被涵蓋」的排除法，非隨機挑選——已列出既有涵蓋角度供比對。
- DEF-185 的修法刻意不動 `lib/axios.ts` 的 401 refresh-失敗路徑：該路徑操作的 refresh token 本身已經失效（refresh 呼叫本身失敗才會走到這裡），呼叫後端 logout 對一個已失效的 token 沒有實益，維持原樣而非機械式全面替換。
- 本輪零後端程式碼變動，`mvn verify`/`make validate-schema` 皆未執行——如實記錄為「本輪範圍不需要」而非略過不提；`make validate-e2e` 本身已包含後端以 `ddl-auto=validate` 啟動的 schema 對齊驗證，足以覆蓋本輪需求。
- §7 三項範圍外項目是掃描時的副產品，如實記錄而非因為「順手就能做」而擅自擴大本輪範圍。
