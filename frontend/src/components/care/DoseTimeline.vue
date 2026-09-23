<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhArrowCounterClockwise, PhCheck, PhCheckCircle, PhCloudArrowUp, PhDotsThreeOutline, PhProhibit, PhSkipForward } from '@phosphor-icons/vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import { doseTone, groupByTime, isOpen, splitMinutes } from '@/lib/care'
import { formatTime } from '@/lib/format'
import type { DoseLine, DoseState } from '@/types/care'

const props = defineProps<{
  lines: DoseLine[]
  canRecord: boolean
  /** key of the dose being saved right now */
  busy: string | null
  /** doses recorded on this phone that are waiting for a connection */
  queued: string[]
}>()

const emit = defineEmits<{ give: [line: DoseLine]; notGiven: [line: DoseLine]; undo: [line: DoseLine] }>()

const { t } = useI18n()
const groups = computed(() => groupByTime(props.lines))

function key(line: DoseLine) {
  return `${line.medicationId}-${line.time}`
}

function duration(minutes: number) {
  const { h, m } = splitMinutes(minutes)
  if (!h) return t('common.minutes', { m })
  return m ? t('common.hoursMinutes', { h, m }) : t('common.hours', { h })
}

/** The most pressing state in a group colours its dot on the rail. */
const urgency: DoseState[] = ['MISSED', 'LATE', 'DUE', 'UPCOMING', 'REFUSED', 'SKIPPED', 'GIVEN']
function groupState(lines: DoseLine[]): DoseState {
  return urgency.find((s) => lines.some((l) => l.state === s)) ?? 'UPCOMING'
}

function outcome(line: DoseLine) {
  const e = line.event
  if (!e) return ''
  const time = formatTime(e.at)
  if (line.state === 'GIVEN') return t(line.givenLate ? 'doses.givenLateBy' : 'doses.givenBy', { name: e.by, time })
  if (line.state === 'SKIPPED') return t('doses.skippedBy', { name: e.by, time })
  return t('doses.refusedBy', { name: e.by, time })
}
</script>

<template>
  <ol class="timeline">
    <li v-for="group in groups" :key="group.time" class="slot" :class="`slot--${groupState(group.lines).toLowerCase()}`">
      <div class="slot__rail" aria-hidden="true"><span class="slot__dot" /></div>
      <p class="slot__time num">{{ group.time }}</p>
      <div class="slot__doses">
        <article
          v-for="line in group.lines"
          :key="key(line)"
          class="dose"
          :class="[`dose--${line.state.toLowerCase()}`, { 'dose--queued': queued.includes(key(line)) }]"
        >
          <div class="dose__what">
            <p class="dose__name">
              {{ line.name }} <span v-if="line.strength" class="dose__strength">{{ line.strength }}</span>
            </p>
            <p v-if="line.doseText || line.instructions" class="dose__how">
              {{ [line.doseText, line.instructions].filter(Boolean).join(' · ') }}
            </p>

            <p v-if="isOpen(line.state)" class="dose__state">
              <UiBadge :tone="doseTone(line.state)" size="sm" :dot="line.state === 'DUE'">
                <template v-if="line.state === 'LATE'">{{ $t('doses.lateBy', { time: duration(line.minutesLate) }) }}</template>
                <template v-else-if="line.state === 'MISSED'">{{ $t('doses.missedBy', { time: duration(line.minutesLate) }) }}</template>
                <template v-else>{{ $t(`doses.state.${line.state}`) }}</template>
              </UiBadge>
            </p>
            <p v-else class="dose__outcome" :class="`dose__outcome--${line.state.toLowerCase()}`">
              <PhCloudArrowUp v-if="queued.includes(key(line))" :size="18" weight="bold" aria-hidden="true" />
              <PhCheckCircle v-else-if="line.state === 'GIVEN'" :size="18" weight="fill" aria-hidden="true" />
              <PhSkipForward v-else-if="line.state === 'SKIPPED'" :size="18" weight="fill" aria-hidden="true" />
              <PhProhibit v-else :size="18" weight="bold" aria-hidden="true" />
              <span>
                {{ queued.includes(key(line)) ? $t('sync.queued') : outcome(line) }}
                <span v-if="line.event?.note" class="dose__note">“{{ line.event.note }}”</span>
              </span>
            </p>
          </div>

          <div v-if="canRecord && isOpen(line.state)" class="dose__actions">
            <UiButton
              class="dose__give"
              size="lg"
              :variant="line.state === 'UPCOMING' ? 'secondary' : 'primary'"
              :icon="PhCheck"
              :loading="busy === key(line)"
              :disabled="busy !== null && busy !== key(line)"
              @click="emit('give', line)"
            >
              {{ $t('doses.give') }}
            </UiButton>
            <UiButton
              variant="secondary"
              size="lg"
              :icon="PhDotsThreeOutline"
              class="dose__other"
              :aria-label="$t('doses.notGiven')"
              @click="emit('notGiven', line)"
            >
              <span class="dose__other-label">{{ $t('doses.notGiven') }}</span>
            </UiButton>
          </div>
          <div v-else-if="line.event?.canUndo && !queued.includes(key(line))" class="dose__actions dose__actions--undo">
            <UiButton variant="ghost" size="sm" :icon="PhArrowCounterClockwise" @click="emit('undo', line)">{{ $t('common.undo') }}</UiButton>
          </div>
        </article>
      </div>
    </li>
  </ol>
