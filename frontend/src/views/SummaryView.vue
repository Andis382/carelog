<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  PhCaretLeft,
  PhCaretRight,
  PhChartLine,
  PhChecks,
  PhClockCounterClockwise,
  PhForkKnife,
  PhHeartbeat,
  PhLockSimple,
  PhPackage,
  PhPaperPlaneTilt,
  PhPill,
  PhUserFocus,
  PhWarningOctagon,
  PhWhatsappLogo,
} from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import HeroChip from '@/components/care/HeroChip.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiIconButton from '@/components/ui/UiIconButton.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiNotice from '@/components/ui/UiNotice.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api, ApiError, query } from '@/lib/api'
import { addDays, formatVital, splitMinutes } from '@/lib/care'
import { formatDate, formatDateTime, formatNumber, formatTime, formatWeekday } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useConfirm } from '@/stores/confirm'
import { useToasts } from '@/stores/toasts'
import type { SummaryPage, VitalKind } from '@/types/care'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const toasts = useToasts()
const confirm = useConfirm()

const page = ref<SummaryPage | null>(null)
const failed = ref<string | null>(null)
const sending = ref(false)
const week = ref<string | undefined>(typeof route.query.week === 'string' ? route.query.week : undefined)

const report = computed(() => page.value?.report ?? null)
const adherence = computed(() => report.value?.doses.adherencePct ?? null)
const isThisWeek = computed(() => !!page.value && report.value?.weekStart === page.value.thisWeek)
const payerName = computed(() => page.value?.preview.payerName ?? null)
const dutyMax = computed(() => Math.max(1, ...(report.value?.duty ?? []).map((d) => Math.max(d.scheduledMinutes, d.checkedInMinutes))))
const languageName = computed(() => (page.value?.preview.locale === 'sq' ? 'Shqip' : 'English'))
const ring = computed(() => {
  const pct = adherence.value ?? 0
  const r = 52
  const c = 2 * Math.PI * r
  return { r, c, offset: c - (pct / 100) * c }
})

function fmt(n: number, digits: number) {
  return formatNumber(n, digits)
}

function vital(kind: VitalKind, a: number | null, b: number | null) {
  return a === null ? '—' : formatVital(kind, a, kind === 'BP' ? b : null, fmt)
}

function hours(minutes: number) {
  const { h, m } = splitMinutes(minutes)
  return m ? t('common.hoursMinutes', { h, m }) : t('common.hours', { h })
}

function day(iso: string) {
  const label = formatWeekday(iso, 'short')
  return label.charAt(0).toUpperCase() + label.slice(1)
}

async function load() {
  failed.value = null
  try {
    page.value = await api.get<SummaryPage>('/summary' + query({ week: week.value }))
  } catch (e) {
    failed.value = e instanceof ApiError && e.status === 409 ? e.message : t('errors.generic')
  }
}

function move(weeks: number) {
  const start = report.value?.weekStart
  if (!start) return
  week.value = addDays(start, weeks * 7)
}

watch(week, () => {
  router.replace({ query: week.value ? { week: week.value } : {} })
  load()
})
onMounted(load)

