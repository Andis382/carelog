<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhPlus } from '@phosphor-icons/vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import { formatTime } from '@/lib/format'
import type { MealAmount, MealSlot, TodayData } from '@/types/care'

/** One tap per meal (how much was eaten) and a glass counter for water. */
defineProps<{ meals: TodayData['meals']; glasses: number; canRecord: boolean }>()
const emit = defineEmits<{ meal: [slot: MealSlot, amount: MealAmount | null | undefined]; glass: [] }>()

const { t } = useI18n()
const slots: MealSlot[] = ['BREAKFAST', 'LUNCH', 'DINNER']
const amounts = computed(() => (['ALL', 'HALF', 'LITTLE', 'NONE'] as MealAmount[]).map((a) => ({ value: a, label: t(`meals.amounts.${a}`) })))
</script>

<template>
  <div class="meals">
    <div v-for="slot in slots" :key="slot" class="meal">
      <div class="meal__head">
        <span class="strong">{{ $t(`meals.slots.${slot}`) }}</span>
        <span v-if="meals[slot]" class="xsmall subtle">{{ meals[slot]!.by }} · <span class="num">{{ formatTime(meals[slot]!.at) }}</span></span>
      </div>
      <UiSegmented
        v-if="canRecord"
        :model-value="meals[slot]?.amount ?? null"
        :options="amounts"
        :label="$t(`meals.slots.${slot}`)"
        block
        @update:model-value="(a) => emit('meal', slot, a)"
      />
      <p v-else class="small muted">{{ meals[slot] ? $t(`meals.amounts.${meals[slot]!.amount}`) : '—' }}</p>
    </div>
    <div class="water">
      <div class="water__glasses" aria-hidden="true">
        <span v-for="n in Math.max(8, glasses)" :key="n" class="water__glass" :class="{ 'is-full': n <= glasses }" />
      </div>
      <div class="water__row">
        <p class="water__count">{{ $t('today.glasses', { n: glasses }, glasses) }}</p>
        <UiButton v-if="canRecord" variant="soft" :icon="PhPlus" @click="emit('glass')">{{ $t('today.addGlass') }}</UiButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.meals {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.meal__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.water {
  padding-top: 14px;
  border-top: 1px solid var(--border);
}
.water__glasses {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}
/* Tumblers: a tapered outline, filled with water once drunk */
.water__glass {
  flex: 1;
  max-width: 24px;
  height: 32px;
  clip-path: polygon(0 0, 100% 0, 86% 100%, 14% 100%);
  background: linear-gradient(180deg, var(--brand-100), var(--brand-50));
  box-shadow: inset 0 -3px 0 var(--brand-100);
}
.water__glass.is-full {
  background: linear-gradient(180deg, var(--brand-50) 0 18%, var(--brand-300) 18% 24%, var(--brand-400) 24% 100%);
}
.water__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.water__count {
  font-family: var(--font-display);
  font-weight: 750;
}
</style>
