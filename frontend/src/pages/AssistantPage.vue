<script setup lang="ts">
import { Check, CircleAlert, RefreshCw, Send, Trash2, X } from '@lucide/vue'
import { nextTick, onMounted, ref, watch } from 'vue'
import { authStore } from '../auth/auth.ts'
import { cancelPendingAction, confirmPendingPost, streamAssistant } from '../api/assistant.ts'
import { getCategories } from '../api/categories.ts'
import { errorMessageOf } from '../api/errors.ts'
import type { AiAssistantResponse, AiPostReference, CampusScope, Category, PendingPostAction } from '../types/api.ts'
import { submitOnEnter } from '../utils/submitOnEnter.ts'

interface ConversationTurn {
  id: string
  question: string
  scope: CampusScope
  answer: string
  references: AiPostReference[]
  insufficientEvidence: boolean
  status: 'streaming' | 'completed' | 'cancelled' | 'failed'
  pendingAction: PendingPostAction | null
  pendingActionStatus: 'pending' | 'confirming' | 'published' | 'cancelled' | null
  pendingActionCategoryId: number | null
  pendingActionPostId: number | null
  pendingActionError: string
}

const legacySessionStorageKey = 'uninook-assistant-session-id'
const insufficientEvidenceMessage = '当前范围内没有足够的帖子可作为可靠依据。'
const question = ref('')
const conversation = ref<ConversationTurn[]>(loadConversation())
const scope = ref<CampusScope>(conversation.value.at(-1)?.scope ?? 'NEARBY_10')
const errorMessage = ref('')
const isSubmitting = ref(false)
const abortController = ref<AbortController | null>(null)
const sessionId = ref(loadSessionId())
const categories = ref<Category[]>([])
const isLoadingCategories = ref(false)

watch(conversation, saveConversation, { deep: true })

onMounted(() => {
  if (conversation.value.some((turn) => turn.pendingAction && turn.pendingActionStatus === 'pending')) {
    void ensureCategories()
  }
})

async function submit(): Promise<void> {
  const trimmedQuestion = question.value.trim()
  if (!trimmedQuestion || isSubmitting.value) return

  const turn: ConversationTurn = {
    id: createId(),
    question: trimmedQuestion,
    scope: scope.value,
    answer: '',
    references: [],
    insufficientEvidence: false,
    status: 'streaming',
    pendingAction: null,
    pendingActionStatus: null,
    pendingActionCategoryId: null,
    pendingActionPostId: null,
    pendingActionError: '',
  }
  conversation.value.push(turn)
  question.value = ''
  focusLatestTurn()
  await requestAnswer(turn)
}

async function refreshTurn(turn: ConversationTurn): Promise<void> {
  if (isSubmitting.value) return

  turn.answer = ''
  turn.references = []
  turn.insufficientEvidence = false
  turn.status = 'streaming'
  await requestAnswer(turn)
}

async function requestAnswer(turn: ConversationTurn): Promise<void> {
  isSubmitting.value = true
  errorMessage.value = ''

  const controller = new AbortController()
  abortController.value = controller
  try {
    await streamAssistant(turn.question, turn.scope, sessionId.value, {
      onChunk: (chunk) => {
        turn.answer += chunk
      },
      onDone: (response) => finishTurn(turn, response),
    }, controller.signal)
  } catch (error) {
    if (isAbortError(error)) {
      turn.status = 'cancelled'
    } else {
      turn.status = 'failed'
      errorMessage.value = errorMessageOf(error, '校园助手暂时无法回答，请稍后重试。')
    }
    saveConversation()
  } finally {
    if (abortController.value === controller) {
      abortController.value = null
      isSubmitting.value = false
    }
  }
}

function finishTurn(turn: ConversationTurn, response: AiAssistantResponse): void {
  turn.answer = response.answer
  turn.references = response.references
  turn.insufficientEvidence = response.insufficientEvidence
  turn.status = 'completed'
  turn.pendingAction = response.pendingAction
  turn.pendingActionStatus = response.pendingAction ? 'pending' : null
  turn.pendingActionCategoryId = categories.value[0]?.id ?? null
  turn.pendingActionPostId = null
  turn.pendingActionError = ''
  if (response.pendingAction) void ensureCategories(turn)
  saveConversation()
}

