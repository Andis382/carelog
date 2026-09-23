<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { PhCalendarCheck, PhClockCounterClockwise, PhPencilSimple, PhPill, PhProhibit } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import ScheduleText from '@/components/care/ScheduleText.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiDl from '@/components/ui/UiDl.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiField from '@/components/ui/UiField.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api, ApiError } from '@/lib/api'
import { addDays, daysBetween, doseTone } from '@/lib/care'
import { dayKey, formatDate, formatDateTime, formatTime, formatWeekday } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useToasts } from '@/stores/toasts'
import type { DoseState, FieldChange, MedicationDetail } from '@/types/care'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const toasts = useToasts()

const id = computed(() => Number(route.params.id))
const detail = ref<MedicationDetail | null>(null)
const failed = ref(false)
const stopOpen = ref(false)
const stopReason = ref('')
const stopping = ref(false)

const legend: DoseState[] = ['GIVEN', 'REFUSED', 'SKIPPED', 'MISSED', 'UPCOMING']
const med = computed(() => detail.value?.medication ?? null)
const scheduled = computed(() => med.value?.frequency !== 'AS_NEEDED')

/** Two weeks as a grid: one row per time of day, one column per day. */
const grid = computed(() => {
  const d = detail.value
  if (!d || !scheduled.value) return null
  const today = dayKey(new Date())
  const span = daysBetween(d.recentFrom, today)
  const days = Array.from({ length: span + 1 }, (_, i) => addDays(d.recentFrom, i))
  const times = [...new Set(d.recent.map((r) => r.time).filter((x): x is string => !!x))].sort()
  const cell = (day: string, time: string) => d.recent.find((r) => r.date === day && r.time === time) ?? null
  return { days, times, cell }
})

function fieldValue(change: FieldChange, value: string | null) {
  if (value === null || value === '') return t('meds.emptyValue')
  if (change.field === 'frequency') return t(`meds.frequency.${value}`)
  if (change.field === 'weekdays') return value.split(',').map((n) => formatWeekday(addDays('2024-01-01', Number(n) - 1), 'short')).join(', ')
  if (change.field === 'startDate' || change.field === 'endDate') return formatDate(value)
  return value
}

function cellTitle(c: MedicationDetail['recent'][number] | null) {
  if (!c) return ''
  const state = t(`doses.state.${c.state}`)
  return c.byName && c.at ? `${state} · ${c.byName} ${formatTime(c.at)}` : state
}

async function load() {
  try {
    detail.value = await api.get<MedicationDetail>(`/medications/${id.value}`)
  } catch {
    failed.value = true
  }
}

