/** Shapes of the CareLog API. Dates are ISO strings; times of day are "HH:mm" in the circle's time zone. */

export type Role = 'OWNER' | 'FAMILY' | 'CARER' | 'VIEWER'
export type DoseState = 'UPCOMING' | 'DUE' | 'LATE' | 'MISSED' | 'GIVEN' | 'SKIPPED' | 'REFUSED'
export type DoseStatus = 'GIVEN' | 'SKIPPED' | 'REFUSED'
export type VitalKind = 'BP' | 'SUGAR' | 'TEMP' | 'PULSE' | 'SPO2' | 'WEIGHT'
export type MealSlot = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK' | 'DRINK'
export type MealAmount = 'ALL' | 'HALF' | 'LITTLE' | 'NONE'
export type JournalKind = 'NOTE' | 'MOOD' | 'PAIN' | 'SLEEP' | 'TOILET' | 'INCIDENT'
export type ShiftKind = 'DAY' | 'NIGHT' | 'VISIT'
export type SupplyStatus = 'OK' | 'RUNNING_LOW' | 'OUT'
export type Frequency = 'DAILY' | 'WEEKLY' | 'AS_NEEDED'
export type Plan = 'FREE' | 'FAMILY'

export type DoseEventView = {
  id: number
  status: DoseStatus
  byId: number
  by: string
  at: string
  note: string | null
  canUndo: boolean
}

export type DoseLine = {
  medicationId: number
  name: string
  strength: string | null
  doseText: string | null
  instructions: string | null
  time: string
  state: DoseState
  givenLate: boolean
  minutesLate: number
  event: DoseEventView | null
}

export type AsNeeded = {
  medicationId: number
  name: string
  strength: string | null
  doseText: string | null
  instructions: string | null
  last: DoseEventView | null
}

export type VitalRange = { kind: VitalKind; low: number | null; high: number | null; low2: number | null; high2: number | null }

export type Reading = {
  id: number
  kind: VitalKind
  value1: number
  value2: number | null
  measuredAt: string
  by: string
  note: string | null
  position: 'above' | 'below' | null
}

export type ScoreMark = { score: number; by: string; at: string }
export type MealMark = { amount: MealAmount; by: string; at: string }

export type JournalEntry = {
  id: number
  kind: JournalKind
  score: number | null
  text: string | null
  photoUrl: string | null
  by: string
  at: string
}

export type Duty = { userId: number; name: string; start: string; end: string; kind: ShiftKind; now: boolean; arrivedAt: string | null }

export type Appointment = {
  date: string
  time: string | null
  doctorName: string
  specialty: string | null
  place: string | null
  daysAway: number
}

export type ElderCard = {
  fullName: string
  firstName: string
  age: number | null
  town: string | null
  photoUrl: string | null
  conditions: string | null
  allergies: string | null
}

export type TodayData = {
  date: string
  today: string
  now: string
  graceMinutes: number
  elder: ElderCard | null
  duty: Duty[]
  myCheckIn: { id: number; checkedInAt: string } | null
  doses: DoseLine[]
  asNeeded: AsNeeded[]
  vitals: { kind: VitalKind; last: Reading | null; range: VitalRange }[]
  meals: Partial<Record<MealSlot, MealMark>>
  glasses: number
  mood: ScoreMark | null
  pain: ScoreMark | null
  journal: JournalEntry[]
  nextAppointment: Appointment | null
  lowSupplies: string[]
  swapsForMe: number
  canRecord: boolean
}

export type Medication = {
  id: number
  name: string
  strength: string | null
  label: string
  doseText: string | null
  instructions: string | null
  frequency: Frequency
  times: string[]
  weekdays: number[]
  startDate: string
  endDate: string | null
  active: boolean
  prescriber: string | null
  boxPhotoFileId: string | null
  boxPhotoUrl: string | null
  createdAt: string
  createdByName: string | null
  stoppedAt: string | null
  stoppedByName: string | null
  stopReason: string | null
}

export type FieldChange = { field: string; from: string | null; to: string | null }

export type MedicationDetail = {
  medication: Medication
  history: { id: number; kind: 'STARTED' | 'CHANGED' | 'STOPPED'; changes: FieldChange[]; note: string | null; byName: string; at: string }[]
  recent: { date: string; time: string | null; state: DoseState; givenLate: boolean; byName: string | null; at: string | null; note: string | null }[]
  recentFrom: string
}

