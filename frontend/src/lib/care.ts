import type { DoseLine, DoseState, VitalKind, VitalRange } from '@/types/care'

/** Small pure helpers for the care screens. Words stay in i18n; these only shape numbers and lists. */

export type Tone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'accent'

export const VITAL_KINDS: VitalKind[] = ['BP', 'SUGAR', 'TEMP', 'PULSE', 'SPO2', 'WEIGHT']

/** Temperature and weight keep one decimal, the rest are whole numbers (as the server stores them). */
export const VITAL_DECIMALS: Record<VitalKind, number> = { BP: 0, SUGAR: 0, TEMP: 1, PULSE: 0, SPO2: 0, WEIGHT: 1 }

export function isOpen(state: DoseState): boolean {
  return state === 'UPCOMING' || state === 'DUE' || state === 'LATE' || state === 'MISSED'
}

export function doseTone(state: DoseState): Tone {
  switch (state) {
    case 'GIVEN':
      return 'success'
    case 'DUE':
      return 'primary'
    case 'LATE':
      return 'warning'
    case 'MISSED':
      return 'danger'
    case 'REFUSED':
      return 'accent'
    default:
      return 'neutral'
  }
}

/** Doses that share a time are given together, so the timeline groups them. */
export function groupByTime(lines: DoseLine[]): { time: string; lines: DoseLine[] }[] {
  const groups: { time: string; lines: DoseLine[] }[] = []
  for (const line of lines) {
    const last = groups[groups.length - 1]
    if (last && last.time === line.time) last.lines.push(line)
    else groups.push({ time: line.time, lines: [line] })
  }
  return groups
}

/** How many of the day's doses have an answer (given, skipped or refused). */
export function progress(lines: DoseLine[]): { done: number; total: number } {
  return { done: lines.filter((l) => !isOpen(l.state)).length, total: lines.length }
}

export function splitMinutes(total: number): { h: number; m: number } {
  const minutes = Math.max(0, Math.round(total))
  return { h: Math.floor(minutes / 60), m: minutes % 60 }
}

type NumberFormat = (value: number, digits: number) => string

/** "132/84" for blood pressure, "36,7" or "36.7" for temperature, as the locale writes it. */
export function formatVital(kind: VitalKind, value1: number, value2: number | null, fmt: NumberFormat): string {
  const first = fmt(value1, VITAL_DECIMALS[kind])
  return value2 === null || value2 === undefined ? first : `${first}/${fmt(value2, 0)}`
}

/** "90–140 / 60–90", or null when the circle has no range for this kind. */
export function rangeLabel(range: VitalRange | null | undefined, fmt: NumberFormat): string | null {
  if (!range || range.low === null || range.high === null) return null
  const digits = VITAL_DECIMALS[range.kind]
  const first = `${fmt(range.low, digits)}–${fmt(range.high, digits)}`
  if (range.low2 === null || range.high2 === null) return first
  return `${first} / ${fmt(range.low2, 0)}–${fmt(range.high2, 0)}`
}

/** Mirrors the server: above if either value is over its upper bound, below if under its lower one. */
export function position(value1: number, value2: number | null, range: VitalRange | null | undefined): 'above' | 'below' | null {
  if (!range) return null
  const over = (v: number | null, limit: number | null) => v !== null && limit !== null && v > limit
  const under = (v: number | null, limit: number | null) => v !== null && limit !== null && v < limit
  if (over(value1, range.high) || over(value2, range.high2)) return 'above'
  if (under(value1, range.low) || under(value2, range.low2)) return 'below'
  return null
}

// ----------------------------------------------------------------- calendar days as "YYYY-MM-DD"

export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function parse(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y!, m! - 1, d!, 12)
}

export function addDays(iso: string, days: number): string {
  const d = parse(iso)
  d.setDate(d.getDate() + days)
  return isoDate(d)
}

/** The Monday of the week containing the day (weeks run Monday to Sunday). */
export function mondayOf(iso: string): string {
  const d = parse(iso)
  const offset = (d.getDay() + 6) % 7
  return addDays(iso, -offset)
}

export function weekDates(monday: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i))
}

export function daysBetween(fromIso: string, toIso: string): number {
  return Math.round((parse(toIso).getTime() - parse(fromIso).getTime()) / 86_400_000)
}
