'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

interface TenantApplyFormData {
  storeName: string
  businessType: string
  contactEmail: string
  contactPhone: string
}

interface FormErrors {
  storeName?: string
  businessType?: string
  contactEmail?: string
  contactPhone?: string
}

export default function TenantApplyForm() {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [formData, setFormData] = useState<TenantApplyFormData>({
    storeName: '',
    businessType: '',
    contactEmail: '',
    contactPhone: '',
  })
  const [errors, setErrors] = useState<FormErrors>({})

  const businessTypes = [
    { value: 'RETAIL', label: '零售' },
    { value: 'WHOLESALE', label: '批發' },
    { value: 'F&B', label: '餐飲' },
    { value: 'SERVICE', label: '服務業' },
    { value: 'MANUFACTURING', label: '製造業' },
    { value: 'OTHER', label: '其他' },
  ]

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {}

    // Store name validation
    if (!formData.storeName.trim()) {
      newErrors.storeName = '請輸入店鋪名稱'
    } else if (formData.storeName.length < 2) {
      newErrors.storeName = '店鋪名稱至少需要 2 個字元'
    } else if (formData.storeName.length > 100) {
      newErrors.storeName = '店鋪名稱不能超過 100 個字元'
    }

    // Business type validation
    if (!formData.businessType) {
      newErrors.businessType = '請選擇營業類型'
    }

    // Email validation
    if (!formData.contactEmail.trim()) {
      newErrors.contactEmail = '請輸入聯絡信箱'
    } else {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      if (!emailRegex.test(formData.contactEmail)) {
        newErrors.contactEmail = '請輸入有效的信箱格式'
      }
    }

    // Phone validation
    if (!formData.contactPhone.trim()) {
      newErrors.contactPhone = '請輸入聯絡電話'
    } else {
      const phoneRegex = /^[\d\-\+\(\)\s]{8,20}$/
      if (!phoneRegex.test(formData.contactPhone)) {
        newErrors.contactPhone = '請輸入有效的電話號碼'
      }
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
    // Clear error when user starts typing
    if (errors[name as keyof FormErrors]) {
      setErrors((prev) => ({ ...prev, [name]: undefined }))
    }
  }

  const handleBusinessTypeChange = (value: string) => {
    setFormData((prev) => ({ ...prev, businessType: value }))
    if (errors.businessType) {
      setErrors((prev) => ({ ...prev, businessType: undefined }))
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!validateForm()) {
      return
    }

    setLoading(true)

    try {
      await apiClient.post(API_ENDPOINTS.tenants.apply, formData)
      // 申請成功後導向至店鋪列表頁
      router.push('/dashboard/tenants')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '申請失敗，請稍後再試')
      } else {
        setError('申請失敗，請稍後再試')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="w-full max-w-lg mx-auto">
      <CardHeader>
        <CardTitle>開店申請</CardTitle>
        <CardDescription>
          填寫以下表單申請開店，我們會在 1-3 個工作天內完成審核
        </CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit}>
        <CardContent className="space-y-4">
          {error && (
            <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md text-sm">
              {error}
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="storeName">
              店鋪名稱 <span className="text-error">*</span>
            </Label>
            <Input
              id="storeName"
              name="storeName"
              type="text"
              placeholder="請輸入店鋪名稱"
              value={formData.storeName}
              onChange={handleInputChange}
              className={errors.storeName ? 'border-error' : ''}
              disabled={loading}
            />
            {errors.storeName && (
              <p className="text-error text-xs">{errors.storeName}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="businessType">
              營業類型 <span className="text-error">*</span>
            </Label>
            <Select
              value={formData.businessType}
              onValueChange={handleBusinessTypeChange}
              disabled={loading}
            >
              <SelectTrigger className={errors.businessType ? 'border-error' : ''}>
                <SelectValue placeholder="請選擇營業類型" />
              </SelectTrigger>
              <SelectContent>
                {businessTypes.map((type) => (
                  <SelectItem key={type.value} value={type.value}>
                    {type.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.businessType && (
              <p className="text-error text-xs">{errors.businessType}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="contactEmail">
              聯絡信箱 <span className="text-error">*</span>
            </Label>
            <Input
              id="contactEmail"
              name="contactEmail"
              type="email"
              placeholder="example@company.com"
              value={formData.contactEmail}
              onChange={handleInputChange}
              className={errors.contactEmail ? 'border-error' : ''}
              disabled={loading}
            />
            {errors.contactEmail && (
              <p className="text-error text-xs">{errors.contactEmail}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="contactPhone">
              聯絡電話 <span className="text-error">*</span>
            </Label>
            <Input
              id="contactPhone"
              name="contactPhone"
              type="tel"
              placeholder="+886-912345678"
              value={formData.contactPhone}
              onChange={handleInputChange}
              className={errors.contactPhone ? 'border-error' : ''}
              disabled={loading}
            />
            {errors.contactPhone && (
              <p className="text-error text-xs">{errors.contactPhone}</p>
            )}
          </div>
        </CardContent>
        <CardFooter className="flex justify-between">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.back()}
            disabled={loading}
          >
            取消
          </Button>
          <Button type="submit" disabled={loading}>
            {loading ? '提交中...' : '提交申請'}
          </Button>
        </CardFooter>
      </form>
    </Card>
  )
}
