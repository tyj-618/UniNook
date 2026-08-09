import { apiClient } from './client.ts'
import type { ApiResponse, MyComment, MyLike, PageResponse, PostListItem, PublicUserProfile, SchoolChangeQuota, SessionUser } from '../types/api.ts'

export interface UpdateProfileRequest {
  nickname?: string
  bio?: string | null
  schoolId?: number
}

export interface AvatarUploadResponse {
  avatarUrl: string
}

export async function updateCurrentUser(request: UpdateProfileRequest): Promise<SessionUser> {
  const response = await apiClient.put<ApiResponse<SessionUser>>('/users/me', request)
  return response.data.data
}

export async function getSchoolChangeQuota(): Promise<SchoolChangeQuota> {
  const response = await apiClient.get<ApiResponse<SchoolChangeQuota>>('/users/me/school-change-quota')
  return response.data.data!
}

export async function uploadAvatar(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await apiClient.post<ApiResponse<AvatarUploadResponse>>('/users/me/avatar', formData)
  return response.data.data!.avatarUrl
}

export async function getUserProfile(userId: number): Promise<PublicUserProfile> {
  const response = await apiClient.get<ApiResponse<PublicUserProfile>>(`/users/${userId}`)
  return response.data.data!
}

export async function getUserPosts(userId: number, page = 1, size = 20): Promise<PageResponse<PostListItem>> {
  const response = await apiClient.get<ApiResponse<PageResponse<PostListItem>>>(`/users/${userId}/posts`, {
    params: { page, size },
  })
  return response.data.data!
}

export async function getMyComments(page = 1, size = 20): Promise<PageResponse<MyComment>> {
  const response = await apiClient.get<ApiResponse<PageResponse<MyComment>>>('/users/me/comments', {
    params: { page, size },
  })
  return response.data.data!
}

export async function getMyLikes(page = 1, size = 20): Promise<PageResponse<MyLike>> {
  const response = await apiClient.get<ApiResponse<PageResponse<MyLike>>>('/users/me/likes', {
    params: { page, size },
  })
  return response.data.data!
}
