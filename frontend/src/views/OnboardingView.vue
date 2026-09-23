<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BrandMark from '@/components/layout/BrandMark.vue'
import LanguageSwitch from '@/components/layout/LanguageSwitch.vue'
import ElderForm from '@/components/care/ElderForm.vue'
import { useAuth } from '@/stores/auth'
import { useCircle } from '@/stores/circle'
import { useToasts } from '@/stores/toasts'
import type { Elder } from '@/types/care'

/** First run after sign-up: who is the circle caring for? Two short steps, then the daily card. */
const { t } = useI18n()
const router = useRouter()
const auth = useAuth()
const circle = useCircle()
const toasts = useToasts()
const step = ref(1)

async function saved(elder: Elder) {
  await circle.load()
  if (circle.data) circle.data.elder = elder
  toasts.success(t('onboarding.done'))
  router.replace({ name: 'home' })
}
</script>

<template>
  <div class="welcome">
    <header class="welcome__band">
      <div class="welcome__bar">
        <BrandMark :size="34" />
        <LanguageSwitch inverse />
      </div>
      <div class="welcome__hero">
        <p class="welcome__eyebrow">{{ auth.organization?.name }} · {{ $t('onboarding.step', { n: step }) }}</p>
        <h1>{{ step === 1 ? $t('onboarding.aboutTitle') : $t('onboarding.healthTitle') }}</h1>
        <p class="welcome__text">{{ step === 1 ? $t('onboarding.aboutText') : $t('onboarding.healthText') }}</p>
        <div class="welcome__steps" aria-hidden="true">
          <span :class="{ 'is-on': step >= 1 }" />
          <span :class="{ 'is-on': step >= 2 }" />
        </div>
      </div>
    </header>
    <main class="welcome__card">
      <ElderForm :elder="null" steps :submit-label="$t('onboarding.finish')" @saved="saved" @step="step = $event" />
    </main>
  </div>
</template>

<style scoped>
.welcome {
  min-height: 100dvh;
  padding-bottom: 48px;
}
.welcome__band {
  padding: 0 var(--gutter) 88px;
  color: var(--header-text);
  background:
    radial-gradient(900px 320px at 12% -10%, var(--header-glow), transparent 65%),
    radial-gradient(640px 260px at 92% 115%, var(--header-warm), transparent 62%),
    linear-gradient(120deg, var(--header-from) 0%, var(--header-via) 55%, var(--header-to) 100%);
}
.welcome__bar,
.welcome__hero {
  max-width: 860px;
  margin: 0 auto;
}
.welcome__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 68px;
}
.welcome__hero {
  padding-top: 20px;
}
.welcome__hero h1 {
  color: var(--header-text);
  font-size: clamp(1.7rem, 1.2rem + 1.6vw, 2.4rem);
  font-weight: 800;
  letter-spacing: -0.03em;
}
.welcome__eyebrow {
  margin-bottom: 8px;
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-inverse-muted);
}
.welcome__text {
  margin-top: 8px;
  color: var(--text-inverse-muted);
  max-width: 56ch;
}
.welcome__steps {
  display: flex;
  gap: 6px;
  margin-top: 18px;
}
.welcome__steps span {
  width: 42px;
  height: 5px;
  border-radius: var(--radius-pill);
  background: rgb(255 255 255 / 0.2);
}
.welcome__steps span.is-on {
  background: var(--accent-400);
}
.welcome__card {
  max-width: 860px;
  margin: -56px auto 0;
  padding: 28px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg), var(--highlight);
}
@media (max-width: 900px) {
  .welcome__card {
    margin-inline: var(--gutter);
    padding: 20px;
  }
}
</style>
