<script setup lang="ts">
import { ArrowLeft, Check, CircleAlert, CircleDotDashed, ExternalLink, RotateCcw, Send, Sparkles, Trash2, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { acceptQuestionAnswer, completeQuestion, deleteQuestion, getQuestion, getQuestionAnswers, rejectQuestionAnswer, reopenQuestion, reviewQuestionAnswerWithAi } from '../api/questions.ts'
import { createPostComment, deletePostComment } from '../api/posts.ts'
import { errorMessageOf } from '../api/errors.ts'
import { authStore } from '../auth/auth.ts'
import type { CandidateAnswerAiReview, QuestionAnswer, QuestionTracking } from '../types/api.ts'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { submitOnEnter } from '../utils/submitOnEnter.ts'

const route = useRoute()
const router = useRouter()
const question = ref<QuestionTracking | null>(null)
const answers = ref<QuestionAnswer[]>([])
const isLoading = ref(true)
const isSubmitting = ref(false)
const errorMessage = ref('')
const pendingAction = ref<QuestionAnswer | null>(null)
const pendingActionType = ref<'accept' | 'reject' | null>(null)
const pendingQuestionAction = ref<'complete' | 'reopen' | null>(null)
const isQuestionSubmitting = ref(false)
const reviewingAnswerId = ref<number | null>(null)
const aiReviews = ref<Record<number, CandidateAnswerAiReview>>({})
const pendingDeleteAnswer = ref<QuestionAnswer | null>(null)
const isDeletingAnswer = ref(false)
const isDeleteQuestionDialogOpen = ref(false)
const isDeletingQuestion = ref(false)
const candidateContent = ref('')
const isPublishingCandidate = ref(false)
const handleCandidateKeydown = submitOnEnter

const questionId = computed(() => {
  const raw = Array.isArray(route.params.questionId) ? null : route.params.questionId
  const value = Number(raw)
  return Number.isSafeInteger(value) && value > 0 ? value : null
})
const canManage = computed(() => question.value?.asker.id === authStore.state.user?.id)
const approvedAnswers = computed(() => answers.value.filter((answer) => answer.status === 'ACCEPTED'))
const pendingAnswers = computed(() => answers.value.filter((answer) => answer.status === 'PENDING'))
const openedFromQuestionTracking = computed(() => route.query.source === 'questions')
const openedFromNotice = computed(() => route.query.source === 'notice')
const openedFromCommentQuestions = computed(() => route.query.source === 'comment-questions')

watch(questionId, () => void load(), { immediate: true })

function postTarget(commentId?: number, returnToAnswers = false): { name: 'post-detail'; params: { id: number }; query: Record<string, string> } | null {
  if (!question.value) return null
  const query: Record<string, string> = {}
  if (returnToAnswers) {
    query.source = 'question-answers'
    query.questionId = String(question.value.id)
  } else if (openedFromQuestionTracking.value) {
    query.source = 'questions'
  } else if (openedFromNotice.value) {
    query.source = 'notice'
  } else if (openedFromCommentQuestions.value) {
    query.source = 'comment-questions'
  }
  const targetCommentId = openedFromCommentQuestions.value
    ? commentId
    : commentId ?? (question.value.sourceType === 'COMMENT' ? question.value.sourceId : undefined)
  if (targetCommentId !== undefined) query.commentId = String(targetCommentId)
  return { name: 'post-detail', params: { id: question.value.sourcePostId }, query }
}

async function load(): Promise<void> {
  if (questionId.value === null) {
    errorMessage.value = '候选答复地址无效。'
    isLoading.value = false
    return
  }
  isLoading.value = true
  errorMessage.value = ''
  try {
    const [loadedQuestion, loadedAnswers] = await Promise.all([
      getQuestion(questionId.value),
      getQuestionAnswers(questionId.value),
    ])
    question.value = loadedQuestion
    answers.value = loadedAnswers
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '候选答复暂时无法加载，请稍后重试。')
  } finally {
    isLoading.value = false
  }
}

