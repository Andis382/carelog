/** App-wide constants. The demo account is created by the backend's seeder in demo mode. */
export const DEMO = {
  email: 'demo@carelog.test',
  password: 'demo1234',
}

/** Roles the coordinator can hand out with a join link (the coordinator role itself is not one). */
export const INVITE_ROLES: string[] = ['FAMILY', 'CARER', 'VIEWER']
