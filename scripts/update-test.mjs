// Update test (spec section 7): fills a running app with fictional test data, and later checks that an
// update kept the data. Used by the CI job "update-test"; works the same against a local docker compose.
//
//   node scripts/update-test.mjs seed  <snapshot.json>   create the data and save what the app shows
//   node scripts/update-test.mjs check <snapshot.json>   compare what the app shows now with the snapshot
//
// Environment: ADMIN_EMAIL and ADMIN_PASSWORD (the system admin of the app), BASE_URL (default
// http://localhost:8080).
//
// AP 27 (section 19) replaced series and category targets with planned items. "seed" finds out which model the
// running version has:
// - "legacy" (before AP 27, e.g. main before the merge): data in the old model; "check" proves the conversion of
//   migration V14: both users can still sign in, the bookings of the month stay with their amounts (past occurrences
//   become real bookings), the series become planned items, the default account and the icons stay.
// - "plan" (from AP 27): data in the new model; "check" compares what the app shows before and after.

import { readFileSync, writeFileSync } from 'node:fs'

const baseUrl = process.env.BASE_URL ?? 'http://localhost:8080'
const month = '2026-10'
const personA = { email: process.env.ADMIN_EMAIL, password: process.env.ADMIN_PASSWORD }
const personB = { email: 'person-b@example.test', displayName: 'Person B', password: 'test-only-password-b' }

/** A signed-in browser: keeps the session and the CSRF cookie and sends the token on writing calls. */
class Client {
  cookies = new Map()

  async request(method, path, body) {
    const response = await this.send(method, path, body)
    if (!response.ok) {
      throw new Error(`${method} ${path} -> ${response.status} ${await response.text()}`)
    }
    return response.status === 204 ? null : response.json()
  }

