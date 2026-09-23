<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhCalendarCheck, PhFileText, PhMapPin, PhPencilSimple, PhPlus, PhStethoscope, PhTrash } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiIconButton from '@/components/ui/UiIconButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api, ApiError } from '@/lib/api'
import { daysBetween } from '@/lib/care'
import { dayKey, formatDate, formatMonthShort, formatWeekday } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useConfirm } from '@/stores/confirm'
import { useToasts } from '@/stores/toasts'
import type { Visit } from '@/types/care'

const { t } = useI18n()
const auth = useAuth()
const toasts = useToasts()
const confirm = useConfirm()

const visits = ref<Visit[] | null>(null)
const failed = ref(false)
const today = dayKey(new Date())

const upcoming = computed(() =>
  (visits.value ?? []).filter((v) => v.nextDate && v.nextDate >= today).sort((a, b) => (a.nextDate! < b.nextDate! ? -1 : 1)),
)

function canEdit(v: Visit) {
  return auth.canPlan || v.recordedById === auth.user?.id
}

async function load() {
  try {
    visits.value = await api.get<Visit[]>('/visits')
  } catch {
    failed.value = true
  }
}

async function remove(v: Visit) {
  if (!(await confirm.ask({ title: t('visits.deleteTitle'), text: `${v.doctorName} · ${formatDate(v.date)}`, danger: true, confirmLabel: t('common.delete') }))) return
  try {
    await api.delete(`/visits/${v.id}`)
    toasts.success(t('visits.deleted'))
    load()
  } catch (e) {
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  }
}

onMounted(load)
</script>

<template>
  <AppPage :title="$t('visits.title')" :subtitle="$t('visits.subtitle')">
    <template v-if="auth.canRecord" #actions>
      <UiButton variant="inverse" size="lg" :icon="PhPlus" :to="{ name: 'visit-new' }">{{ $t('visits.add') }}</UiButton>
    </template>

    <UiSkeleton v-if="!visits && !failed" card :lines="6" />
    <UiCard v-else-if="failed">
      <UiEmpty :icon="PhStethoscope" :title="$t('errors.generic')">
        <UiButton variant="secondary" @click="load">{{ $t('common.retry') }}</UiButton>
      </UiEmpty>
    </UiCard>
    <UiCard v-else-if="visits && !visits.length">
      <UiEmpty :icon="PhStethoscope" :title="$t('visits.empty')" :text="$t('visits.emptyText')">
        <UiButton v-if="auth.canRecord" :icon="PhPlus" :to="{ name: 'visit-new' }">{{ $t('visits.add') }}</UiButton>
      </UiEmpty>
    </UiCard>

    <template v-else-if="visits">
      <section v-if="upcoming.length" class="stack stack-sm" :aria-label="$t('visits.upcoming')">
        <div class="upcoming">
          <article v-for="v in upcoming" :key="`n${v.id}`" class="appointment">
            <div class="appointment__date">
              <span class="appointment__month">{{ formatMonthShort(v.nextDate!) }}</span>
              <span class="appointment__day num">{{ Number(v.nextDate!.slice(8)) }}</span>
              <span class="appointment__weekday">{{ formatWeekday(v.nextDate!, 'short') }}</span>
            </div>
            <div class="appointment__body">
              <p class="appointment__title">{{ v.specialty ?? $t('visits.next') }} · {{ v.doctorName }}</p>
              <p class="small muted">
                <span v-if="v.nextTime" class="num">{{ v.nextTime }} · </span>{{ v.place }}
              </p>
              <p class="appointment__in">{{ $t('common.inDays', { n: daysBetween(today, v.nextDate!) }, daysBetween(today, v.nextDate!)) }}</p>
            </div>
          </article>
        </div>
      </section>

      <section class="stack stack-sm">
        <h2 class="group">{{ $t('visits.past') }}</h2>
        <article v-for="v in visits" :key="v.id" class="visit">
          <div class="visit__head">
            <span class="visit__icon"><PhStethoscope :size="22" weight="duotone" aria-hidden="true" /></span>
            <div class="visit__titles">
              <h3>{{ v.doctorName }}<span v-if="v.specialty" class="visit__specialty"> · {{ v.specialty }}</span></h3>
              <p class="small muted">
                <span class="num">{{ formatDate(v.date, 'long') }}</span>
                <template v-if="v.place"> · <PhMapPin :size="14" weight="bold" class="inline-icon" aria-hidden="true" /> {{ v.place }}</template>
              </p>
            </div>
            <div v-if="canEdit(v)" class="visit__actions">
              <UiIconButton :icon="PhPencilSimple" :label="$t('common.edit')" size="sm" :to="{ name: 'visit-edit', params: { id: v.id } }" />
              <UiIconButton v-if="auth.isOwner || v.recordedById === auth.user?.id" :icon="PhTrash" :label="$t('common.delete')" size="sm" @click="remove(v)" />
            </div>
          </div>
          <div class="visit__body">
            <p v-if="v.notes" class="visit__notes">{{ v.notes }}</p>
            <a v-if="v.prescriptionUrl" :href="v.prescriptionUrl" target="_blank" rel="noopener" class="visit__rx">
              <img :src="v.prescriptionUrl" :alt="$t('visits.prescription')" loading="lazy" />
              <span><PhFileText :size="16" weight="bold" aria-hidden="true" /> {{ $t('visits.openPrescription') }}</span>
            </a>
          </div>
          <footer class="visit__foot">
            <span v-if="v.nextDate" class="visit__next">
              <PhCalendarCheck :size="16" weight="bold" aria-hidden="true" />
              <span>{{ $t('visits.nextOn', { date: formatDate(v.nextDate) }) }}<template v-if="v.nextTime">, {{ v.nextTime }}</template></span>
            </span>
            <span class="xsmall subtle">{{ $t('visits.writtenBy', { name: v.recordedBy ?? '—' }) }}</span>
          </footer>
        </article>
      </section>
    </template>
  </AppPage>
