<script setup lang="ts">
import axios from 'axios'
import { ArrowLeft, CircleAlert, Flag } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createPostComment, deletePost, deletePostComment, getPostComments, getPostDetail, likeComment, likePost, unlikeComment, unlikePost, updatePost } from '../api/posts.ts'
import { createQuestion, getQuestionsBySources } from '../api/questions.ts'
import { submitContentReport } from '../api/reports.ts'
import { errorMessageOf } from '../api/errors.ts'
import { authStore } from '../auth/auth.ts'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PostCommentForm from '../components/PostCommentForm.vue'
import PostCommentList from '../components/PostCommentList.vue'
import PostDetailHeader from '../components/PostDetailHeader.vue'
import QuestionAnswerPanel from '../components/QuestionAnswerPanel.vue'
import QuestionTrackingPanel from '../components/QuestionTrackingPanel.vue'
import type { CampusScope, PostComment, PostDetail, QuestionSourceSummary, QuestionTracking, ReportTargetType } from '../types/api.ts'
import { readFeedPreferences } from '../utils/feedPreferences.ts'

interface PostContext {
  postId: number
  scope: CampusScope
  sort: 'latest' | 'hot'
  radiusKm: number
  commentId: number | null
  source: string | null
}

type PendingDelete = { kind: 'post' } | { kind: 'comment'; comment: PostComment }
type ReportTarget = { type: ReportTargetType; id: number; label: string }

const campusScopes = new Set<CampusScope>(['CAMPUS', 'UNIVERSITY', 'NEARBY_10', 'NEARBY_20', 'CITY'])
const route = useRoute()
const router = useRouter()
const post = ref<PostDetail | null>(null)
const comments = ref<PostComment[]>([])
const postQuestion = ref<QuestionTracking | null>(null)
const commentQuestions = ref<Record<number, QuestionSourceSummary>>({})
const isLoading = ref(true)
const isCommentLoading = ref(true)
const isLiking = ref(false)
const isSubmittingComment = ref(false)
const isSavingPost = ref(false)
const isDeleting = ref(false)
const likingCommentId = ref<number | null>(null)
const errorMessage = ref('')
const interactionMessage = ref('')
const commentContent = ref('')
const selectedPostQuestionId = ref<number | null>(null)
const activeReplyCommentId = ref<number | null>(null)
const inlineReplyContent = ref('')
const inlineCandidateQuestionId = ref<number | null>(null)
const submittingReplyCommentId = ref<number | null>(null)
const activeCommentQuestionId = ref<number | null>(null)
const commentQuestionText = ref('')
const isSubmittingCommentQuestion = ref(false)
const isEditingPost = ref(false)
const pendingDelete = ref<PendingDelete | null>(null)
const pendingReport = ref<ReportTarget | null>(null)
const reportReason = ref('')
const isSubmittingReport = ref(false)
const replyFocus = ref<{ commentId: number | null; version: number }>({ commentId: null, version: 0 })
const questionPanelVersion = ref(0)
const answerPanelVersion = ref(0)
let activeRequest: AbortController | null = null

const selectedPostQuestion = computed(() => postQuestion.value?.id === selectedPostQuestionId.value ? postQuestion.value : null)

const backTarget = computed(() => {
  const context = readPostContext()
  if (context?.source === 'notice') return { name: 'notices' }
  if (context?.source === 'questions') return { name: 'questions' }
  if (context?.source === 'question-answers') {
    const questionId = Number(Array.isArray(route.query.questionId) ? NaN : route.query.questionId)
    if (Number.isSafeInteger(questionId) && questionId > 0) return { name: 'question-answer-list', params: { questionId } }
  }
  if (context?.source === 'profile') {
    const profileUserId = Number(Array.isArray(route.query.profileUserId) ? NaN : route.query.profileUserId)
    const tab = Array.isArray(route.query.tab) ? undefined : route.query.tab
    const query = tab === 'comments' || tab === 'likes' ? { tab } : undefined
    if (Number.isSafeInteger(profileUserId) && profileUserId > 0) {
      return profileUserId === authStore.state.user?.id
        ? { name: 'profile', query }
        : { name: 'user-profile', params: { id: profileUserId }, query }
    }
  }
  const preferences = readFeedPreferences()
  return { name: 'feed', query: { scope: context?.scope ?? preferences.scope, sort: context?.sort ?? preferences.sort } }
})

