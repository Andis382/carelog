<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { PhFloppyDisk } from '@phosphor-icons/vue'
import AppPage from '@/components/layout/AppPage.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import UiPhotoInput from '@/components/ui/UiPhotoInput.vue'
import UiFormErrors from '@/components/ui/UiFormErrors.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { api } from '@/lib/api'
import { useForm } from '@/lib/form'
import { isoDate } from '@/lib/care'
import { useToasts } from '@/stores/toasts'
import type { Visit } from '@/types/care'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toasts = useToasts()

const editing = computed(() => route.name === 'visit-edit')
const id = computed(() => Number(route.params.id))
const ready = ref(!editing.value)
const photo = ref<File | null>(null)
const photoUrl = ref<string | null>(null)

const form = useForm({
  date: isoDate(new Date()),
  doctorName: '',
  specialty: '',
  place: '',
  notes: '',
  nextDate: '',
  nextTime: '',
  prescriptionFileId: null as string | null,
})

onMounted(async () => {
  if (!editing.value) return
  const visit = (await api.get<Visit[]>('/visits')).find((v) => v.id === id.value)
  if (!visit) {
    router.replace({ name: 'visits' })
    return
  }
  form.fill({
    date: visit.date,
    doctorName: visit.doctorName,
    specialty: visit.specialty ?? '',
    place: visit.place ?? '',
    notes: visit.notes ?? '',
    nextDate: visit.nextDate ?? '',
    nextTime: visit.nextTime ?? '',
    prescriptionFileId: visit.prescriptionFileId,
  })
  photoUrl.value = visit.prescriptionUrl
  ready.value = true
})

async function save() {
  const saved = await form.submit(async () => {
    if (photo.value) {
      const upload = new FormData()
      upload.append('file', photo.value, photo.value.name || 'prescription.jpg')
      form.data.prescriptionFileId = (await api.upload<{ id: string }>('/uploads', upload)).id
    }
    const body = { ...form.data, nextDate: form.data.nextDate || null, nextTime: form.data.nextTime || null }
    return editing.value ? api.put<Visit>(`/visits/${id.value}`, body) : api.post<Visit>('/visits', body)
  })
  if (saved) {
    toasts.success(t('visits.saved'))
    router.replace({ name: 'visits' })
  }
}
</script>

<template>
  <AppPage :title="editing ? $t('visits.edit') : $t('visits.add')" :subtitle="$t('visits.subtitle')" :back="{ name: 'visits' }" :back-label="$t('visits.title')">
    <UiSkeleton v-if="!ready" card :lines="8" />
    <form v-else class="layout" novalidate @submit.prevent="save">
      <UiCard>
        <div class="stack">
          <UiFormErrors :errors="form.errors.value" :message="form.message.value" :trigger="form.submitted.value" />
          <div class="grid-2">
            <UiField id="f-visit-date" :label="$t('visits.date')" :error="form.error('date')" required>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.date" type="date" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
            <UiField id="f-visit-doctor" :label="$t('visits.doctor')" :error="form.error('doctorName')" required>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.doctorName" :placeholder="$t('visits.doctorPlaceholder')" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
          </div>
          <div class="grid-2">
            <UiField id="f-visit-specialty" :label="$t('visits.specialty')" :error="form.error('specialty')" optional>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.specialty" :placeholder="$t('visits.specialtyPlaceholder')" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
            <UiField id="f-visit-place" :label="$t('visits.place')" :error="form.error('place')" optional>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.place" :placeholder="$t('visits.placePlaceholder')" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
          </div>
          <UiField id="f-visit-notes" :label="$t('visits.notes')" :error="form.error('notes')" optional>
            <template #default="{ id: fid, invalid, describedby }">
              <UiTextarea :id="fid" v-model="form.data.notes" :rows="5" :placeholder="$t('visits.notesPlaceholder')" :invalid="invalid" :describedby="describedby" />
            </template>
          </UiField>
          <div class="grid-2">
            <UiField id="f-visit-next" :label="$t('visits.nextDate')" :error="form.error('nextDate')" optional>
              <template #default="{ id: fid, invalid, describedby }">
                <UiInput :id="fid" v-model="form.data.nextDate" type="date" :invalid="invalid" :describedby="describedby" />
              </template>
            </UiField>
            <UiField id="f-visit-next-time" :label="$t('visits.nextTime')" optional>
              <template #default="{ id: fid }">
                <UiInput :id="fid" v-model="form.data.nextTime" type="time" :disabled="!form.data.nextDate" />
              </template>
            </UiField>
          </div>
        </div>
      </UiCard>
      <div class="side">
        <UiCard :title="$t('visits.prescription')">
          <UiPhotoInput id="f-visit-photo" v-model="photo" :current-url="photoUrl" aspect="3 / 4" />
        </UiCard>
        <UiButton type="submit" size="lg" block :icon="PhFloppyDisk" :loading="form.processing.value">{{ $t('common.save') }}</UiButton>
      </div>
    </form>
  </AppPage>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
  align-items: start;
}
.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
@media (max-width: 960px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
