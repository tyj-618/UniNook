<script setup lang="ts">
import { ArrowRight, CircleAlert } from '@lucide/vue'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessageOf } from '../api/errors.ts'
import { updateCurrentUser } from '../api/users.ts'
import { authStore } from '../auth/auth.ts'

const route = useRoute()
const router = useRouter()
const nickname = ref(authStore.state.user?.nickname ?? '')
const errorMessage = ref('')
const isSubmitting = ref(false)

async function handleSubmit(): Promise<void> {
  const value = nickname.value.trim()
  if (!value) {
    errorMessage.value = '请输入昵称'
    return
  }

  errorMessage.value = ''
  isSubmitting.value = true
  try {
    const user = await updateCurrentUser({ nickname: value })
    authStore.updateUser(user)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/feed'
    await router.replace(user.schoolId ? redirect : { name: 'school-onboarding', query: { redirect } })
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '昵称保存失败，请稍后重试。')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <RouterLink class="brand" to="/login">
        <span class="brand-mark">U</span>
        <span>UniNook</span>
      </RouterLink>
      <div class="auth-intro">
        <p class="eyebrow">WELCOME TO THE CIRCLE</p>
        <h1>设置你的昵称</h1>
        <p>昵称会公开显示在帖子、评论和个人主页中；用户名仅用于登录。</p>
      </div>
      <form class="auth-form" @submit.prevent="handleSubmit">
        <label>
          昵称
          <input v-model="nickname" autocomplete="nickname" maxlength="32" required autofocus />
          <small>已为你生成默认昵称，也可以直接修改。</small>
        </label>
        <p v-if="errorMessage" class="form-error"><CircleAlert :size="16" />{{ errorMessage }}</p>
        <button class="primary-button auth-submit" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? '保存中...' : '确认昵称并继续' }}
          <ArrowRight v-if="!isSubmitting" :size="18" />
        </button>
      </form>
    </section>
  </main>
</template>
