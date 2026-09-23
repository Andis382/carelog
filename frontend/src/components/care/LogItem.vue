<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PhArrowsLeftRight,
  PhDoorOpen,
  PhForkKnife,
  PhHeartbeat,
  PhMapPin,
  PhNotePencil,
  PhPackage,
  PhPill,
  PhStethoscope,
  PhWarningOctagon,
} from '@phosphor-icons/vue'
import UiAvatar from '@/components/ui/UiAvatar.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import { formatVital, splitMinutes } from '@/lib/care'
import { dayKey, formatDate, formatNumber, formatTime } from '@/lib/format'
import type { LogEntry, VitalKind } from '@/types/care'

/** One line of the shared log: what happened, who did it, at what time. */
const props = defineProps<{ entry: LogEntry }>()
const { t } = useI18n()

const icons: Record<LogEntry['type'], Component> = {
  MEDS: PhPill,
  VITALS: PhHeartbeat,
  MEALS: PhForkKnife,
  NOTES: PhNotePencil,
  VISITS: PhStethoscope,
  SHIFTS: PhMapPin,
  SUPPLIES: PhPackage,
}

const d = computed(() => props.entry.data)
const str = (k: string) => (d.value[k] === null || d.value[k] === undefined ? '' : String(d.value[k]))

const icon = computed(() => {
  const e = props.entry
  if (e.type === 'NOTES' && e.kind === 'INCIDENT') return PhWarningOctagon
  if (e.type === 'SHIFTS' && e.kind === 'CHECK_OUT') return PhDoorOpen
  if (e.type === 'SHIFTS' && e.kind.startsWith('SWAP')) return PhArrowsLeftRight
  return icons[e.type]
})

const tone = computed(() => {
  const e = props.entry
  if (e.kind === 'INCIDENT' || e.kind === 'DOSE_REFUSED') return 'warm'
  if (e.kind === 'DOSE_SKIPPED' || e.kind === 'STOPPED') return 'quiet'
  return e.type.toLowerCase()
})

function duration(minutes: number) {
  const { h, m } = splitMinutes(minutes)
  if (!h) return t('common.minutes', { m })
  return m ? t('common.hoursMinutes', { h, m }) : t('common.hours', { h })
}

const title = computed(() => {
  const e = props.entry
  switch (e.type) {
    case 'MEDS':
      return t(`log.entry.${e.kind}`, { medication: str('medication') })
    case 'VITALS': {
      const kind = e.kind as VitalKind
      const value = formatVital(kind, Number(d.value.value1), d.value.value2 === null ? null : Number(d.value.value2), (n, digits) =>
        formatNumber(n, digits),
      )
      return `${t(`vitals.kinds.${kind}`)} ${value} ${t(`vitals.units.${kind}`)}`
    }
    case 'MEALS':
      if (e.kind === 'DRINK') return t('log.entry.drink', { n: Number(d.value.glasses) }, Number(d.value.glasses))
      return t('log.entry.meal', { slot: t(`meals.slots.${e.kind}`), amount: t(`meals.amounts.${str('amount')}`).toLowerCase() })
    case 'NOTES':
      return t(`journal.kinds.${e.kind}`)
    case 'VISITS':
      return t('log.entry.visit', { doctor: str('doctorName') }) + (d.value.specialty ? ` · ${str('specialty')}` : '')
    case 'SHIFTS':
      if (e.kind === 'CHECK_IN') return t('log.entry.CHECK_IN')
      if (e.kind === 'CHECK_OUT') return t('log.entry.CHECK_OUT', { duration: duration(Number(d.value.minutes)) })
      return t(`log.entry.${e.kind}`, {
        from: str('from'),
        date: d.value.date ? formatDate(str('date'), 'short') : '',
        start: str('start'),
        end: str('end'),
      })
    case 'SUPPLIES':
      return t('log.entry.supply', { name: str('name'), status: t(`supplies.status.${e.kind}`) })
  }
  return ''
})

