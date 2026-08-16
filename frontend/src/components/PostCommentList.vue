<script setup lang="ts">
import { Flag, Heart, MapPinned, Reply, Send, Trash2 } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { authStore } from '../auth/auth.ts'
import type { PostComment, PostDetail, QuestionSourceSummary } from '../types/api.ts'
import { formatCompactDateTime } from '../utils/date.ts'
import { submitOnEnter } from '../utils/submitOnEnter.ts'

const replyBatchSize = 3

const props = defineProps<{
  post: PostDetail
  comments: PostComment[]
  isCommentLoading: boolean
  commentQuestions: Record<number, QuestionSourceSummary>
  focusedCommentId: number | null
  replyFocus: { commentId: number | null; version: number }
  likingCommentId: number | null
  activeReplyCommentId: number | null
  inlineReplyContent: string
  inlineCandidateQuestionId: number | null
  submittingReplyCommentId: number | null
  activeCommentQuestionId: number | null
  commentQuestionText: string
  isSubmittingCommentQuestion: boolean
  userProfileTarget: (userId: number, commentId?: number) => RouteLocationRaw
}>()

const emit = defineEmits<{
  'like-comment': [comment: PostComment]
  'start-reply': [comment: PostComment]
  'cancel-reply': []
  'submit-reply': [comment: PostComment]
  'open-question-composer': [comment: PostComment]
  'close-question-composer': []
  'submit-comment-question': [comment: PostComment]
  'report-comment': [comment: PostComment]
  'delete-comment': [comment: PostComment]
  'answer-comment-question': [comment: PostComment, question: QuestionSourceSummary]
  'focus-comment': [commentId: number]
  'update:inlineReplyContent': [value: string]
  'update:commentQuestionText': [value: string]
}>()

const visibleRepliesByRoot = ref<Record<number, number>>({})
const expandedReplyRoots = ref<Set<number>>(new Set())

const visibleComments = computed(() => props.comments.filter((comment) => {
  if (comment.rootCommentId === null) return true
  const limit = visibleRepliesByRoot.value[comment.rootCommentId] ?? replyBatchSize
  const replies = props.comments.filter((item) => item.rootCommentId === comment.rootCommentId)
  return replies.findIndex((item) => item.id === comment.id) < limit
}))

const commentQuestionItems = computed(() => props.comments.flatMap((comment) => {
  const question = commentQuestion(comment)
  return question ? [{ comment, question }] : []
}))

const inlineReplyModel = computed({
  get: () => props.inlineReplyContent,
  set: (value: string) => emit('update:inlineReplyContent', value),
})

const commentQuestionTextModel = computed({
  get: () => props.commentQuestionText,
  set: (value: string) => emit('update:commentQuestionText', value),
})

watch(
  () => props.replyFocus,
  (focus) => initializeReplyVisibility(focus.commentId),
  { immediate: true },
)

function isCommentAuthor(comment: PostComment): boolean { return comment.author.id === authStore.state.user?.id }
function isPostAuthorComment(comment: PostComment): boolean { return comment.author.id === props.post.author.id }
function canCreateCommentQuestion(comment: PostComment): boolean { return isCommentAuthor(comment) && comment.rootCommentId === null }
function commentQuestion(comment: PostComment): QuestionSourceSummary | null { return props.commentQuestions[comment.id] ?? null }
function questionStatusText(question: QuestionSourceSummary): string { return question.status === 'OPEN' ? '进行中' : '已完成' }

function initializeReplyVisibility(focusCommentId: number | null): void {
  visibleRepliesByRoot.value = {}
  expandedReplyRoots.value = new Set()
  const focusedComment = props.comments.find((comment) => comment.id === focusCommentId)
  if (!focusedComment?.rootCommentId) return
  const replies = props.comments.filter((comment) => comment.rootCommentId === focusedComment.rootCommentId)
  const focusedIndex = replies.findIndex((comment) => comment.id === focusCommentId)
  visibleRepliesByRoot.value = { [focusedComment.rootCommentId]: Math.max(replyBatchSize, focusedIndex + 1) }
  expandedReplyRoots.value = new Set([focusedComment.rootCommentId])
}

function replyTotal(rootCommentId: number): number { return props.comments.filter((comment) => comment.rootCommentId === rootCommentId).length }
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
</script>

