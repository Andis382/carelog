<script setup lang="ts">
import { computed, ref } from 'vue'
import { PhArrowsClockwise, PhCloudSlash, PhWarningCircle } from '@phosphor-icons/vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiSpinner from '@/components/ui/UiSpinner.vue'
import { formatTime } from '@/lib/format'
import { useSync } from '@/stores/sync'

/** A small pill in the band: offline, waiting to sync, or entries the server refused. */
const sync = useSync()
const open = ref(false)

const state = computed(() => {
  if (sync.problems.length) return 'problem'
  if (sync.syncing) return 'syncing'
  if (!sync.online) return 'offline'
  if (sync.waiting) return 'waiting'
  return null
})
</script>

<template>
  <button v-if="state" type="button" class="sync" :class="`sync--${state}`" @click="open = true">
    <UiSpinner v-if="state === 'syncing'" size="15px" />
    <PhWarningCircle v-else-if="state === 'problem'" :size="17" weight="bold" aria-hidden="true" />
    <PhCloudSlash v-else-if="state === 'offline'" :size="17" weight="bold" aria-hidden="true" />
    <PhArrowsClockwise v-else :size="17" weight="bold" aria-hidden="true" />
    <span class="sync__label">
      <template v-if="state === 'problem'">{{ $t('sync.problems', { n: sync.problems.length }, sync.problems.length) }}</template>
      <template v-else-if="state === 'syncing'">{{ $t('sync.syncing') }}</template>
      <template v-else-if="state === 'offline'">{{ $t('sync.offlineShort', { n: sync.waiting }) }}</template>
      <template v-else>{{ $t('sync.waiting', { n: sync.waiting }, sync.waiting) }}</template>
    </span>
  </button>

  <UiDialog v-model:open="open" :title="$t('sync.title')" :description="sync.online ? $t('sync.onlineText') : $t('sync.offlineText')">
    <div class="stack">
      <div v-if="sync.problems.length" class="stack stack-sm">
        <p class="eyebrow">{{ $t('sync.refused') }}</p>
        <div v-for="p in sync.problems" :key="p.id" class="item item--problem">
          <div>
            <p class="strong">{{ p.label }}</p>
            <p class="small muted">{{ p.message }}</p>
          </div>
          <UiButton size="sm" variant="ghost" @click="sync.dismiss(p.id)">{{ $t('sync.dismiss') }}</UiButton>
        </div>
      </div>
      <div v-if="sync.pending.length" class="stack stack-sm">
        <p class="eyebrow">{{ $t('sync.pendingTitle') }}</p>
        <div v-for="w in sync.pending" :key="w.id" class="item">
          <p class="strong">{{ w.label }}</p>
          <p class="small muted num">{{ formatTime(w.queuedAt) }}</p>
        </div>
      </div>
    </div>
    <template v-if="sync.pending.length && sync.online" #footer>
      <UiButton :icon="PhArrowsClockwise" :loading="sync.syncing" @click="sync.flush()">{{ $t('sync.retry') }}</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.sync {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid rgb(255 255 255 / 0.2);
  border-radius: var(--radius-pill);
  background: rgb(255 255 255 / 0.1);
  color: var(--header-text);
  font-size: var(--text-sm);
  font-weight: 650;
  white-space: nowrap;
}
.sync:focus-visible {
  outline: 3px solid rgb(255 255 255 / 0.55);
  outline-offset: 2px;
}
.sync--offline,
.sync--problem {
  background: color-mix(in srgb, var(--accent-500) 30%, transparent);
  border-color: color-mix(in srgb, var(--accent-300) 60%, transparent);
}
.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
}
.item--problem {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--accent-500) 25%, transparent);
}
@media (max-width: 520px) {
  .sync__label {
    display: none;
  }
  .sync--problem .sync__label,
  .sync--offline .sync__label {
    display: inline;
  }
}
</style>
