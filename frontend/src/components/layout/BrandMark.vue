<script setup lang="ts">
import { useId } from 'vue'

withDefaults(defineProps<{ size?: number; withName?: boolean; inverse?: boolean }>(), { size: 34, withName: true, inverse: true })

// Two marks can be on one page (band + sheet); gradient ids must not collide.
const uid = useId()
</script>

<template>
  <span class="brand" :class="{ 'brand--inverse': inverse }">
    <svg :width="size" :height="size" viewBox="0 0 40 40" aria-hidden="true" class="brand__mark">
      <defs>
        <linearGradient :id="`${uid}-bg`" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#8ec4ad" />
          <stop offset="0.55" stop-color="#468f75" />
          <stop offset="1" stop-color="#2a5245" />
        </linearGradient>
        <linearGradient :id="`${uid}-heart`" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#f6b9a7" />
          <stop offset="1" stop-color="#e07a5f" />
        </linearGradient>
      </defs>
      <rect x="1" y="1" width="38" height="38" rx="11" :fill="`url(#${uid}-bg)`" />
      <rect x="1.5" y="1.5" width="37" height="37" rx="10.5" fill="none" stroke="#fff" stroke-opacity=".3" />
      <path d="M8.5 19.2 20 10l11.5 9.2" fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M12 17.2V29.5h16V17.2" fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" />
      <path
        d="M20 27.3c-4.3-2.7-5.6-5-4.8-6.8.8-1.7 3.2-2 4.8-.2 1.6-1.8 4-1.5 4.8.2.8 1.8-.5 4.1-4.8 6.8Z"
        :fill="`url(#${uid}-heart)`"
      />
    </svg>
    <span v-if="withName" class="brand__name">{{ $t('app.name') }}</span>
  </span>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text);
  text-decoration: none;
}
.brand--inverse {
  color: var(--header-text);
}
.brand__mark {
  flex: none;
  filter: drop-shadow(0 4px 10px rgb(0 0 0 / 0.22));
}
.brand__name {
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}
</style>
