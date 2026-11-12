// @vitest-environment node
import { describe, expect, it } from 'vitest'
import robots from '@/app/robots'
import { listSourceFiles, readSource } from './source'

// 公開頁的 SEO 伺服器端渲染守門（DEF-500，Sprint 341）。
//
// 為什麼要守：公開頁（首頁、商品、房源、店鋪、部落格、商品詳情）的內容原本都由瀏覽器載入，第一份 HTML 只有載入中骨架——
// 搜尋引擎與不執行 JavaScript 的爬蟲看不到任何商品或文章（Sprint 340 實測平台 /blog 的 12 張卡片在初始 HTML 裡 0 張）。
// 改成伺服器端先取資料之後，「新增一個公開頁卻忘了這樣做」「改版時不小心把 initialData 拿掉」不會有任何型別、編譯或功能上的訊號——
// 頁面在瀏覽器裡一切正常，只有爬蟲看到的是空的。所以把規則寫成測試。
//
// 這份守門讀的是原始碼文字；真正「第一份 HTML 裡有內容」由真實後端的 e2e/at-seo-ssr-real.spec.ts（不執行 JavaScript）驗證。

const STOREFRONT = 'app/(storefront)'

interface DataPage {
  /** 頁面（server component）：必須在伺服器端取資料，並把結果當 props 傳給用戶端元件 */
  page: string
  /** 頁面傳給用戶端元件的屬性（initialXxx） */
  props: string[]
  /** 接收這些屬性、並把它們交給 useAsyncData 的元件檔 */
  components: string[]
  /** 不是「initialData: 屬性名」這種直接交給 useAsyncData 的屬性，各自的消費方式（元件檔、必須出現的寫法、理由） */
  consumedAs?: Record<string, { file: string; patterns: RegExp[]; why: string }>
}

