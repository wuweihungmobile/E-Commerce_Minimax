// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { ADMIN_TITLE_TEMPLATE, DASHBOARD_TITLE_TEMPLATE, SITE_NAME, SITE_TITLE, TITLE_TEMPLATE } from '@/lib/site'
import { findTitleSpec, resolveTitle, titleSourceFiles, type TitleSpec } from './titles'
import { listSourceFiles, readSource, sourceExists } from './source'

// 頁面標題守門（Sprint 332，DEF-465）：每個頁面都有自己的 <title>，而且解析出來的最終標題帶品牌、帶對的區域後綴、彼此不重複。
// 在這之前整站 104 個頁面有 102 個共用同一個 document.title：分頁列、瀏覽紀錄、書籤分不出是哪一頁，
// 螢幕閱讀器換頁時念出的標題也一模一樣（WCAG 2.4.2 Page Titled）。

const CONSTANTS = { SITE_TITLE, SITE_NAME, TITLE_TEMPLATE, DASHBOARD_TITLE_TEMPLATE, ADMIN_TITLE_TEMPLATE }

const dirOf = (file: string) => file.split('/').slice(0, -1).join('/')

interface PageTitleInfo {
  page: string
  /** 頁面自己（page.tsx）或同目錄的 layout.tsx 有標題；只靠上層繼承不算 */
  own: boolean
  resolved: string | null
}

function collect(pages: string[]): PageTitleInfo[] {
  return pages.map((page) => {
    const chain: TitleSpec[] = []
    let own = false
    for (const file of titleSourceFiles(page)) {
      if (!sourceExists(file)) continue
      const spec = findTitleSpec(readSource(file), CONSTANTS, file)
      if (!spec) continue
      chain.push(spec)
      if (file === page || file === `${dirOf(page)}/layout.tsx`) own = true
    }
    return { page, own, resolved: chain.length > 0 ? resolveTitle(chain) : null }
  })
}

const hasBrand = (resolved: string) => resolved === SITE_TITLE || resolved.endsWith(SITE_NAME)

describe('每個頁面都有自己的標題（真實專案）', () => {
  const pages = listSourceFiles('app', (path) => path.endsWith('/page.tsx'))
  const infos = collect(pages)

  it('掃描範圍涵蓋全部頁面', () => {
    // 下限只是確認掃描沒有掃空，不是頁面數的規格
    expect(pages.length).toBeGreaterThan(90)
  })

  it('每個頁面自己（page.tsx 或同目錄的 layout.tsx）匯出標題，不能只靠上層繼承預設標題', () => {
    const missing = infos.filter((i) => !i.own).map((i) => i.page)
    expect(
      missing,
      '沒有自己的標題：分頁列、瀏覽紀錄、書籤與螢幕閱讀器看到的是整站共用的預設標題。' +
        "Server Component 頁面在 page.tsx 匯出 metadata；有 'use client' 的頁面不能匯出，改放同目錄的 layout.tsx"
    ).toEqual([])
  })

  it('解析後的標題都帶品牌（template 沒有被中間層切斷）', () => {
    const bare = infos.filter((i) => i.resolved !== null && !hasBrand(i.resolved)).map((i) => `${i.page} → ${i.resolved}`)
    expect(
      bare,
      '標題少了「意象若水 RUOSHUI」：多半是路徑上某個 layout 設了字串標題而沒有重新宣告 template——' +
        'Next 的規則是中間層的標題沒有 template 就會切斷上層傳下來的 template。layout 要寫 title: { default, template: <區域的 TITLE_TEMPLATE> }'
    ).toEqual([])
  })

  it('區域後綴正確：賣家後台頁面有「賣家後台」、平台管理台頁面有「平台管理」，其他頁面都沒有', () => {
    const wrong: string[] = []
    for (const i of infos) {
      if (i.resolved === null) continue
      const area = i.page.startsWith('app/dashboard/') ? '賣家後台' : i.page.startsWith('app/admin/') ? '平台管理' : null
      for (const label of ['賣家後台', '平台管理']) {
        const has = i.resolved.includes(`｜${label}｜`)
        if (has !== (area === label)) wrong.push(`${i.page} → ${i.resolved}`)
      }
    }
    expect(wrong, '「客服工單」「店鋪管理」「訂單詳情」在買家、賣家、平台三邊各有一頁，沒有區域後綴就分不出來').toEqual([])
  })

  it('解析後的標題彼此不重複（兩個不同的頁面不能有同一個標題）', () => {
    const groups = new Map<string, string[]>()
    for (const i of infos) {
      if (i.resolved === null) continue
      groups.set(i.resolved, [...(groups.get(i.resolved) ?? []), i.page])
    }
    const duplicated = [...groups.entries()].filter(([, files]) => files.length > 1).map(([title, files]) => `${title}：${files.join('、')}`)
    expect(duplicated, '標題的用途就是分辨頁面；同一個標題等於沒有').toEqual([])
  })

  it('首頁用完整品牌標題（title.absolute），不是「頁名｜品牌」', () => {
    const home = infos.find((i) => i.page === 'app/(storefront)/page.tsx')
    expect(home?.resolved).toBe(SITE_TITLE)
  })
})

