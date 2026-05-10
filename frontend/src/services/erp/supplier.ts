import apiClient from '@/lib/axios'
import { API_ENDPOINTS_ERP } from '@/lib/api_erp'

// ========== Types ==========

export interface SupplierDto {
  id: string
  name: string
  contactPerson: string
  email: string
  phone: string
  address: string
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
  updatedAt: string
}

export interface SupplierCreateRequest {
  name: string
  contactPerson?: string
  email?: string
  phone?: string
  address?: string
}

export interface SupplierUpdateRequest {
  name?: string
  contactPerson?: string
  email?: string
  phone?: string
  address?: string
  status?: 'ACTIVE' | 'INACTIVE'
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

class SupplierService {
  async listSuppliers(status?: string): Promise<SupplierDto[]> {
    const params = status ? `?status=${status}` : ''
    const response = await apiClient.get<ApiResponse<SupplierDto[]>>(
      API_ENDPOINTS_ERP.suppliers.list + params
    )
    return response.data.data
  }

  async getSupplier(id: string): Promise<SupplierDto> {
    const response = await apiClient.get<ApiResponse<SupplierDto>>(
      API_ENDPOINTS_ERP.suppliers.detail(id)
    )
    return response.data.data
  }

  async createSupplier(request: SupplierCreateRequest): Promise<SupplierDto> {
    const response = await apiClient.post<ApiResponse<SupplierDto>>(
      API_ENDPOINTS_ERP.suppliers.create,
      request
    )
    return response.data.data
  }

  async updateSupplier(id: string, request: SupplierUpdateRequest): Promise<SupplierDto> {
    const response = await apiClient.put<ApiResponse<SupplierDto>>(
      API_ENDPOINTS_ERP.suppliers.update(id),
      request
    )
    return response.data.data
  }
}

export default new SupplierService()