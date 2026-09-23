<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { addDays } from '@/lib/care'
import { formatWeekday } from '@/lib/format'
import type { Medication } from '@/types/care'

/** "Every day" or "Sun" plus the time chips; "As needed" when there is no schedule. */
const props = defineProps<{ medication: Pick<Medication, 'frequency' | 'times' | 'weekdays'> }>()
const { t } = useI18n()

// 2024-01-01 was a Monday, so ISO day n is that date plus n - 1.
const days = computed(() => props.medication.weekdays.map((n) => formatWeekday(addDays('2024-01-01', n - 1), 'short')).join(', '))
const when = computed(() => {
  if (props.medication.frequency === 'AS_NEEDED') return t('meds.asNeeded')
  if (props.medication.frequency === 'WEEKLY') return days.value
  return t('meds.everyDay')
})
</script>

<template>
  <div class="schedule">
    <span class="schedule__when">{{ when }}</span>
    <span v-for="time in medication.times" :key="time" class="schedule__time num">{{ time }}</span>
  </div>
</template>

<style scoped>
.schedule {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.schedule__when {
  margin-right: 2px;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-muted);
}
.schedule__time {
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
  color: var(--primary-soft-text);
  font-family: var(--font-display);
  font-size: var(--text-sm);
  font-weight: 750;
}
</style>
