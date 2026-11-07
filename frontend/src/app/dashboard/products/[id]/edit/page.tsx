import type { Metadata } from 'next'
import ProductForm from '@/components/product/ProductForm'
import SkuManager from '@/components/product/SkuManager'

export const metadata: Metadata = {
  title: '編輯商品',
}

export default async function EditProductPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params

  return (
    <div>
      <main className="max-w-3xl">
        <div className="space-y-6">
          <ProductForm productId={id} />
          <SkuManager listingId={id} />
        </div>
      </main>
    </div>
  )
}