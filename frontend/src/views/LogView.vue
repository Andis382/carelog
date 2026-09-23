<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhArrowDown, PhClockCounterClockwise, PhListBullets, PhLockSimple } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import LogItem from '@/components/care/LogItem.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api, query } from '@/lib/api'
import { addDays } from '@/lib/care'
import { dayKey, formatDate } from '@/lib/format'
import { useToasts } from '@/stores/toasts'
import type { LogEntry, LogPage, LogType } from '@/types/care'

const DAYS_PER_PAGE = 7
const TYPES: LogType[] = ['MEDS', 'VITALS', 'MEALS', 'NOTES', 'VISITS', 'SHIFTS', 'SUPPLIES']

const { t } = useI18n()
const toasts = useToasts()

const selected = ref<LogType[]>([])
const entries = ref<LogEntry[]>([])
const from = ref<string | null>(null)
const limited = ref(false)
const loading = ref(true)
const loadingMore = ref(false)

const today = computed(() => dayKey(new Date()))

/** Entries grouped under their day, newest day first (entries arrive newest first). */
const days = computed(() => {
  const groups: { day: string; entries: LogEntry[] }[] = []
  for (const e of entries.value) {
    const day = dayKey(e.at)
    const last = groups[groups.length - 1]
    if (last?.day === day) last.entries.push(e)
    else groups.push({ day, entries: [e] })
  }
  return groups
})

function dayLabel(day: string) {
  if (day === today.value) return t('common.today')
  if (day === addDays(today.value, -1)) return t('common.yesterday')
  const label = formatDate(day, 'long')
  return label.charAt(0).toUpperCase() + label.slice(1)
}

async function fetchPage(to: string | null): Promise<LogPage> {
  return api.get<LogPage>('/log' + query({ to, days: DAYS_PER_PAGE, types: selected.value.length ? selected.value.join(',') : null }))
}

async function load() {
  loading.value = true
  try {
    const page = await fetchPage(null)
    entries.value = page.entries
    from.value = page.from
    limited.value = page.limitedByPlan
  } catch {
    toasts.error(t('errors.generic'))
  } finally {
    loading.value = false
  }
}

async function older() {
  if (!from.value) return
  loadingMore.value = true
  try {
    const page = await fetchPage(addDays(from.value, -1))
    entries.value = [...entries.value, ...page.entries]
    from.value = page.from
    limited.value = page.limitedByPlan
  } catch {
    toasts.error(t('errors.generic'))
  } finally {
    loadingMore.value = false
  }
}

function toggle(type: LogType | null) {
  if (type === null) selected.value = []
  else if (selected.value.includes(type)) selected.value = selected.value.filter((x) => x !== type)
  else selected.value = [...selected.value, type]
}

onMounted(load)
watch(selected, load)
</script>

<template>
  <AppPage :title="$t('log.title')" :subtitle="$t('log.subtitle')">
    <div class="filters" role="group" :aria-label="$t('log.filters')">
      <button type="button" class="filter" :class="{ 'is-on': !selected.length }" :aria-pressed="!selected.length" @click="toggle(null)">
        {{ $t('common.all') }}
      </button>
      <button
        v-for="type in TYPES"
        :key="type"
        type="button"
        class="filter"
        :class="[`filter--${type.toLowerCase()}`, { 'is-on': selected.includes(type) }]"
        :aria-pressed="selected.includes(type)"
        @click="toggle(type)"
      >
        <span class="filter__dot" aria-hidden="true" />{{ $t(`log.types.${type}`) }}
      </button>
    </div>

    <UiCard v-if="loading" padding="lg"><UiSkeleton :lines="10" /></UiCard>

    <template v-else>
      <UiCard v-if="!entries.length && !limited">
        <UiEmpty :icon="PhListBullets" :title="selected.length ? $t('log.emptyFiltered') : $t('log.empty')" />
      </UiCard>

      <section v-for="group in days" :key="group.day" class="day">
        <h2 class="day__label">
          <span>{{ dayLabel(group.day) }}</span>
          <span class="day__count num">{{ group.entries.length }}</span>
        </h2>
        <UiCard padding="sm" class="day__card">
          <LogItem v-for="e in group.entries" :key="e.key" :entry="e" />
        </UiCard>
      </section>

      <UiCard v-if="limited" tone="primary" class="limit">
        <div class="limit__row">
          <span class="limit__icon"><PhLockSimple :size="22" weight="duotone" aria-hidden="true" /></span>
          <div class="limit__text">
            <h3>{{ $t('log.limitTitle') }}</h3>
            <p class="muted">{{ $t('log.limitText') }}</p>
          </div>
          <UiButton :to="{ name: 'circle', query: { tab: 'plan' } }">{{ $t('log.limitAction') }}</UiButton>
        </div>
      </UiCard>
      <div v-else-if="from" class="more">
        <UiButton variant="secondary" :icon="entries.length ? PhArrowDown : PhClockCounterClockwise" :loading="loadingMore" @click="older">
          {{ $t('log.older') }}
        </UiButton>
      </div>
    </template>
  </AppPage>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), var(--highlight);
}
.filter {
  --dot: var(--brand-500);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text);
  font-size: var(--text-sm);
  font-weight: 650;
  transition:
    background-color var(--duration) var(--ease),
    border-color var(--duration) var(--ease),
    color var(--duration) var(--ease);
}
.filter--vitals {
  --dot: var(--info);
}
.filter--meals {
  --dot: var(--warning);
}
.filter--notes,
.filter--shifts {
  --dot: var(--gray-500);
}
.filter--supplies {
  --dot: var(--accent-500);
}
.filter__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--dot);
}
.filter:hover {
  border-color: var(--primary-soft-border);
  background: var(--primary-soft);
}
.filter.is-on {
  color: var(--on-primary);
  border-color: var(--brand-700);
  background: linear-gradient(180deg, var(--brand-500), var(--brand-600));
  box-shadow: var(--primary-shadow);
}
.filter.is-on .filter__dot {
  background: var(--surface);
}
.day {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.day__label {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 2px;
  background: linear-gradient(180deg, var(--bg) 70%, transparent);
  font-size: var(--text-md);
  font-weight: 750;
}
.day__count {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--surface-sunken);
  color: var(--text-muted);
  font-size: var(--text-xs);
  font-weight: 700;
}
.day__card :deep(.card__body) > :last-child {
  border-bottom: 0;
}
.more {
  display: flex;
  justify-content: center;
}
.limit__row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.limit__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: var(--radius);
  color: var(--primary);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}
.limit__text {
  flex: 1;
}
@media (max-width: 640px) {
  .limit__row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
