// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { isEntityHref, linkElements } from './detect'
import { listSourceFiles, readSource, sourceExists } from './source'

// 指向實體頁的連結不預取（Sprint 340，DEF-499／DEF-487）。
//
// 為什麼：實體頁（/stores/[slug]…、/stores/[slug]/blog/[postSlug]、/listings/[id]）的 generateMetadata 會在伺服器端呼叫後端（DEF-475，lib/seo.ts），
// 而 Next 16 對每個進入視窗的 <Link> 都會送預取請求，伺服器為了回應預取也會執行目標頁的 generateMetadata。
// 量測（docs/04_planning/SPRINT_340_PLAN.md）：一頁 12 張文章卡片 ＝ 24 個後端請求（每張卡片 2 個：店鋪＋文章），店鋪分頁連結每個 1 個，商品卡每張 1 個；
// 這些請求全從 Next 伺服器同一個來源 IP 出去，共用匿名限流桶（每分鐘 100 次），三、四次瀏覽就耗盡；
// 本機 E2E 守門（所有請求同一個來源 IP）也因此間歇失敗（DEF-499）。這個放大從 Sprint 335 加上 generateMetadata 起就存在，四個 Sprint 沒人看見。
//
// 預取對這些連結沒有可量測的好處：點擊到內容出現（本機、各 8 次），有預取中位數 410 ms、擋掉預取 378 ms；
// 官方指南（node_modules/next/dist/docs/01-app/02-guides/prefetching.md「Preventing too many prefetches」）也建議對大量連結關閉。
//
// 為什麼不改成「預取時 generateMetadata 回靜態標題」：Next 在把請求交給使用者程式碼前會刪掉 next-router-prefetch 標頭（stripFlightHeaders），
// 在 generateMetadata 裡偵測不到預取；而且預取的結果會被客戶端快取（沒有 loading.js 的路由 5 分鐘），回靜態標題有卡住錯誤標題的風險。
//
// 規則：href 指向實體頁的 <Link> 一律要寫 prefetch={false}。新增這類連結時照寫；如果你認為某個連結真的需要預取，
// 先量測它會讓 Next 伺服器多打幾個後端請求，再改這條規則並寫下理由，不要只是繞過它。

/**
 * href 是變數、偵測器看不出指向哪裡的共用元件：檔案裡的每個 <Link> 都要關預取。
 * ProductCard 只用來渲染商品／房源卡片（連到 /listings/{id}），href 由四個使用處傳進來。
 */
const ENTITY_LINK_COMPONENTS = ['components/storefront/ProductCard.tsx']

describe('指向實體頁的連結不預取（真實專案）', () => {
  const files = listSourceFiles(
    '.',
    (path) => /^(app|components)\//.test(path) && /\.tsx$/.test(path) && !/\.test\.tsx$/.test(path)
  )
  const offenders: string[] = []
  let guarded = 0
  for (const file of files) {
    for (const link of linkElements(readSource(file))) {
      if (!ENTITY_LINK_COMPONENTS.includes(file) && !isEntityHref(link.href)) continue
      if (link.noPrefetch) guarded++
      else offenders.push(`${file}:${link.line}（href=${link.href.slice(0, 60) || '（變數）'}）`)
    }
  }

  it('掃描範圍涵蓋全部頁面與元件', () => {
    expect(files.length).toBeGreaterThan(150)
  })

  it('每個指向實體頁的 <Link> 都有 prefetch={false}', () => {
    expect(
      offenders,
      '這些連結會在進入視窗時預取，讓 Next 伺服器為每個連結多打一到兩個後端請求（見本檔開頭的說明）：' +
        '在 <Link> 加 prefetch={false}'
    ).toEqual([])
  })

  it('偵測器沒有對真實專案失明：已關閉預取的實體頁連結不少於 8 個', () => {
    // 這個數字是下限，不是目標：Sprint 340 實作時是 11 個。守的是「偵測器什麼都沒找到所以全部放行」這種靜默失效。
    expect(guarded).toBeGreaterThanOrEqual(8)
  })

  it('共用元件清單裡的檔案都存在（改名或搬動時，守門不會悄悄失效）', () => {
    for (const file of ENTITY_LINK_COMPONENTS) {
      expect(sourceExists(file), file).toBe(true)
    }
  })
})