const backLabel = computed(() => {
  const source = readPostContext()?.source
  if (source === 'notice') return '返回通知'
  if (source === 'questions') return '返回问题追踪'
  if (source === 'question-answers') return '返回候选答复'
  if (source === 'profile') return '返回个人主页'
  return '返回校园动态'
})

function readPostContext(): PostContext | null {
  const postId = Number(Array.isArray(route.params.id) ? NaN : route.params.id)
  const rawScope = Array.isArray(route.query.scope) ? null : route.query.scope
  const rawSort = Array.isArray(route.query.sort) ? null : route.query.sort
  const rawCommentId = Array.isArray(route.query.commentId) ? null : route.query.commentId
  const source = Array.isArray(route.query.source) ? null : route.query.source
  const preferences = readFeedPreferences()
  const scope = campusScopes.has(rawScope as CampusScope) ? rawScope as CampusScope : preferences.scope
  const sort = rawSort === 'hot' || rawSort === 'latest' ? rawSort : preferences.sort
  const commentId = rawCommentId == null ? null : Number(rawCommentId)
  if (!Number.isSafeInteger(postId) || postId <= 0 || (commentId !== null && (!Number.isSafeInteger(commentId) || commentId <= 0))) return null
  return { postId, scope, sort, radiusKm: 10, commentId, source: source ?? null }
}

function isPostAuthor(): boolean { return post.value?.author.id === authStore.state.user?.id }

async function afterPaint(): Promise<void> {
  await new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
}

function centerComment(commentId: number): void {
  const target = document.getElementById(`comment-${commentId}`)
  if (!target) return
  const rect = target.getBoundingClientRect()
  const targetTop = window.scrollY + rect.top - Math.max((window.innerHeight - Math.min(rect.height, window.innerHeight * 0.65)) / 2, 24)
  window.scrollTo({ top: Math.max(0, targetTop), behavior: 'auto' })
}

function centerCommentQuestions(): void {
  const target = document.getElementById('comment-questions-heading')
  if (!target) return
  const rect = target.getBoundingClientRect()
  const targetTop = window.scrollY + rect.top - Math.max((window.innerHeight - Math.min(rect.height, window.innerHeight * 0.65)) / 2, 24)
  window.scrollTo({ top: Math.max(0, targetTop), behavior: 'auto' })
}

async function focusComment(commentId: number | null): Promise<void> {
  if (commentId === null) return
  for (let attempt = 0; attempt < 10; attempt += 1) {
    await nextTick()
    await afterPaint()
    const target = document.getElementById(`comment-${commentId}`)
    if (target) {
      centerComment(commentId)
      ;[120, 360, 720].forEach((delay) => window.setTimeout(() => centerComment(commentId), delay))
      return
    }
    await new Promise<void>((resolve) => window.setTimeout(resolve, 60))
  }
}

async function focusCommentQuestions(): Promise<void> {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    await nextTick()
    await afterPaint()
    if (document.getElementById('comment-questions-heading')) {
      centerCommentQuestions()
      ;[120, 360].forEach((delay) => window.setTimeout(centerCommentQuestions, delay))
      return
    }
    await new Promise<void>((resolve) => window.setTimeout(resolve, 60))
  }
}

async function loadCommentQuestions(): Promise<void> {
  const sourceIds = comments.value.map((comment) => comment.id)
  if (sourceIds.length === 0) {
    commentQuestions.value = {}
    return
  }
  try {
    commentQuestions.value = await getQuestionsBySources('COMMENT', sourceIds)
  } catch (error) {
    commentQuestions.value = {}
    interactionMessage.value = errorMessageOf(error, '评论问题加载失败，请稍后重试。')
  }
}

