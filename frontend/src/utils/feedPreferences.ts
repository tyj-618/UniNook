import { ref } from 'vue'
import type { CampusScope } from '../types/api.ts'

export type FeedSort = 'latest' | 'hot'

export interface FeedPreferences {
  scope: CampusScope
  sort: FeedSort
}

const storageKey = 'campuscircle.feed-preferences'
const defaultPreferences: FeedPreferences = { scope: 'NEARBY_10', sort: 'latest' }
const validScopes = new Set<CampusScope>(['CAMPUS', 'UNIVERSITY', 'NEARBY_10', 'NEARBY_20', 'CITY'])
const preferencesState = ref<FeedPreferences>({ ...defaultPreferences })
let hasLoadedPreferences = false

function loadPreferences(): FeedPreferences {
  if (hasLoadedPreferences) return preferencesState.value
  hasLoadedPreferences = true
  try {
    const value = JSON.parse(window.localStorage.getItem(storageKey) ?? '{}') as Partial<FeedPreferences>
    preferencesState.value = {
      scope: value.scope && validScopes.has(value.scope) ? value.scope : defaultPreferences.scope,
      sort: value.sort === 'hot' ? 'hot' : defaultPreferences.sort,
    }
  } catch {
    preferencesState.value = { ...defaultPreferences }
  }
  return preferencesState.value
}

export function readFeedPreferences(): FeedPreferences {
  return loadPreferences()
}

export function saveFeedPreferences(preferences: FeedPreferences): void {
  hasLoadedPreferences = true
  preferencesState.value = { ...preferences }
  window.localStorage.setItem(storageKey, JSON.stringify(preferences))
}

export function feedRouteQuery(): Record<string, string> {
  const preferences = readFeedPreferences()
  return { scope: preferences.scope, sort: preferences.sort }
}
