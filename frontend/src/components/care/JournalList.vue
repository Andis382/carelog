<script setup lang="ts">
import UiBadge from '@/components/ui/UiBadge.vue'
import { formatTime } from '@/lib/format'
import type { JournalEntry } from '@/types/care'

defineProps<{ entries: JournalEntry[] }>()

function tone(entry: JournalEntry) {
  if (entry.kind === 'INCIDENT') return 'accent'
  if (entry.kind === 'MOOD' || entry.kind === 'PAIN') return 'primary'
  return 'neutral'
}

function scoreLabel(entry: JournalEntry, t: (key: string) => string) {
  if (entry.score === null) return null
  if (entry.kind === 'MOOD') return t(`journal.mood.${entry.score}`)
  if (entry.kind === 'PAIN') return t(`journal.pain.${entry.score}`)
  return null
}
</script>

<template>
  <ul class="journal">
    <li v-for="e in entries" :key="e.id" class="entry" :class="{ 'entry--incident': e.kind === 'INCIDENT' }">
      <div class="entry__head">
        <UiBadge :tone="tone(e)" size="sm">{{ $t(`journal.kinds.${e.kind}`) }}</UiBadge>
        <span v-if="scoreLabel(e, $t)" class="small strong">{{ scoreLabel(e, $t) }}</span>
        <span class="entry__who xsmall subtle">{{ e.by }} · <span class="num">{{ formatTime(e.at) }}</span></span>
      </div>
      <p v-if="e.text" class="entry__text">{{ e.text }}</p>
      <a v-if="e.photoUrl" :href="e.photoUrl" target="_blank" rel="noopener" class="entry__photo">
        <img :src="e.photoUrl" :alt="$t('common.photo')" loading="lazy" />
      </a>
    </li>
  </ul>
</template>

<style scoped>
.journal {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}
.entry {
  padding: 12px 4px;
  border-bottom: 1px solid var(--border);
}
.entry:last-child {
  border-bottom: 0;
}
.entry--incident {
  margin: 4px 0;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--accent-500) 25%, transparent);
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
}
.entry__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.entry__who {
  margin-left: auto;
}
.entry__text {
  margin-top: 6px;
  font-size: var(--text-sm);
  color: var(--text);
  white-space: pre-line;
}
.entry__photo img {
  width: 120px;
  height: 90px;
  margin-top: 8px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
}
</style>
