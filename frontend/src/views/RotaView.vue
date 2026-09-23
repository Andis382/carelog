<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { PhArrowsLeftRight, PhCalendarDots, PhCaretLeft, PhCaretRight, PhMoon, PhPlus, PhSun, PhTrash, PhUser } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import ShiftDialog from '@/components/care/ShiftDialog.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiIconButton from '@/components/ui/UiIconButton.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiField from '@/components/ui/UiField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import UiTabs from '@/components/ui/UiTabs.vue'
import { api, ApiError, query } from '@/lib/api'
import { addDays, mondayOf, splitMinutes, weekDates } from '@/lib/care'
import { formatDate, formatWeekday } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useConfirm } from '@/stores/confirm'
import { useToasts } from '@/stores/toasts'
import type { MyRota, RotaWeek, ShiftView } from '@/types/care'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const toasts = useToasts()
const confirm = useConfirm()

const tab = ref<'week' | 'mine'>(route.query.tab === 'mine' ? 'mine' : 'week')
const week = ref<RotaWeek | null>(null)
const mine = ref<MyRota | null>(null)
const weekStart = ref<string | null>(typeof route.query.week === 'string' ? mondayOf(route.query.week) : null)

const me = computed(() => auth.user?.id ?? 0)
const days = computed(() => (week.value ? weekDates(week.value.weekStart) : []))
const people = computed(() => week.value?.people ?? mine.value?.people ?? [])
const tabs = computed(() => [
  { value: 'week' as const, label: t('rota.week') },
  { value: 'mine' as const, label: t('rota.mine'), count: mine.value?.incoming.length || null },
])

/** Everyone keeps the same colour all week: their place in the member list. */
function tint(userId: number) {
  const index = people.value.findIndex((p) => p.id === userId)
  return `var(--person-${(index < 0 ? 0 : index % 6) + 1})`
}

function shiftsOn(day: string) {
  return week.value?.shifts.filter((s) => s.date === day) ?? []
}

const hours = computed(() =>
  people.value
    .map((p) => ({ person: p, minutes: (week.value?.shifts ?? []).filter((s) => s.userId === p.id).reduce((sum, s) => sum + s.minutes, 0) }))
    .filter((x) => x.minutes > 0),
)

function hoursLabel(minutes: number) {
  const { h, m } = splitMinutes(minutes)
  return m ? t('common.hoursMinutes', { h, m }) : t('common.hours', { h })
}

function dayTitle(day: string) {
  const label = formatWeekday(day, 'short')
  return label.charAt(0).toUpperCase() + label.slice(1)
}

async function loadWeek() {
  week.value = await api.get<RotaWeek>('/rota' + query({ week: weekStart.value }))
  weekStart.value = week.value.weekStart
}

async function loadMine() {
  mine.value = await api.get<MyRota>('/rota/mine')
}

async function reload() {
  try {
    await Promise.all([loadWeek(), loadMine()])
  } catch {
    toasts.error(t('errors.generic'))
  }
}

function move(weeks: number) {
  if (!weekStart.value) return
  weekStart.value = addDays(weekStart.value, weeks * 7)
  loadWeek()
}

function thisWeek() {
  weekStart.value = null
  loadWeek()
}

watch(tab, (value) => router.replace({ query: value === 'mine' ? { tab: 'mine' } : {} }))
onMounted(reload)

// ------------------------------------------------------------ add, open, delete

const addOpen = ref(false)
const addDay = ref('')

function openAdd(day?: string) {
  addDay.value = day ?? (week.value && days.value.includes(week.value.today) ? week.value.today : (days.value[0] ?? ''))
  addOpen.value = true
}

function onSaved(saved: ShiftView[]) {
  toasts.success(t('rota.saved', { n: saved.length }, saved.length))
  reload()
}

const selected = ref<ShiftView | null>(null)
const detailOpen = ref(false)

function openShift(shift: ShiftView) {
  selected.value = shift
  detailOpen.value = true
}