async function reviewWithAi(answer: QuestionAnswer): Promise<void> {
  if (!question.value || reviewingAnswerId.value !== null) return
  reviewingAnswerId.value = answer.id
  errorMessage.value = ''
  try {
    aiReviews.value = {
      ...aiReviews.value,
      [answer.id]: await reviewQuestionAnswerWithAi(question.value.id, answer.id),
    }
  } catch (error) {
    errorMessage.value = errorMessageOf(error, 'AI 辅助判断暂时不可用，请稍后重试。')
  } finally {
    reviewingAnswerId.value = null
  }
}

function reviewLabel(review: CandidateAnswerAiReview): string {
  if (review.verdict === 'RELEVANT') return '关联度较高'
  if (review.verdict === 'IRRELEVANT') return '关联度较低'
  return '需要人工判断'
}

function requestAction(answer: QuestionAnswer, action: 'accept' | 'reject'): void {
  pendingAction.value = answer
  pendingActionType.value = action
}

function closeDialog(): void {
  pendingAction.value = null
  pendingActionType.value = null
}

async function confirmAction(): Promise<void> {
  if (!question.value || !pendingAction.value || !pendingActionType.value || isSubmitting.value) return
  isSubmitting.value = true
  errorMessage.value = ''
  try {
    if (pendingActionType.value === 'accept') {
      question.value = await acceptQuestionAnswer(question.value.id, pendingAction.value.id)
      answers.value = answers.value.map((item) => item.id === pendingAction.value?.id
        ? { ...item, status: 'ACCEPTED', reviewedAt: new Date().toISOString() }
        : item)
    } else {
      const answer = await rejectQuestionAnswer(question.value.id, pendingAction.value.id)
      answers.value = answers.value.map((item) => item.id === answer.id ? answer : item)
    }
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '答复状态更新失败，请稍后重试。')
  } finally {
    isSubmitting.value = false
    closeDialog()
  }
}

function requestQuestionAction(action: 'complete' | 'reopen'): void {
  pendingQuestionAction.value = action
}

function closeQuestionDialog(): void {
  pendingQuestionAction.value = null
}

function isAnswerAuthor(answer: QuestionAnswer): boolean {
  return answer.answerer.id === authStore.state.user?.id
}

function requestDeleteAnswer(answer: QuestionAnswer): void {
  pendingDeleteAnswer.value = answer
}

async function confirmDeleteAnswer(): Promise<void> {
  if (!pendingDeleteAnswer.value || isDeletingAnswer.value) return
  isDeletingAnswer.value = true
  errorMessage.value = ''
  try {
    await deletePostComment(pendingDeleteAnswer.value.commentId)
    await load()
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '删除答复失败，请稍后重试。')
  } finally {
    isDeletingAnswer.value = false
    pendingDeleteAnswer.value = null
  }
}

async function confirmDeleteQuestion(): Promise<void> {
  if (!question.value || isDeletingQuestion.value) return
  isDeletingQuestion.value = true
  errorMessage.value = ''
  try {
    await deleteQuestion(question.value.id)
    if (openedFromNotice.value) {
      await router.replace({ name: 'notices' })
    } else if (openedFromQuestionTracking.value) {
      await router.replace({ name: 'questions' })
    } else {
      await router.replace({ name: 'post-detail', params: { id: question.value.sourcePostId } })
    }
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '删除问题失败，请稍后重试。')
  } finally {
    isDeletingQuestion.value = false
    isDeleteQuestionDialogOpen.value = false
  }
}

async function confirmQuestionAction(): Promise<void> {
  if (!question.value || !pendingQuestionAction.value || isQuestionSubmitting.value) return
  isQuestionSubmitting.value = true
  errorMessage.value = ''
  try {
    question.value = pendingQuestionAction.value === 'complete'
      ? await completeQuestion(question.value.id)
      : await reopenQuestion(question.value.id)
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '问题状态更新失败，请稍后重试。')
  } finally {
    isQuestionSubmitting.value = false
    closeQuestionDialog()
  }
}

