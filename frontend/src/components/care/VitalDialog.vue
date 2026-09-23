<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhInfo } from '@phosphor-icons/vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiFormErrors from '@/components/ui/UiFormErrors.vue'
import { position, rangeLabel } from '@/lib/care'
import { formatNumber } from '@/lib/format'
import type { FieldErrors } from '@/lib/api'
import type { VitalKind, VitalRange } from '@/types/care'

/** One reading, typed as the carer reads it off the device. The usual range is shown, never judged. */
const open = defineModel<boolean>('open', { default: false })
const props = defineProps<{ kind: VitalKind | null; range: VitalRange | null; busy: boolean; errors: FieldErrors; trigger: number }>()
const emit = defineEmits<{ submit: [value1: number | null, value2: number | null, note: string] }>()

const { t } = useI18n()
const first = ref('')
const second = ref('')
const note = ref('')

watch(open, (value) => {
  if (value) {
    first.value = ''
    second.value = ''
    note.value = ''
  }
})

function parse(raw: string): number | null {
  const n = Number(raw.replace(',', '.').trim())
  return raw.trim() && Number.isFinite(n) ? n : null
}

const paired = computed(() => props.kind === 'BP')
const decimal = computed(() => props.kind === 'TEMP' || props.kind === 'WEIGHT')
const usual = computed(() => rangeLabel(props.range, (n, d) => formatNumber(n, d)))
const hint = computed(() => {
  const v1 = parse(first.value)
  if (v1 === null || (paired.value && parse(second.value) === null)) return null
  return position(v1, paired.value ? parse(second.value) : null, props.range)
})

function submit() {
  emit('submit', parse(first.value), paired.value ? parse(second.value) : null, note.value.trim())
}
</script>

<template>
  <UiDialog v-model:open="open" :title="kind ? $t('vitals.addTitle', { kind: t(`vitals.kinds.${kind}`) }) : ''" :description="usual ? $t('vitals.usual', { range: usual }) : $t('vitals.noRange')">
    <form v-if="kind" id="vital-form" class="stack" novalidate @submit.prevent="submit">
      <UiFormErrors :errors="errors" :trigger="trigger" />
      <div :class="paired ? 'grid-2 pair' : ''">
        <UiField id="f-value1" :label="paired ? $t('vitals.systolic') : $t('vitals.value')" :error="errors.value1?.[0]">
          <template #default="{ id, invalid, describedby }">
            <UiInput
              :id="id"
              v-model="first"
              size="lg"
              :inputmode="decimal ? 'decimal' : 'numeric'"
              :suffix="$t(`vitals.units.${kind}`)"
              :invalid="invalid"
              :describedby="describedby"
              autocomplete="off"
              autofocus
            />
          </template>
        </UiField>
        <UiField v-if="paired" id="f-value2" :label="$t('vitals.diastolic')" :error="errors.value2?.[0]">
          <template #default="{ id, invalid, describedby }">
            <UiInput
              :id="id"
              v-model="second"
              size="lg"
              inputmode="numeric"
              :suffix="$t('vitals.units.BP')"
              :invalid="invalid"
              :describedby="describedby"
              autocomplete="off"
            />
          </template>
        </UiField>
      </div>
      <p v-if="hint" class="hint" role="status">
        <PhInfo :size="18" weight="bold" aria-hidden="true" />
        {{ hint === 'above' ? $t('vitals.above') : $t('vitals.below') }}. {{ $t('vitals.neutral') }}
      </p>
      <UiField id="f-vital-note" :label="$t('vitals.note')" optional>
        <template #default="{ id }">
          <UiInput :id="id" v-model="note" :placeholder="$t('vitals.notePlaceholder')" />
        </template>
      </UiField>
    </form>
    <template #footer>
      <UiButton variant="ghost" @click="open = false">{{ $t('common.cancel') }}</UiButton>
      <UiButton type="submit" form="vital-form" :loading="busy">{{ $t('common.save') }}</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.pair {
  gap: 12px;
}
@media (max-width: 760px) {
  .pair {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.hint {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--info-soft);
  color: var(--info-text);
  font-size: var(--text-sm);
}
.hint :deep(svg) {
  flex: none;
  margin-top: 2px;
}
</style>
