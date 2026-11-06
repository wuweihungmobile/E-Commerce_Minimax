import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { ConfirmDialogHost, confirmDialog } from './confirm-dialog'

// confirmDialog() 取代原生 confirm()：破壞性操作（刪除、取消訂單）的唯一安全網。
// 這裡守的是「答案不會答錯、不會答兩次、不會被別人的請求吃掉」——E2E-UIDS-05 在真實瀏覽器守取消／ESC／確定三條路，
// 這裡補它走不到的佇列語意。
describe('confirmDialog', () => {
  // confirmDialog 的佇列是模組層級的單例：某個案例失敗或逾時時，沒回答的請求會留到下一個案例，連鎖紅燈。
  // 每個案例結束都把還開著的對話框按「取消」，讓佇列清空（RTL 的 cleanup 在這之後才卸載 Host）。
  afterEach(() => {
    for (let guard = 0; guard < 10; guard++) {
      const cancel = screen.queryByTestId('confirm-dialog-cancel')
      if (!cancel) break
      act(() => {
        fireEvent.click(cancel)
      })
    }
  })

  const dialogText = () => screen.queryByTestId('confirm-dialog')?.textContent ?? null

  it('按「確定」→ 回 true；按「取消」→ 回 false', async () => {
    render(<ConfirmDialogHost />)

    let first!: Promise<boolean>
    act(() => {
      first = confirmDialog('確定要刪除嗎？')
    })
    expect(dialogText()).toContain('確定要刪除嗎？')
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))
    await expect(first).resolves.toBe(true)

    let second!: Promise<boolean>
    act(() => {
      second = confirmDialog('再問一次')
    })
    fireEvent.click(await screen.findByTestId('confirm-dialog-cancel'))
    await expect(second).resolves.toBe(false)
  })

  it('按 ESC 等同取消 → 回 false（不能因為關掉對話框就當成同意）', async () => {
    render(<ConfirmDialogHost />)
    let answer!: Promise<boolean>
    act(() => {
      answer = confirmDialog('確定要刪除嗎？')
    })
    fireEvent.keyDown(document, { key: 'Escape' })
    await expect(answer).resolves.toBe(false)
  })

  it('同時有兩個請求：依序一次顯示一個；第一個確定之後，第二個不會被「遲到的關閉事件」誤判成取消', async () => {
    render(<ConfirmDialogHost />)
    let first!: Promise<boolean>
    let second!: Promise<boolean>
    act(() => {
      first = confirmDialog('第一個問題')
      second = confirmDialog('第二個問題')
    })
    // 只顯示第一個
    expect(dialogText()).toContain('第一個問題')
    expect(dialogText()).not.toContain('第二個問題')

    // Radix 的確定鈕按下去後，還會再呼叫一次 onOpenChange(false)；此時第二個請求已經是目前的請求。
    // 如果關閉事件不比對「是哪個請求」，第二個問題會在使用者看到之前就被當成取消。
    fireEvent.click(screen.getByTestId('confirm-dialog-confirm'))
    await expect(first).resolves.toBe(true)

    const secondPending = await Promise.race([second.then(() => 'settled'), Promise.resolve('pending')])
    expect(secondPending).toBe('pending')
    expect(await screen.findByText('第二個問題')).toBeTruthy()

    fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))
    await expect(second).resolves.toBe(false)
  })

  it('破壞性請求的確認鈕外觀和一般確認不同，並使用自訂文案', async () => {
    render(<ConfirmDialogHost />)

    // 不斷言 class 名（換皮會改），只斷言「破壞性」確實讓確認鈕長得不一樣
    let normal!: Promise<boolean>
    act(() => {
      normal = confirmDialog('一般確認')
    })
    const normalClass = (await screen.findByTestId('confirm-dialog-confirm')).className
    fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))
    await expect(normal).resolves.toBe(false)

    let answer!: Promise<boolean>
    act(() => {
      answer = confirmDialog({ message: '刪除後無法復原', confirmLabel: '刪除', cancelLabel: '先不要', destructive: true })
    })
    const confirm = await screen.findByTestId('confirm-dialog-confirm')
    expect(confirm.textContent).toBe('刪除')
    expect(screen.getByTestId('confirm-dialog-cancel').textContent).toBe('先不要')
    expect(confirm.className).not.toBe(normalClass)
    fireEvent.click(screen.getByTestId('confirm-dialog-cancel'))
    await expect(answer).resolves.toBe(false)
  })
})
