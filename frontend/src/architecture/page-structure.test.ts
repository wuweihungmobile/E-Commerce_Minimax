// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { HEADING, hasHeadingTransitively, resolveComponentImport } from './detect'
import { listSourceFiles, readSource, sourceExists } from './source'

// 頁面結構守門（Sprint 331）：登入守門在外殼（ADR-002 決定 4）、每頁都有頁面標題（DEF-459）。

describe('登入守門在外殼，不在頁面（ADR-002 決定 4）', () => {
  // dashboard／admin／cms 三個區域都包在 SellerShell 裡，SellerShell 用 AuthGuard 擋未登入的人（沒登入就不渲染內容、不發請求）。
  // Sprint 331 之前：37 個 dashboard 頁面各自在 useEffect 裡檢查登入並導去 /login，admin／cms 幾乎沒有守門。
  const sellerAreaFiles = listSourceFiles('app', (path) =>
    /^app\/(dashboard|admin|cms)\//.test(path) && /\.tsx?$/.test(path) && !/\.test\.tsx?$/.test(path)
  )

  it('掃描範圍涵蓋賣家三個區域', () => {
    expect(sellerAreaFiles.length).toBeGreaterThan(60)
    expect(sellerAreaFiles.some((f) => f.startsWith('app/admin/'))).toBe(true)
    expect(sellerAreaFiles.some((f) => f.startsWith('app/cms/'))).toBe(true)
  })

  it('賣家區域的頁面不再自己檢查登入（AuthService.isAuthenticated）', () => {
    const offenders = sellerAreaFiles.filter((file) => /\bisAuthenticated\(/.test(readSource(file)))
    expect(offenders, '登入守門只放在 SellerShell；頁面裡再寫一份是重複，也會讓新頁面忘記而不一致').toEqual([])
  })

  it('SellerShell 用 AuthGuard 包住頁面內容', () => {
    const shell = readSource('components/layout/SellerShell.tsx')
    expect(shell).toMatch(/<AuthGuard>\s*\{children\}\s*<\/AuthGuard>/)
  })
})

describe('每個頁面都有頁面標題（DEF-459 靜態口徑）', () => {
  const pages = listSourceFiles('app', (path) => path.endsWith('/page.tsx'))

  it('掃描範圍涵蓋全部頁面', () => {
    expect(pages.length).toBeGreaterThan(100)
  })

  it('每個 page.tsx 自己或它（傳遞式）引用的元件裡有 <PageTitle> 或 <h1>', () => {
    const missing = pages.filter((page) => !hasHeadingTransitively(page, readSource, sourceExists))
    expect(
      missing,
      '沒有頁面標題，螢幕閱讀器的標題導覽找不到這一頁。純視覺上不想顯示可用 <PageTitle className="sr-only">'
    ).toEqual([])
  })

  it('標題偵測認得 PageTitle 與原生 h1，不把 h2／h3 當頁面標題', () => {
    expect(HEADING.test('<PageTitle>x</PageTitle>')).toBe(true)
    expect(HEADING.test('<h1 className="a">x</h1>')).toBe(true)
    expect(HEADING.test('<h2>x</h2><h3>y</h3><CardTitle>z</CardTitle>')).toBe(false)
  })

  it('追得到元件裡的標題：頁面本身沒有標題、但引用的元件有 → 算有標題；引用的元件也沒有 → 沒有', () => {
    const files: Record<string, string> = {
      'app/a/page.tsx': "import Body from '@/components/a/Body'\nexport default function P() { return <Body /> }",
      'components/a/Body.tsx': "import Inner from './Inner'\nexport default function B() { return <Inner /> }",
      'components/a/Inner.tsx': 'export default function I() { return <h1>標題</h1> }',
      'app/b/page.tsx': "import Body from '@/components/b/Body'\nexport default function P() { return <Body /> }",
      'components/b/Body.tsx': 'export default function B() { return <h2>不是頁面標題</h2> }',
    }
    const read = (p: string) => files[p]
    const exists = (p: string) => p in files
    expect(hasHeadingTransitively('app/a/page.tsx', read, exists)).toBe(true)
    expect(hasHeadingTransitively('app/b/page.tsx', read, exists)).toBe(false)
  })

  it('引用迴圈不會無限遞迴', () => {
    const files: Record<string, string> = {
      'components/x/A.tsx': "import B from './B'\nexport const A = () => <B />",
      'components/x/B.tsx': "import A from './A'\nexport const B = () => <A />",
    }
    expect(hasHeadingTransitively('components/x/A.tsx', (p) => files[p], (p) => p in files)).toBe(false)
  })

  it('import 路徑解析：@/ 別名與相對路徑；不追 services／lib／套件', () => {
    const exists = () => true
    expect(resolveComponentImport('app/a/page.tsx', '@/components/ui/page-title', exists)).toBe('components/ui/page-title.tsx')
    expect(resolveComponentImport('components/a/Body.tsx', './Inner', exists)).toBe('components/a/Inner.tsx')
    expect(resolveComponentImport('components/a/Body.tsx', '../b/Other', exists)).toBe('components/b/Other.tsx')
    expect(resolveComponentImport('app/a/page.tsx', '@/services/auth', exists)).toBeNull()
    expect(resolveComponentImport('app/a/page.tsx', 'react', exists)).toBeNull()
  })
})
