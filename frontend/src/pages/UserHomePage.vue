<script setup lang="ts">
import { ArrowLeft, CalendarDays, FileText, Heart, MessageCircle, PencilLine, School, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessageOf } from '../api/errors.ts'
import { getMyComments, getMyLikes, getUserPosts, getUserProfile } from '../api/users.ts'
import { authStore } from '../auth/auth.ts'
import type { CampusScope, MyComment, MyLike, PostListItem, PublicUserProfile } from '../types/api.ts'
import { formatCompactDate, formatCompactDateTime } from '../utils/date.ts'
import { readFeedPreferences } from '../utils/feedPreferences.ts'

type ProfileTab = 'posts' | 'comments' | 'likes'

const route = useRoute()
const router = useRouter()
const profile = ref<PublicUserProfile | null>(null)
const posts = ref<PostListItem[]>([])
const comments = ref<MyComment[]>([])
const likes = ref<MyLike[]>([])
const isLoading = ref(true)
const errorMessage = ref('')
const activeTab = ref<ProfileTab>('posts')
const avatarPreviewVisible = ref(false)

const profileUserId = computed(() => {
  const rawId = Array.isArray(route.params.id) ? null : route.params.id
  return rawId ? Number(rawId) : authStore.state.user?.id ?? null
})
const isOwnProfile = computed(() => profileUserId.value !== null && profileUserId.value === authStore.state.user?.id)

const returnToPostTarget = computed(() => {
  const source = Array.isArray(route.query.source) ? null : route.query.source
  const rawPostId = Array.isArray(route.query.postId) ? null : route.query.postId
  const postId = Number(rawPostId)
  if (source !== 'post-detail' || !Number.isSafeInteger(postId) || postId <= 0) return null
  const preferences = readFeedPreferences()
  const rawScope = Array.isArray(route.query.scope) ? null : route.query.scope
  const rawSort = Array.isArray(route.query.sort) ? null : route.query.sort
  const validScopes = new Set<CampusScope>(['CAMPUS', 'UNIVERSITY', 'NEARBY_10', 'NEARBY_20', 'CITY'])
  const scope = validScopes.has(rawScope as CampusScope) ? rawScope as CampusScope : preferences.scope
  const sort = rawSort === 'hot' ? 'hot' : rawSort === 'latest' ? 'latest' : preferences.sort
  const rawCommentId = Array.isArray(route.query.commentId) ? null : route.query.commentId
  const commentId = Number(rawCommentId)
  return {
    name: 'post-detail',
    params: { id: postId },
    query: {
      source: 'profile-user',
      scope,
      sort,
      ...(Number.isSafeInteger(commentId) && commentId > 0 ? { commentId: String(commentId) } : {}),
    },
  }
})

function resolveTab(): ProfileTab {
  const rawTab = Array.isArray(route.query.tab) ? null : route.query.tab
  return rawTab === 'comments' || rawTab === 'likes' ? rawTab : 'posts'
}

async function loadProfile(): Promise<void> {
  const userId = profileUserId.value
  if (!userId || !Number.isSafeInteger(userId)) {
    errorMessage.value = '用户主页地址无效。'
    isLoading.value = false
    return
  }
  isLoading.value = true
  errorMessage.value = ''
  activeTab.value = resolveTab()
  try {
    const [profileData, postPage] = await Promise.all([getUserProfile(userId), getUserPosts(userId)])
    profile.value = profileData
    posts.value = postPage.records
    if (isOwnProfile.value) {
      const [commentPage, likePage] = await Promise.all([getMyComments(), getMyLikes()])
      comments.value = commentPage.records
      likes.value = likePage.records
    } else {
      comments.value = []
      likes.value = []
    }
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '个人主页暂时无法加载，请稍后重试。')
  } finally {
    isLoading.value = false
  }
}

function postTarget(postId: number, commentId?: number): Record<string, unknown> {
  return {
    name: 'post-detail',
    params: { id: postId },
    query: {
      source: 'profile',
      profileUserId: String(profileUserId.value),
      tab: activeTab.value,
      ...(commentId ? { commentId: String(commentId) } : {}),
    },
  }
}

function openSchoolChange(): void {
  void router.push({ name: 'school-onboarding', query: { from: 'profile' } })
}

watch(() => route.fullPath, () => void loadProfile(), { immediate: true })
</script>