async function ensureCategories(turn?: ConversationTurn): Promise<void> {
  if (categories.value.length || isLoadingCategories.value) return
  isLoadingCategories.value = true
  try {
    categories.value = await getCategories()
    if (!categories.value.length) {
      if (turn) turn.pendingActionError = '暂时没有可用于发布的分类。'
      return
    }
    for (const item of conversation.value) {
      if (item.pendingAction && item.pendingActionStatus === 'pending' && !item.pendingActionCategoryId) {
        item.pendingActionCategoryId = categories.value[0].id
      }
    }
  } catch (error) {
    if (turn) turn.pendingActionError = errorMessageOf(error, '分类加载失败，请稍后重试。')
  } finally {
    isLoadingCategories.value = false
  }
}

async function confirmPostDraft(turn: ConversationTurn): Promise<void> {
  if (!turn.pendingAction || turn.pendingActionStatus !== 'pending' || !turn.pendingActionCategoryId) return
  turn.pendingActionStatus = 'confirming'
  turn.pendingActionError = ''
  try {
    const response = await confirmPendingPost(turn.pendingAction.actionId, turn.pendingActionCategoryId)
    turn.pendingActionPostId = response.postId
    turn.pendingActionStatus = 'published'
  } catch (error) {
    turn.pendingActionStatus = 'pending'
    turn.pendingActionError = errorMessageOf(error, '发布失败：草稿可能已过期，请重新发起。')
  } finally {
    saveConversation()
  }
}

async function cancelPostDraft(turn: ConversationTurn): Promise<void> {
  if (!turn.pendingAction || turn.pendingActionStatus !== 'pending') return
  turn.pendingActionStatus = 'confirming'
  turn.pendingActionError = ''
  try {
    await cancelPendingAction(turn.pendingAction.actionId)
    turn.pendingActionStatus = 'cancelled'
  } catch (error) {
    turn.pendingActionStatus = 'pending'
    turn.pendingActionError = errorMessageOf(error, '取消失败：草稿可能已过期。')
  } finally {
    saveConversation()
  }
}

function focusLatestTurn(): void {
  void nextTick(() => {
    const container = document.querySelector<HTMLElement>('.assistant-conversation')
    const turn = container?.lastElementChild
    if (!container || !turn) return

    const top = container.scrollTop + turn.getBoundingClientRect().top - container.getBoundingClientRect().top - 12
    container.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
  })
}

function cancel(): void {
  abortController.value?.abort()
}

