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
