<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { PhChartLine, PhHeartbeat, PhPlus } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import VitalChart from '@/components/care/VitalChart.vue'
import VitalDialog from '@/components/care/VitalDialog.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiNotice from '@/components/ui/UiNotice.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import UiTabs from '@/components/ui/UiTabs.vue'
import { api, ApiError, query, type FieldErrors } from '@/lib/api'
import { formatVital, rangeLabel, VITAL_KINDS } from '@/lib/care'
import { formatDateTime, formatNumber } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useToasts } from '@/stores/toasts'
import type { VitalHistory, VitalKind } from '@/types/care'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const toasts = useToasts()

const kind = ref<VitalKind>(VITAL_KINDS.includes(route.query.kind as VitalKind) ? (route.query.kind as VitalKind) : 'BP')
const days = ref<7 | 30 | 90>(30)
const history = ref<VitalHistory | null>(null)
const loading = ref(true)

const tabs = computed(() => VITAL_KINDS.map((k) => ({ value: k, label: t(`vitals.short.${k}`) })))
const periods = computed(() => ([7, 30, 90] as const).map((n) => ({ value: n, label: t('vitals.days', { n }) })))
const unit = computed(() => t(`vitals.units.${kind.value}`))
const usual = computed(() => rangeLabel(history.value?.range, fmt))
const TABLE_ROWS = 12
const showAll = ref(false)
const newestFirst = computed(() => [...(history.value?.readings ?? [])].reverse())
const tableRows = computed(() => (showAll.value ? newestFirst.value : newestFirst.value.slice(0, TABLE_ROWS)))
const clipped = computed(() => {
  const h = history.value
  return !!h?.visibleFrom && h.from === h.visibleFrom
})

function fmt(n: number, digits: number) {
  return formatNumber(n, digits)
}

function stat(a: number | null, b: number | null) {
  return a === null ? '—' : formatVital(kind.value, a, kind.value === 'BP' ? b : null, fmt)
}

async function load() {
  loading.value = true
  try {
    history.value = await api.get<VitalHistory>('/vitals' + query({ kind: kind.value, days: days.value }))
  } catch {
    toasts.error(t('errors.generic'))
  } finally {
    loading.value = false
  }
}

watch([kind, days], () => {
  showAll.value = false
  router.replace({ query: { kind: kind.value } })
  load()
})
onMounted(load)

const dialogOpen = ref(false)
const saving = ref(false)
const errors = ref<FieldErrors>({})
const trigger = ref(0)

