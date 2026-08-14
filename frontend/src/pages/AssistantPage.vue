<script setup lang="ts">
import { CircleAlert, Send } from '@lucide/vue'
import { ref } from 'vue'
import { streamAssistant } from '../api/assistant.ts'
import { errorMessageOf } from '../api/errors.ts'
import type { AiAssistantResponse, CampusScope } from '../types/api.ts'
import { submitOnEnter } from '../utils/submitOnEnter.ts'

const question = ref('')
const scope = ref<CampusScope>('NEARBY_10')
const result = ref<AiAssistantResponse | null>(null)
const errorMessage = ref('')
const isSubmitting = ref(false)
const abortController = ref<AbortController | null>(null)
const sessionId = createSessionId()

async function submit(): Promise<void> {
  const trimmedQuestion = question.value.trim()
  if (!trimmedQuestion) return
  isSubmitting.value = true
  errorMessage.value = ''
  result.value = { answer: '', references: [], insufficientEvidence: false, requestId: '' }
  const controller = new AbortController()
  abortController.value = controller
  try {
    await streamAssistant(trimmedQuestion, scope.value, sessionId, {
      onChunk: (chunk) => {
        if (result.value) result.value.answer += chunk
      },
      onDone: (response) => { result.value = response },
    }, controller.signal)
  } catch (error) {
    if (!isAbortError(error)) {
    errorMessage.value = errorMessageOf(error, '校园助手暂时无法回答，请稍后重试。')
    }
  } finally {
    if (abortController.value === controller) {
      abortController.value = null
      isSubmitting.value = false
    }
  }
}

function cancel(): void {
  abortController.value?.abort()
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function createSessionId(): string {
  const storageKey = 'uninook-assistant-session-id'
  const existing = localStorage.getItem(storageKey)
  if (existing) return existing
  const value = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `assistant-${Date.now()}-${Math.random().toString(36).slice(2)}`
  localStorage.setItem(storageKey, value)
  return value
}
</script>

<template>
  <section class="content-page form-page"><div class="page-heading"><div><p class="eyebrow">CAMPUS ASSISTANT</p><h1>校园助手</h1><p class="muted">仅检索你当前附近校园范围内的公开帖子，并附上参考来源。</p></div></div>
    <form class="editor-form" @submit.prevent="submit"><label>查看范围<select v-model="scope"><option value="CAMPUS">同校区</option><option value="UNIVERSITY">同校</option><option value="NEARBY_10">10 km</option><option value="NEARBY_20">20 km</option><option value="CITY">同市</option></select></label><label>你的问题<textarea v-model="question" rows="5" maxlength="500" placeholder="例如：附近有哪些适合期末复习的地方？" required @keydown="submitOnEnter($event, submit)" /></label><p class="editor-keyboard-hint">Enter 发送，Shift + Enter 换行</p><button class="primary-button" :disabled="isSubmitting"><Send :size="17" />{{ isSubmitting ? '检索并生成中...' : '询问助手' }}</button></form>
    <p v-if="errorMessage" class="form-error"><CircleAlert :size="16" />{{ errorMessage }}</p><section v-if="result" class="assistant-answer"><p class="eyebrow">ANSWER</p><p>{{ result.answer || '正在生成回答…' }}</p><p v-if="result.insufficientEvidence" class="muted">当前范围内没有足够的帖子可作为可靠依据。</p><div v-if="result.references.length"><h2>参考帖子</h2><RouterLink v-for="reference in result.references" :key="reference.postId" class="reference-item" :to="{ name: 'post-detail', params: { id: reference.postId }, query: { scope } }"><strong>{{ reference.title }}</strong><span>{{ reference.schoolName }} · {{ reference.excerpt }}</span></RouterLink></div></section>
    <button v-if="isSubmitting" type="button" class="secondary-button" @click="cancel">停止生成</button>
  </section>
</template>
