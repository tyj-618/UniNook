<script setup lang="ts">
import { AlertCircle, Database, FileText, ShieldCheck, UsersRound } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { authStore } from '../auth/auth.ts'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import {
  disableAdminUser,
  enableAdminUser,
  getAdminActionLogs,
  getAdminPosts,
  getAdminUsers,
  hideAdminPost,
  rebuildAdminPostIndex,
  restoreAdminPost,
} from '../api/admin.ts'
import { errorMessageOf } from '../api/errors.ts'
import type { AdminActionLogItem, AdminPostListItem, AdminUserListItem } from '../types/api.ts'
import { formatCompactDateTime } from '../utils/date.ts'

type AdminTab = 'posts' | 'users' | 'logs'
type ConfirmAction = { title: string; message: string; confirmText: string; run: () => Promise<void> } | null

const activeTab = ref<AdminTab>('posts')
const keyword = ref('')
const status = ref<number | undefined>()
const page = ref(1)
const total = ref(0)
const records = ref<AdminPostListItem[] | AdminUserListItem[] | AdminActionLogItem[]>([])
const isLoading = ref(false)
const isSubmitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const confirmAction = ref<ConfirmAction>(null)

const t = {
  eyebrow: '\u0041\u0044\u004d\u0049\u004e\u0020\u0043\u004f\u004e\u0053\u004f\u004c\u0045',
  title: '\u5185\u5bb9\u7ba1\u7406',
  subtitle: '\u67e5\u770b\u5e16\u5b50\u3001\u7528\u6237\u4e0e\u7ba1\u7406\u64cd\u4f5c\u8bb0\u5f55\u3002\u654f\u611f\u64cd\u4f5c\u9700\u4e8c\u6b21\u786e\u8ba4\u3002',
  posts: '\u5e16\u5b50', users: '\u7528\u6237', logs: '\u5ba1\u8ba1\u8bb0\u5f55',
  searchPlaceholder: '\u641c\u7d22\u6807\u9898\u3001\u5185\u5bb9\u3001\u7528\u6237\u540d\u6216\u6635\u79f0',
  allStatus: '\u5168\u90e8\u72b6\u6001', normal: '\u6b63\u5e38', hidden: '\u5df2\u9690\u85cf', disabled: '\u5df2\u7981\u7528',
  search: '\u67e5\u8be2', refresh: '\u5237\u65b0',
  postTitle: '\u6807\u9898', author: '\u4f5c\u8005', campus: '\u6821\u533a', statusLabel: '\u72b6\u6001', createdAt: '\u521b\u5efa\u65f6\u95f4', action: '\u64cd\u4f5c',
  username: '\u7528\u6237\u540d', nickname: '\u6635\u79f0', role: '\u89d2\u8272',
  admin: '\u7ba1\u7406\u5458', user: '\u666e\u901a\u7528\u6237',
  hide: '\u9690\u85cf', restore: '\u6062\u590d', disable: '\u7981\u7528', enable: '\u542f\u7528',
  rebuild: '\u91cd\u5efa\u641c\u7d22\u7d22\u5f15',
  empty: '\u6682\u65e0\u7b26\u5408\u6761\u4ef6\u7684\u8bb0\u5f55',
  loadFailed: '\u52a0\u8f7d\u5931\u8d25',
  previous: '\u4e0a\u4e00\u9875', next: '\u4e0b\u4e00\u9875',
  page: '\u7b2c', pageSuffix: '\u9875', totalPrefix: '\u5171', totalSuffix: '\u6761',
  hideTitle: '\u786e\u8ba4\u9690\u85cf\u5e16\u5b50', hideMessage: '\u9690\u85cf\u540e\u5e16\u5b50\u5c06\u4e0d\u518d\u5bf9\u666e\u901a\u7528\u6237\u5c55\u793a\uff0c\u53ef\u4ee5\u5728\u540e\u53f0\u6062\u590d\u3002',
  restoreTitle: '\u786e\u8ba4\u6062\u590d\u5e16\u5b50', restoreMessage: '\u6062\u590d\u540e\u5e16\u5b50\u4f1a\u91cd\u65b0\u53c2\u4e0e\u793e\u533a\u5c55\u793a\u4e0e\u68c0\u7d22\u3002',
  disableTitle: '\u786e\u8ba4\u7981\u7528\u7528\u6237', disableMessage: '\u7981\u7528\u540e\u8be5\u7528\u6237\u65e0\u6cd5\u7ee7\u7eed\u4f7f\u7528\u8d26\u53f7\u3002',
  enableTitle: '\u786e\u8ba4\u542f\u7528\u7528\u6237', enableMessage: '\u542f\u7528\u540e\u8be5\u7528\u6237\u53ef\u4ee5\u6062\u590d\u6b63\u5e38\u4f7f\u7528\u3002',
  rebuildTitle: '\u786e\u8ba4\u91cd\u5efa\u7d22\u5f15', rebuildMessage: '\u5c06\u91cd\u65b0\u5199\u5165\u5f53\u524d\u5e16\u5b50\u641c\u7d22\u7d22\u5f15\uff0c\u8fc7\u7a0b\u53ef\u80fd\u9700\u8981\u4e00\u4e9b\u65f6\u95f4\u3002',
  confirm: '\u786e\u8ba4', processing: '\u5904\u7406\u4e2d', rebuildSuccessPrefix: '\u5df2\u91cd\u5efa', rebuildSuccessSuffix: '\u6761\u5e16\u5b50\u7d22\u5f15',
}

