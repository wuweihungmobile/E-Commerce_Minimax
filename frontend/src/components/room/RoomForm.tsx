'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import RoomService, { CreateRoomRequest, UpdateRoomRequest, Room } from '@/services/room'
import AuthService from '@/services/auth'

interface FormErrors {
  title?: string
  location?: string
  basePrice?: string
  general?: string
}

interface RoomFormProps {
  roomId?: string
}

export default function RoomForm({ roomId }: RoomFormProps) {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [fetchLoading, setFetchLoading] = useState(!!roomId)
  const [errors, setErrors] = useState<FormErrors>({})
  const [generalError, setGeneralError] = useState<string | null>(null)

  const [formData, setFormData] = useState<CreateRoomRequest>({
    title: '',
    description: '',
    location: '',
    latitude: undefined,
    longitude: undefined,
    basePrice: 0,
    coverImageUrl: '',
    tags: [],
    maxGuests: undefined,
    amenities: [],
    checkInTime: undefined,
    checkOutTime: undefined,
    roomCount: undefined,
    openUntilDate: undefined,
    bookingWindowDays: undefined,
  })

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    if (roomId) {
      fetchRoom()
    }
  }, [router, roomId])

  const fetchRoom = async () => {
    setFetchLoading(true)
    try {
      const room = await RoomService.getRoom(roomId!)
      setFormData({
        title: room.title,
        description: room.description || '',
        location: room.location,
        latitude: room.latitude || undefined,
        longitude: room.longitude || undefined,
        basePrice: room.basePrice,
        coverImageUrl: room.coverImageUrl || '',
        tags: room.tags || [],
        maxGuests: room.maxGuests || undefined,
        amenities: room.amenities || [],
        checkInTime: room.checkInTime || undefined,
        checkOutTime: room.checkOutTime || undefined,
        roomCount: room.roomCount || undefined,
        openUntilDate: room.openUntilDate || undefined,
        bookingWindowDays: room.bookingWindowDays || undefined,
      })
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setGeneralError(axiosErr.response?.data?.message || '載入房源失敗')
      } else {
        setGeneralError('載入房源失敗')
      }
    } finally {
      setFetchLoading(false)
    }
  }

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {}

    if (!formData.title.trim()) {
      newErrors.title = '請輸入房源名稱'
    } else if (formData.title.length > 200) {
      newErrors.title = '房源名稱不能超過 200 字元'
    }

    if (!formData.location.trim()) {
      newErrors.location = '請輸入房源地點'
    }

    if (!formData.basePrice || formData.basePrice <= 0) {
      newErrors.basePrice = '請輸入有效的房源價格'
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
      if (roomId) {
        const request: UpdateRoomRequest = {
          ...formData,
          status: 'ACTIVE',
        }
        await RoomService.updateRoom(roomId, request)
      } else {
        await RoomService.createRoom(formData)
      }
      router.push('/dashboard/rooms')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string; errors?: Array<{ field: string; message: string }> } } }
        const serverErrors = axiosErr.response?.data?.errors
        if (serverErrors && Array.isArray(serverErrors)) {
          const fieldErrors: FormErrors = {}
          serverErrors.forEach((error) => {
            if (error.field === 'title') fieldErrors.title = error.message
            else if (error.field === 'location') fieldErrors.location = error.message
            else if (error.field === 'basePrice') fieldErrors.basePrice = error.message
            else fieldErrors.general = error.message
          })
          setErrors(fieldErrors)
        } else {
          const message = axiosErr.response?.data?.message || '儲存失敗'
          if (message.includes('BOOKING_ENABLED')) {
            setGeneralError('您的店鋪尚未啟用訂房功能，請聯繫管理員開啟「訂房功能」')
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

  const [clearingOpenWindow, setClearingOpenWindow] = useState(false)

  /**
   * 清除開放窗（Sprint 57 AI-2202f）：清空輸入框後送出並不會清除既有值
   * （undefined 欄位會被 JSON.stringify 省略，等同「未提供」），故編輯既有房源時
   * 改呼叫專屬清除端點；新增房源尚未持久化，僅需清空本地表單欄位。
   */
  const handleClearOpenWindow = async () => {
    if (!roomId) {
      handleChange('openUntilDate', undefined)
      handleChange('bookingWindowDays', undefined)
      return
    }
    setClearingOpenWindow(true)
    setGeneralError(null)
    try {
      await RoomService.clearOpenWindow(roomId)
      handleChange('openUntilDate', undefined)
      handleChange('bookingWindowDays', undefined)
    } catch {
      setGeneralError('清除開放窗設定失敗')
    } finally {
      setClearingOpenWindow(false)
    }
  }

  const handleChange = (field: keyof CreateRoomRequest, value: string | number | string[] | undefined) => {
    setFormData((prev) => ({ ...prev, [field]: value }))
    if (errors[field as keyof FormErrors]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }))
    }
  }

  const amenitiesList = ['WiFi', '空调', '游泳池', '健身房', '停车场', '厨房', '洗衣机', '烘干机', '电视', '吹风机']

  const toggleAmenity = (amenity: string) => {
    const current = formData.amenities || []
    if (current.includes(amenity)) {
      handleChange('amenities', current.filter((a) => a !== amenity))
    } else {
      handleChange('amenities', [...current, amenity])
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
        <CardTitle>{roomId ? '編輯房源' : '新增房源'}</CardTitle>
        <CardDescription>
          {roomId ? '修改房源資料' : '填寫房源資料以建立新房源'}
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
                房源名稱 <span className="text-red-500">*</span>
              </Label>
              <Input
                id="title"
                value={formData.title}
                onChange={(e) => handleChange('title', e.target.value)}
                placeholder="請輸入房源名稱"
                className={errors.title ? 'border-red-500' : ''}
              />
              {errors.title && <p className="text-red-500 text-sm mt-1">{errors.title}</p>}
            </div>

            <div>
              <Label htmlFor="location">
                地點 <span className="text-red-500">*</span>
              </Label>
              <Input
                id="location"
                value={formData.location}
                onChange={(e) => handleChange('location', e.target.value)}
                placeholder="請輸入房源地點"
                className={errors.location ? 'border-red-500' : ''}
              />
              {errors.location && <p className="text-red-500 text-sm mt-1">{errors.location}</p>}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="latitude">緯度</Label>
                <Input
                  id="latitude"
                  type="number"
                  step="any"
                  value={formData.latitude || ''}
                  onChange={(e) => handleChange('latitude', e.target.value ? parseFloat(e.target.value) : undefined)}
                  placeholder="25.0330"
                />
              </div>
              <div>
                <Label htmlFor="longitude">經度</Label>
                <Input
                  id="longitude"
                  type="number"
                  step="any"
                  value={formData.longitude || ''}
                  onChange={(e) => handleChange('longitude', e.target.value ? parseFloat(e.target.value) : undefined)}
                  placeholder="121.5654"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <Label htmlFor="basePrice">
                  每晚價格 <span className="text-red-500">*</span>
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
                <Label htmlFor="maxGuests">最大入住人數</Label>
                <Input
                  id="maxGuests"
                  type="number"
                  min="1"
                  value={formData.maxGuests || ''}
                  onChange={(e) => handleChange('maxGuests', e.target.value ? parseInt(e.target.value) : undefined)}
                  placeholder="2"
                />
              </div>

              <div>
                <Label htmlFor="roomCount">房間數量</Label>
                <Input
                  id="roomCount"
                  type="number"
                  min="1"
                  value={formData.roomCount || ''}
                  onChange={(e) => handleChange('roomCount', e.target.value ? parseInt(e.target.value) : undefined)}
                  placeholder="1"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="checkInTime">入住時間</Label>
                <Input
                  id="checkInTime"
                  type="time"
                  value={formData.checkInTime || ''}
                  onChange={(e) => handleChange('checkInTime', e.target.value || undefined)}
                />
              </div>
              <div>
                <Label htmlFor="checkOutTime">退房時間</Label>
                <Input
                  id="checkOutTime"
                  type="time"
                  value={formData.checkOutTime || ''}
                  onChange={(e) => handleChange('checkOutTime', e.target.value || undefined)}
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="openUntilDate">開放預訂至（選填）</Label>
                <Input
                  id="openUntilDate"
                  type="date"
                  value={formData.openUntilDate || ''}
                  onChange={(e) => handleChange('openUntilDate', e.target.value || undefined)}
                />
                <p className="text-xs text-gray-500 mt-1">開放至某固定日；未填 = 無限制</p>
              </div>
              <div>
                <Label htmlFor="bookingWindowDays">開放未來天數（選填）</Label>
                <Input
                  id="bookingWindowDays"
                  type="number"
                  min="1"
                  value={formData.bookingWindowDays ?? ''}
                  onChange={(e) => handleChange('bookingWindowDays', e.target.value ? parseInt(e.target.value) : undefined)}
                  placeholder="90"
                />
                <p className="text-xs text-gray-500 mt-1">開放未來 N 天（滾動）；與截止日兩者取最早生效</p>
              </div>
            </div>
            {(formData.openUntilDate || formData.bookingWindowDays) && (
              <div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={clearingOpenWindow}
                  onClick={handleClearOpenWindow}
                >
                  {clearingOpenWindow ? '清除中...' : '清除開放窗設定（恢復無限制）'}
                </Button>
              </div>
            )}

            <div>
              <Label htmlFor="coverImageUrl">房源圖片 URL</Label>
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
              <Label>設施</Label>
              <div className="flex flex-wrap gap-2 mt-2">
                {amenitiesList.map((amenity) => (
                  <button
                    key={amenity}
                    type="button"
                    onClick={() => toggleAmenity(amenity)}
                    className={`px-3 py-1 text-sm rounded-full border ${
                      (formData.amenities || []).includes(amenity)
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-gray-50 text-gray-700 border-gray-300'
                    }`}
                  >
                    {amenity}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <Label htmlFor="description">房源描述</Label>
              <textarea
                id="description"
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                placeholder="請輸入房源描述..."
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
          <Link href="/dashboard/rooms">
            <Button type="button" variant="outline">
              取消
            </Button>
          </Link>
        </CardFooter>
      </form>
    </Card>
  )
}