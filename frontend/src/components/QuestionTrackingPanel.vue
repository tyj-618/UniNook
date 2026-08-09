<script setup lang="ts">
import { BellPlus, BellRing, Check, CircleDotDashed, List, Send, Trash2, X } from '@lucide/vue'
import { ref, watch } from 'vue'
import { completeQuestion, createQuestion, deleteQuestion, getQuestionBySource, subscribeQuestion, unsubscribeQuestion } from '../api/questions.ts'
import { errorMessageOf } from '../api/errors.ts'
import type { QuestionSourceType, QuestionStatus, QuestionTracking } from '../types/api.ts'
import ConfirmDialog from './ConfirmDialog.vue'
import { submitOnEnter } from '../utils/submitOnEnter.ts'

const props = defineProps<{ sourceType: QuestionSourceType; sourceId: number; canManage: boolean }>()
const emit = defineEmits<{
  loaded: [question: QuestionTracking | null]
  updated: [question: QuestionTracking | null]
  answerRequested: [question: QuestionTracking]
}>()

const question = ref<QuestionTracking | null>(null)
const isLoading = ref(false)
const isSubmitting = ref(false)
const isChangingSubscription = ref<number | null>(null)
const isCompleting = ref(false)
const isDeleting = ref(false)
const isComposerOpen = ref(false)
const isCompleteDialogOpen = ref(false)
const isDeleteDialogOpen = ref(false)
const pendingQuestion = ref<QuestionTracking | null>(null)
const questionText = ref('')
const message = ref('')

const sourceLabel = props.sourceType === 'POST' ? '帖子' : '评论'
const canCreate = () => props.canManage && question.value === null

watch(() => [props.sourceType, props.sourceId], () => void loadQuestions(), { immediate: true })

function formatStatus(status: QuestionStatus): string { return status === 'COMPLETED' ? '已完成' : '进行中' }
function statusClass(status: QuestionStatus): string { return `question-status--${status.toLowerCase()}` }

async function loadQuestions(): Promise<void> {
  isLoading.value = true
  message.value = ''
  try {
    question.value = await getQuestionBySource(props.sourceType, props.sourceId)
    emit('loaded', question.value)
  } catch (error) {
    question.value = null
    message.value = errorMessageOf(error, '问题追踪加载失败，请稍后重试。')
    emit('loaded', null)
  } finally { isLoading.value = false }
}

function openComposer(): void { message.value = ''; questionText.value = ''; isComposerOpen.value = true }
function closeComposer(): void { isComposerOpen.value = false; questionText.value = '' }

async function submitQuestion(): Promise<void> {
  const content = questionText.value.trim()
  if (!content || !canCreate() || isSubmitting.value) return
  isSubmitting.value = true
  message.value = ''
  try {
    const created = await createQuestion(props.sourceType, props.sourceId, content)
    question.value = created
    emit('updated', question.value)
    closeComposer()
  } catch (error) { message.value = errorMessageOf(error, '问题追踪发起失败，请稍后重试。') } finally { isSubmitting.value = false }
}

async function toggleSubscription(question: QuestionTracking): Promise<void> {
  if (props.canManage || isChangingSubscription.value !== null) return
  isChangingSubscription.value = question.id
  message.value = ''
  try {
    const result = question.subscribed ? await unsubscribeQuestion(question.id) : await subscribeQuestion(question.id)
    replaceQuestion({ ...question, ...result })
  } catch (error) { message.value = errorMessageOf(error, '订阅状态更新失败，请稍后重试。') } finally { isChangingSubscription.value = null }
}

function openComplete(question: QuestionTracking): void { pendingQuestion.value = question; isCompleteDialogOpen.value = true }
function openDelete(question: QuestionTracking): void { pendingQuestion.value = question; isDeleteDialogOpen.value = true }

async function confirmComplete(): Promise<void> {
  if (!pendingQuestion.value || !props.canManage || isCompleting.value) return
  isCompleting.value = true
  try { replaceQuestion(await completeQuestion(pendingQuestion.value.id)) }
  catch (error) { message.value = errorMessageOf(error, '问题结束失败，请稍后重试。') }
  finally { isCompleting.value = false; isCompleteDialogOpen.value = false; pendingQuestion.value = null }
}

