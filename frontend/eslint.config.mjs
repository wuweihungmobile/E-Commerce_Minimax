import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import designTokens from "./eslint-rules/design-tokens.mjs";

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
    rules: { "design-tokens/no-raw-palette": "error" },
  },
  // Radix 的 <Select.Item> 在 render 時對空字串 value 直接 throw，整頁崩潰（DEF-401：店主的商品管理列表頁曾因此
  // 每次載入都崩潰，從來沒成功渲染過）。要「全部／不篩選」選項時改用哨兵值（例如 "ALL"），在狀態裡再轉回空字串。
  // 這條規則由使用者在 Sprint 300 收尾決定新增（Sprint 301 落地）。
  {
    files: ["**/*.{ts,tsx}"],
    rules: {
      "no-restricted-syntax": [
        "error",
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
      ],
    },
  },
]);

export default eslintConfig;
