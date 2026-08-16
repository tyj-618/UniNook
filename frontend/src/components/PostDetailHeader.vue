<script setup lang="ts">
import { CircleAlert, Eye, Flag, Heart, MessageCircle, Pencil, Trash2 } from '@lucide/vue'
import { ref } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import type { PostDetail, ReportTargetType } from '../types/api.ts'
import { formatCompactDateTime } from '../utils/date.ts'
import { submitOnEnter } from '../utils/submitOnEnter.ts'

const props = defineProps<{
  post: PostDetail | null
  isPostAuthor: boolean
  isLoading: boolean
  errorMessage: string
  isEditingPost: boolean
  isLiking: boolean
  isSavingPost: boolean
  userProfileTarget: (userId: number, commentId?: number) => RouteLocationRaw
}>()

const emit = defineEmits<{
  'edit-post': []
  'delete-post': []
  like: []
  report: [type: ReportTargetType, id: number, label: string]
  'save-edit': [title: string, content: string]
  'cancel-edit': []
  reload: []
}>()

const editTitle = ref('')
const editContent = ref('')

function startEdit(): void {
  if (!props.post) return
  editTitle.value = props.post.title
  editContent.value = props.post.content
  emit('edit-post')
}

function saveEdit(): void {
  emit('save-edit', editTitle.value, editContent.value)
}
</script>

<template>
  <section v-if="isLoading" class="empty-feed"><h2>正在加载帖子...</h2></section>
  <section v-else-if="errorMessage" class="empty-feed"><CircleAlert :size="24" /><h2>无法查看该帖子</h2><p>{{ errorMessage }}</p><button class="primary-button" type="button" @click="emit('reload')">重新加载</button></section>
  <article v-else-if="post" class="post-detail">
    <div class="post-meta"><span>{{ post.school.name }}<template v-if="post.school.campusName"> · {{ post.school.campusName }}</template></span><span>{{ post.category.name }}</span><span>{{ formatCompactDateTime(post.createdAt) }}</span><span v-if="post.updatedAt !== post.createdAt">修改于 {{ formatCompactDateTime(post.updatedAt) }}</span></div>
    <div v-if="isPostAuthor" class="post-owner-actions"><button class="text-button" type="button" @click="startEdit"><Pencil :size="14" />修改</button><button class="text-button danger" type="button" @click="emit('delete-post')"><Trash2 :size="14" />删除</button></div>
    <template v-if="isEditingPost"><form class="editor-form post-edit-form" @submit.prevent="saveEdit"><label>标题<input v-model="editTitle" maxlength="100" /></label><label>正文<textarea v-model="editContent" rows="8" maxlength="5000" @keydown="submitOnEnter($event, saveEdit)" /></label><div class="page-actions"><button class="text-button" type="button" @click="emit('cancel-edit')">取消</button><button class="primary-button" type="submit" :disabled="isSavingPost || !editTitle.trim() || !editContent.trim()">{{ isSavingPost ? '保存中...' : '保存修改' }}</button></div></form></template>
    <template v-else><h1>{{ post.title }}</h1><div class="post-author"><RouterLink class="post-author-avatar" :to="userProfileTarget(post.author.id)" :aria-label="`查看 ${post.author.nickname} 的主页`"><img v-if="post.author.avatarUrl" :src="post.author.avatarUrl" alt="" /><template v-else>{{ post.author.nickname.slice(0, 1).toUpperCase() }}</template></RouterLink><span>发布者：</span><RouterLink :to="userProfileTarget(post.author.id)">{{ post.author.nickname }}</RouterLink></div><div class="post-content">{{ post.content }}</div></template>
    <div v-if="!isEditingPost" class="post-detail-footer"><div class="post-stats"><span><Eye :size="16" />{{ post.viewCount }}</span><span><MessageCircle :size="16" />{{ post.commentCount }}</span></div><div class="post-detail-actions"><button v-if="!isPostAuthor" class="text-button" type="button" @click="emit('report', 'POST', post.id, '这篇帖子')"><Flag :size="15" />举报</button><button class="like-button" :class="{ active: post.liked }" type="button" :disabled="isLiking" @click="emit('like')"><Heart :size="17" :fill="post.liked ? 'currentColor' : 'none'" />{{ post.liked ? '已赞' : '点赞' }} {{ post.likeCount }}</button></div></div>
  </article>
</template>

<style scoped>
.post-detail-actions { display: flex; align-items: center; gap: 0.75rem; }
</style>