async function submitCandidateAnswer(): Promise<void> {
  const content = candidateContent.value.trim()
  const currentUserId = authStore.state.user?.id
  if (!question.value || question.value.status !== 'OPEN' || !currentUserId || !content || isPublishingCandidate.value) return
  isPublishingCandidate.value = true
  errorMessage.value = ''
  try {
    const parentCommentId = question.value.sourceType === 'COMMENT' ? question.value.sourceId : undefined
    await createPostComment(question.value.sourcePostId, 10, content, currentUserId, parentCommentId, question.value.id)
    candidateContent.value = ''
    await load()
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '候选答复发布失败，请稍后重试。')
  } finally {
    isPublishingCandidate.value = false
  }
}
</script>

<template>
  <section class="content-page question-answer-page">
    <RouterLink v-if="postTarget()" class="detail-back" :to="postTarget()!"><ArrowLeft :size="18" />返回原帖</RouterLink>
    <section v-if="isLoading" class="empty-feed"><CircleDotDashed :size="22" /><h2>正在加载候选答复…</h2></section>
    <section v-else-if="errorMessage || !question" class="empty-feed"><CircleAlert :size="22" /><h2>加载失败</h2><p>{{ errorMessage || '问题不存在。' }}</p><button class="primary-button" type="button" @click="load">重新加载</button></section>
    <template v-else>
      <div class="page-heading question-answer-page-heading">
        <div>
          <p class="eyebrow">CANDIDATE ANSWERS</p>
          <h1>候选答复</h1>
          <p class="muted">{{ question.questionText }}</p>
        </div>
        <div class="question-answer-page-actions">
          <span class="question-status" :class="`question-status--${question.status.toLowerCase()}`">{{ question.status === 'COMPLETED' ? '已完成' : '进行中' }}</span>
          <button
            v-if="canManage && question.status === 'OPEN'"
            class="primary-button"
            type="button"
            :disabled="approvedAnswers.length === 0"
            title="至少通过一条候选答复后才能结束问题"
            @click="requestQuestionAction('complete')"
          >
            <Check :size="16" />结束问题
          </button>
          <button
            v-else-if="canManage"
            class="secondary-button question-reopen-button"
            type="button"
            @click="requestQuestionAction('reopen')"
          >
            <RotateCcw :size="16" />重新开启问题
          </button>
        </div>
      </div>

      <form v-if="question.status === 'OPEN'" class="candidate-answer-compose" @submit.prevent="submitCandidateAnswer">
        <label for="candidate-answer-content">提交候选答复</label>
        <textarea id="candidate-answer-content" v-model="candidateContent" rows="4" maxlength="500" placeholder="补充你确认有效的信息或可行建议..." @keydown="handleCandidateKeydown($event, submitCandidateAnswer)" />
        <div class="candidate-answer-compose-footer"><span>{{ candidateContent.length }}/500 · Enter 发送，Shift + Enter 换行</span><button class="primary-button" type="submit" :disabled="!candidateContent.trim() || isPublishingCandidate"><Send :size="16" />{{ isPublishingCandidate ? '发布中...' : '提交答复' }}</button></div>
      </form>

      <section class="answer-group" aria-labelledby="approved-answer-heading">
        <div class="answer-group-heading"><h2 id="approved-answer-heading">已通过</h2><span>{{ approvedAnswers.length }} 条</span></div>
        <p v-if="approvedAnswers.length === 0" class="question-tracking-empty">发起者尚未通过任何候选答复。</p>
        <div v-else class="question-answer-list">
          <article v-for="answer in approvedAnswers" :key="answer.id" class="question-answer-item question-answer-item--accepted">
            <div class="question-answer-meta"><strong>{{ answer.answerer.nickname }}</strong><span>已通过</span></div>
            <p>{{ answer.content }}</p>
            <div class="answer-item-footer">
              <RouterLink class="text-button answer-source-link" :to="postTarget(answer.commentId, true)!"><ExternalLink :size="15" />查看原评论</RouterLink>
              <button v-if="isAnswerAuthor(answer)" class="text-button danger" type="button" @click="requestDeleteAnswer(answer)"><Trash2 :size="15" />删除答复</button>
            </div>
          </article>
        </div>
      </section>

      <section class="answer-group" aria-labelledby="pending-answer-heading">
        <div class="answer-group-heading"><h2 id="pending-answer-heading">未判断</h2><span>{{ pendingAnswers.length }} 条</span></div>
        <p v-if="pendingAnswers.length === 0" class="question-tracking-empty">暂无待判断的候选答复。</p>
        <div v-else class="question-answer-list">
          <article v-for="answer in pendingAnswers" :key="answer.id" class="question-answer-item">
            <div class="question-answer-meta"><strong>{{ answer.answerer.nickname }}</strong><span>{{ canManage ? '请判断' : '待发起者判断' }}</span></div>
            <p>{{ answer.content }}</p>
            <aside v-if="aiReviews[answer.id]" class="candidate-ai-review" :class="`candidate-ai-review--${aiReviews[answer.id].verdict.toLowerCase()}`">
              <strong><Sparkles :size="15" /> AI 建议：{{ reviewLabel(aiReviews[answer.id]) }} · {{ aiReviews[answer.id].relevanceScore }}/100</strong>
              <span>{{ aiReviews[answer.id].rationale }}</span>
              <small>仅供参考，是否通过仍由发起者决定。</small>
            </aside>
            <div class="answer-item-footer">
              <RouterLink class="text-button answer-source-link" :to="postTarget(answer.commentId, true)!"><ExternalLink :size="15" />查看原评论</RouterLink>
              <div v-if="canManage && question.status === 'OPEN'" class="question-tracking-actions">
                <button class="secondary-button candidate-ai-review-button" type="button" :disabled="reviewingAnswerId === answer.id" @click="reviewWithAi(answer)">
                  <CircleDotDashed v-if="reviewingAnswerId === answer.id" :size="16" />
                  <Sparkles v-else :size="16" />{{ reviewingAnswerId === answer.id ? '分析中…' : 'AI 辅助判断' }}
                </button>
                <button class="primary-button" type="button" @click="requestAction(answer, 'accept')"><Check :size="16" />通过答复</button>
                <button class="text-button" type="button" @click="requestAction(answer, 'reject')"><X :size="16" />标记无效</button>
              </div>
              <button v-if="isAnswerAuthor(answer)" class="text-button danger" type="button" @click="requestDeleteAnswer(answer)"><Trash2 :size="15" />删除答复</button>
            </div>
          </article>
        </div>
      </section>
      <div v-if="canManage" class="question-answer-danger-zone">
        <button class="text-button danger" type="button" @click="isDeleteQuestionDialogOpen = true"><Trash2 :size="15" />删除问题</button>
      </div>
    </template>
  </section>
  <ConfirmDialog :visible="pendingAction !== null" :title="pendingActionType === 'accept' ? '通过答复' : '标记无效'" :message="pendingActionType === 'accept' ? '该答复会加入“已通过”列表。你仍可继续查看并通过其他有效答复。' : '标记后，该答复不会展示在候选答复列表中。'" :confirm-text="pendingActionType === 'accept' ? '确认通过' : '确认标记'" :danger="pendingActionType === 'reject'" :is-loading="isSubmitting" @confirm="confirmAction" @cancel="closeDialog" />
  <ConfirmDialog
    :visible="pendingQuestionAction !== null"
    :title="pendingQuestionAction === 'complete' ? '结束问题' : '重新开启问题'"
    :message="pendingQuestionAction === 'complete'
      ? '结束后将不再接收新的候选答复，已通过答复会保留并通知订阅者。'
      : '重新开启后将继续接收候选答复，已有的已通过答复和订阅记录会保留，订阅者将收到提醒。'"
    :confirm-text="pendingQuestionAction === 'complete' ? '确认结束' : '确认重新开启'"
    :is-loading="isQuestionSubmitting"
    @confirm="confirmQuestionAction"
    @cancel="closeQuestionDialog"
  />
  <ConfirmDialog :visible="pendingDeleteAnswer !== null" title="删除答复" message="删除后无法恢复。" confirm-text="确认删除" :danger="true" :is-loading="isDeletingAnswer" @confirm="confirmDeleteAnswer" @cancel="pendingDeleteAnswer = null" />
  <ConfirmDialog :visible="isDeleteQuestionDialogOpen" title="删除问题" message="删除后无法恢复，订阅记录和候选答复将一并清理。" confirm-text="确认删除" :danger="true" :is-loading="isDeletingQuestion" @confirm="confirmDeleteQuestion" @cancel="isDeleteQuestionDialogOpen = false" />
</template>
