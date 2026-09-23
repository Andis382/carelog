<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { PhClock, PhFloppyDisk, PhPlus, PhX } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiPhotoInput from '@/components/ui/UiPhotoInput.vue'
import UiFormErrors from '@/components/ui/UiFormErrors.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api } from '@/lib/api'
import { useForm } from '@/lib/form'
import { addDays, isoDate } from '@/lib/care'
import { formatWeekday } from '@/lib/format'
import { useToasts } from '@/stores/toasts'
import type { Frequency, Medication, MedicationDetail } from '@/types/care'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toasts = useToasts()

const editing = computed(() => route.name === 'medication-edit')
const id = computed(() => Number(route.params.id))
const ready = ref(!editing.value)
const photo = ref<File | null>(null)
const photoUrl = ref<string | null>(null)
const newTime = ref('08:00')

const form = useForm({
  name: '',
  strength: '',
  doseText: '',
  instructions: '',
  frequency: 'DAILY' as Frequency,
  times: [] as string[],
  weekdays: [] as number[],
  startDate: isoDate(new Date()),
  endDate: '',
  prescriber: '',
  boxPhotoFileId: null as string | null,
})

const frequencies = computed(() => (['DAILY', 'WEEKLY', 'AS_NEEDED'] as Frequency[]).map((f) => ({ value: f, label: t(`meds.frequency.${f}`) })))
const presets = ['08:00', '13:00', '20:00', '21:00']
// 2024-01-01 was a Monday: ISO weekday n is that date plus n - 1.
const weekdays = computed(() => [1, 2, 3, 4, 5, 6, 7].map((n) => ({ n, label: formatWeekday(addDays('2024-01-01', n - 1), 'short') })))

function addTime(time: string) {
  if (!time || form.data.times.includes(time)) return
  form.data.times = [...form.data.times, time].sort()
}

function removeTime(time: string) {
  form.data.times = form.data.times.filter((x) => x !== time)
}

function toggleDay(n: number) {
  form.data.weekdays = form.data.weekdays.includes(n) ? form.data.weekdays.filter((d) => d !== n) : [...form.data.weekdays, n].sort()
}

onMounted(async () => {
  if (!editing.value) return
  const detail = await api.get<MedicationDetail>(`/medications/${id.value}`)
  const m = detail.medication
  form.fill({
    name: m.name,
    strength: m.strength ?? '',
    doseText: m.doseText ?? '',
    instructions: m.instructions ?? '',
    frequency: m.frequency,
    times: m.times,
    weekdays: m.weekdays,
    startDate: m.startDate,
    endDate: m.endDate ?? '',
    prescriber: m.prescriber ?? '',
    boxPhotoFileId: m.boxPhotoFileId,
  })
  photoUrl.value = m.boxPhotoUrl
  ready.value = true
})

async function save() {
  const saved = await form.submit(async () => {
    if (photo.value) {
      const upload = new FormData()
      upload.append('file', photo.value, photo.value.name || 'box.jpg')
      form.data.boxPhotoFileId = (await api.upload<{ id: string }>('/uploads', upload)).id
    }
    const body = { ...form.data, endDate: form.data.endDate || null, times: form.data.frequency === 'AS_NEEDED' ? [] : form.data.times }
    return editing.value ? api.put<Medication>(`/medications/${id.value}`, body) : api.post<Medication>('/medications', body)
  })
  if (saved) {
    toasts.success(t('meds.saved'))
    router.replace({ name: 'medication', params: { id: saved.id } })
  }
}
</script>

