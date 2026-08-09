<script setup lang="ts">
import { Check, CircleDotDashed, List, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { acceptQuestionAnswer, getQuestionAnswers, rejectQuestionAnswer } from '../api/questions.ts'
import { errorMessageOf } from '../api/errors.ts'
import type { QuestionAnswer, QuestionTracking } from '../types/api.ts'
import ConfirmDialog from './ConfirmDialog.vue'

const props = defineProps<{
  question: QuestionTracking
  canManage: boolean
}>()

const emit = defineEmits<{
  updated: [question: QuestionTracking]
}>()

const answers = ref<QuestionAnswer[]>([])
const isLoading = ref(false)
const pendingAction = ref<QuestionAnswer | null>(null)
const pendingActionType = ref<'accept' | 'reject' | null>(null)
const isSubmitting = ref(false)
const message = ref('')

const displayLimit = computed(() => props.question.sourceType === 'POST' ? 3 : 1)
const pendingAnswers = computed(() => answers.value.filter((answer) => answer.status === 'PENDING'))
const approvedAnswers = computed(() => answers.value.filter((answer) => answer.status === 'ACCEPTED'))
const previewAnswers = computed(() => [...approvedAnswers.value, ...pendingAnswers.value].slice(0, displayLimit.value))
const visibleAnswerCount = computed(() => approvedAnswers.value.length + pendingAnswers.value.length)
const hiddenAnswerCount = computed(() => Math.max(visibleAnswerCount.value - previewAnswers.value.length, 0))

watch(() => props.question.id, () => void loadAnswers(), { immediate: true })

async function loadAnswers(): Promise<void> {
  isLoading.value = true
  message.value = ''
  try {
    answers.value = await getQuestionAnswers(props.question.id)
  } catch (error) {
    message.value = errorMessageOf(error, '候选答复加载失败，请稍后重试。')
  } finally {
    isLoading.value = false
  }
}

function requestAction(answer: QuestionAnswer, action: 'accept' | 'reject'): void {
  if (answer.status !== 'PENDING') return
  pendingAction.value = answer
  pendingActionType.value = action
}

function closeDialog(): void {
  pendingAction.value = null
  pendingActionType.value = null
}

async function confirmAction(): Promise<void> {
  if (!pendingAction.value || !pendingActionType.value || isSubmitting.value) return
  isSubmitting.value = true
  message.value = ''
  try {
    if (pendingActionType.value === 'accept') {
      const question = await acceptQuestionAnswer(props.question.id, pendingAction.value.id)
      answers.value = answers.value.map((item) => item.id === pendingAction.value?.id
        ? { ...item, status: 'ACCEPTED', reviewedAt: new Date().toISOString() }
        : item)
      emit('updated', question)
    } else {
      const answer = await rejectQuestionAnswer(props.question.id, pendingAction.value.id)
      answers.value = answers.value.map((item) => item.id === answer.id ? answer : item)
    }
  } catch (error) {
    message.value = errorMessageOf(error, '答复状态更新失败，请稍后重试。')
  } finally {
    isSubmitting.value = false
    closeDialog()
  }
}

function actionTitle(): string {
  return pendingActionType.value === 'accept' ? '通过答复' : '标记无效'
}

function actionMessage(): string {
  return pendingActionType.value === 'accept'
    ? '该答复会加入“已通过”列表。你仍可继续查看并通过其他有效答复。'
    : '标记后，该答复不会展示在候选答复列表中。'
}
</script>

<template>
  <section class="question-answer-section" aria-labelledby="question-answer-heading">
    <div class="question-tracking-heading">
      <div>
        <p class="eyebrow">CANDIDATE ANSWERS</p>
        <h2 id="question-answer-heading">候选答复</h2>
        <p class="question-answer-context">对应问题：{{ question.questionText }}</p>
      </div>
      <RouterLink class="text-button" :to="{ name: 'question-answer-list', params: { questionId: question.id } }">
        <List :size="16" />候选答复{{ visibleAnswerCount > 0 ? ` ${visibleAnswerCount} 条` : '' }}
      </RouterLink>
    </div>

    <p v-if="isLoading" class="question-tracking-empty"><CircleDotDashed :size="18" />正在加载候选答复…</p>
    <p v-else-if="visibleAnswerCount === 0" class="question-tracking-empty">暂时没有被标记为候选答复的评论。</p>
    <template v-else>
      <div class="question-answer-list">
        <article v-for="answer in previewAnswers" :key="answer.id" class="question-answer-item" :class="{ 'question-answer-item--accepted': answer.status === 'ACCEPTED' }">
          <div class="question-answer-meta"><strong>{{ answer.answerer.nickname }}</strong><span>{{ answer.status === 'ACCEPTED' ? '已通过' : '待判断' }}</span></div>
          <p class="question-answer-preview">{{ answer.content }}</p>
          <div v-if="canManage && question.status === 'OPEN' && answer.status === 'PENDING'" class="question-tracking-actions">
            <button class="primary-button" type="button" @click="requestAction(answer, 'accept')"><Check :size="16" />通过答复</button>
            <button class="text-button" type="button" @click="requestAction(answer, 'reject')"><X :size="16" />标记无效</button>
          </div>
        </article>
      </div>
      <RouterLink v-if="hiddenAnswerCount > 0" class="question-answer-more" :to="{ name: 'question-answer-list', params: { questionId: question.id } }">
        还有 {{ hiddenAnswerCount }} 条候选答复，查看全部
      </RouterLink>
    </template>
    <p v-if="message" class="form-error">{{ message }}</p>
  </section>
  <ConfirmDialog :visible="pendingAction !== null" :title="actionTitle()" :message="actionMessage()" :confirm-text="pendingActionType === 'accept' ? '确认通过' : '确认标记'" :danger="pendingActionType === 'reject'" :is-loading="isSubmitting" @confirm="confirmAction" @cancel="closeDialog" />
</template>