</template>

<style scoped>
.group {
  font-size: var(--text-md);
}
.upcoming {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 360px), 1fr));
}
.appointment {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 16px;
  background: linear-gradient(135deg, var(--primary-soft), var(--surface) 70%);
  border: 1px solid var(--primary-soft-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md), var(--highlight);
}
.appointment__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 74px;
  height: 84px;
  flex: none;
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.appointment__month {
  align-self: stretch;
  padding: 3px 0;
  text-align: center;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-inverse);
  background: linear-gradient(180deg, var(--accent-400), var(--accent-500));
}
.appointment__day {
  font-family: var(--font-display);
  font-size: 1.9rem;
  font-weight: 800;
  line-height: 1.1;
}
.appointment__weekday {
  font-size: 11px;
  font-weight: 650;
  color: var(--text-muted);
}
.appointment__title {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: var(--text-lg);
}
.appointment__in {
  margin-top: 6px;
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: var(--surface);
  border: 1px solid var(--primary-soft-border);
  color: var(--primary-strong);
  font-size: var(--text-sm);
  font-weight: 700;
}
.visit {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), var(--highlight);
}
.visit__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px 0;
}
.visit__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: var(--radius-sm);
  color: var(--primary);
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
}
.visit__titles {
  flex: 1;
  min-width: 0;
}
.visit__titles h3 {
  font-size: var(--text-md);
}
.visit__specialty {
  font-weight: 600;
  color: var(--text-muted);
}
.inline-icon {
  display: inline;
  vertical-align: -2px;
}
.visit__actions {
  display: flex;
  gap: 4px;
}
.visit__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 12px 18px 14px 72px;
}
.visit__notes {
  flex: 1;
  white-space: pre-line;
}
.visit__rx {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: none;
  width: 140px;
  font-size: var(--text-xs);
  font-weight: 650;
  text-decoration: none;
}
.visit__rx img {
  width: 140px;
  height: 104px;
  object-fit: cover;
  object-position: top;
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-md);
}
.visit__rx span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.visit__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 18px;
  border-top: 1px solid var(--border);
  background: var(--surface-muted);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}
.visit__next {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  font-weight: 650;
  color: var(--primary-strong);
}
@media (max-width: 640px) {
  .visit__icon {
    display: none;
  }
  .visit__head {
    align-items: flex-start;
  }
  .visit__body {
    flex-direction: column;
    padding-left: 18px;
  }
}
</style>
