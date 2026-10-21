# Sprint 293 Plan — 依賴安全審計修補：`follow-redirects` 1.16.0 → 1.16.1（2 則 high 公告）

## 1. 起點與範圍

### 1.1 起點

[Sprint 292](SPRINT_292_PLAN.md) 做文件一致性檢查時，順手重跑了前端正式相依的安全審計（`npm audit --omit=dev --audit-level=high`，與每週一排程 `dependency-audit.yml` 同一條指令），結果從上次（2026-10-20，Sprint 287 升級 `next` 後，run 39439129009）的綠燈變成 **1 個 high 等級漏洞**。這是依賴問題不是文件問題，所以不混進 Sprint 292 的文件 commit，另開本 Sprint。

對使用者指示的解讀（**推論，不是使用者說的**）：使用者說「請依序多執行幾個項目」，這一項是 AI 能獨立處理、不涉產品決策的修補；先例是 Sprint 287（排程審計失敗後升級 `next`）。排程的設計（`DEF-314`）是「只通知、不擋 push」，下一次排程在 2026-10-26（週一）01:00 UTC，沒有人處理的話那時會以 failure 通知使用者；先修可以避免這次通知。

### 1.2 成功標準（開工時定義）

1. `npm audit --omit=dev --audit-level=high` 回報 0 個漏洞。
2. 變更範圍僅限 `frontend/package-lock.json` 中 `follow-redirects` 一個條目，`package.json` 與其他套件版本不變。
3. 升級後的 `follow-redirects` 與 `axios` 實際可用：轉址仍會被跟隨，跨主機轉址仍剝除憑證標頭。
4. `lint`（0 errors，warning 數與 Sprint 291 的 99 相同）、`type-check`、`build` 通過。
5. 誠實揭露：沒有驗證的項目與公告觸發條件是否適用本專案。

## 2. 事實

