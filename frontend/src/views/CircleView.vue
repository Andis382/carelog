<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  PhCheck,
  PhCrown,
  PhGearSix,
  PhHeart,
  PhLinkSimple,
  PhSliders,
  PhTrash,
  PhUserCircle,
  PhUsersThree,
  PhWhatsappLogo,
} from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import ElderForm from '@/components/care/ElderForm.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiIconButton from '@/components/ui/UiIconButton.vue'
import UiAvatar from '@/components/ui/UiAvatar.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiCopy from '@/components/ui/UiCopy.vue'
import UiField from '@/components/ui/UiField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiSwitch from '@/components/ui/UiSwitch.vue'
import UiTabs from '@/components/ui/UiTabs.vue'
import UiNotice from '@/components/ui/UiNotice.vue'
import UiFormErrors from '@/components/ui/UiFormErrors.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api, ApiError } from '@/lib/api'
import { useForm } from '@/lib/form'
import { formatPhone, formatRelative } from '@/lib/format'
import { useAuth, type Me, type Organization } from '@/stores/auth'
import { useCircle } from '@/stores/circle'
import { useConfirm } from '@/stores/confirm'
import { useToasts } from '@/stores/toasts'
import { setLocale, type Locale } from '@/i18n'
import { INVITE_ROLES } from '@/config'
import type { Circle, Elder, Member, Plan, VitalKind } from '@/types/care'

type Tab = 'elder' | 'people' | 'plan' | 'settings'
type Pending = { id: number; name: string | null; role: string; url: string; expiresAt: string }

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const circle = useCircle()
const toasts = useToasts()
const confirm = useConfirm()

const tab = ref<Tab>((['elder', 'people', 'plan', 'settings'] as Tab[]).includes(route.query.tab as Tab) ? (route.query.tab as Tab) : 'elder')
const data = computed(() => circle.data)
const pending = ref<Pending[]>([])
const lastLink = ref<Pending | null>(null)

const tabs = computed(() => [
  { value: 'elder' as const, label: t('circle.tabs.elder', { name: data.value?.elder?.firstName ?? '…' }), icon: PhHeart },
  { value: 'people' as const, label: t('circle.tabs.people'), icon: PhUsersThree, count: data.value?.members.length ?? null },
  { value: 'plan' as const, label: t('circle.tabs.plan'), icon: PhCrown },
  { value: 'settings' as const, label: t('circle.tabs.settings'), icon: PhGearSix },
])

watch(tab, (value) => router.replace({ query: { tab: value } }))

async function refresh() {
  circle.set(await api.get<Circle>('/circle'))
}

async function loadInvites() {
  if (!auth.isOwner) return
  pending.value = (await api.get<{ invitations: Pending[] }>('/team')).invitations
}

onMounted(async () => {
  await Promise.all([refresh(), loadInvites()])
  fillSettings()
})

// ------------------------------------------------------------ elder

function elderSaved(elder: Elder) {
  if (circle.data) circle.data.elder = elder
  toasts.success(t('circle.elderSaved'))
}

// ------------------------------------------------------------ people

const invite = useForm({ name: '', role: 'FAMILY' })
const roleOptions = computed(() => INVITE_ROLES.map((r) => ({ value: r, label: t(`roles.${r}`) })))
const full = computed(() => !!data.value?.maxMembers && data.value.members.length + data.value.openInvitations >= data.value.maxMembers)

async function createInvite() {
  const created = await invite.submit(() => api.post<Pending>('/team/invitations', invite.data))
  if (created) {
    lastLink.value = created
    invite.reset()
    toasts.success(t('circle.linkCreated'))
    await Promise.all([loadInvites(), refresh()])
  }
}

/** wa.me without a number lets the coordinator pick the chat on her own phone. */
function shareLink(p: Pending) {
  return `https://wa.me/?text=${encodeURIComponent(t('circle.inviteMessage', { circle: data.value?.name ?? '', link: p.url }))}`
}