async function loadComments(context: PostContext, signal?: AbortSignal): Promise<void> {
  isCommentLoading.value = true
  try {
    const pageRequest = getPostComments(context.postId, { radiusKm: context.radiusKm, page: 1, size: 50 }, signal ? { signal } : undefined)
    const focusRequest = context.commentId === null ? null : getPostComments(context.postId, { radiusKm: context.radiusKm, page: 1, size: 50, focusCommentId: context.commentId }, signal ? { signal } : undefined)
    const page = await pageRequest
    const focused = focusRequest === null ? null : await focusRequest
    const commentMap = new Map(page.records.map((comment) => [comment.id, comment]))
    focused?.records.forEach((comment) => commentMap.set(comment.id, comment))
    comments.value = [...commentMap.values()].sort((left, right) => {
      const leftRootId = left.rootCommentId ?? left.id
      const rightRootId = right.rootCommentId ?? right.id
      if (leftRootId !== rightRootId) return leftRootId - rightRootId
      if (left.rootCommentId === null && right.rootCommentId !== null) return -1
      if (left.rootCommentId !== null && right.rootCommentId === null) return 1
      return left.createdAt.localeCompare(right.createdAt) || left.id - right.id
    })
    replyFocus.value = { commentId: context.commentId, version: replyFocus.value.version + 1 }
    await loadCommentQuestions()
  } finally {
    isCommentLoading.value = false
  }
}

async function loadPage(): Promise<void> {
  const context = readPostContext()
  if (!context) {
    post.value = null
    comments.value = []
    errorMessage.value = '帖子地址无效，请返回校园动态重新选择。'
    isLoading.value = false
    isCommentLoading.value = false
    return
  }
  activeRequest?.abort()
  const request = new AbortController()
  activeRequest = request
  isLoading.value = true
  errorMessage.value = ''
  interactionMessage.value = ''
  post.value = null
  comments.value = []
  commentQuestions.value = {}
  try {
    const [detail] = await Promise.all([getPostDetail(context.postId, context.radiusKm, { signal: request.signal }), loadComments(context, request.signal)])
    if (activeRequest === request) {
      post.value = detail
    }
  } catch (error) {
    if (!axios.isCancel(error) && activeRequest === request) errorMessage.value = errorMessageOf(error, '帖子暂时无法加载，请稍后重试。')
  } finally {
    if (activeRequest === request) {
      isLoading.value = false
      isCommentLoading.value = false
      activeRequest = null
    }
  }
}

function userProfileTarget(userId: number, commentId?: number) {
  const context = readPostContext()
  const postId = post.value?.id ?? context?.postId
  if (!postId) return { name: 'user-profile', params: { id: userId } }
  const preferences = readFeedPreferences()
  const query: Record<string, string> = { source: 'post-detail', postId: String(postId), scope: context?.scope ?? preferences.scope, sort: context?.sort ?? preferences.sort }
  if (commentId !== undefined) query.commentId = String(commentId)
  return { name: 'user-profile', params: { id: userId }, query }
}

async function handleLike(): Promise<void> {
  const context = readPostContext()
  if (!context || !post.value || isLiking.value) return
  isLiking.value = true
  try {
    const result = post.value.liked ? await unlikePost(context.postId, context.radiusKm) : await likePost(context.postId, context.radiusKm)
    post.value = { ...post.value, liked: result.liked, likeCount: result.likeCount }
  } catch (error) { interactionMessage.value = errorMessageOf(error, '点赞操作失败，请稍后重试。') } finally { isLiking.value = false }
}

async function handleCommentLike(comment: PostComment): Promise<void> {
  const context = readPostContext()
  if (!context || likingCommentId.value !== null) return
  likingCommentId.value = comment.id
  try {
    const result = comment.liked ? await unlikeComment(comment.id, context.radiusKm) : await likeComment(comment.id, context.radiusKm)
    comments.value = comments.value.map((item) => item.id === comment.id ? { ...item, liked: result.liked, likeCount: result.likeCount } : item)
  } catch (error) { interactionMessage.value = errorMessageOf(error, '评论点赞失败，请稍后重试。') } finally { likingCommentId.value = null }
}

async function startReply(comment: PostComment): Promise<void> {
  activeReplyCommentId.value = comment.id
  inlineReplyContent.value = ''
  inlineCandidateQuestionId.value = null
  interactionMessage.value = ''
  await nextTick()
  document.getElementById(`reply-${comment.id}`)?.focus()
}