async function confirmDelete(): Promise<void> {
  if (!pendingQuestion.value || !props.canManage || isDeleting.value) return
  isDeleting.value = true
  try {
    await deleteQuestion(pendingQuestion.value.id)
    question.value = null
    emit('updated', null)
  } catch (error) { message.value = errorMessageOf(error, '问题删除失败，请稍后重试。') }
  finally { isDeleting.value = false; isDeleteDialogOpen.value = false; pendingQuestion.value = null }
}

function replaceQuestion(updatedQuestion: QuestionTracking): void {
  question.value = updatedQuestion
  emit('updated', question.value)
}
</script>

<template>
  <section class="question-tracking-section" aria-labelledby="question-tracking-heading">
    <div class="question-tracking-heading">
      <div><p class="eyebrow">QUESTION TRACKING</p><h2 id="question-tracking-heading">问题追踪</h2></div>
      <span class="question-tracking-count">{{ question ? 1 : 0 }}/1 个问题</span>
    </div>
    <p v-if="isLoading" class="question-tracking-empty"><CircleDotDashed :size="18" />正在加载问题追踪…</p>
    <template v-else>
      <p v-if="!question" class="question-tracking-empty">这条{{ sourceLabel }}暂未发起问题追踪。</p>
      <article v-if="question" class="question-tracking-summary">
        <div class="question-tracking-card-heading"><span class="question-status" :class="statusClass(question.status)">{{ formatStatus(question.status) }}</span><span>{{ question.subscriberCount }} 人订阅答案</span></div>
        <p>{{ question.questionText }}</p>
        <strong v-if="question.approvedAnswerCount > 0">已通过 {{ question.approvedAnswerCount }} 条候选答复</strong>
        <div class="question-tracking-actions">
          <button v-if="!canManage" class="secondary-button" type="button" :disabled="isChangingSubscription === question.id" @click="toggleSubscription(question)"><BellRing v-if="question.subscribed" :size="16" /><BellPlus v-else :size="16" />{{ question.subscribed ? '取消订阅' : '订阅答案' }}</button>
          <button v-if="!canManage && question.status === 'OPEN'" class="primary-button" type="button" @click="emit('answerRequested', question)"><Send :size="16" />快速作答</button>
          <button v-if="canManage && question.status === 'OPEN'" class="primary-button" type="button" @click="openComplete(question)"><Check :size="16" />结束问题</button>
          <RouterLink class="text-button" :to="{ name: 'question-answer-list', params: { questionId: question.id } }"><List :size="16" />候选答复</RouterLink>
          <button v-if="canManage" class="text-button danger" type="button" @click="openDelete(question)"><Trash2 :size="15" />删除问题</button>
        </div>
      </article>
      <button v-if="canCreate() && !isComposerOpen" class="secondary-button" type="button" @click="openComposer"><BellPlus :size="16" />发起问题追踪</button>
      <p v-else-if="canManage && !isComposerOpen" class="question-tracking-hint">当前{{ sourceLabel }}已发起问题追踪，可管理或删除后重新发起。</p>
    </template>
    <form v-if="isComposerOpen" class="question-tracking-editor" @submit.prevent="submitQuestion">
      <div class="question-tracking-editor-heading"><label for="question-tracking-text">你想持续追踪什么答案？</label><button class="icon-button" type="button" aria-label="取消发起问题追踪" @click="closeComposer"><X :size="17" /></button></div>
      <textarea id="question-tracking-text" v-model="questionText" maxlength="300" rows="3" placeholder="例如：希望补充可靠的解决方式或最终结论" autofocus @keydown="submitOnEnter($event, submitQuestion)" />
      <div class="question-tracking-editor-footer"><span>{{ questionText.length }}/300 · Enter 发起，Shift + Enter 换行</span><button class="primary-button" type="submit" :disabled="isSubmitting || !questionText.trim()"><Send :size="16" />{{ isSubmitting ? '发起中...' : '确认发起' }}</button></div>
    </form>
    <p v-if="message" class="form-error">{{ message }}</p>
  </section>
  <ConfirmDialog :visible="isCompleteDialogOpen" title="结束问题" message="结束后将不再接收候选答复。" confirm-text="确认结束" :is-loading="isCompleting" @confirm="confirmComplete" @cancel="isCompleteDialogOpen = false" />
  <ConfirmDialog :visible="isDeleteDialogOpen" title="删除问题" message="删除后无法恢复。" confirm-text="确认删除" :danger="true" :is-loading="isDeleting" @confirm="confirmDelete" @cancel="isDeleteDialogOpen = false" />
</template>