  async send(method, path, body) {
    const headers = { Cookie: [...this.cookies].map(([name, value]) => `${name}=${value}`).join('; ') }
    if (method !== 'GET') {
      headers['X-XSRF-TOKEN'] = this.cookies.get('XSRF-TOKEN') ?? ''
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
    }
    const response = await fetch(baseUrl + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    for (const cookie of response.headers.getSetCookie()) {
      const [pair] = cookie.split(';')
      const index = pair.indexOf('=')
      this.cookies.set(pair.slice(0, index), pair.slice(index + 1))
    }
    return response
  }

  // Only fetches the CSRF cookie; 401 is expected without a session
  async fetchCsrfToken() {
    await this.request('GET', '/api/auth/me').catch(() => {})
    return this
  }

  async signIn({ email, password }) {
    await this.fetchCsrfToken()
    await this.request('POST', '/api/auth/login', { email, password })
    return this
  }
}

async function categoryId(client, name) {
  const categories = await client.request('GET', `/api/categories?month=${month}`)
  return categories.find((category) => category.name === name).id
}

/** "plan" when the running version has planned items (AP 27), else "legacy". */
async function modelOf(client) {
  const response = await client.send('GET', `/api/plan?month=${month}`)
  return response.ok ? 'plan' : 'legacy'
}

const singles = [
  [3000, 'Gehalt', '2026-10-01', 'Gehalt/Lohn (netto)'],
  [-120.3, 'Wocheneinkauf', '2026-10-04', 'Lebensmittel'],
  [-200.2, 'Großeinkauf', '2026-10-18', 'Lebensmittel'],
  [30, 'Erstattung Pfand', '2026-10-20', 'Lebensmittel'],
  [-45.99, 'Tanken', '2026-10-12', 'Mobilität'],
]

/** A second user with own data, to see that the data stays separated (R15) and the users stay after an update. */
async function invitePersonB(a) {
  const { link } = await a.request('POST', '/api/invitations')
  const token = new URL(link).searchParams.get('einladung')
  const invited = await new Client().fetchCsrfToken()
  await invited.request('POST', `/api/invitations/${token}/accept`, personB)
  const b = await new Client().signIn(personB)
  await b.request('POST', '/api/transactions', {
    amount: -55, description: 'Kino', date: '2026-10-09', categoryId: await categoryId(b, 'Freizeit'),
  })
}

async function seedLegacy(a) {
  await a.request('PUT', `/api/categories/${await categoryId(a, 'Lebensmittel')}/target?month=${month}`, {
    targetAmount: 400,
  })
  for (const [amount, description, date, category] of singles) {
    await a.request('POST', '/api/transactions', { amount, description, date, categoryId: await categoryId(a, category) })
  }
  await a.request('POST', '/api/recurring-transactions', {
    amount: -900, description: 'Miete', categoryId: await categoryId(a, 'Wohnen'), interval: 'MONTHLY', startDate: '2026-01-01',
  })
  await a.request('POST', '/api/recurring-transactions', {
    amount: -240, description: 'Haftpflicht', categoryId: await categoryId(a, 'Versicherungen'), interval: 'YEARLY', startDate: '2026-10-15',
  })
  await invitePersonB(a)
}

async function seedPlan(a) {
  // Accounts (P11): a savings account with a bank balance and a comfort amount
  const accounts = await a.request('GET', '/api/bank-accounts')
  const giro = accounts.find((account) => account.defaultAccount)
  const tagesgeld = await a.request('POST', '/api/bank-accounts', { name: 'Tagesgeld', role: 'SAVINGS' })
  await a.request('POST', `/api/bank-accounts/${tagesgeld.id}/balances`, { date: '2026-09-30', amount: 1000 })
  await a.request('PUT', `/api/bank-accounts/${tagesgeld.id}/comfort-amount`, { amount: 500 })

  // Planned items (P2–P5); MANUAL, so no automatic entries depend on the day of the test
  const item = (name, amount, category, unit, count, anchorDate) => ({
    name, amount, categoryId: category, rhythmUnit: unit, rhythmCount: count, anchorDate, entryMode: 'MANUAL',
    validFrom: '2026-01',
  })
  await a.request('POST', '/api/planned-items', item('Gehalt', 3000, await categoryId(a, 'Gehalt/Lohn (netto)'), 'MONTH', 1, '2026-01-01'))
  await a.request('POST', '/api/planned-items', item('Miete', -900, await categoryId(a, 'Wohnen'), 'MONTH', 1, '2026-01-01'))
  const kfz = await a.request('POST', '/api/planned-items',
    item('Kfz-Versicherung', -1200, await categoryId(a, 'Versicherungen'), 'MONTH', 12, '2026-10-15'))
  await a.request('PUT', `/api/planned-items/${kfz.id}/reserve-account`, { accountId: tagesgeld.id })
  await a.request('POST', '/api/planned-items', item('Lebensmittel', -400, await categoryId(a, 'Lebensmittel'), null, null, null))
  const goal = await a.request('POST', '/api/saving-goals', { name: 'Notgroschen', targetAmount: 3000, targetDate: null })

  for (const [amount, description, date, category] of singles) {
    await a.request('POST', '/api/transactions', { amount, description, date, categoryId: await categoryId(a, category) })
  }
  await a.request('POST', '/api/transactions', {
    amount: -900, description: 'Miete', date: '2026-10-01', categoryId: await categoryId(a, 'Wohnen'),
  })
  // P8: the yearly payment from the reserve, from the linked savings account
  await a.request('POST', '/api/transactions', {
    amount: -1200, description: 'Kfz-Versicherung', date: '2026-10-15', categoryId: await categoryId(a, 'Versicherungen'),
    accountId: tagesgeld.id, reserveItemKey: kfz.itemKey,
  })
  // P1: Sparen from the everyday account to the savings account; a payment from the saving goal
  await a.request('POST', '/api/transactions', {
    amount: -300, description: 'Auf Tagesgeld', date: '2026-10-02', saving: true, accountId: giro.id,
    targetAccountId: tagesgeld.id,
  })
  await a.request('POST', '/api/transactions', {
    amount: -80, description: 'Reparatur', date: '2026-10-22', categoryId: await categoryId(a, 'Mobilität'),
    savingGoalId: goal.id,
  })
  await invitePersonB(a)
}

async function seed() {
  const a = await new Client().signIn(personA)
  const model = await modelOf(a)
  await (model === 'plan' ? seedPlan(a) : seedLegacy(a))
  return { model, ...(await snapshot(model)) }
}

const entriesOf = (entries) =>
  entries.filter((entry) => !entry.transfer).map((entry) => `${entry.date} ${entry.amount} ${entry.description}`).sort()

/**
 * What the app shows for both users; must be the same before and after an update. In the legacy model only what
 * migration V14 keeps: the bookings of the month with their sums (past occurrences of series become bookings).
 */
async function snapshot(model) {
  const result = {}
  for (const [name, credentials] of [['Person A', personA], ['Person B', personB]]) {
    const client = await new Client().signIn(credentials)
    const dashboard = await client.request('GET', `/api/dashboard?month=${month}`)
    const entries = await client.request('GET', `/api/transactions?month=${month}`)
    const person = {
      incomeTotal: dashboard.incomeTotal,
      expenses: dashboard.expenses.actual,
      entries: entriesOf(entries),
    }
    if (model === 'plan') {
      const plan = await client.request('GET', `/api/plan?month=${month}`)
      const accounts = await client.request('GET', '/api/accounts')
      const reserves = await client.request('GET', '/api/reserves/by-account')
      const goals = await client.request('GET', '/api/saving-goals')
      Object.assign(person, {
        budget: dashboard.budget?.actual ?? null,
        fromReserves: dashboard.fromReserves ?? null,
        saved: dashboard.saving?.actual ?? null,
        fix: dashboard.expenses.recurring,
        plan: [plan.income, plan.fixed, plan.budgets, plan.saving],
        items: plan.items.map((item) => `${item.name} ${item.amount} ${item.entryMode}`).sort(),
        transfers: entries.filter((entry) => entry.transfer).map((entry) => `${entry.date} ${entry.amount} ${entry.accountName}`).sort(),
        accounts: accounts.flatMap((group) => group.accounts.map((account) =>
          `${account.name} ${account.role} ${account.defaultAccount} ${account.statementAmount} ${account.comfortAmount}`)).sort(),
        reserves: reserves.map((group) => `${group.accountName} ${group.target}`).sort(),
        goals: goals.map((goal) => `${goal.name} ${goal.targetAmount}`).sort(),
      })
    }
    result[name] = person
  }
  return result
}

function fail(message) {
  console.error(message)
  process.exit(1)
}

async function check(before) {
  const a = await new Client().signIn(personA)
  const model = await modelOf(a)
  if (before.model === 'plan' && model !== 'plan') {
    fail('The update went back from planned items to the old model.')
  }
  // Both users must still sign in (snapshot signs in as Person A and Person B)
  const after = JSON.stringify({ model: before.model, ...(await snapshot(before.model)) }, null, 2)
  const expected = JSON.stringify(before, null, 2)
  if (after !== expected) {
    fail(`The data changed with the update.\nBefore:\n${expected}\nAfter:\n${after}`)
  }
  // US28: the categories keep their icon
  const categories = await a.request('GET', `/api/categories?month=${month}`)
  if (categories.find((category) => category.name === 'Wohnen')?.icon !== 'home') {
    fail('Expected the icon "home" for Wohnen after the update.')
  }
  if (before.model === 'legacy') {
    // V14: the series became planned items; R26: the used default account "Girokonto" stays
    const plan = await a.request('GET', `/api/plan?month=${month}`)
    const names = plan.items.map((item) => item.name).sort()
    if (!names.includes('Miete') || !names.includes('Haftpflicht')) {
      fail(`Expected the series Miete and Haftpflicht as planned items after the update, got ${JSON.stringify(names)}.`)
    }
    const accounts = await a.request('GET', '/api/bank-accounts')
    if (accounts.length !== 1 || accounts[0].name !== 'Girokonto' || !accounts[0].defaultAccount || !accounts[0].used) {
      fail(`Expected the used default account "Girokonto" after the update, got ${JSON.stringify(accounts)}.`)
    }
  }
  console.log(`Data unchanged after the update (${before.model} model).`)
}

const [command, file] = process.argv.slice(2)
if (!personA.email || !personA.password || !file || !['seed', 'check'].includes(command)) {
  console.error('Usage: ADMIN_EMAIL=… ADMIN_PASSWORD=… node scripts/update-test.mjs seed|check <snapshot.json>')
  process.exit(2)
}

if (command === 'seed') {
  const before = await seed()
  writeFileSync(file, JSON.stringify(before, null, 2))
  console.log('Test data created:\n' + JSON.stringify(before, null, 2))
} else {
  await check(JSON.parse(readFileSync(file, 'utf8')))
}
