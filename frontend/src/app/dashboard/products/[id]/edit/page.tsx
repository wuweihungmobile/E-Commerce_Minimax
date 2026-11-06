'use client'

import { useParams } from 'next/navigation'
import ProductForm from '@/components/product/ProductForm'
import SkuManager from '@/components/product/SkuManager'

export default function EditProductPage() {
  const params = useParams()

  return (
    <div>
      <main className="max-w-3xl">
        <div className="space-y-6">
          <ProductForm productId={params.id as string} />
          <SkuManager listingId={params.id as string} />
        </div>
      </main>
    </div>
  )
}