import { albanianDate, albanianDateTime, albanianRelative, MONTHS, MONTHS_SHORT, partsOf, WEEKDAYS, WEEKDAYS_SHORT } from './albanian'

/** Locale-aware formatting. Albanian uses "sq-AL" conventions (day first, comma decimals). */

let locale = 'en'
let currency = 'EUR'
let timeZone: string | undefined

/** Chrome's Intl has no Albanian; dates then come from ./albanian and numbers use Italian rules (same marks). */
const SQ_MISSING = Intl.DateTimeFormat.supportedLocalesOf(['sq']).length === 0

export function setFormatLocale(next: string) {
  locale = next
}

export function setFormatCurrency(next: string) {
  currency = next
}

export function setFormatTimeZone(next: string | undefined) {
  timeZone = next
}

function tag() {
  if (locale !== 'sq') return 'en-GB'
  return SQ_MISSING ? 'it-IT' : 'sq-AL'
}

function albanianFallback() {
  return locale === 'sq' && SQ_MISSING
}

function toDate(value: string | number | Date): Date {
  if (value instanceof Date) return value
  // Plain dates ("2026-09-23") are calendar days, not instants: read them at local noon.
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) return new Date(value + 'T12:00:00')
  return new Date(value)
}

export function formatDate(value: string | number | Date | null | undefined, style: 'short' | 'medium' | 'long' = 'medium') {
  if (value === null || value === undefined || value === '') return '—'
  const d = toDate(value)
  const zone = isPlainDate(value) ? undefined : timeZone
  if (albanianFallback()) return albanianDate(partsOf(d, zone), style)
  const options: Intl.DateTimeFormatOptions =
    style === 'short'
      ? { day: 'numeric', month: 'short' }
      : style === 'long'
        ? { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }
        : { day: 'numeric', month: 'short', year: 'numeric' }
  return new Intl.DateTimeFormat(tag(), { ...options, timeZone: zone }).format(d)
}

export function formatDateTime(value: string | number | Date | null | undefined) {
  if (value === null || value === undefined || value === '') return '—'
  if (albanianFallback()) return albanianDateTime(partsOf(toDate(value), timeZone))
  return new Intl.DateTimeFormat(tag(), {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZone,
  }).format(toDate(value))
}

export function formatTime(value: string | number | Date | null | undefined) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'string' && /^\d{2}:\d{2}/.test(value)) return value.slice(0, 5)
  const p = partsOf(toDate(value), timeZone)
  return `${p.hour}:${p.minute}`
}

/** The calendar day ("2026-09-23") an instant falls on in the organisation's time zone. */
export function dayKey(value: string | number | Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone }).formatToParts(toDate(value))
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return `${get('year')}-${get('month')}-${get('day')}`
}

export function formatWeekday(value: string | Date, style: 'short' | 'long' = 'long') {
  if (albanianFallback()) {
    const day = partsOf(toDate(value), undefined).weekday
    return style === 'short' ? WEEKDAYS_SHORT[day]! : WEEKDAYS[day]!
  }
  return new Intl.DateTimeFormat(tag(), { weekday: style }).format(toDate(value))
}

export function formatMonthShort(value: string | Date) {
  if (albanianFallback()) return MONTHS_SHORT[partsOf(toDate(value), undefined).month]!
  return new Intl.DateTimeFormat(tag(), { month: 'short' }).format(toDate(value))
}

export function formatMonth(value: string | Date) {
  if (albanianFallback()) {
    const p = partsOf(toDate(value), undefined)
    return `${MONTHS[p.month]} ${p.year}`
  }
  return new Intl.DateTimeFormat(tag(), { month: 'long', year: 'numeric' }).format(toDate(value))
}

/** "3 days ago", "in 2 weeks" */
export function formatRelative(value: string | number | Date | null | undefined, now: Date = new Date()) {
  if (value === null || value === undefined || value === '') return '—'
  const d = toDate(value)
  const diffSeconds = Math.round((d.getTime() - now.getTime()) / 1000)
  const abs = Math.abs(diffSeconds)
  const say = albanianFallback()
    ? albanianRelative
    : (n: number, unit: Intl.RelativeTimeFormatUnit) => new Intl.RelativeTimeFormat(tag(), { numeric: 'auto' }).format(n, unit)
  if (abs < 60) return say(diffSeconds, 'second')
  if (abs < 3600) return say(Math.round(diffSeconds / 60), 'minute')
  if (abs < 86400) return say(Math.round(diffSeconds / 3600), 'hour')
  if (abs < 86400 * 7) return say(Math.round(diffSeconds / 86400), 'day')
  if (abs < 86400 * 45) return say(Math.round(diffSeconds / (86400 * 7)), 'week')
  if (abs < 86400 * 365) return say(Math.round(diffSeconds / (86400 * 30)), 'month')
  return say(Math.round(diffSeconds / (86400 * 365)), 'year')
}

/** Money is always carried as integer cents. */
export function formatMoney(cents: number | null | undefined, options: { currency?: string; decimals?: boolean } = {}) {
  if (cents === null || cents === undefined) return '—'
  const decimals = options.decimals ?? cents % 100 !== 0
  return new Intl.NumberFormat(tag(), {
    style: 'currency',
    currency: options.currency ?? currency,
    minimumFractionDigits: decimals ? 2 : 0,
    maximumFractionDigits: decimals ? 2 : 0,
  }).format(cents / 100)
}

export function formatNumber(value: number | null | undefined, maximumFractionDigits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return new Intl.NumberFormat(tag(), { maximumFractionDigits }).format(value)
}

export function formatPercent(value: number | null | undefined, maximumFractionDigits = 0) {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return new Intl.NumberFormat(tag(), { style: 'percent', maximumFractionDigits }).format(value)
}

/** "355691234567" -> "+355 69 123 4567" (Albanian mobile grouping, generic otherwise) */
export function formatPhone(value: string | null | undefined) {
  if (!value) return '—'
  const digits = value.replace(/\D/g, '')
  if (digits.startsWith('355') && digits.length === 12) {
    return `+355 ${digits.slice(3, 5)} ${digits.slice(5, 8)} ${digits.slice(8)}`
  }
  if (digits.startsWith('383') && digits.length === 11) {
    return `+383 ${digits.slice(3, 5)} ${digits.slice(5, 8)} ${digits.slice(8)}`
  }
  return '+' + digits.replace(/(\d{3})(?=\d)/g, '$1 ').trim()
}

/** Parse "12,50" or "12.50" typed by a person into cents. */
export function parseMoney(input: string | number | null | undefined): number | null {
  if (input === null || input === undefined || input === '') return null
  if (typeof input === 'number') return Math.round(input * 100)
  const normalized = input.replace(/\s/g, '').replace(',', '.')
  const n = Number(normalized)
  return Number.isFinite(n) ? Math.round(n * 100) : null
}

export function centsToInput(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) return ''
  return (cents / 100).toFixed(2).replace(/\.00$/, '')
}

export function todayIso(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function initials(name: string | null | undefined) {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '')).toUpperCase()
}

function isPlainDate(value: unknown) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
}
