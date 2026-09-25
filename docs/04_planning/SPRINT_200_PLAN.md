# Sprint 200 Plan — HSTS 收斂（拿掉 `includeSubDomains`）、前後端 HTTPS 判斷對齊、Sprint 199 push

**Sprint**: Sprint 200
**日期**: 2026-09-26

## 1. 起點與決策紀錄

使用者對 Sprint 199 收尾的「需要你決定」三項回覆如下（原文）：

| 項目 | 使用者回覆 |
|---|---|
| 要不要 push | 「請依照慣例commit + Push to main」 |
| HSTS `includeSubDomains`（我建議：不確定就先縮短 `max-age` 或拿掉它） | 「請完成https」 |
| 整體 | 「以上若需要我的決定，請先以最佳理想化進行處理，若不行在問我！」 |

**我的理解（推論，使用者未逐字確認）**：
- push：這次明確授權推 `main`（Sprint 199 的錯誤處理修正也在其中）。
- 「請完成https」：使用者沒有給出子網域清單，也沒有說「都能走 https」，所以我**無法把它當成「已確認子網域皆 https」**。我把它理解為「把 HTTPS 這塊在程式內做到收尾」，遇到無法驗證的部分取保守值。

## 2. 決策：HSTS 拿掉 `includeSubDomains`，保留 `max-age=31536000`、不加 `preload`

| 選項 | 判斷 |
|---|---|
| 保留 `includeSubDomains` | 需要「所有子網域都能走 https」為真，我無從驗證；若為假，瀏覽器一年內直接擋掉那些子網域，且**送出後無法收回** |
| **拿掉 `includeSubDomains`（採用）** | 只約束送出標頭的那個網域本身——這正是本服務的實際保護目標。日後確認子網域皆 https 再加回是**一行改動**；反方向（收回）做不到。不對稱的代價決定了保守值 |
| 縮短 `max-age` | 本輪不做：repo 沒有環境參數化的機制，為此新增設定面超出需求；見 §5 揭露 |

`preload` 維持不加（需向瀏覽器廠商登記，幾乎無法反悔）。

## 3. 前後端 HTTPS 判斷對齊（Sprint 199 §2 指出「大寫未實測」，本輪實測）

後端 `SecurityConfig.isHttpsRequest`：`X-Forwarded-Proto` 取第一段、去空白、不分大小寫等於 `https`。前端原本用 `https.*`，**實測前端對大寫 `HTTPS` 不送 HSTS**（E2E-SEC-HDR-09 紅燈：`Received: undefined`）——同一請求前後端會做出不同的 HSTS 決定。

修法：`next.config.ts` 的 `has` 值改為 `\s*[Hh][Tt][Tt][Pp][Ss]\s*(,.*)?`。原因是 Next 以 `new RegExp(`^${value}$`)` 比對且不帶旗標（讀 `node_modules/next/dist/shared/lib/router/utils/prepare-destination.js` 確認），無法用 `i` 旗標，只能以字元集表示不分大小寫。順帶消除舊式樣的過寬比對（`httpsx` 也會命中）。

| `X-Forwarded-Proto` | 後端 | 前端（修復前） | 前端（修復後） |
|---|---|---|---|
| （無）／`http` | 不送 | 不送 | 不送 |
| `https` | 送 | 送 | 送 |
| `HTTPS` | 送 | **不送（實測）** | 送 |
| `https, http` | 送 | 送 | 送 |
| `http,https` | 不送 | 不送 | 不送 |
| `httpsx` | 不送 | **送** | 不送 |

「修復前」欄只有 `HTTPS` 那格是實測；其餘各格是我依舊式樣 `https.*` 的比對規則推得，**沒有在舊碼上逐一實測**（HDR-09 在第一個斷言就紅了，後面的斷言沒跑到舊碼）。「修復後」欄有 E2E-SEC-HDR-09 與 production curl 實測；後端欄除 `httpsx` 外都有 IT-SEC-HDR-07~10 實測，`httpsx` 是由 `equalsIgnoreCase("https")` 推得、後端沒有對應測試。

## 4. 「完成 https」在 repo 內查證的範圍