</template>

<style scoped>
.timeline {
  margin: 0;
  padding: 0;
  list-style: none;
}
.slot {
  display: grid;
  grid-template-columns: 22px 64px minmax(0, 1fr);
  column-gap: 10px;
  position: relative;
  padding-bottom: 18px;
}
.slot:last-child {
  padding-bottom: 0;
}
.slot__rail {
  position: relative;
  display: flex;
  justify-content: center;
}
.slot__rail::before {
  content: '';
  position: absolute;
  top: 22px;
  bottom: -18px;
  width: 2px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--gray-200), var(--gray-100));
}
.slot:last-child .slot__rail::before {
  display: none;
}
.slot__dot {
  position: relative;
  z-index: 1;
  width: 14px;
  height: 14px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--surface);
  border: 3px solid var(--gray-300);
  box-shadow: 0 0 0 4px var(--surface);
}
.slot--given .slot__dot {
  border-color: var(--success);
  background: var(--success);
}
.slot--due .slot__dot {
  border-color: var(--primary);
  box-shadow:
    0 0 0 4px var(--surface),
    0 0 0 7px var(--primary-soft-border);
}
.slot--late .slot__dot {
  border-color: var(--warning);
}
.slot--missed .slot__dot {
  border-color: var(--danger);
}
.slot--refused .slot__dot,
.slot--skipped .slot__dot {
  border-color: var(--accent-400);
}
.slot__time {
  padding-top: 1px;
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--text);
}
.slot--given .slot__time {
  color: var(--text-subtle);
}
.slot__doses {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.dose {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs), var(--highlight);
  transition:
    border-color var(--duration) var(--ease),
    background-color var(--duration) var(--ease);
}
.dose--due {
  border-color: var(--primary-soft-border);
  background: linear-gradient(180deg, var(--primary-soft), var(--surface) 80%);
  box-shadow: var(--shadow-sm), var(--highlight);
}
.dose--late {
  border-color: color-mix(in srgb, var(--warning) 30%, transparent);
  background: linear-gradient(180deg, var(--warning-soft), var(--surface) 80%);
}
.dose--missed {
  border-color: color-mix(in srgb, var(--danger) 28%, transparent);
  background: linear-gradient(180deg, var(--danger-soft), var(--surface) 80%);
}
.dose--given,
.dose--skipped,
.dose--refused {
  background: var(--surface-muted);
  box-shadow: none;
}
.dose--queued {
  border-style: dashed;
}
.dose__what {
  flex: 1;
  min-width: 0;
}
.dose__name {
  font-family: var(--font-display);
  font-size: var(--text-md);
  font-weight: 750;
  color: var(--text);
}
.dose__strength {
  font-weight: 600;
  color: var(--text-muted);
}
.dose--given .dose__name {
  color: var(--text-muted);
}
.dose__how {
  margin-top: 2px;
  font-size: var(--text-sm);
  color: var(--text-muted);
}
.dose__state {
  margin-top: 8px;
}
.dose__outcome {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 8px;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--success-text);
}
.dose__outcome :deep(svg) {
  flex: none;
  margin-top: 1px;
}
.dose__outcome--skipped {
  color: var(--text-muted);
}
.dose__outcome--refused {
  color: var(--accent-soft-text);
}
.dose--queued .dose__outcome {
  color: var(--info-text);
}
.dose__note {
  display: block;
  margin-top: 2px;
  font-weight: 500;
  color: var(--text-muted);
  font-style: italic;
}
.dose__actions {
  display: flex;
  gap: 8px;
  flex: none;
}
.dose__give {
  min-width: 132px;
}
@media (max-width: 640px) {
  .slot {
    grid-template-columns: 18px minmax(0, 1fr);
    column-gap: 10px;
  }
  .slot__time {
    grid-column: 2;
    margin-bottom: 8px;
  }
  .slot__doses {
    grid-column: 2;
  }
  .slot__rail {
    grid-row: 1 / span 2;
  }
  .dose {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    padding: 14px;
  }
  .dose__actions {
    width: 100%;
  }
  .dose__give {
    flex: 1;
    min-width: 0;
  }
  .dose__other-label {
    display: none;
  }
  .dose__actions--undo {
    justify-content: flex-end;
    margin-top: -8px;
  }
}
</style>