/** A second line with the detail that matters: which dose, the note, the score. */
const detail = computed(() => {
  const e = props.entry
  if (e.type === 'MEDS' && e.kind.startsWith('DOSE') && d.value.time) {
    const sameDay = str('date') === dayKey(e.at)
    return sameDay ? t('log.entry.forSlot', { time: str('time') }) : t('log.entry.forSlotDay', { time: str('time'), date: formatDate(str('date'), 'short') })
  }
  if (e.type === 'NOTES' && d.value.score !== null && d.value.score !== undefined) {
    const scale = e.kind === 'MOOD' ? 'mood' : e.kind === 'PAIN' ? 'pain' : null
    return scale ? `${t(`journal.${scale}.${str('score')}`)} · ${t('journal.scoreOf', { score: str('score') })}` : t('journal.scoreOf', { score: str('score') })
  }
  if (e.type === 'SHIFTS' && e.kind === 'CHECK_IN' && d.value.located) return t('log.entry.located')
  if (e.type === 'VISITS' && d.value.nextDate) return t('log.entry.nextVisit', { date: formatDate(str('nextDate'), 'medium') })
  return null
})

const text = computed(() => {
  const e = props.entry
  if (e.type === 'NOTES') return str('text')
  if (e.type === 'VISITS') return str('notes')
  return str('note')
})

const photo = computed(() => str('photoUrl') || str('prescriptionUrl') || null)
</script>

<template>
  <article class="item" :class="`item--${tone}`">
    <span class="item__icon"><component :is="icon" :size="18" weight="duotone" aria-hidden="true" /></span>
    <div class="item__body">
      <p class="item__title">
        {{ title }}
        <UiBadge v-if="entry.data.position" tone="info" size="sm">
          {{ entry.data.position === 'above' ? $t('vitals.aboveShort') : $t('vitals.belowShort') }}
        </UiBadge>
      </p>
      <p v-if="detail" class="item__detail">{{ detail }}</p>
      <p v-if="text" class="item__text">{{ text }}</p>
      <a v-if="photo" :href="photo" target="_blank" rel="noopener" class="item__photo">
        <img :src="photo" :alt="$t('common.photo')" loading="lazy" />
      </a>
    </div>
    <div class="item__meta">
      <span class="item__time num">{{ formatTime(entry.at) }}</span>
      <span v-if="entry.by" class="item__who">
        <UiAvatar :name="entry.by" :size="20" />
        <span class="truncate">{{ entry.by }}</span>
      </span>
    </div>
  </article>
</template>

<style scoped>
.item {
  --tint: var(--brand-600);
  --tint-soft: var(--brand-50);
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  padding: 12px 4px;
  border-bottom: 1px solid var(--border);
}
.item--vitals {
  --tint: var(--info);
  --tint-soft: var(--info-soft);
}
.item--meals {
  --tint: var(--warning);
  --tint-soft: var(--warning-soft);
}
.item--notes {
  --tint: var(--gray-600);
  --tint-soft: var(--gray-100);
}
.item--visits {
  --tint: var(--brand-700);
  --tint-soft: var(--brand-100);
}
.item--shifts {
  --tint: var(--gray-600);
  --tint-soft: var(--surface-sunken);
}
.item--supplies,
.item--warm {
  --tint: var(--accent-600);
  --tint-soft: var(--accent-soft);
}
.item--quiet {
  --tint: var(--gray-500);
  --tint-soft: var(--gray-100);
}
.item__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: var(--radius-sm);
  color: var(--tint);
  background: var(--tint-soft);
  border: 1px solid color-mix(in srgb, var(--tint) 16%, transparent);
}
.item__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-weight: 650;
  color: var(--text);
}
.item__detail {
  font-size: var(--text-sm);
  color: var(--text-muted);
}
.item__text {
  margin-top: 4px;
  font-size: var(--text-sm);
  color: var(--text);
  white-space: pre-line;
}
.item__photo img {
  width: 132px;
  height: 96px;
  margin-top: 8px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
}
.item__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  max-width: 150px;
  text-align: right;
}
.item__time {
  font-family: var(--font-display);
  font-weight: 750;
  font-size: var(--text-sm);
  color: var(--text);
}
.item__who {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  font-size: var(--text-xs);
  color: var(--text-muted);
}
@media (max-width: 640px) {
  .item {
    grid-template-columns: 34px minmax(0, 1fr);
  }
  .item__icon {
    width: 34px;
    height: 34px;
  }
  .item__meta {
    grid-column: 2;
    flex-direction: row;
    align-items: center;
    max-width: none;
    text-align: left;
  }
}
</style>
