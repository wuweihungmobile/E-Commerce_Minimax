'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import ProductService, { CreateProductRequest, UpdateProductRequest, Product } from '@/services/product'
import AuthService from '@/services/auth'

interface FormErrors {
  title?: string
  category?: string
  basePrice?: string
  general?: string
}

interface ProductFormProps {
  productId?: string
}

export default function ProductForm({ productId }: ProductFormProps) {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [fetchLoading, setFetchLoading] = useState(!!productId)
  const [errors, setErrors] = useState<FormErrors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)

  const [formData, setFormData] = useState<CreateProductRequest>({
    title: '',
    description: '',
    category: '',
    brand: '',
    basePrice: 0,
    coverImageUrl: '',
    tags: [],
    weightGrams: undefined,
    dimensionsCm: '',
  })

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    if (productId) {
      fetchProduct()
    }
  }, [router, productId])

  const fetchProduct = async () => {
    setFetchLoading(true)
    try {
      const product = await ProductService.getProduct(productId!)
      setFormData({
        title: product.title,
        description: product.description || '',
        category: product.category,
        brand: product.brand || '',
        basePrice: product.basePrice,
        coverImageUrl: product.coverImageUrl || '',
        tags: product.tags || [],
        weightGrams: product.weightGrams || undefined,
        dimensionsCm: product.dimensionsCm || '',
      })
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setGeneralError(axiosErr.response?.data?.message || '載入商品失敗')
      } else {
        setGeneralError('載入商品失敗')
      }
    } finally {
      setFetchLoading(false)
    }
  }

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {}

    if (!formData.title.trim()) {
      newErrors.title = '請輸入商品名稱'
    } else if (formData.title.length > 200) {
      newErrors.title = '商品名稱不能超過 200 字元'
    }

    if (!formData.category) {
      newErrors.category = '請選擇商品類別'
    }

    if (!formData.basePrice || formData.basePrice <= 0) {
      newErrors.basePrice = '請輸入有效的商品價格'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setGeneralError(null)

    if (!validateForm()) return

    setLoading(true)
    try {
      if (productId) {
        const request: UpdateProductRequest = {
          ...formData,
          status: 'ACTIVE',
        }
        await ProductService.updateProduct(productId, request)
      } else {
        await ProductService.createProduct(formData)
      }
      router.push('/dashboard/products')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string; errors?: Array<{ field: string; message: string }> } } }
        const serverErrors = axiosErr.response?.data?.errors
        if (serverErrors && Array.isArray(serverErrors)) {
          const fieldErrors: FormErrors = {}
          serverErrors.forEach((error) => {
            if (error.field === 'title') fieldErrors.title = error.message
            else if (error.field === 'category') fieldErrors.category = error.message
            else if (error.field === 'basePrice') fieldErrors.basePrice = error.message
            else fieldErrors.general = error.message
          })
          setErrors(fieldErrors)
        } else {
          const message = axiosErr.response?.data?.message || '儲存失敗'
          if (message.includes('RETAIL_ENABLED')) {
            setGeneralError('您的店鋪尚未啟用商品功能，請聯繫管理員開啟「零售功能」')
          } else {
            setGeneralError(message)
          }
        }
      } else {
        setGeneralError('儲存失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleChange = (field: keyof CreateProductRequest, value: string | number | string[] | undefined) => {
    setFormData((prev) => ({ ...prev, [field]: value }))
    if (errors[field as keyof FormErrors]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }))
    }
  }

  if (fetchLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{productId ? '編輯商品' : '新增商品'}</CardTitle>
        <CardDescription>
          {productId ? '修改商品資料' : '填寫商品資料以建立新商品'}
        </CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit}>
        <CardContent className="space-y-6">
          {generalError && (
            <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
              {generalError}
            </div>
          )}

          <div className="space-y-4">
            <div>
              <Label htmlFor="title">
                商品名稱 <span className="text-red-500">*</span>
              </Label>
              <Input
                id="title"
                value={formData.title}
                onChange={(e) => handleChange('title', e.target.value)}
                placeholder="請輸入商品名稱"
                className={errors.title ? 'border-red-500' : ''}
              />
              {errors.title && <p className="text-red-500 text-sm mt-1">{errors.title}</p>}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="category">
                  類別 <span className="text-red-500">*</span>
                </Label>
                <Select value={formData.category} onValueChange={(v) => handleChange('category', v)}>
                  <SelectTrigger className={errors.category ? 'border-red-500' : ''}>
                    <SelectValue placeholder="請選擇類別" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ELECTRONICS">電子產品</SelectItem>
                    <SelectItem value="FASHION">時尚服飾</SelectItem>
                    <SelectItem value="HOME">居家生活</SelectItem>
                    <SelectItem value="FOOD">食品飲料</SelectItem>
                    <SelectItem value="BEAUTY">美妝保養</SelectItem>
                    <SelectItem value="SPORTS">運動休閒</SelectItem>
                    <SelectItem value="BOOKS">圖書文具</SelectItem>
                    <SelectItem value="OTHER">其他</SelectItem>
                  </SelectContent>
                </Select>
                {errors.category && <p className="text-red-500 text-sm mt-1">{errors.category}</p>}
              </div>

              <div>
                <Label htmlFor="brand">品牌</Label>
                <Input
                  id="brand"
                  value={formData.brand}
                  onChange={(e) => handleChange('brand', e.target.value)}
                  placeholder="請輸入品牌名稱"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="basePrice">
                  價格 <span className="text-red-500">*</span>
                </Label>
                <Input
                  id="basePrice"
                  type="number"
                  min="0"
                  step="0.01"
                  value={formData.basePrice}
                  onChange={(e) => handleChange('basePrice', parseFloat(e.target.value) || 0)}
                  placeholder="0.00"
                  className={errors.basePrice ? 'border-red-500' : ''}
                />
                {errors.basePrice && <p className="text-red-500 text-sm mt-1">{errors.basePrice}</p>}
              </div>

              <div>
                <Label htmlFor="weightGrams">重量 (公克)</Label>
                <Input
                  id="weightGrams"
                  type="number"
                  min="1"
                  value={formData.weightGrams || ''}
                  onChange={(e) => handleChange('weightGrams', e.target.value ? parseInt(e.target.value) : undefined)}
                  placeholder="請輸入重量"
                />
              </div>
            </div>

            <div>
              <Label htmlFor="coverImageUrl">商品圖片 URL</Label>
              <Input
                id="coverImageUrl"
                value={formData.coverImageUrl}
                onChange={(e) => handleChange('coverImageUrl', e.target.value)}
                placeholder="https://example.com/image.jpg"
              />
              {formData.coverImageUrl && (
                <img
                  src={formData.coverImageUrl}
                  alt="預覽"
                  className="mt-2 w-32 h-32 object-cover rounded-md border"
                  onError={(e) => {
                    (e.target as HTMLImageElement).style.display = 'none'
                  }}
                />
              )}
            </div>

            <div>
              <Label htmlFor="dimensionsCm">尺寸 (長x寬x高 cm)</Label>
              <Input
                id="dimensionsCm"
                value={formData.dimensionsCm}
                onChange={(e) => handleChange('dimensionsCm', e.target.value)}
                placeholder="30x20x10"
              />
            </div>

            <div>
              <Label htmlFor="description">商品描述</Label>
              <textarea
                id="description"
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                placeholder="請輸入商品描述..."
                rows={4}
                className="w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <Label htmlFor="tags">標籤 (用逗號分隔)</Label>
              <Input
                id="tags"
                value={formData.tags?.join(', ') || ''}
                onChange={(e) => {
                  const tags = e.target.value
                    .split(',')
                    .map((t) => t.trim())
                    .filter((t) => t)
                  handleChange('tags', tags)
                }}
                placeholder="標籤1, 標籤2, 標籤3"
              />
            </div>
          </div>
        </CardContent>
        <CardFooter className="flex gap-2">
          <Button type="submit" disabled={loading}>
            {loading ? '儲存中...' : '儲存'}
          </Button>
          <Link href="/dashboard/products">
            <Button type="button" variant="outline">
              取消
            </Button>
          </Link>
        </CardFooter>
      </form>
    </Card>
  )
}