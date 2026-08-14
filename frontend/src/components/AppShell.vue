<script setup lang="ts">
import { Bell, Bot, Compass, ListChecks, LogOut, Menu, PencilLine, ShieldCheck, UserRound, X } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getUnreadNoticeCount } from '../api/notices.ts'
import { authStore } from '../auth/auth.ts'
import { feedRouteQuery } from '../utils/feedPreferences.ts'

const router = useRouter()
const isMobileNavigationOpen = ref(false)
const unreadNoticeCount = ref(0)
let unreadTimer: number | undefined
const feedTarget = computed(() => ({ name: 'feed', query: feedRouteQuery() }))
const isAdmin = computed(() => authStore.state.user?.role === 1)
const adminLabel = '\u7ba1\u7406\u540e\u53f0'

async function refreshUnreadNoticeCount(): Promise<void> {
  try { unreadNoticeCount.value = await getUnreadNoticeCount() } catch { unreadNoticeCount.value = 0 }
}

onMounted(() => {
  void refreshUnreadNoticeCount()
  window.addEventListener('campuscircle:notices-read', refreshUnreadNoticeCount)
  unreadTimer = window.setInterval(() => void refreshUnreadNoticeCount(), 30000)
})
onBeforeUnmount(() => {
  if (unreadTimer) window.clearInterval(unreadTimer)
  window.removeEventListener('campuscircle:notices-read', refreshUnreadNoticeCount)
})

async function handleLogout(): Promise<void> {
  await authStore.logout()
  await router.replace({ name: 'login' })
}

function closeMobileNavigation(): void {
  isMobileNavigationOpen.value = false
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" :to="feedTarget" aria-label="UniNook 首页">
        <span class="brand-mark">U</span>
        <span>UniNook</span>
      </RouterLink>
      <div class="topbar-actions">
        <button
          class="icon-button mobile-menu"
          type="button"
          :aria-expanded="isMobileNavigationOpen"
          aria-controls="mobile-navigation"
          :aria-label="isMobileNavigationOpen ? '关闭导航' : '打开导航'"
          @click="isMobileNavigationOpen = !isMobileNavigationOpen"
        >
          <X v-if="isMobileNavigationOpen" :size="20" />
          <Menu v-else :size="20" />
        </button>
        <RouterLink class="user-summary" to="/profile" aria-label="进入个人主页">
          <span class="avatar" :class="{ 'avatar--image': authStore.state.user?.avatarUrl }">
            <img v-if="authStore.state.user?.avatarUrl" :src="authStore.state.user.avatarUrl" alt="" />
            <template v-else>{{ authStore.state.user?.nickname.slice(0, 1).toUpperCase() }}</template>
          </span>
          <span class="user-name">{{ authStore.state.user?.nickname }}</span>
        </RouterLink>
        <button class="icon-button" type="button" aria-label="退出登录" @click="handleLogout">
          <LogOut :size="18" />
        </button>
      </div>
    </header>

    <div class="app-body">
      <aside class="sidebar" aria-label="主导航">
        <nav>
          <RouterLink class="nav-item" :to="feedTarget">
            <Compass :size="19" />
            <span>校园动态</span>
          </RouterLink>
          <RouterLink class="nav-item" to="/posts/new"><PencilLine :size="19" /><span>发布讨论</span></RouterLink>
          <RouterLink class="nav-item" to="/assistant"><Bot :size="19" /><span>校园助手</span></RouterLink>
          <RouterLink class="nav-item" to="/questions"><ListChecks :size="19" /><span>问题追踪</span></RouterLink>
          <RouterLink class="nav-item" to="/notices" @click="refreshUnreadNoticeCount"><Bell :size="19" /><span>通知</span><span v-if="unreadNoticeCount" class="notice-badge">{{ unreadNoticeCount > 99 ? '99+' : unreadNoticeCount }}</span></RouterLink>
          <RouterLink class="nav-item" to="/profile"><UserRound :size="19" /><span>我的主页</span></RouterLink>
          <RouterLink v-if="isAdmin" class="nav-item" to="/admin"><ShieldCheck :size="19" /><span>{{ adminLabel }}</span></RouterLink>
        </nav>
      </aside>
      <main class="main-content">
        <RouterView />
      </main>
    </div>

    <div v-if="isMobileNavigationOpen" class="mobile-navigation-backdrop" @click="closeMobileNavigation">
      <aside id="mobile-navigation" class="mobile-navigation" aria-label="移动端主导航" @click.stop>
        <nav>
          <RouterLink class="nav-item" :to="feedTarget" @click="closeMobileNavigation">
            <Compass :size="19" />
            <span>校园动态</span>
          </RouterLink>
          <RouterLink class="nav-item" to="/posts/new" @click="closeMobileNavigation"><PencilLine :size="19" /><span>发布讨论</span></RouterLink>
          <RouterLink class="nav-item" to="/assistant" @click="closeMobileNavigation"><Bot :size="19" /><span>校园助手</span></RouterLink>
          <RouterLink class="nav-item" to="/questions" @click="closeMobileNavigation"><ListChecks :size="19" /><span>问题追踪</span></RouterLink>
          <RouterLink class="nav-item" to="/notices" @click="closeMobileNavigation(); refreshUnreadNoticeCount()"><Bell :size="19" /><span>通知</span><span v-if="unreadNoticeCount" class="notice-badge">{{ unreadNoticeCount > 99 ? '99+' : unreadNoticeCount }}</span></RouterLink>
          <RouterLink class="nav-item" to="/profile" @click="closeMobileNavigation"><UserRound :size="19" /><span>我的主页</span></RouterLink>
          <RouterLink v-if="isAdmin" class="nav-item" to="/admin" @click="closeMobileNavigation"><ShieldCheck :size="19" /><span>{{ adminLabel }}</span></RouterLink>
        </nav>
      </aside>
    </div>
  </div>
</template>
