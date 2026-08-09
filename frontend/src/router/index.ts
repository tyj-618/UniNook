import { createRouter, createWebHistory } from 'vue-router'
import { authStore } from '../auth/auth.ts'
import AppShell from '../components/AppShell.vue'
import FeedPage from '../pages/FeedPage.vue'
import AssistantPage from '../pages/AssistantPage.vue'
import LoginPage from '../pages/LoginPage.vue'
import NoticePage from '../pages/NoticePage.vue'
import NotFoundPage from '../pages/NotFoundPage.vue'
import PostDetailPage from '../pages/PostDetailPage.vue'
import CreatePostPage from '../pages/CreatePostPage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import UserHomePage from '../pages/UserHomePage.vue'
import RegisterPage from '../pages/RegisterPage.vue'
import SchoolOnboardingPage from '../pages/SchoolOnboardingPage.vue'
import QuestionTrackingPage from '../pages/QuestionTrackingPage.vue'
import QuestionAnswerListPage from '../pages/QuestionAnswerListPage.vue'
import NicknameOnboardingPage from '../pages/NicknameOnboardingPage.vue'

const scrollStorageKey = 'campuscircle.route-scroll-positions'

function readScrollPositions(): Record<string, number> {
  try {
    return JSON.parse(window.sessionStorage.getItem(scrollStorageKey) ?? '{}') as Record<string, number>
  } catch {
    return {}
  }
}

function saveScrollPosition(path: string): void {
  const positions = readScrollPositions()
  positions[path] = window.scrollY
  window.sessionStorage.setItem(scrollStorageKey, JSON.stringify(positions))
}

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (to.name === 'post-detail' && typeof to.query.commentId === 'string') {
      return { left: 0, top: 0 }
    }
    if (savedPosition) return savedPosition
    const savedTop = readScrollPositions()[to.fullPath]
    return { left: 0, top: savedTop ?? 0 }
  },
  routes: [
    { path: '/login', name: 'login', component: LoginPage, meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: RegisterPage, meta: { guestOnly: true } },
    { path: '/onboarding/nickname', name: 'nickname-onboarding', component: NicknameOnboardingPage, meta: { requiresAuth: true } },
    { path: '/onboarding/school', name: 'school-onboarding', component: SchoolOnboardingPage, meta: { requiresAuth: true } },
    {
      path: '/',
      component: AppShell,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/feed' },
        { path: 'feed', name: 'feed', component: FeedPage },
        { path: 'posts/new', name: 'post-create', component: CreatePostPage },
        { path: 'posts/:id', name: 'post-detail', component: PostDetailPage },
        { path: 'profile', name: 'profile', component: UserHomePage },
        { path: 'settings/profile', name: 'profile-settings', component: ProfilePage },
        { path: 'users/:id(\\d+)', name: 'user-profile', component: UserHomePage },
        { path: 'notices', name: 'notices', component: NoticePage },
        { path: 'assistant', name: 'assistant', component: AssistantPage },
        { path: 'questions', name: 'questions', component: QuestionTrackingPage },
        { path: 'questions/:questionId(\\d+)/answers', name: 'question-answer-list', component: QuestionAnswerListPage },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundPage },
  ],
})

router.beforeEach(async (to, from) => {
  if (from.fullPath) saveScrollPosition(from.fullPath)
  await authStore.restore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAuth && authStore.state.user?.nicknameSetupRequired && to.name !== 'nickname-onboarding') {
    return { name: 'nickname-onboarding', query: { redirect: to.fullPath } }
  }

  if (to.name === 'nickname-onboarding' && !authStore.state.user?.nicknameSetupRequired) {
    return authStore.state.user?.schoolId ? { name: 'feed' } : { name: 'school-onboarding' }
  }

  if (to.meta.requiresAuth
      && !authStore.state.user?.schoolId
      && to.name !== 'school-onboarding'
      && to.name !== 'nickname-onboarding') {
    return { name: 'school-onboarding' }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated.value) {
    return { name: 'feed' }
  }

  return true
})