| 項目 | 內容 |
|---|---|
| 依賴鏈 | `frontend`（直接依賴 `axios ^1.20.0`，裝的是 1.20.0）→ `follow-redirects`（`axios@1.20.0` 要求 `^1.16.0`，原鎖定 1.16.0） |
| 公告 1 | [GHSA-q3jh-wrc6-jgq8](https://github.com/advisories/GHSA-q3jh-wrc6-jgq8)：array-form headers 可繞過跨來源憑證剝除（`removeMatchingHeaders` 用 `for...in` 迭代，遇到 Node 也接受的「名稱與值交錯陣列」形式的 headers 時只會得到數字索引，不會比對到任何標頭名）。影響 `>= 1.16.0, < 1.16.1`，修補 1.16.1 |
| 公告 2 | [GHSA-8r9p-f939-6h3c](https://github.com/advisories/GHSA-8r9p-f939-6h3c)：`sensitiveHeaders` 選項（1.16.0 新增）的 regex 跳脫不完整，宣告的標頭名含 `^`、`\|`、`[`、`{`、`}` 時保護失效；**內建的 Authorization／Proxy-Authorization／Cookie 不受影響**。影響與修補版本同上 |
| 公布時間 | 兩則皆 2026-10-20 17:53～17:58 UTC，晚於當天 14:25 UTC 的綠燈審計 run（3.5 小時），所以不是 Sprint 287 漏掉 |
| 修補版 | 1.16.1 已發布（`npm view follow-redirects versions` 最後一個）；lock 內 integrity 與 registry 的 `dist.integrity` 逐字相同 |

## 3. 暴露面評估（我沒有重現漏洞）

- **公告 1** 要求應用程式以 array-form headers 發請求。`axios` 的 Node http adapter 全程把 headers 當**物件**操作（`Object.keys(options.headers)`、`options.headers['Proxy-Authorization'] = …`、`options.headers.host = …`，`node_modules/axios/lib/adapters/http.js`），所以走 axios 的請求不會踩到。
- **公告 2** 要求使用 `sensitiveHeaders` 選項且標頭名含特殊字元。專案程式碼沒有任何 `sensitiveHeaders`（`grep` 為空）；axios 自己設的是 `options.sensitiveHeaders = []`；內建三個標頭不受影響。
- 專案的 axios 實例只建立在 `src/lib/axios.ts`；`src/services/*.ts` 多數沒有 `'use client'`，所以伺服器端（RSC）是否會呼叫、走到 Node 的 http adapter，我**沒有逐一查證**。
- 結論：實際可被利用的面很小，升級的主要理由是「審計排程以 high 判失敗」與衛生，不是「已被攻擊」。

## 4. 做法與決定

- `npm update follow-redirects`：只更新這一個傳遞依賴。`package.json` 不動（`axios` 的範圍 `^1.16.0` 已涵蓋 1.16.1，不需要新增直接依賴）。
- 不用 `npm audit fix`：它可能順手動其他套件；本次只有這一項漏洞，不需要放大範圍。

## 5. 驗證

| 檢查 | 結果 |
|---|---|
| lock 檔差異 | 只有 `frontend/package-lock.json`，`3 insertions(+), 1 deletion(-)`：`follow-redirects` 的 `version` 1.16.0→1.16.1，並補上 `resolved`、`integrity`（原條目沒有這兩個欄位） |
| integrity | lock 內 `sha512-FNvFGzoMLWmE6Yj9spb/zjd7yiNCHiAW9/Tg9CXrQ8wuu32HtlJOwWO11OJafl5FfY3DxTdQ0vj42zU1kvv5jg==` 與 `npm view follow-redirects@1.16.1 dist.integrity` 相同 |
| `npm audit --omit=dev --audit-level=high` | 修復前 1 個 high；修復後 `found 0 vulnerabilities` |
| `npm ls follow-redirects` | 單一副本 1.16.1（經 `axios@1.20.0`） |
| 煙霧測試（本機臨時 server，腳本在 session 暫存目錄，未進 repo） | 5／5 通過：同源轉址跟隨到 `/final` 且 Authorization／Cookie 保留；跨主機轉址（127.0.0.1→localhost）跟隨到 `/landed` 且 Authorization、Cookie 皆已剝除 |
| `npm run lint` | `✖ 99 problems (0 errors, 99 warnings)`，與 Sprint 291 的 99 相同（`DEF-398` 範圍，未動） |
| `npm run type-check` | 通過 |
| `npm run build` | 通過（`.next/BUILD_ID` 時間戳為執行當下；產物含 `/dashboard/reviews`）；`git status` 只有 `package-lock.json` 一個變更 |
| 雲端 CI | 見 §8（push 後回填） |

## 6. 限制（誠實揭露）

1. **煙霧測試的鑑別力有限**：axios 傳物件形式的 headers，不會觸發公告 1 的條件，所以這 5 項在 1.16.0 上很可能也會通過——它證明「升級後轉址與剝除行為正常」，**不證明漏洞被修好**（那是上游的修補，我信任 1.16.1 的版本範圍與公告，沒有自己重現漏洞）。
2. **沒有跑 `make validate-e2e`**：前端沒有單元測試（`npm test` 只印「No unit tests configured」），雲端 CI 也沒有 E2E job；本機 E2E 才會走到伺服器端 axios（若有）。我判斷一個 patch 版的傳遞依賴升級不值得 20～30 分鐘的全棧閘門，但這是**取捨，不是驗證過**。
3. **下一次排程審計（2026-10-26 週一 01:00 UTC）仍可能出現新公告**，這次修的只是當下這一個。
4. **只涵蓋前端 npm**：`DEF-314` 的排程審計只看前端正式相依，後端 Maven 相依不在其中（既有範圍，本輪沒有擴張）。

## 7. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | 不等排程通知，開工當下就修 | 修補版已發布、改動範圍極小（lock 檔 3 行）、有 Sprint 287 先例 | `git revert` 這個 commit，下次排程仍會通知 |
| 2 | 只更新 lock 檔，不新增直接依賴、不用 `npm audit fix` | 範圍最小 | — |
| 3 | 不跑 `make validate-e2e` | 見 §6.2 | 想補驗證：`make validate-e2e`（約 20～30 分鐘） |

## 8. 雲端 CI 與狀態

push 後回填。

## 9. 需要使用者決定（沿用 Sprint 292 §8，無新增）

`DEF-397`（PRD §16.4.1 charset，建議修訂 PRD 措辭）、`DEF-398`（前端 99 筆 ESLint warning，建議先做行為不變的 58 筆）、`DEF-399`（文件結構性漂移守門，建議做 (a)＋(b)）——回「依照建議」即照做。

## 10. 文件異動清單

| 文件 | 異動 |
|---|---|
| `frontend/package-lock.json` | `follow-redirects` 1.16.0→1.16.1 |
| `docs/04_planning/RELEASE_TRACKER.md` | v4.10→**v4.11**：新增 Sprint 293 row；回填 Sprint 292 的 push 狀態與雲端 CI 結果 |
| `docs/04_planning/SPRINT_293_PLAN.md` | 新增（本文件） |

`DEFERRED_ITEMS_TRACKER.md` 本輪不更新：沒有動到任何追蹤項目（比照 Sprint 290 的先例，不事後補寫沒有內容的版本條目）。
