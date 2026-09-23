/**
 * Albanian dates and relative times for browsers whose Intl data has no "sq" (Chrome ships
 * ICU trimmed to its interface languages, and Albanian is not one of them). Pure functions over
 * calendar parts, so they can be tested anywhere.
 */

export const MONTHS = ['janar', 'shkurt', 'mars', 'prill', 'maj', 'qershor', 'korrik', 'gusht', 'shtator', 'tetor', 'nëntor', 'dhjetor']
export const MONTHS_SHORT = ['jan', 'shk', 'mar', 'pri', 'maj', 'qer', 'korr', 'gush', 'sht', 'tet', 'nën', 'dhj']
export const WEEKDAYS = ['e diel', 'e hënë', 'e martë', 'e mërkurë', 'e enjte', 'e premte', 'e shtunë']
export const WEEKDAYS_SHORT = ['Die', 'Hën', 'Mar', 'Mër', 'Enj', 'Pre', 'Sht']

/** Calendar parts of an instant: month 0-11, weekday 0 = Sunday, hour and minute as "08". */
export type DateParts = { year: number; month: number; day: number; weekday: number; hour: string; minute: string }

const ENGLISH_WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

/** Reads the parts in a time zone with en-US data, which every browser has. */
export function partsOf(date: Date, timeZone: string | undefined): DateParts {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return {
    year: Number(get('year')),
    month: Number(get('month')) - 1,
    day: Number(get('day')),
    weekday: ENGLISH_WEEKDAYS.indexOf(get('weekday')),
    hour: get('hour').padStart(2, '0'),
    minute: get('minute').padStart(2, '0'),
  }
}

/** "23 sht", "23 sht 2026", "e mërkurë, 23 shtator 2026" */
export function albanianDate(p: DateParts, style: 'short' | 'medium' | 'long'): string {
  if (style === 'short') return `${p.day} ${MONTHS_SHORT[p.month]}`
  if (style === 'long') return `${WEEKDAYS[p.weekday]}, ${p.day} ${MONTHS[p.month]} ${p.year}`
  return `${p.day} ${MONTHS_SHORT[p.month]} ${p.year}`
}

/** "23 sht, 08:05" */
export function albanianDateTime(p: DateParts): string {
  return `${p.day} ${MONTHS_SHORT[p.month]}, ${p.hour}:${p.minute}`
}

type Unit = 'second' | 'minute' | 'hour' | 'day' | 'week' | 'month' | 'year'

// [past, future singular, future plural] as Albanian says them.
const UNITS: Record<Unit, { past: string; one: string; many: string }> = {
  second: { past: 'sekonda', one: 'sekonde', many: 'sekondash' },
  minute: { past: 'minuta', one: 'minute', many: 'minutash' },
  hour: { past: 'orë', one: 'ore', many: 'orësh' },
  day: { past: 'ditë', one: 'dite', many: 'ditësh' },
  week: { past: 'javë', one: 'jave', many: 'javësh' },
  month: { past: 'muaj', one: 'muaji', many: 'muajsh' },
  year: { past: 'vjet', one: 'viti', many: 'vitesh' },
}

/** "3 orë më parë", "pas 2 ditësh", "dje", "nesër", "tani" */
export function albanianRelative(value: number, unit: Unit): string {
  if (unit === 'second' && Math.abs(value) < 60) return 'tani'
  if (unit === 'day' && value === -1) return 'dje'
  if (unit === 'day' && value === 1) return 'nesër'
  if (value === 0) return unit === 'day' ? 'sot' : 'tani'
  const n = Math.abs(value)
  const u = UNITS[unit]
  if (value < 0) return `${n} ${n === 1 && unit === 'minute' ? 'minutë' : n === 1 && unit === 'second' ? 'sekondë' : u.past} më parë`
  return `pas ${n} ${n === 1 ? u.one : u.many}`
}
