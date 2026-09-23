<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  PhArrowsLeftRight,
  PhCaretLeft,
  PhCaretRight,
  PhDoorOpen,
  PhDrop,
  PhEye,
  PhForkKnife,
  PhHandHeart,
  PhHeartbeat,
  PhMapPin,
  PhNotePencil,
  PhPackage,
  PhPill,
  PhPlus,
  PhSmiley,
  PhStethoscope,
} from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import HeroChip from '@/components/care/HeroChip.vue'
import DoseTimeline from '@/components/care/DoseTimeline.vue'
import NotGivenDialog from '@/components/care/NotGivenDialog.vue'
import GuardDialog, { type GuardInfo } from '@/components/care/GuardDialog.vue'
import VitalDialog from '@/components/care/VitalDialog.vue'
import ScorePicker from '@/components/care/ScorePicker.vue'
import NoteComposer from '@/components/care/NoteComposer.vue'
import JournalList from '@/components/care/JournalList.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiIconButton from '@/components/ui/UiIconButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiNotice from '@/components/ui/UiNotice.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import UiProgress from '@/components/ui/UiProgress.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import { api, ApiError, query, type FieldErrors } from '@/lib/api'
import { addDays, formatVital, progress, rangeLabel } from '@/lib/care'
import { formatDate, formatNumber, formatRelative, formatTime } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useSync } from '@/stores/sync'
import { useToasts } from '@/stores/toasts'
import { useConfirm } from '@/stores/confirm'
import type { AsNeeded, DoseLine, DoseStatus, JournalEntry, JournalKind, MealAmount, MealSlot, Reading, TodayData, VitalKind } from '@/types/care'

type DoseResult = { id: number; status: DoseStatus; note: string | null; recordedById: number; recordedBy: string; recordedAt: string; canUndo: boolean }

const REFRESH_MS = 120_000

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const sync = useSync()
const toasts = useToasts()
const confirm = useConfirm()

const data = ref<TodayData | null>(null)
const failed = ref(false)
const offlineAt = ref<string | null>(null)
const busy = ref<string | null>(null)
const queued = ref<string[]>([])

const date = computed(() => (typeof route.query.date === 'string' ? route.query.date : undefined))
const isToday = computed(() => !data.value || data.value.date === data.value.today)
const canRecord = computed(() => !!data.value?.canRecord)
const doseProgress = computed(() => progress(data.value?.doses ?? []))
const onDuty = computed(() => data.value?.duty.find((d) => d.now) ?? null)
const nextDuty = computed(() => data.value?.duty.find((d) => !d.now && d.start > formatTime(new Date())) ?? null)
const appointmentSoon = computed(() => {
  const a = data.value?.nextAppointment
  return a && a.daysAway <= 14 ? a : null
})

// ------------------------------------------------------------ loading, with an offline copy

function cacheKey() {
  return `carelog.today.${auth.user?.id}.${date.value ?? 'today'}`
}

function saveCache(payload: TodayData) {
  try {
    localStorage.setItem(cacheKey(), JSON.stringify({ at: new Date().toISOString(), payload }))
  } catch {
    /* storage blocked: offline copy is a nicety */
  }
}

