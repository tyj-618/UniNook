<script setup lang="ts">
import { Send } from '@lucide/vue'
import { computed } from 'vue'
import type { PostDetail, QuestionTracking } from '../types/api.ts'
import { submitOnEnter } from '../utils/submitOnEnter.ts'

const props = defineProps<{
  post: PostDetail
  selectedPostQuestion: QuestionTracking | null
  content: string
  isSubmitting: boolean
}>()

const emit = defineEmits<{
  'submit-comment': [content: string]
  'cancel-answer': []
  'update:content': [value: string]
}>()

const contentModel = computed({
  get: () => props.content,
  set: (value: string) => emit('update:content', value),
})

function submit(): void {
  emit('submit-comment', props.content)
}
</script>

<template>
  <form class="comment-form" @submit.prevent="submit"><div class="comment-form-heading"><label for="comment-content">参与讨论</label><button v-if="selectedPostQuestion" class="text-button" type="button" @click="emit('cancel-answer')">取消候选答复</button></div><p v-if="selectedPostQuestion" class="candidate-answer-context">正在作为“{{ selectedPostQuestion.questionText }}”的候选答复</p><textarea id="comment-content" v-model="contentModel" maxlength="500" rows="4" placeholder="写下你的想法..." @keydown="submitOnEnter($event, submit)" /><div class="comment-form-footer"><span>{{ content.length }}/500 · Enter 发送，Shift + Enter 换行</span><button class="primary-button" type="submit" :disabled="isSubmitting || !content.trim()"><Send :size="16" />{{ isSubmitting ? '发布中...' : '发布评论' }}</button></div></form>
</template>
