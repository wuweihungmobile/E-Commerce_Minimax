import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// DEF-472（Sprint 333）：採購單建立表單的「供應商」「商品」下拉是建單的必填欄位（supplierId、品項的 listingId），不是可有可無的篩選器。
// 載入失敗時下拉只剩佔位選項，使用者無法建單，也不知道為什麼——原本只 console.error。
// 這裡守的是：失敗要明講是哪一個載入失敗並能重試（只重抓失敗的）；「載入失敗」與「真的沒有啟用中的供應商」要是兩種不同的畫面。

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/services/erp/supplier', () => ({ default: { listSuppliers: vi.fn() } }))
vi.mock('@/services/erp/purchaseOrder', () => ({
  default: { listListingOptions: vi.fn(), getPurchaseOrder: vi.fn() },
}))

import PurchaseOrderService from '@/services/erp/purchaseOrder'
import SupplierService from '@/services/erp/supplier'
import PurchaseOrderForm from './PurchaseOrderForm'

const supplier = {
  id: 's1',
  name: '好供應商',
  contactPerson: '',
  email: '',
  phone: '',
  address: '',
  status: 'ACTIVE',
  createdAt: '',
  updatedAt: '',
}
const listing = { id: 'l1', title: '手沖咖啡豆', basePrice: 100, currency: 'TWD', skus: [{ id: 'k1', skuCode: 'SKU-1', specName: null }] }
const order = {
  id: 'o1',
  poNumber: 'PO-1',
  supplierId: 's1',
  supplierName: '好供應商',
  status: 'DRAFT',
  totalAmount: 0,
  currency: 'TWD',
  expectedDeliveryDate: '',
  notes: '',
  items: [],
  createdAt: '',
  updatedAt: '',
}

const listSuppliers = vi.mocked(SupplierService.listSuppliers)
const listListingOptions = vi.mocked(PurchaseOrderService.listListingOptions)
const getPurchaseOrder = vi.mocked(PurchaseOrderService.getPurchaseOrder)

