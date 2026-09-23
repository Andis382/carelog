<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { formatVital, VITAL_DECIMALS } from '@/lib/care'
import { formatDate, formatDateTime, formatNumber } from '@/lib/format'
import type { Reading, VitalKind, VitalRange } from '@/types/care'

/**
 * Readings over time with the circle's usual range drawn as a soft band. Blood pressure has two
 * lines (upper and lower) and two bands. Dependency-free SVG, sized to its container.
 */
const props = defineProps<{ kind: VitalKind; readings: Reading[]; range: VitalRange | null; from: string; label: string }>()

const root = ref<HTMLElement | null>(null)
const width = ref(640)
const hover = ref<number | null>(null)
const HEIGHT = 260
const PAD = { top: 16, right: 16, bottom: 30, left: 44 }
let observer: ResizeObserver | null = null

onMounted(() => {
  if (!root.value) return
  width.value = root.value.clientWidth || 640
  observer = new ResizeObserver(([entry]) => {
    if (entry) width.value = Math.max(280, entry.contentRect.width)
  })
  observer.observe(root.value)
})
onBeforeUnmount(() => observer?.disconnect())

const paired = computed(() => props.kind === 'BP')
const points = computed(() => props.readings.map((r) => ({ t: new Date(r.measuredAt).getTime(), a: r.value1, b: r.value2, r })))

const xDomain = computed(() => {
  const start = new Date(props.from + 'T00:00:00').getTime()
  const end = Math.max(Date.now(), ...points.value.map((p) => p.t))
  return [start, end] as const
})

const yDomain = computed(() => {
  const values: number[] = []
  for (const p of points.value) {
    values.push(p.a)
    if (p.b !== null) values.push(p.b)
  }
  const r = props.range
  if (r) [r.low, r.high, r.low2, r.high2].forEach((v) => v !== null && values.push(v))
  if (!values.length) return [0, 1] as const
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pad = Math.max((max - min) * 0.12, VITAL_DECIMALS[props.kind] ? 0.3 : 3)
  return [min - pad, max + pad] as const
})

const plotW = computed(() => width.value - PAD.left - PAD.right)
const plotH = HEIGHT - PAD.top - PAD.bottom

function x(t: number) {
  const [a, b] = xDomain.value
  return PAD.left + (b === a ? plotW.value / 2 : ((t - a) / (b - a)) * plotW.value)
}
function y(v: number) {
  const [a, b] = yDomain.value
  return PAD.top + plotH - ((v - a) / (b - a)) * plotH
}

/** Round, readable ticks: four or five lines across the value range. */
const ticks = computed(() => {
  const [a, b] = yDomain.value
  const raw = (b - a) / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? raw
  const out: number[] = []
  for (let v = Math.ceil(a / step) * step; v <= b; v += step) out.push(Number(v.toFixed(4)))
  return out
})

const dayTicks = computed(() => {
  const [a, b] = xDomain.value
  const count = width.value < 480 ? 3 : 5
  return Array.from({ length: count }, (_, i) => a + ((b - a) * i) / (count - 1))
})

function line(key: 'a' | 'b') {
  return points.value
    .filter((p) => p[key] !== null)
    .map((p, i) => `${i ? 'L' : 'M'}${x(p.t).toFixed(1)},${y(p[key] as number).toFixed(1)}`)
    .join(' ')
}

function area(key: 'a') {
  const pts = points.value
  if (pts.length < 2) return ''
  const first = pts[0]!
  const last = pts[pts.length - 1]!
  return `${line(key)} L${x(last.t).toFixed(1)},${PAD.top + plotH} L${x(first.t).toFixed(1)},${PAD.top + plotH} Z`
}

function band(low: number | null, high: number | null) {
  if (low === null || high === null) return null
  const top = y(Math.min(high, yDomain.value[1]))
  const bottom = y(Math.max(low, yDomain.value[0]))
  return { y: top, h: Math.max(0, bottom - top) }
}

const bands = computed(() => {
  const r = props.range
  if (!r) return []
  return [band(r.low, r.high), paired.value ? band(r.low2, r.high2) : null].filter((b): b is { y: number; h: number } => !!b)
})

function fmt(n: number, digits: number) {
  return formatNumber(n, digits)
}

