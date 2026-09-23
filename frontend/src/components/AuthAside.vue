<script setup lang="ts">
import { PhCheckCircle, PhHandHeart, PhHeartbeat, PhShieldCheck } from '@phosphor-icons/vue'

// The sign-in panel: the promise in three lines, and a small daily card showing it at work.
const points = [
  { key: 'doses', icon: PhShieldCheck },
  { key: 'log', icon: PhHeartbeat },
  { key: 'summary', icon: PhHandHeart },
]
</script>

<template>
  <div class="aside">
    <h2 class="aside__title">{{ $t('aside.title') }}</h2>
    <ul class="aside__points">
      <li v-for="p in points" :key="p.key">
        <span class="aside__icon"><component :is="p.icon" :size="20" weight="duotone" aria-hidden="true" /></span>
        {{ $t(`aside.points.${p.key}`) }}
      </li>
    </ul>

    <div class="artifact" aria-hidden="true">
      <div class="artifact__head">
        <span class="artifact__avatar">DK</span>
        <div>
          <p class="artifact__title">{{ $t('aside.cardTitle') }}</p>
          <p class="artifact__sub">81 · Shkodër</p>
        </div>
      </div>
      <div class="artifact__row">
        <span class="artifact__time">08:00</span>
        <div class="artifact__med">
          <p>Metformin 500 mg</p>
          <span class="artifact__pill artifact__pill--given"><PhCheckCircle :size="14" weight="fill" /> {{ $t('aside.given') }}</span>
        </div>
      </div>
      <div class="artifact__row">
        <span class="artifact__time">08:00</span>
        <div class="artifact__med">
          <p>Amlodipine 5 mg</p>
          <span class="artifact__pill artifact__pill--due">{{ $t('aside.due') }}</span>
        </div>
      </div>
      <div class="artifact__vital">
        <span>{{ $t('aside.bp') }}</span>
        <strong class="num">128/78</strong>
      </div>
      <div class="artifact__guard">{{ $t('aside.guard') }}</div>
    </div>
  </div>
</template>

<style scoped>
.aside__title {
  color: var(--header-text);
  font-size: clamp(1.8rem, 1.2rem + 1.8vw, 2.5rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  max-width: 17ch;
}
.aside__points {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 26px 0 0;
  padding: 0;
  list-style: none;
  color: var(--text-inverse-muted);
  max-width: 44ch;
}
.aside__points li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.aside__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  color: var(--brand-200);
  background: rgb(255 255 255 / 0.08);
  border: 1px solid rgb(255 255 255 / 0.12);
}

/* A slice of the Today card, tilted a little, like a phone on the table */
.artifact {
  position: relative;
  width: min(100%, 340px);
  margin-top: 36px;
  padding: 16px;
  color: var(--text);
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow:
    0 30px 60px -20px rgb(0 0 0 / 0.5),
    0 0 0 1px rgb(255 255 255 / 0.08),
    var(--highlight);
  transform: rotate(-2deg);
}
.artifact__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.artifact__avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--primary-strong);
  background: var(--brand-100);
  font-weight: 750;
  font-size: var(--text-sm);
}
.artifact__title {
  font-family: var(--font-display);
  font-weight: 750;
}
.artifact__sub {
  font-size: var(--text-xs);
  color: var(--text-subtle);
}
.artifact__row {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
}
.artifact__time {
  font-family: var(--font-display);
  font-weight: 750;
  font-size: var(--text-sm);
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.artifact__med p {
  font-weight: 650;
  font-size: var(--text-sm);
}
.artifact__pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  padding: 2px 9px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 700;
}
.artifact__pill--given {
  color: var(--success-text);
  background: var(--success-soft);
}
.artifact__pill--due {
  color: var(--primary-soft-text);
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
}
.artifact__vital {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 10px 0 2px;
  font-size: var(--text-sm);
  color: var(--text-muted);
}
.artifact__vital strong {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  color: var(--text);
}
.artifact__guard {
  position: absolute;
  right: -18px;
  bottom: -18px;
  padding: 9px 13px;
  border-radius: var(--radius);
  color: var(--text-inverse);
  background: linear-gradient(180deg, var(--accent-400), var(--accent-600));
  box-shadow: var(--accent-shadow);
  font-size: 12px;
  font-weight: 700;
  transform: rotate(3deg);
}
</style>
