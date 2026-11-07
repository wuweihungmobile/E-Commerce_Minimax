import type { Metadata } from 'next'
import ShippingTemplateList from '@/components/shipping/ShippingTemplateList'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '運費模板設定',
}

export default function ShippingTemplatesPage() {
  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>運費模板設定</PageTitle>
            <PageDescription>
              設定固定運費或滿額免運門檻，套用於商品訂單結帳計算
            </PageDescription>
          </div>
          <ShippingTemplateList />
        </div>
      </main>
    </div>
  )
}