beforeEach(() => {
  listSuppliers.mockReset()
  listListingOptions.mockReset()
  getPurchaseOrder.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('採購單表單（建立模式）的供應商與商品下拉', () => {
  it('兩個清單都載入成功 → 供應商與商品下拉都有選項，沒有錯誤也沒有提示', async () => {
    listSuppliers.mockResolvedValue([supplier] as never)
    listListingOptions.mockResolvedValue([listing] as never)
    render(<PurchaseOrderForm mode="create" />)
    expect(await screen.findByRole('option', { name: '好供應商' })).toBeTruthy()
    expect(await screen.findByRole('option', { name: '手沖咖啡豆' })).toBeTruthy()
    expect(screen.queryByTestId('po-options-error')).toBeNull()
    expect(screen.queryByTestId('po-no-supplier-hint')).toBeNull()
  })

  it('供應商載入失敗 → 明講「載入供應商失敗」並提供「重新載入」；商品下拉不受影響；這不是「沒有供應商」', async () => {
    listSuppliers.mockRejectedValue(new Error('network down'))
    listListingOptions.mockResolvedValue([listing] as never)
    render(<PurchaseOrderForm mode="create" />)
    // 修復前：整頁沒有任何提示（找不到這個錯誤區塊），下拉只剩「請選擇供應商」
    const alert = await screen.findByTestId('po-options-error')
    expect(alert.textContent).toContain('載入供應商失敗')
    expect(alert.textContent).not.toContain('載入商品選項失敗')
    expect(screen.getByRole('button', { name: '重新載入' })).toBeTruthy()
    expect(await screen.findByRole('option', { name: '手沖咖啡豆' })).toBeTruthy()
    expect(screen.queryByTestId('po-no-supplier-hint'), '載入失敗被說成了「沒有啟用中的供應商」').toBeNull()
  })

  it('商品選項載入失敗 → 明講「載入商品選項失敗」；供應商下拉不受影響', async () => {
    listSuppliers.mockResolvedValue([supplier] as never)
    listListingOptions.mockRejectedValue(new Error('network down'))
    render(<PurchaseOrderForm mode="create" />)
    const alert = await screen.findByTestId('po-options-error')
    expect(alert.textContent).toContain('載入商品選項失敗')
    expect(alert.textContent).not.toContain('載入供應商失敗')
    expect(await screen.findByRole('option', { name: '好供應商' })).toBeTruthy()
  })

  it('兩個都失敗 → 同一個錯誤區塊列出兩個失敗，只有一個「重新載入」', async () => {
    listSuppliers.mockRejectedValue(new Error('network down'))
    listListingOptions.mockRejectedValue(new Error('network down'))
    render(<PurchaseOrderForm mode="create" />)
    await waitFor(() => {
      const text = screen.getByTestId('po-options-error').textContent ?? ''
      expect(text).toContain('載入供應商失敗')
      expect(text).toContain('載入商品選項失敗')
    })
    expect(screen.getAllByRole('button', { name: '重新載入' })).toHaveLength(1)
  })

  it('按「重新載入」只重抓失敗的那一個；成功後錯誤消失、下拉有選項', async () => {
    listSuppliers.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce([supplier] as never)
    listListingOptions.mockResolvedValue([listing] as never)
    render(<PurchaseOrderForm mode="create" />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByRole('option', { name: '好供應商' })).toBeTruthy()
    expect(listSuppliers).toHaveBeenCalledTimes(2)
    expect(listListingOptions, '成功的那個清單不該被重抓').toHaveBeenCalledTimes(1)
    expect(screen.queryByTestId('po-options-error')).toBeNull()
  })

  it('商品選項載入失敗後按「重新載入」→ 只重抓商品選項；成功後錯誤消失、商品下拉有選項', async () => {
    listSuppliers.mockResolvedValue([supplier] as never)
    listListingOptions.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce([listing] as never)
    render(<PurchaseOrderForm mode="create" />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByRole('option', { name: '手沖咖啡豆' })).toBeTruthy()
    expect(listListingOptions).toHaveBeenCalledTimes(2)
    expect(listSuppliers, '成功的那個清單不該被重抓').toHaveBeenCalledTimes(1)
    expect(screen.queryByTestId('po-options-error')).toBeNull()
  })

  it('兩個都失敗後按一次「重新載入」→ 兩個清單都重抓，成功後錯誤消失', async () => {
    listSuppliers.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce([supplier] as never)
    listListingOptions.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce([listing] as never)
    render(<PurchaseOrderForm mode="create" />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByRole('option', { name: '好供應商' })).toBeTruthy()
    expect(await screen.findByRole('option', { name: '手沖咖啡豆' })).toBeTruthy()
    expect(listSuppliers).toHaveBeenCalledTimes(2)
    expect(listListingOptions).toHaveBeenCalledTimes(2)
    expect(screen.queryByTestId('po-options-error')).toBeNull()
  })

  it('後端有給失敗訊息 → 接在「載入供應商失敗：」後面一起顯示（沒給才只顯示「載入供應商失敗」）', async () => {
    const serverError = Object.assign(new Error('Request failed with status code 503'), {
      response: { status: 503, data: { code: 'E-X', message: '資料庫忙碌中' } },
    })
    listSuppliers.mockRejectedValue(serverError)
    listListingOptions.mockResolvedValue([listing] as never)
    render(<PurchaseOrderForm mode="create" />)
    const alert = await screen.findByTestId('po-options-error')
    expect(alert.textContent).toContain('載入供應商失敗：資料庫忙碌中')
  })

  it('載入成功但沒有任何啟用中的供應商 → 顯示「沒有啟用中的供應商」與新增入口，而且不是載入失敗的錯誤', async () => {
    listSuppliers.mockResolvedValue([] as never)
    listListingOptions.mockResolvedValue([listing] as never)
    render(<PurchaseOrderForm mode="create" />)
    const hint = await screen.findByTestId('po-no-supplier-hint')
    expect(hint.textContent).toContain('沒有啟用中的供應商')
    expect(screen.getByRole('link', { name: '新增供應商' }).getAttribute('href')).toBe('/dashboard/erp/suppliers/new')
    expect(screen.queryByTestId('po-options-error'), '「沒有供應商」被當成了載入失敗').toBeNull()
  })
})

describe('採購單表單（其他模式）', () => {
  it('檢視模式不載入供應商與商品下拉，也不會出現這兩個載入的錯誤或提示', async () => {
    getPurchaseOrder.mockResolvedValue(order as never)
    render(<PurchaseOrderForm orderId="o1" mode="view" />)
    expect(await screen.findByText('採購訂單詳情')).toBeTruthy()
    expect(listSuppliers).not.toHaveBeenCalled()
    expect(listListingOptions).not.toHaveBeenCalled()
    expect(screen.queryByTestId('po-options-error')).toBeNull()
    expect(screen.queryByTestId('po-no-supplier-hint')).toBeNull()
  })
})
