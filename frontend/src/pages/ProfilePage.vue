<script setup lang="ts">
import { ImagePlus, UserRound } from '@lucide/vue'
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessageOf } from '../api/errors.ts'
import { updateCurrentUser, uploadAvatar } from '../api/users.ts'
import { authStore } from '../auth/auth.ts'

const MAX_AVATAR_UPLOAD_BYTES = 2 * 1024 * 1024
const MAX_AVATAR_SOURCE_BYTES = 20 * 1024 * 1024
const MAX_AVATAR_DIMENSION = 2048
const MIN_AVATAR_DIMENSION = 640
const AVATAR_MIME_TYPES = ['image/jpeg', 'image/png']

const router = useRouter()
const nickname = ref(authStore.state.user?.nickname ?? '')
const bio = ref(authStore.state.user?.bio ?? '')
const avatarPreview = ref(authStore.state.user?.avatarUrl ?? '')
const avatarInput = ref<HTMLInputElement | null>(null)
const pendingAvatar = ref<File | null>(null)
const message = ref('')
const messageType = ref<'error' | 'success'>('error')
const isSaving = ref(false)
const isProcessingAvatar = ref(false)
let previewObjectUrl: string | null = null

watch(() => authStore.state.user, (user) => {
  nickname.value = user?.nickname ?? ''
  bio.value = user?.bio ?? ''
  if (!pendingAvatar.value) avatarPreview.value = user?.avatarUrl ?? ''
})

function showMessage(content: string, type: 'error' | 'success' = 'error'): void {
  message.value = content
  messageType.value = type
}

function clearMessage(): void {
  message.value = ''
}

function releasePreviewObjectUrl(): void {
  if (!previewObjectUrl) return
  URL.revokeObjectURL(previewObjectUrl)
  previewObjectUrl = null
}

