<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhProhibit, PhSkipForward } from '@phosphor-icons/vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import type { DoseLine } from '@/types/care'

/** Skipped or refused: both need a reason, so the next person and the family understand. */
const open = defineModel<boolean>('open', { default: false })
const props = defineProps<{ line: DoseLine | null; busy: boolean; error?: string | null }>()
const emit = defineEmits<{ submit: [status: 'SKIPPED' | 'REFUSED', note: string] }>()

const { t } = useI18n()
const status = ref<'SKIPPED' | 'REFUSED'>('REFUSED')
const note = ref('')

const options = computed(() => [
  { value: 'REFUSED' as const, label: t('doses.refused'), icon: PhProhibit },
  { value: 'SKIPPED' as const, label: t('doses.skipped'), icon: PhSkipForward },
])
const reasons = ['asleep', 'nausea', 'noFood', 'doctor', 'spat'] as const

watch(open, (value) => {
  if (value) {
    status.value = 'REFUSED'
    note.value = ''
  }
})

function pick(reason: (typeof reasons)[number]) {
  note.value = t(`doses.reasons.${reason}`)
}

function submit() {
  emit('submit', status.value, note.value.trim())
}
</script>

<template>
  <UiDialog
    v-model:open="open"
    :title="props.line ? $t('doses.notGivenTitle', { name: props.line.name, time: props.line.time }) : ''"
    :description="$t('doses.notGivenText')"
  >
    <form id="not-given" class="stack" novalidate @submit.prevent="submit">
      <UiSegmented v-model="status" :options="options" :label="$t('common.status')" block size="lg" />
      <div class="reasons" role="group" :aria-label="$t('doses.reason')">
        <button v-for="r in reasons" :key="r" type="button" class="reason" @click="pick(r)">{{ $t(`doses.reasons.${r}`) }}</button>
      </div>
      <UiField id="f-dose-note" :label="$t('doses.reason')" :error="error">
        <template #default="{ id, invalid, describedby }">
          <UiTextarea :id="id" v-model="note" :rows="2" :invalid="invalid" :describedby="describedby" />
        </template>
      </UiField>
    </form>
    <template #footer>
      <UiButton variant="ghost" @click="open = false">{{ $t('common.cancel') }}</UiButton>
      <UiButton type="submit" form="not-given" :loading="busy" :disabled="!note.trim()">{{ $t('common.save') }}</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.reason {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text);
  font-size: var(--text-sm);
  font-weight: 600;
  box-shadow: var(--shadow-xs);
  transition:
    background-color var(--duration) var(--ease),
    border-color var(--duration) var(--ease);
}
.reason:hover {
  border-color: var(--primary-soft-border);
  background: var(--primary-soft);
}
</style>
