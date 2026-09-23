import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/lib/api'
import type { Circle } from '@/types/care'

/** The circle as a whole: elder, members, plan, settings. Loaded once after sign-in. */
export const useCircle = defineStore('circle', () => {
  const data = ref<Circle | null>(null)
  let loading: Promise<void> | null = null

  const loaded = computed(() => data.value !== null)
  const elder = computed(() => data.value?.elder ?? null)
  const isFree = computed(() => data.value?.plan === 'FREE')

  function load(): Promise<void> {
    loading ??= api
      .get<Circle>('/circle')
      .then((c) => {
        data.value = c
      })
      .finally(() => {
        loading = null
      })
    return loading
  }

  function set(next: Circle) {
    data.value = next
  }

  function clear() {
    data.value = null
  }

  return { data, loaded, elder, isFree, load, set, clear }
})
