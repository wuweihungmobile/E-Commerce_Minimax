import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 AddressDto.Response / CreateRequest / UpdateRequest（Sprint 87，PRD §14.3.1 Phase 2-B）

export interface Address {
  id: string
  recipientName: string
  phone: string
  postalCode: string | null
  city: string
  district: string | null
  addressLine: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export interface AddressInput {
  recipientName: string
  phone: string
  postalCode?: string
  city: string
  district?: string
  addressLine: string
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

class AddressService {
  async list(): Promise<Address[]> {
    const response = await apiClient.get<ApiResponse<Address[]>>(API_ENDPOINTS.addresses.list)
    return response.data.data
  }

  async create(input: AddressInput): Promise<Address> {
    const response = await apiClient.post<ApiResponse<Address>>(API_ENDPOINTS.addresses.create, input)
    return response.data.data
  }

  async update(id: string, input: Partial<AddressInput>): Promise<Address> {
    const response = await apiClient.put<ApiResponse<Address>>(API_ENDPOINTS.addresses.update(id), input)
    return response.data.data
  }

  async remove(id: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.addresses.delete(id))
  }

  async setDefault(id: string): Promise<Address> {
    const response = await apiClient.put<ApiResponse<Address>>(API_ENDPOINTS.addresses.setDefault(id))
    return response.data.data
  }
}

export default new AddressService()
