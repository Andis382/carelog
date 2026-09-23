import { describe, expect, it } from 'vitest'
import {
  addDays,
  daysBetween,
  doseTone,
  formatVital,
  groupByTime,
  mondayOf,
  position,
  progress,
  rangeLabel,
  splitMinutes,
  weekDates,
} from '../care'
import type { DoseLine, VitalRange } from '@/types/care'

const fmt = (n: number, digits: number) => n.toFixed(digits)

function line(time: string, name: string, state: DoseLine['state']): DoseLine {
  return {
    medicationId: 1,
    name,
    strength: null,
    doseText: null,
    instructions: null,
    time,
    state,
    givenLate: false,
    minutesLate: 0,
    event: null,
  }
}

describe('dose timeline helpers', () => {
  it('groups doses that share a time', () => {
    const groups = groupByTime([line('08:00', 'Amlodipine', 'GIVEN'), line('08:00', 'Metformin', 'DUE'), line('20:00', 'Metformin', 'UPCOMING')])
    expect(groups.map((g) => [g.time, g.lines.length])).toEqual([
      ['08:00', 2],
      ['20:00', 1],
    ])
  })

  it('counts answered doses, whatever the answer', () => {
    const lines = [line('08:00', 'A', 'GIVEN'), line('08:00', 'B', 'REFUSED'), line('13:00', 'C', 'LATE'), line('20:00', 'D', 'UPCOMING')]
    expect(progress(lines)).toEqual({ done: 2, total: 4 })
  })

  it('gives each state a calm, distinct tone', () => {
    expect(doseTone('GIVEN')).toBe('success')
    expect(doseTone('LATE')).toBe('warning')
    expect(doseTone('MISSED')).toBe('danger')
    expect(doseTone('UPCOMING')).toBe('neutral')
  })

  it('splits minutes into hours and minutes', () => {
    expect(splitMinutes(135)).toEqual({ h: 2, m: 15 })
    expect(splitMinutes(40)).toEqual({ h: 0, m: 40 })
    expect(splitMinutes(-5)).toEqual({ h: 0, m: 0 })
  })
})

describe('vital helpers', () => {
  const bp: VitalRange = { kind: 'BP', low: 90, high: 140, low2: 60, high2: 90 }

  it('formats blood pressure as a pair and temperature with a decimal', () => {
    expect(formatVital('BP', 132, 84, fmt)).toBe('132/84')
    expect(formatVital('TEMP', 36.7, null, fmt)).toBe('36.7')
    expect(formatVital('SUGAR', 128, null, fmt)).toBe('128')
  })

  it('writes the usual range, or nothing when there is none', () => {
    expect(rangeLabel(bp, fmt)).toBe('90–140 / 60–90')
    expect(rangeLabel({ kind: 'TEMP', low: 36, high: 37.5, low2: null, high2: null }, fmt)).toBe('36.0–37.5')
    expect(rangeLabel({ kind: 'WEIGHT', low: null, high: null, low2: null, high2: null }, fmt)).toBeNull()
  })

  it('places a reading against the range like the server does', () => {
    expect(position(132, 84, bp)).toBeNull()
    expect(position(150, 84, bp)).toBe('above')
    expect(position(132, 95, bp)).toBe('above')
    expect(position(85, 55, bp)).toBe('below')
    expect(position(132, null, null)).toBeNull()
  })
})

describe('calendar helpers', () => {
  it('finds the Monday of any day of the week', () => {
    expect(mondayOf('2026-09-23')).toBe('2026-09-21')
    expect(mondayOf('2026-09-21')).toBe('2026-09-21')
    expect(mondayOf('2026-09-27')).toBe('2026-09-21')
  })

  it('walks across months and counts days', () => {
    expect(addDays('2026-09-28', 5)).toBe('2026-10-03')
    expect(weekDates('2026-09-28')).toHaveLength(7)
    expect(weekDates('2026-09-28')[6]).toBe('2026-10-04')
    expect(daysBetween('2026-09-23', '2026-10-03')).toBe(10)
  })
})
