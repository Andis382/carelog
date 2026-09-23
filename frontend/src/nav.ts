import type { Component } from 'vue'
import {
  PhCalendarDots,
  PhChartLine,
  PhChatCircleDots,
  PhHeartbeat,
  PhHouseLine,
  PhListBullets,
  PhPackage,
  PhPill,
  PhStethoscope,
  PhUsersThree,
} from '@phosphor-icons/vue'

export type NavItem = {
  /** route name */
  name: string
  /** i18n key for the label */
  label: string
  icon: Component
  /** shown in the phone's bottom bar (max 4); others go under "More" */
  primary?: boolean
  /** on desktop, lives in the account menu instead of the main navigation */
  menu?: boolean
  roles?: string[]
}

export const NAV: NavItem[] = [
  { name: 'home', label: 'nav.today', icon: PhHouseLine, primary: true },
  { name: 'log', label: 'nav.log', icon: PhListBullets, primary: true },
  { name: 'medications', label: 'nav.medications', icon: PhPill, primary: true },
  { name: 'vitals', label: 'nav.vitals', icon: PhHeartbeat },
  { name: 'rota', label: 'nav.rota', icon: PhCalendarDots, primary: true },
  { name: 'supplies', label: 'nav.supplies', icon: PhPackage },
  { name: 'visits', label: 'nav.visits', icon: PhStethoscope },
  { name: 'summary', label: 'nav.summary', icon: PhChartLine },
  { name: 'circle', label: 'nav.circle', icon: PhUsersThree, menu: true },
  { name: 'messages', label: 'nav.messages', icon: PhChatCircleDots, menu: true },
]