<template>
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
          <button class="text-button" type="button" @click="emit('focus-comment', item.comment.id)">查看评论</button>
          <RouterLink class="text-button" :to="{ name: 'question-answer-list', params: { questionId: item.question.id }, query: { source: 'comment-questions', postId: post.id } }">候选答复</RouterLink>
          <button v-if="item.question.status === 'OPEN' && item.question.asker.id !== authStore.state.user?.id" class="text-button" type="button" @click="emit('answer-comment-question', item.comment, item.question)"><Send :size="14" />快速作答</button>
        </div>
      </article>
    </div>
  </section>

  <section v-if="isCommentLoading" class="comments-status"><p>正在加载评论...</p></section>
  <section v-else-if="comments.length === 0" class="comments-status"><MapPinned :size="22" /><p>暂时还没有评论，来留下第一条讨论吧。</p></section>
  <ol v-else class="comment-list">
    <template v-for="comment in visibleComments" :key="comment.id">
      <li :id="`comment-${comment.id}`" class="comment-item" :class="{ 'comment-item--reply': comment.rootCommentId !== null, 'comment-item--focused': comment.id === focusedCommentId }">
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
              <button v-if="commentQuestion(comment)!.status === 'OPEN' && commentQuestion(comment)!.asker.id !== authStore.state.user?.id" class="text-button" type="button" @click="emit('answer-comment-question', comment, commentQuestion(comment)!)"><Send :size="14" />快速作答</button>
            </div>
          </div>
          <div class="comment-actions">
            <button class="text-button" type="button" @click="emit('start-reply', comment)"><Reply :size="14" />{{ comment.rootCommentId === null ? '追评' : '回复' }}</button>
            <button v-if="canCreateCommentQuestion(comment) && !commentQuestion(comment)" class="text-button" type="button" @click="emit('open-question-composer', comment)">发起问题追踪</button>
            <button class="text-button" :class="{ active: comment.liked }" type="button" :disabled="likingCommentId === comment.id" @click="emit('like-comment', comment)"><Heart :size="14" :fill="comment.liked ? 'currentColor' : 'none'" />{{ comment.liked ? '已赞' : '点赞' }} {{ comment.likeCount }}</button>
            <button v-if="!isCommentAuthor(comment)" class="text-button" type="button" @click="emit('report-comment', comment)"><Flag :size="14" />举报</button>
            <button v-if="isCommentAuthor(comment)" class="text-button danger" type="button" @click="emit('delete-comment', comment)"><Trash2 :size="14" />删除</button>
          </div>
          <form v-if="activeCommentQuestionId === comment.id" class="inline-comment-form" @submit.prevent="emit('submit-comment-question', comment)">
            <label :for="`comment-question-${comment.id}`">你想持续追踪什么答案？</label>
            <textarea :id="`comment-question-${comment.id}`" v-model="commentQuestionTextModel" maxlength="300" rows="3" placeholder="写下需要追踪的问题..." autofocus @keydown="submitOnEnter($event, () => emit('submit-comment-question', comment))" />
            <div><button class="text-button" type="button" @click="emit('close-question-composer')">取消</button><button class="primary-button" type="submit" :disabled="!commentQuestionText.trim() || isSubmittingCommentQuestion">{{ isSubmittingCommentQuestion ? '发起中...' : '确认发起' }}</button></div>
          </form>
          <form v-if="activeReplyCommentId === comment.id" class="inline-comment-form" @submit.prevent="emit('submit-reply', comment)">
            <label :for="`reply-${comment.id}`">{{ inlineCandidateQuestionId ? '候选答复' : (comment.rootCommentId === null ? '追评' : `回复 ${comment.author.nickname}`) }}</label>
            <p v-if="inlineCandidateQuestionId" class="candidate-answer-context">仅会作为当前问题的候选答复提交</p>
            <textarea :id="`reply-${comment.id}`" v-model="inlineReplyModel" maxlength="500" rows="3" placeholder="写下你的评论..." @keydown="submitOnEnter($event, () => emit('submit-reply', comment))" />
            <div><button class="text-button" type="button" @click="emit('cancel-reply')">取消</button><button class="primary-button" type="submit" :disabled="!inlineReplyContent.trim() || submittingReplyCommentId === comment.id">{{ submittingReplyCommentId === comment.id ? '发布中...' : '发布' }}</button></div>
          </form>
        </div>
      </li>
      <li v-if="isLastVisibleReply(comment)" class="reply-expand-item"><button class="text-button" type="button" @click="expandReplies(comment.rootCommentId!)">{{ expandLabel(comment.rootCommentId!) }}</button></li>
    </template>
  </ol>
  <div v-if="focusedCommentId" class="comment-focus-spacer" aria-hidden="true"></div>
</template>