const pages = computed(() => Math.max(1, Math.ceil(total.value / 20)))
const isPostTab = computed(() => activeTab.value === 'posts')
const isUserTab = computed(() => activeTab.value === 'users')

watch(activeTab, () => {
  page.value = 1
  keyword.value = ''
  status.value = undefined
  void loadRecords()
})

onMounted(() => void loadRecords())

function statusText(item: AdminPostListItem | AdminUserListItem): string {
  if (item.status === 0) return t.normal
  return isPostTab.value ? t.hidden : t.disabled
}

function campusText(item: AdminPostListItem | AdminUserListItem): string {
  return [item.schoolName, item.campusName].filter(Boolean).join(' · ') || '—'
}

async function loadRecords(): Promise<void> {
  isLoading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    if (activeTab.value === 'posts') {
      const result = await getAdminPosts({ page: page.value, size: 20, keyword: keyword.value || undefined, status: status.value })
      records.value = result.records
      total.value = result.total
    } else if (activeTab.value === 'users') {
      const result = await getAdminUsers({ page: page.value, size: 20, keyword: keyword.value || undefined, status: status.value })
      records.value = result.records
      total.value = result.total
    } else {
      const result = await getAdminActionLogs(page.value, 20)
      records.value = result.records
      total.value = result.total
    }
  } catch (error) {
    errorMessage.value = errorMessageOf(error, t.loadFailed)
  } finally {
    isLoading.value = false
  }
}

function queuePostAction(item: AdminPostListItem): void {
  const hidden = item.status === 2
  confirmAction.value = {
    title: hidden ? t.restoreTitle : t.hideTitle,
    message: hidden ? t.restoreMessage : t.hideMessage,
    confirmText: hidden ? t.restore : t.hide,
    run: async () => { if (hidden) await restoreAdminPost(item.id); else await hideAdminPost(item.id) },
  }
}

function queueUserAction(item: AdminUserListItem): void {
  const disabled = item.status === 1
  confirmAction.value = {
    title: disabled ? t.enableTitle : t.disableTitle,
    message: disabled ? t.enableMessage : t.disableMessage,
    confirmText: disabled ? t.enable : t.disable,
    run: async () => { if (disabled) await enableAdminUser(item.id); else await disableAdminUser(item.id) },
  }
}

function queueRebuild(): void {
  confirmAction.value = {
    title: t.rebuildTitle,
    message: t.rebuildMessage,
    confirmText: t.rebuild,
    run: async () => {
      const count = await rebuildAdminPostIndex()
      successMessage.value = `${t.rebuildSuccessPrefix} ${count} ${t.rebuildSuccessSuffix}`
    },
  }
}

