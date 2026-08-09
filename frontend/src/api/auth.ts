import { apiClient } from './client.ts'
import type { ApiResponse, LoginResponse, RegisterRequest, RegisterResponse, SessionUser } from '../types/api.ts'

export interface LoginRequest {
  username: string
  password: string
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', request)
  return response.data.data!
}

export async function register(request: RegisterRequest): Promise<RegisterResponse> {
  const response = await apiClient.post<ApiResponse<RegisterResponse>>('/auth/register', request)
  return response.data.data!
}

export async function logout(): Promise<void> {
  await apiClient.post<ApiResponse<boolean>>('/auth/logout')
}

export async function getCurrentUser(): Promise<SessionUser> {
  const response = await apiClient.get<ApiResponse<SessionUser>>('/users/me')
  return response.data.data!
}