async function revoke(p: Pending) {
  await api.delete(`/team/invitations/${p.id}`)
  if (lastLink.value?.id === p.id) lastLink.value = null
  await Promise.all([loadInvites(), refresh()])
}

async function removeMember(m: Member) {
  const ok = await confirm.ask({ title: t('circle.removeMember', { name: m.name }), text: t('circle.removeMemberText'), danger: true, confirmLabel: t('common.remove') })
  if (!ok) return
  try {
    await api.delete(`/team/members/${m.id}`)
    toasts.success(t('circle.removed', { name: m.name }))
    await refresh()
  } catch (e) {
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  }
}

// ------------------------------------------------------------ plan

const payer = ref<number | null>(null)
const planBusy = ref(false)
const payerOptions = computed(() => (data.value?.members ?? []).map((m) => ({ value: m.id, label: m.name })))

watch(
  () => data.value?.payerUserId,
  (id) => {
    payer.value = id ?? null
  },
  { immediate: true },
)

async function setPlan(plan: Plan, payerId = payer.value) {
  if (!payerId) return
  const planChanged = plan !== data.value?.plan
  planBusy.value = true
  try {
    circle.set(await api.put<Circle>('/circle/plan', { plan, payerUserId: payerId }))
    toasts.success(planChanged ? t('circle.switched', { plan: t(`circle.plan${plan === 'FREE' ? 'Free' : 'Family'}`) }) : t('circle.payerSaved'))
  } catch (e) {
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  } finally {
    planBusy.value = false
  }
}

async function savePayer() {
  if (data.value && payer.value && payer.value !== data.value.payerUserId) await setPlan(data.value.plan, payer.value)
}

// ------------------------------------------------------------ settings

type RangeRow = { kind: VitalKind; low: string; high: string; low2: string; high2: string }
const care = useForm({ graceMinutes: 30, doseAlerts: false, weeklySummary: true, ranges: [] as RangeRow[] })
const graceOptions = computed(() => [15, 30, 45, 60, 90].map((n) => ({ value: n, label: t('circle.graceMinutes', { n }) })))
const kindsWithRange: VitalKind[] = ['BP', 'SUGAR', 'TEMP', 'PULSE', 'SPO2']

function fillSettings() {
  const c = data.value
  if (!c) return
  const text = (n: number | null) => (n === null ? '' : String(n))
  care.fill({
    graceMinutes: c.graceMinutes,
    doseAlerts: c.doseAlerts,
    weeklySummary: c.weeklySummary,
    ranges: kindsWithRange.map((kind) => {
      const r = c.ranges.find((x) => x.kind === kind)
      return { kind, low: text(r?.low ?? null), high: text(r?.high ?? null), low2: text(r?.low2 ?? null), high2: text(r?.high2 ?? null) }
    }),
  })
}

async function saveCare() {
  const num = (s: string) => (s.trim() === '' ? null : Number(s.replace(',', '.')))
  const saved = await care.submit(() =>
    api.put<Circle>('/circle/settings', {
      graceMinutes: care.data.graceMinutes,
      doseAlerts: care.data.doseAlerts,
      weeklySummary: care.data.weeklySummary,
      ranges: care.data.ranges.map((r) => ({ kind: r.kind, low: num(r.low), high: num(r.high), low2: num(r.low2), high2: num(r.high2) })),
    }),
  )
  if (saved) {
    circle.set(saved)
    toasts.success(t('circle.settingsSaved'))
  }
}

const profile = useForm({ name: auth.user?.name ?? '', phone: auth.user?.phone ? formatPhone(auth.user.phone) : '', locale: (auth.user?.locale ?? 'sq') as Locale })
const localeOptions = [
  { value: 'sq' as Locale, label: 'Shqip' },
  { value: 'en' as Locale, label: 'English' },
]