function readableFileSize(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(bytes >= 1024 * 1024 ? 1 : 2)} MB`
}

function outputAvatarFileName(file: File): string {
  const name = file.name.replace(/\.[^.]+$/, '') || 'avatar'
  return `${name}.jpg`
}

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(url)
      resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('image_load_failed'))
    }
    image.src = url
  })
}

function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
}

async function compressAvatar(file: File): Promise<File | null> {
  const image = await loadImage(file)
  const originalWidth = image.naturalWidth || image.width
  const originalHeight = image.naturalHeight || image.height
  if (!originalWidth || !originalHeight) return null

  const largestSide = Math.max(originalWidth, originalHeight)
  const initialScale = Math.min(1, MAX_AVATAR_DIMENSION / largestSide)
  let width = Math.max(1, Math.round(originalWidth * initialScale))
  let height = Math.max(1, Math.round(originalHeight * initialScale))

  for (let resizeAttempt = 0; resizeAttempt < 10; resizeAttempt += 1) {
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) return null

    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, width, height)
    context.drawImage(image, 0, 0, width, height)

    for (const quality of [0.98, 0.96, 0.94, 0.92, 0.9, 0.86, 0.82]) {
      const blob = await canvasToBlob(canvas, quality)
      if (blob && blob.size <= MAX_AVATAR_UPLOAD_BYTES) {
        return new File([blob], outputAvatarFileName(file), {
          type: 'image/jpeg',
          lastModified: file.lastModified,
        })
      }
    }

    if (Math.max(width, height) <= MIN_AVATAR_DIMENSION) break
    width = Math.max(MIN_AVATAR_DIMENSION, Math.round(width * 0.9))
    height = Math.max(MIN_AVATAR_DIMENSION, Math.round(height * 0.9))
  }

  return null
}

async function selectAvatar(file: File | undefined): Promise<void> {
  if (!file || isProcessingAvatar.value) return
  if (!AVATAR_MIME_TYPES.includes(file.type)) {
    showMessage('头像仅支持 PNG 或 JPEG 格式')
    return
  }
  if (file.size > MAX_AVATAR_SOURCE_BYTES) {
    showMessage('图片超过 20MB，暂时无法自动处理，请选择更小的图片')
    return
  }

  isProcessingAvatar.value = true
  clearMessage()
  try {
    const avatarFile = file.size <= MAX_AVATAR_UPLOAD_BYTES ? file : await compressAvatar(file)
    if (!avatarFile) {
      showMessage('图片自动压缩失败，请更换一张 PNG 或 JPEG 图片')
      return
    }

    releasePreviewObjectUrl()
    previewObjectUrl = URL.createObjectURL(avatarFile)
    pendingAvatar.value = avatarFile
    avatarPreview.value = previewObjectUrl
    if (avatarFile !== file) {
      showMessage(`已自动压缩至 ${readableFileSize(avatarFile.size)}，保存后即可上传`, 'success')
    }
  } catch {
    showMessage('图片自动压缩失败，请更换一张 PNG 或 JPEG 图片')
  } finally {
    isProcessingAvatar.value = false
  }
}

function onAvatarInputChange(event: Event): void {
  const input = event.target as HTMLInputElement
  void selectAvatar(input.files?.[0])
  input.value = ''
}

function onAvatarDrop(event: DragEvent): void {
  event.preventDefault()
  void selectAvatar(event.dataTransfer?.files[0])
}

async function persistProfile(): Promise<boolean> {
  if (!nickname.value.trim()) {
    showMessage('昵称不能为空')
    return false
  }

  isSaving.value = true
  clearMessage()
  try {
    if (pendingAvatar.value) {
      const avatarUrl = await uploadAvatar(pendingAvatar.value)
      if (authStore.state.user) authStore.updateUser({ ...authStore.state.user, avatarUrl })
      avatarPreview.value = avatarUrl
      pendingAvatar.value = null
      releasePreviewObjectUrl()
    }
    authStore.updateUser(await updateCurrentUser({
      nickname: nickname.value.trim(),
      bio: bio.value.trim() || null,
    }))
    return true
  } catch (error) {
    showMessage(errorMessageOf(error, '资料保存失败，请稍后重试'))
    return false
  } finally {
    isSaving.value = false
  }
}

async function save(): Promise<void> {
  if (await persistProfile()) await router.replace('/profile')
}

onBeforeUnmount(releasePreviewObjectUrl)
</script>

<template>
  <section class="content-page form-page profile-settings-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">PROFILE SETTINGS</p>
        <h1>资料设置</h1>
        <p class="muted">管理你的头像、昵称和个人简介。</p>
      </div>
      <RouterLink class="text-button profile-home-link" to="/profile"><UserRound :size="15" />返回主页</RouterLink>
    </div>

    <form class="editor-form" @submit.prevent="save">
      <div class="avatar-field">
        <span class="field-label">头像</span>
        <input ref="avatarInput" class="visually-hidden" type="file" accept="image/png,image/jpeg" @change="onAvatarInputChange" />
        <button
          class="avatar-upload-dropzone"
          :class="{ 'avatar-upload-dropzone--image': avatarPreview }"
          type="button"
          :disabled="isProcessingAvatar || isSaving"
          @click="avatarInput?.click()"
          @dragenter.prevent
          @dragover.prevent
          @drop="onAvatarDrop"
        >
          <img v-if="avatarPreview" :src="avatarPreview" alt="头像预览" />
          <ImagePlus v-else :size="30" />
          <span class="avatar-upload-overlay">{{ isProcessingAvatar ? '正在处理图片' : '更换头像' }}</span>
        </button>
        <small>点击选择图片，或将图片拖到这里。支持 PNG、JPEG；超过 2MB 时会自动压缩后上传。</small>
      </div>
      <label>昵称<input v-model="nickname" maxlength="32" required /></label>
      <label>个人简介<textarea v-model="bio" maxlength="255" rows="5" placeholder="介绍一下你自己" /><small>{{ bio.length }}/255</small></label>
      <p v-if="message" :class="messageType === 'success' ? 'form-success' : 'form-error'">{{ message }}</p>
      <button class="primary-button" type="submit" :disabled="isSaving || isProcessingAvatar">{{ isSaving ? '保存中...' : '保存并返回主页' }}</button>
    </form>
  </section>
</template>