const canSwap = computed(() => {
  const s = selected.value
  return !!s && s.userId === me.value && !s.pendingSwap && new Date(`${s.date}T${s.start}`).getTime() > Date.now() && auth.canRecord
})

async function removeShift() {
  const s = selected.value
  if (!s || !(await confirm.ask({ title: t('rota.deleteTitle'), danger: true, confirmLabel: t('common.remove') }))) return
  try {
    await api.delete(`/shifts/${s.id}`)
    detailOpen.value = false
    toasts.success(t('rota.deleted'))
    reload()
  } catch (e) {
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  }
}

// ------------------------------------------------------------ swaps

const swapOpen = ref(false)
const swapShift = ref<ShiftView | null>(null)
const swapTo = ref<number | null>(null)
const swapMessage = ref('')
const swapBusy = ref(false)
const swapError = ref<string | null>(null)
const swapCandidates = computed(() => people.value.filter((p) => p.id !== me.value).map((p) => ({ value: p.id, label: p.name })))

function openSwap(shift: ShiftView) {
  swapShift.value = shift
  swapTo.value = swapCandidates.value[0]?.value ?? null
  swapMessage.value = ''
  swapError.value = null
  detailOpen.value = false
  swapOpen.value = true
}

async function sendSwap() {
  if (!swapShift.value || !swapTo.value) return
  swapBusy.value = true
  swapError.value = null
  try {
    await api.post(`/shifts/${swapShift.value.id}/swap`, { toUserId: swapTo.value, message: swapMessage.value })
    toasts.success(t('rota.swapSent', { name: people.value.find((p) => p.id === swapTo.value)?.name ?? '' }))
    swapOpen.value = false
    reload()
  } catch (e) {
    swapError.value = e instanceof ApiError ? e.message : t('errors.generic')
  } finally {
    swapBusy.value = false
  }
}

async function answer(swapId: number, action: 'accept' | 'decline' | 'cancel') {
  try {
    await api.post(`/swaps/${swapId}/${action}`)
    toasts.success(t(action === 'accept' ? 'rota.accepted' : action === 'decline' ? 'rota.declined' : 'rota.cancelled'))
    reload()
  } catch (e) {
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  }
}
</script>

