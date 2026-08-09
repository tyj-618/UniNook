<script setup lang="ts">
import { Bell, CheckCheck, CircleAlert, Trash2 } from '@lucide/vue'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessageOf } from '../api/errors.ts'
import { clearReadNotices, getNotices, markAllNoticesRead, markNoticeRead } from '../api/notices.ts'
import type { Notice } from '../types/api.ts'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { formatCompactDateTime } from '../utils/date.ts'
import { feedRouteQuery } from '../utils/feedPreferences.ts'

const router = useRouter()
const notices = ref<Notice[]>([])
const errorMessage = ref('')
const isLoading = ref(true)
const isClearingRead = ref(false)
const showClearConfirmation = ref(false)

async function load(): Promise<void> {
  isLoading.value = true
  errorMessage.value = ''
  try {
    notices.value = (await getNotices()).records
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '通知加载失败，请稍后重试。')
  } finally {
    isLoading.value = false
  }
}

async function openNotice(notice: Notice): Promise<void> {
  if (notice.readStatus === 0) {
    await markNoticeRead(notice.id)
    notice.readStatus = 1
    window.dispatchEvent(new Event('campuscircle:notices-read'))
  }
  if (notice.targetDeleted || !notice.postId) return
  if (notice.questionId) {
    await router.push({
      name: 'question-answer-list',
      params: { questionId: notice.questionId },
      query: { source: 'notice' },
    })
    return
  }
  await router.push({
    name: 'post-detail',
    params: { id: notice.postId },
    query: {
      ...feedRouteQuery(),
      commentId: notice.commentId ?? undefined,
      source: 'notice',
    },
  })
}

async function readAll(): Promise<void> {
  await markAllNoticesRead()
  notices.value.forEach((notice) => { notice.readStatus = 1 })
  window.dispatchEvent(new Event('campuscircle:notices-read'))
}

async function confirmClearRead(): Promise<void> {
  isClearingRead.value = true
  try {
    await clearReadNotices()
    notices.value = notices.value.filter((notice) => notice.readStatus === 0)
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '已读通知清理失败，请稍后重试。')
  } finally {
    isClearingRead.value = false
    showClearConfirmation.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="content-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">NOTIFICATIONS</p>
        <h1>通知</h1>
        <p class="muted">查看评论、点赞等互动提醒。</p>
      </div>
      <div class="page-actions">
        <button class="icon-button" type="button" aria-label="全部标记为已读" @click="readAll"><CheckCheck :size="19" /></button>
        <button class="icon-button" type="button" aria-label="清空已读通知" @click="showClearConfirmation = true"><Trash2 :size="18" /></button>
      </div>
    </div>

    <section v-if="isLoading" class="empty-feed"><h2>正在加载通知...</h2></section>
    <section v-else-if="errorMessage" class="empty-feed"><CircleAlert :size="22" /><p>{{ errorMessage }}</p><button class="primary-button" @click="load">重新加载</button></section>
    <section v-else-if="notices.length === 0" class="empty-feed"><Bell :size="25" /><h2>暂时没有新通知</h2></section>
    <ol v-else class="notice-list">
      <li v-for="notice in notices" :key="notice.id">
        <button
          class="notice-item"
          :class="{ unread: notice.readStatus === 0, 'notice-item--deleted': notice.targetDeleted }"
          type="button"
          @click="openNotice(notice)"
        >
          <span class="avatar">{{ notice.sender.nickname.slice(0, 1).toUpperCase() }}</span>
          <span>
            <strong>{{ notice.sender.nickname }}</strong>
            <span>{{ notice.targetDeleted ? notice.targetDeletedMessage : notice.content }}</span>
            <small>{{ formatCompactDateTime(notice.createdAt) }}</small>
          </span>
        </button>
      </li>
    </ol>
  </section>

  <ConfirmDialog
    :visible="showClearConfirmation"
    title="清空已读通知"
    message="已读通知将被永久移除，未读通知会保留。"
    confirm-text="清空已读"
    :is-loading="isClearingRead"
    @confirm="confirmClearRead"
    @cancel="showClearConfirmation = false"
  />
</template>