async function startCommentQuestionAnswer(comment: PostComment, question: QuestionSourceSummary): Promise<void> {
  if (question.status !== 'OPEN' || question.asker.id === authStore.state.user?.id) return
  await startReply(comment)
  inlineCandidateQuestionId.value = question.id
}

function cancelReply(): void {
  activeReplyCommentId.value = null
  inlineReplyContent.value = ''
  inlineCandidateQuestionId.value = null
}

function openCommentQuestionComposer(comment: PostComment): void {
  if (!isCommentQuestionAllowed(comment)) return
  activeCommentQuestionId.value = comment.id
  commentQuestionText.value = ''
}

function isCommentQuestionAllowed(comment: PostComment): boolean {
  return comment.author.id === authStore.state.user?.id && comment.rootCommentId === null && commentQuestions.value[comment.id] === undefined
}

function closeCommentQuestionComposer(): void {
  activeCommentQuestionId.value = null
  commentQuestionText.value = ''
}

async function submitCommentQuestion(comment: PostComment): Promise<void> {
  const content = commentQuestionText.value.trim()
  if (!content || !isCommentQuestionAllowed(comment) || isSubmittingCommentQuestion.value) return
  isSubmittingCommentQuestion.value = true
  try {
    const created = await createQuestion('COMMENT', comment.id, content)
    commentQuestions.value = { ...commentQuestions.value, [comment.id]: created }
    closeCommentQuestionComposer()
  } catch (error) { interactionMessage.value = errorMessageOf(error, '问题追踪发起失败，请稍后重试。') } finally { isSubmittingCommentQuestion.value = false }
}

function handleQuestionLoaded(question: QuestionTracking | null): void { postQuestion.value = question }
function handleQuestionUpdated(question: QuestionTracking | null): void {
  postQuestion.value = question
  questionPanelVersion.value += 1
  answerPanelVersion.value += 1
}
function handleQuestionAnswerUpdated(question: QuestionTracking): void {
  if (postQuestion.value?.id === question.id) postQuestion.value = question
  questionPanelVersion.value += 1
  answerPanelVersion.value += 1
}

async function startCandidateAnswer(question: QuestionTracking): Promise<void> {
  if (question.status !== 'OPEN' || question.asker.id === authStore.state.user?.id) return
  selectedPostQuestionId.value = question.id
  await nextTick()
  const composer = document.getElementById('comment-content')
  composer?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  composer?.focus()
}

function cancelPostCandidateAnswer(): void { selectedPostQuestionId.value = null }

async function handleCommentSubmit(rawContent: string): Promise<void> {
  const context = readPostContext()
  const currentUserId = authStore.state.user?.id
  const content = rawContent.trim()
  if (!context || !post.value || !currentUserId || isSubmittingComment.value) return
  if (!content) { interactionMessage.value = '评论内容不能为空。'; return }
  const answerQuestionId = selectedPostQuestion.value?.status === 'OPEN' ? selectedPostQuestion.value.id : undefined
  isSubmittingComment.value = true
  try {
    await createPostComment(context.postId, context.radiusKm, content, currentUserId, undefined, answerQuestionId)
    if (answerQuestionId) answerPanelVersion.value += 1
    commentContent.value = ''
    selectedPostQuestionId.value = null
    post.value = { ...post.value, commentCount: post.value.commentCount + 1 }
    await loadComments({ ...context, commentId: null })
  } catch (error) { interactionMessage.value = errorMessageOf(error, '评论发布失败，请稍后重试。') } finally { isSubmittingComment.value = false }
}

async function handleInlineReply(comment: PostComment): Promise<void> {
  const context = readPostContext()
  const currentUserId = authStore.state.user?.id
  const content = inlineReplyContent.value.trim()
  if (!context || !currentUserId || !content || submittingReplyCommentId.value !== null) return
  const answerQuestionId = inlineCandidateQuestionId.value ?? undefined
  submittingReplyCommentId.value = comment.id
  try {
    await createPostComment(context.postId, context.radiusKm, content, currentUserId, comment.id, answerQuestionId)
    if (answerQuestionId) answerPanelVersion.value += 1
    cancelReply()
    await loadComments({ ...context, commentId: null })
  } catch (error) { interactionMessage.value = errorMessageOf(error, '评论发布失败，请稍后重试。') } finally { submittingReplyCommentId.value = null }
}

