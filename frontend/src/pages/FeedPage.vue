<script setup lang="ts">
import axios from 'axios'
import { Eye, Heart, MapPinned, MessageCircle, PenLine, RefreshCw } from '@lucide/vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessageOf } from '../api/errors.ts'
import { getNearbyFeed } from '../api/posts.ts'
import type { CampusScope, PostListItem } from '../types/api.ts'
import { formatCompactDateTime } from '../utils/date.ts'
import { readFeedPreferences, saveFeedPreferences } from '../utils/feedPreferences.ts'

const scopeOptions: Array<{ value: CampusScope; label: string }> = [
  { value: 'CAMPUS', label: '同校区' },
  { value: 'UNIVERSITY', label: '同校' },
  { value: 'NEARBY_10', label: '10 km' },
  { value: 'NEARBY_20', label: '20 km' },
  { value: 'CITY', label: '同市' },
]
const route = useRoute()
const router = useRouter()
const scope = ref<CampusScope>('NEARBY_10')
const sort = ref<'latest' | 'hot'>('latest')
const posts = ref<PostListItem[]>([])
const isLoading = ref(true)
const isLoadingMore = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)
const errorMessage = ref('')
let activeRequest: AbortController | null = null
const hasMore = computed(() => currentPage.value < totalPages.value)

async function loadFirstPage(): Promise<void> {
  activeRequest?.abort()
  const request = new AbortController()
  activeRequest = request
  isLoading.value = true
  errorMessage.value = ''

  try {
    const page = await getNearbyFeed(
      { scope: scope.value, sort: sort.value, page: 1, size: 10 },
      { signal: request.signal },
    )
    if (activeRequest === request) {
      posts.value = page.records
      currentPage.value = page.page
      totalPages.value = page.records.length < page.size ? page.page : page.pages
    }
  } catch (error) {
    if (!axios.isCancel(error) && activeRequest === request) {
      errorMessage.value = errorMessageOf(error, '校园动态暂时无法加载，请稍后重试。')
    }
  } finally {
    if (activeRequest === request) {
      isLoading.value = false
      activeRequest = null
    }
  }
}

async function loadMore(): Promise<void> {
  if (isLoading.value || isLoadingMore.value || !hasMore.value) return
  isLoadingMore.value = true
  errorMessage.value = ''

  const nextPage = currentPage.value + 1
  try {
    const page = await getNearbyFeed({ scope: scope.value, sort: sort.value, page: nextPage, size: 10 })
    posts.value = [...posts.value, ...page.records]
    currentPage.value = page.page
    totalPages.value = page.records.length < page.size ? page.page : page.pages
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '加载更多失败，请稍后重试。')
  } finally {
    isLoadingMore.value = false
  }
}

function readScope(): CampusScope {
  const raw = Array.isArray(route.query.scope) ? null : route.query.scope
  return scopeOptions.some((option) => option.value === raw) ? raw as CampusScope : readFeedPreferences().scope
}

function readSort(): 'latest' | 'hot' {
  const raw = Array.isArray(route.query.sort) ? null : route.query.sort
  return raw === 'hot' || raw === 'latest' ? raw : readFeedPreferences().sort
}

async function changeFeedOptions(nextScope: CampusScope, nextSort: 'latest' | 'hot'): Promise<void> {
  if (scope.value === nextScope && sort.value === nextSort) return
  scope.value = nextScope
  sort.value = nextSort
  saveFeedPreferences({ scope: nextScope, sort: nextSort })
  await router.replace({ query: { ...route.query, scope: nextScope, sort: nextSort } })
}

function selectScope(nextScope: CampusScope): void {
  void changeFeedOptions(nextScope, sort.value)
}

function selectSort(nextSort: 'latest' | 'hot'): void {
  void changeFeedOptions(scope.value, nextSort)
}

watch(
  () => [route.query.scope, route.query.sort],
  () => {
    scope.value = readScope()
    sort.value = readSort()
    saveFeedPreferences({ scope: scope.value, sort: sort.value })
    void loadFirstPage()
  },
  { immediate: true },
)
onBeforeUnmount(() => activeRequest?.abort())
</script>

<template>
  <section class="content-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">NEARBY CAMPUS FEED</p>
        <h1>校园动态</h1>
        <p class="muted">按距离查看你所在学校及附近高校的讨论。</p>
      </div>
      <div class="page-actions"><RouterLink class="icon-button" :to="{ name: 'post-create' }" aria-label="发布讨论"><PenLine :size="18" /></RouterLink><button class="icon-button" type="button" aria-label="刷新校园动态" @click="loadFirstPage"><RefreshCw :size="18" /></button></div>
    </div>

    <section class="feed-toolbar" aria-label="动态筛选">
      <div class="segmented-control" aria-label="查看范围">
        <button v-for="option in scopeOptions" :key="option.value" :class="{ active: option.value === scope }" type="button" @click="selectScope(option.value)">
          {{ option.label }}
        </button>
      </div>
      <div class="segmented-control" aria-label="排序方式">
        <button :class="{ active: sort === 'latest' }" type="button" @click="selectSort('latest')">最新</button>
        <button :class="{ active: sort === 'hot' }" type="button" @click="selectSort('hot')">热门</button>
      </div>
    </section>

    <section v-if="isLoading" class="empty-feed"><h2>正在加载校园动态</h2></section>
    <section v-else-if="errorMessage" class="empty-feed">
      <h2>加载失败</h2>
      <p>{{ errorMessage }}</p>
      <button class="primary-button" type="button" @click="loadFirstPage">重新加载</button>
    </section>
    <section v-else-if="posts.length === 0" class="empty-feed">
      <MapPinned :size="24" />
      <h2>这个范围内还没有动态</h2>
      <p>换一个距离范围，或成为第一个发起讨论的人。</p>
    </section>
    <section v-else class="post-list" aria-label="帖子列表">
      <article v-for="post in posts" :key="post.id" class="post-card">
          <div class="post-meta">
            <span>{{ post.school.name }}<template v-if="post.school.campusName"> · {{ post.school.campusName }}</template></span>
            <span>{{ post.category.name }}</span>
            <span>{{ formatCompactDateTime(post.createdAt) }}</span>
          </div>
          <RouterLink class="post-card-author" :to="{ name: 'user-profile', params: { id: post.author.id } }">
            <span class="comment-avatar" :class="{ 'comment-avatar--image': post.author.avatarUrl }">
              <img v-if="post.author.avatarUrl" :src="post.author.avatarUrl" alt="" />
              <template v-else>{{ post.author.nickname.slice(0, 1).toUpperCase() }}</template>
            </span>
            <span>{{ post.author.nickname }}</span>
          </RouterLink>
          <RouterLink class="post-card-link" :to="{ name: 'post-detail', params: { id: post.id }, query: { scope, sort } }">
            <h2>{{ post.title }}</h2>
            <p>{{ post.summary }}</p>
          </RouterLink>
          <div class="post-stats" aria-label="帖子数据">
            <span><Eye :size="16" />{{ post.viewCount }}</span>
            <span><Heart :size="16" />{{ post.likeCount }}</span>
            <span><MessageCircle :size="16" />{{ post.commentCount }}</span>
          </div>
      </article>
    </section>
    <div v-if="!isLoading && !errorMessage && posts.length" class="feed-more">
      <button v-if="hasMore" class="secondary-button" type="button" :disabled="isLoadingMore" @click="loadMore">
        {{ isLoadingMore ? '加载中…' : '加载更多' }}
      </button>
      <p v-else class="feed-more__end">已经到底了</p>
    </div>
  </section>
</template>