<template>
  <AppPage
    :title="editing ? $t('meds.edit') : $t('meds.add')"
    :subtitle="$t('meds.subtitle')"
    :back="editing ? { name: 'medication', params: { id } } : { name: 'medications' }"
  >
    <UiSkeleton v-if="!ready" card :lines="8" />
    <form v-else class="layout" novalidate @submit.prevent="save">
      <UiCard class="main">
        <div class="stack">
          <UiFormErrors :errors="form.errors.value" :message="form.message.value" :trigger="form.submitted.value" />
          <div class="grid-2">
            <UiField id="f-name" :label="$t('meds.name')" :error="form.error('name')" required>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.name" :placeholder="$t('meds.namePlaceholder')" :invalid="invalid" :describedby="describedby" size="lg" />
              </template>
            </UiField>
            <UiField id="f-strength" :label="$t('meds.strength')" :error="form.error('strength')" optional>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.strength" :placeholder="$t('meds.strengthPlaceholder')" :invalid="invalid" :describedby="describedby" size="lg" />
              </template>
            </UiField>
          </div>
          <div class="grid-2">
            <UiField id="f-dose" :label="$t('meds.doseText')" :error="form.error('doseText')" optional>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.doseText" :placeholder="$t('meds.dosePlaceholder')" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
            <UiField id="f-prescriber" :label="$t('meds.prescriber')" :error="form.error('prescriber')" optional>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.prescriber" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
          </div>
          <UiField id="f-instructions" :label="$t('meds.instructions')" :error="form.error('instructions')" optional>
            <template #default="{ id: fid, invalid, describedby }">
              <UiTextarea :id="fid" v-model="form.data.instructions" :rows="2" :placeholder="$t('meds.instructionsPlaceholder')" :invalid="invalid" :describedby="describedby" />
            </template>
          </UiField>

          <div class="stack stack-sm">
            <p class="field-label">{{ $t('meds.frequencyLabel') }}</p>
            <UiSegmented v-model="form.data.frequency" :options="frequencies" :label="$t('meds.frequencyLabel')" block />
          </div>

          <UiField v-if="form.data.frequency === 'WEEKLY'" id="f-weekdays" :label="$t('meds.weekdays')" :error="form.error('weekdays')">
            <div id="f-weekdays" class="days" role="group" :aria-label="$t('meds.weekdays')" tabindex="-1">
              <button
                v-for="d in weekdays"
                :key="d.n"
                type="button"
                class="day"
                :class="{ 'is-on': form.data.weekdays.includes(d.n) }"
                :aria-pressed="form.data.weekdays.includes(d.n)"
                @click="toggleDay(d.n)"
              >
                {{ d.label }}
              </button>
            </div>
          </UiField>

          <UiField v-if="form.data.frequency !== 'AS_NEEDED'" id="f-times" :label="$t('meds.times')" :hint="$t('meds.timesHint')" :error="form.error('times')">
            <div class="times">
              <div class="times__chips">
                <span v-for="time in form.data.times" :key="time" class="chip num">
                  {{ time }}
                  <button type="button" class="chip__x" :aria-label="$t('meds.removeTime', { time })" @click="removeTime(time)">
                    <PhX :size="14" weight="bold" aria-hidden="true" />
                  </button>
                </span>
                <button
                  v-for="p in presets.filter((x) => !form.data.times.includes(x))"
                  :key="p"
                  type="button"
                  class="preset num"
                  @click="addTime(p)"
                >
                  <PhPlus :size="12" weight="bold" aria-hidden="true" /> {{ p }}
                </button>
              </div>
              <div class="times__add">
                <UiInput id="f-times" v-model="newTime" type="time" :icon="PhClock" class="times__input" />
                <UiButton variant="secondary" :icon="PhPlus" @click="addTime(newTime)">{{ $t('meds.addTime') }}</UiButton>
              </div>
            </div>
          </UiField>

          <div class="grid-2">
            <UiField id="f-start" :label="$t('meds.startDate')" :error="form.error('startDate')">
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.startDate" type="date" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
            <UiField id="f-end" :label="$t('meds.endDate')" :hint="$t('meds.endHint')" :error="form.error('endDate')" optional>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.endDate" type="date" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
          </div>
        </div>
      </UiCard>

      <div class="side">
        <UiCard :title="$t('meds.boxPhoto')" :subtitle="$t('meds.boxPhotoHint')">
          <UiPhotoInput id="f-photo" v-model="photo" :current-url="photoUrl" aspect="4 / 3" />
        </UiCard>
        <UiButton type="submit" size="lg" block :icon="PhFloppyDisk" :loading="form.processing.value">{{ $t('common.save') }}</UiButton>
      </div>
    </form>
  </AppPage>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
  align-items: start;
}
.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 16px;
}
@media (max-width: 960px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .side {
    position: static;
  }
}
.field-label {
  font-size: var(--text-sm);
  font-weight: 650;
  color: var(--text);
}
.days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
  outline: none;
}
.day {
  min-height: 46px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  font-weight: 650;
  font-size: var(--text-sm);
  text-transform: capitalize;
}
.day.is-on {
  color: var(--on-primary);
  border-color: var(--brand-700);
  background: linear-gradient(180deg, var(--brand-500), var(--brand-600));
  box-shadow: var(--primary-shadow);
}
.times {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.times__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 40px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 6px 0 14px;
  border-radius: var(--radius-pill);
  color: var(--on-primary);
  background: linear-gradient(180deg, var(--brand-500), var(--brand-600));
  box-shadow: var(--primary-shadow);
  font-family: var(--font-display);
  font-weight: 750;
}
.chip__x {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: rgb(255 255 255 / 0.18);
  color: inherit;
}
.preset {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 40px;
  padding: 0 14px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text-muted);
  font-weight: 650;
}
.preset:hover {
  color: var(--primary-strong);
  border-color: var(--primary-soft-border);
  background: var(--primary-soft);
}
.times__add {
  display: flex;
  gap: 8px;
  align-items: center;
}
.times__input {
  max-width: 180px;
}
</style>
