<script setup lang="ts">
import { BellRing, CircleAlert, CircleDotDashed, ListChecks } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { getMyQuestions, unsubscribeQuestion } from '../api/questions.ts'
import { errorMessageOf } from '../api/errors.ts'
import type { MyQuestionRole, QuestionStatus, QuestionTracking } from '../types/api.ts'
import { formatCompactDateTime } from '../utils/date.ts'

const activeRole = ref<MyQuestionRole>('ASKED')
const activeSubscriptionStatus = ref<QuestionStatus>('OPEN')
const questions = ref<QuestionTracking[]>([])
const total = ref(0)
const isLoading = ref(false)
const errorMessage = ref('')
const cancellingQuestionId = ref<number | null>(null)

const visibleQuestions = computed(() => activeRole.value === 'SUBSCRIBED'
  ? questions.value.filter((question) => question.status === activeSubscriptionStatus.value)
  : questions.value)

watch(activeRole, () => void loadQuestions(), { immediate: true })

function statusText(status: QuestionStatus): string {
  return status === 'COMPLETED' ? '已完成' : '进行中'
}

async function loadQuestions(): Promise<void> {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const page = await getMyQuestions(activeRole.value)
    questions.value = page.records
    total.value = page.total
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '问题追踪暂时无法加载，请稍后重试。')
  } finally {
    isLoading.value = false
  }
}

async function cancelSubscription(question: QuestionTracking): Promise<void> {
  if (cancellingQuestionId.value !== null) return
  cancellingQuestionId.value = question.id
  errorMessage.value = ''
  try {
    await unsubscribeQuestion(question.id)
    questions.value = questions.value.filter((item) => item.id !== question.id)
    total.value = Math.max(total.value - 1, 0)
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '取消订阅失败，请稍后重试。')
  } finally {
    cancellingQuestionId.value = null
  }
}
</script>

<template>
  <section class="content-page question-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">QUESTION TRACKING</p>
        <h1>我的问题追踪</h1>
        <p class="muted">集中查看自己发起的问题，以及订阅的结果。</p>
      </div>
      <span class="question-page-count"><ListChecks :size="18" />{{ total }} 条</span>
    </div>

    <div class="segmented-control" aria-label="问题追踪类型">
      <button :class="{ active: activeRole === 'ASKED' }" type="button" @click="activeRole = 'ASKED'">我发起的</button>
      <button :class="{ active: activeRole === 'SUBSCRIBED' }" type="button" @click="activeRole = 'SUBSCRIBED'">我订阅的</button>
    </div>
    <div v-if="activeRole === 'SUBSCRIBED'" class="segmented-control" aria-label="订阅状态">
      <button :class="{ active: activeSubscriptionStatus === 'OPEN' }" type="button" @click="activeSubscriptionStatus = 'OPEN'">进行中</button>
      <button :class="{ active: activeSubscriptionStatus === 'COMPLETED' }" type="button" @click="activeSubscriptionStatus = 'COMPLETED'">已完成</button>
    </div>

    <section v-if="isLoading" class="empty-feed"><CircleDotDashed :size="22" /><h2>正在加载问题追踪…</h2></section>
    <section v-else-if="errorMessage" class="empty-feed"><CircleAlert :size="22" /><h2>加载失败</h2><p>{{ errorMessage }}</p><button class="primary-button" type="button" @click="loadQuestions">重新加载</button></section>
    <section v-else-if="visibleQuestions.length === 0" class="empty-feed"><BellRing :size="22" /><h2>{{ activeRole === 'ASKED' ? '暂未发起问题追踪' : activeSubscriptionStatus === 'OPEN' ? '暂无进行中的订阅' : '暂无已完成的订阅' }}</h2><p>{{ activeRole === 'ASKED' ? '在自己的帖子或评论中发起追踪，集中等待后续结果。' : '订阅会保留在这里，你可以随时取消。' }}</p></section>
    <ol v-else class="question-list">
      <li v-for="item in visibleQuestions" :key="item.id">
        <RouterLink class="question-list-item" :to="{ name: 'question-answer-list', params: { questionId: item.id }, query: { source: 'questions' } }">
          <div class="question-list-main">
            <p class="activity-meta">{{ item.sourceType === 'POST' ? '帖子问题' : '评论问题' }} · {{ formatCompactDateTime(item.updatedAt) }}</p>
            <h2>{{ item.questionText }}</h2>
            <p>{{ item.sourcePreview }}</p>
          </div>
          <div class="question-list-meta">
            <span class="question-status" :class="`question-status--${item.status.toLowerCase()}`">{{ statusText(item.status) }}</span>
            <span>{{ item.subscriberCount }} 人订阅</span>
          </div>
        </RouterLink>
        <button v-if="activeRole === 'SUBSCRIBED'" class="text-button question-list-cancel" type="button" :disabled="cancellingQuestionId === item.id" @click="cancelSubscription(item)">{{ cancellingQuestionId === item.id ? '取消中…' : '取消订阅' }}</button>
      </li>
    </ol>
  </section>
</template>
