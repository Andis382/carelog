<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhShieldCheck } from '@phosphor-icons/vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'

/**
 * The double-dose guard, as the person at the bedside sees it: somebody already recorded this
 * dose, here is who and when, and nothing was saved.
 */
export type GuardInfo = { medicine: string; time: string; status: string; by: string; at: string; note: string | null }

const open = defineModel<boolean>('open', { default: false })
const props = defineProps<{ info: GuardInfo | null }>()

const { t } = useI18n()
const line = computed(() => {
  const i = props.info
  if (!i) return ''
  if (i.status === 'GIVEN') return t('doses.guardGiven', { name: i.by, time: i.at })
  return t('doses.guardOther', { state: t(`doses.state.${i.status}`), name: i.by, time: i.at })
})
</script>

<template>
  <UiDialog v-model:open="open" :title="$t('doses.guardTitle')" size="sm">
    <div v-if="info" class="guard">
      <span class="guard__icon"><PhShieldCheck :size="34" weight="duotone" aria-hidden="true" /></span>
      <p class="guard__medicine">{{ info.medicine }} · <span class="num">{{ info.time }}</span></p>
      <p class="guard__who">{{ line }}</p>
      <p v-if="info.note" class="guard__note">“{{ info.note }}”</p>
      <p class="guard__text">{{ $t('doses.guardText') }}</p>
    </div>
    <template #footer>
      <UiButton block size="lg" @click="open = false">{{ $t('doses.guardOk') }}</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.guard {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 6px;
  padding: 4px 0 6px;
}
.guard__icon {
  display: grid;
  place-items: center;
  width: 68px;
  height: 68px;
  margin-bottom: 8px;
  border-radius: 50%;
  color: var(--accent-600);
  background: var(--accent-soft);
  box-shadow: 0 0 0 8px color-mix(in srgb, var(--accent-soft) 55%, transparent);
}
.guard__medicine {
  font-size: var(--text-sm);
  font-weight: 650;
  color: var(--text-muted);
}
.guard__who {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text);
  text-wrap: balance;
}
.guard__note {
  font-style: italic;
  color: var(--text-muted);
}
.guard__text {
  margin-top: 6px;
  font-size: var(--text-sm);
  color: var(--text-muted);
}
</style>