async function send() {
  if (!report.value || !payerName.value) return
  const ok = await confirm.ask({ title: t('summary.sendConfirm', { name: payerName.value }), text: t('summary.sendConfirmText'), confirmLabel: t('summary.send') })
  if (!ok) return
  sending.value = true
  try {
    await api.post('/summary/send', { week: report.value.weekStart })
    toasts.success(t('summary.sent', { name: payerName.value }))
    load()
  } catch (e) {
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <AppPage
    :title="$t('summary.title')"
    :eyebrow="report ? `${formatDate(report.weekStart, 'short')} – ${formatDate(report.weekEnd, 'medium')}` : undefined"
    :subtitle="payerName ? $t('summary.subtitle', { name: payerName }) : $t('summary.subtitleNoPayer')"
  >
    <template v-if="report" #meta>
      <HeroChip v-if="isThisWeek" tone="live">{{ $t('summary.inProgress') }}</HeroChip>
      <HeroChip :icon="PhUserFocus">{{ $t('summary.disclaimer') }}</HeroChip>
    </template>
    <template #actions>
      <div class="weeknav">
        <UiIconButton variant="inverse" :icon="PhCaretLeft" :label="$t('common.earlier')" @click="move(-1)" />
        <UiIconButton variant="inverse" :icon="PhCaretRight" :label="$t('common.later')" :disabled="isThisWeek || !report" @click="move(1)" />
      </div>
      <UiButton
        v-if="auth.canPlan && report"
        variant="inverse"
        size="lg"
        :icon="PhPaperPlaneTilt"
        :loading="sending"
        :disabled="!page?.preview.payerHasPhone"
        @click="send"
      >
        {{ $t('summary.send') }}
      </UiButton>
    </template>

    <UiCard v-if="failed" tone="primary">
      <UiEmpty :icon="PhLockSimple" :title="failed">
        <UiButton variant="secondary" @click="week = undefined">{{ $t('common.lastWeek') }}</UiButton>
      </UiEmpty>
    </UiCard>

    <div v-else-if="!report" class="grid-3">
      <UiSkeleton card :lines="4" />
      <UiSkeleton card :lines="4" />
      <UiSkeleton card :lines="4" />
    </div>

    <template v-else-if="page">
      <UiNotice v-if="payerName && !page.preview.payerHasPhone" tone="warning">{{ $t('summary.noPayerPhone', { name: payerName }) }}</UiNotice>
      <UiNotice v-else-if="!payerName" tone="warning">{{ $t('summary.noPayer') }}</UiNotice>

      <div class="top">
        <UiCard class="adherence">
          <div class="adherence__row">
            <svg class="ring" viewBox="0 0 128 128" role="img" :aria-label="`${$t('summary.adherence')}: ${adherence ?? '—'}%`">
              <circle cx="64" cy="64" :r="ring.r" class="ring__track" />
              <circle
                v-if="adherence !== null"
                cx="64"
                cy="64"
                :r="ring.r"
                class="ring__value"
                :stroke-dasharray="ring.c"
                :stroke-dashoffset="ring.offset"
              />
              <text x="64" y="70" text-anchor="middle" class="ring__text">{{ adherence === null ? '—' : `${adherence}%` }}</text>
            </svg>
            <div class="adherence__text">
              <p class="eyebrow">{{ $t('summary.adherence') }}</p>
              <p class="adherence__big num">{{ report.doses.due ? $t('summary.dosesOf', { given: report.doses.given, due: report.doses.due }) : $t('summary.noDoses') }}</p>
              <p v-if="report.doses.givenLate" class="small muted">{{ $t('summary.givenLate', { n: report.doses.givenLate }, report.doses.givenLate) }}</p>
            </div>
          </div>
          <div class="adherence__counts">
            <span class="count"><span class="count__n num">{{ report.doses.missed }}</span> {{ $t('summary.missed') }}</span>
            <span class="count"><span class="count__n num">{{ report.doses.refused }}</span> {{ $t('summary.refused') }}</span>
            <span class="count"><span class="count__n num">{{ report.doses.skipped }}</span> {{ $t('summary.skipped') }}</span>
          </div>
        </UiCard>

        <UiCard :title="$t('summary.problems')" :icon="PhPill" class="problems">
          <ul v-if="report.problems.length" class="problems__list">
            <li v-for="(p, i) in report.problems" :key="i" class="problem">
              <span class="problem__when num">{{ day(p.date) }} {{ p.time }}</span>
              <div class="problem__body">
                <p class="strong">{{ p.medicine }}</p>
                <p v-if="p.note" class="small muted">“{{ p.note }}” <template v-if="p.by">· {{ p.by }}</template></p>
              </div>
              <UiBadge :tone="p.state === 'MISSED' ? 'danger' : p.state === 'REFUSED' ? 'accent' : 'neutral'" size="sm">
                {{ $t(`doses.state.${p.state}`) }}
              </UiBadge>
            </li>
          </ul>
          <p v-else class="ok"><PhChecks :size="20" weight="bold" aria-hidden="true" /> {{ $t('summary.noProblems') }}</p>
        </UiCard>
      </div>

      <div class="grid-2">
        <UiCard :title="$t('summary.vitals')" :icon="PhHeartbeat" padding="none">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>{{ $t('summary.kind') }}</th>
                  <th class="num">{{ $t('summary.avg') }}</th>
                  <th class="num">{{ $t('summary.range') }}</th>
                  <th class="num">{{ $t('summary.outside') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="v in report.vitals" :key="v.kind">
                  <td>
                    <RouterLink :to="{ name: 'vitals', query: { kind: v.kind } }" class="strong">{{ $t(`vitals.kinds.${v.kind}`) }}</RouterLink>
                    <span class="xsmall subtle"> · {{ $t('vitals.readingCount', { n: v.count }, v.count) }}</span>
                  </td>
                  <td class="num strong">{{ vital(v.kind, v.avg, v.avg2) }} <span class="xsmall subtle">{{ $t(`vitals.units.${v.kind}`) }}</span></td>
                  <td class="num nowrap">{{ vital(v.kind, v.min, v.min2) }} – {{ vital(v.kind, v.max, v.max2) }}</td>
                  <td class="num">
                    <UiBadge v-if="v.outsideRange" tone="info" size="sm">{{ v.outsideRange }}</UiBadge>
                    <span v-else class="subtle">0</span>
                  </td>
                </tr>
                <tr v-if="!report.vitals.length">
                  <td colspan="4" class="muted">{{ $t('vitals.empty') }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </UiCard>

        <UiCard :title="$t('summary.meals')" :icon="PhForkKnife">
          <div class="facts">
            <div class="fact">
              <span class="fact__label">{{ $t('summary.ateWell') }}</span>
              <span class="fact__value num">{{ $t('summary.ateWellOf', { n: report.meals.ateWell, total: report.meals.mainMeals }) }}</span>
            </div>
            <div class="fact">
              <span class="fact__label">{{ $t('summary.water') }}</span>
              <span class="fact__value num">{{ report.meals.glassesPerDay === null ? '—' : $t('summary.waterGlasses', { n: formatNumber(report.meals.glassesPerDay, 1) }) }}</span>
            </div>
            <div class="fact">
              <span class="fact__label">{{ $t('summary.moodAvg') }}</span>
              <span class="fact__value num">{{ report.moodAverage === null ? '—' : `${formatNumber(report.moodAverage, 1)} / 5` }}</span>
            </div>
            <div class="fact">
              <span class="fact__label">{{ $t('summary.painAvg') }}</span>
              <span class="fact__value num">{{ report.painAverage === null ? '—' : `${formatNumber(report.painAverage, 1)} / 5` }}</span>
            </div>
          </div>
        </UiCard>
      </div>

      <div class="grid-2">
        <UiCard :title="$t('summary.notes')" :icon="PhWarningOctagon">
          <ul v-if="report.notes.length" class="notes">
            <li v-for="(n, i) in report.notes" :key="i" class="note" :class="`note--${n.kind.toLowerCase()}`">
              <p class="note__head">
                <UiBadge :tone="n.kind === 'INCIDENT' ? 'accent' : 'primary'" size="sm">{{ $t(`journal.kinds.${n.kind}`) }}</UiBadge>
                <span v-if="n.score" class="small strong">{{ $t('journal.scoreOf', { score: n.score }) }}</span>
                <span class="xsmall subtle">{{ formatDateTime(n.at) }} · {{ n.by }}</span>
              </p>
              <p v-if="n.text" class="small">{{ n.text }}</p>
            </li>
          </ul>
          <p v-else class="ok"><PhChecks :size="20" weight="bold" aria-hidden="true" /> {{ $t('summary.noNotes') }}</p>
        </UiCard>

        <UiCard :title="$t('summary.duty')" :icon="PhClockCounterClockwise">
          <div class="duty">
            <div v-for="d in report.duty" :key="d.userId" class="duty__row">
              <p class="duty__name strong">{{ d.name }}</p>
              <div class="duty__bars">
                <div class="duty__bar duty__bar--plan" :style="{ width: `${(d.scheduledMinutes / dutyMax) * 100}%` }" />
                <div class="duty__bar duty__bar--real" :style="{ width: `${(d.checkedInMinutes / dutyMax) * 100}%` }" />
              </div>
              <p class="duty__nums small num">
                <span>{{ $t('summary.scheduled') }} {{ hours(d.scheduledMinutes) }}</span>
                <span>{{ $t('summary.checkedIn') }} {{ hours(d.checkedInMinutes) }}</span>
              </p>
            </div>
            <p v-if="!report.duty.length" class="muted small">{{ $t('rota.empty') }}</p>
          </div>
          <div v-if="report.suppliesLow.length" class="low">
            <PhPackage :size="18" weight="bold" aria-hidden="true" />
            <span><strong>{{ $t('summary.supplies') }}:</strong> {{ report.suppliesLow.join(', ') }}</span>
          </div>
        </UiCard>
      </div>

      <div class="grid-2">
        <UiCard :title="$t('summary.whatsapp')" :subtitle="payerName ? `${$t('summary.previewFor', { name: payerName })} · ${$t('summary.previewLanguage', { language: languageName })}` : undefined" :icon="PhWhatsappLogo">
          <div class="phone">
            <div class="bubble">
              <p class="bubble__text">{{ page.preview.text }}</p>
              <span class="bubble__time num">19:00</span>
            </div>
          </div>
        </UiCard>

        <UiCard :title="$t('summary.history')" :icon="PhChartLine">
          <ul v-if="page.sent.length" class="sent">
            <li v-for="s in page.sent" :key="s.id" class="sent__row">
              <span class="sent__pct num" :class="{ 'sent__pct--low': (s.adherencePct ?? 100) < 90 }">{{ s.adherencePct ?? '—' }}%</span>
              <div class="sent__body">
                <button type="button" class="sent__week" @click="week = s.weekStart">
                  {{ $t('common.week', { date: formatDate(s.weekStart, 'short') }) }}
                </button>
                <p class="xsmall subtle">
                  {{ $t(`summary.source.${s.source}`) }} · {{ s.sentTo }} · <span class="num">{{ formatDate(s.generatedAt, 'short') }} {{ formatTime(s.generatedAt) }}</span>
                </p>
              </div>
            </li>
          </ul>
          <p v-else class="muted small">{{ $t('summary.noHistory') }}</p>
        </UiCard>
      </div>
    </template>
  </AppPage>
</template>

<style scoped>
.weeknav {
  display: flex;
  gap: 6px;
}
.top {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 20px;
}
@media (max-width: 960px) {
  .top {
    grid-template-columns: minmax(0, 1fr);
  }
}
.adherence__row {
  display: flex;
  align-items: center;
  gap: 20px;
}
.ring {
  width: 128px;
  height: 128px;
  flex: none;
  transform: rotate(-90deg);
}
.ring__track {
  fill: none;
  stroke: var(--gray-100);
  stroke-width: 12;
}
.ring__value {
  fill: none;
  stroke: var(--brand-500);
  stroke-width: 12;
  stroke-linecap: round;
  transition: stroke-dashoffset 600ms var(--ease);
}
.ring__text {
  transform: rotate(90deg);
  transform-origin: 64px 64px;
  fill: var(--text);
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 800;
}
.adherence__big {
  margin-top: 4px;
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 800;
}
.adherence__counts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 18px;
}
.count {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  border-radius: var(--radius);
  background: var(--surface-muted);
  border: 1px solid var(--border);
  font-size: var(--text-xs);
  font-weight: 650;
  color: var(--text-muted);
}
.count__n {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 800;
  color: var(--text);
}
.problems__list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.problem {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.problem:last-child {
  border-bottom: 0;
}
.problem__when {
  width: 84px;
  flex: none;
  font-family: var(--font-display);
  font-weight: 750;
  color: var(--text-muted);
}
.problem__body {
  flex: 1;
  min-width: 0;
}
.ok {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--success-text);
  font-weight: 650;
}
.facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.fact {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px;
  border-radius: var(--radius);
  background: var(--surface-muted);
  border: 1px solid var(--border);
}
.fact__label {
  font-size: var(--text-xs);
  font-weight: 650;
  color: var(--text-muted);
}
.fact__value {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 800;
}
.notes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.note {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  border: 1px solid var(--border);
}
.note--incident {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--accent-500) 25%, transparent);
}
.note__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.duty {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.duty__bars {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 6px 0;
}
.duty__bar {
  height: 10px;
  min-width: 4px;
  border-radius: var(--radius-pill);
}
.duty__bar--plan {
  background: var(--brand-100);
}
.duty__bar--real {
  background: linear-gradient(90deg, var(--brand-400), var(--brand-600));
}
.duty__nums {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: var(--text-muted);
}
.low {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-top: 18px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--warning-soft);
  color: var(--warning-text);
  font-size: var(--text-sm);
}
.low :deep(svg) {
  flex: none;
  margin-top: 2px;
}
.phone {
  padding: 18px;
  border-radius: var(--radius);
  background:
    radial-gradient(circle at 20% 20%, rgb(255 255 255 / 0.5), transparent 40%),
    color-mix(in srgb, var(--brand-100) 60%, var(--warning-soft));
}
.bubble {
  position: relative;
  max-width: 460px;
  padding: 10px 12px 20px;
  background: var(--surface);
  border-radius: 4px var(--radius) var(--radius) var(--radius);
  box-shadow: var(--shadow-sm);
}
.bubble__text {
  font-size: var(--text-sm);
  line-height: 1.5;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.bubble__time {
  position: absolute;
  right: 10px;
  bottom: 4px;
  font-size: 11px;
  color: var(--text-subtle);
}
.sent {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}
.sent__row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.sent__row:last-child {
  border-bottom: 0;
}
.sent__pct {
  display: grid;
  place-items: center;
  width: 58px;
  height: 40px;
  flex: none;
  border-radius: var(--radius-sm);
  background: var(--success-soft);
  color: var(--success-text);
  font-family: var(--font-display);
  font-weight: 800;
}
.sent__pct--low {
  background: var(--warning-soft);
  color: var(--warning-text);
}
.sent__week {
  padding: 0;
  border: 0;
  background: none;
  color: var(--primary);
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
