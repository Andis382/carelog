<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { PhCaretRight, PhPill, PhPlus, PhProhibit } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import HeroChip from '@/components/care/HeroChip.vue'
import ScheduleText from '@/components/care/ScheduleText.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import type { Medication } from '@/types/care'

const auth = useAuth()
const medications = ref<Medication[] | null>(null)
const failed = ref(false)

const active = computed(() => medications.value?.filter((m) => m.active) ?? [])
const stopped = computed(() => medications.value?.filter((m) => !m.active) ?? [])

async function load() {
  try {
    medications.value = await api.get<Medication[]>('/medications')
  } catch {
    failed.value = true
  }
}

onMounted(load)
</script>

<template>
  <AppPage :title="$t('meds.title')" :subtitle="$t('meds.subtitle')">
    <template v-if="medications?.length" #meta>
      <HeroChip :icon="PhPill">{{ $t('meds.activeCount', { n: active.length }, active.length) }}</HeroChip>
      <HeroChip v-if="stopped.length" :icon="PhProhibit">{{ $t('meds.stoppedCount', { n: stopped.length }, stopped.length) }}</HeroChip>
    </template>
    <template v-if="auth.canPlan" #actions>
      <UiButton variant="inverse" size="lg" :icon="PhPlus" :to="{ name: 'medication-new' }">{{ $t('meds.add') }}</UiButton>
    </template>

    <div v-if="!medications && !failed" class="grid-2">
      <UiSkeleton card :lines="4" />
      <UiSkeleton card :lines="4" />
    </div>

    <UiCard v-else-if="failed">
      <UiEmpty :icon="PhPill" :title="$t('errors.generic')">
        <UiButton variant="secondary" @click="load">{{ $t('common.retry') }}</UiButton>
      </UiEmpty>
    </UiCard>

    <template v-else-if="medications">
      <UiCard v-if="!active.length">
        <UiEmpty :icon="PhPill" :title="$t('meds.empty')" :text="$t('meds.emptyText')">
          <UiButton v-if="auth.canPlan" :icon="PhPlus" :to="{ name: 'medication-new' }">{{ $t('meds.add') }}</UiButton>
        </UiEmpty>
      </UiCard>

      <section v-else class="stack stack-sm">
        <div class="meds">
          <RouterLink v-for="m in active" :key="m.id" :to="{ name: 'medication', params: { id: m.id } }" class="med">
            <div class="med__main">
              <p class="med__name">{{ m.name }} <span v-if="m.strength" class="med__strength">{{ m.strength }}</span></p>
              <p v-if="m.doseText || m.instructions" class="med__how">{{ [m.doseText, m.instructions].filter(Boolean).join(' · ') }}</p>
              <ScheduleText :medication="m" class="med__schedule" />
              <p class="med__meta">
                {{ $t('meds.since', { date: formatDate(m.startDate) }) }}
                <template v-if="m.prescriber"> · {{ $t('meds.prescribedBy', { name: m.prescriber }) }}</template>
              </p>
            </div>
            <img v-if="m.boxPhotoUrl" :src="m.boxPhotoUrl" :alt="$t('meds.boxPhoto')" class="med__photo" loading="lazy" />
            <PhCaretRight class="med__go" :size="18" weight="bold" aria-hidden="true" />
          </RouterLink>
        </div>
      </section>

      <section v-if="stopped.length" class="stack stack-sm">
        <h2 class="section-title">{{ $t('meds.stopped') }} <span class="count num">{{ stopped.length }}</span></h2>
        <UiCard padding="sm">
          <RouterLink v-for="m in stopped" :key="m.id" :to="{ name: 'medication', params: { id: m.id } }" class="stopped">
            <PhProhibit :size="18" weight="bold" class="stopped__icon" aria-hidden="true" />
            <div class="stopped__body">
              <p class="strong">{{ m.label }}</p>
              <p class="small muted">
                {{ $t('meds.stoppedOn', { date: formatDate(m.endDate), name: m.stoppedByName ?? '' }) }}
                <template v-if="m.stopReason"> · “{{ m.stopReason }}”</template>
              </p>
            </div>
            <PhCaretRight :size="16" weight="bold" class="subtle" aria-hidden="true" />
          </RouterLink>
        </UiCard>
      </section>
    </template>
  </AppPage>
</template>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: var(--text-md);
}
.count {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--surface-sunken);
  color: var(--text-muted);
  font-size: var(--text-xs);
}
.meds {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 420px), 1fr));
}
.med {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 18px 18px 20px;
  color: inherit;
  text-decoration: none;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), var(--highlight);
  transition:
    transform var(--duration) var(--ease),
    box-shadow var(--duration) var(--ease),
    border-color var(--duration) var(--ease);
}
.med:hover {
  color: inherit;
  transform: translateY(-2px);
  border-color: var(--primary-soft-border);
  box-shadow: var(--shadow-lg), var(--highlight);
}
.med__main {
  flex: 1;
  min-width: 0;
}
.med__name {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 800;
  letter-spacing: -0.01em;
}
.med__strength {
  font-weight: 600;
  color: var(--text-muted);
}
.med__how {
  margin-top: 2px;
  font-size: var(--text-sm);
  color: var(--text-muted);
}
.med__schedule {
  margin-top: 12px;
}
.med__meta {
  margin-top: 12px;
  font-size: var(--text-xs);
  color: var(--text-subtle);
}
.med__photo {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}
.med__go {
  flex: none;
  color: var(--text-subtle);
}
.stopped {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid var(--border);
}
.stopped:last-child {
  border-bottom: 0;
}
.stopped:hover {
  color: inherit;
  background: var(--surface-hover);
}
.stopped__icon {
  flex: none;
  color: var(--text-subtle);
}
.stopped__body {
  flex: 1;
  min-width: 0;
}
</style>