describe('findTitleSpec：認得的寫法', () => {
  const find = (text: string) => findTitleSpec(text, CONSTANTS, 'test.tsx')

  it('字串標題（單引號、雙引號都行）', () => {
    expect(find("export const metadata: Metadata = {\n  title: '登入',\n}")).toEqual({ kind: 'string', text: '登入' })
    expect(find('export const metadata: Metadata = { title: "隱私權政策" }')).toEqual({ kind: 'string', text: '隱私權政策' })
  })

  it('{ default, template }：值可以是字串或常數名稱', () => {
    expect(find("export const metadata = { title: { default: '庫存', template: DASHBOARD_TITLE_TEMPLATE } }")).toEqual({
      kind: 'object',
      default: '庫存',
      template: DASHBOARD_TITLE_TEMPLATE,
    })
    expect(find('export const metadata = { title: { default: SITE_TITLE, template: TITLE_TEMPLATE }, description: "x" }')).toEqual({
      kind: 'object',
      default: SITE_TITLE,
      template: TITLE_TEMPLATE,
    })
  })

  it('{ absolute }', () => {
    expect(find('export const metadata = { title: { absolute: SITE_TITLE } }')).toEqual({ kind: 'object', absolute: SITE_TITLE })
  })

  it('generateMetadata 只能確認「有」', () => {
    expect(find('export async function generateMetadata() { return { title: x } }')).toEqual({ kind: 'dynamic' })
  })

  it('沒有 metadata、或有 metadata 但沒有標題 → null；巢狀物件裡的 title（例如 openGraph）不算', () => {
    expect(find('export default function P() { return null }')).toBeNull()
    expect(find("export const metadata = { description: '只有說明' }")).toBeNull()
    expect(find("export const metadata = { openGraph: { title: '分享用的標題' } }")).toBeNull()
  })

  it('不認得的寫法丟錯（不靜默放行）：未知常數、含 ${} 的字串、運算式', () => {
    expect(() => find("export const metadata = { title: { default: '頁', template: UNKNOWN_TEMPLATE } }")).toThrow('不認得的常數')
    expect(() => find('export const metadata = { title: `頁 ${x}` }')).toThrow('運算式')
    expect(() => find("export const metadata = { title: 'a' + 'b' }")).toThrow()
    expect(() => find('export const metadata = { title: {} }')).toThrow('沒有 default')
  })
})

describe('resolveTitle：模擬 Next 的標題解析', () => {
  const root: TitleSpec = { kind: 'object', default: SITE_TITLE, template: TITLE_TEMPLATE }

  it('根 template ＋ 頁面字串標題 → 「頁名｜品牌」', () => {
    expect(resolveTitle([root, { kind: 'string', text: '登入' }])).toBe(`登入｜${SITE_NAME}`)
  })

  it('區域 layout 的 template 取代根 template；同一層的 default 經過上一層的 template', () => {
    const dashboard: TitleSpec = { kind: 'object', default: '營運總覽｜賣家後台', template: DASHBOARD_TITLE_TEMPLATE }
    expect(resolveTitle([root, dashboard])).toBe(`營運總覽｜賣家後台｜${SITE_NAME}`)
    expect(resolveTitle([root, dashboard, { kind: 'string', text: '商品管理' }])).toBe(`商品管理｜賣家後台｜${SITE_NAME}`)
  })

  it('中間層的字串標題會切斷 template——底下的頁面標題變成沒有品牌（Sprint 332 開發時踩到的）', () => {
    const middle: TitleSpec = { kind: 'string', text: '預訂詳情' }
    expect(resolveTitle([root, middle])).toBe(`預訂詳情｜${SITE_NAME}`)
    const below = resolveTitle([root, middle, { kind: 'string', text: '預訂付款已取消' }])
    expect(below).toBe('預訂付款已取消')
    expect(hasBrand(below as string)).toBe(false)
  })

  it('中間層若重新宣告 template，底下的頁面標題保有品牌', () => {
    const middle: TitleSpec = { kind: 'object', default: '預訂詳情', template: TITLE_TEMPLATE }
    expect(resolveTitle([root, middle, { kind: 'string', text: '預訂付款已取消' }])).toBe(`預訂付款已取消｜${SITE_NAME}`)
  })

  it('absolute 不套 template；沒有標題的鏈回傳 null', () => {
    expect(resolveTitle([root, { kind: 'object', absolute: SITE_TITLE }])).toBe(SITE_TITLE)
    expect(resolveTitle([])).toBeNull()
  })

  it('titleSourceFiles：每一層目錄的 layout.tsx 由根到頁面，最後是頁面本身', () => {
    expect(titleSourceFiles('app/dashboard/erp/page.tsx')).toEqual([
      'app/layout.tsx',
      'app/dashboard/layout.tsx',
      'app/dashboard/erp/layout.tsx',
      'app/dashboard/erp/page.tsx',
    ])
  })
})
