<script setup lang="ts">
import { ArrowRight, CircleAlert } from '@lucide/vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/auth.ts'
import { errorMessageOf } from '../api/errors.ts'

const router = useRouter()
const username = ref('')
const password = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

async function handleSubmit(): Promise<void> {
  errorMessage.value = ''
  isSubmitting.value = true
  try {
    await register({ username: username.value.trim(), password: password.value })
    await router.replace({ name: 'login' })
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '注册失败，请稍后重试。')
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
        <p class="eyebrow">JOIN THE CIRCLE</p>
        <h1>创建校园账号</h1>
        <p>先创建登录账号，昵称会在首次登录时设置。</p>
      </div>
      <form class="auth-form" @submit.prevent="handleSubmit">
        <label>
          用户名
          <input v-model="username" autocomplete="username" minlength="3" maxlength="32" pattern="[A-Za-z0-9_]+" required />
          <small>使用 3-32 位英文字母、数字或下划线。</small>
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="new-password" minlength="6" maxlength="32" required />
          <small>使用 6-32 位密码。</small>
        </label>
        <p v-if="errorMessage" class="form-error"><CircleAlert :size="16" />{{ errorMessage }}</p>
        <button class="primary-button auth-submit" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? '创建中...' : '创建账号' }}
          <ArrowRight v-if="!isSubmitting" :size="18" />
        </button>
      </form>
      <p class="auth-switch">已有账号？<RouterLink to="/login">去登录</RouterLink></p>
    </section>
  </main>
</template>