<template>
  <section class="content-page user-home-page">
    <section v-if="isLoading" class="empty-feed"><h2>正在加载个人主页…</h2></section>
    <section v-else-if="errorMessage" class="empty-feed"><h2>加载失败</h2><p>{{ errorMessage }}</p><button class="primary-button" type="button" @click="loadProfile">重新加载</button></section>
    <template v-else-if="profile">
      <RouterLink v-if="returnToPostTarget" class="detail-back" :to="returnToPostTarget"><ArrowLeft :size="18" />返回讨论</RouterLink>
      <header class="profile-hero">
        <button v-if="profile.avatarUrl" class="profile-avatar-large profile-avatar-large--button" type="button" @click="avatarPreviewVisible = true">
          <img :src="profile.avatarUrl" :alt="`${profile.nickname} 的头像`" />
        </button>
        <div v-else class="profile-avatar-large"><span>{{ profile.nickname.slice(0, 1).toUpperCase() }}</span></div>
        <div class="profile-heading">
          <div class="profile-heading-top">
            <div><p class="eyebrow">{{ isOwnProfile ? 'MY PROFILE' : 'CAMPUS MEMBER' }}</p><h1>{{ profile.nickname }}</h1></div>
            <RouterLink v-if="isOwnProfile" class="text-button profile-settings-link" :to="{ name: 'profile-settings' }"><PencilLine :size="15" />资料设置</RouterLink>
          </div>
          <div class="profile-identifiers">
            <span><b>账号 ID</b>{{ profile.id }}</span>
            <span v-if="profile.username"><b>用户名</b>@{{ profile.username }}</span>
          </div>
          <div v-if="profile.schoolName" class="profile-campus-row">
            <p class="profile-campus">{{ profile.schoolName }}<template v-if="profile.campusName"> · {{ profile.campusName }}</template><template v-if="profile.schoolCity"> · {{ profile.schoolCity }}</template></p>
            <button v-if="isOwnProfile" class="profile-school-change" type="button" title="切换学校或校区" @click="openSchoolChange"><School :size="16" />切换校园校区</button>
          </div>
          <p v-if="profile.bio" class="profile-bio">{{ profile.bio }}</p>
          <p class="profile-joined"><CalendarDays :size="15" />加入 CampusCircle 于 {{ formatCompactDate(profile.createdAt) }}</p>
        </div>
      </header>

      <section class="profile-stats" aria-label="用户互动概览">
        <div><FileText :size="18" /><strong>{{ profile.postCount }}</strong><span>发布</span></div>
        <div><MessageCircle :size="18" /><strong>{{ profile.commentCount }}</strong><span>评论</span></div>
        <div><Heart :size="18" /><strong>{{ profile.likeCount }}</strong><span>点赞</span></div>
      </section>

      <div v-if="isOwnProfile" class="segmented-control profile-tabs" aria-label="个人记录">
        <button :class="{ active: activeTab === 'posts' }" type="button" @click="activeTab = 'posts'">我的帖子</button>
        <button :class="{ active: activeTab === 'comments' }" type="button" @click="activeTab = 'comments'">我的评论</button>
        <button :class="{ active: activeTab === 'likes' }" type="button" @click="activeTab = 'likes'">我的点赞</button>
      </div>

      <section v-if="activeTab === 'posts'" class="activity-section" aria-label="帖子列表">
        <h2>{{ isOwnProfile ? '我的帖子' : '发布的帖子' }}</h2>
        <p v-if="posts.length === 0" class="muted">暂时还没有发布内容。</p>
        <div v-else class="activity-list">
          <RouterLink v-for="item in posts" :key="item.id" class="activity-item" :to="postTarget(item.id)">
            <div class="activity-item-main"><p class="activity-meta">{{ item.category.name }} · {{ formatCompactDateTime(item.createdAt) }}</p><h3>{{ item.title }}</h3><p>{{ item.summary }}</p></div>
            <span class="activity-counts">{{ item.likeCount }} 赞 · {{ item.commentCount }} 评论</span>
          </RouterLink>
        </div>
      </section>

      <section v-else-if="activeTab === 'comments'" class="activity-section" aria-label="评论记录">
        <h2>我的评论</h2>
        <p v-if="comments.length === 0" class="muted">暂时还没有发表过评论。</p>
        <div v-else class="activity-list">
          <RouterLink v-for="item in comments" :key="item.id" class="activity-item" :to="postTarget(item.postId, item.id)">
            <div class="activity-item-main"><p class="activity-meta">{{ item.postTitle }} · {{ formatCompactDateTime(item.createdAt) }}</p><p>{{ item.content }}</p></div>
            <span class="activity-jump">查看原帖</span>
          </RouterLink>
        </div>
      </section>

      <section v-else-if="activeTab === 'likes'" class="activity-section" aria-label="点赞记录">
        <h2>我的点赞</h2>
        <p v-if="likes.length === 0" class="muted">暂时还没有点赞记录。</p>
        <div v-else class="activity-list">
          <RouterLink v-for="item in likes" :key="`${item.targetType}-${item.commentId ?? item.postId}`" class="activity-item" :to="postTarget(item.postId, item.commentId ?? undefined)">
            <div class="activity-item-main"><p class="activity-meta">{{ item.targetType === 'POST' ? '点赞了帖子' : '点赞了评论' }} · {{ formatCompactDateTime(item.createdAt) }}</p><h3>{{ item.postTitle }}</h3><p>{{ item.targetContent }}</p></div>
            <span class="activity-jump">查看原帖</span>
          </RouterLink>
        </div>
      </section>

    </template>
  </section>

  <div v-if="avatarPreviewVisible && profile?.avatarUrl" class="avatar-preview-backdrop" @click.self="avatarPreviewVisible = false">
    <section class="avatar-preview-dialog" role="dialog" aria-modal="true" aria-label="头像预览">
      <button class="avatar-preview-close" type="button" aria-label="关闭头像预览" @click="avatarPreviewVisible = false"><X :size="20" /></button>
      <img :src="profile.avatarUrl" :alt="`${profile.nickname} 的头像大图`" />
    </section>
  </div>
</template>
