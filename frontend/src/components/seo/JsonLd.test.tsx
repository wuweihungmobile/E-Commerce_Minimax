import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { JsonLd, jsonLdText } from './JsonLd'

// 結構化資料裡有店家與使用者輸入的文字（商品名稱、說明、文章標題）。JSON.stringify 不處理 HTML：
// 內容含 </script> 會提前結束標籤並注入腳本。這組測試守的是「輸出的 HTML 無論資料是什麼都不能被注入」，與資料的 JSON 意義不變。
describe('JsonLd：輸出安全', () => {
  const hostile = '</script><script>alert(1)</script><!-- <b>x</b>'

  it('輸出的文字不含任何 `<`（惡意內容無法提前結束 script 標籤），但解析回來的 JSON 與原資料完全相同', () => {
    const text = jsonLdText({ name: hostile, nested: { items: [hostile] } })
    expect(text).not.toContain('<')
    expect(JSON.parse(text)).toEqual({ name: hostile, nested: { items: [hostile] } })
  })

  it('渲染出的元素是 application/ld+json 資料區塊；整個 DOM 裡只有這一個 script，沒有被注入的第二個', () => {
    const { container } = render(<JsonLd data={{ '@type': 'Product', name: hostile }} />)
    const scripts = container.querySelectorAll('script')
    expect(scripts).toHaveLength(1)
    expect(scripts[0].getAttribute('type')).toBe('application/ld+json')
    expect(JSON.parse(scripts[0].textContent ?? '').name).toBe(hostile)
  })

  it('U+2028／U+2029（舊 JavaScript 引擎當成換行）也被跳脫', () => {
    const text = jsonLdText({ name: 'a\u2028b\u2029c' })
    expect(text).not.toMatch(/[\u2028\u2029]/)
    expect(JSON.parse(text).name).toBe('a\u2028b\u2029c')
  })

  it('資料是 null（資料不足、不該輸出）→ 什麼都不渲染', () => {
    const { container } = render(<JsonLd data={null} />)
    expect(container.innerHTML).toBe('')
  })
})
