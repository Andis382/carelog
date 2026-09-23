<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhArrowLeft, PhArrowRight, PhFloppyDisk, PhPlus, PhTrash } from '@phosphor-icons/vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiIconButton from '@/components/ui/UiIconButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiTextarea from '@/components/ui/UiTextarea.vue'
import UiPhotoInput from '@/components/ui/UiPhotoInput.vue'
import UiFormErrors from '@/components/ui/UiFormErrors.vue'
import { api } from '@/lib/api'
import { useForm } from '@/lib/form'
import { formatPhone } from '@/lib/format'
import type { Contact, Elder } from '@/types/care'

/**
 * The elder's profile. On the Circle page it is one form; in onboarding it is two short steps
 * (who she is, then health and contacts) so a worried child is not faced with twelve fields.
 */
const props = withDefaults(defineProps<{ elder: Elder | null; steps?: boolean; submitLabel?: string }>(), { steps: false, submitLabel: undefined })
const emit = defineEmits<{ saved: [elder: Elder]; step: [n: number] }>()
const { t } = useI18n()

const e = props.elder
const form = useForm({
  fullName: e?.fullName ?? '',
  birthYear: (e?.birthYear ?? null) as number | null,
  town: e?.town ?? '',
  address: e?.address ?? '',
  conditions: e?.conditions ?? '',
  allergies: e?.allergies ?? '',
  gpName: e?.gpName ?? '',
  gpPhone: e?.gpPhone ? formatPhone(e.gpPhone) : '',
  photoFileId: e?.photoFileId ?? null,
  contacts: (e?.contacts.length ? e.contacts : [{ name: '', relation: '', phone: '' }]).map((c: Contact) => ({
    name: c.name,
    relation: c.relation ?? '',
    phone: c.phone ? formatPhone(c.phone) : '',
  })),
})
const photo = ref<File | null>(null)
const step = ref(1)
const showAbout = computed(() => !props.steps || step.value === 1)
const showHealth = computed(() => !props.steps || step.value === 2)
watch(step, (n) => emit('step', n))

function addContact() {
  form.data.contacts = [...form.data.contacts, { name: '', relation: '', phone: '' }]
}

function removeContact(index: number) {
  form.data.contacts = form.data.contacts.filter((_, i) => i !== index)
}

function next() {
  if (!form.data.fullName.trim()) {
    form.errors.value = { fullName: [t('errors.required')] }
    form.submitted.value++
    return
  }
  form.errors.value = {}
  step.value = 2
}

async function save() {
  const saved = await form.submit(async () => {
    if (photo.value) {
      const upload = new FormData()
      upload.append('file', photo.value, photo.value.name || 'photo.jpg')
      form.data.photoFileId = (await api.upload<{ id: string }>('/uploads', upload)).id
    }
    return api.put<Elder>('/elder', {
      ...form.data,
      birthYear: Number(form.data.birthYear) || null,
      contacts: form.data.contacts.filter((c) => c.name.trim()),
    })
  })
  if (saved) {
    photo.value = null
    emit('saved', saved)
  } else if (props.steps && form.errors.value.fullName) {
    step.value = 1
  }
}
</script>