function clearConversation(): void {
  if (isSubmitting.value) return
  conversation.value = []
  localStorage.removeItem(conversationStorageKey())
  sessionId.value = createSessionId()
  localStorage.setItem(sessionStorageKey(), sessionId.value)
  errorMessage.value = ''
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function loadConversation(): ConversationTurn[] {
  const raw = localStorage.getItem(conversationStorageKey())
  if (!raw) return []
  try {
    const stored = JSON.parse(raw) as unknown
    if (!Array.isArray(stored)) return []
    const restored = stored.filter(isConversationTurn).map((turn) => ({
      ...turn,
      answer: turn.status === 'streaming' && !turn.answer ? '页面刷新已中断本次生成，请重新提问。' : turn.answer,
      status: turn.status === 'streaming' && turn.answer ? 'completed' : turn.status === 'streaming' ? 'cancelled' : turn.status,
      pendingAction: isPendingPostAction(turn.pendingAction) ? turn.pendingAction : null,
      pendingActionStatus: isPendingPostAction(turn.pendingAction) ? (isPendingActionStatus(turn.pendingActionStatus) ? turn.pendingActionStatus : 'pending') : null,
      pendingActionCategoryId: typeof turn.pendingActionCategoryId === 'number' ? turn.pendingActionCategoryId : null,
      pendingActionPostId: typeof turn.pendingActionPostId === 'number' ? turn.pendingActionPostId : null,
      pendingActionError: typeof turn.pendingActionError === 'string' ? turn.pendingActionError : '',
    }))
    localStorage.setItem(conversationStorageKey(), JSON.stringify(restored))
    return restored
  } catch {
    localStorage.removeItem(conversationStorageKey())
    return []
  }
}

function saveConversation(): void {
  localStorage.setItem(conversationStorageKey(), JSON.stringify(conversation.value))
}

function isConversationTurn(value: unknown): value is ConversationTurn {
  if (!value || typeof value !== 'object') return false
  const turn = value as Partial<ConversationTurn>
  return typeof turn.id === 'string'
    && typeof turn.question === 'string'
    && typeof turn.scope === 'string'
    && typeof turn.answer === 'string'
    && Array.isArray(turn.references)
    && typeof turn.insufficientEvidence === 'boolean'
    && typeof turn.status === 'string'
}

function isPendingPostAction(value: unknown): value is PendingPostAction {
  if (!value || typeof value !== 'object') return false
  const action = value as Partial<PendingPostAction>
  return typeof action.actionId === 'string'
    && action.type === 'CREATE_POST'
    && typeof action.title === 'string'
    && typeof action.content === 'string'
    && typeof action.expiresAt === 'string'
}

function isPendingActionStatus(value: unknown): value is NonNullable<ConversationTurn['pendingActionStatus']> {
  return value === 'pending' || value === 'confirming' || value === 'published' || value === 'cancelled'
}

function loadSessionId(): string {
  const stored = localStorage.getItem(sessionStorageKey())
  if (stored) return stored

  const legacy = localStorage.getItem(legacySessionStorageKey)
  if (legacy) {
    localStorage.setItem(sessionStorageKey(), legacy)
    localStorage.removeItem(legacySessionStorageKey)
    return legacy
  }

  const value = createSessionId()
  localStorage.setItem(sessionStorageKey(), value)
  return value
}

function createSessionId(): string {
  return `assistant-${createId()}`
}

function createId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function conversationStorageKey(): string {
  return `uninook-assistant-conversation:${currentUserKey()}`
}

function sessionStorageKey(): string {
  return `uninook-assistant-session-id:${currentUserKey()}`
}

function currentUserKey(): string {
  return String(authStore.state.user?.id ?? 'anonymous')
}

function scopeLabel(value: CampusScope): string {
  return {
    CAMPUS: '同校区',
    UNIVERSITY: '同校',
    NEARBY_10: '10 km',
    NEARBY_20: '20 km',
    CITY: '同市',
  }[value]
}
</script>

<template>
  <section class="content-page form-page assistant-page">
    <div class="page-heading assistant-page__heading">
      <div>
        <p class="eyebrow">CAMPUS ASSISTANT</p>
        <h1>校园助手</h1>
        <p class="muted">仅检索你当前附近校园范围内的公开帖子，并附上参考来源。</p>
      </div>
      <button v-if="conversation.length" class="text-button" type="button" :disabled="isSubmitting" @click="clearConversation">
        <Trash2 :size="15" />清空会话
      </button>
    </div>

    <section v-if="conversation.length" class="assistant-conversation" aria-label="当前会话">
      <article v-for="turn in conversation" :key="turn.id" class="assistant-turn">
        <div class="assistant-message assistant-message--user">
          <p class="assistant-message__meta">你 · {{ scopeLabel(turn.scope) }}</p>
          <p>{{ turn.question }}</p>
        </div>
        <div class="assistant-message assistant-message--assistant">
          <button v-if="turn.status === 'cancelled' || turn.status === 'failed'" type="button" class="assistant-turn__refresh"
            :disabled="isSubmitting" @click="refreshTurn(turn)">
            <RefreshCw :size="14" />刷新
          </button>
          <p class="assistant-message__meta">校园助手</p>
          <p>{{ turn.answer || '正在生成回答…' }}</p>
          <p v-if="turn.status === 'cancelled'" class="muted">已停止生成，以上为已收到的内容。</p>
          <p v-if="turn.status === 'failed'" class="muted">本次回答未完成，请重新提问。</p>
          <p v-if="turn.insufficientEvidence && turn.answer !== insufficientEvidenceMessage" class="muted">
            {{ insufficientEvidenceMessage }}
          </p>
          <div v-if="turn.references.length" class="assistant-references">
            <h2>参考帖子</h2>
            <RouterLink v-for="reference in turn.references" :key="reference.postId" class="reference-item"
              :to="{ name: 'post-detail', params: { id: reference.postId }, query: { scope: turn.scope } }">
              <strong>{{ reference.title }}</strong>
              <span>{{ reference.schoolName }} · {{ reference.excerpt }}</span>
            </RouterLink>
          </div>
          <section v-if="turn.pendingAction?.type === 'CREATE_POST'" class="assistant-pending-post">
            <template v-if="turn.pendingActionStatus === 'pending' || turn.pendingActionStatus === 'confirming'">
              <p class="assistant-pending-post__eyebrow">待确认发布</p>
              <h2>{{ turn.pendingAction.title }}</h2>
              <p class="assistant-pending-post__content">{{ turn.pendingAction.content }}</p>
              <label>发布分类
                <select v-model="turn.pendingActionCategoryId" :disabled="turn.pendingActionStatus === 'confirming' || isLoadingCategories">
                  <option :value="null" disabled>{{ isLoadingCategories ? '正在加载分类…' : '请选择分类' }}</option>
                  <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
                </select>
              </label>
              <p v-if="turn.pendingActionError" class="form-error"><CircleAlert :size="15" />{{ turn.pendingActionError }}</p>
              <div class="assistant-pending-post__actions">
                <button class="primary-button" type="button" :disabled="turn.pendingActionStatus === 'confirming' || !turn.pendingActionCategoryId" @click="confirmPostDraft(turn)">
                  <Check :size="16" />{{ turn.pendingActionStatus === 'confirming' ? '处理中…' : '确认发布' }}
                </button>
                <button class="secondary-button" type="button" :disabled="turn.pendingActionStatus === 'confirming'" @click="cancelPostDraft(turn)">
                  <X :size="16" />取消草稿
                </button>
              </div>
              <p class="muted">确认后才会发布到校园社区；草稿将在 {{ new Date(turn.pendingAction.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }} 前失效。</p>
            </template>
            <template v-else-if="turn.pendingActionStatus === 'published'">
              <p class="assistant-pending-post__eyebrow">已发布</p>
              <p>帖子已按你的确认发布。</p>
              <RouterLink v-if="turn.pendingActionPostId" class="text-button" :to="{ name: 'post-detail', params: { id: turn.pendingActionPostId }, query: { scope: turn.scope } }">查看帖子</RouterLink>
            </template>
            <template v-else-if="turn.pendingActionStatus === 'cancelled'">
              <p class="assistant-pending-post__eyebrow">已取消</p>
              <p class="muted">这份草稿未被发布。</p>
            </template>
          </section>
        </div>
      </article>
    </section>
    <p v-else class="assistant-empty-state">从一个校园问题开始，后续问答会保留在当前会话中。</p>

    <form class="editor-form assistant-composer" @submit.prevent="submit">
      <label>查看范围
        <select v-model="scope" :disabled="isSubmitting">
          <option value="CAMPUS">同校区</option><option value="UNIVERSITY">同校</option><option value="NEARBY_10">10 km</option>
          <option value="NEARBY_20">20 km</option><option value="CITY">同市</option>
        </select>
      </label>
      <label>你的问题
        <textarea v-model="question" rows="4" maxlength="500" :disabled="isSubmitting"
          placeholder="例如：附近有哪些适合期末复习的地方？" required @keydown="submitOnEnter($event, submit)" />
      </label>
      <p class="editor-keyboard-hint">Enter 发送，Shift + Enter 换行</p>
      <div class="assistant-composer__actions">
        <button class="primary-button" :disabled="isSubmitting"><Send :size="17" />{{ isSubmitting ? 'AI 正在搜索并生成…' : '询问助手' }}</button>
        <button v-if="isSubmitting" type="button" class="secondary-button" @click="cancel">停止生成</button>
      </div>
    </form>
    <p v-if="errorMessage" class="form-error"><CircleAlert :size="16" />{{ errorMessage }}</p>
  </section>
</template>
