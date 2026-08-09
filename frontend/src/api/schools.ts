import { apiClient } from './client.ts'
import type { ApiResponse, School } from '../types/api.ts'

export async function searchSchools(keyword: string): Promise<School[]> {
  const response = await apiClient.get<ApiResponse<School[]>>('/schools/search', { params: { keyword, limit: 10 } })
  return response.data.data
}

export async function getSchoolProvinces(): Promise<string[]> {
  const response = await apiClient.get<ApiResponse<string[]>>('/schools/provinces')
  return response.data.data
}

export async function getSchoolCities(province: string): Promise<string[]> {
  const response = await apiClient.get<ApiResponse<string[]>>('/schools/cities', { params: { province } })
  return response.data.data
}

export async function getCampuses(province: string, city: string, keyword = ''): Promise<School[]> {
  const response = await apiClient.get<ApiResponse<School[]>>('/schools/campuses', {
    params: { province, city, keyword: keyword || undefined, limit: 100 },
  })
  return response.data.data
}