function readCache(): { at: string; payload: TodayData } | null {
  try {
    const raw = localStorage.getItem(cacheKey())
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

async function load() {
  try {
    const payload = await api.get<TodayData>('/today' + query({ date: date.value }))
    data.value = payload
    offlineAt.value = null
    failed.value = false
    queued.value = queued.value.filter((k) => payload.doses.some((d) => key(d) === k && d.state !== 'GIVEN'))
    saveCache(payload)
  } catch (e) {
    const cached = e instanceof ApiError && e.status === 0 ? readCache() : null
    if (cached) {
      data.value = cached.payload
      offlineAt.value = cached.at
    } else if (!data.value) {
      failed.value = true
    }
  }
}

let timer: ReturnType<typeof setInterval> | null = null
let unsubscribe: (() => void) | null = null
onMounted(() => {
  load()
  timer = setInterval(load, REFRESH_MS)
  unsubscribe = sync.onSynced(() => {
    toasts.success(t('sync.synced'))
    load()
  })
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  unsubscribe?.()
})
watch(date, () => {
  data.value = null
  load()
})

function goToDay(day: string | undefined) {
  router.replace({ name: 'home', query: day && day !== data.value?.today ? { date: day } : {} })
}

/** A saved write says so; a queued one says it is waiting on this phone. */
function announce(queued: boolean, text: string) {
  if (queued) toasts.info(t('sync.queued'))
  else toasts.success(text)
}

function problem(e: unknown) {
  toasts.error(e instanceof ApiError && e.status !== 0 ? e.message : t('errors.network'))
}

// ------------------------------------------------------------ doses

function key(line: { medicationId: number; time: string }) {
  return `${line.medicationId}-${line.time}`
}

const guardOpen = ref(false)
const guard = ref<GuardInfo | null>(null)
const notGivenOpen = ref(false)
const notGivenLine = ref<DoseLine | null>(null)
const notGivenBusy = ref(false)
const notGivenError = ref<string | null>(null)

function showGuard(line: { name: string; time: string | null }, e: ApiError) {
  const d = e.details as { status?: string; recordedBy?: string; time?: string; note?: string | null }
  guard.value = {
    medicine: line.name,
    time: line.time ?? '',
    status: d.status ?? 'GIVEN',
    by: d.recordedBy ?? '',
    at: d.time ?? '',
    note: d.note ?? null,
  }
  guardOpen.value = true
}

/** Returns a field error for the dialog, true when done, false when it failed. */
async function recordDose(line: DoseLine, status: DoseStatus, note?: string): Promise<true | false | string> {
  if (!data.value) return false
  const k = key(line)
  busy.value = status === 'GIVEN' ? k : null
  try {
    const res = await sync.record<DoseResult>(
      '/doses',
      { medicationId: line.medicationId, date: data.value.date, time: line.time, status, note },
      `${line.name} ${line.time} · ${t(`doses.state.${status}`)}`,
    )
    line.state = status
    if (res.queued) {
      queued.value.push(k)
      toasts.info(t('sync.queued'))
    } else {
      const r = res.result
      line.event = { id: r.id, status: r.status, byId: r.recordedById, by: r.recordedBy, at: r.recordedAt, note: r.note, canUndo: r.canUndo }
      toasts.success(t('doses.saved', { name: line.name }))
      load()
    }
    return true
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      showGuard(line, e)
      load()
      return true
    }
    if (e instanceof ApiError && e.status === 422) return e.errors.note?.[0] ?? e.message
    problem(e)
    return false
  } finally {
    busy.value = null
  }
}

function give(line: DoseLine) {
  recordDose(line, 'GIVEN')
}

function openNotGiven(line: DoseLine) {
  notGivenLine.value = line
  notGivenError.value = null
  notGivenOpen.value = true
}

async function submitNotGiven(status: 'SKIPPED' | 'REFUSED', note: string) {
  if (!notGivenLine.value) return
  notGivenBusy.value = true
  const result = await recordDose(notGivenLine.value, status, note)
  notGivenBusy.value = false
  if (result === true) notGivenOpen.value = false
  else if (typeof result === 'string') notGivenError.value = result
}

async function undo(line: DoseLine) {
  if (!line.event) return
  try {
    await api.delete(`/doses/${line.event.id}`)
    toasts.info(t('doses.undone'))
    load()
  } catch (e) {
    problem(e)
  }
}

async function giveAsNeeded(med: AsNeeded) {
  const label = [med.name, med.strength].filter(Boolean).join(' ')
  const text = med.last
    ? t('today.giveNowText', { when: formatRelative(med.last.at), name: med.last.by })
    : t('today.giveNowFirst')
  if (!(await confirm.ask({ title: t('today.giveNowTitle', { name: label }), text, confirmLabel: t('doses.give') }))) return
  try {
    const res = await sync.record<DoseResult>('/doses', { medicationId: med.medicationId, date: data.value?.date, status: 'GIVEN' }, `${label} · ${t('doses.state.GIVEN')}`)
    announce(res.queued, t('doses.saved', { name: label }))
    load()
  } catch (e) {
    problem(e)
  }
}

// ------------------------------------------------------------ vitals

const vitalOpen = ref(false)
const vitalKind = ref<VitalKind | null>(null)
const vitalBusy = ref(false)
const vitalErrors = ref<FieldErrors>({})
const vitalTrigger = ref(0)
const vitalRange = computed(() => data.value?.vitals.find((v) => v.kind === vitalKind.value)?.range ?? null)

function fmt(n: number, digits: number) {
  return formatNumber(n, digits)
}

function openVital(kind: VitalKind) {
  vitalKind.value = kind
  vitalErrors.value = {}
  vitalOpen.value = true
}

async function saveVital(value1: number | null, value2: number | null, note: string) {
  const kind = vitalKind.value
  if (!kind || !data.value) return
  const missing: FieldErrors = {}
  if (value1 === null) missing.value1 = [t('vitals.value')]
  if (kind === 'BP' && value2 === null) missing.value2 = [t('vitals.diastolic')]
  if (Object.keys(missing).length) {
    vitalErrors.value = missing
    vitalTrigger.value++
    return
  }
  vitalBusy.value = true
  try {
    const res = await sync.record<Reading>('/vitals', { kind, value1, value2, note }, `${t(`vitals.kinds.${kind}`)} ${formatVital(kind, value1!, value2, fmt)}`)
    const card = data.value.vitals.find((v) => v.kind === kind)
    if (card) {
      card.last = res.queued
        ? { id: 0, kind, value1: value1!, value2, measuredAt: new Date().toISOString(), by: auth.user?.name ?? '', note, position: null }
        : res.result
    }
    announce(res.queued, t('vitals.saved', { kind: t(`vitals.kinds.${kind}`) }))
    vitalOpen.value = false
  } catch (e) {
    if (e instanceof ApiError && e.status === 422) {
      vitalErrors.value = e.errors
      vitalTrigger.value++
    } else problem(e)
  } finally {
    vitalBusy.value = false
  }
}

// ------------------------------------------------------------ meals, water, mood, notes

const mealSlots: MealSlot[] = ['BREAKFAST', 'LUNCH', 'DINNER']
const amounts = computed(() => (['ALL', 'HALF', 'LITTLE', 'NONE'] as MealAmount[]).map((a) => ({ value: a, label: t(`meals.amounts.${a}`) })))

async function recordMeal(slot: MealSlot, amount: MealAmount | null | undefined) {
  if (!data.value || !amount) return
  const previous = data.value.meals[slot]
  data.value.meals[slot] = { amount, by: auth.user?.name ?? '', at: new Date().toISOString() }
  try {
    const res = await sync.record('/meals', { date: data.value.date, slot, amount }, `${t(`meals.slots.${slot}`)} · ${t(`meals.amounts.${amount}`)}`)
    announce(res.queued, t('meals.saved', { slot: t(`meals.slots.${slot}`), amount: t(`meals.amounts.${amount}`) }))
  } catch (e) {
    if (previous) data.value.meals[slot] = previous
    else delete data.value.meals[slot]
    problem(e)
  }
}

async function addGlass() {
  if (!data.value) return
  data.value.glasses++
  try {
    const res = await sync.record('/meals', { date: data.value.date, slot: 'DRINK', glasses: 1 }, t('meals.glassSaved'))
    announce(res.queued, t('meals.glassSaved'))
  } catch (e) {
    data.value.glasses--
    problem(e)
  }
}

async function recordScore(kind: 'MOOD' | 'PAIN', score: number) {
  if (!data.value) return
  const label = t(`journal.${kind === 'MOOD' ? 'mood' : 'pain'}.${score}`)
  const mark = { score, by: auth.user?.name ?? '', at: new Date().toISOString() }
  try {
    const res = await sync.record('/journal', { kind, score }, `${t(`journal.kinds.${kind}`)} · ${label}`)
    if (kind === 'MOOD') data.value.mood = mark
    else data.value.pain = mark
    announce(res.queued, t('journal.scoreSaved', { kind: t(`journal.kinds.${kind}`), label }))
  } catch (e) {
    problem(e)
  }
}

const composer = ref<InstanceType<typeof NoteComposer> | null>(null)
const noteBusy = ref(false)
const noteError = ref<string | null>(null)

async function saveNote(kind: JournalKind, text: string, photo: File | null) {
  if (!data.value) return
  noteBusy.value = true
  noteError.value = null
  try {
    let photoFileId: string | null = null
    if (photo) {
      const form = new FormData()
      form.append('file', photo, photo.name || 'photo.jpg')
      photoFileId = (await api.upload<{ id: string }>('/uploads', form)).id
    }
    const res = await sync.record<JournalEntry>('/journal', { kind, text, photoFileId }, `${t(`journal.kinds.${kind}`)}: ${text.slice(0, 40)}`)
    const entry: JournalEntry = res.queued
      ? { id: -Date.now(), kind, score: null, text, photoUrl: null, by: auth.user?.name ?? '', at: new Date().toISOString() }
      : res.result
    data.value.journal = [entry, ...data.value.journal]
    composer.value?.reset()
    announce(res.queued, t('journal.saved'))
  } catch (e) {
    if (e instanceof ApiError && e.status === 422) noteError.value = e.errors.text?.[0] ?? e.message
    else if (photo && e instanceof ApiError && e.status === 0) toasts.error(t('common.uploadFailed'), t('errors.network'))
    else problem(e)
  } finally {
    noteBusy.value = false
  }
}

// ------------------------------------------------------------ check in / out

const checking = ref(false)

function locate(): Promise<{ lat: number; lng: number; accuracy: number } | null> {
  if (!('geolocation' in navigator)) return Promise.resolve(null)
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: Math.round(p.coords.accuracy) }),
      () => resolve(null),
      { enableHighAccuracy: true, timeout: 6000, maximumAge: 60_000 },
    )
  })
}

