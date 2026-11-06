'use client'

import ProductList from '@/components/product/ProductList'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

export default function ProductsPage() {
  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>商品管理</PageTitle>
            <PageDescription>
              管理您的商品資料，上下架商品
            </PageDescription>
          </div>
          <ProductList />
        </div>
      </main>
    </div>
  )
}