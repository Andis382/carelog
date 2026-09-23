import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import { stamp, WriteQueue, type QueuedWrite } from '../sync'

function memoryStore() {
  const data = new Map<string, string>()
  return {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => void data.set(k, v),
  }
}

function write(id: string, label = id): QueuedWrite {
  return { id, url: '/doses', body: { clientId: id }, label, queuedAt: '2026-09-23T08:00:00Z' }
}

describe('stamp', () => {
  it('adds a client id and the moment of the action', () => {
    const body = stamp({ status: 'GIVEN' }, new Date('2026-09-23T06:05:00Z'))
    expect(body.status).toBe('GIVEN')
    expect(body.at).toBe('2026-09-23T06:05:00.000Z')
    expect(typeof body.clientId).toBe('string')
    expect((body.clientId as string).length).toBeGreaterThan(10)
  })

  it('keeps what the caller already set, so a retry is the same write', () => {
    const body = stamp({ clientId: 'abc', at: '2026-09-23T06:00:00Z' })
    expect(body).toEqual({ clientId: 'abc', at: '2026-09-23T06:00:00Z' })
  })
})

describe('WriteQueue', () => {
  it('keeps writes in storage in the order they happened', () => {
    const store = memoryStore()
    new WriteQueue(store, 'q').add(write('a'))
    new WriteQueue(store, 'q').add(write('b'))
    expect(new WriteQueue(store, 'q').items().map((w) => w.id)).toEqual(['a', 'b'])
  })

  it('sends everything oldest first and empties itself', async () => {
    const queue = new WriteQueue(memoryStore(), 'q')
    queue.add(write('a'))
    queue.add(write('b'))
    const send = vi.fn().mockResolvedValue({})

    const result = await queue.flush(send)

    expect(send.mock.calls.map((c) => c[1].clientId)).toEqual(['a', 'b'])
    expect(result.sent).toHaveLength(2)
    expect(result.stopped).toBe(false)
    expect(queue.items()).toEqual([])
  })

  it('stops at the first network failure and keeps the rest for later', async () => {
    const queue = new WriteQueue(memoryStore(), 'q')
    queue.add(write('a'))
    queue.add(write('b'))
    queue.add(write('c'))
    const send = vi.fn().mockResolvedValueOnce({}).mockRejectedValueOnce(new ApiError(0, 'network'))

    const result = await queue.flush(send)

    expect(result.stopped).toBe(true)
    expect(send).toHaveBeenCalledTimes(2)
    expect(queue.items().map((w) => w.id)).toEqual(['b', 'c'])
  })

  it('turns a refusal into a problem to show, and carries on', async () => {
    const queue = new WriteQueue(memoryStore(), 'q')
    queue.add(write('a', 'Metformin 08:00'))
    queue.add(write('b'))
    const send = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(409, 'Already given by Mira at 08:05.'))
      .mockResolvedValueOnce({})

    const result = await queue.flush(send)

    expect(result.problems).toEqual([{ id: 'a', label: 'Metformin 08:00', message: 'Already given by Mira at 08:05.' }])
    expect(result.sent.map((w) => w.id)).toEqual(['b'])
    expect(queue.items()).toEqual([])
  })

  it('keeps a write when the server is having trouble', async () => {
    const queue = new WriteQueue(memoryStore(), 'q')
    queue.add(write('a'))

    const result = await queue.flush(vi.fn().mockRejectedValue(new ApiError(503, 'Unavailable')))

    expect(result.stopped).toBe(true)
    expect(queue.items()).toHaveLength(1)
  })

  it('survives storage that cannot be read', () => {
    const broken = {
      getItem: () => {
        throw new Error('blocked')
      },
      setItem: () => undefined,
    }
    expect(new WriteQueue(broken, 'q').items()).toEqual([])
  })
})