async function stop() {
  stopping.value = true
  try {
    await api.post(`/medications/${id.value}/stop`, { reason: stopReason.value.trim() || null })
    toasts.success(t('meds.stoppedToast', { name: med.value?.label ?? '' }))
    stopOpen.value = false
    await load()
  } catch (e) {
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  } finally {
    stopping.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppPage
    :title="med?.label ?? $t('nav.medications')"
    :subtitle="med ? [med.doseText, med.instructions].filter(Boolean).join(' · ') || undefined : undefined"
    :back="{ name: 'medications' }"
    :back-label="$t('nav.medications')"
  >
    <template v-if="med && auth.canPlan && med.active" #actions>
      <UiButton variant="inverse" :icon="PhPencilSimple" :to="{ name: 'medication-edit', params: { id: med.id } }">{{ $t('common.edit') }}</UiButton>
      <UiButton variant="inverse" :icon="PhProhibit" @click="stopOpen = true">{{ $t('meds.stop') }}</UiButton>
    </template>

    <UiSkeleton v-if="!detail && !failed" card :lines="6" />
    <UiCard v-else-if="failed">
      <UiEmpty :icon="PhPill" :title="$t('errors.notFoundTitle')">
        <UiButton variant="secondary" @click="router.push({ name: 'medications' })">{{ $t('nav.medications') }}</UiButton>
      </UiEmpty>
    </UiCard>

    <div v-else-if="detail && med" class="layout">
      <div class="stack stack-lg">
        <UiCard :title="scheduled ? $t('meds.recent') : $t('meds.recentAsNeeded')" :icon="PhCalendarCheck">
          <template v-if="grid">
            <div v-if="grid.times.length" class="grid-wrap">
              <table class="dosegrid">
                <thead>
                  <tr>
                    <th scope="col" class="visually-hidden">{{ $t('common.time') }}</th>
                    <th v-for="day in grid.days" :key="day" scope="col" class="dosegrid__day">
                      <span>{{ formatWeekday(day, 'short').slice(0, 2) }}</span>
                      <span class="num">{{ Number(day.slice(8)) }}</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="time in grid.times" :key="time">
                    <th scope="row" class="dosegrid__time num">{{ time }}</th>
                    <td v-for="day in grid.days" :key="day">
                      <span
                        v-if="grid.cell(day, time)"
                        class="dosegrid__cell"
                        :class="`dosegrid__cell--${doseTone(grid.cell(day, time)!.state)}`"
                        :title="cellTitle(grid.cell(day, time))"
                        role="img"
                        :aria-label="`${formatDate(day, 'short')} ${time}: ${cellTitle(grid.cell(day, time))}`"
                      />
                      <span v-else class="dosegrid__cell dosegrid__cell--none" aria-hidden="true" />
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="legend">
                <span v-for="s in legend" :key="s" class="legend__item">
                  <span class="dosegrid__cell" :class="`dosegrid__cell--${doseTone(s)}`" aria-hidden="true" />
                  {{ $t(`doses.state.${s}`) }}
                </span>
              </div>
            </div>
            <p v-else class="muted small">{{ $t('meds.noRecent') }}</p>
          </template>
          <template v-else>
            <ul v-if="detail.recent.length" class="prn">
              <li v-for="(r, i) in detail.recent" :key="i" class="prn__row">
                <UiBadge :tone="doseTone(r.state)" size="sm">{{ $t(`doses.state.${r.state}`) }}</UiBadge>
                <span class="num strong">{{ r.at ? formatDateTime(r.at) : formatDate(r.date) }}</span>
                <span class="small muted">{{ r.byName }}</span>
                <span v-if="r.note" class="small muted prn__note">“{{ r.note }}”</span>
              </li>
            </ul>
            <p v-else class="muted small">{{ $t('meds.noRecent') }}</p>
          </template>
        </UiCard>

        <UiCard :title="$t('meds.history')" :icon="PhClockCounterClockwise">
          <ol class="history">
            <li v-for="h in detail.history" :key="h.id" class="history__item" :class="`history__item--${h.kind.toLowerCase()}`">
              <span class="history__dot" aria-hidden="true" />
              <div>
                <p class="strong">{{ $t(`meds.change.${h.kind}`) }}</p>
                <p v-for="c in h.changes" :key="c.field" class="small">
                  {{ $t('meds.changedField', { field: $t(`meds.fields.${c.field}`), from: fieldValue(c, c.from), to: fieldValue(c, c.to) }) }}
                </p>
                <p v-if="h.note" class="small muted">“{{ h.note }}”</p>
                <p class="xsmall subtle">{{ h.byName }} · <span class="num">{{ formatDateTime(h.at) }}</span></p>
              </div>
            </li>
          </ol>
        </UiCard>
      </div>

      <UiCard :title="$t('common.details')" :icon="PhPill" class="aside">
        <div class="stack">
          <UiBadge v-if="!med.active" tone="neutral" :icon="PhProhibit">{{ $t('meds.stopped') }}</UiBadge>
          <ScheduleText :medication="med" />
          <UiDl
            :items="[
              { label: $t('meds.startDate'), value: formatDate(med.startDate) },
              { label: $t('meds.endDate'), value: med.endDate ? formatDate(med.endDate) : '—' },
              { label: $t('meds.prescriber'), value: med.prescriber ?? '—' },
            ]"
          />
          <p v-if="med.stopReason" class="small muted">“{{ med.stopReason }}” · {{ med.stoppedByName }}</p>
          <img v-if="med.boxPhotoUrl" :src="med.boxPhotoUrl" :alt="$t('meds.boxPhoto')" class="box" />
        </div>
      </UiCard>
    </div>

    <UiDialog v-model:open="stopOpen" :title="$t('meds.stopTitle', { name: med?.label ?? '' })" :description="$t('meds.stopText')">
      <UiField id="f-stop-reason" :label="$t('meds.stopReason')" optional>
        <template #default="{ id: fid }">
          <UiTextarea :id="fid" v-model="stopReason" :rows="2" :placeholder="$t('meds.stopReasonPlaceholder')" />
        </template>
      </UiField>
      <template #footer>
        <UiButton variant="ghost" @click="stopOpen = false">{{ $t('common.cancel') }}</UiButton>
        <UiButton variant="danger" :icon="PhProhibit" :loading="stopping" @click="stop">{{ $t('meds.stop') }}</UiButton>
      </template>
    </UiDialog>
  </AppPage>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
}
@media (max-width: 960px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .aside {
    order: -1;
  }
}
.grid-wrap {
  overflow-x: auto;
}
.dosegrid {
  width: auto;
  border-collapse: separate;
  border-spacing: 5px;
}
.dosegrid__day {
  display: table-cell;
  min-width: 26px;
  font-size: 11px;
  font-weight: 650;
  color: var(--text-subtle);
  text-align: center;
  line-height: 1.2;
}
.dosegrid__day span {
  display: block;
}
.dosegrid__time {
  padding-right: 8px;
  font-family: var(--font-display);
  font-size: var(--text-sm);
  font-weight: 750;
  text-align: left;
}
.dosegrid__cell {
  display: block;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--gray-200);
}
.dosegrid__cell--success {
  background: linear-gradient(180deg, var(--brand-400), var(--success));
}
.dosegrid__cell--warning {
  background: var(--warning);
}
.dosegrid__cell--danger {
  background: repeating-linear-gradient(135deg, var(--danger-soft) 0 4px, color-mix(in srgb, var(--danger) 55%, transparent) 4px 8px);
  border: 1px solid color-mix(in srgb, var(--danger) 40%, transparent);
}
.dosegrid__cell--accent {
  background: var(--accent-400);
}
.dosegrid__cell--primary,
.dosegrid__cell--neutral {
  background: var(--gray-200);
}
.dosegrid__cell--none {
  background: transparent;
  border: 1px dashed var(--gray-200);
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 12px;
  font-size: var(--text-xs);
  color: var(--text-muted);
}
.legend__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend__item .dosegrid__cell {
  width: 14px;
  height: 14px;
  border-radius: 4px;
}
.prn {
  margin: 0;
  padding: 0;
  list-style: none;
}
.prn__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.prn__row:last-child {
  border-bottom: 0;
}
.prn__note {
  flex-basis: 100%;
}
.history {
  margin: 0;
  padding: 0;
  list-style: none;
}
.history__item {
  position: relative;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 16px;
}
.history__item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 16px;
  bottom: 0;
  width: 2px;
  background: var(--gray-100);
}
.history__dot {
  width: 16px;
  height: 16px;
  margin-top: 2px;
  border-radius: 50%;
  border: 3px solid var(--brand-400);
  background: var(--surface);
}
.history__item--stopped .history__dot {
  border-color: var(--gray-400);
}
.history__item--changed .history__dot {
  border-color: var(--accent-400);
}
.box {
  width: 100%;
  max-height: 220px;
  object-fit: cover;
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}
</style>
