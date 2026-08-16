<script setup lang="ts">
import axios from 'axios'
import { ArrowLeft, CircleAlert, Eye, Flag, Heart, MapPinned, MessageCircle, Pencil, Reply, Send, Trash2 } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createPostComment, deletePost, deletePostComment, getPostComments, getPostDetail, likeComment, likePost, unlikeComment, unlikePost, updatePost } from '../api/posts.ts'
import { createQuestion, getQuestionsBySources } from '../api/questions.ts'
import { submitContentReport } from '../api/reports.ts'
import { errorMessageOf } from '../api/errors.ts'
import { authStore } from '../auth/auth.ts'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import QuestionAnswerPanel from '../components/QuestionAnswerPanel.vue'
import QuestionTrackingPanel from '../components/QuestionTrackingPanel.vue'
import type { CampusScope, PostComment, PostDetail, QuestionSourceSummary, QuestionTracking, ReportTargetType } from '../types/api.ts'
import { submitOnEnter } from '../utils/submitOnEnter.ts'
import { formatCompactDateTime } from '../utils/date.ts'
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

const replyBatchSize = 3
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
const editTitle = ref('')
const editContent = ref('')
const pendingDelete = ref<PendingDelete | null>(null)
const pendingReport = ref<ReportTarget | null>(null)
const reportReason = ref('')
const isSubmittingReport = ref(false)
const visibleRepliesByRoot = ref<Record<number, number>>({})
const expandedReplyRoots = ref<Set<number>>(new Set())
const questionPanelVersion = ref(0)
const answerPanelVersion = ref(0)
let activeRequest: AbortController | null = null

const visibleComments = computed(() => comments.value.filter((comment) => {
  if (comment.rootCommentId === null) return true
  const limit = visibleRepliesByRoot.value[comment.rootCommentId] ?? replyBatchSize
  const replies = comments.value.filter((item) => item.rootCommentId === comment.rootCommentId)
  return replies.findIndex((item) => item.id === comment.id) < limit
}))

const commentQuestionItems = computed(() => comments.value.flatMap((comment) => {
  const question = commentQuestion(comment)
  return question ? [{ comment, question }] : []
}))

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
function isCommentAuthor(comment: PostComment): boolean { return comment.author.id === authStore.state.user?.id }
function isPostAuthorComment(comment: PostComment): boolean { return comment.author.id === post.value?.author.id }
function canCreateCommentQuestion(comment: PostComment): boolean { return isCommentAuthor(comment) && comment.rootCommentId === null }
function commentQuestion(comment: PostComment): QuestionSourceSummary | null { return commentQuestions.value[comment.id] ?? null }
function questionStatusText(question: QuestionSourceSummary): string { return question.status === 'OPEN' ? '进行中' : '已完成' }

function initializeReplyVisibility(focusCommentId: number | null): void {
  visibleRepliesByRoot.value = {}
  expandedReplyRoots.value = new Set()
  const focusedComment = comments.value.find((comment) => comment.id === focusCommentId)
  if (!focusedComment?.rootCommentId) return
  const replies = comments.value.filter((comment) => comment.rootCommentId === focusedComment.rootCommentId)
  const focusedIndex = replies.findIndex((comment) => comment.id === focusCommentId)
  visibleRepliesByRoot.value = { [focusedComment.rootCommentId]: Math.max(replyBatchSize, focusedIndex + 1) }
  expandedReplyRoots.value = new Set([focusedComment.rootCommentId])
}

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
    initializeReplyVisibility(context.commentId)
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