function requestDeleteComment(comment: PostComment): void { pendingDelete.value = { kind: 'comment', comment } }
function requestDeletePost(): void { pendingDelete.value = { kind: 'post' } }
function openReport(type: ReportTargetType, id: number, label: string): void {
  pendingReport.value = { type, id, label }
  reportReason.value = ''
}
function closeReport(): void {
  if (isSubmittingReport.value) return
  pendingReport.value = null
  reportReason.value = ''
}
async function submitReport(): Promise<void> {
  const target = pendingReport.value
  const reason = reportReason.value.trim()
  if (!target || !reason || isSubmittingReport.value) return
  isSubmittingReport.value = true
  try {
    await submitContentReport(target.type, target.id, reason)
    interactionMessage.value = '举报已提交，管理员会尽快处理。'
    pendingReport.value = null
    reportReason.value = ''
  } catch (error) {
    interactionMessage.value = errorMessageOf(error, '举报提交失败，请稍后重试。')
  } finally {
    isSubmittingReport.value = false
  }
}
async function confirmDelete(): Promise<void> {
  const context = readPostContext()
  if (!context || !pendingDelete.value) return
  isDeleting.value = true
  try {
    if (pendingDelete.value.kind === 'post') {
      await deletePost(context.postId)
      await router.replace(backTarget.value)
      return
    }
    await deletePostComment(pendingDelete.value.comment.id)
    if (post.value) post.value = { ...post.value, commentCount: Math.max(post.value.commentCount - 1, 0) }
    await loadComments({ ...context, commentId: null })
  } catch (error) { interactionMessage.value = errorMessageOf(error, '删除操作失败，请稍后重试。') } finally { isDeleting.value = false; pendingDelete.value = null }
}

function startEditPost(): void {
  if (!post.value) return
  isEditingPost.value = true
}
function cancelEditPost(): void { isEditingPost.value = false }
async function savePost(title: string, content: string): Promise<void> {
  const context = readPostContext()
  if (!context || !post.value || isSavingPost.value || !title.trim() || !content.trim()) return
  isSavingPost.value = true
  try {
    await updatePost(context.postId, post.value.category.id, title.trim(), content.trim())
    isEditingPost.value = false
    await loadPage()
  } catch (error) { interactionMessage.value = errorMessageOf(error, '帖子修改失败，请稍后重试。') } finally { isSavingPost.value = false }
}

watch(() => route.fullPath, () => void loadPage(), { immediate: true })
watch(
  () => [isLoading.value, isCommentLoading.value, route.fullPath] as const,
  ([postLoading, commentLoading]) => {
    const context = readPostContext()
    if (postLoading || commentLoading || !post.value || !context) return
    if (context.commentId !== null) {
      void focusComment(context.commentId)
    } else if (context.source === 'comment-questions') {
      void focusCommentQuestions()
    }
  },
  { flush: 'post' },
)
onBeforeUnmount(() => activeRequest?.abort())
</script>

