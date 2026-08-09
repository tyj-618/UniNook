import { computed, reactive } from 'vue'
import { getCurrentUser, login as requestLogin, logout as requestLogout } from '../api/auth.ts'
import { refreshAccessToken } from '../api/client.ts'
import { clearStoredUser, readAccessToken, readStoredUser, setAccessToken, storeUser } from './session.ts'
import type { LoginResponse, SessionUser } from '../types/api.ts'

const state = reactive({
  token: readAccessToken() as string | null,
  user: readStoredUser() as SessionUser | null,
  restored: false,
})

let restorePromise: Promise<void> | null = null

function reset(): void {
  clearStoredUser()
  state.token = null
  state.user = null
  state.restored = true
}

function updateUser(user: SessionUser): void {
  state.user = user
  storeUser(user)
}

async function restore(): Promise<void> {
  if (state.restored) {
    return
  }
  if (restorePromise) {
    return restorePromise
  }

  restorePromise = refreshAccessToken()
    .then((token) => {
      state.token = token
      return getCurrentUser()
    })
    .then((user) => {
      updateUser(user)
    })
    .catch(reset)
    .finally(() => {
      state.restored = true
      restorePromise = null
    })
  return restorePromise
}

async function login(username: string, password: string): Promise<void> {
  const response: LoginResponse = await requestLogin({ username, password })
  setAccessToken(response.token)
  state.token = response.token
  storeUser(response.user)
  state.user = await getCurrentUser()
  state.restored = true
  storeUser(state.user)
}

async function logout(): Promise<void> {
  try {
    await requestLogout()
  } finally {
    reset()
  }
}

window.addEventListener('campuscircle:auth-expired', reset)
window.addEventListener('campuscircle:token-refreshed', ((event: CustomEvent<string>) => {
  state.token = event.detail
}) as EventListener)

export const authStore = {
  state,
  isAuthenticated: computed(() => Boolean(state.user && state.token)),
  restore,
  login,
  logout,
  reset,
  updateUser,
}
