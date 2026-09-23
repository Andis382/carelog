<script setup lang="ts">
import { PhPlus } from '@phosphor-icons/vue'
import { formatVital, rangeLabel } from '@/lib/care'
import { formatNumber, formatRelative } from '@/lib/format'
import type { TodayData, VitalKind } from '@/types/care'

/** The last reading of each kind with the circle's usual range; a tap adds a new reading. */
defineProps<{ vitals: TodayData['vitals']; canRecord: boolean }>()
const emit = defineEmits<{ add: [kind: VitalKind] }>()

function fmt(n: number, digits: number) {
  return formatNumber(n, digits)
}
</script>

<template>
  <div class="vitals">
    <button
      v-for="v in vitals"
      :key="v.kind"
      type="button"
      class="vital"
      :class="{ 'vital--out': v.last?.position }"
      :disabled="!canRecord"
      @click="emit('add', v.kind)"
    >
      <span class="vital__kind">{{ $t(`vitals.short.${v.kind}`) }}</span>
      <span v-if="v.last" class="vital__value num">
        {{ formatVital(v.kind, v.last.value1, v.last.value2, fmt) }}<small>{{ $t(`vitals.units.${v.kind}`) }}</small>
      </span>
      <span v-else class="vital__value vital__value--none">—</span>
      <span class="vital__meta">
        <template v-if="v.last">{{ formatRelative(v.last.measuredAt) }}</template>
        <template v-else>{{ $t('vitals.none') }}</template>
      </span>
      <span v-if="rangeLabel(v.range, fmt)" class="vital__range num">{{ $t('vitals.usual', { range: rangeLabel(v.range, fmt) }) }}</span>
      <span v-if="canRecord" class="vital__add" aria-hidden="true"><PhPlus :size="14" weight="bold" /></span>
    </button>
  </div>
</template>

<style scoped>
.vitals {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
@media (min-width: 1001px) and (max-width: 1180px) {
  .vitals {
    grid-template-columns: minmax(0, 1fr);
  }
}
.vital {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-height: 104px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: linear-gradient(180deg, var(--surface), var(--surface-muted));
  color: var(--text);
  text-align: left;
  box-shadow: var(--shadow-xs), var(--highlight);
  transition:
    transform var(--duration) var(--ease),
    box-shadow var(--duration) var(--ease),
    border-color var(--duration) var(--ease);
}
.vital:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: var(--primary-soft-border);
  box-shadow: var(--shadow-md), var(--highlight);
}
.vital:disabled {
  cursor: default;
}
.vital--out {
  border-color: color-mix(in srgb, var(--info) 28%, transparent);
}
.vital__kind {
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-subtle);
}
.vital__value {
  font-family: var(--font-display);
  font-size: 1.55rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.15;
}
.vital__value small {
  margin-left: 4px;
  font-family: var(--font-body);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0;
  color: var(--text-subtle);
}
.vital__value--none {
  color: var(--gray-300);
}
.vital__meta {
  font-size: var(--text-xs);
  color: var(--text-muted);
}
.vital__range {
  margin-top: auto;
  padding-top: 4px;
  font-size: 11px;
  color: var(--text-subtle);
}
.vital--out .vital__range {
  color: var(--info-text);
  font-weight: 600;
}
.vital__add {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: var(--primary);
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
}
</style>