async function runConfirmation(): Promise<void> {
  if (!confirmAction.value) return
  isSubmitting.value = true
  errorMessage.value = ''
  try {
    await confirmAction.value.run()
    confirmAction.value = null
    await loadRecords()
  } catch (error) {
    errorMessage.value = errorMessageOf(error, t.loadFailed)
  } finally {
    isSubmitting.value = false
  }
}

function changePage(nextPage: number): void {
  if (nextPage < 1 || nextPage > pages.value || isLoading.value) return
  page.value = nextPage
  void loadRecords()
}
</script>

<template>
  <section class="content-page admin-page">
    <div class="page-heading admin-page-heading">
      <div>
        <p class="eyebrow"><ShieldCheck :size="16" /> {{ t.eyebrow }}</p>
        <h1>{{ t.title }}</h1>
        <p class="muted">{{ t.subtitle }}</p>
      </div>
      <button class="secondary-button" type="button" :disabled="isLoading" @click="queueRebuild"><Database :size="17" />{{ t.rebuild }}</button>
    </div>

    <div class="segmented-control admin-tabs">
      <button :class="{ active: activeTab === 'posts' }" type="button" @click="activeTab = 'posts'"><FileText :size="17" />{{ t.posts }}</button>
      <button :class="{ active: activeTab === 'users' }" type="button" @click="activeTab = 'users'"><UsersRound :size="17" />{{ t.users }}</button>
      <button :class="{ active: activeTab === 'logs' }" type="button" @click="activeTab = 'logs'"><ShieldCheck :size="17" />{{ t.logs }}</button>
    </div>

    <form v-if="activeTab !== 'logs'" class="admin-filters" @submit.prevent="page = 1; loadRecords()">
      <input v-model.trim="keyword" :placeholder="t.searchPlaceholder" maxlength="100" />
      <select v-model="status">
        <option :value="undefined">{{ t.allStatus }}</option>
        <option :value="0">{{ t.normal }}</option>
        <option :value="isPostTab ? 2 : 1">{{ isPostTab ? t.hidden : t.disabled }}</option>
      </select>
      <button class="primary-button" type="submit" :disabled="isLoading">{{ t.search }}</button>
    </form>

    <p v-if="successMessage" class="admin-success">{{ successMessage }}</p>
    <section v-if="errorMessage" class="admin-message admin-message--error"><AlertCircle :size="18" />{{ errorMessage }}</section>
    <section v-else-if="isLoading" class="admin-message">{{ t.processing }}</section>
    <section v-else-if="records.length === 0" class="admin-message">{{ t.empty }}</section>

    <div v-else class="admin-table-wrap">
      <table v-if="isPostTab" class="admin-table">
        <thead><tr><th>{{ t.postTitle }}</th><th>{{ t.author }}</th><th>{{ t.campus }}</th><th>{{ t.statusLabel }}</th><th>{{ t.createdAt }}</th><th>{{ t.action }}</th></tr></thead>
        <tbody><tr v-for="raw in records as AdminPostListItem[]" :key="raw.id"><td><strong>{{ raw.title }}</strong><small>#{{ raw.id }}</small></td><td>{{ raw.authorNickname }}</td><td>{{ campusText(raw) }}</td><td><span class="admin-status" :class="{ 'admin-status--warning': raw.status !== 0 }">{{ statusText(raw) }}</span></td><td>{{ formatCompactDateTime(raw.createdAt) }}</td><td><button class="text-button" type="button" @click="queuePostAction(raw)">{{ raw.status === 2 ? t.restore : t.hide }}</button></td></tr></tbody>
      </table>
      <table v-else-if="isUserTab" class="admin-table">
        <thead><tr><th>{{ t.username }}</th><th>{{ t.nickname }}</th><th>{{ t.role }}</th><th>{{ t.campus }}</th><th>{{ t.statusLabel }}</th><th>{{ t.createdAt }}</th><th>{{ t.action }}</th></tr></thead>
        <tbody><tr v-for="raw in records as AdminUserListItem[]" :key="raw.id"><td>{{ raw.username }}<small>#{{ raw.id }}</small></td><td>{{ raw.nickname }}</td><td>{{ raw.role === 1 ? t.admin : t.user }}</td><td>{{ campusText(raw) }}</td><td><span class="admin-status" :class="{ 'admin-status--warning': raw.status !== 0 }">{{ statusText(raw) }}</span></td><td>{{ formatCompactDateTime(raw.createdAt) }}</td><td><button class="text-button" type="button" :disabled="raw.id === authStore.state.user?.id" @click="queueUserAction(raw)">{{ raw.status === 1 ? t.enable : t.disable }}</button></td></tr></tbody>
      </table>
      <table v-else class="admin-table">
        <thead><tr><th>#</th><th>{{ t.admin }}</th><th>{{ t.action }}</th><th>{{ t.createdAt }}</th></tr></thead>
        <tbody><tr v-for="raw in records as AdminActionLogItem[]" :key="raw.id"><td>#{{ raw.id }}</td><td>{{ raw.adminNickname }} (#{{ raw.adminUserId }})</td><td>{{ raw.action }} · {{ raw.targetType }}<template v-if="raw.targetId"> #{{ raw.targetId }}</template></td><td>{{ formatCompactDateTime(raw.createdAt) }}</td></tr></tbody>
      </table>
    </div>

    <div class="admin-pagination">
      <span>{{ t.totalPrefix }} {{ total }} {{ t.totalSuffix }} · {{ t.page }} {{ page }} / {{ pages }} {{ t.pageSuffix }}</span>
      <div><button class="secondary-button" type="button" :disabled="page <= 1 || isLoading" @click="changePage(page - 1)">{{ t.previous }}</button><button class="secondary-button" type="button" :disabled="page >= pages || isLoading" @click="changePage(page + 1)">{{ t.next }}</button></div>
    </div>

    <ConfirmDialog
      :visible="confirmAction !== null"
      :title="confirmAction?.title ?? ''"
      :message="confirmAction?.message ?? ''"
      :confirm-text="confirmAction?.confirmText ?? t.confirm"
      :is-loading="isSubmitting"
      danger
      @cancel="confirmAction = null"
      @confirm="runConfirmation"
    />
  </section>
