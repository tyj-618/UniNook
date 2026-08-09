import type { AxiosRequestConfig } from 'axios'
import { apiClient } from './client.ts'
import type {
  ApiResponse,
  CreateCommentResponse,
  CreatePostResponse,
  LikeResponse,
  PageResponse,
  CampusScope,
  PostComment,
  PostDetail,
  PostListItem,
} from '../types/api.ts'

export interface FeedQuery {
  page?: number
  size?: number
  scope: CampusScope
  sort: 'latest' | 'hot'
}

export interface PostCommentsQuery {
  page?: number
  size?: number
  radiusKm: number
  focusCommentId?: number
}

export async function getNearbyFeed(
  query: FeedQuery,
  config?: Pick<AxiosRequestConfig, 'signal'>,
): Promise<PageResponse<PostListItem>> {
  const response = await apiClient.get<ApiResponse<PageResponse<PostListItem>>>('/posts/feed', { params: query, ...config })
  return response.data.data!
}

export async function getPostDetail(
  postId: number,
  radiusKm: number,
  config?: Pick<AxiosRequestConfig, 'signal'>,
): Promise<PostDetail> {
  const response = await apiClient.get<ApiResponse<PostDetail>>(`/posts/${postId}`, {
    params: { radiusKm },
    ...config,
  })
  return response.data.data!
}

export async function getPostComments(
  postId: number,
  query: PostCommentsQuery,
  config?: Pick<AxiosRequestConfig, 'signal'>,
): Promise<PageResponse<PostComment>> {
  const response = await apiClient.get<ApiResponse<PageResponse<PostComment>>>(`/posts/${postId}/comments`, {
    params: query,
    ...config,
  })
  return response.data.data!
}

export async function createPostComment(
  postId: number,
  radiusKm: number,
  content: string,
  clientUserId: number,
  parentCommentId?: number,
  answerQuestionId?: number,
): Promise<CreateCommentResponse> {
  const response = await apiClient.post<ApiResponse<CreateCommentResponse>>(
    `/posts/${postId}/comments`,
    { content, parentCommentId, answerQuestionId },
    {
      params: { radiusKm },
      headers: { 'X-CampusCircle-User-Id': String(clientUserId) },
    },
  )
  return response.data.data!
}

export async function deletePostComment(commentId: number): Promise<void> {
  await apiClient.delete<ApiResponse<boolean>>(`/comments/${commentId}`)
}

export async function likeComment(commentId: number, radiusKm: number): Promise<LikeResponse> {
  const response = await apiClient.post<ApiResponse<LikeResponse>>(`/comments/${commentId}/like`, null, {
    params: { radiusKm },
  })
  return response.data.data!
}

export async function unlikeComment(commentId: number, radiusKm: number): Promise<LikeResponse> {
  const response = await apiClient.delete<ApiResponse<LikeResponse>>(`/comments/${commentId}/like`, {
    params: { radiusKm },
  })
  return response.data.data!
}

export async function likePost(postId: number, radiusKm: number): Promise<LikeResponse> {
  const response = await apiClient.post<ApiResponse<LikeResponse>>(`/posts/${postId}/like`, null, {
    params: { radiusKm },
  })
  return response.data.data!
}

export async function unlikePost(postId: number, radiusKm: number): Promise<LikeResponse> {
  const response = await apiClient.delete<ApiResponse<LikeResponse>>(`/posts/${postId}/like`, {
    params: { radiusKm },
  })
  return response.data.data!
}

export async function createPost(categoryId: number, title: string, content: string): Promise<CreatePostResponse> {
  const response = await apiClient.post<ApiResponse<CreatePostResponse>>('/posts', { categoryId, title, content })
  return response.data.data!
}

export async function updatePost(postId: number, categoryId: number, title: string, content: string): Promise<void> {
  await apiClient.put<ApiResponse<boolean>>(`/posts/${postId}`, { categoryId, title, content })
}

export async function deletePost(postId: number): Promise<void> {
  await apiClient.delete<ApiResponse<boolean>>(`/posts/${postId}`)
}
