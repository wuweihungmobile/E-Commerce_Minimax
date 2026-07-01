import { DEFAULT_THEME, THEME_STORAGE_KEY, THEMES } from "./theme"

// 於 hydration 前套用使用者記憶的主題，避免主題閃爍（FOUC）。
// 以 dangerouslySetInnerHTML 注入同步 inline script，於 <html> 設定 data-theme。
export function ThemeScript() {
  const code = `(function(){try{var t=localStorage.getItem('${THEME_STORAGE_KEY}');var allowed=${JSON.stringify(
    THEMES
  )};document.documentElement.setAttribute('data-theme',allowed.indexOf(t)>-1?t:'${DEFAULT_THEME}');}catch(e){document.documentElement.setAttribute('data-theme','${DEFAULT_THEME}');}})();`
  return <script dangerouslySetInnerHTML={{ __html: code }} />
}
