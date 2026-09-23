<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhPackage, PhPlus, PhTrash } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiIconButton from '@/components/ui/UiIconButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiField from '@/components/ui/UiField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api, ApiError } from '@/lib/api'
import { useForm } from '@/lib/form'
import { formatRelative } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useConfirm } from '@/stores/confirm'
import { useToasts } from '@/stores/toasts'
import type { Supply, SupplyStatus } from '@/types/care'

const { t } = useI18n()
const auth = useAuth()
const toasts = useToasts()
const confirm = useConfirm()

const supplies = ref<Supply[] | null>(null)
const failed = ref(false)
const form = useForm({ name: '' })

const toBuy = computed(() => supplies.value?.filter((s) => s.status !== 'OK') ?? [])
const stocked = computed(() => supplies.value?.filter((s) => s.status === 'OK') ?? [])
const statuses = computed(() => (['OK', 'RUNNING_LOW', 'OUT'] as SupplyStatus[]).map((s) => ({ value: s, label: t(`supplies.status.${s}`) })))

async function load() {
  try {
    supplies.value = await api.get<Supply[]>('/supplies')
  } catch {
    failed.value = true
  }
}

async function add() {
  const created = await form.submit(() => api.post<Supply>('/supplies', { name: form.data.name }))
  if (created) {
    form.reset()
    toasts.success(created.name)
    load()
  }
}

async function mark(supply: Supply, status: SupplyStatus | null | undefined) {
  if (!status || status === supply.status) return
  const previous = supply.status
  supply.status = status
  try {
    const res = await api.put<{ supply: Supply; notified: string[] }>(`/supplies/${supply.id}/status`, { status })
    Object.assign(supply, res.supply)
    toasts.success(
      t('supplies.marked', { name: supply.name, status: t(`supplies.status.${status}`) }),
      res.notified.length ? t('supplies.notified', { names: res.notified.join(', ') }) : undefined,
    )
    load()
  } catch (e) {
    supply.status = previous
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  }
}

async function remove(supply: Supply) {
  if (!(await confirm.ask({ title: t('supplies.deleteTitle', { name: supply.name }), danger: true, confirmLabel: t('common.remove') }))) return
  try {
    await api.delete(`/supplies/${supply.id}`)
    toasts.success(t('supplies.removed'))
    load()
  } catch (e) {
    toasts.error(e instanceof ApiError ? e.message : t('errors.generic'))
  }
}

onMounted(load)
</script>

<template>
  <AppPage :title="$t('supplies.title')" :subtitle="$t('supplies.subtitle')">
    <UiCard v-if="auth.canRecord" padding="sm">
      <form class="add" novalidate @submit.prevent="add">
        <UiField id="f-supply" :label="$t('supplies.add')" :error="form.error('name')" class="add__field">
          <template #default="{ id, invalid, describedby }">
            <UiInput :id="id" v-model="form.data.name" :placeholder="$t('supplies.namePlaceholder')" :icon="PhPackage" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
        <UiButton type="submit" :icon="PhPlus" :loading="form.processing.value" :disabled="!form.data.name.trim()">{{ $t('common.add') }}</UiButton>
      </form>
    </UiCard>

    <UiSkeleton v-if="!supplies && !failed" card :lines="6" />
    <UiCard v-else-if="failed">
      <UiEmpty :icon="PhPackage" :title="$t('errors.generic')">
        <UiButton variant="secondary" @click="load">{{ $t('common.retry') }}</UiButton>
      </UiEmpty>
    </UiCard>
    <UiCard v-else-if="supplies && !supplies.length">
      <UiEmpty :icon="PhPackage" :title="$t('supplies.empty')" :text="$t('supplies.emptyText')" />
    </UiCard>

    <template v-else-if="supplies">
      <section v-for="group in [{ key: 'needBuying', items: toBuy }, { key: 'stocked', items: stocked }]" :key="group.key" class="stack stack-sm">
        <template v-if="group.items.length">
          <h2 class="group">{{ $t(`supplies.${group.key}`) }} <span class="count num">{{ group.items.length }}</span></h2>
          <div class="list">
            <article v-for="s in group.items" :key="s.id" class="supply" :class="`supply--${s.status.toLowerCase()}`">
              <div class="supply__body">
                <p class="supply__name">{{ s.name }}</p>
                <p class="xsmall subtle">{{ $t('supplies.updated', { name: s.updatedBy ?? '—', when: formatRelative(s.updatedAt) }) }}</p>
              </div>
              <UiSegmented
                v-if="auth.canRecord"
                class="supply__status"
                :model-value="s.status"
                :options="statuses"
                :label="s.name"
                @update:model-value="(v) => mark(s, v)"
              />
              <span v-else class="supply__badge">{{ $t(`supplies.status.${s.status}`) }}</span>
              <UiIconButton v-if="auth.canPlan" :icon="PhTrash" :label="$t('common.remove')" size="sm" @click="remove(s)" />
            </article>
          </div>
        </template>
      </section>
    </template>
  </AppPage>
</template>

<style scoped>
.add {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 4px;
}
.add__field {
  flex: 1;
}
.group {
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
.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.supply {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-left: 5px solid var(--success);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm), var(--highlight);
}
.supply--running_low {
  border-left-color: var(--warning);
  background: linear-gradient(90deg, var(--warning-soft), var(--surface) 45%);
}
.supply--out {
  border-left-color: var(--accent-500);
  background: linear-gradient(90deg, var(--accent-soft), var(--surface) 45%);
}
.supply__body {
  flex: 1;
  min-width: 0;
}
.supply__name {
  font-family: var(--font-display);
  font-weight: 750;
}
.supply__status {
  flex: none;
}
/* The chosen status wears its colour: sage for enough, amber for low, coral for out */
.supply--ok .supply__status :deep(.seg__opt.is-active) {
  color: var(--success-text);
  background: var(--success-soft);
}
.supply--running_low .supply__status :deep(.seg__opt.is-active) {
  color: var(--warning-text);
  background: var(--surface);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--warning) 35%, transparent),
    var(--shadow-sm);
}
.supply--out .supply__status :deep(.seg__opt.is-active) {
  color: var(--accent-soft-text);
  background: var(--surface);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--accent-500) 35%, transparent),
    var(--shadow-sm);
}
.supply__badge {
  font-size: var(--text-sm);
  font-weight: 650;
  color: var(--text-muted);
}
@media (max-width: 640px) {
  .supply {
    flex-wrap: wrap;
  }
  .supply__status {
    order: 3;
    flex: 1 1 100%;
  }
}
</style>