function replyTotal(rootCommentId: number): number { return comments.value.filter((comment) => comment.rootCommentId === rootCommentId).length }
function visibleReplyCount(rootCommentId: number): number { return Math.min(visibleRepliesByRoot.value[rootCommentId] ?? replyBatchSize, replyTotal(rootCommentId)) }
function remainingReplies(rootCommentId: number): number { return Math.max(replyTotal(rootCommentId) - visibleReplyCount(rootCommentId), 0) }
function isLastVisibleReply(comment: PostComment): boolean {
  if (comment.rootCommentId === null) return false
  const visibleReplies = visibleComments.value.filter((item) => item.rootCommentId === comment.rootCommentId)
  return visibleReplies.at(-1)?.id === comment.id && remainingReplies(comment.rootCommentId) > 0
}
function expandReplies(rootCommentId: number): void {
  visibleRepliesByRoot.value = { ...visibleRepliesByRoot.value, [rootCommentId]: visibleReplyCount(rootCommentId) + replyBatchSize }
  expandedReplyRoots.value = new Set([...expandedReplyRoots.value, rootCommentId])
}
function expandLabel(rootCommentId: number): string {
  return expandedReplyRoots.value.has(rootCommentId) ? '继续展开' : `展开其余 ${remainingReplies(rootCommentId)} 条追评`
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
  if (!canCreateCommentQuestion(comment) || commentQuestion(comment)) return
  activeCommentQuestionId.value = comment.id
  commentQuestionText.value = ''
}

function closeCommentQuestionComposer(): void {
  activeCommentQuestionId.value = null
  commentQuestionText.value = ''
}

