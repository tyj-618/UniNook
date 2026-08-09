import axios, { type AxiosRequestConfig } from 'axios'
import { clearStoredUser, readAccessToken, setAccessToken } from '../auth/session.ts'
import { ApiBusinessError, isApiResponse } from './errors.ts'

interface RefreshTokenResponse {
  token: string
  expiresIn: number
}

interface RetryableRequestConfig extends AxiosRequestConfig {
  _authRetry?: boolean
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15_000,
  withCredentials: true,
})

const refreshClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15_000,
  withCredentials: true,
})

let refreshPromise: Promise<string> | null = null

export function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = refreshClient
      .post<{ code: number; message: string; data: RefreshTokenResponse }>('/auth/refresh')
      .then((response) => {
        if (!isApiResponse(response.data) || response.data.code !== 0 || !response.data.data?.token) {
          throw new Error('刷新登录状态失败')
        }
        const token = response.data.data.token
        setAccessToken(token)
        window.dispatchEvent(new CustomEvent<string>('campuscircle:token-refreshed', { detail: token }))
        return token
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

apiClient.interceptors.request.use((config) => {
  const token = readAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    if (isApiResponse(response.data) && response.data.code !== 0) {
      return Promise.reject(new ApiBusinessError(response.data.code, response.data.message))
    }
    return response
  },
  async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const request = error.config as RetryableRequestConfig | undefined
      if (request && !request._authRetry && request.url !== '/auth/refresh') {
        request._authRetry = true
        try {
          const token = await refreshAccessToken()
          request.headers = request.headers ?? {}
          request.headers.Authorization = `Bearer ${token}`
          return apiClient.request(request)
        } catch {
          clearStoredUser()
          window.dispatchEvent(new Event('campuscircle:auth-expired'))
        }
      } else {
        clearStoredUser()
        window.dispatchEvent(new Event('campuscircle:auth-expired'))
      }
    }
    if (axios.isAxiosError(error) && isApiResponse(error.response?.data)) {
      return Promise.reject(new ApiBusinessError(error.response.data.code, error.response.data.message))
    }
    return Promise.reject(error)
  },
)