async function toggleCheckIn() {
  if (!data.value) return
  const leaving = !!data.value.myCheckIn
  checking.value = true
  try {
    const where = await locate()
    if (!where) toasts.info(t('checkin.denied'))
    const res = await sync.record<{ id: number; checkedInAt: string }>(
      leaving ? '/checkins/out' : '/checkins/in',
      { ...where },
      leaving ? t('today.checkOut') : t('today.checkIn'),
    )
    data.value.myCheckIn = leaving ? null : res.queued ? { id: 0, checkedInAt: new Date().toISOString() } : res.result
    announce(res.queued, leaving ? t('today.checkedOut') : t('today.checkedIn'))
  } catch (e) {
    problem(e)
    load()
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <AppPage
    :title="data?.elder?.fullName ?? $t('nav.today')"
    :eyebrow="data ? (isToday ? formatDate(data.date, 'long') : $t('today.eyebrowPast', { date: formatDate(data.date, 'long') })) : undefined"
    :subtitle="data?.elder ? [data.elder.age ? $t('today.age', { age: data.elder.age }) : null, data.elder.town].filter(Boolean).join(' · ') : undefined"
  >
    <template v-if="data" #meta>
      <template v-if="isToday">
        <HeroChip v-if="onDuty" tone="live">
          {{ $t('today.onDutyNow') }}: <strong>{{ onDuty.name }}</strong> · <span class="num">{{ onDuty.start }}–{{ onDuty.end }}</span>
        </HeroChip>
        <HeroChip v-else :icon="PhHandHeart">
          {{ nextDuty ? $t('today.nextOnDuty', { name: nextDuty.name, time: nextDuty.start }) : $t('today.nobodyOnDuty') }}
        </HeroChip>
      </template>
      <HeroChip v-else :icon="PhCaretLeft" :to="{ name: 'home' }">{{ $t('today.backToToday') }}</HeroChip>
      <HeroChip v-if="appointmentSoon" :icon="PhStethoscope" :to="{ name: 'visits' }">
        {{ $t('today.appointment', { specialty: appointmentSoon.specialty ?? $t('visits.next'), doctor: appointmentSoon.doctorName }) }} ·
        {{ $t('common.inDays', { n: appointmentSoon.daysAway }, appointmentSoon.daysAway) }}
      </HeroChip>
      <HeroChip v-if="data.lowSupplies.length" tone="warm" :icon="PhPackage" :to="{ name: 'supplies' }">
        {{ $t('today.lowSupplies', { items: data.lowSupplies.join(', ') }) }}
      </HeroChip>
      <HeroChip v-if="data.swapsForMe" tone="warm" :icon="PhArrowsLeftRight" :to="{ name: 'rota' }">
        {{ $t('today.swapsForMe', { n: data.swapsForMe }, data.swapsForMe) }}
      </HeroChip>
    </template>
    <template v-if="data" #actions>
      <div class="daynav">
        <UiIconButton variant="inverse" :icon="PhCaretLeft" :label="$t('today.previousDay')" @click="goToDay(addDays(data.date, -1))" />
        <UiIconButton
          variant="inverse"
          :icon="PhCaretRight"
          :label="$t('today.nextDay')"
          :disabled="isToday"
          @click="goToDay(addDays(data.date, 1))"
        />
      </div>
      <UiButton
        v-if="canRecord && isToday"
        variant="inverse"
        size="lg"
        :icon="data.myCheckIn ? PhDoorOpen : PhMapPin"
        :loading="checking"
        @click="toggleCheckIn"
      >
        {{ data.myCheckIn ? $t('today.checkOut') : $t('today.checkIn') }}
        <span v-if="data.myCheckIn" class="checkin-since num">· {{ formatTime(data.myCheckIn.checkedInAt) }}</span>
      </UiButton>
    </template>

    <UiNotice v-if="offlineAt" tone="warning">{{ $t('sync.offlineNotice', { time: formatTime(offlineAt) }) }}</UiNotice>
    <UiNotice v-if="data && !canRecord" :icon="PhEye">{{ $t('today.readOnly') }}</UiNotice>

    <div v-if="!data && !failed" class="today">
      <div class="today__main"><UiSkeleton card :lines="8" /></div>
      <div class="today__side"><UiSkeleton card :lines="4" /><UiSkeleton card :lines="4" /></div>
    </div>

    <UiCard v-else-if="failed">
      <UiEmpty :icon="PhHeartbeat" :title="$t('errors.generic')">
        <UiButton variant="secondary" @click="load">{{ $t('common.retry') }}</UiButton>
      </UiEmpty>
    </UiCard>

    <div v-else-if="data" class="today">
      <div class="today__main">
        <UiCard :title="$t('today.medsTitle')" :icon="PhPill" class="meds">
          <template v-if="data.doses.length" #actions>
            <span class="meds__count num">{{ $t('today.medsSubtitle', { done: doseProgress.done, total: doseProgress.total }) }}</span>
          </template>
          <template v-if="data.doses.length">
            <UiProgress
              class="meds__progress"
              :value="doseProgress.done"
              :max="doseProgress.total"
              :label="$t('today.medsSubtitle', { done: doseProgress.done, total: doseProgress.total })"
              tone="success"
            />
            <DoseTimeline
              :lines="data.doses"
              :can-record="canRecord"
              :busy="busy"
              :queued="queued"
              @give="give"
              @not-given="openNotGiven"
              @undo="undo"
            />
          </template>
          <UiEmpty v-else compact :icon="PhPill" :title="$t('today.medsEmpty')">
            <UiButton v-if="auth.canPlan" variant="secondary" :icon="PhPlus" :to="{ name: 'medication-new' }">{{ $t('today.medsEmptyAction') }}</UiButton>
          </UiEmpty>
        </UiCard>

        <UiCard v-if="data.asNeeded.length" :title="$t('today.asNeeded')" :icon="PhDrop" padding="sm">
          <ul class="prn">
            <li v-for="m in data.asNeeded" :key="m.medicationId" class="prn__item">
              <div class="prn__what">
                <p class="strong">{{ m.name }} <span class="muted">{{ m.strength }}</span></p>
                <p class="small muted">{{ m.instructions }}</p>
                <p class="small" :class="m.last ? 'subtle' : 'muted'">
                  {{ m.last ? $t('today.lastGiven', { when: formatRelative(m.last.at), name: m.last.by }) : $t('today.neverGiven') }}
                </p>
              </div>
              <UiButton v-if="canRecord && isToday" variant="secondary" :icon="PhPlus" @click="giveAsNeeded(m)">{{ $t('today.giveNow') }}</UiButton>
            </li>
          </ul>
        </UiCard>

        <UiCard v-if="canRecord && isToday" :title="$t('today.noteTitle')" :icon="PhNotePencil" class="order-note">
          <NoteComposer ref="composer" :busy="noteBusy" :error="noteError" @save="saveNote" />
        </UiCard>

        <UiCard :title="$t('today.notesToday')" padding="sm" class="order-notes">
          <JournalList v-if="data.journal.length" :entries="data.journal" />
          <p v-else class="small muted">{{ $t('today.noNotes') }}</p>
        </UiCard>
      </div>

      <div class="today__side">
        <UiCard v-if="isToday" :title="$t('today.vitalsTitle')" :subtitle="canRecord ? $t('today.vitalsHint') : undefined" :icon="PhHeartbeat">
          <div class="vitals">
            <button
              v-for="v in data.vitals"
              :key="v.kind"
              type="button"
              class="vital"
              :class="{ 'vital--out': v.last?.position }"
              :disabled="!canRecord"
              @click="openVital(v.kind)"
            >
              <span class="vital__kind">{{ $t(`vitals.short.${v.kind}`) }}</span>
              <span v-if="v.last" class="vital__value num">
                {{ formatVital(v.kind, v.last.value1, v.last.value2, fmt) }}<small>{{ $t(`vitals.units.${v.kind}`) }}</small>
              </span>
              <span v-else class="vital__value vital__value--none">—</span>
              <span class="vital__meta">
                <template v-if="v.last">{{ formatRelative(v.last.measuredAt) }}</template>
                <template v-else>{{ $t('vitals.none') }}</template>
              </span>
              <span v-if="rangeLabel(v.range, fmt)" class="vital__range num">{{ $t('vitals.usual', { range: rangeLabel(v.range, fmt) }) }}</span>
              <span v-if="canRecord" class="vital__add" aria-hidden="true"><PhPlus :size="14" weight="bold" /></span>
            </button>
          </div>
        </UiCard>

        <UiCard :title="$t('today.mealsTitle')" :icon="PhForkKnife">
          <div class="meals">
            <div v-for="slot in mealSlots" :key="slot" class="meal">
              <div class="meal__head">
                <span class="strong">{{ $t(`meals.slots.${slot}`) }}</span>
                <span v-if="data.meals[slot]" class="xsmall subtle">{{ data.meals[slot]!.by }} · <span class="num">{{ formatTime(data.meals[slot]!.at) }}</span></span>
              </div>
              <UiSegmented
                v-if="canRecord"
                :model-value="data.meals[slot]?.amount ?? null"
                :options="amounts"
                :label="$t(`meals.slots.${slot}`)"
                block
                @update:model-value="(a) => recordMeal(slot, a)"
              />
              <p v-else class="small muted">{{ data.meals[slot] ? $t(`meals.amounts.${data.meals[slot]!.amount}`) : '—' }}</p>
            </div>
            <div class="water">
              <div class="water__glasses" aria-hidden="true">
                <span v-for="n in Math.max(8, data.glasses)" :key="n" class="water__glass" :class="{ 'is-full': n <= data.glasses }" />
              </div>
              <div class="water__row">
                <p class="water__count">{{ $t('today.glasses', { n: data.glasses }, data.glasses) }}</p>
                <UiButton v-if="canRecord" variant="soft" :icon="PhPlus" @click="addGlass">{{ $t('today.addGlass') }}</UiButton>
              </div>
            </div>
          </div>
        </UiCard>

        <UiCard v-if="isToday" :title="$t('today.moodTitle')" :icon="PhSmiley">
          <div class="stack">
            <div>
              <p class="eyebrow scale-label">{{ $t('today.mood') }}</p>
              <ScorePicker kind="MOOD" :last="data.mood" :disabled="!canRecord" @pick="(s) => recordScore('MOOD', s)" />
            </div>
            <div>
              <p class="eyebrow scale-label">{{ $t('today.pain') }}</p>
              <ScorePicker kind="PAIN" :last="data.pain" :disabled="!canRecord" @pick="(s) => recordScore('PAIN', s)" />
            </div>
          </div>
        </UiCard>

      </div>
    </div>

    <NotGivenDialog v-model:open="notGivenOpen" :line="notGivenLine" :busy="notGivenBusy" :error="notGivenError" @submit="submitNotGiven" />
    <GuardDialog v-model:open="guardOpen" :info="guard" />
    <VitalDialog
      v-model:open="vitalOpen"
      :kind="vitalKind"
      :range="vitalRange"
      :busy="vitalBusy"
      :errors="vitalErrors"
      :trigger="vitalTrigger"
      @submit="saveVital"
    />
  </AppPage>
</template>

<style scoped>
.today {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  gap: 20px;
  align-items: start;
}
.today__main,
.today__side {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}
/* One column on phones: the note composer and today's notes move below vitals, meals and mood */
@media (max-width: 1000px) {
  .today {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }
  .today__main,
  .today__side {
    display: contents;
  }
  .order-note {
    order: 6;
  }
  .order-notes {
    order: 7;
  }
}
.daynav {
  display: flex;
  gap: 6px;
}
.checkin-since {
  font-weight: 600;
  opacity: 0.85;
}
.meds__count {
  font-size: var(--text-sm);
  font-weight: 650;
  color: var(--text-muted);
  white-space: nowrap;
}
.meds__progress {
  margin: -2px 0 18px;
}

.prn {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.prn__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-muted);
}
.prn__what {
  flex: 1;
  min-width: 0;
}
@media (max-width: 640px) {
  .prn__item {
    flex-direction: column;
    align-items: stretch;
  }
}

.vitals {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
@media (min-width: 1001px) and (max-width: 1180px) {
  .vitals {
    grid-template-columns: minmax(0, 1fr);
  }
}
.vital {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-height: 104px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: linear-gradient(180deg, var(--surface), var(--surface-muted));
  color: var(--text);
  text-align: left;
  box-shadow: var(--shadow-xs), var(--highlight);
  transition:
    transform var(--duration) var(--ease),
    box-shadow var(--duration) var(--ease),
    border-color var(--duration) var(--ease);
}
.vital:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: var(--primary-soft-border);
  box-shadow: var(--shadow-md), var(--highlight);
}
.vital:disabled {
  cursor: default;
}
.vital--out {
  border-color: color-mix(in srgb, var(--info) 28%, transparent);
}
.vital__kind {
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-subtle);
}
.vital__value {
  font-family: var(--font-display);
  font-size: 1.55rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.15;
}
.vital__value small {
  margin-left: 4px;
  font-family: var(--font-body);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0;
  color: var(--text-subtle);
}
.vital__value--none {
  color: var(--gray-300);
}
.vital__meta {
  font-size: var(--text-xs);
  color: var(--text-muted);
}
.vital__range {
  margin-top: auto;
  padding-top: 4px;
  font-size: 11px;
  color: var(--text-subtle);
}
.vital--out .vital__range {
  color: var(--info-text);
  font-weight: 600;
}
.vital__add {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: var(--primary);
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
}

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
.scale-label {
  margin-bottom: 8px;
}
</style>
