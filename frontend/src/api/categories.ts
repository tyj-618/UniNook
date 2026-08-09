import { apiClient } from './client.ts'
import type { ApiResponse, Category } from '../types/api.ts'

export async function getCategories(): Promise<Category[]> {
  const response = await apiClient.get<ApiResponse<Category[]>>('/categories')
  return response.data.data
}