async function saveProfile() {
  const me = await profile.submit(() => api.put<Me>('/auth/me', profile.data))
  if (me) {
    auth.apply(me)
    setLocale(profile.data.locale)
    toasts.success(t('settings.saved'))
    refresh()
  }
}

const org = useForm({ name: auth.organization?.name ?? '', timezone: auth.organization?.timezone ?? 'Europe/Tirane' })
const zones = ['Europe/Tirane', 'Europe/Belgrade', 'Europe/Rome', 'Europe/Athens', 'Europe/Berlin', 'Europe/London', 'Europe/Istanbul'].map((z) => ({ value: z, label: z.replace('_', ' ') }))

async function saveOrg() {
  const o = auth.organization
  if (!o) return
  const saved = await org.submit(() => api.put<Organization>('/organization', { ...org.data, phone: o.phone, locale: o.locale, currency: o.currency }))
  if (saved) {
    auth.organization = saved
    toasts.success(t('settings.saved'))
    refresh()
  }
}
</script>

<template>
  <AppPage :title="$t('circle.title')" :subtitle="$t('circle.subtitle', { name: data?.elder?.firstName ?? data?.name ?? '' })">
    <div class="tabbar-card">
      <UiTabs v-model="tab" :tabs="tabs" :label="$t('circle.title')" class="tabs" />
    </div>

    <UiSkeleton v-if="!data" card :lines="8" />

    <template v-else>
      <!-- About her -->
      <UiCard v-if="tab === 'elder'" :title="$t('circle.elderTitle')" :subtitle="$t('circle.elderHint')" :icon="PhHeart" padding="lg">
        <ElderForm v-if="auth.canPlan" :key="data.elder?.fullName ?? 'new'" :elder="data.elder" @saved="elderSaved" />
        <div v-else-if="data.elder" class="stack">
          <p class="strong">{{ data.elder.fullName }}</p>
          <p class="muted">{{ [data.elder.town, data.elder.address].filter(Boolean).join(' · ') }}</p>
          <p v-if="data.elder.conditions">{{ data.elder.conditions }}</p>
          <p v-if="data.elder.allergies"><strong>{{ $t('circle.allergies') }}:</strong> {{ data.elder.allergies }}</p>
          <p v-for="c in data.elder.contacts" :key="c.name" class="small">
            {{ c.name }} · {{ c.relation }} · <a v-if="c.phone" :href="`tel:+${c.phone}`">{{ formatPhone(c.phone) }}</a>
          </p>
        </div>
      </UiCard>

      <!-- People -->
      <template v-else-if="tab === 'people'">
        <UiCard :title="$t('circle.peopleTitle')" :icon="PhUsersThree">
          <ul class="people">
            <li v-for="m in data.members" :key="m.id" class="person">
              <UiAvatar :name="m.name" :size="44" />
              <div class="person__who">
                <p class="strong">
                  {{ m.name }} <span v-if="m.you" class="subtle small">({{ $t('common.you') }})</span>
                </p>
                <p class="small muted truncate">
                  <span class="num">{{ m.phone ? formatPhone(m.phone) : $t('circle.noPhone') }}</span> · {{ m.email }}
                </p>
                <p class="xsmall subtle">{{ m.lastLoginAt ? $t('circle.lastSeen', { when: formatRelative(m.lastLoginAt) }) : $t('circle.neverSeen') }}</p>
              </div>
              <div class="person__tags">
                <UiBadge :tone="m.role === 'OWNER' ? 'primary' : m.role === 'CARER' ? 'accent' : 'neutral'">{{ $t(`roles.${m.role}`) }}</UiBadge>
                <UiBadge v-if="m.payer" tone="success" size="sm">{{ $t('circle.payer') }}</UiBadge>
              </div>
              <UiIconButton v-if="auth.isOwner && !m.you" :icon="PhTrash" :label="$t('common.remove')" size="sm" @click="removeMember(m)" />
            </li>
          </ul>
        </UiCard>

        <UiCard v-if="auth.isOwner" :title="$t('circle.invite')" :subtitle="$t('circle.inviteHint')" :icon="PhLinkSimple">
          <div class="stack">
            <UiNotice v-if="full" tone="info">
              {{ $t('circle.seatsFree', { max: data.maxMembers }) }}
              <template #actions>
                <UiButton size="sm" @click="tab = 'plan'">{{ $t('log.limitAction') }}</UiButton>
              </template>
            </UiNotice>
            <form v-else class="stack" novalidate @submit.prevent="createInvite">
              <UiFormErrors :errors="invite.errors.value" :message="invite.message.value" :trigger="invite.submitted.value" />
              <div class="invite">
                <UiField id="f-invite-name" :label="$t('circle.inviteName')" optional class="invite__name">
                  <template #default="{ id }"><UiInput :id="id" v-model="invite.data.name" /></template>
                </UiField>
                <UiField id="f-invite-role" :label="$t('circle.inviteRole')" class="invite__role">
                  <template #default="{ id }"><UiSelect :id="id" v-model="invite.data.role" :options="roleOptions" /></template>
                </UiField>
                <UiButton type="submit" variant="secondary" :icon="PhLinkSimple" :loading="invite.processing.value">{{ $t('circle.createLink') }}</UiButton>
              </div>
              <p class="small muted">{{ $t(`roleHints.${invite.data.role}`) }}</p>
            </form>

            <div v-if="pending.length" class="stack stack-sm">
              <p class="eyebrow">{{ $t('circle.pending') }}</p>
              <div v-for="p in pending" :key="p.id" class="pending" :class="{ 'pending--new': lastLink?.id === p.id }">
                <div class="pending__head">
                  <span class="strong">{{ p.name || $t(`roles.${p.role}`) }}</span>
                  <UiBadge size="sm">{{ $t(`roles.${p.role}`) }}</UiBadge>
                  <UiButton variant="ghost" size="sm" @click="revoke(p)">{{ $t('circle.revoke') }}</UiButton>
                </div>
                <UiCopy :value="p.url" />
                <UiButton variant="soft" size="sm" :icon="PhWhatsappLogo" :href="shareLink(p)" target="_blank">
                  {{ $t('circle.shareWhatsApp') }}
                </UiButton>
              </div>
            </div>
          </div>
        </UiCard>
      </template>

      <!-- Plan -->
      <template v-else-if="tab === 'plan'">
        <div class="plans">
          <article v-for="plan in ['FREE', 'FAMILY'] as Plan[]" :key="plan" class="plan" :class="{ 'plan--current': data.plan === plan, 'plan--family': plan === 'FAMILY' }">
            <p class="plan__name">{{ $t(`circle.plan${plan === 'FREE' ? 'Free' : 'Family'}`) }}</p>
            <p class="plan__price num">{{ $t(`circle.plan${plan === 'FREE' ? 'Free' : 'Family'}Price`) }}</p>
            <ul class="plan__features">
              <li v-for="f in ['members', 'history', 'core']" :key="f">
                <PhCheck :size="16" weight="bold" aria-hidden="true" /> {{ $t(`circle.plan${plan === 'FREE' ? 'Free' : 'Family'}Features.${f}`) }}
              </li>
            </ul>
            <UiBadge v-if="data.plan === plan" tone="primary">{{ $t('circle.currentPlan') }}</UiBadge>
            <template v-else-if="auth.isOwner">
              <UiButton
                :variant="plan === 'FAMILY' ? 'primary' : 'secondary'"
                :loading="planBusy"
                :disabled="plan === 'FREE' && data.members.length > 2"
                @click="setPlan(plan)"
              >
                {{ $t('circle.switchTo', { plan: $t(`circle.plan${plan === 'FREE' ? 'Free' : 'Family'}`) }) }}
              </UiButton>
              <p v-if="plan === 'FREE' && data.members.length > 2" class="xsmall muted">{{ $t('circle.freeTooMany') }}</p>
            </template>
          </article>
        </div>
        <UiCard :title="$t('circle.payerTitle')" :icon="PhCrown">
          <div class="payer">
            <UiField id="f-payer" :label="$t('circle.payerLabel')" class="payer__field">
              <template #default="{ id }">
                <UiSelect :id="id" v-model="payer" :options="payerOptions" :disabled="!auth.isOwner" />
              </template>
            </UiField>
            <UiButton v-if="auth.isOwner" :disabled="payer === data.payerUserId" :loading="planBusy" @click="savePayer">{{ $t('common.save') }}</UiButton>
          </div>
          <p class="small muted demo-note">{{ $t('circle.demoBilling') }}</p>
        </UiCard>
      </template>

      <!-- Settings -->
      <template v-else>
        <UiCard v-if="auth.isOwner" :title="$t('circle.careTitle')" :icon="PhSliders">
          <form class="stack" novalidate @submit.prevent="saveCare">
            <UiFormErrors :errors="care.errors.value" :message="care.message.value" :trigger="care.submitted.value" />
            <UiField id="f-grace" :label="$t('circle.grace')">
              <div><UiSegmented v-model="care.data.graceMinutes" :options="graceOptions" :label="$t('circle.grace')" /></div>
            </UiField>
            <UiSwitch id="f-alerts" v-model="care.data.doseAlerts" :label="$t('circle.alerts')" :hint="$t('circle.alertsHint')" />
            <UiSwitch id="f-weekly" v-model="care.data.weeklySummary" :label="$t('circle.weekly')" />
            <div class="ranges">
              <p class="strong">{{ $t('circle.rangesTitle') }}</p>
              <p class="small muted">{{ $t('circle.rangesHint') }}</p>
              <div v-for="r in care.data.ranges" :key="r.kind" class="range">
                <span class="range__kind">{{ $t(`vitals.kinds.${r.kind}`) }} <span class="xsmall subtle">{{ $t(`vitals.units.${r.kind}`) }}</span></span>
                <div class="range__inputs">
                  <UiInput :id="`f-range-${r.kind}-low`" v-model="r.low" inputmode="decimal" :aria-label="`${$t(`vitals.kinds.${r.kind}`)} ${$t('circle.low')}`" />
                  <span aria-hidden="true">–</span>
                  <UiInput :id="`f-range-${r.kind}-high`" v-model="r.high" inputmode="decimal" :aria-label="`${$t(`vitals.kinds.${r.kind}`)} ${$t('circle.high')}`" />
                  <template v-if="r.kind === 'BP'">
                    <span class="range__slash" aria-hidden="true">/</span>
                    <UiInput id="f-range-BP-low2" v-model="r.low2" inputmode="decimal" :aria-label="`${$t('vitals.diastolic')} ${$t('circle.low')}`" />
                    <span aria-hidden="true">–</span>
                    <UiInput id="f-range-BP-high2" v-model="r.high2" inputmode="decimal" :aria-label="`${$t('vitals.diastolic')} ${$t('circle.high')}`" />
                  </template>
                </div>
              </div>
            </div>
            <div><UiButton type="submit" :loading="care.processing.value">{{ $t('common.save') }}</UiButton></div>
          </form>
        </UiCard>

        <div class="grid-2">
          <UiCard :title="$t('circle.profileTitle')" :icon="PhUserCircle">
            <form class="stack" novalidate @submit.prevent="saveProfile">
              <UiFormErrors :errors="profile.errors.value" :trigger="profile.submitted.value" />
              <UiField id="f-name" :label="$t('common.name')" :error="profile.error('name')">
                <template #default="{ id, invalid, describedby }">
                  <UiInput :id="id" v-model="profile.data.name" autocomplete="name" :invalid="invalid" :describedby="describedby" />
                </template>
              </UiField>
              <UiField id="f-phone" :label="$t('common.phone')" :hint="$t('circle.phoneHint')" :error="profile.error('phone')">
                <template #default="{ id, invalid, describedby }">
                  <UiInput :id="id" v-model="profile.data.phone" type="tel" inputmode="tel" autocomplete="tel" :invalid="invalid" :describedby="describedby" />
                </template>
              </UiField>
              <UiField id="f-locale" :label="$t('common.language')" :hint="$t('circle.languageHint')">
                <UiSegmented v-model="profile.data.locale" :options="localeOptions" :label="$t('common.language')" />
              </UiField>
              <div><UiButton type="submit" :loading="profile.processing.value">{{ $t('common.save') }}</UiButton></div>
            </form>
          </UiCard>

          <UiCard :title="$t('circle.circleTitle')" :icon="PhUsersThree">
            <form class="stack" novalidate @submit.prevent="saveOrg">
              <UiFormErrors :errors="org.errors.value" :trigger="org.submitted.value" />
              <UiField id="f-org-name" :label="$t('circle.circleName')" :error="org.error('name')">
                <template #default="{ id, invalid, describedby }">
                  <UiInput :id="id" v-model="org.data.name" :invalid="invalid" :describedby="describedby" :disabled="!auth.isOwner" />
                </template>
              </UiField>
              <UiField id="f-org-tz" :label="$t('circle.timezone')" :hint="$t('circle.timezoneHint')">
                <template #default="{ id }">
                  <UiSelect :id="id" v-model="org.data.timezone" :options="zones" :disabled="!auth.isOwner" />
                </template>
              </UiField>
              <div v-if="auth.isOwner"><UiButton type="submit" :loading="org.processing.value">{{ $t('common.save') }}</UiButton></div>
            </form>
          </UiCard>
        </div>
      </template>
    </template>
  </AppPage>