describe('linkElements／isEntityHref：偵測器本身', () => {
  it('字串 href 與樣板字串 href 都讀得到；行號正確', () => {
    const text = ['const a = 1', '<Link href="/login">登入</Link>', '<Link href={`/stores/${slug}`}>店鋪</Link>'].join('\n')
    expect(linkElements(text).map(({ line, href }) => ({ line, href }))).toEqual([
      { line: 2, href: '/login' },
      { line: 3, href: '`/stores/${slug}`' },
    ])
  })

  it('prefetch={false} 在 href 前面、後面、不同行都算有關；沒寫、寫 true、寫條件式都不算', () => {
    const noPrefetch = (text: string) => linkElements(text)[0].noPrefetch
    expect(noPrefetch('<Link prefetch={false} href="/x">a</Link>')).toBe(true)
    expect(noPrefetch('<Link href="/x" prefetch={false}>a</Link>')).toBe(true)
    expect(noPrefetch('<Link\n  href="/x"\n  className="c"\n  prefetch={ false }\n>a</Link>')).toBe(true)
    expect(noPrefetch('<Link href="/x">a</Link>')).toBe(false)
    expect(noPrefetch('<Link href="/x" prefetch={true}>a</Link>')).toBe(false)
    expect(noPrefetch('<Link href="/x" prefetch={hot ? false : null}>a</Link>')).toBe(false)
  })

  it('屬性裡的箭頭函式（=>）與字串裡的 > 不會讓開頭標籤提早結束（否則後面的 prefetch 會被漏看）', () => {
    expect(linkElements('<Link href="/x" onClick={() => go()} className="a > b" prefetch={false}>a</Link>')[0].noPrefetch).toBe(true)
    expect(linkElements('<Link href={cond ? "/a" : "/b"} onClick={(e) => { e.preventDefault() }} prefetch={false}>a</Link>')[0].noPrefetch).toBe(true)
  })

  it('子元素裡的 > 與別的標籤不影響：開頭標籤只看到第一個 > 為止，prefetch 在子元素文字裡不算', () => {
    expect(linkElements('<Link href="/x"><span>prefetch={false}</span></Link>')[0].noPrefetch).toBe(false)
  })

  it('<LinkButton 這類名稱更長的元件不算 <Link', () => {
    expect(linkElements('<LinkButton href="/stores/x">a</LinkButton>')).toEqual([])
  })

  it('href={識別字}：換成同檔宣告的右式，才看得出指向哪裡', () => {
    const text = 'const blogHref = `/stores/${slug}/blog`;\nreturn <Link href={blogHref}>返回</Link>'
    const [link] = linkElements(text)
    expect(link.href).toBe('`/stores/${slug}/blog`')
    expect(isEntityHref(link.href)).toBe(true)
  })

  it('href 是找不到宣告的識別字（例如元件的 prop）→ 維持識別字本身，由呼叫端決定怎麼處理', () => {
    expect(linkElements('<Link href={href}>a</Link>')[0].href).toBe('href')
  })

  it('沒有 href 屬性 → 空字串', () => {
    expect(linkElements('<Link {...rest} prefetch={false} />')[0].href).toBe('')
  })

  it('同一個檔案多個 <Link：逐一回報，自閉合的 <Link /> 也算', () => {
    const text = [
      '<Link href="/a" prefetch={false}>A</Link>',
      '<Link href="/b">B</Link>',
      '<Link href="/c" prefetch={false} />',
    ].join('\n')
    expect(linkElements(text).map((l) => [l.line, l.noPrefetch])).toEqual([
      [1, true],
      [2, false],
      [3, true],
    ])
  })

  it('括號不成對丟錯，不靜默放行', () => {
    expect(() => linkElements('<Link href={`/x` prefetch={false}>a</Link>')).toThrow('不成對')
    expect(() => linkElements('<Link href="/x')).toThrow('不成對')
    expect(() => linkElements('<Link href="/x"')).toThrow('沒有結束')
  })

  it('isEntityHref：實體頁與產生它們網址的輔助函式算；其他路徑不算', () => {
    for (const href of [
      '`/stores/${slug}`',
      '`/stores/${slug}/products`',
      '"/stores/demo/blog"',
      '`/listings/${item.id}`',
      'articlePath(post.tenantSlug, post.slug)',
      'storeBlogPath(slug)',
      "storeBlogPath(storeSlug, { categoryId: post.categoryId })",
    ]) {
      expect(isEntityHref(href), href).toBe(true)
    }
    for (const href of [
      '/login',
      '`/cart`',
      '`/orders/${orderId}`',
      '`/reviews/product/${id}`',
      '`/admin/stores/${id}`', // 後台路徑裡剛好有 /stores/，不是公開的店鋪頁
      '`/dashboard/tenants/${tenantId}`',
      '`/stores`', // 沒有店鋪代稱的列表頁，沒有 generateMetadata 的後端呼叫
      '',
    ]) {
      expect(isEntityHref(href), href).toBe(false)
    }
  })
})
