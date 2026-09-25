import { DEFAULT_THEME, THEME_STORAGE_KEY, THEMES } from "./theme"

// 於 hydration 前套用使用者記憶的主題，避免主題閃爍（FOUC）。
// 以 dangerouslySetInnerHTML 注入同步 inline script，於 <html> 設定 data-theme。
// 嚴格 CSP（見 src/proxy.ts）只放行帶本次請求 nonce 的 inline script，故必須帶入 nonce；
// 瀏覽器會把已解析 script 的 nonce 屬性隱藏，與 server 渲染值不同，故 suppressHydrationWarning。
export function ThemeScript({ nonce }: { nonce?: string }) {
  const code = `(function(){try{var t=localStorage.getItem('${THEME_STORAGE_KEY}');var allowed=${JSON.stringify(
    THEMES
  )};document.documentElement.setAttribute('data-theme',allowed.indexOf(t)>-1?t:'${DEFAULT_THEME}');}catch(e){document.documentElement.setAttribute('data-theme','${DEFAULT_THEME}');}})();`
  return <script nonce={nonce} suppressHydrationWarning dangerouslySetInnerHTML={{ __html: code }} />
}
