import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api, ApiError } from '@/lib/api'
import { newClientId, stamp, WriteQueue, type QueuedWrite, type SyncProblem } from '@/lib/sync'

const RETRY_MS = 30_000

function storage(): Storage | { getItem: () => null; setItem: () => void } {
  try {
    return window.localStorage
  } catch {
    return { getItem: () => null, setItem: () => undefined }
  }
}

export type Recorded<T> = { queued: true } | { queued: false; result: T }

/** Reactive face of the offline queue: what is waiting, what failed, and whether we are online. */
export const useSync = defineStore('sync', () => {
  const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
  const pending = ref<QueuedWrite[]>([])
  const problems = ref<SyncProblem[]>([])
  const syncing = ref(false)
  const listeners = new Set<() => void>()
  let queue: WriteQueue | null = null
  let timer: ReturnType<typeof setInterval> | null = null

  const waiting = computed(() => pending.value.length)

  function goOnline() {
    online.value = true
    void flush()
  }

  function goOffline() {
    online.value = false
  }

  /** Called once the user is known: each person has their own queue on a shared phone. */
  function start(userId: number) {
    stop()
    queue = new WriteQueue(storage(), `carelog.queue.${userId}`)
    pending.value = queue.items()
    window.addEventListener('online', goOnline)
    window.addEventListener('offline', goOffline)
    timer = setInterval(() => {
      if (pending.value.length) void flush()
    }, RETRY_MS)
    if (pending.value.length) void flush()
  }

  function stop() {
    window.removeEventListener('online', goOnline)
    window.removeEventListener('offline', goOffline)
    if (timer) clearInterval(timer)
    timer = null
    queue = null
    pending.value = []
    problems.value = []
  }

  /** Posts now, or keeps it for later when there is no connection. Other errors reach the caller. */
  async function record<T>(url: string, body: Record<string, unknown>, label: string): Promise<Recorded<T>> {
    const stamped = stamp(body)
    if (!online.value && queue) {
      enqueue(url, stamped, label)
      return { queued: true }
    }
    try {
      return { queued: false, result: await api.post<T>(url, stamped) }
    } catch (e) {
      if (e instanceof ApiError && e.status === 0 && queue) {
        online.value = false
        enqueue(url, stamped, label)
        return { queued: true }
      }
      throw e
    }
  }

  function enqueue(url: string, body: Record<string, unknown>, label: string) {
    queue?.add({ id: newClientId(), url, body, label, queuedAt: new Date().toISOString() })
    pending.value = queue?.items() ?? []
  }

  async function flush() {
    if (!queue || syncing.value || !pending.value.length) return
    syncing.value = true
    try {
      const result = await queue.flush((url, body) => api.post(url, body))
      pending.value = queue.items()
      problems.value = [...problems.value, ...result.problems]
      online.value = !result.stopped
      if (result.sent.length || result.problems.length) listeners.forEach((fn) => fn())
    } finally {
      syncing.value = false
    }
  }

  function dismiss(id: string) {
    problems.value = problems.value.filter((p) => p.id !== id)
  }

  /** Lets a screen reload once queued writes have landed. Returns an unsubscribe function. */
  function onSynced(fn: () => void) {
    listeners.add(fn)
    return () => listeners.delete(fn)
  }

  return { online, pending, problems, syncing, waiting, start, stop, record, flush, dismiss, onSynced }
})
