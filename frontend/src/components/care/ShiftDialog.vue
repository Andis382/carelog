<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiFormErrors from '@/components/ui/UiFormErrors.vue'
import { api } from '@/lib/api'
import { useForm } from '@/lib/form'
import type { Person, ShiftKind, ShiftView } from '@/types/care'

/** Add a shift; owner and family for anyone, a carer for herself. Weekly repeats in one go. */
const open = defineModel<boolean>('open', { default: false })
const props = defineProps<{ people: Person[]; me: number; canPlan: boolean; day: string }>()
const emit = defineEmits<{ saved: [shifts: ShiftView[]] }>()

const { t } = useI18n()
const form = useForm({ userId: props.me, date: props.day, start: '08:00', end: '16:00', kind: 'DAY' as ShiftKind, note: '', repeatWeeks: 0 })

const who = computed(() => (props.canPlan ? props.people : props.people.filter((p) => p.id === props.me)).map((p) => ({ value: p.id, label: p.name })))
const kinds = computed(() => (['DAY', 'NIGHT', 'VISIT'] as ShiftKind[]).map((k) => ({ value: k, label: t(`rota.kinds.${k}`) })))
const repeats = computed(() => [0, 1, 2, 3, 4, 6, 8].map((n) => ({ value: n, label: n ? t('rota.repeatWeeks', { n }, n) : t('rota.repeatNone') })))

const presets: Record<ShiftKind, [string, string]> = { DAY: ['08:00', '16:00'], NIGHT: ['21:30', '07:30'], VISIT: ['18:30', '21:00'] }

watch(open, (value) => {
  if (value) form.reset({ userId: props.canPlan ? (props.people[0]?.id ?? props.me) : props.me, date: props.day })
})
watch(
  () => form.data.kind,
  (kind) => {
    ;[form.data.start, form.data.end] = presets[kind]
  },
)

async function save() {
  const saved = await form.submit(() => api.post<ShiftView[]>('/shifts', form.data))
  if (saved) {
    emit('saved', saved)
    open.value = false
  }
}
</script>

<template>
  <UiDialog v-model:open="open" :title="$t('rota.addShift')">
    <form id="shift-form" class="stack" novalidate @submit.prevent="save">
      <UiFormErrors :errors="form.errors.value" :message="form.message.value" :trigger="form.submitted.value" />
      <UiField id="f-shift-user" :label="$t('rota.person')" :error="form.error('userId')">
        <template #default="{ id }">
          <UiSelect :id="id" v-model="form.data.userId" :options="who" />
        </template>
      </UiField>
      <UiSegmented v-model="form.data.kind" :options="kinds" :label="$t('rota.kind')" block />
      <UiField id="f-shift-date" :label="$t('rota.date')" :error="form.error('date')">
        <template #default="{ id, invalid, describedby }">
          <UiInput :id="id" v-model="form.data.date" type="date" :invalid="invalid" :describedby="describedby" />
        </template>
      </UiField>
      <div class="grid-2 pair">
        <UiField id="f-shift-start" :label="$t('rota.start')" :error="form.error('start')">
          <template #default="{ id, invalid, describedby }">
            <UiInput :id="id" v-model="form.data.start" type="time" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
        <UiField id="f-shift-end" :label="$t('rota.end')" :error="form.error('end')">
          <template #default="{ id, invalid, describedby }">
            <UiInput :id="id" v-model="form.data.end" type="time" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
      </div>
      <UiField id="f-shift-repeat" :label="$t('rota.repeat')" :hint="$t('rota.repeatHint')">
        <template #default="{ id }">
          <UiSelect :id="id" v-model="form.data.repeatWeeks" :options="repeats" />
        </template>
      </UiField>
      <UiField id="f-shift-note" :label="$t('rota.note')" optional>
        <template #default="{ id }">
          <UiInput :id="id" v-model="form.data.note" />
        </template>
      </UiField>
    </form>
    <template #footer>
      <UiButton variant="ghost" @click="open = false">{{ $t('common.cancel') }}</UiButton>
      <UiButton type="submit" form="shift-form" :loading="form.processing.value">{{ $t('common.save') }}</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
@media (max-width: 760px) {
  .pair {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
