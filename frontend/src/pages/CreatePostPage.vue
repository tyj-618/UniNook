<script setup lang="ts">
import { CircleAlert, Send } from '@lucide/vue'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCategories } from '../api/categories.ts'
import { errorMessageOf } from '../api/errors.ts'
import { createPost } from '../api/posts.ts'
import type { Category } from '../types/api.ts'
import { submitOnEnter } from '../utils/submitOnEnter.ts'

const router = useRouter()
const categories = ref<Category[]>([])
const categoryId = ref<number | null>(null)
const title = ref('')
const content = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

onMounted(async () => {
  try {
    categories.value = await getCategories()
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '分类加载失败，请刷新页面后重试。')
  }
})

async function submit(): Promise<void> {
  if (!categoryId.value || !title.value.trim() || !content.value.trim()) {
    errorMessage.value = '请完整填写分类、标题和正文。'
    return
  }
  isSubmitting.value = true
  errorMessage.value = ''
  try {
    const response = await createPost(categoryId.value, title.value.trim(), content.value.trim())
    await router.replace({ name: 'post-detail', params: { id: response.postId }, query: { scope: 'NEARBY_10' } })
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '帖子发布失败，请稍后重试。')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <section class="content-page form-page">
    <div class="page-heading"><div><p class="eyebrow">NEW DISCUSSION</p><h1>发布讨论</h1><p class="muted">内容将归属到你已绑定的学校，并出现在附近校园范围内。</p></div></div>
    <form class="editor-form" @submit.prevent="submit">
      <label>分类<select v-model="categoryId" required><option :value="null" disabled>请选择分类</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></label>
      <label>标题<input v-model="title" maxlength="100" required placeholder="用一句话概括你想讨论的内容" /><small>{{ title.length }}/100</small></label>
      <label>正文<textarea v-model="content" maxlength="10000" rows="12" required placeholder="写下背景、问题和你希望得到的讨论..." @keydown="submitOnEnter($event, submit)" /><small>{{ content.length }}/10000 · Enter 发布，Shift + Enter 换行</small></label>
      <p v-if="errorMessage" class="form-error"><CircleAlert :size="16" />{{ errorMessage }}</p>
      <button class="primary-button" type="submit" :disabled="isSubmitting"><Send :size="17" />{{ isSubmitting ? '发布中...' : '发布帖子' }}</button>
    </form>
  </section>
</template>