/** 公開、含資料的頁面登記表。新增公開資料頁要在這裡登記（並照做），或寫進 EXEMPT 並說明理由。 */
const DATA_PAGES: DataPage[] = [
  { page: `${STOREFRONT}/page.tsx`, props: ['initialResult'], components: ['components/storefront/HomeContent.tsx'] },
  { page: `${STOREFRONT}/products/page.tsx`, props: ['initialResult'], components: ['components/storefront/ListingTypeBrowser.tsx'] },
  { page: `${STOREFRONT}/rooms/page.tsx`, props: ['initialResult'], components: ['components/storefront/ListingTypeBrowser.tsx'] },
  { page: `${STOREFRONT}/blog/page.tsx`, props: ['initialFeed'], components: ['components/storefront/PlatformBlogFeed.tsx'] },
  { page: `${STOREFRONT}/listings/[id]/page.tsx`, props: ['initialListing'], components: ['components/storefront/ListingDetail.tsx'] },
  {
    page: `${STOREFRONT}/stores/[slug]/page.tsx`,
    props: ['initialProfile', 'initialFeatured'],
    components: ['components/storefront/StoreLayout.tsx', 'components/storefront/StoreFeaturedListings.tsx'],
  },
  {
    page: `${STOREFRONT}/stores/[slug]/products/page.tsx`,
    props: ['initialProfile', 'initialListings'],
    components: ['components/storefront/StoreLayout.tsx', 'components/storefront/StoreListingGrid.tsx'],
  },
  {
    page: `${STOREFRONT}/stores/[slug]/rooms/page.tsx`,
    props: ['initialProfile', 'initialListings'],
    components: ['components/storefront/StoreLayout.tsx', 'components/storefront/StoreListingGrid.tsx'],
  },
  {
    page: `${STOREFRONT}/stores/[slug]/blog/page.tsx`,
    props: ['initialProfile', 'initialCategories', 'initialPosts'],
    components: ['components/storefront/StoreLayout.tsx', 'components/storefront/StoreBlogList.tsx'],
  },
  {
    page: `${STOREFRONT}/stores/[slug]/blog/[postSlug]/page.tsx`,
    props: ['initialProfile', 'initialPost', 'initialCards'],
    components: ['components/storefront/StoreLayout.tsx', 'components/storefront/StoreArticle.tsx'],
    consumedAs: {
      initialPost: {
        file: 'components/storefront/StoreArticle.tsx',
        patterns: [/enabled:\s*!initialPost/, /const post = initialPost \?\? loaded/, /enabled:\s*Boolean\(initialPost\)/],
        why: '文章有伺服器資料時不再載入（enabled: !initialPost），但瀏覽器仍要計一次瀏覽（第二個 useAsyncData，enabled: Boolean(initialPost)）：伺服器端取文章不計瀏覽',
      },
      initialCards: {
        file: 'components/storefront/StoreArticle.tsx',
        patterns: [/initialCard=\{initialCards\?\.\[/, /initialData:\s*initialCard\b/],
        why: '嵌入卡片由 renderContentWithEmbeds 依編號取出，交給每張卡自己的 useAsyncData（屬性名是 initialCard）',
      },
    },
  },
]

/** 不需要伺服器端取資料的公開頁，每一筆都要有理由。 */
const EXEMPT: Record<string, string> = {
  [`${STOREFRONT}/privacy/page.tsx`]: '靜態內容（沒有資料可取）',
  [`${STOREFRONT}/terms/page.tsx`]: '靜態內容（沒有資料可取）',
  [`${STOREFRONT}/blog/[slug]/page.tsx`]:
    '舊文章網址 /blog/{slug} 的導向頁：靠登入者瀏覽器的 tenantId 找出文章所屬店鋪再導向新網址，沒有可收錄的內容（見 DEF-483／DEF-500⑤）',
}

describe('公開頁都在伺服器端取資料（DEF-500 守門）', () => {
  const pages = listSourceFiles(STOREFRONT, (path) => path.endsWith('/page.tsx'))

  it('掃描範圍涵蓋店鋪前台的全部頁面（不是掃到零個檔案而假綠燈）', () => {
    expect(pages.length).toBeGreaterThanOrEqual(DATA_PAGES.length + Object.keys(EXEMPT).length)
    expect(pages).toContain(`${STOREFRONT}/page.tsx`)
  })

  it('店鋪前台的每一頁不是登記為「伺服器端取資料」，就是寫明理由豁免——新增公開頁不能悄悄漏掉', () => {
    const known = new Set([...DATA_PAGES.map((entry) => entry.page), ...Object.keys(EXEMPT)])
    const unregistered = pages.filter((page) => !known.has(page))
    expect(
      unregistered,
      '新的公開頁：照 DEF-500 的做法在伺服器端取資料並傳 initial* 給元件（見 lib/server-api、lib/public-api），再登記到 DATA_PAGES；確實沒有資料可取的才登記到 EXEMPT 並寫理由'
    ).toEqual([])
    const stale = [...known].filter((page) => !pages.includes(page))
    expect(stale, '登記的頁面已經不存在：從清單移除').toEqual([])
  })

  for (const entry of DATA_PAGES) {
    describe(entry.page, () => {
      const source = readSource(entry.page)

      it('在伺服器端取資料（fetchPublic 或店鋪／貼文載入函式），不是 client component', () => {
        expect(source, '資料頁必須是 server component（不能有 "use client"）：伺服器端才取得到資料').not.toMatch(/^\s*['"]use client['"]/m)
        expect(source).toMatch(/\b(?:fetchPublic|peekStoreProfile|loadStoreBlog|loadEmbeddedCards)\s*[<(]/)
      })

      it(`把伺服器端取到的資料傳給元件：${entry.props.join('、')}`, () => {
        for (const prop of entry.props) expect(source, `${prop}= 不見了：第一份 HTML 會是載入中骨架`).toContain(`${prop}=`)
      })

      it('元件宣告這些屬性，並交給 useAsyncData 的 initialData（否則資料送到了、元件卻還是重新請求或不顯示）', () => {
        for (const prop of entry.props) {
          const holders = entry.components.filter((file) => new RegExp(`\\b${prop}\\b`).test(readSource(file)))
          expect(holders, `${prop} 沒有任何元件接收`).not.toEqual([])
          const special = entry.consumedAs?.[prop]
          if (special) {
            for (const pattern of special.patterns) {
              expect(readSource(special.file), `${special.file} 對 ${prop} 的處理不見了（${special.why}）：${pattern}`).toMatch(pattern)
            }
            continue
          }
          for (const file of holders) {
            expect(readSource(file), `${file} 沒有把 ${prop} 交給 useAsyncData`).toMatch(
              new RegExp(`initialData:\\s*${prop}\\b`)
            )
          }
        }
      })
    })
  }
})

describe('店鋪存在與否只由 stores/[slug]/layout.tsx 判定', () => {
  // Sprint 341 開發時踩到：layout 與頁面都拋 notFound()，離頁面最近的 not-found 邊界接走——文章頁就把「店鋪不存在」說成「找不到文章」。
  it('layout 用 loadStoreProfile（店鋪不存在 → notFound()）', () => {
    expect(readSource(`${STOREFRONT}/stores/[slug]/layout.tsx`)).toMatch(/loadStoreProfile\(/)
  })

  it('店鋪底下的頁面不再呼叫 loadStoreProfile（會丟 notFound），一律用 peekStoreProfile', () => {
    const pages = listSourceFiles(`${STOREFRONT}/stores`, (path) => path.endsWith('/page.tsx'))
    expect(pages.length).toBeGreaterThanOrEqual(5)
    const offenders = pages.filter((page) => /\bloadStoreProfile\(/.test(readSource(page)))
    expect(offenders).toEqual([])
  })
})

describe('公開列表的分頁是真正的連結', () => {
  // 搜尋引擎與不執行 JavaScript 的爬蟲只會沿著 <a href> 走；按鈕＋onChange 的分頁讓它們永遠到不了第 2 頁以後的內容。
  const files = listSourceFiles('components/storefront', (path) => /\.tsx$/.test(path) && !/\.test\.tsx$/.test(path))

  it('店鋪前台元件裡每一個 <Pagination> 都帶 hrefFor', () => {
    const offenders: string[] = []
    let count = 0
    for (const file of files) {
      const source = readSource(file)
      for (const match of source.matchAll(/<Pagination\b([\s\S]*?)\/>/g)) {
        count++
        if (!/\bhrefFor=/.test(match[1])) offenders.push(file)
      }
    }
    expect(count, '掃描到的 <Pagination> 數量（不是掃到零個而假綠燈）').toBeGreaterThanOrEqual(5)
    expect(offenders, '公開列表的分頁要用 hrefFor 渲染成連結（見 components/ui/pagination.tsx）').toEqual([])
  })
})

describe('私人區域不被搜尋引擎收錄，公開區域不被誤擋', () => {
  it.each(['app/(auth)/layout.tsx', 'app/dashboard/layout.tsx', 'app/admin/layout.tsx'])('%s 宣告 noindex', (file) => {
    expect(readSource(file)).toMatch(/robots:\s*\{\s*index:\s*false/)
  })

  it.each(['app/layout.tsx', `${STOREFRONT}/layout.tsx`])('%s（公開區域）沒有 noindex——複製貼上時不能把整個前台擋掉', (file) => {
    expect(readSource(file)).not.toMatch(/index:\s*false/)
  })

  it('robots.txt 擋掉後台、管理介面與 API，其餘開放，並指向 sitemap.xml', () => {
    const result = robots()
    const rules = Array.isArray(result.rules) ? result.rules : [result.rules]
    const all = rules.find((rule) => rule.userAgent === '*')
    expect(all?.allow).toBe('/')
    const disallowed = ([] as string[]).concat(all?.disallow ?? [])
    for (const path of ['/dashboard', '/admin', '/api/']) expect(disallowed).toContain(path)
    expect(disallowed, '公開內容不能被 robots.txt 擋掉').not.toContain('/')
    expect(String(result.sitemap)).toMatch(/\/sitemap\.xml$/)
  })
})

describe('公開頁有結構化資料', () => {
  it.each([
    [`${STOREFRONT}/page.tsx`, 'websiteJsonLd'],
    [`${STOREFRONT}/stores/[slug]/page.tsx`, 'storeJsonLd'],
    [`${STOREFRONT}/listings/[id]/page.tsx`, 'listingJsonLd'],
    [`${STOREFRONT}/stores/[slug]/blog/[postSlug]/page.tsx`, 'articleJsonLd'],
  ])('%s 輸出 %s', (page, builder) => {
    const source = readSource(page)
    expect(source).toContain(`${builder}(`)
    expect(source).toContain('<JsonLd')
  })
})

describe('公開頁元件的輸出不依賴伺服器與瀏覽器各自的環境（hydration 不符的來源）', () => {
  // 公開頁的內容由伺服器渲染（容器通常是 UTC、預設語系 en-US），再由訪客的瀏覽器（台灣 UTC+8、zh-TW）hydration。
  // 輸出若依賴時區、語系或「現在」，兩邊算出不同的字串，React 就丟掉伺服器的 HTML 重新渲染（console 出現 hydration 錯誤，
  // 搜尋引擎看到的與使用者看到的也不同）。這類問題在開發機上永遠看不到——伺服器與瀏覽器在同一台機器、同一個時區。
  const files = listSourceFiles('components/storefront', (path) => /\.tsx$/.test(path) && !/\.test\.tsx$/.test(path))

  it('掃描範圍涵蓋店鋪前台元件', () => {
    expect(files.length).toBeGreaterThan(15)
  })

  it('不直接用 toLocaleDateString／toLocaleTimeString（沒指定時區就依環境而異）：日期一律用 lib/date 的 formatPublicDate', () => {
    const offenders = files.filter((file) => /\.toLocale(Date|Time)String\(/.test(readSource(file)))
    expect(offenders).toEqual([])
  })

  it('每個 toLocaleString( 都指定語系（沒指定時，伺服器與瀏覽器的預設語系不同，數字的千分位與小數點會不同）', () => {
    const offenders = files.filter((file) => /\.toLocaleString\(\s*\)/.test(readSource(file)))
    expect(offenders).toEqual([])
  })

  it('依賴「現在」的程式（new Date() 不帶參數、Date.now()）只有登記的元件，而且登記的元件只在瀏覽器渲染', () => {
    const registered: Record<string, string> = {
      'components/storefront/MonthCalendar.tsx': '第一個畫面就依賴現在（本月是幾月、今天是哪天）：只在瀏覽器接手之後渲染（ListingDetail 的 useIsClient）',
      'components/storefront/ListingDetail.tsx': 'new Date() 只在事件處理常式裡（點「查詢可用性」「加入購物車」時驗證日期），不在 render',
    }
    const usesNow = files.filter((file) => /new Date\(\s*\)|Date\.now\(\)/.test(readSource(file)))
    expect(usesNow, '新的「依賴現在」的元件：要在瀏覽器接手之後才渲染（useIsClient），並登記到這裡寫理由').toEqual(Object.keys(registered).sort())
    // MonthCalendar 的第一個畫面依賴現在（本月是幾月）：用它的 ListingDetail 必須等 useIsClient 才渲染它
    const detail = readSource('components/storefront/ListingDetail.tsx')
    expect(detail).toMatch(/useIsClient\(\)/)
    expect(detail).toMatch(/isClient \? \(\s*<MonthCalendar/)
  })
})

