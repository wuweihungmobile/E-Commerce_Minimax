import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './pagination'

// 分頁是「使用者怎麼在長列表裡移動」：要能到首尾、看得出現在在哪、不能點出範圍。
describe('Pagination', () => {
  const pageButtons = () =>
    screen
      .getAllByRole('button')
      .map((b) => b.getAttribute('aria-label') ?? b.textContent)

  it('只有一頁（或沒有資料）不渲染分頁列', () => {
    const { container } = render(<Pagination current={1} total={1} />)
    expect(container.firstChild).toBeNull()
  })

  it('頁數不多（≤7）全部列出', () => {
    render(<Pagination current={3} total={7} />)
    expect(pageButtons()).toEqual(['上一頁', '1', '2', '3', '4', '5', '6', '7', '下一頁'])
  })

  it('頁數很多：永遠有首頁與末頁，當前頁前後各一頁，其餘用省略號（省略號不是按鈕）', () => {
    render(<Pagination current={10} total={30} />)
    expect(pageButtons()).toEqual(['上一頁', '1', '9', '10', '11', '30', '下一頁'])
    expect(screen.getAllByText('…')).toHaveLength(2)
  })

  it('在前段只在右邊出現省略號；在後段只在左邊出現', () => {
    const { unmount } = render(<Pagination current={1} total={30} />)
    expect(pageButtons()).toEqual(['上一頁', '1', '2', '30', '下一頁'])
    expect(screen.getAllByText('…')).toHaveLength(1)
    unmount()
    render(<Pagination current={30} total={30} />)
    expect(pageButtons()).toEqual(['上一頁', '1', '29', '30', '下一頁'])
    expect(screen.getAllByText('…')).toHaveLength(1)
  })

  it('目前頁用 aria-current="page" 標示（輔助科技與 E2E 讀這個，不讀 class）', () => {
    render(<Pagination current={4} total={9} />)
    const current = screen.getAllByRole('button').filter((b) => b.getAttribute('aria-current') === 'page')
    expect(current.map((b) => b.textContent)).toEqual(['4'])
  })

  it('點頁碼、上一頁、下一頁都回報目標頁碼', () => {
    const onChange = vi.fn()
    render(<Pagination current={5} total={9} onChange={onChange} />)
    fireEvent.click(screen.getByText('9')) // 末頁一直在頁碼列上
    fireEvent.click(screen.getByLabelText('上一頁'))
    fireEvent.click(screen.getByLabelText('下一頁'))
    expect(onChange.mock.calls.map((c) => c[0])).toEqual([9, 4, 6])
  })

  it('點目前這一頁不觸發（避免同一頁重抓一次資料）', () => {
    const onChange = vi.fn()
    render(<Pagination current={5} total={9} onChange={onChange} />)
    fireEvent.click(screen.getByText('5'))
    expect(onChange).not.toHaveBeenCalled()
  })

  it('第一頁的「上一頁」與最後一頁的「下一頁」是停用的，點了也不會超出範圍', () => {
    const onChange = vi.fn()
    const { unmount } = render(<Pagination current={1} total={3} onChange={onChange} />)
    const prev = screen.getByLabelText('上一頁') as HTMLButtonElement
    expect(prev.disabled).toBe(true)
    fireEvent.click(prev)
    unmount()
    render(<Pagination current={3} total={3} onChange={onChange} />)
    const next = screen.getByLabelText('下一頁') as HTMLButtonElement
    expect(next.disabled).toBe(true)
    fireEvent.click(next)
    expect(onChange).not.toHaveBeenCalled()
  })
})

// DEF-500（Sprint 341）：搜尋引擎與不執行 JavaScript 的爬蟲只會沿著 <a href> 走。按鈕＋onChange 的分頁讓它們永遠到不了第 2 頁以後的內容，
// 所以公開列表的分頁要用真正的連結。這組測試守的是「連結真的有 href、指向正確的頁」，而不是長相。
describe('Pagination：連結模式（hrefFor）', () => {
  const hrefFor = (page: number) => (page === 1 ? '/blog' : `/blog?page=${page}`)
  const links = () => screen.getAllByRole('link').map((a) => [a.getAttribute('aria-label') ?? a.textContent, a.getAttribute('href')])

  it('頁碼、上一頁、下一頁都是真正的連結，href 指向對應的頁（第 1 頁沒有 ?page）', () => {
    render(<Pagination current={3} total={5} hrefFor={hrefFor} />)
    expect(links()).toEqual([
      ['上一頁', '/blog?page=2'],
      ['1', '/blog'],
      ['2', '/blog?page=2'],
      ['4', '/blog?page=4'],
      ['5', '/blog?page=5'],
      ['下一頁', '/blog?page=4'],
    ])
    expect(screen.queryAllByRole('button'), '連結模式不該再有按鈕：爬蟲點不了').toHaveLength(0)
  })

  it('目前這一頁不是連結（連到自己沒有意義），用 aria-current="page" 標示', () => {
    render(<Pagination current={3} total={5} hrefFor={hrefFor} />)
    const current = document.querySelector('[aria-current="page"]')
    expect(current?.textContent).toBe('3')
    expect(current?.tagName).toBe('SPAN')
  })

  it('第一頁沒有「上一頁」連結、最後一頁沒有「下一頁」連結（停用的外觀，不會帶爬蟲到不存在的頁）', () => {
    const { unmount } = render(<Pagination current={1} total={3} hrefFor={hrefFor} />)
    expect(screen.getByLabelText('上一頁').getAttribute('aria-disabled')).toBe('true')
    expect(screen.getByLabelText('上一頁').getAttribute('href')).toBeNull()
    expect(screen.getByLabelText('下一頁').getAttribute('href')).toBe('/blog?page=2')
    unmount()
    render(<Pagination current={3} total={3} hrefFor={hrefFor} />)
    expect(screen.getByLabelText('下一頁').getAttribute('aria-disabled')).toBe('true')
    expect(screen.getByLabelText('下一頁').getAttribute('href')).toBeNull()
  })

  it('頁數很多時同樣有首頁、末頁與省略號（省略號不是連結）', () => {
    render(<Pagination current={10} total={30} hrefFor={hrefFor} />)
    expect(links().map(([label]) => label)).toEqual(['上一頁', '1', '9', '11', '30', '下一頁'])
    expect(screen.getAllByText('…')).toHaveLength(2)
  })

  it('沒給 hrefFor 的既有用法完全不變（仍是按鈕＋onChange，賣家後台等列表在用）', () => {
    render(<Pagination current={2} total={3} onChange={() => {}} />)
    expect(screen.queryAllByRole('link')).toHaveLength(0)
    expect(screen.getAllByRole('button')).toHaveLength(5)
  })
})
