<script setup lang="ts">
import type { Component } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/** A translucent pill for facts that sit in the dark band under a page title. */
withDefaults(defineProps<{ icon?: Component; to?: RouteLocationRaw; tone?: 'plain' | 'warm' | 'live' }>(), {
  icon: undefined,
  to: undefined,
  tone: 'plain',
})
</script>

<template>
  <component :is="to ? RouterLink : 'span'" :to="to" class="chip" :class="[`chip--${tone}`, { 'chip--link': !!to }]">
    <span v-if="tone === 'live'" class="chip__pulse" aria-hidden="true" />
    <component :is="icon" v-else-if="icon" :size="16" weight="bold" aria-hidden="true" />
    <span class="chip__text"><slot /></span>
  </component>
</template>

<style scoped>
.chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  max-width: 100%;
  padding: 6px 13px;
  border: 1px solid rgb(255 255 255 / 0.16);
  border-radius: var(--radius-pill);
  background: rgb(255 255 255 / 0.08);
  color: var(--header-text);
  font-size: var(--text-sm);
  font-weight: 600;
  line-height: 1.3;
  text-decoration: none;
  backdrop-filter: blur(6px);
}
.chip__text {
  min-width: 0;
}
.chip--warm {
  border-color: color-mix(in srgb, var(--accent-300) 55%, transparent);
  background: color-mix(in srgb, var(--accent-500) 26%, transparent);
}
.chip--link {
  transition:
    background-color var(--duration) var(--ease),
    transform var(--duration) var(--ease);
}
.chip--link:hover {
  color: var(--header-text);
  background: rgb(255 255 255 / 0.16);
  transform: translateY(-1px);
}
.chip--link:focus-visible {
  outline: 3px solid rgb(255 255 255 / 0.55);
  outline-offset: 2px;
}
.chip__pulse {
  position: relative;
  flex: none;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--brand-300);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand-300) 30%, transparent);
}
.chip__pulse::after {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid var(--brand-300);
  animation: pulse 2s var(--ease) infinite;
}
@keyframes pulse {
  from {
    opacity: 0.8;
    transform: scale(0.8);
  }
  to {
    opacity: 0;
    transform: scale(2.2);
  }
}
</style>
