import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

export interface Product {
  listingId: string
  tenantId: string
  title: string
  description: string
  category: string
  brand: string
  basePrice: number
  currency: string
  coverImageUrl: string
  status: string
  tags: string[]
  weightGrams: number
  dimensionsCm: string
  createdAt: string
  updatedAt: string
}

export interface ProductListItem {
  listingId: string
  title: string
  category: string
  brand: string
  basePrice: number
  currency: string
  coverImageUrl: string
  status: string
  createdAt: string
}

export interface ProductFilters {
  category?: string
  brand?: string
  keyword?: string
  page?: number
  size?: number
  sortBy?: string
  sortDir?: 'ASC' | 'DESC'
}

export interface CreateProductRequest {
  title: string
  description?: string
  category: string
  brand?: string
  basePrice: number
  coverImageUrl?: string
  tags?: string[]
  weightGrams?: number
  dimensionsCm?: string
}

export interface UpdateProductRequest extends CreateProductRequest {
  status?: string
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
  errors?: Array<{
    field: string
    message: string
    rejectedValue?: unknown
  }>
}

class ProductService {
  async getProducts(filters: ProductFilters = {}): Promise<PaginatedResponse<ProductListItem>> {
    const params = new URLSearchParams()
    if (filters.category) params.append('category', filters.category)
    if (filters.brand) params.append('brand', filters.brand)
    if (filters.keyword) params.append('keyword', filters.keyword)
    if (filters.page !== undefined) params.append('page', String(filters.page))
    if (filters.size !== undefined) params.append('size', String(filters.size ?? 20))
    if (filters.sortBy) params.append('sortBy', filters.sortBy)
    if (filters.sortDir) params.append('sortDir', filters.sortDir)

    const response = await apiClient.get<ApiResponse<PaginatedResponse<ProductListItem>>>(
      API_ENDPOINTS.products.list + '?' + params.toString()
    )
    return response.data.data
  }

  async getProduct(id: string): Promise<Product> {
    const response = await apiClient.get<ApiResponse<Product>>(
      API_ENDPOINTS.products.detail(id)
    )
    return response.data.data
  }

  async createProduct(request: CreateProductRequest): Promise<Product> {
    const response = await apiClient.post<ApiResponse<Product>>(
      API_ENDPOINTS.products.create,
      request
    )
    return response.data.data
  }

  async updateProduct(id: string, request: UpdateProductRequest): Promise<Product> {
    const response = await apiClient.put<ApiResponse<Product>>(
      API_ENDPOINTS.products.update(id),
      request
    )
    return response.data.data
  }

  async deleteProduct(id: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.products.delete(id))
  }
}

export default new ProductService()