<template>
  <section class="content-page post-detail-page">
    <RouterLink class="detail-back" :to="backTarget"><ArrowLeft :size="18" />返回 {{ backLabel.replace('返回', '') }}</RouterLink>
    <PostDetailHeader :post="post" :is-post-author="isPostAuthor()" :is-loading="isLoading" :error-message="errorMessage"
      :is-editing-post="isEditingPost" :is-liking="isLiking" :is-saving-post="isSavingPost" :user-profile-target="userProfileTarget"
      @edit-post="startEditPost" @cancel-edit="cancelEditPost" @save-edit="savePost" @delete-post="requestDeletePost"
      @like="handleLike" @report="openReport" @reload="loadPage" />

    <template v-if="post">
      <section v-if="!isEditingPost" class="post-question-workspace">
        <QuestionTrackingPanel :key="questionPanelVersion" source-type="POST" :source-id="post.id" :can-manage="isPostAuthor()" @loaded="handleQuestionLoaded" @updated="handleQuestionUpdated" @answer-requested="startCandidateAnswer" />
        <QuestionAnswerPanel v-if="postQuestion" :key="`${postQuestion.id}-${postQuestion.status}-${answerPanelVersion}`" :question="postQuestion" :can-manage="isPostAuthor()" @updated="handleQuestionAnswerUpdated" />
      </section>

      <section class="comments-section" aria-labelledby="comments-heading">
        <div class="comments-heading"><div><p class="eyebrow">DISCUSSION</p><h2 id="comments-heading">评论 {{ post.commentCount }}</h2></div></div>
        <PostCommentForm :post="post" :selected-post-question="selectedPostQuestion" v-model:content="commentContent"
          :is-submitting="isSubmittingComment" @submit-comment="handleCommentSubmit" @cancel-answer="cancelPostCandidateAnswer" />
        <p v-if="interactionMessage" class="form-error"><CircleAlert :size="16" />{{ interactionMessage }}</p>
        <PostCommentList :post="post" :comments="comments" :is-comment-loading="isCommentLoading" :comment-questions="commentQuestions"
          :focused-comment-id="readPostContext()?.commentId ?? null" :reply-focus="replyFocus" :liking-comment-id="likingCommentId"
          :active-reply-comment-id="activeReplyCommentId" v-model:inline-reply-content="inlineReplyContent"
          :inline-candidate-question-id="inlineCandidateQuestionId" :submitting-reply-comment-id="submittingReplyCommentId"
          :active-comment-question-id="activeCommentQuestionId" v-model:comment-question-text="commentQuestionText"
          :is-submitting-comment-question="isSubmittingCommentQuestion" :user-profile-target="userProfileTarget"
          @like-comment="handleCommentLike" @start-reply="startReply" @cancel-reply="cancelReply" @submit-reply="handleInlineReply"
          @open-question-composer="openCommentQuestionComposer" @close-question-composer="closeCommentQuestionComposer"
          @submit-comment-question="submitCommentQuestion" @answer-comment-question="startCommentQuestionAnswer"
          @focus-comment="focusComment" @report-comment="(comment) => openReport('COMMENT', comment.id, '这条评论')"
          @delete-comment="requestDeleteComment" />
      </section>
    </template>
  </section>
  <div v-if="pendingReport" class="report-dialog-backdrop" @click.self="closeReport">
    <form class="report-dialog" @submit.prevent="submitReport">
      <h2>举报内容</h2>
      <p class="muted">请说明举报 {{ pendingReport.label }} 的原因，管理员将结合内容进行处理。</p>
      <textarea v-model="reportReason" rows="4" maxlength="500" placeholder="例如：广告、骚扰、虚假信息等" autofocus />
      <div class="page-actions"><button class="secondary-button" type="button" :disabled="isSubmittingReport" @click="closeReport">取消</button><button class="primary-button" type="submit" :disabled="isSubmittingReport || !reportReason.trim()"><Flag :size="16" />{{ isSubmittingReport ? '提交中…' : '提交举报' }}</button></div>
    </form>
  </div>
  <ConfirmDialog :visible="pendingDelete !== null" :title="pendingDelete?.kind === 'post' ? '删除帖子' : '删除评论'" message="删除后无法恢复。" confirm-text="确认删除" :danger="true" :is-loading="isDeleting" @confirm="confirmDelete" @cancel="pendingDelete = null" />
</template>

<style scoped>
.report-dialog-backdrop { position: fixed; inset: 0; z-index: var(--z-overlay); display: grid; place-items: center; padding: 1rem; background: var(--bg-overlay); }
.report-dialog { width: min(100%, 520px); display: grid; gap: 1rem; padding: 1.5rem; border-radius: var(--radius-lg); background: var(--bg-surface); box-shadow: var(--shadow-dialog); }
.report-dialog h2 { margin: 0; }
.report-dialog textarea { width: 100%; resize: vertical; border: 1px solid var(--border-default); border-radius: var(--radius-md); padding: 0.75rem; font: inherit; color: var(--text-primary); background: var(--bg-surface); }
</style>