- 後端 `src/main` 與前端 `src` **沒有任何 cookie 使用**（無 `Set-Cookie`／`ResponseCookie`／`document.cookie`），所以沒有 `Secure`／`SameSite` 旗標要補；token 存 `localStorage`（Sprint 198 已以嚴格 CSP 回應此風險）。
- 後端 `src/main` 與設定檔沒有寫死的非 localhost `http://` 網址。
- 因此程式內的 HTTPS 工作只有 §2、§3 兩件。

**repo 外、我做不到也無法驗證的部分**：TLS 終止與憑證、邊緣的 HTTP→HTTPS 導向、代理確實送出 `X-Forwarded-Proto`、子網域盤點。這些是維運動作，見 §7。

## 5. 測試與驗證

**紅燈先行**（每項都先看到「因正確原因失敗」才改實作）：
- 後端：期望值改為不含 `includeSubDomains` 後 11 個中 **5 紅**，訊息為 `expected:<max-age=31536000> but was:<max-age=31536000 ; includeSubDomains>`。
- 前端：HDR-09 第一個斷言即紅（`HTTPS` → `undefined`）。HDR-08 同輪出現一次 `ECONNRESET`（Playwright 自起的 dev server 冷啟動，第一次請求撞上編譯），修復後重跑未再出現，我判斷為冷啟動的暫時現象而非缺陷，但**未另外重現**。

**變更**
| 檔案 | 變更 |
|---|---|
| `SecurityConfig` | HSTS 加 `.includeSubDomains(false)`，附理由註解 |
| `SecurityHeadersIntegrationTest` | 9→**11**：HSTS 期望值改為 `max-age=31536000`；新增 HDR-10（大小寫、`https, http`、`http,https`）與 HDR-11（不含 `includeSubDomains`，測試名稱寫明原因） |
| `next.config.ts` | HSTS 值不含 `includeSubDomains`；`has` 條件與後端對齊，附註解 |
| `e2e/at-security-headers.spec.ts` | 8→**9**：HDR-08 期望值同步；新增 HDR-09（邊界寫法與後端一致，含 `httpsx`） |

**已跑**：後端 `SecurityHeadersIntegrationTest` **11/11 綠**（真實 postgres／redis）；前端 `at-security-headers` **9/9 綠**；`tsc --noEmit`、eslint 乾淨；`npm run build` 成功；**production `next start` + curl** 7 種 `X-Forwarded-Proto` 寫法的結果與 §3 表格「修復後」欄逐項相同（與 dev 走不同的 routes-manifest 路徑，故另行驗證）。

**全量回歸**：由 push 前守門 `make validate-release`（act 跑 backend verify＋frontend、schema、`validate-e2e`）涵蓋，結果見 `RELEASE_TRACKER` Sprint 200 列的狀態欄。

## 6. Push

Sprint 199（`72a7f93`）與本輪 commit 一併 push `main`。依 CLAUDE.md，不使用 `--no-verify`，走 pre-push 守門。

## 7. 揭露與下一步

- **已 push 的 Sprint 198（`a305ae5`）帶有 `includeSubDomains`**。**若該版本已部署、且瀏覽器已在 https 下收過這個標頭，瀏覽器會記住一年——本輪的程式碼改動不會撤回它**；要撤回必須讓該網域在 https 回應中送 `max-age=0`。我沒有部署資訊；Sprint 199 總結寫「上 staging 前」，我**推測尚未部署**，但這是推測。
- `max-age` 仍是一年（Spring 預設、OWASP 建議值）。若上線初期擔心憑證出問題，可先以較短值逐步拉長；本輪未做（見 §2）。
- 未在 Safari／Firefox 驗證 CSP 與 HSTS 行為（只有 Playwright chromium）。
- **維運確認**（沿用 Sprint 198／199）：前方代理有送 `X-Forwarded-Proto`；staging 首次上線看瀏覽器主控台有無 CSP 違規（有問題可先設 `CSP_REPORT_ONLY=1`）。
- 若日後確認所有子網域皆走 https，要加回 `includeSubDomains`：改 `SecurityConfig` 一行、`next.config.ts` 一行，並同步 `IT-SEC-HDR-11` 與 E2E-SEC-HDR-08／09 的期望值。
- DEF-281（防火牆拒絕的請求回 401、內容缺 `requestId`）維持待處理，本輪未動。