<template>
  <AppPage :title="$t('rota.title')" :subtitle="$t('rota.subtitle')">
    <template v-if="auth.canRecord" #actions>
      <UiButton variant="inverse" size="lg" :icon="PhPlus" @click="openAdd()">{{ $t('rota.addShift') }}</UiButton>
    </template>

    <div class="bar">
      <UiTabs v-model="tab" :tabs="tabs" :label="$t('rota.title')" />
      <div v-if="tab === 'week' && week" class="weeknav">
        <UiIconButton :icon="PhCaretLeft" :label="$t('common.earlier')" variant="secondary" @click="move(-1)" />
        <button type="button" class="weeknav__label" @click="thisWeek">
          {{ formatDate(week.weekStart, 'short') }} – {{ formatDate(addDays(week.weekStart, 6), 'short') }}
        </button>
        <UiIconButton :icon="PhCaretRight" :label="$t('common.later')" variant="secondary" @click="move(1)" />
      </div>
    </div>

    <!-- The week -->
    <template v-if="tab === 'week'">
      <UiSkeleton v-if="!week" card :lines="8" />
      <template v-else>
        <div class="week" :class="{ 'week--empty': !week.shifts.length }">
          <section v-for="day in days" :key="day" class="day" :class="{ 'day--today': day === week.today }">
            <header class="day__head">
              <span class="day__name">{{ dayTitle(day) }}</span>
              <span class="day__date num">{{ formatDate(day, 'short') }}</span>
              <button v-if="auth.canRecord" type="button" class="day__add" :aria-label="`${$t('rota.addShift')} · ${formatDate(day, 'long')}`" @click="openAdd(day)">
                <PhPlus :size="14" weight="bold" aria-hidden="true" />
              </button>
            </header>
            <div class="day__shifts">
              <button
                v-for="s in shiftsOn(day)"
                :key="s.id"
                type="button"
                class="shift"
                :class="[`shift--${s.kind.toLowerCase()}`, { 'shift--now': s.now, 'shift--mine': s.userId === me }]"
                :style="{ '--tint': tint(s.userId) }"
                @click="openShift(s)"
              >
                <span class="shift__who">{{ s.userName }}</span>
                <span class="shift__time num">
                  <PhMoon v-if="s.kind === 'NIGHT'" :size="13" weight="fill" aria-hidden="true" />
                  <PhSun v-else-if="s.kind === 'DAY'" :size="13" weight="fill" aria-hidden="true" />
                  {{ s.start }}–{{ s.end }}
                </span>
                <span v-if="s.now" class="shift__now">{{ $t('rota.now') }}</span>
                <span v-if="s.pendingSwap" class="shift__swap">
                  <PhArrowsLeftRight :size="12" weight="bold" aria-hidden="true" /> {{ $t('rota.swapPending', { name: s.pendingSwap.toName }) }}
                </span>
              </button>
              <p v-if="!shiftsOn(day).length" class="day__free">{{ $t('rota.freeDay') }}</p>
            </div>
          </section>
        </div>

        <UiCard v-if="!week.shifts.length">
          <UiEmpty :icon="PhCalendarDots" :title="$t('rota.empty')" :text="$t('rota.emptyText')">
            <UiButton v-if="auth.canRecord" :icon="PhPlus" @click="openAdd()">{{ $t('rota.addShift') }}</UiButton>
          </UiEmpty>
        </UiCard>

        <UiCard v-else padding="sm">
          <ul class="people">
            <li v-for="h in hours" :key="h.person.id" class="person" :style="{ '--tint': tint(h.person.id) }">
              <span class="person__dot" aria-hidden="true" />
              <span class="strong">{{ h.person.name }}</span>
              <span class="small muted">{{ $t(`roles.${h.person.role}`) }}</span>
              <span class="person__hours num">{{ hoursLabel(h.minutes) }}</span>
            </li>
          </ul>
        </UiCard>
      </template>
    </template>

    <!-- My shifts and swap requests -->
    <template v-else>
      <UiSkeleton v-if="!mine" card :lines="6" />
      <template v-else>
        <UiCard v-if="mine.incoming.length" :title="$t('rota.incoming')" :icon="PhArrowsLeftRight" tone="warning">
          <div class="requests">
            <div v-for="r in mine.incoming" :key="r.swap.id" class="request">
              <div class="request__body">
                <p class="strong">{{ $t('rota.swapFrom', { name: r.swap.fromName }) }}</p>
                <p class="num">{{ formatDate(r.shift.date, 'long') }} · {{ r.shift.start }}–{{ r.shift.end }}</p>
                <p v-if="r.swap.message" class="small muted">“{{ r.swap.message }}”</p>
              </div>
              <div class="request__actions">
                <UiButton @click="answer(r.swap.id, 'accept')">{{ $t('rota.accept') }}</UiButton>
                <UiButton variant="secondary" @click="answer(r.swap.id, 'decline')">{{ $t('rota.decline') }}</UiButton>
              </div>
            </div>
          </div>
        </UiCard>

        <UiCard v-if="mine.outgoing.length" :title="$t('rota.outgoing')" :icon="PhArrowsLeftRight">
          <div class="requests">
            <div v-for="r in mine.outgoing" :key="r.swap.id" class="request">
              <div class="request__body">
                <p class="strong">{{ $t('rota.swapPending', { name: r.swap.toName }) }}</p>
                <p class="num">{{ formatDate(r.shift.date, 'long') }} · {{ r.shift.start }}–{{ r.shift.end }}</p>
              </div>
              <UiButton variant="ghost" @click="answer(r.swap.id, 'cancel')">{{ $t('rota.cancel') }}</UiButton>
            </div>
          </div>
        </UiCard>

        <UiCard :title="$t('rota.mine')" :icon="PhUser">
          <UiEmpty v-if="!mine.shifts.length" compact :icon="PhCalendarDots" :title="$t('rota.noShiftsMine')" />
          <ul v-else class="mylist">
            <li v-for="s in mine.shifts" :key="s.id" class="myshift" :style="{ '--tint': tint(s.userId) }">
              <div class="myshift__date">
                <span class="myshift__day">{{ dayTitle(s.date) }}</span>
                <span class="myshift__num num">{{ Number(s.date.slice(8)) }}</span>
              </div>
              <div class="myshift__body">
                <p class="strong num">{{ s.start }}–{{ s.end }} <UiBadge size="sm">{{ $t(`rota.kinds.${s.kind}`) }}</UiBadge></p>
                <p v-if="s.pendingSwap" class="small muted">{{ $t('rota.swapPending', { name: s.pendingSwap.toName }) }}</p>
                <p v-else-if="s.note" class="small muted">{{ s.note }}</p>
              </div>
              <UiButton v-if="!s.pendingSwap && !s.now" variant="secondary" size="sm" :icon="PhArrowsLeftRight" @click="openSwap(s)">
                {{ $t('rota.swap') }}
              </UiButton>
            </li>
          </ul>
        </UiCard>
      </template>
    </template>

    <ShiftDialog v-model:open="addOpen" :people="people" :me="me" :can-plan="auth.canPlan" :day="addDay" @saved="onSaved" />

    <UiDialog v-model:open="detailOpen" :title="selected ? `${selected.userName} · ${$t(`rota.kinds.${selected.kind}`)}` : ''" size="sm">
      <div v-if="selected" class="stack stack-sm">
        <p class="detail__when num">{{ formatDate(selected.date, 'long') }}</p>
        <p class="detail__time num">{{ selected.start }}–{{ selected.end }}</p>
        <p class="muted">{{ hoursLabel(selected.minutes) }}</p>
        <p v-if="selected.note" class="small">“{{ selected.note }}”</p>
        <p v-if="selected.pendingSwap" class="small muted">{{ $t('rota.swapPending', { name: selected.pendingSwap.toName }) }}</p>
      </div>
      <template #footer>
        <UiButton v-if="auth.canPlan" variant="ghost" :icon="PhTrash" @click="removeShift">{{ $t('common.remove') }}</UiButton>
        <UiButton v-if="canSwap && selected" :icon="PhArrowsLeftRight" @click="openSwap(selected)">{{ $t('rota.swap') }}</UiButton>
        <UiButton v-else variant="secondary" @click="detailOpen = false">{{ $t('common.close') }}</UiButton>
      </template>
    </UiDialog>

    <UiDialog v-model:open="swapOpen" :title="$t('rota.swapTitle')" :description="$t('rota.swapText')">
      <form id="swap-form" class="stack" novalidate @submit.prevent="sendSwap">
        <p v-if="swapShift" class="strong num">{{ formatDate(swapShift.date, 'long') }} · {{ swapShift.start }}–{{ swapShift.end }}</p>
        <UiField id="f-swap-to" :label="$t('rota.swapTo')" :error="swapError">
          <template #default="{ id }">
            <UiSelect :id="id" v-model="swapTo" :options="swapCandidates" />
          </template>
        </UiField>
        <UiField id="f-swap-message" :label="$t('rota.swapMessage')" optional>
          <template #default="{ id }">
            <UiInput :id="id" v-model="swapMessage" :placeholder="$t('rota.swapMessagePlaceholder')" />
          </template>
        </UiField>
      </form>
      <template #footer>
        <UiButton variant="ghost" @click="swapOpen = false">{{ $t('common.cancel') }}</UiButton>
        <UiButton type="submit" form="swap-form" :loading="swapBusy" :disabled="!swapTo">{{ $t('common.send') }}</UiButton>
      </template>
    </UiDialog>
  </AppPage>
