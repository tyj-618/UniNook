<script setup lang="ts">
import { ArrowRight, CircleAlert } from '@lucide/vue'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authStore } from '../auth/auth.ts'
import { errorMessageOf } from '../api/errors.ts'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

async function handleSubmit(): Promise<void> {
  errorMessage.value = ''
  isSubmitting.value = true
  try {
    await authStore.login(username.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/feed'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '登录失败，请稍后重试。')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <RouterLink class="brand" to="/login">
        <span class="brand-mark">C</span>
        <span>CampusCircle</span>
      </RouterLink>
      <div class="auth-intro">
        <p class="eyebrow">CAMPUS COMMUNITY</p>
        <h1>回到你的校园圈</h1>
        <p>查看附近高校正在发生的讨论。</p>
      </div>
      <form class="auth-form" @submit.prevent="handleSubmit">
        <label>
          用户名
          <input v-model="username" autocomplete="username" required />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password" required />
        </label>
        <p v-if="errorMessage" class="form-error"><CircleAlert :size="16" />{{ errorMessage }}</p>
        <button class="primary-button auth-submit" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? '登录中...' : '登录' }}
          <ArrowRight v-if="!isSubmitting" :size="18" />
        </button>
      </form>
      <p class="auth-switch">还没有账号？<RouterLink to="/register">创建账号</RouterLink></p>
    </section>
  </main>
</template>
