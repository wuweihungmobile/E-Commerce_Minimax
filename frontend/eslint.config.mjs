import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import designTokens from "./eslint-rules/design-tokens.mjs";

// Radix <Select.Item value=""> 的三種寫法（見下方 no-restricted-syntax 區塊的說明）
const SELECT_ITEM_EMPTY_VALUE_SELECTORS = [
  {
    selector: "JSXOpeningElement[name.name='SelectItem'] > JSXAttribute[name.name='value'][value.value='']",
    message: "<SelectItem value=\"\"> 會讓 Radix 在 render 時 throw（整頁崩潰）。「全部」選項請改用哨兵值，例如 value=\"ALL\"。",
  },
  {
    selector:
      "JSXOpeningElement[name.name='SelectItem'] > JSXAttribute[name.name='value'] > JSXExpressionContainer > Literal[value='']",
    message: "<SelectItem value={''}> 會讓 Radix 在 render 時 throw（整頁崩潰）。「全部」選項請改用哨兵值，例如 value=\"ALL\"。",
  },
  {
    selector:
      "JSXOpeningElement[name.name='SelectItem'] > JSXAttribute[name.name='value'] > JSXExpressionContainer > TemplateLiteral[expressions.length=0][quasis.0.value.cooked='']",
    message: "<SelectItem value={``}> 會讓 Radix 在 render 時 throw（整頁崩潰）。「全部」選項請改用哨兵值，例如 value=\"ALL\"。",
  },
]

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  // 設計稿 token 守門（Sprint 328）：全站 UI 以 Homepage (standalone) _OK.html 設計稿為 BASE。
  // 禁止 Tailwind 預設色盤 class（text-gray-500、bg-blue-600…）、陰影 class（shadow-sm…）與 class 裡寫死的 hex；
  // 顏色一律用 --rs-* token（bg-rs-primary、text-rs-ink…），層次用 border border-rs-hairline。見 eslint-rules/design-tokens.mjs。
  {
    files: ["src/**/*.{ts,tsx}"],
    plugins: { "design-tokens": designTokens },
    rules: {
      "design-tokens/no-raw-palette": "error",
      // Sprint 329：這兩類寫法「不報錯、只是靜靜地沒有效果」或不可測試，任何地方都不允許。
      "design-tokens/no-native-dialog": "error", // alert()／confirm()／prompt() → toast／confirmDialog
      "design-tokens/no-removed-utility": "error", // bg-opacity-50（v4 已移除）、text-warning（沒定義）
    },
  },
  // Sprint 329：視覺決定只能寫在 components/ui。頁面與領域元件不得用原生表單／表格元素，也不得手刻「已經有元件」的配方
  // （頁面標題、錯誤橫幅、載入／空狀態、Modal 遮罩、卡片）。豁免：設計系統本身（ui）、外殼（layout，自己的版面）、
  // 主題切換器（theme，頂欄上無框透明的特殊 select）。見 docs/07_design/UI_DESIGN_SYSTEM_CONVENTIONS.md §9、docs/02_architecture/adr/ADR-001。
  {
    files: ["src/**/*.{ts,tsx}"],
    ignores: ["src/components/ui/**", "src/components/layout/**", "src/components/theme/**"],
    plugins: { "design-tokens": designTokens },
    rules: {
      "design-tokens/no-raw-control": "error",
      "design-tokens/no-ui-recipe": "error",
    },
  },
  // Radix 的 <Select.Item> 在 render 時對空字串 value 直接 throw，整頁崩潰（DEF-401：店主的商品管理列表頁曾因此
  // 每次載入都崩潰，從來沒成功渲染過）。要「全部／不篩選」選項時改用哨兵值（例如 "ALL"），在狀態裡再轉回空字串。
  // 這條規則由使用者在 Sprint 300 收尾決定新增（Sprint 301 落地）。
  {
    files: ["**/*.{ts,tsx}"],
    rules: {
      "no-restricted-syntax": ["error", ...SELECT_ITEM_EMPTY_VALUE_SELECTORS],
    },
  },
  // （上面那個全域的 no-restricted-syntax 只有 SelectItem；下面資料層分層的區塊對 src/app、components、hooks 會再加一條 selector，
  //  同一個規則名稱後面的設定整個覆蓋前面的，所以順序必須是「全域在前、分層在後」。）
  // Sprint 331（DEF-455）：資料層分層。HTTP 請求（axios 實例、API 路徑常數）只能寫在 services/（與 lib/）；
  // 頁面、元件、hook 呼叫 services 的方法，不自己組請求。理由：請求形狀與錯誤處理散在畫面裡，後端契約一變就要改幾十處，
  // 型別也無法統一（Sprint 331 之前有 16 個頁面／元件繞過 services/，另有 85 處手寫的錯誤形狀斷言）。
  // 只禁「發請求的工具」：API_CONFIG 這種純設定值（proxy.ts、useChatSocket 組 URL 用）不在禁止之列。
  // 見 docs/02_architecture/adr/ADR-002-frontend-data-access-layering.md。
  {
    files: ["src/app/**/*.{ts,tsx}", "src/components/**/*.{ts,tsx}", "src/hooks/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            { name: "axios", message: "請求只能寫在 services/：到 src/services/ 新增或使用對應方法（見 ADR-002）。" },
            { name: "@/lib/axios", message: "請求只能寫在 services/：到 src/services/ 新增或使用對應方法（見 ADR-002）。" },
            { name: "@/lib/api_erp", message: "API 路徑常數只能在 services/ 使用：到 src/services/erp/ 新增或使用對應方法（見 ADR-002）。" },
            {
              name: "@/lib/api",
              importNames: ["API_ENDPOINTS"],
              message: "API 路徑常數只能在 services/ 使用：到 src/services/ 新增或使用對應方法（見 ADR-002）。",
            },
          ],
          // 相對路徑寫法（../../lib/axios）同樣擋下
          patterns: [
            { group: ["**/lib/axios", "**/lib/api_erp"], message: "請求只能寫在 services/（見 ADR-002）。" },
            { group: ["**/lib/api"], importNames: ["API_ENDPOINTS"], message: "API 路徑常數只能在 services/ 使用（見 ADR-002）。" },
          ],
        },
      ],
      // 直接呼叫瀏覽器的 fetch 同樣繞過 services/（Sprint 331 之前零使用，守住它）
      "no-restricted-globals": ["error", { name: "fetch", message: "請求只能寫在 services/（見 ADR-002）。" }],
      // 畫面裡手寫 `err as { response?: { data?: { message?: string } } }`：Sprint 331 之前 85 處、59 個檔案各寫一次。
      // 錯誤解析只有一個來源：lib/api-error.ts 的 apiErrorMessage()／getApiErrorInfo()。
      // （同一個規則名稱在 flat config 會整個覆蓋，所以 SelectItem 的 selector 要一併帶上。）
      "no-restricted-syntax": [
        "error",
        ...SELECT_ITEM_EMPTY_VALUE_SELECTORS,
        {
          selector: "TSAsExpression > TSTypeLiteral > TSPropertySignature[key.name='response']",
          message: "不要在畫面裡手寫 axios 錯誤形狀斷言：用 lib/api-error.ts 的 apiErrorMessage(err, 後備文案) 或 getApiErrorInfo(err)（見 ADR-002）。",
        },
      ],
    },
  },
  // Sprint 331（DEF-456）：useAsyncData(fetcher, deps, options) 的 deps 要和 useEffect 一樣被檢查——fetcher 用到的值漏列進 deps，
  // 就會拿到過期的閉包（篩選條件改了卻沒重抓）。additionalHooks 讓 exhaustive-deps 把它的第一個參數當成 effect 回呼、第二個當依賴陣列。
  {
    files: ["src/**/*.{ts,tsx}"],
    rules: {
      "react-hooks/exhaustive-deps": ["warn", { additionalHooks: "(useAsyncData)" }],
    },
  },
]);

export default eslintConfig;
