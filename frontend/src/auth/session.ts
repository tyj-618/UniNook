import type { SessionUser } from '../types/api.ts'

const userStorageKey = 'campuscircle.session.user'

let accessToken: string | null = null

export function readStoredUser(): SessionUser | null {
  const rawUser = window.localStorage.getItem(userStorageKey)
  if (!rawUser) {
    return null
  }

  try {
    return JSON.parse(rawUser) as SessionUser
  } catch {
    clearStoredUser()
    return null
  }
}

export function readAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function storeUser(user: SessionUser): void {
  window.localStorage.setItem(userStorageKey, JSON.stringify(user))
}

export function clearStoredUser(): void {
  accessToken = null
  window.localStorage.removeItem(userStorageKey)
}
