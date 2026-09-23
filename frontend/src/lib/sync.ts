import { ApiError } from './api'

/**
 * Offline tolerance for the daily card. A write that cannot reach the server waits in local
 * storage with the moment it happened (`at`) and a `clientId`; the server uses the first to log
 * the true time and the second to ignore a retry it has already seen.
 */

export type QueuedWrite = {
  id: string
  url: string
  body: Record<string, unknown>
  /** What the person did, shown while it waits ("Metformin 08:00 given"). */
  label: string
  queuedAt: string
}

/** A queued write the server refused when it finally arrived, e.g. someone else gave that dose. */
export type SyncProblem = { id: string; label: string; message: string }

export type FlushResult = { sent: QueuedWrite[]; problems: SyncProblem[]; stopped: boolean }

type KeyValueStore = Pick<Storage, 'getItem' | 'setItem'>

export function newClientId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // Plain-http LAN addresses have no randomUUID; getRandomValues is still there.
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/** Adds the idempotency key and the real time of the action, unless the caller set them. */
export function stamp(body: Record<string, unknown>, now: Date = new Date()): Record<string, unknown> {
  return { ...body, clientId: body.clientId ?? newClientId(), at: body.at ?? now.toISOString() }
}

export class WriteQueue {
  constructor(
    private readonly store: KeyValueStore,
    private readonly key: string,
  ) {}

  items(): QueuedWrite[] {
    try {
      const raw = this.store.getItem(this.key)
      return raw ? (JSON.parse(raw) as QueuedWrite[]) : []
    } catch {
      return []
    }
  }

  add(write: QueuedWrite) {
    this.save([...this.items(), write])
  }

  remove(id: string) {
    this.save(this.items().filter((w) => w.id !== id))
  }

  /**
   * Sends waiting writes oldest first. A network or server failure stops the run (the rest keep
   * their order for next time); a refusal (409, 422...) is final: it leaves the queue as a problem.
   */
  async flush(send: (url: string, body: Record<string, unknown>) => Promise<unknown>): Promise<FlushResult> {
    const sent: QueuedWrite[] = []
    const problems: SyncProblem[] = []
    for (const write of this.items()) {
      try {
        await send(write.url, write.body)
        this.remove(write.id)
        sent.push(write)
      } catch (e) {
        if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) {
          this.remove(write.id)
          problems.push({ id: write.id, label: write.label, message: e.message })
          continue
        }
        return { sent, problems, stopped: true }
      }
    }
    return { sent, problems, stopped: false }
  }

  private save(items: QueuedWrite[]) {
    try {
      this.store.setItem(this.key, JSON.stringify(items))
    } catch {
      /* storage full or blocked: the write stays in memory for this session only */
    }
  }
}
