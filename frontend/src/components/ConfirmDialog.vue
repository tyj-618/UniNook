<script setup lang="ts">
defineProps<{
  visible: boolean
  title: string
  message: string
  confirmText: string
  isLoading?: boolean
  danger?: boolean
}>()

defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <div v-if="visible" class="confirm-backdrop" role="presentation" @click.self="$emit('cancel')">
    <section class="confirm-dialog" role="dialog" aria-modal="true" :aria-label="title">
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
      <div class="confirm-dialog-actions">
        <button class="text-button" type="button" :disabled="isLoading" @click="$emit('cancel')">取消</button>
        <button class="primary-button" :class="{ 'danger-button': danger }" type="button" :disabled="isLoading" @click="$emit('confirm')">
          {{ isLoading ? '处理中...' : confirmText }}
        </button>
      </div>
    </section>
  </div>
</template>
