import { describe, expect, it } from 'vitest'
import { cn } from './utils'

// components/ui 的每個元件都是 cn(配方 class, 呼叫端 className)。呼叫端要能「覆寫」配方（例如把 px-4 改成 px-2），
// 靠的是 tailwind-merge 讓後面的衝突 utility 取代前面的；如果只是字串相接，兩個 class 同時存在，
// 誰贏取決於 CSS 檔裡的出現順序，不是呼叫端的意圖。
describe('cn', () => {
  it('衝突的 utility 以後面的為準（呼叫端覆寫配方）', () => {
    expect(cn('px-4 py-2', 'px-2')).toBe('py-2 px-2')
  })

  it('不衝突的 utility 都保留', () => {
    expect(cn('flex', 'gap-2')).toBe('flex gap-2')
  })

  it('略過 false／undefined／null，支援陣列與物件寫法', () => {
    expect(cn('a', false && 'b', undefined, null, ['c'], { d: true, e: false })).toBe('a c d')
  })
})
