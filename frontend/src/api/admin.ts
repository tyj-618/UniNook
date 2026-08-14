import { apiClient } from './client.ts'
import type {
  AdminActionLogItem,
  AdminFeedbackStatsResponse,
  AdminPostListItem,
  AdminReportListItem,
  AdminUserListItem,
  ApiResponse,
  PageResponse,
} from '../types/api.ts'

export interface AdminListQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number
}

export async function getAdminPosts(query: AdminListQuery): Promise<PageResponse<AdminPostListItem>> {
  const response = await apiClient.get<ApiResponse<PageResponse<AdminPostListItem>>>('/admin/posts', { params: query })
  return response.data.data!
}

export async function getAdminUsers(query: AdminListQuery): Promise<PageResponse<AdminUserListItem>> {
  const response = await apiClient.get<ApiResponse<PageResponse<AdminUserListItem>>>('/admin/users', { params: query })
  return response.data.data!
}

export async function getAdminActionLogs(page = 1, size = 20): Promise<PageResponse<AdminActionLogItem>> {
  const response = await apiClient.get<ApiResponse<PageResponse<AdminActionLogItem>>>('/admin/action-logs', {
    params: { page, size },
  })
  return response.data.data!
}

export async function getAdminReports(page = 1, size = 20, status?: string): Promise<PageResponse<AdminReportListItem>> {
  const response = await apiClient.get<ApiResponse<PageResponse<AdminReportListItem>>>('/admin/reports', {
    params: { page, size, status },
  })
  return response.data.data!
}

export async function processAdminReport(reportId: number, status: 'PROCESSED' | 'REJECTED', adminNote?: string): Promise<void> {
  await apiClient.post<ApiResponse<boolean>>(`/admin/reports/${reportId}/process`, { status, adminNote })
}

export async function getAdminFeedbackStats(): Promise<AdminFeedbackStatsResponse> {
  const response = await apiClient.get<ApiResponse<AdminFeedbackStatsResponse>>('/admin/feedback-stats')
  return response.data.data!
}

export async function hideAdminPost(postId: number): Promise<void> {
  await apiClient.put<ApiResponse<boolean>>(`/admin/posts/${postId}/hide`)
}

export async function restoreAdminPost(postId: number): Promise<void> {
  await apiClient.put<ApiResponse<boolean>>(`/admin/posts/${postId}/restore`)
}

export async function disableAdminUser(userId: number): Promise<void> {
  await apiClient.put<ApiResponse<boolean>>(`/admin/users/${userId}/disable`)
}

export async function enableAdminUser(userId: number): Promise<void> {
  await apiClient.put<ApiResponse<boolean>>(`/admin/users/${userId}/enable`)
}

export async function rebuildAdminPostIndex(): Promise<number> {
  const response = await apiClient.post<ApiResponse<number>>('/admin/search/posts/reindex')
  return response.data.data!
}