function onMove(e: PointerEvent) {
  if (!points.value.length || !root.value) return
  const rect = root.value.getBoundingClientRect()
  const px = e.clientX - rect.left
  let best = 0
  points.value.forEach((p, i) => {
    if (Math.abs(x(p.t) - px) < Math.abs(x(points.value[best]!.t) - px)) best = i
  })
  hover.value = best
}

const hovered = computed(() => (hover.value === null ? null : points.value[hover.value] ?? null))
</script>

<template>
  <div ref="root" class="chart" @pointermove="onMove" @pointerleave="hover = null">
    <svg :width="width" :height="HEIGHT" :viewBox="`0 0 ${width} ${HEIGHT}`" role="img" :aria-label="label">
      <rect v-for="(b, i) in bands" :key="i" :x="PAD.left" :y="b.y" :width="plotW" :height="b.h" class="band" :class="{ 'band--second': i === 1 }" />
      <g class="grid">
        <g v-for="v in ticks" :key="v">
          <line :x1="PAD.left" :x2="width - PAD.right" :y1="y(v)" :y2="y(v)" />
          <text :x="PAD.left - 8" :y="y(v) + 4" text-anchor="end">{{ formatNumber(v, VITAL_DECIMALS[kind]) }}</text>
        </g>
        <text v-for="(tick, i) in dayTicks" :key="i" :x="x(tick)" :y="HEIGHT - 8" :text-anchor="i === 0 ? 'start' : i === dayTicks.length - 1 ? 'end' : 'middle'">
          {{ formatDate(new Date(tick), 'short') }}
        </text>
      </g>
      <path v-if="!paired" :d="area('a')" class="area" />
      <path :d="line('a')" class="line" />
      <path v-if="paired" :d="line('b')" class="line line--second" />
      <g>
        <circle
          v-for="(p, i) in points"
          :key="`a${i}`"
          :cx="x(p.t)"
          :cy="y(p.a)"
          :r="hover === i ? 6 : 3.5"
          class="dot"
          :class="{ 'dot--out': p.r.position }"
        />
        <template v-if="paired">
          <circle
            v-for="(p, i) in points"
            :key="`b${i}`"
            :cx="x(p.t)"
            :cy="y(p.b ?? p.a)"
            :r="hover === i ? 6 : 3.5"
            class="dot dot--second"
          />
        </template>
      </g>
      <line v-if="hovered" :x1="x(hovered.t)" :x2="x(hovered.t)" :y1="PAD.top" :y2="PAD.top + plotH" class="cursor" />
    </svg>
    <div
      v-if="hovered"
      class="tip"
      :style="{ left: `${Math.min(Math.max(x(hovered.t), 90), width - 90)}px`, top: `${Math.max(y(hovered.a) - 58, 0)}px` }"
    >
      <strong class="num">{{ formatVital(kind, hovered.a, hovered.b, fmt) }}</strong>
      <span>{{ formatDateTime(hovered.r.measuredAt) }} · {{ hovered.r.by }}</span>
    </div>
  </div>
</template>

<style scoped>
.chart {
  position: relative;
  width: 100%;
  touch-action: pan-y;
}
svg {
  display: block;
  overflow: visible;
}
.band {
  fill: var(--success-soft);
}
.band--second {
  fill: color-mix(in srgb, var(--accent-soft) 70%, transparent);
}
.grid line {
  stroke: var(--gray-100);
  stroke-width: 1;
}
.grid text {
  fill: var(--text-subtle);
  font-size: 11px;
  font-family: var(--font-body);
  font-variant-numeric: tabular-nums;
}
.area {
  fill: color-mix(in srgb, var(--brand-500) 10%, transparent);
}
.line {
  fill: none;
  stroke: var(--brand-600);
  stroke-width: 2.5;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.line--second {
  stroke: var(--accent-500);
}
.dot {
  fill: var(--brand-600);
  stroke: var(--surface);
  stroke-width: 1.5;
  transition: r var(--duration) var(--ease);
}
.dot--second {
  fill: var(--accent-500);
}
.dot--out {
  fill: var(--surface);
  stroke: var(--info);
  stroke-width: 2.5;
}
.cursor {
  stroke: var(--border-strong);
  stroke-dasharray: 3 3;
}
.tip {
  position: absolute;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 7px 11px;
  transform: translateX(-50%);
  pointer-events: none;
  white-space: nowrap;
  background: var(--gray-900);
  color: var(--text-inverse);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-lg);
  font-size: var(--text-xs);
}
.tip strong {
  font-family: var(--font-display);
  font-size: var(--text-md);
}
</style>
