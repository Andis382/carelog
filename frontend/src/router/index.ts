import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useCircle } from '@/stores/circle'
import AppShell from '@/components/layout/AppShell.vue'

declare module 'vue-router' {
  interface RouteMeta {
    /** needs a signed-in user */
    auth?: boolean
    /** only for signed-out visitors (login, register) */
    guest?: boolean
    /** roles allowed; empty = everyone signed in */
    roles?: string[]
    /** which nav item to highlight for nested screens */
    nav?: string
    /** document title key */
    title?: string
  }
}

const PLANNERS = ['OWNER', 'FAMILY']

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: { guest: true } },
  { path: '/register', name: 'register', component: () => import('@/views/auth/RegisterView.vue'), meta: { guest: true } },
  { path: '/join/:token', name: 'join', component: () => import('@/views/auth/JoinView.vue') },
  {
    path: '/welcome',
    name: 'welcome',
    component: () => import('@/views/OnboardingView.vue'),
    meta: { auth: true, roles: PLANNERS, title: 'onboarding.title' },
  },
  {
    path: '/',
    component: AppShell,
    meta: { auth: true },
    children: [
      { path: '', name: 'home', component: () => import('@/views/TodayView.vue'), meta: { title: 'nav.today' } },
      { path: 'log', name: 'log', component: () => import('@/views/LogView.vue'), meta: { title: 'nav.log' } },
      { path: 'medications', name: 'medications', component: () => import('@/views/MedicationsView.vue'), meta: { title: 'nav.medications' } },
      {
        path: 'medications/new',
        name: 'medication-new',
        component: () => import('@/views/MedicationFormView.vue'),
        meta: { nav: 'medications', roles: PLANNERS, title: 'meds.add' },
      },
      {
        path: 'medications/:id',
        name: 'medication',
        component: () => import('@/views/MedicationDetailView.vue'),
        meta: { nav: 'medications', title: 'nav.medications' },
      },
      {
        path: 'medications/:id/edit',
        name: 'medication-edit',
        component: () => import('@/views/MedicationFormView.vue'),
        meta: { nav: 'medications', roles: PLANNERS, title: 'meds.edit' },
      },
      { path: 'vitals', name: 'vitals', component: () => import('@/views/VitalsView.vue'), meta: { title: 'nav.vitals' } },
      { path: 'rota', name: 'rota', component: () => import('@/views/RotaView.vue'), meta: { title: 'nav.rota' } },
      { path: 'supplies', name: 'supplies', component: () => import('@/views/SuppliesView.vue'), meta: { title: 'nav.supplies' } },
      { path: 'visits', name: 'visits', component: () => import('@/views/VisitsView.vue'), meta: { title: 'nav.visits' } },
      {
        path: 'visits/new',
        name: 'visit-new',
        component: () => import('@/views/VisitFormView.vue'),
        meta: { nav: 'visits', title: 'visits.add' },
      },
      {
        path: 'visits/:id/edit',
        name: 'visit-edit',
        component: () => import('@/views/VisitFormView.vue'),
        meta: { nav: 'visits', title: 'visits.edit' },
      },
      { path: 'summary', name: 'summary', component: () => import('@/views/SummaryView.vue'), meta: { title: 'nav.summary' } },
      { path: 'circle', name: 'circle', component: () => import('@/views/CircleView.vue'), meta: { title: 'nav.circle' } },
      { path: 'messages', name: 'messages', component: () => import('@/views/MessagesView.vue'), meta: { title: 'nav.messages' } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    if (to.hash) return { el: to.hash }
    if (to.path !== from.path) return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const auth = useAuth()
  if (!auth.ready) await auth.load()
  if (to.meta.auth && !auth.signedIn) {
    return { name: 'login', query: to.fullPath !== '/' ? { next: to.fullPath } : {} }
  }
  if (to.meta.guest && auth.signedIn) return { name: 'home' }
  const roles = to.matched.flatMap((r) => r.meta.roles ?? [])
  if (roles.length && !auth.hasRole(...roles)) return { name: 'home' }

  // A new circle starts with its elder: owner and family land on the setup until it exists.
  if (auth.signedIn && to.matched.some((r) => r.meta.auth)) {
    const circle = useCircle()
    if (!circle.loaded) {
      try {
        await circle.load()
      } catch {
        return true
      }
    }
    if (!circle.elder && auth.canPlan && to.name !== 'welcome') return { name: 'welcome' }
  }
})

export default router
