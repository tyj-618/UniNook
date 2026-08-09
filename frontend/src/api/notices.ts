import { apiClient } from './client.ts'
import type { ApiResponse, Notice, PageResponse } from '../types/api.ts'

export async function getNotices(): Promise<PageResponse<Notice>> {
  const response = await apiClient.get<ApiResponse<PageResponse<Notice>>>('/notices', { params: { page: 1, size: 30 } })
  return response.data.data
}

export async function getUnreadNoticeCount(): Promise<number> {
  const response = await apiClient.get<ApiResponse<{ count: number }>>('/notices/unread-count')
  return response.data.data.count
}

export async function markNoticeRead(noticeId: number): Promise<void> {
  await apiClient.put<ApiResponse<boolean>>(`/notices/${noticeId}/read`)
}

export async function markAllNoticesRead(): Promise<void> {
  await apiClient.put<ApiResponse<boolean>>('/notices/read-all')
}

export async function clearReadNotices(): Promise<void> {
  await apiClient.delete<ApiResponse<{ updatedCount: number }>>('/notices/read')
}