async function submitCommentQuestion(comment: PostComment): Promise<void> {
  const content = commentQuestionText.value.trim()
  if (!content || !canCreateCommentQuestion(comment) || commentQuestion(comment) || isSubmittingCommentQuestion.value) return
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

async function handleCommentSubmit(): Promise<void> {
  const context = readPostContext()
  const currentUserId = authStore.state.user?.id
  const content = commentContent.value.trim()
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
  editTitle.value = post.value.title
  editContent.value = post.value.content
  isEditingPost.value = true
}
function cancelEditPost(): void { isEditingPost.value = false }
async function savePost(): Promise<void> {
  const context = readPostContext()
  if (!context || !post.value || isSavingPost.value || !editTitle.value.trim() || !editContent.value.trim()) return
  isSavingPost.value = true
  try {
    await updatePost(context.postId, post.value.category.id, editTitle.value.trim(), editContent.value.trim())
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
    <section v-if="isLoading" class="empty-feed"><h2>正在加载帖子...</h2></section>
    <section v-else-if="errorMessage" class="empty-feed"><CircleAlert :size="24" /><h2>无法查看该帖子</h2><p>{{ errorMessage }}</p><button class="primary-button" type="button" @click="loadPage">重新加载</button></section>
    <template v-else-if="post">
      <article class="post-detail">
        <div class="post-meta"><span>{{ post.school.name }}<template v-if="post.school.campusName"> · {{ post.school.campusName }}</template></span><span>{{ post.category.name }}</span><span>{{ formatCompactDateTime(post.createdAt) }}</span><span v-if="post.updatedAt !== post.createdAt">修改于 {{ formatCompactDateTime(post.updatedAt) }}</span></div>
        <div v-if="isPostAuthor()" class="post-owner-actions"><button class="text-button" type="button" @click="startEditPost"><Pencil :size="14" />修改</button><button class="text-button danger" type="button" @click="requestDeletePost"><Trash2 :size="14" />删除</button></div>
        <template v-if="isEditingPost"><form class="editor-form post-edit-form" @submit.prevent="savePost"><label>标题<input v-model="editTitle" maxlength="100" /></label><label>正文<textarea v-model="editContent" rows="8" maxlength="5000" @keydown="submitOnEnter($event, savePost)" /></label><div class="page-actions"><button class="text-button" type="button" @click="cancelEditPost">取消</button><button class="primary-button" type="submit" :disabled="isSavingPost || !editTitle.trim() || !editContent.trim()">{{ isSavingPost ? '保存中...' : '保存修改' }}</button></div></form></template>
        <template v-else><h1>{{ post.title }}</h1><div class="post-author"><RouterLink class="post-author-avatar" :to="userProfileTarget(post.author.id)" :aria-label="`查看 ${post.author.nickname} 的主页`"><img v-if="post.author.avatarUrl" :src="post.author.avatarUrl" alt="" /><template v-else>{{ post.author.nickname.slice(0, 1).toUpperCase() }}</template></RouterLink><span>发布者：</span><RouterLink :to="userProfileTarget(post.author.id)">{{ post.author.nickname }}</RouterLink></div><div class="post-content">{{ post.content }}</div></template>
        <div v-if="!isEditingPost" class="post-detail-footer"><div class="post-stats"><span><Eye :size="16" />{{ post.viewCount }}</span><span><MessageCircle :size="16" />{{ post.commentCount }}</span></div><div class="post-detail-actions"><button v-if="!isPostAuthor()" class="text-button" type="button" @click="openReport('POST', post.id, '这篇帖子')"><Flag :size="15" />举报</button><button class="like-button" :class="{ active: post.liked }" type="button" :disabled="isLiking" @click="handleLike"><Heart :size="17" :fill="post.liked ? 'currentColor' : 'none'" />{{ post.liked ? '已赞' : '点赞' }} {{ post.likeCount }}</button></div></div>
      </article>

      <section v-if="!isEditingPost" class="post-question-workspace">
        <QuestionTrackingPanel :key="questionPanelVersion" source-type="POST" :source-id="post.id" :can-manage="isPostAuthor()" @loaded="handleQuestionLoaded" @updated="handleQuestionUpdated" @answer-requested="startCandidateAnswer" />
        <QuestionAnswerPanel v-if="postQuestion" :key="`${postQuestion.id}-${postQuestion.status}-${answerPanelVersion}`" :question="postQuestion" :can-manage="isPostAuthor()" @updated="handleQuestionAnswerUpdated" />
      </section>

      <section v-if="commentQuestionItems.length > 0" class="comment-question-section" aria-labelledby="comment-questions-heading">
        <div class="question-tracking-heading">
          <div><p class="eyebrow">COMMENT QUESTIONS</p><h2 id="comment-questions-heading">评论中的问题</h2></div>
          <span class="comment-question-count">{{ commentQuestionItems.length }} 个问题</span>
        </div>
        <div class="comment-question-summary-list">
          <article v-for="item in commentQuestionItems" :key="item.question.id" class="comment-question-summary">
            <div>
              <div class="comment-question-summary-meta"><span class="question-status" :class="`question-status--${item.question.status.toLowerCase()}`">{{ questionStatusText(item.question) }}</span><span>来自 {{ item.comment.author.nickname }} 的评论</span></div>
              <strong>{{ item.question.questionText }}</strong>
              <p v-if="item.question.approvedAnswerCount > 0">已通过 {{ item.question.approvedAnswerCount }} 条候选答复</p>
            </div>
            <div class="comment-question-actions">
              <button class="text-button" type="button" @click="focusComment(item.comment.id)">查看评论</button>
              <RouterLink class="text-button" :to="{ name: 'question-answer-list', params: { questionId: item.question.id }, query: { source: 'comment-questions', postId: post.id } }">候选答复</RouterLink>
              <button v-if="item.question.status === 'OPEN' && item.question.asker.id !== authStore.state.user?.id" class="text-button" type="button" @click="startCommentQuestionAnswer(item.comment, item.question)"><Send :size="14" />快速作答</button>
            </div>
          </article>
        </div>
      </section>

      <section class="comments-section" aria-labelledby="comments-heading">
        <div class="comments-heading"><div><p class="eyebrow">DISCUSSION</p><h2 id="comments-heading">评论 {{ post.commentCount }}</h2></div></div>
        <form class="comment-form" @submit.prevent="handleCommentSubmit"><div class="comment-form-heading"><label for="comment-content">参与讨论</label><button v-if="selectedPostQuestion" class="text-button" type="button" @click="cancelPostCandidateAnswer">取消候选答复</button></div><p v-if="selectedPostQuestion" class="candidate-answer-context">正在作为“{{ selectedPostQuestion.questionText }}”的候选答复</p><textarea id="comment-content" v-model="commentContent" maxlength="500" rows="4" placeholder="写下你的想法..." @keydown="submitOnEnter($event, handleCommentSubmit)" /><div class="comment-form-footer"><span>{{ commentContent.length }}/500 · Enter 发送，Shift + Enter 换行</span><button class="primary-button" type="submit" :disabled="isSubmittingComment || !commentContent.trim()"><Send :size="16" />{{ isSubmittingComment ? '发布中...' : '发布评论' }}</button></div></form>
        <p v-if="interactionMessage" class="form-error"><CircleAlert :size="16" />{{ interactionMessage }}</p>
        <section v-if="isCommentLoading" class="comments-status"><p>正在加载评论...</p></section>
        <section v-else-if="comments.length === 0" class="comments-status"><MapPinned :size="22" /><p>暂时还没有评论，来留下第一条讨论吧。</p></section>
        <ol v-else class="comment-list">
          <template v-for="comment in visibleComments" :key="comment.id">
            <li :id="`comment-${comment.id}`" class="comment-item" :class="{ 'comment-item--reply': comment.rootCommentId !== null, 'comment-item--focused': comment.id === readPostContext()?.commentId }">
              <RouterLink class="comment-avatar" :class="{ 'comment-avatar--image': comment.author.avatarUrl }" :to="userProfileTarget(comment.author.id, comment.id)">
                <img v-if="comment.author.avatarUrl" :src="comment.author.avatarUrl" alt="" />
                <template v-else>{{ comment.author.nickname.slice(0, 1).toUpperCase() }}</template>
              </RouterLink>
              <div class="comment-body">
                <div class="comment-meta">
                  <RouterLink class="comment-author-link" :to="userProfileTarget(comment.author.id, comment.id)">{{ comment.author.nickname }}</RouterLink>
                  <span v-if="isPostAuthorComment(comment)" class="author-badge">作者</span>
                  <span v-if="comment.author.schoolName" class="school-tag">{{ comment.author.schoolName }}<template v-if="comment.author.campusName"> · {{ comment.author.campusName }}</template></span>
                  <span>{{ formatCompactDateTime(comment.createdAt) }}</span>
                </div>
                <p><template v-if="comment.replyToNickname">回复 <RouterLink v-if="comment.replyToUserId" class="reply-user-link" :to="userProfileTarget(comment.replyToUserId, comment.id)">@{{ comment.replyToNickname }}</RouterLink><strong v-else>@{{ comment.replyToNickname }}</strong>：</template>{{ comment.content }}</p>
                <div v-if="commentQuestion(comment)" class="comment-question-inline">
                  <div><span class="question-status" :class="`question-status--${commentQuestion(comment)!.status.toLowerCase()}`">{{ questionStatusText(commentQuestion(comment)!) }}</span><span>{{ commentQuestion(comment)!.questionText }}</span></div>
                  <div class="comment-question-actions">
                    <RouterLink class="text-button" :to="{ name: 'question-answer-list', params: { questionId: commentQuestion(comment)!.id }, query: { source: 'post', postId: post.id, commentId: comment.id } }">候选答复</RouterLink>
                    <button v-if="commentQuestion(comment)!.status === 'OPEN' && commentQuestion(comment)!.asker.id !== authStore.state.user?.id" class="text-button" type="button" @click="startCommentQuestionAnswer(comment, commentQuestion(comment)!)"><Send :size="14" />快速作答</button>
                  </div>
                </div>
                <div class="comment-actions">
                  <button class="text-button" type="button" @click="startReply(comment)"><Reply :size="14" />{{ comment.rootCommentId === null ? '追评' : '回复' }}</button>
                  <button v-if="canCreateCommentQuestion(comment) && !commentQuestion(comment)" class="text-button" type="button" @click="openCommentQuestionComposer(comment)">发起问题追踪</button>
                  <button class="text-button" :class="{ active: comment.liked }" type="button" :disabled="likingCommentId === comment.id" @click="handleCommentLike(comment)"><Heart :size="14" :fill="comment.liked ? 'currentColor' : 'none'" />{{ comment.liked ? '已赞' : '点赞' }} {{ comment.likeCount }}</button>
                  <button v-if="!isCommentAuthor(comment)" class="text-button" type="button" @click="openReport('COMMENT', comment.id, '这条评论')"><Flag :size="14" />举报</button>
                  <button v-if="isCommentAuthor(comment)" class="text-button danger" type="button" @click="requestDeleteComment(comment)"><Trash2 :size="14" />删除</button>
                </div>
                <form v-if="activeCommentQuestionId === comment.id" class="inline-comment-form" @submit.prevent="submitCommentQuestion(comment)">
                  <label :for="`comment-question-${comment.id}`">你想持续追踪什么答案？</label>
                  <textarea :id="`comment-question-${comment.id}`" v-model="commentQuestionText" maxlength="300" rows="3" placeholder="写下需要追踪的问题..." autofocus @keydown="submitOnEnter($event, () => submitCommentQuestion(comment))" />
                  <div><button class="text-button" type="button" @click="closeCommentQuestionComposer">取消</button><button class="primary-button" type="submit" :disabled="!commentQuestionText.trim() || isSubmittingCommentQuestion">{{ isSubmittingCommentQuestion ? '发起中...' : '确认发起' }}</button></div>
                </form>
                <form v-if="activeReplyCommentId === comment.id" class="inline-comment-form" @submit.prevent="handleInlineReply(comment)">
                  <label :for="`reply-${comment.id}`">{{ inlineCandidateQuestionId ? '候选答复' : (comment.rootCommentId === null ? '追评' : `回复 ${comment.author.nickname}`) }}</label>
                  <p v-if="inlineCandidateQuestionId" class="candidate-answer-context">仅会作为当前问题的候选答复提交</p>
                  <textarea :id="`reply-${comment.id}`" v-model="inlineReplyContent" maxlength="500" rows="3" placeholder="写下你的评论..." @keydown="submitOnEnter($event, () => handleInlineReply(comment))" />
                  <div><button class="text-button" type="button" @click="cancelReply">取消</button><button class="primary-button" type="submit" :disabled="!inlineReplyContent.trim() || submittingReplyCommentId === comment.id">{{ submittingReplyCommentId === comment.id ? '发布中...' : '发布' }}</button></div>
                </form>
              </div>
            </li>
            <li v-if="isLastVisibleReply(comment)" class="reply-expand-item"><button class="text-button" type="button" @click="expandReplies(comment.rootCommentId!)">{{ expandLabel(comment.rootCommentId!) }}</button></li>
          </template>
        </ol>
        <div v-if="readPostContext()?.commentId" class="comment-focus-spacer" aria-hidden="true"></div>
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
.post-detail-actions { display: flex; align-items: center; gap: 0.75rem; }
.report-dialog-backdrop { position: fixed; inset: 0; z-index: var(--z-overlay); display: grid; place-items: center; padding: 1rem; background: var(--bg-overlay); }
.report-dialog { width: min(100%, 520px); display: grid; gap: 1rem; padding: 1.5rem; border-radius: var(--radius-lg); background: var(--bg-surface); box-shadow: var(--shadow-dialog); }
.report-dialog h2 { margin: 0; }
.report-dialog textarea { width: 100%; resize: vertical; border: 1px solid var(--border-default); border-radius: var(--radius-md); padding: 0.75rem; font: inherit; color: var(--text-primary); background: var(--bg-surface); }
</style>