async function save(value1: number | null, value2: number | null, note: string) {
  saving.value = true
  errors.value = {}
  try {
    await api.post('/vitals', { kind: kind.value, value1, value2, note })
    toasts.success(t('vitals.saved', { kind: t(`vitals.kinds.${kind.value}`) }))
    dialogOpen.value = false
    load()
  } catch (e) {
    if (e instanceof ApiError && e.status === 422) {
      errors.value = e.errors
      trigger.value++
    } else toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppPage :title="$t('vitals.title')" :subtitle="$t('vitals.subtitle')">
    <template v-if="auth.canRecord" #actions>
      <UiButton variant="inverse" size="lg" :icon="PhPlus" @click="dialogOpen = true">{{ $t('vitals.add') }}</UiButton>
    </template>

    <UiCard padding="sm">
      <div class="controls">
        <UiTabs v-model="kind" :tabs="tabs" :label="$t('vitals.title')" />
        <UiSegmented v-model="days" :options="periods" :label="$t('vitals.period')" />
      </div>
    </UiCard>

    <div class="stats">
      <div class="stat">
        <span class="stat__label">{{ $t('vitals.average') }}</span>
        <span class="stat__value num">{{ history ? stat(history.stats.avg, history.stats.avg2) : '—' }}<small>{{ unit }}</small></span>
      </div>
      <div class="stat">
        <span class="stat__label">{{ $t('vitals.lowest') }}</span>
        <span class="stat__value num">{{ history ? stat(history.stats.min, history.stats.min2) : '—' }}</span>
      </div>
      <div class="stat">
        <span class="stat__label">{{ $t('vitals.highest') }}</span>
        <span class="stat__value num">{{ history ? stat(history.stats.max, history.stats.max2) : '—' }}</span>
      </div>
      <div class="stat stat--range">
        <span class="stat__label">{{ $t('vitals.readings') }}</span>
        <span class="stat__value num">{{ history?.stats.count ?? '—' }}</span>
        <span class="stat__hint">{{ usual ? $t('vitals.usual', { range: usual }) : $t('vitals.noRange') }}</span>
      </div>
    </div>

    <UiNotice v-if="clipped" tone="info">{{ $t('log.limitText') }}</UiNotice>

    <UiCard :title="$t(`vitals.kinds.${kind}`)" :subtitle="usual ? $t('vitals.usual', { range: usual }) + ' ' + unit : undefined" :icon="PhChartLine">
      <template v-if="kind === 'BP'" #actions>
        <div class="legend">
          <span class="legend__item"><span class="legend__line" />{{ $t('vitals.systolic') }}</span>
          <span class="legend__item"><span class="legend__line legend__line--second" />{{ $t('vitals.diastolic') }}</span>
        </div>
      </template>
      <UiSkeleton v-if="loading && !history" height="240px" :lines="1" />
      <UiEmpty v-else-if="history && !history.readings.length" compact :icon="PhHeartbeat" :title="$t('vitals.empty')" :text="$t('vitals.emptyText')" />
      <VitalChart
        v-else-if="history"
        :kind="kind"
        :readings="history.readings"
        :range="history.range"
        :from="history.from"
        :label="$t('vitals.chartLabel', { kind: $t(`vitals.kinds.${kind}`), n: days })"
      />
      <p class="neutral xsmall subtle">{{ $t('vitals.neutral') }}</p>
    </UiCard>

    <UiCard v-if="history?.readings.length" :title="$t('vitals.table')" padding="none">
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>{{ $t('common.date') }}</th>
              <th class="num">{{ $t(`vitals.kinds.${kind}`) }}</th>
              <th>{{ $t('vitals.by') }}</th>
              <th>{{ $t('vitals.note') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in tableRows" :key="r.id">
              <td class="nowrap num">{{ formatDateTime(r.measuredAt) }}</td>
              <td class="nowrap">
                <span class="value num">{{ formatVital(kind, r.value1, r.value2, fmt) }}</span>
                <UiBadge v-if="r.position" tone="info" size="sm">{{ r.position === 'above' ? $t('vitals.aboveShort') : $t('vitals.belowShort') }}</UiBadge>
              </td>
              <td class="nowrap muted">{{ r.by }}</td>
              <td class="muted">{{ r.note }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <template v-if="newestFirst.length > TABLE_ROWS" #footer>
        <UiButton variant="ghost" size="sm" @click="showAll = !showAll">
          {{ showAll ? $t('vitals.showFewer') : $t('vitals.showAll', { n: newestFirst.length }) }}
        </UiButton>
      </template>
    </UiCard>

    <VitalDialog
      v-model:open="dialogOpen"
      :kind="kind"
      :range="history?.range ?? null"
      :busy="saving"
      :errors="errors"
      :trigger="trigger"
      @submit="save"
    />
  </AppPage>
</template>

<style scoped>
.controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.controls :deep(.tabs) {
  overflow-x: auto;
  scrollbar-width: none;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
@media (max-width: 760px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 16px 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), var(--highlight);
}
.stat__label {
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-subtle);
}
.stat__value {
  font-family: var(--font-display);
  font-size: 1.7rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.stat__value small {
  margin-left: 5px;
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-subtle);
  letter-spacing: 0;
}
.stat__hint {
  font-size: var(--text-xs);
  color: var(--text-muted);
}
.legend {
  display: flex;
  gap: 14px;
  font-size: var(--text-xs);
  color: var(--text-muted);
}
.legend__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend__line {
  width: 18px;
  height: 3px;
  border-radius: 2px;
  background: var(--brand-600);
}
.legend__line--second {
  background: var(--accent-500);
}
.neutral {
  margin-top: 10px;
}
.value {
  margin-right: 8px;
  font-family: var(--font-display);
  font-weight: 750;
}
</style>
