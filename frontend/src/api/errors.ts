import axios from 'axios'
import type { ApiResponse } from '../types/api.ts'

export class ApiBusinessError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiBusinessError'
    this.code = code
  }
}

export function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return Boolean(
    value
      && typeof value === 'object'
      && 'code' in value
      && 'message' in value
      && typeof (value as ApiResponse<unknown>).code === 'number',
  )
}

export function errorMessageOf(error: unknown, fallback: string): string {
  if (error instanceof ApiBusinessError && error.message) {
    return error.message
  }
  if (axios.isAxiosError(error) && isApiResponse(error.response?.data)) {
    return error.response.data.message || fallback
  }
  return fallback
}
