import type { Metadata } from 'next'
import ProductForm from '@/components/product/ProductForm'

export const metadata: Metadata = {
  title: '新增商品',
}

export default function NewProductPage() {
  return (
    <div>
      <main className="max-w-3xl">
        <div>
          <ProductForm />
        </div>
      </main>
    </div>
  )
}