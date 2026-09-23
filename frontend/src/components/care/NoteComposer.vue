<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhCamera, PhFloppyDisk, PhWarningOctagon, PhX } from '@phosphor-icons/vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import { downscaleImage } from '@/lib/image'
import type { JournalKind } from '@/types/care'

/** A note with an optional photo from the phone's camera. Incidents are called out in coral. */
const props = defineProps<{ busy: boolean; error?: string | null }>()
const emit = defineEmits<{ save: [kind: JournalKind, text: string, photo: File | null] }>()

const { t } = useI18n()
const kind = ref<JournalKind>('NOTE')
const text = ref('')
const photo = ref<File | null>(null)
const preview = ref<string | null>(null)
const picker = ref<HTMLInputElement | null>(null)

const kinds = computed(() =>
  (['NOTE', 'SLEEP', 'TOILET', 'INCIDENT'] as JournalKind[]).map((k) => ({ value: k, label: t(`journal.kinds.${k}`) })),
)
const canSave = computed(() => !!text.value.trim() || !!photo.value || kind.value === 'TOILET')

watch(photo, (file) => {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = file ? URL.createObjectURL(file) : null
})
onBeforeUnmount(() => {
  if (preview.value) URL.revokeObjectURL(preview.value)
})

async function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) photo.value = await downscaleImage(file)
}

function submit() {
  emit('save', kind.value, text.value.trim(), photo.value)
}

/** The parent calls this once the note is safely recorded. */
function reset() {
  text.value = ''
  photo.value = null
  kind.value = 'NOTE'
}

defineExpose({ reset })
</script>

<template>
  <form class="stack note" novalidate @submit.prevent="submit">
    <UiSegmented v-model="kind" :options="kinds" :label="$t('common.status')" block />
    <p v-if="kind === 'INCIDENT'" class="note__incident">
      <PhWarningOctagon :size="18" weight="bold" aria-hidden="true" /> {{ $t('journal.incidentHint') }}
    </p>
    <UiField id="f-note-text" :label="$t(`journal.kinds.${kind}`)" :error="props.error">
      <template #default="{ id, invalid, describedby }">
        <UiTextarea :id="id" v-model="text" :rows="3" :placeholder="$t('today.notePlaceholder')" :invalid="invalid" :describedby="describedby" />
      </template>
    </UiField>
    <div class="note__foot">
      <div v-if="preview" class="note__thumb">
        <img :src="preview" alt="" />
        <button type="button" class="note__thumb-x" :aria-label="$t('common.removePhoto')" @click="photo = null">
          <PhX :size="14" weight="bold" aria-hidden="true" />
        </button>
      </div>
      <UiButton v-else variant="secondary" :icon="PhCamera" @click="picker?.click()">{{ $t('common.photo') }}</UiButton>
      <UiButton type="submit" :icon="PhFloppyDisk" :loading="busy" :disabled="!canSave">{{ $t('today.saveNote') }}</UiButton>
    </div>
    <input ref="picker" type="file" accept="image/*" capture="environment" class="visually-hidden" tabindex="-1" @change="onPick" />
  </form>
</template>

<style scoped>
.note__incident {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--accent-soft-text);
  font-size: var(--text-sm);
  font-weight: 600;
}
.note__incident :deep(svg) {
  flex: none;
  margin-top: 1px;
}
.note__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.note__thumb {
  position: relative;
  width: 64px;
  height: 64px;
}
.note__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
}
.note__thumb-x {
  position: absolute;
  top: -8px;
  right: -8px;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 2px solid var(--surface);
  border-radius: 50%;
  background: var(--gray-800);
  color: var(--text-inverse);
}
</style>