export type VitalHistory = {
  kind: VitalKind
  range: VitalRange
  from: string
  visibleFrom: string | null
  readings: Reading[]
  stats: { count: number; min: number | null; avg: number | null; max: number | null; min2: number | null; avg2: number | null; max2: number | null }
}

export type LogType = 'MEDS' | 'VITALS' | 'MEALS' | 'NOTES' | 'VISITS' | 'SHIFTS' | 'SUPPLIES'

export type LogEntry = {
  type: LogType
  kind: string
  key: string
  at: string
  byId: number | null
  by: string | null
  data: Record<string, string | number | boolean | null>
}

export type LogPage = { from: string; to: string; visibleFrom: string | null; limitedByPlan: boolean; entries: LogEntry[] }

export type SwapView = {
  id: number
  shiftId: number
  fromUserId: number
  fromName: string
  toUserId: number
  toName: string
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED'
  message: string | null
  createdAt: string
  respondedAt: string | null
}

export type ShiftView = {
  id: number
  userId: number
  userName: string
  date: string
  start: string
  end: string
  kind: ShiftKind
  note: string | null
  minutes: number
  now: boolean
  pendingSwap: SwapView | null
}

export type Person = { id: number; name: string; role: Role }

export type RotaWeek = { weekStart: string; today: string; shifts: ShiftView[]; people: Person[] }

export type MyRota = {
  shifts: ShiftView[]
  incoming: { swap: SwapView; shift: ShiftView }[]
  outgoing: { swap: SwapView; shift: ShiftView }[]
  people: Person[]
}

export type Supply = { id: number; name: string; status: SupplyStatus; updatedBy: string | null; updatedAt: string }

export type Visit = {
  id: number
  date: string
  doctorName: string
  specialty: string | null
  place: string | null
  notes: string | null
  nextDate: string | null
  nextTime: string | null
  prescriptionFileId: string | null
  prescriptionUrl: string | null
  recordedById: number
  recordedBy: string | null
  createdAt: string
}

export type WeeklyReport = {
  weekStart: string
  weekEnd: string
  asOf: string
  complete: boolean
  doses: { due: number; given: number; givenLate: number; skipped: number; refused: number; missed: number; open: number; adherencePct: number | null }
  problems: { date: string; time: string; medicine: string; state: 'MISSED' | 'REFUSED' | 'SKIPPED'; note: string | null; by: string | null }[]
  vitals: {
    kind: VitalKind
    count: number
    min: number | null
    avg: number | null
    max: number | null
    min2: number | null
    avg2: number | null
    max2: number | null
    outsideRange: number
  }[]
  meals: { mainMeals: number; ateWell: number; ateLittle: number; daysRecorded: number; glassesPerDay: number | null }
  notes: { at: string; kind: JournalKind; score: number | null; text: string | null; by: string | null }[]
  moodAverage: number | null
  painAverage: number | null
  suppliesLow: string[]
  duty: { userId: number; name: string | null; scheduledMinutes: number; checkedInMinutes: number }[]
}

export type SentSummary = {
  id: number
  weekStart: string
  generatedAt: string
  source: 'SCHEDULED' | 'MANUAL'
  sentTo: string | null
  adherencePct: number | null
  content: string
}

export type SummaryPage = {
  report: WeeklyReport
  preview: { payerId: number | null; payerName: string | null; payerHasPhone: boolean; locale: string; text: string }
  sent: SentSummary[]
  thisWeek: string
  visibleFrom: string | null
}

export type Contact = { name: string; relation: string | null; phone: string | null }

export type Elder = {
  fullName: string
  firstName: string
  birthYear: number | null
  age: number | null
  photoFileId: string | null
  photoUrl: string | null
  conditions: string | null
  allergies: string | null
  gpName: string | null
  gpPhone: string | null
  address: string | null
  town: string | null
  contacts: Contact[]
}

export type Member = {
  id: number
  name: string
  email: string
  phone: string | null
  role: Role
  lastLoginAt: string | null
  you: boolean
  payer: boolean
}

export type Circle = {
  name: string
  timezone: string
  locale: string
  plan: Plan
  maxMembers: number | null
  historyDays: number
  visibleFrom: string | null
  payerUserId: number | null
  graceMinutes: number
  doseAlerts: boolean
  weeklySummary: boolean
  ranges: VitalRange[]
  elder: Elder | null
  members: Member[]
  openInvitations: number
}
