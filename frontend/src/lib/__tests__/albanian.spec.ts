import { describe, expect, it } from 'vitest'
import { albanianDate, albanianDateTime, albanianRelative, partsOf } from '../albanian'

describe('Albanian dates without browser locale data', () => {
  const parts = partsOf(new Date('2026-09-23T06:05:00Z'), 'Europe/Tirane')

  it('reads calendar parts in the circle time zone', () => {
    expect(parts).toEqual({ year: 2026, month: 8, day: 23, weekday: 3, hour: '08', minute: '05' })
  })

  it('writes the three date lengths the way Albanian does', () => {
    expect(albanianDate(parts, 'short')).toBe('23 sht')
    expect(albanianDate(parts, 'medium')).toBe('23 sht 2026')
    expect(albanianDate(parts, 'long')).toBe('e mërkurë, 23 shtator 2026')
    expect(albanianDateTime(parts)).toBe('23 sht, 08:05')
  })

  it('says how long ago or how soon, with the right case endings', () => {
    expect(albanianRelative(-3, 'hour')).toBe('3 orë më parë')
    expect(albanianRelative(-1, 'minute')).toBe('1 minutë më parë')
    expect(albanianRelative(-1, 'day')).toBe('dje')
    expect(albanianRelative(1, 'day')).toBe('nesër')
    expect(albanianRelative(10, 'day')).toBe('pas 10 ditësh')
    expect(albanianRelative(1, 'week')).toBe('pas 1 jave')
    expect(albanianRelative(-20, 'second')).toBe('tani')
  })
})