</template>

<style scoped>
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.weeknav {
  display: flex;
  align-items: center;
  gap: 8px;
}
.weeknav__label {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text);
  font-weight: 650;
  font-size: var(--text-sm);
  box-shadow: var(--shadow-xs), var(--highlight);
}

.week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
}
.week--empty {
  display: none;
}
.day {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 220px;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), var(--highlight);
}
.day--today {
  border-color: var(--primary-soft-border);
  background: linear-gradient(180deg, var(--primary-soft), var(--surface) 55%);
  box-shadow:
    0 0 0 2px var(--primary-soft-border),
    var(--shadow-md);
}
.day__head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 2px 2px 6px;
  border-bottom: 1px solid var(--border);
}
.day__name {
  font-family: var(--font-display);
  font-weight: 800;
}
.day--today .day__name {
  color: var(--primary-strong);
}
.day__date {
  font-size: var(--text-xs);
  color: var(--text-subtle);
}
.day__add {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  margin-left: auto;
  border: 1px dashed var(--border-strong);
  border-radius: 50%;
  background: transparent;
  color: var(--text-subtle);
}
.day__add:hover {
  color: var(--primary);
  border-color: var(--primary-soft-border);
  background: var(--primary-soft);
}
.day__shifts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.day__free {
  padding: 8px 2px;
  font-size: var(--text-xs);
  color: var(--text-subtle);
}
.shift {
  --tint: var(--person-1);
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  padding: 9px 10px 9px 12px;
  border: 1px solid color-mix(in srgb, var(--tint) 22%, transparent);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--tint) 9%, var(--surface));
  color: var(--text);
  text-align: left;
  overflow: hidden;
  transition:
    transform var(--duration) var(--ease),
    box-shadow var(--duration) var(--ease);
}
.shift::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--tint);
}
.shift:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}
.shift--night {
  background: color-mix(in srgb, var(--tint) 16%, var(--gray-50));
}
.shift--visit {
  border-style: dashed;
}
.shift--now {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--tint) 45%, transparent);
}
.shift__who {
  max-width: 100%;
  font-size: var(--text-sm);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shift__time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-muted);
}
.shift__time :deep(svg) {
  color: var(--tint);
}
.shift__now {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: var(--tint);
  color: var(--text-inverse);
  font-size: 10px;
  font-weight: 750;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.shift__swap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 11px;
  font-weight: 650;
  color: var(--accent-soft-text);
}
@media (max-width: 1100px) {
  .week {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .week {
    grid-template-columns: minmax(0, 1fr);
  }
  .day {
    min-height: 0;
  }
}

.people {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  margin: 0;
  padding: 4px 6px;
  list-style: none;
}
.person {
  display: flex;
  align-items: center;
  gap: 8px;
}
.person__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  background: var(--tint);
}
.person__hours {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--surface-sunken);
  font-size: var(--text-xs);
  font-weight: 700;
}

.requests {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.request {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
}
.request__body {
  flex: 1;
  min-width: 0;
}
.request__actions {
  display: flex;
  gap: 8px;
}
@media (max-width: 640px) {
  .request {
    flex-direction: column;
    align-items: stretch;
  }
  .request__actions > * {
    flex: 1;
  }
}
.mylist {
  margin: 0;
  padding: 0;
  list-style: none;
}
.myshift {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 2px;
  border-bottom: 1px solid var(--border);
}
.myshift:last-child {
  border-bottom: 0;
}
.myshift__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  flex: none;
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--tint) 12%, var(--surface));
  border: 1px solid color-mix(in srgb, var(--tint) 25%, transparent);
}
.myshift__day {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
}
.myshift__num {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 800;
  line-height: 1;
}
.myshift__body {
  flex: 1;
  min-width: 0;
}
.detail__when {
  color: var(--text-muted);
}
.detail__time {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 800;
}
</style>
