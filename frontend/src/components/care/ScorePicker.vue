<script setup lang="ts">
import { formatTime } from '@/lib/format'
import type { ScoreMark } from '@/types/care'

/**
 * A 1-5 scale as five big buttons. Mood runs from coral (low) to sage (good); pain the other
 * way, so "good" always looks the same colour.
 */
const props = defineProps<{ kind: 'MOOD' | 'PAIN'; last: ScoreMark | null; disabled?: boolean }>()
const emit = defineEmits<{ pick: [score: number] }>()

const scores = [1, 2, 3, 4, 5]

function warmth(score: number) {
  return props.kind === 'MOOD' ? 5 - score : score - 1
}
</script>

<template>
  <div class="score">
    <div class="score__row" role="group" :aria-label="$t(`journal.kinds.${kind}`)">
      <button
        v-for="s in scores"
        :key="s"
        type="button"
        class="score__btn"
        :class="[`score__btn--w${warmth(s)}`, { 'is-active': last?.score === s }]"
        :aria-pressed="last?.score === s"
        :disabled="disabled"
        @click="emit('pick', s)"
      >
        <span class="score__num num">{{ s }}</span>
        <span class="score__label">{{ $t(`journal.${kind === 'MOOD' ? 'mood' : 'pain'}.${s}`) }}</span>
      </button>
    </div>
    <p v-if="last" class="score__last small subtle">
      {{ $t(`journal.${kind === 'MOOD' ? 'mood' : 'pain'}.${last.score}`) }} · {{ last.by }} · <span class="num">{{ formatTime(last.at) }}</span>
    </p>
  </div>
</template>

<style scoped>
.score__row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
}
.score__btn {
  --tint: var(--brand-500);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  min-height: 58px;
  padding: 6px 2px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  box-shadow: var(--shadow-xs), var(--highlight);
  transition:
    transform var(--duration) var(--ease),
    background-color var(--duration) var(--ease),
    border-color var(--duration) var(--ease);
}
.score__btn--w0 {
  --tint: var(--brand-500);
}
.score__btn--w1 {
  --tint: var(--brand-400);
}
.score__btn--w2 {
  --tint: var(--gray-400);
}
.score__btn--w3 {
  --tint: var(--accent-400);
}
.score__btn--w4 {
  --tint: var(--accent-600);
}
.score__btn:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--tint) 50%, transparent);
}
.score__btn.is-active {
  color: var(--text-inverse);
  border-color: transparent;
  background: linear-gradient(180deg, color-mix(in srgb, var(--tint) 80%, var(--surface)), var(--tint));
  box-shadow: 0 6px 14px -6px var(--tint);
}
.score__btn:disabled {
  cursor: default;
  opacity: 0.6;
}
.score__num {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 800;
  line-height: 1.1;
}
.score__label {
  font-size: 11px;
  font-weight: 600;
  line-height: 1.2;
  text-align: center;
  overflow-wrap: anywhere;
}
.score__btn:not(.is-active) .score__num {
  color: var(--tint);
}
.score__last {
  margin-top: 8px;
}
</style>