</template>

<style scoped>
.admin-page-heading { align-items: flex-start; gap: 24px; }
.eyebrow { display: flex; align-items: center; gap: 7px; }
.admin-tabs button { display: inline-flex; align-items: center; gap: 7px; }
.admin-filters { display: flex; gap: 12px; margin: 24px 0; }
.admin-filters input { flex: 1; min-width: 0; }
.admin-filters input, .admin-filters select { min-height: 42px; border: 1px solid var(--line); border-radius: 8px; padding: 0 12px; background: var(--surface); color: var(--ink); }
.admin-message, .admin-success { margin: 20px 0; padding: 14px 16px; border: 1px solid var(--line); border-radius: 10px; color: var(--muted); }
.admin-message { display: flex; align-items: center; gap: 8px; }
.admin-message--error { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 30%, var(--line)); }
.admin-success { color: var(--accent); border-color: color-mix(in srgb, var(--accent) 30%, var(--line)); }
.admin-table-wrap { overflow-x: auto; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); }
.admin-table { width: 100%; min-width: 820px; border-collapse: collapse; text-align: left; }
.admin-table th, .admin-table td { padding: 14px 16px; border-bottom: 1px solid var(--line); vertical-align: middle; }
.admin-table th { color: var(--muted); font-size: 13px; font-weight: 700; white-space: nowrap; }
.admin-table tr:last-child td { border-bottom: 0; }
.admin-table small { display: block; margin-top: 4px; color: var(--muted); }
.admin-status { display: inline-flex; padding: 4px 8px; border-radius: 999px; color: var(--accent); background: color-mix(in srgb, var(--accent) 10%, transparent); font-size: 13px; white-space: nowrap; }
.admin-status--warning { color: #9b6b00; background: #fff6df; }
.admin-pagination { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-top: 18px; color: var(--muted); font-size: 14px; }
.admin-pagination > div { display: flex; gap: 8px; }
@media (max-width: 700px) { .admin-page-heading, .admin-filters, .admin-pagination { flex-direction: column; align-items: stretch; } .admin-page-heading > .secondary-button { width: 100%; justify-content: center; } .admin-filters select { width: 100%; } }
</style>