</template>

<style scoped>
.tabbar-card {
  padding: 8px 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), var(--highlight);
}
.tabs {
  overflow-x: auto;
  scrollbar-width: none;
}
.people {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 440px), 1fr));
  margin: 0;
  padding: 0;
  list-style: none;
}
.person {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: var(--surface-muted);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.person__who {
  flex: 1;
  min-width: 0;
}
.person__tags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.invite {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 12px;
}
.invite__name,
.invite__role {
  flex: 1 1 220px;
}
.pending {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
}
.pending--new {
  border-style: solid;
  border-color: var(--primary-soft-border);
  background: var(--primary-soft);
}
.pending__head {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.pending__head .btn {
  margin-left: auto;
}
.pending :deep(.copy) {
  width: 100%;
}
.plans {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
@media (max-width: 760px) {
  .plans {
    grid-template-columns: minmax(0, 1fr);
  }
}
.plan {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), var(--highlight);
}
.plan--family {
  background: linear-gradient(160deg, var(--primary-soft), var(--surface) 60%);
  border-color: var(--primary-soft-border);
}
.plan--current {
  box-shadow:
    0 0 0 2px var(--primary),
    var(--shadow-lg);
}
.plan__name {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 800;
}
.plan__price {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 800;
  color: var(--primary-strong);
}
.plan__features {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 4px 0 8px;
  padding: 0;
  list-style: none;
}
.plan__features li {
  display: flex;
  align-items: center;
  gap: 8px;
}
.plan__features :deep(svg) {
  color: var(--success);
}
.payer {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}
.payer__field {
  flex: 1;
  max-width: 420px;
}
.demo-note {
  margin-top: 12px;
}
.ranges {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-muted);
}
.range {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}
.range__kind {
  font-weight: 650;
}
.range__inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}
.range__inputs :deep(.control) {
  max-width: 96px;
}
.range__slash {
  margin: 0 6px;
  color: var(--text-subtle);
}
@media (max-width: 640px) {
  .range {
    grid-template-columns: minmax(0, 1fr);
    gap: 4px;
  }
  .range__inputs {
    flex-wrap: wrap;
  }
}
</style>