<template>
  <form class="stack stack-lg" novalidate @submit.prevent="steps && step === 1 ? next() : save()">
    <UiFormErrors :errors="form.errors.value" :message="form.message.value" :trigger="form.submitted.value" />

    <div v-if="showAbout" class="about">
      <div class="about__photo">
        <UiPhotoInput id="f-elder-photo" v-model="photo" :current-url="elder?.photoUrl ?? null" aspect="1 / 1" :hint="$t('circle.photoHint')" />
      </div>
      <div class="stack">
        <UiField id="f-fullName" :label="$t('circle.fullName')" :error="form.error('fullName')" required>
          <template #default="{ id, invalid, describedby }">
            <UiInput :id="id" v-model="form.data.fullName" size="lg" autocomplete="off" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
        <div class="grid-2">
          <UiField id="f-birthYear" :label="$t('circle.birthYear')" :error="form.error('birthYear')" optional>
            <template #default="{ id, invalid, describedby }">
              <UiInput :id="id" v-model.number="form.data.birthYear" inputmode="numeric" :invalid="invalid" :describedby="describedby" />
            </template>
          </UiField>
          <UiField id="f-town" :label="$t('circle.town')" :error="form.error('town')" optional>
            <template #default="{ id, invalid, describedby }">
              <UiInput :id="id" v-model="form.data.town" :invalid="invalid" :describedby="describedby" />
            </template>
          </UiField>
        </div>
        <UiField id="f-address" :label="$t('circle.address')" :error="form.error('address')" optional>
          <template #default="{ id, invalid, describedby }">
            <UiInput :id="id" v-model="form.data.address" autocomplete="street-address" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
      </div>
    </div>

    <div v-if="showHealth" class="stack">
      <div class="grid-2">
        <UiField id="f-conditions" :label="$t('circle.conditions')" :error="form.error('conditions')" optional>
          <template #default="{ id, invalid, describedby }">
            <UiTextarea :id="id" v-model="form.data.conditions" :rows="3" :placeholder="$t('circle.conditionsPlaceholder')" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
        <UiField id="f-allergies" :label="$t('circle.allergies')" :error="form.error('allergies')" optional>
          <template #default="{ id, invalid, describedby }">
            <UiTextarea :id="id" v-model="form.data.allergies" :rows="3" :placeholder="$t('circle.allergiesPlaceholder')" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
      </div>
      <div class="grid-2">
        <UiField id="f-gpName" :label="$t('circle.gp')" :error="form.error('gpName')" optional>
          <template #default="{ id, invalid, describedby }">
            <UiInput :id="id" v-model="form.data.gpName" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
        <UiField id="f-gpPhone" :label="$t('circle.gpPhone')" :error="form.error('gpPhone')" optional>
          <template #default="{ id, invalid, describedby }">
            <UiInput :id="id" v-model="form.data.gpPhone" type="tel" inputmode="tel" :invalid="invalid" :describedby="describedby" />
          </template>
        </UiField>
      </div>

      <fieldset class="contacts">
        <legend class="contacts__legend">{{ $t('circle.contacts') }}</legend>
        <div v-for="(c, i) in form.data.contacts" :key="i" class="contact">
          <UiField :id="`f-contact-name-${i}`" :label="$t('circle.contactName')">
            <template #default="{ id }"><UiInput :id="id" v-model="c.name" /></template>
          </UiField>
          <UiField :id="`f-contact-relation-${i}`" :label="$t('circle.contactRelation')">
            <template #default="{ id }"><UiInput :id="id" v-model="c.relation" /></template>
          </UiField>
          <UiField :id="`f-contact-phone-${i}`" :label="$t('circle.contactPhone')">
            <template #default="{ id }"><UiInput :id="id" v-model="c.phone" type="tel" inputmode="tel" /></template>
          </UiField>
          <UiIconButton class="contact__remove" :icon="PhTrash" :label="$t('circle.removeContact')" size="sm" @click="removeContact(i)" />
        </div>
        <div>
          <UiButton v-if="form.data.contacts.length < 5" variant="ghost" size="sm" :icon="PhPlus" @click="addContact">{{ $t('circle.addContact') }}</UiButton>
        </div>
      </fieldset>
    </div>

    <div class="actions">
      <UiButton v-if="steps && step === 2" variant="ghost" :icon="PhArrowLeft" @click="step = 1">{{ $t('common.back') }}</UiButton>
      <UiButton v-if="steps && step === 1" type="submit" size="lg" :icon-right="PhArrowRight">{{ $t('common.next') }}</UiButton>
      <UiButton v-else type="submit" size="lg" :icon="PhFloppyDisk" :loading="form.processing.value">{{ submitLabel ?? $t('common.save') }}</UiButton>
    </div>
  </form>
</template>

<style scoped>
.about {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}
@media (max-width: 700px) {
  .about {
    grid-template-columns: minmax(0, 1fr);
  }
  .about__photo {
    max-width: 220px;
  }
}
.contacts {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-muted);
}
.contacts__legend {
  padding: 0 6px;
  font-size: var(--text-sm);
  font-weight: 700;
}
.contact {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr) minmax(0, 3fr) auto;
  gap: 10px;
  align-items: end;
}
.contact__remove {
  margin-bottom: 4px;
}
@media (max-width: 700px) {
  .contact {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
    padding-bottom: 12px;
    border-bottom: 1px dashed var(--border-strong);
  }
  .contact > :nth-child(3) {
    grid-column: 1 / 3;
  }
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
