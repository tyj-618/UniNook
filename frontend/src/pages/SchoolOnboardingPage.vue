<script setup lang="ts">
import { ArrowLeft, Check, CircleAlert, Search } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCampuses, getSchoolCities, getSchoolProvinces } from '../api/schools.ts'
import { getSchoolChangeQuota, updateCurrentUser } from '../api/users.ts'
import { authStore } from '../auth/auth.ts'
import { errorMessageOf } from '../api/errors.ts'
import type { School, SchoolChangeQuota } from '../types/api.ts'

const router = useRouter()
const route = useRoute()
const province = ref('')
const city = ref('')
const keyword = ref('')
const provinces = ref<string[]>([])
const cities = ref<string[]>([])
const results = ref<School[]>([])
const selected = ref<School | null>(null)
const schoolChangeQuota = ref<SchoolChangeQuota | null>(null)
const errorMessage = ref('')
const isLoadingProvinces = ref(false)
const isLoadingCities = ref(false)
const isSearching = ref(false)
const isSaving = ref(false)

type SchoolSaveDestination = 'feed' | 'profile'

const isSchoolChangeFlow = computed(() => route.query.from === 'profile' || route.query.from === 'profile-settings' || Boolean(authStore.state.user?.schoolId))
const isQuotaExhausted = computed(() => isSchoolChangeFlow.value
  && selected.value?.id !== authStore.state.user?.schoolId
  && schoolChangeQuota.value?.remaining === 0)
const canSave = computed(() => selected.value !== null && !isSaving.value && !isQuotaExhausted.value)
const schoolChangeQuotaText = computed(() => {
  const quota = schoolChangeQuota.value
  if (!quota) return '正在读取本月校园修改次数...'
  const resetsOn = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' })
    .format(new Date(`${quota.resetsOn}T00:00:00`))
  return `本月还可修改 ${quota.remaining}/${quota.limit} 次，将于 ${resetsOn} 重置。`
})

function returnToProfile(): void {
  void router.replace({ name: 'profile' })
}

async function loadSchoolChangeQuota(): Promise<void> {
  try {
    schoolChangeQuota.value = await getSchoolChangeQuota()
  } catch {
    schoolChangeQuota.value = null
  }
}

async function loadProvinces(): Promise<void> {
  isLoadingProvinces.value = true
  try {
    provinces.value = await getSchoolProvinces()
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '省份加载失败，请稍后重试。')
  } finally {
    isLoadingProvinces.value = false
  }
}

async function loadCities(): Promise<void> {
  if (!province.value) return
  isLoadingCities.value = true
  try {
    cities.value = await getSchoolCities(province.value)
  } catch (error) {
    cities.value = []
    errorMessage.value = errorMessageOf(error, '城市加载失败，请稍后重试。')
  } finally {
    isLoadingCities.value = false
  }
}

async function loadCampuses(): Promise<void> {
  if (!province.value || !city.value) {
    results.value = []
    return
  }
  isSearching.value = true
  try {
    results.value = await getCampuses(province.value, city.value, keyword.value.trim())
  } catch (error) {
    results.value = []
    errorMessage.value = errorMessageOf(error, '校区加载失败，请稍后重试。')
  } finally {
    isSearching.value = false
  }
}

watch(province, async () => {
  city.value = ''
  cities.value = []
  results.value = []
  selected.value = null
  errorMessage.value = ''
  await loadCities()
})

watch([city, keyword], () => {
  selected.value = null
  errorMessage.value = ''
  void loadCampuses()
})

async function saveSchool(destination: SchoolSaveDestination): Promise<void> {
  if (!selected.value) return
  isSaving.value = true
  errorMessage.value = ''
  try {
    const user = await updateCurrentUser({ schoolId: selected.value.id })
    authStore.updateUser(user)
    await router.replace({ name: destination })
  } catch (error) {
    errorMessage.value = errorMessageOf(error, '校区绑定失败，请稍后重试。')
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  void loadProvinces()
  void loadSchoolChangeQuota()
})
</script>

<template>
  <main class="onboarding-page">
    <section class="onboarding-panel">
      <button v-if="isSchoolChangeFlow" class="text-button onboarding-return-button" type="button" @click="returnToProfile"><ArrowLeft :size="15" />返回个人主页</button>
      <p class="eyebrow">CAMPUS SETUP</p>
      <h1>选择你的校区</h1>
      <p class="muted">校园圈以校区坐标为中心；之后可在个人主页中修改。</p>
      <p v-if="isSchoolChangeFlow" class="school-change-quota" :class="{ 'school-change-quota--exhausted': isQuotaExhausted }">{{ schoolChangeQuotaText }}</p>

      <div class="school-selector-grid">
        <label>
          <span>省份</span>
          <select v-model="province" :disabled="isLoadingProvinces">
            <option value="">请选择省份</option>
            <option v-for="item in provinces" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>
          <span>城市</span>
          <select v-model="city" :disabled="!province || isLoadingCities">
            <option value="">请选择城市</option>
            <option v-for="item in cities" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
      </div>

      <label class="search-field">
        <Search :size="18" />
        <input v-model="keyword" :disabled="!city" placeholder="筛选高校或校区名称" />
      </label>
      <p v-if="isSearching" class="muted">正在加载校区...</p>
      <div v-else class="school-results">
        <button v-for="campus in results" :key="campus.id" class="school-option" :class="{ selected: selected?.id === campus.id }" type="button" @click="selected = campus">
          <span><strong>{{ campus.name }} · {{ campus.campusName }}</strong><small>{{ campus.province }} · {{ campus.city }}</small></span>
          <Check v-if="selected?.id === campus.id" :size="18" />
        </button>
      </div>
      <p v-if="errorMessage" class="form-error"><CircleAlert :size="16" />{{ errorMessage }}</p>
      <div v-if="isSchoolChangeFlow" class="onboarding-actions">
        <button class="text-button" type="button" :disabled="!canSave" @click="saveSchool('profile')">确认校园校区并返回个人主页</button>
        <button class="primary-button" type="button" :disabled="!canSave" @click="saveSchool('feed')">{{ isSaving ? '保存中...' : '确认并进入校园圈' }}</button>
      </div>
      <button v-else class="primary-button" type="button" :disabled="!canSave" @click="saveSchool('feed')">{{ isSaving ? '保存中...' : '确认并进入校园圈' }}</button>
    </section>
  </main>
</template>
